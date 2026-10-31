package pt.atlas.personal

import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.agent.*
import pt.atlas.personal.data.*

class ConversationRegressionTest {
    private val badTask = """{"tool":"tasks.create","arguments":{"title":"Como te chamas?"}}"""
    private fun user(id: String, text: String) = Message(id = id, conversationId = "chat", role = "user", content = text, createdAt = 1000)
    private fun assistant(text: String) = Message(conversationId = "chat", role = "assistant", content = text, createdAt = 2000)
    private fun system(text: String) = Message(conversationId = "chat", role = "system", content = text, createdAt = 3000)

    @Test fun identityAndOrdinaryQuestionsCannotProposeTasksOrEvents() {
        for (question in listOf("Qual é o teu nome?", "Como te chamas?", "Olá", "Explica a fotossíntese", "Quanto é 2 + 2?")) {
            assertTrue(question, AgentPolicy.allowedTools(question).isEmpty())
            assertFalse(AgentPolicy.permits(ToolProtocol.parse(badTask)!!, question))
            assertFalse(AgentPolicy.permits(ToolCall("events.create", mapOf("title" to question)), question))
        }
    }
    @Test fun previousActionDoesNotGiveNextQuestionPermission() {
        assertEquals(setOf("tasks.create"), AgentPolicy.allowedTools("Cria uma tarefa de estudar"))
        assertTrue(AgentPolicy.allowedTools("Como te chamas?").isEmpty())
        assertTrue(AgentPolicy.allowedTools("Não quero guardar nada").isEmpty())
    }
    @Test fun actionTypeComesFromCurrentUserRequest() {
        assertEquals("notes.create", AgentPolicy.writeTool("Anota que preciso de comprar pão"))
        assertEquals("tasks.create", AgentPolicy.writeTool("Atlas, lembra-me de estudar amanhã às 20h"))
        assertEquals("events.create", AgentPolicy.writeTool("Marca uma reunião amanhã às 10h"))
        assertEquals("memory.save", AgentPolicy.writeTool("Guarda na memória que prefiro estudar de manhã"))
        assertEquals("journal.create", AgentPolicy.writeTool("Regista no diário que hoje fui caminhar"))
        assertFalse(AgentPolicy.permits(ToolCall("events.create", mapOf("title" to "Pão")), "Anota comprar pão"))
    }
    @Test fun negationQuotationAndExplanatoryQuestionsGrantNoWritePermission() {
        for (text in listOf("Não cries uma tarefa", "Como criar uma tarefa?", "Explica como guardar na agenda", "\"cria uma tarefa estudar\" significa o quê?", "Cancela o pedido anterior")) {
            assertNull(text, AgentPolicy.writeTool(text))
        }
    }
    @Test fun readingTasksCannotCreateOne() {
        val query = "Quais são as minhas tarefas?"
        assertEquals(setOf("tasks.list"), AgentPolicy.allowedTools(query))
        assertFalse(AgentPolicy.permits(ToolCall("tasks.create", mapOf("title" to "Estudar")), query))
    }
    @Test fun textReviewAndExplanationsAreNotCalendarOrNoteCommands() {
        for (text in listOf("Marca os erros neste texto", "Aponta os erros desta frase", "Lembra-me de como se resolve uma equação")) {
            assertNull(text, AgentPolicy.writeTool(text))
        }
    }
    @Test fun userNameCanReadAuthorizedMemoryButAssistantNameIsJustChat() {
        assertEquals(setOf("memory.list"), AgentPolicy.allowedTools("Qual é o meu nome?"))
        assertTrue(AgentPolicy.allowedTools("Qual é o teu nome?").isEmpty())
    }
    @Test fun ordinaryPromptContainsNoToolSchemaOrTaskExample() {
        val prompt = ToolProtocol.instructions()
        assertTrue(prompt.contains("Chamas-te Atlas Personal"))
        assertFalse(prompt.contains("tasks.create"))
        assertFalse(prompt.contains("events.create"))
        assertFalse(prompt.contains("\"arguments\""))
    }
    @Test fun actionPromptOnlyOffersAuthorizedType() {
        val prompt = ToolProtocol.instructions(setOf("notes.create"))
        assertTrue(prompt.contains("notes.create"))
        assertFalse(prompt.contains("tasks.create"))
        assertFalse(prompt.contains("events.create"))
    }
    @Test fun aSimpleNoteIsNotACalendarEventOrTask() {
        val proposal = ToolProtocol.note("Anota que preciso de comprar pão")!!
        assertEquals("notes.create", proposal.tool)
        assertEquals(Kind.NOTE, proposal.record.kind)
        assertEquals("preciso de comprar pão", proposal.record.body)
        assertNull(proposal.record.dueAt)
        assertFalse(proposal.record.reminder)
        assertTrue(proposal.explanation.contains("Apontamentos"))
        assertFalse(proposal.explanation.contains("lembrete"))
    }
    @Test fun protocolParametersAreNotExposedDuringStreaming() {
        for (text in listOf("{", "{\"tool\"", badTask, "```json\n$badTask", "<tool_call>$badTask")) {
            assertEquals("", AgentPolicy.streamingText(text))
        }
        assertEquals("Chamo-me Atlas Personal.", AgentPolicy.streamingText("Chamo-me Atlas Personal."))
        assertFalse(AgentPolicy.isProtocol("{\"name\":\"Atlas\"}"))
    }
    @Test fun legacyCancellationRepairsExistingConversationWithoutDeletion() {
        val original = listOf(user("old", "Anota comprar pão"), assistant(badTask),
            system("Proposta cancelada. Nenhum registo criado."), user("new", "Como te chamas?"))
        val context = ConversationContext.turns(original, "new")
        assertFalse(context.any { it.content.contains("\"tool\"") || it.content.contains("Anota comprar pão") })
        assertTrue(context.any { it.content.contains("cancelada") })
        assertTrue(context.last().content.contains("Como te chamas?"))
        assertEquals(4, original.size) // no Room history was deleted
    }
    @Test fun linkedCancellationIsIncludedAndOldRequestCannotBeReplayed() {
        val receipt = ActionReceipt("cancelled", "old", Kind.NOTE, "Comprar pão")
        val original = listOf(user("old", "Anota comprar pão"), system(receipt.copy(status = "pending").encode()),
            system(receipt.encode()), user("new", "Qual é o teu nome?"))
        val context = ConversationContext.turns(original, "new")
        assertFalse(context.any { it.content.contains("Anota comprar pão") || it.content.contains("ATLAS_ACTION_V1") })
        assertTrue(context.any { it.content.contains("Pedido cancelado") })
        assertTrue(AgentPolicy.allowedTools(original.last().content).isEmpty())
    }
    @Test fun confirmedActionsAreApplicationFactsNotNewCommands() {
        val original = listOf(user("old", "Cria uma tarefa de estudar"),
            system(ActionReceipt("confirmed", "old", Kind.TASK, "Estudar").encode()), user("new", "Olá"))
        val context = ConversationContext.turns(original, "new")
        assertFalse(context.any { it.role == "user" && it.content.contains("Cria uma tarefa") })
        assertTrue(context.any { it.content.contains("Confirmado e guardado localmente") })
    }
    @Test fun orphanedActionsFromOlderVersionsDoNotRemainActiveInTheModelPrompt() {
        val original = listOf(user("old", "Cria uma tarefa de estudar"), assistant(badTask), user("new", "Explica o céu azul"))
        val context = ConversationContext.turns(original, "new")
        assertEquals(1, context.size)
        assertTrue(context.single().content.contains("Explica o céu azul"))
    }
    @Test fun ordinaryConversationAndRealAnswersRemainInHistory() {
        val original = listOf(user("a", "Olá"), assistant("Olá, como posso ajudar?"), user("b", "Como te chamas?"))
        val context = ConversationContext.turns(original, "b")
        assertEquals(3, context.size)
        assertEquals("Olá, como posso ajudar?", context[1].content)
    }
    @Test fun unhelpfulGeneratedTextIsNotArbitrarilyRemoved() {
        val original = listOf(user("a", "O que é o espiritismo?"), assistant("Olá! Como posso te ajudar hoje?"), user("b", "Explica melhor"))
        val context = ConversationContext.turns(original, "b")
        assertEquals(3, context.size)
        assertEquals("Olá! Como posso te ajudar hoje?", context[1].content)
    }
    @Test fun oldParametersAndPendingReceiptsAreHiddenButCancellationRemainsVisible() {
        val cancellation = system(ActionReceipt("cancelled", "old", Kind.NOTE, "Pão").encode())
        val messages = listOf(user("old", "Anota pão"), assistant(badTask),
            system(ActionReceipt("pending", "old", Kind.NOTE, "Pão").encode()), cancellation)
        val visible = ConversationContext.visibleMessages(messages)
        assertEquals(listOf(messages[0], cancellation), visible)
    }
    @Test fun receiptsSurviveSpecialCharactersAndDoNotExposeInternalIds() {
        val receipt = ActionReceipt("cancelled", "request-secret-id", Kind.NOTE, "Uma \"nota\"\ncom acentos: pão")
        assertEquals(receipt, ActionReceipt.parse(receipt.encode()))
        assertFalse(receipt.display().contains("request-secret-id"))
    }
}
