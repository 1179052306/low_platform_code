/** 低代码平台协议类型定义 */

/** 属性设置器类型 */
export type SetterType = 'input' | 'textarea' | 'number' | 'switch' | 'select' | 'apiSelect' | 'color' | 'iconSelect' | 'json' | 'columns' | 'toolbarItems' | 'summary' | 'pageOpen' | 'optionsEditor' | 'itemsSelect'

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
  /** 分层继承：所属层（方向一） */
  layerType?: MetaLayer
  /** 分层继承：客户层覆盖产品层的属性（prop → 覆盖值） */
  overrideProps?: Record<string, unknown>
  /** 分层继承：客户层新增节点标记（产品层无此节点） */
  isNew?: boolean
  /** 分层继承：客户层删除产品层节点标记（逻辑删除，合并时跳过） */
  isRemoved?: boolean
  /** 计算表达式（SpEL/JS，如 "unitPrice * quantity"），watch triggerFields 变化时自动计算 */
  computeExpr?: string
  /** 计算表达式触发字段列表（watch 这些字段变化时重新计算 computeExpr） */
  computeTriggerFields?: string[]
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

/** 元数据分层（方向一：分层继承，PLATFORM → INDUSTRY → CUSTOMER） */
export type MetaLayer = 'PLATFORM' | 'INDUSTRY' | 'CUSTOMER'

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
  /** 主键生成策略：UUID=字符串主键(默认), SNOWFLAKE=数字主键 */
  keyStrategy?: string
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
  /** 分层继承：本页面所属层 */
  layerType?: MetaLayer
  /** 分层继承：父层页面 ID（INDUSTRY/CUSTOMER 层指向上一层 pageId） */
  parentPageId?: string
  root: ComponentSchema[]
  /** 业务形态下三种子模式的独立页面内容（仅 mode=biz 时使用） */
  bizSchemas?: BizSchemas
  /** 业务形态下最后使用的子模式（持久化，重新打开时恢复） */
  lastBizMode?: 'form' | 'table' | 'mobile'
  /** 查询配置（列表页/查询页的可查询字段、默认过滤、排序、分页） */
  queryConfig?: QueryConfig
  /** 主从表结构配置 */
  masterDetail?: MasterDetailConfig
  /** 字段变更事件规则（页面级，后端第 13 章触发，前端只配置） */
  fieldChangeEvents?: FieldBChangeEventRule[]
  /** 附件配置（FE-S10 P2 附件管理） */
  attachmentConfig?: AttachmentConfig
  /** 字段联动规则（FE-S7 场景B） */
  fieldLinkageRules?: FieldLinkageRule[]
  /** 绑定的审批流 ID（单据页面走审批流时使用） */
  workflowId?: string
  /** 页面级计算规则（FE-S10：表达式从字段级提升为界面级规则） */
  computeRules?: ComputeRule[]
}

/** 计算规则目标字段（一个目标字段 + 其计算表达式） */
export interface ComputeRuleTarget {
  /** 目标字段名（表达式计算结果回填到此字段） */
  fieldName: string
  /** 计算表达式（如 "unitPrice * quantity"） */
  expr: string
}

/** 页面级计算规则（一个规则 = 多触发字段 + 多目标字段表达式） */
export interface ComputeRule {
  /** 规则 ID */
  id: string
  /** 规则名称（如"金额计算"） */
  name: string
  /** 触发字段列表（这些字段值变化时触发本规则） */
  triggerFields: string[]
  /** 目标字段及其表达式列表（同时计算多个目标字段） */
  targets: ComputeRuleTarget[]
  /** 是否启用 */
  enabled?: boolean
}

/** 附件配置（FE-S10 P2） */
export interface AttachmentConfig {
  /** 允许的文件类型（如 ['jpg','png','pdf']） */
  fileTypes?: string[]
  /** 最大文件大小（MB） */
  maxSize?: number
  /** 最大文件数量 */
  maxCount?: number
  /** 是否必填 */
  required?: boolean
  /** 附件存储业务编码 */
  bizCode?: string
}

/** 业务形态三种子模式的独立页面内容 */
export interface BizSchemas {
  form: ComponentSchema[]
  table: ComponentSchema[]
  mobile: ComponentSchema[]
}
// ================ 查询配置（G1 通用查询引擎） ================

/** 查询过滤规则 */
export interface FilterRule {
  field: string
  operator: string
  value: unknown
  value2?: unknown
}

/** 查询排序规则 */
export interface SortRule {
  field: string
  desc?: boolean
}

/** 可查询字段配置 */
export interface QueryFieldConfig {
  fieldName: string
  label: string
  operators: string[]
  required?: boolean
  defaultValue?: unknown
  /** 引用类型（下拉选，指向基础资料页面） */
  refBizCode?: string
}

/** 页面级查询配置 */
export interface QueryConfig {
  fields: QueryFieldConfig[]
  defaultFilters?: FilterRule[]
  defaultSort?: SortRule[]
  pageSize?: number
}

// ================ 主从表配置（G2） ================

/** 从表配置 */
export interface DetailTableConfig {
  tableName: string
  label: string
  /** 关联主表的外键 */
  foreignKey: string
  /** 从表显示列 */
  columns: string[]
  editable: boolean
  allowAdd?: boolean
  allowDelete?: boolean
}

/** 主从表结构配置 */
export interface MasterDetailConfig {
  masterTable: string
  details: DetailTableConfig[]
}

// ================ 权限元数据（G3 权限驱动渲染） ================

/** 字段权限类型 */
export type FieldPermType = 'hidden' | 'readonly' | 'editable'

/** 权限元数据（登录时拉取，存 authStore.permissions） */
export interface PermissionMeta {
  /** 字段权限：bizCode → fieldName → 权限类型 */
  fieldPermissions: Record<string, Record<string, FieldPermType>>
  /** 按钮权限：bizCode → buttonCode → 是否可见 */
  buttonPermissions: Record<string, Record<string, boolean>>
  /** 操作权限：bizCode → action → 是否允许 */
  actionPermissions: Record<string, Record<string, boolean>>
  /** 数据范围：tableName → 数据过滤表达式 */
  dataScopes: Record<string, string>
  /** 数据冻结：bizCode → 冻结状态列表（如 ['CLOSED','ARCHIVED']） */
  frozenStates?: Record<string, string[]>
  /** 冻结时禁用的操作：bizCode → 禁用动作列表（如 ['edit','delete']） */
  frozenActions?: Record<string, string[]>
}
// ================ 字段变更事件（后端第 13 章） ================

/** 字段变更事件触发类型 */
export type FieldEventTriggerType = 'ANY_CHANGE' | 'ALL_CHANGE' | 'EXPRESSION'

/** 字段变更事件触发阶段 */
export type FieldEventFirePhase = 'SYNC' | 'ASYNC' | 'BOTH'

/** 字段变更事件规则（页面级配置，运行时由后端触发，前端只配置） */
export interface FieldBChangeEventRule {
  /** 事件编码（唯一） */
  eventCode: string
  /** 事件名称 */
  eventName: string
  /** 触发类型：任意字段变更/全部字段变更/表达式满足 */
  triggerType: FieldEventTriggerType
  /** 监听字段列表 */
  watchFields: string[]
  /** 触发条件表达式（triggerType=EXPRESSION 时用，后端 SpEL 执行） */
  conditionExpr?: string
  /** 触发阶段：同步插件/异步消息/两者 */
  firePhase: FieldEventFirePhase
  /** 同步插件 ID（firePhase=SYNC/BOTH 时用） */
  syncPluginId?: string
  /** 异步消息 Topic（firePhase=ASYNC/BOTH 时用） */
  asyncTopic?: string
  /** 是否启用 */
  enabled: boolean
  /** 排序号 */
  sortNo: number
}
// ================ 网关权限矩阵 + 字段权限管理（FE-S9 API 治理转变） ================

/**
 * 网关权限矩阵规则（对应后端 lc_gateway_permission 表）。
 * bizCode × action → 允许/拒绝的角色列表。
 * 管理界面维护，运行时由后端网关校验 + 前端 hasActionPermission 预校验。
 */
export interface GatewayPermissionRule {
  /** 规则 ID */
  id: string
  /** 业务编码（如 'order'） */
  bizCode: string
  /** 操作动作（如 'create'/'query'/'submit'/'approve'） */
  action: string
  /** 允许的角色编码列表 */
  allowedRoles: string[]
  /** 拒绝的角色编码列表（黑名单，优先级高于 allowedRoles） */
  deniedRoles: string[]
  /** 描述 */
  description?: string
  /** 是否启用 */
  enabled: boolean
}

/**
 * 字段权限规则（对应后端 lc_field_permission 表）。
 * bizCode + fieldName + permType → 角色列表。
 * 管理界面维护，运行时由后端网关返回 + 前端驱动渲染（hidden/readonly/editable）。
 */
export interface FieldPermissionRule {
  /** 规则 ID */
  id: string
  /** 业务编码 */
  bizCode: string
  /** 字段名 */
  fieldName: string
  /** 权限类型 */
  permType: FieldPermType
  /** 适用角色编码列表（空表示对所有角色生效） */
  roles: string[]
  /** 描述 */
  description?: string
  /** 是否启用 */
  enabled: boolean
}
// ================ 元数据版本化（FE-S4 方向3） ================

/** 元数据版本记录 */
export interface MetaVersion {
  /** 版本 ID */
  versionId: string
  /** 元数据键 */
  metaKey: string
  /** 版本号 */
  versionNo: number
  /** 版本内容（JSON） */
  content: Record<string, unknown>
  /** 变更说明 */
  changeLog?: string
  /** 是否有 BREAKING 变更 */
  breaking?: boolean
  /** 创建人 */
  createdBy?: string
  /** 创建时间 */
  createdAt: string
}

/** BREAKING 变更类型 */
export type BreakingChangeType = 'FIELD_TYPE_CHANGE' | 'FIELD_DELETE' | 'FIELD_RENAME' | 'TABLE_RENAME'

/** BREAKING 变更记录 */
export interface BreakingChange {
  type: BreakingChangeType
  description: string
  /** 迁移脚本（用户输入） */
  migrationScript?: string
}

// ================ 应用包（FE-S5 方向4） ================
// 类型定义已拆至 types/package.ts，此处 re-export 保持向后兼容
export type { AppPackage } from '@/types/package'

// ================ 审批流（FE-S6） ================
// 类型定义已拆至 types/workflow.ts，此处 re-export 保持向后兼容
export type {
  WorkflowDef,
  WorkflowNode,
  WorkflowTransition,
  ApprovalActionType,
  ApprovalRecord,
  TodoItem,
} from '@/types/workflow'

// ================ 字段联动（FE-S7 场景B） ================

/** 字段联动规则 */
export interface FieldLinkageRule {
  /** 规则 ID */
  id: string
  /** 触发字段 */
  triggerField: string
  /** 联动目标字段 */
  targetField: string
  /** 联动类型 */
  type: 'value' | 'visible' | 'editable' | 'options'
  /** 条件表达式 */
  conditionExpr: string
  /** 联动值/表达式 */
  value?: string
  /** 是否启用 */
  enabled: boolean
}
// ================ 单据编号规则（多段编码，参考用友BIP） ================

/** 编码段类型 */
export type NumberRuleSegmentType =
  | 'constant'
  | 'random'
  | 'sysDate'
  | 'bizDate'
  | 'bizField'
  | 'sequence'

/** 编码段使用模式（bizField 类型用） */
export type NumberRuleUsageMode = 'full' | 'suffix' | 'prefix'

/** 流水号重置周期 */
export type NumberRuleSeqScope = 'DAILY' | 'MONTHLY' | 'YEARLY' | 'GLOBAL'

/** 编码段配置 */
export interface NumberRuleSegment {
  /** 段类型 */
  type: NumberRuleSegmentType
  /** 常量值（type=constant） */
  value?: string
  /** 随机码长度（type=random） */
  length?: number
  /** 日期显示格式（type=sysDate/bizDate，如 yyyyMMdd） */
  dateFormat?: string
  /** 业务对象字段名（type=bizDate/bizField） */
  fieldName?: string
  /** 使用模式（type=bizField） */
  usageMode?: NumberRuleUsageMode
  /** 取值位数（usageMode=suffix/prefix 时） */
  takeLength?: number
  /** 流水号位数（type=sequence） */
  seqLength?: number
  /** 流水号重置周期（type=sequence） */
  seqScope?: NumberRuleSeqScope
  /** 流水号起始值（type=sequence） */
  seqStart?: number
  /** 流水号步长（type=sequence） */
  seqStep?: number
  /** 超过设置长度时自动升位（type=sequence） */
  seqAutoExpand?: boolean
  /** 断号补偿（type=sequence） */
  seqGapFill?: boolean
  /** 用0补位（type=sequence） */
  seqZeroPad?: boolean
  /** 段间分隔符（该段与下一段之间的分隔符，仅非首段有效） */
  separator?: string
}

/** 编号规则配置（多段组合） */
export interface NumberRuleConfig {
  /** 编码段列表 */
  segments: NumberRuleSegment[]
}
