package com.vivid.feature.streaming.opengles // Or whatever the correct package is

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

// Make sure your class signature matches this. It likely extends GLSurfaceView.
class OpenGlView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    // ... your existing renderer setup and other code ...

    /**
     * Exposes the onResume method from the parent GLSurfaceView.
     * This MUST be called from the Activity/Fragment's onResume().
     */
    override fun onResume() {
        super.onResume()
    }

    /**
     * Exposes the onPause method from the parent GLSurfaceView.
     * This MUST be called from the Activity/Fragment's onPause().
     */
    override fun onPause() {
        super.onPause()
    }

    // ... any other methods you have ...
}
