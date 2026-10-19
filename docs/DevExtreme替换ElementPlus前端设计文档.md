# DevExtreme 控件替换 Element Plus 前端设计文档

> 范围：`D:\code\web-code` 前端低代码平台
> 目标：将所有 DevExtreme 控件（除 DxDataGrid 数据表格、DxTreeList 树形表格外）替换为 Element Plus / ECharts / 第三方控件，并完整保留原有功能。
> 原则：**语义化去前缀命名**——type 与 UI 框架解耦，去掉 `dx-`/`el-` 前缀用语义名（如 `text-box`、`select-box`、`data-grid`）。存量页面配置可全删，无需数据迁移。保留 `data-grid`/`tree-list` 仍用 DevExtreme 实现。

---

## 1. 现状摸底

### 1.1 DevExtreme 依赖
- `devextreme` ^26.1.4、`devextreme-vue` 26.1.4、`@devexpress/analytics-core` 26.1、`devexpress-reporting` 26.1
- 样式入口 `main.ts`：`import 'devextreme/dist/css/dx.light.css'`
- 多语言 `store/lang.ts` 第 152-183 行动态加载 `devextreme/localization`

### 1.2 需替换的 DevExtreme 物料（50 个，保留 DxDataGrid/DxTreeList）
- 表单编辑类 19 个
- 操作类 6 个
- 导航类 2 个
- 弹层类 3 个
- 图表类 12 个
- 复杂数据类 8 个（含 List/TreeView/FilterBuilder 等可替换 + Scheduler/Gantt/FileManager/PivotGrid 需第三方）

### 1.3 实际模板使用的高频 Dx 组件（替换主体）
| 组件 | 模板使用次数 | 涉及文件数 |
|------|------------|-----------|
| DxButton | 83 | ~20 |
| DxTextBox | 28 | ~12 |
| DxSelectBox | 11 | ~8 |
| DxToolbar | 10 | ~6 |
| DxNumberBox | 6 | ~5 |
| DxColorBox | 5 | ~3 |
| DxSwitch | 3 | ~3 |
| DxCheckBox | 2 | ~2 |
| DxTreeView | 1 | 1 |
| DxLoadPanel | 1 | 1 |

### 1.4 保留不替换的 DevExtreme 组件
| 组件 | 原因 |
|------|------|
| DxDataGrid | 用户明确保留（15 文件使用） |
| DxTreeList | 用户明确保留（物料注册） |
| DxColumn | DxDataGrid 列定义子组件（83 处） |
| DxMasterDetail | DxDataGrid 主从表子组件 |
| DxItem | DxToolbar/DxDataGrid 子项（28 处，随 DxToolbar 处理） |
| DxValidator | 校验适配层内部复用（见 §5） |
| DxBaseData | 自定义基础资料控件，内部用 DxDataGrid（保留） |

### 1.5 涉及文件清单（27 个）
- **views/**（19 个）：ApiManageView、BizMetaView、LangManageView、CacheManageView、DataDictView、LangListManageView、DesignerView、PageManageView、PackageManagerView、SystemManageView、WorkflowDesignerView、TodoView、PageLangManageView、MetaManageView、ThemeManageView 等
- **components/designer/**（6 个）：PropertyPanel、ApiSelectDialog、PageSelectDialog、VersionHistoryPanel、ThemeSelectDialog、CanvasNode
- **components/renderer/**（2 个）：RendererNode、AdvancedSearchPanel
- **materials/**：dx-materials.ts、index.ts、dx-events.ts、dx-prop-meta.ts、dx-validation.ts
- **store/lang.ts**、**main.ts**

---

## 2. 控件映射总表

> 命名约定：封装组件文件名用 `El` 前缀（如 `ElTextBox.vue`）。**物料 type 去前缀语义化**：`dx-text-box`→`text-box`、`dx-select-box`→`select-box`、`dx-data-grid`→`data-grid`、`dx-tree-list`→`tree-list`，以此类推（全部去掉 `dx-` 前缀）。下表 type 列已按此命名。

### 2.1 表单编辑类（19 个）→ Element Plus
| DevExtreme | type（不变） | 封装组件 | Element Plus 原生 | 备注 |
|------------|-------------|---------|------------------|------|
| DxTextBox | dx-text-box | ElTextBox.vue | ElInput | value→modelValue |
| DxTextArea | dx-text-area | ElTextArea.vue | ElInput(type=textarea) | |
| DxNumberBox | dx-number-box | ElNumberBox.vue | ElInputNumber | min/max/step/precision |
| DxCheckBox | dx-check-box | ElCheckBox.vue | ElCheckbox | |
| DxSwitch | dx-switch | ElSwitchBox.vue | ElSwitch | |
| DxSlider | dx-slider | ElSliderBox.vue | ElSlider | |
| DxRangeSlider | dx-range-slider | ElRangeSlider.vue | ElSlider(range) | |
| DxCalendar | dx-calendar | ElCalendarBox.vue | ElDatePicker(type=date) | DevExtreme 日历无直接对应，用日期选择器 |
| DxDateBox | dx-date-box | ElDateBox.vue | ElDatePicker | type 映射：datetime→datetime, date→date, time→time |
| DxDateRangeBox | dx-date-range-box | ElDateRangeBox.vue | ElDatePicker(range) | |
| DxColorBox | dx-color-box | ElColorBox.vue | ElColorPicker | |
| DxSelectBox | dx-select-box | ElSelectBox.vue | ElSelect + ElOption | dataSource→options 适配 |
| DxLookup | dx-lookup | ElLookup.vue | ElSelect + 远程搜索 | 分页查找用远程搜索 |
| DxTagBox | dx-tag-box | ElTagBox.vue | ElSelect(multiple) | |
| DxAutocomplete | dx-autocomplete | ElAutocompleteBox.vue | ElAutocomplete | |
| DxRadioGroup | dx-radio-group | ElRadioGroupBox.vue | ElRadioGroup + ElRadio | |
| DxDropDownBox | dx-drop-down-box | ElDropDownBox.vue | ElPopover + 自定义内容 | 通用下拉容器 |
| DxFileUploader | dx-file-uploader | ElFileUploader.vue | ElUpload | |
| DxHtmlEditor | dx-html-editor | ElHtmlEditor.vue | 引入 wangeditor 或 quill | Element Plus 无富文本，需第三方 |

### 2.2 操作类（6 个）→ Element Plus
| DevExtreme | type | 封装组件 | Element Plus | 备注 |
|------------|------|---------|-------------|------|
| DxButton | dx-button | ElButtonBox.vue | ElButton | 三色配置保留（见 §6.3） |
| DxButtonGroup | dx-button-group | ElButtonGroupBox.vue | ElButtonGroup | |
| DxDropDownButton | dx-drop-down-button | ElDropDownButton.vue | ElDropdown | |
| DxSpeedDialAction | dx-speed-dial-action | ElSpeedDialAction.vue | 自封装（ElButton 悬浮） | |
| DxProgressBar | dx-progress-bar | ElProgressBarBox.vue | ElProgress | |
| DxLoadIndicator | dx-load-indicator | ElLoadIndicator.vue | ElLoading | |

### 2.3 导航类（2 个）
| DevExtreme | type | 封装组件 | 方案 | 备注 |
|------------|------|---------|------|------|
| DxToolbar | dx-toolbar | ElToolbarBox.vue | 自封装（ElButton + ElTooltip + ElDropdown） | DxItem 子项模式适配 |
| DxGallery | dx-gallery | ElGalleryBox.vue | ElCarousel | |

### 2.4 弹层类（3 个）→ Element Plus
| DevExtreme | type | 封装组件 | Element Plus | 备注 |
|------------|------|---------|-------------|------|
| DxPopup | dx-popup | ElPopupBox.vue | ElDialog | visible→modelValue |
| DxToast | dx-toast | ElToastBox.vue | ElMessage | 命令式 API |
| DxLoadPanel | dx-load-panel | ElLoadPanelBox.vue | ElLoading | 命令式 API |

### 2.5 图表类（12 个）→ ECharts
> 引入 `echarts` + `vue-echarts` 依赖。封装统一 `ElChart.vue` 基础组件，各图表类型通过 `option` 映射。

| DevExtreme | type | 封装组件 | ECharts 对应 |
|------------|------|---------|-------------|
| DxChart | dx-chart | ElChart.vue | echarts bar/line/scatter |
| DxPieChart | dx-pie-chart | ElPieChart.vue | echarts pie |
| DxPolarChart | dx-polar-chart | ElPolarChart.vue | echarts polar/radar |
| DxBarGauge | dx-bar-gauge | ElBarGauge.vue | echarts gauge |
| DxBullet | dx-bullet | ElBullet.vue | echarts 自定义 series |
| DxCircularGauge | dx-circular-gauge | ElCircularGauge.vue | echarts gauge |
| DxLinearGauge | dx-linear-gauge | ElLinearGauge.vue | echarts gauge |
| DxFunnel | dx-funnel | ElFunnel.vue | echarts funnel |
| DxSankey | dx-sankey | ElSankey.vue | echarts sankey |
| DxSparkline | dx-sparkline | ElSparkline.vue | echarts 迷你 series |
| DxTreeMap | dx-tree-map | ElTreeMap.vue | echarts treemap |
| DxRangeSelector | dx-range-selector | ElRangeSelector.vue | echarts dataZoom |

**关键**：DevExtreme 图表配置（series/types/valueAxis）需转换为 ECharts option。封装层内做 `dxOptionToEchartsOption` 转换函数，对存量 schema 透明。

### 2.6 数据类（8 个，除 DataGrid/TreeList）
| DevExtreme | type | 封装组件 | 方案 | 备注 |
|------------|------|---------|------|------|
| DxList | dx-list | ElListBox.vue | ElScroll + 自定义项 | 或保留 DevExtreme |
| DxTreeView | dx-tree-view | ElTreeViewBox.vue | ElTree | |
| DxPivotGrid | dx-pivot-grid | ElPivotGridBox.vue | 自封装（ElTable + 透视聚合） | 成本最高 |
| DxPivotGridFieldChooser | dx-pivot-field-chooser | ElPivotFieldChooser.vue | 自封装 | |
| DxFilterBuilder | dx-filter-builder | ElFilterBuilder.vue | 自封装（ElForm + 条件组） | |
| DxScheduler | dx-scheduler | ElSchedulerBox.vue | @fullcalendar/vue3 | 见 §3 |
| DxGantt | dx-gantt | ElGanttBox.vue | dhtmlx-gantt 社区版 | 见 §3 |
| DxFileManager | dx-file-manager | ElFileManagerBox.vue | 自封装（ElTable+ElTree+ElUpload） | 见 §3 |

---

## 3. 复杂控件第三方替换方案

### 3.1 DxScheduler → FullCalendar
- 依赖：`@fullcalendar/vue3`、`@fullcalendar/core`、`@fullcalendar/daygrid`、`@fullcalendar/timegrid`、`@fullcalendar/interaction`
- 功能对齐：月/周/日视图、事件拖拽、调整时长、点击新增
- 封装：`ElSchedulerBox.vue` 内部把 DevExtreme 的 dataSource（含 startDate/endDate/text 字段）映射为 FullCalendar events
- 许可：标准版 MIT 免费；高级视图（timeline/资源视图）需 Premium，当前不需要

### 3.2 DxGantt → dhtmlx-gantt 社区版
- 依赖：`dhtmlx-gantt`（GitHub GPLv2 社区版免费，npm `dhtmlx-gantt`）
- 功能对齐：任务树、甘特条、依赖连线、拖拽调整、缩放
- 封装：`ElGanttBox.vue` 内部把 DevExtreme tasks dataSource 映射为 dhtmlx gantt data
- 注意：GPLv2 许可，若商用需购买商业版。**需用户确认许可可接受**

### 3.3 DxFileManager → 自封装
- 无成熟开源 Vue3 文件管理器
- 方案：基于 `ElTree`（目录树）+ `ElTable`（文件列表）+ `ElUpload`（上传）+ `ElBreadcrumb`（路径导航）自封装
- 功能：目录浏览、上传、下载、重命名、删除、新建文件夹
- 成本：中高，但可控

### 3.4 DxPivotGrid → 自封装
- 无成熟开源 Vue3 透视表
- 方案：基于 `ElTable` + 前端聚合逻辑（groupBy + sum/count/avg）自封装
- 功能：行/列维度、聚合值、拖拽维度切换、展开折叠
- 成本：**最高**，建议本期降级为"基本透视"（固定行列维度配置），高级交互后续迭代

### 3.5 DxHtmlEditor → wangeditor
- 依赖：`@wangeditor/editor` + `@wangeditor/editor-for-vue`
- 功能对齐：富文本编辑、图片插入、表格、源码模式
- 许可：MIT 免费

---

## 4. 封装组件接口设计

### 4.1 统一约定
所有封装组件遵循以下接口约定，确保 RendererNode 的 `boundProps` 透传可工作：

```ts
// 封装组件 props 约定
interface MaterialComponentProps {
  modelValue?: any            // v-model 值（表单控件）
  readOnly?: boolean          // 只读（替代 DevExtreme disabled+readOnly）
  label?: string              // 标签（由 RendererNode 统一渲染，控件内不重复渲染）
  // ...各控件特有 props 透传
}
```

### 4.2 关键适配点

**值绑定**：DevExtreme 用 `value` prop + `onValueChanged` 事件；Element Plus 用 `modelValue` + `update:modelValue`。封装组件内做桥接：
```vue
<!-- ElTextBox.vue 示例 -->
<template>
  <el-input
    :model-value="modelValue"
    :readonly="readOnly"
    :placeholder="placeholder"
    v-bind="$attrs"
    @update:model-value="v => $emit('update:modelValue', v)"
  />
</template>
<script setup>
defineProps({ modelValue: [String, Number], readOnly: Boolean, placeholder: String })
defineEmits(['update:modelValue', 'valueChanged'])  // valueChanged 兼容旧事件
</script>
```

**dataSource 适配**：DevExtreme 的 DataSource/CustomStore 异步数据源 → Element Plus 的普通数组 + 远程搜索。封装层内统一转换：
```ts
// utils/dx-to-el.ts
export async function resolveDataSource(ds: any): Promise<any[]> {
  if (Array.isArray(ds)) return ds
  if (ds?.store?.load) return ds.store.load({})  // DevExtreme CustomStore
  if (typeof ds === 'function') return ds()
  return []
}
```

### 4.3 事件映射
| DevExtreme 事件 | Element Plus 事件 | 封装层处理 |
|----------------|------------------|-----------|
| onValueChanged | update:modelValue | 桥接 + 保留 valueChanged 兼容 |
| onSelectionChanged | change / current-change | 按控件适配 |
| onContentReady | mounted / updated | 封装层 emit contentReady |
| onOptionChanged | - | 一般无需透传 |

---

## 5. 校验适配层设计

### 5.1 方案
在封装组件内部把 DevExtreme 校验规则转换为 async-validator 规则（Element Plus el-form 校验引擎即 async-validator）。

### 5.2 规则映射
| DevExtreme ValidationRule | async-validator |
|---------------------------|----------------|
| required: true | required: true |
| min/max（数字） | min/max（type: number） |
| minLength/maxLength（字符串） | min/max（type: string） |
| pattern（正则） | pattern |
| customValidation | validator: 自定义函数 |
| compare | validator: 比较函数 |

### 5.3 实现位置
- `materials/dx-validation.ts` 新增 `dxRulesToAsyncValidator(rules)` 转换函数
- RendererNode 中 DxValidator 注入逻辑改为：把 `dxValidationRules` 转换后挂到封装组件的 `rules` prop（由 el-form-item 消费）
- **保留 DxValidator**：转换函数内部仍可复用 DevExtreme 的规则类型定义（仅类型，不依赖运行时），避免重写规则元数据

### 5.4 表单级校验
DevExtreme 用 DxValidationGroup 包裹；Element Plus 用 ElForm + ElFormItem。RendererNode 在表单容器分支注入 ElForm，各字段校验规则挂到 ElFormItem。

---

## 6. 各模块改造细节

### 6.1 物料注册表 `materials/dx-materials.ts`
**核心改动**：`dxComponents` 映射表（第 74-134 行）将 type 键名去 `dx-` 前缀，指向 Element Plus 封装组件：
```ts
// 改造前
import DxTextBox from 'devextreme-vue/text-box'
const dxComponents = { 'dx-text-box': DxTextBox, ... }

// 改造后
import ElTextBox from './components/ElTextBox.vue'
const dxComponents = { 'text-box': ElTextBox, ... }
```
**保留**：`data-grid` → DxDataGrid、`tree-list` → DxTreeList（去前缀但实现仍是 DevExtreme）。
**同步改键名处**：`dx-prop-meta.ts`、`dx-events.ts`、`dx-validation.ts`、RendererNode 的 `FORM_CONTROL_TYPES`/`VALIDATABLE_TYPES`/`OVERLAY_TYPES`、CanvasNode.vue 的 `FORM_CONTROL_TYPES`、`materials/index.ts` 的 `materialComponents`。

### 6.2 物料元数据 `dx-events.ts` / `dx-prop-meta.ts`
- 事件名映射：`onValueChanged` → `update:modelValue`（在事件配置元数据中同步）
- props 元数据：DevExtreme 特有 props（如 `dataSource`、`displayExpr`、`valueExpr`）在封装组件中做兼容接收，属性面板配置项可保留不变

### 6.3 按钮三色配置
根据既有反馈，DevExtreme 按钮需独立配置字体色/背景色/悬停色。Element Plus ElButton 通过 CSS 变量实现：
- 封装 `ElButtonBox.vue` 接收 `fontColor`/`bgColor`/`hoverColor` props
- 内部用 `:style` + CSS 变量（`--el-button-text-color`/`--el-button-bg-color`/`--el-button-hover-bg-color`）注入
- 按钮类型默认三色预设保留

### 6.4 设计器属性面板 `PropertyPanel.vue`（19 处 Dx）
| Dx 属性编辑器 | 替换为 | 
|--------------|--------|
| DxTextBox | ElInput |
| DxNumberBox | ElInputNumber |
| DxSwitch | ElSwitch |
| DxSelectBox | ElSelect + ElOption |
| DxColorBox | ElColorPicker |
| DxButton | ElButton |

属性面板的编辑器用于编辑 schema 节点的 props 值，替换为 Element Plus 后值绑定方式从 `:value` + `@onValueChanged` 改为 `v-model`。

### 6.5 管理页面 views/（19 个文件）
逐文件替换模板中的 Dx 控件标签为 Element Plus 封装组件或直接用 Element Plus 原生组件。**保留所有 DxDataGrid/DxColumn/DxMasterDetail**。

高频替换模式：
```vue
<!-- 改造前 -->
<DxTextBox :value="form.name" @onValueChanged="e => form.name = e.value" />
<DxButton text="保存" @onClick="save" />

<!-- 改造后 -->
<el-input v-model="form.name" />
<el-button @click="save">保存</el-button>
```

### 6.6 代码生成器 `DesignerView.vue`（第 817-829 行）
```ts
// 改造前
if (name.startsWith('dx-')) {
  const dxPkg = type.replace(/^dx-/, 'devextreme-vue/')
  imports.push(`import ${name} from '${dxPkg}'`)
}

// 改造后
if (name.startsWith('dx-') && !['dx-data-grid','dx-tree-list'].includes(name)) {
  imports.push(`import ${name} from '@/materials/components/El${Pascal}.vue'`)
} else if (name === 'dx-data-grid') {
  imports.push(`import DxDataGrid from 'devextreme-vue/data-grid'`)
}
```

### 6.7 多语言 `store/lang.ts`
- 移除 `devextreme/localization` 动态加载
- 改用 Element Plus `ElLocale`（项目已 import）
- 图表/复杂控件的第三方库多语言各自配置（ECharts 默认中文，FullCalendar 有 locale 包）

### 6.8 样式 `main.ts`
- `import 'devextreme/dist/css/dx.light.css'` **保留**（DxDataGrid/DxTreeList 仍需）
- 新增 `import 'echarts/dist/echarts.min.css'`（如需要）
- 新增第三方库样式（fullcalendar/dhtmlx-gantt/wangeditor）

### 6.9 新增依赖
```json
{
  "echarts": "^5.5.0",
  "vue-echarts": "^7.0.0",
  "@fullcalendar/vue3": "^6.1.0",
  "@fullcalendar/core": "^6.1.0",
  "@fullcalendar/daygrid": "^6.1.0",
  "@fullcalendar/timegrid": "^6.1.0",
  "@fullcalendar/interaction": "^6.1.0",
  "dhtmlx-gantt": "^8.0.0",
  "@wangeditor/editor": "^5.1.0",
  "@wangeditor/editor-for-vue": "^5.1.0"
}
```
**不引入** `@element-plus/icons-vue`（按既有约定用文本符号）。

---

## 7. 分阶段任务清单

### 阶段 1：基础设施（低风险，不影响现有功能）
1. 安装新依赖（echarts/vue-echarts/fullcalendar/dhtmlx-gantt/wangeditor）
2. 新建 `utils/dx-to-el.ts`（dataSource 转换、事件桥接工具）
3. 新建 `materials/dx-validation.ts` 的 `dxRulesToAsyncValidator` 转换函数
4. 新建 19 个表单编辑类封装组件 `materials/components/El*.vue`
5. 新建 6 个操作类封装组件
6. 新建 2 个导航类封装组件（ElToolbarBox 含 DxItem 适配）
7. 新建 3 个弹层类封装组件
8. 单元自测各封装组件 props/事件/值绑定

### 阶段 2：物料注册表切换（核心切换点）
9. 修改 `dx-materials.ts` 的 `dxComponents` 映射，指向新封装组件（保留 dx-data-grid/dx-tree-list）
10. 修改 `dx-events.ts` 事件名映射
11. 构建验证 + 设计器预览验证表单控件渲染

### 阶段 3：图表类封装
12. 新建 `ElChart.vue` 基础组件（vue-echarts 封装）
13. 新建 12 个图表封装组件 + `dxOptionToEchartsOption` 转换函数
14. 物料注册表切换图表类

### 阶段 4：复杂控件封装
15. ElSchedulerBox（FullCalendar 封装）
16. ElGanttBox（dhtmlx-gantt 封装）
17. ElFileManagerBox（自封装）
18. ElPivotGridBox + ElPivotFieldChooser（自封装，本期降级基本透视）
19. ElFilterBuilder（自封装）
20. ElListBox、ElTreeViewBox（ElTree 适配）
21. ElHtmlEditor（wangeditor 封装）

### 阶段 5：设计器属性面板
22. 替换 `PropertyPanel.vue` 19 处 Dx 属性编辑器为 Element Plus
23. 替换 `CanvasNode.vue` 的 DxToolbar
24. 替换 designer 下 5 个弹窗组件的非表格 Dx 控件

### 阶段 6：管理页面 views/
25-43. 逐个替换 19 个 View 文件中的 Dx 控件（保留 DxDataGrid/DxColumn）

### 阶段 7：代码生成器 + 多语言 + 样式
44. 修改 `DesignerView.vue` 代码生成逻辑
45. 修改 `store/lang.ts` 多语言方案
46. 修改 `main.ts` 样式引入

### 阶段 8：回归验证
47. 设计器：拖入每个控件类型，属性配置正常
48. 预览：每个控件渲染正常，值绑定正常
49. 运行时：表单提交、校验、表格内表单控件正常
50. 代码导出：导出的 Vue 代码可独立运行
51. 多语言：中英文切换正常

---

## 8. 风险与回滚

### 8.1 风险
1. **API 差异致功能丢失**：DevExtreme 某些特有功能（如 DxSelectBox 的分组、DxDateBox 的自定义格式）Element Plus 可能不支持 → 封装层做兼容，必要时保留 DevExtreme 该控件
2. **图表 schema 转换不全**：DevExtreme 图表配置复杂，转换函数可能遗漏 → 先支持常用配置（bar/line/pie 基础），复杂配置后续补
3. **PivotGrid 降级**：本期透视表只做基本功能，高级交互缺失 → 明确告知用户
4. **dhtmlx-gantt GPLv2**：商用需购买许可 → 需用户确认
5. **设计时属性面板回归**：PropertyPanel 替换影响所有控件配置体验 → 阶段 5 重点测试

### 8.2 回滚策略
- 物料注册表 `dxComponents` 是唯一切换点，回滚只需改回指向 DevExtreme 组件
- 封装组件文件保留，不删除原 DevExtreme import（注释保留），便于回滚
- 分阶段提交，每阶段独立可回滚

### 8.3 保留 DevExtreme 依赖
因保留 DxDataGrid/DxTreeList/DxBaseData，`devextreme` + `devextreme-vue` 依赖**不移除**，仅减少使用范围。`devexpress-reporting`（报表）按报表模块是否保留决定。

---

## 9. 待用户确认事项

1. **dhtmlx-gantt GPLv2 许可**是否可接受？若商用需购买商业版，或改用其他甘特库
2. **DxPivotGrid 本期降级**为基本透视（固定行列维度配置，无拖拽切换）是否可接受？
3. **DxHtmlEditor** 用 wangeditor（MIT）是否可接受？或偏好 quill/其他
4. 是否保留 `devexpress-reporting` 报表模块（ReportViewer.vue）？报表不在本次替换范围
5. 阶段推进顺序是否认可？是否优先某个阶段