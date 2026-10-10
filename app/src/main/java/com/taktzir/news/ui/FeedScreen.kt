package com.taktzir.news.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taktzir.news.data.Article
import com.taktzir.news.data.Category
import com.taktzir.news.net.MarketQuote
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(vm: MainViewModel, onHistory: () -> Unit, onSettings: () -> Unit) {
    val category by vm.category.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val articles by vm.articles.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val markets by vm.markets.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val open: (Article) -> Unit = { a ->
        vm.markRead(a)
        openInBrowser(context, a.url)
    }
    val swipeRead: (Article) -> Unit = { a ->
        vm.markRead(a)
        scope.launch {
            val r = snackbar.showSnackbar("סומן כנקרא", actionLabel = "ביטול", withDismissAction = false)
            if (r == SnackbarResult.ActionPerformed) vm.markUnread(a)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FeedHeader(onHistory, onSettings)
            CategoryTabs(category, counts, vm::selectCategory)
            RangeToggle(range, vm::setRange)

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val sections = remember(articles, range) { buildSections(articles, range) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    if (category == Category.ECONOMY && markets.isNotEmpty()) {
                        item(key = "markets") { MarketStrip(markets) }
                    }
                    if (!settings.hasAi) {
                        item(key = "ai_hint") { AiHint(onSettings) }
                    }
                    if (articles.isEmpty()) {
                        item(key = "empty") { EmptyState(range, refreshing) }
                    }
                    sections.forEach { (label, list) ->
                        if (label != null) {
                            item(key = "h_$label") { SectionLabel(label) }
                        }
                        items(list, key = { it.id }) { article ->
                            SwipeAction(label = "נקרא", icon = Icons.Outlined.Done, onSwiped = { swipeRead(article) }) {
                                ArticleItem(
                                    article = article,
                                    onOpen = { open(article) },
                                    onQuickRead = { vm.openReader(article) },
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** In "week" mode, groups by day (today, yesterday, …); inside a group the most important comes first. */
private fun buildSections(articles: List<Article>, range: Range): List<Pair<String?, List<Article>>> {
    val ordered = compareByDescending<Article> { it.importance }.thenByDescending { it.publishedAt }
    if (range == Range.TODAY) return listOf(null to articles.sortedWith(ordered))
    return articles.groupBy { dayOf(it.publishedAt) }
        .toSortedMap(compareByDescending<java.time.LocalDate> { it })
        .map { (day, list) -> dayLabel(day) to list.sortedWith(ordered) }
}

@Composable
private fun FeedHeader(onHistory: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("תקציר", style = MaterialTheme.typography.headlineMedium)
            Text(
                todayLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onHistory) {
            Icon(Icons.Outlined.History, contentDescription = "היסטוריה", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "הגדרות", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryTabs(selected: Category, counts: Map<String, Int>, onSelect: (Category) -> Unit) {
    val index = Category.entries.indexOf(selected)
    ScrollableTabRow(
        selectedTabIndex = index,
        edgePadding = 12.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        divider = { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant) },
        indicator = { positions ->
            if (index < positions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[index]),
                    height = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) {
        Category.entries.forEach { cat ->
            val count = counts[cat.key] ?: 0
            Tab(
                selected = cat == selected,
                onClick = { onSelect(cat) },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.title, style = MaterialTheme.typography.titleSmall)
                        if (count > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun RangeToggle(range: Range, onChange: (Range) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(Range.WEEK to "השבוע", Range.TODAY to "היום").forEach { (r, label) ->
            val selected = r == range
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onChange(r) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleItem(article: Article, onOpen: () -> Unit, onQuickRead: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onOpen, onLongClick = onQuickRead)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (article.importance >= 8) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = article.source + "  ·  " + relativeTime(article.publishedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onQuickRead, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.AutoStories,
                    contentDescription = "קריאה מהירה",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = article.title,
            style = MaterialTheme.typography.titleMedium.auto,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(end = 8.dp),
        )
        if (article.summary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = article.summary,
                style = MaterialTheme.typography.bodyMedium.auto,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun MarketStrip(quotes: List<MarketQuote>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        quotes.forEach { q ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(q.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(
                    String.format(Locale.US, "%,.0f", q.price),
                    style = MaterialTheme.typography.titleMedium,
                )
                val up = q.changePercent >= 0
                Text(
                    String.format(Locale.US, "%s%.2f%%", if (up) "+" else "−", kotlin.math.abs(q.changePercent)),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (up) MarketColors.up else MarketColors.down,
                )
            }
        }
    }
}

@Composable
private fun AiHint(onSettings: () -> Unit) {
    Text(
        text = "טיפ: הוספת מפתח Claude בהגדרות תיתן סיכומים בעברית ובחירה חכמה יותר של הכתבות",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSettings)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun EmptyState(range: Range, refreshing: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (refreshing) "אוסף את החדשות…" else "אין כאן כתבות שלא קראת",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (!refreshing) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (range == Range.TODAY) "אפשר לעבור ל״השבוע״, או למשוך למטה כדי לרענן" else "משיכה למטה תרענן את הרשימה",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
