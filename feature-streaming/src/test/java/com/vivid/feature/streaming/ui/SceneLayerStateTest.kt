package com.vivid.feature.streaming.ui

import com.vivid.core.data.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class SceneLayerStateTest {
    @ParameterizedTest @EnumSource(SceneVideoSource::class)
    fun `persisted active empty source closes the scene gate before manual reactivation`(source: SceneVideoSource) = runTest {
        val scene = StreamScene("saved", "Saved", widgets = emptyList(), videoSource = source)
        val repo = mockk<SceneRepository> {
            every { scenesFlow } returns MutableStateFlow(listOf(scene))
            every { widgetsFlow } returns MutableStateFlow(emptyList())
            every { activeSceneIdFlow } returns MutableStateFlow(scene.id)
            every { libraryInitializedFlow } returns MutableStateFlow(true)
        }
        assertEquals(source == SceneVideoSource.EMPTY, SceneWidgetsOverlayViewModel(repo).state.first().sourceEmpty)
    }
}
