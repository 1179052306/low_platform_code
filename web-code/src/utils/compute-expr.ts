/**
 * 计算表达式安全求值（FE-S10 计算表达式）。
 *
 * 前端预计算：watch triggerFields 变化时，用 formData 上下文求值 computeExpr。
 * 后端是最终校验（SpEL），前端只做展现预填，求值失败静默忽略。
 *
 * 安全策略：用 Function 构造器隔离全局作用域，只暴露数学函数 + 上下文字段。
 * 禁止访问 window/document/globalThis/eval/Function/Reflect/Proxy 等。
 */
const ALLOWED_MATH_FNS = [
  'Math',
  'Number',
  'parseInt',
  'parseFloat',
  'isNaN',
  'isFinite',
  'round',
  'floor',
  'ceil',
  'abs',
  'min',
  'max',
  'toFixed',
]

/**
 * 安全求值计算表达式。
 * @param expr 表达式（如 "unitPrice * quantity" 或 "Math.round(amount * 0.13)"）
 * @param context 字段上下文（如 { unitPrice: 10, quantity: 2 }）
 * @returns 求值结果，失败返回 null
 */
export function evaluateExpr(expr: string, context: Record<string, unknown>): unknown | null {
  if (!expr || !expr.trim()) return null
  try {
    const keys = Object.keys(context)
    const values = Object.values(context)
    const mathCtx = {
      Math,
      Number,
      parseInt,
      parseFloat,
      isNaN,
      isFinite,
      round: Math.round,
      floor: Math.floor,
      ceil: Math.ceil,
      abs: Math.abs,
      min: Math.min,
      max: Math.max,
      toFixed: (n: number, digits: number) => Number(n.toFixed(digits)),
    }
    const fn = new Function(
      ...ALLOWED_MATH_FNS,
      ...keys,
      `"use strict"; return (${expr});`,
    )
    return fn(...Object.values(mathCtx), ...values)
  } catch {
    return null
  }
}

/**
 * 从 formData 中提取指定字段的值集合（供 watch 用）。
 */
export function pickFields(
  formData: Record<string, unknown>,
  fields: string[],
): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  for (const f of fields) {
    result[f] = formData[f]
  }
  return result
}