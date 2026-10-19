<script setup lang="ts">
/**
 * ElFilterBuilder — 替换 DxFilterBuilder
 * 递归渲染 AND/OR 条件组与叶子条件。
 * value 结构（DevExtreme 风格）：
 *   组:  ['and'|'or', <condition>, <condition>, ...]
 *   叶:  [<field>, <op>, <value>]
 */
import { ref, watch } from 'vue'
import { ElForm, ElFormItem, ElSelect, ElOption, ElInput, ElButton, ElButtonGroup } from 'element-plus'

defineOptions({ name: 'ElFilterBuilder' })

interface FieldDef {
  field: string
  label?: string
  dataType?: 'string' | 'number' | 'date' | 'boolean'
  editorType?: 'select' | 'input'
  options?: { label: string; value: any }[]
}

const props = defineProps<{
  fields?: (string | FieldDef)[]
  value?: any[]
  modelValue?: any[]
  readOnly?: boolean
  maxLevel?: number
}>()

const emit = defineEmits<{
  'update:value': [val: any[]]
  'update:modelValue': [val: any[]]
  valueChanged: [val: any[]]
}>()

const fieldsNorm = computed<FieldDef[]>(() =>
  (props.fields ?? []).map((f) => (typeof f === 'string' ? { field: f, label: f } : f)),
)
import { computed } from 'vue'

const local = ref<any[]>([])

function sync() {
  const v = props.modelValue ?? props.value
  local.value = v ? clone(v) : ['and', ['', '=', '']]
}
watch(() => [props.value, props.modelValue], sync, { immediate: true })

function clone(v: any): any {
  return Array.isArray(v) ? v.map(clone) : (v && typeof v === 'object' ? { ...v } : v)
}

function isGroup(cond: any[]): boolean {
  return cond[0] === 'and' || cond[0] === 'or'
}

function labelOf(field: string): string {
  return fieldsNorm.value.find((x) => x.field === field)?.label ?? field
}
function fieldDef(field: string): FieldDef | undefined {
  return fieldsNorm.value.find((x) => x.field === field)
}

const OPS_BY_TYPE: Record<string, { value: string; label: string }[]> = {
  string: [
    { value: '=', label: '等于' }, { value: '<>', label: '不等于' },
    { value: 'contains', label: '包含' }, { value: 'notcontains', label: '不包含' },
    { value: 'startswith', label: '开始于' }, { value: 'endswith', label: '结束于' },
    { value: 'isblank', label: '为空' }, { value: 'isnotblank', label: '不为空' },
  ],
  number: [
    { value: '=', label: '等于' }, { value: '<>', label: '不等于' },
    { value: '>', label: '大于' }, { value: '>=', label: '大于等于' },
    { value: '<', label: '小于' }, { value: '<=', label: '小于等于' },
    { value: 'isblank', label: '为空' }, { value: 'isnotblank', label: '不为空' },
  ],
  date: [
    { value: '=', label: '等于' }, { value: '<>', label: '不等于' },
    { value: '>', label: '晚于' }, { value: '<', label: '早于' },
    { value: 'isblank', label: '为空' }, { value: 'isnotblank', label: '不为空' },
  ],
  boolean: [{ value: '=', label: '等于' }],
}

function opsFor(field: string) {
  const dt = fieldDef(field)?.dataType ?? 'string'
  return OPS_BY_TYPE[dt] ?? OPS_BY_TYPE.string
}

function emitChange() {
  emit('update:value', local.value)
  emit('update:modelValue', local.value)
  emit('valueChanged', local.value)
}

function setGroupOp(op: 'and' | 'or') {
  local.value[0] = op
  emitChange()
}
function addChild() {
  local.value.push(['', '=', ''])
  emitChange()
}
function removeChild(i: number) {
  local.value.splice(i, 1)
  emitChange()
}
function replaceChild(i: number, child: any[]) {
  local.value[i] = child
  emitChange()
}
function setLeafField(i: number, field: string) {
  local.value[i] = [field, '=', '']
  emitChange()
}
function setLeafOp(i: number, op: string) {
  local.value[i][1] = op
  emitChange()
}
function setLeafVal(i: number, v: any) {
  local.value[i][2] = v
  emitChange()
}
</script>

<template>
  <div class="el-filter-builder">
    <ElForm label-position="top" :disabled="readOnly">
      <!-- 组 -->
      <template v-if="isGroup(local)">
        <div class="fb-group">
          <div class="fb-group__header">
            <ElButtonGroup>
              <ElButton
                size="small"
                :type="local[0] === 'and' ? 'primary' : 'default'"
                @click="setGroupOp('and')"
              >且 (AND)</ElButton>
              <ElButton
                size="small"
                :type="local[0] === 'or' ? 'primary' : 'default'"
                @click="setGroupOp('or')"
              >或 (OR)</ElButton>
            </ElButtonGroup>
            <ElButton
              v-if="!readOnly"
              size="small"
              type="primary"
              plain
              @click="addChild"
            >+ 条件</ElButton>
          </div>
          <div
            v-for="(child, i) in local.slice(1)"
            :key="i"
            class="fb-group__child"
          >
            <ElFilterBuilder
              :fields="fieldsNorm"
              :value="child"
              :read-only="readOnly"
              @update:value="(c: any[]) => replaceChild(i + 1, c)"
            />
            <ElButton
              v-if="!readOnly"
              size="small"
              type="danger"
              text
              @click="removeChild(i + 1)"
            >删除</ElButton>
          </div>
        </div>
      </template>
      <!-- 叶子 -->
      <template v-else>
        <div class="fb-leaf">
          <ElFormItem>
            <ElSelect
              :model-value="local[0]"
              placeholder="字段"
              style="width: 140px"
              @update:model-value="(v: any) => setLeafField(0, v)"
            >
              <ElOption
                v-for="f in fieldsNorm"
                :key="f.field"
                :label="f.label ?? f.field"
                :value="f.field"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem>
            <ElSelect
              :model-value="local[1]"
              placeholder="操作符"
              style="width: 110px"
              @update:model-value="(v: any) => setLeafOp(1, v)"
            >
              <ElOption
                v-for="op in opsFor(local[0])"
                :key="op.value"
                :label="op.label"
                :value="op.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="local[1] !== 'isblank' && local[1] !== 'isnotblank'">
            <ElSelect
              v-if="fieldDef(local[0])?.editorType === 'select'"
              :model-value="local[2]"
              placeholder="值"
              style="width: 160px"
              @update:model-value="(v: any) => setLeafVal(2, v)"
            >
              <ElOption
                v-for="o in fieldDef(local[0])?.options ?? []"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </ElSelect>
            <ElInput
              v-else
              :model-value="local[2]"
              placeholder="值"
              style="width: 160px"
              @update:model-value="(v: any) => setLeafVal(2, v)"
            />
          </ElFormItem>
        </div>
      </template>
    </ElForm>
  </div>
</template>

<style scoped>
.el-filter-builder {
  width: 100%;
  box-sizing: border-box;
}
.fb-group {
  border: 1px dashed var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  padding: 8px;
  margin-bottom: 6px;
}
.fb-group__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.fb-group__child {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 4px;
}
.fb-leaf {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.fb-leaf :deep(.el-form-item) {
  margin-bottom: 0;
}
</style>