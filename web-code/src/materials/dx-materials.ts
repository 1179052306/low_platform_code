/**
 * DevExtreme 物料注册：将 DX 组件直接作为低代码物料接入。
 * 复杂配置（columns / series / items / dataSource 等）通过 json 设置器编辑。
 */
import type { Component } from 'vue'
import type { MaterialConfig, PropSetterConfig } from '@/types/schema'

// ---- 图表 ----
import DxChart from 'devextreme-vue/chart'
import DxPieChart from 'devextreme-vue/pie-chart'
import DxPolarChart from 'devextreme-vue/polar-chart'
import DxBarGauge from 'devextreme-vue/bar-gauge'
import DxBullet from 'devextreme-vue/bullet'
import DxCircularGauge from 'devextreme-vue/circular-gauge'
import DxLinearGauge from 'devextreme-vue/linear-gauge'
import DxFunnel from 'devextreme-vue/funnel'
import DxSankey from 'devextreme-vue/sankey'
import DxSparkline from 'devextreme-vue/sparkline'
import DxTreeMap from 'devextreme-vue/tree-map'
import DxRangeSelector from 'devextreme-vue/range-selector'

// ---- 数据 ----
import DxDataGrid from 'devextreme-vue/data-grid'
import DxTreeList from 'devextreme-vue/tree-list'
import DxList from 'devextreme-vue/list'
import DxTreeView from 'devextreme-vue/tree-view'
import DxPivotGrid from 'devextreme-vue/pivot-grid'
import DxPivotGridFieldChooser from 'devextreme-vue/pivot-grid-field-chooser'
import DxFilterBuilder from 'devextreme-vue/filter-builder'
import DxScheduler from 'devextreme-vue/scheduler'
import DxGantt from 'devextreme-vue/gantt'
import DxFileManager from 'devextreme-vue/file-manager'

// ---- 表单编辑 ----
import DxTextBox from 'devextreme-vue/text-box'
import DxTextArea from 'devextreme-vue/text-area'
import DxNumberBox from 'devextreme-vue/number-box'
import DxCheckBox from 'devextreme-vue/check-box'
import DxSwitch from 'devextreme-vue/switch'
import DxSlider from 'devextreme-vue/slider'
import DxRangeSlider from 'devextreme-vue/range-slider'
import DxCalendar from 'devextreme-vue/calendar'
import DxDateBox from 'devextreme-vue/date-box'
import DxDateRangeBox from 'devextreme-vue/date-range-box'
import DxColorBox from 'devextreme-vue/color-box'
import DxSelectBox from 'devextreme-vue/select-box'
import DxLookup from 'devextreme-vue/lookup'
import DxTagBox from 'devextreme-vue/tag-box'
import DxAutocomplete from 'devextreme-vue/autocomplete'
import DxRadioGroup from 'devextreme-vue/radio-group'
import DxDropDownButton from 'devextreme-vue/drop-down-button'
import DxDropDownBox from 'devextreme-vue/drop-down-box'
import DxFileUploader from 'devextreme-vue/file-uploader'
import DxHtmlEditor from 'devextreme-vue/html-editor'

// ---- 导航 ----
import DxToolbar from 'devextreme-vue/toolbar'
import DxGallery from 'devextreme-vue/gallery'
import DxSpeedDialAction from 'devextreme-vue/speed-dial-action'


// ---- 布局 / 基础 ----
import DxButtonGroup from 'devextreme-vue/button-group'
import DxButton from 'devextreme-vue/button'
import DxProgressBar from 'devextreme-vue/progress-bar'
import DxLoadIndicator from 'devextreme-vue/load-indicator'

// ---- 弹层 ----
import DxPopup from 'devextreme-vue/popup'
import DxToast from 'devextreme-vue/toast'
import DxLoadPanel from 'devextreme-vue/load-panel'

/** 组件实现注册表 */
export const dxComponents: Record<string, Component> = {
  // 图表
  'dx-chart': DxChart as Component,
  'dx-pie-chart': DxPieChart as Component,
  'dx-polar-chart': DxPolarChart as Component,
  'dx-bar-gauge': DxBarGauge as Component,
  'dx-bullet': DxBullet as Component,
  'dx-circular-gauge': DxCircularGauge as Component,
  'dx-linear-gauge': DxLinearGauge as Component,
  'dx-funnel': DxFunnel as Component,
  'dx-sankey': DxSankey as Component,
  'dx-sparkline': DxSparkline as Component,
  'dx-tree-map': DxTreeMap as Component,
  'dx-range-selector': DxRangeSelector as Component,
  // 数据
  'dx-data-grid': DxDataGrid as Component,
  'dx-tree-list': DxTreeList as Component,
  'dx-list': DxList as Component,
  'dx-tree-view': DxTreeView as Component,
  'dx-pivot-grid': DxPivotGrid as Component,
  'dx-pivot-field-chooser': DxPivotGridFieldChooser as Component,
  'dx-filter-builder': DxFilterBuilder as Component,
  'dx-scheduler': DxScheduler as Component,
  'dx-gantt': DxGantt as Component,
  'dx-file-manager': DxFileManager as Component,
  // 表单
  'dx-text-box': DxTextBox as Component,
  'dx-text-area': DxTextArea as Component,
  'dx-number-box': DxNumberBox as Component,
  'dx-check-box': DxCheckBox as Component,
  'dx-switch': DxSwitch as Component,
  'dx-slider': DxSlider as Component,
  'dx-range-slider': DxRangeSlider as Component,
  'dx-calendar': DxCalendar as Component,
  'dx-date-box': DxDateBox as Component,
  'dx-date-range-box': DxDateRangeBox as Component,
  'dx-color-box': DxColorBox as Component,
  'dx-select-box': DxSelectBox as Component,
  'dx-lookup': DxLookup as Component,
  'dx-tag-box': DxTagBox as Component,
  'dx-autocomplete': DxAutocomplete as Component,
  'dx-radio-group': DxRadioGroup as Component,
  'dx-drop-down-button': DxDropDownButton as Component,
  'dx-drop-down-box': DxDropDownBox as Component,
  'dx-file-uploader': DxFileUploader as Component,
  'dx-html-editor': DxHtmlEditor as Component,
  // 导航
  'dx-toolbar': DxToolbar as Component,
  'dx-gallery': DxGallery as Component,
  'dx-speed-dial-action': DxSpeedDialAction as Component,

  // 布局 / 基础
  'dx-button-group': DxButtonGroup as Component,
  'dx-button': DxButton as Component,
  'dx-progress-bar': DxProgressBar as Component,
  'dx-load-indicator': DxLoadIndicator as Component,
  // 弹层
  'dx-popup': DxPopup as Component,
  'dx-toast': DxToast as Component,
  'dx-load-panel': DxLoadPanel as Component,
}

// ---------- 通用设置器 ----------

const s = (prop: string, label: string, type: PropSetterConfig['type'], options?: PropSetterConfig['options']): PropSetterConfig => ({
  prop,
  label,
  type,
  options,
})

const widthSetter = s('width', '宽度', 'number')
const heightSetter = s('height', '高度', 'number')
const disabledSetter = s('disabled', '禁用', 'switch')
const jsonSetter = (prop: string, label: string): PropSetterConfig => s(prop, label, 'json')

// ---------- 示例数据 ----------

const salesData = [
  { month: '1月', sales: 120, profit: 40 },
  { month: '2月', sales: 98, profit: 32 },
  { month: '3月', sales: 145, profit: 51 },
  { month: '4月', sales: 132, profit: 46 },
  { month: '5月', sales: 168, profit: 60 },
  { month: '6月', sales: 155, profit: 55 },
]

const regionData = [
  { region: '华东', value: 120 },
  { region: '华北', value: 95 },
  { region: '华南', value: 88 },
  { region: '西部', value: 62 },
]

const gridData = [
  { id: 1, name: '张三', age: 28, city: '杭州', department: '研发' },
  { id: 2, name: '李四', age: 32, city: '上海', department: '产品' },
  { id: 3, name: '王五', age: 26, city: '北京', department: '设计' },
  { id: 4, name: '赵六', age: 35, city: '深圳', department: '研发' },
  { id: 5, name: '孙七', age: 29, city: '广州', department: '市场' },
]

const appointments = [
  { text: '产品评审', startDate: '2026-08-13T09:30:00', endDate: '2026-08-13T11:00:00' },
  { text: '客户拜访', startDate: '2026-08-14T14:00:00', endDate: '2026-08-14T16:00:00' },
]

const ganttTasks = [
  { id: 1, parentId: 0, title: '项目启动', start: '2026-08-03', end: '2026-08-07', progress: 100 },
  { id: 2, parentId: 0, title: '需求分析', start: '2026-08-07', end: '2026-08-14', progress: 60 },
  { id: 3, parentId: 0, title: '开发实现', start: '2026-08-14', end: '2026-08-28', progress: 20 },
]

const pivotDataSource = {
  fields: [
    { dataField: 'region', area: 'row' },
    { dataField: 'month', area: 'column' },
    { dataField: 'sales', area: 'data', summaryType: 'sum' },
  ],
  store: [
    { region: '华东', month: '1月', sales: 120 },
    { region: '华东', month: '2月', sales: 98 },
    { region: '华北', month: '1月', sales: 88 },
    { region: '华北', month: '2月', sales: 105 },
  ],
}

// ---------- 物料元数据 ----------

export const dxMaterialConfigs: MaterialConfig[] = [
  // ==================== 图表 ====================
  {
    type: 'dx-chart',
    title: '图表',
    enName: 'Chart',
    category: '图表',
    icon: '📈',
    defaultProps: {
      dataSource: salesData,
      series: [
        { valueField: 'sales', name: '销售额' },
        { valueField: 'profit', name: '利润' },
      ],
      argumentAxis: { categories: salesData.map((d) => d.month) },
      height: 300,
      palette: 'soft',
    },
    propSetters: [
      jsonSetter('dataSource', '数据源'),
      jsonSetter('series', '系列配置'),
      jsonSetter('argumentAxis', '横轴配置'),
      heightSetter,
      s('palette', '调色板', 'select', [
        { label: '柔和', value: 'soft' },
        { label: '默认', value: 'default' },
        { label: '复古', value: 'vintage' },
        { label: '海洋', value: 'ocean' },
      ]),
      s('title', '标题', 'input'),
    ],
  },
  {
    type: 'dx-pie-chart',
    title: '饼图',
    enName: 'PieChart',
    category: '图表',
    icon: '◕',
    defaultProps: {
      dataSource: regionData,
      series: [{ argumentField: 'region', valueField: 'value' }],
      height: 300,
      legend: { visible: true },
    },
    propSetters: [jsonSetter('dataSource', '数据源'), jsonSetter('series', '系列配置'), heightSetter, jsonSetter('legend', '图例'), s('title', '标题', 'input')],
  },
  {
    type: 'dx-polar-chart',
    title: '极坐标图',
    enName: 'PolarChart',
    category: '图表',
    icon: '❉',
    defaultProps: {
      dataSource: salesData,
      series: [{ valueField: 'sales', name: '销售额' }],
      argumentAxis: { categories: salesData.map((d) => d.month) },
      height: 320,
    },
    propSetters: [jsonSetter('dataSource', '数据源'), jsonSetter('series', '系列配置'), heightSetter],
  },
  {
    type: 'dx-bar-gauge',
    title: '条形仪表盘',
    enName: 'BarGauge',
    category: '图表',
    icon: '◔',
    defaultProps: {
      values: [47, 65, 84, 93],
      startValue: 0,
      endValue: 100,
      height: 260,
      width: 260,
    },
    propSetters: [jsonSetter('values', '数值组'), s('startValue', '起始值', 'number'), s('endValue', '结束值', 'number'), heightSetter, widthSetter],
  },
  {
    type: 'dx-bullet',
    title: '子弹图',
    enName: 'Bullet',
    category: '图表',
    icon: '―',
    defaultProps: { value: 42, startScaleValue: 0, endScaleValue: 100, color: '#1976d2', width: 240, height: 40 },
    propSetters: [s('value', '数值', 'number'), s('startScaleValue', '起始值', 'number'), s('endScaleValue', '结束值', 'number'), s('color', '颜色', 'color'), widthSetter, heightSetter],
  },
  {
    type: 'dx-circular-gauge',
    title: '圆形仪表',
    enName: 'CircularGauge',
    category: '图表',
    icon: '◷',
    defaultProps: {
      value: 75,
      scale: { startValue: 0, endValue: 100, tickInterval: 25 },
      height: 220,
      width: 220,
    },
    propSetters: [s('value', '数值', 'number'), jsonSetter('scale', '刻度'), heightSetter, widthSetter],
  },
  {
    type: 'dx-linear-gauge',
    title: '线性仪表',
    enName: 'LinearGauge',
    category: '图表',
    icon: '⊸',
    defaultProps: {
      value: 60,
      scale: { startValue: 0, endValue: 100, tickInterval: 25 },
      height: 120,
      width: 320,
    },
    propSetters: [s('value', '数值', 'number'), jsonSetter('scale', '刻度'), heightSetter, widthSetter],
  },
  {
    type: 'dx-funnel',
    title: '漏斗图',
    enName: 'Funnel',
    category: '图表',
    icon: '▽',
    defaultProps: {
      dataSource: [
        { stage: '浏览', value: 1000 },
        { stage: '咨询', value: 620 },
        { stage: '下单', value: 380 },
        { stage: '成交', value: 210 },
      ],
      argumentField: 'stage',
      valueField: 'value',
      height: 300,
    },
    propSetters: [jsonSetter('dataSource', '数据源'), s('argumentField', '分类字段', 'input'), s('valueField', '数值字段', 'input'), heightSetter],
  },
  {
    type: 'dx-sankey',
    title: '桑基图',
    enName: 'Sankey',
    category: '图表',
    icon: '⇶',
    defaultProps: {
      dataSource: [
        { source: '原料', target: '加工', weight: 10 },
        { source: '加工', target: '成品', weight: 8 },
        { source: '成品', target: '销售', weight: 6 },
      ],
      sourceField: 'source',
      targetField: 'target',
      weightField: 'weight',
      height: 280,
    },
    propSetters: [jsonSetter('dataSource', '数据源'), s('sourceField', '来源字段', 'input'), s('targetField', '目标字段', 'input'), s('weightField', '权重字段', 'input'), heightSetter],
  },
  {
    type: 'dx-sparkline',
    title: '迷你图',
    enName: 'Sparkline',
    category: '图表',
    icon: '∿',
    defaultProps: {
      dataSource: salesData.map((d) => ({ arg: d.month, val: d.sales })),
      argumentField: 'arg',
      valueField: 'val',
      type: 'line',
      width: 240,
      height: 60,
    },
    propSetters: [
      jsonSetter('dataSource', '数据源'),
      s('type', '类型', 'select', [
        { label: '折线', value: 'line' },
        { label: '面积', value: 'area' },
        { label: '柱状', value: 'bar' },
      ]),
      s('lineColor', '颜色', 'color'),
      widthSetter,
      heightSetter,
    ],
  },
  {
    type: 'dx-tree-map',
    title: '矩形树图',
    enName: 'TreeMap',
    category: '图表',
    icon: '▩',
    defaultProps: {
      dataSource: [
        { name: '华东', items: [{ name: '上海', value: 120 }, { name: '杭州', value: 90 }] },
        { name: '华北', items: [{ name: '北京', value: 150 }] },
      ],
      valueField: 'value',
      labelField: 'name',
      height: 300,
    },
    propSetters: [jsonSetter('dataSource', '数据源'), s('valueField', '数值字段', 'input'), s('labelField', '标签字段', 'input'), heightSetter],
  },
  {
    type: 'dx-range-selector',
    title: '范围选择器',
    enName: 'RangeSelector',
    category: '图表',
    icon: '⇤⇥',
    defaultProps: {
      dataSource: salesData.map((d) => ({ arg: d.month, val: d.sales })),
      chart: { series: { argumentField: 'arg', valueField: 'val' } },
      scale: { categories: salesData.map((d) => d.month) },
      height: 200,
    },
    propSetters: [jsonSetter('dataSource', '数据源'), jsonSetter('chart', '图表配置'), jsonSetter('scale', '刻度'), heightSetter],
  },

  // ==================== 数据 ====================
  {
    type: 'dx-data-grid',
    title: '数据表格',
    enName: 'DataGrid',
    category: '数据',
    icon: '▦',
    defaultProps: {
      visible: true,
      showColumnHeaders: true,
      showColumnLines: true,
      allowColumnReordering: true,
      dataSourceType: 'static',
      remoteMethod: 'POST',
      dataSource: [],
      keyExpr: 'id',
      columns: [],
      height: 300,
      showBorders: true,
      columnAutoWidth: true,
      paging: { pageSize: 10, enabled: true },
      pager: {
        showPageSizeSelector: true,
        allowedPageSizes: [10, 20, 50, 100],
        showInfo: true,
        showNavigationButtons: true,
      },
      filterRow: { visible: false },
      headerFilter: { visible: false },
      searchPanel: { visible: false },
      sorting: { mode: 'single' },
      selection: { mode: 'none' },
      editing: { mode: 'row', allowAdding: false, allowUpdating: false, allowDeleting: false },
      toolbar: {
        visible: false,
        items: [
          { name: 'add', caption: '新增', icon: 'plus', location: 'before', visible: false, isCustom: false },
          { name: 'delete', caption: '删除', icon: 'trash', location: 'before', visible: false, isCustom: false },
          { name: 'edit', caption: '编辑', icon: 'edit', location: 'before', visible: false, isCustom: false },
          { name: 'export', caption: '导出', icon: 'export', location: 'after', visible: false, isCustom: false },
          { name: 'refresh', caption: '刷新', icon: 'refresh', location: 'after', visible: false, isCustom: false },
          { name: 'save', caption: '保存', icon: 'save', location: 'after', visible: false, isCustom: false },
          { name: 'cancel', caption: '取消', icon: 'revert', location: 'after', visible: false, isCustom: false },
        ],
      },
    },
    propSetters: [
      // ---- 数据源（最常用） ----
      s('dataSourceType', '数据源类型', 'select', [
        { label: '静态数据', value: 'static' },
        { label: '远程加载', value: 'remote' },
      ]),
      { ...s('serviceId', 'API 服务', 'apiSelect'), visibleIf: { prop: 'dataSourceType', equals: 'remote' } },
      // 远程加载模式只允许走 API 服务注册表（治理要求），不再提供手填 URL 入口；
      // 存量页面 schema 中已保存的 remoteUrl/remoteMethod/remoteBody 仍由渲染端回退逻辑兼容
      { ...s('firstLoad', '首次不加载', 'switch'), visibleIf: { prop: 'dataSourceType', equals: 'remote' } },
      { ...s('advancedSearch', '高级查询', 'switch'), visibleIf: { prop: 'dataSourceType', equals: 'remote' } },
      { ...jsonSetter('dataSource', '数据源'), visibleIf: { prop: 'dataSourceType', equals: 'static' } },
      // ---- 列配置 ----
      { prop: 'columns', label: '列配置', type: 'columns' as const },
      s('keyExpr', '主键字段', 'input'),
      // ---- 工具栏 ----
      s('toolbar.visible', '显示工具栏', 'switch'),
      { ...{ prop: 'toolbar.items', label: '工具栏按钮', type: 'toolbarItems' as const }, visibleIf: { prop: 'toolbar.visible', equals: true } },
      // ---- 分页 ----
      s('paging.enabled', '分页', 'switch'),
      s('paging.pageSize', '每页条数', 'number'),
      s('paging.pageIndex', '当前页', 'number'),
      s('pager.showPageSizeSelector', '显示每页条数选择器', 'switch'),
      s('pager.showInfo', '显示分页信息', 'switch'),
      s('pager.showNavigationButtons', '显示导航按钮', 'switch'),
      // ---- 编辑 ----
      s('editing.mode', '编辑模式', 'select', [
        { label: '行', value: 'row' },
        { label: '批量', value: 'batch' },
        { label: '单元格', value: 'cell' },
        { label: '表单', value: 'form' },
        { label: '弹窗', value: 'popup' },
      ]),
      s('editing.allowAdding', '允许新增', 'switch'),
      s('editing.allowUpdating', '允许修改', 'switch'),
      s('editing.allowDeleting', '允许删除', 'switch'),
      // ---- 选择 ----
      s('selection.mode', '选择模式', 'select', [
        { label: '无', value: 'none' },
        { label: '单选', value: 'single' },
        { label: '多选', value: 'multiple' },
      ]),
      s('selection.showCheckBoxesMode', '选择列显示', 'select', [
        { label: '点击显示', value: 'onClick' },
        { label: '长按显示', value: 'onLongTap' },
        { label: '始终显示', value: 'always' },
        { label: '隐藏', value: 'none' },
      ]),
      // ---- 排序 & 搜索 & 筛选 ----
      s('sorting.mode', '排序模式', 'select', [
        { label: '单列', value: 'single' },
        { label: '多列', value: 'multiple' },
        { label: '禁用', value: 'none' },
      ]),
      s('searchPanel.visible', '搜索面板', 'switch'),
      s('searchPanel.highlightSearchText', '高亮搜索文本', 'switch'),
      s('searchPanel.searchVisibleColumnsOnly', '仅搜索可见列', 'switch'),
      s('filterRow.visible', '行筛选', 'switch'),
      s('filterRow.operation', '行筛选默认操作', 'select', [
        { label: '等于', value: '=' },
        { label: '不等于', value: '<>' },
        { label: '小于', value: '<' },
        { label: '大于', value: '>' },
        { label: '小于等于', value: '<=' },
        { label: '大于等于', value: '>=' },
        { label: '之间', value: 'between' },
        { label: '包含', value: 'contains' },
        { label: '不包含', value: 'notcontains' },
        { label: '以...开头', value: 'startswith' },
        { label: '以...结尾', value: 'endswith' },
      ]),
      s('filterRow.applyFilter', '行筛选应用时机', 'select', [
        { label: '自动', value: 'auto' },
        { label: '点击应用', value: 'onClick' },
      ]),
      s('headerFilter.visible', '表头筛选', 'switch'),
      s('headerFilter.allowSearch', '表头筛选搜索', 'switch'),
      // ---- 外观样式 ----
      heightSetter,
      s('showBorders', '边框', 'switch'),
      s('showRowLines', '行线', 'switch'),
      s('showColumnLines', '列线', 'switch'),
      s('columnAutoWidth', '列宽自适应', 'switch'),
      s('allowColumnReordering', '列可拖拽排序', 'switch'),
      s('allowColumnResizing', '列可调整宽度', 'switch'),
      // ---- 高级配置 ----
      jsonSetter('grouping', '分组高级配置'),
      s('summary', '汇总高级配置', 'summary'),
    ],
  },
  {
    type: 'dx-tree-list',
    title: '树形表格',
    enName: 'TreeList',
    category: '数据',
    icon: '⊞',
    defaultProps: {
      visible: true,
      showColumnHeaders: true,
      showColumnLines: true,
      allowColumnReordering: true,
      dataSource: [
        { id: 1, parentId: 0, name: '总公司', size: '' },
        { id: 2, parentId: 1, name: '研发部', size: 42 },
        { id: 3, parentId: 1, name: '市场部', size: 28 },
        { id: 4, parentId: 2, name: '前端组', size: 18 },
      ],
      keyExpr: 'id',
      parentIdExpr: 'parentId',
      columns: ['name', 'size'],
      height: 260,
      showBorders: true,
    },
    propSetters: [jsonSetter('dataSource', '数据源'), jsonSetter('columns', '列配置'), heightSetter, s('showBorders', '边框', 'switch')],
  },
  {
    type: 'dx-list',
    title: '列表',
    enName: 'List',
    category: '数据',
    icon: '≡',
    defaultProps: {
      items: ['列表项 1', '列表项 2', '列表项 3', '列表项 4'],
      height: 220,
      width: 280,
      searchEnabled: true,
    },
    propSetters: [jsonSetter('items', '列表项'), heightSetter, widthSetter, s('searchEnabled', '搜索', 'switch'), s('selectionMode', '选择模式', 'select', [{ label: '无', value: 'none' }, { label: '单选', value: 'single' }, { label: '多选', value: 'multiple' }])],
  },
  {
    type: 'dx-tree-view',
    title: '树形控件',
    enName: 'TreeView',
    category: '数据',
    icon: '🌳',
    defaultProps: {
      items: [
        { id: 1, text: '公司', items: [{ id: 2, text: '研发部' }, { id: 3, text: '市场部' }] },
        { id: 4, text: '合作伙伴', items: [{ id: 5, text: '供应商A' }] },
      ],
      keyExpr: 'id',
      showCheckBoxesMode: 'none',
      height: 240,
      width: 280,
    },
    propSetters: [
      jsonSetter('items', '树节点'),
      heightSetter,
      widthSetter,
      s('showCheckBoxesMode', '复选框', 'select', [
        { label: '无', value: 'none' },
        { label: '显示复选框', value: 'normal' },
        { label: '全选复选框', value: 'selectAll' },
      ]),
    ],
  },
  {
    type: 'dx-pivot-grid',
    title: '数据透视表',
    enName: 'PivotGrid',
    category: '数据',
    icon: '◫',
    defaultProps: { dataSource: pivotDataSource, height: 300, showBorders: true },
    propSetters: [jsonSetter('dataSource', '数据源'), heightSetter, s('showBorders', '边框', 'switch')],
  },
  {
    type: 'dx-pivot-field-chooser',
    title: '透视字段选择器',
    enName: 'PivotGridFieldChooser',
    category: '数据',
    icon: '⊕',
    defaultProps: { dataSource: pivotDataSource, height: 300 },
    propSetters: [jsonSetter('dataSource', '数据源'), heightSetter],
  },
  {
    type: 'dx-filter-builder',
    title: '筛选构造器',
    enName: 'FilterBuilder',
    category: '数据',
    icon: '⧩',
    defaultProps: {
      fields: [
        { dataField: 'name', caption: '名称', dataType: 'string' },
        { dataField: 'age', caption: '年龄', dataType: 'number' },
      ],
      value: [],
      height: 160,
    },
    propSetters: [jsonSetter('fields', '字段定义'), jsonSetter('value', '筛选值'), heightSetter],
  },
  {
    type: 'dx-scheduler',
    title: '日程表',
    enName: 'Scheduler',
    category: '数据',
    icon: '🗓',
    defaultProps: {
      dataSource: appointments,
      currentDate: '2026-08-13',
      currentView: 'week',
      views: ['day', 'week', 'month'],
      height: 450,
    },
    propSetters: [jsonSetter('dataSource', '日程数据'), s('currentDate', '当前日期', 'input'), s('currentView', '视图', 'select', [{ label: '日', value: 'day' }, { label: '周', value: 'week' }, { label: '月', value: 'month' }]), heightSetter],
  },
  {
    type: 'dx-gantt',
    title: '甘特图',
    enName: 'Gantt',
    category: '数据',
    icon: '▭',
    defaultProps: {
      tasks: { dataSource: ganttTasks },
      height: 350,
      scaleType: 'days',
    },
    propSetters: [jsonSetter('tasks', '任务数据'), heightSetter],
  },
  {
    type: 'dx-file-manager',
    title: '文件管理器',
    enName: 'FileManager',
    category: '数据',
    icon: '🗀',
    defaultProps: { fileSystemProvider: [], height: 320 },
    propSetters: [jsonSetter('fileSystemProvider', '文件源'), heightSetter],
  },
  // ==================== 表单编辑 ====================
  {
    type: 'dx-text-box',
    title: '单行输入',
    enName: 'TextBox',
    category: '表单',
    icon: '⌨',
    defaultProps: { placeholder: '请输入', value: '', width: 240 },
    propSetters: [s('value', '默认值', 'input'), s('placeholder', '占位文字', 'input'), s('mode', '输入类型', 'select', [{ label: '文本', value: 'text' }, { label: '邮箱', value: 'email' }, { label: '密码', value: 'password' }, { label: '电话', value: 'tel' }]), widthSetter, disabledSetter],
  },
  {
    type: 'dx-text-area',
    title: '多行输入',
    enName: 'TextArea',
    category: '表单',
    icon: '▤',
    defaultProps: { placeholder: '请输入内容', value: '', width: 280, height: 90 },
    propSetters: [s('value', '默认值', 'textarea'), s('placeholder', '占位文字', 'input'), widthSetter, heightSetter, disabledSetter],
  },
  {
    type: 'dx-number-box',
    title: '数字输入',
    enName: 'NumberBox',
    category: '表单',
    icon: '#',
    defaultProps: { value: 0, showSpinButtons: true, width: 180 },
    propSetters: [s('value', '默认值', 'number'), s('min', '最小值', 'number'), s('max', '最大值', 'number'), s('step', '步长', 'number'), s('showSpinButtons', '步进按钮', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-check-box',
    title: '复选框',
    enName: 'CheckBox',
    category: '表单',
    icon: '☑',
    defaultProps: { value: true, text: '复选框' },
    propSetters: [s('text', '文字', 'input'), s('value', '选中', 'switch'), disabledSetter],
  },
  {
    type: 'dx-switch',
    title: '开关',
    enName: 'Switch',
    category: '表单',
    icon: '◐',
    defaultProps: { value: true },
    propSetters: [s('value', '开启', 'switch'), disabledSetter],
  },
  {
    type: 'dx-slider',
    title: '滑块',
    enName: 'Slider',
    category: '表单',
    icon: '⟟',
    defaultProps: { value: 50, min: 0, max: 100, width: 260, showRange: true },
    propSetters: [s('value', '默认值', 'number'), s('min', '最小值', 'number'), s('max', '最大值', 'number'), s('step', '步长', 'number'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-range-slider',
    title: '范围滑块',
    enName: 'RangeSlider',
    category: '表单',
    icon: '⟺',
    defaultProps: { value: [20, 60], min: 0, max: 100, width: 260 },
    propSetters: [jsonSetter('value', '范围值'), s('min', '最小值', 'number'), s('max', '最大值', 'number'), widthSetter],
  },
  {
    type: 'dx-calendar',
    title: '日历',
    enName: 'Calendar',
    category: '表单',
    icon: '🗓',
    defaultProps: { value: '2026-08-13', width: 280 },
    propSetters: [s('value', '选中日期', 'input'), s('zoomLevel', '视图层级', 'select', [{ label: '月', value: 'month' }, { label: '年', value: 'year' }, { label: '十年', value: 'decade' }, { label: '世纪', value: 'century' }]), widthSetter],
  },
  {
    type: 'dx-date-box',
    title: '日期选择',
    enName: 'DateBox',
    category: '表单',
    icon: '📅',
    defaultProps: { value: '2026-08-13', width: 220, type: 'date' },
    propSetters: [s('value', '默认值', 'input'), s('type', '类型', 'select', [{ label: '日期', value: 'date' }, { label: '时间', value: 'time' }, { label: '日期时间', value: 'datetime' }]), s('placeholder', '占位文字', 'input'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-date-range-box',
    title: '日期范围',
    enName: 'DateRangeBox',
    category: '表单',
    icon: '⇄',
    defaultProps: { value: ['2026-08-01', '2026-08-13'], width: 320 },
    propSetters: [jsonSetter('value', '范围值'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-color-box',
    title: '颜色选择',
    enName: 'ColorBox',
    category: '表单',
    icon: '🎨',
    defaultProps: { value: '#1976d2', width: 180, editAlphaChannel: true },
    propSetters: [s('value', '默认颜色', 'color'), s('editAlphaChannel', '透明度', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-select-box',
    title: '下拉选择',
    enName: 'SelectBox',
    category: '表单',
    icon: '▼',
    defaultProps: { items: ['选项一', '选项二', '选项三'], value: '选项一', width: 220 },
    propSetters: [s('value', '默认值', 'input'), jsonSetter('items', '选项'), s('placeholder', '占位文字', 'input'), s('searchEnabled', '可搜索', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-lookup',
    title: '查找选择',
    enName: 'Lookup',
    category: '表单',
    icon: '🔍',
    defaultProps: { items: ['北京', '上海', '杭州', '深圳', '广州'], placeholder: '请选择', width: 220 },
    propSetters: [jsonSetter('items', '选项'), s('placeholder', '占位文字', 'input'), s('searchEnabled', '可搜索', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-tag-box',
    title: '标签选择',
    enName: 'TagBox',
    category: '表单',
    icon: '🏷',
    defaultProps: { items: ['前端', '后端', '测试', '设计'], value: ['前端'], width: 280 },
    propSetters: [jsonSetter('items', '选项'), jsonSetter('value', '选中值'), s('placeholder', '占位文字', 'input'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-autocomplete',
    title: '自动补全',
    enName: 'Autocomplete',
    category: '表单',
    icon: '⌕',
    defaultProps: { items: ['张三', '张四', '李四', '王五'], placeholder: '输入搜索', width: 220 },
    propSetters: [jsonSetter('items', '候选项'), s('placeholder', '占位文字', 'input'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-radio-group',
    title: '单选组',
    enName: 'RadioGroup',
    category: '表单',
    icon: '◉',
    defaultProps: { items: ['选项 A', '选项 B', '选项 C'], value: '选项 A', layout: 'horizontal', width: 220, height: 36 },
    propSetters: [jsonSetter('items', '选项'), s('value', '默认值', 'input'), s('layout', '排列方式', 'select', [{ label: '水平', value: 'horizontal' }, { label: '垂直', value: 'vertical' }]), widthSetter, heightSetter, disabledSetter],
  },
  {
    type: 'dx-drop-down-button',
    title: '下拉按钮',
    enName: 'DropDownButton',
    category: '表单',
    icon: '⬇',
    defaultProps: { text: '下拉按钮', items: ['操作一', '操作二', '操作三'], width: 150 },
    propSetters: [s('text', '按钮文字', 'input'), jsonSetter('items', '选项'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-drop-down-box',
    title: '下拉容器',
    enName: 'DropDownBox',
    category: '表单',
    icon: '⤵',
    defaultProps: { placeholder: '请选择', width: 240 },
    propSetters: [s('placeholder', '占位文字', 'input'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-file-uploader',
    title: '文件上传',
    enName: 'FileUploader',
    category: '表单',
    icon: '⇧',
    defaultProps: { labelText: '选择或拖拽文件到此处', selectButtonText: '选择文件', width: 300 },
    propSetters: [s('labelText', '提示文字', 'input'), s('selectButtonText', '按钮文字', 'input'), s('multiple', '多文件', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-html-editor',
    title: '富文本编辑器',
    enName: 'HtmlEditor',
    category: '表单',
    icon: '✎',
    defaultProps: { value: '<p>在此输入富文本内容</p>', height: 220 },
    propSetters: [s('value', '内容 (HTML)', 'textarea'), heightSetter, disabledSetter],
  },

  // ==================== 导航 ====================
  {
    type: 'dx-toolbar',
    title: '工具栏',
    enName: 'Toolbar',
    category: '导航',
    icon: '⊞',
    defaultProps: {
      items: [{ widget: 'dxButton', options: { text: '保存' } }, { text: '工具栏标题', location: 'center' }],
      width: 360,
    },
    propSetters: [jsonSetter('items', '工具栏项'), widthSetter],
  },
  {
    type: 'dx-gallery',
    title: '画廊',
    enName: 'Gallery',
    category: '导航',
    icon: '🖼',
    defaultProps: { items: ['幻灯片一', '幻灯片二', '幻灯片三'], width: 320, height: 200, showNavButtons: true, showIndicator: true },
    propSetters: [jsonSetter('items', '画廊项'), widthSetter, heightSetter, s('showNavButtons', '导航按钮', 'switch'), s('loop', '循环', 'switch')],
  },
  {
    type: 'dx-speed-dial-action',
    title: '悬浮操作按钮',
    enName: 'SpeedDialAction',
    category: '操作',
    icon: '✚',
    defaultProps: { label: '操作', icon: 'add' },
    propSetters: [s('label', '标签', 'input'), s('icon', '图标', 'input')],
  },


  // ==================== 布局 ====================
  {
    type: 'dx-button-group',
    title: '按钮组',
    enName: 'ButtonGroup',
    category: '操作',
    icon: '▢▢',
    defaultProps: { items: [{ text: '选项一' }, { text: '选项二' }, { text: '选项三' }] },
    propSetters: [jsonSetter('items', '按钮项'), s('selectionMode', '选择模式', 'select', [{ label: '单选', value: 'single' }, { label: '多选', value: 'multiple' }, { label: '无', value: 'none' }]), disabledSetter],
  },
  {
    type: 'dx-button',
    title: '按钮',
    enName: 'Button',
    category: '操作',
    icon: '▢',
    defaultProps: { text: '按钮', type: 'default', stylingMode: 'contained', width: 120, permCode: '' },
    propSetters: [
      s('text', '按钮文字', 'input'),
      s('type', '类型', 'select', [{ label: '默认', value: 'default' }, { label: '普通', value: 'normal' }, { label: '成功', value: 'success' }, { label: '危险', value: 'danger' }]),
      s('stylingMode', '样式', 'select', [{ label: '实心', value: 'contained' }, { label: '描边', value: 'outlined' }, { label: '文字', value: 'text' }]),
      s('icon', '图标', 'input'),
      s('permCode', '权限码', 'input'),
      widthSetter,
      disabledSetter,
      s('openPage', '打开页面', 'pageOpen'),
    ],
  },
  {
    type: 'dx-progress-bar',
    title: '进度条',
    enName: 'ProgressBar',
    category: '操作',
    icon: '▰',
    defaultProps: { value: 60, width: 300, showStatus: true },
    propSetters: [s('value', '进度', 'number'), s('min', '最小值', 'number'), s('max', '最大值', 'number'), s('showStatus', '显示百分比', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'dx-load-indicator',
    title: '加载指示器',
    enName: 'LoadIndicator',
    category: '操作',
    icon: '◌',
    defaultProps: { visible: true, height: 48, width: 48 },
    propSetters: [s('visible', '显示', 'switch'), heightSetter, widthSetter],
  },

  // ==================== 弹层 ====================
  {
    type: 'dx-popup',
    title: '弹出框',
    enName: 'Popup',
    category: '弹层',
    icon: '❐',
    defaultProps: { visible: true, title: '弹出框标题', width: 380, height: 240, showTitle: true, dragEnabled: true, hideOnOutsideClick: false },
    propSetters: [s('visible', '显示', 'switch'), s('title', '标题', 'input'), widthSetter, heightSetter, s('showTitle', '显示标题', 'switch'), s('hideOnOutsideClick', '点击外部关闭', 'switch')],
  },
  {
    type: 'dx-toast',
    title: '轻提示',
    enName: 'Toast',
    category: '弹层',
    icon: '💡',
    defaultProps: { visible: true, message: '这是一条提示消息', type: 'info' },
    propSetters: [s('visible', '显示', 'switch'), s('message', '消息', 'input'), s('type', '类型', 'select', [{ label: '信息', value: 'info' }, { label: '成功', value: 'success' }, { label: '警告', value: 'warning' }, { label: '错误', value: 'error' }])],
  },
  {
    type: 'dx-load-panel',
    title: '加载遮罩',
    enName: 'LoadPanel',
    category: '弹层',
    icon: '◍',
    defaultProps: { visible: true, showIndicator: true, showPane: false, message: '加载中...', width: 160, height: 90, hideOnOutsideClick: false },
    propSetters: [s('visible', '显示', 'switch'), s('message', '提示文字', 'input'), s('showIndicator', '显示指示器', 'switch'), widthSetter, heightSetter],
  },
]
// ---------- 表单控件 label 属性 ----------

const labelDefaultProps: Record<string, unknown> = {
  visible: true,
  label: '',
  labelVisible: true,
  labelPosition: 'top',
  required: false,
  requiredPosition: 'after',
  tooltipEnabled: false,
  tooltipText: '',
}

const labelPropSetters: PropSetterConfig[] = [
  s('visible', '显示', 'switch'),
  s('label', '标签文字', 'input'),
  s('labelVisible', '标签显示', 'switch'),
  s('labelPosition', '标签位置', 'select', [{ label: '左侧', value: 'left' }, { label: '上方', value: 'top' }]),
  s('required', '必填', 'switch'),
  { ...s('requiredPosition', '星号位置', 'select', [{ label: '标签后', value: 'after' }, { label: '标签前', value: 'before' }]), visibleIf: { prop: 'required', equals: true } },
  s('tooltipEnabled', '启用介绍', 'switch'),
  { ...s('tooltipText', '介绍内容', 'textarea'), visibleIf: { prop: 'tooltipEnabled', equals: true } },
]

const FORM_CONTROL_TYPES = new Set([
  'dx-text-box', 'dx-text-area', 'dx-number-box', 'dx-check-box', 'dx-switch',
  'dx-slider', 'dx-range-slider', 'dx-calendar', 'dx-date-box', 'dx-date-range-box',
  'dx-color-box', 'dx-select-box', 'dx-lookup', 'dx-tag-box', 'dx-autocomplete',
  'dx-radio-group', 'dx-drop-down-button', 'dx-drop-down-box',
  'dx-file-uploader', 'dx-html-editor',
])

for (const mat of dxMaterialConfigs) {
  if (FORM_CONTROL_TYPES.has(mat.type)) {
    mat.defaultProps = { ...labelDefaultProps, label: mat.title, ...mat.defaultProps }
    mat.propSetters = [...mat.propSetters, ...labelPropSetters]
  }
  if (mat.type.startsWith('dx-') && mat.defaultProps.visible === undefined) {
    mat.defaultProps.visible = true
  }
}
