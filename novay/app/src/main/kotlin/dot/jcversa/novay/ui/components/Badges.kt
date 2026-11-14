package dot.jcversa.novay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Badges et pastilles — portés de Mihon/Tachiyomi
 * (github.com/mihonapp/mihon, Apache-2.0), adaptés à NoVaY.
 *
 * Sémantique Mihon : secondary = badge « nouveaux épisodes »,
 * tertiary = badge « téléchargé ».
 */

/** Groupe de badges (coins partagés, fond uni). */
@Composable
fun BadgeGroup(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier = modifier.clip(shape)) {
        content()
    }
}

/** Badge texte : rectangle plein, padding 3×1 dp (style Mihon). */
@Composable
fun NovayBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    textColor: Color = MaterialTheme.colorScheme.onSecondary,
    shape: Shape = RectangleShape,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(color)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        color = textColor,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Pastille arrondie (compteur d'app bar, libellés discrets). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = MaterialTheme.typography.bodySmall.fontSize,
) {
    Surface(
        modifier = modifier.padding(start = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = color,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.padding(6.dp, 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                maxLines = 1,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Fond de sélection multi (Mihon : tint primaire léger, plein bloc). */
@Composable
fun Modifier.selectedBackground(
    selected: Boolean,
    color: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
): Modifier = if (selected) this.then(Modifier.background(color)) else this
