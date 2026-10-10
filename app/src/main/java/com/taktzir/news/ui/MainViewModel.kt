package com.taktzir.news.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taktzir.news.NewsApp
import com.taktzir.news.data.AppSettings
import com.taktzir.news.data.Article
import com.taktzir.news.data.Category
import com.taktzir.news.domain.NewsRepository
import com.taktzir.news.net.MarketFetcher
import com.taktzir.news.net.MarketQuote
import com.taktzir.news.net.ReaderBlock
import com.taktzir.news.net.ReaderExtractor
import com.taktzir.news.work.Scheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Range { TODAY, WEEK }

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Ready(val blocks: List<ReaderBlock>) : ReaderState
    data object Unavailable : ReaderState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val newsApp = app as NewsApp
    private val repo = newsApp.repository
    private val dao = newsApp.db.articles()
    private val store = newsApp.settings

    private val _category = MutableStateFlow(Category.POLITICS)
    val category: StateFlow<Category> = _category.asStateFlow()

    private val _range = MutableStateFlow(Range.WEEK)
    val range: StateFlow<Range> = _range.asStateFlow()

    /** Bumped periodically so "today" rolls over at midnight. */
    private val clock = MutableStateFlow(System.currentTimeMillis())

    private fun since(range: Range): Long =
        if (range == Range.TODAY) repo.startOfToday() else System.currentTimeMillis() - NewsRepository.RETENTION_MS

    val articles: StateFlow<List<Article>> =
        combine(_category, _range, clock) { c, r, _ -> c to since(r) }
            .flatMapLatest { (c, s) -> dao.observeAvailable(c.key, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val counts: StateFlow<Map<String, Int>> =
        combine(_range, clock) { r, _ -> since(r) }
            .flatMapLatest { s -> dao.observeCounts(s) }
            .map { list -> list.associate { it.category to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val history: StateFlow<List<Article>> = dao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = store.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _markets = MutableStateFlow<List<MarketQuote>>(emptyList())
    val markets: StateFlow<List<MarketQuote>> = _markets.asStateFlow()

    private val _reader = MutableStateFlow<Pair<Article, ReaderState>?>(null)
    val reader: StateFlow<Pair<Article, ReaderState>?> = _reader.asStateFlow()
    private var readerJob: Job? = null

    init {
        viewModelScope.launch {
            val last = store.current().lastRefresh
            if (System.currentTimeMillis() - last > 30 * 60 * 1000L) refresh()
        }
        loadMarkets()
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                clock.value = System.currentTimeMillis()
            }
        }
    }

    fun selectCategory(c: Category) {
        _category.value = c
    }

    fun selectCategoryByKey(key: String?) {
        Category.fromKey(key)?.let { _category.value = it }
    }

    fun setRange(r: Range) {
        _range.value = r
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                val result = repo.refresh()
                if (result.aiError != null) _message.value = "סיכומי ה-AI לא זמינים כרגע: ${result.aiError}"
            } catch (e: Exception) {
                _message.value = "לא הצלחתי לרענן. בדוק את החיבור לאינטרנט."
            } finally {
                _refreshing.value = false
            }
        }
        loadMarkets()
    }

    private fun loadMarkets() {
        viewModelScope.launch { runCatching { MarketFetcher.fetch() }.onSuccess { if (it.isNotEmpty()) _markets.value = it } }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun markRead(article: Article) {
        viewModelScope.launch { repo.markRead(article) }
    }

    fun markUnread(article: Article) {
        viewModelScope.launch { repo.markUnread(article) }
    }

    fun openReader(article: Article) {
        readerJob?.cancel()
        _reader.value = article to ReaderState.Loading
        markRead(article)
        readerJob = viewModelScope.launch {
            val blocks = runCatching { ReaderExtractor.extract(article.url) }.getOrNull()
            _reader.value = article to (if (blocks.isNullOrEmpty()) ReaderState.Unavailable else ReaderState.Ready(blocks))
        }
    }

    fun closeReader() {
        readerJob?.cancel()
        _reader.value = null
    }

    // ---- Settings ----

    fun saveApiKey(key: String) {
        viewModelScope.launch { store.setApiKey(key) }
    }

    fun saveModel(model: String) {
        viewModelScope.launch { store.setModel(model) }
    }

    fun savePerDay(n: Int) {
        viewModelScope.launch { store.setPerDay(n) }
    }

    fun saveNotifications(config: Map<Category, List<String>>) {
        viewModelScope.launch {
            store.setNotifications(config)
            Scheduler.scheduleNotifications(getApplication(), config)
        }
    }
}
