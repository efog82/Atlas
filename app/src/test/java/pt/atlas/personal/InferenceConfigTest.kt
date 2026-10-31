package pt.atlas.personal

import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.ai.*
import pt.atlas.personal.security.Preferences

class InferenceConfigTest {
    @Test fun profilesRespectPhysicalAndLogicalBounds() {
        for (profile in InferenceProfile.entries) {
            val config = InferenceConfig.from(profile.applyTo(Preferences()), 8)
            config.validate()
            assertTrue(config.microbatch <= config.batch)
            assertEquals(profile.threads, config.threads)
        }
    }
    @Test fun threadCountsAreCappedByAvailableCores() {
        val p = Preferences(threads = 8, batchThreads = 8)
        assertEquals(2, InferenceConfig.from(p, 2).threads)
        assertEquals(2, InferenceConfig.from(p, 2).batchThreads)
    }
    @Test fun invalidMicrobatchAndThreadsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { InferenceConfig(0,2,64,16).validate() }
        assertThrows(IllegalArgumentException::class.java) { InferenceConfig(2,2,32,64).validate() }
    }
    @Test fun ratesUseMeasuredTimeAndSeparatePrefillFromGeneration() {
        val m = InferenceMetrics(generated = 10, generationMs = 2000.0, evaluated = 100, prefillMs = 500.0)
        assertEquals(5.0, m.tokensPerSecond, 0.001)
        assertEquals(200.0, m.prefillTokensPerSecond, 0.001)
        assertEquals(0.0, InferenceMetrics().tokensPerSecond, 0.0)
    }
    @Test fun systemPrefixDoesNotIncludeSubsecondClock() {
        val first = pt.atlas.personal.agent.ToolProtocol.instructions()
        assertFalse(first.contains("Data e hora locais:"))
        assertEquals(first, pt.atlas.personal.agent.ToolProtocol.instructions())
    }
    @Test fun persistedUserClockIsStableAndAssistantTokensAreNotModified() {
        val zone = java.time.ZoneId.of("Europe/Lisbon")
        val first = historyTurn("user", "Lembra-me de estudar", 1793448000000, zone)
        assertEquals(first, historyTurn("user", "Lembra-me de estudar", 1793448000000, zone))
        assertTrue(first.content.contains("Data/hora local deste pedido:"))
        assertEquals(Turn("assistant", "Olá"), historyTurn("assistant", "Olá", 1793448000000, zone))
    }
}
