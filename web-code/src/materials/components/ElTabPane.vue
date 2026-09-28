<script setup lang="ts">
/**
 * 标签页面板组件 — 对齐 Element Plus <el-tab-pane>
 * 作为 el-tabs 的子节点使用，每个面板是一个独立的可拖拽内容区域
 *
 * 属性：
 * - label: 标签显示文字
 * - name: 标签唯一标识
 */
import { computed, inject, type Ref } from 'vue'

const props = defineProps<{
  label?: string
  name?: string
  padding?: string
  background?: string
}>()

const paneStyle = computed(() => {
  const style: Record<string, string> = {}
  if (props.padding) style.padding = props.padding
  if (props.background) style.background = props.background
  return style
})

// 从父级 ElTabs 注入激活状态
const activeName = inject<Ref<string> | null>('tabsActiveName', null)

const isActive = computed(() => {
  if (!activeName) return true // 不在 tabs 内时默认显示
  return activeName.value === props.name
})
</script>

<template>
  <div class="lc-el-tab-pane" :class="{ 'is-active': isActive }" :style="paneStyle">
    <slot />
  </div>
</template>

<style scoped>
.lc-el-tab-pane {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  min-height: 40px;
  position: relative;
  overflow: visible;
}

/* 内容区域支持自由定位 */
:deep(.container-content) {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: visible;
}
</style>
