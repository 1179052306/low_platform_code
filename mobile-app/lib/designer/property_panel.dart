import 'package:flutter/material.dart';
import '../models/schema.dart';
import 'component_registry.dart';
import 'designer_state.dart';
import 'prop_field.dart';

class PropertyPanel extends StatelessWidget {
  final DesignerState state;
  const PropertyPanel({required this.state, super.key});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: state,
      builder: (_, __) {
        final node = state.selectedNode;
        if (node == null) {
          return const Center(child: Text('未选中控件', style: TextStyle(color: Colors.grey)));
        }
        final def = ComponentRegistry.get(node.type);
        if (def == null) {
          return Center(child: Text('未知控件: ${node.type}', style: const TextStyle(color: Colors.grey)));
        }
        return ListView(
          padding: const EdgeInsets.symmetric(vertical: 8),
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              child: Row(children: [
                Text(def.icon, style: const TextStyle(fontSize: 18)),
                const SizedBox(width: 8),
                Text(def.title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                const Spacer(),
                Text(node.type, style: TextStyle(fontSize: 11, color: Colors.grey.shade500)),
              ]),
            ),
            const Divider(height: 1),
            ..._buildSetters(node, def.propSetters),
            const Divider(height: 1),
            PropRow('ID', child: Text(node.id, style: TextStyle(fontSize: 11, color: Colors.grey.shade500))),
            PropRow('X', child: Text((node.x ?? 0).toStringAsFixed(0), style: const TextStyle(fontSize: 12))),
            PropRow('Y', child: Text((node.y ?? 0).toStringAsFixed(0), style: const TextStyle(fontSize: 12))),
          ],
        );
      },
    );
  }

  List<Widget> _buildSetters(SchemaNode node, List<PropSetter> setters) {
    final existingProps = setters.map((s) => s.prop).toSet();
    final result = setters.map((setter) => _buildSetter(node, setter)).toList();

    if (!existingProps.contains('field')) {
      result.add(PropTextField('字段名', value: node.propStr('field', ''), onChange: (v) => state.updateProp(node.id, 'field', v)));
    }
    if (!existingProps.contains('labelPosition') && (node.props.containsKey('label') || existingProps.contains('label'))) {
      result.add(PropSelect('标签位置', value: node.propStr('labelPosition', 'top'),
        options: [PropOption('上方', 'top'), PropOption('左侧', 'left')],
        onChange: (v) => state.updateProp(node.id, 'labelPosition', v)));
    }
    if (!existingProps.contains('width')) {
      result.add(PropNumberField('宽度', value: node.width ?? 200, onChange: (v) => state.updateSize(node.id, v.toDouble(), node.height)));
    }
    if (!existingProps.contains('height')) {
      result.add(PropNumberField('高度', value: node.height ?? 60, onChange: (v) => state.updateSize(node.id, node.width, v.toDouble())));
    }
    return result;
  }

  Widget _buildSetter(SchemaNode node, PropSetter setter) {
    final value = node.props[setter.prop];
    switch (setter.type) {
      case PropType.input:
        return PropTextField(setter.label, value: value?.toString() ?? '', onChange: (v) => state.updateProp(node.id, setter.prop, v));
      case PropType.number:
        return PropNumberField(setter.label, value: value is num ? value : (num.tryParse(value?.toString() ?? '') ?? 0), onChange: (v) => state.updateProp(node.id, setter.prop, v));
      case PropType.switchToggle:
        return PropSwitch(setter.label, value: value == true || value?.toString() == 'true', onChange: (v) => state.updateProp(node.id, setter.prop, v));
      case PropType.select:
        return PropSelect(setter.label, value: value?.toString() ?? '', options: setter.options ?? [], onChange: (v) => state.updateProp(node.id, setter.prop, v));
      case PropType.json:
        return PropJsonField(setter.label, value: value, onChange: (v) => state.updateProp(node.id, setter.prop, v));
      case PropType.color:
        return PropColorField(setter.label, value: value?.toString() ?? '#1976D2', onChange: (v) => state.updateProp(node.id, setter.prop, v));
      case PropType.iconSelect:
        return PropTextField(setter.label, value: value?.toString() ?? '', onChange: (v) => state.updateProp(node.id, setter.prop, v));
    }
  }
}