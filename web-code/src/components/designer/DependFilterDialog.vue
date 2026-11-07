<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElDialog, ElForm, ElButton, ElSelect, ElOption, ElInput, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'

export interface DependFilterCondition {
  field: string
  operator: string
  valueType: 'depend' | 'fixed' | 'rowField' | 'formControl'
  fixedValue?: string
  rowField?: string
  formControlField?: string
}

export interface DependFieldOption {
  dbField: string
  displayText: string
}

const props = defineProps<{
  visible: boolean
  conditions?: DependFilterCondition[]
  fields: DependFieldOption[]
  rowFields?: DependFieldOption[]
  formFields?: DependFieldOption[]
  showRowField?: boolean
  showFormControl?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [conditions: DependFilterCondition[]]
}>()

const localConditions = ref<DependFilterCondition[]>([])

const OPERATORS = [
  { value: '=', label: '等于' },
  { value: '<>', label: '不等于' },
  { value: '>', label: '大于' },
  { value: '>=', label: '大于等于' },
  { value: '<', label: '小于' },
  { value: '<=', label: '小于等于' },
  { value: 'contains', label: '包含' },
  { value: 'startswith', label: '开头是' },
  { value: 'endswith', label: '结尾是' },
]

const valueTypeOptions = computed(() => {
  const opts = [
    { value: 'fixed', label: '固定值' },
  ]
  if (props.showRowField) opts.push({ value: 'rowField', label: '当前行字段' })
  if (props.showFormControl) opts.push({ value: 'formControl', label: '表单控件' })
  opts.push({ value: 'depend', label: '父控件值' })
  return opts
})

watch(
  () => props.visible,
  (v) => {
    if (v) {
      localConditions.value = JSON.parse(JSON.stringify(props.conditions ?? []))
    }
  },
  { immediate: true },
)

function addCondition() {
  localConditions.value.push({
    field: '',
    operator: '=',
    valueType: 'fixed',
    fixedValue: '',
  })
}

function removeCondition(idx: number) {
  localConditions.value.splice(idx, 1)
}

function onSave() {
  for (const c of localConditions.value) {
    if (!c.field) {
      ElMessage.warning(t('请选择过滤字段'))
      return
    }
    if (c.valueType === 'fixed' && (c.fixedValue == null || c.fixedValue === '')) {
      ElMessage.warning(t('请填写固定值'))
      return
    }
    if (c.valueType === 'rowField' && !c.rowField) {
      ElMessage.warning(t('请选择行字段'))
      return
    }
    if (c.valueType === 'formControl' && !c.formControlField) {
      ElMessage.warning(t('请选择表单控件'))
      return
    }
  }
  emit('save', localConditions.value)
  emit('update:visible', false)
}

function onClose() {
  emit('update:visible', false)
}

const { isMaximized, toggleMaximize, resetDialog } = useDialogDragAndMax()
watch(() => props.visible, (v) => { if (!v) resetDialog() })
</script>

<template>
  <ElDialog
    :model-value="visible"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    width="720px"
    append-to-body
    draggable
    :fullscreen="isMaximized"
  >
    <template #header>
      <DialogMaxHeader :title="t('配置过滤条件')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <ElForm label-width="90px">
      <div class="condition-list">
        <div v-for="(c, idx) in localConditions" :key="idx" class="condition-row">
          <ElSelect v-model="c.field" :placeholder="t('过滤字段')" style="width: 150px" filterable popper-class="dialog-inner-popper">
            <ElOption
              v-for="f in fields"
              :key="f.dbField"
              :value="f.dbField"
              :label="f.displayText"
            />
          </ElSelect>
          <ElSelect v-model="c.operator" style="width: 100px" popper-class="dialog-inner-popper">
            <ElOption
              v-for="op in OPERATORS"
              :key="op.value"
              :value="op.value"
              :label="t(op.label)"
            />
          </ElSelect>
          <ElSelect v-model="c.valueType" style="width: 120px" popper-class="dialog-inner-popper">
            <ElOption
              v-for="vt in valueTypeOptions"
              :key="vt.value"
              :value="vt.value"
              :label="t(vt.label)"
            />
          </ElSelect>
          <ElInput
            v-if="c.valueType === 'fixed'"
            v-model="c.fixedValue"
            :placeholder="t('固定值')"
            style="width: 130px"
          />
          <ElSelect
            v-else-if="c.valueType === 'rowField'"
            v-model="c.rowField"
            :placeholder="t('行字段')"
            style="width: 130px"
            filterable
            popper-class="dialog-inner-popper"
          >
            <ElOption
              v-for="f in (rowFields || [])"
              :key="f.dbField"
              :value="f.dbField"
              :label="f.displayText"
            />
          </ElSelect>
          <ElSelect
            v-else-if="c.valueType === 'formControl'"
            v-model="c.formControlField"
            :placeholder="t('表单控件')"
            style="width: 130px"
            filterable
            popper-class="dialog-inner-popper"
          >
            <ElOption
              v-for="f in (formFields || [])"
              :key="f.dbField"
              :value="f.dbField"
              :label="f.displayText"
            />
          </ElSelect>
          <span v-else style="width: 130px" />
          <ElButton type="danger" link @click="removeCondition(idx)">{{ t('删除') }}</ElButton>
        </div>
        <ElButton type="primary" link @click="addCondition">+ {{ t('添加条件') }}</ElButton>
      </div>
    </ElForm>

    <template #footer>
      <ElButton @click="onClose">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onSave">{{ t('保存') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.condition-list {
  width: 100%;
}
.condition-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
</style>