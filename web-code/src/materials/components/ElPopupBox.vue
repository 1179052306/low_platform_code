<script setup lang="ts">
import { computed } from 'vue'
import { ElDialog } from 'element-plus'
import { designerStore } from '@/store/designer'

const props = defineProps<{
  visible?: boolean
  modelValue?: boolean
  title?: string
  width?: number | string
  height?: number | string
  showCloseButton?: boolean
  closeOnOutsideClick?: boolean
  dragEnabled?: boolean
  resizeEnabled?: boolean
  shading?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  'update:modelValue': [val: boolean]
  showing: []
  shown: []
  hiding: []
  hidden: []
}>()

const isDesign = computed(() => !designerStore.previewMode)
const inner = computed({
  get: () => !isDesign.value && (props.modelValue ?? props.visible ?? false),
  set: (v) => {
    emit('update:visible', v)
    emit('update:modelValue', v)
  },
})

const dialogWidth = computed(() => props.width ? (typeof props.width === 'number' ? `${props.width}px` : props.width) : '50%')
</script>

<template>
  <div v-if="isDesign" class="el-popup-placeholder">
    <span>📋</span>
    <span>{{ title || '弹窗' }}</span>
  </div>
  <ElDialog
    v-else
    v-model="inner"
    :title="title ?? ''"
    :width="dialogWidth"
    :close-on-click-modal="closeOnOutsideClick !== false"
    :show-close="showCloseButton !== false"
    :draggable="dragEnabled"
    :modal="shading !== false"
    @open="() => { emit('showing'); emit('shown') }"
    @close="() => { emit('hiding'); emit('hidden') }"
  >
    <slot />
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </ElDialog>
</template>

<style scoped>
.el-popup-placeholder {
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