import '../models/schema.dart';
import 'api_client.dart';

class SchemaFetcher {
  static SchemaFetcher? _instance;
  static SchemaFetcher get instance => _instance ??= SchemaFetcher._();

  SchemaFetcher._();

  final _cache = <String, PageSchema>{};

  Future<PageSchema> fetch(String pageCode) async {
    if (_cache.containsKey(pageCode)) return _cache[pageCode]!;
    final json = await ApiClient.instance.get(
      '/api/lowcode/page/schema',
      query: {'code': pageCode},
    );
    final schemaJson = json['data'] ?? json;
    final schema = PageSchema.fromJson(schemaJson as Map<String, dynamic>);
    _cache[pageCode] = schema;
    return schema;
  }

  void clearCache() => _cache.clear();
  void evict(String pageCode) => _cache.remove(pageCode);
}