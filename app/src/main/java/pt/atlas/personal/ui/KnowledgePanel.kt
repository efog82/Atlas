package pt.atlas.personal.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.atlas.personal.data.*
import pt.atlas.personal.knowledge.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun sourceDate(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm"))

@Composable
fun EvidenceCards(rows: List<Pair<String, Evidence>>, onNotice: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Fontes consultadas · excertos literais", style = MaterialTheme.typography.titleSmall)
        Text("A explicação da IA pode conter erros. Compara-a com estes excertos; uma referência não prova que a interpretação esteja correta.", style = MaterialTheme.typography.bodySmall)
        rows.forEach { (label, source) -> OutlinedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("[$label] ${source.title}", style = MaterialTheme.typography.titleSmall)
                Text(source.location + if (source.private) " · só neste dispositivo" else " · Wikipédia", style = MaterialTheme.typography.labelSmall)
                Text("“${source.quote}”", style = MaterialTheme.typography.bodyMedium)
                source.fetchedAt?.let { Text("Guardado em ${sourceDate(it)}", style = MaterialTheme.typography.labelSmall) }
                source.revisionAt?.takeIf { it.isNotBlank() }?.let { Text("Revisão da fonte: $it", style = MaterialTheme.typography.labelSmall) }
                source.url?.takeIf(SourceMeta::validUrl)?.let { url -> TextButton(onClick = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    catch (_: Exception) { onNotice("Não foi possível abrir o navegador.") }
                }) { Text("Abrir revisão da fonte") } }
            }
        } }
    }
}

@Composable
fun PublicSearchDialog(vm: AtlasViewModel, onDismiss: () -> Unit, onSearch: () -> Unit = {}) {
    var topic by rememberSaveable { mutableStateOf("") }
    val prefs by vm.settings.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Pesquisar um tema público") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (prefs.onlineEnabled) "Procura primeiro na biblioteca local e pode enviar apenas o tema abaixo à Wikipédia. Não inclui histórico, agenda, diário ou memórias." else "Pesquisa online desligada: serão usadas apenas as fontes locais.")
            OutlinedTextField(topic, { topic = it }, label = { Text("Tema público, sem dados pessoais") }, singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { vm.searchPublic(topic); onDismiss(); onSearch() }, enabled = PublicQuery.fromPublicTopic(topic) != null) { Text("Pesquisar fontes") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun KnowledgePanel(vm: AtlasViewModel, records: List<Record>, onDelete: (Record) -> Unit, onChat: () -> Unit) {
    val prefs by vm.settings.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val sections by vm.sources.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<Record?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    val enabled = !chat.generating && operation == null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Conhecimento com fontes", "Online quando disponível; local sem Internet.")
        Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Usar Internet para perguntas públicas", Modifier.weight(1f))
                Switch(prefs.onlineEnabled, { vm.updateKnowledgeOptions(online = it) })
            }
            Text("Consulta a Wikipédia em português para perguntas gerais claras. Pedidos pessoais e excertos dos teus registos não são enviados. A pesquisa pode revelar o tema da pergunta ao fornecedor.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Consultar dados pessoais automaticamente", Modifier.weight(1f))
                Switch(prefs.autoPersonal, { vm.updateKnowledgeOptions(personal = it) })
            }
            Text("Quando relevantes, agenda, tarefas, diário e memórias autorizadas são consultados localmente. Nada é convertido em memória permanente sem confirmação.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Exigir fontes para perguntas factuais", Modifier.weight(1f))
                Switch(prefs.requireSources, { vm.updateKnowledgeOptions(strict = it) }, enabled = enabled)
            }
            Text("Sem excertos suficientes, o Atlas indica que não encontrou suporte. Desligar permite respostas apenas do modelo, com maior risco de invenção.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Atualizar fontes públicas por Wi-Fi", Modifier.weight(1f))
                Switch(prefs.refreshKnowledge, { vm.updateKnowledgeOptions(refresh = it) }, enabled = prefs.onlineEnabled)
            }
            Text("Verificação diária com bateria disponível; o Android pode adiar a execução. Atualiza só fontes públicas já guardadas.", style = MaterialTheme.typography.bodySmall)
        } }
        Button(onClick = { searching = true }, enabled = enabled) { Text("Pesquisar fontes") }
        OutlinedButton(onClick = vm::refreshKnowledge, enabled = enabled && prefs.onlineEnabled) { Text("Atualizar fontes guardadas agora") }
        Text("Os TXT/PDF importados em Estudos também são pesquisados automaticamente e continuam locais. As páginas da Wikipédia ficam disponíveis sem Internet; podem estar desatualizadas ou conter erros. Textos Wikipédia: CC BY-SA, com origem e revisão identificadas.", style = MaterialTheme.typography.bodySmall)
        val saved = records.filter { it.kind == Kind.SOURCE && (filter.isBlank() || it.title.contains(filter, true)) }
        OutlinedTextField(filter, { filter = it }, label = { Text("Filtrar fontes públicas guardadas") }, singleLine = true)
        if (saved.isEmpty()) EmptyCard("Biblioteca pública ainda vazia", "Pesquisa um tema com Internet para guardar fontes ou importa TXT/PDF em Estudos.")
        saved.take(30).forEach { source -> OutlinedCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(source.title, style = MaterialTheme.typography.titleMedium)
                SourceMeta.parse(source.body)?.let { Text("Guardado: ${sourceDate(it.fetchedAt)} · revisão ${it.revision}", style = MaterialTheme.typography.labelSmall) }
                Row {
                    TextButton(onClick = { vm.readDocument(source); reading = source }) { Text("Ler excertos offline") }
                    TextButton(onClick = { onDelete(source) }, enabled = enabled) { Text("Eliminar…") }
                }
            }
        } }
        if (saved.size > 30) Text("Mostradas 30 fontes. Usa o filtro para encontrar outras.")
    }
    if (searching) PublicSearchDialog(vm, { searching = false }, onChat)
    reading?.let { source -> AlertDialog(onDismissRequest = { reading = null }, title = { Text(source.title) },
        text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
            val excerpts = sections.filter { it.documentId == source.id }
            if (excerpts.isEmpty()) item { Text("A carregar excertos…") }
            items(excerpts.size) { index -> Text("Excerto ${excerpts[index].page}\n${excerpts[index].text}\n") }
        } }, confirmButton = { TextButton(onClick = { reading = null }) { Text("Fechar") } }) }
}
