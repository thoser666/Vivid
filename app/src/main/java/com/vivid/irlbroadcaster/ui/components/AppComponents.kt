package com.vivid.irlbroadcaster.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vivid.R

/**
 * A reusable Composable to display the app's thumbnail or logo.
 */
@Composable
fun AppThumbnail(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.vivid_logo),
        contentDescription = stringResource(R.string.app_logo_content_desc),
        modifier = modifier.size(128.dp),
    )
}

@Preview(showBackground = true)
@Composable
fun AppThumbnailPreview() {
    AppThumbnail()
}
