import 'package:flutter/material.dart';

import '../../core/theme/shapes.dart';
import '../../core/theme/swiftslate.dart';
import '../widgets/squircle_card.dart';

/// Écran Épisodes (squelette V0). V1 : liste réelle + bouton lecture ET
/// téléchargement par épisode.
class EpisodesScreenPlaceholder extends StatelessWidget {
  const EpisodesScreenPlaceholder({super.key, required this.onOpenPlayer});

  final VoidCallback onOpenPlayer;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: SwiftSlate.bg,
        title: const Text('Kill Blue — Saison 1'),
      ),
      body: ListView.builder(
        padding: const EdgeInsets.all(20),
        itemCount: 12,
        itemBuilder: (context, index) => Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: SquircleCard(
            onTap: onOpenPlayer,
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  alignment: Alignment.center,
                  decoration: const ShapeDecoration(
                    color: SwiftSlate.surfaceHigh,
                    shape: SquircleBorder(radius: SwiftSlate.radiusTile),
                  ),
                  child: Text('${index + 1}', style: const TextStyle(fontWeight: FontWeight.w700)),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text('Épisode ${index + 1}', style: const TextStyle(fontWeight: FontWeight.w600)),
                ),
                const Icon(Icons.play_arrow_rounded, color: SwiftSlate.accent),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
