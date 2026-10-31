package pt.atlas.personal.ai

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pt.atlas.personal.security.Preferences
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class Turn(val role: String, val content: String)
data class EngineStatus(val model: String? = null, val busy: Boolean = false, val stopping: Boolean = false,
    val loadMs: Long = 0, val metrics: InferenceMetrics = InferenceMetrics())

class LocalEngine {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(EngineStatus())
    val status = mutable.asStateFlow()
    @Volatile private var linked = false
    @Volatile private var activeStop: AtomicBoolean? = null
    private var modelBytes = 0L
    private fun link() { if (!linked) { System.loadLibrary("atlas"); linked = true } }
    private external fun nativeLoad(path: String)
    private external fun nativeUnload()
    private external fun nativeBegin(roles: Array<String>, contents: Array<String>, session: String, context: Int,
        maxTokens: Int, threads: Int, batchThreads: Int, batch: Int, microbatch: Int)
    private external fun nativeNext(): ByteArray?
    private external fun nativeStop()
    private external fun nativeResetStop()
    private external fun nativeEnd()
    private external fun nativeClearCache()
    private external fun nativeStats(): DoubleArray

    suspend fun load(file: File, preferences: Preferences, availableMemory: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            link()
            require(file.isFile) { "O modelo já não existe." }
            if (mutable.value.model == file.name) return@withLock
            val estimate = file.length() + preferences.contextTokens * 196_608L + 192L * 1048576
            require(estimate <= preferences.memoryMb * 1048576L && estimate < availableMemory * 0.8) {
                "Verificação preventiva: ${estimate / 1048576} MB. Reduz o contexto ou escolhe um modelo menor."
            }
            val start = SystemClock.elapsedRealtime()
            mutable.value = EngineStatus(busy = true)
            try {
                nativeLoad(file.absolutePath); modelBytes = file.length()
                mutable.value = EngineStatus(file.name, loadMs = SystemClock.elapsedRealtime() - start)
            } catch (e: Throwable) { modelBytes = 0; mutable.value = EngineStatus(); throw e }
        }
    }
    suspend fun unload() = withContext(Dispatchers.IO) {
        stop()
        mutex.withLock { if (linked) nativeUnload(); modelBytes = 0; mutable.value = EngineStatus() }
    }
    suspend fun clearCache() = withContext(Dispatchers.IO) { mutex.withLock { if (linked) nativeClearCache() } }
    fun stop() {
        activeStop?.let { request ->
            request.set(true)
            if (linked) nativeStop() // Atomic only: never waits for the inference mutex.
            mutable.update { it.copy(stopping = true) }
        }
    }
    private fun refresh() { mutable.update { it.copy(metrics = InferenceMetrics.from(nativeStats())) } }
    private suspend fun <T> withCancellationSignal(request: AtomicBoolean, block: suspend () -> T): T = coroutineScope {
        val finished = AtomicBoolean(false)
        // A coroutine cancelled by timeout/parent must also abort a currently blocking native decode.
        val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally {
                if (!finished.get()) { request.set(true); nativeStop() }
            }
        }
        try { block() } finally { finished.set(true); watcher.cancel() }
    }

    fun generate(turns: List<Turn>, prefs: Preferences, session: String = "default"): Flow<String> = flow {
        mutex.withLock {
            check(mutable.value.model != null) { "Instala e carrega um modelo em Mais → Modelos de IA." }
            val estimate = modelBytes + prefs.contextTokens * 196_608L + 192L * 1048576
            require(estimate <= prefs.memoryMb * 1048576L) { "O contexto excede o orçamento de memória." }
            val config = InferenceConfig.from(prefs)
            val request = AtomicBoolean(false)
            activeStop = request
            mutable.update { it.copy(busy = true, stopping = false, metrics = InferenceMetrics()) }
            val bytes = ByteArrayOutputStream()
            var lastEmission = 0L
            var lastRefresh = 0L
            var emitted = ""
            withCancellationSignal(request) { try {
                currentCoroutineContext().ensureActive()
                nativeResetStop()
                // A Stop concurrent with reset cannot be lost: recheck the Kotlin request flag.
                if (request.get()) { nativeStop(); throw CancellationException("Interrompido") }
                currentCoroutineContext().ensureActive()
                nativeBegin(turns.map { it.role }.toTypedArray(), turns.map { it.content }.toTypedArray(), session,
                    prefs.contextTokens, prefs.maxTokens, config.threads, config.batchThreads, config.batch, config.microbatch)
                refresh()
                while (currentCoroutineContext().isActive && !request.get()) {
                    val part = nativeNext() ?: break
                    if (request.get()) break
                    val now = SystemClock.elapsedRealtime()
                    if (part.isNotEmpty()) bytes.write(part)
                    if (part.isNotEmpty() && (emitted.isEmpty() || now - lastEmission >= 80)) {
                        emitted = bytes.toString(Charsets.UTF_8.name()); emit(emitted); lastEmission = now
                    }
                    if (now - lastRefresh >= 100) { refresh(); lastRefresh = now }
                    yield()
                }
                if (request.get()) throw CancellationException("Interrompido")
                val finalText = bytes.toString(Charsets.UTF_8.name())
                if (finalText != emitted) emit(finalText)
            } finally {
                // No suspension: executes on the IO owner, even when the Job is cancelled.
                nativeEnd(); refresh(); activeStop = null
                mutable.update { it.copy(busy = false, stopping = false) }
            } }
        }
    }.flowOn(Dispatchers.IO)
}
