<script setup lang="ts">
import { ElButtonGroup } from 'element-plus'
import { useSlots, computed } from 'vue'
import { designerStore } from '@/store/designer'

defineProps<{
  readOnly?: boolean
  disabled?: boolean
}>()

const slots = useSlots()
const isDesign = computed(() => !designerStore.previewMode)
const hasContent = computed(() => !!slots.default)
</script>

<template>
  <ElButtonGroup>
    <slot />
    <span v-if="isDesign && !hasContent" class="el-btn-group-empty">按钮组（请添加子按钮）</span>
  </ElButtonGroup>
</template>

<style scoped>
.el-btn-group-empty {
  display: inline-block;
  padding: 6px 14px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-light);
}
</style>