import 'package:flutter/material.dart';

import '../../core/theme/shapes.dart';
import '../../core/theme/swiftslate.dart';

/// Carte SwiftSlate de base : surface squircle + contour discret +
/// micro-élévation au toucher (le « feel » signature).
class SquircleCard extends StatefulWidget {
  const SquircleCard({
    super.key,
    required this.child,
    this.onTap,
    this.padding = const EdgeInsets.all(16),
  });

  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry padding;

  @override
  State<SquircleCard> createState() => _SquircleCardState();
}

class _SquircleCardState extends State<SquircleCard> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapUp: (_) => setState(() => _pressed = false),
      onTapCancel: () => setState(() => _pressed = false),
      onTap: widget.onTap,
      child: AnimatedScale(
        scale: _pressed ? 0.97 : 1,
        duration: SwiftSlate.fast,
        curve: SwiftSlate.ease,
        child: AnimatedContainer(
          duration: SwiftSlate.fast,
          curve: SwiftSlate.ease,
          padding: widget.padding,
          decoration: ShapeDecoration(
            color: _pressed ? SwiftSlate.surfaceHigh : SwiftSlate.surface,
            shape: const SquircleBorder(
              side: BorderSide(color: SwiftSlate.stroke, width: 1),
              radius: SwiftSlate.radiusCard,
            ),
          ),
          child: widget.child,
        ),
      ),
    );
  }
}
