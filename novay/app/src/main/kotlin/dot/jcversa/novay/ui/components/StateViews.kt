package dot.jcversa.novay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dot.jcversa.novay.R
import dot.jcversa.novay.ui.theme.NovayTheme

fun isDeviceOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** État de chargement centré (spinner signature crimson + message). */
@Composable
fun LoadingView(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SwiftSlateSpinner(size = 36.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** État d'erreur centré avec titre contextuel et action « Réessayer » accessible (>=48dp). */
@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.error_connection_title),
    icon: ImageVector = NovayIcons.OfflineCloud,
) {
    val overlays = NovayTheme.overlays
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = overlays.ctaContainer,
                    contentColor = overlays.onCtaContainer,
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("error_retry_button"),
            ) {
                Icon(
                    imageVector = NovayIcons.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_retry),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * État hors-ligne dédié : animation Wi-Fi Lottie, message explicite, bouton
 * « Réessayer » et raccourci optionnel vers les téléchargements hors-ligne.
 */
@Composable
fun OfflineStateView(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.state_offline_title),
    subtitle: String = stringResource(R.string.state_offline_subtitle),
    onOpenDownloads: (() -> Unit)? = null,
) {
    val overlays = NovayTheme.overlays
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("offline_state_view"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 390.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, overlays.glassBorder),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    NovayLottieIcon(
                        res = R.raw.lottie_wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 34.dp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = overlays.ctaContainer,
                        contentColor = overlays.onCtaContainer,
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("offline_retry_button"),
                ) {
                    Icon(
                        imageVector = NovayIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_retry),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (onOpenDownloads != null) {
                    OutlinedButton(
                        onClick = onOpenDownloads,
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("offline_downloads_button"),
                    ) {
                        Icon(
                            imageVector = NovayIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.state_offline_downloads_cta),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

/**
 * État « Aucun résultat » avec suggestions contextuelles cliquables.
 */
@Composable
fun NoResultsStateView(
    title: String,
    subtitle: String,
    suggestions: List<String>,
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
) {
    val overlays = NovayTheme.overlays
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("no_results_state_view"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(68.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    NovayLottieIcon(
                        res = R.raw.lottie_explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 32.dp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.state_no_results_suggestions_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    suggestions.forEach { suggestion ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(0.5.dp, overlays.glassBorder),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onSelectSuggestion(suggestion) }
                                .minimumInteractiveComponentSize()
                                .testTag("no_results_suggestion_${suggestion.lowercase()}"),
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
            if (resetLabel != null && onReset != null) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = overlays.ctaContainer,
                        contentColor = overlays.onCtaContainer,
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("no_results_reset_button"),
                ) {
                    Text(
                        text = resetLabel,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * Bandeau de chargement partiel non bloquant : conserve le contenu déjà affiché
 * tout en indiquant discrètement une actualisation ou un chargement en cours.
 */
@Composable
fun PartialLoadingBanner(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
) {
    val motion = LocalSlateMotion.current
    val overlays = NovayTheme.overlays

    AnimatedVisibility(
        visible = visible,
        enter = if (motion.reduceMotion) {
            EnterTransition.None
        } else {
            fadeIn(motion.enterSpec(180)) +
                slideInVertically(motion.offsetEnterSpec(180)) { -it / 2 }
        },
        exit = if (motion.reduceMotion) {
            ExitTransition.None
        } else {
            fadeOut(motion.exitSpec(120))
        },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .novayCinemaGlass(
                    shape = RoundedCornerShape(14.dp),
                    tint = overlays.glassSurface,
                    borderColor = overlays.glassBorder,
                    showBorder = true,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("partial_loading_banner"),
        ) {
            SwiftSlateSpinner(size = 16.dp)
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
