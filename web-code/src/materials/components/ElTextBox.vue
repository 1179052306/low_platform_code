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
  /** 启用编码规则：输入禁止空格、换行等空白与特殊字符 */
  codeRuleEnabled?: boolean
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
    // 编码规则字段：过滤空格、换行、tab 等空白与控制字符
    const cleaned = props.codeRuleEnabled && typeof v === 'string'
      ? v.replace(/[\s\u0000-\u001f\u007f]/g, '')
      : v
    emit('update:value', cleaned)
    emit('update:modelValue', cleaned)
    emit('valueChanged', { value: cleaned })
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
