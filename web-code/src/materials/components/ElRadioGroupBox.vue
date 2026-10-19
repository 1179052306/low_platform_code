<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElRadioGroup, ElRadio } from 'element-plus'
import { resolveDataSource, toSelectOptions } from '../dx-to-el'

const props = defineProps<{
  value?: any
  modelValue?: any
  readOnly?: boolean
  disabled?: boolean
  dataSource?: any
  items?: any[]
  displayExpr?: string | ((item: any) => string)
  valueExpr?: string | ((item: any) => any)
  layout?: 'horizontal' | 'vertical'
}>()

const emit = defineEmits<{
  'update:value': [val: any]
  'update:modelValue': [val: any]
  valueChanged: [e: { value: any }]
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value,
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
  <ElRadioGroup
    v-model="inner"
    :disabled="readOnly || disabled"
    :class="{ 'el-radio-vertical': layout === 'vertical' }"
  >
    <ElRadio
      v-for="opt in options"
      :key="opt.value"
      :value="opt.value"
    >
      {{ opt.label }}
    </ElRadio>
  </ElRadioGroup>
</template>

<style scoped>
.el-radio-vertical {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>