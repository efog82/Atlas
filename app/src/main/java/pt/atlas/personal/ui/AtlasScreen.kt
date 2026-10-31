package pt.atlas.personal.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.atlas.personal.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasScreen(vm: AtlasViewModel, onLock: (Boolean) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val records by vm.records.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var editKind by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Record?>(null) }
    var deleting by remember { mutableStateOf<Record?>(null) }
    var viewing by remember { mutableStateOf<Record?>(null) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val titles = listOf("Início", "Atlas IA", "Agenda", "Diário", "Estudos", "Mais")
    val icons = listOf(Icons.Default.Home, Icons.Default.AutoAwesome, Icons.Default.CalendarMonth, Icons.Default.Book, Icons.Default.School, Icons.Default.MoreHoriz)
    LaunchedEffect(notice) { notice?.let { snack.showSnackbar(it); vm.clearNotice() } }
    fun add(kind: String, date: LocalDate? = null) { editing = null; editKind = kind; day = date }
    fun edit(record: Record) { editing = record; editKind = record.kind; day = null }
    Scaffold(
        topBar = { TopAppBar(title = { Column {
            Text("Atlas Personal", style = MaterialTheme.typography.titleLarge)
            Text("Versão ${pt.atlas.personal.BuildConfig.VERSION_NAME} · No teu dispositivo.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } }, actions = { Icon(Icons.Default.Shield, "Dados locais", Modifier.padding(16.dp)) }) },
        bottomBar = { NavigationBar {
            titles.forEachIndexed { index, title -> NavigationBarItem(selected = tab == index, onClick = { tab = index },
                icon = { Icon(icons[index], title) }, label = { Text(title, fontSize = 10.sp, maxLines = 1) }) }
        } },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (operation != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(operation.orEmpty(), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen(records, { tab = 1 }, { tab = 2 }, { add(Kind.TASK) }, { add(Kind.JOURNAL) }, { viewing = it }, vm::complete)
                    1 -> ChatScreen(vm)
                    2 -> AgendaScreen(records, { kind, date -> add(kind, date) }, { viewing = it }, ::edit, { deleting = it }, vm::complete)
                    3 -> JournalScreen(records, { add(Kind.JOURNAL) }, { viewing = it }, ::edit, { deleting = it })
                    4 -> StudyScreen(vm, records, { add(Kind.NOTE) }, { viewing = it }, ::edit, { deleting = it }, { tab = 1 })
                    5 -> MoreScreen(vm, records, onLock, { add(it) }, { viewing = it }, ::edit, { deleting = it })
                }
            }
        }
    }
    editKind?.let { kind -> RecordEditor(kind, editing, day, onDismiss = { editKind = null }, onSave = { record -> vm.save(record) { editKind = null } }) }
    deleting?.let { record -> ConfirmDialog("Eliminar ${kindLabel(record.kind).lowercase()}?", "“${record.title}” será eliminado permanentemente deste dispositivo." + if (record.recurrence != "NONE") " Todas as ocorrências desta série serão eliminadas." else "", "Eliminar",
        { deleting = null }, { vm.delete(record); deleting = null }) }
    viewing?.let { record ->
        AlertDialog(onDismissRequest = { viewing = null }, title = { Text(record.title) },
            text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${kindLabel(record.kind)} · ${formatDate(record.createdAt)}", style = MaterialTheme.typography.labelMedium)
                if (record.dueAt != null) Text("Data: ${formatDate(record.dueAt)}\n${recurrenceLabel(record.recurrence)}")
                if (record.endAt != null) Text("Fim: ${formatDate(record.endAt)}")
                if (record.category.isNotBlank()) Text(record.category)
                Text(record.body.ifBlank { "Sem texto adicional." })
                if (record.tags.isNotBlank()) Text("Etiquetas: ${record.tags}")
            } },
            dismissButton = { TextButton(onClick = { viewing = null }) { Text("Fechar") } },
            confirmButton = { if (record.kind != Kind.DOCUMENT) TextButton(onClick = { viewing = null; edit(records.firstOrNull { it.id == record.id } ?: record) }) { Text("Editar") } })
    }
}

@Composable
fun HomeScreen(records: List<Record>, onChat: () -> Unit, onAgenda: () -> Unit, onTask: () -> Unit,
    onJournal: () -> Unit, onOpen: (Record) -> Unit, onComplete: (Record) -> Unit) {
    val today = LocalDate.now()
    val tasks = records.filter { it.kind == Kind.TASK && !it.done }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
    val events = records.filter { it.kind == Kind.EVENT && RecordRules.occursOn(it, today) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Olá. Vamos organizar o dia?", today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-PT")))) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), onClick = onChat) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Text("Um pouco mais de clareza.", style = MaterialTheme.typography.headlineSmall)
                    Text("Conversa com a tua IA local, consulta a agenda ou prepara um plano para hoje.")
                    FilledTonalButton(onClick = onChat) { Text("Falar com o Atlas") }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedCard(onClick = onAgenda, modifier = Modifier.weight(1f)) { Column(Modifier.padding(18.dp)) {
                Text("${events.size}", style = MaterialTheme.typography.headlineLarge); Text("compromissos hoje")
            } }
            OutlinedCard(onClick = onAgenda, modifier = Modifier.weight(1f)) { Column(Modifier.padding(18.dp)) {
                Text("${tasks.size}", style = MaterialTheme.typography.headlineLarge); Text("tarefas por concluir")
            } }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onTask, label = { Text("Nova tarefa") }, leadingIcon = { Icon(Icons.Default.AddTask, null, Modifier.size(18.dp)) })
            AssistChip(onClick = onJournal, label = { Text("Escrever no diário") }, leadingIcon = { Icon(Icons.Default.EditNote, null, Modifier.size(18.dp)) })
        } }
        item { SectionTitle("Hoje na agenda", action = { TextButton(onClick = onAgenda) { Text("Ver tudo") } }) }
        if (events.isEmpty()) item { EmptyCard("Um dia com espaço", "Ainda não tens compromissos para hoje.") }
        items(events.take(3), key = { "event-${it.id}" }) { record -> Card(onClick = { onOpen(record) }) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(record.title, style = MaterialTheme.typography.titleMedium); Text(formatDate(RecordRules.nextOccurrence(record, today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1))) }
        } }
        item { SectionTitle("Próximos passos") }
        if (tasks.isEmpty()) item { EmptyCard("Tudo em dia", "Cria uma tarefa para guardar o teu próximo passo.") }
        items(tasks.take(4), key = { "task-${it.id}" }) { record -> Card(onClick = { onOpen(record) }) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(false, { onComplete(record) }); Column { Text(record.title); Text(formatDate(record.dueAt), style = MaterialTheme.typography.labelMedium) }
            }
        } }
    }
}

@Composable
fun JournalScreen(records: List<Record>, onAdd: () -> Unit, onOpen: (Record) -> Unit, onEdit: (Record) -> Unit, onDelete: (Record) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    val entries = records.filter { it.kind == Kind.JOURNAL }
    val filtered = entries.filter { (category.isBlank() || it.category == category) && listOf(it.title, it.body, it.tags).any { text -> text.contains(query, true) } }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("O teu diário", "Um espaço privado para pensar.") { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Novo registo") } } }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Pesquisar texto ou etiquetas") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("") + entries.map { it.category }.filter { it.isNotBlank() }.distinct()).forEach { value ->
                FilterChip(category == value, { category = value }, label = { Text(value.ifBlank { "Todas" }) })
            }
        } }
        item { Text("O diário não é guardado automaticamente como memória da IA.", style = MaterialTheme.typography.bodySmall) }
        if (filtered.isEmpty()) item { EmptyCard("Ainda sem registos", "Escreve uma reflexão ou ajusta a pesquisa.") }
        items(filtered, key = { it.id }) { record -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(formatDate(record.createdAt), style = MaterialTheme.typography.labelSmall)
            RecordCard(record, { onOpen(record) }, { onEdit(record) }, { onDelete(record) })
        } }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp)); Text("Atlas Personal", style = MaterialTheme.typography.headlineLarge)
        Text("Os teus dados estão neste dispositivo.")
        Spacer(Modifier.height(24.dp)); Button(onClick = onUnlock) { Text("Desbloquear") }
    } }
}
