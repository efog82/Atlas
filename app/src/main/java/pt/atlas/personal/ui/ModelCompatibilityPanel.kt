package pt.atlas.personal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.atlas.personal.ai.SamplingConfig

@Composable
fun ModelCompatibilityPanel(vm: AtlasViewModel) {
    val prefs by vm.settings.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val enabled = !chat.generating && !engine.busy
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Compatibilidade e respostas", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Modo factual"); Text("Usa amostragem conservadora por família e pede correção de premissas e reconhecimento de incertezas. Mantém as fontes em Mais → Conhecimento. Não garante exatidão.", style = MaterialTheme.typography.bodySmall) }
            Switch(prefs.factual, { vm.app.settings.update(prefs.copy(factual = it)) }, enabled = enabled)
        }
        if (engine.model != null) {
            val info = engine.info
            Text("Motor: ${engine.backend}\nFamília: ${info.family} · ${info.architecture}\nTemplate: ${if (info.manualTemplate) "ChatML escolhido manualmente" else if (engine.backend == "MediaPipe CPU") "Gemma 3 / MediaPipe" else "Jinja do GGUF"}", style = MaterialTheme.typography.bodySmall)
            if (info.thinkingSwitch) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Raciocínio do modelo"); Text("Desligado: non-thinking. Ligado: aguarda a resposta final; pode precisar de 512–1024 tokens ou mais.", style = MaterialTheme.typography.bodySmall) }
                Switch(prefs.thinking, { vm.app.settings.update(prefs.copy(thinking = it)) }, enabled = enabled)
            } else if (info.reasoning) Text("Este template não permite alternar o raciocínio. O modo é determinado pelo próprio modelo.", style = MaterialTheme.typography.bodySmall)
            val key = requireNotNull(engine.model)
            val custom = prefs.samplingOverrides[key]
            val config = custom ?: SamplingConfig.automatic(info, info.effectiveThinking(prefs.thinking), prefs.factual)
            fun save(value: SamplingConfig) { vm.app.settings.update(prefs.copy(samplingOverrides = prefs.samplingOverrides + (key to value))) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Amostragem personalizada deste modelo", Modifier.weight(1f))
                Switch(custom != null, { if (it) save(config) else vm.app.settings.update(prefs.copy(samplingOverrides = prefs.samplingOverrides - key)) }, enabled = enabled)
            }
            Text("Temperatura ${config.temperature} · top-p ${config.topP} · top-k ${config.topK}\nmin-p ${config.minP} · repetição ${config.repeatPenalty}", style = MaterialTheme.typography.bodySmall)
            if (custom != null) {
                Text("Temperatura"); Slider(config.temperature, { save(config.copy(temperature = it)) }, valueRange = 0f..2f, enabled = enabled)
                Text("Top-p"); Slider(config.topP, { save(config.copy(topP = it)) }, valueRange = 0.01f..1f, enabled = enabled)
                Text("Top-k"); Slider(config.topK.toFloat(), { save(config.copy(topK = it.toInt())) }, valueRange = 1f..200f, enabled = enabled)
                Text("Min-p"); Slider(config.minP, { save(config.copy(minP = it)) }, valueRange = 0f..1f, enabled = enabled)
                Text("Penalização de repetição"); Slider(config.repeatPenalty, { save(config.copy(repeatPenalty = it)) }, valueRange = 1f..1.5f, enabled = enabled)
                Text("Para Qwen3, o fabricante desaconselha temperatura zero. Os valores automáticos distinguem thinking e non-thinking.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("ChatML para GGUF sem template"); Text("Só ativa se o autor do modelo indicar ChatML. Não substitui um template inválido. Recarrega o modelo após alterar.", style = MaterialTheme.typography.bodySmall) }
            Switch(prefs.allowChatml, { vm.app.settings.update(prefs.copy(allowChatml = it)) }, enabled = enabled)
        }
    }
}
