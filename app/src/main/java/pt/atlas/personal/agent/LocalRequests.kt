package pt.atlas.personal.agent

import java.text.Normalizer
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import pt.atlas.personal.data.*

sealed interface ReminderResolution {
    data class Ready(val proposal: Proposal) : ReminderResolution
    data class Clarify(val message: String) : ReminderResolution
}

data class LocalRead(val kind: String, val day: LocalDate? = null, val query: String = "")

object LocalRequests {
    fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
    fun command(text: String): String {
        var value = text.trim()
        repeat(5) { value = value.replace(Regex("(?i)^(?:atlas[, :]+|por favor[, ]+|podes\\s+|poderias\\s+|consegues\\s+|quero(?: que)?\\s+|gostaria de\\s+|preciso que\\s+)"), "").trim() }
        return value
    }
    private val prefix = Regex("(?i)^(?:(?:cria|criar|crie|adiciona|adicionar|define|definir|coloca|colocar|faz|fazer)\\s+(?:um\\s+)?lembrete\\b|lembrete\\b|lembra-me\\b|avisa-me\\b)")
    fun isReminder(text: String): Boolean = prefix.containsMatchIn(command(text)) && !Regex("^lembra-me de (?:como|porque|por que|quem|o que|qual|quando)\\b").containsMatchIn(normalized(command(text)))

    fun reminder(text: String, now: ZonedDateTime = ZonedDateTime.now()): ReminderResolution? {
        val input = command(text)
        val start = prefix.find(input) ?: return null
        if (!isReminder(text)) return null
        var body = input.substring(start.range.last + 1).trim()
        val clock = Regex("(?i)(?:(?:às|as|à|ad|pelas)\\s*)?(?<!\\d)(\\d{1,2})(?:\\s*horas?\\b|\\s*h(?:\\s*(\\d{2}))?\\b|:(\\d{2})\\b)")
        val clocks = clock.findAll(body).toList()
        if (clocks.size > 1) return ReminderResolution.Clarify("Encontrei mais de uma hora. Indica uma única hora para o lembrete.")
        val match = clocks.singleOrNull() ?: Regex("(?i)(?:às|as|à|ad|pelas)\\s*(\\d{1,2})(?!\\d)").find(body)
            ?: return ReminderResolution.Clarify("A que horas queres o lembrete? Repete o pedido com a hora, por exemplo: cria um lembrete às 21h para eu ligar o computador.")
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues.getOrNull(2)?.ifBlank { match.groupValues.getOrNull(3).orEmpty() }?.ifBlank { "0" }?.toInt() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return ReminderResolution.Clarify("A hora não é válida. Usa uma hora entre 00:00 e 23:59.")
        body = body.removeRange(match.range).trim()
        val norm = normalized(body)
        var day = when {
            norm.contains("depois de amanha") -> now.toLocalDate().plusDays(2)
            Regex("\\bamanha\\b").containsMatchIn(norm) -> now.toLocalDate().plusDays(1)
            else -> now.toLocalDate()
        }
        val explicit = Regex("\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{2}[-/]\\d{2}[-/]\\d{4})\\b").find(body)
        if (explicit != null) {
            day = runCatching {
                val pattern = if (explicit.value[4] == '-') "uuuu-MM-dd" else if (explicit.value.contains('/')) "dd/MM/uuuu" else "dd-MM-uuuu"
                LocalDate.parse(explicit.value, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT))
            }.getOrElse { return ReminderResolution.Clarify("A data não é válida. Usa dia/mês/ano.") }
            body = body.removeRange(explicit.range)
        } else if (Regex("\\b(?:segunda|terca|quarta|quinta|sexta|sabado|domingo|semana)\\b").containsMatchIn(norm)) {
            return ReminderResolution.Clarify("Indica a data exata ou usa hoje/amanhã para este lembrete.")
        }
        body = body.replace(Regex("(?i)\\b(?:depois de amanh[ãa]|amanh[ãa]|hoje)\\b"), " ").trim()
        repeat(3) { body = body.replace(Regex("(?i)^(?:de|para(?:\\s+eu)?|que|eu)\\s+"), "").trim() }
        body = body.trim(' ', '.', ',', ';').replace(Regex("\\s+"), " ")
        if (body.isBlank()) return ReminderResolution.Clarify("O que queres que te lembre? Inclui a ação e a hora no pedido.")
        val local = day.atTime(hour, minute)
        if (now.zone.rules.getValidOffsets(local).size != 1) return ReminderResolution.Clarify("Esta hora é ambígua ou não existe devido à mudança de hora. Escolhe outra hora.")
        val due = local.atZone(now.zone)
        if (!due.isAfter(now)) return ReminderResolution.Clarify("Essa hora já passou. Queres amanhã ou outra data? Repete o pedido indicando a data pretendida.")
        val proposal = ToolProtocol.proposal(ToolCall("tasks.create", mapOf("title" to body.take(200), "body" to body,
            "date" to local.format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")))), now.zone, now.toInstant().toEpochMilli())
        return ReminderResolution.Ready(proposal)
    }

    fun read(text: String, today: LocalDate = LocalDate.now()): LocalRead? {
        val input = command(text)
        val n = normalized(input).trimEnd('?', '.', '!')
        if (AgentPolicy.writeToolWithoutReminder(text) != null || isReminder(text)) return null
        val readVerb = Regex("^(?:/|verifica|verificar|ver|ve |mostra|mostrar|consulta|consultar|quais?|o que|que |lista|listar|diz|dizer|abre|abrir|procura|procurar|pesquisa|pesquisar)")
        if (!readVerb.containsMatchIn(n) && n !in setOf("agenda", "minha agenda", "diario", "meu diario", "tarefas", "minhas tarefas")) return null
        val kind = when {
            Regex("\\bagenda\\b|\\bcompromissos?\\b|\\breunioes\\b").containsMatchIn(n) -> Kind.EVENT
            Regex("\\bdiario\\b").containsMatchIn(n) -> Kind.JOURNAL
            Regex("\\btarefas?\\b").containsMatchIn(n) -> Kind.TASK
            else -> return null
        }
        val day = when {
            Regex("\\bamanha\\b").containsMatchIn(n) -> today.plusDays(1)
            Regex("\\bontem\\b").containsMatchIn(n) -> today.minusDays(1)
            Regex("\\bhoje\\b").containsMatchIn(n) -> today
            else -> null
        }
        val topic = if (n.startsWith("/diario ")) input.substringAfter(' ').trim() else Regex("(?i)\\b(?:sobre|contendo|com o texto)\\s+(.+)$").find(input)?.groupValues?.get(1)?.trim().orEmpty()
        return LocalRead(kind, day, topic)
    }

    fun capabilities(text: String): Boolean = normalized(command(text)).trimEnd('?', '.', '!') in setOf(
        "o que posso falar contigo", "o que posso falar cntg", "o que podes fazer", "o que consegues fazer", "como me podes ajudar")
    const val CAPABILITIES = "Podes conversar e pedir explicações, criar lembretes com confirmação, consultar a agenda, pesquisar o diário e guardar apontamentos. Por exemplo: «cria um lembrete às 21h para eu ligar o computador» ou «verifica a minha agenda». As consultas usam os registos locais. As explicações gerais são geradas pelo modelo instalado e podem conter erros."
}

object LocalReadFormatter {
    fun format(request: LocalRead, records: List<Record>, now: ZonedDateTime = ZonedDateTime.now()): String {
        val date = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm")
        fun at(time: Long) = Instant.ofEpochMilli(time).atZone(now.zone).format(date)
        if (request.kind == Kind.EVENT) {
            val first = request.day ?: now.toLocalDate()
            val last = request.day ?: first.plusDays(29)
            val found = buildList {
                for (record in records.filter { it.kind in setOf(Kind.EVENT, Kind.TASK) && it.dueAt != null && !it.done }) {
                    for (offset in 0..java.time.temporal.ChronoUnit.DAYS.between(first, last).toInt()) {
                        val day = first.plusDays(offset.toLong())
                        if (RecordRules.occursOn(record, day, now.zone)) {
                            val occurrence = RecordRules.nextOccurrence(record, day.atStartOfDay(now.zone).toInstant().toEpochMilli() - 1, now.zone)
                            if (occurrence != null) add(record to occurrence)
                        }
                    }
                }
            }.filter { request.query.isBlank() || (it.first.title + " " + it.first.body).contains(request.query, true) }.sortedBy { it.second }
            val heading = "Agenda local · ${if (request.day != null) request.day else "próximos 30 dias"}"
            if (found.isEmpty()) return "$heading\nNão há eventos ou lembretes guardados para este período."
            return heading + "\n" + found.take(20).joinToString("\n") { (r, time) -> "• ${at(time)} — ${r.title} (${if (r.kind == Kind.TASK && r.reminder) "lembrete" else if (r.kind == Kind.TASK) "tarefa" else "evento"})" } +
                if (found.size > 20) "\nMostrados os primeiros 20 registos. Abre Agenda para consultar os restantes." else ""
        }
        val rows = records.filter { r ->
            r.kind == request.kind && (request.kind != Kind.TASK || !r.done) &&
                (request.day == null || Instant.ofEpochMilli(if (request.kind == Kind.TASK) r.dueAt ?: r.createdAt else r.createdAt).atZone(now.zone).toLocalDate() == request.day) &&
                (request.query.isBlank() || (r.title + " " + r.body + " " + r.tags).contains(request.query, true))
        }.sortedByDescending { it.createdAt }
        val heading = if (request.kind == Kind.JOURNAL) "Diário local" else "Tarefas locais"
        if (rows.isEmpty()) return "$heading\nNão há registos guardados que correspondam a este pedido."
        return heading + "\n" + rows.take(10).joinToString("\n\n") { r ->
            "• ${r.title}\n${at(if (r.kind == Kind.TASK) r.dueAt ?: r.createdAt else r.createdAt)}" + if (r.body.isBlank()) "" else "\n${r.body.take(600)}"
        } + if (rows.size > 10) "\nMostrados os primeiros 10 registos." else ""
    }
}
