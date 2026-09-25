package com.example.moducommerce.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.moducommerce.core.model.UserProfile
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.commerceDataStore: DataStore<Preferences> by preferencesDataStore(name = SessionStore.STORE_NAME)

/** 모두 계정 토큰과 userinfo 프로필. 옛 XML 앱의 SharedPreferences 와는 별개라 재설치 후 다시 로그인한다. */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
) {

    private val dataStore = context.commerceDataStore

    val profile: Flow<UserProfile?> = dataStore.data.map { prefs -> decode(prefs[KEY_PROFILE]) }

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs -> !prefs[KEY_ACCESS].isNullOrBlank() }

    suspend fun accessToken(): String? = dataStore.data.first()[KEY_ACCESS]?.ifBlank { null }

    suspend fun refreshToken(): String? = dataStore.data.first()[KEY_REFRESH]?.ifBlank { null }

    suspend fun saveTokens(access: String, refresh: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS] = access
            prefs[KEY_REFRESH] = refresh
        }
    }

    suspend fun saveProfile(profile: UserProfile) {
        dataStore.edit { prefs -> prefs[KEY_PROFILE] = gson.toJson(profile) }
    }

    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS)
            prefs.remove(KEY_REFRESH)
            prefs.remove(KEY_PROFILE)
        }
    }

    private fun decode(json: String?): UserProfile? =
        if (json.isNullOrBlank()) null else runCatching { gson.fromJson(json, UserProfile::class.java) }.getOrNull()

    companion object {
        const val STORE_NAME = "modu-commerce"
        val KEY_ACCESS = stringPreferencesKey("access-token")
        val KEY_REFRESH = stringPreferencesKey("refresh-token")
        val KEY_PROFILE = stringPreferencesKey("profile")
    }
}
