package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SpacedomDao {

    // Offline Videos
    @Query("SELECT * FROM offline_videos ORDER BY addedAt DESC")
    fun observeAllVideos(): Flow<List<OfflineVideoEntity>>

    @Query("SELECT * FROM offline_videos WHERE lastPositionMs > 0 ORDER BY lastPlayedAt DESC")
    fun observeContinueWatching(): Flow<List<OfflineVideoEntity>>

    @Query("SELECT * FROM offline_videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: String): OfflineVideoEntity?

    @Query("SELECT * FROM offline_videos WHERE uriString = :uriString LIMIT 1")
    suspend fun getVideoByUri(uriString: String): OfflineVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideo(video: OfflineVideoEntity)

    @Query("UPDATE offline_videos SET lastPositionMs = :positionMs, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END, lastPlayedAt = :playedAt WHERE id = :id")
    suspend fun updatePlaybackProgress(id: String, positionMs: Long, durationMs: Long, playedAt: Long)

    @Query("UPDATE offline_videos SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE offline_videos SET title = :title WHERE id = :id")
    suspend fun renameVideo(id: String, title: String)

    @Query("DELETE FROM offline_videos WHERE id = :id")
    suspend fun deleteVideoById(id: String)

    // Bookmarks
    @Query("SELECT * FROM stream_bookmarks ORDER BY createdAt DESC")
    fun observeAllBookmarks(): Flow<List<StreamBookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM stream_bookmarks WHERE url = :url)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: StreamBookmarkEntity)

    @Query("DELETE FROM stream_bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    @Query("DELETE FROM stream_bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)

    // Downloads
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadTaskEntity?

    @Query(
        "SELECT * FROM download_tasks WHERE sourceUrl = :sourceUrl " +
            "AND ((:variantUrl IS NULL AND selectedVariantUrl IS NULL) OR selectedVariantUrl = :variantUrl) " +
            "AND status IN ('QUEUED', 'DOWNLOADING', 'PAUSED', 'COMPLETED') LIMIT 1"
    )
    suspend fun findDuplicateDownload(sourceUrl: String, variantUrl: String?): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDownloadIfMissing(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDownloadProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        status: String,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE download_tasks SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, status = :status, " +
            "errorMessage = :errorMessage, supportsPauseResume = :supportsPauseResume, " +
            "subtitlePath = COALESCE(:subtitlePath, subtitlePath), updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateDownloadStateDetailed(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        status: String,
        errorMessage: String? = null,
        supportsPauseResume: Boolean = false,
        subtitlePath: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteDownloadById(id: String)

    // Cloud Provider Extensions
    @Query("SELECT * FROM provider_extensions ORDER BY isActive DESC, installedAt ASC")
    fun observeAllProviders(): Flow<List<ProviderExtensionEntity>>

    @Query("SELECT * FROM provider_extensions WHERE isActive = 1 LIMIT 1")
    fun observeActiveProvider(): Flow<ProviderExtensionEntity?>

    @Query("SELECT * FROM provider_extensions WHERE id = :id LIMIT 1")
    suspend fun getProviderById(id: String): ProviderExtensionEntity?

    @Query("SELECT * FROM provider_extensions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProviderOnce(): ProviderExtensionEntity?

    @Query("SELECT * FROM provider_extensions")
    suspend fun getAllProvidersOnce(): List<ProviderExtensionEntity>

    @Query("SELECT COUNT(*) FROM provider_extensions")
    suspend fun getProviderCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(provider: ProviderExtensionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProviders(providers: List<ProviderExtensionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProvidersIfMissing(providers: List<ProviderExtensionEntity>)

    @Query("UPDATE provider_extensions SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActiveProvider(id: String)

    @Query("UPDATE provider_extensions SET isEnabled = :enabled WHERE id = :id")
    suspend fun setProviderEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM provider_extensions WHERE id = :id")
    suspend fun deleteProviderById(id: String)

    @Transaction
    suspend fun activateProvider(id: String) {
        setProviderEnabled(id, true)
        setActiveProvider(id)
    }

    // Search History
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 50")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchHistoryById(id: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAllSearchHistory()
}
