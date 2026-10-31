package pt.atlas.personal.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

object Kind {
    const val TASK = "task"
    const val EVENT = "event"
    const val JOURNAL = "journal"
    const val NOTE = "note"
    const val MEMORY = "memory"
    const val HABIT = "habit"
    const val DOCUMENT = "document"
    val all = setOf(TASK, EVENT, JOURNAL, NOTE, MEMORY, HABIT, DOCUMENT)
}

@Entity(tableName = "records", indices = [Index("kind"), Index("dueAt")])
data class Record(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val kind: String,
    val title: String,
    val body: String = "",
    val category: String = "",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
    val endAt: Long? = null,
    val recurrence: String = "NONE",
    val done: Boolean = false,
    val reminder: Boolean = false,
    val progress: Int = 0
)

@Entity(tableName = "sections", foreignKeys = [ForeignKey(entity = Record::class,
    parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentId")])
data class Section(@PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val documentId: String, val page: Int, val text: String)

@Entity(tableName = "conversations")
data class Conversation(@PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "Nova conversa", val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "messages", foreignKeys = [ForeignKey(entity = Conversation::class,
    parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("conversationId")])
data class Message(@PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val conversationId: String, val role: String, val content: String,
    val createdAt: Long = System.currentTimeMillis())

@Dao
interface AtlasDao {
    @Query("SELECT * FROM records ORDER BY updatedAt DESC") fun observeRecords(): Flow<List<Record>>
    @Query("SELECT * FROM records ORDER BY updatedAt DESC") suspend fun records(): List<Record>
    @Query("SELECT * FROM records WHERE id = :id") suspend fun record(id: String): Record?
    @Query("SELECT * FROM records WHERE kind = :kind ORDER BY dueAt, createdAt LIMIT :limit")
    suspend fun byKind(kind: String, limit: Int = 50): List<Record>
    @Query("SELECT * FROM records WHERE (title LIKE :pattern ESCAPE '\' OR body LIKE :pattern ESCAPE '\' OR tags LIKE :pattern ESCAPE '\') AND kind IN (:kinds) ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun search(pattern: String, kinds: List<String>, limit: Int = 12): List<Record>
    @Query("SELECT * FROM sections WHERE text LIKE :pattern ESCAPE '\' ORDER BY documentId, page LIMIT :limit")
    suspend fun searchSections(pattern: String, limit: Int = 8): List<Section>
    @Query("SELECT * FROM sections WHERE documentId = :id ORDER BY page") suspend fun sections(id: String): List<Section>
    @Query("SELECT * FROM sections ORDER BY documentId, page") suspend fun allSections(): List<Section>
    @Upsert suspend fun save(record: Record)
    @Upsert suspend fun saveSections(sections: List<Section>)
    @Query("DELETE FROM records WHERE id = :id") suspend fun deleteRecord(id: String)
    @Query("DELETE FROM records") suspend fun deleteRecords()
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC") fun observeConversations(): Flow<List<Conversation>>
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC") suspend fun conversations(): List<Conversation>
    @Upsert suspend fun saveConversation(conversation: Conversation)
    @Query("DELETE FROM conversations WHERE id = :id") suspend fun deleteConversation(id: String)
    @Query("DELETE FROM conversations") suspend fun deleteConversations()
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt, rowid")
    fun observeMessages(id: String): Flow<List<Message>>
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt, rowid")
    suspend fun messages(id: String): List<Message>
    @Query("SELECT * FROM messages ORDER BY createdAt, rowid") suspend fun allMessages(): List<Message>
    @Upsert suspend fun saveMessage(message: Message)
}

@Database(entities = [Record::class, Section::class, Conversation::class, Message::class], version = 1, exportSchema = false)
abstract class AtlasDatabase : RoomDatabase() { abstract fun dao(): AtlasDao }

fun searchPattern(text: String): String = "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
