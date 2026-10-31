package pt.atlas.personal.security

import android.content.Context
import org.json.JSONObject
import pt.atlas.personal.ai.SamplingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.atlas.personal.ai.InferenceConfig
import pt.atlas.personal.ai.InferenceProfile

data class Preferences(val theme: String = "system", val lock: Boolean = false,
    val contextTokens: Int = 2048, val maxTokens: Int = 192, val memoryMb: Int = 1536,
    val profile: String = "BALANCED", val threads: Int = 2, val batchThreads: Int = 4,
    val batch: Int = 128, val microbatch: Int = 32, val autoLoad: Boolean = true,
    val thinking: Boolean = false, val factual: Boolean = true, val allowChatml: Boolean = false,
    val samplingOverrides: Map<String, SamplingConfig> = emptyMap(),
    val onlineEnabled: Boolean = true, val autoPersonal: Boolean = true, val requireSources: Boolean = true, val refreshKnowledge: Boolean = true)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("atlas-settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(Preferences(prefs.getString("theme", "system") ?: "system",
        prefs.getBoolean("lock", false), prefs.getInt("context", 2048).coerceIn(512, 4096),
        prefs.getInt("tokens", 192).coerceIn(64, 1024), prefs.getInt("memory", 1536).coerceIn(512, 4096),
        prefs.getString("profile", "BALANCED") ?: "BALANCED", prefs.getInt("threads", 2).coerceIn(1, 8),
        prefs.getInt("batchThreads", 4).coerceIn(1, 8), prefs.getInt("batch", 128).takeIf { it in setOf(32,64,128,256,512) } ?: 128,
        prefs.getInt("microbatch", 32).takeIf { it in setOf(8,16,32,64,128) && it <= prefs.getInt("batch",128) } ?: 16,
        prefs.getBoolean("autoLoad", true), prefs.getBoolean("thinking", false), prefs.getBoolean("factual", true),
        prefs.getBoolean("allowChatml", false), readSampling(), prefs.getBoolean("onlineEnabled", true),
        prefs.getBoolean("autoPersonal", true), prefs.getBoolean("requireSources", true), prefs.getBoolean("refreshKnowledge", true)))
    private fun readSampling(): Map<String, SamplingConfig> = runCatching {
        val root = JSONObject(prefs.getString("samplingOverrides", "{}") ?: "{}")
        root.keys().asSequence().mapNotNull { key -> runCatching {
            val o = root.getJSONObject(key)
            key to SamplingConfig(o.getDouble("temperature").toFloat(), o.getDouble("topP").toFloat(), o.getInt("topK"),
                o.getDouble("minP").toFloat(), o.getDouble("repeatPenalty").toFloat()).also { it.validate() }
        }.getOrNull() }.toMap()
    }.getOrDefault(emptyMap())
    val state = mutable.asStateFlow()
    val lastModel: String? get() = prefs.getString("lastModel", null)
    fun rememberModel(name: String?) { prefs.edit().putString("lastModel", name).apply() }
    fun update(value: Preferences) {
        require(value.theme in setOf("system", "light", "dark"))
        require(value.contextTokens in 512..4096 && value.maxTokens in 64..1024 && value.memoryMb in 512..4096)
        require(value.profile in InferenceProfile.entries.map { it.name } + "CUSTOM")
        InferenceConfig(value.threads, value.batchThreads, value.batch, value.microbatch).validate()
        value.samplingOverrides.values.forEach { it.validate() }
        val overrides = JSONObject()
        value.samplingOverrides.forEach { (key, c) -> overrides.put(key, JSONObject().put("temperature", c.temperature.toDouble())
            .put("topP", c.topP.toDouble()).put("topK", c.topK).put("minP", c.minP.toDouble()).put("repeatPenalty", c.repeatPenalty.toDouble())) }
        prefs.edit().putBoolean("onlineEnabled", value.onlineEnabled).putBoolean("autoPersonal", value.autoPersonal)
            .putBoolean("requireSources", value.requireSources).putBoolean("refreshKnowledge", value.refreshKnowledge).putBoolean("thinking", value.thinking).putBoolean("factual", value.factual).putBoolean("allowChatml", value.allowChatml)
            .putString("samplingOverrides", overrides.toString()).putString("theme", value.theme).putBoolean("lock", value.lock)
            .putInt("context", value.contextTokens).putInt("tokens", value.maxTokens).putInt("memory", value.memoryMb)
            .putString("profile", value.profile).putInt("threads", value.threads).putInt("batchThreads", value.batchThreads)
            .putInt("batch", value.batch).putInt("microbatch", value.microbatch).putBoolean("autoLoad", value.autoLoad).apply()
        mutable.value = value
    }
}
