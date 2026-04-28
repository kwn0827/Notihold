package com.lumifold.notihold

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    companion object {
        val IS_AD_REMOVED = booleanPreferencesKey("is_ad_removed")
    }

    val isAdRemoved: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_AD_REMOVED] ?: false
        }

    suspend fun setAdRemoved(isRemoved: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_AD_REMOVED] = isRemoved
        }
    }
}
