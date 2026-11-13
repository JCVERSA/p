package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LangPill
import dot.jcversa.novay.ui.components.LoadingView
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.theme.Amber500
import dot.jcversa.novay.ui.theme.DarkCardBorder
import dot.jcversa.novay.ui.theme.DarkSurfaceCard
import dot.jcversa.novay.ui.theme.MintGreen
import dot.jcversa.novay.ui.theme.TextMuted

/**
 * Écran Fiche/Saisons : saisons et films de l'anime, CLASSES PAR CATÉGORIE
 * — toutes les VF d'abord, toutes les VOSTFR ensuite (jamais mélangées ;
 * cf. orderByLanguage).
 *
 * Lisibilité (retour owner 13/11 : « s1, s2, s1, s2 » paraissait non trié) :
 *  - chaque carte porte SON badge de langue (VF ambre / VOSTFR vert) ;
 *  - chaque section a une bannière colorée pleine largeur ;
 *  - l'en-tête de section est ÉPINGLÉ (sticky) : la langue courante reste
 *    visible en haut pendant le défilement.
 */
@OptIn(ExperimentalFoundationApi::class)
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
                    // V2.1 : banniere affiche (image reelle du catalogue).
                    item(key = "banner") {
                        ShowBanner(anime = anime)
                    }
                    if (vf.isNotEmpty()) {
                        stickyHeader(key = "header-vf") {
                            SectionHeader(label = "VF", vf = true, count = vf.size)
                        }
                    }
                    itemsIndexed(vf, key = { _, s -> s.url }) { index, season ->
                        AnimateEntrance(index = index) {
                            SeasonCard(season = season, onClick = { onOpenEpisodes(season) })
                        }
                    }
                    if (vostfr.isNotEmpty()) {
                        stickyHeader(key = "header-vostfr") {
                            Column {
                                if (vf.isNotEmpty()) Spacer(Modifier.height(16.dp))
                                SectionHeader(label = "VOSTFR", vf = false, count = vostfr.size)
                            }
                        }
                    }
                    itemsIndexed(vostfr, key = { _, s -> s.url }) { index, season ->
                        AnimateEntrance(index = index) {
                            SeasonCard(season = season, onClick = { onOpenEpisodes(season) })
                        }
                    }
                    if (others.isNotEmpty()) {
                        stickyHeader(key = "header-others") {
                            Column {
                                if (vf.isNotEmpty() || vostfr.isNotEmpty()) Spacer(Modifier.height(16.dp))
                                SectionHeader(label = "Autres", vf = false, count = others.size)
                            }
                        }
                    }
                    itemsIndexed(others, key = { _, s -> s.url }) { index, season ->
                        AnimateEntrance(index = index) {
                            SeasonCard(season = season, onClick = { onOpenEpisodes(season) })
                        }
                    }
                    item(key = "footer") { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

/**
 * Bannière de catégorie : pleine largeur, teintée de la couleur de la
 * langue, avec libellé explicite — impossible à manquer.
 */
@Composable
private fun SectionHeader(label: String, vf: Boolean, count: Int) {
    val tint = if (vf) Amber500 else MintGreen
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LangPill(text = label, vf = vf)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        vf -> "Version française"
                        label == "VOSTFR" -> "Version originale sous-titrée"
                        else -> "Langue du lecteur"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (label == "Autres") {
                        "$count entrée(s)"
                    } else {
                        "$count saison(s) & film(s)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
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
            // La langue sur CHAQUE carte : la catégorie est lisible carte
            // par carte, pas seulement dans l'en-tête de section.
            when (season.language) {
                "VF" -> LangPill(text = "VF", vf = true)
                "VOSTFR" -> LangPill(text = "VOSTFR")
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}

/**
 * Bannière de la fiche anime : l'affiche réelle du catalogue en pleine
 * largeur, avec un dégradé sombre pour garder le sous-titre lisible.
 * Sans affiche : le placeholder film, même forme.
 */
@Composable
private fun ShowBanner(anime: AnimeSearchResult) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, DarkCardBorder, RoundedCornerShape(24.dp))
    ) {
        PosterImage(
            url = anime.posterUrl,
            contentDescription = anime.title,
            spinnerSize = 20.dp,
            modifier = Modifier.fillMaxSize()
        )
        // Dégradé bas : le sous-titre reste lisible sur n'importe quelle
        // image (claire ou sombre).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xCC000000)
                        )
                    )
                )
        )
        if (!anime.subtitle.isNullOrBlank()) {
            Text(
                text = anime.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            )
        }
    }
}
