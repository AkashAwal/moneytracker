package com.akash.moneytracker.data

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongBackupPasswordException : Exception("Wrong password, or the file has been changed.")

/**
 * Password-protected backups, so the file is safe to keep in Drive or send over WhatsApp.
 *
 * - Key: PBKDF2-HMAC-SHA256 over the password, 600,000 iterations, random 16-byte salt, 256-bit output.
 * - Cipher: AES-256-GCM with a random 12-byte nonce. GCM authenticates the data, so a wrong password and a
 *   modified file are both detected instead of producing garbage.
 * - The header (magic, iteration count, salt) is bound to the ciphertext as additional authenticated data.
 *
 * Layout (before Base64): "MTB1" | iterations (4 bytes) | salt (16) | nonce (12) | ciphertext + 16-byte tag.
 * Nothing about the password is stored in the file.
 */
object BackupCrypto {
    const val PREFIX = "MTBK1:"
    const val MIN_PASSWORD_LENGTH = 8

    private const val MAGIC = "MTB1"
    private const val DEFAULT_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000 // stops a crafted file from freezing the phone
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 4 + SALT_BYTES

    private val random = SecureRandom()

    fun isEncrypted(text: String): Boolean = text.trimStart().startsWith(PREFIX)

    fun encrypt(plain: String, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): String {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "unsupported iteration count" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC.toByteArray()).putInt(iterations).put(salt).array()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = deriveKey(password, salt, iterations)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        val blob = ByteBuffer.allocate(HEADER_BYTES + NONCE_BYTES + sealed.size).put(header).put(nonce).put(sealed).array()
        return PREFIX + Base64.getEncoder().encodeToString(blob)
    }

    /** @throws WrongBackupPasswordException for a wrong password or a modified file; [InvalidBackupException] for a malformed one. */
    fun decrypt(text: String, password: CharArray): String {
        val blob = try {
            Base64.getDecoder().decode(text.trim().removePrefix(PREFIX))
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("This file isn't a readable Money Tracker backup.")
        }
        if (blob.size < HEADER_BYTES + NONCE_BYTES + TAG_BITS / 8) throw InvalidBackupException("This file isn't a readable Money Tracker backup.")

        val buffer = ByteBuffer.wrap(blob)
        val header = ByteArray(HEADER_BYTES).also(buffer::get)
        val headerBuffer = ByteBuffer.wrap(header)
        val magic = ByteArray(4).also(headerBuffer::get)
        val iterations = headerBuffer.getInt()
        val salt = ByteArray(SALT_BYTES).also(headerBuffer::get)
        if (String(magic) != MAGIC || iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw InvalidBackupException("This file isn't a readable Money Tracker backup.")
        }
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        val sealed = ByteArray(buffer.remaining()).also(buffer::get)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw WrongBackupPasswordException()
        } catch (e: GeneralSecurityException) {
            throw WrongBackupPasswordException()
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
