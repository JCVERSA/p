import 'package:flutter/material.dart';

import 'core/theme/swiftslate.dart';
import 'ui/screens/episodes_screen.dart';
import 'ui/screens/player_screen.dart';
import 'ui/screens/search_screen.dart';
import 'ui/screens/show_screen.dart';

/// Coque de navigation NoVaY (V0) : flux linéaire
/// Recherche → Fiche → Épisodes → Lecteur.
///
/// V1 remplacera ces squelettes par les vrais écrans ; la structure de
/// navigation (hero transitions, dock magnétique) se branchera ici.
class AppShell extends StatelessWidget {
  const AppShell({super.key});

  /// Transition signature : fondu + glissé vertical léger, courbe ease.
  void _push(BuildContext context, Widget screen) {
    Navigator.of(context).push(
      PageRouteBuilder(
        transitionDuration: SwiftSlate.normal,
        reverseTransitionDuration: SwiftSlate.fast,
        pageBuilder: (_, __, ___) => screen,
        transitionsBuilder: (_, animation, __, child) {
          final curved =
              CurvedAnimation(parent: animation, curve: SwiftSlate.ease);
          return FadeTransition(
            opacity: curved,
            child: SlideTransition(
              position: Tween(
                begin: const Offset(0, 0.03),
                end: Offset.zero,
              ).animate(curved),
              child: child,
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SearchScreen(
        onOpenShow: () => _push(
          context,
          ShowScreenPlaceholder(
            onOpenEpisodes: () => _push(
              context,
              EpisodesScreenPlaceholder(
                onOpenPlayer: () =>
                    _push(context, const PlayerScreenPlaceholder()),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
