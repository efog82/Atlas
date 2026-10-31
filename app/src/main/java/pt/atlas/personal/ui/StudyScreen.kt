package pt.atlas.personal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.atlas.personal.data.*

@Composable
fun StudyScreen(vm: AtlasViewModel, records: List<Record>, onAdd: () -> Unit, onOpen: (Record) -> Unit,
    onEdit: (Record) -> Unit, onDelete: (Record) -> Unit, onChat: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf<Record?>(null) }
    val sections by vm.sources.collectAsStateWithLifecycle()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importDocument(uri) }
    val notes = records.filter { it.kind == if (tab == 0) Kind.NOTE else Kind.DOCUMENT }
    val filtered = notes.filter { (topic.isBlank() || it.category == topic) && (it.title.contains(query, true) || it.body.contains(query, true) || it.tags.contains(query, true)) }
    LaunchedEffect(query, tab) { if (tab == 1) vm.searchSources(query) else vm.clearSources() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Espaço de estudo", "Aprende ao teu ritmo.") {
            IconButton(onClick = { if (tab == 0) onAdd() else importer.launch(arrayOf("text/plain", "application/pdf")) }) {
                Icon(if (tab == 0) Icons.Default.Add else Icons.Default.FileOpen, if (tab == 0) "Novo apontamento" else "Importar documento")
            }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0; topic = "" }, label = { Text("Apontamentos") })
            FilterChip(tab == 1, { tab = 1; topic = "" }, label = { Text("Biblioteca") })
        } }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(if (tab == 0) "Pesquisar apontamentos" else "Pesquisar títulos e texto importado") }, singleLine = true) }
        if (tab == 0) item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("") + notes.map { it.category }.filter { it.isNotBlank() }.distinct()).forEach { value ->
                FilterChip(topic == value, { topic = value }, label = { Text(value.ifBlank { "Todos os temas" }) })
            }
        } }
        if (tab == 1) item { Text("TXT UTF-8 e PDF com texto selecionável · até 20 MB / 300 páginas. Texto extraído guardado localmente, sem OCR. A IA recebe excertos de até três páginas/secções para respeitar o contexto.", style = MaterialTheme.typography.bodySmall) }
        if (filtered.isEmpty() && sections.isEmpty()) item { EmptyCard("Prepara o teu material", "Cria um apontamento ou importa um documento. Depois pede um resumo ou inicia uma revisão com a IA local.") }
        items(filtered, key = { it.id }) { record ->
            RecordCard(record, { if (record.kind == Kind.DOCUMENT) { vm.readDocument(record); reading = record } else onOpen(record) }, { onEdit(record) }, { onDelete(record) }, footer = {
                Text("Progresso: ${record.progress}%", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { record.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { vm.studyPrompt(record, false); onChat() }) { Text("Resumir excerto") }
                    TextButton(onClick = { vm.studyPrompt(record, true); onChat() }) { Text("Rever com IA") }
                    TextButton(onClick = { vm.progress(record) }) { Text("Estudei +10%") }
                }
            })
        }
        if (query.isNotBlank() && tab == 1) {
            item { Text("Citações recuperadas · texto literal", style = MaterialTheme.typography.titleMedium) }
            items(sections, key = { it.id }) { section ->
                val record = records.firstOrNull { it.id == section.documentId }
                OutlinedCard(onClick = { if (record != null) { reading = record; vm.readDocument(record) } }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${record?.title ?: "Documento"} · página/secção ${section.page}", style = MaterialTheme.typography.labelMedium)
                        Text(section.text.take(1200), maxLines = 12)
                    }
                }
            }
        }
    }
    reading?.let { record -> AlertDialog(onDismissRequest = { reading = null; vm.searchSources(query) }, title = { Text(record.title) },
        text = { LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(sections.filter { it.documentId == record.id }, key = { it.id }) { section -> Column {
                Text("Página/secção ${section.page} · texto literal", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.text.selection.SelectionContainer { Text(section.text) }
            } }
        } }, confirmButton = { TextButton(onClick = { reading = null; vm.searchSources(query) }) { Text("Fechar") } }) }
}
