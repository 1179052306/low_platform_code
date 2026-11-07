/**
 * 物料注册：Element Plus / ECharts / 第三方控件替换 DevExtreme 控件。
 * 保留 data-grid / tree-list 仍用 DevExtreme 实现。
 * 复杂配置（columns / series / items / dataSource 等）通过 json 设置器编辑。
 */
import type { Component } from 'vue'
import type { MaterialConfig, PropSetterConfig } from '@/types/schema'

// ---- 表格类（Element Plus 封装：LcTable / LcTreeTable 替换 DevExtreme DataGrid/TreeList） ----
import LcTable from './components/LcTable.vue'
import LcTreeTable from './components/LcTreeTable.vue'

// ---- 表单编辑类（Element Plus 封装） ----
import ElTextBox from './components/ElTextBox.vue'
import ElTextArea from './components/ElTextArea.vue'
import ElNumberBox from './components/ElNumberBox.vue'
import ElCheckBox from './components/ElCheckBox.vue'
import ElSwitchBox from './components/ElSwitchBox.vue'
import ElSliderBox from './components/ElSliderBox.vue'
import ElRangeSlider from './components/ElRangeSlider.vue'
import ElCalendarBox from './components/ElCalendarBox.vue'
import ElDateBox from './components/ElDateBox.vue'
import ElDateRangeBox from './components/ElDateRangeBox.vue'
import ElColorBox from './components/ElColorBox.vue'
import ElSelectBox from './components/ElSelectBox.vue'
import ElLookup from './components/ElLookup.vue'
import ElTagBox from './components/ElTagBox.vue'
import ElAutocompleteBox from './components/ElAutocompleteBox.vue'
import ElRadioGroupBox from './components/ElRadioGroupBox.vue'
import ElDropDownBox from './components/ElDropDownBox.vue'
import ElFileUploader from './components/ElFileUploader.vue'
import ElHtmlEditor from './components/ElHtmlEditor.vue'

// ---- 操作类（Element Plus 封装） ----
import ElButtonBox from './components/ElButtonBox.vue'
import ElButtonGroupBox from './components/ElButtonGroupBox.vue'
import ElDropDownButton from './components/ElDropDownButton.vue'
import ElSpeedDialAction from './components/ElSpeedDialAction.vue'
import ElProgressBarBox from './components/ElProgressBarBox.vue'
import ElLoadIndicator from './components/ElLoadIndicator.vue'

// ---- 导航类（Element Plus 封装） ----
import ElToolbarBox from './components/ElToolbarBox.vue'
import ElGalleryBox from './components/ElGalleryBox.vue'

// ---- 弹层类（Element Plus 封装） ----
import ElPopupBox from './components/ElPopupBox.vue'
import ElToastBox from './components/ElToastBox.vue'
import ElLoadPanelBox from './components/ElLoadPanelBox.vue'

// ---- 图表类（ECharts 封装） ----
import ElChart from './components/ElChart.vue'
import ElPieChart from './components/ElPieChart.vue'
import ElPolarChart from './components/ElPolarChart.vue'
import ElBarGauge from './components/ElBarGauge.vue'
import ElBullet from './components/ElBullet.vue'
import ElCircularGauge from './components/ElCircularGauge.vue'
import ElLinearGauge from './components/ElLinearGauge.vue'
import ElFunnel from './components/ElFunnel.vue'
import ElSankey from './components/ElSankey.vue'
import ElSparkline from './components/ElSparkline.vue'
import ElTreeMap from './components/ElTreeMap.vue'
import ElRangeSelector from './components/ElRangeSelector.vue'

// ---- 复杂数据类（第三方/自封装） ----
import ElListBox from './components/ElListBox.vue'
import ElTreeViewBox from './components/ElTreeViewBox.vue'
import ElPivotGridBox from './components/ElPivotGridBox.vue'
import ElPivotFieldChooser from './components/ElPivotFieldChooser.vue'
import ElFilterBuilder from './components/ElFilterBuilder.vue'
import ElSchedulerBox from './components/ElSchedulerBox.vue'
import ElGanttBox from './components/ElGanttBox.vue'
import ElFileManagerBox from './components/ElFileManagerBox.vue'

/** 组件实现注册表 */
export const dxComponents: Record<string, Component> = {
  // 图表（ECharts 封装）
  'chart': ElChart as Component,
  'pie-chart': ElPieChart as Component,
  'polar-chart': ElPolarChart as Component,
  'bar-gauge': ElBarGauge as Component,
  'bullet': ElBullet as Component,
  'circular-gauge': ElCircularGauge as Component,
  'linear-gauge': ElLinearGauge as Component,
  'funnel': ElFunnel as Component,
  'sankey': ElSankey as Component,
  'sparkline': ElSparkline as Component,
  'tree-map': ElTreeMap as Component,
  'range-selector': ElRangeSelector as Component,
  // 数据（Element Plus 封装：LcTable / LcTreeTable）
  'data-grid': LcTable as Component,
  'tree-list': LcTreeTable as Component,
  // 复杂数据（第三方/自封装）
  'list': ElListBox as Component,
  'tree-view': ElTreeViewBox as Component,
  'pivot-grid': ElPivotGridBox as Component,
  'pivot-field-chooser': ElPivotFieldChooser as Component,
  'filter-builder': ElFilterBuilder as Component,
  'scheduler': ElSchedulerBox as Component,
  'gantt': ElGanttBox as Component,
  'file-manager': ElFileManagerBox as Component,
  // 表单（Element Plus 封装）
  'text-box': ElTextBox as Component,
  'text-area': ElTextArea as Component,
  'number-box': ElNumberBox as Component,
  'check-box': ElCheckBox as Component,
  'switch': ElSwitchBox as Component,
  'slider': ElSliderBox as Component,
  'range-slider': ElRangeSlider as Component,
  'calendar': ElCalendarBox as Component,
  'date-box': ElDateBox as Component,
  'date-range-box': ElDateRangeBox as Component,
  'color-box': ElColorBox as Component,
  'select-box': ElSelectBox as Component,
  'lookup': ElLookup as Component,
  'tag-box': ElTagBox as Component,
  'autocomplete': ElAutocompleteBox as Component,
  'radio-group': ElRadioGroupBox as Component,
  'drop-down-box': ElDropDownBox as Component,
  'file-uploader': ElFileUploader as Component,
  'html-editor': ElHtmlEditor as Component,
  // 导航（Element Plus 封装）
  'toolbar': ElToolbarBox as Component,
  'gallery': ElGalleryBox as Component,
  // 操作（Element Plus 封装）
  'button-group': ElButtonGroupBox as Component,
  'button': ElButtonBox as Component,
  'progress-bar': ElProgressBarBox as Component,
  'load-indicator': ElLoadIndicator as Component,
  'drop-down-button': ElDropDownButton as Component,
  'speed-dial-action': ElSpeedDialAction as Component,
  // 弹层（Element Plus 封装）
  'popup': ElPopupBox as Component,
  'toast': ElToastBox as Component,
  'load-panel': ElLoadPanelBox as Component,
}

/** 保留 DevExtreme 实现的物料 type（表格已全部迁移到 Element Plus 封装，无保留项） */
export const DEVEXTREME_TYPES = new Set<string>([])

/** 所有物料 type 集合 */
export const MATERIAL_TYPES = new Set(Object.keys(dxComponents))

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

/** 给一组 setter 批上 group 标签，用于属性面板折叠面板分组 */
const grp = (name: string, setters: PropSetterConfig[]): PropSetterConfig[] =>
  setters.map((st) => ({ ...st, group: name }))

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
    type: 'chart',
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
    type: 'pie-chart',
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
    type: 'polar-chart',
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
    type: 'bar-gauge',
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
    type: 'bullet',
    title: '子弹图',
    enName: 'Bullet',
    category: '图表',
    icon: '―',
    defaultProps: { value: 42, startScaleValue: 0, endScaleValue: 100, color: '#1976d2', width: 240, height: 40 },
    propSetters: [s('value', '数值', 'number'), s('startScaleValue', '起始值', 'number'), s('endScaleValue', '结束值', 'number'), s('color', '颜色', 'color'), widthSetter, heightSetter],
  },
  {
    type: 'circular-gauge',
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
    type: 'linear-gauge',
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
    type: 'funnel',
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
    type: 'sankey',
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
    type: 'sparkline',
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
    type: 'tree-map',
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
    type: 'range-selector',
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
    type: 'data-grid',
    title: '数据表格',
    enName: 'DataGrid',
    category: '数据',
    icon: '▦',
    defaultProps: {
      visible: true,
      showColumnHeaders: true,
      showColumnLines: true,
      showRowLines: true,
      allowColumnReordering: true,
      allowColumnResizing: true,
      dataSourceType: 'static',
      remoteMethod: 'POST',
      dataSource: [],
      keyExpr: '',
      keyStrategy: 'SNOWFLAKE',
      tableName: '',
      tableComment: '',
      allowEmptySave: true,

      parentType: 'page',
      parentRef: '',
      columns: [],
      height: 300,
      showBorders: true,
      columnAutoWidth: true,
      wordWrapEnabled: true,
      showOverflow: true,
      showHeaderOverflow: true,
      showSeq: true,
      seqWidth: 60,
      autoResize: true,
      size: 'mini',
      stripe: true,
      round: false,
      showFooter: false,
      showFooterOverflow: true,
      keepSource: false,
      virtualScroll: false,
      virtualScrollThreshold: 60,
      columnPersistence: false,
      editShowStatus: true,
      paging: { pageSize: 1000, enabled: true },
      pager: {
        showPageSizeSelector: true,
        allowedPageSizes: [15, 30, 50, 100],
        showInfo: true,
        showNavigationButtons: true,
      },
      filterRow: { visible: true },
      headerFilter: { visible: false },
      sorting: { mode: 'single' },
      selection: { mode: 'multiple', showCheckBoxesMode: 'onClick' },
      editing: { mode: 'cell', allowAdding: false, allowUpdating: false, allowDeleting: false },
      toolbar: {
        visible: true,
        items: [
          { name: 'addRow', caption: '新增行', icon: 'plus', location: 'before', visible: true, isCustom: false },
          { name: 'deleteRows', caption: '删除行', icon: 'trash', location: 'before', visible: true, isCustom: false },
          { name: 'import', caption: '导入', icon: 'upload', location: 'before', visible: false, isCustom: false },
          { name: 'sortAsc', caption: '升序', icon: 'arrowup', location: 'before', visible: false, isCustom: false },
          { name: 'sortDesc', caption: '降序', icon: 'arrowdown', location: 'before', visible: false, isCustom: false },
          { name: 'copyRow', caption: '复制行', icon: 'copy', location: 'before', visible: false, isCustom: false, openPage: { pageId: '__current__', bizMode: 'form', passSelectedRow: true } },
          { name: 'export', caption: '导出', icon: 'export', location: 'after', visible: false, isCustom: false, itemType: 'dropdown', children: [
            { name: 'export_page', caption: '页面数据导出', icon: 'export', location: 'after', visible: true, isCustom: false },
            { name: 'export_selected', caption: '选中导出', icon: 'export', location: 'after', visible: true, isCustom: false },
            { name: 'export_query', caption: '查询导出', icon: 'export', location: 'after', visible: true, isCustom: false },
          ] },
          { name: 'columnConfig', caption: '列配置', icon: 'settings', location: 'after', visible: false, isCustom: false },
        ],
      },
      batchToolbar: {
        visible: false,
        items: [
          { name: 'batchDelete', caption: '批量删除', icon: 'trash', location: 'before', visible: true, isCustom: false },
          { name: 'batchSubmit', caption: '批量下达', icon: 'upload', location: 'before', visible: true, isCustom: false },
          { name: 'batchApprove', caption: '批量审批', icon: 'check', location: 'before', visible: true, isCustom: false },
        ],
      },
      modalConfig: { width: 900, height: 600, maximizable: true, draggable: true },
    },
    propSetters: [
      ...grp('基本', [

        s('visible', '显示', 'switch'),
      ]),
      ...grp('数据源', [
        s('dataSourceType', '数据源类型', 'select', [
          { label: '静态数据', value: 'static' },
          { label: '远程加载', value: 'remote' },
        ]),
        { ...s('serviceId', 'API 服务', 'apiSelect'), visibleIf: { prop: 'dataSourceType', equals: 'remote' } },
        { ...s('firstLoad', '首次不加载', 'switch'), visibleIf: { prop: 'dataSourceType', equals: 'remote' } },
        { ...s('advancedSearch', '高级查询', 'switch'), visibleIf: { prop: 'dataSourceType', equals: 'remote' } },
        { ...s('presetFilterConditions', '预设过滤条件', 'filterConditions'), visibleIf: { prop: 'dataSourceType', equals: 'remote' } },
        { ...jsonSetter('dataSource', '数据源'), visibleIf: { prop: 'dataSourceType', equals: 'static' } },
      ]),
      ...grp('列配置', [
        { prop: 'columns', label: '列配置', type: 'columns' as const },
      ]),
      ...grp('明细表', [
        s('tableName', '明细表名', 'input'),
        s('tableComment', '明细表说明', 'input'),
        s('keyExpr', '主键字段', 'input'),
        s('keyStrategy', '主键策略', 'select', [
          { label: '雪花ID (Snowflake)', value: 'SNOWFLAKE' },
          { label: 'UUID', value: 'UUID' },
        ]),
        s('parentType', '上级类型', 'select', [
          { label: '页面', value: 'page' },
          { label: '表格', value: 'table' },
        ]),
        { prop: 'parentRef', label: '上级来源', type: 'parentTablePicker' as const },
      ]),
      ...grp('保存配置', [
        s('allowEmptySave', '允许空数据保存', 'switch'),
      ]),
      ...grp('工具栏', [
        s('toolbar.visible', '显示工具栏', 'switch'),
        { ...{ prop: 'toolbar.items', label: '工具栏按钮', type: 'toolbarItems' as const }, visibleIf: { prop: 'toolbar.visible', equals: true } },
        s('batchToolbar.visible', '显示批量工具栏', 'switch'),
        { ...{ prop: 'batchToolbar.items', label: '批量工具栏按钮', type: 'toolbarItems' as const }, visibleIf: { prop: 'batchToolbar.visible', equals: true } },
      ]),
      ...grp('分页', [
        s('paging.enabled', '分页', 'switch'),
        s('paging.pageSize', '每页条数', 'number'),
        s('paging.pageIndex', '当前页', 'number'),
        s('pager.showPageSizeSelector', '显示每页条数选择器', 'switch'),
        s('pager.showInfo', '显示分页信息', 'switch'),
        s('pager.showNavigationButtons', '显示导航按钮', 'switch'),
      ]),
      ...grp('编辑', [
        s('editing.mode', '编辑模式', 'select', [
          { label: '单元格', value: 'cell' },
          { label: '行', value: 'row' },
        ]),
        s('editTrigger', '编辑触发方式', 'select', [
          { label: '双击', value: 'dblclick' },
          { label: '单击', value: 'click' },
        ]),
        s('editShowStatus', '显示编辑状态', 'switch'),
        s('editAutoClear', '编辑结束自动清空', 'switch'),
        s('keepSource', '保留原始值', 'switch'),
        jsonSetter('editRules', '编辑校验规则'),
      ]),
      ...grp('选择', [
        s('selection.mode', '选择模式', 'select', [
          { label: '无', value: 'none' },
          { label: '单选', value: 'single' },
          { label: '多选', value: 'multiple' },
        ]),
        jsonSetter('radioConfig', '单选框配置'),
      ]),
      ...grp('排序与筛选', [
        s('sorting.mode', '排序模式', 'select', [
          { label: '单列', value: 'single' },
          { label: '多列', value: 'multiple' },
          { label: '禁用', value: 'none' },
        ]),
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
        s('headerFilter.visible', '表头筛选', 'switch'),
      ]),
      ...grp('外观样式', [
        heightSetter,
        s('size', '尺寸模式', 'select', [
          { label: '超紧凑', value: 'mini' },
          { label: '紧凑', value: 'small' },
          { label: '中等(默认)', value: 'medium' },
          { label: '宽松', value: 'large' },
          { label: '超宽松', value: 'xlarge' },
        ]),
        s('stripe', '斑马纹', 'switch'),
        s('showBorders', '边框', 'switch'),
        s('showRowLines', '行线', 'switch'),
        s('showColumnLines', '列线', 'switch'),
        s('columnAutoWidth', '列宽自适应', 'switch'),
        s('wordWrapEnabled', '单词换行', 'switch'),
        s('showOverflow', '内容超出提示', 'switch'),
        s('showHeaderOverflow', '表头超出提示', 'switch'),
        s('showSeq', '显示行号', 'switch'),
        s('seqWidth', '行号列宽', 'number'),
        s('autoResize', '自动响应式', 'switch'),
        s('round', '圆角边框', 'switch'),
        s('align', '全局对齐', 'select', [
          { label: '左对齐', value: 'left' },
          { label: '居中', value: 'center' },
          { label: '右对齐', value: 'right' },
        ]),
        s('headerAlign', '表头对齐', 'select', [
          { label: '左对齐', value: 'left' },
          { label: '居中', value: 'center' },
          { label: '右对齐', value: 'right' },
        ]),
        s('minHeight', '最小高度', 'input'),
        s('maxHeight', '最大高度', 'input'),
      ]),
      ...grp('列操作', [
        s('allowColumnReordering', '列可拖拽排序', 'switch'),
        s('allowColumnResizing', '列可调整宽度', 'switch'),
        s('columnPersistence', '列配置持久化', 'switch'),
      ]),
      ...grp('表尾与汇总', [
        s('showFooter', '显示表尾', 'switch'),
        s('showFooterOverflow', '表尾超出提示', 'switch'),
        s('footerAlign', '表尾对齐', 'select', [
          { label: '左对齐', value: 'left' },
          { label: '居中', value: 'center' },
          { label: '右对齐', value: 'right' },
        ]),
        jsonSetter('footerMethod', '表尾方法'),
        jsonSetter('footerData', '表尾数据'),
        s('summary', '汇总高级配置', 'summary'),
        jsonSetter('grouping', '分组高级配置'),
      ]),
      ...grp('弹窗', [
        s('modalConfig.draggable', '允许拖动', 'switch'),
      ]),
      ...grp('虚拟滚动', [
        s('virtualScroll', '纵向虚拟滚动', 'switch'),
        s('virtualScrollThreshold', '虚拟滚动阈值', 'number'),
        jsonSetter('virtualXConfig', '横向虚拟滚动配置'),
      ]),
      ...grp('高级配置', [
        jsonSetter('treeConfig', '树形配置'),
        jsonSetter('expandConfig', '展开行配置'),
        jsonSetter('rowDragConfig', '行拖拽排序配置'),
        jsonSetter('customConfig', '个性化配置(高级)'),
        jsonSetter('scrollbarConfig', '滚动条配置'),
        jsonSetter('tooltipConfig', '单元格提示配置'),
        jsonSetter('headerTooltipConfig', '表头提示配置'),
        jsonSetter('menuConfig', '右键菜单配置'),
        jsonSetter('keyboardConfig', '键盘导航配置'),
        jsonSetter('clipConfig', '复制粘贴配置'),
        jsonSetter('fnrConfig', '查找替换配置'),
        jsonSetter('zoomConfig', '缩放配置'),
        jsonSetter('seqConfig', '序号列配置'),
        jsonSetter('spanMethod', '合并单元格方法'),
        jsonSetter('mergeCells', '合并单元格配置'),
        jsonSetter('mergeHeaderCells', '合并表头配置'),
        jsonSetter('aggregateConfig', '数据聚合配置'),
        jsonSetter('emptyRender', '空内容渲染'),
        jsonSetter('importConfig', '导入配置'),
        jsonSetter('printConfig', '打印配置'),
        jsonSetter('nativeExportConfig', '原生导出配置'),
        jsonSetter('undoRedoHistoryConfig', '撤销恢复配置'),
      ]),
    ],
  },
  {
    type: 'tree-list',
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
    type: 'list',
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
    type: 'tree-view',
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
    type: 'pivot-grid',
    title: '数据透视表',
    enName: 'PivotGrid',
    category: '数据',
    icon: '◫',
    defaultProps: { dataSource: pivotDataSource, height: 300, showBorders: true },
    propSetters: [jsonSetter('dataSource', '数据源'), heightSetter, s('showBorders', '边框', 'switch')],
  },
  {
    type: 'pivot-field-chooser',
    title: '透视字段选择器',
    enName: 'PivotGridFieldChooser',
    category: '数据',
    icon: '⊕',
    defaultProps: { dataSource: pivotDataSource, height: 300 },
    propSetters: [jsonSetter('dataSource', '数据源'), heightSetter],
  },
  {
    type: 'filter-builder',
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
    type: 'scheduler',
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
    type: 'gantt',
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
    type: 'file-manager',
    title: '文件管理器',
    enName: 'FileManager',
    category: '数据',
    icon: '🗀',
    defaultProps: { fileSystemProvider: [], height: 320 },
    propSetters: [jsonSetter('fileSystemProvider', '文件源'), heightSetter],
  },
  // ==================== 表单编辑 ====================
  {
    type: 'text-box',
    title: '单行输入',
    enName: 'TextBox',
    category: '表单',
    icon: '⌨',
    defaultProps: { placeholder: '请输入', value: '', width: 240 },
    propSetters: [s('value', '默认值', 'input'), { ...s('placeholder', '占位文字', 'input'), hiddenIf: { prop: 'defaultValueExpr', notEmpty: true } }, s('mode', '输入类型', 'select', [{ label: '文本', value: 'text' }, { label: '邮箱', value: 'email' }, { label: '密码', value: 'password' }, { label: '电话', value: 'tel' }]), widthSetter, disabledSetter],
  },
  {
    type: 'text-area',
    title: '多行输入',
    enName: 'TextArea',
    category: '表单',
    icon: '▤',
    defaultProps: { placeholder: '请输入内容', value: '', width: 280, height: 90 },
    propSetters: [s('value', '默认值', 'textarea'), s('placeholder', '占位文字', 'input'), widthSetter, heightSetter, disabledSetter],
  },
  {
    type: 'number-box',
    title: '数字输入',
    enName: 'NumberBox',
    category: '表单',
    icon: '#',
    defaultProps: { showSpinButtons: true, controlsPosition: 'right', prefix: '', suffix: '', width: 180 },
    propSetters: [s('value', '默认值', 'number'), s('min', '最小值', 'number'), s('max', '最大值', 'number'), s('step', '步长', 'number'), s('showSpinButtons', '步进按钮', 'switch'), s('controlsPosition', '按钮位置', 'select', [{ label: '默认', value: '' }, { label: '右侧', value: 'right' }]), s('prefix', '前缀', 'input'), s('suffix', '后缀', 'input'), widthSetter, disabledSetter],
  },
  {
    type: 'check-box',
    title: '复选框',
    enName: 'CheckBox',
    category: '表单',
    icon: '☑',
    defaultProps: { value: true, text: '复选框' },
    propSetters: [s('text', '文字', 'input'), s('value', '选中', 'switch'), disabledSetter],
  },
  {
    type: 'switch',
    title: '开关',
    enName: 'Switch',
    category: '表单',
    icon: '◐',
    defaultProps: { value: true },
    propSetters: [s('value', '开启', 'switch'), disabledSetter],
  },
  {
    type: 'slider',
    title: '滑块',
    enName: 'Slider',
    category: '表单',
    icon: '⟟',
    defaultProps: { value: 50, min: 0, max: 100, width: 260, showRange: true },
    propSetters: [s('value', '默认值', 'number'), s('min', '最小值', 'number'), s('max', '最大值', 'number'), s('step', '步长', 'number'), widthSetter, disabledSetter],
  },
  {
    type: 'range-slider',
    title: '范围滑块',
    enName: 'RangeSlider',
    category: '表单',
    icon: '⟺',
    defaultProps: { value: [20, 60], min: 0, max: 100, width: 260 },
    propSetters: [jsonSetter('value', '范围值'), s('min', '最小值', 'number'), s('max', '最大值', 'number'), widthSetter],
  },
  {
    type: 'calendar',
    title: '日历',
    enName: 'Calendar',
    category: '表单',
    icon: '🗓',
    defaultProps: { value: '2026-08-13', width: 280 },
    propSetters: [s('value', '选中日期', 'input'), s('zoomLevel', '视图层级', 'select', [{ label: '月', value: 'month' }, { label: '年', value: 'year' }, { label: '十年', value: 'decade' }, { label: '世纪', value: 'century' }]), widthSetter],
  },
  {
    type: 'date-box',
    title: '日期选择',
    enName: 'DateBox',
    category: '表单',
    icon: '📅',
    defaultProps: { value: '2026-08-13', width: 220, type: 'date' },
    propSetters: [s('value', '默认值', 'input'), s('type', '类型', 'select', [{ label: '日期', value: 'date' }, { label: '时间', value: 'time' }, { label: '日期时间', value: 'datetime' }]), s('placeholder', '占位文字', 'input'), widthSetter, disabledSetter],
  },
  {
    type: 'date-range-box',
    title: '日期范围',
    enName: 'DateRangeBox',
    category: '表单',
    icon: '⇄',
    defaultProps: { value: ['2026-08-01', '2026-08-13'], width: 320 },
    propSetters: [jsonSetter('value', '范围值'), widthSetter, disabledSetter],
  },
  {
    type: 'color-box',
    title: '颜色选择',
    enName: 'ColorBox',
    category: '表单',
    icon: '🎨',
    defaultProps: { value: '#1976d2', width: 180, editAlphaChannel: true },
    propSetters: [s('value', '默认颜色', 'color'), s('editAlphaChannel', '透明度', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'select-box',
    title: '下拉选择',
    enName: 'SelectBox',
    category: '表单',
    icon: '▼',
    defaultProps: { items: [{ value: '1', text: '选项一' }, { value: '2', text: '选项二' }, { value: '3', text: '选项三' }], value: '1', displayExpr: 'text', valueExpr: 'value', width: 220 },
    propSetters: [s('value', '默认值', 'itemsSelect'), s('items', '选项', 'optionsEditor'), s('placeholder', '占位文字', 'input'), s('searchEnabled', '可搜索', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'lookup',
    title: '查找选择',
    enName: 'Lookup',
    category: '表单',
    icon: '🔍',
    defaultProps: { items: ['北京', '上海', '杭州', '深圳', '广州'], placeholder: '请选择', width: 220 },
    propSetters: [jsonSetter('items', '选项'), s('placeholder', '占位文字', 'input'), s('searchEnabled', '可搜索', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'tag-box',
    title: '标签选择',
    enName: 'TagBox',
    category: '表单',
    icon: '🏷',
    defaultProps: { items: ['前端', '后端', '测试', '设计'], value: ['前端'], width: 280 },
    propSetters: [jsonSetter('items', '选项'), jsonSetter('value', '选中值'), s('placeholder', '占位文字', 'input'), widthSetter, disabledSetter],
  },
  {
    type: 'autocomplete',
    title: '自动补全',
    enName: 'Autocomplete',
    category: '表单',
    icon: '⌕',
    defaultProps: { items: ['张三', '张四', '李四', '王五'], placeholder: '输入搜索', width: 220 },
    propSetters: [jsonSetter('items', '候选项'), s('placeholder', '占位文字', 'input'), widthSetter, disabledSetter],
  },
  {
    type: 'radio-group',
    title: '单选组',
    enName: 'RadioGroup',
    category: '表单',
    icon: '◉',
    defaultProps: { items: ['选项 A', '选项 B', '选项 C'], value: '选项 A', layout: 'horizontal', width: 220, height: 36 },
    propSetters: [jsonSetter('items', '选项'), s('value', '默认值', 'input'), s('layout', '排列方式', 'select', [{ label: '水平', value: 'horizontal' }, { label: '垂直', value: 'vertical' }]), widthSetter, heightSetter, disabledSetter],
  },
  {
    type: 'drop-down-button',
    title: '下拉按钮',
    enName: 'DropDownButton',
    category: '操作',
    icon: '⬇',
    defaultProps: { text: '下拉按钮', items: ['操作一', '操作二', '操作三'], width: 150 },
    propSetters: [s('text', '按钮文字', 'input'), jsonSetter('items', '选项'), widthSetter, disabledSetter],
  },
  {
    type: 'drop-down-box',
    title: '下拉容器',
    enName: 'DropDownBox',
    category: '表单',
    icon: '⤵',
    defaultProps: { placeholder: '请选择', width: 240 },
    propSetters: [s('placeholder', '占位文字', 'input'), widthSetter, disabledSetter],
  },
  {
    type: 'file-uploader',
    title: '文件上传',
    enName: 'FileUploader',
    category: '表单',
    icon: '⇧',
    defaultProps: { labelText: '选择或拖拽文件到此处', selectButtonText: '选择文件', width: 300 },
    propSetters: [s('labelText', '提示文字', 'input'), s('selectButtonText', '按钮文字', 'input'), s('multiple', '多文件', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'html-editor',
    title: '富文本编辑器',
    enName: 'HtmlEditor',
    category: '表单',
    icon: '✎',
    defaultProps: { value: '<p>在此输入富文本内容</p>', height: 220 },
    propSetters: [s('value', '内容 (HTML)', 'textarea'), heightSetter, disabledSetter],
  },

  // ==================== 导航 ====================
  {
    type: 'toolbar',
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
    type: 'gallery',
    title: '画廊',
    enName: 'Gallery',
    category: '导航',
    icon: '🖼',
    defaultProps: { items: ['幻灯片一', '幻灯片二', '幻灯片三'], width: 320, height: 200, showNavButtons: true, showIndicator: true },
    propSetters: [jsonSetter('items', '画廊项'), widthSetter, heightSetter, s('showNavButtons', '导航按钮', 'switch'), s('loop', '循环', 'switch')],
  },
  {
    type: 'speed-dial-action',
    title: '悬浮操作按钮',
    enName: 'SpeedDialAction',
    category: '操作',
    icon: '✚',
    defaultProps: { label: '操作', icon: 'add' },
    propSetters: [s('label', '标签', 'input'), s('icon', '图标', 'input')],
  },


  // ==================== 布局 ====================
  {
    type: 'button-group',
    title: '按钮组',
    enName: 'ButtonGroup',
    category: '操作',
    icon: '▢▢',
    defaultProps: { items: [{ text: '选项一' }, { text: '选项二' }, { text: '选项三' }] },
    propSetters: [jsonSetter('items', '按钮项'), s('selectionMode', '选择模式', 'select', [{ label: '单选', value: 'single' }, { label: '多选', value: 'multiple' }, { label: '无', value: 'none' }]), disabledSetter],
  },
  {
    type: 'button',
    title: '按钮',
    enName: 'Button',
    category: '操作',
    icon: '▢',
    defaultProps: { text: '按钮', type: 'default', stylingMode: 'contained', width: 120, permCode: '', showIcon: true, icon: 'check', badgeVisible: false, badgeContent: '', badgeType: 'danger' },
    propSetters: [
      s('text', '按钮文字', 'input'),
      s('type', '类型', 'select', [{ label: '默认', value: 'default' }, { label: '普通', value: 'normal' }, { label: '成功', value: 'success' }, { label: '危险', value: 'danger' }]),
      s('stylingMode', '样式', 'select', [{ label: '实心', value: 'contained' }, { label: '描边', value: 'outlined' }, { label: '文字', value: 'text' }]),
      s('showIcon', '显示图标', 'switch'),
      { ...s('icon', '图标', 'iconSelect'), visibleIf: { prop: 'showIcon', equals: true } },
      s('bgColor', '背景色', 'color'),
      s('textColor', '字体色', 'color'),
      s('hoverColor', '悬停色', 'color'),
      s('permCode', '权限码', 'input'),
      widthSetter,
      disabledSetter,
      s('openPage', '打开页面', 'pageOpen'),
      s('badgeVisible', '显示徽标', 'switch'),
      { ...s('badgeContent', '徽标内容', 'input'), visibleIf: { prop: 'badgeVisible', equals: true } },
      { ...s('badgeType', '徽标类型', 'select', [{ label: '主要', value: 'primary' }, { label: '成功', value: 'success' }, { label: '警告', value: 'warning' }, { label: '危险', value: 'danger' }, { label: '信息', value: 'info' }]), visibleIf: { prop: 'badgeVisible', equals: true } },
    ],
  },
  {
    type: 'progress-bar',
    title: '进度条',
    enName: 'ProgressBar',
    category: '操作',
    icon: '▰',
    defaultProps: { value: 60, width: 300, showStatus: true },
    propSetters: [s('value', '进度', 'number'), s('min', '最小值', 'number'), s('max', '最大值', 'number'), s('showStatus', '显示百分比', 'switch'), widthSetter, disabledSetter],
  },
  {
    type: 'load-indicator',
    title: '加载指示器',
    enName: 'LoadIndicator',
    category: '操作',
    icon: '◌',
    defaultProps: { visible: true, height: 48, width: 48 },
    propSetters: [s('visible', '显示', 'switch'), heightSetter, widthSetter],
  },

  // ==================== 弹层 ====================
  {
    type: 'popup',
    title: '弹出框',
    enName: 'Popup',
    category: '弹层',
    icon: '❐',
    defaultProps: { visible: true, title: '弹出框标题', width: 380, height: 240, showTitle: true, dragEnabled: true, hideOnOutsideClick: false },
    propSetters: [s('visible', '显示', 'switch'), s('title', '标题', 'input'), widthSetter, heightSetter, s('showTitle', '显示标题', 'switch'), s('hideOnOutsideClick', '点击外部关闭', 'switch')],
  },
  {
    type: 'toast',
    title: '轻提示',
    enName: 'Toast',
    category: '弹层',
    icon: '💡',
    defaultProps: { visible: true, message: '这是一条提示消息', type: 'info' },
    propSetters: [s('visible', '显示', 'switch'), s('message', '消息', 'input'), s('type', '类型', 'select', [{ label: '信息', value: 'info' }, { label: '成功', value: 'success' }, { label: '警告', value: 'warning' }, { label: '错误', value: 'error' }])],
  },
  {
    type: 'load-panel',
    title: '加载遮罩',
    enName: 'LoadPanel',
    category: '弹层',
    icon: '◍',
    defaultProps: { visible: true, showIndicator: true, showPane: false, message: '加载中...', width: 160, height: 90, hideOnOutsideClick: false },
    propSetters: [s('visible', '显示', 'switch'), s('message', '提示文字', 'input'), s('showIndicator', '显示指示器', 'switch'), widthSetter, heightSetter],
  },
  // ---------- 移动端专属控件 ----------
  {
    type: 'm-scaffold',
    title: '页面骨架',
    enName: 'Scaffold',
    category: '移动端布局',
    icon: '📱',
    isContainer: true,
    defaultProps: { title: '页面标题', appBarColor: '#1976D2', width: '100%', height: '100%', layout: 'vertical', gap: 8, padding: 0 },
    propSetters: [
      s('title', '标题', 'input'),
      s('appBarColor', '导航栏颜色', 'color'),
      s('layout', '布局方式', 'select', [{ label: '纵向排列', value: 'vertical' }, { label: '横向排列', value: 'horizontal' }, { label: '自由定位', value: 'free' }]),
      s('gap', '子控件间距', 'number'),
      s('padding', '内边距', 'number'),
          s('width', '宽度', 'input'),
      s('height', '高度', 'input'),
    ],
  },
  {
    type: 'm-list-view',
    title: '列表',
    enName: 'ListView',
    category: '移动端展示',
    icon: '📋',
    defaultProps: { bizCode: '', primaryKey: 'id' },
    propSetters: [s('bizCode', '业务编码', 'input'), s('primaryKey', '主键字段', 'input'), { prop: 'columns', label: '列配置', type: 'columns' as const }],
  },
  {
    type: 'm-card',
    title: '卡片',
    enName: 'Card',
    category: '移动端布局',
    icon: '📇',
    isContainer: true,
    defaultProps: { elevation: 2, width: '100%', layout: 'vertical', gap: 8, padding: 8 },
    propSetters: [
      s('elevation', '阴影', 'number'),
      s('layout', '布局方式', 'select', [{ label: '纵向排列', value: 'vertical' }, { label: '横向排列', value: 'horizontal' }, { label: '自由定位', value: 'free' }]),
      s('gap', '子控件间距', 'number'),
      s('padding', '内边距', 'number'),
          s('width', '宽度', 'input'),
      s('height', '高度', 'input'),
    ],
  },
  {
    type: 'm-app-bar',
    title: '导航栏',
    enName: 'AppBar',
    category: '移动端布局',
    icon: '◀️',
    defaultProps: { title: '标题', backgroundColor: '#1976D2', showBack: true },
    propSetters: [s('title', '标题', 'input'), s('backgroundColor', '背景色', 'color'), s('showBack', '显示返回', 'switch'),
      s('width', '宽度', 'input'),
      s('height', '高度', 'input'),
    ],
  },
  {
    type: 'm-bottom-nav',
    title: '底部导航',
    enName: 'BottomNav',
    category: '移动端布局',
    icon: '⬇️',
    defaultProps: {},
    propSetters: [      s('width', '宽度', 'input'),
      s('height', '高度', 'input'),
    ],
  },
  {
    type: 'm-fab',
    title: '悬浮按钮',
    enName: 'FloatingActionButton',
    category: '移动端布局',
    icon: '➕',
    defaultProps: { icon: 'add' },
    propSetters: [
      s('icon', '图标', 'iconSelect'),
      s('width', '宽度', 'input'),
      s('height', '高度', 'input'),
    ],
  },
  {
    type: 'm-avatar',
    title: '头像',
    enName: 'Avatar',
    category: '移动端展示',
    icon: '👤',
    defaultProps: { src: '', radius: 24 },
    propSetters: [s('src', '图片地址', 'input'), s('radius', '半径', 'number')],
  },
  {
    type: 'm-tab-bar',
    title: '选项卡',
    enName: 'TabBar',
    category: '移动端布局',
    icon: '📑',
    isContainer: true,
    defaultProps: { layout: 'vertical', gap: 8, padding: 8 },
    propSetters: [
      s('layout', '布局方式', 'select', [{ label: '纵向排列', value: 'vertical' }, { label: '横向排列', value: 'horizontal' }, { label: '自由定位', value: 'free' }]),
      s('gap', '子控件间距', 'number'),
      s('padding', '内边距', 'number'),
          s('width', '宽度', 'input'),
      s('height', '高度', 'input'),
    ],
  },
  {
    type: 'm-image',
    title: '图片',
    enName: 'Image',
    category: '移动端展示',
    icon: '🖼️',
    defaultProps: { src: '', height: 100 },
    propSetters: [s('src', '图片地址', 'input'), s('height', '高度', 'number')],
  },
  // ---- 移动端表单/操作 ----
  {
    type: 'm-button',
    title: '按钮',
    enName: 'MButton',
    category: '移动端表单',
    icon: '🔘',
    defaultProps: { text: '按钮', theme: 'primary', size: 'medium', block: false },
    propSetters: [
      s('text', '按钮文字', 'input'),
      s('theme', '主题', 'select', [{ label: '主要', value: 'primary' }, { label: '次要', value: 'default' }, { label: '危险', value: 'danger' }, { label: '警告', value: 'warning' }]),
      s('size', '尺寸', 'select', [{ label: '大', value: 'large' }, { label: '中', value: 'medium' }, { label: '小', value: 'small' }]),
      s('block', '撑满宽度', 'switch'),
    ],
  },
  {
    type: 'm-input',
    title: '输入框',
    enName: 'MInput',
    category: '移动端表单',
    icon: '⌨',
    defaultProps: { placeholder: '请输入', label: '标签', clearable: true, labelPosition: 'top', required: false },
    propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), s('placeholder', '占位文字', 'input'), s('clearable', '可清空', 'switch'),
    ],
  },
  {
    type: 'm-textarea',
    title: '多行输入',
    enName: 'MTextarea',
    category: '移动端表单',
    icon: '📝',
    defaultProps: { placeholder: '请输入内容', maxlength: 200, label: '', labelPosition: 'top', required: false },
    propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), s('placeholder', '占位文字', 'input'), s('maxlength', '最大字数', 'number'),
    ],
  },
  {
    type: 'm-switch',
    title: '开关',
    enName: 'MSwitch',
    category: '移动端表单',
    icon: '🔄',
    defaultProps: { label: '开关', value: true, labelPosition: 'top', required: false },
    propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), s('value', '开启', 'switch'),
    ],
  },
  {
    type: 'm-stepper',
    title: '步进器',
    enName: 'Stepper',
    category: '移动端表单',
    icon: '🔢',
    defaultProps: { value: 1, min: 0, max: 10, step: 1, label: '', labelPosition: 'top', required: false },
    propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), s('value', '当前值', 'number'), s('min', '最小值', 'number'), s('max', '最大值', 'number'), s('step', '步长', 'number'),
    ],
  },
  {
    type: 'm-rate',
    title: '评分',
    enName: 'Rate',
    category: '移动端表单',
    icon: '⭐',
    defaultProps: { value: 3, max: 5, allowHalf: false, label: '', labelPosition: 'top', required: false },
    propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), s('value', '当前值', 'number'), s('max', '最大星数', 'number'), s('allowHalf', '允许半选', 'switch'),
    ],
  },
  {
    type: 'm-search',
    title: '搜索框',
    enName: 'Search',
    category: '移动端表单',
    icon: '🔍',
    defaultProps: { placeholder: '搜索关键词', label: '', labelPosition: 'top', required: false },
    propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), s('placeholder', '占位文字', 'input'),
    ],
  },
  {
    type: 'm-slider',
    title: '滑块',
    enName: 'MSlider',
    category: '移动端表单',
    icon: '⟟',
    defaultProps: { value: 50, min: 0, max: 100, label: '', labelPosition: 'top', required: false },
    propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), s('value', '当前值', 'number'), s('min', '最小值', 'number'), s('max', '最大值', 'number'),
    ],
  },
  {
    type: 'm-radio',
    title: '单选组',
    enName: 'MRadio',
    category: '移动端表单',
    icon: '🔘',
    defaultProps: { options: [{ label: '选项一', value: '1' }, { label: '选项二', value: '2' }], value: '1', label: '', labelPosition: 'top', required: false },
    propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), jsonSetter('options', '选项'), s('value', '默认值', 'input'),
    ],
  },
  {
    type: 'm-checkbox',
    title: '多选组',
    enName: 'MCheckbox',
    category: '移动端表单',
    icon: '☑️',
    defaultProps: { options: [{ label: '选项一', value: '1' }, { label: '选项二', value: '2' }], value: ['1'], label: '', labelPosition: 'top', required: false },
    propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), jsonSetter('options', '选项'), jsonSetter('value', '选中值'),
    ],
  },
  {
    type: 'm-upload',
    title: '上传',
    enName: 'MUpload',
    category: '移动端表单',
    icon: '⬆️',
    defaultProps: { max: 5, label: '', labelPosition: 'top', required: false },
    propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), s('max', '最大数量', 'number')],
  },
  // ---- 移动端展示 ----
  {
    type: 'm-cell',
    title: '单元格',
    enName: 'MCell',
    category: '移动端展示',
    icon: '📋',
    defaultProps: { title: '标题', description: '描述', arrow: true },
    propSetters: [s('title', '标题', 'input'), s('description', '描述', 'input'), s('arrow', '显示箭头', 'switch')],
  },
  {
    type: 'm-tag',
    title: '标签',
    enName: 'MTag',
    category: '移动端展示',
    icon: '🏷️',
    defaultProps: { text: '标签', theme: 'primary' },
    propSetters: [s('text', '文字', 'input'), s('theme', '主题', 'select', [{ label: '主要', value: 'primary' }, { label: '成功', value: 'success' }, { label: '警告', value: 'warning' }, { label: '危险', value: 'danger' }])],
  },
  {
    type: 'm-badge',
    title: '徽标',
    enName: 'Badge',
    category: '移动端展示',
    icon: '🔔',
    defaultProps: { text: '新', dot: false },
    propSetters: [s('text', '内容', 'input'), s('dot', '圆点', 'switch')],
  },
  {
    type: 'm-notice-bar',
    title: '公告栏',
    enName: 'NoticeBar',
    category: '移动端展示',
    icon: '📢',
    defaultProps: { text: '这是一条公告信息' },
    propSetters: [s('text', '公告内容', 'input')],
  },
  {
    type: 'm-progress',
    title: '进度条',
    enName: 'MProgress',
    category: '移动端展示',
    icon: '📊',
    defaultProps: { value: 60, max: 100 },
    propSetters: [s('value', '当前值', 'number'), s('max', '最大值', 'number')],
  },
  {
    type: 'm-divider',
    title: '分割线',
    enName: 'Divider',
    category: '移动端展示',
    icon: '➖',
    defaultProps: { text: '' },
    propSetters: [s('text', '文字', 'input')],
  },
  {
    type: 'm-collapse',
    title: '折叠面板',
    enName: 'Collapse',
    category: '移动端布局',
    icon: '📂',
    isContainer: true,
    defaultProps: { layout: 'vertical', gap: 8, padding: 8 },
    propSetters: [s('layout', '布局方式', 'select', [{ label: '纵向排列', value: 'vertical' }, { label: '横向排列', value: 'horizontal' }]), s('gap', '间距', 'number'), s('padding', '内边距', 'number'),
      s('width', '宽度', 'input'),
      s('height', '高度', 'input'),
    ],
  },
  {
    type: 'm-empty',
    title: '空状态',
    enName: 'MEmpty',
    category: '移动端展示',
    icon: '🕳️',
    defaultProps: { description: '暂无数据' },
    propSetters: [s('description', '描述', 'input')],
  },
  {
    type: 'm-loading',
    title: '加载',
    enName: 'MLoading',
    category: '移动端展示',
    icon: '⏳',
    defaultProps: { text: '加载中...' },
    propSetters: [s('text', '文字', 'input')],
  },
  {
    type: 'm-result',
    title: '结果页',
    enName: 'Result',
    category: '移动端展示',
    icon: '✅',
    defaultProps: { title: '操作成功', description: '内容详情' },
    propSetters: [s('title', '标题', 'input'), s('description', '描述', 'input')],
  },
  // ---- 移动端弹层 ----
  {
    type: 'm-toast',
    title: '轻提示',
    enName: 'MToast',
    category: '移动端弹层',
    icon: '💬',
    defaultProps: { text: '提示内容', theme: 'info' },
    propSetters: [s('text', '内容', 'input'), s('theme', '类型', 'select', [{ label: '信息', value: 'info' }, { label: '成功', value: 'success' }, { label: '警告', value: 'warning' }, { label: '错误', value: 'error' }])],
  },
  {
    type: 'm-dialog',
    title: '对话框',
    enName: 'MDialog',
    category: '移动端弹层',
    icon: '❓',
    defaultProps: { title: '标题', content: '对话框内容', confirmBtn: '确认', cancelBtn: '取消' },
    propSetters: [s('title', '标题', 'input'), s('content', '内容', 'input'), s('confirmBtn', '确认按钮', 'input'), s('cancelBtn', '取消按钮', 'input')],
  },
  {
    type: 'm-action-sheet',
    title: '动作面板',
    enName: 'ActionSheet',
    category: '移动端弹层',
    icon: '📂',
    defaultProps: { items: ['操作一', '操作二', '操作三'] },
    propSetters: [jsonSetter('items', '操作项')],
  },
  {
    type: 'm-popup',
    title: '弹出层',
    enName: 'MPopup',
    category: '移动端布局',
    icon: '📦',
    isContainer: true,
    defaultProps: { visible: true, placement: 'bottom', layout: 'vertical', gap: 8, padding: 16 },
    propSetters: [s('visible', '显示', 'switch'), s('placement', '位置', 'select', [{ label: '底部', value: 'bottom' }, { label: '顶部', value: 'top' }, { label: '左侧', value: 'left' }, { label: '右侧', value: 'right' }, { label: '居中', value: 'center' }]), s('layout', '布局方式', 'select', [{ label: '纵向', value: 'vertical' }, { label: '横向', value: 'horizontal' }]), s('gap', '间距', 'number'), s('padding', '内边距', 'number'),
      s('width', '宽度', 'input'),
      s('height', '高度', 'input'),
    ],
  },
  // ---- getwidget 扩展控件 ----
  { type: 'm-accordion', title: '手风琴', enName: 'Accordion', category: '移动端布局', icon: '📂', defaultProps: { items: [{ title: '标题一', content: '内容一' }, { title: '标题二', content: '内容二' }] }, propSetters: [jsonSetter('items', '折叠项')] },
  { type: 'm-border', title: '边框容器', enName: 'Border', category: '移动端布局', icon: '🔲', isContainer: true, defaultProps: { layout: 'vertical', gap: 8, padding: 8 }, propSetters: [s('layout', '布局方式', 'select', [{ label: '纵向', value: 'vertical' }, { label: '横向', value: 'horizontal' }]), s('gap', '间距', 'number'), s('padding', '内边距', 'number'), s('width', '宽度', 'input'), s('height', '高度', 'input')] },
  { type: 'm-dropdown', title: '下拉选择', enName: 'Dropdown', category: '移动端表单', icon: '▼', defaultProps: { items: ['选项一', '选项二', '选项三'], label: '', labelPosition: 'top', required: false }, propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), jsonSetter('items', '选项')] },
  { type: 'm-multi-select', title: '多选', enName: 'MultiSelect', category: '移动端表单', icon: '☑', defaultProps: { items: ['选项一', '选项二', '选项三'], label: '', labelPosition: 'top', required: false }, propSetters: [s('label', '标签', 'input'), s('labelPosition', '标签位置', 'select', [{ label: '上方', value: 'top' }, { label: '左侧', value: 'left' }]), s('required', '必填', 'switch'), jsonSetter('items', '选项')] },
  { type: 'm-checkbox-list-tile', title: '复选列表项', enName: 'CheckboxListTile', category: '移动端表单', icon: '☑', defaultProps: { title: '标题', subtitle: '副标题', value: false }, propSetters: [s('title', '标题', 'input'), s('subtitle', '副标题', 'input'), s('value', '选中', 'switch')] },
  { type: 'm-radio-list-tile', title: '单选列表项', enName: 'RadioListTile', category: '移动端表单', icon: '◉', defaultProps: { title: '标题', subtitle: '副标题', value: '1', groupValue: '1' }, propSetters: [s('title', '标题', 'input'), s('subtitle', '副标题', 'input'), s('value', '值', 'input'), s('groupValue', '组值', 'input')] },
  { type: 'm-button-bar', title: '按钮栏', enName: 'ButtonBar', category: '移动端表单', icon: '▢▢', defaultProps: { buttons: [{ text: '确定' }, { text: '取消' }] }, propSetters: [jsonSetter('buttons', '按钮')] },
  { type: 'm-typography', title: '排版文字', enName: 'Typography', category: '移动端展示', icon: '📝', defaultProps: { text: '文字内容' }, propSetters: [s('text', '文字', 'input')] },
  { type: 'm-animation', title: '动画', enName: 'Animation', category: '移动端展示', icon: '✨', defaultProps: { text: '动画文字' }, propSetters: [s('text', '文字', 'input')] },
  { type: 'm-carousel', title: '轮播', enName: 'Carousel', category: '移动端展示', icon: '🎠', defaultProps: { items: [{ image: 'https://picsum.photos/400/200' }] }, propSetters: [jsonSetter('items', '轮播项')] },
  { type: 'm-image-overlay', title: '图片覆盖层', enName: 'ImageOverlay', category: '移动端展示', icon: '🖼', defaultProps: { src: '', title: '标题', subtitle: '副标题' }, propSetters: [s('src', '图片地址', 'input'), s('title', '标题', 'input'), s('subtitle', '副标题', 'input')] },
  { type: 'm-button-badge', title: '按钮徽标', enName: 'ButtonBadge', category: '移动端展示', icon: '🔔', defaultProps: { text: '按钮', badgeText: '1' }, propSetters: [s('text', '按钮文字', 'input'), s('badgeText', '徽标文字', 'input')] },
  { type: 'm-icon-badge', title: '图标徽标', enName: 'IconBadge', category: '移动端展示', icon: '🔔', defaultProps: { text: '1' }, propSetters: [s('text', '徽标文字', 'input')] },
  { type: 'm-shimmer', title: '骨架屏', enName: 'Shimmer', category: '移动端展示', icon: '⏳', defaultProps: {}, propSetters: [] },
  { type: 'm-segment-tabs', title: '分段标签', enName: 'SegmentTabs', category: '移动端布局', icon: '📑', defaultProps: { tabs: [{ label: '标签一' }, { label: '标签二' }] }, propSetters: [jsonSetter('tabs', '标签项')] },
  { type: 'm-tabs', title: '标签栏', enName: 'Tabs', category: '移动端布局', icon: '📑', defaultProps: { tabs: [{ label: '标签一' }, { label: '标签二' }] }, propSetters: [jsonSetter('tabs', '标签项')] },
  { type: 'm-tab-bar-view', title: '标签视图', enName: 'TabBarView', category: '移动端布局', icon: '📑', defaultProps: { tabs: [{ label: '页面一' }, { label: '页面二' }] }, propSetters: [jsonSetter('tabs', '标签项')] },
  { type: 'm-drawer', title: '抽屉', enName: 'Drawer', category: '移动端布局', icon: '📂', defaultProps: { items: [{ label: '菜单项一' }, { label: '菜单项二' }] }, propSetters: [jsonSetter('items', '菜单项')] },
  { type: 'm-sticky-header', title: '粘性头部', enName: 'StickyHeader', category: '移动端布局', icon: '📌', isContainer: true, defaultProps: { title: '标题', layout: 'vertical', gap: 8, padding: 8 }, propSetters: [s('title', '标题', 'input'), s('layout', '布局方式', 'select', [{ label: '纵向', value: 'vertical' }, { label: '横向', value: 'horizontal' }]), s('gap', '间距', 'number'), s('padding', '内边距', 'number')] },
  { type: 'm-alert', title: '警告框', enName: 'Alert', category: '移动端弹层', icon: '⚠', defaultProps: { title: '提示', content: '警告内容' }, propSetters: [s('title', '标题', 'input'), s('content', '内容', 'input')] },
  { type: 'm-bottom-sheet', title: '底部弹窗', enName: 'BottomSheet', category: '移动端弹层', icon: '⬇', defaultProps: { title: '底部弹窗' }, propSetters: [s('title', '标题', 'input')] },
  { type: 'm-icon-button', title: '图标按钮', enName: 'IconButton', category: '移动端表单', icon: '🔘', defaultProps: { icon: 'add' }, propSetters: [s('icon', '图标', 'iconSelect')] },
  { type: 'm-social-button', title: '社交按钮', enName: 'SocialButton', category: '移动端表单', icon: '👥', defaultProps: { text: '社交登录' }, propSetters: [s('text', '文字', 'input')] },
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
  'text-box', 'text-area', 'number-box', 'check-box', 'switch',
  'slider', 'range-slider', 'calendar', 'date-box', 'date-range-box',
  'color-box', 'select-box', 'lookup', 'tag-box', 'autocomplete',
  'radio-group', 'drop-down-button', 'drop-down-box',
  'file-uploader', 'html-editor',
])

for (const mat of dxMaterialConfigs) {
  if (FORM_CONTROL_TYPES.has(mat.type)) {
    mat.defaultProps = { ...labelDefaultProps, label: mat.title, ...mat.defaultProps }
    mat.propSetters = [...mat.propSetters, ...labelPropSetters]
  }
  if ((MATERIAL_TYPES.has(mat.type) || mat.type.startsWith('m-')) && mat.defaultProps.visible === undefined) {
    mat.defaultProps.visible = true
  }
  if (!FORM_CONTROL_TYPES.has(mat.type) && !mat.propSetters.some((p) => p.prop === 'visible')) {
    mat.propSetters = [s('visible', '显示', 'switch'), ...mat.propSetters]
  }
}
