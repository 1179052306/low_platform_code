<script setup lang="ts">
import { computed } from 'vue'
import { ElInputNumber, ElInput } from 'element-plus'

const props = defineProps<{
  value?: number
  modelValue?: number
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
  min?: number
  max?: number
  step?: number
  precision?: number
  format?: string
  showClearButton?: boolean
  prefix?: string
  suffix?: string
  controlsPosition?: '' | 'right'
}>()

const emit = defineEmits<{
  'update:value': [val: number]
  'update:modelValue': [val: number]
  valueChanged: [e: { value: number }]
  focusIn: []
  focusOut: []
}>()

const inner = computed({
  get: () => (props.modelValue ?? props.value) as number,
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v })
  },
})

const hasAffix = computed(() => !!(props.prefix || props.suffix))

function onAffixChange(val: number | string) {
  let num = typeof val === 'string' ? parseFloat(val) : val
  if (isNaN(num)) return
  if (props.precision !== undefined) {
    num = parseFloat(num.toFixed(props.precision))
  }
  if (props.min !== undefined && num < props.min) num = props.min
  if (props.max !== undefined && num > props.max) num = props.max
  inner.value = num
}
</script>

<template>
  <div v-if="hasAffix" class="el-number-affix">
    <ElInput
      v-model.number="inner"
      type="number"
      :readonly="readOnly || disabled"
      :disabled="disabled"
      :placeholder="placeholder"
      :min="min"
      :max="max"
      :step="step ?? 1"
      class="el-number-affix-input"
      @change="onAffixChange"
      @focus="() => emit('focusIn')"
      @blur="() => emit('focusOut')"
    >
      <template v-if="prefix" #prefix><span class="el-number-affix-text">{{ prefix }}</span></template>
      <template v-if="suffix" #suffix><span class="el-number-affix-text">{{ suffix }}</span></template>
    </ElInput>
  </div>
  <ElInputNumber
    v-else
    v-model="inner"
    :readonly="readOnly || disabled"
    :disabled="disabled"
    :placeholder="placeholder"
    :min="min"
    :max="max"
    :step="step ?? 1"
    :precision="precision"
    :controls="!showClearButton"
    :controls-position="controlsPosition || 'right'"
    style="width: 100%"
    @focus="() => emit('focusIn')"
    @blur="() => emit('focusOut')"
  />
</template>

<style scoped>
.el-number-affix {
  width: 100%;
}
.el-number-affix-input {
  width: 100%;
}
.el-number-affix-text {
  color: var(--el-text-color-regular);
  white-space: nowrap;
}
:deep(.el-input__inner) {
  text-align: left;
}
</style>