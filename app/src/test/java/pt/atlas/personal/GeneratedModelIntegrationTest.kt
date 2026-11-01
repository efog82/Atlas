package pt.atlas.personal

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import pt.atlas.personal.ai.*
import pt.atlas.personal.knowledge.*
import java.io.File

/** Uses actual raw GGUF output from the shared C++ renderer/sampler, never fabricated fixtures. */
class GeneratedModelIntegrationTest {
    @Test fun actualModelsHaveCorrectFramingAndFinalAnswerPresentation() {
        val directory = System.getenv("ATLAS_MODEL_RESULTS")
        assumeTrue("Real GGUF results not supplied; run the model integration workflow", !directory.isNullOrBlank())
        val files = File(requireNotNull(directory)).listFiles { f -> f.name.endsWith("-raw.json") }.orEmpty()
        assertTrue("No real inference results", files.isNotEmpty())
        val presented = JSONArray()
        for (file in files) {
            val rows = JSONArray(file.readText())
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val info = ModelInfo(architecture = row.getString("architecture"), name = row.getString("name"), thinkingSwitch = row.getBoolean("thinkingSwitch"))
                val expected = SamplingConfig.automatic(info, row.getBoolean("thinking"), factual = true)
                val sampling = row.getJSONObject("sampling")
                assertEquals("${file.name}/${row.getString("id")}: temperature", expected.temperature.toDouble(), sampling.getDouble("temperature"), 0.0001)
                assertEquals("${file.name}/${row.getString("id")}: topP", expected.topP.toDouble(), sampling.getDouble("topP"), 0.0001)
                assertEquals("${file.name}/${row.getString("id")}: topK", expected.topK, sampling.getInt("topK"))
                assertEquals(expected.minP.toDouble(), sampling.getDouble("minP"), 0.0001)
                assertEquals(expected.repeatPenalty.toDouble(), sampling.getDouble("repeatPenalty"), 0.0001)
                val raw = row.getString("raw")
                val forced = row.getBoolean("forcedOpen")
                val reasoning = row.getBoolean("reasoning")
                val view = ModelOutput.present(raw, reasoning, forced, complete = true)
                assertFalse("${file.name}/${row.getString("id")}: thinking budget exhausted", view.thinking)
                assertTrue("${file.name}: empty final answer", view.text.isNotBlank())
                assertFalse(row.getBoolean("doubleBos"))
                assertEquals(row.getInt("expectedAccepted"), row.getInt("samplerAccepted"))
                for (token in listOf("<think>", "</think>", "<|im_start|>", "<|im_end|>", "<start_of_turn>", "<end_of_turn>", "<bos>", "<eos>")) {
                    assertFalse("Leaked $token", view.text.contains(token))
                }
                // Exercise actual app presentation at every byte boundary, including incomplete UTF-8/tags.
                val bytes = raw.toByteArray()
                for (length in 1..bytes.size) {
                    val partial = ModelOutput.present(ModelOutput.decodeUtf8(bytes.copyOf(length)), reasoning, forced).text
                    assertFalse(partial.contains('\uFFFD'))
                    assertFalse(partial.contains("<think>") || partial.contains("</think>"))
                }
                val prompt = row.getString("prompt")
                if (file.name.startsWith("qwen3")) {
                    assertTrue(prompt.contains("<|im_start|>user\n"))
                    assertTrue(prompt.contains("<|im_start|>assistant\n"))
                    if (!row.getBoolean("thinking")) assertTrue(prompt.trimEnd().endsWith("</think>"))
                }
                if (file.name.startsWith("gemma3")) {
                    assertTrue(prompt.contains("<start_of_turn>user\n"))
                    assertTrue(prompt.endsWith("<start_of_turn>model\n"))
                    assertFalse(prompt.contains("<|im_start|>"))
                    assertFalse(prompt.contains("<start_of_turn>system"))
                }
                if (row.optString("fixtureExpected").isNotBlank()) {
                    val fixture = listOf(Evidence("test-fixture", "Documento fictício", "Cenário inventado apenas para ensaio.", "secção 1", true))
                    row.put("fixtureFactUsed", view.text.contains(row.getString("fixtureExpected"), true))
                    row.put("citationWarning", Grounding.citationWarning(view.text, fixture) ?: org.json.JSONObject.NULL)
                }
                presented.put(row.put("model", file.name.removeSuffix("-raw.json")).put("final", view.text))
            }
        }
        File(directory, "presented.json").writeText(presented.toString(2))
    }
}
