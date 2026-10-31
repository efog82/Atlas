package pt.atlas.personal.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.atlas.personal.ai.ModelStore
import pt.atlas.personal.data.*
import java.io.File
import java.time.LocalDate

@Composable
fun MoreScreen(vm: AtlasViewModel, records: List<Record>, onLock: (Boolean) -> Unit, onAdd: (String) -> Unit,
    onOpen: (Record) -> Unit, onEdit: (Record) -> Unit, onDelete: (Record) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val prefs by vm.settings.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    var modelDelete by remember { mutableStateOf<File?>(null) }
    var wipe by remember { mutableStateOf(false) }
    var uriText by rememberSaveable { mutableStateOf<String?>(null) }
    var restoring by rememberSaveable { mutableStateOf(false) }
    val importModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) vm.importModel(it) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        if (it != null) { uriText = it.toString(); restoring = false }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) { uriText = it.toString(); restoring = true }
    }
    val exportMemory = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { if (it != null) vm.exportMemories(it) }
    var memoryExport by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.tell(if (it) "Notificações autorizadas." else "Notificações desativadas. Podes ativá-las nas definições Android.")
    }
    fun open(uri: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
        catch (_: Exception) { vm.tell("Nenhuma aplicação disponível para abrir esta ligação.") }
    }
    val names = listOf("Hábitos", "Memória", "Modelos", "Cópias", "Definições")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { i, title -> FilterChip(page == i, { page = i }, label = { Text(title) }) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (page) {
                0 -> {
                    item { SectionTitle("Pequenos hábitos", "Consistência, um dia de cada vez.") { IconButton(onClick = { onAdd(Kind.HABIT) }) { Icon(Icons.Default.Add, "Novo hábito") } } }
                    val habits = records.filter { it.kind == Kind.HABIT }
                    if (habits.isEmpty()) item { EmptyCard("Começa por algo simples", "Cria um hábito e regista os dias em que o praticaste.") }
                    items(habits, key = { it.id }) { record ->
                        val days = record.body.lineSequence().filter { runCatching { LocalDate.parse(it) }.isSuccess }.count()
                        val today = record.body.lineSequence().any { it == LocalDate.now().toString() }
                        RecordCard(record, { onOpen(record) }, { onEdit(record) }, { onDelete(record) }, footer = {
                            Text("$days dias registados", style = MaterialTheme.typography.labelMedium)
                            FilledTonalButton(onClick = { vm.habitCheck(record) }, enabled = !today) { Text(if (today) "Feito hoje" else "Registar hoje") }
                        })
                    }
                }
                1 -> {
                    item { SectionTitle("Memória autorizada", "Preferências e objetivos que decidiste guardar.") { IconButton(onClick = { onAdd(Kind.MEMORY) }) { Icon(Icons.Default.Add, "Nova memória") } } }
                    item { Text("O diário e os documentos não são copiados automaticamente para aqui. A IA pode consultar estas memórias através de uma ferramenta local.") }
                    item { OutlinedButton(onClick = { memoryExport = true }) { Text("Exportar memórias em JSON") } }
                    val memories = records.filter { it.kind == Kind.MEMORY }
                    if (memories.isEmpty()) item { EmptyCard("Tu escolhes o que fica", "Guarda uma preferência, informação ou objetivo com autorização explícita.") }
                    items(memories, key = { it.id }) { record -> RecordCard(record, { onOpen(record) }, { onEdit(record) }, { onDelete(record) }) }
                }
                2 -> {
                    item { SectionTitle("IA no teu dispositivo", "Instala uma vez. Usa offline.") }
                    item { Card { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("1 · Obter um modelo GGUF", style = MaterialTheme.typography.titleMedium)
                        Text(ModelStore.RECOMMENDED)
                        Text("Recomendado: modelo multilingue pequeno, licença Apache 2.0. Escolhe o ficheiro qwen2.5-0.5b-instruct-q4_k_m.gguf na página oficial. Se estiver dividido em partes, usa outro GGUF único ou junta-o no computador com llama-gguf-split.")
                        Text("Reserva aproximadamente 1–1,5 GB de RAM livre para modelo e contexto pequeno. Estimativa, ainda não medida em dispositivo. Podes transferir o GGUF por USB.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { open(ModelStore.MODEL_PAGE) }) { Text("Abrir página oficial no navegador") }
                        Text("2 · Importar e carregar", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { importModel.launch(arrayOf("*/*")) }, enabled = operation == null && !chat.generating) { Text("Importar GGUF") }
                        Text("Só são aceites GGUF v2/v3. A compatibilidade final é verificada pelo motor llama.cpp ao carregar. O APK não contém pesos.")
                        Text("3 · Ativar modo de avião e conversar", style = MaterialTheme.typography.titleMedium)
                        Text("A app não tem permissão de Internet. O navegador é usado apenas para obter o modelo. Conversas e documentos permanecem no armazenamento privado Android.")
                    } } }
                    if (models.isEmpty()) item { EmptyCard("Nenhum modelo instalado", "Importa um GGUF e toca em Carregar. Não são usadas respostas simuladas.") }
                    items(models, key = { it.file.name }) { model -> OutlinedCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(model.file.name, style = MaterialTheme.typography.titleSmall)
                            Text("${model.bytes / 1048576} MB${if (engine.model == model.file.name) " · Carregado" else ""}")
                            Text("SHA-256: ${model.sha256 ?: "Indisponível"}", style = MaterialTheme.typography.labelSmall)
                            Row {
                                TextButton(onClick = { vm.loadModel(model.file) }, enabled = operation == null && !chat.generating && engine.model != model.file.name) { Text("Carregar") }
                                TextButton(onClick = { modelDelete = model.file }, enabled = operation == null && !chat.generating) { Text("Eliminar") }
                            }
                        }
                    } }
                    if (engine.model != null) item { OutlinedButton(onClick = vm::unloadModel, enabled = !chat.generating && operation == null) { Text("Descarregar da memória") } }
                }
                3 -> {
                    item { SectionTitle("Cópias de segurança", "Encriptação antes de sair da aplicação.") }
                    item { Text("Incluem agenda, tarefas, diário, memórias, hábitos, texto importado e conversas. Não incluem modelos GGUF nem definições de bloqueio. Guarda a palavra-passe num local seguro.") }
                    item { Button(onClick = { exportBackup.launch("atlas-${LocalDate.now()}.atlas") }, enabled = operation == null && !chat.generating) { Text("Exportar cópia encriptada") } }
                    item { OutlinedButton(onClick = { importBackup.launch(arrayOf("*/*")) }, enabled = operation == null && !chat.generating) { Text("Importar e substituir dados…") } }
                    item { Text("AES-256-GCM · PBKDF2-HMAC-SHA256 · 210 000 iterações. Uma palavra-passe errada ou conteúdo alterado impede a restauração. O seletor Android pode incluir destinos na nuvem: só o ficheiro já encriptado é escrito no destino.", style = MaterialTheme.typography.bodySmall) }
                }
                else -> {
                    item { SectionTitle("Definições", "Privacidade e recursos sob o teu controlo.") }
                    item { Text("Aparência", style = MaterialTheme.typography.titleMedium) }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("system" to "Sistema", "light" to "Claro", "dark" to "Escuro").forEach { (value, label) ->
                            FilterChip(prefs.theme == value, { vm.app.settings.update(prefs.copy(theme = value)) }, label = { Text(label) })
                        }
                    } }
                    item { Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Bloqueio da aplicação"); Text("Biometria ou credenciais do dispositivo", style = MaterialTheme.typography.bodySmall) }
                        Switch(prefs.lock, onLock)
                    } }
                    item { Text("O armazenamento usa a proteção do Android. A base de dados não tem uma segunda camada de encriptação própria. As cópias exportadas são sempre encriptadas. Capturas de ecrã permitidas para partilhar imagens e erros.", style = MaterialTheme.typography.bodySmall) }
                    item { OutlinedButton(onClick = {
                        var owner: android.content.Context = context
                        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
                        val activity = owner as? android.app.Activity
                        val secure = activity?.window?.attributes?.flags?.let { it and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0 }
                        val diagnostic = "Atlas ${pt.atlas.personal.BuildConfig.VERSION_NAME} (${pt.atlas.personal.BuildConfig.VERSION_CODE})\nAndroid ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nFLAG_SECURE=$secure"
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(diagnostic))
                        vm.tell("Diagnóstico copiado. Não contém conversas nem registos pessoais.")
                    }) { Text("Copiar diagnóstico de capturas") } }
                    item { Text("Limites de inferência", style = MaterialTheme.typography.titleMedium) }
                    item { PerformancePanel(vm) }
                    item { Text("Contexto: ${prefs.contextTokens} tokens")
                        Slider(prefs.contextTokens.toFloat(), { vm.app.settings.update(prefs.copy(contextTokens = (it.toInt() / 512 * 512).coerceIn(512, 4096))) }, valueRange = 512f..4096f, steps = 6, enabled = !chat.generating) }
                    item { Text("Resposta: ${prefs.maxTokens} tokens")
                        Slider(prefs.maxTokens.toFloat(), { vm.app.settings.update(prefs.copy(maxTokens = (it.toInt() / 64 * 64).coerceIn(64, 1024))) }, valueRange = 64f..1024f, steps = 14, enabled = !chat.generating) }
                    item { Text("Orçamento de memória: ${prefs.memoryMb} MB")
                        Slider(prefs.memoryMb.toFloat(), { vm.app.settings.update(prefs.copy(memoryMb = (it.toInt() / 512 * 512).coerceIn(512, 4096))) }, valueRange = 512f..4096f, steps = 6, enabled = !chat.generating) }
                    item { Text("O orçamento é uma verificação preventiva aproximada; não limita o processo no sistema. Aumentar contexto ou modelo pode levar o Android a terminar a app por falta de RAM. Os limites aplicam-se ao próximo pedido; não precisas de recarregar os pesos do modelo.", style = MaterialTheme.typography.bodySmall) }
                    item { Text("Lembretes", style = MaterialTheme.typography.titleMedium) }
                    item { OutlinedButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Autorizar notificações") } }
                    item { OutlinedButton(onClick = {
                        try { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
                        catch (_: Exception) { vm.tell("Configura os alarmes nas definições Android.") }
                    }) { Text("Autorizar alarmes exatos") } }
                    item { Text("Mantém as notificações autorizadas. Sem permissão de alarmes exatos, o Android gere o horário via WorkManager. Alguns fabricantes também impõem restrições de bateria.", style = MaterialTheme.typography.bodySmall) }
                    item { HorizontalDivider() }
                    item { OutlinedButton(onClick = { wipe = true }, enabled = !chat.generating && operation == null) { Icon(Icons.Default.DeleteForever, null); Text(" Apagar registos e histórico…") } }
                    item { Text("Atlas Personal 0.2.3 · Android 13+ · ARM64\nKotlin / Compose / Room / llama.cpp (MIT).\nModelo recomendado: Qwen2.5-0.5B-Instruct (Apache 2.0). Confirma sempre os dados e as sugestões da IA.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    modelDelete?.let { file -> ConfirmDialog("Eliminar modelo?", "O ficheiro ${file.name} será apagado. Podes importá-lo novamente.", "Eliminar", { modelDelete = null }, { vm.deleteModel(file); modelDelete = null }) }
    if (wipe) ConfirmDialog("Apagar dados pessoais?", "Serão apagados todos os registos, documentos extraídos, memórias e conversas. Exporta uma cópia primeiro se quiseres recuperá-los.", "Apagar tudo", { wipe = false }, { vm.wipe(); wipe = false })
    uriText?.let { text -> PasswordDialog(restoring, onDismiss = { uriText = null }, onSubmit = { password ->
        uriText = null
        if (restoring) vm.restoreBackup(Uri.parse(text), password) else vm.exportBackup(Uri.parse(text), password)
    }) }
    if (memoryExport) ConfirmDialog("Exportar memórias sem encriptação?", "O JSON poderá ser lido por quem tiver acesso ao destino. Para proteção por palavra-passe usa as cópias de segurança.", "Escolher destino", { memoryExport = false }, {
        memoryExport = false; exportMemory.launch("atlas-memorias.json")
    })
}
