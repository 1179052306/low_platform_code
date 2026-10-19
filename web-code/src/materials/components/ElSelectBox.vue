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
  items?: any[]
  displayExpr?: string | ((item: any) => string)
  valueExpr?: string | ((item: any) => any)
  showClearButton?: boolean
  searchEnabled?: boolean
  searchExpr?: string | string[]
  multiple?: boolean
}>()

const emit = defineEmits<{
  'update:value': [val: any]
  'update:modelValue': [val: any]
  valueChanged: [e: { value: any; selectedItem: any }]
  selectionChanged: [e: { selectedItem: any }]
}>()

const inner = computed({
  get: () => {
    const v = props.modelValue ?? props.value
    if (props.multiple) {
      if (v == null || v === '') return []
      if (!Array.isArray(v)) return [v]
      return v
    }
    return v
  },
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    if (props.multiple && Array.isArray(v)) {
      const items = v.map((val) => options.value.find((o) => o.value === val)).filter(Boolean)
      emit('valueChanged', { value: v, selectedItem: items })
      emit('selectionChanged', { selectedItem: items })
    } else {
      const item = options.value.find((o) => o.value === v)
      emit('valueChanged', { value: v, selectedItem: item })
      emit('selectionChanged', { selectedItem: item })
    }
  },
})

const options = ref<{ label: string; value: any }[]>([])
const loading = ref(false)

async function loadOptions() {
  const ds = props.dataSource ?? props.items
  if (!ds) { options.value = []; return }
  loading.value = true
  try {
    const data = await resolveDataSource(ds)
    options.value = toSelectOptions(data, props.displayExpr, props.valueExpr)
    // 兼容旧数据：value 类型不匹配时做宽松修正（如 "0" → 0）
    const cur = props.modelValue ?? props.value
    if (cur != null && !options.value.some((o) => o.value === cur)) {
      const match = options.value.find((o) => String(o.value) === String(cur))
      if (match) {
        emit('update:value', match.value)
        emit('update:modelValue', match.value)
      }
    }
  } finally {
    loading.value = false
  }
}

watch(() => [props.dataSource, props.items], loadOptions, { immediate: true })
</script>

<template>
  <ElSelect
    v-model="inner"
    :disabled="readOnly || disabled"
    :placeholder="placeholder ?? '请选择'"
    :clearable="showClearButton"
    :filterable="searchEnabled !== false"
    :multiple="multiple"

    :loading="loading"
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