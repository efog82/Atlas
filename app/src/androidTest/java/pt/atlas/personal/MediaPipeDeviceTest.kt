package pt.atlas.personal

import android.app.ActivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.atlas.personal.ai.*
import pt.atlas.personal.security.Preferences

/** Requires a real ARM64 device and the .task imported through Atlas. A skip is NOT validation. */
@RunWith(AndroidJUnit4::class)
class MediaPipeDeviceTest {
    @Test fun taskStreamsCancelsAndCanGenerateAgainWithOnlineDisabled() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AtlasApp>()
        val model = ModelStore(app).list().firstOrNull { it.file.extension == "task" }
        assumeTrue("Importa o Gemma 3 1B .task antes deste teste.", model != null)
        val original = app.settings.state.value
        val memory = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val prefs = Preferences(contextTokens = 1024, maxTokens = 64, memoryMb = 2048, onlineEnabled = false)
        app.settings.update(original.copy(onlineEnabled = false))
        try {
            app.engine.unload()
            app.engine.load(model!!.file, prefs, memory.availMem)
            assertEquals("MediaPipe CPU", app.engine.status.value.backend)
            suspend fun hello(): String = withTimeout(180000) {
                app.engine.generate(listOf(Turn("user", "Diz olá em português numa frase.")), prefs).toList().lastOrNull().orEmpty()
            }
            assertTrue(hello().isNotBlank())
            val firstChunk = CompletableDeferred<Unit>()
            val generation = launch {
                app.engine.generate(listOf(Turn("user", "Escreve um texto longo sobre estudar com calma.")), prefs).collect {
                    if (it.isNotBlank()) firstChunk.complete(Unit)
                }
            }
            withTimeout(180000) { firstChunk.await() }
            app.engine.stop()
            withTimeout(45000) { generation.cancelAndJoin() }
            assertFalse(app.engine.status.value.busy)
            assertTrue(hello().isNotBlank())
            assertFalse(app.engine.status.value.busy)
        } finally {
            withContext(NonCancellable) { app.engine.stop(); app.engine.unload(); app.settings.update(original) }
        }
    }
}
