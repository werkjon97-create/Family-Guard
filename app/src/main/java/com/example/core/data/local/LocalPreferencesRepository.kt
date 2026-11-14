package com.example.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.domain.AppRole
import com.example.core.security.PairingSecurity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "family_guard_prefs")

data class LocalPrefsState(
    val activeRole: AppRole? = null,
    val childDeviceId: String = "",
    val linkedParentUid: String = "",
    val childName: String = "",
    val privacyMode: Boolean = false,
    val lastSyncMs: Long = 0L,
    val fcmToken: String = "",
    val languageOverride: String = "" // "" = system default, "ar", "en"
)

class LocalPreferencesRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_ROLE = stringPreferencesKey("active_role")
        val CHILD_DEVICE_ID = stringPreferencesKey("child_device_id")
        val LINKED_PARENT_UID = stringPreferencesKey("linked_parent_uid")
        val CHILD_NAME = stringPreferencesKey("child_name")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val LANGUAGE_OVERRIDE = stringPreferencesKey("language_override")
    }

    val state: Flow<LocalPrefsState> = context.dataStore.data.map { prefs ->
        LocalPrefsState(
            activeRole = prefs[Keys.ACTIVE_ROLE]?.let { AppRole.fromValue(it) },
            childDeviceId = prefs[Keys.CHILD_DEVICE_ID].orEmpty(),
            linkedParentUid = prefs[Keys.LINKED_PARENT_UID].orEmpty(),
            childName = prefs[Keys.CHILD_NAME].orEmpty(),
            privacyMode = prefs[Keys.PRIVACY_MODE] ?: false,
            lastSyncMs = prefs[Keys.LAST_SYNC_MS] ?: 0L,
            fcmToken = prefs[Keys.FCM_TOKEN].orEmpty(),
            languageOverride = prefs[Keys.LANGUAGE_OVERRIDE].orEmpty()
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val current = context.dataStore.data.first()[Keys.CHILD_DEVICE_ID].orEmpty()
        if (current.isNotBlank()) return current
        val fresh = PairingSecurity.generateResettableDeviceId()
        context.dataStore.edit { it[Keys.CHILD_DEVICE_ID] = fresh }
        return fresh
    }

    suspend fun setActiveRole(role: AppRole?) {
        context.dataStore.edit { prefs ->
            if (role == null) {
                prefs.remove(Keys.ACTIVE_ROLE)
            } else {
                prefs[Keys.ACTIVE_ROLE] = role.value
            }
        }
    }

    suspend fun saveChildLink(deviceId: String, parentUid: String, childName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CHILD_DEVICE_ID] = deviceId
            prefs[Keys.LINKED_PARENT_UID] = parentUid
            prefs[Keys.CHILD_NAME] = childName
            prefs[Keys.ACTIVE_ROLE] = AppRole.CHILD.value
        }
    }

    suspend fun clearChildLink() {
        val freshDeviceId = PairingSecurity.generateResettableDeviceId()
        context.dataStore.edit { prefs ->
            prefs[Keys.CHILD_DEVICE_ID] = freshDeviceId
            prefs.remove(Keys.LINKED_PARENT_UID)
            prefs.remove(Keys.CHILD_NAME)
            prefs.remove(Keys.LAST_SYNC_MS)
        }
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PRIVACY_MODE] = enabled }
    }

    suspend fun setLastSyncMs(timestampMs: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC_MS] = timestampMs }
    }

    suspend fun setFcmToken(token: String) {
        context.dataStore.edit { it[Keys.FCM_TOKEN] = token }
    }

    suspend fun setLanguageOverride(langCode: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_OVERRIDE] = langCode }
    }
}
