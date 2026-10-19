<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { watch, computed } from 'vue'
import { designerStore } from '@/store/designer'

const props = defineProps<{
  message?: string
  type?: 'info' | 'success' | 'warning' | 'error'
  displayTime?: number
  position?: string
  visible?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  shown: []
  hidden: []
}>()

const isDesign = computed(() => !designerStore.previewMode)

function show() {
  ElMessage({
    message: props.message ?? '',
    type: props.type ?? 'info',
    duration: props.displayTime ?? 2000,
    onClose: () => emit('hidden'),
  })
  emit('shown')
}

watch(() => [props.visible, isDesign.value] as const, ([v, design]) => {
  if (design) return
  if (v) show()
}, { immediate: true })
</script>

<template>
  <div v-if="isDesign" class="el-toast-placeholder">
    <span>💡</span>
    <span>{{ message ?? '提示消息' }}</span>
  </div>
  <span v-else style="display:none" />
</template>

<style scoped>
.el-toast-placeholder {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
}
</style>