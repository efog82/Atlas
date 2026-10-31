package pt.atlas.personal.ai

import java.util.Locale

enum class ModelFamily { QWEN3, QWEN25, GEMMA3, GENERIC }
data class ModelInfo(val architecture: String = "", val name: String = "", val template: String = "", val thinkingSwitch: Boolean = false,
    val reasoning: Boolean = false, val manualTemplate: Boolean = false, val thinkingForced: Boolean = false) {
    fun effectiveThinking(requested: Boolean) = thinkingForced || (thinkingSwitch && requested)
    val family: ModelFamily get() {
        val arch = architecture.lowercase(Locale.ROOT)
        val label = name.lowercase(Locale.ROOT)
        return when {
            arch.startsWith("qwen3") -> ModelFamily.QWEN3
            arch.startsWith("gemma3") -> ModelFamily.GEMMA3
            arch == "qwen2" && ("qwen2.5" in label || "qwen2_5" in label) -> ModelFamily.QWEN25
            else -> ModelFamily.GENERIC
        }
    }
}

data class SamplingConfig(val temperature: Float = 0.7f, val topP: Float = 0.9f, val topK: Int = 40,
    val minP: Float = 0f, val repeatPenalty: Float = 1f) {
    fun validate() {
        require(temperature.isFinite() && temperature in 0f..2f && topP.isFinite() && topP in 0.01f..1f)
        require(topK in 1..200 && minP.isFinite() && minP in 0f..1f && repeatPenalty.isFinite() && repeatPenalty in 1f..1.5f)
    }
    companion object {
        fun recommended(info: ModelInfo, thinking: Boolean): SamplingConfig = when (info.family) {
            ModelFamily.QWEN3 -> if (thinking) SamplingConfig(0.6f, 0.95f, 20) else SamplingConfig(0.7f, 0.8f, 20)
            ModelFamily.GEMMA3 -> SamplingConfig(1f, 0.95f, 64)
            ModelFamily.QWEN25 -> SamplingConfig(0.7f, 0.8f, 20, repeatPenalty = 1.05f)
            ModelFamily.GENERIC -> SamplingConfig()
        }
    }
}

object ModelInstructions {
    const val language = "Responde em português, salvo se o utilizador pedir outro idioma. Mantém as citações no idioma original e identifica-as como citações."
    const val factual = "Antes de responder, verifica as premissas da pergunta. Se uma premissa for falsa, corrige-a em vez de a aceitar. Distingue factos, hipóteses e opiniões. Se não souberes ou não puderes confirmar um detalhe, admite a incerteza. Não inventes datas, nomes, números, fontes ou citações. Não afirmes ter consultado a Internet. Prefere uma resposta curta e rigorosa a detalhes especulativos."
}
