package com.vivid.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.vivid.feature.chat.model.AlertDetail
import com.vivid.feature.chat.model.ChatAlert
import com.vivid.feature.chat.model.ChatAlertType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Render-Verifikation der Event-Alerts ([AlertRow] in ui/ChatOverlay.kt):
 * alle 6 Twitch-Alert-Typen — inklusive Hype-Train-Ende und anonymer Gifts —
 * müssen ihren lokalisierten Text (hier EN, `qualifiers = "en"`) korrekt
 * zusammensetzen und anzeigen. Die Zuordnung EventSub-Payload → ChatAlert
 * friert TwitchChatEventSubReaderTest ein; dieser Test deckt die UI-Schicht
 * (String-Templates, Tier-Label, Plurals, Kombinationen wie Gift+Kumulativ
 * und Resub+Serie) ab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class ChatAlertRowRenderRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun alert(
        type: ChatAlertType,
        detail: AlertDetail = AlertDetail(),
        name: String = "user1",
    ) = ChatAlert(id = "a-${type.name}-${detail.hashCode()}", type = type, displayName = name, timestamp = 0L, detail = detail)

    private fun setContent(alerts: List<ChatAlert>) {
        composeRule.setContent {
            Column {
                alerts.forEach { AlertRow(it) }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `follow renders localized banner`() {
        setContent(listOf(alert(ChatAlertType.FOLLOW, name = "alice")))
        composeRule.onNodeWithText("🔔 alice is now following!").assertExists()
    }

    @Test
    fun `subscribe renders with tier and optional gifter`() {
        setContent(
            listOf(
                alert(ChatAlertType.SUBSCRIBE, AlertDetail(tier = "2000"), name = "bob"),
                alert(ChatAlertType.SUBSCRIBE, AlertDetail(tier = "1000", gifterName = "gifty"), name = "carl"),
                alert(ChatAlertType.SUBSCRIBE, name = "dave"),
            ),
        )
        composeRule.onNodeWithText("🎉 bob subscribed (Tier 2)").assertExists()
        composeRule.onNodeWithText("🎉 carl subscribed (Tier 1) · gifted by gifty").assertExists()
        composeRule.onNodeWithText("🎉 dave subscribed").assertExists()
    }

    @Test
    fun `gift sub renders with tier cumulative total and anonymous variant`() {
        setContent(
            listOf(
                alert(ChatAlertType.GIFT_SUB, AlertDetail(count = 5, tier = "1000", cumulativeTotal = 12), name = "gifter1"),
                alert(ChatAlertType.GIFT_SUB, AlertDetail(count = 1, tier = "2000", isAnonymous = true)),
            ),
        )
        composeRule.onNodeWithText("🎁 gifter1 gifted 5 subs (Tier 1) · 12 total").assertExists()
        composeRule.onNodeWithText("🎁 Anonymous gifted 1 sub (Tier 2)").assertExists()
    }

    @Test
    fun `resub renders months tier and streak`() {
        setContent(
            listOf(
                alert(ChatAlertType.RESUB, AlertDetail(tier = "1000", months = 24, streakMonths = 6), name = "erin"),
                alert(ChatAlertType.RESUB, AlertDetail(months = 3), name = "frank"),
            ),
        )
        composeRule.onNodeWithText("🔁 erin resubscribed (Tier 1) — month 24 · 6-month streak").assertExists()
        composeRule.onNodeWithText("🔁 frank resubscribed — month 3").assertExists()
    }

    @Test
    fun `raid renders viewer count`() {
        setContent(listOf(alert(ChatAlertType.RAID, AlertDetail(viewerCount = 12), name = "raider1")))
        composeRule.onNodeWithText("⚔️ Raid from raider1 (12 viewers)").assertExists()
    }

    @Test
    fun `hype train renders begin progress and end`() {
        setContent(
            listOf(
                alert(ChatAlertType.HYPE_TRAIN, AlertDetail(hypeTrainLevel = 1, hypeTrainProgress = 40, hypeTrainGoal = 100)),
                alert(ChatAlertType.HYPE_TRAIN, AlertDetail(hypeTrainEnded = true, hypeTrainLevel = 3)),
                alert(ChatAlertType.HYPE_TRAIN, AlertDetail(hypeTrainEnded = true)),
            ),
        )
        composeRule.onNodeWithText("🚂 Hype Train level 1 — 40/100").assertExists()
        composeRule.onNodeWithText("🚂 Hype Train ended — level 3").assertExists()
        composeRule.onNodeWithText("🚂 Hype Train active").assertExists()
    }
}
