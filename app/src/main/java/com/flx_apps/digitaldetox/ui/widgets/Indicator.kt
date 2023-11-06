import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R

/**
 * A small circle that can be used as an indicator for something. It is basically just a wrapper for
 * [Box] and reduces some boilerplate.
 * @param modifier The modifier to be applied to the indicator.
 * @param indicatorColor The color of the indicator.
 */
@Composable
fun StatusIndicator(
    modifier: Modifier = Modifier, indicatorColor: Color = colorResource(id = R.color.green)
) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(color = indicatorColor)
    )
}