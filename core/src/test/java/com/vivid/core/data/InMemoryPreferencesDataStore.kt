package com.vivid.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Flüchtiger [DataStore] für Tests, die mehrfach in denselben Store schreiben.
 *
 * **Warum es das gibt (07.11.2026, PR #338).** DataStore 1.3.0 schreibt jeden
 * Satz zuerst in eine Scratch-Datei und benennt sie danach auf die Zieldatei um
 * (`FileStorageConnection.writeScope`). Unter Windows scheitert
 * `File.renameTo` auf ein **bereits vorhandenes** Ziel, deshalb wirft jedes
 * zweite Schreiben in denselben Store
 * `IOException: Unable to rename … This likely means that there are multiple
 * instances of DataStore for this file`. Auf Linux und macOS ist das Umbenennen
 * über eine vorhandene Datei erlaubt — dort fällt es nie auf, weshalb die
 * Ubuntu-CI die betroffenen Tests grün fährt.
 *
 * Die Tests, die diesen Store nutzen, prüfen die Abbildung von
 * [SettingsRepository] auf Preferences (Migration alter Einzelziele, Auswahl
 * eines Profils, Unabhängigkeit mehrerer Repository-Instanzen) — nicht die
 * Datei-Ablage von DataStore. Ein flüchtiger Store deckt genau diese Absicht ab
 * und verhält sich auf jeder Plattform gleich.
 *
 * Absichtlich **keine** Verhaltenssimulation: es gibt keine Datei, keine
 * Serialisierung und keinen Corrupt-Handler. Wer dieses Verhalten prüfen will,
 * braucht einen Datei-Store und einen Linux-Runner.
 */
internal class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val lock = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = lock.withLock {
        transform(state.value).also { state.value = it }
    }
}
