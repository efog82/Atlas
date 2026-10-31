package pt.atlas.personal.ai

data class PresentedOutput(val text: String, val thinking: Boolean = false)

/** Only protocol structure is removed. No language/factuality filter or replacement answer. */
object ModelOutput {
    private val boundaries = listOf("<|im_end|>", "<|im_start|>", "<end_of_turn>", "<start_of_turn>", "<eos>", "<|eot_id|>", "<|end_of_text|>")
    fun present(raw: String, reasoning: Boolean, forcedOpen: Boolean = false, complete: Boolean = false): PresentedOutput {
        var text = raw.trimStart()
        var open = forcedOpen
        if (reasoning && !open && text.isNotEmpty() && "<think>".startsWith(text) && !complete) return PresentedOutput("", true)
        if (reasoning && text.startsWith("<think>")) { text = text.removePrefix("<think>"); open = true }
        if (open) {
            val end = text.indexOf("</think>")
            if (end < 0) return PresentedOutput("", true)
            text = text.substring(end + "</think>".length).trimStart()
        } else if (reasoning && text.startsWith("</think>")) text = text.removePrefix("</think>").trimStart()
        val boundary = boundaries.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()
        if (boundary != null) text = text.substring(0, boundary)
        if (!complete) {
            // Hold a token split across native UTF-8/stream chunks until its meaning is known.
            for (token in boundaries + if (reasoning) listOf("<think>", "</think>") else emptyList()) {
                for (length in 1 until token.length) if (text.endsWith(token.take(length))) {
                    text = text.dropLast(length); break
                }
            }
        }
        return PresentedOutput(text.trimEnd(), false)
    }
    fun decodeUtf8(bytes: ByteArray, complete: Boolean = false): String {
        val decoder = Charsets.UTF_8.newDecoder()
        val output = java.nio.CharBuffer.allocate(bytes.size)
        val result = decoder.decode(java.nio.ByteBuffer.wrap(bytes), output, complete)
        if (result.isError) result.throwException()
        output.flip()
        return output.toString()
    }
    fun legacy(raw: String): String = present(raw, reasoning = raw.trimStart().startsWith("<think>"), complete = true).text
}
