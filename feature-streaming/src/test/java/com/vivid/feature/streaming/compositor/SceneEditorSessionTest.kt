package com.vivid.feature.streaming.compositor

import com.vivid.core.data.*
import com.vivid.feature.streaming.scene.editor.SceneEditorSession
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneEditorSessionTest {
    private fun editor(): SceneEditorSession {
        var id = 0
        return SceneEditorSession(StreamScene("scene", "Scene", streamKey = "preserve"), newId = { "layer-${++id}" })
    }

    @Test fun `migration and editing preserve scene settings and stable source identity`() {
        val editor = editor()
        val original = editor.state.composition.sources.single()
        editor.addSource(original)
        assertEquals(1, editor.state.composition.sources.size)
        assertEquals(2, editor.state.composition.layers.size)
        assertEquals("preserve", editor.state.scene.streamKey)
        assertEquals(original.id, editor.state.selectedLayer!!.sourceId)
        editor.deleteSelected()
        assertEquals(1, editor.state.composition.sources.size)
    }

    @Test fun `duplicate reorder visibility and delete keep unrelated layers intact`() {
        val editor = editor()
        val primary = editor.state.composition.layers.single()
        editor.duplicateSelected()
        val copy = editor.state.selectedLayer!!
        editor.setVisible(false)
        editor.moveSelected(-1)
        assertEquals(copy.id, editor.state.composition.layers.first().id)
        assertFalse(editor.state.selectedLayer!!.visible)
        editor.deleteSelected()
        assertEquals(listOf(primary), editor.state.composition.layers)
        editor.undo()
        assertFalse(editor.state.selectedLayer!!.visible)
    }

    @Test fun `a long drag is one undo step with redo and alignment guides`() {
        val editor = editor()
        val start = editor.state.selectedLayer!!.transform
        editor.beginGesture()
        repeat(20) { editor.transformGesture(start.copy(x = 0.1f + it / 100f), 1000, 1000) }
        editor.transformGesture(start.copy(x = 0.506f), 1000, 1000)
        assertEquals(0.5f, editor.state.verticalGuide)
        editor.endGesture()
        editor.undo()
        assertEquals(start, editor.state.selectedLayer!!.transform)
        assertFalse(editor.state.canUndo)
        editor.redo()
        assertEquals(0.5f, editor.state.selectedLayer!!.transform.x, .00001f)
    }

    @Test fun `hit testing selects frontmost visible layer and respects rotation`() {
        val editor = editor()
        editor.addSource(SceneSource("logo", SceneSourceType.IMAGE, "content://logo"))
        val logo = editor.state.selectedLayer!!.id
        editor.setTransform(SceneTransform(0.2f, 0.2f, 0.4f, 0.4f, 90f))
        assertEquals(logo, editor.selectAt(0.4f, 0.4f))
        editor.setVisible(false)
        assertNotEquals(logo, editor.selectAt(0.4f, 0.4f))
    }

    @Test fun `presets retain sources crop-independent identity and extra layers`() {
        val editor = editor()
        editor.addSource(SceneSource("logo", SceneSourceType.IMAGE))
        editor.duplicateSelected()
        val ids = editor.state.composition.layers.map { it.id }
        editor.layout(SceneLayoutPreset.PICTURE_IN_PICTURE)
        assertEquals(ids, editor.state.composition.layers.map { it.id })
        assertEquals(0.67f, editor.state.composition.layers[1].transform.x)
        editor.setTransition(SceneTransitionType.FADE, 500)
        editor.rename("Renamed")
        assertEquals(500L, editor.state.composition.transition.durationMillis)
        editor.undo()
        assertEquals("Scene", editor.state.scene.name)
    }

    @Test fun `source and layer budgets reject additions without modifying the draft`() {
        val editor = editor()
        repeat(SceneComposition.MAX_SOURCES - 1) {
            assertTrue(editor.addSource(SceneSource("source-$it", SceneSourceType.IMAGE)))
        }
        val before = editor.state
        assertFalse(editor.addSource(SceneSource("excess", SceneSourceType.IMAGE)))
        assertEquals(before, editor.state)
        repeat(SceneComposition.MAX_LAYERS - editor.state.composition.layers.size) { assertTrue(editor.duplicateSelected()) }
        assertFalse(editor.duplicateSelected())
    }
}
