package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.data.AnimeSearchResult
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
import kotlinx.coroutines.launch

/** États de l'écran de recherche. */
private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Error(val message: String) : SearchState
    data class Results(val results: List<AnimeSearchResult>) : SearchState
}

/**
 * Écran Recherche : interroge le catalogue (variantes de requête côté
 * source : article retiré, singulier/pluriel, « rezero » → « re zero »).
 */
@Composable
fun SearchScreen(
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "NoVaY",
            style = MaterialTheme.typography.displayMedium
        )
        Text(
            text = "Animes VF & VOSTFR, depuis ton téléphone",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "Rechercher un anime…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = TextMuted
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Amber500,
                unfocusedBorderColor = DarkCardBorder,
                focusedContainerColor = DarkSurfaceCard,
                unfocusedContainerColor = DarkSurfaceCard,
                cursorColor = Amber500,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            SearchState.Idle -> Text(
                text = "Tape au moins 2 lettres puis valide — la recherche essaie aussi les variantes proches (article, pluriel).",
                style = MaterialTheme.typography.bodySmall
            )
            SearchState.Loading -> LoadingView(message = "Recherche sur ${source.name}…")
            is SearchState.Error -> ErrorView(message = s.message, onRetry = { runSearch() })
            is SearchState.Results -> {
                if (s.results.isEmpty()) {
                    Text(
                        text = "Aucun résultat pour « ${query.trim()} » — vérifie l'orthographe, ou sépare bien les mots du titre.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(s.results) { index, result ->
                            AnimateEntrance(index = index) {
                                ResultCard(result = result, onClick = { onOpenShow(result) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: AnimeSearchResult, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = TextMuted
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!result.subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}
