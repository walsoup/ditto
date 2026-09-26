package com.walsoup.ditto.data

import android.content.Context
import android.content.SharedPreferences
import com.walsoup.ditto.core.audio.AudioFormatType
import com.walsoup.ditto.core.audio.HistoryItem
import com.walsoup.ditto.core.audio.VoiceFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HistoryManager(private val context: Context) {

    companion object {
        private var sharedAppFilterEnabled: MutableStateFlow<Boolean>? = null
        private var sharedSelectedPackages: MutableStateFlow<Set<String>>? = null
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ditto_settings", Context.MODE_PRIVATE)

    private val historyFile = File(context.filesDir, "recordings_history.json")

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val _isAppFilterEnabled: MutableStateFlow<Boolean> = synchronized(HistoryManager::class.java) {
        sharedAppFilterEnabled ?: MutableStateFlow(prefs.getBoolean("app_filter_enabled", false)).also {
            sharedAppFilterEnabled = it
        }
    }
    val isAppFilterEnabledFlow: StateFlow<Boolean> = _isAppFilterEnabled.asStateFlow()

    private val _selectedAppPackages: MutableStateFlow<Set<String>> = synchronized(HistoryManager::class.java) {
        sharedSelectedPackages ?: MutableStateFlow(
            prefs.getStringSet("selected_app_packages", emptySet())?.toSet() ?: emptySet()
        ).also {
            sharedSelectedPackages = it
        }
    }
    val selectedAppPackagesFlow: StateFlow<Set<String>> = _selectedAppPackages.asStateFlow()

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "app_filter_enabled" -> {
                _isAppFilterEnabled.value = prefs.getBoolean("app_filter_enabled", false)
            }
            "selected_app_packages" -> {
                _selectedAppPackages.value = prefs.getStringSet("selected_app_packages", emptySet())?.toSet() ?: emptySet()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        loadHistory()
    }

    var isHistoryEnabled: Boolean
        get() = prefs.getBoolean("history_enabled", true)
        set(value) {
            prefs.edit().putBoolean("history_enabled", value).apply()
            if (!value) {
                clearHistory()
            }
        }

    var isAutoPasteEnabled: Boolean
        get() = prefs.getBoolean("auto_paste_enabled", true)
        set(value) {
            prefs.edit().putBoolean("auto_paste_enabled", value).apply()
        }

    var selectedFormat: AudioFormatType
        get() {
            val name = prefs.getString("audio_format", AudioFormatType.M4A.name)
            return try {
                AudioFormatType.valueOf(name ?: AudioFormatType.M4A.name)
            } catch (_: Exception) {
                AudioFormatType.M4A
            }
        }
        set(value) {
            prefs.edit().putString("audio_format", value.name).apply()
        }

    var isFloatingServiceEnabled: Boolean
        get() = prefs.getBoolean("floating_service_enabled", false)
        set(value) {
            prefs.edit().putBoolean("floating_service_enabled", value).apply()
        }

    var isAppFilterEnabled: Boolean
        get() = _isAppFilterEnabled.value
        set(value) {
            _isAppFilterEnabled.value = value
            prefs.edit().putBoolean("app_filter_enabled", value).apply()
        }

    var selectedAppPackages: Set<String>
        get() = _selectedAppPackages.value
        set(value) {
            _selectedAppPackages.value = value
            prefs.edit().putStringSet("selected_app_packages", value).apply()
        }

    fun toggleAppPackage(packageName: String, isSelected: Boolean) {
        val current = _selectedAppPackages.value.toMutableSet()
        if (isSelected) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        selectedAppPackages = current
    }

    fun selectAllApps(packageNames: Collection<String>) {
        val current = _selectedAppPackages.value.toMutableSet()
        current.addAll(packageNames)
        selectedAppPackages = current
    }

    fun clearAllSelectedApps() {
        selectedAppPackages = emptySet()
    }

    fun loadHistory() {
        if (!isHistoryEnabled) {
            _history.value = emptyList()
            return
        }
        scope.launch {
            if (!historyFile.exists()) {
                _history.value = emptyList()
                return@launch
            }
            try {
                val jsonStr = historyFile.readText()
                val array = JSONArray(jsonStr)
                val list = mutableListOf<HistoryItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val file = File(obj.getString("filePath"))
                    if (file.exists()) {
                        val formatStr = obj.optString("format", AudioFormatType.WAV.name)
                        val format = AudioFormatType.entries.find {
                            it.name.equals(formatStr, ignoreCase = true) || it.displayName.equals(formatStr, ignoreCase = true)
                        } ?: AudioFormatType.WAV

                        val filterStr = obj.optString("filterName", obj.optString("filter", VoiceFilter.RAW.name))
                        val filter = VoiceFilter.entries.find {
                            it.name.equals(filterStr, ignoreCase = true) || it.displayName.equals(filterStr, ignoreCase = true)
                        } ?: VoiceFilter.RAW

                        list.add(
                            HistoryItem(
                                id = obj.getString("id"),
                                fileName = obj.getString("fileName"),
                                filePath = obj.getString("filePath"),
                                durationMs = obj.getLong("durationMs"),
                                format = format,
                                filter = filter,
                                createdAt = obj.getLong("createdAt")
                            )
                        )
                    }
                }
                _history.value = list.sortedByDescending { it.createdAt }
            } catch (e: Exception) {
                e.printStackTrace()
                _history.value = emptyList()
            }
        }
    }

    fun addRecording(
        file: File,
        durationMs: Long,
        filter: VoiceFilter,
        format: AudioFormatType
    ) {
        if (!isHistoryEnabled) return

        val item = HistoryItem(
            id = System.currentTimeMillis().toString(),
            fileName = file.name,
            filePath = file.absolutePath,
            durationMs = durationMs,
            format = format,
            filter = filter,
            createdAt = System.currentTimeMillis()
        )

        val updated = listOf(item) + _history.value
        _history.value = updated
        saveHistoryToFile(updated)
    }

    fun addRecording(
        file: File,
        durationMs: Long,
        filterName: String,
        format: AudioFormatType
    ) {
        val filter = VoiceFilter.entries.find {
            it.name.equals(filterName, ignoreCase = true) || it.displayName.equals(filterName, ignoreCase = true)
        } ?: VoiceFilter.RAW
        addRecording(file, durationMs, filter, format)
    }

    fun deleteItem(id: String) {
        val item = _history.value.find { it.id == id }
        item?.let {
            try {
                File(it.filePath).delete()
            } catch (_: Exception) {}
        }
        val updated = _history.value.filterNot { it.id == id }
        _history.value = updated
        saveHistoryToFile(updated)
    }

    fun clearHistory() {
        val currentItems = _history.value
        _history.value = emptyList()
        scope.launch {
            currentItems.forEach {
                try {
                    File(it.filePath).delete()
                } catch (_: Exception) {}
            }
            if (historyFile.exists()) {
                historyFile.delete()
            }
        }
    }

    private fun saveHistoryToFile(list: List<HistoryItem>) {
        scope.launch {
            try {
                val array = JSONArray()
                for (item in list) {
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("fileName", item.fileName)
                        put("filePath", item.filePath)
                        put("durationMs", item.durationMs)
                        put("format", item.format.name)
                        put("filterName", item.filter.name)
                        put("createdAt", item.createdAt)
                    }
                    array.put(obj)
                }
                historyFile.writeText(array.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
