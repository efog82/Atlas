package pt.atlas.personal.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import pt.atlas.personal.AtlasApp
import pt.atlas.personal.MainActivity
import pt.atlas.personal.R
import pt.atlas.personal.data.*
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context, private val dao: AtlasDao) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("atlas_reminders", "Lembretes Atlas", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Agenda, tarefas e hábitos locais"; lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
    }
    private fun pending(id: String, at: Long = 0): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("atlas://reminder/$id"))
            .putExtra("id", id).putExtra("at", at), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun cancel(id: String) {
        alarms.cancel(pending(id))
        WorkManager.getInstance(context).cancelUniqueWork("reminder-$id")
    }

    fun schedule(record: Record, after: Long = System.currentTimeMillis()) {
        cancel(record.id)
        if (!record.reminder || record.done) return
        val at = RecordRules.nextOccurrence(record, after) ?: return
        if (alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(record.id, at))
                return
            } catch (_: SecurityException) { /* permission can be revoked between check and call */ }
        }
        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf("id" to record.id, "at" to at))
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("reminder-${record.id}", ExistingWorkPolicy.REPLACE, work)
    }

    suspend fun reschedule() { dao.records().forEach { schedule(it) } }

    suspend fun deliver(id: String, at: Long) {
        val record = dao.record(id) ?: return
        if (!record.reminder || record.done || RecordRules.nextOccurrence(record, at - 1) != at) return
        val receipts = context.getSharedPreferences("reminder-receipts", Context.MODE_PRIVATE)
        if (receipts.getLong(id, 0) >= at) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, "atlas_reminders")
                .setSmallIcon(R.drawable.ic_atlas).setContentTitle(record.title)
                .setContentText("Tens um lembrete no Atlas Personal.")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build()
            context.getSystemService(NotificationManager::class.java).notify(id, 1, notification)
        }
        receipts.edit().putLong(id, at).apply()
        // Queue the next occurrence without replacing the currently executing WorkManager request.
        if (record.recurrence != "NONE") {
            val next = RecordRules.nextOccurrence(record, maxOf(at, System.currentTimeMillis()))
            if (next != null) {
                if (alarms.canScheduleExactAlarms()) {
                    try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(id, next)); return }
                    catch (_: SecurityException) { }
                }
                WorkManager.getInstance(context).enqueueUniqueWork("reminder-$id", ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("id" to id, "at" to next))
                        .setInitialDelay((next - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build())
            }
        }
    }
}

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as AtlasApp
        app.reminders.deliver(inputData.getString("id") ?: return Result.failure(), inputData.getLong("at", 0))
        Result.success()
    } catch (_: Exception) { Result.retry() }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { (context.applicationContext as AtlasApp).reminders.deliver(intent.getStringExtra("id") ?: return@launch, intent.getLongExtra("at", 0)) }
            finally { pending.finish() }
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).enqueueUniqueWork("restore-reminders", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RescheduleWorker>().build())
    }
}

class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as AtlasApp).reminders.reschedule(); Result.success()
    } catch (_: Exception) { Result.retry() }
}
