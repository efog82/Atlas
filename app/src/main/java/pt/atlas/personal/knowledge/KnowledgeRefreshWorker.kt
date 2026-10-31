package pt.atlas.personal.knowledge

import android.content.Context
import androidx.work.*
import pt.atlas.personal.AtlasApp
import java.util.concurrent.TimeUnit

class KnowledgeRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AtlasApp
        if (!app.settings.state.value.onlineEnabled || !app.settings.state.value.refreshKnowledge) return Result.success()
        return try { app.knowledge.refreshSaved(); Result.success() }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { if (runAttemptCount < 2) Result.retry() else Result.failure() }
    }
    companion object {
        fun schedule(app: AtlasApp) {
            val manager = WorkManager.getInstance(app)
            val prefs = app.settings.state.value
            if (!prefs.onlineEnabled || !prefs.refreshKnowledge) { manager.cancelUniqueWork("atlas-knowledge-refresh"); return }
            val request = PeriodicWorkRequestBuilder<KnowledgeRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
                .setInitialDelay(24, TimeUnit.HOURS).build()
            manager.enqueueUniquePeriodicWork("atlas-knowledge-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
