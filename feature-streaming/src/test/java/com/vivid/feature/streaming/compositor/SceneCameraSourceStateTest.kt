package com.vivid.feature.streaming.compositor

import androidx.compose.runtime.saveable.SaverScope
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.ui.scene.reusableSceneCameraSource
import com.vivid.feature.streaming.ui.scene.sceneCameraSourceSaver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneCameraSourceStateTest {
    @Test fun `permission completion resolves a pending physical camera against existing default sources again`() {
        val existing = SceneSource("saved", SceneSourceType.CAMERA, name = "My camera")
        val pending = SceneSource("new", SceneSourceType.CAMERA, "rear-device")
        assertSame(pending, reusableSceneCameraSource(listOf(existing), pending) { "" })
        assertSame(existing, reusableSceneCameraSource(listOf(existing), pending) { "rear-device" })
    }
    @Test fun `pending source descriptors survive activity recreation and malformed state is discarded`() {
        val source = SceneSource("pending-id", SceneSourceType.UVC, "external-camera", "External")
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = value is String }
        val raw = with(sceneCameraSourceSaver) { scope.save(source) }
        assertNotNull(raw)
        assertEquals(source, sceneCameraSourceSaver.restore(raw!!))
        assertNull(sceneCameraSourceSaver.restore(listOf("invalid")))
        assertNull(with(sceneCameraSourceSaver) { scope.save(null) })
    }
    @Test fun `camera aliases preserve an existing source ID and label without merging distinct devices`() {
        val existing = SceneSource("saved", SceneSourceType.UVC, "external", "Named camera")
        val alias = existing.copy(id = "candidate", type = SceneSourceType.CAMERA, name = "New label")
        assertSame(existing, reusableSceneCameraSource(listOf(existing), alias) { it.locator })
        val another = alias.copy(locator = "another")
        assertSame(another, reusableSceneCameraSource(listOf(existing), another) { it.locator })
    }
}
