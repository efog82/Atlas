package pt.atlas.personal.ai

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import pt.atlas.personal.security.Preferences
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline LiteRT-LM backend. Engine and conversation are serialized on one owned thread;
 * the model lives in private app storage rather than in the APK.
 */
class LiteRtEngine(context: Context, private val file: File, private val capacity: Int) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { work ->
        Thread(work, "atlas-litert-lm").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var engine: Engine? = null
    @Volatile private var active: Conversation? = null
    private val stopping = AtomicBoolean(false)
    @Volatile private var closed = false
    private val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }

    suspend fun initialize() = withContext(dispatcher) {
        check(!closed)
        val instance = Engine(EngineConfig(
            modelPath = file.absolutePath,
            backend = Backend.CPU(),
            maxNumTokens = capacity,
            cacheDir = cacheDir.absolutePath
        ))
        // Never call close on an engine whose initialize failed.
        instance.initialize()
        engine = instance
    }

    fun stop() {
        stopping.set(true)
        active?.let { runCatching { it.cancelProcess() } }
    }

    fun generate(
        turns: List<Turn>,
        prefs: Preferences,
        sampling: SamplingConfig,
        updateMetrics: (InferenceMetrics) -> Unit
    ): Flow<String> = channelFlow {
        withContext(dispatcher) {
            check(!closed) { "LiteRT-LM encerrado." }
            val parent = checkNotNull(engine) { "LiteRT-LM ainda não está carregado." }
            stopping.set(false)
            require(sampling.minP == 0f && sampling.repeatPenalty == 1f) {
                "LiteRT-LM não suporta estes ajustes de min-p e repetição. Desativa a amostragem personalizada."
            }
            val prepared = ChatHistory.prepare(turns, prefs.factual)
            val system = prepared.firstOrNull { it.role == "system" }?.content.orEmpty()
            val messages = prepared.drop(1)
            require(messages.isNotEmpty() && messages.last().role == "user") { "Falta a pergunta." }
            // A new conversation for each request: no accidental carry-over across Atlas sessions.
            // Old turns come from Atlas's persisted history, with a conservative character budget.
            val kept = messages.toMutableList()
            while (kept.size > 1 && kept.sumOf { it.content.length } > capacity * 2) {
                kept.removeAt(0)
                if (kept.firstOrNull()?.role == "assistant") kept.removeAt(0)
            }
            val config = ConversationConfig(
                systemInstruction = Contents.of(system),
                initialMessages = kept.dropLast(1).map { turn ->
                    if (turn.role == "assistant") Message.model(turn.content) else Message.user(turn.content)
                },
                samplerConfig = SamplerConfig(
                    topK = sampling.topK,
                    topP = sampling.topP.toDouble(),
                    temperature = sampling.temperature.toDouble()
                )
            )
            val started = SystemClock.elapsedRealtime()
            var conversation: Conversation? = null
            var metrics = InferenceMetrics(
                contextTokens = capacity,
                estimatedBytes = maxOf(1024L * 1048576, file.length() + 512L * 1048576)
            )
            var firstToken = -1L
            val response = StringBuilder()
            try {
                conversation = parent.createConversation(config)
                active = conversation
                updateMetrics(metrics.copy(phase = 1))
                conversation.sendMessageAsync(kept.last().content).collect { chunk ->
                    ensureActive()
                    if (stopping.get()) throw CancellationException("Interrompido")
                    val text = chunk.toString()
                    if (text.isNotEmpty()) {
                        if (firstToken < 0) firstToken = SystemClock.elapsedRealtime()
                        response.append(text)
                        send(ModelOutput.present(response.toString(), false, complete = false).text)
                    }
                    val now = SystemClock.elapsedRealtime()
                    metrics = metrics.copy(
                        phase = 2,
                        firstTokenMs = if (firstToken < 0) -1.0 else (firstToken - started).toDouble(),
                        prefillMs = (if (firstToken < 0) now else firstToken) - started.toDouble(),
                        generationMs = if (firstToken < 0) 0.0 else (now - firstToken).toDouble(),
                        totalMs = (now - started).toDouble()
                    )
                    updateMetrics(metrics)
                }
                val finished = ModelOutput.present(response.toString(), false, complete = true).text
                send(finished)
                updateMetrics(metrics.copy(phase = 3, totalMs = (SystemClock.elapsedRealtime() - started).toDouble()))
            } catch (e: CancellationException) {
                conversation?.cancelProcess()
                updateMetrics(metrics.copy(phase = 4))
                throw e
            } catch (e: Throwable) {
                updateMetrics(metrics.copy(phase = 5))
                throw e
            } finally {
                active = null
                conversation?.close()
            }
        }
    }

    suspend fun shutdown() = withContext(dispatcher) {
        if (!closed) {
            closed = true
            active?.let { runCatching { it.cancelProcess() } }
            engine?.close()
            engine = null
        }
    }.also { dispatcher.close(); executor.shutdown() }

    override fun close() {
        // Called only from LocalEngine's serialized lifecycle, never during generation.
        runBlocking { shutdown() }
    }
}
