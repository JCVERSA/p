# Audit complet NoVaY — V2.9.4

- **Date** : 15/11/2026 (après livraison V2.9.4, run CI 43672664840 VERT, 184 tests)
- **Version auditée** : commit `68e974f` (écran noir VLC corrigé — attachement surface sur le thread principal)
- **Périmètre** : `novay/` uniquement (module Android). 20 113 lignes de Kotlin (main), 25 fichiers de test (dont 1 désactivé). Les outils bot à la racine du repo sont hors périmètre.
- **Méthode** : lecture statique intégrale des couches data/downloads/player + inspection ciblée des écrans, manifeste, Gradle, CI ; recoupement avec l'historique git (V2.5 → V2.9.4) et les retours owner sur appareil (Android 7, 32 bits). Aucune modification de code.
- **Sévérités** : P0 bloqueur → P4 cosmétique. Statut : CONFIRMÉ (preuve lue), SUSPECTÉ (risque réel non reproduit), ÉCARTÉ (vérifié sans objet), ASSUMÉ (choix documenté).

---

## 1. Synthèse

| Volet | P1 | P2 | P3 | P4 | OK/écartés |
|---|---|---|---|---|---|
| Stabilité | 0 | 1 | 1 | 0 | 4 |
| Bugs logiques | 0 | 0 | 0 | 3 | 1 |
| Performance | 0 | 0 | 3 | 1 | 7 |
| Sécurité / vie privée | 0 | 0 | 1 | 1 | 3 |
| Compatibilité / livraison | 0 | 0 | 1 | 1 | 2 |
| Qualité / dette | 0 | 1 | 1 | 3 | 1 |

**Aucun P0, aucun P1.** L'app est saine : pas de crash connu restant, fuites maîtrisées, cycle de vie du lecteur et des services correct. Les V2.9.1→V2.9.4 ont laissé l'app **plus solide que la V2.8** (Throwable rattrapés, processus isolé, garde anti-boucle, watchdog de rendu).

**Top 5 des actions recommandées (aucune urgente, toutes compatibles entre elles)** :

1. **C-01 — versionning** (effort S) : `versionCode=1` / `versionName="0.1.0"` depuis V0 alors que l'app est en V2.9.4.
2. **Q-01 — déduplication de l'overlay lecteur** (effort M) : ~1 040 lignes dupliquées, toute correction doit être faite deux fois.
3. **S-01 — verrou sur la liste des tâches** (effort S) : dernier risque théorique de crash.
4. **P-05 — clés de listes** (effort S) : `items()` sans `key` dans 8 listes sur 10.
5. **P-03 — polices sous-ensemblées** (effort S) : 1,5 Mo de TTF pour ~0,4 Mo utiles.

---

## 2. Stabilité

### S-01 · Liste des tâches partagée sans verrou — P2, SUSPECTÉ
**Preuve** : `FileDownloadEngine.kt` — `tasksInternal` est une `mutableListOf` lue/modifiée depuis le thread principal (`enqueue`, `cancel`, `pauseAll`, `resumeAll`, `Downloads.remove`) ET depuis la coroutine IO de la boucle `pump()` (`firstOrNull`, `forEach`), ainsi que par le callback réseau `watchNetwork()`. Seuls `init`/`tasks`/`find` sont `@Synchronized`.
**Impact** : `ConcurrentModificationException` théorique si l'utilisateur annule une tâche pile pendant un parcours de la file. Jamais reproduit (fenêtre minuscule), mais c'est le seul chemin de crash résiduel identifié.
**Correctif** : `CopyOnWriteArrayList` ou étendre le verrou existant à `enqueue`/`cancel`/`pauseAll`/`resumeAll`/`pump`. Effort S, testable JVM.

### S-02 · Boucle de progression non coupée à la fermeture du lecteur — P3, CONFIRMÉ
**Preuve** : `PlayerStateManager.closeAndStop()` (ligne ~910) annule `loadJob` et `recoveryJob` mais pas `progressJob` ; seul `release()` (fin de vie de l'app, `NoVaYApp` DisposableEffect) l'annule.
**Impact** : après fermeture du lecteur, une coroutine poll tous les 500 ms pour toujours (inoffensive — retombe sur `delay(500)` sans moteur — mais tourne inutilement, y compris pendant le mini-player où elle est en revanche **utile** : à pondérer).
**Correctif** : annuler `progressJob` dans `closeAndStop()` et le relancer au retour du mini-player. Effort S.

### S-03 · Instances LibVLC/MediaPlayer à vie d'app — ÉCARTÉ
Singleton `LibVLC` (chargement natif unique) et `MediaPlayer` réutilisé : voulu (réouverture instantanée). Le descripteur de fichier `content://` est bien fermé à l'arrêt (`closeHeldFileDescriptor`), les vues détachées. Sans objet.

### S-04 · Service de téléchargement — OK
`NovayDownloadService` : `startForeground()` TOUJOURS appelé en premier (pas de `RemoteServiceException`), `onTimeout` Android 15+ géré (pause silencieuse + arrêt), `START_NOT_STICKY` cohérent, arrêt quand rien à faire mais notification conservée en pause. Testé JVM (`NovayDownloadServiceTimeoutTest`). Exemplaire.

### S-05 · Robustesse de finalisation — OK
`runTask` rattrape `Throwable` (V2.9.1), la sonde codec est isolée dans le processus `:probe` (V2.9.3), la garde `interruptedStateOnLoad` empêche la re-finalisation en boucle (5 tests JVM). Les trois leçons du crash V2.9 sont verrouillées par des tests.

---

## 3. Bugs logiques

### B-01 · Ligne arithmétiquement morte — P4, CONFIRMÉ
**Preuve** : `FileDownloadEngine.downloadSegments()` :
`task.bytesTotal = (task.bytesDone * (i + 1) / (i + 1)).coerceAtLeast(task.bytesDone)` — la multiplication/division par `(i+1)` s'annule ; la ligne équivaut à `bytesTotal = bytesDone`, valeur immédiatement écrasée par `estimateTotal()` deux lignes plus bas.
**Correctif** : supprimer la ligne. Effort S.

### B-02 · MediaStore insère sans dédoublonnage (Android 10+) — P4, CONFIRMÉ
**Preuve** : `writePublishedFile()` branche Q+ fait un `insert` direct ; la branche 7-9, elle, utilise `HlsPlaylist.availableFileName()` (anti-collision). 
**Impact** : re-télécharger un épisode dont le fichier publié a été supprimé depuis un autre écran (pas depuis l'app, qui supprime proprement) produit `Épisode (1).ts` au lieu d'écraser. Cosmétique.
**Correctif** : requête `DISPLAY_NAME = ?` avant insertion. Effort S.

### B-03 · Quatre `!!` défensifs — P4, CONFIRMÉ
`EpisodesScreen:425`, `ShowScreen:261`, `ShowScreen:465` (pattern `when { x == null -> …; x!!.… }` — sûr mais fragile au moindre remaniement) et `VoiranimeParsers:187` (gardé par `explicit`, vérifié sûr). Aucun plantage possible aujourd'hui.
**Correctif** : variables locales immuables pour smart-cast. Effort S, pure hygiène.

### B-04 · Reprise et états interrompus — OK
Reprise à l'octet près (`segmentsDone` + fichiers `seg_%05d.bin`), `init.bin` retéléchargé si reprise à zéro, persistance tous les 10 segments, états morts au chargement testés (5 tests). Solide.

---

## 4. Performance (mise à jour de PERFORMANCE.md)

### P-01 (ex F-01) · Boucle de progression — P3, PARTIELLEMENT CORRIGÉ
Voir S-02 : `release()` annule, `closeAndStop()` non. Reste à couper.

### P-02 (ex F-03) · Timeouts réseau — P4, ÉCARTÉ
`SamaSource.defaultClient()` : connect 12 s / read 12 s — présents et raisonnables ; `callTimeout` absent mais `readTimeout` couvre les lectures suspendues. Le point F-03 de l'audit précédent est sans objet en l'état.

### P-03 (ex F-02) · Polices non sous-ensemblées — P3, CONFIRMÉ
**Preuve** : `res/font/` = 11 TTF, 1,5 Mo (Inter ×4, JetBrains Mono ×3, Outfit ×4, fichiers de 32 à 326 Ko — latin complet, glyphes inutilisés inclus).
**Correctif** : sous-ensemblage `fonttools` (pyftsubset, latin+ponctuation) en CI → ~0,4 Mo attendus. Effort S.

### P-04 (ex F-04) · Fetchs séquentiels — P3, SUSPECTÉ (à re-vérifier)
Point signalé dans PERFORMANCE.md (double fetch consécutif à l'ouverture d'une saison). Non re-vérifié ce jour — à confirmer sur `ShowScreen` avant action.

### P-05 · Listes paresseuses sans clés — P3, CONFIRMÉ
**Preuve** : 2 `items(` avec `key =` sur ~10 dans les écrans (`BrowseScreen` 505/628/716, `BrowseSuggestionsContent` 117/147…).
**Impact** : recompositions inutiles au scroll, positions non stabilisées au rafraîchissement des catalogues.
**Correctif** : `key = { it.id }` partout. Effort S, gain immédiat sur liste longue.

### P-06 · Release non minifiée — P3, CONFIRMÉ
`isMinifyEnabled = false` (`build.gradle.kts`). L'APK (50,94 Mo) est dominé par `libvlc.so` armeabi-v7a (~40 Mo) que R8 ne touche pas ; le gain resterait sur le dex et les ressources (2-4 Mo estimés).
**Correctif** : activer R8 + règles de conservation (libVLC/media3/Coil), tester la lecture ET le téléchargement sur appareil avant adoption. Effort M. **Optionnel** — ne justifie pas le risque seul.

### Points forts vérifiés (aucune action)
Caches LRU-TTL sur toutes les sources (`SamaSource` lignes 97-101) ; debounce de recherche ; `NovayApp` ImageLoader Coil configuré main 22 % + disque 64 Mo + `rgb565` (précieux en 32 bits) ; regex des parseurs compilées une fois (propriétés top-level) ; `SpeedMeter` lissé ; entrelacement des pistes non applicable (plus de remux) ; `movableContent` et `remember` globalement bien employés.

---

## 5. Sécurité et vie privée

- **SEC-01 OK** — permissions minimales et conditionnelles : `WRITE_EXTERNAL_STORAGE` bornée `maxSdkVersion=28`, `POST_NOTIFICATIONS` demandée à l'exécution au bon moment (`EpisodesScreen:263-266`), stockage 7-9 vérifié avant chaque tâche avec message clair.
- **SEC-02 OK** — seule `MainActivity` est exportée (launcher) ; services `exported=false` ; `allowBackup=false` ; aucun `http://` en dur ; aucun secret dans le module.
- **SEC-03 P3 ASSUMÉ** — release signée avec la clé debug (choix V0 documenté : installation par-dessus stable pour l'owner et ses proches, pas de Play Store). **Ne pas changer la clé sans le dire à l'owner** : un changement force la désinstallation (perte des données). Rester tel quel tant que la distribution reste sideload.
- **SEC-04 P4** — `ComponentActivity` déclarée dans le manifeste principal (contournement documenté pour les tests Robolectric sous AGP 9). Non exportée, invisible du lanceur, surface nulle. Acceptable en l'état.

---

## 6. Compatibilité et livraison

### C-01 · versionCode/versionName jamais incrémentés — P3, CONFIRMÉ
**Preuve** : `build.gradle.kts` : `versionCode = 1`, `versionName = "0.1.0"` — inchangés depuis V0 alors que l'app est en V2.9.4 (14+ livraisons).
**Impact** : les installations par-dessus fonctionnent (même versionCode, même signature), mais aucun suivi de version côté système, `UpdatesScreen` ne peut pas comparer, et une future mise à jour série devra de toute façon passer par un vrai versioning.
**Correctif** : `versionCode = 29` (ou 294), `versionName = "2.9.4"`. Effort S. **Recommandé avant toute nouvelle fonctionnalité.**

### C-02 OK — minSdk 24 respecté : gardes `Build.VERSION` vérifiées sur les trois points sensibles (stockage, focus audio, foreground service type). 
### C-03 P4 — pas de `largeHeap` (défaut). Le décodage VLC alloue hors tas Java ; le point P8 du backlog (OOM potentiels 32 bits) n'est pas reproduit depuis la sortie `.ts` brute (moins de pression). À surveiller, ne rien changer sans reproduction.

---

## 7. Qualité et dette

### Q-01 · Overlay lecteur dupliqué — P2, CONFIRMÉ
**Preuve** : `PlayerControlsOverlay.kt` (1 409 lignes) contient DEUX vues quasi identiques : `EmbeddedVideoPlayer` (lignes 133-651) et `FullscreenPlayerOverlay` (652-1228) — ~520 lignes chacune.
**Impact** : toute correction doit être portée aux deux endroits. C'est arrivé exactement comme prévu pendant V2.9.2 (icône play/pause corrigée aux DEUX endroits — la moitié oubliée aurait laissé le bug en plein écran). Risque de divergence permanent.
**Correctif** : extraire un composant unique paramétré (mode embarqué/plein écran). Effort M/L, à faire pendant une fenêtre calme, pas en même temps qu'une autre modif lecteur.

### Q-02 · Pas de lint en CI — P3, CONFIRMÉ
La CI (novay.yml) exécute tests unitaires + build + tailles, mais jamais `lint` ni de détecteur statique.
**Correctif** : étape `gradle :app:lintDebug` non bloquante d'abord (rapport en artifact), bloquante quand propre. Effort S.

### Q-03 P4 — `FullscreenVideoFitUiTest.kt.disabled` : un test UI désactivé (Robolectric). Dette à reprendre ou supprimer.
### Q-04 P4 — chaînes FR en dur (pas de `values-*`) : assumé mono-langue, aucune action.
### Q-05 OK — la dette est ailleurs très faible : commentaires à jour (même les leçons V2.9), pas de `GlobalScope`/`runBlocking`/`Thread.sleep` dans l'app, cycle de vie des joueurs propre (`DisposableEffect` → `release()`).

---

## 8. Cartographie des tests (184 verts)

**Couverts** : parseurs sama (2 suites + perf) et voiranime (fixtures du bot) ; historique/bibliothèque/historique de recherche/choix de téléchargement ; playlist HLS ; métadonnées d'épisode (+ sérialisation locale) ; garde d'interruption moteur (5) ; badge codec ; watchdog de rendu VLC (4) ; timeout du service ; géométrie/gestes du lecteur ; SpeedMeter ; recovery ExoPlayer.

**Trous assumés** : orchestration `PlayerStateManager` (seul `PlayerRecoveryTest` l'effleure) ; écrans Compose (aucun test UI actif — 1 désactivé) ; boucle de segments elle-même (reprise couverte indirectement) ; `Downloads.kt` (notifications).

**Priorité de comblement** : S-01 (verrou) et B-02 (dédoublonnage) sont les deux corrections qui méritent d'apporter leur test avec elles.

---

## 9. Décisions proposées au owner (aucune exécutée)

| # | Finding | Effort | Recommandation |
|---|---|---|---|
| 1 | C-01 versionning | S | OUI, avant tout le reste |
| 2 | S-01 verrou tâches + test | S | OUI |
| 3 | P-05 clés LazyColumn | S | OUI |
| 4 | B-01 ligne morte | S | OUI (trivial) |
| 5 | P-03 polices sous-ensemblées | S | OUI si facile en CI |
| 6 | Q-02 lint CI | S | OUI, non bloquant |
| 7 | S-02 progressJob | S | OUI (avec attention mini-player) |
| 8 | B-02/B-03/Q-03 | S | lot d'hygiène optionnel |
| 9 | Q-01 déduplication overlay | M/L | quand fenêtre calme |
| 10 | P-06 R8 | M | optionnel, tester sur appareil |
| 11 | P-04 double fetch | S | à re-vérifier d'abord |

*Audit en lecture seule — aucune ligne de code modifiée. Rapport commité pour servir de référence aux prochains GO.*

---

## 10. Suivi V2.9.5 (lot correctif, GO owner « ok vasy »)

| # | Finding | Statut V2.9.5 |
|---|---|---|
| C-01 | versionning | ✅ CORRIGÉ — `versionCode 30`, `versionName "2.9.5"` |
| S-01 | verrou liste tâches | ✅ CORRIGÉ — `CopyOnWriteArrayList` |
| S-02 | boucle de progression | ✅ CORRIGÉ des deux côtés — annulée dans `closeAndStop()` ET démarrée dans `switchToVlc()` |
| B-01 | ligne arithmétiquement morte | ✅ CORRIGÉ — supprimée |
| B-02 | doublons MediaStore | ✅ CORRIGÉ — écrasement de l'entrée existante (`"wt"` troncature) |
| B-03 | `!!` défensifs | ✅ CORRIGÉ — 3 sites en smart-cast |
| P-03 | polices | ✅ CORRIGÉ — sous-ensemblées (1 503 → 627 Ko, **−856 Ko**), caractères requis vérifiés présents |
| Q-02 | lint en CI | ✅ CORRIGÉ — étape non bloquante (lint ressort PROPRE sur V2.9.5) ; l archivage du rapport HTML reste non résolu sous AGP 9 (chemin de sortie introuvable — les avertissements passent par le canal inline, fonctionnel) |
| Q-03 | test désactivé mort | ✅ CORRIGÉ — supprimé |
| **P-05** | clés de listes | ❌ **RÉTRACTÉ** — faux positif : le comptage grep de l'audit ne voyait pas les clés sur la ligne suivante. Re-verification exhaustive : **TOUTES** les listes des écrans ont une `key` (les deux `items()` sans clé sont des placeholders Shimmer sans identité — correct). Aucune action. |

### Découverte pendant le correctif S-02 (non vue par l'audit)
Une session **VLC pure** (lecture d'un fichier téléchargé) ne crée jamais d'ExoPlayer — or la boucle de progression (qui sauvegarde l'historique toutes les 5 s) n'était démarrée qu'à la création d'un ExoPlayer : **la position n'était sauvegardée qu'à la fermeture du lecteur** (perdue en cas de kill de l'app). Corrigé : `switchToVlc()` démarre maintenant la boucle.

### Restent en attente (fenêtre calme / GO ultérieur)
- **Q-01** déduplication de l'overlay lecteur (M/L)
- **P-06** minification R8 (M, à tester sur appareil)
- **P-04** double fetch à l'ouverture d'une saison (à re-vérifier d'abord)
- **SEC-03** clé debug release : assumé tant que distribution sideload
