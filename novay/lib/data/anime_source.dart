/// Contrat de catalogue V1 — chaque source (voiranime d'abord, puis
/// anime-sama, jetanimes…) l'implémente. Portage direct du pattern
/// `animeSources.ts` du bot : les modèles sont volontairement identiques
/// pour faciliter le portage des parseurs (mêmes fixtures de test).
library;

/// Un résultat de recherche (une « série » sur le catalogue).
class AnimeSearchResult {
  const AnimeSearchResult({
    required this.title,
    required this.url,
    required this.slug,
    this.subtitle,
    this.language,
    this.isVf = false,
  });

  final String title;
  final String url;
  final String slug;
  final String? subtitle;
  final String? language; // "VF" | "VOSTFR" | null
  final bool isVf;
}

/// Une saison d'une série.
class AnimeSeason {
  const AnimeSeason({required this.name, required this.url, this.language});

  final String name;
  final String url;
  final String? language;
}

/// Un épisode d'une saison.
class AnimeEpisode {
  const AnimeEpisode({required this.number, required this.url});

  final int number;
  final String url;
}

/// Une qualité proposée par le lecteur (lue dans la master playlist HLS).
class StreamQuality {
  const StreamQuality({required this.label, required this.playlistUrl});

  final String label; // "480P", "720P"…
  final String playlistUrl;
}

/// Contrat commun à tous les catalogues V1+.
abstract interface class AnimeSource {
  /// Nom affiché (ex. "voir-anime").
  String get name;

  Future<List<AnimeSearchResult>> search(String query);

  Future<List<AnimeSeason>> seasons(AnimeSearchResult show);

  Future<List<AnimeEpisode>> episodes(AnimeSeason season);

  /// Qualités disponibles pour un épisode (résolutions du lecteur).
  Future<List<StreamQuality>> qualities(AnimeEpisode episode);
}
