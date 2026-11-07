import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config/app_config.dart';

class ApiClient {
  static ApiClient? _instance;
  static ApiClient get instance => _instance ??= ApiClient._();

  ApiClient._();

  String get _base => AppConfig.instance.baseUrl;

  Map<String, String> get _headers {
    final h = <String, String>{'Content-Type': 'application/json'};
    final token = AppConfig.instance.token;
    if (token.isNotEmpty) h['Authorization'] = 'Bearer $token';
    return h;
  }

  Future<Map<String, dynamic>> get(String path, {Map<String, String>? query}) async {
    final uri = Uri.parse('$_base$path').replace(queryParameters: query);
    final resp = await http.get(uri, headers: _headers);
    return _parse(resp);
  }

  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> body) async {
    final uri = Uri.parse('$_base$path');
    final resp = await http.post(uri, headers: _headers, body: jsonEncode(body));
    return _parse(resp);
  }

  Future<Map<String, dynamic>> request(
    String method,
    String path, {
    Map<String, dynamic>? body,
  }) async {
    final uri = Uri.parse('$_base$path');
    final normalizedMethod = method.toUpperCase();
    final resp = switch (normalizedMethod) {
      'GET' => await http.get(uri, headers: _headers),
      'POST' => await http.post(uri, headers: _headers, body: jsonEncode(body ?? {})),
      'PUT' => await http.put(uri, headers: _headers, body: jsonEncode(body ?? {})),
      'DELETE' => await http.delete(uri, headers: _headers, body: jsonEncode(body ?? {})),
      _ => throw ArgumentError('Unsupported HTTP method: $method'),
    };
    return _parse(resp);
  }

  Map<String, dynamic> _parse(http.Response resp) {
    final json = jsonDecode(resp.body) as Map<String, dynamic>;
    return json;
  }

  Future<Map<String, dynamic>> login(String username, String password) async {
    return post('/api/auth/login', {'username': username, 'password': password});
  }

  Future<List<Map<String, dynamic>>> queryBiz(
    String bizCode, {
    int page = 1,
    int pageSize = 20,
    Map<String, dynamic>? filters,
  }) async {
    final result = await post('/api/lowcode/crud/$bizCode/query', {
      'pageNum': page,
      'pageSize': pageSize,
      if (filters != null) 'filters': filters,
    });
    final data = result['data'];
    if (data is Map) {
      final list = data['data'];
      if (list is List) {
        return list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      }
      if (list is Map) {
        final rows = list['data'];
        if (rows is List) {
          return rows.map((e) => Map<String, dynamic>.from(e as Map)).toList();
        }
      }
    }
    if (data is List) {
      return data.map((e) => Map<String, dynamic>.from(e as Map)).toList();
    }
    return [];
  }

  Future<Map<String, dynamic>> saveBiz(String bizCode, Map<String, dynamic> data) async {
    return post('/api/lowcode/crud/$bizCode/save', data);
  }

  Future<Map<String, dynamic>> deleteBiz(String bizCode, String id, String primaryKey) async {
    return post('/api/lowcode/crud/$bizCode/delete', {primaryKey: id});
  }
}
