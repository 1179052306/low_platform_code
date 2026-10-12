/** 业务元数据 store — 封装后端 MetaController API */
import { httpRequest } from '@/utils/request'
import { t } from '@/store/lang'
import type { BizMeta, FieldMeta, ActionMeta, StateMachineMeta } from '@/types/biz-meta'

/** 后端返回大写字段名，转小驼峰 */
function toBizMeta(row: Record<string, unknown>): BizMeta {
  return {
    id: String(row.ID ?? ''),
    bizCode: String(row.BIZ_CODE ?? ''),
    bizName: String(row.BIZ_NAME ?? ''),
    mainTable: String(row.MAIN_TABLE ?? ''),
    mainTableAlias: String(row.MAIN_TABLE_ALIAS ?? ''),
    statusField: String(row.STATUS_FIELD ?? ''),
    logicDeleteField: String(row.LOGIC_DELETE_FIELD ?? ''),
    tenantId: String(row.TENANT_ID ?? ''),
    version: Number(row.VERSION ?? 1),
    remark: String(row.REMARK ?? ''),
  }
}

function toFieldMeta(row: Record<string, unknown>): FieldMeta {
  return {
    id: String(row.ID ?? ''),
    bizCode: String(row.BIZ_CODE ?? ''),
    fieldName: String(row.FIELD_NAME ?? ''),
    columnType: String(row.COLUMN_TYPE ?? ''),
    dataType: String(row.DATA_TYPE ?? ''),
    isRequired: Boolean(row.IS_REQUIRED),
    isUnique: Boolean(row.IS_UNIQUE),
    isSystemField: Boolean(row.IS_SYSTEM_FIELD),
    permissionExpr: row.PERMISSION_EXPR ? String(row.PERMISSION_EXPR) : '',
    defaultValueExpr: row.DEFAULT_VALUE_EXPR ? String(row.DEFAULT_VALUE_EXPR) : '',
    validationExpr: row.VALIDATION_EXPR ? String(row.VALIDATION_EXPR) : '',
    sortNo: Number(row.SORT_NO ?? 0),
  }
}

function toActionMeta(row: Record<string, unknown>): ActionMeta {
  return {
    id: String(row.ID ?? ''),
    bizCode: String(row.BIZ_CODE ?? ''),
    action: String(row.ACTION ?? ''),
    strategyType: String(row.STRATEGY_TYPE ?? ''),
    preHookPlugins: row.PRE_HOOK_PLUGINS ? String(row.PRE_HOOK_PLUGINS) : '',
    postHookPlugins: row.POST_HOOK_PLUGINS ? String(row.POST_HOOK_PLUGINS) : '',
    statusTransition: row.STATUS_TRANSITION ? String(row.STATUS_TRANSITION) : '',
    dataScopeExpr: row.DATA_SCOPE_EXPR ? String(row.DATA_SCOPE_EXPR) : '',
    enabled: Boolean(row.ENABLED),
  }
}

function toStateMachineMeta(row: Record<string, unknown>): StateMachineMeta {
  return {
    id: String(row.ID ?? ''),
    bizCode: String(row.BIZ_CODE ?? ''),
    currentState: String(row.CURRENT_STATE ?? ''),
    nextState: String(row.NEXT_STATE ?? ''),
    event: String(row.EVENT ?? ''),
    guardExpr: row.GUARD_EXPR ? String(row.GUARD_EXPR) : '',
    action: String(row.ACTION ?? ''),
    enabled: Boolean(row.ENABLED),
  }
}

/** 从 ResMsg 提取 data 数组 */
function extractArray<T>(res: { ok: boolean; data: unknown }, mapper: (row: Record<string, unknown>) => T): T[] {
  if (!res.ok) return []
  const body = res.data as { res?: boolean; data?: unknown[] }
  if (!body || body.res === false || !Array.isArray(body.data)) return []
  return body.data.map((row) => mapper(row as Record<string, unknown>))
}

/** 检查保存/删除结果 */
function checkResult(res: { ok: boolean; data: unknown }, action: string): void {
  if (!res.ok) throw new Error(t(`${action}失败：后端连接异常`))
  const body = res.data as { res?: boolean; msg?: string }
  if (body && body.res === false) throw new Error(t(body.msg || `${action}失败`))
}

// ============ BizMeta ============

export async function listBizMeta(): Promise<BizMeta[]> {
  const res = await httpRequest('/api/lowcode/meta/biz/list', { method: 'GET' })
  return extractArray(res, toBizMeta)
}

export async function saveBizMeta(item: BizMeta): Promise<void> {
  const res = await httpRequest('/api/lowcode/meta/biz', {
    method: 'POST',
    body: JSON.stringify(item),
  })
  checkResult(res, '保存业务对象')
}

export async function deleteBizMeta(bizCode: string): Promise<void> {
  const res = await httpRequest(`/api/lowcode/meta/biz/${bizCode}`, { method: 'DELETE' })
  checkResult(res, '删除业务对象')
}

// ============ FieldMeta ============

export async function listFieldMeta(bizCode: string): Promise<FieldMeta[]> {
  const res = await httpRequest(`/api/lowcode/meta/field/list?bizCode=${bizCode}`, { method: 'GET' })
  return extractArray(res, toFieldMeta)
}

export async function saveFieldMeta(item: FieldMeta): Promise<void> {
  const res = await httpRequest('/api/lowcode/meta/field', {
    method: 'POST',
    body: JSON.stringify(item),
  })
  checkResult(res, '保存字段')
}

export async function deleteFieldMeta(id: string): Promise<void> {
  const res = await httpRequest(`/api/lowcode/meta/field/${id}`, { method: 'DELETE' })
  checkResult(res, '删除字段')
}

// ============ ActionMeta ============

export async function listActionMeta(bizCode: string): Promise<ActionMeta[]> {
  const res = await httpRequest(`/api/lowcode/meta/action/list?bizCode=${bizCode}`, { method: 'GET' })
  return extractArray(res, toActionMeta)
}

export async function saveActionMeta(item: ActionMeta): Promise<void> {
  const res = await httpRequest('/api/lowcode/meta/action', {
    method: 'POST',
    body: JSON.stringify(item),
  })
  checkResult(res, '保存动作')
}

export async function deleteActionMeta(id: string): Promise<void> {
  const res = await httpRequest(`/api/lowcode/meta/action/${id}`, { method: 'DELETE' })
  checkResult(res, '删除动作')
}

// ============ StateMachineMeta ============

export async function listStateMachineMeta(bizCode: string): Promise<StateMachineMeta[]> {
  const res = await httpRequest(`/api/lowcode/meta/stateMachine/list?bizCode=${bizCode}`, { method: 'GET' })
  return extractArray(res, toStateMachineMeta)
}

export async function saveStateMachineMeta(item: StateMachineMeta): Promise<void> {
  const res = await httpRequest('/api/lowcode/meta/stateMachine', {
    method: 'POST',
    body: JSON.stringify(item),
  })
  checkResult(res, '保存状态迁移')
}

export async function deleteStateMachineMeta(id: string): Promise<void> {
  const res = await httpRequest(`/api/lowcode/meta/stateMachine/${id}`, { method: 'DELETE' })
  checkResult(res, '删除状态迁移')
}