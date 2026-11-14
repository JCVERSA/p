package dot.jcversa.novay.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Barre d'app — portée de Mihon/Tachiyomi (Apache-2.0) : titre libre,
 * flèche retour, actions à droite, comportement de scroll M3.
 */
object AppBar {
    val defaultColors: TopAppBarColors
        @Composable
        @OptIn(ExperimentalMaterial3Api::class)
        get() = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        )
}

/** Barre supérieure standard (pinned). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovayAppBar(
    titleContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    navigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        modifier = modifier,
        title = { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { titleContent() } },
        navigationIcon = {
            if (navigateUp != null) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Retour",
                    )
                }
            }
        },
        actions = actions,
        colors = AppBar.defaultColors,
        scrollBehavior = scrollBehavior,
    )
}

/** Titre simple (poids flexibles + ellipse — pattern Mihon). */
@Composable
fun AppBarTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Rangée d'actions d'app bar (icônes à 8 dp d'écart, comme Mihon). */
@Composable
fun AppBarActions(
    content: @Composable RowScope.() -> Unit,
) {
    Row {
        content()
    }
}

/** Bouton d'action d'app bar (24 dp visuels — tokens Mihon). */
@Composable
fun AppBarAction(
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
