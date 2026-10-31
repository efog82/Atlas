package pt.atlas.personal.ai

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// User text must be sent unchanged. Appending metadata here caused the small model to echo it.
fun historyTurn(role: String, content: String): Turn = Turn(role, content)

/** Supply a clock in the system role only for temporal questions/actions, never on every user turn. */
fun clockContextFor(text: String, createdAt: Long, tools: Set<String>, zone: ZoneId = ZoneId.systemDefault()): String? {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    val temporal = tools.any { it in setOf("tasks.create", "events.create", "agenda.list") } ||
        Regex("\\b(?:hoje|amanha|ontem|que horas|qual (?:e )?a data|que dia|daqui a|proxima semana)\\b").containsMatchIn(normalized)
    if (!temporal) return null
    val time = Instant.ofEpochMilli(createdAt).atZone(zone).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm XXX"))
    return "Referência temporal da aplicação: $time (${zone.id}). Usa-a para interpretar datas relativas; responde ao pedido sem repetir este cabeçalho."
}

/** Presentation/context repair for metadata echoed by older APKs; does not modify Room records. */
object AssistantText {
    private val marker = Regex("(?im)^[ \\t]*\\[?data/hora local deste pedido[ \\t]*:?[ \\t]*")
    private val timestamp = Regex("^\\d{4}-\\d{2}-\\d{2}(?:[ \\t]*[Tt][ \\t]*|[ \\t]+)\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:[Zz]|[+-]\\d{2}:?\\d{2})?(?:\\[[A-Za-z_/]+\\])?(?![\\d:+\\-]|\\.\\d)\\]?[ \\t]*(?:[-—:][ \\t]+)?")
    private val prefixes = listOf("data/hora local deste pedido", "[data/hora local deste pedido")

    fun clean(text: String): String {
        var result = text
        // Strip only the known metadata label + ISO time, preserving the actual answer after it.
        val matches = marker.findAll(text).toList().asReversed()
        for (m in matches) {
            val suffix = text.substring(m.range.last + 1)
            val clock = timestamp.find(suffix) ?: continue
            result = result.removeRange(m.range.first, m.range.last + 1 + clock.value.length)
        }
        return result.trim()
    }
    fun streaming(text: String): String {
        val visible = clean(text)
        val lower = visible.trimStart().lowercase(Locale.ROOT)
        if (lower.isNotEmpty() && prefixes.any { it.startsWith(lower) || lower.startsWith(it) }) return ""
        if (marker.containsMatchIn(text) && visible.matches(Regex("[:+\\-\\]\\.\\s]+\\d*"))) return ""
        return visible
    }
}
