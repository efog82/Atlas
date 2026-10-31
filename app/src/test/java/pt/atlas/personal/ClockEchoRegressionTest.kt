package pt.atlas.personal

import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.ai.*
import pt.atlas.personal.agent.*
import pt.atlas.personal.data.Message
import java.time.ZoneId

class ClockEchoRegressionTest {
    @Test fun plainMessagesContainExactlyWhatUserTyped() {
        for (text in listOf("Olá", "Qual é o teu nome?", "Explica a fotossíntese")) {
            assertEquals(text, historyTurn("user", text).content)
            assertNull(clockContextFor(text, 1793466000000, emptySet()))
        }
    }
    @Test fun normalPromptContainsNoTimeHeader() {
        val prompt = ToolProtocol.instructions()
        assertFalse(prompt.contains("Data/hora local deste pedido"))
        assertFalse(prompt.contains("Data local:"))
        assertFalse(prompt.contains("Referência temporal da aplicação"))
    }
    @Test fun temporalReferenceIsSeparateFromUserContent() {
        val text = "Que horas são?"
        val context = clockContextFor(text, 1793466000000, emptySet(), ZoneId.of("Europe/Lisbon"))!!
        val system = ToolProtocol.instructions(clockContext = context)
        assertTrue(system.contains("Referência temporal da aplicação"))
        assertEquals(text, historyTurn("user", text).content)
        assertFalse(historyTurn("user", text).content.contains("2026-10-31"))
    }
    @Test fun calendarActionsStillHaveAClockForRelativeDates() {
        assertNotNull(clockContextFor("Marca uma reunião daqui a uma hora", 1793466000000, setOf("events.create")))
        assertNull(clockContextFor("Procura nas minhas notas", 1793466000000, setOf("notes.search")))
    }
    @Test fun oldBracketedHeaderIsRemovedWithoutLosingAnswer() {
        val answer = "[Data/hora local deste pedido: 2026-10-31T17:04:06Z]\nChamo-me Atlas Personal."
        assertEquals("Chamo-me Atlas Personal.", AssistantText.clean(answer))
        assertEquals("Chamo-me Atlas Personal.", AssistantText.streaming(answer))
    }
    @Test fun unbracketedHeaderWithSpacesKeepsInlineReply() {
        assertEquals("Olá!", AssistantText.clean("Data/hora local deste pedido: 2026-10-31 T17:04 Olá!"))
        assertEquals("Olá!", AssistantText.clean("data/hora local deste pedido 2026-10-31 T17:04 Olá!"))
    }
    @Test fun partialHeaderNeverFlashesDuringStreaming() {
        for (text in listOf("[Data/", "Data/hora local deste pedido:", "[Data/hora local deste pedido: 2026-10-31T17:0", "[Data/hora local deste pedido: 2026-10-31T17:04:0", "data/hora local deste pedido 2026-10")) {
            assertEquals(text, "", AssistantText.streaming(text))
        }
    }
    @Test fun onlyMetadataDoesNotBecomeAnAssistantAnswer() {
        assertEquals("", AssistantText.clean("[Data/hora local deste pedido: 2026-10-31T17:04:06+00:00]"))
    }
    @Test fun legitimateDatesAndQuotedUserContentArePreserved() {
        val answer = "Hoje é 2026-10-31 e o evento começa às 17:04."
        assertEquals(answer, AssistantText.clean(answer))
        val question = "Porque repetes [Data/hora local deste pedido: 2026-10-31T17:04Z]?"
        assertEquals(question, historyTurn("user", question).content)
    }
    @Test fun existingConversationIsRepairedWithoutChangingStoredRows() {
        val user = Message(id = "question", conversationId = "c", role = "user", content = "Como te chamas?")
        val raw = "[Data/hora local deste pedido: 2026-10-31T17:04:06Z]\nSou o Atlas Personal."
        val old = Message(conversationId = "c", role = "assistant", content = raw)
        val next = Message(id = "next", conversationId = "c", role = "user", content = "Olá")
        val rows = listOf(user, old, next)
        val turns = ConversationContext.turns(rows, next.id)
        assertTrue(turns.any { it.role == "assistant" && it.content == "Sou o Atlas Personal." })
        assertFalse(turns.any { it.content.contains("Data/hora local deste pedido") })
        assertEquals("Sou o Atlas Personal.", ConversationContext.visibleMessages(rows)[1].content)
        assertEquals(raw, rows[1].content)
    }
}
