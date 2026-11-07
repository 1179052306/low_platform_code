import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'config/app_config.dart';
import 'pages/splash_page.dart';
import 'designer/designer_app.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppConfig.instance.init();
  if (kIsWeb) {
    final uri = Uri.base;
    final mode = uri.queryParameters['mode'];
    if (mode == 'designer') {
      runApp(MaterialApp(home: const DesignerApp(), debugShowCheckedModeBanner: false, theme: _appTheme));
      return;
    }
  }
  runApp(MaterialApp(
    home: SplashPage(),
    debugShowCheckedModeBanner: false,
    theme: _appTheme,
  ));
}

final _appTheme = ThemeData(
  useMaterial3: true,
  colorScheme: ColorScheme.fromSeed(
    seedColor: Color(0xFF1976D2),
    brightness: Brightness.light,
    surface: Color(0xFFFFFFFF),
  ),
  scaffoldBackgroundColor: Color(0xFFF0F2F5),
  appBarTheme: AppBarTheme(
    backgroundColor: Color(0xFFFFFFFF),
    foregroundColor: Color(0xFF303133),
    elevation: 0,
    centerTitle: false,
  ),
  cardTheme: CardThemeData(
    color: Colors.white,
    elevation: 1,
    margin: EdgeInsets.zero,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.all(Radius.circular(6)),
      side: BorderSide(color: Color(0xFFE4E7ED)),
    ),
  ),
  inputDecorationTheme: InputDecorationTheme(
    filled: true,
    fillColor: Color(0xFFF8FAFD),
    contentPadding: EdgeInsets.symmetric(horizontal: 14, vertical: 13),
    border: OutlineInputBorder(
      borderRadius: BorderRadius.all(Radius.circular(12)),
      borderSide: BorderSide(color: Color(0xFFDCDFE6)),
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.all(Radius.circular(12)),
      borderSide: BorderSide(color: Color(0xFFDCDFE6)),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.all(Radius.circular(12)),
      borderSide: BorderSide(color: Color(0xFF1976D2), width: 1.5),
    ),
  ),
);
