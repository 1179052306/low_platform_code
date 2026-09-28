# Vue3 低代码平台

基于 Vue 3 + TypeScript + Vite + DevExtreme 的低代码页面设计器。

## 功能特性

- **组件库面板**：左侧按「布局 / 基础 / 表单 / 数据 / 图表 / 导航 / 弹层」分类展示物料，拖拽上画布
  - 8 个内置轻量物料（容器/文本/链接/图片/按钮/输入框/开关/表格）
  - **64 个 DevExtreme 控件**：覆盖图表全家桶（Chart/PieChart/PolarChart/各类 Gauge/Funnel/Sankey/TreeMap/Sparkline/RangeSelector）、数据组件（DataGrid/TreeList/PivotGrid/Scheduler/Gantt/FileManager/Form 等）、表单控件（21 个编辑器）、导航组件（Tabs/Menu/Toolbar/Splitter/Gallery/Chat 等）、弹层组件（Popup/Toast/LoadPanel）
- **画布设计**：拖拽排序、拖入容器、点击选中、复制 / 删除节点（含防拖入自身后代的保护）
- **属性配置**：右侧属性面板按物料元数据动态渲染，支持输入、数字、开关、下拉、颜色、**JSON**（用于 columns/series/items 等复杂配置）6 类设置器
- **撤销 / 重做**：50 步历史快照，支持 Ctrl+Z / Ctrl+Shift+Z 快捷键
- **Schema 导入导出**：页面以 JSON Schema 形式导出下载 / 导入还原
- **实时预览**：独立预览路由，用无交互的渲染器回放当前画布

> 未接入的 DevExtreme 模块及原因：context-menu / popover / tooltip（需绑定目标元素）、draggable / sortable / resizable / scroll-view / drawer（行为包装类，无独立可视外观）、validation-group / validator / validation-summary / filter-builder 依赖项、vector-map（地图数据需单独安装 devextreme-dist）。

## 快速开始

```bash
npm install
npm run dev      # 启动开发服务器（默认 http://localhost:5173）
npm run build    # 类型检查 + 生产构建
```

## 目录结构

```
src/
├── types/schema.ts              # 低代码协议类型（PageSchema / ComponentSchema / 物料配置）
├── materials/                   # 物料体系
│   ├── index.ts                 # 物料注册表：type -> 组件实现 + 元数据（属性设置器）
│   └── components/              # 物料组件实现（LcContainer / LcText / LcButton ...）
├── store/designer.ts            # 设计器核心状态：节点树 CRUD、选中、历史快照、导入导出
├── components/designer/         # 设计器三栏（MaterialPanel / CanvasPanel / PropertyPanel）
├── components/renderer/         # 预览用纯渲染器（RendererNode）
├── views/                       # 设计器页 / 预览页
└── router/index.ts              # hash 路由
```

## 扩展新物料

1. 在 `src/materials/components/` 新建 `LcXxx.vue` 组件
2. 在 `src/materials/index.ts` 中注册实现（`materialComponents`）并添加元数据（`materialConfigs`），配置 `defaultProps` 与 `propSetters`

面板与画布会自动识别，无需改动其他代码。
