<script setup lang="ts">
/**
 * 栅格列组件 — 基于 Element Plus Layout (el-col)
 * 作为 el-row 的子节点使用，通过 span 控制列宽（24 栅格制）
 * 实际宽度由外层 CanvasNode 的 wrapperStyle 根据 span 计算后应用到 .canvas-node，
 * 此处承载内边距/背景等样式并保留 el-col 语义，便于生成的 Vue 源码与官方 API 对齐
 * （<el-col :span="12">...</el-col>）
 */
defineProps<{
  span?: number
  offset?: number
  padding?: string
  background?: string
}>()
</script>

<template>
  <div class="el-col lc-el-col" :style="{ padding, background }">
    <slot />
  </div>
</template>

<style scoped>
.lc-el-col {
  width: 100%;
  height: auto;
  box-sizing: border-box;
  min-height: 32px;
}
</style>
