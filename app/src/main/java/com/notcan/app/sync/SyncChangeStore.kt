package com.notcan.app.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class PendingSyncChange(
    val entity: String,
    val entityId: String,
    val operation: String,
    val changedAtEpochMs: Long
)

internal class SyncChangeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markUpsert(entity: String, entityId: String) = put(entity, entityId, "UPSERT")
    fun markDelete(entity: String, entityId: String) = put(entity, entityId, "DELETE")

    @Synchronized
    fun pending(): List<PendingSyncChange> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PENDING, "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val entity = item.optString("entity")
                val entityId = item.optString("entityId")
                val operation = item.optString("operation")
                if (entity.isBlank() || entityId.isBlank() || operation.isBlank()) continue
                add(PendingSyncChange(entity, entityId, operation, item.optLong("changedAtEpochMs")))
            }
        }.sortedBy { it.changedAtEpochMs }
    }.getOrDefault(emptyList())

    @Synchronized
    fun clear(entity: String, entityId: String) {
        val kept = pending().filterNot { it.entity == entity && it.entityId == entityId }
        save(kept)
    }

    fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val value = "android-${UUID.randomUUID()}"
        prefs.edit().putString(KEY_DEVICE_ID, value).apply()
        return value
    }

    @Synchronized
    private fun put(entity: String, entityId: String, operation: String) {
        val current = pending().filterNot { it.entity == entity && it.entityId == entityId }.toMutableList()
        current += PendingSyncChange(entity, entityId, operation, System.currentTimeMillis())
        save(current)
    }

    private fun save(items: List<PendingSyncChange>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject()
                .put("entity", item.entity)
                .put("entityId", item.entityId)
                .put("operation", item.operation)
                .put("changedAtEpochMs", item.changedAtEpochMs))
        }
        prefs.edit().putString(KEY_PENDING, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "notcan_sync_changes"
        private const val KEY_PENDING = "pending"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
