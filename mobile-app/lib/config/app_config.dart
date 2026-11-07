import 'dart:io';
import 'package:shared_preferences/shared_preferences.dart';

class AppConfig {
  static AppConfig? _instance;
  static AppConfig get instance => _instance ??= AppConfig._();

  AppConfig._();

  String _baseUrl = '';
  String get baseUrl => _baseUrl;

  String _token = '';
  String get token => _token;

  String _userName = '';
  String get userName => _userName;

  bool _isConfigured = false;
  bool get isConfigured => _isConfigured;

  bool _isLoggedIn = false;
  bool get isLoggedIn => _isLoggedIn;

  bool _rememberMe = false;
  bool get rememberMe => _rememberMe;

  String _savedUsername = '';
  String get savedUsername => _savedUsername;

  String _savedPassword = '';
  String get savedPassword => _savedPassword;

  String _connMode = 'gateway';
  String get connMode => _connMode;

  String _urlPrefix = 'http://';
  String get urlPrefix => _urlPrefix;

  String _ip = '';
  String get ip => _ip;

  String _port = '7000';
  String get port => _port;

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    _rememberMe = prefs.getBool('rememberMe') ?? false;
    _savedUsername = prefs.getString('savedUsername') ?? '';
    _savedPassword = prefs.getString('savedPassword') ?? '';
    _connMode = prefs.getString('connMode') ?? 'gateway';
    _urlPrefix = prefs.getString('urlPrefix') ?? 'http://';
    _ip = prefs.getString('ip') ?? 'localhost';
    _port = prefs.getString('port') ?? '7000';

    final saved = prefs.getString('baseUrl');
    if (saved != null && saved.isNotEmpty) {
      _baseUrl = saved;
      _isConfigured = true;
    } else {
      _baseUrl = await _detectLocalGateway();
      _isConfigured = false;
    }
    _token = prefs.getString('token') ?? '';
    _userName = prefs.getString('userName') ?? '';
    _isLoggedIn = _token.isNotEmpty;
  }

  String buildBaseUrl() {
    if (_ip.isEmpty) return _baseUrl;
    return '$_urlPrefix$_ip:$_port';
  }

  Future<void> saveServerConfig({
    required String connMode,
    required String urlPrefix,
    required String ip,
    required String port,
  }) async {
    _connMode = connMode;
    _urlPrefix = urlPrefix;
    _ip = ip;
    _port = port;
    _baseUrl = '$urlPrefix$ip:$port';
    _isConfigured = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('connMode', connMode);
    await prefs.setString('urlPrefix', urlPrefix);
    await prefs.setString('ip', ip);
    await prefs.setString('port', port);
    await prefs.setString('baseUrl', _baseUrl);
  }

  Future<void> setBaseUrl(String url) async {
    _baseUrl = url;
    _isConfigured = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('baseUrl', url);
  }

  Future<void> saveRememberMe(bool remember, {String? username, String? password}) async {
    _rememberMe = remember;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('rememberMe', remember);
    if (remember) {
      if (username != null) {
        _savedUsername = username;
        await prefs.setString('savedUsername', username);
      }
      if (password != null) {
        _savedPassword = password;
        await prefs.setString('savedPassword', password);
      }
    } else {
      _savedUsername = '';
      _savedPassword = '';
      await prefs.remove('savedUsername');
      await prefs.remove('savedPassword');
    }
  }

  Future<void> setLogin({required String token, required String userName}) async {
    _token = token;
    _userName = userName;
    _isLoggedIn = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('token', token);
    await prefs.setString('userName', userName);
  }

  Future<void> logout() async {
    _token = '';
    _userName = '';
    _isLoggedIn = false;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('token');
    await prefs.remove('userName');
  }

  Future<String> _detectLocalGateway() async {
    try {
      final interfaces = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
        includeLoopback: false,
      );
      for (final iface in interfaces) {
        for (final addr in iface.addresses) {
          final ip = addr.address;
          if (!ip.startsWith('169.254') && ip != '127.0.0.1') {
            return 'http://$ip:7000';
          }
        }
      }
    } catch (_) {}
    return 'http://10.0.2.2:7000';
  }
}