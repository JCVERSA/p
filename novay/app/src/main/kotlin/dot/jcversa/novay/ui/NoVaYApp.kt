package dot.jcversa.novay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dot.jcversa.novay.ui.components.SlateMotionProvider
import dot.jcversa.novay.ui.screens.EpisodesScreen
import dot.jcversa.novay.ui.screens.PlayerScreen
import dot.jcversa.novay.ui.screens.SearchScreen
import dot.jcversa.novay.ui.screens.ShowScreen
import dot.jcversa.novay.ui.theme.DarkBackground

/** Écrans NoVaY (V0 : squelettes ; V1 : catalogue voiranime). */
private enum class AppScreen { SEARCH, SHOW, EPISODES, PLAYER }

/**
 * Coque de navigation NoVaY : flux linéaire
 * Recherche → Fiche/Saisons → Épisodes → Lecteur, animé suivant la
 * direction (slide horizontal + fondu — canon SwiftSlate du panel).
 */
@Composable
fun NoVaYApp() {
    val stack = remember { mutableStateListOf(AppScreen.SEARCH) }

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val open: (AppScreen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    SlateMotionProvider {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBackground
        ) { innerPadding ->
            AnimatedContent(
                targetState = stack.last(),
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(180))
                            )
                    } else {
                        (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(180))
                            )
                    }
                },
                label = "novay_screen_stack"
            ) { screen ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (screen) {
                        AppScreen.SEARCH -> SearchScreen(
                            onOpenShow = { open(AppScreen.SHOW) }
                        )
                        AppScreen.SHOW -> ShowScreen(
                            onBack = back,
                            onOpenEpisodes = { open(AppScreen.EPISODES) }
                        )
                        AppScreen.EPISODES -> EpisodesScreen(
                            onBack = back,
                            onOpenPlayer = { open(AppScreen.PLAYER) }
                        )
                        AppScreen.PLAYER -> PlayerScreen(
                            onBack = back
                        )
                    }
                }
            }
        }
    }
}
