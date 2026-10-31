package pt.atlas.personal.knowledge

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.ByteArrayOutputStream

fun interface WikiTransport { suspend fun get(parameters: Map<String, String>): String }
data class WikiPage(val pageId: Long, val title: String, val text: String, val revision: Long, val revisionAt: String)

class WikiApi(private val transport: WikiTransport) {
    private val common = mapOf("action" to "query", "format" to "json", "formatversion" to "2", "prop" to "extracts|revisions",
        "explaintext" to "1", "exintro" to "1", "exchars" to "1200", "exlimit" to "max", "rvprop" to "ids|timestamp", "redirects" to "1")
    suspend fun search(query: PublicQuery): List<WikiPage> = parse(transport.get(common + mapOf("generator" to "search", "gsrsearch" to query.text, "gsrlimit" to "4"))).take(4)
    suspend fun refresh(pageIds: List<Long>): List<WikiPage> {
        require(pageIds.isNotEmpty() && pageIds.size <= 20 && pageIds.all { it > 0 })
        return parse(transport.get(common + ("pageids" to pageIds.distinct().joinToString("|"))))
    }
    private fun parse(json: String): List<WikiPage> {
        val root = JSONObject(json)
        check(!root.has("error")) { "A Wikipédia não conseguiu concluir a consulta. Tenta mais tarde." }
        val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        return (0 until pages.length()).mapNotNull { i ->
            val page = pages.getJSONObject(i)
            val revision = page.optJSONArray("revisions")?.optJSONObject(0)
            val id = page.optLong("pageid", -1); val rev = revision?.optLong("revid", -1) ?: -1
            val title = page.optString("title").take(200)
            val text = page.optString("extract").trim().take(8000)
            if (id <= 0 || rev <= 0 || title.isBlank() || text.isBlank()) null
            else WikiPage(id, title, text, rev, revision?.optString("timestamp").orEmpty())
        }.take(20)
    }
}

/** Fixed HTTPS provider. Neither a source URL nor conversation data can select an endpoint. */
class HttpsWikiTransport(private val permitted: () -> Boolean) : WikiTransport {
    override suspend fun get(parameters: Map<String, String>): String = withContext(Dispatchers.IO) {
        check(permitted()) { "Pesquisa online desligada ou sem ligação à Internet. A consulta fica local." }
        val query = parameters.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val connection = URL("https://pt.wikipedia.org/w/api.php?$query").openConnection() as HttpURLConnection
        connection.connectTimeout = 6000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "AtlasPersonal/0.4 (Android; https://github.com/efog82/Atlas)")
        connection.setRequestProperty("Accept", "application/json")
        try {
            currentCoroutineContext().ensureActive()
            check(permitted()) { "Pesquisa online desligada." }
            check(connection.responseCode == 200) { "Não foi possível consultar a Wikipédia (HTTP ${connection.responseCode}). Os dados locais continuam disponíveis." }
            require(connection.contentLengthLong <= 2_000_000) { "Resposta online demasiado grande." }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive(); check(permitted()) { "Pesquisa online desligada." }
                    val count = input.read(buffer); if (count < 0) break
                    require(output.size() + count <= 2_000_000) { "Resposta online demasiado grande." }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}
