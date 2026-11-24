package com.example.data.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.SpacedomDatabase
import com.example.security.ContentBlockerEngine
import kotlinx.coroutines.CancellationException
import java.io.File

class VideoDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        ContentBlockerEngine.initialize(applicationContext)
        val taskId = inputData.getString(KEY_TASK_ID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val rawUrl = inputData.getString(KEY_SOURCE_URL)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Navula Video"
        val destPath = inputData.getString(KEY_DEST_PATH)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val referer = inputData.getString(KEY_REFERER)
        val origin = inputData.getString(KEY_ORIGIN)
        val userAgent = inputData.getString(KEY_USER_AGENT)
        val preferredVariantUrl = inputData.getString(KEY_PREFERRED_VARIANT_URL)
        val prefetchedResolvedUrl = inputData.getString(KEY_PREFETCHED_RESOLVED_URL)
        val prefetchedLength = inputData.getLong(KEY_PREFETCHED_LENGTH, 0L)
        val subtitleUrl = inputData.getString(KEY_SUBTITLE_URL)
        val subtitleLanguage = inputData.getString(KEY_SUBTITLE_LANGUAGE)
        val isResume = inputData.getBoolean(KEY_IS_RESUME, false)

        val dao = SpacedomDatabase.getInstance(applicationContext).spacedomDao()
        dao.insertDownloadIfMissing(
            DownloadTaskEntity(
                id = taskId,
                title = title,
                sourceUrl = rawUrl,
                destinationPath = destPath,
                totalBytes = prefetchedLength,
                status = DownloadTaskEntity.STATUS_QUEUED
            )
        )

        runCatching {
            setForeground(createForegroundInfo(title = title, percent = 0, indeterminate = true, detail = if (isResume) "Resuming download..." else "Verifying secure source..."))
        }

        var lastNotifyMs = 0L

        return try {
            VideoDownloadManager.executeWorkerDownload(
                context = applicationContext,
                dao = dao,
                taskId = taskId,
                rawUrl = rawUrl,
                title = title,
                destFile = File(destPath),
                referer = referer,
                origin = origin,
                userAgent = userAgent,
                preferredVariantUrl = preferredVariantUrl,
                prefetchedResolvedUrl = prefetchedResolvedUrl,
                prefetchedLength = prefetchedLength,
                subtitleUrl = subtitleUrl,
                subtitleLanguage = subtitleLanguage,
                isResume = isResume,
                isStoppedCheck = { isStopped },
                onProgressUpdate = { metrics, downloadedBytes, totalBytes ->
                    setProgress(
                        workDataOf(
                            KEY_TASK_ID to taskId,
                            KEY_PROGRESS_PERCENT to metrics.percent,
                            KEY_PROGRESS_BYTES to downloadedBytes,
                            KEY_TOTAL_BYTES to totalBytes,
                            KEY_SPEED_BPS to metrics.speedBytesPerSec,
                            KEY_ETA_SECONDS to (metrics.etaSeconds ?: -1L),
                            KEY_DETAIL_LABEL to metrics.detailLabel
                        )
                    )
                    val now = System.currentTimeMillis()
                    if (now - lastNotifyMs >= 750L || metrics.percent >= 100) {
                        lastNotifyMs = now
                        runCatching {
                            val indeterminate = totalBytes <= 0L && metrics.percent <= 0
                            setForeground(
                                createForegroundInfo(
                                    title = title,
                                    percent = metrics.percent,
                                    indeterminate = indeterminate,
                                    detail = buildNotificationDetail(metrics, downloadedBytes, totalBytes)
                                )
                            )
                        }
                    }
                }
            )
            val destFile = File(destPath)
            val savedSize = if (destFile.name == "index.m3u8") {
                destFile.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
            } else {
                destFile.length()
            }
            runCatching { postCompletionNotification(taskId, title, savedSize) }
            Result.success(
                workDataOf(
                    KEY_TASK_ID to taskId,
                    KEY_DEST_PATH to destPath,
                    KEY_TITLE to title
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (expired: ExpiredLinkException) {
            Result.failure(
                workDataOf(
                    KEY_TASK_ID to taskId,
                    KEY_ERROR_MESSAGE to (expired.localizedMessage ?: "Download link expired")
                )
            )
        } catch (e: java.io.IOException) {
            if (!isStopped && runAttemptCount < 2) {
                dao.updateDownloadProgress(
                    id = taskId,
                    downloadedBytes = 0L,
                    totalBytes = prefetchedLength,
                    status = DownloadTaskEntity.STATUS_QUEUED,
                    errorMessage = "Reconnecting (attempt ${runAttemptCount + 1})..."
                )
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        KEY_TASK_ID to taskId,
                        KEY_ERROR_MESSAGE to (e.localizedMessage ?: "Network interrupted during download")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                workDataOf(
                    KEY_TASK_ID to taskId,
                    KEY_ERROR_MESSAGE to (e.localizedMessage ?: "Download failed")
                )
            )
        }
    }

    private fun buildNotificationDetail(
        metrics: DownloadLiveMetrics,
        downloadedBytes: Long,
        totalBytes: Long
    ): String = buildString {
        if (metrics.detailLabel.isNotBlank()) {
            append(metrics.detailLabel)
        } else {
            append(VideoMetadataHelper.formatFileSize(downloadedBytes))
            if (totalBytes > 0L) {
                append(" / ${VideoMetadataHelper.formatFileSize(totalBytes)}")
            }
        }
        if (metrics.speedBytesPerSec > 0L) {
            append(" • ${VideoDownloadManager.formatSpeed(metrics.speedBytesPerSec)}")
        }
        val eta = VideoDownloadManager.formatEta(metrics.etaSeconds)
        if (eta.isNotBlank()) {
            append(" • $eta")
        }
    }

    private fun createForegroundInfo(
        title: String,
        percent: Int,
        indeterminate: Boolean,
        detail: String
    ): ForegroundInfo {
        ensureNotificationChannels()
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val cancelLabel = applicationContext.getString(R.string.download_cancel_action)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(detail)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), indeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, cancelLabel, cancelIntent)
            .build()

        val notificationId = taskNotificationId(inputData.getString(KEY_TASK_ID) ?: id.toString())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun postCompletionNotification(
        taskId: String,
        title: String,
        fileSizeBytes: Long
    ) {
        ensureNotificationChannels()
        val launchIntent = android.content.Intent(applicationContext, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_OFFLINE_VIDEO_ID, taskId)
        }
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            taskNotificationId(taskId) + 10000,
            launchIntent,
            flags
        )
        val sizeText = VideoMetadataHelper.formatFileSize(fileSizeBytes)
        val notification = NotificationCompat.Builder(applicationContext, COMPLETE_CHANNEL_ID)
            .setContentTitle("Download Complete: $title")
            .setContentText("Saved offline ($sizeText) • Tap to watch")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(taskNotificationId(taskId) + 20000, notification)
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.download_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = applicationContext.getString(R.string.download_notification_channel_desc)
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
            if (notificationManager.getNotificationChannel(COMPLETE_CHANNEL_ID) == null) {
                val completeChannel = NotificationChannel(
                    COMPLETE_CHANNEL_ID,
                    "Completed Downloads",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when offline videos finish downloading"
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(completeChannel)
            }
        }
    }

    private fun taskNotificationId(taskId: String): Int {
        return 4100 + (taskId.hashCode() and 0x0FFF)
    }

    companion object {
        const val CHANNEL_ID = "navula_video_downloads"
        const val COMPLETE_CHANNEL_ID = "navula_download_complete"
        const val TAG_DOWNLOAD = "navula_video_download"
        const val EXTRA_OPEN_OFFLINE_VIDEO_ID = "extra_open_offline_video_id"

        const val KEY_TASK_ID = "task_id"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_TITLE = "title"
        const val KEY_DEST_PATH = "dest_path"
        const val KEY_REFERER = "referer"
        const val KEY_ORIGIN = "origin"
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_PREFERRED_VARIANT_URL = "preferred_variant_url"
        const val KEY_PREFETCHED_RESOLVED_URL = "prefetched_resolved_url"
        const val KEY_PREFETCHED_LENGTH = "prefetched_length"
        const val KEY_SUBTITLE_URL = "subtitle_url"
        const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        const val KEY_IS_RESUME = "is_resume"

        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_PROGRESS_BYTES = "progress_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_SPEED_BPS = "speed_bps"
        const val KEY_ETA_SECONDS = "eta_seconds"
        const val KEY_DETAIL_LABEL = "detail_label"
        const val KEY_ERROR_MESSAGE = "error_message"

        fun uniqueWorkName(taskId: String): String = "navula_download_$taskId"
    }
}
