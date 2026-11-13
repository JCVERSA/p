import 'package:flutter/material.dart';

import 'app.dart';
import 'core/theme/swiftslate.dart';

void main() {
  runApp(const NoVaYApp());
}

/// Point d'entrée NoVaY. Le thème SwiftSlate vit dans core/theme.
class NoVaYApp extends StatelessWidget {
  const NoVaYApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'NoVaY',
      debugShowCheckedModeBanner: false,
      theme: SwiftSlate.theme(),
      home: const AppShell(),
    );
  }
}
