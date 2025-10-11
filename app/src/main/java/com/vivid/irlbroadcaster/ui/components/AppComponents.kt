package com.vivid.irlbroadcaster.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vivid.irlbroadcaster.R

/**
 * A reusable Composable to display the app's thumbnail or logo.
 */
@Composable
fun AppThumbnail(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.vivid_logo),
        contentDescription = "Vivid Streaming App Logo",
        modifier = modifier.size(128.dp), // Sie können die Grösse von aussen überschreiben
    )
}

@Preview(showBackground = true)
@Composable
fun AppThumbnailPreview() {
    AppThumbnail()
}
