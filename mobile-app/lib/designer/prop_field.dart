import 'dart:convert';
import 'package:flutter/material.dart';
import 'component_registry.dart';

class PropFieldFocus {
  static bool hasFocus = false;
}

class PropRow extends StatelessWidget {
  final String label;
  final Widget child;
  const PropRow(this.label, {required this.child, super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: Row(
        children: [
          SizedBox(width: 72, child: Text(label, style: const TextStyle(fontSize: 12, color: Color(0xFF606266)))),
          const SizedBox(width: 8),
          Expanded(child: child),
        ],
      ),
    );
  }
}

class PropTextField extends StatefulWidget {
  final String label;
  final String value;
  final void Function(String) onChange;
  const PropTextField(this.label, {required this.value, required this.onChange, super.key});

  @override
  State<PropTextField> createState() => _PropTextFieldState();
}

class _PropTextFieldState extends State<PropTextField> {
  late TextEditingController _ctrl;
  @override
  void initState() {
    super.initState();
    _ctrl = TextEditingController(text: widget.value);
  }

  @override
  void didUpdateWidget(PropTextField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value && _ctrl.text != widget.value) {
      _ctrl.text = widget.value;
    }
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PropRow(widget.label, child: Focus(
      onFocusChange: (hasFocus) => PropFieldFocus.hasFocus = hasFocus,
      child: TextField(
        controller: _ctrl,
        style: const TextStyle(fontSize: 13),
        decoration: const InputDecoration(isDense: true, contentPadding: EdgeInsets.symmetric(horizontal: 8, vertical: 8)),
        onChanged: widget.onChange,
      ),
    ));
  }
}

class PropNumberField extends StatefulWidget {
  final String label;
  final num value;
  final void Function(num) onChange;
  const PropNumberField(this.label, {required this.value, required this.onChange, super.key});

  @override
  State<PropNumberField> createState() => _PropNumberFieldState();
}

class _PropNumberFieldState extends State<PropNumberField> {
  late TextEditingController _ctrl;
  @override
  void initState() {
    super.initState();
    _ctrl = TextEditingController(text: widget.value.toString());
  }

  @override
  void didUpdateWidget(PropNumberField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value && _ctrl.text != widget.value.toString()) {
      _ctrl.text = widget.value.toString();
    }
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PropRow(widget.label, child: Focus(
      onFocusChange: (hasFocus) => PropFieldFocus.hasFocus = hasFocus,
      child: TextField(
        controller: _ctrl,
        style: const TextStyle(fontSize: 13),
        keyboardType: TextInputType.number,
        decoration: const InputDecoration(isDense: true, contentPadding: EdgeInsets.symmetric(horizontal: 8, vertical: 8)),
        onChanged: (v) {
          final n = num.tryParse(v);
          if (n != null) widget.onChange(n);
        },
      ),
    ));
  }
}

class PropSwitch extends StatelessWidget {
  final String label;
  final bool value;
  final void Function(bool) onChange;
  const PropSwitch(this.label, {required this.value, required this.onChange, super.key});

  @override
  Widget build(BuildContext context) {
    return PropRow(label, child: Align(
      alignment: Alignment.centerLeft,
      child: Switch(value: value, onChanged: onChange, materialTapTargetSize: MaterialTapTargetSize.shrinkWrap),
    ));
  }
}

class PropSelect extends StatefulWidget {
  final String label;
  final String value;
  final List<PropOption> options;
  final void Function(String) onChange;
  const PropSelect(this.label, {required this.value, required this.options, required this.onChange, super.key});

  @override
  State<PropSelect> createState() => _PropSelectState();
}

class _PropSelectState extends State<PropSelect> {
  late String _currentValue;

  @override
  void initState() {
    super.initState();
    _currentValue = widget.value;
  }

  @override
  void didUpdateWidget(PropSelect oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value) {
      _currentValue = widget.value;
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentLabel = widget.options.firstWhere(
      (o) => o.value == _currentValue,
      orElse: () => PropOption(_currentValue, _currentValue),
    ).label;
    return PropRow(widget.label, child: PopupMenuButton<String>(
      itemBuilder: (ctx) => widget.options.map((o) => PopupMenuItem<String>(
        value: o.value,
        child: Text(o.label, style: const TextStyle(fontSize: 13)),
      )).toList(),
      onSelected: (v) {
        setState(() => _currentValue = v);
        widget.onChange(v);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        decoration: BoxDecoration(borderRadius: BorderRadius.circular(4), color: const Color(0xFFF5F7FA)),
        child: Row(children: [
          Expanded(child: Text(currentLabel, style: const TextStyle(fontSize: 13, color: Color(0xFF303133)))),
          const Icon(Icons.arrow_drop_down, size: 18, color: Color(0xFF909399)),
        ]),
      ),
    ));
  }
}


class PropJsonField extends StatefulWidget {
  final String label;
  final dynamic value;
  final void Function(dynamic) onChange;
  const PropJsonField(this.label, {required this.value, required this.onChange, super.key});

  @override
  State<PropJsonField> createState() => _PropJsonFieldState();
}

class _PropJsonFieldState extends State<PropJsonField> {
  late TextEditingController _ctrl;
  String _error = '';

  @override
  void initState() {
    super.initState();
    _ctrl = TextEditingController(text: _encode(widget.value));
  }

  String _encode(dynamic v) {
    try {
      return const JsonEncoder.withIndent('  ').convert(v);
    } catch (_) {
      return v?.toString() ?? '';
    }
  }

  @override
  void didUpdateWidget(PropJsonField oldWidget) {
    super.didUpdateWidget(oldWidget);
    final encoded = _encode(widget.value);
    if (oldWidget.value != widget.value && _ctrl.text != encoded) {
      _ctrl.text = encoded;
    }
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PropRow(widget.label, child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Focus(
          onFocusChange: (hasFocus) => PropFieldFocus.hasFocus = hasFocus,
          child: TextField(
            controller: _ctrl,
            style: const TextStyle(fontSize: 12, fontFamily: 'monospace'),
            maxLines: 4,
            decoration: InputDecoration(
              isDense: true,
              contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
              errorText: _error.isEmpty ? null : _error,
            ),
            onChanged: (v) {
              try {
                final decoded = jsonDecode(v);
                setState(() => _error = '');
                widget.onChange(decoded);
              } catch (e) {
                setState(() => _error = 'JSON 格式错误');
              }
            },
          ),
        ),
      ],
    ));
  }
}

class PropColorField extends StatelessWidget {
  final String label;
  final String value;
  final void Function(String) onChange;
  const PropColorField(this.label, {required this.value, required this.onChange, super.key});

  Color? get _color {
    final hex = value.replaceAll('#', '');
    if (hex.length == 6) {
      return Color(int.parse('FF$hex', radix: 16));
    }
    if (hex.length == 8) {
      return Color(int.parse(hex, radix: 16));
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final color = _color ?? const Color(0xFF1976D2);
    return PropRow(label, child: Row(
      children: [
        GestureDetector(
          onTap: () async {
            final picked = await showDialog<Color>(
              context: context,
              builder: (_) => AlertDialog(
                contentPadding: EdgeInsets.zero,
                content: SizedBox(width: 300, height: 400, child: ColorPicker(color: color, onColorChanged: (c) {
                  final hex = c.toARGB32().toRadixString(16).toUpperCase().padLeft(8, '0');
                  onChange('#${hex.substring(2)}');
                })),
              ),
            );
            if (picked != null) {
              final hex = picked.toARGB32().toRadixString(16).toUpperCase().padLeft(8, '0');
              onChange('#${hex.substring(2)}');
            }
          },
          child: Container(width: 28, height: 28, decoration: BoxDecoration(color: color, border: Border.all(color: Colors.grey.shade400), borderRadius: BorderRadius.circular(4))),
        ),
        const SizedBox(width: 8),
        Expanded(child: Focus(
          onFocusChange: (hasFocus) => PropFieldFocus.hasFocus = hasFocus,
          child: TextField(
            style: const TextStyle(fontSize: 12, fontFamily: 'monospace'),
            decoration: const InputDecoration(isDense: true, contentPadding: EdgeInsets.symmetric(horizontal: 8, vertical: 8)),
            controller: TextEditingController(text: value),
            onChanged: onChange,
          ),
        )),
      ],
    ));
  }
}

class ColorPicker extends StatefulWidget {
  final Color color;
  final void Function(Color) onColorChanged;
  const ColorPicker({required this.color, required this.onColorChanged, super.key});

  @override
  State<ColorPicker> createState() => _ColorPickerState();
}

class _ColorPickerState extends State<ColorPicker> {
  late double _hue;
  late double _saturation;
  late double _value;

  @override
  void initState() {
    super.initState();
    final hsv = HSVColor.fromColor(widget.color);
    _hue = hsv.hue;
    _saturation = hsv.saturation;
    _value = hsv.value;
  }

  void _notify() {
    widget.onColorChanged(HSVColor.fromAHSV(1.0, _hue, _saturation, _value).toColor());
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Expanded(child: GestureDetector(
          onPanUpdate: (d) {
            setState(() {
              _saturation = (_saturation + d.delta.dx / 300).clamp(0, 1);
              _value = (_value - d.delta.dy / 400).clamp(0, 1);
            });
            _notify();
          },
          child: Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
                colors: [Colors.white, HSVColor.fromAHSV(1.0, _hue, 1.0, 1.0).toColor()],
              ),
            ),
            child: Stack(children: [
              Container(decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Colors.transparent, Colors.black]))),
              Positioned(left: _saturation * 300 - 6, top: (1 - _value) * 400 - 6, child: Container(width: 12, height: 12, decoration: BoxDecoration(border: Border.all(color: Colors.white, width: 2), shape: BoxShape.circle))),
            ]),
          ),
        )),
        SizedBox(height: 40, child: GestureDetector(
          onPanUpdate: (d) {
            setState(() => _hue = (_hue + d.delta.dx / 300 * 360) % 360);
            _notify();
          },
          child: Container(decoration: BoxDecoration(gradient: LinearGradient(colors: List.generate(7, (i) => HSVColor.fromAHSV(1.0, i * 60.0, 1.0, 1.0).toColor())))),
        )),
      ],
    );
  }
}