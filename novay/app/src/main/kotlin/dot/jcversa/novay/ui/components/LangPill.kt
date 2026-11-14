package dot.jcversa.novay.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.ui.theme.NovayTheme

/**
 * Badge de langue éditorial :
 *  - VF en Crimson cinéma (rôle primaire accessible >= 5.0:1) ;
 *  - VOSTFR en Émeraude (rôle tertiaire).
 */
@Composable
fun LangPill(
    text: String,
    vf: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgTint = if (vf) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val textColor = if (vf) {
        NovayTheme.overlays.crimsonOnContainer
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bgTint.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, bgTint.copy(alpha = 0.40f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
        )
    }
}
