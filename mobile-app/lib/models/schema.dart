import 'dart:convert';

class PageSchema {
  String pageType;
  String pageCode;
  String pageName;
  SchemaNode root;

  PageSchema({
    required this.pageType,
    required this.pageCode,
    required this.pageName,
    required this.root,
  });

  factory PageSchema.fromJson(Map<String, dynamic> json) {
    return PageSchema(
      pageType: json['pageType'] ?? 'mobile',
      pageCode: json['pageCode'] ?? '',
      pageName: json['pageName'] ?? '',
      root: SchemaNode.fromJson(json['root'] ?? {}),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'pageType': pageType,
      'pageCode': pageCode,
      'pageName': pageName,
      'root': root.toJson(),
    };
  }
}

class SchemaNode {
  String id;
  String type;
  Map<String, dynamic> props;
  Map<String, dynamic> events;
  List<SchemaNode> children;
  SchemaNode? bottomNav;
  SchemaNode? fab;
  SchemaNode? appBar;
  double? x;
  double? y;
  double? width;
  double? height;

  SchemaNode({
    required this.id,
    required this.type,
    this.props = const {},
    this.events = const {},
    this.children = const [],
    this.bottomNav,
    this.fab,
    this.appBar,
    this.x,
    this.y,
    this.width,
    this.height,
  }) {
    props = Map<String, dynamic>.from(props);
    events = Map<String, dynamic>.from(events);
    children = List<SchemaNode>.from(children);
  }

  factory SchemaNode.fromJson(Map<String, dynamic> json) {
    return SchemaNode(
      id: json['id']?.toString() ?? '',
      type: json['type'] ?? 'column',
      props: Map<String, dynamic>.from(json['props'] ?? {}),
      events: json['events'] is Map
          ? Map<String, dynamic>.from(json['events'] as Map)
          : const {},
      children: (json['children'] as List?)
              ?.map((e) => SchemaNode.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      bottomNav: json['bottomNav'] != null
          ? SchemaNode.fromJson(json['bottomNav'] as Map<String, dynamic>)
          : null,
      fab: json['fab'] != null
          ? SchemaNode.fromJson(json['fab'] as Map<String, dynamic>)
          : null,
      appBar: json['appBar'] != null
          ? SchemaNode.fromJson(json['appBar'] as Map<String, dynamic>)
          : null,
      x: (json['x'] as num?)?.toDouble(),
      y: (json['y'] as num?)?.toDouble(),
      width: (json['width'] as num?)?.toDouble(),
      height: (json['height'] as num?)?.toDouble(),
    );
  }

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{
      'id': id,
      'type': type,
      'props': props,
      'children': children.map((c) => c.toJson()).toList(),
    };
    if (events.isNotEmpty) json['events'] = events;
    if (bottomNav != null) json['bottomNav'] = bottomNav!.toJson();
    if (fab != null) json['fab'] = fab!.toJson();
    if (appBar != null) json['appBar'] = appBar!.toJson();
    if (x != null) json['x'] = x;
    if (y != null) json['y'] = y;
    if (width != null) json['width'] = width;
    if (height != null) json['height'] = height;
    return json;
  }

  SchemaNode clone() {
    return SchemaNode.fromJson(toJson());
  }

  dynamic prop(String key, [dynamic defaultValue]) {
    return props[key] ?? defaultValue;
  }

  String propStr(String key, [String defaultValue = '']) {
    final v = props[key];
    return v?.toString() ?? defaultValue;
  }

  double propNum(String key, [double defaultValue = 0]) {
    final v = props[key];
    if (v == null) return defaultValue;
    if (v is num) return v.toDouble();
    return double.tryParse(v.toString()) ?? defaultValue;
  }

  bool propBool(String key, [bool defaultValue = false]) {
    final v = props[key];
    if (v == null) return defaultValue;
    if (v is bool) return v;
    return v.toString() == 'true';
  }

  List<Map<String, dynamic>> propList(String key) {
    final v = props[key];
    if (v == null) return [];
    if (v is List) return v.map((e) => Map<String, dynamic>.from(e as Map)).toList();
    return [];
  }

  List<dynamic> propValues(String key) {
    final value = props[key];
    return value is List ? List<dynamic>.from(value) : const [];
  }

  Map<String, dynamic> propMap(String key) {
    final v = props[key];
    if (v == null) return {};
    if (v is Map) return Map<String, dynamic>.from(v);
    if (v is String) {
      try {
        final decoded = jsonDecode(v);
        if (decoded is Map) return Map<String, dynamic>.from(decoded);
      } catch (_) {}
    }
    return {};
  }
}
