import 'package:flutter/material.dart';
import 'package:getwidget/getwidget.dart';
import '../models/schema.dart';
import '../render/schema_renderer.dart';
import 'layout_widgets.dart';

class NavigationWidgets {
  static Widget appBar(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return GFAppBar(title: Text(node.propStr('title', '')), automaticallyImplyLeading: node.propBool('showBack', true));
  }

  static Widget tabBar(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final tabs = node.propList('tabs');
    if (tabs.isEmpty) return LayoutWidgets.buildLayoutContent(node, ctx, rc);
    return DefaultTabController(length: tabs.length, child: Column(children: [
      TabBar(
        tabs: tabs.map((t) => Tab(text: t['label']?.toString() ?? '')).toList(),
        onTap: (index) {
          rc.dispatchEvent(node, 'selectionChanged', value: index);
          rc.dispatchEvent(node, 'itemClick', value: tabs[index]);
        },
      ),
      SizedBox(height: node.propNum('viewHeight', 200), child: TabBarView(children: tabs.map((t) => Center(child: Text(t['label']?.toString() ?? ''))).toList())),
    ]));
  }

  static Widget bottomNav(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final items = node.propList('items');
    if (items.isEmpty) return const SizedBox.shrink();
    return _BottomNavWidget(items: items, node: node, rc: rc);
  }

  static Widget fab(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return FloatingActionButton(
      onPressed: () {
        _handleAction(node.propMap('onTap'), rc);
        rc.dispatchEvent(node, 'click');
      },
      child: Icon(_parseIcon(node.propStr('icon', 'add'))),
    );
  }

  static Widget segmentTabs(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final tabs = node.propList('tabs');
    if (tabs.isEmpty) return const SizedBox.shrink();
    return DefaultTabController(length: tabs.length, child: Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), child: TabBar(tabs: tabs.map((t) => Tab(text: t['label']?.toString() ?? t.toString())).toList(), indicator: BoxDecoration(color: GFColors.PRIMARY, borderRadius: BorderRadius.circular(8)))));
  }

  static Widget tabs(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final tabs = node.propList('tabs');
    if (tabs.isEmpty) return const SizedBox.shrink();
    return DefaultTabController(length: tabs.length, child: TabBar(tabs: tabs.map((t) => Tab(text: t['label']?.toString() ?? t.toString())).toList()));
  }

  static Widget tabBarView(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final tabs = node.propList('tabs');
    if (tabs.isEmpty) return const SizedBox.shrink();
    return DefaultTabController(length: tabs.length, child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        TabBar(tabs: tabs.map((t) => Tab(text: t['label']?.toString() ?? '')).toList()),
        SizedBox(
          height: node.propNum('viewHeight', 200),
          child: TabBarView(children: tabs.map((t) => Center(
            child: Container(
              margin: const EdgeInsets.all(12),
              padding: const EdgeInsets.all(20),
              width: double.infinity,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFE4E7ED)),
              ),
              child: Text(t['label']?.toString() ?? '', textAlign: TextAlign.center),
            ),
          )).toList()),
        ),
      ],
    ));
  }

  static Widget drawer(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final items = node.propList('items');
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: OutlinedButton.icon(
        icon: const Icon(Icons.menu_open_rounded),
        label: Text(node.propStr('title', '打开菜单')),
        onPressed: () => showModalBottomSheet<void>(
          context: ctx,
          showDragHandle: true,
          backgroundColor: Colors.white,
          builder: (sheetContext) => SafeArea(
            child: ListView(
              shrinkWrap: true,
              children: [
                const Padding(
                  padding: EdgeInsets.fromLTRB(20, 8, 20, 12),
                  child: Text('菜单', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
                ),
                ...items.map((item) => ListTile(
                  leading: const Icon(Icons.chevron_right_rounded),
                  title: Text(item['label']?.toString() ?? item.toString()),
                  onTap: () {
                    rc.dispatchEvent(node, 'itemClick', value: item);
                    Navigator.pop(sheetContext);
                  },
                )),
              ],
            ),
          ),
        ),
      ),
    );
  }

  static Widget stickyHeader(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Column(children: [Container(color: Colors.white, padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), child: Text(node.propStr('title', '标题'), style: const TextStyle(fontWeight: FontWeight.w500))), LayoutWidgets.buildLayoutContent(node, ctx, rc)]);
  }

  static void _handleAction(Map<String, dynamic> action, RenderContext rc) {
    final act = action['action']?.toString() ?? '';
    if (act == 'openPage') rc.onNavigate(action['pageCode']?.toString() ?? '', mode: action['mode']?.toString());
    else if (act == 'refresh') rc.onRefresh();
  }

  static IconData _parseIcon(String name) => switch (name) { 'add' => Icons.add, 'home' => Icons.home, 'person' => Icons.person, 'search' => Icons.search, 'edit' => Icons.edit, 'delete' => Icons.delete, 'save' => Icons.save, 'back' => Icons.arrow_back, 'menu' => Icons.menu, 'settings' => Icons.settings, 'list' => Icons.list, _ => Icons.widgets };
}

class _BottomNavWidget extends StatefulWidget {
  final List<Map<String, dynamic>> items;
  final SchemaNode node;
  final RenderContext rc;
  const _BottomNavWidget({required this.items, required this.node, required this.rc});
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
        widget.rc.dispatchEvent(widget.node, 'selectionChanged', value: i);
        widget.rc.dispatchEvent(widget.node, 'itemClick', value: widget.items[i]);
        final pageCode = widget.items[i]['pageCode']?.toString() ?? '';
        if (pageCode.isNotEmpty) widget.rc.onNavigate(pageCode);
      },
      items: widget.items.map((item) => BottomNavigationBarItem(
        icon: Icon(NavigationWidgets._parseIcon(item['icon']?.toString() ?? 'home')),
        label: item['label']?.toString() ?? '',
      )).toList(),
    );
  }
}
