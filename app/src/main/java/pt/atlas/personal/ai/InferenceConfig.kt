package pt.atlas.personal.ai

import pt.atlas.personal.security.Preferences

// Starting points for measurement, not claimed benchmark winners on a particular SoC.
enum class InferenceProfile(val label: String, val threads: Int, val batchThreads: Int,
    val batch: Int, val microbatch: Int, val context: Int, val output: Int) {
    BALANCED("Equilibrado", 2, 4, 128, 32, 2048, 192),
    FAST("Rápido", 4, 4, 256, 64, 2048, 256),
    ECO("Económico", 2, 2, 64, 16, 1024, 128);
    fun applyTo(p: Preferences) = p.copy(profile = name, threads = threads, batchThreads = batchThreads,
        batch = batch, microbatch = microbatch, contextTokens = context, maxTokens = output)
}

data class InferenceConfig(val threads: Int, val batchThreads: Int, val batch: Int, val microbatch: Int) {
    fun validate() {
        require(threads in 1..8 && batchThreads in 1..8) { "Threads: escolhe entre 1 e 8." }
        require(batch in setOf(32, 64, 128, 256, 512)) { "Lote inválido." }
        require(microbatch in setOf(8, 16, 32, 64, 128) && microbatch <= batch) { "O microlote deve ser menor ou igual ao lote." }
    }
    companion object {
        fun from(p: Preferences, cores: Int = Runtime.getRuntime().availableProcessors()): InferenceConfig {
            val ceiling = cores.coerceIn(1, 8)
            return InferenceConfig(p.threads.coerceAtMost(ceiling), p.batchThreads.coerceAtMost(ceiling), p.batch, p.microbatch).also { it.validate() }
        }
    }
}

data class InferenceMetrics(val phase: Int = 0, val promptTokens: Int = 0, val evaluated: Int = 0, val reused: Int = 0,
    val generated: Int = 0, val prefillMs: Double = 0.0, val generationMs: Double = 0.0, val firstTokenMs: Double = -1.0,
    val totalMs: Double = 0.0, val kvTokens: Int = 0, val estimatedBytes: Long = 0, val contextCreations: Int = 0,
    val contextTokens: Int = 0, val threads: Int = 0, val batchThreads: Int = 0, val batch: Int = 0, val microbatch: Int = 0,
    val setupMs: Double = 0.0, val stopMs: Double = -1.0) {
    val tokensPerSecond: Double get() = if (generationMs > 0) generated * 1000.0 / generationMs else 0.0
    val prefillTokensPerSecond: Double get() = if (prefillMs > 0) evaluated * 1000.0 / prefillMs else 0.0
    val phaseLabel: String get() = when (phase) { 1 -> "A processar pedido"; 2 -> "A gerar"; 3 -> "Concluído"; 4 -> "Interrompido"; 5 -> "Erro"; else -> "Pronto" }
    companion object {
        fun from(values: DoubleArray): InferenceMetrics {
            require(values.size == 19)
            return InferenceMetrics(values[0].toInt(), values[1].toInt(), values[2].toInt(), values[3].toInt(), values[4].toInt(),
                values[5], values[6], values[7], values[8], values[9].toInt(), values[10].toLong(), values[11].toInt(),
                values[12].toInt(), values[13].toInt(), values[14].toInt(), values[15].toInt(), values[16].toInt(), values[17], values[18])
        }
    }
}

data class BenchmarkResult(val profile: String, val warm: Boolean, val metrics: InferenceMetrics, val thermalStatus: Int)
