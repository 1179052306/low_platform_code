<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  value?: string
  modelValue?: string
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
  height?: number | string
}>()

const emit = defineEmits<{
  'update:value': [val: string]
  'update:modelValue': [val: string]
  valueChanged: [e: { value: string }]
}>()

const editorRef = ref<HTMLDivElement>()
const inner = computed(() => props.modelValue ?? props.value ?? '')

function onInput() {
  if (!editorRef.value) return
  const html = editorRef.value.innerHTML
  emit('update:value', html)
  emit('update:modelValue', html)
  emit('valueChanged', { value: html })
}

watch(() => props.modelValue, (v) => {
  if (editorRef.value && v != null && editorRef.value.innerHTML !== v) {
    editorRef.value.innerHTML = v
  }
})
</script>

<template>
  <div class="el-html-editor" :style="{ height: height ? (typeof height === 'number' ? `${height}px` : height) : '300px' }">
    <div
      v-if="readOnly || disabled"
      class="el-html-editor-readonly"
      v-html="inner"
    />
    <div
      v-else
      ref="editorRef"
      class="el-html-editor-container"
      contenteditable="true"
      :data-placeholder="placeholder ?? '请输入内容'"
      @input="onInput"
      v-html="inner"
    />
  </div>
</template>

<style scoped>
.el-html-editor { border: 1px solid var(--el-border-color); border-radius: 4px; overflow: auto; }
.el-html-editor-readonly { padding: 8px; }
.el-html-editor-container {
  min-height: 100%;
  padding: 8px;
  outline: none;
}
.el-html-editor-container:empty::before {
  content: attr(data-placeholder);
  color: var(--el-text-color-placeholder);
}
</style>