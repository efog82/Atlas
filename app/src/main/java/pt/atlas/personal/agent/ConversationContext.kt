package pt.atlas.personal.agent

import org.json.JSONObject
import pt.atlas.personal.ai.Turn
import pt.atlas.personal.ai.historyTurn
import pt.atlas.personal.ai.AssistantText
import pt.atlas.personal.data.Message
import pt.atlas.personal.data.Kind

/** Persisted as system messages: no Room migration, no deletion of existing conversations. */
data class ActionReceipt(val status: String, val requestId: String, val kind: String, val title: String) {
    fun encode(): String = PREFIX + JSONObject().put("status", status).put("request", requestId).put("kind", kind).put("title", title).toString()
    fun display(): String = when (status) {
        "cancelled" -> "Pedido cancelado. Nenhum registo foi criado."
        "confirmed" -> "Confirmado e guardado localmente: $title"
        else -> "Pedido preparado para confirmação: $title"
    }
    companion object {
        private const val PREFIX = "ATLAS_ACTION_V1:"
        fun parse(text: String): ActionReceipt? = runCatching {
            if (!text.startsWith(PREFIX)) return null
            val obj = JSONObject(text.removePrefix(PREFIX))
            val status = obj.getString("status")
            require(status in setOf("pending", "cancelled", "confirmed"))
            ActionReceipt(status, obj.getString("request"), obj.getString("kind"), obj.getString("title"))
        }.getOrNull()
    }
}

object ConversationContext {
    /** Current request remains; old action commands and tool envelopes cannot become new instructions. */
    fun turns(messages: List<Message>, currentRequestId: String): List<Turn> {
        data class Entry(val requestId: String?, val turn: Turn)
        val output = mutableListOf<Entry>()
        val receipts = messages.mapNotNull { ActionReceipt.parse(it.content) }
        val resolved = receipts.associateBy { it.requestId }
        var owner: String? = null
        for (message in messages) {
            if (message.role == "user") {
                owner = message.id
                val oldAction = message.id != currentRequestId && (AgentPolicy.writeTool(message.content) != null || resolved.containsKey(message.id))
                if (!oldAction) output += Entry(owner, historyTurn(message.role, message.content))
            } else if (message.role == "assistant") {
                val content = AssistantText.clean(message.content)
                if (content.isNotBlank() && !AgentPolicy.isProtocol(content) && output.any { it.requestId == owner }) {
                    output += Entry(owner, Turn("assistant", content))
                }
            } else {
                val receipt = ActionReceipt.parse(message.content)
                if (receipt != null) {
                    if (receipt.status != "pending" && receipt == resolved[receipt.requestId]) {
                        output.removeAll { it.requestId == receipt.requestId }
                        output += Entry(null, Turn("assistant", "[Estado da aplicação: ${receipt.display()} Não repetir esta ação sem um novo pedido explícito.]"))
                    }
                } else if (message.content.startsWith("Proposta cancelada.") || message.content.startsWith("Confirmado e guardado localmente:")) {
                    // Repair 0.1/0.2 conversations whose tool JSON was saved but outcome was ignored.
                    if (owner != currentRequestId) output.removeAll { it.requestId == owner }
                    output += Entry(null, Turn("assistant", "[Estado da aplicação: ${message.content} Não repetir pedidos anteriores.]"))
                }
            }
        }
        return output.map { it.turn }.takeLast(24)
    }

    fun visibleMessages(messages: List<Message>): List<Message> = messages.mapNotNull {
        if (it.role == "assistant") {
            val content = AssistantText.clean(it.content)
            if (content.isBlank() || AgentPolicy.isProtocol(content)) null else it.copy(content = content)
        } else if (it.role == "system" && ActionReceipt.parse(it.content)?.status == "pending") null
        else it
    }
}
