package pt.atlas.personal.study

import android.net.Uri
import androidx.room.withTransaction
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import kotlinx.coroutines.*
import pt.atlas.personal.AtlasApp
import pt.atlas.personal.data.*
import java.io.File
import java.util.UUID

class DocumentImporter(private val app: AtlasApp) {
    suspend fun import(uri: Uri): Record = withContext(Dispatchers.IO) {
        val name = app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Documento.txt"
        val type = app.contentResolver.getType(uri)
        val pdf = name.endsWith(".pdf", true) || type == "application/pdf"
        require(pdf || name.endsWith(".txt", true) || type == "text/plain") { "Importa um TXT UTF-8 ou PDF com texto selecionável." }
        val temp = File(app.cacheDir, "import-${UUID.randomUUID()}")
        try {
            app.contentResolver.openInputStream(uri).use { source ->
                requireNotNull(source) { "Não foi possível abrir o documento." }
                temp.outputStream().use { target ->
                    val buf = ByteArray(8192); var count = 0L
                    while (true) {
                        ensureActive(); val n = source.read(buf); if (n < 0) break
                        count += n; require(count <= 20L * 1024 * 1024) { "Limite de importação: 20 MB." }
                        target.write(buf, 0, n)
                    }
                }
            }
            val record = Record(kind = Kind.DOCUMENT, title = name.take(200), category = "Biblioteca")
            val sections = mutableListOf<Section>()
            if (pdf) {
                PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                    require(!document.isEncrypted) { "PDF protegido: exporta uma cópia sem palavra-passe." }
                    require(document.numberOfPages <= 300) { "Limite: 300 páginas por PDF." }
                    val stripper = PDFTextStripper()
                    var total = 0
                    for (page in 1..document.numberOfPages) {
                        ensureActive(); stripper.startPage = page; stripper.endPage = page
                        val text = stripper.getText(document).trim()
                        require(text.length <= 500_000) { "Página demasiado grande." }
                        total += text.length; require(total <= 4_000_000) { "Limite: 4 milhões de caracteres por documento." }
                        if (text.isNotBlank()) sections += Section(documentId = record.id, page = page, text = text)
                    }
                }
            } else {
                require(temp.length() <= 4_000_000) { "Limite TXT: 4 MB." }
                val text = temp.readText(Charsets.UTF_8)
                text.chunked(6000).forEachIndexed { index, chunk -> sections += Section(documentId = record.id, page = index + 1, text = chunk) }
            }
            require(sections.any { it.text.isNotBlank() }) { "Não foi encontrado texto selecionável. OCR não está incluído." }
            app.database.withTransaction { app.database.dao().save(record); app.database.dao().saveSections(sections) }
            record
        } finally { temp.delete() }
    }
}
