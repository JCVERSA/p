package dot.jcversa.novay.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.ScanChapter
import dot.jcversa.novay.data.ScanPanel
import dot.jcversa.novay.data.WatchHistory
import dot.jcversa.novay.data.WatchHistoryEntry
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.SwiftSlateSpinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lecteur de scans (V3.0) — pages en défilement vertical, chrome
 * réactif au toucher (barre du haut : retour, titre, chapitre ;
 * barre du bas : chapitre précédent/suivant + indicateur de page).
 *
 * Les chapitres peuvent être fournis par l'écran appelant (depuis la
 * liste) ou chargés à l'ouverture (reprise depuis l'historique).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReaderScreen(
    source: AnimeSource,
    anime: AnimeSearchResult,
    panel: AnimeSeason,
    chapters: List<ScanChapter>,
    initialChapterId: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val motion = LocalSlateMotion.current
    var loadedChapters by remember(panel.url) { mutableStateOf(chapters) }
    var error by remember(panel.url) { mutableStateOf<String?>(null) }
    var chapterIndex by remember(panel.url) { mutableIntStateOf(-1) }
    var chromeVisible by remember { mutableStateOf(true) }

    // Chargement paresseux des chapitres (reprise depuis l'historique).
    LaunchedEffect(panel.url) {
        if (loadedChapters.isEmpty()) {
            val list = runCatching {
                source.scanChapters(anime, ScanPanel(name = panel.name, url = panel.url, language = panel.language))
            }.getOrElse {
                emptyList()
            }
            if (list.isEmpty()) {
                error = "Chapitres indisponibles pour ce scan."
                return@LaunchedEffect
            }
            loadedChapters = list
        }
        chapterIndex = loadedChapters.indexOfFirst { it.id == initialChapterId }
            .takeIf { it >= 0 } ?: 0
    }

    val chapter = loadedChapters.getOrNull(chapterIndex)

    // Historique : chaque chapitre ouvert devient la dernière lecture
    // (reprise directe depuis l'accueil et l'historique, comme les animés).
    LaunchedEffect(chapter?.id) {
        val target = chapter ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            WatchHistory.record(
                context,
                WatchHistoryEntry(
                    animeTitle = anime.title,
                    animeUrl = anime.url,
                    posterUrl = anime.posterUrl,
                    seasonName = panel.name,
                    seasonUrl = panel.url,
                    episodeNumber = target.id,
                    episodeLabel = target.label,
                    lienIds = emptyList(),
                    jeton = "",
                    watchedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    val listState = rememberLazyListState()
    // Page courante approximée par la première page visible.
    val currentPage by remember(chapter?.id) {
        derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceAtLeast(1) }
    }
    // Le chrome réapparaît au changement de chapitre (retour au début).
    LaunchedEffect(chapter?.id) {
        if (chapter != null) {
            listState.scrollToItem(0)
            chromeVisible = true
        }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            error != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = error ?: "",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("Retour")
                }
            }
            chapter == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SwiftSlateSpinner(size = 34.dp)
            }
            else -> {
                val pageCount = chapter.pageCount.coerceAtLeast(1)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { chromeVisible = !chromeVisible },
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(pageCount, key = { it }) { page ->
                        AsyncImage(
                            model = chapter.imageUrlPrefix + page + ".jpg",
                            contentDescription = "${chapter.label} — page $page",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_page_$page"),
                        )
                    }
                }

                // ── Chrome du haut : retour + titre + chapitre ──────────────
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn(animationSpec = motion.enterSpec(160)),
                    exit = fadeOut(animationSpec = motion.enterSpec(160)),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = NovayIcons.Back,
                                    contentDescription = "Retour",
                                    tint = Color.White,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = anime.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = panel.name,
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = "$currentPage / $pageCount",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .testTag("scan_page_indicator"),
                            )
                        }
                    }
                }

                // ── Chrome du bas : chapitre précédent / suivant ────────────
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn(animationSpec = motion.enterSpec(160)),
                    exit = fadeOut(animationSpec = motion.enterSpec(160)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                enabled = chapterIndex > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.16f),
                                    contentColor = Color.White,
                                ),
                                onClick = { chapterIndex-- },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = loadedChapters.getOrNull(chapterIndex - 1)?.label
                                        ?.let { "← $it" }
                                        ?: "← Précédent",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Button(
                                enabled = chapterIndex < loadedChapters.lastIndex,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                onClick = { chapterIndex++ },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = loadedChapters.getOrNull(chapterIndex + 1)?.label
                                        ?.let { "$it →" }
                                        ?: "Suivant →",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
