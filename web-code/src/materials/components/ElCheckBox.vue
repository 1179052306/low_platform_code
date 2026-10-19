<script setup lang="ts">
import { computed } from 'vue'
import { ElCheckbox } from 'element-plus'

const props = defineProps<{
  value?: boolean
  modelValue?: boolean
  readOnly?: boolean
  disabled?: boolean
  text?: string
  enableThreeStateBehavior?: boolean
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
</script>

<template>
  <ElCheckbox
    v-model="inner"
    :disabled="readOnly || disabled"
  >
    {{ text }}
  </ElCheckbox>
</template>