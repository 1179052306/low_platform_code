/**
 * DevExtreme → Element Plus 适配工具
 * - dataSource 转换（DevExtreme DataSource/CustomStore → 普通数组）
 * - 事件桥接（DevExtreme 事件 → Element Plus 事件）
 * - 校验规则转换（DevExtreme ValidationRule → async-validator 规则）
 */

/** DevExtreme DataSource / CustomStore 兼容解析为普通数组 */
export async function resolveDataSource(ds: any): Promise<any[]> {
  if (ds == null) return []
  if (Array.isArray(ds)) return ds
  // DevExtreme DataSource 对象
  if (typeof ds === 'object') {
    // 直接有 store.load
    if (ds.store?.load) {
      const res = await ds.store.load({})
      return res?.data ?? res ?? []
    }
    // DataSource 实例（有 loadItems / load）
    if (typeof ds.load === 'function') {
      const res = await ds.load({})
      return res?.data ?? res ?? []
    }
    // { data: [...] } 包装
    if (Array.isArray(ds.data)) return ds.data
  }
  if (typeof ds === 'function') return ds()
  return []
}

/** 把 DevExtreme displayExpr/valueExpr 应用于数据数组，生成 ElSelect 的 options */
export function toSelectOptions(
  data: any[],
  displayExpr?: string | ((item: any) => string),
  valueExpr?: string | ((item: any) => any),
): { label: string; value: any }[] {
  return data.map((item) => ({
    label: typeof displayExpr === 'function' ? displayExpr(item) : (displayExpr ? item?.[displayExpr] : String(item)),
    value: typeof valueExpr === 'function' ? valueExpr(item) : (valueExpr ? item?.[valueExpr] : item),
  }))
}

/** DevExtreme 日期类型 → Element Plus 日期 type 映射 */
export function dxDateTypeToEl(dxType?: string): 'date' | 'datetime' | 'month' | 'year' | 'week' | 'daterange' | 'datetimerange' {
  switch (dxType) {
    case 'date': return 'date'
    case 'datetime': return 'datetime'
    case 'time': return 'datetime'
    case 'month': return 'month'
    case 'year': return 'year'
    case 'week': return 'week'
    default: return 'date'
  }
}

/** DevExtreme 校验规则 → async-validator 规则 */
export function dxRulesToAsyncValidator(rules: any[]): any[] {
  if (!Array.isArray(rules)) return []
  return rules.map((r) => {
    const rule: any = { message: r.message }
    switch (r.type) {
      case 'required':
        return { required: true, message: r.message ?? '此字段为必填项', trigger: 'blur' }
      case 'email':
        return { type: 'email', message: r.message ?? '请输入正确的邮箱', trigger: 'blur' }
      case 'numeric':
        return { type: 'number', message: r.message ?? '请输入数字', trigger: 'blur' }
      case 'pattern':
        return { pattern: new RegExp(r.pattern), message: r.message ?? '格式不正确', trigger: 'blur' }
      case 'range':
        return {
          validator: (_rule: any, value: number, cb: (e?: Error) => void) => {
            if (value == null || String(value) === '') return cb()
            const n = Number(value)
            if (r.min != null && n < Number(r.min)) return cb(new Error(r.message ?? `不能小于 ${r.min}`))
            if (r.max != null && n > Number(r.max)) return cb(new Error(r.message ?? `不能大于 ${r.max}`))
            cb()
          },
          trigger: 'blur',
        }
      case 'stringLength':
        return {
          min: r.min != null ? Number(r.min) : undefined,
          max: r.max != null ? Number(r.max) : undefined,
          message: r.message ?? `长度需在 ${r.min ?? 0}-${r.max ?? '∞'} 之间`,
          trigger: 'blur',
        }
      case 'compare':
        return {
          validator: (_rule: any, value: any, cb: (e?: Error) => void) => {
            if (r.comparisonTarget == null) return cb()
            const target = typeof r.comparisonTarget === 'function' ? r.comparisonTarget() : r.comparisonTarget
            const ok = compareValues(value, target, r.comparisonType ?? '==')
            if (!ok) return cb(new Error(r.message ?? '比较不通过'))
            cb()
          },
          trigger: 'blur',
        }
      case 'custom':
        return {
          validator: (_rule: any, value: any, cb: (e?: Error) => void) => {
            if (typeof r.validationCallback === 'function') {
              const ok = r.validationCallback(value)
              if (!ok) return cb(new Error(r.message ?? '校验不通过'))
            }
            cb()
          },
          trigger: 'blur',
        }
      default:
        return rule
    }
  })
}

function compareValues(a: any, b: any, op: string): boolean {
  switch (op) {
    case '==': return a == b
    case '!=': return a != b
    case '>': return a > b
    case '>=': return a >= b
    case '<': return a < b
    case '<=': return a <= b
    default: return false
  }
}