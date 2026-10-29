<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElDialog, ElButton, ElInput, ElSwitch, ElSelect, ElOption, ElDatePicker, ElMessage, ElMessageBox } from 'element-plus'
import { t } from '@/store/lang'
import ExprDialog from './ExprDialog.vue'
import EventCodeDialog from './EventCodeDialog.vue'
import PageSelectDialog from './PageSelectDialog.vue'
import type { PageRule, PageRuleAction, PageRuleActionType, PageRuleCondition, PropSetterConfig } from '@/types/schema'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'
import { designerStore, findNodeByFieldNameIn } from '@/store/designer'
import { getConfig, getDxAllPropSetters } from '@/materials'

const props = defineProps<{
  modelValue: boolean
  fields: string[]
  rules: PageRule[]
}>()

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  confirm: [rules: PageRule[]]
}>()

const innerRules = ref<PageRule[]>([])
const selectedRuleId = ref('')
const selectedRule = ref<PageRule | null>(null)

const showExprDialog = ref(false)
const exprDialogTarget = ref<'setValue' | 'setProp'>('setValue')
const exprDialogActionId = ref('')

const ACTION_TYPES: { value: PageRuleActionType; label: string }[] = [
  { value: 'setVisible', label: '显示/隐藏' },
  { value: 'setDisabled', label: '启用/禁用' },
  { value: 'setRequired', label: '必填/非必填' },
  { value: 'setReadOnly', label: '只读/可编辑' },
  { value: 'setValue', label: '赋值' },
  { value: 'setDataSource', label: '刷新数据源' },
  { value: 'setProp', label: '设置属性' },
  { value: 'setStyle', label: '设置样式' },
  { value: 'callApi', label: '调用接口' },
  { value: 'openPage', label: '打开页面' },
  { value: 'showMessage', label: '提示消息' },
  { value: 'runScript', label: '执行脚本' },
]

const OPERATORS_BY_TYPE: Record<string, { value: string; label: string }[]> = {
  number: [
    { value: '=', label: '等于' },
    { value: '<>', label: '不等于' },
    { value: '>', label: '大于' },
    { value: '>=', label: '大于等于' },
    { value: '<', label: '小于' },
    { value: '<=', label: '小于等于' },
    { value: 'between', label: '之间' },
  ],
  date: [
    { value: '=', label: '等于' },
    { value: '<>', label: '不等于' },
    { value: '>', label: '大于' },
    { value: '>=', label: '大于等于' },
    { value: '<', label: '小于' },
    { value: '<=', label: '小于等于' },
    { value: 'between', label: '之间' },
  ],
  boolean: [
    { value: '=', label: '等于' },
    { value: '<>', label: '不等于' },
  ],
  string: [
    { value: 'contains', label: '包含' },
    { value: 'notcontains', label: '不包含' },
    { value: '=', label: '等于' },
    { value: '<>', label: '不等于' },
    { value: 'startswith', label: '以...开头' },
    { value: 'endswith', label: '以...结尾' },
  ],
}

const NUMBER_TYPES = new Set(['number-box', 'slider', 'range-slider'])
const DATE_TYPES = new Set(['date-box', 'date-range-box', 'calendar'])
const BOOLEAN_TYPES = new Set(['check-box', 'switch'])
const SELECT_TYPES = new Set(['select-box', 'lookup', 'radio-group', 'tag-box'])

function getFieldType(field: string): string {
  if (!field) return ''
  const node = findNodeByFieldNameIn(designerStore.schema, field)
  return node?.type ?? ''
}

function getFieldDataType(field: string): string {
  const type = getFieldType(field)
  if (NUMBER_TYPES.has(type)) return 'number'
  if (DATE_TYPES.has(type)) return 'date'
  if (BOOLEAN_TYPES.has(type)) return 'boolean'
  return 'string'
}

function getOperatorsForField(field: string): { value: string; label: string }[] {
  return OPERATORS_BY_TYPE[getFieldDataType(field)] ?? OPERATORS_BY_TYPE.string
}

function getFieldItems(field: string): { label: string; value: string }[] {
  if (!field) return []
  const node = findNodeByFieldNameIn(designerStore.schema, field)
  if (!node) return []
  const items = node.props.items
  if (!Array.isArray(items)) return []
  return items.map((it: any) => ({ label: it.label ?? it.text ?? String(it.value), value: String(it.value) }))
}

function getFieldPropSetters(field: string): PropSetterConfig[] {
  const type = getFieldType(field)
  if (!type) return []
  return getDxAllPropSetters(type)
}

function getPropSetterConfig(field: string, propPath: string): PropSetterConfig | undefined {
  if (!field || !propPath) return undefined
  return getFieldPropSetters(field).find((s) => s.prop === propPath)
}

function onPropPathChange(act: PageRuleAction): void {
  const setter = getPropSetterConfig(act.targetField || '', act.propPath || '')
  if (!setter) {
    act.propValue = ''
    return
  }
  switch (setter.type) {
    case 'switch':
      act.propValue = 'false'
      break
    case 'select':
      act.propValue = setter.options?.length ? String(setter.options[0].value) : ''
      break
    case 'color':
      act.propValue = '#000000'
      break
    case 'number':
      act.propValue = ''
      break
    default:
      act.propValue = ''
  }
}

function actionLabel(type: PageRuleActionType): string {
  return ACTION_TYPES.find((a) => a.value === type)?.label ?? type
}

function boolLabel(type: PageRuleActionType, val: boolean): string {
  switch (type) {
    case 'setVisible': return val ? '显示' : '隐藏'
    case 'setDisabled': return val ? '禁用' : '启用'
    case 'setRequired': return val ? '必填' : '非必填'
    case 'setReadOnly': return val ? '只读' : '可编辑'
    default: return ''
  }
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      innerRules.value = JSON.parse(JSON.stringify(props.rules ?? []))
      for (const r of innerRules.value) {
        if (!r.conditions) r.conditions = []
        if (!r.elseActions) r.elseActions = []
        for (const c of r.conditions) {
          if (c.value2 === undefined) c.value2 = ''
        }
      }
      selectedRuleId.value = innerRules.value[0]?.id ?? ''
      syncSelectedRule()
    }
  },
)

function syncSelectedRule() {
  selectedRule.value = innerRules.value.find((r) => r.id === selectedRuleId.value) ?? null
}

function selectRule(id: string) {
  selectedRuleId.value = id
  syncSelectedRule()
}

function addRule() {
  const id = `pr_${Date.now().toString(36)}`
  const rule: PageRule = {
    id,
    name: t('新规则'),
    enabled: true,
    conditions: [],
    actions: [],
    elseActions: [],
    sortNo: innerRules.value.length,
  }
  innerRules.value.push(rule)
  selectedRuleId.value = id
  syncSelectedRule()
}

function deleteRule(rule: PageRule) {
  ElMessageBox.confirm(t('确认删除该规则？'), t('提示'), { type: 'warning' })
    .then(() => {
      const idx = innerRules.value.findIndex((r) => r.id === rule.id)
      if (idx >= 0) innerRules.value.splice(idx, 1)
      if (selectedRuleId.value === rule.id) {
        selectedRuleId.value = innerRules.value[0]?.id ?? ''
        syncSelectedRule()
      }
    })
    .catch(() => {})
}

function genActionId(): string {
  return `act_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`
}

function addAction(list: 'actions' | 'elseActions') {
  if (!selectedRule.value) return
  const action: PageRuleAction = { id: genActionId(), type: 'setVisible', targetField: '', boolValue: true }
  if (list === 'actions') {
    selectedRule.value.actions.push(action)
  } else {
    if (!selectedRule.value.elseActions) selectedRule.value.elseActions = []
    selectedRule.value.elseActions.push(action)
  }
}

function removeAction(list: 'actions' | 'elseActions', idx: number) {
  if (!selectedRule.value) return
  if (list === 'actions') {
    selectedRule.value.actions.splice(idx, 1)
  } else if (selectedRule.value.elseActions) {
    selectedRule.value.elseActions.splice(idx, 1)
  }
}

function ensureActionConfig(act: PageRuleAction): void {
  switch (act.type) {
    case 'setDataSource':
      if (!act.dataSourceConfig) act.dataSourceConfig = { url: '' }
      break
    case 'callApi':
      if (!act.apiConfig) act.apiConfig = { method: 'GET', url: '' }
      break
    case 'openPage':
      if (!act.pageConfig) act.pageConfig = { pageName: '' }
      break
    case 'showMessage':
      if (!act.messageConfig) act.messageConfig = { type: 'success', text: '' }
      break
  }
}

// ================ 条件管理 ================
function addCondition() {
  if (!selectedRule.value) return
  selectedRule.value.conditions.push({ field: '', operator: '=', value: '', value2: '' })
}

function removeCondition(idx: number) {
  if (!selectedRule.value) return
  selectedRule.value.conditions.splice(idx, 1)
}

function onConditionFieldChange(cond: PageRuleCondition) {
  const ops = getOperatorsForField(cond.field)
  if (!ops.find((o) => o.value === cond.operator)) {
    cond.operator = ops[0]?.value ?? '='
  }
  cond.value = ''
  cond.value2 = ''
}

function onConditionOperatorChange(cond: PageRuleCondition) {
  cond.value = ''
  cond.value2 = ''
}

// ================ 多值编辑弹窗（与高级查询一致：可编辑表格） ================
const showMultiValueDialog = ref(false)
const multiValueEditing = ref<{ cond: PageRuleCondition | null }>({ cond: null })
const multiValueRows = ref<string[]>([])
const multiValueFieldCaption = ref('')

function openMultiValueDialog(cond: PageRuleCondition) {
  multiValueEditing.value.cond = cond
  multiValueFieldCaption.value = cond.field || t('值')
  const existing = cond.value ? String(cond.value).split('\n').map((v) => v.trim()).filter((v) => v !== '') : []
  const rows = [...existing]
  while (rows.length < 100) rows.push('')
  multiValueRows.value = rows
  showMultiValueDialog.value = true
}

function onMultiValueConfirm() {
  const cond = multiValueEditing.value.cond
  if (cond) {
    const values = multiValueRows.value.map((v) => v.trim()).filter((v) => v !== '')
    cond.value = values.join('\n')
  }
  showMultiValueDialog.value = false
}

function clearAllMultiValueRows() {
  multiValueRows.value = multiValueRows.value.map(() => '')
}

function isMultiValue(cond: PageRuleCondition): boolean {
  if (cond.operator !== '=' && cond.operator !== '<>') return false
  const dataType = getFieldDataType(cond.field)
  if (dataType === 'date' || dataType === 'boolean') return false
  if (SELECT_TYPES.has(getFieldType(cond.field)) && getFieldItems(cond.field).length) return false
  return true
}

function isMultiValueReadonly(cond: PageRuleCondition): boolean {
  return cond.value != null && String(cond.value).includes('\n')
}

function multiValueDisplay(cond: PageRuleCondition): string {
  return cond.value.replace(/\n/g, ', ')
}

// ================ 表达式编辑（赋值/属性值） ================
function openExprDialog(target: 'setValue' | 'setProp', actionId: string) {
  exprDialogTarget.value = target
  exprDialogActionId.value = actionId
  showExprDialog.value = true
}

const exprDialogExpr = computed(() => {
  if (!selectedRule.value || !exprDialogActionId.value) return ''
  const action = findActionById(exprDialogActionId.value)
  if (!action) return ''
  if (exprDialogTarget.value === 'setValue') return action.expr ?? ''
  return action.propValue ?? ''
})

function findActionById(id: string): PageRuleAction | undefined {
  if (!selectedRule.value) return undefined
  return [...selectedRule.value.actions, ...(selectedRule.value.elseActions ?? [])].find((a) => a.id === id)
}

function onExprConfirm(expr: string, _triggerFields: string[]) {
  if (!selectedRule.value || !exprDialogActionId.value) return
  const action = findActionById(exprDialogActionId.value)
  if (!action) return
  if (exprDialogTarget.value === 'setValue') action.expr = expr
  else action.propValue = expr
}

// ================ 页面选择（打开页面动作） ================
const showPageSelect = ref(false)
const pageSelectActionId = ref('')

function openPageSelectDialog(actionId: string) {
  pageSelectActionId.value = actionId
  showPageSelect.value = true
}

function onPageSelectConfirm(pageId: string) {
  const action = findActionById(pageSelectActionId.value)
  if (action && action.pageConfig) {
    action.pageConfig.pageName = pageId
  }
}

// ================ 脚本编辑（执行脚本动作） ================
const showScriptDialog = ref(false)
const scriptDialogValue = ref('')
const scriptActionId = ref('')

function openScriptDialog(actionId: string) {
  const action = findActionById(actionId)
  scriptActionId.value = actionId
  scriptDialogValue.value = action?.script ?? ''
  showScriptDialog.value = true
}

function onScriptConfirm(value: string) {
  const action = findActionById(scriptActionId.value)
  if (action) action.script = value
}

function onConfirm() {
  for (const r of innerRules.value) {
    if (!r.name.trim()) { ElMessage.warning(t('规则名称不能为空')); return }
  }
  emit('confirm', innerRules.value)
  emit('update:modelValue', false)
}

const { isMaximized, toggleMaximize, resetDialog } = useDialogDragAndMax()
watch(() => props.modelValue, (v) => { if (!v) resetDialog() })
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    width="960px"
    append-to-body
    draggable
    :fullscreen="isMaximized"
    :close-on-click-modal="false"
  >
    <template #header>
      <DialogMaxHeader :title="t('页面规则')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <div class="pr-dialog-body">
      <div class="pr-left">
        <div class="pr-left-header">
          <span class="pr-left-title">{{ t('规则列表') }}</span>
          <ElButton type="primary" size="small" @click="addRule">+ {{ t('新建') }}</ElButton>
        </div>
        <div class="pr-rule-list">
          <div
            v-for="r in innerRules"
            :key="r.id"
            class="pr-rule-item"
            :class="{ 'pr-rule-selected': r.id === selectedRuleId }"
            @click="selectRule(r.id)"
          >
            <span class="pr-rule-name">{{ r.name }}</span>
            <span class="pr-rule-info">{{ r.conditions.length }}{{ t('条件') }}/{{ r.actions.length }}{{ t('动作') }}</span>
            <ElButton type="danger" link size="small" @click.stop="deleteRule(r)">×</ElButton>
          </div>
          <div v-if="!innerRules.length" class="pr-rule-empty">{{ t('暂无规则，点击新建') }}</div>
        </div>
      </div>

      <div class="pr-right" v-if="selectedRule">
        <div class="pr-form-row">
          <label class="pr-form-label">{{ t('规则名称') }}</label>
          <ElInput v-model="selectedRule.name" size="small" style="width: 240px" />
          <label class="pr-form-label" style="margin-left: 16px">{{ t('启用') }}</label>
          <ElSwitch v-model="selectedRule.enabled" />
        </div>

        <div class="pr-section">
          <div class="pr-section-header">
            <span class="pr-section-title">{{ t('触发条件') }}（{{ t('多条件且关系，条件字段变化时触发') }}）</span>
            <ElButton type="primary" size="small" @click="addCondition">+ {{ t('添加条件') }}</ElButton>
          </div>
          <div class="pr-cond-list">
            <div v-for="(cond, idx) in selectedRule.conditions" :key="idx" class="pr-cond-row">
              <select class="pr-cond-field" v-model="cond.field" @change="onConditionFieldChange(cond)">
                <option value="">{{ t('选择字段') }}</option>
                <option v-for="f in fields" :key="f" :value="f">{{ f }}</option>
              </select>
              <select class="pr-cond-op" v-model="cond.operator" @change="onConditionOperatorChange(cond)">
                <option v-for="op in getOperatorsForField(cond.field)" :key="op.value" :value="op.value">{{ op.label }}</option>
              </select>
              <template v-if="getFieldDataType(cond.field) === 'date'">
                <ElDatePicker
                  v-model="cond.value"
                  type="datetime"
                  size="small"
                  style="width: 160px"
                  :placeholder="cond.operator === 'between' ? t('开始') : t('选择日期')"
                />
                <template v-if="cond.operator === 'between'">
                  <span class="pr-between-sep">~</span>
                  <ElDatePicker
                    v-model="cond.value2"
                    type="datetime"
                    size="small"
                    style="width: 160px"
                    :placeholder="t('结束')"
                  />
                </template>
              </template>
              <template v-else-if="getFieldDataType(cond.field) === 'boolean'">
                <ElSelect v-model="cond.value" size="small" style="width: 100px">
                  <ElOption label="true" value="true" />
                  <ElOption label="false" value="false" />
                </ElSelect>
              </template>
              <template v-else-if="SELECT_TYPES.has(getFieldType(cond.field)) && getFieldItems(cond.field).length">
                <ElSelect v-model="cond.value" size="small" style="width: 160px" :placeholder="t('选择值')">
                  <ElOption v-for="it in getFieldItems(cond.field)" :key="it.value" :label="it.label" :value="it.value" />
                </ElSelect>
              </template>
              <template v-else>
                <ElInput
                  v-if="isMultiValueReadonly(cond)"
                  :model-value="multiValueDisplay(cond)"
                  size="small"
                  style="width: 160px"
                  readonly
                  :placeholder="t('多值')"
                />
                <ElInput
                  v-else
                  v-model="cond.value"
                  :type="getFieldDataType(cond.field) === 'number' ? 'number' : 'text'"
                  size="small"
                  style="width: 160px"
                  :placeholder="cond.operator === 'between' ? t('开始') : t('值')"
                />
                <template v-if="cond.operator === 'between'">
                  <span class="pr-between-sep">~</span>
                  <ElInput
                    v-model="cond.value2"
                    :type="getFieldDataType(cond.field) === 'number' ? 'number' : 'text'"
                    size="small"
                    style="width: 160px"
                    :placeholder="t('结束')"
                  />
                </template>
                <button v-if="isMultiValue(cond)" class="pr-multi-btn" @click="openMultiValueDialog(cond)">{{ t('多值') }}</button>
              </template>
              <button class="pr-cond-del" @click="removeCondition(idx)">×</button>
            </div>
            <div v-if="!selectedRule.conditions.length" class="pr-action-empty">{{ t('暂无条件，无条件触发') }}</div>
          </div>
        </div>

        <div class="pr-section">
          <div class="pr-section-header">
            <span class="pr-section-title">{{ t('满足条件时执行') }}</span>
            <ElButton type="primary" size="small" @click="addAction('actions')">+ {{ t('添加动作') }}</ElButton>
          </div>
          <div class="pr-action-list">
            <div v-for="(act, idx) in selectedRule.actions" :key="act.id" class="pr-action-row">
              <span class="pr-action-index">{{ idx + 1 }}.</span>
              <select class="pr-action-type" v-model="act.type" @change="ensureActionConfig(act)">
                <option v-for="at in ACTION_TYPES" :key="at.value" :value="at.value">{{ t(at.label) }}</option>
              </select>
              <select v-if="act.type !== 'callApi' && act.type !== 'openPage' && act.type !== 'showMessage' && act.type !== 'runScript'" class="pr-action-field" v-model="act.targetField">
                <option value="">{{ t('目标字段') }}</option>
                <option v-for="f in fields" :key="f" :value="f">{{ f }}</option>
              </select>
              <template v-if="act.type === 'setVisible' || act.type === 'setDisabled' || act.type === 'setRequired' || act.type === 'setReadOnly'">
                <button class="pr-bool-btn" :class="{ 'pr-bool-on': act.boolValue }" @click="act.boolValue = true">{{ boolLabel(act.type, true) }}</button>
                <button class="pr-bool-btn" :class="{ 'pr-bool-off': !act.boolValue }" @click="act.boolValue = false">{{ boolLabel(act.type, false) }}</button>
              </template>
              <template v-if="act.type === 'setValue'">
                <ElDatePicker
                  v-if="getFieldDataType(act.targetField || '') === 'date'"
                  v-model="act.expr"
                  type="datetime"
                  size="small"
                  style="width: 160px"
                  :placeholder="t('选择日期')"
                />
                <ElSelect
                  v-else-if="getFieldDataType(act.targetField || '') === 'boolean'"
                  v-model="act.expr"
                  size="small"
                  style="width: 100px"
                >
                  <ElOption label="true" value="true" />
                  <ElOption label="false" value="false" />
                </ElSelect>
                <ElSelect
                  v-else-if="SELECT_TYPES.has(getFieldType(act.targetField || '')) && getFieldItems(act.targetField || '').length"
                  v-model="act.expr"
                  size="small"
                  style="width: 160px"
                >
                  <ElOption v-for="it in getFieldItems(act.targetField || '')" :key="it.value" :label="it.label" :value="it.value" />
                </ElSelect>
                <ElInput
                  v-else
                  v-model="act.expr"
                  size="small"
                  style="width: 160px"
                  :placeholder="t('值或表达式')"
                />
                <button class="pr-target-edit" @click="openExprDialog('setValue', act.id)">{{ t('表达式') }}</button>
              </template>
              <template v-if="act.type === 'setProp'">
                <select class="pr-prop-path" v-model="act.propPath" @change="onPropPathChange(act)">
                  <option v-for="s in getFieldPropSetters(act.targetField || '')" :key="s.prop" :value="s.prop">{{ s.label }}</option>
                </select>
                <ElSwitch
                  v-if="getPropSetterConfig(act.targetField || '', act.propPath || '')?.type === 'switch'"
                  v-model="act.propValue"
                  :active-value="'true'"
                  :inactive-value="'false'"
                />
                <ElSelect
                  v-else-if="getPropSetterConfig(act.targetField || '', act.propPath || '')?.type === 'select'"
                  v-model="act.propValue"
                  size="small"
                  style="width: 140px"
                >
                  <ElOption
                    v-for="opt in getPropSetterConfig(act.targetField || '', act.propPath || '')?.options ?? []"
                    :key="opt.value"
                    :label="opt.label"
                    :value="String(opt.value)"
                  />
                </ElSelect>
                <input
                  v-else-if="getPropSetterConfig(act.targetField || '', act.propPath || '')?.type === 'color'"
                  type="color"
                  v-model="act.propValue"
                  class="pr-color-input"
                />
                <ElInput
                  v-else-if="getPropSetterConfig(act.targetField || '', act.propPath || '')?.type === 'number'"
                  v-model="act.propValue"
                  type="number"
                  size="small"
                  style="width: 120px"
                  :placeholder="t('属性值')"
                />
                <ElInput
                  v-else
                  v-model="act.propValue"
                  size="small"
                  style="width: 120px"
                  :placeholder="t('属性值')"
                />
              </template>
              <template v-if="act.type === 'setStyle'">
                <input class="pr-prop-path" type="text" v-model="act.styleKey" :placeholder="t('样式键')" />
                <input class="pr-expr-input" type="text" v-model="act.styleValue" :placeholder="t('样式值')" />
              </template>
              <template v-if="act.type === 'setDataSource'">
                <input class="pr-ds-url" type="text" v-model="act.dataSourceConfig!.url" :placeholder="t('数据源URL（可${field}引用）')" />
              </template>
              <template v-if="act.type === 'callApi'">
                <select class="pr-api-method" v-model="act.apiConfig!.method">
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                </select>
                <input class="pr-ds-url" type="text" v-model="act.apiConfig!.url" :placeholder="t('接口URL')" />
              </template>
              <template v-if="act.type === 'openPage'">
                <button class="pr-target-edit" @click="openPageSelectDialog(act.id)">
                  {{ act.pageConfig?.pageName ? t('已选：') + act.pageConfig.pageName : t('选择页面') }}
                </button>
              </template>
              <template v-if="act.type === 'showMessage'">
                <select class="pr-api-method" v-model="act.messageConfig!.type">
                  <option value="success">success</option>
                  <option value="warning">warning</option>
                  <option value="error">error</option>
                  <option value="info">info</option>
                </select>
                <input class="pr-expr-input" type="text" v-model="act.messageConfig!.text" :placeholder="t('消息内容')" />
              </template>
              <template v-if="act.type === 'runScript'">
                <button class="pr-target-edit" @click="openScriptDialog(act.id)">
                  {{ act.script ? t('已编辑，点击修改') : t('编辑脚本') }}
                </button>
              </template>
              <button class="pr-action-del" @click="removeAction('actions', idx)">×</button>
            </div>
            <div v-if="!selectedRule.actions.length" class="pr-action-empty">{{ t('暂无动作') }}</div>
          </div>
        </div>

        <div class="pr-section">
          <div class="pr-section-header">
            <span class="pr-section-title">{{ t('不满足条件时执行') }}（{{ t('可选') }}）</span>
            <ElButton type="primary" size="small" @click="addAction('elseActions')">+ {{ t('添加动作') }}</ElButton>
          </div>
          <div class="pr-action-list">
            <div v-for="(act, idx) in selectedRule.elseActions" :key="act.id" class="pr-action-row">
              <span class="pr-action-index">{{ idx + 1 }}.</span>
              <select class="pr-action-type" v-model="act.type" @change="ensureActionConfig(act)">
                <option v-for="at in ACTION_TYPES" :key="at.value" :value="at.value">{{ t(at.label) }}</option>
              </select>
              <select v-if="act.type !== 'callApi' && act.type !== 'openPage' && act.type !== 'showMessage' && act.type !== 'runScript'" class="pr-action-field" v-model="act.targetField">
                <option value="">{{ t('目标字段') }}</option>
                <option v-for="f in fields" :key="f" :value="f">{{ f }}</option>
              </select>
              <template v-if="act.type === 'setVisible' || act.type === 'setDisabled' || act.type === 'setRequired' || act.type === 'setReadOnly'">
                <button class="pr-bool-btn" :class="{ 'pr-bool-on': act.boolValue }" @click="act.boolValue = true">{{ boolLabel(act.type, true) }}</button>
                <button class="pr-bool-btn" :class="{ 'pr-bool-off': !act.boolValue }" @click="act.boolValue = false">{{ boolLabel(act.type, false) }}</button>
              </template>
              <template v-if="act.type === 'setValue'">
                <ElDatePicker
                  v-if="getFieldDataType(act.targetField || '') === 'date'"
                  v-model="act.expr"
                  type="datetime"
                  size="small"
                  style="width: 160px"
                  :placeholder="t('选择日期')"
                />
                <ElSelect
                  v-else-if="getFieldDataType(act.targetField || '') === 'boolean'"
                  v-model="act.expr"
                  size="small"
                  style="width: 100px"
                >
                  <ElOption label="true" value="true" />
                  <ElOption label="false" value="false" />
                </ElSelect>
                <ElSelect
                  v-else-if="SELECT_TYPES.has(getFieldType(act.targetField || '')) && getFieldItems(act.targetField || '').length"
                  v-model="act.expr"
                  size="small"
                  style="width: 160px"
                >
                  <ElOption v-for="it in getFieldItems(act.targetField || '')" :key="it.value" :label="it.label" :value="it.value" />
                </ElSelect>
                <ElInput
                  v-else
                  v-model="act.expr"
                  size="small"
                  style="width: 160px"
                  :placeholder="t('值或表达式')"
                />
                <button class="pr-target-edit" @click="openExprDialog('setValue', act.id)">{{ t('表达式') }}</button>
              </template>
              <template v-if="act.type === 'setProp'">
                <select class="pr-prop-path" v-model="act.propPath" @change="onPropPathChange(act)">
                  <option v-for="s in getFieldPropSetters(act.targetField || '')" :key="s.prop" :value="s.prop">{{ s.label }}</option>
                </select>
                <ElSwitch
                  v-if="getPropSetterConfig(act.targetField || '', act.propPath || '')?.type === 'switch'"
                  v-model="act.propValue"
                  :active-value="'true'"
                  :inactive-value="'false'"
                />
                <ElSelect
                  v-else-if="getPropSetterConfig(act.targetField || '', act.propPath || '')?.type === 'select'"
                  v-model="act.propValue"
                  size="small"
                  style="width: 140px"
                >
                  <ElOption
                    v-for="opt in getPropSetterConfig(act.targetField || '', act.propPath || '')?.options ?? []"
                    :key="opt.value"
                    :label="opt.label"
                    :value="String(opt.value)"
                  />
                </ElSelect>
                <input
                  v-else-if="getPropSetterConfig(act.targetField || '', act.propPath || '')?.type === 'color'"
                  type="color"
                  v-model="act.propValue"
                  class="pr-color-input"
                />
                <ElInput
                  v-else-if="getPropSetterConfig(act.targetField || '', act.propPath || '')?.type === 'number'"
                  v-model="act.propValue"
                  type="number"
                  size="small"
                  style="width: 120px"
                  :placeholder="t('属性值')"
                />
                <ElInput
                  v-else
                  v-model="act.propValue"
                  size="small"
                  style="width: 120px"
                  :placeholder="t('属性值')"
                />
              </template>
              <template v-if="act.type === 'setStyle'">
                <input class="pr-prop-path" type="text" v-model="act.styleKey" :placeholder="t('样式键')" />
                <input class="pr-expr-input" type="text" v-model="act.styleValue" :placeholder="t('样式值')" />
              </template>
              <template v-if="act.type === 'setDataSource'">
                <input class="pr-ds-url" type="text" v-model="act.dataSourceConfig!.url" :placeholder="t('数据源URL')" />
              </template>
              <template v-if="act.type === 'callApi'">
                <select class="pr-api-method" v-model="act.apiConfig!.method">
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                </select>
                <input class="pr-ds-url" type="text" v-model="act.apiConfig!.url" :placeholder="t('接口URL')" />
              </template>
              <template v-if="act.type === 'openPage'">
                <button class="pr-target-edit" @click="openPageSelectDialog(act.id)">
                  {{ act.pageConfig?.pageName ? t('已选：') + act.pageConfig.pageName : t('选择页面') }}
                </button>
              </template>
              <template v-if="act.type === 'showMessage'">
                <select class="pr-api-method" v-model="act.messageConfig!.type">
                  <option value="success">success</option>
                  <option value="warning">warning</option>
                  <option value="error">error</option>
                  <option value="info">info</option>
                </select>
                <input class="pr-expr-input" type="text" v-model="act.messageConfig!.text" :placeholder="t('消息内容')" />
              </template>
              <template v-if="act.type === 'runScript'">
                <button class="pr-target-edit" @click="openScriptDialog(act.id)">
                  {{ act.script ? t('已编辑，点击修改') : t('编辑脚本') }}
                </button>
              </template>
              <button class="pr-action-del" @click="removeAction('elseActions', idx)">×</button>
            </div>
            <div v-if="!selectedRule.elseActions?.length" class="pr-action-empty">{{ t('暂无动作') }}</div>
          </div>
        </div>
      </div>
      <div class="pr-right-empty" v-else>
        {{ t('请从左侧选择或新建规则') }}
      </div>
    </div>

    <!-- 多值编辑弹窗（与高级查询一致：可编辑表格） -->
    <ElDialog
      v-model="showMultiValueDialog"
      :title="`${t('多值编辑')} - ${multiValueFieldCaption}`"
      width="400px"
      append-to-body
      :close-on-click-modal="false"
      class="pr-multi-value-dialog"
    >
      <div class="pr-multi-value-table-wrapper">
        <div class="pr-multi-value-table-header">
          <span class="pr-multi-value-col-field">{{ multiValueFieldCaption }}</span>
          <span class="pr-multi-value-col-op">{{ t('操作') }}</span>
        </div>
        <div class="pr-multi-value-table-body">
          <div v-for="(_, rowIdx) in multiValueRows" :key="rowIdx" class="pr-multi-value-row">
            <ElInput
              v-model="multiValueRows[rowIdx]"
              size="small"
              class="pr-multi-value-row-input"
            />
            <button
              class="pr-multi-value-row-del"
              @click="multiValueRows[rowIdx] = ''"
              :title="t('清空')"
            >×</button>
          </div>
        </div>
      </div>
      <template #footer>
        <ElButton @click="clearAllMultiValueRows">{{ t('清空全部') }}</ElButton>
        <ElButton @click="showMultiValueDialog = false">{{ t('取消') }}</ElButton>
        <ElButton type="primary" @click="onMultiValueConfirm">{{ t('确定') }}</ElButton>
      </template>
    </ElDialog>

    <ExprDialog
      v-model="showExprDialog"
      :fields="fields"
      :expr="exprDialogExpr"
      :trigger-fields="selectedRule?.conditions.map((c) => c.field).filter(Boolean) ?? []"
      @confirm="onExprConfirm"
    />

    <PageSelectDialog
      v-model:visible="showPageSelect"
      :exclude-page-id="designerStore.pageId"
      @select="onPageSelectConfirm"
    />

    <EventCodeDialog
      v-model:model-value="scriptDialogValue"
      v-model:visible="showScriptDialog"
      :title="t('脚本编辑')"
      @update:model-value="onScriptConfirm"
    />

    <template #footer>
      <ElButton @click="emit('update:modelValue', false)">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onConfirm">{{ t('确定') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.pr-dialog-body { display: flex; gap: 16px; min-height: 520px; }
.pr-left { flex: 0 0 220px; border-right: 1px solid #ebeef5; padding-right: 12px; display: flex; flex-direction: column; }
.pr-left-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.pr-left-title { font-size: 13px; font-weight: 600; color: #303133; }
.pr-rule-list { flex: 1; overflow-y: auto; }
.pr-rule-item { display: flex; align-items: center; gap: 6px; padding: 6px 8px; border-radius: 4px; cursor: pointer; }
.pr-rule-item:hover { background: #f5f7fa; }
.pr-rule-selected { background: #ecf5ff; }
.pr-rule-name { flex: 1; font-size: 13px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pr-rule-info { font-size: 11px; color: #909399; flex-shrink: 0; }
.pr-rule-empty { padding: 20px; text-align: center; color: #c0c4cc; font-size: 13px; }
.pr-right { flex: 1; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; }
.pr-right-empty { flex: 1; display: flex; align-items: center; justify-content: center; color: #c0c4cc; font-size: 14px; }
.pr-form-row { display: flex; align-items: center; gap: 8px; }
.pr-form-label { font-size: 13px; font-weight: 600; color: #303133; }
.pr-section { border: 1px solid #ebeef5; border-radius: 4px; padding: 10px; }
.pr-section-title { font-size: 12px; font-weight: 600; color: #606266; margin-bottom: 8px; }
.pr-section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.pr-cond-list { display: flex; flex-direction: column; gap: 6px; }
.pr-cond-row { display: flex; align-items: center; gap: 6px; }
.pr-cond-field { width: 130px; padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; }
.pr-cond-op { width: 90px; padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; }
.pr-cond-del { padding: 3px 8px; font-size: 14px; color: #f56c6c; background: #fef0f0; border: 1px solid #fbc4c4; border-radius: 3px; cursor: pointer; }
.pr-between-sep { font-size: 12px; color: #909399; flex-shrink: 0; }
.pr-multi-btn { padding: 3px 8px; font-size: 12px; color: #409eff; background: #ecf5ff; border: 1px solid #b3d8ff; border-radius: 3px; cursor: pointer; white-space: nowrap; }
.pr-action-list { display: flex; flex-direction: column; gap: 6px; }
.pr-action-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 6px; border: 1px solid #f0f0f0; border-radius: 4px; }
.pr-action-index { font-size: 12px; color: #909399; width: 20px; }
.pr-action-type { width: 110px; padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; }
.pr-action-field { width: 120px; padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; }
.pr-bool-btn { padding: 3px 10px; font-size: 12px; border: 1px solid #dcdfe6; border-radius: 4px; cursor: pointer; background: #fff; color: #606266; }
.pr-bool-on { background: #f0f9eb; border-color: #b3e19c; color: #67c23a; }
.pr-bool-off { background: #fef0f0; border-color: #fbc4c4; color: #f56c6c; }
.pr-expr-input { flex: 1; min-width: 100px; padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; font-family: 'Consolas', monospace; color: #606266; }
.pr-prop-path { width: 110px; padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; }
.pr-color-input { width: 40px; height: 28px; padding: 2px; border: 1px solid #dcdfe6; border-radius: 4px; cursor: pointer; }
.pr-ds-url { flex: 1; min-width: 160px; padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; }
.pr-api-method { width: 70px; padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; }
.pr-target-edit { padding: 3px 8px; font-size: 12px; color: #409eff; background: #ecf5ff; border: 1px solid #b3d8ff; border-radius: 3px; cursor: pointer; white-space: nowrap; }
.pr-action-del { padding: 3px 8px; font-size: 14px; color: #f56c6c; background: #fef0f0; border: 1px solid #fbc4c4; border-radius: 3px; cursor: pointer; }
.pr-action-empty { padding: 12px; text-align: center; color: #c0c4cc; font-size: 13px; }
.pr-multi-value-dialog :deep(.el-dialog__body) { padding: 12px 20px; }
.pr-multi-value-table-wrapper { border: 1px solid #e4e7ed; border-radius: 4px; overflow: hidden; }
.pr-multi-value-table-header { display: flex; align-items: center; background: #f5f7fa; border-bottom: 1px solid #e4e7ed; padding: 6px 8px; font-size: 13px; font-weight: 500; color: #606266; }
.pr-multi-value-col-field { flex: 1; }
.pr-multi-value-col-op { width: 50px; text-align: center; }
.pr-multi-value-table-body { max-height: 400px; overflow-y: auto; }
.pr-multi-value-row { display: flex; align-items: center; padding: 2px 8px; border-bottom: 1px solid #f0f0f0; }
.pr-multi-value-row-input { flex: 1; }
.pr-multi-value-row-del { width: 24px; height: 24px; border: none; background: none; cursor: pointer; color: #c0c4cc; font-size: 16px; line-height: 1; flex-shrink: 0; margin-left: 4px; }
.pr-multi-value-row-del:hover { color: #f56c6c; }
</style>