import 'package:flutter/material.dart';

import '../../core/theme/shapes.dart';
import '../../core/theme/swiftslate.dart';

/// Badge VF / VOSTFR (accent ou accent2 selon la langue).
class LangPill extends StatelessWidget {
  const LangPill({super.key, required this.label, this.vf = false});

  final String label;
  final bool vf;

  @override
  Widget build(BuildContext context) {
    final color = vf ? SwiftSlate.accent : SwiftSlate.accent2;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: ShapeDecoration(
        color: color.withValues(alpha: 0.14),
        shape: const SquircleBorder(radius: SwiftSlate.radiusPill),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 11,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.5,
        ),
      ),
    );
  }
}
