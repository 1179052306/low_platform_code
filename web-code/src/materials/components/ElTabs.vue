<script setup lang="ts">
/**
 * 标签页容器组件 — 对齐 Element Plus <el-tabs>
 * 子节点为 el-tab-pane，每个 pane 是一个可自由拖拽的内容区域
 *
 * 设计器中：点击 tab 标签切换当前编辑的面板，所有面板内容独立编辑
 * 预览模式：正常标签页交互
 */
import { ref, computed, provide, watch } from 'vue'
import type { ComponentSchema } from '@/types/schema'
import { designerStore } from '@/store/designer'

const props = defineProps<{
  type?: string
  tabPosition?: string
  nodeId?: string
  children?: ComponentSchema[]
}>()

const activeName = ref('')

/** 获取子面板列表（从 children 中读取 label/name） */
const panes = computed(() => {
  if (!props.children || !props.children.length) return []
  return props.children.map(child => ({
    name: child.props.name as string || child.id,
    label: child.props.label as string || '标签',
  }))
})

// 初始化激活第一个面板
watch(() => panes.value, (newPanes) => {
  if (newPanes.length > 0 && !activeName.value) {
    activeName.value = newPanes[0].name
  }
  // 如果当前激活的面板被删除了，切到第一个
  if (newPanes.length > 0 && !newPanes.find(p => p.name === activeName.value)) {
    activeName.value = newPanes[0].name
  }
}, { immediate: true, deep: true })

/** 切换标签 */
function switchTab(name: string) {
  activeName.value = name
  // 触发一次 schema 变更，确保重新渲染
  designerStore.schemaRevision++
}

/** 当前激活面板的 name */
const activeTabName = computed(() => activeName.value)

// 向子组件（ElTabPane）提供激活状态
provide('tabsActiveName', activeName)

const tabPositionClass = computed(() => {
  const pos = props.tabPosition || 'top'
  return `tabs-${pos}`
})

const typeClass = computed(() => {
  return props.type ? `tabs-type-${props.type}` : ''
})
</script>

<template>
  <div class="lc-tabs" :class="[tabPositionClass, typeClass]">
    <!-- Tab 标签头 -->
    <div class="tabs-header">
      <div
        v-for="pane in panes"
        :key="pane.name"
        class="tab-item"
        :class="{ 'is-active': activeTabName === pane.name }"
        @click.stop="switchTab(pane.name)"
      >
        {{ pane.label }}
      </div>
      <div class="tabs-active-bar" v-if="panes.length" :style="{
        transform: `translateX(${panes.findIndex(p => p.name === activeTabName) * 100}%)`
      }"></div>
    </div>
    <!-- Tab 内容区域：所有面板都渲染，但只有激活的显示（用于拖拽） -->
    <div class="tabs-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.lc-tabs {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow: hidden;
}

.tabs-header {
  position: relative;
  display: flex;
  flex-wrap: nowrap;
  border-bottom: 1px solid #e4e7ed;
  margin-bottom: -1px;
  padding: 0;
  flex-shrink: 0;
  background: #fff;
}

.tab-item {
  padding: 0 20px;
  height: 40px;
  line-height: 40px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.2s;
  user-select: none;
  position: relative;
}

.tab-item:hover {
  color: #409eff;
}

.tab-item.is-active {
  color: #409eff;
  font-weight: 500;
}

.tabs-active-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 2px;
  background: #409eff;
  transition: transform 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
  width: 0;
  display: none;
}

.tabs-content {
  flex: 1;
  overflow: hidden;
  position: relative;
  min-height: 40px;
}

/* 内容区域中的 container-content（子节点包装） */
.tabs-content :deep(.container-content) {
  width: 100%;
  height: 100%;
  position: relative;
}

/* 非激活的 tab-pane 及其外层 canvas-node 隐藏 */
.tabs-content :deep(.canvas-node:has(> .lc-el-tab-pane:not(.is-active))) {
  display: none;
}
.tabs-content :deep(.lc-el-tab-pane:not(.is-active)) {
  display: none;
}

/* 卡片风格 */
.tabs-type-card .tabs-header {
  border-bottom: none;
  background: #f5f7fa;
}

.tabs-type-card .tab-item {
  border: 1px solid transparent;
  margin-right: 2px;
  border-radius: 4px 4px 0 0;
  background: transparent;
  height: 38px;
  line-height: 38px;
}

.tabs-type-card .tab-item.is-active {
  background: #fff;
  border-color: #e4e7ed;
  border-bottom-color: #fff;
}

/* 底部标签 */
.tabs-bottom {
  flex-direction: column-reverse;
}

.tabs-bottom .tabs-header {
  border-bottom: none;
  border-top: 1px solid #e4e7ed;
  margin-bottom: 0;
  margin-top: -1px;
}

.tabs-bottom .tabs-active-bar {
  bottom: auto;
  top: 0;
}

/* 左侧标签 */
.tabs-left {
  flex-direction: row;
}

.tabs-left .tabs-header {
  flex-direction: column;
  border-bottom: none;
  border-right: 1px solid #e4e7ed;
  margin-bottom: 0;
  margin-right: -1px;
  height: 100%;
}

.tabs-left .tab-item {
  padding: 8px 20px;
  height: auto;
  line-height: 1.5;
}

.tabs-left .tabs-active-bar {
  bottom: auto;
  right: 0;
  left: auto;
  width: 2px;
  height: auto;
  transform: none;
}

/* 右侧标签 */
.tabs-right {
  flex-direction: row-reverse;
}

.tabs-right .tabs-header {
  flex-direction: column;
  border-bottom: none;
  border-left: 1px solid #e4e7ed;
  margin-bottom: 0;
  margin-left: -1px;
  height: 100%;
}

.tabs-right .tab-item {
  padding: 8px 20px;
  height: auto;
  line-height: 1.5;
}

.tabs-right .tabs-active-bar {
  bottom: auto;
  left: 0;
  right: auto;
  width: 2px;
  height: auto;
  transform: none;
}
</style>
