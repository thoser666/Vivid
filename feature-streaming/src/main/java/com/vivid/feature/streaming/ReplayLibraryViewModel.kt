package com.vivid.feature.streaming

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Ein Replay-Eintrag der Bibliothek. */
data class ReplayItem(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/** UI-Zustand der Replay-Bibliothek. */
data class ReplayLibraryUiState(
    val items: List<ReplayItem> = emptyList(),
    val loading: Boolean = false,
    /** Replay, das zur Bestätigung des Löschens ausgewählt wurde. */
    val deleteCandidate: ReplayItem? = null,
    /** Replay, das gerade im Player geöffnet ist. */
    val playing: ReplayItem? = null,
)

/**
 * ViewModel der Replay-Bibliothek: lädt die MP4-Liste, löscht Einträge
 * (einzeln/alles) und erzeugt den Share-Intent. Die Wiedergabe selbst
 * rendert [com.vivid.feature.playback.StreamPlayer] mit der FileProvider-Uri.
 */
@HiltViewModel
class ReplayLibraryViewModel @Inject constructor(
    private val library: ReplayLibrary,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReplayLibraryUiState())
    val uiState: StateFlow<ReplayLibraryUiState> = _uiState.asStateFlow()

    /** Lädt die Replays neu. */
    fun refresh() {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val items = library.items().map(::toItem)
            _uiState.value = _uiState.value.copy(items = items, loading = false)
        }
    }

    /** Öffnet ein Replay im Player (FileProvider-Uri für den Media3-Player). */
    fun open(item: ReplayItem) {
        _uiState.value = _uiState.value.copy(playing = item)
    }

    /** Schließt den Player. */
    fun close() {
        _uiState.value = _uiState.value.copy(playing = null)
    }

    /** Fordert Lösch-Bestätigung an. */
    fun requestDelete(item: ReplayItem) {
        _uiState.value = _uiState.value.copy(deleteCandidate = item)
    }

    /** Verwirft die Lösch-Anfrage. */
    fun dismissDelete() {
        _uiState.value = _uiState.value.copy(deleteCandidate = null)
    }

    /** Löscht das Bestätigungs-Replay und lädt die Liste neu. */
    fun confirmDelete() {
        val candidate = _uiState.value.deleteCandidate ?: return
        _uiState.value = _uiState.value.copy(deleteCandidate = null)
        viewModelScope.launch {
            library.delete(candidate.file)
            refresh()
        }
    }

    /** Löscht alle Replays und lädt die Liste neu. */
    fun confirmDeleteAll() {
        viewModelScope.launch {
            library.deleteAll()
            refresh()
        }
    }

    /**
     * Erzeugt den Teilen-Intent (video/mp4) über den FileProvider.
     *
     * @return null, wenn die Datei nicht mehr existiert.
     */
    fun shareIntent(item: ReplayItem): Intent? {
        val file = item.file
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun toItem(file: File) = ReplayItem(
        file = file,
        name = file.name,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
    )
}
