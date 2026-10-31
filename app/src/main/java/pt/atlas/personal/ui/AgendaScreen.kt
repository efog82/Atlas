package pt.atlas.personal.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.atlas.personal.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun AgendaScreen(records: List<Record>, onAdd: (String, LocalDate?) -> Unit, onOpen: (Record) -> Unit,
    onEdit: (Record) -> Unit, onDelete: (Record) -> Unit, onComplete: (Record) -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selected = LocalDate.parse(dateText)
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var showTasks by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val events = records.filter { it.kind in setOf(Kind.EVENT, Kind.TASK) && it.dueAt != null && !it.done }
    val tasks = records.filter { it.kind == Kind.TASK && (showDone || !it.done) }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
    val first = selected.withDayOfMonth(1)
    val gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekStart = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = if (mode == 1) (0L..6L).map { weekStart.plusDays(it) } else listOf(selected)
    val visibleEvents = days.flatMap { day ->
        val start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        events.filter { RecordRules.occursOn(it, day) }.map { record ->
            val occurrence = RecordRules.nextOccurrence(record.copy(done = false), start - 1)
            val duration = record.endAt?.let { it - requireNotNull(record.dueAt) }
            record.copy(dueAt = occurrence, endAt = if (occurrence != null && duration != null) occurrence + duration else null)
        }
    }.sortedBy { it.dueAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Agenda e tarefas", "Tempo para o que importa.") { IconButton(onClick = { onAdd(if (showTasks) Kind.TASK else Kind.EVENT, selected) }) { Icon(Icons.Default.Add, "Criar") } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!showTasks, { showTasks = false }, label = { Text("Agenda") })
            FilterChip(showTasks, { showTasks = true }, label = { Text("Tarefas") })
        } }
        if (showTasks) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Switch(showDone, { showDone = it }); Text("  Mostrar concluídas") } }
            if (tasks.isEmpty()) item { EmptyCard("Sem tarefas nesta vista", "Cria uma tarefa com ou sem data. Podes pedir ao Atlas um lembrete.") }
            items(tasks, key = { it.id }) { record -> RecordCard(record, { onOpen(record) }, { onEdit(record) }, { onDelete(record) }, { onComplete(record) }) }
        } else {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Mês", "Semana", "Dia").forEachIndexed { i, label -> FilterChip(mode == i, { mode = i }, label = { Text(label) }) }
            } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { dateText = when(mode) { 0 -> selected.minusMonths(1); 1 -> selected.minusWeeks(1); else -> selected.minusDays(1) }.toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Anterior")
                }
                Text(selected.format(DateTimeFormatter.ofPattern(if (mode == 0) "MMMM yyyy" else "d MMM yyyy", Locale.forLanguageTag("pt-PT"))), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { dateText = LocalDate.now().toString() }) { Text("Hoje") }
                IconButton(onClick = { dateText = when(mode) { 0 -> selected.plusMonths(1); 1 -> selected.plusWeeks(1); else -> selected.plusDays(1) }.toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Seguinte")
                }
            } }
            if (mode == 0 || mode == 1) item {
                OutlinedCard {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row { listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
                        repeat(if (mode == 0) 6 else 1) { row ->
                            Row {
                                repeat(7) { col ->
                                    val day = (if (mode == 0) gridStart else weekStart).plusDays((row * 7 + col).toLong())
                                    val count = events.count { RecordRules.occursOn(it, day) }
                                    Surface(onClick = { dateText = day.toString() }, modifier = Modifier.weight(1f).height(52.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (day == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        contentColor = if (day.month == selected.month || mode == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text(day.dayOfMonth.toString()); if (count > 0) Text("• $count", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Text(if (mode == 1) "Compromissos da semana" else "Compromissos de ${selected.dayOfMonth}/${selected.monthValue}", style = MaterialTheme.typography.titleMedium) }
            if (visibleEvents.isEmpty()) item { EmptyCard("Sem compromissos", "Usa + para criar um evento ou pede ajuda ao Atlas.") }
            items(visibleEvents, key = { "${it.id}-${it.dueAt}" }) { record ->
                val original = events.first { it.id == record.id }
                RecordCard(record, { onOpen(record) }, { onEdit(original) }, { onDelete(original) })
            }
            item { Text("As notificações exatas dependem da autorização Android. Sem ela, os lembretes usam WorkManager e podem atrasar-se.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
