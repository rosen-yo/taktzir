package com.taktzir.news.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taktzir.news.data.Article
import com.taktzir.news.data.Category
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val groups = remember(history) {
        history.groupBy { dayOf(it.readAt ?: it.publishedAt) }.toList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SubScreenHeader("היסטוריה", onBack)
            if (history.isEmpty()) {
                Text(
                    "עדיין לא קראת כתבות. כתבות שתקרא יופיעו כאן.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
                return@Column
            }
            Text(
                "החלקה על כתבה מחזירה אותה לרשימה",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                groups.forEach { (day, list) ->
                    item(key = "h_$day") {
                        Text(
                            dayLabel(day),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { it.id }) { article ->
                        SwipeAction(
                            label = "החזר לרשימה",
                            icon = Icons.AutoMirrored.Outlined.Undo,
                            onSwiped = {
                                vm.markUnread(article)
                                scope.launch {
                                    val r = snackbar.showSnackbar("הוחזר לרשימה", actionLabel = "ביטול")
                                    if (r == SnackbarResult.ActionPerformed) vm.markRead(article)
                                }
                            },
                        ) {
                            HistoryItem(article) { openInBrowser(context, article.url) }
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

@Composable
private fun HistoryItem(article: Article, onOpen: () -> Unit) {
    val categoryTitle = Category.fromKey(article.category)?.title.orEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            listOf(categoryTitle, article.source, article.readAt?.let { "נקרא ב-" + clock(it) })
                .filter { !it.isNullOrBlank() }
                .joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            article.title,
            style = MaterialTheme.typography.titleMedium.auto,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
