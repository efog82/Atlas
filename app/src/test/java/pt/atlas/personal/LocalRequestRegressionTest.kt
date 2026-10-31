package pt.atlas.personal

import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.agent.*
import pt.atlas.personal.data.Kind
import pt.atlas.personal.data.Record
import java.time.*

class LocalRequestRegressionTest {
    private val now = ZonedDateTime.of(2026, 10, 31, 18, 0, 0, 0, ZoneId.of("Europe/Lisbon"))
    @Test fun computerReminderPreservesActionAndUsesRealTaskProposal() {
        for (text in listOf("Cria um lembrete às 21h para eu ligar o computador", "Atlas, criar um lembrete ad 21h para eu ligar o computador", "Lembra-me de ligar o computador às 21:00")) {
            val result = LocalRequests.reminder(text, now) as ReminderResolution.Ready
            assertEquals("tasks.create", result.proposal.tool)
            assertEquals("ligar o computador", result.proposal.record.title)
            assertEquals(now.withHour(21).toInstant().toEpochMilli(), result.proposal.record.dueAt)
            assertTrue(result.proposal.record.reminder)
            assertEquals(setOf("tasks.create"), AgentPolicy.allowedTools(text))
        }
    }
    @Test fun missingPastInvalidAndAmbiguousTimesRequireClarification() {
        for (text in listOf("cria um lembrete para estudar", "cria um lembrete às 17h para estudar", "cria um lembrete às 25h para estudar", "cria um lembrete às 21h ou 22h para estudar")) {
            assertTrue(text, LocalRequests.reminder(text, now) is ReminderResolution.Clarify)
        }
    }
    @Test fun tomorrowReminderKeepsDateAndDoesNotBecomeGeneralChat() {
        val result = LocalRequests.reminder("Atlas, lembra-me de estudar amanhã às 20h.", now) as ReminderResolution.Ready
        assertEquals("estudar", result.proposal.record.title)
        assertEquals(now.plusDays(1).withHour(20).toInstant().toEpochMilli(), result.proposal.record.dueAt)
        assertNull(LocalRequests.reminder("Lembra-me de como funciona a fotossíntese", now))
    }
    @Test fun agendaIncludesConfirmedScheduledTasksAndNeverDiary() {
        val due = now.withHour(21).toInstant().toEpochMilli()
        val records = listOf(Record(kind = Kind.TASK, title = "ligar o computador", dueAt = due, reminder = true),
            Record(kind = Kind.JOURNAL, title = "Nome da Loja", body = "Nome do Item", createdAt = due))
        val read = LocalRequests.read("Podes verificar a minha agenda?", now.toLocalDate())!!
        assertEquals(Kind.EVENT, read.kind)
        val result = LocalReadFormatter.format(read, records, now)
        assertTrue(result.contains("21:00 — ligar o computador (lembrete)"))
        assertFalse(result.contains("Loja"))
        assertFalse(result.contains("Item"))
        assertTrue(LocalReadFormatter.format(read, emptyList(), now).contains("Não há eventos ou lembretes guardados"))
    }
    @Test fun diaryReadsOnlyPersistedMatchingEntries() {
        val read = LocalRequests.read("Mostra o meu diário sobre estudo", now.toLocalDate())!!
        val output = LocalReadFormatter.format(read, listOf(
            Record(kind = Kind.JOURNAL, title = "estudo", body = "Texto realmente guardado"),
            Record(kind = Kind.JOURNAL, title = "outro", body = "Não corresponde"),
            Record(kind = Kind.EVENT, title = "estudo", body = "evento privado")), now)
        assertTrue(output.contains("Texto realmente guardado"))
        assertFalse(output.contains("Não corresponde"))
        assertFalse(output.contains("evento privado"))
    }
    @Test fun ordinaryKnowledgeQuestionIsIsolatedFromOldActions() {
        assertTrue(NormalChat.standaloneQuestion("O que era o espiritismo?"))
        assertFalse(NormalChat.standaloneQuestion("Explica melhor"))
        assertFalse(NormalChat.standaloneQuestion("Como funciona isso?"))
        assertNull(LocalRequests.read("O que era o espiritismo?"))
        assertNull(AgentPolicy.writeTool("O que era o espiritismo?"))
        assertTrue(AgentPolicy.allowedTools("O que era o espiritismo?").isEmpty())
        assertTrue(NormalChat.genericNonAnswer("O que era o espiritismo?", "Como assistente, sou projetado para responder perguntas. Meu propósito principal é ajudar. Sinta-se à vontade para perguntar mais."))
        assertFalse(NormalChat.genericNonAnswer("O que é espiritismo?", "O espiritismo é uma doutrina que aborda a existência dos espíritos e a reencarnação."))
        assertTrue(NormalChat.genericNonAnswer("O que posso falar cntg", "Olá! Como posso te ajudar hoje?"))
        assertTrue(LocalRequests.capabilities("O que posso falar cntg?"))
    }
}
