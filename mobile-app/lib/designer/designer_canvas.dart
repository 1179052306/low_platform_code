import 'package:flutter/material.dart';
import '../models/schema.dart';
import '../render/schema_renderer.dart';
import '../render/widget_factory.dart';
import 'designer_state.dart';

class DesignerCanvas extends StatelessWidget {
  final DesignerState state;
  final bool isPreview;
  final GlobalKey canvasKey;
  static const double canvasWidth = 375;
  static const double canvasHeight = 812;

  const DesignerCanvas({required this.state, this.isPreview = false, required this.canvasKey, super.key});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: state,
      builder: (_, __) {
        return Center(child: Container(
          width: canvasWidth,
          height: canvasHeight,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(4),
            border: Border.all(color: const Color(0xFFDFE3E8), width: 1),
            boxShadow: [const BoxShadow(color: Color(0x0F000000), blurRadius: 12, offset: Offset(0, 2))],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: Container(
              key: canvasKey,
              width: canvasWidth,
              height: canvasHeight,
              color: Colors.white,
              child: _renderContent(context),
            ),
          ),
        ));
      },
    );
  }

  Widget _renderContent(BuildContext context) {
    final rc = RenderContext(
      formData: {},
      onNavigate: (pageCode, {params, mode}) {},
      onShowSnackbar: (_) {},
      onRefresh: () async {},
      onClosePage: () {},
    );
    SchemaRenderer.editMode = !isPreview;
    SchemaRenderer.selectedId = state.selectedId;
    SchemaRenderer.designerState = isPreview ? null : state;
    return SchemaRenderer.render(state.root, context, rc);
  }
}