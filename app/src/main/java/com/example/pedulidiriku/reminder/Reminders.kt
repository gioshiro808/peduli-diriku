package com.example.pedulidiriku.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.pedulidiriku.TrackerApplication
import com.example.pedulidiriku.domain.SummarizeDay
import com.example.pedulidiriku.presentation.MainActivity
import java.time.Duration
import java.util.concurrent.TimeUnit

object Reminders {
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork(
            "hourly", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).setInitialDelay(
                1,
                TimeUnit.HOURS
            )
                .setInputData(workDataOf("kind" to "hourly")).build()
        )
        daily(context, "summary", 23, 30)
        daily(context, "breakfast", 7, 0)
    }

    fun daily(context: Context, kind: String, hour: Int, minute: Int) {
        val clock = (context.applicationContext as TrackerApplication).clock
        val now = clock.now().atZone(clock.zone())
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("kind" to kind, "date" to next.toLocalDate().toString()))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(kind, ExistingWorkPolicy.KEEP, work)
    }

    fun resetDaily(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("summary"); manager.cancelUniqueWork("breakfast")
        // REPLACE prevents an old zone's schedule from surviving the change.
        listOf(
            Triple("summary", 23, 30),
            Triple("breakfast", 7, 0)
        ).forEach { (kind, hour, minute) ->
            val clock = (context.applicationContext as TrackerApplication).clock
            val now = clock.now().atZone(clock.zone())
            var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            manager.enqueueUniqueWork(
                kind, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            "kind" to kind,
                            "date" to next.toLocalDate().toString()
                        )
                    ).build()
            )
        }
    }
}

class ReminderWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TrackerApplication
        val kind = inputData.getString("kind") ?: return Result.failure()
        try {
            val (title, message) = when (kind) {
                "summary" -> {
                    val date = java.time.LocalDate.parse(inputData.getString("date"))
                    val total = SummarizeDay(app.repository, app.clock)(date).total
                    "Ringkasan asupan $date" to "%.0f kkal · gula %.1f g · natrium %.0f mg · protein %.1f g. Lihat riwayat untuk data terbaru.".format(
                        total.calories,
                        total.sugar,
                        total.sodium,
                        total.protein
                    )
                }

                "breakfast" -> "Waktunya sarapan" to "Ide sarapan: nasi, telur atau tempe, sayur, dan air putih. Sesuaikan porsi dengan kebutuhanmu."
                else -> "Sudah mencatat asupanmu?" to "Catat makanan dan minuman yang baru kamu konsumsi."
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                NotificationChannel(
                    "intake",
                    "Pengingat asupan",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val open = Intent(applicationContext, MainActivity::class.java).putExtra(
                    "date",
                    if (kind == "summary") inputData.getString("date") else null
                )
                val pending = PendingIntent.getActivity(
                    applicationContext,
                    kind.hashCode(),
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                manager.notify(
                    kind.hashCode(),
                    NotificationCompat.Builder(applicationContext, "intake")
                        .setSmallIcon(com.example.pedulidiriku.R.drawable.ic_notification)
                        .setContentTitle(title).setContentText(message)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                        .setContentIntent(pending).setAutoCancel(true).build()
                )
            }
            return Result.success()
        } finally {
            if (kind == "summary" || kind == "breakfast") {
                // A separate chain name lets the next request survive this worker's completion.
                val now = app.clock.now().atZone(app.clock.zone())
                var next = now.withHour(if (kind == "summary") 23 else 7)
                    .withMinute(if (kind == "summary") 30 else 0).withSecond(0).withNano(0)
                if (!next.isAfter(now)) next = next.plusDays(1)
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    kind, ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(
                        Duration.between(
                            now,
                            next
                        ).toMillis(), TimeUnit.MILLISECONDS
                    )
                        .setInputData(
                            workDataOf(
                                "kind" to kind,
                                "date" to next.toLocalDate().toString()
                            )
                        ).build()
                )
            }
        }
    }
}
