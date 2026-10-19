<script setup lang="ts">
import { computed } from 'vue'
import { ElInput } from 'element-plus'

const props = defineProps<{
  value?: string
  modelValue?: string
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
  maxLength?: number
  height?: number
  autoResize?: boolean
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
</script>

<template>
  <ElInput
    v-model="inner"
    type="textarea"
    :readonly="readOnly || disabled"
    :placeholder="placeholder"
    :maxlength="maxLength"
    :rows="height ? Math.max(1, Math.floor(height / 32)) : 3"
    :resize="autoResize ? 'vertical' : 'none'"
    @focus="() => emit('focusIn')"
    @blur="() => emit('focusOut')"
  />
</template>