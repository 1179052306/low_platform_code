<script setup lang="ts">
import { computed } from 'vue'
import { ElInput } from 'element-plus'

const props = defineProps<{
  value?: string | number
  modelValue?: string | number
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
  maxLength?: number
  mask?: string
  showClearButton?: boolean
}>()

const emit = defineEmits<{
  'update:value': [val: string | number]
  'update:modelValue': [val: string | number]
  valueChanged: [e: { value: string | number }]
  focusIn: []
  focusOut: []
  enterKey: []
  keyDown: [e: KeyboardEvent]
  keyUp: [e: KeyboardEvent]
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value ?? '',
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v })
  },
})

function onKeydown(e: KeyboardEvent) {
  emit('keyDown', e)
  if (e.key === 'Enter') emit('enterKey')
}
</script>

<template>
  <ElInput
    v-model="inner"
    :readonly="readOnly || disabled"
    :placeholder="placeholder"
    :maxlength="maxLength"
    :clearable="showClearButton"
    @keydown="(e: any) => onKeydown(e as KeyboardEvent)"
    @keyup="(e: any) => emit('keyUp', e as KeyboardEvent)"
    @focus="() => emit('focusIn')"
    @blur="() => emit('focusOut')"
  />
</template>
