<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElInput, ElButton, ElTable, ElTableColumn, ElSelect, ElOption, ElInputNumber, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import type { QueryConfig, QueryFieldConfig, FilterRule, SortRule } from '@/types/schema'

const props = defineProps<{
  visible: boolean
  config?: QueryConfig
  /** 可选字段列表（从 schema 提取，供选择可查询字段） */
  fields?: { fieldName: string; label: string }[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [config: QueryConfig]
}>()

const allOperators = ['eq', 'ne', 'like', 'in', 'between', 'dateRange', 'gt', 'gte', 'lt', 'lte']

const queryFields = ref<QueryFieldConfig[]>([])
const defaultFilters = ref<FilterRule[]>([])
const defaultSort = ref<SortRule[]>([])
const pageSize = ref<number | undefined>(undefined)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      queryFields.value = JSON.parse(JSON.stringify(props.config?.fields ?? []))
      defaultFilters.value = JSON.parse(JSON.stringify(props.config?.defaultFilters ?? []))
      defaultSort.value = JSON.parse(JSON.stringify(props.config?.defaultSort ?? []))
      pageSize.value = props.config?.pageSize
    }
  },
  { immediate: true },
)

const availableFields = computed(() => props.fields ?? [])

function addField() {
  const f = availableFields.value[0]
  queryFields.value.push({
    fieldName: f?.fieldName ?? '',
    label: f?.label ?? '',
    operators: ['eq'],
  })
}

function removeField(idx: number) {
  queryFields.value.splice(idx, 1)
}

function addFilter() {
  defaultFilters.value.push({ field: '', operator: 'eq', value: '' })
}

function addSort() {
  defaultSort.value.push({ field: '', desc: false })
}

function onSave() {
  if (!queryFields.value.length) {
    ElMessage.warning(t('请至少配置一个可查询字段'))
    return
  }
  const config: QueryConfig = {
    fields: queryFields.value,
    defaultFilters: defaultFilters.value.length ? defaultFilters.value : undefined,
    defaultSort: defaultSort.value.length ? defaultSort.value : undefined,
    pageSize: pageSize.value,
  }
  emit('save', config)
  emit('update:visible', false)
}

function onClose() {
  emit('update:visible', false)
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    :title="t('查询条件配置')"
    width="720px"
    append-to-body
  >
    <ElForm label-width="100px">
      <ElFormItem :label="t('可查询字段')">
        <div class="field-list">
          <div v-for="(f, idx) in queryFields" :key="idx" class="field-row">
            <ElSelect v-model="f.fieldName" :placeholder="t('选择字段')" style="width: 160px">
              <ElOption
                v-for="af in availableFields"
                :key="af.fieldName"
                :label="af.label || af.fieldName"
                :value="af.fieldName"
              />
            </ElSelect>
            <ElInput v-model="f.label" :placeholder="t('显示名')" style="width: 120px" />
            <ElSelect v-model="f.operators" multiple :placeholder="t('操作符')" style="width: 220px">
              <ElOption v-for="op in allOperators" :key="op" :label="op" :value="op" />
            </ElSelect>
            <ElButton type="danger" link @click="removeField(idx)">{{ t('删除') }}</ElButton>
          </div>
          <ElButton type="primary" link @click="addField">+ {{ t('添加字段') }}</ElButton>
        </div>
      </ElFormItem>

      <ElFormItem :label="t('默认分页')">
        <ElInputNumber v-model="pageSize" :min="1" :placeholder="t('每页条数')" />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="onClose">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onSave">{{ t('保存') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.field-list {
  width: 100%;
}
.field-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
</style>