package com.taktzir.news.net

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class MarketQuote(
    val name: String,
    val price: Double,
    val changePercent: Double,
)

/** Index quotes for the economy tab (Yahoo Finance public chart endpoint, no key needed). */
object MarketFetcher {

    private val symbols = listOf(
        "S&P 500" to "^GSPC",
        "NASDAQ" to "^IXIC",
        "ת\"א 35" to "TA35.TA",
    )

    suspend fun fetch(): List<MarketQuote> = coroutineScope {
        symbols.map { (name, symbol) -> async { quote(name, symbol) } }.mapNotNull { it.await() }
    }

    private suspend fun quote(name: String, symbol: String): MarketQuote? {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/" +
            URLEncoder.encode(symbol, "UTF-8") + "?range=1d&interval=1d"
        val text = Http.client.fetchText(Http.get(url)) ?: return null
        return runCatching {
            val meta = Json.parseToJsonElement(text).jsonObject["chart"]!!.jsonObject["result"]!!
                .jsonArray[0].jsonObject["meta"]!!.jsonObject
            val price = meta["regularMarketPrice"]!!.jsonPrimitive.double
            val prev = meta["chartPreviousClose"]?.jsonPrimitive?.doubleOrNull
                ?: meta["previousClose"]?.jsonPrimitive?.doubleOrNull
                ?: return null
            MarketQuote(name, price, (price - prev) / prev * 100.0)
        }.getOrNull()
    }
}
