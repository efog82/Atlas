package pt.atlas.personal

import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.data.*
import java.time.*

class RecordRulesTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private fun at(date: String): Long = LocalDateTime.parse(date).atZone(zone).toInstant().toEpochMilli()

    @Test fun dailyRecurrencePreservesLocalTimeAcrossDst() {
        val record = Record(kind = Kind.EVENT, title = "Estudar", dueAt = at("2027-03-27T20:00"), recurrence = "DAILY")
        val next = RecordRules.nextOccurrence(record, at("2027-03-28T00:00"), zone)!!
        assertEquals(at("2027-03-28T20:00"), next)
        assertEquals(23L * 60 * 60 * 1000, next - record.dueAt!!)
    }
    @Test fun monthlyRecurrenceRetainsOriginalAnchor() {
        val record = Record(kind = Kind.EVENT, title = "Mensal", dueAt = at("2027-01-31T12:00"), recurrence = "MONTHLY")
        assertEquals(at("2027-02-28T12:00"), RecordRules.nextOccurrence(record, at("2027-02-01T00:00"), zone))
        assertEquals(at("2027-03-31T12:00"), RecordRules.nextOccurrence(record, at("2027-03-01T00:00"), zone))
    }
    @Test fun completedAndExpiredOneShotHaveNoNextOccurrence() {
        val record = Record(kind = Kind.TASK, title = "Teste", dueAt = at("2027-01-01T10:00"))
        assertNull(RecordRules.nextOccurrence(record, at("2027-01-02T10:00"), zone))
        assertNull(RecordRules.nextOccurrence(record.copy(done = true), 0, zone))
    }
    @Test fun weeklyEventsAreVisibleOnlyOnCorrectDays() {
        val record = Record(kind = Kind.EVENT, title = "Semanal", dueAt = at("2027-01-04T10:00"), recurrence = "WEEKLY")
        assertTrue(RecordRules.occursOn(record, LocalDate.parse("2027-01-11"), zone))
        assertFalse(RecordRules.occursOn(record, LocalDate.parse("2027-01-12"), zone))
    }
    @Test fun invalidRecordsCannotBeSaved() {
        assertThrows(IllegalArgumentException::class.java) { RecordRules.validate(Record(kind = Kind.TASK, title = "")) }
        assertThrows(IllegalArgumentException::class.java) { RecordRules.validate(Record(kind = Kind.TASK, title = "A", reminder = true)) }
    }
    @Test fun literalSearchEscapesWildcards() {
        assertEquals("%100\\%\\_real%", searchPattern("100%_real"))
    }
}
