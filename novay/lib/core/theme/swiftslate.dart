import 'package:flutter/material.dart';

/// SwiftSlate pour NoVaY — réinterprétation Flutter de l'ADN visuel de
/// l'appli panel (fond ardoise profond, accents lumineux, formes squircle,
/// morphing doux). Toutes les valeurs de marque vivent ICI : changer
/// l'identité = toucher ce fichier uniquement.
abstract final class SwiftSlate {
  // ── Palette ──────────────────────────────────────────────────────────
  /// Fond principal (ardoise presque noire).
  static const Color bg = Color(0xFF0B0E14);
  /// Surface des cartes (un cran au-dessus du fond).
  static const Color surface = Color(0xFF121722);
  /// Surface élevée (dialogs, lecteur).
  static const Color surfaceHigh = Color(0xFF1A2130);
  /// Contours discrets.
  static const Color stroke = Color(0xFF232B3B);
  /// Accent principal — le « N » de NoVaY.
  static const Color accent = Color(0xFF6C8CFF);
  /// Accent secondaire (VOSTFR, badges).
  static const Color accent2 = Color(0xFF49D0A6);
  /// Texte principal / secondaire / tertiaire.
  static const Color text = Color(0xFFE8ECF5);
  static const Color textDim = Color(0xFF9AA5B8);
  static const Color textFaint = Color(0xFF5C6575);

  // ── Rayons (squircle partout) ───────────────────────────────────────
  static const double radiusCard = 22;
  static const double radiusButton = 18;
  static const double radiusTile = 14;
  static const double radiusPill = 999;

  // ── Motion (le cœur de l'identité) ──────────────────────────────────
  /// Durées standard : rapide (micro-feedback), normale (transitions),
  /// lente (apparitions de pages).
  static const Duration fast = Duration(milliseconds: 180);
  static const Duration normal = Duration(milliseconds: 320);
  static const Duration slow = Duration(milliseconds: 560);

  /// Courbe signature : démarre vif, atterrit en douceur.
  static const Curve ease = Curves.easeOutCubic;
  /// Courbe « ressort » pour les morphings et le dock magnétique.
  static const Curve spring = Curves.easeOutBack;

  // ── Thème complet ────────────────────────────────────────────────────
  static ThemeData theme() {
    final base = ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: const ColorScheme.dark(
        surface: surface,
        primary: accent,
        secondary: accent2,
      ),
      scaffoldBackgroundColor: bg,
      splashFactory: InkSparkle.splashFactory,
    );
    return base.copyWith(
      textTheme: base.textTheme.apply(
        bodyColor: text,
        displayColor: text,
      ),
    );
  }
}
