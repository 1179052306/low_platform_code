<script setup lang="ts">
import { computed } from 'vue'
import { ElSwitch } from 'element-plus'

const props = defineProps<{
  value?: boolean
  modelValue?: boolean
  readOnly?: boolean
  disabled?: boolean
  switchedOnText?: string
  switchedOffText?: string
  width?: number | string
}>()

const emit = defineEmits<{
  'update:value': [val: boolean]
  'update:modelValue': [val: boolean]
  valueChanged: [e: { value: boolean }]
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value ?? false,
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v })
  },
})

const activeText = computed(() => props.switchedOnText ?? '')
const inactiveText = computed(() => props.switchedOffText ?? '')
</script>

<template>
  <ElSwitch
    v-model="inner"
    :disabled="readOnly || disabled"
    :active-text="activeText"
    :inactive-text="inactiveText"
    inline-prompt
  />
</template>