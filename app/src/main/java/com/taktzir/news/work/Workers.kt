package com.taktzir.news.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.taktzir.news.NewsApp
import com.taktzir.news.R
import com.taktzir.news.data.Category
import com.taktzir.news.ui.MainActivity

/** Background refresh of all categories. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NewsApp
        return runCatching { app.repository.refresh() }.fold({ Result.success() }, { Result.retry() })
    }
}

/**
 * Runs at a user-chosen time for one category: refreshes it, then notifies about
 * important (importance ≥ 7) articles that appeared since the last notification.
 */
class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_CATEGORY = "category"
        const val KEY_TIME = "time"
        const val MIN_IMPORTANCE = 7
        const val EXTRA_CATEGORY = "open_category"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as NewsApp
        val category = Category.fromKey(inputData.getString(KEY_CATEGORY)) ?: return Result.success()
        val time = inputData.getString(KEY_TIME) ?: return Result.success()

        // Schedule tomorrow's run first, if this time is still configured.
        val config = app.settings.current().notifications
        if (config[category]?.contains(time) == true) {
            Scheduler.scheduleOne(applicationContext, category, time, ExistingWorkPolicy.APPEND_OR_REPLACE)
        } else {
            return Result.success()
        }

        runCatching { app.repository.refresh(listOf(category)) }

        val dao = app.db.articles()
        val since = System.currentTimeMillis() - 12 * 60 * 60 * 1000L
        val pending = dao.pendingNotifications(category.key, MIN_IMPORTANCE, since)
        dao.markAllNotified(category.key)
        if (pending.isNotEmpty()) post(category, pending.map { it.title to it.summary })
        return Result.success()
    }

    private fun post(category: Category, items: List<Pair<String, String>>) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CATEGORY, category.key)
        }
        val pi = PendingIntent.getActivity(
            ctx, category.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(ctx, NewsApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (items.size == 1) {
            val (title, summary) = items.first()
            builder.setContentTitle(category.title + " · " + title)
                .setContentText(summary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
        } else {
            val style = NotificationCompat.InboxStyle()
            items.take(5).forEach { style.addLine("• " + it.first) }
            builder.setContentTitle("${category.title} · ${items.size} עדכונים חשובים")
                .setContentText(items.first().first)
                .setStyle(style)
        }

        runCatching { NotificationManagerCompat.from(ctx).notify(1000 + category.ordinal, builder.build()) }
    }
}
