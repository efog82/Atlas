package pt.atlas.personal.security

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pt.atlas.personal.AtlasApp
import pt.atlas.personal.data.*
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException

class BackupService(private val app: AtlasApp, private val database: AtlasDatabase = app.database,
    private val reminders: pt.atlas.personal.reminders.ReminderScheduler = app.reminders) {
    private val dao = database.dao()
    suspend fun export(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            val json = database.withTransaction {
                val records = JSONArray()
                dao.records().forEach { r -> records.put(JSONObject().put("id", r.id).put("kind", r.kind).put("title", r.title)
                    .put("body", r.body).put("category", r.category).put("tags", r.tags).put("created", r.createdAt)
                    .put("updated", r.updatedAt).put("due", r.dueAt ?: JSONObject.NULL).put("end", r.endAt ?: JSONObject.NULL)
                    .put("recurrence", r.recurrence).put("done", r.done).put("reminder", r.reminder).put("progress", r.progress)) }
                val conversations = JSONArray()
                dao.conversations().forEach { conversations.put(JSONObject().put("id", it.id).put("title", it.title).put("created", it.createdAt)) }
                val messages = JSONArray()
                dao.allMessages().forEach { messages.put(JSONObject().put("id", it.id).put("conversation", it.conversationId)
                    .put("role", it.role).put("content", it.content).put("created", it.createdAt)) }
                val sections = JSONArray()
                dao.allSections().forEach { sections.put(JSONObject().put("id", it.id).put("document", it.documentId).put("page", it.page).put("text", it.text)) }
                JSONObject().put("version", 1).put("records", records).put("conversations", conversations).put("messages", messages).put("sections", sections)
            }
            val encrypted = BackupCrypto.encrypt(json.toString().toByteArray(Charsets.UTF_8), password)
            app.contentResolver.openOutputStream(uri, "wt").use { requireNotNull(it) { "Destino indisponível." }.write(encrypted) }
        } finally { password.fill('\u0000') }
    }

    /** All validation/decryption finishes before the transaction touches existing data. */
    suspend fun restore(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            val encrypted = app.contentResolver.openInputStream(uri).use { stream ->
                requireNotNull(stream) { "Ficheiro indisponível." }
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = stream.read(buf); if (n < 0) break
                    require(out.size() + n <= 64 * 1024 * 1024 + 52) { "Cópia demasiado grande." }; out.write(buf, 0, n)
                }
                out.toByteArray()
            }
            val plain = try { BackupCrypto.decrypt(encrypted, password) }
            catch (_: AEADBadTagException) { error("Palavra-passe incorreta ou cópia alterada. Os dados atuais não foram modificados.") }
            val root = JSONObject(String(plain, Charsets.UTF_8))
            plain.fill(0)
            require(root.getInt("version") == 1) { "Versão de cópia não suportada." }
            fun objects(name: String): List<JSONObject> {
                val array = root.getJSONArray(name)
                require(array.length() <= 100_000) { "Demasiados registos na cópia." }
                return (0 until array.length()).map(array::getJSONObject)
            }
            fun JSONObject.id(key: String): String = getString(key).also { require(it.length in 1..100) }
            fun JSONObject.nullLong(key: String): Long? = if (isNull(key)) null else getLong(key)
            val records = objects("records").map { r -> Record(r.id("id"), r.getString("kind"), r.getString("title"),
                r.getString("body"), r.getString("category"), r.getString("tags"), r.getLong("created"), r.getLong("updated"),
                r.nullLong("due"), r.nullLong("end"), r.getString("recurrence"), r.getBoolean("done"), r.getBoolean("reminder"), r.getInt("progress"))
                .also(RecordRules::validate) }
            require(records.map { it.id }.toSet().size == records.size)
            val docs = records.filter { it.kind == Kind.DOCUMENT }.map { it.id }.toSet()
            val sections = objects("sections").map { s -> Section(s.id("id"), s.id("document"), s.getInt("page"), s.getString("text"))
                .also { require(it.documentId in docs && it.page in 1..10000 && it.text.length <= 500_000) } }
            require(sections.map { it.id }.toSet().size == sections.size)
            val conversations = objects("conversations").map { c -> Conversation(c.id("id"), c.getString("title"), c.getLong("created"))
                .also { require(it.title.length <= 200) } }
            require(conversations.map { it.id }.toSet().size == conversations.size)
            val ids = conversations.map { it.id }.toSet()
            val messages = objects("messages").map { m -> Message(m.id("id"), m.id("conversation"), m.getString("role"), m.getString("content"), m.getLong("created"))
                .also { require(it.conversationId in ids && it.role in setOf("user", "assistant", "system") && it.content.length <= 500_000) } }
            require(messages.map { it.id }.toSet().size == messages.size)
            val old = dao.records()
            database.withTransaction {
                dao.deleteConversations(); dao.deleteRecords()
                records.forEach { dao.save(it) }; dao.saveSections(sections)
                conversations.forEach { dao.saveConversation(it) }; messages.forEach { dao.saveMessage(it) }
            }
            old.forEach { reminders.cancel(it.id) }
            reminders.reschedule()
        } finally { password.fill('\u0000') }
    }
}
