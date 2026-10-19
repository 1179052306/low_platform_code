<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElButton, ElInput, ElSwitch, ElMessage, ElMessageBox } from 'element-plus'
import { t } from '@/store/lang'
import ExprDialog from './ExprDialog.vue'
import type { ComputeRule, ComputeRuleTarget } from '@/types/schema'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'

const props = defineProps<{
  modelValue: boolean
  fields: string[]
  rules: ComputeRule[]
}>()

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  confirm: [rules: ComputeRule[]]
}>()

const innerRules = ref<ComputeRule[]>([])
const selectedRuleId = ref('')
const showExprDialog = ref(false)
const editingTargetIndex = ref(-1)

const selectedRule = ref<ComputeRule | null>(null)

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      innerRules.value = JSON.parse(JSON.stringify(props.rules ?? []))
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
  const id = `rule_${Date.now().toString(36)}`
  const rule: ComputeRule = { id, name: t('新规则'), triggerFields: [], targets: [], enabled: true }
  innerRules.value.push(rule)
  selectedRuleId.value = id
  syncSelectedRule()
}

function deleteRule(rule: ComputeRule) {
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

function addTarget() {
  if (!selectedRule.value) return
  const target: ComputeRuleTarget = { fieldName: '', expr: '' }
  selectedRule.value.targets.push(target)
}

function removeTarget(idx: number) {
  if (!selectedRule.value) return
  selectedRule.value.targets.splice(idx, 1)
}

function openExprDialog(idx: number) {
  editingTargetIndex.value = idx
  showExprDialog.value = true
}

function onExprConfirm(expr: string, triggerFields: string[]) {
  if (!selectedRule.value || editingTargetIndex.value < 0) return
  const target = selectedRule.value.targets[editingTargetIndex.value]
  if (target) {
    target.expr = expr
    for (const tf of triggerFields) {
      if (!selectedRule.value.triggerFields.includes(tf)) {
        selectedRule.value.triggerFields.push(tf)
      }
    }
  }
}

function toggleTrigger(field: string) {
  if (!selectedRule.value) return
  const idx = selectedRule.value.triggerFields.indexOf(field)
  if (idx >= 0) selectedRule.value.triggerFields.splice(idx, 1)
  else selectedRule.value.triggerFields.push(field)
}

function isTrigger(field: string): boolean {
  return selectedRule.value?.triggerFields.includes(field) ?? false
}

/** 检测规则间是否存在循环依赖（规则A的目标字段是规则B的触发字段，且反过来也成立） */
function detectCircularDependency(rules: ComputeRule[]): string | null {
  const n = rules.length
  const graph: number[][] = Array.from({ length: n }, () => [])
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      if (i === j) continue
      const aTargets = rules[i].targets.map((t) => t.fieldName)
      const bTriggers = rules[j].triggerFields
      if (aTargets.some((f) => bTriggers.includes(f))) {
        graph[i].push(j)
      }
    }
  }
  const visited = new Array(n).fill(0)
  function dfs(u: number, path: number[]): boolean {
    visited[u] = 1
    path.push(u)
    for (const v of graph[u]) {
      if (visited[v] === 1) {
        path.push(v)
        return true
      }
      if (visited[v] === 0 && dfs(v, path)) return true
    }
    visited[u] = 2
    path.pop()
    return false
  }
  for (let i = 0; i < n; i++) {
    if (visited[i] === 0) {
      const path: number[] = []
      if (dfs(i, path)) {
        const cycle = path.map((idx) => rules[idx].name).join(' → ')
        return cycle
      }
    }
  }
  return null
}

function onConfirm() {
  for (const r of innerRules.value) {
    if (!r.name.trim()) { ElMessage.warning(t('规则名称不能为空')); return }
    if (!r.triggerFields.length) { ElMessage.warning(t('规则') + r.name + t('未选择触发字段')); return }
    if (!r.targets.length) { ElMessage.warning(t('规则') + r.name + t('未配置目标字段')); return }
    for (const tgt of r.targets) {
      if (!tgt.fieldName || !tgt.expr) { ElMessage.warning(t('目标字段和表达式不能为空')); return }
    }
  }
  const cycle = detectCircularDependency(innerRules.value)
  if (cycle) {
    ElMessage.error(t('检测到规则循环依赖') + '：' + cycle + t('，请调整规则配置'))
    return
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
    width="900px"
    append-to-body
    draggable
    :fullscreen="isMaximized"
    :close-on-click-modal="false"
  >
    <template #header>
      <DialogMaxHeader :title="t('计算规则')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <div class="cr-dialog-body">
      <div class="cr-left">
        <div class="cr-left-header">
          <span class="cr-left-title">{{ t('规则列表') }}</span>
          <ElButton type="primary" size="small" @click="addRule">+ {{ t('新建') }}</ElButton>
        </div>
        <div class="cr-rule-list">
          <div
            v-for="r in innerRules"
            :key="r.id"
            class="cr-rule-item"
            :class="{ 'cr-rule-selected': r.id === selectedRuleId }"
            @click="selectRule(r.id)"
          >
            <span class="cr-rule-name">{{ r.name }}</span>
            <span class="cr-rule-info">{{ r.triggerFields.length }} {{ t('触发') }} / {{ r.targets.length }} {{ t('目标') }}</span>
            <ElButton type="danger" link size="small" @click.stop="deleteRule(r)">×</ElButton>
          </div>
          <div v-if="!innerRules.length" class="cr-rule-empty">{{ t('暂无规则，点击新建') }}</div>
        </div>
      </div>

      <div class="cr-right" v-if="selectedRule">
        <div class="cr-form-row">
          <label class="cr-form-label">{{ t('规则名称') }}</label>
          <ElInput v-model="selectedRule.name" size="small" style="width: 240px" />
          <label class="cr-form-label" style="margin-left: 16px">{{ t('启用') }}</label>
          <ElSwitch v-model="selectedRule.enabled" />
        </div>

        <div class="cr-section">
          <div class="cr-section-title">{{ t('触发字段') }}（{{ t('勾选哪些字段变更时触发本规则') }}）</div>
          <div class="cr-field-grid">
            <label v-for="f in fields" :key="f" class="cr-field-check" :class="{ 'cr-field-checked': isTrigger(f) }">
              <input type="checkbox" :checked="isTrigger(f)" @change="toggleTrigger(f)" />
              <span>{{ f }}</span>
            </label>
          </div>
        </div>

        <div class="cr-section">
          <div class="cr-section-header">
            <span class="cr-section-title">{{ t('目标字段') }}（{{ t('触发时同时计算以下字段') }}）</span>
            <ElButton type="primary" size="small" @click="addTarget">+ {{ t('新增目标') }}</ElButton>
          </div>
          <div class="cr-target-list">
            <div v-for="(tgt, idx) in selectedRule.targets" :key="idx" class="cr-target-row">
              <select class="cr-target-field" v-model="tgt.fieldName">
                <option value="">{{ t('选择字段') }}</option>
                <option v-for="f in fields" :key="f" :value="f">{{ f }}</option>
              </select>
              <input class="cr-target-expr" type="text" v-model="tgt.expr" :placeholder="t('表达式')" readonly @click="openExprDialog(idx)" />
              <button class="cr-target-edit" @click="openExprDialog(idx)">{{ t('编辑') }}</button>
              <button class="cr-target-del" @click="removeTarget(idx)">×</button>
            </div>
            <div v-if="!selectedRule.targets.length" class="cr-target-empty">{{ t('暂无目标字段') }}</div>
          </div>
        </div>
      </div>
      <div class="cr-right-empty" v-else>
        {{ t('请从左侧选择或新建规则') }}
      </div>
    </div>

    <ExprDialog
      v-model="showExprDialog"
      :fields="fields"
      :expr="selectedRule && editingTargetIndex >= 0 ? selectedRule.targets[editingTargetIndex]?.expr ?? '' : ''"
      :trigger-fields="selectedRule?.triggerFields ?? []"
      @confirm="onExprConfirm"
    />

    <template #footer>
      <ElButton @click="emit('update:modelValue', false)">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onConfirm">{{ t('确定') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.cr-dialog-body { display: flex; gap: 16px; min-height: 480px; }
.cr-left { flex: 0 0 240px; border-right: 1px solid #ebeef5; padding-right: 12px; display: flex; flex-direction: column; }
.cr-left-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.cr-left-title { font-size: 13px; font-weight: 600; color: #303133; }
.cr-rule-list { flex: 1; overflow-y: auto; }
.cr-rule-item { display: flex; align-items: center; gap: 6px; padding: 6px 8px; border-radius: 4px; cursor: pointer; }
.cr-rule-item:hover { background: #f5f7fa; }
.cr-rule-selected { background: #ecf5ff; }
.cr-rule-name { flex: 1; font-size: 13px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cr-rule-info { font-size: 11px; color: #909399; flex-shrink: 0; }
.cr-rule-empty { padding: 20px; text-align: center; color: #c0c4cc; font-size: 13px; }
.cr-right { flex: 1; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; }
.cr-right-empty { flex: 1; display: flex; align-items: center; justify-content: center; color: #c0c4cc; font-size: 14px; }
.cr-form-row { display: flex; align-items: center; gap: 8px; }
.cr-form-label { font-size: 13px; font-weight: 600; color: #303133; }
.cr-section { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px; }
.cr-section-title { font-size: 12px; font-weight: 600; color: #606266; margin-bottom: 8px; }
.cr-section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.cr-field-grid { display: flex; flex-wrap: wrap; gap: 6px; max-height: 120px; overflow-y: auto; }
.cr-field-check { display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: 4px; cursor: pointer; font-size: 12px; border: 1px solid #dcdfe6; }
.cr-field-check:hover { background: #f5f7fa; }
.cr-field-checked { background: #f0f9eb; border-color: #b3e19c; color: #67c23a; }
.cr-target-list { display: flex; flex-direction: column; gap: 6px; }
.cr-target-row { display: flex; align-items: center; gap: 6px; }
.cr-target-field { width: 140px; padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; }
.cr-target-expr { flex: 1; padding: 4px 6px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 12px; font-family: 'Consolas', monospace; cursor: pointer; color: #606266; }
.cr-target-expr:hover { border-color: #409eff; }
.cr-target-edit { padding: 3px 8px; font-size: 12px; color: #409eff; background: #ecf5ff; border: 1px solid #b3d8ff; border-radius: 3px; cursor: pointer; }
.cr-target-del { padding: 3px 8px; font-size: 14px; color: #f56c6c; background: #fef0f0; border: 1px solid #fbc4c4; border-radius: 3px; cursor: pointer; }
.cr-target-empty { padding: 16px; text-align: center; color: #c0c4cc; font-size: 13px; }
</style>