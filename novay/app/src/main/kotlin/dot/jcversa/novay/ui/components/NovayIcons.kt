package dot.jcversa.novay.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Suite d'icônes vectorielles sur mesure NoVaY — esthétique cinéma japonais :
 * coupes nettes biseautées, trait constant (1.85f) sur grille optique 24dp
 * et mise en cache paresseuse (zéro allocation à l'exécution).
 *
 * Les icônes du dock bas et du rail de navigation restent exclusivement
 * gérées par [NovayTabMorphIcon].
 */
object NovayIcons {

    private val Ink = SolidColor(Color.Black)

    private inline fun buildIcon(
        name: String,
        autoMirror: Boolean = false,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = "Novay.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply(block).build()

    private inline fun ImageVector.Builder.strokePath(
        width: Float = 1.85f,
        cap: StrokeCap = StrokeCap.Butt,
        join: StrokeJoin = StrokeJoin.Miter,
        block: PathBuilder.() -> Unit,
    ) {
        path(
            fill = null,
            stroke = Ink,
            strokeLineWidth = width,
            strokeLineCap = cap,
            strokeLineJoin = join,
            pathBuilder = block,
        )
    }

    private inline fun ImageVector.Builder.fillPath(
        evenOdd: Boolean = false,
        block: PathBuilder.() -> Unit,
    ) {
        path(
            fill = Ink,
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            pathBuilder = block,
        )
    }

    // ── Navigation & Chromes ──────────────────────────────────────────────

    /** Flèche retour angulaire type lame katana. */
    val Back: ImageVector by lazy {
        buildIcon("Back", autoMirror = true) {
            strokePath(width = 2f) {
                moveTo(19.5f, 12f)
                lineTo(5.2f, 12f)
                moveTo(11f, 5.5f)
                lineTo(4.5f, 12f)
                lineTo(11f, 18.5f)
            }
        }
    }

    /** Chevron droit architectural à sommet net. */
    val ChevronRight: ImageVector by lazy {
        buildIcon("ChevronRight", autoMirror = true) {
            strokePath(width = 2f) {
                moveTo(9f, 5.5f)
                lineTo(15.5f, 12f)
                lineTo(9f, 18.5f)
            }
        }
    }

    /** Chevron bas architectural pour sections repliables. */
    val ChevronDown: ImageVector by lazy {
        buildIcon("ChevronDown") {
            strokePath(width = 2f) {
                moveTo(5.5f, 9f)
                lineTo(12f, 15.5f)
                lineTo(18.5f, 9f)
            }
        }
    }

    /** Sceau d'information circulaire minimal. */
    val Info: ImageVector by lazy {
        buildIcon("Info") {
            strokePath(width = 1.8f) {
                moveTo(12f, 3.2f)
                arcTo(8.8f, 8.8f, 0f, true, true, 11.99f, 3.2f)
                close()
                moveTo(12f, 11f)
                lineTo(12f, 16.5f)
            }
            fillPath {
                moveTo(11.1f, 7.2f)
                lineTo(12.9f, 7.2f)
                lineTo(12.9f, 9f)
                lineTo(11.1f, 9f)
                close()
            }
        }
    }

    /** Croix calligraphique nette. */
    val Close: ImageVector by lazy {
        buildIcon("Close") {
            strokePath(width = 2f) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    /** Actualisation en arc Ensō brisé avec pointe nette. */
    val Refresh: ImageVector by lazy {
        buildIcon("Refresh") {
            strokePath(width = 1.85f) {
                moveTo(19.5f, 12f)
                arcTo(7.5f, 7.5f, 0f, true, true, 17.3f, 6.7f)
            }
            fillPath {
                moveTo(19.5f, 3.8f)
                lineTo(19.5f, 8.8f)
                lineTo(14.5f, 8.8f)
                close()
            }
        }
    }

    /** Menu contextuel : trois losanges verticaux (sceau kamon). */
    val Overflow: ImageVector by lazy {
        buildIcon("Overflow") {
            fillPath {
                // Losange haut
                moveTo(12f, 3.8f); lineTo(13.8f, 5.6f); lineTo(12f, 7.4f); lineTo(10.2f, 5.6f); close()
                // Losange milieu
                moveTo(12f, 10.2f); lineTo(13.8f, 12f); lineTo(12f, 13.8f); lineTo(10.2f, 12f); close()
                // Losange bas
                moveTo(12f, 16.6f); lineTo(13.8f, 18.4f); lineTo(12f, 20.2f); lineTo(10.2f, 18.4f); close()
            }
        }
    }

    // ── Lecture & Cinéma ──────────────────────────────────────────────────

    /** Lame de lecture Play pleine biseautée. */
    val Play: ImageVector by lazy {
        buildIcon("Play") {
            fillPath {
                moveTo(6.8f, 4.5f)
                lineTo(19.5f, 12f)
                lineTo(6.8f, 19.5f)
                lineTo(8.2f, 12f)
                close()
            }
        }
    }

    /** Doubles barres Pause biseautées. */
    val Pause: ImageVector by lazy {
        buildIcon("Pause") {
            fillPath {
                moveTo(6.5f, 5f); lineTo(10.2f, 5f); lineTo(10.2f, 19f); lineTo(6.5f, 17.6f); close()
                moveTo(13.8f, 6.4f); lineTo(17.5f, 5f); lineTo(17.5f, 19f); lineTo(13.8f, 19f); close()
            }
        }
    }

    /** Lecture dans un anneau de diaphragme cinéma. */
    val PlayCircle: ImageVector by lazy {
        buildIcon("PlayCircle") {
            strokePath(width = 1.8f) {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 11.99f, 3f)
                close()
            }
            fillPath {
                moveTo(9.8f, 8.2f)
                lineTo(16.4f, 12f)
                lineTo(9.8f, 15.8f)
                close()
            }
        }
    }

    /** Clap / pellicule cinéma à rayures japonaises. */
    val ClapperFilm: ImageVector by lazy {
        buildIcon("ClapperFilm") {
            strokePath(width = 1.75f) {
                moveTo(3.5f, 5.5f)
                lineTo(20.5f, 5.5f)
                lineTo(20.5f, 19f)
                lineTo(3.5f, 19f)
                close()
                moveTo(3.5f, 9.5f)
                lineTo(20.5f, 9.5f)
                moveTo(7.5f, 5.5f)
                lineTo(9.8f, 9.5f)
                moveTo(13.2f, 5.5f)
                lineTo(15.5f, 9.5f)
            }
            fillPath {
                moveTo(10.5f, 12f); lineTo(15f, 14.3f); lineTo(10.5f, 16.6f); close()
            }
        }
    }

    /** Étoile kamon 4 branches pour la section « À la une ». */
    val Spotlight: ImageVector by lazy {
        buildIcon("Spotlight") {
            fillPath {
                moveTo(11f, 2.5f)
                lineTo(13f, 9f)
                lineTo(19.5f, 11f)
                lineTo(13f, 13f)
                lineTo(11f, 19.5f)
                lineTo(9f, 13f)
                lineTo(2.5f, 11f)
                lineTo(9f, 9f)
                close()
                // Éclat secondaire
                moveTo(18.5f, 15f)
                lineTo(19.3f, 17.5f)
                lineTo(21.8f, 18.3f)
                lineTo(19.3f, 19.1f)
                lineTo(18.5f, 21.6f)
                lineTo(17.7f, 19.1f)
                lineTo(15.2f, 18.3f)
                lineTo(17.7f, 17.5f)
                close()
            }
        }
    }

    /** Cartes superposées de saisons avec découpe Play. */
    val SeasonReel: ImageVector by lazy {
        buildIcon("SeasonReel") {
            strokePath(width = 1.75f) {
                moveTo(4f, 6.5f)
                lineTo(17f, 6.5f)
                lineTo(17f, 17.5f)
                lineTo(4f, 17.5f)
                close()
                moveTo(8f, 3.8f)
                lineTo(20.5f, 3.8f)
                lineTo(20.5f, 15f)
            }
            fillPath {
                moveTo(8.8f, 9.3f); lineTo(13.6f, 12f); lineTo(8.8f, 14.7f); close()
            }
        }
    }

    // ── HUD Lecteur Vidéo ─────────────────────────────────────────────────

    /** Cadenas architectural à pans coupés. */
    val Lock: ImageVector by lazy {
        buildIcon("Lock") {
            strokePath(width = 1.85f) {
                moveTo(7.5f, 10.2f)
                lineTo(7.5f, 7.2f)
                arcTo(4.5f, 4.5f, 0f, true, true, 16.5f, 7.2f)
                lineTo(16.5f, 10.2f)
                moveTo(5.2f, 10.2f)
                lineTo(18.8f, 10.2f)
                lineTo(18.8f, 20f)
                lineTo(5.2f, 20f)
                close()
            }
            fillPath {
                moveTo(11.1f, 13.4f); lineTo(12.9f, 13.4f); lineTo(12.9f, 17.2f); lineTo(11.1f, 17.2f); close()
            }
        }
    }

    /** Cadre incliné avec double arc de rotation écran. */
    val ScreenRotate: ImageVector by lazy {
        buildIcon("ScreenRotate") {
            strokePath(width = 1.75f) {
                // Rectangle écran incliné
                moveTo(10.5f, 4.2f)
                lineTo(19.8f, 13.5f)
                lineTo(13.5f, 19.8f)
                lineTo(4.2f, 10.5f)
                close()
                // Arc bas gauche
                moveTo(3.5f, 15f)
                arcTo(8.5f, 8.5f, 0f, false, false, 8.8f, 20.5f)
                // Arc haut droit
                moveTo(20.5f, 9f)
                arcTo(8.5f, 8.5f, 0f, false, false, 15.2f, 3.5f)
            }
        }
    }

    /** Régulateurs de qualité & affichage (3 rails géométriques). */
    val QualitySliders: ImageVector by lazy {
        buildIcon("QualitySliders") {
            strokePath(width = 1.8f) {
                moveTo(3.5f, 6.5f); lineTo(8.5f, 6.5f)
                moveTo(13.5f, 6.5f); lineTo(20.5f, 6.5f)
                moveTo(3.5f, 12f); lineTo(14.5f, 12f)
                moveTo(19f, 12f); lineTo(20.5f, 12f)
                moveTo(3.5f, 17.5f); lineTo(6.5f, 17.5f)
                moveTo(11.5f, 17.5f); lineTo(20.5f, 17.5f)
            }
            fillPath {
                // Curseur 1
                moveTo(9f, 4.3f); lineTo(13f, 4.3f); lineTo(13f, 8.7f); lineTo(9f, 8.7f); close()
                // Curseur 2
                moveTo(14.5f, 9.8f); lineTo(18.5f, 9.8f); lineTo(18.5f, 14.2f); lineTo(14.5f, 14.2f); close()
                // Curseur 3
                moveTo(7f, 15.3f); lineTo(11f, 15.3f); lineTo(11f, 19.7f); lineTo(7f, 19.7f); close()
            }
        }
    }

    /** Cadran de vitesse de lecture à aiguille angulaire. */
    val SpeedGauge: ImageVector by lazy {
        buildIcon("SpeedGauge") {
            strokePath(width = 1.85f) {
                moveTo(4.2f, 17.2f)
                arcTo(8.8f, 8.8f, 0f, true, true, 19.8f, 17.2f)
                moveTo(12f, 14.5f)
                lineTo(17.2f, 8.8f)
            }
            fillPath {
                moveTo(10.5f, 14.5f)
                lineTo(12f, 13f)
                lineTo(13.5f, 14.5f)
                lineTo(12f, 16f)
                close()
            }
        }
    }

    /** Coins cinéma vers l'extérieur pour passer en plein écran paysage. */
    val FullscreenEnter: ImageVector by lazy {
        buildIcon("FullscreenEnter") {
            strokePath(width = 1.9f) {
                moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
                moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
                moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
                moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
            }
        }
    }

    /** Coins cinéma vers l'intérieur pour quitter le plein écran. */
    val FullscreenExit: ImageVector by lazy {
        buildIcon("FullscreenExit") {
            strokePath(width = 1.9f) {
                moveTo(9f, 4f); lineTo(9f, 9f); lineTo(4f, 9f)
                moveTo(15f, 4f); lineTo(15f, 9f); lineTo(20f, 9f)
                moveTo(20f, 15f); lineTo(15f, 15f); lineTo(15f, 20f)
                moveTo(4f, 15f); lineTo(9f, 15f); lineTo(9f, 20f)
            }
        }
    }

    /** Cadre avec vignette inférieure pour réduire le lecteur en mini-lecteur flottant. */
    val MinimizePlayer: ImageVector by lazy {
        buildIcon("MinimizePlayer", autoMirror = true) {
            strokePath(width = 1.8f) {
                moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
                moveTo(6.5f, 8.5f); lineTo(11f, 12.2f)
                moveTo(8.2f, 12.2f); lineTo(11f, 12.2f); lineTo(11f, 9.4f)
            }
            fillPath {
                moveTo(12.8f, 12.8f); lineTo(18.8f, 12.8f); lineTo(18.8f, 16.8f); lineTo(12.8f, 16.8f); close()
            }
        }
    }

    /** Cartouche de sous-titres et pistes audio cinéma. */
    val Subtitles: ImageVector by lazy {
        buildIcon("Subtitles") {
            strokePath(width = 1.85f) {
                moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f); lineTo(20.5f, 18.5f); lineTo(3.5f, 18.5f); close()
                moveTo(7f, 11.2f); lineTo(11.5f, 11.2f)
                moveTo(13.5f, 11.2f); lineTo(17f, 11.2f)
                moveTo(7f, 14.8f); lineTo(9.5f, 14.8f)
                moveTo(11.5f, 14.8f); lineTo(17f, 14.8f)
            }
        }
    }

    /** Soleil japonais Hinomaru à 8 rayons nets pour la luminosité. */
    val Brightness: ImageVector by lazy {
        buildIcon("Brightness") {
            strokePath(width = 1.8f) {
                moveTo(12f, 7.8f)
                arcTo(4.2f, 4.2f, 0f, true, true, 11.99f, 7.8f)
                close()
                moveTo(12f, 2.5f); lineTo(12f, 5f)
                moveTo(12f, 19f); lineTo(12f, 21.5f)
                moveTo(2.5f, 12f); lineTo(5f, 12f)
                moveTo(19f, 12f); lineTo(21.5f, 12f)
                moveTo(5.3f, 5.3f); lineTo(7.1f, 7.1f)
                moveTo(16.9f, 16.9f); lineTo(18.7f, 18.7f)
                moveTo(18.7f, 5.3f); lineTo(16.9f, 7.1f)
                moveTo(7.1f, 16.9f); lineTo(5.3f, 18.7f)
            }
        }
    }

    /** Haut-parleur biseauté et doubles ondes sonores. */
    val Volume: ImageVector by lazy {
        buildIcon("Volume", autoMirror = true) {
            fillPath {
                moveTo(3.8f, 9.2f)
                lineTo(7.6f, 9.2f)
                lineTo(12.8f, 4.8f)
                lineTo(12.8f, 19.2f)
                lineTo(7.6f, 14.8f)
                lineTo(3.8f, 14.8f)
                close()
            }
            strokePath(width = 1.85f) {
                moveTo(15.6f, 8.8f)
                arcTo(4.5f, 4.5f, 0f, false, true, 15.6f, 15.2f)
                moveTo(18.2f, 6.2f)
                arcTo(8.2f, 8.2f, 0f, false, true, 18.2f, 17.8f)
            }
        }
    }

    // ── Catalogue, Bibliothèque & Navigation interne ──────────────────────

    /** Loupe optique cinéma à manche katana incliné. */
    val Search: ImageVector by lazy {
        buildIcon("Search") {
            strokePath(width = 1.9f) {
                moveTo(10.5f, 4f)
                arcTo(6.5f, 6.5f, 0f, true, true, 10.49f, 4f)
                close()
                moveTo(15.3f, 15.3f)
                lineTo(20.5f, 20.5f)
            }
        }
    }

    /** Flèche diagonale katana pour insérer une suggestion dans la barre de recherche. */
    val FillQuery: ImageVector by lazy {
        buildIcon("FillQuery", autoMirror = true) {
            strokePath(width = 1.9f) {
                moveTo(17.5f, 17.5f)
                lineTo(6.5f, 6.5f)
                moveTo(15.2f, 6.5f)
                lineTo(6.5f, 6.5f)
                lineTo(6.5f, 15.2f)
            }
        }
    }

    /** Grille compacte shoji 3×3. */
    val GridCompact: ImageVector by lazy {
        buildIcon("GridCompact") {
            fillPath {
                val coords = floatArrayOf(4f, 10.2f, 16.4f)
                for (y in coords) {
                    for (x in coords) {
                        moveTo(x, y)
                        lineTo(x + 3.6f, y)
                        lineTo(x + 3.6f, y + 3.6f)
                        lineTo(x, y + 3.6f)
                        close()
                    }
                }
            }
        }
    }

    /** Grille confortable 2×2. */
    val GridComfortable: ImageVector by lazy {
        buildIcon("GridComfortable") {
            strokePath(width = 1.8f) {
                moveTo(4f, 4f); lineTo(10.4f, 4f); lineTo(10.4f, 10.4f); lineTo(4f, 10.4f); close()
                moveTo(13.6f, 4f); lineTo(20f, 4f); lineTo(20f, 10.4f); lineTo(13.6f, 10.4f); close()
                moveTo(4f, 13.6f); lineTo(10.4f, 13.6f); lineTo(10.4f, 20f); lineTo(4f, 20f); close()
                moveTo(13.6f, 13.6f); lineTo(20f, 13.6f); lineTo(20f, 20f); lineTo(13.6f, 20f); close()
            }
        }
    }

    /** Vue liste éditoriale (vignettes + lignes). */
    val ViewList: ImageVector by lazy {
        buildIcon("ViewList", autoMirror = true) {
            fillPath {
                moveTo(4f, 5f); lineTo(7.2f, 5f); lineTo(7.2f, 8.2f); lineTo(4f, 8.2f); close()
                moveTo(4f, 10.4f); lineTo(7.2f, 10.4f); lineTo(7.2f, 13.6f); lineTo(4f, 13.6f); close()
                moveTo(4f, 15.8f); lineTo(7.2f, 15.8f); lineTo(7.2f, 19f); lineTo(4f, 19f); close()
            }
            strokePath(width = 1.9f) {
                moveTo(9.5f, 6.6f); lineTo(20f, 6.6f)
                moveTo(9.5f, 12f); lineTo(20f, 12f)
                moveTo(9.5f, 17.4f); lineTo(17f, 17.4f)
            }
        }
    }

    /** Tri asymétrique style pinceau japonais. */
    val Sort: ImageVector by lazy {
        buildIcon("Sort", autoMirror = true) {
            strokePath(width = 2.1f) {
                moveTo(4f, 6.5f); lineTo(20f, 6.5f)
                moveTo(4f, 12f); lineTo(14.5f, 12f)
                moveTo(4f, 17.5f); lineTo(9.5f, 17.5f)
            }
        }
    }

    /** Filtre architectural biseauté pour les critères avancés du catalogue. */
    val FilterFunnel: ImageVector by lazy {
        buildIcon("FilterFunnel") {
            strokePath(width = 1.85f) {
                moveTo(3.5f, 5.2f)
                lineTo(20.5f, 5.2f)
                lineTo(14.2f, 12.6f)
                lineTo(14.2f, 19.2f)
                lineTo(9.8f, 17.2f)
                lineTo(9.8f, 12.6f)
                close()
            }
        }
    }

    /** Jaquettes d'animés empilées pour la bibliothèque vide. */
    val LibraryShelf: ImageVector by lazy {
        buildIcon("LibraryShelf") {
            strokePath(width = 1.75f) {
                moveTo(7.2f, 3.8f); lineTo(20f, 3.8f); lineTo(20f, 16.8f); lineTo(7.2f, 16.8f); close()
                moveTo(4f, 7.5f); lineTo(4f, 20.2f); lineTo(16.8f, 20.2f)
            }
            fillPath {
                moveTo(11.6f, 7.5f); lineTo(16.2f, 10.3f); lineTo(11.6f, 13.1f); close()
            }
        }
    }

    /** Cadran historique en boucle Ensō inversée. */
    val HistoryClock: ImageVector by lazy {
        buildIcon("HistoryClock") {
            strokePath(width = 1.85f) {
                moveTo(4.2f, 10.5f)
                arcTo(8f, 8f, 0f, true, true, 6.4f, 17.5f)
                moveTo(4.2f, 5.5f); lineTo(4.2f, 10.8f); lineTo(9.2f, 10.8f)
                moveTo(12.2f, 7.8f); lineTo(12.2f, 12.2f); lineTo(15.6f, 14.4f)
            }
        }
    }

    /** Sceau hanko octogonal pour les nouveautés / mises à jour. */
    val UpdatesSeal: ImageVector by lazy {
        buildIcon("UpdatesSeal") {
            strokePath(width = 1.85f) {
                moveTo(12f, 2.8f)
                lineTo(15.6f, 5.2f)
                lineTo(19.6f, 6f)
                lineTo(19.6f, 10.2f)
                lineTo(21.2f, 13.8f)
                lineTo(18f, 16.8f)
                lineTo(16.8f, 20.4f)
                lineTo(12f, 19.6f)
                lineTo(7.2f, 20.4f)
                lineTo(6f, 16.8f)
                lineTo(2.8f, 13.8f)
                lineTo(4.4f, 10.2f)
                lineTo(4.4f, 6f)
                lineTo(8.4f, 5.2f)
                close()
                moveTo(12f, 7.8f); lineTo(12f, 13f)
            }
            fillPath {
                moveTo(11.1f, 15.1f); lineTo(12.9f, 15.1f); lineTo(12.9f, 16.9f); lineTo(11.1f, 16.9f); close()
            }
        }
    }

    // ── Téléchargements, États & Réglages ─────────────────────────────────

    /** Flèche de téléchargement vers plateau torii. */
    val Download: ImageVector by lazy {
        buildIcon("Download") {
            strokePath(width = 1.9f) {
                moveTo(12f, 3.8f); lineTo(12f, 15f)
                moveTo(7.2f, 10.6f); lineTo(12f, 15.4f); lineTo(16.8f, 10.6f)
                moveTo(4.5f, 16.8f); lineTo(4.5f, 19.5f); lineTo(19.5f, 19.5f); lineTo(19.5f, 16.8f)
            }
        }
    }

    /** Validation de téléchargement terminé. */
    val DownloadDone: ImageVector by lazy {
        buildIcon("DownloadDone") {
            strokePath(width = 2f) {
                moveTo(5.2f, 11.4f); lineTo(9.8f, 15.8f); lineTo(18.8f, 6.4f)
                moveTo(5f, 19.2f); lineTo(19f, 19.2f)
            }
        }
    }

    /** Coche calligraphique nette. */
    val Check: ImageVector by lazy {
        buildIcon("Check") {
            strokePath(width = 2.1f) {
                moveTo(4.8f, 12.2f); lineTo(9.8f, 17.2f); lineTo(19.4f, 7f)
            }
        }
    }

    /** Cercle plein avec coche inversée (EvenOdd). */
    val CheckCircle: ImageVector by lazy {
        buildIcon("CheckCircle") {
            fillPath(evenOdd = true) {
                moveTo(12f, 2.8f)
                arcTo(9.2f, 9.2f, 0f, true, true, 11.99f, 2.8f)
                close()
                // Coche découpée en négatif
                moveTo(10.6f, 16.4f)
                lineTo(6.8f, 12.6f)
                lineTo(8.1f, 11.3f)
                lineTo(10.6f, 13.8f)
                lineTo(16.1f, 8.3f)
                lineTo(17.4f, 9.6f)
                close()
            }
        }
    }

    /** Anneau de sélection vide. */
    val UncheckedCircle: ImageVector by lazy {
        buildIcon("UncheckedCircle") {
            strokePath(width = 1.85f) {
                moveTo(12f, 3.2f)
                arcTo(8.8f, 8.8f, 0f, true, true, 11.99f, 3.2f)
                close()
            }
        }
    }

    /** Double coche nette « Tout marquer comme vu ». */
    val MarkAllSeen: ImageVector by lazy {
        buildIcon("MarkAllSeen") {
            strokePath(width = 1.9f) {
                moveTo(2.5f, 12.5f); lineTo(6.8f, 16.8f); lineTo(15.2f, 7.6f)
                moveTo(10.6f, 14.5f); lineTo(12.9f, 16.8f); lineTo(21.4f, 7.6f)
            }
        }
    }

    /** Sablier / file d'attente cinéma. */
    val QueuedClock: ImageVector by lazy {
        buildIcon("QueuedClock") {
            strokePath(width = 1.8f) {
                moveTo(12f, 3.2f)
                arcTo(8.8f, 8.8f, 0f, true, true, 11.99f, 3.2f)
                close()
                moveTo(12f, 7.2f); lineTo(12f, 12.2f); lineTo(15.8f, 14.2f)
            }
        }
    }

    /** Sceau d'alerte octogonal. */
    val ErrorSeal: ImageVector by lazy {
        buildIcon("ErrorSeal") {
            strokePath(width = 1.8f) {
                moveTo(8.2f, 3.2f)
                lineTo(15.8f, 3.2f)
                lineTo(20.8f, 8.2f)
                lineTo(20.8f, 15.8f)
                lineTo(15.8f, 20.8f)
                lineTo(8.2f, 20.8f)
                lineTo(3.2f, 15.8f)
                lineTo(3.2f, 8.2f)
                close()
                moveTo(12f, 7.5f); lineTo(12f, 13.2f)
            }
            fillPath {
                moveTo(11f, 15.2f); lineTo(13f, 15.2f); lineTo(13f, 17.2f); lineTo(11f, 17.2f); close()
            }
        }
    }

    /** Nuage hors-ligne tranché en diagonale. */
    val OfflineCloud: ImageVector by lazy {
        buildIcon("OfflineCloud") {
            strokePath(width = 1.85f) {
                moveTo(7.4f, 18.2f)
                lineTo(17.4f, 18.2f)
                arcTo(4.1f, 4.1f, 0f, false, false, 18.4f, 10.2f)
                arcTo(5.6f, 5.6f, 0f, false, false, 8.2f, 7.6f)
                moveTo(5.8f, 9.6f)
                arcTo(4.5f, 4.5f, 0f, false, false, 7.4f, 18.2f)
                moveTo(3.5f, 4f)
                lineTo(20.5f, 20f)
            }
        }
    }

    /** Corbeille shoji minimale à couvercle biseauté. */
    val DeleteTrash: ImageVector by lazy {
        buildIcon("DeleteTrash") {
            strokePath(width = 1.8f) {
                moveTo(4.2f, 6.6f); lineTo(19.8f, 6.6f)
                moveTo(9.2f, 6.6f); lineTo(10f, 3.8f); lineTo(14f, 3.8f); lineTo(14.8f, 6.6f)
                moveTo(6.4f, 6.6f); lineTo(7.5f, 20.2f); lineTo(16.5f, 20.2f); lineTo(17.6f, 6.6f)
                moveTo(10.3f, 10.2f); lineTo(10.6f, 16.8f)
                moveTo(13.7f, 10.2f); lineTo(13.4f, 16.8f)
            }
        }
    }

    /** Croissant de lune AMOLED et étoile kamon. */
    val AmoledMoon: ImageVector by lazy {
        buildIcon("AmoledMoon") {
            strokePath(width = 1.8f) {
                moveTo(14.8f, 3.6f)
                arcTo(8.5f, 8.5f, 0f, true, false, 20.4f, 15.2f)
                arcTo(6.4f, 6.4f, 0f, true, true, 14.8f, 3.6f)
                close()
            }
            fillPath {
                moveTo(17.8f, 4.2f)
                lineTo(18.5f, 6.1f)
                lineTo(20.4f, 6.8f)
                lineTo(18.5f, 7.5f)
                lineTo(17.8f, 9.4f)
                lineTo(17.1f, 7.5f)
                lineTo(15.2f, 6.8f)
                lineTo(17.1f, 6.1f)
                close()
            }
        }
    }

    /** Ondes Wi-Fi géométriques ascendantes. */
    val WifiSignal: ImageVector by lazy {
        buildIcon("WifiSignal") {
            strokePath(width = 1.85f) {
                moveTo(3.2f, 9.4f)
                arcTo(12.5f, 12.5f, 0f, false, true, 20.8f, 9.4f)
                moveTo(6.4f, 12.8f)
                arcTo(8f, 8f, 0f, false, true, 17.6f, 12.8f)
                moveTo(9.4f, 16.2f)
                arcTo(3.8f, 3.8f, 0f, false, true, 14.6f, 16.2f)
            }
            fillPath {
                moveTo(12f, 18.2f)
                lineTo(13.6f, 19.8f)
                lineTo(12f, 21.4f)
                lineTo(10.4f, 19.8f)
                close()
            }
        }
    }
}
