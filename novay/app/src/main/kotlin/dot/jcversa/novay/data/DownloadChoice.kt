package dot.jcversa.novay.data

/**
 * Choix du flux à télécharger pour un épisode — PUR (testable JVM).
 *
 * Politique V2 : la MEILLEURE qualité disponible (les qualités d'un
 * ResolvedStream sont déjà triées par hauteur décroissante — cf.
 * parseHlsMasterPlaylist). Télécharger le master entier embarquerait
 * TOUTES les variantes (4× le poids) : on télécharge la playlist de la
 * variante choisie.
 */
object DownloadChoice {

    data class Choice(
        val url: String,
        val isHls: Boolean,
        val qualityLabel: String
    )

    /** Meilleure qualité jouable en téléchargement. */
    fun best(stream: ResolvedStream): Choice {
        val top = stream.qualities.firstOrNull()
        return if (top != null) {
            Choice(url = top.url, isHls = true, qualityLabel = top.label)
        } else {
            // Pas de variantes lues : master direct (HLS) ou fichier MP4.
            Choice(
                url = stream.url,
                isHls = stream.isHls,
                qualityLabel = if (stream.isHls) "Auto" else "Direct"
            )
        }
    }

    /**
     * Qualité CHOISIE par l'utilisateur (V2.3) : la hauteur exacte si ce
     * lecteur la propose, sinon sa meilleure (repli honnête — le
     * téléchargement part quand même).
     */
    fun pick(stream: ResolvedStream, height: Int?): Choice =
        if (height == null) best(stream)
        else {
            val q = stream.qualities.firstOrNull { it.height == height }
            if (q != null) Choice(url = q.url, isHls = true, qualityLabel = q.label)
            else best(stream)
        }
}
