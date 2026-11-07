import 'package:flutter/material.dart';
import 'designer_state.dart';

class DesignerToolbar extends StatelessWidget {
  final DesignerState state;
  final VoidCallback onSave;
  final VoidCallback onPreview;
  final ValueChanged<bool> onPreviewToggle;
  final bool isPreview;
  const DesignerToolbar({
    required this.state,
    required this.onSave,
    required this.onPreview,
    required this.onPreviewToggle,
    required this.isPreview,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: state,
      builder: (_, __) {
        return Container(
          height: 40,
          padding: const EdgeInsets.symmetric(horizontal: 6),
          decoration: const BoxDecoration(
            color: Colors.white,
            border: Border(bottom: BorderSide(color: Color(0xFFE4E7ED))),
          ),
          child: Row(children: [
            _btn(Icons.undo, '撤销', state.canUndo, state.undo),
            _btn(Icons.redo, '重做', state.canRedo, state.redo),
            _divider(),
            _btn(Icons.delete_outline, '删除', state.selectedId.isNotEmpty, state.removeSelected),
            _btn(Icons.content_copy, '复制', state.selectedId.isNotEmpty, state.copySelected),
            _btn(Icons.content_paste, '粘贴', state.selectedId.isNotEmpty, state.pasteSelected),
            _divider(),
            _btn(Icons.clear, '清空', true, () {
              showDialog(context: context, builder: (_) => AlertDialog(
                title: const Text('确认清空'),
                content: const Text('清空所有控件？此操作可撤销。'),
                actions: [
                  TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
                  TextButton(onPressed: () { Navigator.pop(context); state.clearAll(); }, child: const Text('确定')),
                ],
              ));
            }),
            const Spacer(),
            _buildModeToggle(),
            const SizedBox(width: 6),
            _buildSaveBtn(),
          ]),
        );
      },
    );
  }

  Widget _divider() {
    return Container(width: 1, height: 16, margin: const EdgeInsets.symmetric(horizontal: 4), color: const Color(0xFFE4E7ED));
  }

  Widget _btn(IconData icon, String tip, bool enabled, VoidCallback onTap) {
    return Tooltip(
      message: tip,
      child: InkWell(
        borderRadius: BorderRadius.circular(4),
        onTap: enabled ? onTap : null,
        child: Padding(
          padding: const EdgeInsets.all(6),
          child: Icon(icon, size: 16, color: enabled ? const Color(0xFF606266) : const Color(0xFFC0C4CC)),
        ),
      ),
    );
  }

  Widget _buildModeToggle() {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFFF0F2F5),
        borderRadius: BorderRadius.circular(4),
      ),
      padding: const EdgeInsets.all(2),
      child: Row(children: [
        _toggleItem('设计', !isPreview, () => onPreviewToggle(false)),
        _toggleItem('预览', isPreview, () => onPreviewToggle(true)),
      ]),
    );
  }

  Widget _toggleItem(String label, bool active, VoidCallback onTap) {
    return InkWell(
      borderRadius: BorderRadius.circular(3),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: active ? const Color(0xFF1976D2) : Colors.transparent,
          borderRadius: BorderRadius.circular(3),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12,
            color: active ? Colors.white : const Color(0xFF606266),
            fontWeight: active ? FontWeight.w500 : FontWeight.normal,
          ),
        ),
      ),
    );
  }

  Widget _buildSaveBtn() {
    return InkWell(
      borderRadius: BorderRadius.circular(4),
      onTap: onSave,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: const Color(0xFF1976D2),
          borderRadius: BorderRadius.circular(4),
        ),
        child: const Row(children: [
          Icon(Icons.save, size: 14, color: Colors.white),
          SizedBox(width: 4),
          Text('保存', style: TextStyle(fontSize: 12, color: Colors.white, fontWeight: FontWeight.w500)),
        ]),
      ),
    );
  }
}