package dot.jcversa.novay.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dot.jcversa.novay.ui.theme.NovayTheme

/**
 * Barre supérieure Midnight Noir : titre expressif (Outfit), flèche retour
 * avec cible tactile >=48dp, verre dépoli Haze et intégration edge-to-edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovayAppBar(
    titleContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    navigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    hazeState: HazeState? = LocalNovayHazeState.current,
    containerColor: Color = Color.Transparent,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    val glassBg = NovayTheme.overlays.glassSurface
    val useGlass = hazeState != null && containerColor == Color.Transparent
    val barModifier = if (useGlass) {
        modifier.novayCinemaGlass(hazeState = hazeState, tint = glassBg)
    } else {
        modifier
    }
    val resolvedContainer = when {
        useGlass -> Color.Transparent
        containerColor != Color.Transparent -> containerColor
        else -> MaterialTheme.colorScheme.background
    }
    Column(modifier = barModifier) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    titleContent()
                }
            },
            navigationIcon = {
                if (navigateUp != null) {
                    IconButton(
                        onClick = navigateUp,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("appbar_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour",
                        )
                    }
                }
            },
            actions = actions,
            windowInsets = windowInsets,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = resolvedContainer,
                scrolledContainerColor = if (useGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
            scrollBehavior = scrollBehavior,
        )
        if (useGlass) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
fun AppBarTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun AppBarActions(
    content: @Composable RowScope.() -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

@Composable
fun AppBarAction(
    contentDescription: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
