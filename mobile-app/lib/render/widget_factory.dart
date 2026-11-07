import 'package:flutter/material.dart';
import '../models/schema.dart';
import 'schema_renderer.dart';
import '../widgets/layout_widgets.dart';
import '../widgets/form_widgets.dart';
import '../widgets/display_widgets.dart';
import '../widgets/navigation_widgets.dart';
import '../widgets/interaction_widgets.dart';

class WidgetFactory {
  static Widget build(SchemaNode node, BuildContext ctx, RenderContext rc) {
    Widget widget = _buildRaw(node, ctx, rc);
    if (node.type == 'scaffold' || node.type == 'm-scaffold' || node.type == 'm-page-root') {
      if (!SchemaRenderer.editMode && node.events.containsKey('click')) {
        widget = GestureDetector(
          behavior: HitTestBehavior.translucent,
          onTap: () => rc.dispatchEvent(node, 'click'),
          child: widget,
        );
      }
      return widget;
    }
    if (SchemaRenderer.editMode) {
      widget = IgnorePointer(child: widget);
    }
    if ((node.width != null && node.width! > 0) ||
        (node.height != null && node.height! > 0)) {
      widget = SizedBox(
        width: node.width,
        height: node.height,
        child: widget,
      );
    }
    if (!SchemaRenderer.editMode && node.events.containsKey('click')) {
      widget = GestureDetector(
        behavior: HitTestBehavior.translucent,
        onTap: () => rc.dispatchEvent(node, 'click'),
        child: widget,
      );
    }
    return widget;
  }

  static Widget _buildRaw(SchemaNode node, BuildContext ctx, RenderContext rc) {
    switch (node.type) {
      // 布局类
      case 'scaffold':
      case 'm-scaffold':
        return LayoutWidgets.scaffold(node, ctx, rc);
      case 'm-page-root':
        return LayoutWidgets.buildLayoutContent(node, ctx, rc);
      case 'column':
        return LayoutWidgets.column(node, ctx, rc);
      case 'row':
        return LayoutWidgets.row(node, ctx, rc);
      case 'stack':
        return LayoutWidgets.stack(node, ctx, rc);
      case 'grid':
        return LayoutWidgets.grid(node, ctx, rc);
      case 'container':
        return LayoutWidgets.container(node, ctx, rc);
      case 'm-accordion':
        return LayoutWidgets.accordion(node, ctx, rc);

      // 表单类
      case 'text-box':
      case 'm-input':
        return FormWidgets.textBox(node, ctx, rc);
      case 'select':
      case 'select-box':
        return FormWidgets.select(node, ctx, rc);
      case 'date-picker':
      case 'date-box':
        return FormWidgets.datePicker(node, ctx, rc);
      case 'number-field':
      case 'number-box':
        return FormWidgets.numberField(node, ctx, rc);
      case 'switch-field':
      case 'switch':
      case 'm-switch':
        return FormWidgets.switchField(node, ctx, rc);
      case 'textarea':
      case 'text-area':
      case 'm-textarea':
        return FormWidgets.textarea(node, ctx, rc);
      case 'check-box':
        return FormWidgets.switchField(node, ctx, rc);
      case 'm-stepper':
        return FormWidgets.stepper(node, ctx, rc);
      case 'm-rate':
        return FormWidgets.rate(node, ctx, rc);
      case 'm-search':
        return FormWidgets.search(node, ctx, rc);
      case 'm-slider':
        return FormWidgets.slider(node, ctx, rc);
      case 'm-radio':
        return FormWidgets.radio(node, ctx, rc);
      case 'm-checkbox':
        return FormWidgets.checkbox(node, ctx, rc);
      case 'm-upload':
        return FormWidgets.upload(node, ctx, rc);
      case 'm-dropdown':
        return FormWidgets.dropdown(node, ctx, rc);
      case 'm-multi-select':
        return FormWidgets.multiSelect(node, ctx, rc);
      case 'm-checkbox-list-tile':
        return FormWidgets.checkboxListTile(node, ctx, rc);
      case 'm-radio-list-tile':
        return FormWidgets.radioListTile(node, ctx, rc);
      case 'm-button-bar':
        return FormWidgets.buttonBar(node, ctx, rc);

      // 展示类
      case 'list-view':
      case 'm-list-view':
        return DisplayWidgets.listView(node, ctx, rc);
      case 'card':
      case 'm-card':
        return DisplayWidgets.card(node, ctx, rc);
      case 'image':
      case 'm-image':
        return DisplayWidgets.image(node, ctx, rc);
      case 'avatar':
      case 'm-avatar':
        return DisplayWidgets.avatar(node, ctx, rc);
      case 'tag':
      case 'tag-box':
      case 'm-tag':
        return DisplayWidgets.tag(node, ctx, rc);
      case 'm-cell':
        return DisplayWidgets.listView(node, ctx, rc);
      case 'm-badge':
        return DisplayWidgets.badge(node, ctx, rc);
      case 'm-notice-bar':
        return DisplayWidgets.noticeBar(node, ctx, rc);
      case 'm-progress':
        return DisplayWidgets.progress(node, ctx, rc);
      case 'm-divider':
        return DisplayWidgets.divider(node, ctx, rc);
      case 'm-empty':
        return DisplayWidgets.empty(node, ctx, rc);
      case 'm-loading':
        return DisplayWidgets.loading(node, ctx, rc);
      case 'm-result':
        return DisplayWidgets.result(node, ctx, rc);
      case 'm-typography':
        return DisplayWidgets.typography(node, ctx, rc);
      case 'm-animation':
        return DisplayWidgets.animation(node, ctx, rc);
      case 'm-carousel':
        return DisplayWidgets.carousel(node, ctx, rc);
      case 'm-image-overlay':
        return DisplayWidgets.imageOverlay(node, ctx, rc);
      case 'm-button-badge':
        return DisplayWidgets.buttonBadge(node, ctx, rc);
      case 'm-icon-badge':
        return DisplayWidgets.iconBadge(node, ctx, rc);
      case 'm-shimmer':
        return DisplayWidgets.shimmer(node, ctx, rc);
      case 'm-border':
        return DisplayWidgets.border(node, ctx, rc);

      // 导航类
      case 'app-bar':
      case 'm-app-bar':
        return NavigationWidgets.appBar(node, ctx, rc);
      case 'tab-bar':
      case 'm-tab-bar':
        return NavigationWidgets.tabBar(node, ctx, rc);
      case 'bottom-nav':
      case 'm-bottom-nav':
        return NavigationWidgets.bottomNav(node, ctx, rc);
      case 'floating-action-button':
      case 'm-fab':
        return NavigationWidgets.fab(node, ctx, rc);
      case 'm-segment-tabs':
        return NavigationWidgets.segmentTabs(node, ctx, rc);
      case 'm-tabs':
        return NavigationWidgets.tabs(node, ctx, rc);
      case 'm-tab-bar-view':
        return NavigationWidgets.tabBarView(node, ctx, rc);
      case 'm-drawer':
        return NavigationWidgets.drawer(node, ctx, rc);
      case 'm-sticky-header':
        return NavigationWidgets.stickyHeader(node, ctx, rc);

      // 交互类
      case 'button':
      case 'm-button':
        return InteractionWidgets.button(node, ctx, rc);
      case 'dialog':
      case 'm-dialog':
        return InteractionWidgets.dialog(node, ctx, rc);
      case 'snackbar':
      case 'm-toast':
        return InteractionWidgets.snackbar(node, ctx, rc);
      case 'm-action-sheet':
        return InteractionWidgets.actionSheet(node, ctx, rc);
      case 'm-alert':
        return InteractionWidgets.alert(node, ctx, rc);
      case 'm-icon-button':
        return InteractionWidgets.iconButton(node, ctx, rc);
      case 'm-social-button':
        return InteractionWidgets.socialButton(node, ctx, rc);
      case 'm-bottom-sheet':
        return InteractionWidgets.bottomSheet(node, ctx, rc);

      // 容器类
      case 'm-collapse':
      case 'm-popup':
        return DisplayWidgets.card(node, ctx, rc);

      default:
        return Padding(
          padding: const EdgeInsets.all(8),
          child: Text('未知控件: ${node.type}',
              style: const TextStyle(color: Colors.red, fontSize: 12)),
        );
    }
  }
}
