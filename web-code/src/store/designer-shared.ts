/**
 * 从 designer.ts 抽出的纯工具/常量，不依赖 @/materials。
 * 供 materials 组件（如 DxBaseData.vue）导入，避免 designer.ts ↔ materials/index.ts 循环依赖。
 */
import { type InjectionKey } from 'vue'
import type { ComponentSchema, ComputeRule } from '@/types/schema'

/** 渲染 store 接口（子页面弹窗隔离渲染上下文） */
export interface RenderStore {
  schema: ComponentSchema[]
  schemaRevision: number
  computeRules?: ComputeRule[]
  primaryKeyField?: string
  tableName?: string
  selectMode?: boolean
  selectedRowData?: Record<string, unknown> | null
}

/** designer store 的 injection key，用于 provide/inject 隔离子页面渲染 */
export const DESIGNER_STORE_KEY: InjectionKey<RenderStore> = Symbol('designerStore')

/** 递归收集所有节点（含嵌套 children） */
export function collectAllNodes(list: ComponentSchema[]): ComponentSchema[] {
  const result: ComponentSchema[] = []
  const walk = (nodes: ComponentSchema[]) => {
    for (const node of nodes) {
      result.push(node)
      if (node.children.length) walk(node.children)
    }
  }
  walk(list)
  return result
}

/** 按 fieldName 查找节点（纯函数，在指定 schema 中查找） */
export function findNodeByFieldNameIn(
  schema: ComponentSchema[],
  fieldName: string,
): ComponentSchema | null {
  const all = collectAllNodes(schema)
  return all.find((n) => n.fieldName === fieldName) ?? null
}
// ---- getConfig 依赖注入：打破 designer.ts ↔ materials/index.ts 循环依赖 ----
import type { MaterialConfig } from '@/types/schema'

let _getConfigFn: ((type: string) => MaterialConfig | undefined) | null = null

/** 由 materials/index.ts 在模块初始化时调用，注入真正的 getConfig 实现 */
export function setGetConfigFn(fn: (type: string) => MaterialConfig | undefined): void {
  _getConfigFn = fn
}

/** 获取物料配置（延迟解析，需先由 index.ts 调用 setGetConfigFn 注册） */
export function getConfig(type: string): MaterialConfig | undefined {
  return _getConfigFn?.(type)
}