# NoVaY

Appli Android (Kotlin/Compose) de streaming et téléchargement d'animes
VF/VOSTFR, directement depuis le téléphone — sans serveur, sans WhatsApp.
L'IP résidentielle du téléphone remplace le serveur du bot : atout
anti-Cloudflare.

## Décisions
- **Stack** : Kotlin + Jetpack Compose, design system **SwiftSlate**
  réutilisé de l'appli panel `android/` (décision owner 13/11 : retour sur
  Flutter). Parité de versions avec le panel — AGP 9.1.1, Kotlin 2.2.10,
  Gradle 9.3.1, Compose BOM 2024.09.
- **Package** : `dot.jcversa.novay`, libellé « NoVaY ».
- **Compatibilité** : Android 7+ (minSdk 24) **et 32 bits** — l'APK « fat »
  embarque armeabi-v7a + arm64-v8a + x86_64 dans un seul fichier.
- L'appli panel `android/` (cm.jcversa.nova) reste intacte et continue
  sa CI : ressuscitable si le bot revient.

## Roadmap
- **V0** : thème noir+ambre, motion SwiftSlate, squelettes cliquables. ✔
- **V1** (livré) : catalogue **anime-sama** (voiranime est mort — domaine
  déréférencé des DNS publics le 13/11 ; parseurs conservés dans
  `data/voiranime/` pour un éventuel retour).
  - Recherche : POST `/template-php/defaut/fetch.php` + variantes de
    requête (article retiré, singulier/pluriel, « rezero » → « re zero »).
  - Origine **dynamique** : le site déménage sans cesse (.com/.me/.to/.si…)
    — résolue via le bouton `.btn-primary` du lanceur anime-sama.pw
    (portage de samaOrigin.ts, 9.19h), cache 24 h, repli sur le dernier
    domaine connu.
  - Saisons : appels `panneauAnime("Nom", "saison1/vostfr")` de la page
    anime (placeholders ignorés, langue structurelle du sous-chemin).
    La page statique ne liste que la langue par défaut (VOSTFR) — la VF
    vit à l'URL miroir (`…/vostfr/` → `…/vf/`, validée par sonde) : chaque
    panneau est sondé en parallèle et la variante existante est ajoutée.
    Les saisons sont ensuite CLASSÉES PAR CATÉGORIE : toutes les VF
    d'abord, toutes les VOSTFR ensuite — jamais mélangées (retour owner
    13/11, même politique que les écrans VF/VOSTFR séparés du bot).
  - Épisodes : tableaux INLINE `epsN = ["lien:186672", …]` (positionnels,
    une liste par lecteur) + `epsMeta` (hôtes) + `epsJeton` (jeton de
    session). NB : `episodes.js` externe = 404 désormais.
  - Lecteur : `/api/lien.php?j=<jeton>&l=<id>` → page embed → m3u8 en
    clair (master playlist → VRAIES résolutions) joué par ExoPlayer avec
    en-têtes Referer/Origin/UA. Jeton auto-rafraîchi (rechargement de la
    page de saison) sur expiration.
  - Chaîne validée **E2E depuis un runner GitHub Actions** le 13/11
    (recherche « naruto » → saisons → épisodes → lecteur ansembed →
    master.m3u8 sur vmnow.online).
- **V2** (livré) : **téléchargement hors-ligne** (media3 DownloadService).
  - Un bouton de téléchargement par épisode (icône flèche, puis
    progression % en direct dans la liste, ✓ vert quand c'est prêt).
  - **Meilleure qualité automatique** : la plus haute résolution de la
    master playlist (une seule variante téléchargée, pas quatre).
  - **Progression en notification** (canal dédié, mise à jour 1 s),
    service foreground `dataSync`, un téléchargement à la fois.
  - **Reprise automatique** : index persisté (SQLite media3) + en-têtes
    Referer/Origin par hôte persistés (SharedPreferences) — un
    téléchargement interrompu (réseau, redémarrage du téléphone) repart
    où il s'était arrêté à la réouverture de l'appli.
  - **Stockage privé** `Android/data/dot.jcversa.novay/files/offline`
    (cache media3 SimpleCache) : zéro permission de stockage, invisible
    des autres applis ; suppression depuis l'écran Téléchargements.
  - **Lecture hors-ligne** : un épisode téléchargé se lit depuis le cache
    sans aucune requête réseau (CacheDataSource, cache d'abord, réseau en
    repli silencieux).
  - **Écran Téléchargements** (icône en haut de la recherche) : progression
    par épisode, lecture, suppression.
- **V2.1** (livré) : **interface**.
  - **Affiches réelles** du catalogue (`asn-search-result-img`, src puis
    data-src — les placeholders `data:` 1×1 du lazy-loading sont ignorés) :
    miniatures de recherche + bannière pleine largeur sur la fiche anime
    (dégradé sombre, sous-titre lisible). Coil 2.7, chaque image signée
    UA + Referer de son origine (anti-hotlink).
  - **Écran d'accueil** : « Reprendre la lecture » (historique persisté,
    une entrée par épisode, lienIds + jeton conservés pour reprendre sans
    repasser par la recherche — le jeton expiré est rafraîchi par
    SamaSource), téléchargements en cours avec progression, compteur
    d'épisodes prêts hors-ligne.
  - **Lecteur plein écran** : paysage automatique, barres système
    masquées (immersion), contrôles Compose maison (lecture/pause, barre
    de progression seekable, temps), double-tap ±10 s (moitié
    gauche/droite), qualités dans un bouton réglages.
  - **Barre de navigation basse** : Accueil / Recherche /
    Téléchargements (la barre disparaît pendant la lecture).
- **V2.2** (livré) : **lecteur niveau NextPlayer** (analyse du projet
  anilbeesetti/nextplayer — GPL-3.0 : comportements seulement, aucun code
  copié).
  - Fix bloquant : la rotation vidait la pile de navigation (le lecteur
    « ne s'ouvrait pas ») — `configChanges` complets + pile au niveau
    processus.
  - Fix bloquant (retour terrain) : contrôles masqués impossibles à faire
    revenir — deux couches de gestes empilées s'étouffaient mutuellement
    (le test d'atteinte Compose s'arrête au frère du dessus) ; tous les
    gestes vivent maintenant dans UNE couche (machine à états : tap,
    double-tap, appui long, glissé H/V).
  - Glissé horizontal = seek progressif (50 ms/px, la vidéo suit le
    doigt), double-tap cumulatif (+10 s, +20 s…), indicateur delta +
    temps cible.
  - Glissé vertical : luminosité (gauche) / volume (droite).
  - Reprise à la position exacte (enregistrée toutes les 5 s + à la
    fermeture ; épisode fini = reprise du début).
  - Audio focus + pause quand le casque se débranche.
  - Vitesse de lecture (0,5× à 2×) + appui long = 2× le temps du doigt.
  - Bouton rotation paysage/portrait en mode capteur.
  - Verrouillage des contrôles (cadenas).
- **V3** : autres catalogues.

La sonde réseau CI (job « Sonde anime-sama », push avec `[probe]` dans le
message) re-vérifie le domaine et la structure à la demande.

## Build
Le sandbox n'a pas de SDK Android : **GitHub Actions compile l'APK**
(workflow « NoVaY (Kotlin) », artifact `novay-release-apk`), signé avec la
clé debug (keystore stable en cache CI) — installable directement, les
mises à jour s'installent par-dessus sans désinstallation.

Build local éventuel : Gradle 9.3.1 + JDK 17, puis
`gradle :app:assembleRelease` (créer `novay/debug.keystore` au préalable,
cf. workflow).
