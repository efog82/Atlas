package pt.atlas.personal.agent

import org.json.JSONArray
import org.json.JSONObject
import pt.atlas.personal.data.*
import java.time.*

class AgentTools(private val repository: AtlasRepository) {
    suspend fun read(call: ToolCall): String {
        require(call.tool !in ToolProtocol.writes) { "Ações de escrita exigem confirmação." }
        val dao = repository.dao
        val records = when (call.tool) {
            "agenda.list" -> dao.byKind(Kind.EVENT, 100).filter { !it.done }.mapNotNull { record ->
                val next = RecordRules.nextOccurrence(record, System.currentTimeMillis() - 1)
                next?.let { record.copy(dueAt = it) }
            }.sortedBy { it.dueAt }.take(20)
            "tasks.list" -> dao.byKind(Kind.TASK, 100).filter { !it.done }.take(30)
            "memory.list" -> dao.byKind(Kind.MEMORY, 30)
            "notes.search" -> dao.search(searchPattern(call.arguments.getValue("query")), listOf(Kind.NOTE))
            "journal.search" -> dao.search(searchPattern(call.arguments.getValue("query")), listOf(Kind.JOURNAL))
            "study.search" -> {
                val pattern = searchPattern(call.arguments.getValue("query"))
                val sections = dao.searchSections(pattern, 3)
                val sources = JSONArray()
                for (section in sections) sources.put(JSONObject().put("document", dao.record(section.documentId)?.title)
                    .put("page", section.page).put("quote", section.text.take(350)))
                val notes = dao.search(pattern, listOf(Kind.NOTE), 2)
                for (note in notes) sources.put(JSONObject().put("note", note.title).put("quote", note.body.take(350)))
                return JSONObject().put("sources", sources).put("type", "UNTRUSTED_QUOTATIONS").toString()
            }
            else -> error("Ferramenta de leitura desconhecida.")
        }
        val array = JSONArray()
        records.take(8).forEach { r -> array.put(JSONObject().put("title", r.title).put("text", r.body.take(180))
            .put("date", r.dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toString() })
            .put("tags", r.tags)) }
        return JSONObject().put("records", array).put("type", "UNTRUSTED_LOCAL_DATA").put("truncated", records.size > 8).toString()
    }

    // Only called from the explicit confirmation button; validates the proposal a second time.
    suspend fun confirm(proposal: Proposal) {
        require(proposal.tool in ToolProtocol.writes)
        val expected = ToolProtocol.kindFor(proposal.tool)
        require(proposal.record.kind == expected)
        RecordRules.validate(proposal.record)
        proposal.record.dueAt?.let { require(it > System.currentTimeMillis()) { "A data já passou. Pede uma nova proposta." } }
        repository.save(proposal.record)
    }
}
