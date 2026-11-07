import 'dart:html' as html;
import 'dart:convert';
import '../models/schema.dart';

void setupWebSchemaListener(void Function(PageSchema schema, bool editMode, String? selectedId) onSchema) {
  html.window.onMessage.listen((event) {
    final data = event.data;
    if (data is String) {
      try {
        final json = jsonDecode(data) as Map<String, dynamic>;
        if (json['type'] == 'render') {
          final schema = PageSchema.fromJson(json['schema'] as Map<String, dynamic>);
          final editMode = json['editMode'] == true;
          final selectedId = json['selectedId']?.toString();
          print('[flutter] received schema root=${schema.root.type} children=${schema.root.children.length} editMode=$editMode selectedId=$selectedId');
          onSchema(schema, editMode, selectedId);
        }
      } catch (e, s) {
        print('[flutter] schema parse error: $e');
        print('$s');
      }
    }
  });
  print('[flutter] web schema listener ready, sending ready to parent');
  html.window.parent?.postMessage('ready', '*');
}