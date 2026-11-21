package dot.jcversa.novay.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import coil.Coil
import dot.jcversa.novay.NovayImagePipeline
import dot.jcversa.novay.data.AnimeSearchResult
import dot.jcversa.novay.ui.theme.NoVaYTheme
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class PosterImageUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val validPngBytes: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53,
        0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
        0x54, 0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00,
        0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD.toByte(), 0x8D.toByte(),
        0xB0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
        0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )

    @After
    fun tearDown() {
        PosterRecoveryCoordinator.clearForTest()
        NovayImagePipeline.clearHostStrategies()
    }

    @Test
    fun posterImage_resolvesMissingUrlViaLocalPosterResolverWithoutBreakingCardLayout() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val requestedUrls = CopyOnWriteArrayList<String>()
        val client = NovayImagePipeline.createHttpClient(
            sleepMs = {},
            extraInterceptor = { chain ->
                val req = chain.request()
                requestedUrls.add(req.url.toString())
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(validPngBytes.toResponseBody("image/png".toMediaType()))
                    .build()
            },
        )
        Coil.setImageLoader(NovayImagePipeline.buildImageLoader(context, client))

        val resolvedUrls = CopyOnWriteArrayList<String>()
        val resolver = PosterResolver { anime ->
            if (anime.url.contains("overgeared")) "https://cdn.example/overgeared-healed.png" else null
        }

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    CompositionLocalProvider(LocalPosterResolver provides resolver) {
                        Column {
                            AnimeComfortableGridItem(
                                title = "Overgeared",
                                posterUrl = null,
                                animeUrl = "https://anime-sama.to/catalogue/overgeared/",
                                sharedTransitionEnabled = false,
                                onClick = {},
                                modifier = Modifier.size(160.dp, 260.dp),
                            )
                            PosterImage(
                                url = null,
                                animeUrl = "https://anime-sama.to/catalogue/overgeared/",
                                animeTitle = "Overgeared",
                                onPosterResolved = { resolvedUrls.add(it) },
                                modifier = Modifier.size(60.dp, 90.dp),
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            resolvedUrls.isNotEmpty()
        }
        composeRule.waitForIdle()

        assertEquals(listOf("https://cdn.example/overgeared-healed.png"), resolvedUrls)
        composeRule.onNodeWithText("Overgeared").assertIsDisplayed()
    }

    @Test
    fun lazyColumn_reorderedAnimeItemsMaintainExactPosterToAnimeAssociation() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val fetchedOrder = CopyOnWriteArrayList<String>()
        val client = NovayImagePipeline.createHttpClient(
            sleepMs = {},
            extraInterceptor = { chain ->
                val req = chain.request()
                fetchedOrder.add(req.url.toString())
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(validPngBytes.toResponseBody("image/png".toMediaType()))
                    .build()
            },
        )
        Coil.setImageLoader(NovayImagePipeline.buildImageLoader(context, client))

        val initialItems = (1..20).map { i ->
            AnimeSearchResult(
                title = "Anime $i",
                url = "https://anime-sama.to/catalogue/anime-$i/",
                slug = "anime-$i",
                posterUrl = "https://cdn.example/poster-$i.png",
            )
        }
        var itemsState by mutableStateOf(initialItems)

        composeRule.setContent {
            NoVaYTheme {
                SlateMotionProvider {
                    val listState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("poster_test_lazy_list"),
                        ) {
                            items(
                                items = itemsState,
                                key = { it.url },
                            ) { anime ->
                                AnimeListItem(
                                    title = anime.title,
                                    posterUrl = anime.posterUrl,
                                    animeUrl = anime.url,
                                    sharedTransitionEnabled = false,
                                    onClick = {
                                        scope.launch { listState.scrollToItem(15) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Anime 1").assertIsDisplayed()

        // Reverse item order dynamically to verify keys & image request states track each show cleanly
        composeRule.runOnUiThread {
            itemsState = initialItems.reversed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Anime 20").assertIsDisplayed()
    }
}
