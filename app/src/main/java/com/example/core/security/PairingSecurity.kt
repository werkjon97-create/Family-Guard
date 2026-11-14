package com.example.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object PairingSecurity {
    // Exclude visually ambiguous characters (0, O, I, 1, L)
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val CODE_PART_LENGTH = 4
    private const val EXPIRY_DURATION_MS = 10 * 60 * 1000L // 10 minutes
    private val secureRandom = SecureRandom()

    fun generateRawCode(): String {
        val part1 = buildString(CODE_PART_LENGTH) {
            repeat(CODE_PART_LENGTH) {
                append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
            }
        }
        val part2 = buildString(CODE_PART_LENGTH) {
            repeat(CODE_PART_LENGTH) {
                append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
            }
        }
        return "$part1-$part2"
    }

    fun normalizeCode(rawInput: String): String =
        rawInput.trim().uppercase().replace("-", "").replace(" ", "")

    fun isValidFormat(rawInput: String): Boolean {
        val normalized = normalizeCode(rawInput)
        return normalized.length == 8 && normalized.all { it in ALPHABET }
    }

    /**
     * Computes SHA-256 hex digest of normalized pairing code.
     * Raw pairing codes are never logged to Logcat and never persisted in plain text.
     */
    fun hashCode(rawInput: String): String {
        val normalized = normalizeCode(rawInput)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun computeExpirationMs(nowMs: Long = System.currentTimeMillis()): Long =
        nowMs + EXPIRY_DURATION_MS

    fun generateResettableDeviceId(): String =
        "fg_dev_" + UUID.randomUUID().toString().replace("-", "").take(16)
}
