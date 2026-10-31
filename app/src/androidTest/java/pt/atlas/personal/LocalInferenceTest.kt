package pt.atlas.personal

import android.app.ActivityManager
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import pt.atlas.personal.ai.*
import pt.atlas.personal.security.Preferences

@RunWith(AndroidJUnit4::class)
class LocalInferenceTest {
    @Test fun appHasNoInternetPermission() {
        val app = ApplicationProvider.getApplicationContext<AtlasApp>()
        assertEquals(PackageManager.PERMISSION_DENIED, app.packageManager.checkPermission("android.permission.INTERNET", app.packageName))
    }
    @Test fun installedGgufProducesRealTextWithoutNetworkPermission() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AtlasApp>()
        val model = ModelStore(app).list().firstOrNull()
        Assume.assumeTrue("Importa um GGUF no dispositivo antes deste teste.", model != null)
        val memory = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val prefs = Preferences(contextTokens = 1024, maxTokens = 64, memoryMb = 2048)
        try {
            app.engine.load(model!!.file, prefs, memory.availMem)
            val output = withTimeout(180000) {
                app.engine.generate(listOf(Turn("system", "Responde em português."), Turn("user", "Diz olá numa frase curta.")), prefs).toList()
            }
            assertTrue("O motor deve emitir texto real.", output.lastOrNull()?.isNotBlank() == true)
            assertTrue(output.zipWithNext().all { (a, b) -> b.startsWith(a.dropLast(1)) })
        } finally { app.engine.stop(); app.engine.unload() }
    }
}
