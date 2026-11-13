import 'package:flutter/material.dart';

import '../../core/theme/swiftslate.dart';

/// Écran Lecteur (squelette V0) : zone vidéo + sélecteur de qualité.
/// V1 : media_kit (HLS avec Referer/UA du catalogue) — les résolutions
/// viennent de la master playlist du lecteur.
class PlayerScreenPlaceholder extends StatelessWidget {
  const PlayerScreenPlaceholder({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        title: const Text('Épisode 1'),
      ),
      body: Column(
        children: [
          AspectRatio(
            aspectRatio: 16 / 9,
            child: Container(
              color: SwiftSlate.surfaceHigh,
              child: const Center(
                child: Icon(Icons.play_circle_fill, size: 64, color: SwiftSlate.accent),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Qualité (résolutions du lecteur)', style: TextStyle(color: SwiftSlate.textDim)),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final q in const ['360P', '480P', '720P', '1080P'])
                      Chip(label: Text(q), backgroundColor: SwiftSlate.surface),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
