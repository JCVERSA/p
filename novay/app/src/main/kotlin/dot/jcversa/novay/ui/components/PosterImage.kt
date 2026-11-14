package dot.jcversa.novay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest

/**
 * Affiche d'un anime : utilise [AsyncImage] (sans sous-composition coûteuse
 * en LazyGrid/LazyColumn) avec downsampling Coil automatique, fondu GPU et
 * squelette Shimmer sans allocation pendant le chargement.
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
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = NovayIcons.ClapperFilm,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val context = LocalContext.current
    val request = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(160)
            .build()
    }
    var state by remember(url) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Loading(null))
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            onState = { state = it },
            modifier = Modifier.fillMaxSize(),
        )
        when (state) {
            is AsyncImagePainter.State.Loading -> {
                ShimmerBox(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.extraSmall,
                )
            }
            is AsyncImagePainter.State.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = NovayIcons.ClapperFilm,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> Unit
        }
    }
}
