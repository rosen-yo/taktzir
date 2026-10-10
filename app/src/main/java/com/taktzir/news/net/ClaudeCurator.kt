package com.taktzir.news.net

import com.taktzir.news.data.Category
import com.taktzir.news.domain.Candidate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CuratedPick(
    val index: Int,
    val importance: Int,
    val title: String,
    val summary: String,
)

class CuratorException(message: String) : Exception(message)

/**
 * The "editor": given the heuristic short-list, Claude picks what is genuinely important or enriching,
 * merges duplicates, and writes a Hebrew headline + a 2–3 sentence summary for each pick.
 */
object ClaudeCurator {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val dateFmt = DateTimeFormatter.ofPattern("EEE dd/MM HH:mm").withZone(ZoneId.of("Asia/Jerusalem"))

    suspend fun curate(
        apiKey: String,
        model: String,
        category: Category,
        candidates: List<Candidate>,
        alreadyInFeed: List<String>,
        maxPicks: Int,
        perDay: Int,
    ): List<CuratedPick> {
        if (candidates.isEmpty()) return emptyList()

        val system = """
            You are the editor of a personal daily news briefing for an informed Israeli reader who wants to stay
            up to date without being overloaded. Quality over quantity. You write in natural, fluent Hebrew.
        """.trimIndent()

        val list = buildString {
            candidates.forEachIndexed { i, c ->
                append("[").append(i).append("] ")
                append(c.item.source.name).append(" | ")
                append(dateFmt.format(Instant.ofEpochMilli(c.item.publishedAt)))
                if (c.coverage > 1) append(" | covered by ").append(c.coverage).append(" sources")
                append("\nTitle: ").append(c.item.title)
                if (c.item.snippet.isNotBlank()) append("\nSnippet: ").append(c.item.snippet.take(400))
                append("\n\n")
            }
        }

        val already = if (alreadyInFeed.isEmpty()) "(none)" else alreadyInFeed.take(60).joinToString("\n") { "- $it" }

        val user = """
            Topic tab: ${category.title}
            What this tab is about: ${category.focus}

            Below are candidate articles from quality sources (numbered). Choose the ones that truly deserve a place
            in this tab: important developments the reader should know, or genuinely enriching analysis.

            Rules:
            - Pick at most $maxPicks items in total, and roughly no more than $perDay per publication day.
              Fewer is fine if the rest is not worth reading. Never pad.
            - The same story from several sources = pick ONE (the most informative source). Wide coverage signals importance.
            - Skip anything off-topic for this tab, trivia, celebrity/lifestyle, sports, promotional content,
              live-blog fragments, routine minor updates, and opinion pieces without real insight.
            - Skip stories already in the feed (listed below), including new versions of the same story,
              unless there is a major new development.
            - importance: 1-10. 9-10 = major event everyone should know today; 7-8 = clearly significant;
              5-6 = worth knowing / enriching; below 5 = don't pick it.
            - title: a clear, neutral Hebrew headline (max ~12 words), no clickbait.
            - summary: 2-3 short Hebrew sentences with the key facts (who, what, why it matters, key numbers).
              Base it only on the title and snippet; do not invent details.
        """.trimIndent() +
            "\n\nAlready in the feed:\n" + already +
            "\n\nCandidates:\n" + list +
            "Respond with JSON only, no other text, in exactly this shape:\n" +
            "{\"items\":[{\"id\":0,\"importance\":8,\"title\":\"...\",\"summary\":\"...\"}]}"

        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("system", system)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
        }.toString()

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val (code, text) = Http.slowClient.newCall(request).await().use { it.code to (it.body?.string().orEmpty()) }
        if (code != 200) {
            val msg = runCatching {
                json.parseToJsonElement(text).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
            }.getOrDefault("HTTP $code")
            throw CuratorException(msg)
        }

        val content = json.parseToJsonElement(text).jsonObject["content"]!!.jsonArray
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.content == "text" }
            .joinToString("") { it["text"]!!.jsonPrimitive.content }

        return parsePicks(content, candidates.size)
    }

    private fun parsePicks(text: String, candidateCount: Int): List<CuratedPick> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw CuratorException("Unexpected AI response")
        val root = json.parseToJsonElement(text.substring(start, end + 1)).jsonObject
        val items = root["items"]?.jsonArray ?: return emptyList()
        return items.mapNotNull { el ->
            val o: JsonObject = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            if (id !in 0 until candidateCount) return@mapNotNull null
            val title = o["title"]?.jsonPrimitive?.content?.trim().orEmpty()
            val summary = o["summary"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val importance = runCatching { o["importance"]!!.jsonPrimitive.int }.getOrDefault(5).coerceIn(1, 10)
            CuratedPick(id, importance, title, summary)
        }.distinctBy { it.index }
    }
}
