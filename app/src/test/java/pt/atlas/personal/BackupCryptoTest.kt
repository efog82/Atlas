package pt.atlas.personal

import org.junit.Assert.*
import org.junit.Test
import pt.atlas.personal.security.BackupCrypto
import javax.crypto.AEADBadTagException

class BackupCryptoTest {
    private val password = "palavra-passe de teste".toCharArray()
    @Test fun roundTripAndRandomizedCiphertext() {
        val data = "Diário privado: amanhã estudar português 🌍".toByteArray()
        val first = BackupCrypto.encrypt(data, password)
        val second = BackupCrypto.encrypt(data, password)
        assertArrayEquals(data, BackupCrypto.decrypt(first, password))
        assertFalse(first.contentEquals(second))
        assertFalse(String(first).contains("Diário"))
    }
    @Test fun rejectsWrongPasswordAndTampering() {
        val encrypted = BackupCrypto.encrypt("secreto".toByteArray(), password)
        assertThrows(AEADBadTagException::class.java) { BackupCrypto.decrypt(encrypted, "outra palavra-passe".toCharArray()) }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { BackupCrypto.decrypt(encrypted, password) }
    }
    @Test fun rejectsTruncatedAndUnrecognizedBackups() {
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(ByteArray(12), password) }
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(ByteArray(100), password) }
    }
}
