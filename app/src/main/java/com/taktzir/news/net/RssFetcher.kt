package com.taktzir.news.net

import android.util.Xml
import com.taktzir.news.data.Source
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RawItem(
    val title: String,
    val link: String,
    val snippet: String,
    val publishedAt: Long,
    val source: Source,
)

object RssFetcher {

    suspend fun fetch(source: Source): List<RawItem> {
        val xml = Http.client.fetchText(Http.get(source.url)) ?: return emptyList()
        return runCatching { parse(xml, source) }.getOrDefault(emptyList())
    }

    /** Parses RSS 2.0 and Atom. Tolerant: keeps whatever was parsed before an error. */
    fun parse(xml: String, source: Source): List<RawItem> {
        val out = mutableListOf<RawItem>()
        val parser = Xml.newPullParser()
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml.trimStart('﻿', ' ', '\n', '\r', '\t')))

        var inItem = false
        var title = ""
        var link = ""
        var desc = ""
        var date = ""

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "item" || name == "entry") {
                            inItem = true
                            title = ""; link = ""; desc = ""; date = ""
                        } else if (inItem) {
                            when (name) {
                                "title" -> title = parser.safeText()
                                "link" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    val rel = parser.getAttributeValue(null, "rel")
                                    if (href != null) {
                                        if (link.isEmpty() || rel == null || rel == "alternate") link = href
                                    } else {
                                        val text = parser.safeText()
                                        if (text.isNotBlank()) link = text
                                    }
                                }
                                "guid", "id" -> {
                                    val text = parser.safeText()
                                    if (link.isEmpty() && text.startsWith("http")) link = text
                                }
                                "description", "summary" -> if (desc.isEmpty()) desc = parser.safeText()
                                "content:encoded", "content" -> {
                                    val text = parser.safeText()
                                    if (desc.isEmpty()) desc = text
                                }
                                "pubdate", "published", "dc:date", "updated" ->
                                    if (date.isEmpty()) date = parser.safeText()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase()
                        if ((name == "item" || name == "entry") && inItem) {
                            inItem = false
                            val cleanTitle = clean(title)
                            if (cleanTitle.isNotBlank() && link.startsWith("http")) {
                                out += RawItem(
                                    title = cleanTitle,
                                    link = link.trim(),
                                    snippet = clean(desc).take(600),
                                    publishedAt = parseDate(date) ?: System.currentTimeMillis(),
                                    source = source,
                                )
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Malformed tail – keep what we have.
        }
        return out
    }

    private fun XmlPullParser.safeText(): String = runCatching { nextText() }.getOrDefault("")

    /** Strips HTML and collapses whitespace. */
    fun clean(html: String): String {
        if (html.isBlank()) return ""
        val text = if (html.contains('<') || html.contains('&')) Jsoup.parse(html).text() else html
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private val rfcFallbacks = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm zzz",
        "dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd HH:mm:ss",
    )

    fun parseDate(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        runCatching { return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        for (pattern in rfcFallbacks) {
            val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).parse(s)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}
