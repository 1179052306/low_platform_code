/**
 * 容器类型常量 — 全局唯一定义，所有组件共享
 *
 * 新增布局容器类型时只需在此文件中修改，无需在各组件中逐一更新。
 */

/** 布局容器类型：el-row / el-splitter / el-tabs
 *  - 需要用 ✥ 手柄拖动（子节点会拦截直接拖拽）
 *  - 拖入画布时默认 heightAuto=true，宽度默认 300px
 */
export const LAYOUT_CONTAINER_TYPES = new Set([
  'el-row',
  'el-splitter',
  'el-tabs',
])

/** 布局结构子节点类型：el-col / el-splitter-panel / el-tab-pane
 *  - 拖拽时重定向到父布局容器
 *  - 不能移动到画布根级
 *  - 不在物料面板中展示
 */
export const STRUCTURAL_CHILD_TYPES = new Set([
  'el-col',
  'el-splitter-panel',
  'el-tab-pane',
])

/** 支持子节点自由定位的容器类型：el-splitter-panel / el-tab-pane
 *  - 子节点使用 absolute 定位，容器使用 position:relative
 *  - 拖放事件冒泡到 CanvasPanel 统一处理（不在 CanvasNode 中拦截）
 *  - 拖入时保持原有宽高，不设置 CSS 100%
 */
export const FREE_POSITION_CONTAINERS = new Set([
  'el-splitter-panel',
  'el-tab-pane',
])

/** 栅格 section 类型：el-col / el-splitter-panel
 *  - 宽高由 flex/span/sectionKey 控制，不应用 CSS width/height
 *  - 默认 padding/margin 为 0
 *  - 在 wrapperStyle 中仅提取非尺寸类 CSS
 */
export const GRID_SECTION_TYPES = new Set([
  'el-col',
  'el-splitter-panel',
])

/** 子容器类型（用于选中视觉区分）：el-col / el-splitter-panel / el-tab-pane
 *  - 选中时显示绿色实线边框
 */
export const SUB_CONTAINER_TYPES = new Set([
  'el-col',
  'el-splitter-panel',
  'el-tab-pane',
])

/** 受保护的布局容器类型：这些容器的直接子节点不能被拖拽分离或删除 */
export const PROTECTED_LAYOUT_TYPES = LAYOUT_CONTAINER_TYPES

/** 布局结构子节点类型（别名，用于语义区分）：不能移动到画布根级 */
export const LAYOUT_SECTION_TYPES = STRUCTURAL_CHILD_TYPES

/** 不允许嵌套自身的布局容器类型
 *  - 栅格（el-row）仍禁止自嵌套（栅格的行列结构嵌套自身会导致布局混乱）
 *  - 分割面板（el-splitter）/ 标签页（el-tabs）已支持容器内自由布局（类画布），允许自嵌套
 */
export const NO_SELF_NESTING_TYPES = new Set(['el-row'])

/** 在物料面板中隐藏的类型（布局容器的子节点专用类型） */
export const HIDDEN_MATERIAL_TYPES = STRUCTURAL_CHILD_TYPES
