package dot.jcversa.novay.ui.theme

import androidx.compose.ui.graphics.Color

// NoVaY — tokens couleur SwiftSlate.
// Héritage : panel web/Android 9.18vv (décision owner 16/10 — identité
// unifiée noir + ambre). Ramp calquée sur le panel : #0b0b0c (page),
// #0e0e11 (cartes), #18181b (surélevé), zinc pour les textes, ambre 500
// en accent. Les noms historiques trompeurs du panel (CyanNeon devenu
// ambre) sont nettoyés ici : nouvelle base, noms vrais.
val DarkBackground = Color(0xFF0B0B0C)
val DarkSurface = Color(0xFF0E0E11)
val DarkSurfaceVariant = Color(0xFF18181B)
val DarkSurfaceCard = Color(0xFF141416)
val DarkCardBorder = Color(0xFF26262B)

val Amber500 = Color(0xFFF59E0B) // accent principal — le « N » de NoVaY
val AmberGlow = Color(0x33F59E0B) // halo ambre 20 %
val Amber400 = Color(0xFFFBBF24) // actions secondaires
val Amber300 = Color(0xFFFCD34D) // accents doux

val MintGreen = Color(0xFF34D399) // succès / VOSTFR
val MintGlow = Color(0x3334D399)
val CoralRed = Color(0xFFFB7185) // erreurs
val AmberWarning = Color(0xFFFBBF24)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFFA1A1AA) // zinc-400, comme le panel
val TextMuted = Color(0xFF71717A) // zinc-500
