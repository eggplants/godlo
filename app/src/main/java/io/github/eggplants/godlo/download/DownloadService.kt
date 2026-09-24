package io.github.eggplants.godlo.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.eggplants.godlo.GodloApp
import io.github.eggplants.godlo.MainActivity
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.AppLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Keeps the process alive while the queue drains, with a progress notification. */
class DownloadService : LifecycleService() {
    private var job: Job? = null

    /** This service in the chosen UI language, which AppCompat only applies to activities. */
    private var strings: Context = this

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        strings = AppLanguage.localize(this)
        createChannels(strings)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            progressNotification(null),
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
        if (job?.isActive != true) {
            val manager = (application as GodloApp).container.downloads
            job = lifecycleScope.launch {
                // A task queued just as the last one finished would otherwise wait for the next start.
                do manager.drain { task -> onUpdate(task) } while (manager.hasPending)
                ServiceCompat.stopForeground(
                    this@DownloadService,
                    ServiceCompat.STOP_FOREGROUND_REMOVE
                )
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun onUpdate(task: DownloadTask) {
        val notifications = NotificationManagerCompat.from(this)
        if (!notifications.areNotificationsEnabled()) return
        try {
            when (task.state) {
                TaskState.RUNNING -> notifications.notify(
                    NOTIFICATION_ID,
                    progressNotification(task)
                )

                TaskState.DONE, TaskState.FAILED -> notifications.notify(
                    task.id.toInt(),
                    resultNotification(task)
                )

                else -> Unit
            }
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked mid-download.
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun progressNotification(task: DownloadTask?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                task?.title?.ifBlank {
                    null
                } ?: task?.url ?: strings.getString(R.string.downloading)
            )
            .setContentText(task?.detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
        val progress = task?.progress ?: -1f
        if (progress >=
            0f
        ) {
            builder.setProgress(1000, (progress * 1000).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun resultNotification(task: DownloadTask): Notification =
        NotificationCompat.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(
                if (task.state ==
                    TaskState.DONE
                ) {
                    android.R.drawable.stat_sys_download_done
                } else {
                    android.R.drawable.stat_notify_error
                }
            )
            .setContentTitle(
                strings.getString(
                    if (task.state ==
                        TaskState.DONE
                    ) {
                        R.string.download_done
                    } else {
                        R.string.download_failed
                    }
                )
            )
            .setContentText(task.title.ifBlank { task.url })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    listOf(task.title, task.message).filter {
                        it.isNotBlank()
                    }.joinToString("\n")
                )
            )
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_PROGRESS = "downloads"
        private const val CHANNEL_RESULT = "results"

        fun intent(context: Context) = Intent(context, DownloadService::class.java)

        private fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PROGRESS,
                    context.getString(R.string.channel_progress),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RESULT,
                    context.getString(R.string.channel_result),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }
}
