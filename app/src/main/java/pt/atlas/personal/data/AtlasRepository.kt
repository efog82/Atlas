package pt.atlas.personal.data

import androidx.room.withTransaction
import pt.atlas.personal.reminders.ReminderScheduler

class AtlasRepository(val db: AtlasDatabase, private val reminders: ReminderScheduler) {
    val dao = db.dao()
    suspend fun save(record: Record) {
        RecordRules.validate(record)
        dao.save(record.copy(title = record.title.trim(), updatedAt = System.currentTimeMillis()))
        reminders.schedule(record)
    }
    suspend fun delete(record: Record) { dao.deleteRecord(record.id); reminders.cancel(record.id) }
    suspend fun complete(record: Record) {
        if (!record.done && record.recurrence != "NONE" && record.dueAt != null) {
            val next = RecordRules.nextOccurrence(record, maxOf(System.currentTimeMillis(), record.dueAt))
            val duration = record.endAt?.minus(record.dueAt)
            save(record.copy(dueAt = next, endAt = next?.let { if (duration != null) it + duration else null },
                progress = (record.progress + 1).coerceAtMost(100)))
        } else save(record.copy(done = !record.done))
    }
    suspend fun wipe() {
        val records = dao.records()
        db.withTransaction { dao.deleteConversations(); dao.deleteRecords() }
        records.forEach { reminders.cancel(it.id) }
    }
}
