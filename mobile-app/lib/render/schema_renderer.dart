import 'dart:convert';
import 'package:flutter/material.dart';
import '../models/schema.dart';
import '../net/api_client.dart';
import 'widget_factory.dart';
import '../net/post_helper_stub.dart' if (dart.library.html) '../net/post_helper.dart';
import '../designer/designer_state.dart';

class RenderContext {
  final Map<String, dynamic> formData;
  final void Function(String pageCode, {Map<String, dynamic>? params, String? mode}) onNavigate;
  final void Function(String message) onShowSnackbar;
  final Future<void> Function() onRefresh;
  final void Function() onClosePage;
  final String? masterId;

  RenderContext({
    Map<String, dynamic>? formData,
    required this.onNavigate,
    required this.onShowSnackbar,
    required this.onRefresh,
    required this.onClosePage,
    this.masterId,
  }) : formData = formData ?? {};

  RenderContext copyWith({
    Map<String, dynamic>? formData,
    String? masterId,
  }) {
    return RenderContext(
      formData: formData ?? this.formData,
      onNavigate: onNavigate,
      onShowSnackbar: onShowSnackbar,
      onRefresh: onRefresh,
      onClosePage: onClosePage,
      masterId: masterId ?? this.masterId,
    );
  }

  void dispatchEvent(SchemaNode node, String eventName, {dynamic value}) {
    if (SchemaRenderer.editMode) return;
    final raw = node.events[eventName];
    if (raw == null) return;

    Map<String, dynamic> config;
    try {
      final decoded = raw is String ? jsonDecode(raw) : raw;
      if (decoded is! Map) return;
      config = Map<String, dynamic>.from(decoded);
      final nested = config[eventName];
      if (nested is Map) config = Map<String, dynamic>.from(nested);
    } catch (_) {
      return;
    }

    final action = config['action']?.toString() ?? '';
    switch (action) {
      case 'openPage':
        final rawParams = config['params'];
        Map<String, dynamic>? params;
        if (rawParams is Map) {
          params = Map<String, dynamic>.from(rawParams);
        } else if (rawParams is String && rawParams.trim().isNotEmpty) {
          try {
            final decoded = jsonDecode(rawParams);
            if (decoded is Map) params = Map<String, dynamic>.from(decoded);
          } catch (_) {}
        }
        onNavigate(
          config['pageCode']?.toString() ?? '',
          params: params,
          mode: config['mode']?.toString(),
        );
        break;
      case 'closePage':
        onClosePage();
        break;
      case 'showSnackbar':
      case 'snackbar':
        onShowSnackbar(config['message']?.toString() ?? '操作成功');
        break;
      case 'refresh':
        onRefresh();
        break;
      case 'setFormValue':
        final field = config['field']?.toString() ?? '';
        if (field.isNotEmpty) {
          final configuredValue = config['value'];
          formData[field] = configuredValue is String
              ? configuredValue.replaceAll(r'${event.value}', value?.toString() ?? '')
              : configuredValue;
        }
        break;
      case 'requestApi':
        final url = config['url']?.toString() ?? '';
        if (url.isEmpty) break;
        _requestApi(config['method']?.toString() ?? 'GET', url);
        break;
    }
  }

  Future<void> _requestApi(String method, String url) async {
    try {
      await ApiClient.instance.request(
        method,
        url,
        body: Map<String, dynamic>.from(formData),
      );
      onShowSnackbar('请求完成');
    } catch (error) {
      onShowSnackbar('请求失败: $error');
    }
  }
}

class SchemaRenderer {
  static bool editMode = false;
  static String? selectedId;
  static DesignerState? designerState;

  static const _containerTypes = {
    'm-scaffold', 'scaffold', 'm-page-root',
    'column', 'row', 'stack', 'grid', 'container',
    'm-card', 'card', 'm-accordion', 'm-border', 'm-sticky-header',
  };

  static Widget render(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final widget = WidgetFactory.build(node, ctx, rc);
    if (editMode) {
      if (_containerTypes.contains(node.type) || node.children.isNotEmpty) {
        return _EditContainerSelectable(nodeId: node.id, child: widget);
      }
      return _EditSelectable(nodeId: node.id, child: widget);
    }
    return widget;
  }

  static List<Widget> renderChildren(
    List<SchemaNode> children,
    BuildContext ctx,
    RenderContext rc,
  ) {
    return children.map((child) => render(child, ctx, rc)).toList();
  }
}

class _EditContainerSelectable extends StatelessWidget {
  final String nodeId;
  final Widget child;

  const _EditContainerSelectable({required this.nodeId, required this.child});

  void _notifySelect() {
    final ds = SchemaRenderer.designerState;
    if (ds != null) {
      ds.selectNode(nodeId);
    } else {
      postToParent(jsonEncode({'type': 'select', 'nodeId': nodeId}));
    }
  }

  @override
  Widget build(BuildContext context) {
    final isSelected = SchemaRenderer.selectedId == nodeId;
    final content = isSelected
        ? DecoratedBox(
            decoration: BoxDecoration(
              border: Border.all(color: const Color(0xFF1976D2), width: 1.5),
            ),
            child: child,
          )
        : child;
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onTap: _notifySelect,
      child: content,
    );
  }
}

enum _ResizeEdge { left, top, right, bottom }

class _EditSelectable extends StatefulWidget {
  final String nodeId;
  final Widget child;

  const _EditSelectable({required this.nodeId, required this.child});

  @override
  State<_EditSelectable> createState() => _EditSelectableState();
}

class _EditSelectableState extends State<_EditSelectable> {
  Offset _dragOffset = Offset.zero;

  void _notifySelect() {
    final ds = SchemaRenderer.designerState;
    if (ds != null) {
      ds.selectNode(widget.nodeId);
    } else {
      postToParent(jsonEncode({'type': 'select', 'nodeId': widget.nodeId}));
    }
  }

  void _onPanUpdate(DragUpdateDetails d) {
    if (d.delta.distance < 0.5) return;
    _dragOffset += d.delta;
    final ds = SchemaRenderer.designerState;
    if (ds != null) {
      final node = findNodeById(ds.root, widget.nodeId);
      if (node != null) {
        ds.updatePositionSilent(widget.nodeId, (node.x ?? 0) + d.delta.dx, (node.y ?? 0) + d.delta.dy);
      }
    } else {
      setState(() {});
    }
  }

  void _onPanEnd(DragEndDetails _) {
    if (_dragOffset == Offset.zero) return;
    final ds = SchemaRenderer.designerState;
    if (ds != null) {
      final node = findNodeById(ds.root, widget.nodeId);
      if (node != null) {
        ds.updatePosition(widget.nodeId, node.x ?? 0, node.y ?? 0);
      }
    } else {
      postToParent(jsonEncode({
        'type': 'panEnd',
        'nodeId': widget.nodeId,
        'dx': _dragOffset.dx.toStringAsFixed(1),
        'dy': _dragOffset.dy.toStringAsFixed(1),
      }));
    }
    _dragOffset = Offset.zero;
  }

  void _onResizeUpdate(DragUpdateDetails d, Set<_ResizeEdge> edges) {
    final ds = SchemaRenderer.designerState;
    if (ds != null) {
      final node = findNodeById(ds.root, widget.nodeId);
      if (node != null) {
        var newX = node.x ?? 0;
        var newY = node.y ?? 0;
        var newW = node.width ?? 200;
        var newH = node.height ?? 60;
        if (edges.contains(_ResizeEdge.left)) {
          newX += d.delta.dx;
          newW -= d.delta.dx;
        }
        if (edges.contains(_ResizeEdge.right)) {
          newW += d.delta.dx;
        }
        if (edges.contains(_ResizeEdge.top)) {
          newY += d.delta.dy;
          newH -= d.delta.dy;
        }
        if (edges.contains(_ResizeEdge.bottom)) {
          newH += d.delta.dy;
        }
        if (newW < 20) {
          if (edges.contains(_ResizeEdge.left)) newX -= (20 - newW);
          newW = 20;
        }
        if (newH < 20) {
          if (edges.contains(_ResizeEdge.top)) newY -= (20 - newH);
          newH = 20;
        }
        const canvasW = 375.0, canvasH = 812.0;
        if (newX < 0) {
          if (edges.contains(_ResizeEdge.left)) newW += newX;
          newX = 0;
        }
        if (newY < 0) {
          if (edges.contains(_ResizeEdge.top)) newH += newY;
          newY = 0;
        }
        if (newX + newW > canvasW) newW = canvasW - newX;
        if (newY + newH > canvasH) newH = canvasH - newY;
        ds.updatePositionSilent(widget.nodeId, newX, newY);
        ds.updateSizeSilent(widget.nodeId, newW, newH);
      }
    } else {
      setState(() {});
    }
  }

  void _onResizeEnd(DragEndDetails _) {
    final ds = SchemaRenderer.designerState;
    if (ds != null) {
      final node = findNodeById(ds.root, widget.nodeId);
      if (node != null) {
        ds.updatePosition(widget.nodeId, node.x ?? 0, node.y ?? 0);
        ds.updateSize(widget.nodeId, node.width, node.height);
      }
    }
  }

  Widget _resizeHandle({
    double? top, double? bottom, double? left, double? right,
    required Set<_ResizeEdge> edges,
  }) {
    return Positioned(
      top: top, bottom: bottom, left: left, right: right,
      child: GestureDetector(
        onPanUpdate: (d) => _onResizeUpdate(d, edges),
        onPanEnd: _onResizeEnd,
        child: Container(
          width: 10, height: 10,
          decoration: BoxDecoration(
            color: Colors.white,
            border: Border.all(color: const Color(0xFF1976D2), width: 1.5),
            borderRadius: BorderRadius.circular(2),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isSelected = SchemaRenderer.selectedId == widget.nodeId;
    final child = isSelected
        ? DecoratedBox(
            decoration: BoxDecoration(
              border: Border.all(color: const Color(0xFF1976D2), width: 1.5),
              borderRadius: BorderRadius.circular(2),
            ),
            child: widget.child,
          )
        : widget.child;
    return LayoutBuilder(
      builder: (context, constraints) {
        final w = constraints.maxWidth.isFinite ? constraints.maxWidth : 200;
        final h = constraints.maxHeight.isFinite ? constraints.maxHeight : 60;
        return Stack(
          children: [
            GestureDetector(
              onTapDown: (_) => _notifySelect(),
              onPanUpdate: _onPanUpdate,
              onPanEnd: _onPanEnd,
              behavior: HitTestBehavior.opaque,
              child: child,
            ),
            if (isSelected) ...[
              _resizeHandle(top: -6, left: -6, edges: {_ResizeEdge.left, _ResizeEdge.top}),
              _resizeHandle(top: -6, right: -6, edges: {_ResizeEdge.top, _ResizeEdge.right}),
              _resizeHandle(bottom: -6, left: -6, edges: {_ResizeEdge.left, _ResizeEdge.bottom}),
              _resizeHandle(bottom: -6, right: -6, edges: {_ResizeEdge.right, _ResizeEdge.bottom}),
              _resizeHandle(top: -6, left: w / 2 - 6, edges: {_ResizeEdge.top}),
              _resizeHandle(bottom: -6, left: w / 2 - 6, edges: {_ResizeEdge.bottom}),
              _resizeHandle(left: -6, top: h / 2 - 6, edges: {_ResizeEdge.left}),
              _resizeHandle(right: -6, top: h / 2 - 6, edges: {_ResizeEdge.right}),
            ],
          ],
        );
      },
    );
  }
}
