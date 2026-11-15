# NoVaY — Audit performance (V2.8.1)

Audit en **lecture seule** (Mode C du protocole d'ingénierie performance) :
aucune modification de code n'a été effectuée dans le cadre de cet audit.
Toute mise en œuvre des recommandations ci-dessous nécessite un feu vert
explicite de l'owner, un changement à la fois, CI verte à chaque étape.

- **Date** : 15/11/2026
- **Commit audité** : `937126f` (V2.8.1, run CI vert 43656265842)
- **Périmètre** : appli Android `novay/` uniquement (le bot/panel n'a ni
  VPS actif ni utilisateur — hors périmètre)
- **Plateforme cible** : Android 7+ 32 bits (armeabi-v7a), appareil
  modeste — profil exact du téléphone de l'owner

---

## 1. Contexte projet

| Champ | Valeur |
|---|---|
| Application | NoVaY (streaming/téléchargement anime) |
| Repo / branche | `JCVERSA/p` — `arena/01a05555-p` |
| Type | Appli native Kotlin + Jetpack Compose |
| Toolchain | AGP 9.1.1, Kotlin 2.2.10, Gradle 9.3.1, BOM 2024.09.00 |
| Sources de données | Scrape HTTP anime-sama (origine dynamique), CDN d'affiches |
| Lecteurs | ExoPlayer (streaming, en-têtes/qualités) + libVLC 3.7.7 (fichiers téléchargés) |
| Livraison | APK arm32 seul + archive .7z (CI GitHub Actions) |
| Parcours critiques | Démarrage → catalogue → recherche → fiche → épisodes → lecture (streaming + fichier local) → téléchargements |

## 2. Méthode et limites

Mesures disponibles depuis cet environnement :

- Annotations CI du run vert 43656265842 (tailles livrées, tests)
- Mesures statiques du dépôt (poids des ressources embarquées)
- Inspection du code (évidence : CONFIRMÉ par lecture)

**Limites assumées** : aucun appareil ni émulateur dans l'environnement de
travail — les métriques d'exécution (démarrage à froid, fluidité de
défilement, mémoire, batterie) ne peuvent pas être mesurées ici. Le
téléchargement des artifacts CI est bloqué depuis cet environnement
(stockage Azure hors hôtes autorisés) — la décomposition fine du contenu
de l'APK reste une estimation arithmétique. Le protocole de mesure sur
appareil réel est fourni en section 6.

## 3. Mesures de référence (baseline)

| Cible | Métrique | Valeur | Méthode | Confiance |
|---|---|---|---|---|
| Livraison arm32 | APK | 50,94 Mo | Annotation CI run 43656265842 | CONFIRMÉ |
| Livraison arm32 | Archive .7z | 26,13 Mo (−49 %) | Annotation CI run 43656265842 | CONFIRMÉ |
| Tests | Unitaires verts | 174 | Annotation CI run 43656265842 | CONFIRMÉ |
| Référence V2.7 (sans libvlc) | APK | 10,58 Mo | Run CI 43634022271 | CONFIRMÉ |
| APK : natives libvlc | Poids déduit | ≈ 40,3 Mo (50,94 − 10,58, hors ~0,7 Mo polices/fonctionnalité genres) | Arithmétique | LIKELY |
| Ressources embarquées | Polices | 1,5 Mo / 11 fichiers TTF | `du` local | CONFIRMÉ |
| Ressources embarquées | Lottie (res/raw) | 104 Ko / 20 fichiers | `du` local | CONFIRMÉ |
| Base de code | Sources Kotlin | 59 fichiers / 928 Ko | `find`/`du` local | CONFIRMÉ |

## 4. Points forts confirmés (à préserver)

L'audit confirme de bonnes pratiques déjà en place — importantes à
connaître pour ne pas les dégrader lors de futures évolutions :

- **Listes paresseuses** : `key` + `contentType` systématiques (catalogue,
  épisodes, rails, historique) — recyclage propre des items.
- **Recherche** : debounce 280 ms, annulation par redémarrage de
  `LaunchedEffect`, cache TTL 3 min (LRU 24 entrées).
- **Caches TTL LRU** sur toutes les sources (home/à la une 10 min, genres
  10 min, saisons 10 min, épisodes 5 min) — zéro re-téléchargement inutile.
- **Coil** : memoryCache 22 % du heap, diskCache 64 Mo, `allowRgb565`
  (moitié de la mémoire bitmap sur appareils anciens), `AsyncImage` sans
  sous-composition, downsampling automatique, Referer anti-hotlink géré.
- **Persistance** : SharedPreferences + cache mémoire — aucun I/O disque
  par frappe clavier.
- **Téléchargements** : notification rafraîchie **par segment** (pas par
  octet), `Downloads.init` différé sur `Dispatchers.IO` au démarrage.
- **Lecteur** : `movableContent` pour la vue vidéo (zéro recréation de
  décodeur entre mini-lecteur/plein écran), `DecoderFallback` ExoPlayer,
  repli logiciel VLC pour fichiers locaux.
- **Accessibilité/motion** : `reduceMotion` respecté partout (Shimmer,
  Lottie, transitions).
- **CI** : garde-fou anti-fuite d'ABI, tailles en annotations, 174 tests.

## 5. Constats

Format du protocole (§15.1). Priorité selon impact utilisateur ×
fréquence × effort.

---

### F-01 — Boucle de progression jamais arrêtée après libération du lecteur

- **Statut : CONFIRMÉ** (lecture de code)
- **Comportement observé** : `ensureProgressLoopStarted()` (PlayerStateManager)
  démarre une boucle `while(isActive) { … delay(500) }` dès la première
  création du lecteur. Elle n'est annulée que par `release()` (fin de
  l'activité). `releasePlayerOnly()` et `closeAndStop()` ne l'annulent pas.
- **Conséquence** : après la première lecture (même fermée), la boucle
  tourne à vide — 2 réveils/seconde à vie du process, coroutine sur le
  dispatcher main. Sur un appareil 32 bits modeste : micro-réveils CPU
  permanents, impact batterie en veille active de l'app.
- **Cause racine** : la boucle était historiquement liée à l'ExoPlayer
  (elle polluait sa position) ; V2.8 en a fait la source unifiée (VLC y
  passe aussi), mais le cycle de vie n'a pas suivi.
- **Changement proposé** : annuler `progressJob` dans `releasePlayerOnly()`
  et `closeAndStop()`, et appeler `ensureProgressLoopStarted()` à
  l'ouverture VLC (`switchToVlc`) — la boucle ne vit que quand un moteur
  existe.
- **Effort : minimal. Risque : faible** (redémarrage automatique de la
  boucle au besoin — garde `if (progressJob?.isActive == true) return`).
- **Vérification** : tests existants + CI verte ; sur appareil : la
  position/progression continue de se mettre à jour en lecture.
- **Rollback** : revert du commit.

### F-02 — Polices embarquées non sous-ensemblées (1,5 Mo)

- **Statut : CONFIRMÉ (mesure locale) / RECOMMANDATION (le gain)**
- **Observé** : 11 fichiers TTF complets = 1,5 Mo. Les trois familles
  (Outfit, Inter, JetBrains Mono) sont toutes référencées par la
  Typography (6 + 5 + 2 styles) — aucune n'est morte. Mais ce sont des
  TTF plein jeu de caractères (ex. `inter_bold.ttf` = 326 Ko) pour une
  appli dont tout le texte visible est latin (français avec accents +
  chiffres + ponctuation).
- **Changement proposé** : sous-ensemblage à la CI (fonttools, jeu
  Latin étendu conservant accents français, dans un dossier de build —
  jamais en écrasant les sources). Gain estimé ~0,8-1,1 Mo sur l'APK
  **et proportionnel sur le .7z** (les polices compressent mal en LZMA2
  lorsqu'elles sont déjà denses) — à confirmer par build avant/après.
- **Effort : moyen** (étape CI + validation visuelle des accents).
- **Risque** : glyphe manquant si le jeu est trop serré — d'où jeu Latin
  étendu et vérification sur écran réel (titres, descriptions, chiffres).
- **Vérification** : tailles en annotations CI (avant/après), inspection
  visuelle sur l'appareil de l'owner.
- **Rollback** : retirer l'étape de sous-ensemblage.

### F-03 — Pas de timeout global sur les appels HTTP

- **Statut : LIKELY**
- **Observé** : `SamaSource.defaultClient()` = connect 12 s + read 12 s
  sans `callTimeout` ; client images = connect 10 s + read 15 s sans
  `callTimeout`. Un appel peut donc pendre bien au-delà de 24 s
  (retries réseau inclus) sans qu'aucune borne globale ne s'applique.
- **Conséquence** : sur réseau lent/instable (profil owner), un écran
  peut rester en chargement très longtemps sans erreur exploitable.
- **Changement proposé** : `callTimeout(20 s)` sur le client SamaSource,
  `callTimeout(25 s)` sur le client d'affiches. Effort : minimal.
- **Risque : nul** (bornes larges, comportement nominal inchangé).

### F-04 — Premier filtrage par genre = 2 requêtes séquentielles

- **Statut : LIKELY**
- **Observé** : `catalogByGenre` charge `/catalogue/` PUIS la home
  (séquentiel) au premier accès à un genre. Sur réseau lent : latence
  double avant affichage. Atténué par TTL 10 min (une seule fois par
  session de 10 min).
- **Changement proposé** : paralléliser les deux requêtes
  (`async`/`awaitAll` déjà utilisés ailleurs dans la classe), ou
  réutiliser la home déjà en cache si fraîche. Effort : faible.
- **Risque : faible** (ordre d'arrivée non garanti — le parseur est déjà
  défensif).

### F-05 — Optimisations hors périmètre

- **Statut : NOT APPLICABLE** — Core Web Vitals, Lighthouse, SSR,
  CDN, stratégies de cache HTTP, base de données, files de jobs :
  notions du protocole pensées pour le web, sans objet pour une appli
  Android native locale. Documenté pour mémoire afin de ne pas les
  « forcer » artificiellement.

### F-06 — Poids des natives libVLC

- **Statut : CONFIRMÉ (arithmétique) / aucune action recommandée**
- ≈ 40 Mo de `.so` libvlc pour armeabi-v7a — c'est le prix de la
  lecture bulletproof des fichiers téléchargés, choix owner assumé
  (V2.8). L'ABI unique est déjà l'optimisation principale ; le 7z
  (−49 %) amortit le téléchargement. Aucune variante libvlc 3.x plus
  légère stable n'existe à ce jour.

## 6. Protocole de mesure sur appareil réel (pour l'owner)

À réaliser sur le téléphone cible, avant puis après chaque optimisation,
conditions comparables (même heure réseau, redémarrage de l'app entre
chaque essai, 3 essais, prendre la médiane) :

1. **Démarrage à froid** : chronométrer du tap sur l'icône à
   l'affichage du catalogue (3 essais).
2. **Fluidité du catalogue** : défilement du rail + de la grille —
   référence qualitative = lecture des fichiers novabox du bot.
3. **Première recherche** : délai tap-à-tap entre la validation et les
   premiers résultats.
4. **Mémoire** : Paramètres → Applications → NoVaY → Mémoire, après
   10 min de navigation (avant/après F-01).
5. **Batterie** : pourcentage consommé pour 30 min de lecture locale
   (VLC) écran allumé.

Reporter les valeurs dans ce fichier à chaque mesure.

## 7. Priorisation proposée (§15 du protocole)

| Ordre | Constat | Impact owner | Effort | Risque |
|---|---|---|---|---|
| 1 | F-01 boucle à vide | Batterie/CPU permanent sur son téléphone | Minimal | Faible |
| 2 | F-03 callTimeout | Robustesse réseau lent | Minimal | Nul |
| 3 | F-02 sous-ensemblage polices | ~1 Mo de moins à télécharger (connexion limitée) | Moyen | Faible |
| 4 | F-04 genres parallélisés | Latence premier filtre par genre | Faible | Faible |

## 8. Statut

Audit terminé. **Aucun code modifié.** La mise en œuvre attend un feu
vert explicite de l'owner, un constat à la fois, avec CI verte et
avant/après mesurés (tailles CI + mesures sur appareil, section 6).
