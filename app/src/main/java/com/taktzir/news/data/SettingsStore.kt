package com.taktzir.news.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    /** Target number of articles per category per day. */
    val perDay: Int = 7,
    /** Notification times ("HH:mm") per enabled category. A category absent from the map has notifications off. */
    val notifications: Map<Category, List<String>> = emptyMap(),
    val lastRefresh: Long = 0L,
) {
    val hasAi: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "claude-haiku-5-5"
    }
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val perDay = intPreferencesKey("per_day")
        val notifications = stringPreferencesKey("notifications")
        val lastRefresh = longPreferencesKey("last_refresh")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            apiKey = p[Keys.apiKey].orEmpty(),
            model = p[Keys.model]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_MODEL,
            perDay = p[Keys.perDay] ?: 7,
            notifications = decodeNotifications(p[Keys.notifications].orEmpty()),
            lastRefresh = p[Keys.lastRefresh] ?: 0L,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setApiKey(value: String) = context.dataStore.edit { it[Keys.apiKey] = value.trim() }
    suspend fun setModel(value: String) = context.dataStore.edit { it[Keys.model] = value.trim() }
    suspend fun setPerDay(value: Int) = context.dataStore.edit { it[Keys.perDay] = value }
    suspend fun setLastRefresh(value: Long) = context.dataStore.edit { it[Keys.lastRefresh] = value }

    suspend fun setNotifications(value: Map<Category, List<String>>) =
        context.dataStore.edit { it[Keys.notifications] = encodeNotifications(value) }

    // Format: "politics=08:00,20:00;cyber=13:00"
    private fun encodeNotifications(map: Map<Category, List<String>>): String =
        map.entries.joinToString(";") { (cat, times) -> cat.key + "=" + times.sorted().joinToString(",") }

    private fun decodeNotifications(raw: String): Map<Category, List<String>> =
        raw.split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val cat = Category.fromKey(part.substring(0, idx)) ?: return@mapNotNull null
            val times = part.substring(idx + 1).split(",").filter { it.matches(Regex("\\d{2}:\\d{2}")) }
            cat to times
        }.toMap()
}
