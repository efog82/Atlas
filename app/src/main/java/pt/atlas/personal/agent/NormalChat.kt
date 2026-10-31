package pt.atlas.personal.agent

object NormalChat {
    // Also read by the real-GGUF smoke test. No tool examples in ordinary conversation.
    val system = """
        Chamas-te Atlas Personal. Responde em português, diretamente à pergunta do utilizador.
        Dá uma explicação concreta do assunto. Evita saudações e descrições genéricas da tua função.
        Se não souberes, admite-o. Não inventes registos pessoais nem afirmes ter guardado algo.
    """.trimIndent()

    fun standaloneQuestion(text: String): Boolean {
        val value = LocalRequests.normalized(text).trimEnd('?', '.', '!')
        if (Regex("^(?:explica|define|como funciona|o que (?:e|significa))\\s+(?:melhor|mais|isso|isto|aquilo|novamente|de novo|a tua resposta|o que disseste)\\b").containsMatchIn(value)) return false
        return Regex("^(?:o que (?:e|era|significa)|explica|define|como funciona|quem (?:e|foi))\\b").containsMatchIn(value)
    }

    fun genericNonAnswer(question: String, answer: String): Boolean {
        val q = LocalRequests.normalized(question)
        val a = LocalRequests.normalized(answer).trim(' ', '.', '!', '?')
        if (q in setOf("ola", "ola!", "oi", "bom dia", "boa tarde", "boa noite")) return false
        if (Regex("^(?:ola[,! ]+)?como (?:posso|te posso|posso te) ajudar(?: hoje)?$").matches(a)) return true
        val boilerplate = listOf("sou projetado para responder", "meu proposito principal", "sinta-se a vontade para perguntar", "como assistente, sou", "fornecer informacoes de forma precisa e util")
        return a.length < 1200 && boilerplate.count { it in a } >= 2
    }
}
