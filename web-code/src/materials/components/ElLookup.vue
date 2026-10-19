<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElSelect, ElOption } from 'element-plus'
import { resolveDataSource, toSelectOptions } from '../dx-to-el'

const props = defineProps<{
  value?: any
  modelValue?: any
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
  dataSource?: any
  displayExpr?: string | ((item: any) => string)
  valueExpr?: string | ((item: any) => any)
  pageMode?: string
  paginate?: boolean
  pageSize?: number
  showClearButton?: boolean
}>()

const emit = defineEmits<{
  'update:value': [val: any]
  'update:modelValue': [val: any]
  valueChanged: [e: { value: any; selectedItem: any }]
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value,
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    const item = options.value.find((o) => o.value === v)
    emit('valueChanged', { value: v, selectedItem: item })
  },
})

const options = ref<{ label: string; value: any }[]>([])
const loading = ref(false)

async function loadOptions() {
  if (!props.dataSource) { options.value = []; return }
  loading.value = true
  try {
    const data = await resolveDataSource(props.dataSource)
    options.value = toSelectOptions(data, props.displayExpr, props.valueExpr)
  } finally {
    loading.value = false
  }
}

watch(() => props.dataSource, loadOptions, { immediate: true })
</script>

<template>
  <ElSelect
    v-model="inner"
    :disabled="readOnly || disabled"
    :placeholder="placeholder ?? '请选择'"
    :clearable="showClearButton"
    :loading="loading"
    filterable
    remote
    :remote-method="() => loadOptions()"
    style="width: 100%"
  >
    <ElOption
      v-for="opt in options"
      :key="opt.value"
      :label="opt.label"
      :value="opt.value"
    />
  </ElSelect>
</template>