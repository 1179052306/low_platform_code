<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElSelect, ElOption } from 'element-plus'
import { resolveDataSource, toSelectOptions } from '../dx-to-el'

const props = defineProps<{
  value?: any[]
  modelValue?: any[]
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
  dataSource?: any
  items?: any[]
  displayExpr?: string | ((item: any) => string)
  valueExpr?: string | ((item: any) => any)
  showClearButton?: boolean
  maxDisplayedTags?: number
}>()

const emit = defineEmits<{
  'update:value': [val: any[]]
  'update:modelValue': [val: any[]]
  valueChanged: [e: { value: any[] }]
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value ?? [],
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v })
  },
})

const options = ref<{ label: string; value: any }[]>([])

async function loadOptions() {
  const ds = props.dataSource ?? props.items
  if (!ds) { options.value = []; return }
  const data = await resolveDataSource(ds)
  options.value = toSelectOptions(data, props.displayExpr, props.valueExpr)
}

watch(() => [props.dataSource, props.items], loadOptions, { immediate: true })
</script>

<template>
  <ElSelect
    v-model="inner"
    multiple
    collapse-tags
    :collapse-tags-tooltip="!!maxDisplayedTags"
    :disabled="readOnly || disabled"
    :placeholder="placeholder ?? '请选择'"
    :clearable="showClearButton"
    filterable
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