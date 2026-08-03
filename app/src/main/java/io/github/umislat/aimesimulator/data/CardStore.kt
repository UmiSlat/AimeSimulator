package io.github.umislat.aimesimulator.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class CardStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    init {
        migrateExistingInstallation()
    }

    @Synchronized
    fun profiles(): List<CardProfile> = decodeProfiles(preferences.getString(KEY_PROFILES, null))

    @Synchronized
    fun selectedProfile(): CardProfile? {
        val selectedId = preferences.getString(KEY_SELECTED, null) ?: return null
        return profiles().firstOrNull { it.profileId == selectedId }
    }

    @Synchronized
    fun put(profile: CardProfile): Boolean {
        val updated = profiles().toMutableList()
        val index = updated.indexOfFirst { it.profileId == profile.profileId }
        if (index >= 0) updated[index] = profile else updated += profile
        return writeProfiles(updated)
    }

    @Synchronized
    fun remove(profileId: String): Boolean {
        val updated = profiles().filterNot { it.profileId == profileId }
        val editor = preferences.edit().putString(KEY_PROFILES, encodeProfiles(updated))
        if (preferences.getString(KEY_SELECTED, null) == profileId) editor.remove(KEY_SELECTED)
        return editor.commit()
    }

    @Synchronized
    fun select(profileId: String?): Boolean {
        val editor = preferences.edit()
        if (profileId == null) editor.remove(KEY_SELECTED) else editor.putString(KEY_SELECTED, profileId)
        return editor.commit()
    }

    fun compatibilityMode(): Boolean = preferences.getBoolean(KEY_COMPATIBILITY, false)

    fun setCompatibilityMode(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_COMPATIBILITY, enabled).apply()
    }

    fun showIdm(): Boolean = preferences.getBoolean(KEY_SHOW_IDM, true)

    fun setShowIdm(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_IDM, enabled).apply()
    }

    fun showAccessCode(): Boolean = preferences.getBoolean(KEY_SHOW_ACCESS_CODE, true)

    fun setShowAccessCode(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_ACCESS_CODE, enabled).apply()
    }

    fun recordHceStatus(message: String) {
        preferences.edit().putString(KEY_HCE_STATUS, message).apply()
    }

    fun hceStatus(): String = preferences.getString(KEY_HCE_STATUS, "Not configured").orEmpty()

    private fun writeProfiles(profiles: List<CardProfile>): Boolean =
        preferences.edit().putString(KEY_PROFILES, encodeProfiles(profiles)).commit()

    private fun encodeProfiles(profiles: List<CardProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(JSONObject().apply {
                put("id", profile.profileId)
                put("label", profile.label)
                put("idm", profile.idm)
                profile.spad0?.let { put("spad0", it) }
                profile.idBlock?.let { put("idBlock", it) }
                profile.accessCode?.let { put("accessCode", it) }
            })
        }
        return array.toString()
    }

    private fun decodeProfiles(encoded: String?): List<CardProfile> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    CardProfile.create(
                        label = item.optString("label"),
                        idm = item.optString("idm"),
                        spad0 = item.optNullableString("spad0"),
                        idBlock = item.optNullableString("idBlock"),
                        accessCode = item.optNullableString("accessCode"),
                        profileId = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }
                    )?.let(::add)
                }
            }
        }.onFailure { Log.w(TAG, "Ignoring malformed profile storage", it) }
            .getOrDefault(emptyList())
    }

    private fun migrateExistingInstallation() {
        if (preferences.getBoolean(KEY_MIGRATION_DONE, false)) return

        val imported = mutableListOf<CardProfile>()
        val oldFile = File(appContext.filesDir, "cards.json")
        if (oldFile.isFile) {
            runCatching {
                val array = JSONArray(oldFile.readText())
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    CardProfile.create(
                        label = item.optString("name"),
                        idm = item.optString("idm"),
                        spad0 = item.optNullableString("spad0"),
                        idBlock = item.optNullableString("felicaLiteIdBlock"),
                        accessCode = item.optNullableString("accessCode"),
                        profileId = item.optString("key").ifBlank { java.util.UUID.randomUUID().toString() }
                    )?.let(imported::add)
                }
            }.onFailure { Log.w(TAG, "Existing profile import failed", it) }
        }

        val editor = preferences.edit().putBoolean(KEY_MIGRATION_DONE, true)
        if (profiles().isEmpty() && imported.isNotEmpty()) {
            editor.putString(KEY_PROFILES, encodeProfiles(imported))
            editor.putString(KEY_SELECTED, imported.first().profileId)
        }
        editor.apply()
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf(String::isNotBlank) else null

    companion object {
        private const val TAG = "AimeCardStore"
        private const val PREFERENCES = "aime_simulator"
        private const val KEY_PROFILES = "profiles_v2"
        private const val KEY_SELECTED = "selected_profile"
        private const val KEY_COMPATIBILITY = "compatibility_mode"
        private const val KEY_SHOW_IDM = "show_idm"
        private const val KEY_SHOW_ACCESS_CODE = "show_access_code"
        private const val KEY_HCE_STATUS = "last_hce_status"
        private const val KEY_MIGRATION_DONE = "storage_migrated"
    }
}
