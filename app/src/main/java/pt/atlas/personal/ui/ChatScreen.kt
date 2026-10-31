package pt.atlas.personal.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.atlas.personal.data.Conversation
import pt.atlas.personal.agent.ToolProtocol
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext

private fun toolLabel(name: String): String = when (name) {
    "tasks.list" -> "tarefas"; "agenda.list" -> "agenda"; "memory.list" -> "memórias autorizadas"
    "notes.search" -> "apontamentos"; "journal.search" -> "diário"; "study.search" -> "materiais de estudo"
    "tasks.create" -> "nova tarefa"; "events.create" -> "novo evento"; "memory.save" -> "nova memória"
    else -> "dados locais"
}

private fun localResult(content: String): String {
    if (!content.startsWith("Dados locais consultados (")) return content
    return runCatching {
        val tool = content.substringAfter('(').substringBefore(')')
        val root = JSONObject(content.substringAfter(":\n"))
        val sources = root.optJSONArray("sources")
        if (sources != null) buildString {
            append("Citações recuperadas · texto literal\n\n")
            if (sources.length() == 0) append("Não foram encontrados materiais para esta pesquisa.")
            for (i in 0 until sources.length()) {
                val source = sources.getJSONObject(i)
                append(source.optString("document", source.optString("note", "Apontamento")))
                if (source.has("page")) append(" · página/secção ${source.getInt("page")}")
                append("\n“${source.getString("quote")}”\n\n")
            }
        } else buildString {
            append("Consulta local · ${toolLabel(tool)}\n\n")
            val records = root.getJSONArray("records")
            if (records.length() == 0) append("Sem registos nesta consulta.")
            for (i in 0 until records.length()) {
                val record = records.getJSONObject(i)
                append("• ${record.getString("title")}\n")
                if (!record.isNull("date")) append("${record.getString("date")}\n")
                val text = record.optString("text")
                if (text.isNotBlank()) append("“$text”\n")
                append('\n')
            }
            if (root.optBoolean("truncated")) append("Mostrados os primeiros 8 resultados. Consulta o separador correspondente para ver todos.")
        }
    }.getOrDefault("Consulta local concluída. Consulta os registos no separador correspondente.")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(vm: AtlasViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val current by vm.conversation.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var history by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    val scroll = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(messages.size, chat.proposal) {
        if (scroll.layoutInfo.totalItemsCount > 0) scroll.animateScrollToItem(scroll.layoutInfo.totalItemsCount - 1)
    }
    LaunchedEffect(chat.generating) {
        while (chat.generating && currentCoroutineContext().isActive) {
            delay(300)
            val total = scroll.layoutInfo.totalItemsCount
            val last = scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (total > 0 && last >= total - 2 && !scroll.isScrollInProgress) scroll.scrollToItem(total - 1)
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Atlas IA", style = MaterialTheme.typography.headlineSmall)
                Text(if (engine.model != null) "Local · ${engine.model}" else "Instala um modelo em Mais → Modelos", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            IconButton(onClick = { history = true }) { Icon(Icons.Default.History, "Conversas") }
            IconButton(onClick = { vm.selectConversation(null) }, enabled = !chat.generating) { Icon(Icons.Default.AddComment, "Nova conversa") }
        }
        if (engine.metrics.phase != 0 || engine.stopping) InferenceMetricsView(engine, compact = true)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (messages.isEmpty()) item {
                EmptyCard("Em que posso ajudar?", "Após carregar um modelo, as respostas são geradas neste dispositivo. As ferramentas consultam dados locais e pedem confirmação antes de guardar alterações.")
                Spacer(Modifier.height(12.dp))
                listOf("Quais são as minhas tarefas?", "Ajuda-me a organizar o dia.", "Atlas, lembra-me de estudar amanhã às 20h.").forEach { text ->
                    SuggestionChip(onClick = { input = text }, label = { Text(text) })
                }
            }
            items(messages, key = { it.id }) { message ->
                var expanded by remember(message.id) { mutableStateOf(false) }
                val system = message.role == "system"
                val user = message.role == "user"
                val display = remember(message.content, message.role) {
                    if (system) localResult(message.content)
                    else if (!user) {
                        val call = runCatching { ToolProtocol.parse(message.content) }.getOrNull()
                        if (call == null) message.content
                        else if (call.tool in ToolProtocol.writes) "Proposta preparada: ${toolLabel(call.tool)} · ${call.arguments["title"].orEmpty()}.\nUma proposta só é guardada após confirmação."
                        else "A consultar ${toolLabel(call.tool)} neste dispositivo."
                    } else message.content
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
                    Text(if (user) "Tu" else if (system) "Aplicação · dados e operações" else "Atlas · texto gerado localmente", style = MaterialTheme.typography.labelSmall)
                    Surface(shape = RoundedCornerShape(18.dp), color = if (user) MaterialTheme.colorScheme.primaryContainer else if (system) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.widthIn(max = 560.dp).combinedClickable(onClick = { expanded = !expanded }, onLongClick = { clipboard.setText(AnnotatedString(display)); vm.tell("Texto copiado.") })) {
                        Column(Modifier.padding(14.dp)) {
                            Text(if (!expanded && display.length > 1800) display.take(1800) + "\n… Toca para expandir" else display,
                                style = if (system) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (chat.generating) item {
                Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Atlas · a gerar localmente", style = MaterialTheme.typography.labelSmall)
                    if (chat.partial.isNotBlank()) Text(chat.partial) else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                } }
            }
            chat.proposal?.let { proposal -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Confirmar ${kindLabel(proposal.record.kind).lowercase()}", style = MaterialTheme.typography.titleLarge)
                        Text(proposal.explanation)
                        Text(proposal.record.title, style = MaterialTheme.typography.titleMedium)
                        if (proposal.record.body.isNotBlank()) Text(proposal.record.body)
                        if (proposal.record.dueAt != null) Text("Data local: ${formatDate(proposal.record.dueAt)}\nNotificação: sim")
                        if (proposal.record.endAt != null) Text("Fim: ${formatDate(proposal.record.endAt)}")
                        if (proposal.record.recurrence != "NONE") Text(recurrenceLabel(proposal.record.recurrence))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = vm::cancelProposal) { Text("Cancelar") }
                            Button(onClick = vm::confirmProposal) { Text("Confirmar e guardar") }
                        }
                    }
                }
            } }
        }
        if (chat.generating) OutlinedButton(onClick = vm::stop, enabled = !engine.stopping, modifier = Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Default.Stop, null); Text(if (engine.stopping) " A interromper…" else " Interromper resposta") }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), label = { Text("Escreve ao Atlas") }, maxLines = 5,
                enabled = !chat.generating && chat.proposal == null)
            IconButton(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank() && !chat.generating && chat.proposal == null) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
        }
    }
    if (history) AlertDialog(onDismissRequest = { history = false }, title = { Text("Conversas") },
        text = { LazyColumn(Modifier.heightIn(max = 420.dp)) {
            if (conversations.isEmpty()) item { Text("Ainda sem conversas.") }
            items(conversations, key = { it.id }) { c -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.selectConversation(c.id); history = false }, Modifier.weight(1f)) { Text((if (current == c.id) "• " else "") + c.title, maxLines = 2) }
                IconButton(onClick = { deleting = c }) { Icon(Icons.Default.DeleteOutline, "Eliminar conversa") }
            } }
        } }, confirmButton = { TextButton(onClick = { history = false }) { Text("Fechar") } })
    deleting?.let { c -> ConfirmDialog("Eliminar conversa?", "O histórico de “${c.title}” será apagado.", "Eliminar", { deleting = null }, { vm.deleteConversation(c.id); deleting = null }) }
}
