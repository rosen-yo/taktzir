package com.taktzir.news.data

/**
 * A curated RSS feed.
 * [weight] reflects editorial quality/reliability (1.0 = baseline).
 * [general] marks broad news feeds that cover many topics; without AI, items from these
 * feeds must match the category's keywords to be considered.
 */
data class Source(
    val name: String,
    val url: String,
    val weight: Double,
    val general: Boolean = false,
)

object Sources {

    // Feeds shared between several categories (fetched once per refresh).
    private val toi = Source("Times of Israel", "https://www.timesofisrael.com/feed/", 1.2, general = true)
    private val jpost = Source("Jerusalem Post", "https://www.jpost.com/rss/rssfeedsisraelnews.aspx", 1.0, general = true)
    private val ynet = Source("ynet", "https://www.ynet.co.il/Integration/StoryRss2.xml", 1.1, general = true)
    private val maariv = Source("מעריב", "https://www.maariv.co.il/Rss/RssChadashot", 0.9, general = true)
    private val globesHome = Source("גלובס", "https://www.globes.co.il/webservice/rss/rssfeeder.asmx/FeederNode?iID=2", 1.1, general = true)
    private val bbcWorld = Source("BBC", "https://feeds.bbci.co.uk/news/world/rss.xml", 1.2, general = true)
    private val npr = Source("NPR", "https://feeds.npr.org/1004/rss.xml", 1.0, general = true)
    private val guardianWorld = Source("The Guardian", "https://www.theguardian.com/world/rss", 1.0, general = true)
    private val alMonitor = Source("Al-Monitor", "https://www.al-monitor.com/rss", 1.1)
    private val warOnTheRocks = Source("War on the Rocks", "https://warontherocks.com/feed/", 1.1)

    val byCategory: Map<Category, List<Source>> = mapOf(
        Category.POLITICS to listOf(
            toi, jpost, ynet, maariv, globesHome,
            Source("BBC", "https://feeds.bbci.co.uk/news/politics/rss.xml", 1.1),
            Source("NPR", "https://feeds.npr.org/1014/rss.xml", 1.1),
            Source("Politico", "https://rss.politico.com/politics-news.xml", 1.1),
        ),
        Category.ECONOMY to listOf(
            Source("גלובס · שוק ההון", "https://www.globes.co.il/webservice/rss/rssfeeder.asmx/FeederNode?iID=585", 1.3),
            Source("ynet כלכלה", "https://www.ynet.co.il/Integration/StoryRss6.xml", 1.0),
            globesHome,
            Source("CNBC", "https://www.cnbc.com/id/20910258/device/rss/rss.html", 1.1),
            Source("CNBC Markets", "https://www.cnbc.com/id/10000664/device/rss/rss.html", 1.1),
            Source("MarketWatch", "https://feeds.content.dowjones.io/public/rss/mw_topstories", 1.1),
            Source("BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", 1.0),
            Source("NPR Economy", "https://feeds.npr.org/1017/rss.xml", 1.0),
        ),
        Category.CYBER to listOf(
            Source("The Record", "https://therecord.media/feed", 1.3),
            Source("BleepingComputer", "https://www.bleepingcomputer.com/feed/", 1.3),
            Source("Krebs on Security", "https://krebsonsecurity.com/feed/", 1.3),
            Source("The Hacker News", "https://feeds.feedburner.com/TheHackersNews", 1.1),
            Source("SecurityWeek", "https://www.securityweek.com/feed/", 1.1),
            Source("Dark Reading", "https://www.darkreading.com/rss.xml", 1.0),
            Source("Unit 42", "https://unit42.paloaltonetworks.com/feed/", 1.1),
            Source("Check Point Research", "https://research.checkpoint.com/feed/", 1.1),
            Source("Schneier on Security", "https://www.schneier.com/feed/atom/", 1.0),
        ),
        Category.SECURITY to listOf(
            toi, jpost, ynet, maariv, bbcWorld, alMonitor, warOnTheRocks,
            Source("Defense News", "https://www.defensenews.com/arc/outboundfeeds/rss/?outputType=xml", 1.2),
            Source("Breaking Defense", "https://breakingdefense.com/feed/", 1.1),
        ),
        Category.GEOPOLITICS to listOf(
            Source("Foreign Policy", "https://foreignpolicy.com/feed/", 1.2),
            Source("The Diplomat", "https://thediplomat.com/feed/", 1.1),
            alMonitor, bbcWorld, guardianWorld, npr, warOnTheRocks,
        ),
    )
}
