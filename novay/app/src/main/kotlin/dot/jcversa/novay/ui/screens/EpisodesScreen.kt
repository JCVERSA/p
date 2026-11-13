package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeEpisode
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LoadingView
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.theme.Amber500
import dot.jcversa.novay.ui.theme.DarkCardBorder
import dot.jcversa.novay.ui.theme.DarkSurfaceCard
import dot.jcversa.novay.ui.theme.DarkSurfaceVariant
import dot.jcversa.novay.ui.theme.TextMuted

/**
 * Écran Épisodes : liste réelle de la saison (tableaux positionnels epsN
 * de la page — chaque épisode porte ses identifiants de lecteurs).
 */
@Composable
fun EpisodesScreen(
    source: AnimeSource,
    season: AnimeSeason,
    onBack: () -> Unit,
    onOpenPlayer: (AnimeEpisode) -> Unit
) {
    var episodes by remember { mutableStateOf<List<AnimeEpisode>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(season.url, retryKey) {
        error = null
        episodes = null
        try {
            episodes = source.episodes(season)
        } catch (e: Exception) {
            error = e.message ?: "Erreur inconnue"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.bounceClick(onClick = onBack)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = season.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            error != null -> ErrorView(message = error!!, onRetry = { retryKey++ })
            episodes == null -> LoadingView(message = "Chargement des épisodes…")
            episodes!!.isEmpty() -> Text(
                text = "Aucun épisode listé pour cette saison.",
                style = MaterialTheme.typography.bodyMedium
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(episodes!!) { index, episode ->
                    AnimateEntrance(index = index) {
                        EpisodeRow(episode = episode, onClick = { onOpenPlayer(episode) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: AnimeEpisode, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = episode.number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = episode.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (episode.lienIds.size > 1) {
                Text(
                    text = "${episode.lienIds.size} lecteurs",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.width(12.dp))
            }
            // V2 : téléchargement sur stockage (grisé tant qu'indisponible).
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = "Télécharger (V2)",
                tint = TextMuted.copy(alpha = 0.4f)
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Lire",
                tint = Amber500
            )
        }
    }
}
