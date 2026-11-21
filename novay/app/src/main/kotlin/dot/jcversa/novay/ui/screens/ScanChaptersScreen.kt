package dot.jcversa.novay.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.ScanChapter
import dot.jcversa.novay.data.ScanPanel
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.NovayIcons
import dot.jcversa.novay.ui.components.SwiftSlateSpinner
import dot.jcversa.novay.ui.components.LocalSlateMotion
import dot.jcversa.novay.ui.LocalBottomChromePadding
import androidx.compose.ui.res.stringResource
import dot.jcversa.novay.R

/**
 * Écran Chapitres d'un panneau de scans (V3.0, téléphone) — même structure
 * qu'EpisodesScreen : NovayAppBar + liste déroulante de lignes cliquables.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanChaptersScreen(
    source: AnimeSource,
    anime: AnimeSearchResult,
    panel: AnimeSeason,
    onBack: () -> Unit,
    onOpenChapter: (List<ScanChapter>, Int) -> Unit,
) {
    val bottomChrome = LocalBottomChromePadding.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomChrome),
    ) {
        NovayAppBar(
            titleContent = { AppBarTitle(title = anime.title) },
            navigateUp = onBack,
            scrollBehavior = scrollBehavior,
        )
        ScanChaptersPane(
            source = source,
            anime = anime,
            panel = panel,
            onOpenChapter = onOpenChapter,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

/**
 * Liste des chapitres d'un panneau de scans — partagée entre l'écran
 * téléphone et le volet tablette de ShowScreen (comme EpisodesPane).
 */
@Composable
fun ScanChaptersPane(
    source: AnimeSource,
    anime: AnimeSearchResult,
    panel: AnimeSeason,
    onOpenChapter: (List<ScanChapter>, Int) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = false,
) {
    var chapters by remember(panel.url) { mutableStateOf<List<ScanChapter>?>(null) }
    var error by remember(panel.url) { mutableStateOf<String?>(null) }
    var retryKey by remember(panel.url) { mutableIntStateOf(0) }

    LaunchedEffect(panel.url, retryKey) {
        error = null
        chapters = null
        val loaded = runCatching {
            source.scanChapters(anime, ScanPanel(name = panel.name, url = panel.url, language = panel.language))
        }.getOrElse {
            error = it.message ?: "Chapitres indisponibles"
            emptyList()
        }
        chapters = loaded
    }

    val viewKey = when {
        error != null -> "error"
        chapters == null -> "loading"
        chapters!!.isEmpty() -> "empty"
        else -> "loaded"
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (viewKey) {
            "error" -> ErrorView(
                message = error ?: "",
                onRetry = { retryKey++ },
                title = stringResource(R.string.error_connection_title),
                icon = NovayIcons.OfflineCloud,
            )
            "loading" -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SwiftSlateSpinner(size = 34.dp)
            }
            "empty" -> EmptyScreen(
                icon = NovayIcons.SeasonReel,
                headingText = "Aucun chapitre disponible",
                subtitleText = "Aucun chapitre n'est listé pour ce scan pour le moment.",
            )
            else -> {
                val list = chapters!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp),
                    contentPadding = PaddingValues(
                        top = if (showHeader) 4.dp else 12.dp,
                        bottom = 32.dp,
                    ),
                ) {
                    if (showHeader) {
                        item(key = "scan_panel_header") {
                            Text(
                                text = panel.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                    itemsIndexed(list, key = { _, item -> "${item.id}_${item.number}" }) { index, chapter ->
                        ScanChapterRow(
                            chapter = chapter,
                            onClick = { onOpenChapter(list, index) },
                            modifier = Modifier.testTag("scan_chapter_row_$index"),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ligne d'un chapitre — même graphie qu'EpisodeRow : carte surface, badge
 * numéroté, libellé, compte de pages, chevron.
 */
@Composable
private fun ScanChapterRow(
    chapter: ScanChapter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val motion = LocalSlateMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            hovered -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motion.colorSpec(180),
        label = "scan_chapter_row_bg",
    )

    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClickLabel = "Lire le ${chapter.label}",
                onClick = onClick,
            )
            .minimumInteractiveComponentSize(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(width = 46.dp, height = 34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = chapter.number.take(4),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = chapter.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chapter.pageCount > 0) {
                    Text(
                        text = "${chapter.pageCount} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Icon(
                imageVector = NovayIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp),
            )
        }
    }
}
