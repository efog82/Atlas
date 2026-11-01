package pt.atlas.personal.ai

import java.io.File
import java.util.zip.ZipFile

/** Checks the official MediaPipe bundle envelope without extracting untrusted files. */
object TaskBundle {
    fun validate(file: File) {
        require(file.length() > 1024) { "Pacote .task incompleto." }
        try {
            ZipFile(file).use { zip ->
                require(zip.size() in 3..20) { "Pacote .task inválido." }
                for (name in listOf("TF_LITE_PREFILL_DECODE", "TOKENIZER_MODEL", "METADATA")) {
                    val entry = zip.getEntry(name)
                    require(entry != null && !entry.isDirectory && entry.size > 0 && entry.size <= 4L * 1024 * 1024 * 1024) {
                        "Não é um pacote MediaPipe LLM: falta $name."
                    }
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw IllegalArgumentException("O .task não é um pacote MediaPipe LLM válido. Não renomeies GGUF ou .litertlm.", e)
        }
    }
}

/** Gemma has no system role: policies are merged into its first user turn. BOS belongs to the bundle. */
object MediaPipePrompt {
    fun render(prepared: List<Turn>): String {
        require(prepared.first().role == "system" && prepared.last().role == "user")
        val system = prepared.first().content
        return buildString {
            prepared.drop(1).forEachIndexed { i, turn ->
                require(turn.role in setOf("user", "assistant"))
                append("<start_of_turn>").append(if (turn.role == "assistant") "model" else "user").append('\n')
                if (i == 0) append(system).append("\n\n")
                append(turn.content).append("<end_of_turn>\n")
            }
            append("<start_of_turn>model\n")
        }
    }
}
