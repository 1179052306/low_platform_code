<script setup lang="ts">
/**
 * 折叠面板子项组件 — 对齐 Element Plus <el-collapse-item>
 * 作为 el-collapse 的子节点使用，每个 item 是一个可折叠的内容区域
 *
 * 属性：
 * - title: 折叠项标题
 * - name: 唯一标识
 * - padding: 内容区内边距
 * - background: 内容区背景色
 */
import { computed, inject, type Ref } from 'vue'

const props = defineProps<{
  title?: string
  name?: string
  padding?: string
  background?: string
  height?: string | number
}>()

const contentStyle = computed(() => {
  const style: Record<string, string> = {}
  if (props.padding) style.padding = props.padding
  if (props.background) style.background = props.background
  if (props.height) {
    let h = String(props.height).trim()
    if (/^\d+(\.\d+)?$/.test(h)) h = `${h}px`
    style.height = h
    style.maxHeight = h
    style.overflowY = 'auto'
  }
  return style
})

const activeNames = inject<Ref<string[]> | null>('collapseActiveNames', null)
const toggle = inject<((name: string) => void) | null>('collapseToggle', null)

const isActive = computed(() => {
  if (!activeNames) return true
  return activeNames.value.includes(props.name || '')
})

function onHeaderClick(e: MouseEvent) {
  e.stopPropagation()
  if (toggle) toggle(props.name || '')
}
</script>

<template>
  <div class="lc-el-collapse-item" :class="{ 'is-active': isActive }">
    <div class="collapse-item-header" @click="onHeaderClick">
      <span class="collapse-item-arrow">{{ isActive ? '▼' : '▶' }}</span>
      <span class="collapse-item-title">{{ title || '折叠面板' }}</span>
    </div>
    <div class="collapse-item-content" v-show="isActive" :style="contentStyle">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.lc-el-collapse-item {
  width: 100%;
  box-sizing: border-box;
  position: relative;
  flex-shrink: 0;
}

.collapse-item-header {
  height: 40px;
  line-height: 40px;
  padding: 0 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  cursor: pointer;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #303133;
  transition: background-color 0.2s;
  flex-shrink: 0;
}

.collapse-item-header:hover {
  background: #ecf5ff;
  color: #409eff;
}

.collapse-item-arrow {
  font-size: 12px;
  transition: transform 0.2s;
  display: inline-block;
  width: 14px;
  text-align: center;
}

.collapse-item-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.collapse-item-content {
  width: 100%;
  min-height: 100px;
  box-sizing: border-box;
  position: relative;
  overflow: visible;
  border-bottom: 1px solid #e4e7ed;
}

.collapse-item-content :deep(.container-content) {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: visible;
  min-height: 100px;
}
</style>