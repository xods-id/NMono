package com.cods.nmono

import android.app.Application
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cods.nmono.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

enum class AppThemeMode { LIGHT, DARK, SEPIA, SOLARIZED, CONTRAST }

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val dataManager = DataManager(application)
    private val dataStore = application.dataStore

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.LIGHT)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _passwordHash = MutableStateFlow<String?>(null)
    val passwordHash: StateFlow<String?> = _passwordHash.asStateFlow()

    private var passwordSalt: ByteArray? = null

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _sortType = MutableStateFlow(SortType.UPDATED)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow<String?>(null)
    val activeFilter: StateFlow<String?> = _activeFilter.asStateFlow()

    private val _customOrder = MutableStateFlow<List<String>>(emptyList())
    val customOrder: StateFlow<List<String>> = _customOrder.asStateFlow()

    private val _tagHistory = MutableStateFlow<List<TagHistory>>(emptyList())
    val tagHistory: StateFlow<List<TagHistory>> = _tagHistory.asStateFlow()

    private val _toastEvent = MutableSharedFlow<ToastData>()
    val toastEvent: SharedFlow<ToastData> = _toastEvent.asSharedFlow()

    private val _autoSaveIndicator = MutableStateFlow(false)
    val autoSaveIndicator: StateFlow<Boolean> = _autoSaveIndicator.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _isFirstRun = MutableStateFlow(true)
    val isFirstRun: StateFlow<Boolean> = _isFirstRun.asStateFlow()

    private val _showExitDialog = MutableStateFlow(true)
    val showExitDialog: StateFlow<Boolean> = _showExitDialog.asStateFlow()

    private var cachedPassword: String? = null
    private var autoSaveJob: Job? = null

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _themeMode.value = AppThemeMode.valueOf(prefs[PreferencesKeys.THEME] ?: "LIGHT")
                _passwordHash.value = prefs[PreferencesKeys.PASSWORD_HASH]
                val saltStr = prefs[PreferencesKeys.PASSWORD_SALT]
                passwordSalt = if (saltStr != null) {
                    try { Base64.decode(saltStr, Base64.NO_WRAP) } catch(e: Exception) { null }
                } else null
                
                _sortType.value = SortType.valueOf(prefs[PreferencesKeys.SORT_TYPE] ?: "UPDATED")
                _biometricEnabled.value = prefs[PreferencesKeys.BIOMETRIC_ENABLED] ?: false
                _isFirstRun.value = prefs[PreferencesKeys.IS_FIRST_RUN] ?: true
                _showExitDialog.value = prefs[PreferencesKeys.SHOW_EXIT_DIALOG] ?: true
                
                val customOrderStr = prefs[PreferencesKeys.CUSTOM_ORDER] ?: "[]"
                _customOrder.value = try {
                    com.google.gson.Gson().fromJson(customOrderStr, object : com.google.gson.reflect.TypeToken<List<String>>(){}.type) ?: emptyList()
                } catch (e: Exception) { emptyList() }

                val tagHistoryStr = prefs[PreferencesKeys.TAG_HISTORY] ?: "[]"
                _tagHistory.value = try {
                    com.google.gson.Gson().fromJson(tagHistoryStr, object : com.google.gson.reflect.TypeToken<List<TagHistory>>(){}.type) ?: emptyList()
                } catch (e: Exception) { emptyList() }
                
                val hash = prefs[PreferencesKeys.PASSWORD_HASH]
                if (hash != null && cachedPassword == null) {
                    _isLocked.value = true
                    _notes.value = emptyList()
                } else if (_notes.value.isEmpty()) {
                    _isLocked.value = false
                    loadNotesFromDisk()
                }
            }
        }
        viewModelScope.launch {
            delay(2000)
            if (!_isLocked.value) purgeOldTrash()
        }
    }

    private fun loadNotesFromDisk() {
        viewModelScope.launch {
            val loaded = dataManager.loadNotes(cachedPassword)
            _notes.value = loaded
        }
    }

    fun unlock(password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val salt = passwordSalt
            if (salt == null) {
                onResult(false)
                return@launch
            }
            val hash = withContext(Dispatchers.Default) { hashPassword(password, salt) }
            if (hash == _passwordHash.value) {
                cachedPassword = password
                _isLocked.value = false
                loadNotesFromDisk()
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun setPassword(password: String) {
        viewModelScope.launch {
            val salt = generateSalt()
            val hash = withContext(Dispatchers.Default) { hashPassword(password, salt) }
            cachedPassword = password
            _passwordHash.value = hash
            passwordSalt = salt
            dataStore.edit { 
                it[PreferencesKeys.PASSWORD_HASH] = hash 
                it[PreferencesKeys.PASSWORD_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
            }
            saveNotesToDisk()
        }
    }

    fun removePassword() {
        cachedPassword = null
        _passwordHash.value = null
        passwordSalt = null
        viewModelScope.launch {
            dataStore.edit { 
                it.remove(PreferencesKeys.PASSWORD_HASH)
                it.remove(PreferencesKeys.PASSWORD_SALT)
            }
            saveNotesToDisk()
        }
    }

    fun lock() {
        cachedPassword = null
        _isLocked.value = true
        _notes.value = emptyList()
    }

    fun setTheme(mode: AppThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch { dataStore.edit { it[PreferencesKeys.THEME] = mode.name } }
    }

    fun toggleTheme() {
        val nextMode = AppThemeMode.entries[(themeMode.value.ordinal + 1) % AppThemeMode.entries.size]
        setTheme(nextMode)
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
        viewModelScope.launch { dataStore.edit { it[PreferencesKeys.SORT_TYPE] = type.name } }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setActiveFilter(tag: String?) { _activeFilter.value = tag }

    fun setBiometricEnabled(enabled: Boolean) {
        _biometricEnabled.value = enabled
        viewModelScope.launch { dataStore.edit { it[PreferencesKeys.BIOMETRIC_ENABLED] = enabled } }
    }

    fun completeSetup() {
        _isFirstRun.value = false
        viewModelScope.launch { dataStore.edit { it[PreferencesKeys.IS_FIRST_RUN] = false } }
    }

    fun setShowExitDialog(show: Boolean) {
        _showExitDialog.value = show
        viewModelScope.launch { dataStore.edit { it[PreferencesKeys.SHOW_EXIT_DIALOG] = show } }
    }

    fun deleteNotes(ids: List<String>) {
        _notes.update { current ->
            current.map { if (it.id in ids) it.copy(deletedAt = System.currentTimeMillis()) else it }
        }
        viewModelScope.launch {
            saveNotesToDisk()
            _toastEvent.emit(ToastData("${ids.size} notes moved to trash", ToastType.ERROR))
        }
    }

    fun saveNote(note: Note) {
        _notes.update { current ->
            val list = current.toMutableList()
            val idx = list.indexOfFirst { it.id == note.id }
            if (idx >= 0) {
                list[idx] = note
            } else {
                list.add(0, note)
                val order = _customOrder.value.toMutableList()
                if (note.id !in order) {
                    order.add(0, note.id)
                    updateCustomOrder(order)
                }
            }
            list
        }
        addTagsToHistory(note.hashtags)
        viewModelScope.launch {
            saveNotesToDisk()
        }
    }

    fun triggerAutoSave(note: Note) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1500)
            saveNote(note)
            showAutoSaveIndicator()
        }
    }

    private fun showAutoSaveIndicator() {
        viewModelScope.launch {
            _autoSaveIndicator.value = true
            delay(1000)
            _autoSaveIndicator.value = false
        }
    }

    fun deleteNote(id: String) {
        _notes.update { current ->
            current.map { if (it.id == id) it.copy(deletedAt = System.currentTimeMillis()) else it }
        }
        viewModelScope.launch {
            saveNotesToDisk()
            _toastEvent.emit(ToastData("Note moved to trash", ToastType.INFO, undoAction = { restoreNote(id) }))
        }
    }

    fun restoreNote(id: String) {
        _notes.update { current ->
            current.map { if (it.id == id) it.copy(deletedAt = null) else it }
        }
        viewModelScope.launch {
            saveNotesToDisk()
            _toastEvent.emit(ToastData("Note restored", ToastType.SUCCESS))
        }
    }

    fun permadeleteNote(id: String) {
        _notes.update { current -> current.filter { it.id != id } }
        val newOrder = _customOrder.value.filter { it != id }
        updateCustomOrder(newOrder)
        viewModelScope.launch { saveNotesToDisk() }
    }

    fun emptyTrash() {
        _notes.update { current -> current.filter { it.deletedAt == null } }
        viewModelScope.launch { saveNotesToDisk() }
    }

    fun reorderNotes(fromId: String, toId: String) {
        val order = _customOrder.value.toMutableList()
        val fromIdx = order.indexOf(fromId)
        val toIdx = order.indexOf(toId)
        if (fromIdx >= 0 && toIdx >= 0) {
            order.removeAt(fromIdx)
            order.add(toIdx, fromId)
            updateCustomOrder(order)
        }
    }

    private fun updateCustomOrder(order: List<String>) {
        _customOrder.value = order
        viewModelScope.launch {
            val json = com.google.gson.Gson().toJson(order)
            dataStore.edit { it[PreferencesKeys.CUSTOM_ORDER] = json }
        }
    }

    private fun addTagsToHistory(tags: List<String>) {
        val history = _tagHistory.value.toMutableList()
        tags.forEach { tag ->
            val normalized = tag.lowercase().removePrefix("#")
            if (normalized.isEmpty()) return@forEach
            val existing = history.find { it.name == normalized }
            if (existing != null) {
                existing.count++
                existing.lastUsed = System.currentTimeMillis()
            } else {
                history.add(TagHistory(normalized, 1, System.currentTimeMillis()))
            }
        }
        history.sortByDescending { it.count }
        val limitedHistory = if (history.size > 50) history.take(50) else history
        _tagHistory.value = limitedHistory
        viewModelScope.launch {
            val json = com.google.gson.Gson().toJson(limitedHistory)
            dataStore.edit { it[PreferencesKeys.TAG_HISTORY] = json }
        }
    }

    fun getFilteredNotes(): List<Note> {
        val query = _searchQuery.value.trim().lowercase()
        val filter = _activeFilter.value
        
        var filtered = _notes.value.filter { note ->
            if (filter != null && !note.hashtags.any { it.lowercase().removePrefix("#") == filter }) return@filter false
            if (query.isNotEmpty() && !note.title.lowercase().contains(query) && !note.content.lowercase().contains(query)) return@filter false
            true
        }

        return when (_sortType.value) {
            SortType.UPDATED -> filtered.sortedByDescending { it.updatedAt }
            SortType.CREATED -> filtered.sortedByDescending { it.createdAt }
            SortType.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortType.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortType.CUSTOM -> {
                val order = _customOrder.value
                filtered.sortedBy { 
                    val idx = order.indexOf(it.id)
                    if (idx == -1) Int.MAX_VALUE else idx
                }
            }
        }
    }

    fun getTrashNotes(): List<Note> = _notes.value.filter { it.deletedAt != null }

    fun getAllUsedTags(): List<Pair<String, Int>> {
        val tagMap = mutableMapOf<String, Int>()
        _notes.value.filter { it.deletedAt == null }.forEach { note ->
            note.hashtags.forEach { tag ->
                val normalized = tag.lowercase().removePrefix("#")
                tagMap[normalized] = (tagMap[normalized] ?: 0) + 1
            }
        }
        return tagMap.entries.map { it.key to it.value }.sortedByDescending { it.second }
    }

    private suspend fun purgeOldTrash() {
        val thirtyDays = 30L * 24 * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        _notes.update { current ->
            current.filter { it.deletedAt == null || (now - it.deletedAt!!) < thirtyDays }
        }
        saveNotesToDisk()
    }

    private suspend fun saveNotesToDisk() = withContext(Dispatchers.IO) {
        if (_isLocked.value && _notes.value.isEmpty()) return@withContext 
        dataManager.saveNotes(_notes.value, cachedPassword)
    }

    fun exportData(password: String): String {
        val json = com.google.gson.Gson().toJson(ExportData(
            exportedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
            notes = _notes.value,
            tagHistory = _tagHistory.value,
            customOrder = _customOrder.value
        ))
        return dataManager.encrypt(json, password)
    }

    fun importData(dataString: String, password: String): Int {
        return try {
            val content = dataString.trim()
            val json = if (content.startsWith("{") || content.startsWith("[")) {
                content 
            } else {
                dataManager.decrypt(dataString, password)
            }
            
            if (json.isBlank()) return -1

            val importedNotes = if (json.startsWith("[")) {
                com.google.gson.Gson().fromJson<List<Note>>(json, object : com.google.gson.reflect.TypeToken<List<Note>>(){}.type)
            } else {
                val data = com.google.gson.Gson().fromJson(json, ExportData::class.java)
                data?.notes
            }

            if (importedNotes == null) return 0
            
            var addedCount = 0
            _notes.update { current ->
                val list = current.toMutableList()
                importedNotes.forEach { importedNote ->
                    val idx = list.indexOfFirst { it.id == importedNote.id }
                    if (idx >= 0) {
                        if (importedNote.updatedAt > list[idx].updatedAt) {
                            list[idx] = importedNote
                            addedCount++
                        }
                    } else {
                        list.add(0, importedNote)
                        addedCount++
                    }
                }
                list
            }
            
            viewModelScope.launch { saveNotesToDisk() }
            addedCount
        } catch (e: Exception) { -1 }
    }

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        viewModelScope.launch { _toastEvent.emit(ToastData(message, type)) }
    }
}
