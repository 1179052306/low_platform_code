<script setup lang="ts">
import { ref, watch } from 'vue'
import { t } from '@/store/lang'

const props = defineProps<{
  modelValue: string
  visible: boolean
  title?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:visible': [value: boolean]
}>()

const localValue = ref('')

watch(
  () => props.modelValue,
  (val) => {
    localValue.value = val
  },
  { immediate: true }
)

function onConfirm() {
  emit('update:modelValue', localValue.value)
  emit('update:visible', false)
}

function onCancel() {
  emit('update:visible', false)
}
</script>

<template>
  <div v-if="visible" class="dialog-overlay" @click.self="onCancel">
    <div class="dialog-container">
      <div class="dialog-header">
        <span class="dialog-title">{{ title || t('文本编辑') }}</span>
        <button class="dialog-close" @click="onCancel">✕</button>
      </div>
      <div class="dialog-body">
        <textarea
          v-model="localValue"
          class="text-editor-area"
          :placeholder="t('请输入文本内容...')"
          spellcheck="false"
        />
      </div>
      <div class="dialog-footer">
        <button class="dialog-btn dialog-btn-cancel" @click="onCancel">
          {{ t('取消') }}
        </button>
        <button class="dialog-btn dialog-btn-confirm" @click="onConfirm">
          {{ t('确定') }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.dialog-container {
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  width: 520px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}
.dialog-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.dialog-close {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  color: #909399;
  padding: 0;
  line-height: 1;
}
.dialog-close:hover {
  color: #303133;
}
.dialog-body {
  padding: 16px;
  flex: 1;
  overflow: auto;
}
.text-editor-area {
  width: 100%;
  min-height: 200px;
  padding: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Consolas', 'Monaco', monospace;
  line-height: 1.6;
  resize: vertical;
  box-sizing: border-box;
  outline: none;
  transition: border-color 0.2s;
}
.text-editor-area:focus {
  border-color: #1976d2;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
}
.dialog-btn {
  padding: 6px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.dialog-btn-cancel {
  background: #fff;
  color: #606266;
}
.dialog-btn-cancel:hover {
  border-color: #1976d2;
  color: #1976d2;
}
.dialog-btn-confirm {
  background: #1976d2;
  color: #fff;
  border-color: #1976d2;
}
.dialog-btn-confirm:hover {
  background: #1565c0;
  border-color: #1565c0;
}
</style>