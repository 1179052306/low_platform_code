<script setup lang="ts">
/**
 * 分割面板容器组件 — 类似 Element Plus Splitter
 * 子节点为 el-splitter-panel，属性面板可配置面板个数、size/min/max/collapsible
 *
 * 拖拽手柄和键盘操作由子组件 ElSplitterPanel 自行处理，
 * 本组件仅负责容器布局和样式。
 */
defineProps<{
  layout?: string
}>()
</script>

<template>
  <div
    class="lc-splitter"
    :class="{ 'lc-splitter-vertical': layout === 'vertical' }"
  >
    <slot />
  </div>
</template>

<style scoped>
.lc-splitter {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  overflow: visible;
}

.lc-splitter :deep(.container-content) {
  display: flex;
  flex-direction: row;
  gap: 0;
  width: 100%;
  height: 100%;
  min-height: 40px;
  min-width: 40px;
  position: relative;
  overflow: visible;
}

.lc-splitter-vertical :deep(.container-content) {
  flex-direction: column;
}

.lc-splitter :deep(.canvas-node) {
  position: relative;
  min-height: 32px;
  min-width: 32px;
}
</style>
