package pt.atlas.personal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.atlas.personal.ai.*
import java.util.Locale

private fun number(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

@Composable
fun InferenceMetricsView(status: EngineStatus, compact: Boolean = false) {
    val m = status.metrics
    Column(Modifier.fillMaxWidth().padding(horizontal = if (compact) 18.dp else 0.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(if (status.stopping) "A interromper o processamento…" else m.phaseLabel, style = MaterialTheme.typography.labelMedium)
        if (m.promptTokens > 0) {
            Text("Pedido: ${m.evaluated} tokens processados · ${m.reused} reutilizados / ${m.promptTokens}", style = MaterialTheme.typography.labelSmall)
            Text("Preparação ${number(m.setupMs / 1000)} s · processamento ${number(m.prefillMs / 1000)} s · ${number(m.prefillTokensPerSecond)} tok/s", style = MaterialTheme.typography.labelSmall)
            Text("Geração ${number(m.generationMs / 1000)} s · ${m.generated} tokens · ${number(m.tokensPerSecond)} tok/s", style = MaterialTheme.typography.labelSmall)
            Text("Primeiro token: ${if (m.firstTokenMs < 0) "a aguardar" else number(m.firstTokenMs / 1000) + " s"} · total ${number(m.totalMs / 1000)} s", style = MaterialTheme.typography.labelSmall)
            if (m.stopMs >= 0) Text("Interrupção concluída em ${number(m.stopMs)} ms", style = MaterialTheme.typography.labelSmall)
            if (!compact) {
                Text("RAM estimada: ${m.estimatedBytes / 1048576} MB · KV ${m.kvTokens} tokens · contextos criados ${m.contextCreations}")
                if (status.backend == "llama.cpp CPU") Text("CPU ${m.threads}/${m.batchThreads} threads · lote ${m.batch}/${m.microbatch} · contexto ${m.contextTokens}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun PerformancePanel(vm: AtlasViewModel) {
    val prefs by vm.settings.collectAsStateWithLifecycle()
    val status by vm.engine.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val comparison by vm.benchmark.collectAsStateWithLifecycle()
    val idle = !chat.generating && operation == null && !status.busy
    val llama = status.backend == "llama.cpp CPU"
    var confirmBenchmark by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { if (it != null) vm.exportBenchmark(it) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Desempenho offline · CPU", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InferenceProfile.entries.forEach { profile ->
                FilterChip(prefs.profile == profile.name, { vm.app.settings.update(profile.applyTo(prefs)) },
                    label = { Text(profile.label) }, enabled = idle && llama)
            }
        }
        Text("Perfis iniciais para comparar no teu dispositivo. Rápido não garante maior velocidade; mais threads podem aquecer e competir entre si.", style = MaterialTheme.typography.bodySmall)
        Text("Threads de geração: ${prefs.threads}")
        Slider(prefs.threads.toFloat(), { vm.app.settings.update(prefs.copy(profile = "CUSTOM", threads = it.toInt().coerceIn(1,8))) },
            valueRange = 1f..8f, steps = 6, enabled = idle && llama)
        Text("Threads de processamento: ${prefs.batchThreads}")
        Slider(prefs.batchThreads.toFloat(), { vm.app.settings.update(prefs.copy(profile = "CUSTOM", batchThreads = it.toInt().coerceIn(1,8))) },
            valueRange = 1f..8f, steps = 6, enabled = idle && llama)
        Text("Lote lógico: ${prefs.batch}")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(32,64,128,256,512).forEach { size -> FilterChip(prefs.batch == size,
                { vm.app.settings.update(prefs.copy(profile = "CUSTOM", batch = size, microbatch = prefs.microbatch.coerceAtMost(size))) },
                label = { Text("$size") }, enabled = idle && llama) }
        }
        Text("Microlote físico: ${prefs.microbatch}")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(8,16,32,64,128).filter { it <= prefs.batch }.forEach { size -> FilterChip(prefs.microbatch == size,
                { vm.app.settings.update(prefs.copy(profile = "CUSTOM", microbatch = size)) }, label = { Text("$size") }, enabled = idle && llama) }
        }
        Text("Alterar só threads mantém o contexto e a KV cache. Contexto ou lotes diferentes exigem uma nova alocação no próximo pedido. Os valores efetivos são limitados pelos núcleos disponíveis.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Carregar automaticamente o último modelo", Modifier.weight(1f))
            Switch(prefs.autoLoad, { vm.app.settings.update(prefs.copy(autoLoad = it)) })
        }
        if (status.model != null) Text("Último carregamento: ${number(status.loadMs / 1000.0)} s", style = MaterialTheme.typography.labelSmall)
        InferenceMetricsView(status)
        if (!llama) Text("MediaPipe CPU: os controlos de threads, lotes e KV cache abaixo pertencem ao llama.cpp. O modelo mantém-se carregado, mas cada pedido tem uma sessão isolada. Velocidade final baseada na retokenização da resposta. RAM estimada com base no catálogo do modelo, não medida no aparelho.", style = MaterialTheme.typography.bodySmall)
        if (llama) Text("RAM é uma estimativa: tensores do modelo + KV FP16 calculada a partir do GGUF + margem de 192 MB. Não é RSS/PSS nem mede energia ou a RAM total da app.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = vm::clearInferenceCache, enabled = idle && llama && status.model != null) { Text("Limpar KV cache (manter histórico)") }
        OutlinedButton(onClick = { confirmBenchmark = true }, enabled = idle && llama && status.model != null) { Text("Comparar perfis neste dispositivo") }
        if (operation?.contains("pedido sem cache") == true || operation?.contains("continuação com cache") == true || operation?.contains("comparar perfis") == true) {
            Button(onClick = vm::stop) { Text("Interromper comparação") }
        }
        comparison.forEach { result ->
            val m = result.metrics
            OutlinedCard { Column(Modifier.padding(12.dp)) {
                Text("${result.profile} · ${if (result.warm) "continuação com cache" else "sem cache"}", style = MaterialTheme.typography.titleSmall)
                Text("${m.threads}/${m.batchThreads} threads · ${m.batch}/${m.microbatch} lote · ${m.generated} tokens", style = MaterialTheme.typography.labelSmall)
                Text("Primeiro token ${number(m.firstTokenMs / 1000)} s · geração ${number(m.tokensPerSecond)} tok/s")
                Text("Processamento ${number(m.prefillMs / 1000)} s · KV reutilizada ${m.reused} · estado térmico ${result.thermalStatus}", style = MaterialTheme.typography.labelSmall)
            } }
        }
        if (comparison.isNotEmpty()) TextButton(onClick = { export.launch("atlas-desempenho.json") }, enabled = idle && llama) { Text("Exportar resultados medidos (JSON)") }
        Text("Vulkan/Mali-G76: não validado neste firmware. Este APK mantém GPU desativada e executa por CPU.", style = MaterialTheme.typography.bodySmall)
    }
    if (confirmBenchmark) ConfirmDialog("Comparar os três perfis?", "Executa seis pedidos sintéticos locais: um sem cache e uma continuação por perfil. Usa contexto 1024 e até 64 tokens de saída para todos. Pode demorar, consumir bateria e aquecer. Limpa a KV cache, mantendo conversas e modelo. Não mede energia e a ordem/temperatura podem influenciar os resultados.",
        "Iniciar comparação", { confirmBenchmark = false }, { confirmBenchmark = false; vm.compareProfiles() })
}
