package pt.atlas.personal.ai

import android.content.Context
import android.os.SystemClock
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import pt.atlas.personal.security.Preferences
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owned by LocalEngine's IO mutex. Never closes a native session while a callback is active. */
class MediaPipeEngine(context: Context, private val file: File, private val capacity: Int) : AutoCloseable {
    private val engine = LlmInference.createFromOptions(context,
        LlmInference.LlmInferenceOptions.builder().setModelPath(file.absolutePath)
            .setMaxTokens(capacity).setMaxTopK(200).setPreferredBackend(LlmInference.Backend.CPU).build())
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var active: LlmInferenceSession? = null
    @Volatile private var running = false

    fun stop() {
        stopRequested.set(true)
        if (running) active?.let { runCatching { it.cancelGenerateResponseAsync() } }
    }
    override fun close() { check(!running); engine.close() }

    fun generate(turns: List<Turn>, prefs: Preferences, sampling: SamplingConfig,
        updateMetrics: (InferenceMetrics) -> Unit) = flow {
        stopRequested.set(false)
        currentCoroutineContext().ensureActive()
        // Only Gemma 3 is supported in this first .task implementation. Do not guess Qwen templates.
        require(Regex("gemma-?3[-_]1b", RegexOption.IGNORE_CASE).containsMatchIn(file.name)) {
            "Este motor .task suporta Gemma 3 1B. Usa o ficheiro indicado no ecrã Modelos, mantendo o nome original. Para outros modelos, usa GGUF."
        }
        require(sampling.minP == 0f && sampling.repeatPenalty == 1f) {
            "MediaPipe não suporta min-p nem penalização de repetição. Desativa a amostragem personalizada ou usa min-p=0 e repetição=1."
        }
        val started = SystemClock.elapsedRealtime()
        val session = LlmInferenceSession.createFromOptions(engine,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(sampling.temperature).setTopK(sampling.topK).setTopP(sampling.topP)
                // Disable bundle turn wrappers: render the full, correctly separated Gemma chat exactly once.
                .setPromptTemplates(PromptTemplates.builder().build()).build())
        active = session
        val chunks = Channel<Pair<String, Boolean>>(Channel.UNLIMITED)
        var future: ListenableFuture<String>? = null
        var metrics = InferenceMetrics(contextTokens = capacity, estimatedBytes = maxOf(2L * 1024 * 1048576, file.length() + 512L * 1048576))
        var interrupted = false
        var failed = false
        var first = -1L
        var promptCount = 0
        var response = ""
        try {
            val prepared = ChatHistory.prepare(turns, prefs.factual).toMutableList()
            var prompt: String
            while (true) {
                currentCoroutineContext().ensureActive()
                if (stopRequested.get()) throw CancellationException("Interrompido")
                prompt = MediaPipePrompt.render(prepared)
                promptCount = session.sizeInTokens(prompt)
                if (promptCount + prefs.maxTokens.coerceAtMost(capacity / 2) < capacity) break
                if (prepared.size <= 2) error("O pedido e as fontes excedem o contexto MediaPipe. Reduz o texto ou usa um GGUF com contexto maior.")
                // Keep system policy, latest question and complete user/assistant pairs.
                prepared.removeAt(1)
                if (prepared.getOrNull(1)?.role == "assistant") prepared.removeAt(1)
            }
            metrics = metrics.copy(phase = 1, promptTokens = promptCount, evaluated = promptCount)
            updateMetrics(metrics)
            session.addQueryChunk(prompt)
            currentCoroutineContext().ensureActive()
            if (stopRequested.get()) throw CancellationException("Interrompido")
            running = true
            val pending = session.generateResponseAsync { part, done ->
                chunks.trySend(part to done)
                if (done) chunks.close()
            }
            future = pending
            if (stopRequested.get()) session.cancelGenerateResponseAsync()
            // Also forward native future failures, which do not necessarily deliver done=true.
            pending.addListener({
                try { pending.get() } catch (e: Throwable) { chunks.close(e.cause ?: e) }
            }, Executor { it.run() })
            val output = StringBuilder()
            for ((part, done) in chunks) {
                currentCoroutineContext().ensureActive()
                if (stopRequested.get()) throw CancellationException("Interrompido")
                if (part.isNotEmpty() && first < 0) first = SystemClock.elapsedRealtime()
                output.append(part)
                response = ModelOutput.present(output.toString(), false, complete = done).text
                if (response.isNotBlank()) emit(response)
                val now = SystemClock.elapsedRealtime()
                metrics = metrics.copy(phase = 2, firstTokenMs = if (first < 0) -1.0 else (first - started).toDouble(),
                    prefillMs = if (first < 0) (now - started).toDouble() else (first - started).toDouble(),
                    generationMs = if (first < 0) 0.0 else (now - first).toDouble(), totalMs = (now - started).toDouble())
                updateMetrics(metrics)
                if (done) break
            }
        } catch (e: CancellationException) {
            interrupted = true
            throw e
        } catch (e: Throwable) {
            failed = true
            throw e
        } finally {
            // Cancellation of a coroutine/future alone does not stop MediaPipe's native inference.
            // Request its native cancellation, then await completion before deleting the session.
            withContext(NonCancellable) {
                val pending = future
                var completionError: Throwable? = null
                if (pending != null) {
                    if (interrupted || stopRequested.get()) runCatching { session.cancelGenerateResponseAsync() }
                    try { awaitFinished(pending) } catch (e: Throwable) { completionError = e; failed = true } finally { running = false }
                }
                try {
                    val count = if (response.isBlank()) 0 else runCatching { session.sizeInTokens(response) }.getOrDefault(0)
                    val now = SystemClock.elapsedRealtime()
                    metrics = metrics.copy(phase = if (interrupted || stopRequested.get()) 4 else if (failed) 5 else 3,
                        generated = count, totalMs = (now - started).toDouble(),
                        generationMs = if (first < 0) 0.0 else (now - first).toDouble())
                    updateMetrics(metrics)
                } finally { active = null; session.close(); chunks.close() }
                if (completionError != null && !interrupted && !stopRequested.get()) throw completionError
            }
        }
    }

    private suspend fun awaitFinished(future: ListenableFuture<String>) = suspendCancellableCoroutine<Unit> { continuation ->
        future.addListener({
            try { future.get(); continuation.resume(Unit) }
            catch (e: Throwable) { continuation.resumeWithException(e.cause ?: e) }
        }, Executor { it.run() })
    }
}
