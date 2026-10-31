package pt.atlas.personal

import android.app.Application
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
    val engine by lazy { LocalEngine() }
    val settings by lazy { Settings(this) }
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        reminders.createChannel()
    }
}
