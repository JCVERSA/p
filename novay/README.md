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
- **V2.3** (livré) : **téléchargements niveau gestionnaire de fichiers**
  (analyses AlirezaJavan/Downpour Apache-2.0, Jaglavak/…/Ketch et
  simtop/TurboDownloader sans licence : comportements seulement, aucun
  code copié — seul SpeedMeter est un portage crédité de Downpour).
  - **La résolution, c'est toi qui la choisis** : le tap sur l'icône de
    téléchargement analyse tous les lecteurs de l'épisode et propose
    CHAQUE résolution disponible — aucune présélection, c'est le tap qui
    lance. Pas de plafond imposé par l'appli.
  - **Multi-sélection** : appui long sur un épisode → mode sélection
    (bouton « Tout », barre « Télécharger (n) »). Le dialog de résolution
    s'applique au lot ; si une résolution manque pour un épisode, la
    meilleure de son lecteur est prise. Mise en file séquentielle avec
    progression « mise en file 2/5 ».
  - **Vitesse + temps restant** : SpeedMeter à fenêtre glissante (5 s,
    lissage) — « 45 % · 2,4 Mo/s · 3min 05s » dans l'écran
    Téléchargements et progression de saison « 3/12 téléchargés ».
  - **Notification pilotable** : boutons Pause / Reprendre / Annuler
    directement dans le tiroir de notifications.
  - **Wi-Fi uniquement** (toggle dans Téléchargements) : pause auto en
    4G, reprise auto en Wi-Fi, préférence persistée.
  - **Raisons de pause explicites** : « En attente du Wi-Fi » vs « En
    pause » vs « En file ».
- **V2.4** (livré) : **interface Mihon** — le design system complet de
  mihonapp/mihon (fork de Tachiyomi, Apache-2.0) porté sur nos moteurs.
  - **Look Tachiyomi exact** : schéma M3 complet (bleu #2979FF, fond
    #1B1B1F, rôles sémantiques : badge nouveaux = secondary, badge
    téléchargé = tertiary) + **thème AMOLED** (noir pur, toggle dans
    Plus → Réglages, bascule instantanée).
  - **5 onglets** : Bibliothèque / Mises à jour / Historique / Catalogue
    / Plus — barre M3 avec pastille de sélection, badge compteur,
    transitions fade-through entre onglets, état préservé par onglet.
  - **Bibliothèque** (nouveau) : suis des animés (cœur sur la fiche),
    grille compacte/confortable/liste, badges « nouveaux épisodes » et
    « téléchargés », bouton ▶ Continuer sur la carte, filtres VF/VOSTFR,
    multi-sélection pour retirer.
  - **Mises à jour** (nouveau) : « Vérifier » recompte les épisodes de
    chaque favori — les nouveaux arrivent en badge sur l'onglet et se
    consomment en ouvrant la saison.
  - **Historique** (nouveau) : groupé par jour, barre de progression par
    épisode, reprise directe, suppression unitaire ou globale.
  - **Fiche anime Mihon** : backdrop d'affiche flouté (4 dp, alpha 0,2)
    qui fond dans le fond, rangée d'actions cœur, FAB « Continuer » qui
    se rétracte au défilement, saisons en lignes chapitres (VF d'abord,
    VOSTFR ensuite — en-têtes épinglés).
  - **File de téléchargements Mihon** : sections par anime avec « x/y »,
    FAB pause/reprise globale, pill compteur — moteurs V2.3 (vitesse,
    ETA, Wi-Fi uniquement) intacts derrière.
  - **Épisodes style chapitres** : indicateur de téléchargement par item
    (cercle → sablier → anneau de progression → coche → erreur),
    « Reprise à mm:ss » des épisodes en cours.
  - Le lecteur vidéo garde SES gestes V2.2 — seul l'accent passe au bleu
    Tachiyomi.
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
