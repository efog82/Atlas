package pt.atlas.personal.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Preferences(val theme: String = "system", val lock: Boolean = false,
    val contextTokens: Int = 2048, val maxTokens: Int = 384, val memoryMb: Int = 1536)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("atlas-settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(Preferences(prefs.getString("theme", "system") ?: "system",
        prefs.getBoolean("lock", false), prefs.getInt("context", 2048).coerceIn(512, 4096),
        prefs.getInt("tokens", 384).coerceIn(64, 1024), prefs.getInt("memory", 1536).coerceIn(512, 4096)))
    val state = mutable.asStateFlow()
    fun update(value: Preferences) {
        require(value.theme in setOf("system", "light", "dark"))
        require(value.contextTokens in 512..4096 && value.maxTokens in 64..1024 && value.memoryMb in 512..4096)
        prefs.edit().putString("theme", value.theme).putBoolean("lock", value.lock)
            .putInt("context", value.contextTokens).putInt("tokens", value.maxTokens).putInt("memory", value.memoryMb).apply()
        mutable.value = value
    }
}
