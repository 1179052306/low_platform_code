import 'package:flutter/material.dart';
import 'component_registry.dart';

class ComponentPanel extends StatefulWidget {
  final void Function(String type) onAdd;
  final void Function(String type, Offset startPos, OverlayState overlay) onDragStart;
  final void Function(Offset pos) onDragUpdate;
  final void Function() onDragEnd;
  const ComponentPanel({
    required this.onAdd,
    required this.onDragStart,
    required this.onDragUpdate,
    required this.onDragEnd,
    super.key,
  });

  @override
  State<ComponentPanel> createState() => _ComponentPanelState();
}

class _ComponentPanelState extends State<ComponentPanel> {
  final _expanded = <String, bool>{};

  @override
  void initState() {
    super.initState();
    final categories = ComponentRegistry.categories;
    for (final cat in categories) {
      _expanded[cat] = cat == '移动端布局' || cat == '移动端表单';
    }
  }

  @override
  Widget build(BuildContext context) {
    final categories = ComponentRegistry.categories;
    return ListView.builder(
      padding: EdgeInsets.zero,
      itemCount: categories.length,
      itemBuilder: (_, ci) {
        final cat = categories[ci];
        final comps = ComponentRegistry.byCategory(cat);
        final expanded = _expanded[cat] ?? false;
        return Column(children: [
          _CategoryHeader(
            title: cat,
            expanded: expanded,
            onToggle: () => setState(() => _expanded[cat] = !expanded),
          ),
          if (expanded)
            ...comps.map((c) => _ComponentTile(
              def: c,
              onAdd: widget.onAdd,
              onDragStart: widget.onDragStart,
              onDragUpdate: widget.onDragUpdate,
              onDragEnd: widget.onDragEnd,
            )),
        ]);
      },
    );
  }
}

class _CategoryHeader extends StatelessWidget {
  final String title;
  final bool expanded;
  final VoidCallback onToggle;
  const _CategoryHeader({required this.title, required this.expanded, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onToggle,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: const BoxDecoration(
          color: Color(0xFFF5F7FA),
          border: Border(bottom: BorderSide(color: Color(0xFFE4E7ED), width: 0.5)),
        ),
        child: Row(children: [
          Icon(
            expanded ? Icons.keyboard_arrow_down : Icons.keyboard_arrow_right,
            size: 14,
            color: const Color(0xFF606266),
          ),
          const SizedBox(width: 4),
          Text(title, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFF303133))),
        ]),
      ),
    );
  }
}

class _ComponentTile extends StatelessWidget {
  final ComponentDef def;
  final void Function(String type) onAdd;
  final void Function(String type, Offset startPos, OverlayState overlay) onDragStart;
  final void Function(Offset pos) onDragUpdate;
  final void Function() onDragEnd;
  const _ComponentTile({
    required this.def,
    required this.onAdd,
    required this.onDragStart,
    required this.onDragUpdate,
    required this.onDragEnd,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => onAdd(def.type),
      onHorizontalDragStart: (details) {
        final overlay = Overlay.of(context, rootOverlay: true);
        onDragStart(def.type, details.globalPosition, overlay);
      },
      onHorizontalDragUpdate: (details) => onDragUpdate(details.globalPosition),
      onHorizontalDragEnd: (_) => onDragEnd(),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: const BoxDecoration(
          border: Border(bottom: BorderSide(color: Color(0xFFE4E7ED), width: 0.5)),
        ),
        child: Row(children: [
          Text(def.icon, style: const TextStyle(fontSize: 14)),
          const SizedBox(width: 8),
          Expanded(child: Text(def.title, style: const TextStyle(fontSize: 12, color: Color(0xFF606266)), overflow: TextOverflow.ellipsis)),
        ]),
      ),
    );
  }
}