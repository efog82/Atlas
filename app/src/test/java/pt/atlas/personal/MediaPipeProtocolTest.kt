package pt.atlas.personal

import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.ai.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MediaPipeProtocolTest {
    @Test fun gemmaRolesAreRenderedExactlyOnceWithoutDuplicateBos() {
        val prompt = MediaPipePrompt.render(ChatHistory.prepare(listOf(Turn("system", "Política local"),
            Turn("user", "Olá"), Turn("assistant", "Bom dia"), Turn("user", "Explica")), true))
        assertTrue(prompt.startsWith("<start_of_turn>user\nPolítica local"))
        assertTrue(prompt.contains("<end_of_turn>\n<start_of_turn>model\nBom dia"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
        assertFalse(prompt.contains("<bos>"))
        assertFalse(prompt.contains("<start_of_turn>system"))
        assertEquals(1, Regex("Política local").findAll(prompt).count())
        assertTrue(prompt.contains(ModelInstructions.factual))
    }
    @Test fun rejectsRenamedGgufAndIncompleteTaskAndAcceptsOfficialEnvelope() {
        val file = File.createTempFile("atlas-task", ".task")
        try {
            file.writeBytes(ByteArray(2048))
            assertThrows(IllegalArgumentException::class.java) { TaskBundle.validate(file) }
            fun zip(names: List<String>) { ZipOutputStream(file.outputStream()).use { zip ->
                names.forEach { name ->
                    val bytes = ByteArray(2048) { (it % 251).toByte() }
                    val entry = ZipEntry(name).apply { method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size
                        crc = java.util.zip.CRC32().apply { update(bytes) }.value }
                    zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
                }
            } }
            zip(listOf("TF_LITE_PREFILL_DECODE", "TOKENIZER_MODEL"))
            assertThrows(IllegalArgumentException::class.java) { TaskBundle.validate(file) }
            zip(listOf("TF_LITE_PREFILL_DECODE", "TOKENIZER_MODEL", "METADATA"))
            TaskBundle.validate(file)
        } finally { file.delete() }
    }
    @Test fun factualSamplingRespectsFamiliesThinkingAndOptOut() {
        for (arch in listOf("gemma3", "qwen3", "qwen2", "llama")) {
            val info = ModelInfo(arch, if (arch == "qwen2") "Qwen2.5 Instruct" else arch)
            SamplingConfig.automatic(info, false, true).validate()
            assertEquals(SamplingConfig.recommended(info, false), SamplingConfig.automatic(info, false, false))
        }
        val qwen = ModelInfo("qwen3")
        assertEquals(SamplingConfig.recommended(qwen, true), SamplingConfig.automatic(qwen, true, true))
        assertTrue(SamplingConfig.automatic(qwen, false, true).temperature > 0f)
        assertEquals(0.3f, SamplingConfig.automatic(ModelInfo("gemma3"), false, true).temperature)
        assertEquals(0.3f, SamplingConfig.automatic(ModelInfo("qwen2", "Qwen2.5"), false, true).temperature)
    }
}
