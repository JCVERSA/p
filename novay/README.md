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
- **V0** : thème noir+ambre, motion SwiftSlate, flux
  Recherche → Fiche → Épisodes → Lecteur en squelettes cliquables. ✔
- **V1** (livré) : catalogue voiranime — recherche (double mode Madara +
  complétion de franchises), saisons regroupées par anime (VF/VOSTFR),
  épisodes, streaming HLS (media3/ExoPlayer) avec en-têtes Referer/UA et
  les VRAIES résolutions lues de la master playlist. Parseurs portés 1:1
  de `src/bot/services/voiranimeClient.ts` + sonde Voe de
  `animeStreamExtractor.ts` (rot13 + double base64 + ord-3 + reverse),
  fixtures de test du bot reprises en JUnit.
- **V2** : téléchargement sur stockage.
- **V3** : autres catalogues (anime-sama, jetanimes…).

## Build
Le sandbox n'a pas de SDK Android : **GitHub Actions compile l'APK**
(workflow « NoVaY (Kotlin) », artifact `novay-release-apk`), signé avec la
clé debug (keystore stable en cache CI) — installable directement, les
mises à jour s'installent par-dessus sans désinstallation.

Build local éventuel : Gradle 9.3.1 + JDK 17, puis
`gradle :app:assembleRelease` (créer `novay/debug.keystore` au préalable,
cf. workflow).
