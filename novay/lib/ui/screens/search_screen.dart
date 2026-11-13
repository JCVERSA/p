import 'package:flutter/material.dart';

import '../widgets/lang_pill.dart';
import '../widgets/squircle_card.dart';
import '../../core/theme/shapes.dart';
import '../../core/theme/swiftslate.dart';

/// Écran Recherche (squelette V0) : champ squircle + grille de résultats
/// factices pour valider le thème et le motion. V1 : vraie recherche
/// voiranime (dio + parseur).
class SearchScreen extends StatelessWidget {
  const SearchScreen({super.key, required this.onOpenShow});

  final VoidCallback onOpenShow;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: CustomScrollView(
        slivers: [
          const SliverPadding(
            padding: EdgeInsets.fromLTRB(20, 24, 20, 8),
            sliver: SliverToBoxAdapter(
              child: _Header(),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
            sliver: SliverToBoxAdapter(child: _SearchField()),
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            sliver: SliverGrid(
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 320,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
                mainAxisExtent: 92,
              ),
              delegate: SliverChildBuilderDelegate(
                (context, index) => _ResultTile(
                  title: _placeholders[index % _placeholders.length],
                  onTap: onOpenShow,
                ),
                childCount: _placeholders.length,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'NoVaY',
          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.w800,
                letterSpacing: -0.5,
              ),
        ),
        const SizedBox(height: 2),
        Text(
          'Animes VF & VOSTFR, depuis ton téléphone',
          style: TextStyle(color: SwiftSlate.textDim),
        ),
      ],
    );
  }
}

class _SearchField extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return TextField(
      decoration: InputDecoration(
        hintText: 'Rechercher un anime…',
        hintStyle: TextStyle(color: SwiftSlate.textFaint),
        prefixIcon: const Icon(Icons.search, color: SwiftSlate.textDim),
        filled: true,
        fillColor: SwiftSlate.surface,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(SwiftSlate.radiusButton),
          borderSide: const BorderSide(color: SwiftSlate.stroke),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(SwiftSlate.radiusButton),
          borderSide: const BorderSide(color: SwiftSlate.stroke),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(SwiftSlate.radiusButton),
          borderSide: const BorderSide(color: SwiftSlate.accent, width: 1.4),
        ),
      ),
    );
  }
}

const _placeholders = [
  'Kill Blue',
  'Solo Leveling',
  'The Apothecary Diaries',
  'Tokyo Revengers',
  'Ranma ½',
  'Jujutsu Kaisen',
];

class _ResultTile extends StatelessWidget {
  const _ResultTile({required this.title, required this.onTap});

  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SquircleCard(
      onTap: onTap,
      padding: const EdgeInsets.all(14),
      child: Row(
        children: [
          Container(
            width: 56,
            height: 64,
            decoration: const ShapeDecoration(
              color: SwiftSlate.surfaceHigh,
              shape: SquircleBorder(radius: SwiftSlate.radiusTile),
            ),
            child: const Icon(Icons.movie_outlined, color: SwiftSlate.textFaint),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 6),
                const Row(
                  children: [
                    LangPill(label: 'VF', vf: true),
                    SizedBox(width: 6),
                    LangPill(label: 'VOSTFR'),
                  ],
                ),
              ],
            ),
          ),
          const Icon(Icons.chevron_right, color: SwiftSlate.textFaint),
        ],
      ),
    );
  }
}
