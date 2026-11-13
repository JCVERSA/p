import 'package:flutter/material.dart';

/// Le squircle SwiftSlate — rectangle à coins « continus », signature
/// visuelle de NoVaY. Approximation du superellipse par arcs cubiques
/// symétriques (4 coins identiques, rayon visuellement constant).
class SquircleBorder extends ShapeBorder {
  const SquircleBorder({this.side = BorderSide.none, this.radius = 22});

  final BorderSide side;
  final double radius;

  /// Tension du coin : 0.55 ≈ cercle, plus grand = coin « carré » tendu
  /// (caractère squircle iOS-like).
  static const double _tension = 0.62;

  Path _path(Rect rect) {
    final r = radius.clamp(0.0, rect.shortestSide / 2);
    final k = r * _tension;
    final left = rect.left, top = rect.top;
    final right = rect.right, bottom = rect.bottom;
    return Path()
      ..moveTo(left + r, top)
      ..lineTo(right - r, top)
      ..cubicTo(right - r + k, top, right, top + r - k, right, top + r)
      ..lineTo(right, bottom - r)
      ..cubicTo(right, bottom - r + k, right - r + k, bottom, right - r, bottom)
      ..lineTo(left + r, bottom)
      ..cubicTo(left + r - k, bottom, left, bottom - r + k, left, bottom - r)
      ..lineTo(left, top + r)
      ..cubicTo(left, top + r - k, left + r - k, top, left + r, top)
      ..close();
  }

  @override
  Path getOuterPath(Rect rect, {TextDirection? textDirection}) => _path(rect);

  @override
  Path getInnerPath(Rect rect, {TextDirection? textDirection}) {
    return _path(rect.deflate(side.strokeWidth / 2));
  }

  @override
  void paint(Canvas canvas, Rect rect) {
    if (side.style == BorderStyle.solid && side.width > 0) {
      canvas.drawPath(
        getInnerPath(rect),
        side.toPaint()..style = PaintingStyle.stroke,
      );
    }
  }

  @override
  SquircleBorder scale(double t) {
    return SquircleBorder(side: side.scale(t), radius: radius * t);
  }

  @override
  ShapeBorder? lerpFrom(ShapeBorder? a, double t) {
    if (a is SquircleBorder) {
      return SquircleBorder(
        side: BorderSide.lerp(a.side, side, t),
        radius: _lerp(a.radius, radius, t),
      );
    }
    return super.lerpFrom(a, t);
  }

  @override
  ShapeBorder? lerpTo(ShapeBorder? b, double t) {
    if (b is SquircleBorder) {
      return SquircleBorder(
        side: BorderSide.lerp(side, b.side, t),
        radius: _lerp(radius, b.radius, t),
      );
    }
    return super.lerpTo(b, t);
  }
}

double _lerp(double a, double b, double t) => a + (b - a) * t;
