package pt.atlas.personal

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import pt.atlas.personal.data.*
import pt.atlas.personal.reminders.ReminderScheduler
import pt.atlas.personal.security.BackupService
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private val app = ApplicationProvider.getApplicationContext<AtlasApp>()
    private lateinit var database: AtlasDatabase
    private lateinit var backup: BackupService
    private lateinit var file: File
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(app, AtlasDatabase::class.java).build()
        backup = BackupService(app, database, ReminderScheduler(app, database.dao()))
        file = File.createTempFile("backup-test-", ".atlas", app.cacheDir)
    }
    @After fun cleanup() { database.close(); file.delete() }
    @Test fun authenticatedBackupRestoresAllRecordsAndRejectsWrongPasswordWithoutChanges() = runBlocking {
        val original = Record(kind = Kind.MEMORY, title = "Objetivo", body = "Estudar português")
        database.dao().save(original)
        backup.export(Uri.fromFile(file), "uma palavra-passe forte".toCharArray())
        val changed = original.copy(body = "Alterado")
        database.dao().save(changed)
        try {
            backup.restore(Uri.fromFile(file), "palavra-passe incorreta".toCharArray())
            fail("Deveria rejeitar a palavra-passe.")
        } catch (_: IllegalStateException) { }
        assertEquals(changed, database.dao().record(original.id))
        backup.restore(Uri.fromFile(file), "uma palavra-passe forte".toCharArray())
        assertEquals(original, database.dao().record(original.id))
    }
}
