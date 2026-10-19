<script setup lang="ts">
/**
 * ElPivotFieldChooser — 替换 DxPivotGridFieldChooser
 * 用三组 ElCheckboxGroup 把字段分配到 行/列/数据 区域（互斥）。
 */
import { computed, ref, watch } from 'vue'
import { ElCheckboxGroup, ElCheckbox, ElButton } from 'element-plus'

interface Field {
  field: string
  label?: string
}

const props = defineProps<{
  fields?: (string | Field)[]
  rowFields?: string[]
  columnFields?: string[]
  dataFields?: string[]
  readOnly?: boolean
}>()

const emit = defineEmits<{
  valueChanged: [e: { rowFields: string[]; columnFields: string[]; dataFields: string[] }]
  fieldsChanged: [e: { rowFields: string[]; columnFields: string[]; dataFields: string[] }]
}>()

const fieldsNorm = computed<Field[]>(() =>
  (props.fields ?? []).map((f) => (typeof f === 'string' ? { field: f, label: f } : f)),
)

const rowSel = ref<string[]>([])
const colSel = ref<string[]>([])
const dataSel = ref<string[]>([])

function sync() {
  rowSel.value = [...(props.rowFields ?? [])]
  colSel.value = [...(props.columnFields ?? [])]
  dataSel.value = [...(props.dataFields ?? [])]
}
watch(() => [props.fields, props.rowFields, props.columnFields, props.dataFields], sync, { immediate: true })

function labelOf(f: string): string {
  return fieldsNorm.value.find((x) => x.field === f)?.label ?? f
}

function emitChange() {
  const e = { rowFields: [...rowSel.value], columnFields: [...colSel.value], dataFields: [...dataSel.value] }
  emit('valueChanged', e)
  emit('fieldsChanged', e)
}

function onRowChange(vals: any[]) {
  if (props.readOnly) return
  const removed = rowSel.value.filter((v) => !vals.includes(v))
  rowSel.value = vals.filter((v) => !colSel.value.includes(v) && !dataSel.value.includes(v))
  if (removed.length || vals.length !== rowSel.value.length) emitChange()
}
function onColChange(vals: any[]) {
  if (props.readOnly) return
  colSel.value = vals.filter((v) => !rowSel.value.includes(v) && !dataSel.value.includes(v))
  emitChange()
}
function onDataChange(vals: any[]) {
  if (props.readOnly) return
  dataSel.value = vals.filter((v) => !rowSel.value.includes(v) && !colSel.value.includes(v))
  emitChange()
}

function reset() {
  rowSel.value = []; colSel.value = []; dataSel.value = []
  emitChange()
}
</script>

<template>
  <div class="el-pivot-field-chooser">
    <div class="pf-section">
      <div class="pf-section__title">行区域</div>
      <ElCheckboxGroup v-model="rowSel" :disabled="readOnly" @change="onRowChange">
        <ElCheckbox
          v-for="f in fieldsNorm"
          :key="`r-${f.field}`"
          :value="f.field"
          :label="f.label ?? f.field"
        />
      </ElCheckboxGroup>
    </div>
    <div class="pf-section">
      <div class="pf-section__title">列区域</div>
      <ElCheckboxGroup v-model="colSel" :disabled="readOnly" @change="onColChange">
        <ElCheckbox
          v-for="f in fieldsNorm"
          :key="`c-${f.field}`"
          :value="f.field"
          :label="f.label ?? f.field"
        />
      </ElCheckboxGroup>
    </div>
    <div class="pf-section">
      <div class="pf-section__title">数据区域</div>
      <ElCheckboxGroup v-model="dataSel" :disabled="readOnly" @change="onDataChange">
        <ElCheckbox
          v-for="f in fieldsNorm"
          :key="`d-${f.field}`"
          :value="f.field"
          :label="f.label ?? f.field"
        />
      </ElCheckboxGroup>
    </div>
    <div v-if="!readOnly" class="pf-actions">
      <ElButton size="small" @click="reset">重置</ElButton>
    </div>
  </div>
</template>

<style scoped>
.el-pivot-field-chooser {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  box-sizing: border-box;
  background: var(--el-bg-color, #fff);
}
.pf-section__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  margin-bottom: 6px;
  padding-left: 4px;
  border-left: 3px solid var(--el-color-primary, #409eff);
}
.pf-section :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.pf-actions {
  display: flex;
  justify-content: flex-end;
}
</style>