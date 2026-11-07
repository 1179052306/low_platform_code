import 'package:flutter/material.dart';
import 'package:getwidget/getwidget.dart';
import '../models/schema.dart';
import '../render/schema_renderer.dart';
import '../net/api_client.dart';

class InteractionWidgets {
  static Widget button(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final label = node.propStr('label', node.propStr('text', '按钮'));
    final theme = node.propStr('theme', 'primary');
    final size = node.propStr('size', 'medium');
    final icon = node.propStr('icon', '');
    final onTap = node.propMap('onTap');
    final iconWidget = icon.isNotEmpty ? Icon(_parseIcon(icon)) : null;
    GFButtonType type; Color color;
    switch (theme) {
      case 'primary': type = GFButtonType.solid; color = GFColors.PRIMARY; break;
      case 'default': type = GFButtonType.outline; color = GFColors.PRIMARY; break;
      case 'danger': type = GFButtonType.solid; color = GFColors.DANGER; break;
      case 'warning': type = GFButtonType.solid; color = GFColors.WARNING; break;
      default: type = GFButtonType.solid; color = GFColors.PRIMARY;
    }
    double gfSize;
    switch (size) {
      case 'large': gfSize = GFSize.LARGE; break;
      case 'small': gfSize = GFSize.SMALL; break;
      default: gfSize = GFSize.MEDIUM;
    }
    Widget btn = GFButton(
      text: label,
      icon: iconWidget,
      onPressed: () {
        _handleAction(onTap, rc, node);
        rc.dispatchEvent(node, 'click');
      },
      type: type,
      color: color,
      fullWidthButton: true,
      size: gfSize,
    );
    return Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), child: btn);
  }

  static Widget dialog(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final title = node.propStr('title', '提示'), content = node.propStr('content', '');
    return GFListTile(titleText: title, subTitleText: content, icon: const Icon(Icons.chevron_right), onTap: () => showDialog(context: ctx, builder: (c) => AlertDialog(title: Text(title), content: Text(content), actions: [TextButton(onPressed: () => Navigator.of(c).pop(), child: const Text('确定'))])));
  }

  static Widget snackbar(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final message = node.propStr('message', node.propStr('text', ''));
    return GFListTile(titleText: '提示', subTitleText: message, icon: const Icon(Icons.chevron_right), onTap: () => GFToast.showToast(message, ctx));
  }

  static Widget actionSheet(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final items = node.propList('items'), title = node.propStr('title', '操作');
    return GFListTile(titleText: title, icon: const Icon(Icons.chevron_right), onTap: () => showModalBottomSheet(context: ctx, builder: (c) => SafeArea(child: Column(mainAxisSize: MainAxisSize.min, children: [Padding(padding: const EdgeInsets.all(12), child: Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500))), const Divider(height: 1), ...items.map((item) => ListTile(title: Text(item.toString(), textAlign: TextAlign.center), onTap: () => Navigator.pop(c)))]))));
  }

  static Widget alert(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Padding(padding: const EdgeInsets.all(16), child: GFAlert(title: node.propStr('title', '提示'), subtitle: node.propStr('content', ''), type: GFAlertType.basic));
  }

  static Widget iconButton(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return GFIconButton(icon: Icon(_parseIcon(node.propStr('icon', 'add'))), onPressed: () {}, type: GFButtonType.solid);
  }

  static Widget socialButton(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), child: GFButton(text: node.propStr('text', '社交登录'), onPressed: () {}, icon: const Icon(Icons.facebook)));
  }

  static Widget bottomSheet(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final title = node.propStr('title', '底部弹窗');
    return GFListTile(titleText: title, icon: const Icon(Icons.chevron_right), onTap: () => showModalBottomSheet(context: ctx, builder: (c) => SafeArea(child: Container(padding: const EdgeInsets.all(16), child: Text(title)))));
  }

  static void _handleAction(Map<String, dynamic> action, RenderContext rc, SchemaNode node) {
    final act = action['action']?.toString() ?? '';
    switch (act) {
      case 'openPage': rc.onNavigate(action['pageCode']?.toString() ?? '', mode: action['mode']?.toString()); break;
      case 'submit': _handleSubmit(action, rc, node); break;
      case 'refresh': rc.onRefresh(); break;
      case 'snackbar': rc.onShowSnackbar(action['message']?.toString() ?? '操作成功'); break;
    }
  }

  static void _handleSubmit(Map<String, dynamic> action, RenderContext rc, SchemaNode node) {
    final bizCode = action['bizCode']?.toString() ?? node.propStr('bizCode');
    if (bizCode.isEmpty) { rc.onShowSnackbar('未配置 bizCode'); return; }
    final data = Map<String, dynamic>.from(rc.formData)..remove('op');
    ApiClient.instance.saveBiz(bizCode, data).then((_) { rc.onShowSnackbar('保存成功'); rc.onRefresh(); }).catchError((e) => rc.onShowSnackbar('保存失败: $e'));
  }

  static IconData _parseIcon(String name) => switch (name) { 'add' => Icons.add, 'save' => Icons.save, 'search' => Icons.search, 'edit' => Icons.edit, 'delete' => Icons.delete, 'back' => Icons.arrow_back, 'close' => Icons.close, 'check' => Icons.check, 'send' => Icons.send, 'download' => Icons.download, 'upload' => Icons.upload, _ => Icons.widgets };
}
