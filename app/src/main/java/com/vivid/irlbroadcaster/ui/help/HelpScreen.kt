package com.vivid.irlbroadcaster.ui.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vivid.R

/** Link-Ziele für den Hilfe-Screen. */
private object HelpLinks {
    const val USER_GUIDE_DE = "https://github.com/thoser666/Vivid/blob/develop/docs/user-guide.md"
    const val USER_GUIDE_EN = "https://github.com/thoser666/Vivid/blob/develop/docs/user-guide.en.md"
    const val BOT_DOC = "https://github.com/thoser666/Vivid/blob/develop/docs/ai-chat-bot.md"
    const val FAQ = "https://github.com/thoser666/Vivid#-faq--häufige-probleme"
    const val ISSUES = "https://github.com/thoser666/Vivid/issues"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(navController: NavHostController) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.help_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Quick-Tipps
            QuickTipsCard()

            // Bot-Befehle Quick-Reference
            BotCommandsCard()

            // Externe Doku-Links
            DocsLinksCard(onOpenUri = uriHandler::openUri)

            // Support
            SupportCard(onOpenUri = uriHandler::openUri)
        }
    }
}

@Composable
private fun QuickTipsCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.help_quick_tips_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.help_quick_tips_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BotCommandsCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.help_bot_commands_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.help_bot_viewer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            BotCommandRow("!help / !commands", stringResource(R.string.help_cmd_help))
            BotCommandRow("!uptime", stringResource(R.string.help_cmd_uptime))
            BotCommandRow("!song / !nowplaying", stringResource(R.string.help_cmd_song))
            BotCommandRow("!next / !skip", stringResource(R.string.help_cmd_next))
            BotCommandRow("!pause / !play", stringResource(R.string.help_cmd_pause))
            BotCommandRow("!bot", stringResource(R.string.help_cmd_bot))
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.help_bot_owner),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold,
            )
            BotCommandRow("!tts", stringResource(R.string.help_cmd_tts))
            BotCommandRow("!start / !go-live", stringResource(R.string.help_cmd_start))
            BotCommandRow("!stop / !end", stringResource(R.string.help_cmd_stop))
            BotCommandRow("!diag / !status", stringResource(R.string.help_cmd_diag))
            BotCommandRow("!ask <frage>", stringResource(R.string.help_cmd_ask))
            BotCommandRow("!testalert <type>", stringResource(R.string.help_cmd_testalert))
            BotCommandRow("!torch", stringResource(R.string.help_cmd_torch))
            BotCommandRow("!ban <user>", stringResource(R.string.help_cmd_ban))
            BotCommandRow("!timeout <user> <min?>", stringResource(R.string.help_cmd_timeout))
            BotCommandRow("!delete <count?>", stringResource(R.string.help_cmd_delete))
        }
    }
}

@Composable
private fun BotCommandRow(command: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = command,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.width(160.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DocsLinksCard(onOpenUri: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.help_docs_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HelpLinkRow(stringResource(R.string.help_link_guide_de), HelpLinks.USER_GUIDE_DE, onOpenUri)
            HorizontalDivider()
            HelpLinkRow(stringResource(R.string.help_link_guide_en), HelpLinks.USER_GUIDE_EN, onOpenUri)
            HorizontalDivider()
            HelpLinkRow(stringResource(R.string.help_link_bot_doc), HelpLinks.BOT_DOC, onOpenUri)
            HorizontalDivider()
            HelpLinkRow(stringResource(R.string.help_link_faq), HelpLinks.FAQ, onOpenUri)
        }
    }
}

@Composable
private fun SupportCard(onOpenUri: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.help_support_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.help_support_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.help_link_issues),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp).clickable { onOpenUri(HelpLinks.ISSUES) },
                )
            }
        }
    }
}

@Composable
private fun HelpLinkRow(label: String, url: String, onOpenUri: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUri(url) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.help_link_open, label),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
