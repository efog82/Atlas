package pt.atlas.personal.agent

import java.text.Normalizer
import java.util.Locale

/** Permission comes from the current user message, never from model output or earlier requests. */
object AgentPolicy {
    private fun normalized(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
    private fun command(text: String): String {
        var value = normalized(text).replace(Regex("^atlas[ ,:]+"), "")
        repeat(4) {
            value = value.replace(Regex("^(?:por favor[, ]+|podes\\s+|poderias\\s+|consegues\\s+|quero(?: que)?\\s+|gostaria de\\s+|preciso que\\s+)"), "").trim()
        }
        return value
    }
    fun writeTool(text: String): String? {
        val value = command(text)
        // Anchored commands: questions, explanations, quotations and negation do not confer permission.
        if (Regex("^(?:nao|nunca|cancela|cancelar|esquece|deixa estar)\\b").containsMatchIn(value)) return null
        if (Regex("^lembra-me de (?:como|porque|por que|quem|o que|qual|quando)\\b").containsMatchIn(value)) return null
        if (Regex("^lembra-me de\\s+.+").containsMatchIn(value)) return "tasks.create"
        val verb = "(?:cria|criar|adiciona|adicionar|regista|registar|guarda|guardar|anota|anotar)"
        if (Regex("^$verb\\s+(?:(?:uma?|o|a|na|no|em|minha|meu)\\s+)*(?:tarefa|tarefas)\\b").containsMatchIn(value)) return "tasks.create"
        if (Regex("^(?:agenda|agendar)\\s+.+").containsMatchIn(value) ||
            Regex("^(?:marca|marcar)\\s+(?:(?:uma?|o|a|na|no|em|minha|meu)\\s+)*(?:reuniao|evento|consulta|compromisso|encontro|lembrete)\\b").containsMatchIn(value) ||
            Regex("^$verb\\s+(?:(?:uma?|o|a|na|no|em|minha|meu)\\s+)*(?:evento|compromisso|reuniao|agenda)\\b").containsMatchIn(value)) return "events.create"
        if (Regex("^(?:memoriza|memorizar|lembra-te que)\\s+.+").containsMatchIn(value) ||
            Regex("^$verb\\s+(?:(?:uma?|o|a|na|no|em|minha|meu)\\s+)*memoria\\b").containsMatchIn(value)) return "memory.save"
        if (Regex("^$verb\\s+(?:(?:uma?|o|a|na|no|em|minha|meu)\\s+)*diario\\b").containsMatchIn(value)) return "journal.create"
        if (Regex("^$verb\\s+(?:(?:uma?|o|a|na|no|em|minha|meu)\\s+)*(?:nota|notas|apontamento)\\b").containsMatchIn(value) ||
            Regex("^(?:anota|anotar|toma nota de|tira nota de)\\s+.+").containsMatchIn(value)) return "notes.create"
        return null
    }
    fun allowedTools(text: String): Set<String> {
        writeTool(text)?.let { return setOf(it) }
        ToolProtocol.routeRead(text)?.let { return setOf(it.tool) }
        val value = command(text)
        if (value.trimEnd('?', '.', '!') in setOf("qual e o meu nome", "qual o meu nome", "como me chamo", "o que sabes sobre mim")) return setOf("memory.list")
        val reading = Regex("^(?:quais?|quando|que|o que|mostra|mostrar|lista|listar|consulta|consultar|procura|procurar|pesquisa|pesquisar|busca|buscar|ve|ver)\\b")
        if (!reading.containsMatchIn(value)) return emptySet()
        return buildSet {
            if (Regex("\\b(?:minhas? tarefas?|tarefas? (?:tenho|pendentes))\\b").containsMatchIn(value)) add("tasks.list")
            if (Regex("\\b(?:minha agenda|meus? compromissos?|minhas? reunioes|proxim[oa] (?:reuniao|evento))\\b").containsMatchIn(value)) add("agenda.list")
            if (Regex("\\bminhas? memorias?\\b").containsMatchIn(value)) add("memory.list")
            if (Regex("\\b(?:minhas? notas?|meus? apontamentos?|nas notas|nos apontamentos)\\b").containsMatchIn(value)) add("notes.search")
            if (Regex("\\b(?:meu diario|no diario)\\b").containsMatchIn(value)) add("journal.search")
            if (Regex("\\b(?:meus? documentos?|materiais de estudo|na biblioteca|nos documentos)\\b").containsMatchIn(value)) add("study.search")
        }
    }
    fun permits(call: ToolCall, currentRequest: String): Boolean = call.tool in allowedTools(currentRequest)

    // Internal envelopes are not conversation text. Also recognizes legacy/interrupted envelopes.
    fun isProtocol(text: String): Boolean {
        val raw = text.trim()
        return Regex("\"(?:tool|arguments|parameters)\"\\s*:").containsMatchIn(raw) ||
            raw.startsWith("<tool_call>") || raw.startsWith("[TOOL_CALLS]") ||
            Regex("^proposta preparada\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(raw)
    }
    fun streamingText(text: String): String {
        val raw = text.trimStart()
        if (raw.startsWith("{") || raw.startsWith("<tool") || raw.startsWith("[TOOL") || raw.startsWith("```json") ||
            "```json".startsWith(raw) || isProtocol(text)) return ""
        return text
    }
}
