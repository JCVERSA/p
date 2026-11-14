package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.data.AnimeSource
import dot.jcversa.novay.ui.components.AnimeComfortableGridItem
import dot.jcversa.novay.ui.components.AppBarTitle
import dot.jcversa.novay.ui.components.EmptyScreen
import dot.jcversa.novay.ui.components.ErrorView
import dot.jcversa.novay.ui.components.LoadingView
import dot.jcversa.novay.ui.components.NovayAppBar
import kotlinx.coroutines.launch

/** États de l'écran de recherche. */
private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Error(val message: String) : SearchState
    data class Results(val results: List<AnimeSearchResult>) : SearchState
}

/**
 * Onglet Catalogue (V2.4, design Mihon BrowseSourceScreen) : recherche du
 * catalogue, résultats en GRILLE de couvertures (au lieu de la liste V2).
 * La logique de requête (variantes article/pluriel côté source) ne change
 * pas d'un iota.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    source: AnimeSource,
    onOpenShow: (AnimeSearchResult) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    val scope = rememberCoroutineScope()

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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
            SearchState.Idle -> EmptyScreen(
                icon = Icons.Rounded.Search,
                headingText = "Explore le catalogue",
                subtitleText = "Tape au moins 2 lettres puis valide — la recherche essaie aussi les variantes proches (article, pluriel).",
            )
            SearchState.Loading -> LoadingView(message = "Recherche sur ${source.name}…")
            is SearchState.Error -> ErrorView(message = s.message, onRetry = { runSearch() })
            is SearchState.Results -> {
                if (s.results.isEmpty()) {
                    EmptyScreen(
                        icon = Icons.Rounded.Search,
                        headingText = "Aucun résultat",
                        subtitleText = "Rien pour « ${query.trim()} » — vérifie l'orthographe, ou sépare bien les mots du titre.",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(128.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(s.results, key = { _, r -> r.url }) { _, result ->
                            AnimeComfortableGridItem(
                                title = result.title,
                                posterUrl = result.posterUrl,
                                onClick = { onOpenShow(result) },
                                onLongClick = { },
                            )
                        }
                    }
                }
            }
        }
    }
}
