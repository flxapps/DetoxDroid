package com.flx_apps.digitaldetox.widgets.minimal_launcher

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.data.DataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class WidgetSelectedApp(
    val packageName: String,
    val customLabel: String? = null,
)

object MinimalLauncherWidgetConfigRepository {
    private const val SELECTED_APPS_PREFIX = "minimal_launcher_widget_selected_apps_"
    private const val PACKAGE_SEPARATOR = ","

    suspend fun saveSelectedAppsAsync(appWidgetId: Int, selectedApps: List<WidgetSelectedApp>) {
        val normalizedApps = selectedApps.asSequence()
            .map {
                WidgetSelectedApp(
                    packageName = it.packageName.trim(),
                    customLabel = it.customLabel?.trim()?.ifBlank { null }
                )
            }
            .filter { it.packageName.isNotEmpty() }
            .distinctBy { it.packageName }
            .toList()
        val serializedApps = JSONArray().apply {
            normalizedApps.forEach { selectedApp ->
                put(
                    JSONObject().apply {
                        put("packageName", selectedApp.packageName)
                        put("customLabel", selectedApp.customLabel)
                    }
                )
            }
        }.toString()
        DataStore.edit { preferences ->
            preferences[selectedAppsKey(appWidgetId)] = serializedApps
        }
    }

    suspend fun getSelectedAppsAsync(appWidgetId: Int): List<WidgetSelectedApp> {
        val persistedValue = DataStore.data
            .map { preferences -> preferences[selectedAppsKey(appWidgetId)] }
            .first()
        return parseSelectedApps(persistedValue)
    }

    suspend fun deleteSelectedPackagesAsync(appWidgetId: Int) {
        DataStore.edit { preferences ->
            preferences.remove(selectedAppsKey(appWidgetId))
        }
    }

    private fun selectedAppsKey(appWidgetId: Int) =
        stringPreferencesKey("$SELECTED_APPS_PREFIX$appWidgetId")

    private fun parseSelectedApps(serialized: String?): List<WidgetSelectedApp> {
        if (serialized.isNullOrBlank()) {
            return emptyList()
        }
        val trimmed = serialized.trim()
        if (!trimmed.startsWith("[")) {
            return trimmed.split(PACKAGE_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .map { WidgetSelectedApp(packageName = it) }
        }
        return runCatching {
            val jsonArray = JSONArray(trimmed)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.optJSONObject(index) ?: continue
                    val packageName = jsonObject.optString("packageName").trim()
                    if (packageName.isBlank()) continue
                    val customLabel = jsonObject.optString("customLabel")
                        .trim()
                        .takeUnless { it.equals("null", ignoreCase = true) || it.isBlank() }
                    add(WidgetSelectedApp(packageName = packageName, customLabel = customLabel))
                }
            }.distinctBy { it.packageName }
        }.getOrDefault(emptyList())
    }
}
