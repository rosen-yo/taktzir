package com.taktzir.news

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.taktzir.news.data.AppDatabase
import com.taktzir.news.data.SettingsStore
import com.taktzir.news.domain.NewsRepository
import com.taktzir.news.work.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NewsApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var repository: NewsRepository
        private set
    lateinit var db: AppDatabase
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.create(this)
        settings = SettingsStore(this)
        repository = NewsRepository(db, settings)

        val channel = NotificationChannel(
            CHANNEL_ID, "עדכונים חשובים", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "התראות על כתבות חשובות בנושאים שבחרת" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        Scheduler.ensurePeriodicRefresh(this)
        appScope.launch { Scheduler.scheduleNotifications(this@NewsApp, settings.current().notifications) }
    }

    companion object {
        const val CHANNEL_ID = "important_updates"
    }
}
