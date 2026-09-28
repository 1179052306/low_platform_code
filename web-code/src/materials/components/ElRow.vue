<script setup lang="ts">
/**
 * 栅格布局包装组件 — 基于 Element Plus Layout (el-row / el-col)
 * 子节点（列）通过 defaultChildren 自动创建，属性面板可增删列和调整 span/offset
 */
const props = defineProps<{
  gutter?: number
  rowGap?: number
  justify?: string
  align?: string
  wrap?: boolean
}>()

const rowStyle = {
  columnGap: `${props.gutter ?? 0}px`,
  rowGap: `${props.rowGap ?? props.gutter ?? 0}px`,
  justifyContent: props.justify || 'start',
  alignItems: props.align || 'stretch',
  flexWrap: props.wrap === false ? 'nowrap' as const : 'wrap' as const,
}
</script>

<template>
  <div
    class="el-row lc-el-row"
    :style="rowStyle"
  >
    <slot />
  </div>
</template>

<style scoped>
.lc-el-row {
  width: 100%;
  /* min-height: 100% 让 ElRow 在父容器有明确高度时填满，auto 时由内容决定 */
  min-height: 100%;
  box-sizing: border-box;
}

/* 仅作用于 el-row 直接子级的 container-content（让各 el-col 横向排列并自动换行）；
   实际 gap/flex-direction 由 CanvasNode 的 inline style 控制，这里作为兜底 */
.lc-el-row > :deep(.container-content) {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 0px;
  width: 100%;
  min-height: 40px;
  height: auto;
  box-sizing: border-box;
}

.lc-el-row :deep(.canvas-node) {
  min-height: 32px;
}
</style>
