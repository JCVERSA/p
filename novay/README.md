# NoVaY

Appli Android (Flutter) de streaming et téléchargement d'animes VF/VOSTFR,
directement depuis le téléphone — sans serveur, sans WhatsApp.

## Identité
- **Stack** : Flutter (Dart) — choisie pour les animations, le layout
  personnalisé et le hot reload.
- **UI** : SwiftSlate réinterprété (`lib/core/theme/`).
- **Package** : `dot.jcversa.novay` — Android 7+ (minSdk 24), **32 bits
  inclus** (l'APK fat embarque armeabi-v7a + arm64-v8a + x86_64).

## Roadmap
- **V0** (ce scaffold) : projet, thème, navigation, 4 écrans squelettes.
- **V1** : catalogue voiranime (recherche → saisons → épisodes → streaming,
  résolutions du lecteur).
- **V2** : téléchargement sur stockage.
- **V3** : autres catalogues.

## Build
Le sandbox n'a pas le SDK Flutter : **GitHub Actions compile l'APK**
(workflow `NoVaY APK`) — artifact `novay-release-apk`, à installer directement.
