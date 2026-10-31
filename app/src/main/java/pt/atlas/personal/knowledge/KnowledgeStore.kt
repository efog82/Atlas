package pt.atlas.personal.knowledge

import androidx.room.withTransaction
import kotlinx.coroutines.*
import pt.atlas.personal.AtlasApp
import pt.atlas.personal.agent.LocalRead
import pt.atlas.personal.agent.LocalReadFormatter
import pt.atlas.personal.data.*
import java.time.*
import java.util.UUID

data class KnowledgeLookup(val sources: List<Evidence>, val notice: String? = null, val networkUsed: Boolean = false)

class KnowledgeStore(private val app: AtlasApp, private val wiki: WikiApi, private val database: AtlasDatabase = app.database) {
    private val dao get() = database.dao()
    private fun documentId(pageId: Long) = UUID.nameUUIDFromBytes("wikipedia:pt:$pageId".toByteArray()).toString()

    suspend fun cache(page: WikiPage, onlyExisting: Boolean = false): Record? {
        val id = documentId(page.pageId)
        return database.withTransaction {
            val previous = dao.record(id)
            if (onlyExisting && previous == null) return@withTransaction null
            val now = System.currentTimeMillis()
            val meta = SourceMeta(page.pageId, "https://pt.wikipedia.org/w/index.php?oldid=${page.revision}", page.revision, page.revisionAt, now, textHash(page.text))
            val record = Record(id = id, kind = Kind.SOURCE, title = page.title.take(200), body = meta.encode(), category = "Wikipédia · português",
                createdAt = previous?.createdAt ?: now, updatedAt = now)
            val sections = page.text.chunked(1200).mapIndexed { i, text -> Section(
                id = UUID.nameUUIDFromBytes("$id:${i+1}".toByteArray()).toString(), documentId = id, page = i+1, text = text) }
            dao.save(record); dao.deleteSections(id); dao.saveSections(sections)
            record
        }
    }

    suspend fun local(question: String): List<Evidence> = withContext(Dispatchers.Default) {
        val privateAllowed = app.settings.state.value.autoPersonal && KnowledgeIntent.personal(question)
        val kinds = listOf(Kind.NOTE) + if (privateAllowed) listOf(Kind.JOURNAL, Kind.MEMORY, Kind.TASK, Kind.EVENT) else emptyList()
        val candidates = mutableListOf<Evidence>()
        val records = mutableMapOf<String, Record?>()
        for (term in KnowledgeWords.terms(question)) {
            currentCoroutineContext().ensureActive()
            val variants = listOf(term, KnowledgeWords.fold(term)).distinct()
            for (variant in variants) {
                val pattern = searchPattern(variant)
                dao.evidenceRecords(variant, pattern, kinds).forEach { r ->
                    val stamp = Instant.ofEpochMilli(r.dueAt ?: r.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    val label = when (r.kind) { Kind.JOURNAL -> "Diário"; Kind.MEMORY -> "Memória autorizada"; Kind.TASK -> "Tarefa"; Kind.EVENT -> "Agenda"; else -> "Apontamento" }
                    candidates += Evidence("record:${r.id}", r.title, (r.title + "\n" + r.text).take(1400), "$label · $stamp", true)
                }
                for (section in dao.evidenceSections(variant, pattern)) {
                    val owner = records.getOrPut(section.documentId) { dao.record(section.documentId) } ?: continue
                    if (owner.kind !in setOf(Kind.DOCUMENT, Kind.SOURCE)) continue
                    val meta = if (owner.kind == Kind.SOURCE) SourceMeta.parse(owner.body) ?: continue else null
                    candidates += Evidence("section:${section.id}:$variant", owner.title, section.text, "${if (meta == null) "Página/secção" else "Excerto"} ${section.page}",
                        private = meta == null, url = meta?.url, fetchedAt = meta?.fetchedAt, revisionAt = meta?.revisionAt)
                }
            }
        }
        val ranked = EvidenceRanker.rank(question, candidates.distinctBy { it.title + it.location + it.quote }, 4)
        if (privateAllowed && KnowledgeIntent.planning(question)) {
            val day = if (Regex("\\bamanha\\b").containsMatchIn(KnowledgeWords.fold(question))) LocalDate.now().plusDays(1) else LocalDate.now()
            val agenda = LocalReadFormatter.format(LocalRead(Kind.EVENT, day), dao.records())
            val memories = dao.byKind(Kind.MEMORY, 2).map { r -> Evidence("record:${r.id}", r.title, (r.title + "\n" + r.body).take(1400), "Memória explicitamente guardada", true) }
            return@withContext (listOf(Evidence("agenda:$day", "Agenda · $day", agenda.take(1400), "Consulta local atual", true)) + ranked + memories).distinctBy { it.id }.take(4)
        }
        if (ranked.isEmpty() && privateAllowed) {
            return@withContext dao.byKind(Kind.MEMORY, 4).map { r -> Evidence("memory:${r.id}", r.title,
                (r.title + "\n" + r.body).take(1400), "Memória explicitamente guardada", true) }
        }
        ranked
    }

    suspend fun lookup(question: String, hasInternet: Boolean): KnowledgeLookup {
        var found = local(question)
        val prefs = app.settings.state.value
        if (!prefs.onlineEnabled || !hasInternet) return KnowledgeLookup(found,
            if (found.isEmpty()) "Sem fonte local suficiente. Podes importar TXT/PDF em Estudos ou consultar fontes públicas quando houver Internet." else null)
        // Private questions and private excerpts are never used to produce an outgoing query.
        val query = PublicQuery.fromQuestion(question)
        if (query == null || KnowledgeIntent.personal(question) || found.any { it.private }) return KnowledgeLookup(found,
            if (found.isEmpty() && KnowledgeIntent.personal(question)) "Não encontrei registos pessoais relevantes. Este pedido não foi enviado para a Internet." else null)
        val stale = found.any { it.fetchedAt != null && System.currentTimeMillis() - it.fetchedAt > 24L * 60 * 60 * 1000 }
        if (found.isNotEmpty() && !stale) return KnowledgeLookup(found)
        return try {
            val pages = wiki.search(query)
            pages.forEach { cache(it) }
            found = local(question)
            KnowledgeLookup(found, if (found.isEmpty()) "A pesquisa na Wikipédia não encontrou excertos suficientes para responder. Não vou completar a resposta com informação sem fonte." else "Pesquisa pública na Wikipédia: «${query.text}». Fontes guardadas para consulta offline.", networkUsed = true)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { KnowledgeLookup(found, "A consulta online falhou. Foram mantidas as fontes locais; tenta novamente mais tarde.") }
    }

    suspend fun refreshSaved(): Int {
        if (!app.settings.state.value.onlineEnabled) return 0
        val saved = dao.sourcesForRefresh(20).mapNotNull { SourceMeta.parse(it.body) }
        if (saved.isEmpty()) return 0
        val pages = wiki.refresh(saved.map { it.pageId })
        var count = 0
        pages.forEach { if (cache(it, onlyExisting = true) != null) count++ }
        return count
    }
    suspend fun excerpts(record: Record): List<Section> = dao.sections(record.id)
}
