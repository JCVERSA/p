import 'package:flutter/material.dart';

import '../../core/theme/shapes.dart';
import '../../core/theme/swiftslate.dart';
import '../widgets/lang_pill.dart';
import '../widgets/squircle_card.dart';

/// Écran Fiche/Saisons (squelette V0). V1 : saisons réelles du catalogue,
/// sélecteur VF/VOSTFR.
class ShowScreenPlaceholder extends StatelessWidget {
  const ShowScreenPlaceholder({super.key, required this.onOpenEpisodes});

  final VoidCallback onOpenEpisodes;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: SwiftSlate.bg,
        title: const Text('Kill Blue'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Row(
            children: [
              Container(
                width: 96,
                height: 136,
                decoration: const ShapeDecoration(
                  color: SwiftSlate.surfaceHigh,
                  shape: SquircleBorder(radius: SwiftSlate.radiusTile),
                ),
                child: const Icon(Icons.movie, color: SwiftSlate.textFaint),
              ),
              const SizedBox(width: 16),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Kill Blue', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
                    SizedBox(height: 8),
                    Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: [LangPill(label: 'VF', vf: true), LangPill(label: 'VOSTFR')],
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          for (final season in const ['Saison 1', 'Saison 2'])
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: SquircleCard(
                onTap: onOpenEpisodes,
                child: Row(
                  children: [
                    Expanded(child: Text(season, style: const TextStyle(fontWeight: FontWeight.w600))),
                    const Icon(Icons.chevron_right, color: SwiftSlate.textFaint),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
