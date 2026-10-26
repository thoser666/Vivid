package com.vivid.irlbroadcaster

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.vivid.feature.streaming.compositor.capture.SceneUsbPermissionActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** Tests the app's merged dependency manifest, rather than the library's merge markers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SceneUsbManifestRobolectricTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `capture SDK adds no broad storage permissions`() {
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertFalse(permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertFalse(permissions.contains("android.permission.READ_EXTERNAL_STORAGE"))
    }

    @Test fun `USB host remains optional for users without USB cameras`() {
        val features = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_CONFIGURATIONS).reqFeatures.orEmpty()
        val usb = features.single { it.name == PackageManager.FEATURE_USB_HOST }
        assertEquals(0, usb.flags and FeatureInfo.FLAG_REQUIRED)
    }

    @Test fun `USB permission bridge is registered only for internal activity results`() {
        val info = context.packageManager.getActivityInfo(ComponentName(context,
            SceneUsbPermissionActivity::class.java), 0)
        assertFalse(info.exported)
    }
    @Test fun `capture dependency preserves the app backup and cleartext restrictions`() {
        val flags = context.applicationInfo.flags
        assertEquals(0, flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertEquals(0, flags and android.content.pm.ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
    }

}
