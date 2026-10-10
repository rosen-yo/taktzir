package com.taktzir.news.domain

import android.util.Log
import com.taktzir.news.data.AppDatabase
import com.taktzir.news.data.AppSettings
import com.taktzir.news.data.Article
import com.taktzir.news.data.Category
import com.taktzir.news.data.Seen
import com.taktzir.news.data.SettingsStore
import com.taktzir.news.data.Source
import com.taktzir.news.data.Sources
import com.taktzir.news.net.ClaudeCurator
import com.taktzir.news.net.CuratedPick
import com.taktzir.news.net.RawItem
import com.taktzir.news.net.RssFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RefreshResult(val added: Int, val aiError: String?)

class NewsRepository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
) {
    private val dao = db.articles()
    private val mutex = Mutex()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val RETENTION_MS = 7 * DAY_MS
        const val HISTORY_MS = 30 * DAY_MS
        private const val MAX_CANDIDATES = 40
        private const val MAX_CANDIDATES_FIRST_RUN = 70
        private const val TAG = "NewsRepository"
    }

    fun startOfToday(): Long = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Refreshes the given categories (all by default). Safe to call concurrently – runs are serialized. */
    suspend fun refresh(categories: List<Category> = Category.entries): RefreshResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cfg = settings.current()
            purge(now)

            // Fetch every needed feed once, in parallel.
            val feeds: List<Source> = categories.flatMap { Sources.byCategory[it].orEmpty() }.distinctBy { it.url }
            val fetched: Map<String, List<RawItem>> = coroutineScope {
                feeds.map { src -> async { src.url to RssFetcher.fetch(src) } }.awaitAll().toMap()
            }

            var added = 0
            var aiError: String? = null
            for (category in categories) {
                val items = Sources.byCategory[category].orEmpty().flatMap { fetched[it.url].orEmpty() }
                try {
                    added += refreshCategory(category, items, cfg, now)
                } catch (e: Exception) {
                    Log.w(TAG, "AI curation failed for ${category.key}", e)
                    aiError = e.message ?: e.javaClass.simpleName
                    // Fall back to heuristic picks so the tab is never empty because of the AI.
                    added += runCatching { refreshCategory(category, items, cfg.copy(apiKey = ""), now) }.getOrDefault(0)
                }
            }
            settings.setLastRefresh(now)
            RefreshResult(added, aiError)
        }
    }

    private suspend fun refreshCategory(category: Category, items: List<RawItem>, cfg: AppSettings, now: Long): Int {
        val windowStart = now - RETENTION_MS
        val fresh = items.filter { it.publishedAt in windowStart..(now + 60 * 60 * 1000) }
        if (fresh.isEmpty()) return 0

        val ranked = Ranker.rank(category, fresh, requireKeywordsForGeneral = !cfg.hasAi, now = now)

        // Drop what is already in the feed or was already evaluated for this category.
        val ids = ranked.map { articleId(it.item.link) }
        val existing = ids.chunked(500).flatMap { dao.existingIds(it) }.toSet()
        val seenKeys = ids.map { seenKey(category, it) }
        val seen = seenKeys.chunked(500).flatMap { dao.seenKeys(it) }.toSet()
        val newCandidates = ranked.filterIndexed { i, _ -> ids[i] !in existing && seenKeys[i] !in seen }
        if (newCandidates.isEmpty()) return 0

        val firstRun = dao.publishTimesSince(category.key, windowStart).isEmpty()
        val shortList = newCandidates.take(if (firstRun) MAX_CANDIDATES_FIRST_RUN else MAX_CANDIDATES)

        // Per-day budget: how many slots are still free on each day.
        val perDayCounts = dao.publishTimesSince(category.key, windowStart).groupingBy { dayOf(it) }.eachCount()
        val days = shortList.map { dayOf(it.item.publishedAt) }.distinct()
        val budget = days.sumOf { (cfg.perDay - (perDayCounts[it] ?: 0)).coerceAtLeast(0) }
        val maxPicks = (budget + 2).coerceAtMost(cfg.perDay * 7)

        val picks: List<CuratedPick> = if (cfg.hasAi) {
            ClaudeCurator.curate(
                apiKey = cfg.apiKey,
                model = cfg.model,
                category = category,
                candidates = shortList,
                alreadyInFeed = dao.titlesSince(category.key, now - 2 * DAY_MS),
                maxPicks = maxPicks,
                perDay = cfg.perDay,
            ).filter { it.importance >= 5 }
        } else {
            shortList.mapIndexed { i, c ->
                CuratedPick(
                    index = i,
                    importance = Ranker.importanceFromScore(c.score),
                    title = c.item.title,
                    summary = c.item.snippet.let { if (it.length > 280) it.take(277).trimEnd() + "…" else it },
                )
            }
        }

        // Enforce the per-day cap in code; allow up to 2 extra per day only for truly major news (9+).
        val accepted = mutableListOf<Article>()
        val usedPerDay = perDayCounts.toMutableMap()
        val extraPerDay = mutableMapOf<LocalDate, Int>()
        for (pick in picks.sortedByDescending { it.importance }) {
            val c = shortList[pick.index]
            val day = dayOf(c.item.publishedAt)
            val used = usedPerDay[day] ?: 0
            val fits = used < cfg.perDay
            val major = pick.importance >= 9 && (extraPerDay[day] ?: 0) < 2
            if (!fits && !major) continue
            if (!fits) extraPerDay[day] = (extraPerDay[day] ?: 0) + 1
            usedPerDay[day] = used + 1
            accepted += Article(
                id = articleId(c.item.link),
                category = category.key,
                title = pick.title,
                summary = pick.summary,
                url = c.item.link,
                source = c.item.source.name,
                publishedAt = c.item.publishedAt.coerceAtMost(now),
                fetchedAt = now,
                importance = pick.importance,
                coverage = c.coverage,
                aiCurated = cfg.hasAi,
            )
        }

        dao.insertAll(accepted)
        // Everything sent for evaluation counts as seen, so rejected items aren't re-evaluated.
        dao.insertSeen(shortList.map { Seen(seenKey(category, articleId(it.item.link)), now) })
        return accepted.size
    }

    private suspend fun purge(now: Long) {
        dao.purgeUnread(now - RETENTION_MS)
        dao.purgeHistory(now - HISTORY_MS)
        dao.purgeSeen(now - RETENTION_MS - DAY_MS)
    }

    private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun seenKey(category: Category, id: String) = category.key + ":" + id

    fun articleId(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(Ranker.normalizeUrl(url).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun markRead(article: Article) = dao.markRead(article.id, System.currentTimeMillis())
    suspend fun markUnread(article: Article) = dao.markUnread(article.id)
}
