/**
 * DevExtreme 验证规则元数据
 */

export type ValidationType = 'required' | 'email' | 'numeric' | 'pattern' | 'range' | 'stringLength' | 'compare' | 'custom'

export interface ValidationRuleConfig {
  type: ValidationType
  message?: string
  pattern?: string
  min?: number | string
  max?: number | string
  comparisonTarget?: string
  comparisonType?: string
  validationCallback?: string
}

export interface ValidationFieldMeta {
  prop: string
  label: string
  inputType: 'text' | 'number' | 'select'
  options?: { label: string; value: string }[]
}

export interface ValidationRuleMeta {
  type: ValidationType
  label: string
  description: string
  fields: ValidationFieldMeta[]
}

export const VALIDATABLE_TYPES = new Set([
  'dx-text-box', 'dx-text-area', 'dx-number-box',
  'dx-check-box', 'dx-switch', 'dx-slider',
  'dx-select-box', 'dx-tag-box', 'dx-lookup', 'dx-autocomplete',
  'dx-date-box', 'dx-color-box',
  'dx-radio-group', 'dx-check-box-group',
  'dx-file-uploader', 'dx-html-editor',
])

export const VALIDATION_RULES: ValidationRuleMeta[] = [
  { type: 'required', label: '必填', description: '拒绝空值和无效值', fields: [] },
  { type: 'email', label: '邮箱', description: '验证邮箱格式', fields: [] },
  { type: 'numeric', label: '数字', description: '验证必须是数字', fields: [] },
  {
    type: 'pattern', label: '正则', description: '验证必须匹配正则表达式',
    fields: [{ prop: 'pattern', label: '正则表达式', inputType: 'text' }],
  },
  {
    type: 'range', label: '范围', description: '验证值在指定范围内',
    fields: [
      { prop: 'min', label: '最小值', inputType: 'number' },
      { prop: 'max', label: '最大值', inputType: 'number' },
    ],
  },
  {
    type: 'stringLength', label: '长度', description: '验证字符串长度在指定范围内',
    fields: [
      { prop: 'min', label: '最小长度', inputType: 'number' },
      { prop: 'max', label: '最大长度', inputType: 'number' },
    ],
  },
  {
    type: 'compare', label: '比较', description: '与另一个字段比较',
    fields: [
      { prop: 'comparisonTarget', label: '比较字段', inputType: 'text' },
      {
        prop: 'comparisonType', label: '比较方式', inputType: 'select',
        options: [
          { label: '等于', value: '==' },
          { label: '不等于', value: '!=' },
          { label: '大于', value: '>' },
          { label: '大于等于', value: '>=' },
          { label: '小于', value: '<' },
          { label: '小于等于', value: '<=' },
        ],
      },
    ],
  },
  {
    type: 'custom', label: '自定义', description: '自定义验证逻辑（参数 value，返回 true/false）',
    fields: [{ prop: 'validationCallback', label: '验证代码', inputType: 'text' }],
  },
]

export function getRuleMeta(type: string): ValidationRuleMeta | undefined {
  return VALIDATION_RULES.find(r => r.type === type)
}

// ---- 运行时验证器全局注册表 ----

export interface ValidatorEntry {
  instance: unknown
  validate: () => boolean
}

/** fieldName → DxValidator 实例注册表（预览模式下使用） */
export const validatorRegistry = new Map<string, ValidatorEntry>()

export function registerValidator(fieldName: string, instance: unknown, validateFn: () => boolean) {
  validatorRegistry.set(fieldName, { instance, validate: validateFn })
}

export function unregisterValidator(fieldName: string) {
  validatorRegistry.delete(fieldName)
}

export function getValidator(fieldName: string): ValidatorEntry | undefined {
  return validatorRegistry.get(fieldName)
}
