package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked
import com.flx_apps.digitaldetox.ui.theme.BrandTone

/**
 * One of the choices in [OptionCards].
 * @param note what the choice still needs before it works, if anything
 */
class CardOption<T>(
    val value: T,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val note: String? = null,
)

/**
 * A choice of one out of a few, each on a card of its own: an icon, a line on what it does, and a
 * radio button, the chosen one in the [tone] of the step it belongs to. Like [OptionsRow] the
 * cards lock with the settings, see [LocalSettingsLocked].
 */
@Composable
fun <T> OptionCards(
    options: List<CardOption<T>>,
    selected: T,
    tone: BrandTone,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !LocalSettingsLocked.current
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) tone.container else colors.surfaceContainer,
                border = if (isSelected) BorderStroke(1.5.dp, tone.content) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.5f)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.value) }
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) tone.content.copy(alpha = 0.16f) else colors.surfaceContainerHighest
                            )
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = if (isSelected) tone.content else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Medium else null
                        )
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        option.note?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) tone.content else colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        enabled = enabled,
                        colors = RadioButtonDefaults.colors(selectedColor = tone.content)
                    )
                }
            }
        }
    }
}
