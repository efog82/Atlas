package pt.atlas.personal

import android.app.ActivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import pt.atlas.personal.ai.*
import pt.atlas.personal.security.Preferences

@RunWith(AndroidJUnit4::class)
class CacheAndCancellationTest {
    private val app = ApplicationProvider.getApplicationContext<AtlasApp>()
    private val prefs = Preferences(contextTokens = 2048, maxTokens = 64, memoryMb = 4096)
    private suspend fun load() {
        val file = ModelStore(app).list().firstOrNull()
        Assume.assumeTrue("Importa um GGUF para executar os testes de inferência.", file != null)
        val memory = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        app.engine.load(file!!.file, prefs, memory.availMem)
        app.engine.clearCache()
    }
    @After fun cleanup() = runBlocking { app.engine.stop(); app.engine.unload() }
    @Test fun continuationReusesTokensAndContextButNewConversationDoesNot() = runBlocking {
        load()
        val turns = listOf(Turn("system", "Responde em português."), Turn("user", "Explica brevemente como estudar."))
        var answer = ""
        app.engine.generate(turns, prefs, "cache-test").collect { answer = it }
        val first = app.engine.status.value.metrics
        assertTrue(answer.isNotBlank())
        app.engine.generate(turns + Turn("assistant", answer) + Turn("user", "Dá outro conselho."), prefs, "cache-test").collect { }
        val second = app.engine.status.value.metrics
        assertEquals(first.contextCreations, second.contextCreations)
        assertTrue(second.reused > 0)
        assertTrue(second.evaluated < second.promptTokens)
        app.engine.generate(turns, prefs.copy(threads = 1), "different-conversation").collect { }
        val isolated = app.engine.status.value.metrics
        assertEquals(0, isolated.reused)
        assertEquals(first.contextCreations, isolated.contextCreations)
    }
    @Test fun stopDuringPrefillAndNextRequestRemainUsable() = runBlocking {
        load()
        val turns = listOf(Turn("system", "Responde em português."), Turn("user", "Resume: " + "O estudo regular permite aprender com calma. ".repeat(100)))
        val job = launch(Dispatchers.Default) { app.engine.generate(turns, prefs, "stop-prefill").collect { } }
        withTimeout(30000) { app.engine.status.first { it.metrics.phase == 1 && it.busy } }
        app.engine.stop(); job.cancel()
        withTimeout(20000) { job.join() }
        assertFalse(app.engine.status.value.busy)
        assertEquals(0, app.engine.status.value.metrics.kvTokens)
        app.engine.generate(listOf(Turn("user", "Diz olá.")), prefs, "after-stop").collect { }
        assertTrue(app.engine.status.value.metrics.generated > 0)
    }
    @Test fun stopDuringDecodeAndTimeoutSignalDoNotLeaveEngineBusy() = runBlocking {
        load()
        val long = listOf(Turn("user", "Escreve uma lista numerada de cem ideias para estudar, sem parar."))
        val job = launch(Dispatchers.Default) { app.engine.generate(long, prefs.copy(maxTokens = 512), "stop-decode").collect { } }
        withTimeout(60000) { app.engine.status.first { it.metrics.generated > 0 && it.busy } }
        app.engine.stop(); job.cancel(); withTimeout(20000) { job.join() }
        assertFalse(app.engine.status.value.busy)
        try { withTimeout(100) { app.engine.generate(long, prefs, "timeout").collect { } } } catch (_: TimeoutCancellationException) { }
        assertFalse(app.engine.status.value.busy)
    }
}
