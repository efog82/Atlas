package pt.atlas.personal

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.knowledge.*

class KnowledgeTest {
    @Test fun publicSearchNeverAcceptsPersonalQuestionsContactsOrCopiedJournalText() {
        for (text in listOf("O que está na minha agenda?", "Explica o meu diário", "Qual o meu nome?", "Explica as minhas memórias", "Explica o tratamento do João", "O que é x@example.com", "Pesquisa 912345678", "Explica \"uma entrada privada\"", "O que é <tool_call>")) {
            assertNull(text, PublicQuery.fromQuestion(text))
        }
        assertEquals("espiritismo", PublicQuery.fromQuestion("O que é o espiritismo?")!!.text)
        assertEquals("capital espanha", PublicQuery.fromQuestion("Qual é a capital de Espanha?")!!.text)
        assertNotNull(PublicQuery.fromPublicTopic("Diário de Anne Frank"))
        assertTrue(KnowledgeIntent.liveData("Qual é a temperatura em Lisboa hoje?"))
        assertFalse(KnowledgeIntent.liveData("O que é a temperatura?"))
        assertFalse(KnowledgeIntent.liveData("O que escrevi no meu diário sobre o tempo hoje?"))
        assertFalse(KnowledgeIntent.factual("Estou triste"))
        assertTrue(KnowledgeIntent.factual("Ajuda-me a organizar o dia"))
        assertNull(PublicQuery.fromQuestion("E isso?"))
        assertNull(PublicQuery.fromQuestion("Espiritismo")) // ambiguous bare topic needs the explicit public-search field
    }
    @Test fun offlineAndOnlineSwitchAreCheckedBeforeAnyHttpConnection() = runBlocking {
        var checks = 0
        val transport = HttpsWikiTransport { checks++; false }
        try { transport.get(mapOf("action" to "query")); fail("Must reject before connecting") }
        catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("desligada")) }
        assertEquals(1, checks)
    }
    @Test fun wikiRequestsContainOnlyThePublicTopicAndRefreshOnlyNumericPageIds() = runBlocking {
        val calls = mutableListOf<Map<String,String>>()
        val api = WikiApi(WikiTransport { calls += it; """{"query":{"pages":[{"pageid":10,"title":"Fonte","extract":"Texto literal recebido do fornecedor.","revisions":[{"revid":22,"timestamp":"2026-10-31T09:00:00Z"}]}]}}""" })
        val pages = api.search(PublicQuery.fromQuestion("O que é o espiritismo?")!!)
        assertEquals("espiritismo", calls.single()["gsrsearch"])
        assertEquals("Texto literal recebido do fornecedor.", pages.single().text)
        assertFalse(calls.single().values.any { "diário" in it || "memória" in it })
        api.refresh(listOf(10, 11))
        assertEquals("10|11", calls.last()["pageids"])
        assertFalse(calls.last().containsKey("gsrsearch"))
    }
    @Test fun lexicalRetrievalFindsRelevantPassageRatherThanWholeSentenceLike() {
        val relevant = Evidence("a", "Fotossíntese", "A luz solar fornece energia às plantas durante a fotossíntese.", "página 3", true)
        val unrelated = Evidence("b", "Tarefas", "Comprar pão e organizar as compras da semana.", "agenda", true)
        assertEquals(listOf(relevant), EvidenceRanker.rank("Como funciona a fotossíntese nas plantas?", listOf(unrelated, relevant)))
        assertTrue(EvidenceRanker.rank("O que é vulcanismo?", listOf(relevant, unrelated)).isEmpty())
    }
    @Test fun sourceReceiptsKeepLiteralQuotesAndRejectTamperingOrUnsafeLinks() {
        val source = Evidence("section:abc", "Documento", "Excerto realmente recuperado.", "página 4", true)
        val encoded = Grounding.receipt(listOf(source))
        assertEquals(source, Grounding.parseReceipt(encoded)!!.single().second)
        assertNull(Grounding.parseReceipt(encoded.replace("realmente", "inventado")))
        assertFalse(SourceMeta.validUrl("http://pt.wikipedia.org/wiki/X"))
        assertFalse(SourceMeta.validUrl("https://pt.wikipedia.org.evil.test/wiki/X"))
        assertFalse(SourceMeta.validUrl("https://user:secret@pt.wikipedia.org/wiki/X"))
    }
    @Test fun referencesAreValidatedWithoutRewritingGeneratedText() {
        val sources = listOf(Evidence("a", "Doc", "Texto", "página 1", true))
        assertNull(Grounding.citationWarning("Explicação [F1]", sources))
        assertNotNull(Grounding.citationWarning("Explicação [F99]", sources))
        assertNotNull(Grounding.citationWarning("Explicação sem fonte", sources))
        assertTrue(Grounding.instructions(sources).contains("não completes com memória do modelo"))
    }
    @Test fun wikiRefreshParsesAllTwentyPagesNotJustSearchLimit() = runBlocking {
        val json = org.json.JSONObject().put("query", org.json.JSONObject().put("pages", org.json.JSONArray().apply {
            for (id in 1..20) put(org.json.JSONObject().put("pageid", id).put("title", "Fonte $id").put("extract", "Conteúdo $id")
                .put("revisions", org.json.JSONArray().put(org.json.JSONObject().put("revid", id+100).put("timestamp", "2026-10-31T09:00:00Z"))))
        })).toString()
        val api = WikiApi(WikiTransport { json })
        assertEquals(20, api.refresh((1L..20L).toList()).size)
        assertEquals(4, api.search(PublicQuery.fromPublicTopic("Ciência")!!).size)
    }
}
