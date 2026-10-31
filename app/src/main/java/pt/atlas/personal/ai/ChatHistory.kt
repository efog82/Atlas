package pt.atlas.personal.ai

/** Roles remain data; text containing role names is never parsed as a message envelope. */
object ChatHistory {
    fun prepare(turns: List<Turn>, factual: Boolean): List<Turn> {
        require(turns.isNotEmpty() && turns.all { it.role in setOf("system", "user", "assistant") }) { "Papéis de conversa inválidos." }
        val systems = turns.filter { it.role == "system" }.map { it.content }.filter { it.isNotBlank() }
        val output = mutableListOf<Turn>()
        for (turn in turns.filter { it.role != "system" }) {
            val text = if (turn.role == "assistant") ModelOutput.legacy(turn.content) else turn.content
            if (text.isBlank() || (output.isEmpty() && turn.role == "assistant")) continue
            if (output.lastOrNull()?.role == turn.role) {
                val last = output.removeAt(output.lastIndex)
                output += Turn(turn.role, last.content + "\n\n" + text)
            } else output += Turn(turn.role, text)
        }
        require(output.lastOrNull()?.role == "user") { "A conversa deve terminar com o pedido do utilizador." }
        val policy = listOf(ModelInstructions.language) + if (factual) listOf(ModelInstructions.factual) else emptyList()
        return listOf(Turn("system", (systems + policy).joinToString("\n\n"))) + output
    }
}
