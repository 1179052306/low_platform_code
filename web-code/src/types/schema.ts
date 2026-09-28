/** 低代码平台协议类型定义 */

/** 属性设置器类型 */
export type SetterType = 'input' | 'textarea' | 'number' | 'switch' | 'select' | 'apiSelect' | 'color' | 'json' | 'columns' | 'toolbarItems' | 'summary' | 'pageOpen'

/** 属性设置器配置：描述右侧属性面板如何编辑某个 prop */
export interface PropSetterConfig {
  prop: string
  label: string
  type: SetterType
  options?: { label: string; value: string | number }[]
  /** 条件显示：当指定 prop 的值等于指定值时才显示此属性 */
  visibleIf?: { prop: string; equals: string | boolean }
  /** 条件隐藏：当指定 prop 有值（notEmpty=true）或为空（notEmpty=false）时隐藏 */
  hiddenIf?: { prop: string; notEmpty?: boolean }
}

/** 物料分类（对齐 DevExtreme 官方文档分类） */
export type MaterialCategory =
  | '布局'
  | '表单'
  | '操作'
  | '基础'
  | '数据'
  | '图表'
  | '导航'
  | '弹层'

/** 物料（组件）元数据配置 */
export interface MaterialConfig {
  /** 唯一类型标识 */
  type: string
  /** 中文名称 */
  title: string
  /** 官方英文名（DevExtreme 官方命名，如 DataGrid / Chart） */
  enName?: string
  /** 分类 */
  category: MaterialCategory
  /** 图标字符 */
  icon: string
  /** 是否容器组件（可接收子节点拖入） */
  isContainer?: boolean
  /** 拖入画布时的默认属性 */
  defaultProps: Record<string, unknown>
  /** 属性面板设置器列表 */
  propSetters: PropSetterConfig[]
  /** 拖入画布时自动创建的默认子节点（用于 Row/Splitter/Container 等布局控件，支持嵌套） */
  defaultChildren?: DefaultChildNode[]
}

/** 默认子节点定义（支持嵌套 children） */
export interface DefaultChildNode {
  type: string
  props: Record<string, unknown>
  children?: DefaultChildNode[]
  /** 布局区域标识（page-layout 专用：header / aside / main / footer） */
  sectionKey?: string
}

/** 控件级 CSS 样式配置（支持 px 与百分比） */
export interface NodeCss {
  /** 宽度，如 "100%" / "320px" */
  width?: string
  /** 高度 */
  height?: string
  /** 内边距 padding，支持 CSS 简写（如 "10px" / "10px 20px"） */
  padding?: string
  /** 外边距 margin，支持 CSS 简写 */
  margin?: string
  /** 背景色 */
  background?: string
  /** 圆角 */
  borderRadius?: string
  /** 边框，如 "1px solid #d9d9d9" */
  border?: string
  /** 溢出处理 overflow */
  overflow?: string
  /** 透明度 opacity (0-1) */
  opacity?: string
  /** 显示/隐藏，如 "none" */
  display?: string
}

/** 组件节点 Schema */
export interface ComponentSchema {
  id: string
  type: string
  props: Record<string, unknown>
  children: ComponentSchema[]
  /** 事件绑定：事件名 → JavaScript 函数体（如 "alert('clicked')"） */
  events?: Record<string, string>
  /** 控件级 CSS 样式（外层包装上应用，不影响组件内部尺寸 props） */
  css?: NodeCss
  /** 字段名称：全局唯一，用于后续保存和表单/数据绑定 */
  fieldName?: string
  /** 数据库字段名：表单控件绑定到数据库表字段的名称，同一表单内唯一 */
  dbField?: string
  /** 关联基础资料页面ID（dx-base-data 控件专用，指向 pageType='basedata' 的页面） */
  refPageId?: string
  /** 布局区域标识（page-layout 专用：header / aside / main / footer） */
  sectionKey?: string
  /** 自由画布位置（根节点使用） */
  x?: number
  y?: number
  /** 控件实际渲染尺寸（画布占位框尺寸） */
  width?: number
  height?: number
  /** 布局容器高度是否自动（true=自适应子节点，false=使用 node.height 固定高度） */
  heightAuto?: boolean
  /** 拖入容器前的原始画布宽高，拖出时恢复 */
  _origWidth?: number
  _origHeight?: number
}

/** 页面级 CSS（目前支持背景） */
export interface PageCss {
  background?: string
}

/** 主题颜色配置 */
export interface ThemeColors {
  primary: string
  primaryHover: string
  success: string
  warning: string
  danger: string
  info: string
  pageBg: string
  cardBg: string
  textPrimary: string
  textSecondary: string
  border: string
}

/** 主题配置 */
export interface Theme {
  id: string
  name: string
  isPreset: boolean
  colors: ThemeColors
  radius: number
}

/** 页面业务类型：单据 / 基础资料 / 自定义 */
export type PageType = 'document' | 'basedata' | 'custom'

/** 页面 Schema（顶层协议） */
export interface PageSchema {
  version: string
  /** 页面唯一标识，用于保存和唯一键验证 */
  pageId: string
  pageName: string
  /** 页面对应的数据表名 */
  tableName?: string
  /** 主键字段名（用于基础资料关联和后端建表） */
  primaryKeyField?: string
  /** 页面级 CSS 配置 */
  pageCss?: PageCss
  /** 页面主题 ID */
  themeId?: string
  /** 所属模块 ID */
  moduleId?: string
  /** 所属系统 ID */
  systemId?: string
  /** 页面业务类型：document=单据，basedata=基础资料，custom=自定义 */
  pageType?: PageType
  /** 软删除时间戳，非空表示已删除 */
  deletedAt?: string
  /** 是否启用，undefined/true 为启用，false 为禁用 */
  enabled?: boolean
  root: ComponentSchema[]
  /** 业务形态下三种子模式的独立页面内容（仅 mode=biz 时使用） */
  bizSchemas?: BizSchemas
  /** 业务形态下最后使用的子模式（持久化，重新打开时恢复） */
  lastBizMode?: 'form' | 'table' | 'mobile'
}

/** 业务形态三种子模式的独立页面内容 */
export interface BizSchemas {
  form: ComponentSchema[]
  table: ComponentSchema[]
  mobile: ComponentSchema[]
}
