import 'package:flutter/material.dart';
import 'package:getwidget/getwidget.dart';
import '../models/schema.dart';
import '../render/schema_renderer.dart';
import '../net/api_client.dart';
import 'layout_widgets.dart';

class DisplayWidgets {
  static Widget listView(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final bizCode = node.propStr('bizCode');
    if (bizCode.isEmpty) return Padding(padding: const EdgeInsets.all(16), child: Center(child: Column(mainAxisSize: MainAxisSize.min, children: [Icon(Icons.list_alt, size: 40, color: Colors.grey), const SizedBox(height: 8), Text('请配置数据源', style: TextStyle(color: Colors.grey.shade500))])));
    return _ListViewWidget(node: node, bizCode: bizCode, columns: node.propList('columns'), primaryKey: node.propStr('primaryKey', 'id'), onRowTap: node.propMap('onRowTap'), rc: rc);
  }

  static Widget card(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Container(
      margin: EdgeInsets.all(node.propNum('margin', 8)),
      decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(6), boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.06), blurRadius: 2, offset: const Offset(0, 1))]),
      child: Padding(padding: const EdgeInsets.all(12), child: LayoutWidgets.buildLayoutContent(node, ctx, rc)),
    );
  }

  static Widget image(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final src = node.propStr('src');
    final width = node.propNum('width', 0), height = node.propNum('height', 100);
    if (src.isEmpty) return Container(width: width > 0 ? width : null, height: height, color: Colors.grey.shade200, child: const Icon(Icons.image, color: Colors.grey));
    return Image.network(src, width: width > 0 ? width : null, height: height > 0 ? height : null, fit: _parseFit(node.propStr('fit', 'cover')), errorBuilder: (_, __, ___) => Container(height: height, color: Colors.grey.shade200, child: const Icon(Icons.broken_image, color: Colors.grey)));
  }

  static Widget avatar(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final src = node.propStr('src'), name = node.propStr('name', '');
    return GFAvatar(backgroundImage: src.isNotEmpty ? NetworkImage(src) : null, child: src.isEmpty && name.isNotEmpty ? Text(name.isNotEmpty ? name.substring(0, 1) : '?') : null);
  }

  static Widget tag(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return GFBadge(text: node.propStr('text', node.propStr('label')), color: GFColors.PRIMARY, shape: GFBadgeShape.pills);
  }

  static Widget badge(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final dot = node.propBool('dot');
    return GFBadge(text: dot ? '' : node.propStr('text', '新'), color: GFColors.DANGER, shape: dot ? GFBadgeShape.circle : GFBadgeShape.pills);
  }

  static Widget noticeBar(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Container(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8), color: Colors.orange.shade50, child: Row(children: [Icon(Icons.notifications, size: 16, color: Colors.orange.shade700), const SizedBox(width: 8), Expanded(child: Text(node.propStr('text', '公告'), style: TextStyle(fontSize: 14, color: Colors.orange.shade900)))]));
  }

  static Widget progress(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12), child: GFProgressBar(percentage: (node.propNum('value', 60) / node.propNum('max', 100)).clamp(0.0, 1.0), lineHeight: 8, backgroundColor: Colors.grey.shade200, progressBarColor: GFColors.PRIMARY));
  }

  static Widget divider(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final text = node.propStr('text', '');
    if (text.isEmpty) return const Divider();
    return Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Row(children: [const Expanded(child: Divider()), Padding(padding: const EdgeInsets.symmetric(horizontal: 8), child: Text(text, style: TextStyle(color: Colors.grey.shade600, fontSize: 13))), const Expanded(child: Divider())]));
  }

  static Widget empty(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [Icon(Icons.inbox_outlined, size: 48, color: Colors.grey.shade400), const SizedBox(height: 8), Text(node.propStr('description', '暂无数据'), style: TextStyle(color: Colors.grey.shade500))]));
  }

  static Widget loading(SchemaNode node, BuildContext ctx, RenderContext rc) => const Center(child: GFLoader(type: GFLoaderType.android));

  static Widget result(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [const Icon(Icons.check_circle, size: 64, color: Colors.green), const SizedBox(height: 16), Text(node.propStr('title', '操作成功'), style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w500)), if (node.propStr('description', '').isNotEmpty) ...[const SizedBox(height: 8), Text(node.propStr('description', ''), style: TextStyle(color: Colors.grey.shade600))]]));
  }

  static Widget typography(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), child: GFTypography(text: node.propStr('text', '文字'), type: GFTypographyType.typo3));
  }

  static Widget animation(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return GFAnimation(type: GFAnimationType.scaleTransition, child: Padding(padding: const EdgeInsets.all(16), child: Text(node.propStr('text', '动画'), style: const TextStyle(fontSize: 18))));
  }

  static Widget carousel(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final items = node.propList('items');
    if (items.isEmpty) return Padding(padding: const EdgeInsets.all(16), child: Center(child: Column(mainAxisSize: MainAxisSize.min, children: [Icon(Icons.view_carousel, size: 40, color: Colors.grey), const SizedBox(height: 8), Text('请配置轮播数据', style: TextStyle(color: Colors.grey.shade500))])));
    return GFCarousel(items: items.map((e) => Image.network(e['image']?.toString() ?? e.toString(), fit: BoxFit.cover, errorBuilder: (_, __, ___) => Container(color: Colors.grey.shade200, child: const Icon(Icons.broken_image, color: Colors.grey)))).toList(), autoPlay: true);
  }

  static Widget imageOverlay(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final src = node.propStr('src', '');
    return GFImageOverlay(image: src.isNotEmpty ? NetworkImage(src) : null, child: Column(children: [if (node.propStr('title', '').isNotEmpty) Text(node.propStr('title', ''), style: const TextStyle(color: Colors.white, fontSize: 18)), if (node.propStr('subtitle', '').isNotEmpty) Text(node.propStr('subtitle', ''), style: const TextStyle(color: Colors.white70))]));
  }

  static Widget buttonBadge(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Stack(children: [GFButton(text: node.propStr('text', '按钮'), onPressed: () {}), Positioned(right: 0, top: 0, child: GFBadge(text: node.propStr('badgeText', '1'), color: GFColors.DANGER, shape: GFBadgeShape.circle))]);
  }

  static Widget iconBadge(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return Stack(children: [const Icon(Icons.notifications, size: 32), Positioned(right: 0, top: 0, child: GFBadge(text: node.propStr('text', '1'), color: GFColors.DANGER, shape: GFBadgeShape.circle))]);
  }

  static Widget shimmer(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return GFShimmer(child: Column(children: List.generate(3, (_) => Padding(padding: const EdgeInsets.all(8), child: Row(children: [Container(width: 48, height: 48, color: Colors.grey.shade300), const SizedBox(width: 8), Expanded(child: Column(children: [Container(height: 12, color: Colors.grey.shade300), const SizedBox(height: 8), Container(height: 12, color: Colors.grey.shade300)]))])))));
  }

  static Widget border(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return GFBorder(color: GFColors.PRIMARY, child: LayoutWidgets.buildLayoutContent(node, ctx, rc));
  }

  static BoxFit _parseFit(String s) => switch (s) { 'contain' => BoxFit.contain, 'fill' => BoxFit.fill, 'none' => BoxFit.none, 'fitWidth' => BoxFit.fitWidth, 'fitHeight' => BoxFit.fitHeight, _ => BoxFit.cover };
}

class _ListViewWidget extends StatefulWidget {
  final SchemaNode node; final String bizCode; final List<Map<String, dynamic>> columns; final String primaryKey; final Map<String, dynamic> onRowTap; final RenderContext rc;
  const _ListViewWidget({required this.node, required this.bizCode, required this.columns, required this.primaryKey, required this.onRowTap, required this.rc});
  @override
  State<_ListViewWidget> createState() => _ListViewWidgetState();
}

class _ListViewWidgetState extends State<_ListViewWidget> {
  List<Map<String, dynamic>> _rows = []; bool _loading = true; int _page = 1; bool _hasMore = true;
  final _scrollController = ScrollController();

  @override
  void initState() { super.initState(); _loadData(); _scrollController.addListener(_onScroll); }
  @override
  void dispose() { _scrollController.dispose(); super.dispose(); }

  void _onScroll() {
    if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200 && !_loading && _hasMore) { _page++; _loadData(); }
  }

  Future<void> _loadData() async {
    if (!_loading) setState(() => _loading = true);
    try {
      final filters = <String, dynamic>{};
      if (widget.rc.masterId != null) { final fk = widget.node.propStr('masterForeignKey', 'masterId'); filters[fk] = widget.rc.masterId; }
      final rows = await ApiClient.instance.queryBiz(widget.bizCode, page: _page, pageSize: 20, filters: filters.isNotEmpty ? filters : null);
      setState(() { if (_page == 1) _rows = rows; else _rows.addAll(rows); _hasMore = rows.length >= 20; _loading = false; });
    } catch (_) { setState(() => _loading = false); }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading && _rows.isEmpty) return const Center(child: GFLoader(type: GFLoaderType.android));
    if (_rows.isEmpty) return Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [const Icon(Icons.inbox_outlined, size: 48, color: Colors.grey), const SizedBox(height: 8), const Text('暂无数据', style: TextStyle(color: Colors.grey)), const SizedBox(height: 16), GFButton(text: '刷新', onPressed: _loadData, type: GFButtonType.outline, size: GFSize.SMALL)]));
    return RefreshIndicator(onRefresh: () async { _page = 1; await _loadData(); }, child: ListView.builder(controller: _scrollController, itemCount: _rows.length + (_hasMore ? 1 : 0), itemBuilder: (context, index) {
      if (index >= _rows.length) return const Padding(padding: EdgeInsets.all(16), child: Center(child: GFLoader(type: GFLoaderType.android, size: GFSize.SMALL)));
      final row = _rows[index];
      final titleField = widget.columns.isNotEmpty ? widget.columns[0]['field']?.toString() : '';
      final subtitleField = widget.columns.length > 1 ? widget.columns[1]['field']?.toString() : '';
      return GFListTile(
        title: titleField != null && titleField.isNotEmpty
            ? Text(row[titleField]?.toString() ?? '', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500))
            : null,
        subTitle: subtitleField != null && subtitleField.isNotEmpty
            ? Text(row[subtitleField]?.toString() ?? '', style: TextStyle(fontSize: 13, color: Colors.grey.shade600))
            : null,
        icon: const Icon(Icons.chevron_right),
        onTap: () {
          final action = widget.onRowTap['action']?.toString() ?? '';
          if (action == 'openPage') {
            widget.rc.onNavigate(
              widget.onRowTap['pageCode']?.toString() ?? '',
              params: row,
              mode: widget.onRowTap['mode']?.toString() ?? 'edit',
            );
          }
          widget.rc.dispatchEvent(widget.node, 'itemClick', value: row);
          widget.rc.dispatchEvent(widget.node, 'selectionChanged', value: row);
          widget.rc.dispatchEvent(widget.node, 'click', value: row);
        },
      );
    }));
  }
}
