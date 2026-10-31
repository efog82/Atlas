package pt.atlas.personal

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import pt.atlas.personal.data.*
import pt.atlas.personal.agent.*
import pt.atlas.personal.reminders.ReminderScheduler
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseAgentTest {
    private val app = ApplicationProvider.getApplicationContext<AtlasApp>()
    private val name = "test-${UUID.randomUUID()}.db"
    private lateinit var db: AtlasDatabase
    @Before fun open() { db = Room.databaseBuilder(app, AtlasDatabase::class.java, name).build() }
    @After fun clean() { db.close(); app.deleteDatabase(name) }

    @Test fun recordsSurviveClosingAndReopeningDatabase() = runBlocking {
        val journal = Record(kind = Kind.JOURNAL, title = "Reflexão", body = "Texto privado", tags = "estudo")
        db.dao().save(journal)
        db.close(); open()
        assertEquals(journal, db.dao().record(journal.id))
        assertEquals(1, db.dao().search(searchPattern("privado"), listOf(Kind.JOURNAL)).size)
        assertEquals(0, db.dao().byKind(Kind.MEMORY).size)
    }
    @Test fun creationOnlyOccursAfterConfirmationAndReadsCannotWrite() = runBlocking {
        val repository = AtlasRepository(db, ReminderScheduler(app, db.dao()))
        val tools = AgentTools(repository)
        val proposal = ToolProtocol.proposal(ToolCall("tasks.create", mapOf("title" to "Estudar")))
        assertTrue(db.dao().records().isEmpty())
        tools.read(ToolCall("tasks.list", emptyMap()))
        assertTrue(db.dao().records().isEmpty())
        tools.confirm(proposal)
        assertEquals("Estudar", db.dao().byKind(Kind.TASK).single().title)
        tools.confirm(proposal) // stable proposal UUID makes repeated confirmation idempotent
        assertEquals(1, db.dao().byKind(Kind.TASK).size)
        repository.complete(proposal.record)
        assertTrue(db.dao().record(proposal.record.id)!!.done)
        repository.delete(proposal.record)
        assertNull(db.dao().record(proposal.record.id))
    }
    @Test fun deletingDocumentAndConversationCascadesOnlyTheirChildren() = runBlocking {
        val document = Record(kind = Kind.DOCUMENT, title = "Livro")
        val section = Section(documentId = document.id, page = 1, text = "Conteúdo")
        val conversation = Conversation()
        db.dao().save(document); db.dao().saveSections(listOf(section))
        db.dao().saveConversation(conversation)
        db.dao().saveMessage(Message(conversationId = conversation.id, role = "user", content = "Olá"))
        db.dao().deleteRecord(document.id)
        assertTrue(db.dao().allSections().isEmpty())
        assertEquals(1, db.dao().allMessages().size)
        db.dao().deleteConversation(conversation.id)
        assertTrue(db.dao().allMessages().isEmpty())
    }
    @Test fun literalSearchDoesNotTreatUserWildcardsAsSqlWildcards() = runBlocking {
        db.dao().save(Record(kind = Kind.NOTE, title = "100% real"))
        db.dao().save(Record(kind = Kind.NOTE, title = "1000 real"))
        assertEquals(1, db.dao().search(searchPattern("100%"), listOf(Kind.NOTE)).size)
    }
    @Test fun noteConfirmationDoesNotCreateTaskOrEvent() = runBlocking {
        val repository = AtlasRepository(db, ReminderScheduler(app, db.dao()))
        val tools = AgentTools(repository)
        val proposal = ToolProtocol.note("Anota que preciso de comprar pão")!!
        assertTrue(db.dao().records().isEmpty())
        tools.confirm(proposal)
        assertEquals(1, db.dao().byKind(Kind.NOTE).size)
        assertTrue(db.dao().byKind(Kind.TASK).isEmpty())
        assertTrue(db.dao().byKind(Kind.EVENT).isEmpty())
    }
    @Test fun cancelledRequestStaysCancelledAfterDatabaseReopen() = runBlocking {
        val conversation = Conversation()
        db.dao().saveConversation(conversation)
        val request = Message(conversationId = conversation.id, role = "user", content = "Anota comprar pão")
        db.dao().saveMessage(request)
        val receipt = ActionReceipt("cancelled", request.id, Kind.NOTE, "Comprar pão")
        db.dao().saveMessage(Message(conversationId = conversation.id, role = "system", content = receipt.encode()))
        val question = Message(conversationId = conversation.id, role = "user", content = "Como te chamas?")
        db.dao().saveMessage(question)
        db.close(); open()
        val context = ConversationContext.turns(db.dao().messages(conversation.id), question.id)
        assertTrue(context.any { it.content.contains("Pedido cancelado") })
        assertFalse(context.any { it.content.contains("Anota comprar pão") })
        assertTrue(AgentPolicy.allowedTools(question.content).isEmpty())
        assertTrue(db.dao().records().isEmpty())
    }

    @Test fun computerReminderIsAbsentUntilConfirmedThenSurvivesReopen() = runBlocking {
        val repository = AtlasRepository(db, ReminderScheduler(app, db.dao()))
        val tools = AgentTools(repository)
        val now = java.time.ZonedDateTime.now().withSecond(0).withNano(0)
        val tomorrow = now.plusDays(1)
        val request = "cria um lembrete amanhã às 21h para eu ligar o computador"
        val proposal = (LocalRequests.reminder(request, now) as ReminderResolution.Ready).proposal
        val read = LocalRead(Kind.EVENT, tomorrow.toLocalDate())
        assertTrue(db.dao().records().isEmpty())
        assertFalse(LocalReadFormatter.format(read, db.dao().records(), now).contains("ligar o computador"))
        tools.confirm(proposal)
        val saved = db.dao().record(proposal.record.id)!!
        assertTrue(saved.reminder)
        assertEquals("ligar o computador", saved.title)
        assertTrue(LocalReadFormatter.format(read, db.dao().records(), now).contains("21:00 — ligar o computador"))
        db.close(); open()
        assertEquals(saved, db.dao().record(saved.id))
        ReminderScheduler(app, db.dao()).cancel(saved.id)
    }

    @Test fun publicKnowledgePersistsAndCannotOverwriteOrRecreatePrivateOrDeletedRecords() = runBlocking {
        val api = pt.atlas.personal.knowledge.WikiApi(pt.atlas.personal.knowledge.WikiTransport { error("No HTTP in this test") })
        var knowledge = pt.atlas.personal.knowledge.KnowledgeStore(app, api, db)
        val privateEntry = Record(kind = Kind.JOURNAL, title = "Privado", body = "Fotossíntese no meu diário privado")
        db.dao().save(privateEntry)
        db.dao().save(Record(kind = Kind.MEMORY, title = "Preferências", body = "Estudar de manhã"))
        assertTrue(knowledge.local("Diário de Anne Frank", allowPersonal = false).isEmpty())
        val page = pt.atlas.personal.knowledge.WikiPage(10, "Fotossíntese", "A fotossíntese utiliza luz nas plantas.", 22, "2026-10-31T09:00:00Z")
        val record = knowledge.cache(page)!!
        assertEquals(Kind.SOURCE, record.kind)
        assertEquals(22L, pt.atlas.personal.knowledge.SourceMeta.parse(record.body)!!.revision)
        val found = knowledge.local("O que é fotossíntese?")
        assertTrue(found.isNotEmpty())
        assertTrue(found.none { it.private })
        db.close(); open()
        knowledge = pt.atlas.personal.knowledge.KnowledgeStore(app, api, db)
        assertEquals(record, db.dao().record(record.id))
        knowledge.cache(page.copy(revision = 23, text = "As plantas fazem fotossíntese."), onlyExisting = true)
        assertEquals(privateEntry, db.dao().record(privateEntry.id))
        assertEquals(1, db.dao().sections(record.id).size)
        assertNull(knowledge.cache(page, onlyExisting = true)) // an older response cannot roll back revision 23
        assertEquals(23L, pt.atlas.personal.knowledge.SourceMeta.parse(db.dao().record(record.id)!!.body)!!.revision)
        db.dao().deleteRecord(record.id)
        assertNull(knowledge.cache(page, onlyExisting = true))
        assertNull(db.dao().record(record.id))
        val collision = privateEntry.copy(id = record.id)
        db.dao().save(collision)
        assertNull(knowledge.cache(page))
        assertEquals(collision, db.dao().record(record.id))
    }

}
