package dot.jcversa.novay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Badges & pastilles style Aniyomi — Midnight Noir & Crimson :
 *  - BadgeGroup fusionné (coins externes arrondis, séparation nette 1.5dp) ;
 *  - secondary = nouveaux épisodes (or projection) ;
 *  - tertiary  = téléchargé hors-ligne (émeraude).
 */

@Composable
fun BadgeGroup(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.clip(shape),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

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
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = textColor,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = MaterialTheme.typography.labelSmall.fontSize,
) {
    Surface(
        modifier = modifier.padding(start = 6.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = color,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize),
            )
        }
    }
}

@Composable
fun Modifier.selectedBackground(
    selected: Boolean,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
): Modifier = if (selected) this.then(Modifier.background(color)) else this
