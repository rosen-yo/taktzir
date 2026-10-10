package com.taktzir.news.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.taktzir.news.data.Category
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object Scheduler {

    private const val PERIODIC = "periodic_refresh"
    const val NOTIFY_TAG = "notify"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Keeps the feed fresh in the background every few hours. */
    fun ensurePeriodicRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(4, TimeUnit.HOURS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Replaces all scheduled notification checks with the given configuration. */
    fun scheduleNotifications(context: Context, config: Map<Category, List<String>>) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(NOTIFY_TAG)
        for ((category, times) in config) {
            for (time in times) scheduleOne(context, category, time, ExistingWorkPolicy.REPLACE)
        }
    }

    /** Schedules the next occurrence of [time] for [category]. */
    fun scheduleOne(context: Context, category: Category, time: String, policy: ExistingWorkPolicy) {
        val target = runCatching { LocalTime.parse(time) }.getOrNull() ?: return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(target)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()

        val request = OneTimeWorkRequestBuilder<NotifyWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(network)
            .addTag(NOTIFY_TAG)
            .setInputData(workDataOf(NotifyWorker.KEY_CATEGORY to category.key, NotifyWorker.KEY_TIME to time))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName(category, time), policy, request)
    }

    private fun uniqueName(category: Category, time: String) = "notify_${category.key}_$time"
}
