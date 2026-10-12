/**
 * 权限检查逻辑 composable（G3 权限驱动渲染）。
 * 封装 utils/permission.ts 的函数，供 RendererNode / PropertyPanel / 页面组件统一调用。
 * utils/permission.ts 内部已绑定全局 permissions ref，此处仅做组合封装。
 */
import { computed } from 'vue'
import { permissions } from '@/store/auth'
import {
  hasFieldPermission,
  isFieldHidden,
  isFieldReadonly,
  hasButtonPermission,
  hasActionPermission,
  getDataScope,
  buildDataScopeFilters,
  isFrozen,
  isActionFrozen,
} from '@/utils/permission'
import type { FieldPermType } from '@/types/schema'

export function usePermission() {
  /** 当前权限元数据（响应式） */
  const permissionsRef = computed(() => permissions.value)

  /** 字段是否可见（非 hidden） */
  function fieldVisible(bizCode: string, fieldName: string): boolean {
    return !isFieldHidden(bizCode, fieldName)
  }

  /** 字段是否只读 */
  function fieldReadonly(bizCode: string, fieldName: string): boolean {
    return isFieldReadonly(bizCode, fieldName)
  }

  /** 字段权限类型 */
  function fieldPerm(
    bizCode: string,
    fieldName: string,
  ): FieldPermType | null {
    return hasFieldPermission(bizCode, fieldName)
  }

  /** 按钮是否可见 */
  function buttonVisible(bizCode: string, buttonCode: string): boolean {
    return hasButtonPermission(bizCode, buttonCode)
  }

  /** 操作是否允许 */
  function actionAllowed(bizCode: string, action: string): boolean {
    return hasActionPermission(bizCode, action)
  }

  /** 获取数据范围过滤表达式 */
  function dataScope(tableName: string): string | null {
    return getDataScope(tableName)
  }

  /** 构建数据范围过滤条件（附加到查询参数） */
  function dataScopeFilters(tableName: string) {
    return buildDataScopeFilters(tableName)
  }

  /** 业务数据是否处于冻结状态 */
  function frozen(bizCode: string, currentState: string): boolean {
    return isFrozen(bizCode, currentState)
  }

  /** 冻结状态下操作是否被禁用 */
  function actionFrozen(bizCode: string, action: string): boolean {
    return isActionFrozen(bizCode, action)
  }

  return {
    permissions: permissionsRef,
    fieldVisible,
    fieldReadonly,
    fieldPerm,
    buttonVisible,
    actionAllowed,
    dataScope,
    dataScopeFilters,
    frozen,
    actionFrozen,
  }
}