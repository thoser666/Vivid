package com.vivid.irlbroadcaster

import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule

/**
 * Runtime permissions for the instrumented UI tests (#249).
 *
 * Until v0.5.49-beta the emulator gate pre-installed both debug flavors with
 * `adb install` and granted the permissions from the shell before the connected
 * tasks started. That created two independent install paths for the same
 * applicationId, and adb refuses to replace a package whose signer differs:
 * every leg of the tag run died with
 * `INSTALL_FAILED_UPDATE_INCOMPATIBLE ... signatures do not match`.
 *
 * The grants now happen inside the test process, before any activity is
 * launched, so AGP (the connected task) is the only installer. GrantPermissionRule
 * grants through UiAutomation and therefore produces the same app state the
 * shell grants produced. MainActivity's one-time onboarding request and explicit
 * go-live retries remain separate from screen navigation (#249).
 */
internal object EmulatorTestPermissions {

    /**
     * Permissions the streaming screen and the text overlay need at runtime.
     * POST_NOTIFICATIONS exists from API 33 on; granting it on older devices
     * fails, and the streaming notification does not need it there.
     */
    private fun grantSet(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.toTypedArray()
    }

    fun rule(): GrantPermissionRule = GrantPermissionRule.grant(*grantSet())
}
