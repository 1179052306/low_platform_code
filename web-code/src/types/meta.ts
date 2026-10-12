/**
 * 元数据类型定义（方向二：一切皆元数据统一驱动）
 *
 * 元数据按域分类：
 * - structure：结构域（字段/表/关系）
 * - behavior：行为域（事件/规则/流程）
 * - presentation：展现域（页面/表单/报表/主题）
 * - control：控制域（权限/数据范围/按钮权限）
 */

/** 元数据域 */
export type MetaDomain = 'structure' | 'behavior' | 'presentation' | 'control'

/** 元数据项（统一元数据注册表 lc_meta_registry 的一行） */
export interface MetaItem {
  /** 元数据 ID */
  id: string
  /** 所属域 */
  domain: MetaDomain
  /** 元数据类型（如 field/event/validation/permission/report/page） */
  metaType: string
  /** 元数据键（域内唯一，如 bizCode:fieldName） */
  metaKey: string
  /** 元数据名称（展示用） */
  metaName: string
  /** 元数据内容（JSON，结构因 metaType 而异） */
  content: unknown
  /** 版本号 */
  version?: number
  /** 是否启用 */
  enabled: boolean
  /** 最后更新时间 */
  updatedAt?: string
  /** 关联页面 ID（可选，元数据可绑定到具体页面） */
  pageId?: string
  /** 关联业务编码（可选） */
  bizCode?: string
}

/** 元数据域配置（供 MetaManageView 域分类树展示） */
export interface MetaDomainConfig {
  value: MetaDomain
  label: string
  /** 域下支持的 metaType 列表 */
  types: { value: string; label: string }[]
}

/** 元数据域配置表 */
export const META_DOMAINS: MetaDomainConfig[] = [
  {
    value: 'structure',
    label: '结构域',
    types: [
      { value: 'field', label: '字段' },
      { value: 'table', label: '数据表' },
      { value: 'relation', label: '关联关系' },
    ],
  },
  {
    value: 'behavior',
    label: '行为域',
    types: [
      { value: 'event', label: '事件' },
      { value: 'rule', label: '业务规则' },
      { value: 'workflow', label: '流程' },
    ],
  },
  {
    value: 'presentation',
    label: '展现域',
    types: [
      { value: 'page', label: '页面' },
      { value: 'form', label: '表单' },
      { value: 'report', label: '报表' },
      { value: 'theme', label: '主题' },
    ],
  },
  {
    value: 'control',
    label: '控制域',
    types: [
      { value: 'permission', label: '权限' },
      { value: 'dataScope', label: '数据范围' },
      { value: 'buttonPerm', label: '按钮权限' },
    ],
  },
]