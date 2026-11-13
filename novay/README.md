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
- **V2** : téléchargement sur stockage.
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
