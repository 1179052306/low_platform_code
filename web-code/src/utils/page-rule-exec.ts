/**
 * 页面规则动作执行器：把可视化配置的 PageRuleAction 翻译成运行时 api 调用。
 *
 * 复用 RendererNode.createRuntimeApi() 提供的 api 对象（setValue/setVisible/setRequired/
 * setDataSource/setProp/setStyle/get/post/call/openPage/showMessage 等），
 * 以及 compute-expr.ts 的 evaluateExpr 做表达式求值、sandbox.ts 的 createSandboxedFunction 执行脚本。
 */
import type { PageRuleAction, PageRuleCondition } from '@/types/schema'
import { evaluateExpr } from './compute-expr'
import { createSandboxedFunction } from './sandbox'

/** 比较两个值是否满足运算符关系 */
function compareValues(actual: unknown, operator: string, expected: string, expected2?: string): boolean {
  const a = actual
  const b = expected
  if (b.includes('\n')) {
    const values = b.split('\n').map((v) => v.trim()).filter(Boolean)
    if (operator === '=') return values.includes(String(a))
    if (operator === '<>') return !values.includes(String(a))
    return false
  }
  switch (operator) {
    case '=':
      return String(a) === b
    case '<>':
      return String(a) !== b
    case '>':
      return Number(a) > Number(b)
    case '>=':
      return Number(a) >= Number(b)
    case '<':
      return Number(a) < Number(b)
    case '<=':
      return Number(a) <= Number(b)
    case 'between':
      return Number(a) >= Number(b) && Number(a) <= Number(expected2 ?? b)
    case 'contains':
      return String(a).includes(b)
    case 'notcontains':
      return !String(a).includes(b)
    case 'startswith':
      return String(a).startsWith(b)
    case 'endswith':
      return String(a).endsWith(b)
    default:
      return false
  }
}

/** 求值多条件列表（AND 关系），空数组=无条件满足 */
export function evalConditions(
  conditions: PageRuleCondition[],
  context: Record<string, unknown>,
): boolean {
  if (!conditions || !conditions.length) return true
  return conditions.every((cond) => {
    const actual = context[cond.field]
    return compareValues(actual, cond.operator, cond.value, cond.value2)
  })
}

/** 把 ${fieldName} 模板字符串替换为 context 中对应字段值 */
export function interpolateTemplate(
  tpl: string,
  context: Record<string, unknown>,
): string {
  return tpl.replace(/\$\{(\w+)\}/g, (_m, key: string) => {
    const v = context[key]
    if (v === null || v === undefined) return ''
    return String(v)
  })
}

/** 对表达式求值（复用 evaluateExpr，失败返回 fallback） */
export function evalActionExpr(
  expr: string | undefined,
  context: Record<string, unknown>,
  fallback: unknown = null,
): unknown {
  if (!expr || !expr.trim()) return fallback
  const result = evaluateExpr(expr, context)
  return result === null ? fallback : result
}

/**
 * 执行单个页面规则动作。
 * @param action 动作配置
 * @param api 运行时 api 对象（由 RendererNode.createRuntimeApi 创建）
 * @param context 字段值上下文（用于表达式求值和 ${field} 插值）
 */
export async function executeAction(
  action: PageRuleAction,
  api: any,
  context: Record<string, unknown>,
): Promise<void> {
  const target = action.targetField
  try {
    switch (action.type) {
      case 'setVisible':
        if (target) api.setVisible(target, !!action.boolValue)
        break
      case 'setDisabled':
        if (target) api.setProp(target, 'disabled', !!action.boolValue)
        break
      case 'setRequired':
        if (target) api.setRequired(target, !!action.boolValue)
        break
      case 'setReadOnly':
        if (target) api.setProp(target, 'readOnly', !!action.boolValue)
        break
      case 'setValue': {
        if (target) {
          const val = evalActionExpr(action.expr, context)
          api.setValue(target, val)
        }
        break
      }
      case 'setDataSource': {
        if (target && action.dataSourceConfig) {
          const cfg = action.dataSourceConfig
          const url = interpolateTemplate(cfg.url, context)
          const params: Record<string, string> = {}
          if (cfg.params) {
            for (const [k, v] of Object.entries(cfg.params)) {
              params[k] = interpolateTemplate(v, context)
            }
          }
          const qs = Object.keys(params).length
            ? '?' +
              Object.entries(params)
                .map(
                  ([k, v]) =>
                    `${encodeURIComponent(k)}=${encodeURIComponent(v)}`,
                )
                .join('&')
            : ''
          const data = await api.get(url + qs)
          if (data && Array.isArray((data as any).data)) {
            api.setDataSource(target, (data as any).data)
          } else if (Array.isArray(data)) {
            api.setDataSource(target, data)
          }
        }
        break
      }
      case 'setProp': {
        if (target && action.propPath) {
          const val = action.propValue
            ? evalActionExpr(action.propValue, context)
            : null
          api.setProp(target, action.propPath, val)
        }
        break
      }
      case 'setStyle':
        if (target && action.styleKey) {
          api.setStyle(target, action.styleKey, action.styleValue ?? '')
        }
        break
      case 'callApi': {
        if (action.apiConfig) {
          const cfg = action.apiConfig
          const url = interpolateTemplate(cfg.url, context)
          let body: string | undefined
          if (cfg.body) {
            const bodyVal = evalActionExpr(cfg.body, context)
            body = JSON.stringify(bodyVal)
          }
          const result = await api._request(url, {
            method: cfg.method,
            body,
          })
          if (result && cfg.successActions) {
            for (const subAction of cfg.successActions) {
              await executeAction(subAction, api, context)
            }
          }
        }
        break
      }
      case 'openPage': {
        if (action.pageConfig) {
          const cfg = action.pageConfig
          const params: Record<string, unknown> = {}
          if (cfg.params) {
            for (const [k, v] of Object.entries(cfg.params)) {
              const interpolated = interpolateTemplate(v, context)
              params[k] = interpolated
            }
          }
          await api.openPage(cfg.pageName, params, {
            mode: cfg.mode === 'navigate' ? 'popup' : (cfg.mode as any) || 'popup',
          })
        }
        break
      }
      case 'showMessage':
        if (action.messageConfig) {
          api.showMessage(
            action.messageConfig.text,
            action.messageConfig.type,
          )
        }
        break
      case 'runScript': {
        if (action.script && action.script.trim()) {
          const fn = createSandboxedFunction(
            ['api', 'ctx'],
            `return (async () => { ${action.script} })()`,
          )
          await fn(api, context)
        }
        break
      }
      default:
        console.warn(`[页面规则] 未知动作类型: ${(action as any).type}`)
    }
  } catch (err) {
    console.error(`[页面规则] 动作执行失败 type=${action.type}`, err)
  }
}

/**
 * 批量执行动作列表（顺序执行，支持异步）。
 */
export async function executeActions(
  actions: PageRuleAction[],
  api: any,
  context: Record<string, unknown>,
): Promise<void> {
  for (const action of actions) {
    await executeAction(action, api, context)
  }
}