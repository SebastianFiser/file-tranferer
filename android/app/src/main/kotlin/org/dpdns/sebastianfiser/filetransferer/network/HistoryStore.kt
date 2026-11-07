package org.dpdns.sebastianfiser.filetransferer.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class HistoryEntry(
    val timestamp: Long,
    val fileCount: Int,
    val totalBytes: Long,
    val verified: Boolean,
    val serverName: String
)

class HistoryStore(context: Context) {
    private val ctx = context.applicationContext

    companion object {
        private val KEY = stringPreferencesKey("history_json")
        private const val MAX_ENTRIES = 50
    }

    val entries: Flow<List<HistoryEntry>> = ctx.dataStore.data.map { prefs ->
        decode(prefs[KEY])
    }

    suspend fun clear() {
        ctx.dataStore.edit { it.remove(KEY) }
    }

    suspend fun add(entry: HistoryEntry) {
        ctx.dataStore.edit { prefs ->
            val updated = (listOf(entry) + decode(prefs[KEY])).take(MAX_ENTRIES)
            prefs[KEY] = encode(updated)
        }
    }

    private fun encode(list: List<HistoryEntry>): String {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("t", e.timestamp)
                put("n", e.fileCount)
                put("b", e.totalBytes)
                put("v", e.verified)
                put("s", e.serverName)
            })
        }
        return arr.toString()
    }

    private fun decode(json: String?): List<HistoryEntry> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(
                    timestamp = o.getLong("t"),
                    fileCount = o.getInt("n"),
                    totalBytes = o.getLong("b"),
                    verified = o.getBoolean("v"),
                    serverName = o.getString("s")
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}