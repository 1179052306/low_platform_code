import 'package:flutter/material.dart';
import 'package:getwidget/getwidget.dart';
import '../models/schema.dart';
import '../render/schema_renderer.dart';

class LayoutWidgets {
  /// 根据节点 layout/gap/padding 属性构建子控件布局内容。
  /// layout: vertical(纵向) / horizontal(横向) / free(自由定位)
  static Widget buildLayoutContent(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final children = SchemaRenderer.renderChildren(node.children, ctx, rc);
    final layout = node.propStr('layout', 'vertical');
    final gap = node.propNum('gap', 8);
    final padding = node.propNum('padding', 0);

    Widget content;
    if (node.children.isEmpty) {
      return const SizedBox.shrink();
    }
    if (layout == 'free') {
      final stackHeight = node.children.fold(0.0, (max, child) {
        final h = (child.y ?? 0) + (child.height ?? 60);
        return h > max ? h : max;
      });
      content = SizedBox(
        height: stackHeight,
        child: Stack(
          children: List.generate(node.children.length, (i) {
            final child = node.children[i];
            return Positioned(
              left: child.x ?? 0,
              top: child.y ?? 0,
              width: child.width ?? 200,
              height: child.height,
              child: children[i],
            );
          }),
        ),
      );
    } else if (layout == 'horizontal') {
      content = Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: gap > 0 ? _withSpacing(children, gap, false) : children,
      );
    } else {
      content = Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: gap > 0 ? _withSpacing(children, gap, true) : children,
      );
    }

    if (padding > 0) {
      content = Padding(padding: EdgeInsets.all(padding), child: content);
    }
    return content;
  }

  static Widget scaffold(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final title = node.propStr('title', '');

    final layout = node.propStr('layout', 'vertical');

    Widget body = node.children.isEmpty
        ? const Center(child: Text('空页面'))
        : (layout == 'free'
            ? buildLayoutContent(node, ctx, rc)
            : SingleChildScrollView(child: buildLayoutContent(node, ctx, rc)));

    if (node.bottomNav != null) {
      body = Column(
        children: [
          Expanded(child: body),
          _buildBottomNav(node.bottomNav!, ctx, rc),
        ],
      );
    }

    return Scaffold(
      appBar: title.isNotEmpty
          ? GFAppBar(
              title: Text(title),
              automaticallyImplyLeading: true,
            )
          : null,
      body: SafeArea(child: body),
      floatingActionButton: node.fab != null ? _buildFab(node.fab!, ctx, rc) : null,
    );
  }

  static Widget column(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final gap = node.propNum('gap', 16);
    final children = SchemaRenderer.renderChildren(node.children, ctx, rc);
    final padding = node.propNum('padding', 0);

    Widget col = Column(
      crossAxisAlignment: _crossAxis(node.propStr('crossAxisAlignment', 'start')),
      mainAxisAlignment: _mainAxis(node.propStr('mainAxisAlignment', 'start')),
      children: gap > 0
          ? _withSpacing(children, gap, true)
          : children,
    );

    if (padding > 0) {
      col = Padding(padding: EdgeInsets.all(padding), child: col);
    }
    return col;
  }

  static Widget row(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final gap = node.propNum('gap', 8);
    final children = SchemaRenderer.renderChildren(node.children, ctx, rc);
    return Row(
      crossAxisAlignment: _crossAxis(node.propStr('crossAxisAlignment', 'center')),
      mainAxisAlignment: _mainAxis(node.propStr('mainAxisAlignment', 'start')),
      children: gap > 0 ? _withSpacing(children, gap, false) : children,
    );
  }

  static Widget stack(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final children = SchemaRenderer.renderChildren(node.children, ctx, rc);
    return Stack(
      alignment: _alignment(node.propStr('alignment', 'center')),
      children: children,
    );
  }

  static Widget grid(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final crossAxisCount = node.propNum('crossAxisCount', 2).toInt();
    final spacing = node.propNum('spacing', 8);
    final children = SchemaRenderer.renderChildren(node.children, ctx, rc);
    return GridView.count(
      crossAxisCount: crossAxisCount,
      crossAxisSpacing: spacing,
      mainAxisSpacing: spacing,
      childAspectRatio: node.propNum('childAspectRatio', 1),
      children: children,
    );
  }

  static Widget container(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final padding = node.propNum('padding', 0);
    final bgColor = node.propStr('backgroundColor', '');
    final radius = node.propNum('borderRadius', 0);
    final children = SchemaRenderer.renderChildren(node.children, ctx, rc);

    Widget content = children.isEmpty
        ? const SizedBox.shrink()
        : (children.length == 1 ? children.first : Column(children: children));

    return Container(
      padding: padding > 0 ? EdgeInsets.all(padding) : null,
      decoration: BoxDecoration(
        color: bgColor.isNotEmpty ? _parseColor(bgColor) : null,
        borderRadius: radius > 0 ? BorderRadius.circular(radius) : null,
      ),
      child: content,
    );
  }

  static Widget accordion(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final items = node.propList('items');
    if (items.isEmpty) return const SizedBox.shrink();
    return Column(
      children: items.map((item) {
        final title = item['title']?.toString() ?? '标题';
        final content = item['content']?.toString() ?? '';
        return GFAccordion(title: title, content: content);
      }).toList(),
    );
  }

  static Widget _buildBottomNav(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final items = node.propList('items');
    if (items.isEmpty) return const SizedBox.shrink();

    return _BottomNavWidget(items: items, rc: rc);
  }

  static Widget _buildFab(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final onTap = node.propMap('onTap');
    return FloatingActionButton(
      onPressed: () => _handleAction(onTap, rc),
      child: Icon(_parseIcon(node.propStr('icon', 'add'))),
    );
  }

  static void _handleAction(Map<String, dynamic> action, RenderContext rc) {
    final act = action['action']?.toString() ?? '';
    if (act == 'openPage') {
      rc.onNavigate(
        action['pageCode']?.toString() ?? '',
        mode: action['mode']?.toString(),
      );
    } else if (act == 'refresh') {
      rc.onRefresh();
    }
  }

  static List<Widget> _withSpacing(List<Widget> children, double gap, bool vertical) {
    final result = <Widget>[];
    for (int i = 0; i < children.length; i++) {
      result.add(children[i]);
      if (i < children.length - 1) {
        result.add(SizedBox(
          width: vertical ? 0 : gap,
          height: vertical ? gap : 0,
        ));
      }
    }
    return result;
  }

  static CrossAxisAlignment _crossAxis(String s) => switch (s) {
        'center' => CrossAxisAlignment.center,
        'end' => CrossAxisAlignment.end,
        'stretch' => CrossAxisAlignment.stretch,
        _ => CrossAxisAlignment.start,
      };

  static MainAxisAlignment _mainAxis(String s) => switch (s) {
        'center' => MainAxisAlignment.center,
        'end' => MainAxisAlignment.end,
        'spaceBetween' => MainAxisAlignment.spaceBetween,
        'spaceAround' => MainAxisAlignment.spaceAround,
        'spaceEvenly' => MainAxisAlignment.spaceEvenly,
        _ => MainAxisAlignment.start,
      };

  static Alignment _alignment(String s) => switch (s) {
        'topLeft' => Alignment.topLeft,
        'topRight' => Alignment.topRight,
        'bottomLeft' => Alignment.bottomLeft,
        'bottomRight' => Alignment.bottomRight,
        'center' => Alignment.center,
        _ => Alignment.center,
      };

  static Color _parseColor(String hex) {
    if (hex.isEmpty) return Colors.blue;
    var h = hex.replaceAll('#', '');
    if (h.length == 6) h = 'FF$h';
    return Color(int.tryParse(h, radix: 16) ?? 0xFF1976D2);
  }

  static IconData _parseIcon(String name) => switch (name) {
        'add' => Icons.add,
        'home' => Icons.home,
        'person' => Icons.person,
        'search' => Icons.search,
        'edit' => Icons.edit,
        'delete' => Icons.delete,
        'save' => Icons.save,
        'back' => Icons.arrow_back,
        'menu' => Icons.menu,
        'settings' => Icons.settings,
        'list' => Icons.list,
        'info' => Icons.info,
        _ => Icons.widgets,
      };
}

class _BottomNavWidget extends StatefulWidget {
  final List<Map<String, dynamic>> items;
  final RenderContext rc;

  const _BottomNavWidget({required this.items, required this.rc});

  @override
  State<_BottomNavWidget> createState() => _BottomNavWidgetState();
}

class _BottomNavWidgetState extends State<_BottomNavWidget> {
  int _selectedIndex = 0;

  @override
  Widget build(BuildContext context) {
    return BottomNavigationBar(
      currentIndex: _selectedIndex,
      type: BottomNavigationBarType.fixed,
      onTap: (i) {
        setState(() => _selectedIndex = i);
        final pageCode = widget.items[i]['pageCode']?.toString() ?? '';
        if (pageCode.isNotEmpty) widget.rc.onNavigate(pageCode);
      },
      items: widget.items.map((item) {
        final icon = item['icon']?.toString() ?? 'home';
        return BottomNavigationBarItem(
          icon: Icon(LayoutWidgets._parseIcon(icon)),
          label: item['label']?.toString() ?? '',
        );
      }).toList(),
    );
  }
}