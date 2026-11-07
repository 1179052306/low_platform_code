import 'dart:convert';
import 'package:flutter/foundation.dart';
import '../models/schema.dart';
import 'component_registry.dart';

class NodeResult {
  final SchemaNode node;
  final SchemaNode? parent;
  final List<SchemaNode> list;
  final int index;
  NodeResult(this.node, this.parent, this.list, this.index);
}

NodeResult? findNode(SchemaNode root, String id) {
  if (root.id == id) return NodeResult(root, null, [root], 0);
  for (int i = 0; i < root.children.length; i++) {
    final child = root.children[i];
    if (child.id == id) return NodeResult(child, root, root.children, i);
    final found = findNode(child, id);
    if (found != null) return found;
  }
  return null;
}

SchemaNode? findNodeById(SchemaNode root, String id) => findNode(root, id)?.node;

String genId() => 'n_${DateTime.now().millisecondsSinceEpoch}_${(DateTime.now().microsecond % 1000).toString().padLeft(3, '0')}';

SchemaNode deepClone(SchemaNode node) => SchemaNode.fromJson(jsonDecode(jsonEncode(node.toJson())));

class DesignerState extends ChangeNotifier {
  SchemaNode _root;
  String _selectedId = '';
  final List<String> _history = [];
  int _historyIndex = -1;
  String? _clipboard;
  static const _maxHistory = 50;

  DesignerState(SchemaNode initial) : _root = initial {
    _pushHistory();
  }

  SchemaNode get root => _root;
  String get selectedId => _selectedId;
  bool get canUndo => _historyIndex > 0;
  bool get canRedo => _historyIndex < _history.length - 1;
  SchemaNode? get selectedNode => _selectedId.isEmpty ? null : findNodeById(_root, _selectedId);

  void loadSchema(SchemaNode root) {
    _root = root;
    _selectedId = '';
    _history.clear();
    _historyIndex = -1;
    _pushHistory();
    notifyListeners();
  }

  void selectNode(String id) {
    if (_selectedId != id) {
      _selectedId = id;
      notifyListeners();
    }
  }

  void clearSelection() {
    if (_selectedId.isNotEmpty) {
      _selectedId = '';
      notifyListeners();
    }
  }

  String addNode(String type, {String? parentId, double? x, double? y}) {
    final props = ComponentRegistry.defaultPropsFor(type);
    final isButton = type == 'm-button' || type == 'button' || type == 'm-icon-button' || type == 'm-social-button';
    final node = SchemaNode(
      id: genId(),
      type: type,
      props: props,
      x: x,
      y: y,
      width: isButton ? null : 200,
      height: isButton ? null : 60,
    );
    final targetId = parentId ?? _selectedId;
    SchemaNode target;
    if (targetId != null && targetId.isNotEmpty) {
      final found = findNodeById(_root, targetId);
      target = (found != null && ComponentRegistry.isContainer(found.type)) ? found : _root;
    } else {
      target = _root;
    }
    target.children.add(node);
    _pushHistory();
    _selectedId = node.id;
    notifyListeners();
    return node.id;
  }

  void removeSelected() {
    if (_selectedId.isEmpty) return;
    final result = findNode(_root, _selectedId);
    if (result == null || result.parent == null) return;
    result.list.removeAt(result.index);
    _selectedId = '';
    _pushHistory();
    notifyListeners();
  }

  void updateProp(String id, String key, dynamic value) {
    final node = findNodeById(_root, id);
    if (node == null) return;
    if (value == null || value == '') {
      node.props.remove(key);
    } else {
      node.props[key] = value;
    }
    _pushHistory();
    notifyListeners();
  }

  void updatePropSilent(String id, String key, dynamic value) {
    final node = findNodeById(_root, id);
    if (node == null) return;
    node.props[key] = value;
    notifyListeners();
  }

  void updatePosition(String id, double x, double y) {
    final node = findNodeById(_root, id);
    if (node == null) return;
    node.x = x;
    node.y = y;
    _pushHistory();
    notifyListeners();
  }

  void updatePositionSilent(String id, double x, double y) {
    final node = findNodeById(_root, id);
    if (node == null) return;
    node.x = x;
    node.y = y;
    notifyListeners();
  }

  void updateSize(String id, double? width, double? height) {
    final node = findNodeById(_root, id);
    if (node == null) return;
    node.width = width;
    node.height = height;
    _pushHistory();
    notifyListeners();
  }

  void updateSizeSilent(String id, double? width, double? height) {
    final node = findNodeById(_root, id);
    if (node == null) return;
    node.width = width;
    node.height = height;
    notifyListeners();
  }

  void moveNode(String id, String newParentId, int newIndex) {
    final result = findNode(_root, id);
    if (result == null || result.parent == null) return;
    final newParent = findNodeById(_root, newParentId);
    if (newParent == null) return;
    final node = result.list.removeAt(result.index);
    final clampedIndex = newIndex.clamp(0, newParent.children.length);
    newParent.children.insert(clampedIndex, node);
    _pushHistory();
    notifyListeners();
  }

  void copySelected() {
    if (_selectedId.isEmpty) return;
    final node = findNodeById(_root, _selectedId);
    if (node == null) return;
    _clipboard = jsonEncode(node.toJson());
  }

  void pasteSelected() {
    if (_clipboard == null) return;
    final targetId = _selectedId;
    try {
      final node = SchemaNode.fromJson(jsonDecode(_clipboard!));
      node.id = genId();
      final target = (targetId.isNotEmpty && (findNodeById(_root, targetId)?.type.startsWith('m-') ?? false))
          ? findNodeById(_root, targetId)!
          : _root;
      target.children.add(node);
      _selectedId = node.id;
      _pushHistory();
      notifyListeners();
    } catch (_) {}
  }

  void undo() {
    if (!canUndo) return;
    _historyIndex--;
    _root = SchemaNode.fromJson(jsonDecode(_history[_historyIndex]));
    notifyListeners();
  }

  void redo() {
    if (!canRedo) return;
    _historyIndex++;
    _root = SchemaNode.fromJson(jsonDecode(_history[_historyIndex]));
    notifyListeners();
  }

  void clearAll() {
    _root = SchemaNode(id: 'root', type: 'm-page-root', props: {'layout': 'vertical', 'gap': 8, 'padding': 0});
    _selectedId = '';
    _pushHistory();
    notifyListeners();
  }

  void _pushHistory() {
    final snapshot = jsonEncode(_root.toJson());
    if (_historyIndex < _history.length - 1) {
      _history.removeRange(_historyIndex + 1, _history.length);
    }
    _history.add(snapshot);
    if (_history.length > _maxHistory) {
      _history.removeAt(0);
    }
    _historyIndex = _history.length - 1;
  }

  Map<String, dynamic> exportSchema() {
    return {
      'pageType': 'mobile',
      'pageCode': 'designer',
      'pageName': '设计器',
      'root': _root.toJson(),
    };
  }
}