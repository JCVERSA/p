package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSeason
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.data.CatalogCard
import dot.jcversa.novay.data.CatalogHero
import dot.jcversa.novay.data.sama.SamaParsers
import dot.jcversa.novay.ui.components.AnimeComfortableGridItem
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LoadingView
import dot.jcversa.novay.ui.components.NovayAppBar
import dot.jcversa.novay.ui.components.PosterImage
import dot.jcversa.novay.ui.theme.header
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Random

/** États de l'écran de recherche. */
private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Error(val message: String) : SearchState
    data class Results(val results: List<AnimeSearchResult>) : SearchState
}

/** Nombre de cartes par section « Derniers épisodes ». */
private const val RECENT_LIMIT = 24

/** Nombre de cartes tirées par « Aléatoire ». */
private const val RANDOM_COUNT = 12

/**
 * Onglet Catalogue (V3) : la page d'accueil d'anime-sama dans l'app —
 * « À la une » (carrousel héros du site, défilement auto), « Derniers
 * épisodes » (cartes avec heure d'ajout, tap = SA saison) et
 * « Aléatoire » (tirage au sort reshufflable). La recherche reste en
 * haut et remplace le contenu quand elle a des résultats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    source: AnimeSource,
    onOpenShow: (AnimeSearchResult) -> Unit,
    onOpenEpisodes: (animeTitle: String, posterUrl: String?, season: AnimeSeason) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    val scope = rememberCoroutineScope()

    // Page d'accueil du catalogue.
    var home by remember { mutableStateOf<dot.jcversa.novay.data.CatalogHome?>(null) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var homeRetryKey by remember { mutableIntStateOf(0) }
    var randomSeed by remember { mutableIntStateOf(0) }

    LaunchedEffect(homeRetryKey) {
        home = null
        homeError = null
        try {
            home = source.home()
        } catch (e: Exception) {
            homeError = e.message ?: "Catalogue injoignable"
        }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.length < 2) return
        scope.launch {
            state = SearchState.Loading
            state = try {
                SearchState.Results(source.search(q))
            } catch (e: Exception) {
                SearchState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    /** Ouvre la saison pointée par une carte (ou un héros). */
    fun openCard(title: String, posterUrl: String?, seasonUrl: String, language: String?) {
        onOpenEpisodes(
            title,
            posterUrl,
            AnimeSeason(
                name = SamaParsers.seasonLabelFromUrl(seasonUrl) ?: title,
                url = seasonUrl,
                language = language
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NovayAppBar(
            titleContent = { AppBarTitle(title = "Catalogue") },
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text(text = "Rechercher un anime…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        when (val s = state) {
            is SearchState.Results -> {
                if (s.results.isEmpty()) {
                    EmptyScreen(
                        icon = Icons.Rounded.Search,
                        headingText = "Aucun résultat",
                        subtitleText = "Rien pour « ${query.trim()} » — vérifie l'orthographe, ou sépare bien les mots du titre.",
                    )
                } else {
                    ResultsGrid(results = s.results, onOpenShow = onOpenShow)
                }
            }
            SearchState.Loading -> LoadingView(message = "Recherche sur ${source.name}…")
            is SearchState.Error -> ErrorView(message = s.message, onRetry = { runSearch() })
            SearchState.Idle -> when {
                home == null && homeError == null ->
                    LoadingView(message = "Chargement du catalogue…")
                homeError != null -> ErrorView(
                    message = homeError!!,
                    onRetry = { homeRetryKey++ },
                )
                home == null -> Box(Modifier.fillMaxSize())
                else -> HomeContent(
                    home = home!!,
                    randomSeed = randomSeed,
                    onShuffle = { randomSeed++ },
                    onOpenCard = ::openCard,
                    onOpenShow = onOpenShow,
                )
            }
        }
    }
}

/**
 * Contenu « comme anime-sama » : sections en pleine largeur (en-têtes,
 * carrousel) et cartes en cellules de grille.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    home: dot.jcversa.novay.data.CatalogHome,
    randomSeed: Int,
    onShuffle: () -> Unit,
    onOpenCard: (title: String, posterUrl: String?, seasonUrl: String, language: String?) -> Unit,
    onOpenShow: (AnimeSearchResult) -> Unit
) {
    if (home.heroes.isEmpty() && home.cards.isEmpty()) {
        EmptyScreen(
            icon = Icons.Rounded.Search,
            headingText = "Explore le catalogue",
            subtitleText = "Tape au moins 2 lettres puis valide — la recherche essaie aussi les variantes proches (article, pluriel).",
        )
        return
    }

    val recent = home.cards.take(RECENT_LIMIT)
    val random = remember(randomSeed, home) {
        if (home.cards.isEmpty()) emptyList()
        else home.cards.shuffled(Random(randomSeed.toLong())).take(RANDOM_COUNT)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(128.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ── À la une : le carrousel héros du site ──
        if (home.heroes.isNotEmpty()) {
            item(key = "hero_header", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(title = "À la une")
            }
            item(key = "hero_pager", span = { GridItemSpan(maxLineSpan) }) {
                HeroPager(
                    heroes = home.heroes,
                    onOpen = { hero ->
                        onOpenCard(hero.title, hero.posterUrl, hero.seasonUrl, hero.language)
                    },
                )
            }
        }

        // ── Derniers épisodes ajoutés ──
        if (recent.isNotEmpty()) {
            item(key = "recent_header", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(title = "Derniers épisodes")
            }
            itemsIndexed(recent, key = { _, c -> "recent_${c.animeUrl}" }) { _, card ->
                CatalogCardItem(card = card, onOpen = onOpenCard)
            }
        }

        // ── Aléatoire ──
        if (random.isNotEmpty()) {
            item(key = "random_header", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(title = "Aléatoire", modifier = Modifier.weight(1f))
                    IconButton(onClick = onShuffle) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Regénérer le tirage",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            itemsIndexed(random, key = { _, c -> "random_${randomSeed}_${c.animeUrl}" }) { _, card ->
                CatalogCardItem(card = card, onOpen = onOpenCard)
            }
        }
    }
}

/** En-tête de section (typo header Mihon, bonus de compteur). */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.header,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/**
 * Carrousel « À la une » : affiche de fond, dégradé bas, badge de saison,
 * titre, genres, synopsis tronqué. Défilement automatique (6 s) qui
 * s'arrête pendant la manipulation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroPager(
    heroes: List<CatalogHero>,
    onOpen: (CatalogHero) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0) { heroes.size }
    val scope = rememberCoroutineScope()

    // Défilement auto, en boucle, tant que l'utilisateur ne touche pas.
    LaunchedEffect(pagerState) {
        while (true) {
            delay(6000L)
            if (!pagerState.isScrollInProgress && pagerState.pageCount > 1) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % pagerState.pageCount)
            }
        }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val hero = heroes[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onOpen(hero) },
            ) {
                PosterImage(
                    url = hero.posterUrl,
                    contentDescription = hero.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Dégradé bas pour la lisibilité (pattern Mihon MangaInfo).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color(0xCC101014),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    hero.seasonLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = hero.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (hero.genres.isNotEmpty()) {
                        Text(
                            text = hero.genres.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    hero.synopsis?.let { synopsis ->
                        Text(
                            text = synopsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Pastille « lire » en clair (coin bas droit).
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(30.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Points de pagination.
        if (heroes.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(heroes.size) { i ->
                    val active = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 7.dp else 5.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                    )
                }
            }
        }
    }
}

/** Carte de catalogue (grille) : affiche + titre + heure + langue. */
@Composable
private fun CatalogCardItem(
    card: CatalogCard,
    onOpen: (title: String, posterUrl: String?, seasonUrl: String, language: String?) -> Unit,
) {
    AnimeComfortableGridItem(
        title = card.title,
        posterUrl = card.posterUrl,
        onClick = { onOpen(card.title, card.posterUrl, card.seasonUrl, card.language) },
        onLongClick = { },
    )
}

/** Grille des résultats de recherche (V2.4, inchangée). */
@Composable
private fun ResultsGrid(
    results: List<AnimeSearchResult>,
    onOpenShow: (AnimeSearchResult) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(128.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(results, key = { _, r -> r.url }) { _, result ->
            AnimeComfortableGridItem(
                title = result.title,
                posterUrl = result.posterUrl,
                onClick = { onOpenShow(result) },
                onLongClick = { },
            )
        }
    }
}
