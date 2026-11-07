import 'dart:convert';
import 'dart:html' as html;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/schema.dart';
import 'component_registry.dart';
import 'component_panel.dart';
import 'designer_canvas.dart';
import 'designer_state.dart';
import 'property_panel.dart';
import 'prop_field.dart';
import 'toolbar.dart';

class DesignerApp extends StatefulWidget {
  const DesignerApp({super.key});

  @override
  State<DesignerApp> createState() => _DesignerAppState();
}

class _DesignerAppState extends State<DesignerApp> {
  late DesignerState _state;
  bool _isPreview = false;
  final GlobalKey _canvasKey = GlobalKey();
  OverlayEntry? _dragEntry;
  String? _draggingType;
  Offset _dragPos = Offset.zero;

  @override
  void initState() {
    super.initState();
    final initial = SchemaNode(id: 'root', type: 'm-page-root', props: {'layout': 'free', 'gap': 8, 'padding': 0});
    _state = DesignerState(initial);
    _setupWebListener();
    RawKeyboard.instance.addListener(_onKey);
  }

  @override
  void dispose() {
    RawKeyboard.instance.removeListener(_onKey);
    super.dispose();
  }

  void _setupWebListener() {
    html.window.onMessage.listen((event) {
      final data = event.data;
      if (data is String) {
        try {
          final json = jsonDecode(data) as Map<String, dynamic>;
          if (json['type'] == 'loadSchema') {
            final root = SchemaNode.fromJson(json['root'] as Map<String, dynamic>);
            _state.loadSchema(root);
          } else if (json['type'] == 'dropMaterial') {
            _handleDropMaterial(json);
          }
        } catch (e) {
          print('[designer] loadSchema error: $e');
        }
      }
    });
    html.window.parent?.postMessage(jsonEncode({'type': 'designerReady'}), '*');
  }

  /// 父页面(Vue)物料面板拖入 iframe 的 HTML5 drop 由父页面代收并转发到这里。
  /// x/y 为 iframe 视口坐标（等于 Flutter Web 全局坐标），转换为画布局部坐标后添加节点。
  void _handleDropMaterial(Map<String, dynamic> json) {
    if (_isPreview) return;
    final materialType = json['materialType'] as String?;
    final x = (json['x'] as num?)?.toDouble();
    final y = (json['y'] as num?)?.toDouble();
    if (materialType == null || x == null || y == null) return;
    final canvasPos = _globalToCanvas(Offset(x, y));
    _state.addNode(materialType, x: canvasPos.dx - 50, y: canvasPos.dy - 15);
  }

  void _onKey(RawKeyEvent event) {
    if (event is! RawKeyDownEvent) return;
    final ctrl = event.isControlPressed || event.isMetaPressed;
    if (ctrl && event.logicalKey == LogicalKeyboardKey.keyZ) {
      _state.undo();
    } else if (ctrl && event.logicalKey == LogicalKeyboardKey.keyY) {
      _state.redo();
    } else if (ctrl && event.logicalKey == LogicalKeyboardKey.keyS) {
      _save();
    } else if (event.logicalKey == LogicalKeyboardKey.delete || event.logicalKey == LogicalKeyboardKey.backspace) {
      if (!_isPreview && !PropFieldFocus.hasFocus) {
        _state.removeSelected();
      }
    }
  }

  void _save() {
    final schema = _state.exportSchema();
    html.window.parent?.postMessage(jsonEncode({'type': 'saveSchema', 'schema': schema}), '*');
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已保存'), duration: Duration(seconds: 1)));
  }

  void _startDrag(String type, Offset startPos, OverlayState overlay) {
    _draggingType = type;
    _dragPos = startPos;
    _dragEntry = OverlayEntry(builder: _buildDragFeedback);
    overlay.insert(_dragEntry!);
  }

  void _updateDrag(Offset pos) {
    _dragPos = pos;
    _dragEntry?.markNeedsBuild();
  }

  void _endDrag() {
    try {
      if (_draggingType != null && _isOverCanvas(_dragPos)) {
        final canvasPos = _globalToCanvas(_dragPos);
        _state.addNode(_draggingType!, x: canvasPos.dx - 50, y: canvasPos.dy - 15);
      }
    } finally {
      _dragEntry?.remove();
      _dragEntry = null;
      _draggingType = null;
    }
  }

  Offset _globalToCanvas(Offset globalPos) {
    final box = _canvasKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null) return const Offset(20, 20);
    return box.globalToLocal(globalPos);
  }

  bool _isOverCanvas(Offset pos) {
    final box = _canvasKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null) return false;
    final topLeft = box.localToGlobal(Offset.zero);
    final rect = topLeft & box.size;
    return rect.contains(pos);
  }

  Widget _buildDragFeedback(BuildContext ctx) {
    final def = _draggingType != null ? ComponentRegistry.get(_draggingType!) : null;
    final box = _canvasKey.currentContext?.findRenderObject() as RenderBox?;
    Rect? canvasRect;
    if (box != null) {
      canvasRect = box.localToGlobal(Offset.zero) & box.size;
    }
    return Stack(children: [
      if (canvasRect != null)
        Positioned.fromRect(
          rect: canvasRect,
          child: IgnorePointer(child: Container(
            decoration: BoxDecoration(
              border: Border.all(color: const Color(0xFF1976D2), width: 1.5),
              color: const Color(0xFF1976D2).withValues(alpha: 0.05),
            ),
            child: const Center(child: Icon(Icons.add, size: 36, color: Color(0xFF1976D2))),
          )),
        ),
      Positioned(
        left: _dragPos.dx - 40,
        top: _dragPos.dy - 20,
        child: Material(
          color: Colors.transparent,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: const Color(0xFF1976D2),
              borderRadius: BorderRadius.circular(8),
              boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 8)],
            ),
            child: Text(def?.title ?? _draggingType ?? '', style: const TextStyle(color: Colors.white, fontSize: 13)),
          ),
        ),
      ),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          DesignerToolbar(
            state: _state,
            onSave: _save,
            onPreview: () => setState(() => _isPreview = !_isPreview),
            onPreviewToggle: (v) => setState(() => _isPreview = v),
            isPreview: _isPreview,
          ),
          Expanded(child: Row(
            children: [
              if (!_isPreview) ...[
                SizedBox(width: 220, child: Container(
                  decoration: const BoxDecoration(border: Border(right: BorderSide(color: Color(0xFFE4E7ED)))),
                  child: ComponentPanel(
                    onAdd: (type) {
                      final n = _state.root.children.length;
                      _state.addNode(type, x: 20 + n * 20.0, y: 20 + n * 20.0);
                    },
                    onDragStart: _startDrag,
                    onDragUpdate: _updateDrag,
                    onDragEnd: _endDrag,
                  ),
                )),
                const SizedBox(width: 4),
              ],
              Expanded(child: DesignerCanvas(state: _state, isPreview: _isPreview, canvasKey: _canvasKey)),
              if (!_isPreview) ...[
                const SizedBox(width: 4),
                SizedBox(width: 280, child: Container(
                  decoration: const BoxDecoration(border: Border(left: BorderSide(color: Color(0xFFE4E7ED)))),
                  child: PropertyPanel(state: _state),
                )),
              ],
            ],
          )),
        ],
      ),
    );
  }
}