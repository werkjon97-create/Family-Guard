package com.example.core.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.core.domain.AlertType
import com.example.core.domain.AppErrorCode
import com.example.core.domain.AppRole
import com.example.core.utils.CollectedAppUsage
import com.example.core.utils.CollectedUsageSummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FamilyGuardRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun pairingAndSync_validParentAndChild_succeedsAndEnforcesIsolation() = runBlocking {
        val parentUid = signInTestUser(PARENT_EMAIL)
        val parentRepo = FamilyGuardRepository(firestore, auth)

        val profile = withTimeout(TIMEOUT_MS) { parentRepo.ensureUserProfile(AppRole.PARENT) }
        assertEquals(parentUid, profile.userId)

        val pairing = withTimeout(TIMEOUT_MS) { parentRepo.createPairingCode("Omar") }
        assertTrue(pairing.rawCode.contains("-"))

        // Child signs in and redeems single-use code
        val childUid = signInTestUser(CHILD_EMAIL)
        val childRepo = FamilyGuardRepository(firestore, auth)
        val deviceId = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val linkedDevice = withTimeout(TIMEOUT_MS) {
            childRepo.redeemPairingCode(
                rawCode = pairing.rawCode,
                deviceId = deviceId,
                fallbackChildName = "Omar",
                usageAccessGranted = true
            )
        }
        assertEquals(parentUid, linkedDevice.parentUid)
        assertEquals(childUid, linkedDevice.childUid)

        // Second redemption of same pairing code must fail
        try {
            withTimeout(TIMEOUT_MS) {
                childRepo.redeemPairingCode(
                    rawCode = pairing.rawCode,
                    deviceId = "dev_second_${UUID.randomUUID().toString().take(8)}",
                    fallbackChildName = "Other",
                    usageAccessGranted = true
                )
            }
            fail("Expected second pairing redemption to fail")
        } catch (e: PairingException) {
            assertEquals(AppErrorCode.INVALID_PAIRING_CODE, e.errorCode)
        }

        // Child syncs usage
        val summary = CollectedUsageSummary(
            date = "2026-11-14",
            totalScreenTimeMs = 1_800_000L,
            apps = listOf(
                CollectedAppUsage(
                    packageName = "com.google.android.youtube",
                    appName = "YouTube",
                    usageTimeMs = 1_800_000L,
                    launchCount = 5,
                    lastUsedMs = 1_700_000_000_000L
                )
            )
        )
        withTimeout(TIMEOUT_MS) {
            childRepo.syncChildUsageToFirestore(
                deviceId = deviceId,
                parentUid = parentUid,
                usageAccessGranted = true,
                isOnline = true,
                summary = summary
            )
        }

        // Parent observes devices & sets app limit
        signInTestUser(PARENT_EMAIL)
        val devices = withTimeout(FLOW_TIMEOUT_MS) {
            parentRepo.observeParentDevices(parentUid).first { it.any { d -> d.id == deviceId } }
        }
        assertTrue(devices.any { it.id == deviceId })

        withTimeout(TIMEOUT_MS) {
            parentRepo.upsertAppLimit(
                deviceId = deviceId,
                childUid = childUid,
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                dailyLimitMinutes = 30L,
                isEnabled = true
            )
        }

        // Stranger cannot access parent's devices or limits
        signInTestUser(STRANGER_EMAIL)
        val strangerRepo = FamilyGuardRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                strangerRepo.observeDevice(deviceId).first()
            }
            fail("Expected stranger observation to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }

        // Child cannot create or alter limits (parent-only write)
        signInTestUser(CHILD_EMAIL)
        try {
            withTimeout(TIMEOUT_MS) {
                childRepo.upsertAppLimit(
                    deviceId = deviceId,
                    childUid = childUid,
                    packageName = "com.google.android.youtube",
                    appName = "YouTube",
                    dailyLimitMinutes = 999L,
                    isEnabled = false
                )
            }
            fail("Expected child limit modification to fail")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // Child can report limit exceeded alert
        withTimeout(TIMEOUT_MS) {
            childRepo.reportAlert(
                deviceId = deviceId,
                parentUid = parentUid,
                childUid = childUid,
                type = AlertType.LIMIT_EXCEEDED,
                title = "YouTube limit reached",
                message = "30 min limit reached",
                alertId = "alrt_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            )
        }
    }

    @Test
    fun observeParentDevices_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FamilyGuardRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeParentDevices("some_parent_uid").first()
            }
            fail("Expected unauthenticated query to fail")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    private companion object {
        const val PARENT_EMAIL = "parent_rule_test@familyguard.test"
        const val CHILD_EMAIL = "child_rule_test@familyguard.test"
        const val STRANGER_EMAIL = "stranger_rule_test@familyguard.test"
        const val TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
