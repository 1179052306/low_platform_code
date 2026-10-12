/** 业务元数据类型定义（对应后端 lc_biz_meta / lc_field_meta / lc_action_meta / lc_state_machine） */

/** 业务对象元数据 */
export interface BizMeta {
  id: string
  bizCode: string
  bizName: string
  mainTable: string
  mainTableAlias?: string
  statusField?: string
  logicDeleteField?: string
  tenantId?: string
  version?: number
  remark?: string
}

/** 字段元数据 */
export interface FieldMeta {
  id: string
  bizCode: string
  fieldName: string
  columnType: string
  dataType: string
  isRequired: boolean
  isUnique: boolean
  isSystemField: boolean
  permissionExpr?: string
  defaultValueExpr?: string
  validationExpr?: string
  sortNo?: number
}

/** 动作元数据 */
export interface ActionMeta {
  id: string
  bizCode: string
  action: string
  strategyType: string
  preHookPlugins?: string
  postHookPlugins?: string
  statusTransition?: string
  dataScopeExpr?: string
  enabled: boolean
}

/** 状态机元数据 */
export interface StateMachineMeta {
  id: string
  bizCode: string
  currentState: string
  nextState: string
  event: string
  guardExpr?: string
  action: string
  enabled: boolean
}

/** 字段列类型选项 */
export const COLUMN_TYPES = [
  { value: 'PK', label: '主键' },
  { value: 'BUSINESS_NO', label: '业务编号' },
  { value: 'FK', label: '外键' },
  { value: 'AMOUNT', label: '金额' },
  { value: 'STATUS', label: '状态' },
  { value: 'LOGIC_DELETE', label: '逻辑删除' },
  { value: 'TIMESTAMP', label: '时间戳' },
  { value: 'TEXT', label: '文本' },
  { value: 'NUMBER', label: '数值' },
  { value: 'BOOLEAN', label: '布尔' },
]

/** 数据类型选项 */
export const DATA_TYPES = [
  { value: 'VARCHAR', label: 'VARCHAR' },
  { value: 'DECIMAL', label: 'DECIMAL' },
  { value: 'BOOLEAN', label: 'BOOLEAN' },
  { value: 'INTEGER', label: 'INTEGER' },
  { value: 'TEXT', label: 'TEXT' },
  { value: 'DATE', label: 'DATE' },
  { value: 'TIMESTAMP', label: 'TIMESTAMP' },
]

/** 动作策略类型选项 */
export const STRATEGY_TYPES = [
  { value: 'SINGLE', label: '单条' },
  { value: 'BATCH', label: '批量' },
  { value: 'BULK', label: '大批量' },
]