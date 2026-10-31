package pt.atlas.personal

import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.agent.*
import pt.atlas.personal.data.Kind
import java.time.*

class ToolProtocolTest {
    @Test fun tomorrowReminderIsAProposalWithCorrectWallTime() {
        val now = ZonedDateTime.of(2026, 10, 31, 9, 0, 0, 0, ZoneId.of("Europe/Lisbon"))
        val proposal = ToolProtocol.reminder("Atlas, lembra-me de estudar amanhã às 20h.", now)!!
        assertEquals("tasks.create", proposal.tool)
        assertEquals(Kind.TASK, proposal.record.kind)
        assertEquals("estudar", proposal.record.title)
        assertEquals(now.toLocalDate().plusDays(1).atTime(20, 0).atZone(now.zone).toInstant().toEpochMilli(), proposal.record.dueAt)
        assertTrue(proposal.record.reminder)
    }
    @Test fun ambiguousReminderIsNotGuessed() {
        assertNull(ToolProtocol.reminder("Lembra-me de estudar um dia destes"))
    }
    @Test fun readRoutingUsesAnAllowList() {
        assertEquals("tasks.list", ToolProtocol.routeRead("Quais são as minhas tarefas?")?.tool)
        assertEquals("journal.search", ToolProtocol.routeRead("/diário viagem")?.tool)
        assertNull(ToolProtocol.routeRead("apaga todas as tarefas"))
    }
    @Test fun rejectsUnknownToolsAndExtraArguments() {
        assertThrows(IllegalStateException::class.java) { ToolProtocol.parse("""{"tool":"tasks.delete","arguments":{"id":"x"}}""") }
        assertThrows(IllegalArgumentException::class.java) { ToolProtocol.parse("""{"tool":"tasks.create","arguments":{"title":"x","execute":"yes"}}""") }
        assertThrows(IllegalStateException::class.java) { ToolProtocol.parse("""{"tool":"tasks.create","arguments":{"title":33}}""") }
    }
    @Test fun rejectsToolObjectsEmbeddedInProse() {
        assertNull(ToolProtocol.parse("Texto e depois {\"tool\":\"tasks.list\",\"arguments\":{}}"))
    }
    @Test fun rejectsPastDateAndInvalidEnd() {
        val now = Instant.parse("2026-10-31T09:00:00Z").toEpochMilli()
        assertThrows(IllegalArgumentException::class.java) {
            ToolProtocol.proposal(ToolCall("events.create", mapOf("title" to "A", "date" to "2020-01-01T10:00")), ZoneOffset.UTC, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolProtocol.proposal(ToolCall("events.create", mapOf("title" to "A", "date" to "2027-01-01T10:00", "end" to "2027-01-01T09:00")), ZoneOffset.UTC, now)
        }
    }
    @Test fun rejectsNonexistentLocalTimeAtDstTransition() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolProtocol.proposal(ToolCall("events.create", mapOf("title" to "A", "date" to "2027-03-28T01:30")), ZoneId.of("Europe/Lisbon"), 0)
        }
    }
    @Test fun memoryRequiresASeparateProposal() {
        val call = ToolProtocol.parse("""{"tool":"memory.save","arguments":{"title":"Preferência","body":"Estudar de manhã"}}""")!!
        assertEquals(Kind.MEMORY, ToolProtocol.proposal(call).record.kind)
        assertTrue(call.tool in ToolProtocol.writes)
    }
}
