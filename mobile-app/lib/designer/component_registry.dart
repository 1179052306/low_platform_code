import 'dart:convert';

enum PropType { input, number, switchToggle, select, json, color, iconSelect }

class PropOption {
  final String label;
  final String value;
  const PropOption(this.label, this.value);
}

class PropSetter {
  final String prop;
  final String label;
  final PropType type;
  final List<PropOption>? options;
  const PropSetter(this.prop, this.label, this.type, {this.options});
}

class ComponentDef {
  final String type;
  final String title;
  final String category;
  final String icon;
  final bool isContainer;
  final Map<String, dynamic> defaultProps;
  final List<PropSetter> propSetters;
  const ComponentDef({
    required this.type,
    required this.title,
    required this.category,
    required this.icon,
    this.isContainer = false,
    required this.defaultProps,
    required this.propSetters,
  });
}

PropSetter s(String prop, String label, PropType type, [List<PropOption>? options]) =>
    PropSetter(prop, label, type, options: options);

PropSetter jsonS(String prop, String label) => PropSetter(prop, label, PropType.json);

const _layoutOpts = [
  PropOption('纵向排列', 'vertical'),
  PropOption('横向排列', 'horizontal'),
  PropOption('自由定位', 'free'),
];
const _labelPosOpts = [PropOption('上方', 'top'), PropOption('左侧', 'left')];
const _themeOpts = [
  PropOption('主要', 'primary'),
  PropOption('次要', 'default'),
  PropOption('危险', 'danger'),
  PropOption('警告', 'warning'),
];

List<PropSetter> _labelSetters() => [
      s('label', '标签', PropType.input),
      s('labelPosition', '标签位置', PropType.select, _labelPosOpts),
      s('required', '必填', PropType.switchToggle),
    ];

List<PropSetter> _layoutSetters() => [
      s('layout', '布局方式', PropType.select, _layoutOpts),
      s('gap', '子控件间距', PropType.number),
      s('padding', '内边距', PropType.number),
      s('width', '宽度', PropType.input),
      s('height', '高度', PropType.input),
    ];

final _allComponents = <ComponentDef>[
  ComponentDef(type: 'm-page-root', title: '页面根', category: '移动端布局', icon: '📄', isContainer: true,
    defaultProps: {'layout': 'free', 'gap': 8, 'padding': 0},
    propSetters: [s('layout', '布局方式', PropType.select, _layoutOpts), s('gap', '子控件间距', PropType.number), s('padding', '内边距', PropType.number)]),
  ComponentDef(type: 'm-scaffold', title: '页面骨架', category: '移动端布局', icon: '📱', isContainer: true,
    defaultProps: {'title': '页面标题', 'appBarColor': '#1976D2', 'width': '100%', 'height': '100%', 'layout': 'vertical', 'gap': 8, 'padding': 0},
    propSetters: [s('title', '标题', PropType.input), s('appBarColor', '导航栏颜色', PropType.color), ..._layoutSetters()]),
  ComponentDef(type: 'm-list-view', title: '列表', category: '移动端展示', icon: '📋',
    defaultProps: {'bizCode': '', 'primaryKey': 'id'}, propSetters: [s('bizCode', '业务编码', PropType.input), s('primaryKey', '主键字段', PropType.input)]),
  ComponentDef(type: 'm-card', title: '卡片', category: '移动端布局', icon: '📇', isContainer: true,
    defaultProps: {'elevation': 2, 'width': '100%', 'layout': 'vertical', 'gap': 8, 'padding': 8},
    propSetters: [s('elevation', '阴影', PropType.number), ..._layoutSetters()]),
  ComponentDef(type: 'm-app-bar', title: '导航栏', category: '移动端布局', icon: '◀',
    defaultProps: {'title': '标题', 'backgroundColor': '#1976D2', 'showBack': true},
    propSetters: [s('title', '标题', PropType.input), s('backgroundColor', '背景色', PropType.color), s('showBack', '显示返回', PropType.switchToggle), s('width', '宽度', PropType.input), s('height', '高度', PropType.input)]),
  ComponentDef(type: 'm-bottom-nav', title: '底部导航', category: '移动端布局', icon: '⬇',
    defaultProps: {}, propSetters: [s('width', '宽度', PropType.input), s('height', '高度', PropType.input)]),
  ComponentDef(type: 'm-fab', title: '悬浮按钮', category: '移动端布局', icon: '➕',
    defaultProps: {'icon': 'add'}, propSetters: [s('icon', '图标', PropType.iconSelect), s('width', '宽度', PropType.input), s('height', '高度', PropType.input)]),
  ComponentDef(type: 'm-avatar', title: '头像', category: '移动端展示', icon: '👤',
    defaultProps: {'src': '', 'radius': 24}, propSetters: [s('src', '图片地址', PropType.input), s('radius', '半径', PropType.number)]),
  ComponentDef(type: 'm-tab-bar', title: '选项卡', category: '移动端布局', icon: '📑', isContainer: true,
    defaultProps: {'layout': 'vertical', 'gap': 8, 'padding': 8}, propSetters: _layoutSetters()),
  ComponentDef(type: 'm-image', title: '图片', category: '移动端展示', icon: '🖼',
    defaultProps: {'src': '', 'height': 100}, propSetters: [s('src', '图片地址', PropType.input), s('height', '高度', PropType.number)]),
  ComponentDef(type: 'm-button', title: '按钮', category: '移动端表单', icon: '🔘',
    defaultProps: {'text': '按钮', 'theme': 'primary', 'size': 'medium'},
    propSetters: [s('text', '按钮文字', PropType.input), s('theme', '主题', PropType.select, _themeOpts),
      s('size', '尺寸', PropType.select, [PropOption('大', 'large'), PropOption('中', 'medium'), PropOption('小', 'small')])]),
  ComponentDef(type: 'm-input', title: '输入框', category: '移动端表单', icon: '⌨',
    defaultProps: {'placeholder': '请输入', 'label': '标签', 'clearable': true, 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), s('placeholder', '占位文字', PropType.input), s('clearable', '可清空', PropType.switchToggle)]),
  ComponentDef(type: 'm-textarea', title: '多行输入', category: '移动端表单', icon: '📝',
    defaultProps: {'placeholder': '请输入内容', 'maxlength': 200, 'label': '多行输入', 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), s('placeholder', '占位文字', PropType.input), s('maxlength', '最大字数', PropType.number)]),
  ComponentDef(type: 'm-switch', title: '开关', category: '移动端表单', icon: '🔄',
    defaultProps: {'label': '开关', 'value': true, 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), s('value', '开启', PropType.switchToggle)]),
  ComponentDef(type: 'm-stepper', title: '步进器', category: '移动端表单', icon: '🔢',
    defaultProps: {'value': 1, 'min': 0, 'max': 10, 'step': 1, 'label': '步进器', 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), s('value', '当前值', PropType.number), s('min', '最小值', PropType.number), s('max', '最大值', PropType.number), s('step', '步长', PropType.number)]),
  ComponentDef(type: 'm-rate', title: '评分', category: '移动端表单', icon: '⭐',
    defaultProps: {'value': 3, 'max': 5, 'allowHalf': false, 'label': '评分', 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), s('value', '当前值', PropType.number), s('max', '最大星数', PropType.number), s('allowHalf', '允许半选', PropType.switchToggle)]),
  ComponentDef(type: 'm-search', title: '搜索框', category: '移动端表单', icon: '🔍',
    defaultProps: {'placeholder': '搜索关键词', 'label': '搜索', 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), s('placeholder', '占位文字', PropType.input)]),
  ComponentDef(type: 'm-slider', title: '滑块', category: '移动端表单', icon: '⟟',
    defaultProps: {'value': 50, 'min': 0, 'max': 100, 'label': '滑块', 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), s('value', '当前值', PropType.number), s('min', '最小值', PropType.number), s('max', '最大值', PropType.number)]),
  ComponentDef(type: 'm-radio', title: '单选组', category: '移动端表单', icon: '🔘',
    defaultProps: {'options': [{'label': '选项一', 'value': '1'}, {'label': '选项二', 'value': '2'}], 'value': '1', 'label': '单选组', 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), jsonS('options', '选项'), s('value', '默认值', PropType.input)]),
  ComponentDef(type: 'm-checkbox', title: '多选组', category: '移动端表单', icon: '☑',
    defaultProps: {'options': [{'label': '选项一', 'value': '1'}, {'label': '选项二', 'value': '2'}], 'value': ['1'], 'label': '多选组', 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), jsonS('options', '选项'), jsonS('value', '选中值')]),
  ComponentDef(type: 'm-upload', title: '上传', category: '移动端表单', icon: '⬆',
    defaultProps: {'max': 5, 'label': '上传', 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), s('max', '最大数量', PropType.number)]),
  ComponentDef(type: 'm-cell', title: '单元格', category: '移动端展示', icon: '📋',
    defaultProps: {'title': '标题', 'description': '描述', 'arrow': true},
    propSetters: [s('title', '标题', PropType.input), s('description', '描述', PropType.input), s('arrow', '显示箭头', PropType.switchToggle)]),
  ComponentDef(type: 'm-tag', title: '标签', category: '移动端展示', icon: '🏷',
    defaultProps: {'text': '标签', 'theme': 'primary'},
    propSetters: [s('text', '文字', PropType.input), s('theme', '主题', PropType.select, [PropOption('主要', 'primary'), PropOption('成功', 'success'), PropOption('警告', 'warning'), PropOption('危险', 'danger')])]),
  ComponentDef(type: 'm-badge', title: '徽标', category: '移动端展示', icon: '🔔',
    defaultProps: {'text': '新', 'dot': false}, propSetters: [s('text', '内容', PropType.input), s('dot', '圆点', PropType.switchToggle)]),
  ComponentDef(type: 'm-notice-bar', title: '公告栏', category: '移动端展示', icon: '📢',
    defaultProps: {'text': '这是一条公告信息'}, propSetters: [s('text', '公告内容', PropType.input)]),
  ComponentDef(type: 'm-progress', title: '进度条', category: '移动端展示', icon: '📊',
    defaultProps: {'value': 60, 'max': 100}, propSetters: [s('value', '当前值', PropType.number), s('max', '最大值', PropType.number)]),
  ComponentDef(type: 'm-divider', title: '分割线', category: '移动端展示', icon: '➖',
    defaultProps: {'text': ''}, propSetters: [s('text', '文字', PropType.input)]),
  ComponentDef(type: 'm-collapse', title: '折叠面板', category: '移动端布局', icon: '📂', isContainer: true,
    defaultProps: {'layout': 'vertical', 'gap': 8, 'padding': 8},
    propSetters: [s('layout', '布局方式', PropType.select, [PropOption('纵向排列', 'vertical'), PropOption('横向排列', 'horizontal')]), s('gap', '间距', PropType.number), s('padding', '内边距', PropType.number), s('width', '宽度', PropType.input), s('height', '高度', PropType.input)]),
  ComponentDef(type: 'm-empty', title: '空状态', category: '移动端展示', icon: '🕳',
    defaultProps: {'description': '暂无数据'}, propSetters: [s('description', '描述', PropType.input)]),
  ComponentDef(type: 'm-loading', title: '加载', category: '移动端展示', icon: '⏳',
    defaultProps: {'text': '加载中...'}, propSetters: [s('text', '文字', PropType.input)]),
  ComponentDef(type: 'm-result', title: '结果页', category: '移动端展示', icon: '✅',
    defaultProps: {'title': '操作成功', 'description': '内容详情'}, propSetters: [s('title', '标题', PropType.input), s('description', '描述', PropType.input)]),
  ComponentDef(type: 'm-toast', title: '轻提示', category: '移动端弹层', icon: '💬',
    defaultProps: {'text': '提示内容', 'theme': 'info'},
    propSetters: [s('text', '内容', PropType.input), s('theme', '类型', PropType.select, [PropOption('信息', 'info'), PropOption('成功', 'success'), PropOption('警告', 'warning'), PropOption('错误', 'error')])]),
  ComponentDef(type: 'm-dialog', title: '对话框', category: '移动端弹层', icon: '❓',
    defaultProps: {'title': '标题', 'content': '对话框内容', 'confirmBtn': '确认', 'cancelBtn': '取消'},
    propSetters: [s('title', '标题', PropType.input), s('content', '内容', PropType.input), s('confirmBtn', '确认按钮', PropType.input), s('cancelBtn', '取消按钮', PropType.input)]),
  ComponentDef(type: 'm-action-sheet', title: '动作面板', category: '移动端弹层', icon: '📂',
    defaultProps: {'items': ['操作一', '操作二', '操作三']}, propSetters: [jsonS('items', '操作项')]),
  ComponentDef(type: 'm-popup', title: '弹出层', category: '移动端布局', icon: '📦', isContainer: true,
    defaultProps: {'visible': true, 'placement': 'bottom', 'layout': 'vertical', 'gap': 8, 'padding': 16},
    propSetters: [s('visible', '显示', PropType.switchToggle), s('placement', '位置', PropType.select, [PropOption('底部', 'bottom'), PropOption('顶部', 'top'), PropOption('左侧', 'left'), PropOption('右侧', 'right'), PropOption('居中', 'center')]), s('layout', '布局方式', PropType.select, [PropOption('纵向', 'vertical'), PropOption('横向', 'horizontal')]), s('gap', '间距', PropType.number), s('padding', '内边距', PropType.number), s('width', '宽度', PropType.input), s('height', '高度', PropType.input)]),
  ComponentDef(type: 'm-accordion', title: '手风琴', category: '移动端布局', icon: '📂',
    defaultProps: {'items': [{'title': '标题一', 'content': '内容一'}, {'title': '标题二', 'content': '内容二'}]}, propSetters: [jsonS('items', '折叠项')]),
  ComponentDef(type: 'm-border', title: '边框容器', category: '移动端布局', icon: '🔲', isContainer: true,
    defaultProps: {'layout': 'vertical', 'gap': 8, 'padding': 8},
    propSetters: [s('layout', '布局方式', PropType.select, [PropOption('纵向', 'vertical'), PropOption('横向', 'horizontal')]), s('gap', '间距', PropType.number), s('padding', '内边距', PropType.number), s('width', '宽度', PropType.input), s('height', '高度', PropType.input)]),
  ComponentDef(type: 'm-dropdown', title: '下拉选择', category: '移动端表单', icon: '▼',
    defaultProps: {'items': ['选项一', '选项二', '选项三'], 'label': '下拉选择', 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), jsonS('items', '选项')]),
  ComponentDef(type: 'm-multi-select', title: '多选', category: '移动端表单', icon: '☑',
    defaultProps: {'items': ['选项一', '选项二', '选项三'], 'label': '多选', 'labelPosition': 'top', 'required': false},
    propSetters: [..._labelSetters(), jsonS('items', '选项')]),
  ComponentDef(type: 'm-checkbox-list-tile', title: '复选列表项', category: '移动端表单', icon: '☑',
    defaultProps: {'title': '标题', 'subtitle': '副标题', 'value': false},
    propSetters: [s('title', '标题', PropType.input), s('subtitle', '副标题', PropType.input), s('value', '选中', PropType.switchToggle)]),
  ComponentDef(type: 'm-radio-list-tile', title: '单选列表项', category: '移动端表单', icon: '◉',
    defaultProps: {'title': '标题', 'subtitle': '副标题', 'value': '1', 'groupValue': '1'},
    propSetters: [s('title', '标题', PropType.input), s('subtitle', '副标题', PropType.input), s('value', '值', PropType.input), s('groupValue', '组值', PropType.input)]),
  ComponentDef(type: 'm-button-bar', title: '按钮栏', category: '移动端表单', icon: '▢▢',
    defaultProps: {'buttons': [{'text': '确定'}, {'text': '取消'}]}, propSetters: [jsonS('buttons', '按钮')]),
  ComponentDef(type: 'm-typography', title: '排版文字', category: '移动端展示', icon: '📝',
    defaultProps: {'text': '文字内容'}, propSetters: [s('text', '文字', PropType.input)]),
  ComponentDef(type: 'm-animation', title: '动画', category: '移动端展示', icon: '✨',
    defaultProps: {'text': '动画文字'}, propSetters: [s('text', '文字', PropType.input)]),
  ComponentDef(type: 'm-carousel', title: '轮播', category: '移动端展示', icon: '🎠',
    defaultProps: {'items': [{'image': 'https://picsum.photos/400/200'}]}, propSetters: [jsonS('items', '轮播项')]),
  ComponentDef(type: 'm-image-overlay', title: '图片覆盖层', category: '移动端展示', icon: '🖼',
    defaultProps: {'src': '', 'title': '标题', 'subtitle': '副标题'},
    propSetters: [s('src', '图片地址', PropType.input), s('title', '标题', PropType.input), s('subtitle', '副标题', PropType.input)]),
  ComponentDef(type: 'm-button-badge', title: '按钮徽标', category: '移动端展示', icon: '🔔',
    defaultProps: {'text': '按钮', 'badgeText': '1'}, propSetters: [s('text', '按钮文字', PropType.input), s('badgeText', '徽标文字', PropType.input)]),
  ComponentDef(type: 'm-icon-badge', title: '图标徽标', category: '移动端展示', icon: '🔔',
    defaultProps: {'text': '1'}, propSetters: [s('text', '徽标文字', PropType.input)]),
  ComponentDef(type: 'm-shimmer', title: '骨架屏', category: '移动端展示', icon: '⏳',
    defaultProps: {}, propSetters: []),
  ComponentDef(type: 'm-segment-tabs', title: '分段标签', category: '移动端布局', icon: '📑',
    defaultProps: {'tabs': [{'label': '标签一'}, {'label': '标签二'}]}, propSetters: [jsonS('tabs', '标签项')]),
  ComponentDef(type: 'm-tabs', title: '标签栏', category: '移动端布局', icon: '📑',
    defaultProps: {'tabs': [{'label': '标签一'}, {'label': '标签二'}]}, propSetters: [jsonS('tabs', '标签项')]),
  ComponentDef(type: 'm-tab-bar-view', title: '标签视图', category: '移动端布局', icon: '📑',
    defaultProps: {'tabs': [{'label': '页面一'}, {'label': '页面二'}]}, propSetters: [jsonS('tabs', '标签项')]),
  ComponentDef(type: 'm-drawer', title: '抽屉', category: '移动端布局', icon: '📂',
    defaultProps: {'items': [{'label': '菜单项一'}, {'label': '菜单项二'}]}, propSetters: [jsonS('items', '菜单项')]),
  ComponentDef(type: 'm-sticky-header', title: '粘性头部', category: '移动端布局', icon: '📌', isContainer: true,
    defaultProps: {'title': '标题', 'layout': 'vertical', 'gap': 8, 'padding': 8},
    propSetters: [s('title', '标题', PropType.input), s('layout', '布局方式', PropType.select, [PropOption('纵向', 'vertical'), PropOption('横向', 'horizontal')]), s('gap', '间距', PropType.number), s('padding', '内边距', PropType.number)]),
  ComponentDef(type: 'm-alert', title: '警告框', category: '移动端弹层', icon: '⚠',
    defaultProps: {'title': '提示', 'content': '警告内容'}, propSetters: [s('title', '标题', PropType.input), s('content', '内容', PropType.input)]),
  ComponentDef(type: 'm-bottom-sheet', title: '底部弹窗', category: '移动端弹层', icon: '⬇',
    defaultProps: {'title': '底部弹窗'}, propSetters: [s('title', '标题', PropType.input)]),
  ComponentDef(type: 'm-icon-button', title: '图标按钮', category: '移动端表单', icon: '🔘',
    defaultProps: {'icon': 'add'}, propSetters: [s('icon', '图标', PropType.iconSelect)]),
  ComponentDef(type: 'm-social-button', title: '社交按钮', category: '移动端表单', icon: '👥',
    defaultProps: {'text': '社交登录'}, propSetters: [s('text', '文字', PropType.input)]),
];

final _componentMap = {for (final c in _allComponents) c.type: c};

class ComponentRegistry {
  static ComponentDef? get(String type) => _componentMap[type];
  static List<ComponentDef> get all => _allComponents;
  static List<String> get categories => _allComponents.map((c) => c.category).toSet().toList();
  static List<ComponentDef> byCategory(String cat) => _allComponents.where((c) => c.category == cat).toList();
  static bool isContainer(String type) => _componentMap[type]?.isContainer ?? false;

  static Map<String, dynamic> defaultPropsFor(String type) {
    final def = _componentMap[type];
    if (def == null) return {};
    return _deepCopy(def.defaultProps);
  }

  static Map<String, dynamic> _deepCopy(Map<String, dynamic> src) {
    return jsonDecode(jsonEncode(src)) as Map<String, dynamic>;
  }
}