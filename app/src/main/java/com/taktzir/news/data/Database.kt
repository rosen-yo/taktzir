package com.taktzir.news.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "articles")
data class Article(
    @PrimaryKey val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: Long,
    val fetchedAt: Long,
    /** 1–10, higher = more important. */
    val importance: Int,
    /** How many distinct sources covered the same story. */
    val coverage: Int,
    val aiCurated: Boolean,
    val isRead: Boolean = false,
    val readAt: Long? = null,
    val notified: Boolean = false,
)

/** Candidates that were already evaluated (and possibly rejected), so they aren't re-sent to the AI. */
@Entity(tableName = "seen")
data class Seen(
    @PrimaryKey val key: String,
    val seenAt: Long,
)

data class CategoryCount(val category: String, val count: Int)

@Dao
interface ArticleDao {

    @Query(
        "SELECT * FROM articles WHERE category = :category AND isRead = 0 AND publishedAt >= :since " +
            "ORDER BY publishedAt DESC"
    )
    fun observeAvailable(category: String, since: Long): Flow<List<Article>>

    @Query(
        "SELECT category, COUNT(*) AS count FROM articles WHERE isRead = 0 AND publishedAt >= :since " +
            "GROUP BY category"
    )
    fun observeCounts(since: Long): Flow<List<CategoryCount>>

    @Query("SELECT * FROM articles WHERE isRead = 1 ORDER BY readAt DESC")
    fun observeHistory(): Flow<List<Article>>

    @Query("UPDATE articles SET isRead = 1, readAt = :at WHERE id = :id")
    suspend fun markRead(id: String, at: Long)

    @Query("UPDATE articles SET isRead = 0, readAt = NULL WHERE id = :id")
    suspend fun markUnread(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<Article>)

    @Query("SELECT id FROM articles WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    @Query("SELECT title FROM articles WHERE category = :category AND publishedAt >= :since")
    suspend fun titlesSince(category: String, since: Long): List<String>

    @Query("SELECT publishedAt FROM articles WHERE category = :category AND publishedAt >= :since")
    suspend fun publishTimesSince(category: String, since: Long): List<Long>

    @Query(
        "SELECT * FROM articles WHERE category = :category AND notified = 0 AND isRead = 0 " +
            "AND importance >= :minImportance AND publishedAt >= :since ORDER BY importance DESC, publishedAt DESC"
    )
    suspend fun pendingNotifications(category: String, minImportance: Int, since: Long): List<Article>

    @Query("UPDATE articles SET notified = 1 WHERE category = :category")
    suspend fun markAllNotified(category: String)

    /** Unread items older than the retention window disappear. */
    @Query("DELETE FROM articles WHERE isRead = 0 AND publishedAt < :before")
    suspend fun purgeUnread(before: Long)

    /** History is kept a bit longer so it stays useful. */
    @Query("DELETE FROM articles WHERE isRead = 1 AND readAt < :before")
    suspend fun purgeHistory(before: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeen(items: List<Seen>)

    @Query("SELECT `key` FROM seen WHERE `key` IN (:keys)")
    suspend fun seenKeys(keys: List<String>): List<String>

    @Query("DELETE FROM seen WHERE seenAt < :before")
    suspend fun purgeSeen(before: Long)
}

@Database(entities = [Article::class, Seen::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articles(): ArticleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "taktzir.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
