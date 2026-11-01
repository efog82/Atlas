package pt.atlas.personal

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import pt.atlas.personal.knowledge.*
import androidx.room.Room
import pt.atlas.personal.data.*
import pt.atlas.personal.ai.LocalEngine
import pt.atlas.personal.reminders.ReminderScheduler
import pt.atlas.personal.security.Settings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AtlasApp : Application() {
    val database by lazy { Room.databaseBuilder(this, AtlasDatabase::class.java, "atlas.db").build() }
    val reminders by lazy { ReminderScheduler(this, database.dao()) }
    val repository by lazy { AtlasRepository(database, reminders) }
    val engine by lazy { LocalEngine(this) }
    fun hasInternet(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    val wiki by lazy { WikiApi(HttpsWikiTransport { settings.state.value.onlineEnabled && hasInternet() }) }
    val knowledge by lazy { KnowledgeStore(this, wiki) }
    val settings by lazy { Settings(this) }
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        reminders.createChannel()
        KnowledgeRefreshWorker.schedule(this)
    }
}
