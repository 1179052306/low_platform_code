<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElAutocomplete } from 'element-plus'
import { resolveDataSource } from '../dx-to-el'

const props = defineProps<{
  value?: string
  modelValue?: string
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
  dataSource?: any
  items?: any[]
  displayExpr?: string | ((item: any) => string)
  valueExpr?: string | ((item: any) => any)
  minSearchLength?: number
}>()

const emit = defineEmits<{
  'update:value': [val: string]
  'update:modelValue': [val: string]
  valueChanged: [e: { value: string }]
  focusIn: []
  focusOut: []
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value ?? '',
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v })
  },
})

const suggestions = ref<{ value: string }[]>([])

async function loadAll() {
  const ds = props.dataSource ?? props.items
  if (!ds) return
  const data = await resolveDataSource(ds)
  suggestions.value = data.map((item) => ({
    value: typeof props.displayExpr === 'function' ? props.displayExpr(item) : (props.displayExpr ? item?.[props.displayExpr] : String(item)),
  }))
}

watch(() => [props.dataSource, props.items], loadAll, { immediate: true })

function querySearch(queryString: string, cb: (results: any[]) => void) {
  const results = queryString
    ? suggestions.value.filter((s) => s.value.toLowerCase().includes(queryString.toLowerCase()))
    : suggestions.value
  cb(results)
}
</script>

<template>
  <ElAutocomplete
    v-model="inner"
    :fetch-suggestions="querySearch"
    :disabled="readOnly || disabled"
    :placeholder="placeholder ?? '请输入'"
    :trigger-on-focus="false"
    clearable
    @focus="() => emit('focusIn')"
    @blur="() => emit('focusOut')"
  />
</template>