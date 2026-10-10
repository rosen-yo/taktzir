package com.taktzir.news.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** A block of readable text in quick-read mode. */
data class ReaderBlock(val text: String, val isHeading: Boolean)

/**
 * Lightweight "reader mode": downloads the page and extracts the main text
 * (the element holding the most paragraph text). Returns null for paywalled
 * or script-rendered pages, in which case the UI falls back to the summary.
 */
object ReaderExtractor {

    private const val JUNK = "script, style, noscript, iframe, form, nav, header, footer, aside, button, svg, " +
        "figure, [role=navigation], [aria-hidden=true], .share, .social, .related, .comments, .newsletter, " +
        ".advertisement, .ad, .ads, .promo, .subscribe, .paywall"

    suspend fun extract(url: String): List<ReaderBlock>? {
        val html = Http.client.fetchText(Http.get(url)) ?: return null
        return withContext(Dispatchers.Default) { runCatching { parse(html, url) }.getOrNull() }
    }

    fun parse(html: String, url: String): List<ReaderBlock>? {
        val doc = Jsoup.parse(html, url)
        doc.select(JUNK).remove()

        val root: Element = doc.selectFirst("article")?.takeIf { paragraphText(it) > 600 }
            ?: bestContainer(doc.body() ?: return null)
            ?: return null

        val blocks = mutableListOf<ReaderBlock>()
        val seen = HashSet<String>()
        for (el in root.select("h2, h3, p, li, blockquote")) {
            // Skip <p> nested inside an <li>/<blockquote> we'll also visit.
            if (el.tagName() == "p" && el.parents().any { it.tagName() == "li" || it.tagName() == "blockquote" }) continue
            val text = el.text().replace(Regex("\\s+"), " ").trim()
            val heading = el.tagName() == "h2" || el.tagName() == "h3"
            val minLen = if (heading) 4 else 40
            if (text.length < minLen || !seen.add(text)) continue
            blocks += ReaderBlock(text, heading)
        }
        val total = blocks.filter { !it.isHeading }.sumOf { it.text.length }
        return if (total >= 400) blocks else null
    }

    private fun paragraphText(el: Element): Int =
        el.select("p").sumOf { p -> p.text().length.takeIf { it > 40 } ?: 0 }

    /** Classic readability heuristic: score parents by the paragraph text they contain. */
    private fun bestContainer(body: Element): Element? {
        val scores = HashMap<Element, Double>()
        for (p in body.select("p")) {
            val len = p.text().length
            if (len < 40) continue
            p.parent()?.let { scores[it] = (scores[it] ?: 0.0) + len }
            p.parent()?.parent()?.let { scores[it] = (scores[it] ?: 0.0) + len / 2.0 }
        }
        return scores.maxByOrNull { it.value }?.key
    }
}
