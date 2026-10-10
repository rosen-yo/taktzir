package com.taktzir.news.domain

import com.taktzir.news.data.Category
import com.taktzir.news.net.RawItem
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** A story: the best representative item plus how widely it was covered. */
data class Candidate(
    val item: RawItem,
    /** Number of distinct sources reporting the same story. */
    val coverage: Int,
    val score: Double,
)

/**
 * First-pass ranking that runs without any AI. It answers "what is probably important":
 *  1. Stories covered by several independent quality sources matter more (clustering by title similarity).
 *  2. Better sources weigh more.
 *  3. Fresh news ranks higher (half-life of about a day).
 *  4. Topic keywords boost relevance; filler (podcasts, quizzes, live blogs, deals) is penalised.
 */
object Ranker {

    private val stopWords = setOf(
        "the", "a", "an", "of", "to", "in", "on", "for", "and", "or", "is", "are", "was", "with", "as", "at", "by",
        "from", "after", "over", "its", "it", "be", "has", "have", "new", "says", "say", "will", "that", "this",
        "של", "את", "על", "עם", "לא", "גם", "כי", "הוא", "היא", "זה", "אחרי", "בין", "או", "כל", "עוד", "יותר",
    )

    private val fillerMarkers = listOf(
        "podcast", "quiz", "crossword", "newsletter", "live updates", "live:", "watch:", "video:", "photos:",
        "horoscope", "deal of", "best deals", "sponsored", "webinar", "in pictures", "פודקאסט", "חידון",
        "בשידור חי", "צפו", "גלריה", "מבצע",
    )

    fun tokens(text: String): Set<String> =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }
            .toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    fun keywordHits(category: Category, text: String): Int {
        val lower = " " + text.lowercase() + " "
        return category.keywords.count { lower.contains(it) }
    }

    fun rank(category: Category, items: List<RawItem>, requireKeywordsForGeneral: Boolean, now: Long): List<Candidate> {
        // Dedupe by link.
        val unique = items.distinctBy { normalizeUrl(it.link) }

        val relevant = unique.filter { item ->
            val text = item.title + " " + item.snippet
            !(requireKeywordsForGeneral && item.source.general && keywordHits(category, text) == 0)
        }

        // Greedy clustering on title tokens.
        data class Cluster(val members: MutableList<RawItem>, val tokens: Set<String>)
        val clusters = mutableListOf<Cluster>()
        for (item in relevant.sortedByDescending { it.source.weight }) {
            val t = tokens(item.title)
            val match = clusters.firstOrNull { jaccard(it.tokens, t) >= 0.34 }
            if (match != null) match.members += item else clusters += Cluster(mutableListOf(item), t)
        }

        return clusters.map { cluster ->
            val rep = cluster.members.maxWith(
                compareBy<RawItem>({ it.source.weight }, { it.snippet.length.coerceAtMost(300) }, { it.publishedAt })
            )
            val coverage = cluster.members.map { it.source.name }.distinct().size
            Candidate(rep, coverage, score(category, rep, coverage, now))
        }.sortedByDescending { it.score }
    }

    private fun score(category: Category, item: RawItem, coverage: Int, now: Long): Double {
        val text = item.title + " " + item.snippet
        val ageHours = max(0.0, (now - item.publishedAt) / 3_600_000.0)
        val recency = exp(-ageHours / 36.0) // ~25h half-life
        val coverageScore = ln(1.0 + coverage) / ln(2.0) // 1 source = 1, 3 sources = 2, 7 = 3
        val keywords = min(3, keywordHits(category, text)) * 0.7
        val lowerTitle = item.title.lowercase()
        val filler = if (fillerMarkers.any { lowerTitle.contains(it) }) -3.0 else 0.0
        val thin = if (item.snippet.length < 40) -0.5 else 0.0
        return item.source.weight * 2.0 + coverageScore * 3.0 + recency * 2.5 + keywords + filler + thin
    }

    /** Maps a heuristic score to the 1–10 importance scale used in the UI. */
    fun importanceFromScore(score: Double): Int = (score - 2.0).toInt().coerceIn(1, 10)

    fun normalizeUrl(url: String): String =
        url.trim()
            .substringBefore('#')
            .replace(Regex("[?&](utm_[^&]*|fbclid=[^&]*|ref=[^&]*)"), "")
            .removeSuffix("/")
            .replaceFirst("http://", "https://")
            .lowercase()
}
