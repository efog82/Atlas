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
import org.json.JSONObject

data class EngineStatus(val model: String? = null, val busy: Boolean = false, val stopping: Boolean = false,
    val loadMs: Long = 0, val info: ModelInfo = ModelInfo(), val thinking: Boolean = false, val sampling: SamplingConfig = SamplingConfig(), val metrics: InferenceMetrics = InferenceMetrics(), val backend: String = "llama.cpp CPU")

class LocalEngine(private val context: android.content.Context? = null) {
    @Volatile private var liteRt: LiteRtEngine? = null
    private var liteRtContext = 0
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(EngineStatus())
    val status = mutable.asStateFlow()
    @Volatile private var linked = false
    @Volatile private var activeStop: AtomicBoolean? = null
    private var modelBytes = 0L
    private fun link() { if (!linked) { System.loadLibrary("atlas"); linked = true } }
    private external fun nativeLoad(path: String, allowChatml: Boolean)
    private external fun nativeUnload()
    private external fun nativeBegin(roles: Array<String>, contents: Array<String>, session: String, context: Int,
        maxTokens: Int, threads: Int, batchThreads: Int, batch: Int, microbatch: Int, thinking: Boolean,
        temperature: Float, topP: Float, topK: Int, minP: Float, repeatPenalty: Float): Boolean
    private external fun nativeModelInfo(): String
    private external fun nativeNext(): ByteArray?
    private external fun nativeStop()
    private external fun nativeResetStop()
    private external fun nativeEnd()
    private external fun nativeClearCache()
    private external fun nativeStats(): DoubleArray

    suspend fun load(file: File, preferences: Preferences, availableMemory: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(file.isFile) { "O modelo já não existe." }
            if (mutable.value.model == file.name) return@withLock
            if (file.extension.equals("litertlm", ignoreCase = true)) {
                val estimate = maxOf(1024L * 1048576, file.length() + 512L * 1048576)
                require(estimate <= preferences.memoryMb * 1048576L) {
                    "Para LiteRT-LM estima-se pelo menos ${estimate / 1048576} MB. Ajusta o orçamento de memória."
                }
                require(estimate < availableMemory * 0.8) {
                    "RAM disponível insuficiente para carregar este LiteRT-LM com margem de segurança."
                }
                ModelStore.validateLiteRtLm(file)
                if (linked) nativeUnload()
                liteRt?.shutdown(); liteRt = null
                mutable.value = EngineStatus(busy = true, backend = "LiteRT-LM CPU")
                val start = SystemClock.elapsedRealtime()
                try {
                    val next = LiteRtEngine(requireNotNull(context), file, preferences.contextTokens)
                    next.initialize()
                    liteRt = next
                    liteRtContext = preferences.contextTokens
                    modelBytes = file.length()
                    val info = ModelInfo("litertlm", file.name, "LiteRT-LM")
                    mutable.value = EngineStatus(file.name, loadMs = SystemClock.elapsedRealtime() - start,
                        info = info, sampling = SamplingConfig.automatic(info, false, preferences.factual),
                        backend = "LiteRT-LM CPU")
                } catch (e: Throwable) {
                    modelBytes = 0
                    mutable.value = EngineStatus()
                    throw IllegalStateException("LiteRT-LM não conseguiu carregar o modelo. Confirma que é um .litertlm genuíno e compatível. ${e.message}", e)
                }
                return@withLock
            }
            val estimate = file.length() + preferences.contextTokens * 196_608L + 192L * 1048576
            require(estimate <= preferences.memoryMb * 1048576L && estimate < availableMemory * 0.8) {
                "Verificação preventiva: ${estimate / 1048576} MB. Reduz o contexto ou escolhe um modelo menor."
            }
            link()
            liteRt?.shutdown(); liteRt = null
            val start = SystemClock.elapsedRealtime()
            mutable.value = EngineStatus(busy = true)
            try {
                nativeLoad(file.absolutePath, preferences.allowChatml); modelBytes = file.length()
                val meta = JSONObject(nativeModelInfo())
                val info = ModelInfo(meta.getString("architecture"), meta.getString("name"), meta.getString("template"),
                    meta.getBoolean("thinkingSwitch"), meta.getBoolean("reasoning"), meta.getBoolean("manualTemplate"), meta.getBoolean("thinkingForced"))
                mutable.value = EngineStatus(file.name, loadMs = SystemClock.elapsedRealtime() - start, info = info,
                    sampling = preferences.samplingOverrides[file.name] ?: SamplingConfig.automatic(info, info.effectiveThinking(preferences.thinking), preferences.factual))
            } catch (e: Throwable) { runCatching { nativeUnload() }; modelBytes = 0; mutable.value = EngineStatus(); throw e }
        }
    }
    suspend fun unload() = withContext(Dispatchers.IO) {
        stop()
        mutex.withLock { liteRt?.shutdown(); liteRt = null; if (linked) nativeUnload(); modelBytes = 0; mutable.value = EngineStatus() }
    }
    suspend fun clearCache() = withContext(Dispatchers.IO) { mutex.withLock { if (liteRt == null && linked) { nativeClearCache(); refresh() } } }
    fun stop() {
        liteRt?.stop()
        if (mutable.value.busy && liteRt != null) mutable.update { it.copy(stopping = true) }
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
            liteRt?.let { runtime ->
                require(prefs.contextTokens == liteRtContext) { "Recarrega o LiteRT-LM para alterar o contexto." }
                val info = mutable.value.info
                val sampling = prefs.samplingOverrides[mutable.value.model] ?: SamplingConfig.automatic(info, false, prefs.factual)
                sampling.validate()
                mutable.update { it.copy(busy = true, sampling = sampling, metrics = InferenceMetrics(), stopping = false) }
                try {
                    runtime.generate(turns, prefs, sampling) { metrics -> mutable.update { it.copy(metrics = metrics) } }
                        .collect { emit(it) }
                } finally { mutable.update { it.copy(busy = false, stopping = false) } }
                return@withLock
            }
            val estimate = modelBytes + prefs.contextTokens * 196_608L + 192L * 1048576
            require(estimate <= prefs.memoryMb * 1048576L) { "O contexto excede o orçamento de memória." }
            val config = InferenceConfig.from(prefs)
            val history = ChatHistory.prepare(turns, prefs.factual)
            val info = mutable.value.info
            val sampling = prefs.samplingOverrides[mutable.value.model] ?: SamplingConfig.automatic(info, info.effectiveThinking(prefs.thinking), prefs.factual)
            sampling.validate()
            val request = AtomicBoolean(false)
            activeStop = request
            mutable.update { it.copy(busy = true, stopping = false, sampling = sampling, metrics = InferenceMetrics()) }
            val bytes = ByteArrayOutputStream()
            var lastEmission = 0L
            var lastRefresh = 0L
            var emitted = ""
            try { withCancellationSignal(request) {
                currentCoroutineContext().ensureActive()
                nativeResetStop()
                // A Stop concurrent with reset cannot be lost: recheck the Kotlin request flag.
                if (request.get()) { nativeStop(); throw CancellationException("Interrompido") }
                currentCoroutineContext().ensureActive()
                val forcedOpen = nativeBegin(history.map { it.role }.toTypedArray(), history.map { it.content }.toTypedArray(), session,
                    prefs.contextTokens, prefs.maxTokens, config.threads, config.batchThreads, config.batch, config.microbatch,
                    prefs.thinking, sampling.temperature, sampling.topP, sampling.topK, sampling.minP, sampling.repeatPenalty)
                refresh()
                while (currentCoroutineContext().isActive && !request.get()) {
                    val part = nativeNext() ?: break
                    if (request.get()) break
                    val now = SystemClock.elapsedRealtime()
                    if (part.isNotEmpty()) bytes.write(part)
                    if (part.isNotEmpty() && (emitted.isEmpty() || now - lastEmission >= 80)) {
                        val view = ModelOutput.present(ModelOutput.decodeUtf8(bytes.toByteArray()), info.reasoning, forcedOpen)
                        mutable.update { it.copy(thinking = view.thinking) }
                        if (view.text != emitted) { emitted = view.text; emit(emitted) }
                        lastEmission = now
                    }
                    if (now - lastRefresh >= 100) { refresh(); lastRefresh = now }
                    yield()
                }
                if (request.get()) throw CancellationException("Interrompido")
                val view = ModelOutput.present(ModelOutput.decodeUtf8(bytes.toByteArray(), complete = true), info.reasoning, forcedOpen, complete = true)
                check(!view.thinking) { "O modelo terminou antes da resposta final. Aumenta o limite de resposta ou desativa o raciocínio, se o modelo permitir." }
                if (view.text != emitted) emit(view.text)
            } } finally {
                // No suspension: executes on the IO owner, even when the Job is cancelled.
                try { nativeEnd(); refresh() } finally {
                    activeStop = null
                    mutable.update { it.copy(busy = false, stopping = false, thinking = false) }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
