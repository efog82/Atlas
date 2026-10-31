package pt.atlas.personal.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pt.atlas.personal.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

val localDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
fun formatDate(time: Long?): String = time?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(localDateTimeFormat) } ?: "Sem data"
fun kindLabel(kind: String): String = when (kind) {
    Kind.TASK -> "Tarefa"; Kind.EVENT -> "Evento"; Kind.JOURNAL -> "Diário"; Kind.NOTE -> "Apontamento"
    Kind.MEMORY -> "Memória"; Kind.HABIT -> "Hábito"; else -> "Documento"
}

@Composable
fun EmptyCard(title: String, body: String) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
fun RecordCard(record: Record, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onComplete: (() -> Unit)? = null, footer: (@Composable () -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onComplete != null) Checkbox(checked = record.done, onCheckedChange = { onComplete() })
                Column(Modifier.weight(1f)) {
                    Text(record.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    if (record.category.isNotBlank()) Text(record.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opções") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onEdit() }, enabled = record.kind != Kind.DOCUMENT)
                        DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (record.body.isNotBlank() && record.kind != Kind.HABIT) Text(record.body, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
            if (record.dueAt != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Schedule, null, Modifier.size(16.dp))
                Text(formatDate(record.dueAt) + if (record.recurrence != "NONE") " · ${recurrenceLabel(record.recurrence)}" else "", style = MaterialTheme.typography.labelMedium)
            }
            if (record.tags.isNotBlank()) Text(record.tags.split(',').joinToString("  ") { "#${it.trim()}" }, style = MaterialTheme.typography.labelSmall)
            footer?.invoke()
        }
    }
}

fun recurrenceLabel(value: String): String = when (value) { "DAILY" -> "Diária"; "WEEKLY" -> "Semanal"; "MONTHLY" -> "Mensal"; else -> "Não repetir" }

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String = "Confirmar", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } })
}

@Composable
fun RecordEditor(kind: String, existing: Record?, defaultDay: LocalDate? = null, onDismiss: () -> Unit, onSave: (Record) -> Unit) {
    val context = LocalContext.current
    val key = existing?.id ?: kind
    var title by rememberSaveable(key) { mutableStateOf(existing?.title.orEmpty()) }
    var body by rememberSaveable(key) { mutableStateOf(existing?.body.orEmpty()) }
    var category by rememberSaveable(key) { mutableStateOf(existing?.category.orEmpty()) }
    var tags by rememberSaveable(key) { mutableStateOf(existing?.tags.orEmpty()) }
    var due by rememberSaveable(key) { mutableStateOf(existing?.dueAt?.let(::formatDate) ?: defaultDay?.atTime(20, 0)?.format(localDateTimeFormat).orEmpty()) }
    var end by rememberSaveable(key) { mutableStateOf(existing?.endAt?.let(::formatDate).orEmpty()) }
    var reminder by rememberSaveable(key) { mutableStateOf(existing?.reminder ?: false) }
    var recurrence by rememberSaveable(key) { mutableStateOf(existing?.recurrence ?: "NONE") }
    var error by remember { mutableStateOf<String?>(null) }
    val scheduled = kind in setOf(Kind.TASK, Kind.EVENT, Kind.HABIT)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp).heightIn(max = 760.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${if (existing == null) "Novo registo" else "Editar"} · ${kindLabel(kind)}", style = MaterialTheme.typography.titleLarge)
                if (existing?.recurrence != null && existing.recurrence != "NONE") Text("A alteração aplica-se à série recorrente completa.", style = MaterialTheme.typography.bodySmall)
                if (kind == Kind.MEMORY) Text("Só este conteúdo será guardado como memória permanente, com a tua autorização.")
                OutlinedTextField(title, { title = it.take(200) }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (kind != Kind.HABIT) OutlinedTextField(body, { body = it }, label = { Text("Texto") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 10)
                OutlinedTextField(category, { category = it.take(100) }, label = { Text("Categoria / tema") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(tags, { tags = it.take(500) }, label = { Text("Etiquetas, separadas por vírgulas") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (scheduled) {
                    OutlinedTextField(due, { due = it }, label = { Text("Data e hora · AAAA-MM-DD HH:MM") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row {
                        TextButton(onClick = {
                            val initial = runCatching { LocalDateTime.parse(due, localDateTimeFormat) }.getOrDefault(LocalDateTime.now())
                            DatePickerDialog(context, { _, year, month, day ->
                                TimePickerDialog(context, { _, hour, minute -> due = LocalDate.of(year, month + 1, day).atTime(hour, minute).format(localDateTimeFormat) }, initial.hour, initial.minute, true).show()
                            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                        }) { Icon(Icons.Default.EditCalendar, null); Text(" Escolher data") }
                        TextButton(onClick = { due = ""; end = ""; reminder = false; recurrence = "NONE" }) { Text("Limpar") }
                    }
                    if (kind == Kind.EVENT) OutlinedTextField(end, { end = it }, label = { Text("Fim (opcional) · AAAA-MM-DD HH:MM") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(reminder, { reminder = it }); Spacer(Modifier.width(10.dp)); Text("Notificação local")
                    }
                    Text("Repetição", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecordRules.recurrences.forEach { value -> FilterChip(recurrence == value, { recurrence = value }, label = { Text(recurrenceLabel(value)) }) }
                    }
                }
                if (kind == Kind.JOURNAL) Text("Data do registo: ${formatDate(existing?.createdAt ?: System.currentTimeMillis())}", style = MaterialTheme.typography.labelMedium)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(onClick = {
                        try {
                            fun parse(text: String): Long? = text.takeIf(String::isNotBlank)?.let {
                                val local = LocalDateTime.parse(it, localDateTimeFormat)
                                val zone = ZoneId.systemDefault()
                                require(zone.rules.getValidOffsets(local).isNotEmpty()) { "A hora selecionada não existe devido à mudança de hora." }
                                local.atZone(zone).toInstant().toEpochMilli()
                            }
                            val record = (existing ?: Record(kind = kind, title = title)).copy(title = title, body = body, category = category,
                                tags = tags, dueAt = if (scheduled) parse(due) else null, endAt = if (kind == Kind.EVENT) parse(end) else null,
                                recurrence = if (scheduled) recurrence else "NONE", reminder = scheduled && reminder)
                            require(record.recurrence == "NONE" || record.dueAt != null) { "Uma repetição precisa de data." }
                            if (record.reminder) require((RecordRules.nextOccurrence(record, System.currentTimeMillis()) ?: 0) > System.currentTimeMillis()) { "Escolhe uma data futura para o lembrete." }
                            RecordRules.validate(record); onSave(record)
                        } catch (e: Exception) { error = e.message ?: "Verifica os campos." }
                    }) { Text(if (kind == Kind.MEMORY) "Autorizar e guardar" else "Guardar") }
                }
            }
        }
    }
}

@Composable
fun PasswordDialog(restoring: Boolean, onDismiss: () -> Unit, onSubmit: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (restoring) "Restaurar cópia encriptada" else "Proteger cópia de segurança") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (restoring) "Esta operação substitui todos os registos e conversas atuais. Introduz a palavra-passe da cópia e confirma apenas se aceitas a substituição."
                else "A cópia inclui dados pessoais e conversas. Usa uma palavra-passe com pelo menos 10 caracteres. Sem ela não será possível recuperar os dados. Os modelos não estão incluídos.")
            OutlinedTextField(password, { password = it }, label = { Text("Palavra-passe") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            if (!restoring) OutlinedTextField(repeat, { repeat = it }, label = { Text("Repetir palavra-passe") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = { Button(enabled = password.length >= 10 && (restoring || password == repeat), onClick = {
            val chars = password.toCharArray(); password = ""; repeat = ""; onSubmit(chars)
        }) { Text(if (restoring) "Substituir e restaurar" else "Escolher destino") } })
}
