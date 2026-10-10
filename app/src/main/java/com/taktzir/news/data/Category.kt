package com.taktzir.news.data

/**
 * The five topics shown as tabs. [focus] is what the AI editor is told to look for;
 * [keywords] boost relevance in the heuristic ranker (and gate general-news feeds
 * when no API key is set).
 */
enum class Category(
    val key: String,
    val title: String,
    val focus: String,
    val keywords: List<String>,
) {
    POLITICS(
        key = "politics",
        title = "פוליטיקה",
        focus = "Israeli politics (government, Knesset, coalition, elections, Supreme Court, major policy decisions) " +
            "and the most consequential world politics, especially the United States.",
        keywords = listOf(
            "knesset", "coalition", "netanyahu", "election", "minister", "government", "supreme court", "congress",
            "senate", "white house", "trump", "parliament", "vote", "bill", "opposition", "prime minister",
            "כנסת", "קואליציה", "נתניהו", "בחירות", "שר", "ממשלה", "בג\"ץ", "אופוזיציה", "הצבעה", "חוק", "ראש הממשלה",
        ),
    ),
    ECONOMY(
        key = "economy",
        title = "כלכלה",
        focus = "Markets with emphasis on the S&P 500, NASDAQ and the Tel Aviv Stock Exchange; macro drivers " +
            "(interest rates, inflation, the Fed, Bank of Israel, earnings of major companies); and genuinely " +
            "enriching explainers about economics, investing and capital markets.",
        keywords = listOf(
            "s&p", "nasdaq", "dow", "stocks", "shares", "market", "fed", "interest rate", "inflation", "earnings",
            "bond", "yield", "recession", "gdp", "tase", "bank of israel", "ipo", "investor",
            "בורסה", "מניות", "מניה", "ריבית", "אינפלציה", "מדד", "ת\"א 35", "וול סטריט", "נאסד\"ק", "בנק ישראל",
            "אג\"ח", "משקיעים", "דוחות", "הנפקה", "שקל", "דולר",
        ),
    ),
    CYBER(
        key = "cyber",
        title = "סייבר",
        focus = "Malware and ransomware, major cyber attacks and data breaches, actively exploited critical " +
            "vulnerabilities, state-sponsored threat actors (Iran, Russia, China, North Korea), and notable " +
            "developments in security technology.",
        keywords = listOf(
            "malware", "ransomware", "breach", "hack", "hacker", "vulnerability", "zero-day", "exploit", "apt",
            "phishing", "botnet", "cve", "threat actor", "backdoor", "spyware", "ddos", "cyber",
            "סייבר", "פריצה", "האקרים", "כופר", "תקיפת", "דליפת", "חולשה",
        ),
    ),
    SECURITY(
        key = "security",
        title = "ביטחון",
        focus = "The IDF and Israel's security situation (all fronts), and major defense issues involving the " +
            "US, Russia and the war in Ukraine, Europe and NATO, Iran, China, and other key states; significant " +
            "defense technology and procurement.",
        keywords = listOf(
            "idf", "military", "army", "missile", "drone", "hezbollah", "hamas", "iran", "houthi", "nato",
            "pentagon", "ukraine", "russia", "defense", "strike", "troops", "air force", "navy", "ceasefire",
            "צה\"ל", "צבא", "טיל", "טילים", "כטב\"ם", "חיזבאללה", "חמאס", "איראן", "חות'ים", "תקיפה",
            "ביטחון", "מערכת הביטחון", "שב\"כ", "פיקוד", "הפסקת אש", "עזה", "לבנון",
        ),
    ),
    GEOPOLITICS(
        key = "geopolitics",
        title = "גאופוליטיקה",
        focus = "Relations between states: alliances, diplomacy, summits, sanctions, trade wars, great-power " +
            "competition, and strategic analysis that helps an informed reader understand the world.",
        keywords = listOf(
            "diplomacy", "diplomatic", "sanctions", "summit", "alliance", "relations", "treaty", "tariff",
            "trade war", "china", "beijing", "moscow", "eu", "un ", "united nations", "foreign minister",
            "normalization", "geopolitic", "strategy",
            "דיפלומטי", "סנקציות", "פסגה", "ברית", "יחסים", "הסכם", "מכסים", "סין", "האו\"ם", "נורמליזציה",
        ),
    );

    companion object {
        fun fromKey(key: String?): Category? = entries.firstOrNull { it.key == key }
    }
}
