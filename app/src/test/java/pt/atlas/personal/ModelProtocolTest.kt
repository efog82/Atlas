package pt.atlas.personal

import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.ai.*

class ModelProtocolTest {
    @Test fun familyComesFromMetadataAndSamplingIsModelSpecific() {
        val qwen = ModelInfo(architecture = "qwen3", name = "renamed file")
        assertEquals(ModelFamily.QWEN3, qwen.family)
        assertEquals(SamplingConfig(0.6f, 0.95f, 20), SamplingConfig.recommended(qwen, true))
        assertEquals(SamplingConfig(0.7f, 0.8f, 20), SamplingConfig.recommended(qwen, false))
        assertEquals(64, SamplingConfig.recommended(ModelInfo(architecture = "gemma3"), false).topK)
        assertEquals(ModelFamily.GENERIC, ModelInfo(architecture = "llama", name = "Qwen3 filename").family)
        assertThrows(IllegalArgumentException::class.java) { SamplingConfig(temperature = Float.NaN).validate() }
    }
    @Test fun thinkingIsNeverPresentedAsFinalEvenWithSplitTags() {
        val raw = "<think>Private model work in English</think>Resposta em português."
        for (end in 1 until raw.indexOf("Resposta")) assertEquals("", ModelOutput.present(raw.take(end), true).text)
        assertEquals("Resposta em português.", ModelOutput.present(raw, true, complete = true).text)
        assertTrue(ModelOutput.present("<think>still reasoning", true, complete = true).thinking)
    }
    @Test fun prefilledThinkTagAndNonThinkingEmptyBlockAreSupported() {
        assertEquals("Resposta final", ModelOutput.present("work</think>Resposta final", true, forcedOpen = true, complete = true).text)
        assertEquals("Resposta final", ModelOutput.present("<think>\n\n</think>\nResposta final", true, complete = true).text)
        assertTrue(ModelOutput.present("work without closing tag", true, forcedOpen = true, complete = true).thinking)
    }
    @Test fun protocolBoundariesCannotLeakOrInventAnExtraUserTurn() {
        for (token in listOf("<|im_end|>", "<end_of_turn>", "<|eot_id|>")) {
            for (i in 1 until token.length) assertEquals("Resposta", ModelOutput.present("Resposta" + token.take(i), false).text)
            assertEquals("Resposta", ModelOutput.present("Resposta${token}user: next", false, complete = true).text)
        }
        assertEquals("Não sei confirmar essa data.", ModelOutput.present("Não sei confirmar essa data.", true, complete = true).text)
        assertEquals("A afirmação está errada.", ModelOutput.present("A afirmação está errada.", false, complete = true).text)
        assertEquals("This answer is English.", ModelOutput.present("This answer is English.", false, complete = true).text)
    }
    @Test fun utf8SplitInsideAccentsAndEmojiDoesNotProduceReplacementCharacters() {
        val text = "Reflexão 🧠 em português"
        val bytes = text.toByteArray()
        for (i in 0..bytes.size) assertFalse(ModelOutput.decodeUtf8(bytes.copyOf(i)).contains('\uFFFD'))
        assertEquals(text, ModelOutput.decodeUtf8(bytes, complete = true))
    }
    @Test fun historyHasOneSystemAndAlternatingRolesWithoutFakeAssistantFacts() {
        val prepared = ChatHistory.prepare(listOf(Turn("system", "Identidade"), Turn("assistant", "orphan"),
            Turn("user", "Pergunta"), Turn("system", "Estado local: cancelado"), Turn("user", "Reformulação"),
            Turn("assistant", "<think>old thought</think>Resposta antiga"), Turn("user", "Explica melhor")), true)
        assertEquals(listOf("system", "user", "assistant", "user"), prepared.map { it.role })
        assertTrue(prepared.first().content.contains("cancelado"))
        assertTrue(prepared.first().content.contains("premissa for falsa"))
        assertEquals("Resposta antiga", prepared[2].content)
        assertFalse(prepared.any { "orphan" in it.content || "old thought" in it.content })
        assertEquals("Explica melhor", prepared.last().content)
    }
    @Test fun factualModeIsInstructionOnlyAndCanBeDisabled() {
        val input = listOf(Turn("user", "Porque é Lisboa a capital de Espanha?"))
        assertTrue(ChatHistory.prepare(input, true).first().content.contains("admite a incerteza"))
        assertFalse(ChatHistory.prepare(input, false).first().content.contains("premissa"))
        assertEquals(input.single(), ChatHistory.prepare(input, true).last())
        assertThrows(IllegalArgumentException::class.java) { ChatHistory.prepare(listOf(Turn("tool", "x")), true) }
    }
}
