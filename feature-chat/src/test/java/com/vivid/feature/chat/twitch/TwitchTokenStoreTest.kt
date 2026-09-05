package com.vivid.feature.chat.twitch

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Path
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir

class TwitchTokenStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private val key = SecretKeySpec(ByteArray(32) { (it + 3).toByte() }, "AES")

    data class StoreHandle(
        val tokenStore: DataStoreTwitchTokenStore,
        val scope: CoroutineScope,
        val job: Job,
    ) {
        /** Beendet den DataStore-Scope, damit dieselbe Datei erneut genutzt werden kann. */
        suspend fun release() {
            scope.cancel()
            job.join()
        }
    }

    /** Eigener, beendbarer Scope pro Datei — DataStore verbietet zwei aktive Instanzen pro Datei. */
    private fun store(fileName: String, cipherKey: SecretKey = key): StoreHandle {
        val job = Job()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + job)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tempDir.toFile(), fileName) },
        )
        return StoreHandle(DataStoreTwitchTokenStore(dataStore, AesGcmTokenCipher(cipherKey)), scope, job)
    }

    private val session = TwitchTokenSession(
        accessToken = "oauth:access-token",
        refreshToken = "oauth:refresh-token",
        expiresAtMillis = 1_700_000_000_000L,
        scopes = listOf("channel:manage:broadcast", "user:read:chat"),
    )

    @Test
    fun `persists and reloads an oauth session encrypted`() = runTest {
        val handle = store("session.preferences_pb")
        handle.tokenStore.saveSession(session)

        val reloaded = handle.tokenStore.loadSession()

        assertEquals(session, reloaded)
        handle.release()
    }

    @Test
    fun `returns null before anything was saved`() = runTest {
        val handle = store("empty.preferences_pb")
        assertNull(handle.tokenStore.loadSession())
        handle.release()
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "DataStore 1.1.7 nutzt einen atomaren File-Move, der auf Windows kein bestehendes Ziel ersetzen kann (zweiter Write auf dieselbe Datei).")
    fun `clear removes the stored session`() = runTest {
        // Erneute Schreib-Rename über eine noch aktive DataStore-Instanz
        // schlägt fehl — deshalb pro Operation ein frisches Handle mit `release()`
        // dazwischen (dieselbe Datei, gleiches Verhalten unter Android).
        val first = store("clear.preferences_pb")
        first.tokenStore.saveSession(session)
        first.release()

        val second = store("clear.preferences_pb")
        second.tokenStore.clear()

        assertNull(second.tokenStore.loadSession())
        second.release()
    }

    @Test
    fun `persists the session across a fresh store instance`() = runTest {
        val first = store("reload.preferences_pb")
        first.tokenStore.saveSession(session)
        first.release()

        val second = store("reload.preferences_pb")

        assertEquals(session, second.tokenStore.loadSession())
        second.release()
    }

    @Test
    fun `treats an undecryptable session as absent`() = runTest {
        // Erster Store schreibt mit Schlüssel A; der zweite Store (gleiche Datei,
        // anderer Schlüssel) kann den Ciphertext nicht lesen und liefert `null`.
        val keyB = SecretKeySpec(ByteArray(32) { (it * 7 + 1).toByte() }, "AES")
        val first = store("undecryptable.preferences_pb")
        first.tokenStore.saveSession(session)
        first.release()

        val foreign = store("undecryptable.preferences_pb", keyB)

        assertNull(foreign.tokenStore.loadSession())
        foreign.release()
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "DataStore 1.1.7 nutzt einen atomaren File-Move, der auf Windows kein bestehendes Ziel ersetzen kann (zweiter Write auf dieselbe Datei).")
    fun `clear is idempotent`() = runTest {
        val first = store("idempotent.preferences_pb")
        first.tokenStore.saveSession(session)
        first.release()

        val second = store("idempotent.preferences_pb")
        second.tokenStore.clear()
        second.release()

        val third = store("idempotent.preferences_pb")
        third.tokenStore.clear()
        assertNull(third.tokenStore.loadSession())
        third.release()
    }
}