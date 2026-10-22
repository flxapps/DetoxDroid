package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.flx_apps.digitaldetox.util.appIconBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The launcher icon of [packageName] at [size]. It is loaded off the main thread, so a list of
 * apps scrolls without waiting for the package manager; [placeholder] stands in until then, and
 * for an app that is gone. The space is taken either way, so rows stay aligned.
 */
@Composable
fun AppIcon(
    packageName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholder: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packageName, sizePx) {
        value = withContext(Dispatchers.IO) {
            context.appIconBitmap(packageName, sizePx)?.asImageBitmap()
        }
    }
    Box(modifier = modifier.size(size)) {
        icon?.let {
            Image(bitmap = it, contentDescription = contentDescription, modifier = Modifier.fillMaxSize())
        } ?: placeholder()
    }
}
