package org.dpdns.sebastianfiser.filetransferer.network

import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit

private val Context.dataStore by preferencesDataStore(name = "settings")

class LastServerStore(context: Context) {
    private val ctx = context.applicationContext

    companion object {
        private val KEY = stringPreferencesKey("last_server_url")
    } 

    suspend fun load(): String? {
        val preferences = ctx.dataStore.data.first()

        val ret: String? = preferences[KEY] 

        return ret
    }

    suspend fun save(url: String) {
        ctx.dataStore.edit { prefs ->
            prefs[KEY] = url

        }
    }
}