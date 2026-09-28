import type { ComponentSchema, MaterialConfig, PropSetterConfig } from '@/types/schema'
import type { Component } from 'vue'

import { HIDDEN_MATERIAL_TYPES } from '@/constants/container-types'
import { dxComponents, dxMaterialConfigs } from './dx-materials'
import { dxPropMeta, type DxPropMeta } from './dx-prop-meta'
import { translatePropName } from './prop-labels'
import { VALIDATABLE_TYPES } from './dx-validation'
import ElRow from './components/ElRow.vue'
import ElCol from './components/ElCol.vue'
import ElSplitter from './components/ElSplitter.vue'
import ElSplitterPanel from './components/ElSplitterPanel.vue'
import ElTabs from './components/ElTabs.vue'
import ElTabPane from './components/ElTabPane.vue'
import ImageView from './components/ImageView.vue'
import LinkView from './components/LinkView.vue'
import IframeView from './components/IframeView.vue'
import DxBaseData from './components/DxBaseData.vue'

/** 只读/输出状态属性：不是可配置项，配了也不会产生可预期的外观变化 */
const NON_CONFIGURABLE_PROPS = new Set([
  'isDirty',
  'isValid',
  'validationError',
  'validationErrors',
  'validationStatus',
  'validationMessageMode',
  'validationMessagePosition',
  'opened',
  'selectedItemKeys',
  'width',
  'height',
  'selectedItems',
  'selectedRowKeys',
  'focusedRowIndex',
  'focusedRowKey',
  'focusedColumnIndex',
  'typingUsers',
  'alerts',
])

/** 复杂/对象属性：不适合在基础配置标签页以文本/JSON方式编辑。
 *  这些属性要么是复杂嵌套结构（buttons、validationRules），
 *  要么是DevExtreme内部实现细节（elementAttr、inputAttr），
 *  要么需要专业知识才能正确配置（dataSource、items、columns等）。
 *  它们仍可在「全部属性」标签页中查看/编辑。
 *  
 *  注意：stylingMode、validationMessageMode、validationMessagePosition
 *  等枚举属性不应在此过滤，它们在 dx-prop-meta.ts 中已定义为 kind:"select"，
 *  会自动转为下拉选择器。
 */
const COMPLEX_PROPS = new Set([
  // 事件回调
  'onFocus', 'onBlur', 'onClick', 'onChange', 'onInput', 'onKeyDown', 'onKeyUp',
  'onMouseDown', 'onMouseUp', 'onMouseEnter', 'onMouseLeave', 'onTouchStart', 'onTouchEnd',
  'onItemClick', 'onItemSelected', 'onValueChanged', 'onSelectionChanged',
  'onToolbarButtonClick', 'onFileUploaded', 'onUploadError', 'onEditorPreparing',
  'onEditorPrepared', 'onRowInserting', 'onRowUpdating', 'onRowRemoving',
  'onInitNewRow', 'onCellClick', 'onCellDblClick', 'onRowClick', 'onRowDblClick',
  'onContentReady', 'onDisposed', 'onOptionChanged', 'onContextMenuPreparing',
  'onCellPrepared', 'onRowPrepared', 'onInit',
  // 复杂按钮配置（数组/对象结构，不适合简单配置）
  'buttons',
  // 复杂嵌套对象/数组（内部实现细节或专业配置）
  'elementAttr', 'inputAttr', 'contentAttr', 'dropDownOptions',
  'validationRules', 'items', 'columns', 'masterDetail',
  'paging', 'pager', 'searchPanel', 'searchExpr',
  'scrolling', 'filterRow', 'filterPanel', 'headerFilter',
  'export', 'stateStoring', 'editing', 'grouping', 'sorting',
  'selection', 'loadPanel', 'rowDragging', 'keyboardNavigation',
  'columnFixing', 'columnResizing', 'rowReordering', 'lookup',
  // 数据源相关（需要专业配置）
  'dataSource', 'dataStore', 'store', 'customLoadOptions',
  'remoteOperations', 'keyExpr', 'displayExpr', 'valueExpr',
  // 图表专用复杂配置
  'series', 'seriesTemplate', 'argumentAxis', 'valueAxis',
  'tooltip', 'legend', 'title', 'loadingIndicator',
  'commonAnnotationSettings', 'commonSeriesSettings',
  'customPointText', 'defaultPane', 'panes',
  // 布局容器内部
  'layout', 'itemTemplate',
  // 文件上传
  'fileSystemProvider', 'uploadOptions',
  // 其他高级属性
  'animation', 'annotations', 'adaptiveLayout',
  'render', 'template', 'dropDown', 'popup',
  'position', 'toolbar', 'contextMenu',
  // Chat 组件
  'alerts', 'typingUsers', 'messages', 'participants',
  // TextBox/NumberBox 掩码配置（复杂结构）
  'maskRules',
])

/** 已知的 DevExtreme 枚举型字符串属性映射（自动转为下拉选择器）。
 *  注意：dx-prop-meta.ts 中已定义为 kind:"select" 的属性（如 stylingMode、
 *  mode、validationMessageMode 等）会优先使用组件特定选项，
 *  这里仅保留那些在 dx-prop-meta.ts 中没有定义的全局通用映射。
 */
const KNOWN_SELECT_PROPS: Record<string, { label: string; value: string }[]> = {
  // 对齐方式
  align: [
    { label: '左对齐', value: 'left' },
    { label: '居中', value: 'center' },
    { label: '右对齐', value: 'right' },
  ],
  // 标签位置（布局容器专用）
  labelLocation: [
    { label: '左侧', value: 'left' },
    { label: '顶部', value: 'top' },
    { label: '右侧', value: 'right' },
  ],
  // 编辑器类型（Form/Table 专用）
  editorType: [
    { label: '文本框', value: 'dxTextBox' },
    { label: '数字框', value: 'dxNumberBox' },
    { label: '日期框', value: 'dxDateBox' },
    { label: '下拉框', value: 'dxSelectBox' },
    { label: '复选框', value: 'dxCheckBox' },
    { label: '开关', value: 'dxSwitch' },
  ],
  // 表格编辑模式
  editingMode: [
    { label: '行编辑', value: 'row' },
    { label: '单元格编辑', value: 'cell' },
    { label: '批量编辑', value: 'batch' },
    { label: '表单编辑', value: 'form' },
    { label: '弹窗编辑', value: 'popup' },
  ],
  // 选择模式（DataGrid 专用）
  selectionMode: [
    { label: '无', value: 'none' },
    { label: '单选', value: 'single' },
    { label: '多选', value: 'multiple' },
  ],
  // 排序模式（DataGrid 专用）
  sortMode: [
    { label: '单列', value: 'single' },
    { label: '多列', value: 'multiple' },
    { label: '禁用', value: 'none' },
  ],
  // 筛选操作符（DataGrid 专用）
  filterOperation: [
    { label: '等于', value: '=' },
    { label: '不等于', value: '<>' },
    { label: '小于', value: '<' },
    { label: '大于', value: '>' },
    { label: '小于等于', value: '<=' },
    { label: '大于等于', value: '>=' },
    { label: '包含', value: 'contains' },
    { label: '不包含', value: 'notcontains' },
    { label: '以...开头', value: 'startswith' },
    { label: '以...结尾', value: 'endswith' },
  ],
  // 弹窗位置（全局通用）
  position: [
    { label: '顶部', value: 'top' },
    { label: '底部', value: 'bottom' },
    { label: '左侧', value: 'left' },
    { label: '右侧', value: 'right' },
    { label: '居中', value: 'center' },
  ],
  // Toast 类型
  toastType: [
    { label: '信息', value: 'info' },
    { label: '成功', value: 'success' },
    { label: '警告', value: 'warning' },
    { label: '错误', value: 'error' },
  ],
  // 图表类型（Chart 专用）
  chartType: [
    { label: '折线', value: 'line' },
    { label: '面积', value: 'area' },
    { label: '柱状', value: 'bar' },
    { label: '饼图', value: 'pie' },
    { label: '散点', value: 'scatter' },
  ],
  // 布局方向（Splitter/Layout 专用）
  layoutMode: [
    { label: '横向', value: 'horizontal' },
    { label: '纵向', value: 'vertical' },
  ],
  // 日历缩放级别（Calendar/Scheduler 专用）
  zoomLevel: [
    { label: '月', value: 'month' },
    { label: '年', value: 'year' },
    { label: '十年', value: 'decade' },
    { label: '世纪', value: 'century' },
  ],
  // DropDownBox 模板类型
  template: [
    { label: '弹出', value: 'popup' },
    { label: '下拉', value: 'dropdown' },
  ],
}

/** 把从 DevExtreme 类型声明提取的属性元数据转换为属性面板设置器。
 *  优先使用 dx-prop-meta.ts 中定义的组件特定选项（p.kind === 'select'），
 *  只有当属性是 string 类型但在 KNOWN_SELECT_PROPS 中有映射时才使用全局选项。
 *  这样避免了全局选项覆盖组件特定选项的问题。
 */
function metaPropToSetter(p: DxPropMeta): PropSetterConfig {
  // 优先使用 dx-prop-meta.ts 中定义的 select 类型（组件特定选项）
  if (p.kind === 'select' && p.options && p.options.length > 0) {
    return { prop: p.name, label: translatePropName(p.name), type: 'select', options: p.options }
  }
  // 如果 dx-prop-meta.ts 没有定义选项，但 KNOWN_SELECT_PROPS 有全局映射，则使用全局映射
  if (p.kind === 'string' && KNOWN_SELECT_PROPS[p.name]) {
    return { prop: p.name, label: translatePropName(p.name), type: 'select', options: KNOWN_SELECT_PROPS[p.name] }
  }
  switch (p.kind) {
    case 'string':
      return { prop: p.name, label: translatePropName(p.name), type: 'input' }
    case 'number':
      return { prop: p.name, label: translatePropName(p.name), type: 'number' }
    case 'boolean':
      return { prop: p.name, label: translatePropName(p.name), type: 'switch' }
    case 'select':
      // select 类型但没有选项，使用空选项列表
      return { prop: p.name, label: translatePropName(p.name), type: 'select', options: p.options ?? [] }
    case 'json':
      return { prop: p.name, label: translatePropName(p.name), type: 'json' }
  }
}

/** 为每个 DevExtreme 物料补齐「可配置属性」，过滤掉复杂/事件类属性 */
const dxConfigsWithAllProps: MaterialConfig[] = dxMaterialConfigs.map((config) => {
  // 过滤掉 width/height setter，统一由「尺寸」section 控制
  const filteredSetters = config.propSetters.filter((s) => !NON_CONFIGURABLE_PROPS.has(s.prop))
  const covered = new Set(filteredSetters.map((setter) => setter.prop))
  const extraSetters = (dxPropMeta[config.type] ?? [])
    .filter((p) => !covered.has(p.name) && !NON_CONFIGURABLE_PROPS.has(p.name) && !COMPLEX_PROPS.has(p.name))
    .map(metaPropToSetter)
  return {
    ...config,
    propSetters: [...filteredSetters, ...extraSetters],
  }
})

// 为所有表单控件添加默认必填验证规则
for (const config of dxConfigsWithAllProps) {
  if (config.category === '表单' && VALIDATABLE_TYPES.has(config.type)) {
    if (!config.defaultProps.validationRules) {
      config.defaultProps = {
        ...config.defaultProps,
        validationRules: [{ type: 'required', message: '此字段为必填项' }]
      }
    }
  }
}

/** 获取某类型的所有 propSetters（含基础标签页用的 + 全部属性标签页用的） */
export function getDxAllPropSetters(type: string): PropSetterConfig[] {
  const metaProps = dxPropMeta[type] ?? []
  const all = metaProps
    .filter((p) => !NON_CONFIGURABLE_PROPS.has(p.name) && !COMPLEX_PROPS.has(p.name))
    .map(metaPropToSetter)
  // 合并已覆盖的
  const config = dxMaterialConfigs.find((c) => c.type === type)
  if (!config) return all
  // 过滤掉 width/height setter
  const filteredSetters = config.propSetters.filter((s) => !NON_CONFIGURABLE_PROPS.has(s.prop))
  const covered = new Set(filteredSetters.map((s) => s.prop))
  const rest = all.filter((s) => !covered.has(s.prop))
  return [...filteredSetters, ...rest]
}

/** 物料注册表：type -> 组件实现（内置物料 + DevExtreme 物料） */
export const materialComponents: Record<string, Component> = {
  ...dxComponents,
  // 自定义基础控件
  image: ImageView,
  link: LinkView,
  iframe: IframeView,
  'dx-base-data': DxBaseData,
  // 布局类控件
  'el-row': ElRow,
  'el-col': ElCol,
  'el-splitter': ElSplitter,
  'el-splitter-panel': ElSplitterPanel,
  'el-tabs': ElTabs,
  'el-tab-pane': ElTabPane,
}

/** 物料元数据配置列表（DevExtreme 物料 + 自定义控件 + 布局控件，决定物料面板与属性面板） */
export const materialConfigs: MaterialConfig[] = [
  // ---- DevExtreme 物料 ----
  ...dxConfigsWithAllProps,

  // ---- 自定义基础控件 ----
  {
    type: 'image',
    title: '图片',
    enName: 'Image',
    category: '表单',
    icon: '🖼',
    defaultProps: {
      src: "data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='320' height='160'%3E%3Crect width='100%25' height='100%25' fill='%23e8e8e8'/%3E%3Ctext x='50%25' y='50%25' text-anchor='middle' dominant-baseline='middle' font-size='16' fill='%23999'%3E图片%3C/text%3E%3C/svg%3E",
      alt: '图片',
      fit: 'cover',
      borderRadius: 0,
      width: 320,
      height: 160,
    },
    propSetters: [
      { prop: 'src', label: '图片地址', type: 'input' },
      { prop: 'alt', label: '替代文字', type: 'input' },
      { prop: 'fit', label: '填充模式', type: 'select', options: [
        { label: '覆盖', value: 'cover' },
        { label: '包含', value: 'contain' },
        { label: '填充', value: 'fill' },
        { label: '无', value: 'none' },
        { label: '缩小', value: 'scale-down' },
      ] },
      { prop: 'borderRadius', label: '圆角', type: 'number' },
    ],
  },
  {
    type: 'link',
    title: '链接',
    enName: 'Link',
    category: '表单',
    icon: '🔗',
    defaultProps: { text: '链接', href: 'https://cn.vuejs.org', target: '_blank', underline: false, color: '#337ab7', fontSize: 14 },
    propSetters: [
      { prop: 'text', label: '链接文字', type: 'input' },
      { prop: 'href', label: '地址', type: 'input' },
      { prop: 'target', label: '打开方式', type: 'select', options: [{ label: '新标签页', value: '_blank' }, { label: '当前页', value: '_self' }] },
      { prop: 'underline', label: '下划线', type: 'switch' },
      { prop: 'color', label: '颜色', type: 'color' },
      { prop: 'fontSize', label: '字号', type: 'number' },
    ],
  },
  {
    type: 'iframe',
    title: '内嵌网页',
    enName: 'Iframe',
    category: '表单',
    icon: '▭',
    defaultProps: { src: 'https://vuejs.org', title: '内嵌网页', width: 400, height: 300, border: false },
    propSetters: [
      { prop: 'src', label: '网页地址', type: 'input' },
      { prop: 'title', label: '标题', type: 'input' },
      { prop: 'border', label: '显示边框', type: 'switch' },
    ],
  },
  {
    type: 'dx-base-data',
    title: '基础资料',
    enName: 'BaseData',
    category: '表单',
    icon: '📋',
    defaultProps: {
      refPageId: '',
      displayField: '',
      valueField: 'id',
      placeholder: '请选择',
      width: 240,
      value: null,
      visible: true,
      label: '基础资料',
      labelVisible: true,
      labelPosition: 'top',
      required: false,
      requiredPosition: 'after',
      tooltipEnabled: false,
      tooltipText: '',
    },
    propSetters: [
      { prop: 'refPageId', label: '关联页面', type: 'input' },
      { prop: 'displayField', label: '显示字段', type: 'input' },
      { prop: 'valueField', label: '值字段', type: 'input' },
      { prop: 'placeholder', label: '占位文字', type: 'input' },
      { prop: 'visible', label: '显示', type: 'switch' },
      { prop: 'label', label: '标签文字', type: 'input' },
      { prop: 'labelVisible', label: '标签显示', type: 'switch' },
      { prop: 'labelPosition', label: '标签位置', type: 'select', options: [{ label: '左侧', value: 'left' }, { label: '上方', value: 'top' }] },
      { prop: 'required', label: '必填', type: 'switch' },
      { ...{ prop: 'requiredPosition', label: '星号位置', type: 'select', options: [{ label: '标签后', value: 'after' }, { label: '标签前', value: 'before' }] }, visibleIf: { prop: 'required', equals: true } },
      { prop: 'tooltipEnabled', label: '启用介绍', type: 'switch' },
      { ...{ prop: 'tooltipText', label: '介绍内容', type: 'input' }, visibleIf: { prop: 'tooltipEnabled', equals: true } },
    ],
  },

  // ---- 布局控件 ----
  {
    type: 'el-row',
    title: '栅格布局',
    enName: 'Row',
    category: '布局',
    icon: '▥',
    isContainer: true,
    defaultProps: { gutter: 0, rowGap: 0, justify: 'start', align: 'stretch', wrap: true },
    propSetters: [
      { prop: 'gutter', label: '列间距 (px)', type: 'number' },
      { prop: 'rowGap', label: '行间距 (px)', type: 'number' },
      { prop: 'justify', label: '水平对齐', type: 'select', options: [
        { label: '起始', value: 'start' }, { label: '结尾', value: 'end' },
        { label: '居中', value: 'center' }, { label: '两端对齐', value: 'space-between' },
        { label: '分散对齐', value: 'space-around' },
      ] },
      { prop: 'align', label: '垂直对齐', type: 'select', options: [
        { label: '拉伸', value: 'stretch' }, { label: '顶部', value: 'start' },
        { label: '居中', value: 'center' }, { label: '底部', value: 'end' },
      ] },
      { prop: 'wrap', label: '自动换行', type: 'switch' },
    ],
    defaultChildren: [
      { type: 'el-col', props: { span: 12, offset: 0, padding: '0', background: '#ffffff' } },
      { type: 'el-col', props: { span: 12, offset: 0, padding: '0', background: '#ffffff' } },
    ],
  },
  {
    type: 'el-splitter',
    title: '分割面板',
    enName: 'Splitter',
    category: '布局',
    icon: '◫',
    isContainer: true,
    defaultProps: { layout: 'horizontal' },
    propSetters: [
      { prop: 'layout', label: '布局方向', type: 'select', options: [
        { label: '横向', value: 'horizontal' }, { label: '纵向', value: 'vertical' },
      ] },
    ],
    defaultChildren: [
      { type: 'el-splitter-panel', props: { size: 0, sizeUnit: 'px', min: 100, max: 0, collapsible: false, padding: '0', background: '#ffffff' } },
      { type: 'el-splitter-panel', props: { size: 0, sizeUnit: 'px', min: 100, max: 0, collapsible: false, padding: '0', background: '#ffffff' } },
    ],
  },
  {
    type: 'el-tabs',
    title: '标签页',
    enName: 'Tabs',
    category: '布局',
    icon: '▭▭',
    isContainer: true,
    defaultProps: { type: '', tabPosition: 'top' },
    propSetters: [
      { prop: 'type', label: '标签风格', type: 'select', options: [
        { label: '默认线条', value: '' },
        { label: '卡片风格', value: 'card' },
        { label: '边框卡片', value: 'border-card' },
      ] },
      { prop: 'tabPosition', label: '标签位置', type: 'select', options: [
        { label: '顶部', value: 'top' },
        { label: '底部', value: 'bottom' },
        { label: '左侧', value: 'left' },
        { label: '右侧', value: 'right' },
      ] },
    ],
    defaultChildren: [
      { type: 'el-tab-pane', props: { label: '标签一', name: 'tab1', padding: '0', background: '#ffffff' } },
      { type: 'el-tab-pane', props: { label: '标签二', name: 'tab2', padding: '0', background: '#ffffff' } },
    ],
  },
  // ---- 栅格列（el-row 子节点专用，不在物料面板展示） ----
  {
    type: 'el-col',
    title: '栅格列',
    enName: 'Col',
    category: '布局',
    icon: '▯',
    isContainer: true,
    defaultProps: { span: 12, offset: 0, padding: '0', background: '#ffffff' },
    propSetters: [
      { prop: 'span', label: '列宽 (1-24 栅格)', type: 'number' },
      { prop: 'offset', label: '偏移 (1-24 栅格)', type: 'number' },
      { prop: 'padding', label: '内边距', type: 'input' },
      { prop: 'background', label: '背景色', type: 'color' },
    ],
  },
  // ---- 分割面板面板（el-splitter 子节点专用，不在物料面板展示） ----
  {
    type: 'el-splitter-panel',
    title: '分割面板',
    enName: 'SplitterPanel',
    category: '布局',
    icon: '◫',
    isContainer: true,
    defaultProps: { size: 0, sizeUnit: 'px', min: 100, max: 0, collapsible: false, padding: '0', background: '#ffffff' },
    propSetters: [
      { prop: 'size', label: '尺寸 (0=均分，>0=按单位固定)', type: 'number' },
      { prop: 'sizeUnit', label: '尺寸单位', type: 'select', options: [{ label: '像素', value: 'px' }, { label: '百分比', value: '%' }] },
      { prop: 'min', label: '最小尺寸 (px)', type: 'number' },
      { prop: 'max', label: '最大尺寸 (0=不限)', type: 'number' },
      { prop: 'collapsible', label: '可折叠', type: 'switch' },
      { prop: 'padding', label: '内边距', type: 'input' },
      { prop: 'background', label: '背景色', type: 'color' },
    ],
  },
  // ---- 标签页面板（el-tabs 子节点专用，不在物料面板展示） ----
  {
    type: 'el-tab-pane',
    title: '标签面板',
    enName: 'TabPane',
    category: '布局',
    icon: '▯',
    isContainer: true,
    defaultProps: { label: '标签', name: '', padding: '0', background: '#ffffff' },
    propSetters: [
      { prop: 'label', label: '标签名称', type: 'input' },
      { prop: 'name', label: '唯一标识', type: 'input' },
      { prop: 'padding', label: '内边距', type: 'input' },
      { prop: 'background', label: '背景色', type: 'color' },
    ],
  },
]

/** 按分类分组，供物料面板展示 */
export function groupByCategory(): Record<string, MaterialConfig[]> {
  const groups: Record<string, MaterialConfig[]> = {}
  for (const cfg of materialConfigs) {
    if (HIDDEN_MATERIAL_TYPES.has(cfg.type)) continue
    if (!groups[cfg.category]) groups[cfg.category] = []
    groups[cfg.category].push(cfg)
  }
  return groups
}

export function getConfig(type: string): MaterialConfig | undefined {
  return materialConfigs.find((c) => c.type === type)
}

/**
 * DevExtreme 图表类控件没有 height/width prop，尺寸统一走 size: { height, width }。
 * 这里根据生成的全量属性元数据自动识别这类控件（有 size 且无 height/width 的组件）。
 */
const SIZE_BASED_TYPES = new Set(
  Object.entries(dxPropMeta)
    .filter(
      ([, props]) =>
        props.some((p) => p.name === 'size') &&
        !props.some((p) => p.name === 'height') &&
        !props.some((p) => p.name === 'width'),
    )
    .map(([type]) => type),
)

/**
 * 计算实际绑定到组件上的 props。
 * 对图表类控件，把扁平的 height / width 配置合并进 size 对象后传给组件。
 * 当传入自由画布节点尺寸时，会同时覆盖 width / height / size，让内部控件随外层容器缩放。
 * 当节点有 css.width / css.height 时，将 CSS 值传递给组件的 width / height prop，
 * 使 CSS 宽度/高度设置能够真正影响 DevExtreme 控件的尺寸。
 */
export function resolveNodeProps(
  type: string,
  props: Record<string, unknown>,
  node?: Pick<ComponentSchema, 'width' | 'height' | 'css'> | null,
): Record<string, unknown> {
  const base: Record<string, unknown> = { ...props }
  const metaProps = dxPropMeta[type] ?? []
  const isDx = type.startsWith('dx-')
  const hasWidth = metaProps.some((p) => p.name === 'width') || ['image', 'iframe'].includes(type) || isDx
  const hasHeight = metaProps.some((p) => p.name === 'height') || ['image', 'iframe'].includes(type) || isDx

  // 如果 CSS 中设置了 width/height，将其作为组件的 prop 传入
  if (node?.css) {
    const cssWidth = node.css.width
    const cssHeight = node.css.height
    if (cssWidth && hasWidth) {
      const pxMatch = /^(\d+(?:\.\d+)?)px$/.exec(cssWidth)
      if (pxMatch) {
        base.width = Number(pxMatch[1])
      } else if (cssWidth === 'auto') {
        base.width = undefined
      }
      // '%' 值：传 '100%' 给组件，让组件填充由 CSS 控制宽度的外层容器
      else if (cssWidth.endsWith('%')) {
        base.width = '100%'
      }
    }
    if (cssHeight && hasHeight) {
      const pxMatch = /^(\d+(?:\.\d+)?)px$/.exec(cssHeight)
      if (pxMatch) {
        base.height = Number(pxMatch[1])
      } else if (cssHeight === 'auto') {
        base.height = undefined
      }
      // '%' 值：传 '100%' 给组件，让组件填充由 CSS 控制高度的外层容器
      else if (cssHeight.endsWith('%')) {
        base.height = '100%'
      }
    }
  }

  if (node && typeof node.width === 'number') {
    // 仅当 CSS 未设置 width 时才使用画布定位尺寸
    if (!node?.css?.width) {
      if (SIZE_BASED_TYPES.has(type)) {
        const rawSize = base.size
        const size: Record<string, unknown> =
          rawSize && typeof rawSize === 'object' && !Array.isArray(rawSize)
            ? { ...(rawSize as Record<string, unknown>) }
            : {}
        size.width = node.width
        base.size = size
      } else if (hasWidth) {
        base.width = node.width
      }
    }
  }
  if (node && typeof node.height === 'number') {
    if (!node?.css?.height) {
      if (SIZE_BASED_TYPES.has(type)) {
        const rawSize = base.size
        const size: Record<string, unknown> =
          rawSize && typeof rawSize === 'object' && !Array.isArray(rawSize)
            ? { ...(rawSize as Record<string, unknown>) }
            : {}
        size.height = node.height
        base.size = size
      } else if (hasHeight) {
        base.height = node.height
      }
    }
  }

  if (!SIZE_BASED_TYPES.has(type)) return base

  const result: Record<string, unknown> = {}
  const size: Record<string, unknown> = {}
  const rawSize = base.size
  if (rawSize && typeof rawSize === 'object' && !Array.isArray(rawSize)) {
    Object.assign(size, rawSize)
  }
  for (const [key, value] of Object.entries(base)) {
    if (key === 'height' || key === 'width') {
      if (value !== undefined && value !== null && value !== '') size[key] = value
    } else {
      result[key] = value
    }
  }
  if (Object.keys(size).length) result.size = size
  return result
}
