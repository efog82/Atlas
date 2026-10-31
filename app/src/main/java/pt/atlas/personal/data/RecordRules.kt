package pt.atlas.personal.data

import java.time.*

object RecordRules {
    val recurrences = setOf("NONE", "DAILY", "WEEKLY", "MONTHLY")
    fun validate(record: Record) {
        require(record.kind in Kind.all) { "Tipo de registo inválido." }
        require(record.title.isNotBlank() && record.title.length <= 200) { "O título deve ter entre 1 e 200 caracteres." }
        require(record.body.length <= 500_000) { "Texto demasiado longo." }
        require(record.tags.length <= 500 && record.category.length <= 100) { "Etiquetas ou categoria demasiado longas." }
        require(record.recurrence in recurrences) { "Recorrência inválida." }
        require(record.recurrence == "NONE" || record.dueAt != null) { "Uma repetição precisa de data." }
        require(record.endAt == null || record.dueAt != null && record.endAt > record.dueAt) { "O fim deve ser posterior ao início." }
        require(!record.reminder || record.dueAt != null) { "Define uma data para o lembrete." }
        require(record.progress in 0..100) { "Progresso inválido." }
    }

    /** Calendar arithmetic preserves wall time across DST, unlike adding 24 hours. */
    fun nextOccurrence(record: Record, after: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val due = record.dueAt ?: return null
        if (record.done) return null
        if (due > after) return due
        if (record.recurrence == "NONE") return null
        var time = Instant.ofEpochMilli(due).atZone(zone)
        val target = Instant.ofEpochMilli(after).atZone(zone)
        val steps = when (record.recurrence) {
            "DAILY" -> java.time.temporal.ChronoUnit.DAYS.between(time.toLocalDate(), target.toLocalDate())
            "WEEKLY" -> java.time.temporal.ChronoUnit.WEEKS.between(time.toLocalDate(), target.toLocalDate())
            else -> java.time.temporal.ChronoUnit.MONTHS.between(time.toLocalDate().withDayOfMonth(1), target.toLocalDate().withDayOfMonth(1))
        }.coerceAtLeast(0)
        fun advance(n: Long): ZonedDateTime = when (record.recurrence) {
            "DAILY" -> time.plusDays(n)
            "WEEKLY" -> time.plusWeeks(n)
            "MONTHLY" -> time.plusMonths(n)
            else -> error("Recorrência inválida")
        }
        var n = steps
        var candidate = advance(n)
        while (candidate.toInstant().toEpochMilli() <= after) { n++; candidate = advance(n) }
        return candidate.toInstant().toEpochMilli()
    }

    fun occursOn(record: Record, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val next = nextOccurrence(record.copy(done = false), start - 1, zone) ?: return false
        return next < date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
