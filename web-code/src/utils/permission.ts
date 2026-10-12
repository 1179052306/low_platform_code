/**
 * 权限检查工具（G3 权限驱动渲染 + API 治理预校验）
 *
 * 职责：
 * - 字段权限：渲染前判断字段是否 hidden/readonly/editable
 * - 按钮权限：渲染前判断按钮是否可见
 * - 操作权限：统一网关调用前预校验 bizCode×action（前端提前拦截，后端兜底）
 * - 数据范围：获取 tableName 的数据过滤表达式，注入查询条件
 *
 * 原则：permissions 未加载或未配置对应 bizCode 时统一放行（后端是最终校验边界）。
 */
import { permissions } from '@/store/auth'
import type { FieldPermType, FilterRule } from '@/types/schema'

/**
 * 字段权限检查：返回字段在指定业务下的权限类型。
 * 未配置权限时返回 null（调用方按 editable 处理，即放行）。
 */
export function hasFieldPermission(
  bizCode: string,
  fieldName: string,
): FieldPermType | null {
  const perm = permissions.value
  if (!perm?.fieldPermissions) return null
  const bizPerm = perm.fieldPermissions[bizCode]
  if (!bizPerm) return null
  return bizPerm[fieldName] ?? null
}

/** 字段是否隐藏（便捷方法） */
export function isFieldHidden(bizCode: string, fieldName: string): boolean {
  return hasFieldPermission(bizCode, fieldName) === 'hidden'
}

/** 字段是否只读（便捷方法） */
export function isFieldReadonly(bizCode: string, fieldName: string): boolean {
  return hasFieldPermission(bizCode, fieldName) === 'readonly'
}

/**
 * 按钮权限检查：返回按钮在指定业务下是否可见。
 * 未配置权限时返回 true（放行，后端兜底）。
 */
export function hasButtonPermission(
  bizCode: string,
  buttonCode: string,
): boolean {
  const perm = permissions.value
  if (!perm?.buttonPermissions) return true
  const bizPerm = perm.buttonPermissions[bizCode]
  if (!bizPerm) return true
  return bizPerm[buttonCode] !== false
}

/**
 * 操作权限预校验：统一网关调用前判断 bizCode×action 是否允许。
 * 未配置权限时返回 true（放行，后端兜底）。
 */
export function hasActionPermission(
  bizCode: string,
  action: string,
): boolean {
  const perm = permissions.value
  if (!perm?.actionPermissions) return true
  const bizPerm = perm.actionPermissions[bizCode]
  if (!bizPerm) return true
  return bizPerm[action] !== false
}

/**
 * 获取数据范围过滤表达式（tableName → 数据过滤）。
 * 无数据范围配置时返回 null。
 */
export function getDataScope(tableName: string): string | null {
  const perm = permissions.value
  if (!perm?.dataScopes) return null
  return perm.dataScopes[tableName] ?? null
}

/**
 * 将数据范围表达式转为 FilterRule 数组（注入查询条件）。
 * 后端表达式格式待定，当前透传单条 filter（value=表达式），后端解析。
 */
export function buildDataScopeFilters(tableName: string): FilterRule[] {
  const scope = getDataScope(tableName)
  if (!scope) return []
  return [{ field: '__dataScope__', operator: 'raw', value: scope }]
}

/**
 * 数据冻结检查（FE-S10 用友借鉴数据冻结）。
 * 后端通过权限元数据下发 frozenStates（bizCode → 冻结状态列表）。
 * 当前数据状态命中冻结状态时，前端将字段设为只读 + 隐藏冻结操作按钮。
 */
export function isFrozen(bizCode: string, currentState: string): boolean {
  const perm = permissions.value
  if (!perm?.frozenStates) return false
  const frozen = perm.frozenStates[bizCode]
  if (!frozen || !frozen.length) return false
  return frozen.includes(currentState)
}

/**
 * 检查指定操作在冻结状态下是否被禁用。
 * 后端通过 frozenActions（bizCode → 禁用动作列表）下发。
 */
export function isActionFrozen(bizCode: string, action: string): boolean {
  const perm = permissions.value
  if (!perm?.frozenActions) return false
  const frozenActions = perm.frozenActions[bizCode]
  if (!frozenActions || !frozenActions.length) return false
  return frozenActions.includes(action)
}