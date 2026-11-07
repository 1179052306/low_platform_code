import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:getwidget/getwidget.dart';
import '../models/schema.dart';
import '../render/schema_renderer.dart';

class FormWidgets {
  static Widget textBox(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final field = node.propStr('field');
    final label = node.propStr('label', field);
    final required = node.propBool('required');
    final placeholder = node.propStr('placeholder', '请输入');
    final readonly = node.propBool('readonly');
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: _withKeyEvents(node, rc, TextFormField(
        initialValue: rc.formData[field]?.toString() ?? '',
        decoration: _inputDecoration(placeholder, readonly),
        readOnly: readonly,
        onTap: () => rc.dispatchEvent(node, 'click'),
        onChanged: (v) {
          rc.formData[field] = v;
          rc.dispatchEvent(node, 'valueChanged', value: v);
        },
        onFieldSubmitted: (v) {
          rc.formData[field] = v;
          rc.dispatchEvent(node, 'enterKey', value: v);
        },
      )),
    );
  }

  static Widget textarea(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final field = node.propStr('field');
    final label = node.propStr('label', field);
    final required = node.propBool('required');
    final placeholder = node.propStr('placeholder', '请输入');
    final maxLines = node.propNum('maxLines', 4).toInt();

    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: _withKeyEvents(node, rc, TextFormField(
        initialValue: rc.formData[field]?.toString() ?? '',
        decoration: _inputDecoration(placeholder, false),
        maxLines: maxLines,
        onTap: () => rc.dispatchEvent(node, 'click'),
        onChanged: (v) {
          rc.formData[field] = v;
          rc.dispatchEvent(node, 'valueChanged', value: v);
        },
        onFieldSubmitted: (v) {
          rc.formData[field] = v;
          rc.dispatchEvent(node, 'enterKey', value: v);
        },
      )),
    );
  }

  static Widget select(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final field = node.propStr('field');
    final label = node.propStr('label', field);
    final required = node.propBool('required');
    final options = node.propList('options');

    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: DropdownButtonFormField<String>(
        value: options.isNotEmpty ? options[0]['value']?.toString() : null,
        decoration: _inputDecoration('请选择', false),
        items: options.map((o) => DropdownMenuItem<String>(
          value: o['value']?.toString() ?? '',
          child: Text(o['label']?.toString() ?? o['value']?.toString() ?? ''),
        )).toList(),
        onChanged: (v) {
          if (v == null) return;
          rc.formData[field] = v;
          rc.dispatchEvent(node, 'click', value: v);
          rc.dispatchEvent(node, 'valueChanged', value: v);
        },
      ),
    );
  }

  static Widget _emptyPlaceholder(String text) {
    return Padding(padding: const EdgeInsets.all(16), child: Center(child: Column(mainAxisSize: MainAxisSize.min, children: [Icon(Icons.touch_app, size: 36, color: Colors.grey), const SizedBox(height: 6), Text(text, style: TextStyle(color: Colors.grey.shade500, fontSize: 13))])));
  }

  static Widget datePicker(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final field = node.propStr('field');
    final label = node.propStr('label', field);
    final required = node.propBool('required');

    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: Builder(builder: (context) {
        final currentVal = rc.formData[field]?.toString() ?? '';
        return InkWell(
          onTap: () async {
            rc.dispatchEvent(node, 'click');
            final picked = await showDatePicker(
              context: context,
              initialDate: DateTime.tryParse(currentVal) ?? DateTime.now(),
              firstDate: DateTime(2000),
              lastDate: DateTime(2100),
            );
            if (picked != null) {
              final value = picked.toIso8601String().split('T').first;
              rc.formData[field] = value;
              rc.dispatchEvent(node, 'valueChanged', value: value);
              (context as Element).markNeedsBuild();
            }
          },
          child: InputDecorator(
            decoration: _inputDecoration('请选择日期', false),
            child: Text(currentVal.isEmpty ? '请选择日期' : currentVal,
              style: TextStyle(color: currentVal.isEmpty ? Colors.grey.shade600 : Colors.black87)),
          ),
        );
      }),
    );
  }

  static Widget numberField(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final field = node.propStr('field');
    final label = node.propStr('label', field);
    final required = node.propBool('required');
    final placeholder = node.propStr('placeholder', '请输入数字');

    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: _withKeyEvents(node, rc, TextFormField(
        initialValue: rc.formData[field]?.toString() ?? '',
        decoration: _inputDecoration(placeholder, false),
        keyboardType: TextInputType.number,
        onTap: () => rc.dispatchEvent(node, 'click'),
        onChanged: (v) {
          final value = num.tryParse(v) ?? v;
          rc.formData[field] = value;
          rc.dispatchEvent(node, 'valueChanged', value: value);
        },
        onFieldSubmitted: (v) {
          final value = num.tryParse(v) ?? v;
          rc.formData[field] = value;
          rc.dispatchEvent(node, 'enterKey', value: value);
        },
      )),
    );
  }

  static Widget switchField(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final field = node.propStr('field');
    final label = node.propStr('label', field);
    final required = node.propBool('required');
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: Align(alignment: Alignment.centerLeft, child: StatefulBuilder(builder: (context, setState) {
        var val = rc.formData[field] == true || rc.formData[field]?.toString() == 'true';
        return GFToggle(value: val, onChanged: (v) {
          val = v ?? false;
          setState(() {});
          rc.formData[field] = val;
          rc.dispatchEvent(node, 'valueChanged', value: val);
        });
      })),
    );
  }

  static Widget rate(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final label = node.propStr('label', '');
    final required = node.propBool('required');
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: GFRating(
        value: node.propNum('value', 3),
        itemCount: node.propNum('max', 5).toInt(),
        onChanged: (v) => rc.dispatchEvent(node, 'valueChanged', value: v),
      ),
    );
  }

  static Widget search(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final label = node.propStr('label', '');
    final required = node.propBool('required');
    final placeholder = node.propStr('placeholder', '搜索');
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: _withKeyEvents(node, rc, TextField(
        decoration: InputDecoration(hintText: placeholder, prefixIcon: const Icon(Icons.search), border: const OutlineInputBorder()),
        onTap: () => rc.dispatchEvent(node, 'click'),
        onChanged: (value) => rc.dispatchEvent(node, 'valueChanged', value: value),
        onSubmitted: (value) => rc.dispatchEvent(node, 'enterKey', value: value),
      )),
    );
  }

  static Widget slider(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final label = node.propStr('label', '');
    final required = node.propBool('required');
    var min = node.propNum('min', 0), max = node.propNum('max', 100);
    if (min >= max) max = min + 1;
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: StatefulBuilder(builder: (context, setState) {
        var val = node.propNum('value', 50).clamp(min, max);
        return Slider(
          value: val,
          min: min,
          max: max,
          onChanged: (v) {
            setState(() => val = v);
            rc.dispatchEvent(node, 'valueChanged', value: v);
          },
        );
      }),
    );
  }

  static Widget radio(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final label = node.propStr('label', '');
    final required = node.propBool('required');
    final options = node.propList('options');
    if (options.isEmpty) return _emptyPlaceholder('请配置选项');
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: StatefulBuilder(builder: (context, setState) {
        var selected = node.propStr('value', '');
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: options.map((o) {
          final val = o['value']?.toString() ?? '';
          final itemLabel = o['label']?.toString() ?? val;
          return Row(children: [
            GFRadio(size: GFSize.MEDIUM, radioColor: GFColors.PRIMARY, value: val, groupValue: selected, onChanged: (v) => setState(() {
              selected = v ?? '';
              rc.dispatchEvent(node, 'valueChanged', value: selected);
            })),
            const SizedBox(width: 8), Text(itemLabel),
          ]);
        }).toList());
      }),
    );
  }

  static Widget checkbox(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final label = node.propStr('label', '');
    final required = node.propBool('required');
    final options = node.propList('options');
    if (options.isEmpty) return _emptyPlaceholder('请配置选项');
    final checkedValues = node.propList('value').map((e) => e.toString()).toSet();
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: StatefulBuilder(builder: (context, setState) {
        var selected = Set<String>.from(checkedValues);
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: options.map((o) {
          final val = o['value']?.toString() ?? '';
          final itemLabel = o['label']?.toString() ?? val;
          return Row(children: [
            GFCheckbox(size: GFSize.MEDIUM, activeBgColor: GFColors.PRIMARY, value: selected.contains(val), onChanged: (v) => setState(() {
              if (v ?? false) selected.add(val); else selected.remove(val);
              rc.dispatchEvent(node, 'valueChanged', value: selected.toList());
            })),
            const SizedBox(width: 8), Text(itemLabel),
          ]);
        }).toList());
      }),
    );
  }

  static Widget stepper(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final label = node.propStr('label', '');
    final required = node.propBool('required');
    final min = node.propNum('min', 0).toInt(), max = node.propNum('max', 10).toInt(), step = node.propNum('step', 1).toInt();
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: StatefulBuilder(builder: (context, setState) {
        var val = node.propNum('value', 1).toInt();
        return Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          IconButton(icon: const Icon(Icons.remove_circle_outline), onPressed: val > min ? () => setState(() {
            val -= step;
            rc.dispatchEvent(node, 'valueChanged', value: val);
          }) : null),
          Text('$val', style: const TextStyle(fontSize: 18)),
          IconButton(icon: const Icon(Icons.add_circle_outline), onPressed: val < max ? () => setState(() {
            val += step;
            rc.dispatchEvent(node, 'valueChanged', value: val);
          }) : null),
        ]);
      }),
    );
  }

  static Widget upload(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final label = node.propStr('label', '');
    final required = node.propBool('required');
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: InkWell(
        onTap: () {
          if (node.events.containsKey('click')) {
            rc.dispatchEvent(node, 'click');
          } else {
            rc.onShowSnackbar('上传功能待配置');
          }
        },
        borderRadius: BorderRadius.circular(8),
        child: Container(
          padding: const EdgeInsets.all(20), width: double.infinity,
          decoration: BoxDecoration(border: Border.all(color: Colors.grey.shade400, style: BorderStyle.solid), borderRadius: BorderRadius.circular(8)),
          child: Column(children: [
            const Icon(Icons.cloud_upload, size: 40, color: Colors.grey),
            const SizedBox(height: 8),
            Text('点击或拖拽上传（最多 ${node.propNum('max', 5).toInt()} 个）', style: TextStyle(color: Colors.grey.shade600)),
          ]),
        ),
      ),
    );
  }

  static Widget dropdown(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final label = node.propStr('label', '');
    final required = node.propBool('required');
    final items = node.propValues('items');
    if (items.isEmpty) return _emptyPlaceholder('请配置下拉选项');
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: GFDropdown<String>(
        value: items.isNotEmpty ? items[0].toString() : null,
        items: items.map((e) => DropdownMenuItem(value: e.toString(), child: Text(e.toString()))).toList(),
        onChanged: (v) {
          rc.dispatchEvent(node, 'click', value: v);
          rc.dispatchEvent(node, 'valueChanged', value: v);
        },
      ),
    );
  }

  static Widget multiSelect(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final label = node.propStr('label', '');
    final required = node.propBool('required');
    final items = node.propValues('items').map((e) => e.toString()).toList();
    if (items.isEmpty) return _emptyPlaceholder('请配置选项');
    return _formField(
      label: label,
      required: required,
      labelPosition: node.propStr('labelPosition', 'top'),
      child: StatefulBuilder(builder: (context, setState) {
        var selected = <String>{};
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: items.map((item) => Row(children: [
          GFCheckbox(size: GFSize.SMALL, activeBgColor: GFColors.PRIMARY, value: selected.contains(item), onChanged: (v) => setState(() {
            if (v ?? false) selected.add(item); else selected.remove(item);
            rc.dispatchEvent(node, 'click', value: item);
            rc.dispatchEvent(node, 'valueChanged', value: selected.toList());
          })),
          const SizedBox(width: 8), Text(item),
        ])).toList());
      }),
    );
  }

  static Widget checkboxListTile(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return GFCheckboxListTile(value: node.propBool('value'), onChanged: (v) {}, title: Text(node.propStr('title', '标题')), subTitle: node.propStr('subtitle', '').isNotEmpty ? Text(node.propStr('subtitle', '')) : null);
  }

  static Widget radioListTile(SchemaNode node, BuildContext ctx, RenderContext rc) {
    return GFRadioListTile(value: node.propStr('value', '1'), groupValue: node.propStr('groupValue', '1'), onChanged: (v) {}, title: Text(node.propStr('title', '标题')), subTitle: node.propStr('subtitle', '').isNotEmpty ? Text(node.propStr('subtitle', '')) : null);
  }

  static Widget buttonBar(SchemaNode node, BuildContext ctx, RenderContext rc) {
    final buttons = node.propList('buttons');
    if (buttons.isEmpty) return _emptyPlaceholder('请配置按钮');
    return GFButtonBar(children: buttons.map((b) => GFButton(text: b['text']?.toString() ?? '按钮', onPressed: () {})).toList());
  }

  static Widget _formField({required String label, required bool required, required Widget child, String labelPosition = 'top'}) {
    if (label.isEmpty) return Padding(padding: const EdgeInsets.symmetric(vertical: 9, horizontal: 16), child: child);
    final labelWidget = RichText(text: TextSpan(style: const TextStyle(fontSize: 13, color: Color(0xFF303133), fontWeight: FontWeight.w600), children: [if (required) const TextSpan(text: '* ', style: TextStyle(color: Colors.red)), TextSpan(text: label)]));
    if (labelPosition == 'left') {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 9, horizontal: 16),
        child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
          labelWidget,
          const SizedBox(width: 8),
          Expanded(child: child),
        ]),
      );
    }
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 9, horizontal: 16),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Padding(padding: const EdgeInsets.only(bottom: 7), child: labelWidget),
        child,
      ]),
    );
  }

  static Widget _withKeyEvents(SchemaNode node, RenderContext rc, Widget child) {
    return Focus(
      onKeyEvent: (focusNode, event) {
        final eventName = event is KeyDownEvent
            ? 'keyDown'
            : event is KeyUpEvent
                ? 'keyUp'
                : null;
        if (eventName != null) {
          rc.dispatchEvent(node, eventName, value: event.logicalKey.keyLabel);
        }
        return KeyEventResult.ignored;
      },
      onFocusChange: (hasFocus) => rc.dispatchEvent(
        node,
        hasFocus ? 'focusIn' : 'focusOut',
      ),
      child: child,
    );
  }

  static InputDecoration _inputDecoration(String hint, bool readonly) {
    const radius = BorderRadius.all(Radius.circular(12));
    return InputDecoration(
      hintText: hint,
      isDense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      border: const OutlineInputBorder(borderRadius: radius),
      enabledBorder: const OutlineInputBorder(borderRadius: radius, borderSide: BorderSide(color: Color(0xFFDCDFE6))),
      focusedBorder: const OutlineInputBorder(borderRadius: radius, borderSide: BorderSide(color: Color(0xFF1976D2), width: 1.5)),
      filled: true,
      fillColor: readonly ? const Color(0xFFF5F7FA) : const Color(0xFFF8FAFD),
    );
  }
}
