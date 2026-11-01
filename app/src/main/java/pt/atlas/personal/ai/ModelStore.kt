package pt.atlas.personal.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

data class ModelFile(val file: File, val bytes: Long, val sha256: String?)

class ModelStore(private val context: Context) {
    private val directory = File(context.filesDir, "models").apply { mkdirs() }
    fun list(): List<ModelFile> = directory.listFiles().orEmpty().filter { it.extension in setOf("gguf", "task") && it.isFile }
        .map { ModelFile(it, it.length(), File(directory, it.name + ".sha256").takeIf(File::isFile)?.readText()) }.sortedBy { it.file.name }

    suspend fun import(uri: Uri, onProgress: (Long) -> Unit): File = withContext(Dispatchers.IO) {
        val output = File(directory, UUID.randomUUID().toString() + ".part")
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri).use { stream ->
                requireNotNull(stream) { "Não foi possível abrir o ficheiro." }
                output.outputStream().use { target ->
                    val buffer = ByteArray(1024 * 1024)
                    var count = 0L
                    while (true) {
                        ensureActive()
                        val n = stream.read(buffer)
                        if (n < 0) break
                        count += n
                        require(count <= 4L * 1024 * 1024 * 1024) { "O modelo excede o limite de 4 GB." }
                        require(directory.usableSpace > 32L * 1024 * 1024) { "Sem espaço de armazenamento." }
                        hash.update(buffer, 0, n); target.write(buffer, 0, n); onProgress(count)
                    }
                }
            }
            val filename = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "modelo.gguf"
            val extension = filename.substringAfterLast('.', "").lowercase()
            require(extension in setOf("gguf", "task")) { "Escolhe um ficheiro .gguf ou .task; não renomeies outros formatos." }
            if (extension == "task") TaskBundle.validate(output) else validateGguf(output)
            val digest = hash.digest().joinToString("") { "%02x".format(it) }
            val existing = list().firstOrNull { it.sha256 == digest }
            if (existing != null) { output.delete(); return@withContext existing.file }
            val base = filename.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}_.-]"), "_").take(90)
            val final = File(directory, "${base.ifBlank { "modelo" }}-${digest.take(8)}.$extension")
            check(output.renameTo(final)) { "Não foi possível guardar o modelo." }
            File(directory, final.name + ".sha256").writeText(digest)
            final
        } finally { output.delete() }
    }

    fun delete(file: File) {
        require(file.canonicalFile.parentFile == directory.canonicalFile)
        check(file.delete()) { "Não foi possível eliminar o modelo." }
        File(directory, file.name + ".sha256").delete()
    }
    companion object {
        const val TASK_PAGE = "https://huggingface.co/litert-community/Gemma3-1B-IT"
        const val TASK_DOWNLOAD = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/d448835d84411a08f577e25f3afebe528402d73b/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task?download=true"
        const val LIGHT_MODEL = "Qwen2.5-0.5B-Instruct • Q4_K_M"
        const val MODEL_PAGE = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF"
        const val GEMMA_PAGE = "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/tree/f9c28bcd85737ffc5aef028638d3341d49869c27"
        const val QWEN3_PAGE = "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/tree/23749fefcc72300e3a2ad315e1317431b06b590a"
        fun validateGguf(file: File) {
            require(file.length() >= 24) { "Ficheiro GGUF incompleto." }
            RandomAccessFile(file, "r").use { input ->
                require(input.readInt() == 0x47475546) { "O ficheiro não tem um cabeçalho GGUF." }
                val version = Integer.reverseBytes(input.readInt())
                require(version in 2..3) { "Versão GGUF não suportada: $version." }
            }
        }
    }
}
