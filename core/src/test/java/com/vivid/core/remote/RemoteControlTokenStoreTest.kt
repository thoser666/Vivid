package com.vivid.core.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class RemoteControlTokenStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private fun store(name: String, scope: CoroutineScope): RemoteControlTokenStore {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tempDir.toFile(), "$name.preferences_pb") },
        )
        return RemoteControlTokenStore(dataStore)
    }

    @Test
    fun `getOrCreateToken generates a non-blank token`() = runTest {
        val token = store("a", this).getOrCreateToken()
        assertFalse(token.isBlank())
    }

    @Test
    fun `getOrCreateToken returns the same token on subsequent calls`() = runTest {
        val tokenStore = store("b", this)
        val first = tokenStore.getOrCreateToken()
        val second = tokenStore.getOrCreateToken()
        assertEquals(first, second)
    }

    @Test
    fun `token is written to the datastore and readable via tokenFlow`() = runTest {
        val tokenStore = store("persist", this)
        val token = tokenStore.getOrCreateToken()
        // Der Flow liest aus derselben Datei — Token ist damit über App-Starts stabil.
        assertEquals(token, tokenStore.tokenFlow.first())
    }

    @Test
    fun `different stores generate different tokens`() = runTest {
        val tokenA = store("c1", this).getOrCreateToken()
        val tokenB = store("c2", this).getOrCreateToken()
        assertNotEquals(tokenA, tokenB)
    }
}
