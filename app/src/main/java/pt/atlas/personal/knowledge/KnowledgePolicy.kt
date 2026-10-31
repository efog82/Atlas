package pt.atlas.personal.knowledge

import java.text.Normalizer
import java.util.Locale

object KnowledgeWords {
    fun fold(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    private val stop = setOf("que", "qual", "quais", "como", "quando", "onde", "porque", "por", "para", "uma", "umas", "uns", "dos", "das", "com", "sem", "sobre", "este", "esta", "isto", "isso", "sao", "era", "ser", "tem", "tenho", "podes", "pode", "atlas", "explica", "explicar", "define", "significa", "meu", "minha", "meus", "minhas", "favor", "pesquisa", "procura", "verifica", "hoje", "amanha", "melhor", "mais", "detalhes", "foi", "diz", "saber", "queria", "gostaria", "poderias", "consegues")
    fun terms(text: String): List<String> = Regex("[\\p{L}]{3,}").findAll(text).map { it.value.lowercase(Locale.ROOT) }.filter { fold(it) !in stop }.distinctBy { fold(it) }.take(8).toList()
    fun stem(text: String): String {
        val value = fold(text)
        return when { value.length > 5 && value.endsWith("oes") -> value.dropLast(3) + "ao"; value.length > 4 && value.endsWith('s') -> value.dropLast(1); else -> value }
    }
}

object KnowledgeIntent {
    fun clock(text: String) = Regex("^(?:que horas sao|que horas e|qual e a hora(?: atual)?|qual (?:e )?a data(?: de hoje| atual)?|que dia e hoje|em que dia estamos)[?.! ]*$").matches(KnowledgeWords.fold(text).trim())
    fun liveData(text: String): Boolean {
        val value = KnowledgeWords.fold(text).trim().removePrefix("pesquisa publica: ")
        if (personal(text)) return false
        return Regex("\\b(?:noticias|noticia)\\b").containsMatchIn(value) ||
            Regex("^quanto (?:custa|vale)\\b").containsMatchIn(value) ||
            (Regex("\\b(?:tempo|temperatura|meteorologia|previsao|cotacao|bolsa|cambio|acoes|preco|resultado)\\b").containsMatchIn(value) &&
                Regex("\\b(?:hoje|agora|atual|atualmente|ultimas?|recentes?|amanha)\\b").containsMatchIn(value))
    }
    fun followUp(text: String) = Regex("^(?:explica melhor|mais detalhes|explica mais|e isso|e isto)[?.! ]*$").matches(KnowledgeWords.fold(text).trim())
    fun personal(text: String): Boolean = Regex("\\b(?:meu|minha|meus|minhas|mim|comigo|tenho|estou|sou|diario|memorias?|agenda|compromissos?|tarefas?|rotina|objetivos?|preferencias?|organizar|planear|planejar)\\b").containsMatchIn(KnowledgeWords.fold(text))
    fun planning(text: String) = Regex("\\b(?:organizar|planear|planejar|plano|rotina)\\b").containsMatchIn(KnowledgeWords.fold(text))
    fun factual(text: String): Boolean {
        val value = KnowledgeWords.fold(text).trim().removePrefix("atlas, ")
        if (value.trimEnd('?', '.', '!') in setOf("qual e o teu nome", "qual o teu nome", "como te chamas", "quem es tu")) return false
        return Regex("^(?:o que|quem|qual|quais|quando|onde|como funciona|porque|por que|explica|define|pesquisa|procura|resume)\\b").containsMatchIn(value) || (personal(text) && (planning(text) || text.trimEnd().endsWith("?")))
    }
}

/** Only a fresh public question can become a request. No API accepts history, sources or personal records. */
class PublicQuery private constructor(val text: String) {
    companion object {
        fun fromQuestion(question: String): PublicQuery? {
            val value = question.trim().removePrefix("Atlas, ").removePrefix("atlas, ")
            if (!Regex("^(?:o que|quem|qual|quais|quando|onde|como funciona|porque|por que|explica|define|pesquisa|procura)\\b").containsMatchIn(KnowledgeWords.fold(value))) return null
            return validate(value, explicit = false)
        }
        fun fromPublicTopic(topic: String): PublicQuery? = validate(topic.trim(), explicit = true)
        private fun validate(value: String, explicit: Boolean): PublicQuery? {
            if (value.length !in 3..180) return null
            val personal = if (explicit) Regex("\\b(?:meu|minha|meus|minhas|mim|comigo|tenho|estou|sou)\\b").containsMatchIn(KnowledgeWords.fold(value)) else KnowledgeIntent.personal(value)
            if (personal) return null
            if (Regex("\\b(?:tratamento|diagnostico|medicacao|sintomas|salario|morada|endereco|telefone|contacto|password|senha|nif|cpf|cartao|familia|esposa|filho|namorado|terapeuta)\\b").containsMatchIn(KnowledgeWords.fold(value))) return null
            if (value.any { it in "\n\r@{}<>\"“”" } || Regex("(?i)https?://|www\\.|\\d{4,}|\\+?\\d[\\d ()-]{7,}\\d").containsMatchIn(value)) return null
            val terms = KnowledgeWords.terms(value)
            if (terms.isEmpty()) return null
            return PublicQuery(terms.joinToString(" ").take(120))
        }
    }
}
