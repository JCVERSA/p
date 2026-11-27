FullscreenVideoFitUiTest.kt.disabled
------------------------------------
Test UI Robolectric de l'owner (cadrage video plein ecran : Fit centre,
sans rognage ni etirement, ratios 16:9 / 4:3 / 9:16 / cinemascope).

Desactive : la config de tests unitaires de ce repo (AGP 9, JDK 17,
Robolectric 4.18) ne resout pas l'activite lanzable exigee par
createAndroidComposeRule sous Robolectric — le manifest dedie aux tests
n'est pas pris en compte. Trois tentatives documentees dans l'historique
(dependances, sdk 35 pour JDK 17, regle ComponentActivity + manifest).

Pour le reactiver : CI en JDK 21 + fusion du manifest de test, ou le
scaffold gradle d'origine du repo JCVERSA/novay.
