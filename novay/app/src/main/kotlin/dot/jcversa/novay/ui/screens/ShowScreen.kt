package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PlayCircleOutline
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LangPill
import dot.jcversa.novay.ui.components.LoadingView
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.theme.DarkCardBorder
import dot.jcversa.novay.ui.theme.DarkSurfaceCard
import dot.jcversa.novay.ui.theme.TextMuted

/**
 * Écran Fiche/Saisons : saisons et films de l'anime, CLASSES PAR CATÉGORIE
 * — toutes les VF d'abord, toutes les VOSTFR ensuite (jamais mélangées ;
 * cf. orderByLanguage). La langue vient du sous-chemin structurel
 * (« saison1/vf », « film/vostfr »…) + sondage de l'URL miroir.
 */
@Composable
fun ShowScreen(
    source: AnimeSource,
    anime: AnimeSearchResult,
    onBack: () -> Unit,
    onOpenEpisodes: (AnimeSeason) -> Unit
) {
    var seasons by remember { mutableStateOf<List<AnimeSeason>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(anime.url, retryKey) {
        error = null
        seasons = null
        try {
            seasons = source.seasons(anime)
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
                text = anime.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            error != null -> ErrorView(message = error!!, onRetry = { retryKey++ })
            seasons == null -> LoadingView(message = "Chargement des saisons…")
            seasons!!.isEmpty() -> Text(
                text = "Aucune saison listée sur la page de cet anime.",
                style = MaterialTheme.typography.bodyMedium
            )
            else -> {
                val vf = seasons!!.filter { it.language == "VF" }
                val vostfr = seasons!!.filter { it.language == "VOSTFR" }
                val others = seasons!!.filter { it.language == null }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (vf.isNotEmpty()) {
                        item { SectionHeader(label = "VF", vf = true, count = vf.size) }
                    }
                    itemsIndexed(vf) { index, season ->
                        AnimateEntrance(index = index) {
                            SeasonCard(season = season, onClick = { onOpenEpisodes(season) })
                        }
                    }
                    if (vostfr.isNotEmpty()) {
                        item {
                            if (vf.isNotEmpty()) Spacer(Modifier.height(14.dp))
                            SectionHeader(label = "VOSTFR", vf = false, count = vostfr.size)
                        }
                    }
                    itemsIndexed(vostfr) { index, season ->
                        AnimateEntrance(index = index) {
                            SeasonCard(season = season, onClick = { onOpenEpisodes(season) })
                        }
                    }
                    if (others.isNotEmpty()) {
                        item {
                            if (vf.isNotEmpty() || vostfr.isNotEmpty()) Spacer(Modifier.height(14.dp))
                            SectionHeader(label = "Autres", vf = false, count = others.size)
                        }
                    }
                    itemsIndexed(others) { index, season ->
                        AnimateEntrance(index = index) {
                            SeasonCard(season = season, onClick = { onOpenEpisodes(season) })
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

/** En-tête de catégorie : badge de langue + nombre de saisons. */
@Composable
private fun SectionHeader(label: String, vf: Boolean, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        LangPill(text = label, vf = vf)
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (label == "Autres") {
                "$count entrée(s) — langue du lecteur"
            } else {
                "$count saison(s) & film(s)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun SeasonCard(season: AnimeSeason, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = season.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}
