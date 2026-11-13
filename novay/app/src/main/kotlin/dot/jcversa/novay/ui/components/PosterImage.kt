package dot.jcversa.novay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dot.jcversa.novay.ui.theme.DarkSurfaceVariant
import dot.jcversa.novay.ui.theme.TextMuted

/**
 * Affiche d'un anime : l'image réelle du catalogue si elle existe, sinon
 * le placeholder film (fond sombre + icône). Pendant le chargement : un
 * petit spinner SwiftSlate.
 */
@Composable
fun PosterImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    spinnerSize: Dp = 14.dp
) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier.background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = contentDescription,
                tint = TextMuted
            )
        }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    SwiftSlateSpinner(size = spinnerSize)
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Movie,
                        contentDescription = contentDescription,
                        tint = TextMuted
                    )
                }
            }
        )
    }
}
