<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import {
  ElDialog,
  ElButton,
  ElInput,
  ElInputNumber,
  ElSelect,
  ElOption,
  ElSwitch,
  ElMessage,
} from 'element-plus'
import { t } from '@/store/lang'
import type {
  NumberRuleSegment,
  NumberRuleSegmentType,
  NumberRuleConfig,
  NumberRuleUsageMode,
  NumberRuleSeqScope,
} from '@/types/schema'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'

const props = defineProps<{
  visible: boolean
  /** 当前编号规则配置（JSON 字符串） */
  expr?: string
  /** 日期/时间类型表单字段列表（供业务对象日期选择） */
  dateFieldNames?: string[]
  /** 非日期/时间类型表单字段列表（供业务对象字段选择） */
  nonDateFieldNames?: string[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [expr: string]
}>()

/** 编码段列表 */
const segments = ref<NumberRuleSegment[]>([])

/** 字段选择弹窗 */
const showFieldPicker = ref(false)
const pickingFieldIndex = ref(-1)
/** 字段选择弹窗搜索关键词 */
const fieldSearchKeyword = ref('')

/** 当前字段选择弹窗使用的字段列表（根据段类型动态选择） */
const currentPickerFields = computed(() => {
  if (pickingFieldIndex.value < 0) return []
  const seg = segments.value[pickingFieldIndex.value]
  if (!seg) return []
  if (seg.type === 'bizDate') {
    return props.dateFieldNames || []
  }
  if (seg.type === 'bizField') {
    return props.nonDateFieldNames || []
  }
  return []
})

/** 搜索过滤后的字段列表 */
const filteredPickerFields = computed(() => {
  const kw = fieldSearchKeyword.value.trim().toLowerCase()
  if (!kw) return currentPickerFields.value
  return currentPickerFields.value.filter((f) => f.toLowerCase().includes(kw))
})

/** 段类型选项 */
const segmentTypeOptions: { label: string; value: NumberRuleSegmentType }[] = [
  { label: '常量', value: 'constant' },
  { label: '随机码', value: 'random' },
  { label: '系统日期', value: 'sysDate' },
  { label: '业务对象日期', value: 'bizDate' },
  { label: '业务对象字段', value: 'bizField' },
  { label: '流水号', value: 'sequence' },
]

/** 日期格式选项 */
const dateFormatOptions = [
  { label: 'yyyyMMdd', value: 'yyyyMMdd' },
  { label: 'yyyy-MM-dd', value: 'yyyy-MM-dd' },
  { label: 'yyyyMM', value: 'yyyyMM' },
  { label: 'yyyy', value: 'yyyy' },
]

/** 使用模式选项 */
const usageModeOptions: { label: string; value: NumberRuleUsageMode }[] = [
  { label: '完全取值', value: 'full' },
  { label: '取后几位', value: 'suffix' },
  { label: '取前几位', value: 'prefix' },
]

/** 流水号重置周期选项 */
const seqScopeOptions: { label: string; value: NumberRuleSeqScope }[] = [
  { label: '每日', value: 'DAILY' },
  { label: '每月', value: 'MONTHLY' },
  { label: '每年', value: 'YEARLY' },
  { label: '全局', value: 'GLOBAL' },
]

/** 分隔符选项（完善：空 + 常见符号） */
const separatorOptions = [
  { label: '空', value: '' },
  { label: '- （连字符）', value: '-' },
  { label: '_ （下划线）', value: '_' },
  { label: '/ （斜杠）', value: '/' },
  { label: '. （点）', value: '.' },
  { label: '@', value: '@' },
  { label: '#', value: '#' },
  { label: '$', value: '$' },
  { label: '%', value: '%' },
  { label: '^', value: '^' },
  { label: '&', value: '&' },
  { label: '*', value: '*' },
  { label: '[', value: '[' },
  { label: ']', value: ']' },
  { label: '~', value: '~' },
  { label: '+', value: '+' },
  { label: '= （等号）', value: '=' },
  { label: '| （竖线）', value: '|' },
  { label: ': （冒号）', value: ':' },
  { label: '; （分号）', value: ';' },
]

/** 段类型中文标签 */
function segmentTypeLabel(type: NumberRuleSegmentType): string {
  return segmentTypeOptions.find((o) => o.value === type)?.label || type
}

/** 中文数字 */
const cnNums = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']

/** 解析 JSON 配置 */
function parseExpr(expr: string): void {
  if (!expr) {
    segments.value = []
    return
  }
  try {
    const config = JSON.parse(expr) as NumberRuleConfig
    if (config && Array.isArray(config.segments)) {
      segments.value = config.segments
      return
    }
  } catch {
    /* 旧格式表达式，忽略 */
  }
  segments.value = []
}

/** 生成 JSON 配置字符串 */
const generatedExpr = computed(() => {
  return JSON.stringify({ segments: segments.value })
})

/** 生成预览示例 */
const previewExample = computed(() => {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const parts: string[] = []
  for (let idx = 0; idx < segments.value.length; idx++) {
    const seg = segments.value[idx]
    if (idx > 0 && seg.separator) {
      parts.push(seg.separator)
    }
    let part = ''
    switch (seg.type) {
      case 'constant':
        part = seg.value || ''
        break
      case 'random': {
        const len = seg.length || 8
        part = 'X'.repeat(len)
        break
      }
      case 'sysDate': {
        const fmt = seg.dateFormat || 'yyyyMMdd'
        if (fmt === 'yyyyMMdd') part = `${y}${m}${d}`
        else if (fmt === 'yyyy-MM-dd') part = `${y}-${m}-${d}`
        else if (fmt === 'yyyyMM') part = `${y}${m}`
        else if (fmt === 'yyyy') part = `${y}`
        break
      }
      case 'bizDate':
        part = seg.dateFormat || 'yyyyMMdd'
        break
      case 'bizField': {
        const field = seg.fieldName || 'field'
        if (seg.usageMode === 'suffix') {
          const tl = seg.takeLength || 4
          part = `${field}（取后${tl}位）`
        } else if (seg.usageMode === 'prefix') {
          const tl = seg.takeLength || 4
          part = `${field}（取前${tl}位）`
        } else {
          part = field
        }
        break
      }
      case 'sequence': {
        const len = seg.seqLength || 4
        const start = seg.seqStart || 1
        part = seg.seqZeroPad !== false ? String(start).padStart(len, '0') : String(start)
        break
      }
    }
    parts.push(part)
  }
  return parts.join('')
})

watch(
  () => props.visible,
  (v) => {
    if (v) parseExpr(props.expr || '')
  },
  { immediate: true },
)

/** 新增段 */
function addSegment() {
  segments.value.push({
    type: 'constant',
    value: '',
    separator: '-',
  })
}

/** 删除段 */
function removeSegment(idx: number) {
  segments.value.splice(idx, 1)
}

/** 段类型变化时初始化默认值 */
function onTypeChange(idx: number, type: NumberRuleSegmentType) {
  const seg = segments.value[idx]
  seg.type = type
  switch (type) {
    case 'constant':
      seg.value = seg.value || ''
      break
    case 'random':
      seg.length = seg.length || 8
      break
    case 'sysDate':
      seg.dateFormat = seg.dateFormat || 'yyyyMMdd'
      break
    case 'bizDate':
      seg.dateFormat = seg.dateFormat || 'yyyyMMdd'
      seg.fieldName = seg.fieldName || ''
      break
    case 'bizField':
      seg.usageMode = seg.usageMode || 'full'
      seg.fieldName = seg.fieldName || ''
      break
    case 'sequence':
      seg.seqLength = seg.seqLength || 4
      seg.seqScope = seg.seqScope || 'DAILY'
      seg.seqStart = seg.seqStart || 1
      seg.seqStep = seg.seqStep || 1
      seg.seqAutoExpand = seg.seqAutoExpand ?? false
      seg.seqGapFill = seg.seqGapFill ?? false
      seg.seqZeroPad = seg.seqZeroPad ?? true
      break
  }
}

/** 打开字段选择弹窗 */
function openFieldPicker(idx: number) {
  pickingFieldIndex.value = idx
  fieldSearchKeyword.value = ''
  showFieldPicker.value = true
}

/** 选择字段后回填 */
function pickField(fieldName: string) {
  if (pickingFieldIndex.value >= 0) {
    segments.value[pickingFieldIndex.value].fieldName = fieldName
  }
  showFieldPicker.value = false
}

function onSave() {
  if (segments.value.length === 0) {
    ElMessage.warning(t('请至少添加一个编码段'))
    return
  }
  emit('save', generatedExpr.value)
  emit('update:visible', false)
}

function onClose() {
  emit('update:visible', false)
}

const { isMaximized, toggleMaximize, resetDialog } = useDialogDragAndMax()
watch(() => props.visible, (v) => { if (!v) resetDialog() })
const { isMaximized: fieldPickerMax, toggleMaximize: toggleFieldPickerMax, resetDialog: resetFieldPickerDrag } = useDialogDragAndMax()
watch(() => showFieldPicker.value, (v) => { if (!v) resetFieldPickerDrag() })
</script>

<template>
  <ElDialog
    :model-value="visible"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    width="960px"
    append-to-body
    draggable
    :fullscreen="isMaximized"
    class="number-rule-dialog"
  >
    <template #header>
      <DialogMaxHeader :title="t('编码设置')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <div class="nr-body">
      <!-- 编码示例 -->
      <div class="nr-preview-section">
        <div class="nr-preview-label">{{ t('编码示例') }}</div>
        <div class="nr-preview-value">
          <code v-if="previewExample">{{ previewExample }}</code>
          <span v-else class="nr-preview-empty">{{ t('请添加编码段') }}</span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="nr-actions">
        <ElButton type="primary" size="small" @click="addSegment">
          {{ t('新增') }}
        </ElButton>
        <ElButton
          size="small"
          :disabled="segments.length === 0"
          @click="() => { if (segments.length > 0) removeSegment(segments.length - 1) }"
        >
          {{ t('删除') }}
        </ElButton>
      </div>

      <!-- 编码段卡片 -->
      <div class="nr-segments">
        <div
          v-for="(seg, idx) in segments"
          :key="idx"
          class="nr-segment-card"
        >
          <div class="nr-segment-header">
            <span class="nr-segment-title">{{ t('编码') }}{{ cnNums[idx] || (idx + 1) }}{{ t('段') }}</span>
            <span class="nr-segment-type-badge">{{ segmentTypeLabel(seg.type) }}</span>
            <ElButton
              type="danger"
              link
              size="small"
              class="nr-segment-delete"
              @click="removeSegment(idx)"
            >{{ t('删除') }}</ElButton>
          </div>

          <div class="nr-segment-body">
            <!-- 段类型 -->
            <div class="nr-field">
              <label class="nr-field-label">{{ t('类型') }}</label>
              <ElSelect
                :model-value="seg.type"
                size="small"
                style="width: 140px"
                @update:model-value="(v: NumberRuleSegmentType) => onTypeChange(idx, v)"
              >
                <ElOption
                  v-for="opt in segmentTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </div>

            <!-- 常量：设置值 -->
            <div v-if="seg.type === 'constant'" class="nr-field">
              <label class="nr-field-label nr-required">{{ t('设置值') }}</label>
              <ElInput
                v-model="seg.value"
                size="small"
                style="width: 140px"
                :placeholder="t('如 1 / PO')"
              />
            </div>

            <!-- 随机码：长度 -->
            <div v-if="seg.type === 'random'" class="nr-field">
              <label class="nr-field-label">{{ t('长度') }}</label>
              <ElInputNumber
                v-model="seg.length"
                size="small"
                :min="1"
                :max="32"
                style="width: 100px"
              />
            </div>

            <!-- 系统日期/业务对象日期：显示格式 -->
            <template v-if="seg.type === 'sysDate' || seg.type === 'bizDate'">
              <div class="nr-field">
                <label class="nr-field-label nr-required">{{ t('显示格式') }}</label>
                <ElSelect
                  v-model="seg.dateFormat"
                  size="small"
                  style="width: 140px"
                >
                  <ElOption
                    v-for="opt in dateFormatOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </ElSelect>
              </div>
              <!-- 业务对象日期：字段选择 -->
              <div v-if="seg.type === 'bizDate'" class="nr-field">
                <label class="nr-field-label nr-required">{{ t('日期字段') }}</label>
                <ElInput
                  :model-value="seg.fieldName || ''"
                  size="small"
                  style="width: 140px"
                  readonly
                  :placeholder="t('点击选择字段')"
                  @click="openFieldPicker(idx)"
                />
              </div>
            </template>

            <!-- 业务对象字段：使用模式 + 编码来源（弹窗选择） -->
            <template v-if="seg.type === 'bizField'">
              <div class="nr-field">
                <label class="nr-field-label nr-required">{{ t('使用模式') }}</label>
                <ElSelect
                  v-model="seg.usageMode"
                  size="small"
                  style="width: 120px"
                >
                  <ElOption
                    v-for="opt in usageModeOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </ElSelect>
              </div>
              <div class="nr-field">
                <label class="nr-field-label nr-required">{{ t('编码来源') }}</label>
                <ElInput
                  :model-value="seg.fieldName || ''"
                  size="small"
                  style="width: 160px"
                  readonly
                  :placeholder="t('点击选择字段')"
                  @click="openFieldPicker(idx)"
                />
              </div>
              <div v-if="seg.usageMode === 'suffix' || seg.usageMode === 'prefix'" class="nr-field">
                <label class="nr-field-label">{{ t('取值位数') }}</label>
                <ElInputNumber
                  v-model="seg.takeLength"
                  size="small"
                  :min="1"
                  :max="20"
                  style="width: 100px"
                />
              </div>
            </template>

            <!-- 流水号：增强配置 -->
            <template v-if="seg.type === 'sequence'">
              <div class="nr-field">
                <label class="nr-field-label">{{ t('长度') }}</label>
                <ElInputNumber
                  v-model="seg.seqLength"
                  size="small"
                  :min="1"
                  :max="20"
                  style="width: 80px"
                />
              </div>
              <div class="nr-field">
                <label class="nr-field-label">{{ t('起始值') }}*</label>
                <ElInputNumber
                  v-model="seg.seqStart"
                  size="small"
                  :min="1"
                  style="width: 80px"
                />
              </div>
              <div class="nr-field">
                <label class="nr-field-label">{{ t('步长') }}*</label>
                <ElInputNumber
                  v-model="seg.seqStep"
                  size="small"
                  :min="1"
                  style="width: 80px"
                />
              </div>
              <div class="nr-field">
                <label class="nr-field-label">{{ t('重置周期') }}</label>
                <ElSelect
                  v-model="seg.seqScope"
                  size="small"
                  style="width: 90px"
                >
                  <ElOption
                    v-for="opt in seqScopeOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </ElSelect>
              </div>
              <div class="nr-field nr-field-switch">
                <label class="nr-field-label">{{ t('超过长度自动升位') }}</label>
                <ElSwitch v-model="seg.seqAutoExpand" size="small" />
              </div>
              <div class="nr-field nr-field-switch">
                <label class="nr-field-label">{{ t('断号补偿') }}</label>
                <ElSwitch v-model="seg.seqGapFill" size="small" />
              </div>
              <div class="nr-field nr-field-switch">
                <label class="nr-field-label">{{ t('用0补位') }}</label>
                <ElSwitch v-model="seg.seqZeroPad" size="small" />
              </div>
            </template>

            <!-- 段间分隔符（仅非首段显示） -->
            <div v-if="idx > 0" class="nr-field">
              <label class="nr-field-label">{{ t('段间分隔符') }}</label>
              <ElSelect
                v-model="seg.separator"
                size="small"
                filterable
                style="width: 120px"
              >
                <ElOption
                  v-for="opt in separatorOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="segments.length === 0" class="nr-empty">
          {{ t('点击「新增」添加编码段') }}
        </div>
      </div>
    </div>

    <!-- 字段选择弹窗 -->
    <ElDialog
      v-model="showFieldPicker"
      width="520px"
      append-to-body
      draggable
      :fullscreen="fieldPickerMax"
    >
      <template #header>
        <DialogMaxHeader :title="t('选择字段')" :is-maximized="fieldPickerMax" @toggle="toggleFieldPickerMax" />
      </template>
      <div class="nr-field-picker">
        <div class="nr-field-picker-search">
          <input
            v-model="fieldSearchKeyword"
            class="nr-field-picker-input"
            :placeholder="t('搜索字段名')"
          />
        </div>
        <div class="nr-field-picker-list">
          <div
            v-for="fn in filteredPickerFields"
            :key="fn"
            class="nr-field-picker-item"
            @click="pickField(fn)"
          >{{ fn }}</div>
          <div v-if="filteredPickerFields.length === 0" class="nr-empty">
            {{ currentPickerFields.length === 0 ? t('当前页面无可用字段') : t('未找到匹配的字段') }}
          </div>
        </div>
      </div>
    </ElDialog>

    <template #footer>
      <ElButton @click="onClose">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onSave">{{ t('保存') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.nr-body {
  max-height: 560px;
  overflow-y: auto;
}
.nr-preview-section {
  margin-bottom: 12px;
  padding: 10px 14px;
  background: #f0f7ff;
  border-radius: 6px;
  border: 1px solid #d6e4ff;
}
.nr-preview-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.nr-preview-value code {
  font-size: 15px;
  font-weight: 600;
  color: #409eff;
  letter-spacing: 1px;
}
.nr-preview-empty {
  color: #c0c4cc;
  font-size: 13px;
}
.nr-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.nr-segments {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 4px;
}
.nr-segment-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
}
.nr-segment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}
.nr-segment-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.nr-segment-type-badge {
  font-size: 11px;
  color: #409eff;
  background: #ecf5ff;
  padding: 1px 8px;
  border-radius: 10px;
}
.nr-segment-delete {
  margin-left: auto;
}
.nr-segment-body {
  padding: 10px 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: flex-end;
}
.nr-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.nr-field-switch {
  flex-direction: row;
  align-items: center;
  gap: 6px;
}
.nr-field-label {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}
.nr-field-label.nr-required::after {
  content: ' *';
  color: #f56c6c;
}
.nr-empty {
  grid-column: 1 / -1;
  text-align: center;
  color: #c0c4cc;
  padding: 40px 0;
  font-size: 13px;
}
.nr-field-picker {
  max-height: 500px;
  display: flex;
  flex-direction: column;
}
.nr-field-picker-search {
  margin-bottom: 8px;
}
.nr-field-picker-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
}
.nr-field-picker-input:focus {
  border-color: #409eff;
}
.nr-field-picker-list {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.nr-field-picker-item {
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
}
.nr-field-picker-item:hover {
  background: #ecf5ff;
  color: #409eff;
}
</style>