package pt.atlas.personal.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private val magic = "ATLASBK1".toByteArray(Charsets.US_ASCII)
    private const val iterations = 210_000
    private val random = SecureRandom()
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 10) { "Usa uma palavra-passe com pelo menos 10 caracteres." }
        require(plain.size <= 64 * 1024 * 1024) { "Cópia de segurança demasiado grande." }
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = magic + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }
    fun decrypt(encrypted: ByteArray, password: CharArray): ByteArray {
        require(encrypted.size in 52..(64 * 1024 * 1024 + 52)) { "Tamanho da cópia inválido." }
        require(encrypted.copyOfRange(0, 8).contentEquals(magic)) { "Formato de cópia não suportado." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, encrypted.copyOfRange(8, 24)),
            GCMParameterSpec(128, encrypted.copyOfRange(24, 36)))
        cipher.updateAAD(encrypted.copyOfRange(0, 36))
        return cipher.doFinal(encrypted, 36, encrypted.size - 36)
    }
}
