package pt.atlas.personal.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pt.atlas.personal.security.Preferences
import java.io.ByteArrayOutputStream
import java.io.File

data class Turn(val role: String, val content: String)
data class EngineStatus(val model: String? = null, val busy: Boolean = false)

class LocalEngine {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(EngineStatus())
    val status = mutable.asStateFlow()
    @Volatile private var linked = false
    private var modelBytes = 0L
    private fun link() { if (!linked) { System.loadLibrary("atlas"); linked = true } }
    private external fun nativeLoad(path: String)
    private external fun nativeUnload()
    private external fun nativeBegin(roles: Array<String>, contents: Array<String>, context: Int, maxTokens: Int, threads: Int)
    private external fun nativeNext(): ByteArray?
    private external fun nativeStop()
    private external fun nativeResetStop()
    private external fun nativeEnd()

    suspend fun load(file: File, preferences: Preferences, availableMemory: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            link()
            require(file.isFile) { "O modelo já não existe." }
            // Conservative admission estimate, not a guarantee against Android's low-memory killer.
            val estimate = file.length() + preferences.contextTokens * 196_608L + 192L * 1024 * 1024
            require(estimate <= preferences.memoryMb * 1024L * 1024 && estimate < availableMemory * 0.8) {
                "Memória estimada: ${estimate / 1048576} MB. Reduz o contexto ou escolhe um modelo menor."
            }
            mutable.value = EngineStatus(busy = true)
            try { nativeLoad(file.absolutePath); modelBytes = file.length(); mutable.value = EngineStatus(file.name) }
            catch (e: Throwable) { modelBytes = 0; mutable.value = EngineStatus(); throw e }
        }
    }
    suspend fun unload() = withContext(Dispatchers.IO) {
        stop()
        mutex.withLock { if (linked) nativeUnload(); modelBytes = 0; mutable.value = EngineStatus() }
    }
    fun stop() { if (linked) nativeStop() }

    fun generate(turns: List<Turn>, prefs: Preferences): Flow<String> = flow {
        mutex.withLock {
            check(mutable.value.model != null) { "Instala e carrega um modelo em Mais → Modelos de IA." }
            val estimate = modelBytes + prefs.contextTokens * 196_608L + 192L * 1024 * 1024
            require(estimate <= prefs.memoryMb * 1048576L) { "O contexto excede o orçamento de memória. Reduz o contexto ou aumenta o orçamento." }
            mutable.value = mutable.value.copy(busy = true)
            val bytes = ByteArrayOutputStream()
            try {
                currentCoroutineContext().ensureActive()
                nativeResetStop()
                nativeBegin(turns.map { it.role }.toTypedArray(), turns.map { it.content }.toTypedArray(),
                    prefs.contextTokens, prefs.maxTokens, Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                while (currentCoroutineContext().isActive) {
                    val part = nativeNext() ?: break
                    if (part.isNotEmpty()) { bytes.write(part); emit(bytes.toString(Charsets.UTF_8.name())) }
                    yield()
                }
            } finally { nativeEnd(); mutable.value = mutable.value.copy(busy = false) }
        }
    }.flowOn(Dispatchers.IO)
}
