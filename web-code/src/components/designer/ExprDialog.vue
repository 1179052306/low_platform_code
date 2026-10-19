<script setup lang="ts">
import { ref, watch, computed, nextTick } from 'vue'
import { ElDialog, ElButton, ElInput, ElMessage, ElTabs, ElTabPane } from 'element-plus'
import { t } from '@/store/lang'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'

const props = defineProps<{
  modelValue: boolean
  fields: string[]
  expr: string
  triggerFields: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  confirm: [expr: string, triggerFields: string[]]
}>()

const innerExpr = ref('')
const innerTriggerFields = ref<string[]>([])
const searchText = ref('')
const activeTab = ref('field')
const exprTextareaRef = ref<HTMLTextAreaElement | null>(null)

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      innerExpr.value = props.expr ?? ''
      innerTriggerFields.value = [...(props.triggerFields ?? [])]
      searchText.value = ''
      activeTab.value = 'field'
    }
  },
)

const filteredFields = computed(() => {
  const kw = searchText.value.trim().toLowerCase()
  if (!kw) return props.fields
  return props.fields.filter((f) => f.toLowerCase().includes(kw))
})

const OPERATORS = [
  '+', '-', '*', '/', '=', '<>', '<', '<=', '>', '>=', '(', ')',
  'AND', 'OR', 'NOT', 'IN', 'NOT IN', 'LIKE', 'NOT LIKE',
]

function insertText(text: string): void {
  const ta = exprTextareaRef.value
  if (!ta) {
    innerExpr.value += text
    return
  }
  const start = ta.selectionStart
  const end = ta.selectionEnd
  innerExpr.value = innerExpr.value.slice(0, start) + text + innerExpr.value.slice(end)
  nextTick(() => {
    const pos = start + text.length
    ta.focus()
    ta.setSelectionRange(pos, pos)
  })
}

function insertField(field: string): void {
  insertText(field)
}

function insertOperator(op: string): void {
  const needSpace = /[a-zA-Z]/.test(op)
  const prefix = needSpace ? ' ' : ''
  const suffix = needSpace ? ' ' : ''
  insertText(prefix + op + suffix)
}

function backspace(): void {
  const ta = exprTextareaRef.value
  if (!ta) {
    innerExpr.value = innerExpr.value.slice(0, -1)
    return
  }
  const start = ta.selectionStart
  const end = ta.selectionEnd
  if (start !== end) {
    innerExpr.value = innerExpr.value.slice(0, start) + innerExpr.value.slice(end)
  } else if (start > 0) {
    innerExpr.value = innerExpr.value.slice(0, start - 1) + innerExpr.value.slice(end)
  }
  nextTick(() => {
    const pos = start > 0 ? start - 1 : 0
    ta.focus()
    ta.setSelectionRange(pos, pos)
  })
}

function basicCheck(): void {
  const expr = innerExpr.value.trim()
  if (!expr) {
    ElMessage.warning(t('表达式不能为空'))
    return
  }
  let parenDepth = 0
  for (const ch of expr) {
    if (ch === '(') parenDepth++
    if (ch === ')') parenDepth--
    if (parenDepth < 0) {
      ElMessage.error(t('括号不匹配：右括号多余'))
      return
    }
  }
  if (parenDepth !== 0) {
    ElMessage.error(t('括号不匹配：左括号多余'))
    return
  }
  const quoteCount = (expr.match(/"/g) || []).length
  if (quoteCount % 2 !== 0) {
    ElMessage.error(t('双引号不匹配'))
    return
  }
  ElMessage.success(t('基本检查通过'))
}

function toggleTriggerField(field: string): void {
  const idx = innerTriggerFields.value.indexOf(field)
  if (idx >= 0) {
    innerTriggerFields.value.splice(idx, 1)
  } else {
    innerTriggerFields.value.push(field)
  }
}

function isTriggerField(field: string): boolean {
  return innerTriggerFields.value.includes(field)
}

function onConfirm(): void {
  emit('confirm', innerExpr.value.trim(), innerTriggerFields.value)
  emit('update:modelValue', false)
}

function onCancel(): void {
  emit('update:modelValue', false)
}

const { isMaximized, toggleMaximize, resetDialog } = useDialogDragAndMax()
watch(() => props.modelValue, (v) => { if (!v) resetDialog() })
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    width="780px"
    append-to-body
    :close-on-click-modal="false"
    draggable
    :fullscreen="isMaximized"
  >
    <template #header>
      <DialogMaxHeader :title="t('公式设置')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <div class="expr-dialog-body">
      <div class="expr-left">
        <ElTabs v-model="activeTab" class="expr-tabs">
          <ElTabPane :label="t('字段')" name="field">
            <ElInput
              v-model="searchText"
              :placeholder="t('搜索字段名称')"
              size="small"
              clearable
              class="expr-search"
            />
            <div class="expr-field-list">
              <div
                v-for="f in filteredFields"
                :key="f"
                class="expr-field-item"
                :class="{ 'expr-field-trigger': isTriggerField(f) }"
              >
                <label class="expr-field-check" @click.stop>
                  <input
                    type="checkbox"
                    :checked="isTriggerField(f)"
                    @change="toggleTriggerField(f)"
                  />
                </label>
                <span class="expr-field-name" @click="insertField(f)">{{ f }}</span>
                <span class="expr-field-tag" v-if="isTriggerField(f)">{{ t('触发') }}</span>
              </div>
              <div v-if="!filteredFields.length" class="expr-field-empty">
                {{ t('无匹配字段') }}
              </div>
            </div>
          </ElTabPane>
          <ElTabPane :label="t('变量')" name="variable">
            <div class="expr-field-list">
              <div class="expr-field-item" @click="insertField('currentUser')">
                <span class="expr-field-icon">V</span>
                <span class="expr-field-name">currentUser</span>
              </div>
              <div class="expr-field-item" @click="insertField('currentDate')">
                <span class="expr-field-icon">V</span>
                <span class="expr-field-name">currentDate</span>
              </div>
              <div class="expr-field-item" @click="insertField('currentDept')">
                <span class="expr-field-icon">V</span>
                <span class="expr-field-name">currentDept</span>
              </div>
            </div>
          </ElTabPane>
        </ElTabs>
      </div>

      <div class="expr-right">
        <div class="expr-label">{{ t('表达式') }}</div>
        <textarea
          ref="exprTextareaRef"
          v-model="innerExpr"
          class="expr-textarea"
          :placeholder="t('请编辑表达式，如 unitPrice * quantity')"
          rows="6"
        ></textarea>

        <div class="expr-operators">
          <button
            v-for="op in OPERATORS"
            :key="op"
            class="expr-op-btn"
            @click="insertOperator(op)"
          >{{ op }}</button>
          <button class="expr-op-btn expr-op-backspace" @click="backspace">Backspace</button>
        </div>

        <div class="expr-check-row">
          <ElButton size="small" @click="basicCheck">{{ t('基本检查') }}</ElButton>
        </div>

        <div class="expr-trigger-summary">
          <div class="expr-trigger-label">{{ t('触发字段') }}（{{ t('左侧勾选') }}）</div>
          <div class="expr-trigger-tags">
            <span v-if="!innerTriggerFields.length" class="expr-trigger-none">{{ t('未选择，表达式不会自动触发计算') }}</span>
            <span
              v-for="f in innerTriggerFields"
              :key="f"
              class="expr-trigger-tag"
            >{{ f }} <i @click="toggleTriggerField(f)">×</i></span>
          </div>
        </div>

        <div class="expr-tips">
          <div>1. {{ t('可以从左侧插入字段。') }}</div>
          <div>2. {{ t('请在英文输入法下使用特殊符号等内容。') }}</div>
          <div>3. {{ t('文本内容请用英文双引号。') }}</div>
        </div>
      </div>
    </div>

    <template #footer>
      <ElButton @click="onCancel">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onConfirm">{{ t('确定') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.expr-dialog-body {
  display: flex;
  gap: 16px;
  min-height: 420px;
}
.expr-left {
  flex: 0 0 260px;
  border-right: 1px solid #ebeef5;
  padding-right: 16px;
}
.expr-tabs :deep(.el-tabs__content) {
  max-height: 380px;
  overflow-y: auto;
}
.expr-search {
  margin-bottom: 8px;
}
.expr-field-list {
  max-height: 320px;
  overflow-y: auto;
}
.expr-field-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
}
.expr-field-item:hover {
  background: #ecf5ff;
}
.expr-field-trigger {
  background: #f0f9eb;
}
.expr-field-check {
  flex-shrink: 0;
  cursor: pointer;
}
.expr-field-check input {
  cursor: pointer;
}
.expr-field-name {
  font-size: 13px;
  color: #303133;
  flex: 1;
  cursor: pointer;
}
.expr-field-tag {
  font-size: 11px;
  color: #67c23a;
  background: #f0f9eb;
  border: 1px solid #b3e19c;
  border-radius: 3px;
  padding: 0 4px;
  flex-shrink: 0;
}
.expr-trigger-summary {
  margin-top: 10px;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 4px;
}
.expr-trigger-label {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 4px;
}
.expr-trigger-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.expr-trigger-none {
  font-size: 12px;
  color: #c0c4cc;
}
.expr-trigger-tag {
  font-size: 12px;
  color: #409eff;
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 3px;
  padding: 2px 6px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.expr-trigger-tag i {
  cursor: pointer;
  font-style: normal;
  color: #c0c4cc;
  font-size: 14px;
}
.expr-trigger-tag i:hover {
  color: #f56c6c;
}
.expr-field-empty {
  padding: 20px 8px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.expr-right {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.expr-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.expr-textarea {
  width: 100%;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 14px;
  padding: 8px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
}
.expr-textarea:focus {
  border-color: #409eff;
}
.expr-operators {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 10px;
}
.expr-op-btn {
  padding: 3px 8px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  color: #303133;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.15s;
}
.expr-op-btn:hover {
  color: #409eff;
  border-color: #409eff;
  background: #ecf5ff;
}
.expr-op-backspace {
  margin-left: 8px;
}
.expr-check-row {
  margin-top: 10px;
}
.expr-tips {
  margin-top: 10px;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 4px;
}
.expr-tips div {
  font-size: 12px;
  color: #909399;
  line-height: 1.8;
}
</style>