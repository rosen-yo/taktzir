package com.taktzir.news.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.taktzir.news.work.NotifyWorker

enum class Screen { FEED, HISTORY, SETTINGS }

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.selectCategoryByKey(intent?.getStringExtra(NotifyWorker.EXTRA_CATEGORY))
        setContent {
            TaktzirTheme { AppRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.selectCategoryByKey(intent.getStringExtra(NotifyWorker.EXTRA_CATEGORY))
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.FEED) }
    BackHandler(enabled = screen != Screen.FEED) { screen = Screen.FEED }

    when (screen) {
        Screen.FEED -> FeedScreen(
            vm = vm,
            onHistory = { screen = Screen.HISTORY },
            onSettings = { screen = Screen.SETTINGS },
        )
        Screen.HISTORY -> HistoryScreen(vm = vm, onBack = { screen = Screen.FEED })
        Screen.SETTINGS -> SettingsScreen(vm = vm, onBack = { screen = Screen.FEED })
    }

    ReaderSheet(vm)
}
