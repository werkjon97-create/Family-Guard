package com.example

import com.example.core.domain.AppLimit
import com.example.core.domain.EvaluateAppLimitsUseCase
import com.example.core.security.PairingSecurity
import com.example.core.utils.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyGuardUnitTest {

    @Test
    fun pairingCodeGeneration_isFormattedAndHashesDeterministically() {
        val code1 = PairingSecurity.generateRawCode()
        val code2 = PairingSecurity.generateRawCode()

        assertTrue(PairingSecurity.isValidFormat(code1))
        assertTrue(PairingSecurity.isValidFormat(code2))
        assertNotEquals(code1, code2)

        val hash1 = PairingSecurity.hashCode(code1)
        val hashNormalized = PairingSecurity.hashCode(code1.lowercase().replace("-", ""))
        assertEquals(64, hash1.length)
        assertEquals(hash1, hashNormalized)
    }

    @Test
    fun evaluateAppLimits_flagsOnlyEnabledExceededApps() {
        val limits = listOf(
            AppLimit(packageName = "com.google.android.youtube", appName = "YouTube", dailyLimitMinutes = 30, isEnabled = true),
            AppLimit(packageName = "com.android.chrome", appName = "Chrome", dailyLimitMinutes = 45, isEnabled = true),
            AppLimit(packageName = "com.instagram.android", appName = "Instagram", dailyLimitMinutes = 10, isEnabled = false)
        )
        val usage = listOf(
            "com.google.android.youtube" to 35 * 60_000L,
            "com.android.chrome" to 20 * 60_000L,
            "com.instagram.android" to 50 * 60_000L
        )
        val exceeded = EvaluateAppLimitsUseCase().findExceededLimits(usage, limits)
        assertEquals(1, exceeded.size)
        assertEquals("com.google.android.youtube", exceeded.first().packageName)
    }

    @Test
    fun formatDurationHms_formatsAccurately() {
        val ms = (3 * 3600L + 42 * 60L + 18L) * 1000L
        assertEquals("03:42:18", Formatters.formatDurationHms(ms))
        assertFalse(PairingSecurity.isValidFormat("123"))
    }
}
