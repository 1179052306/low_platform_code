<script setup lang="ts">
/**
 * 折叠面板容器组件 — 对齐 Element Plus <el-collapse>
 * 子节点为 el-collapse-item，每个 item 是一个可折叠的内容区域
 *
 * 设计器中：点击 item 标题切换展开/折叠，展开的 item 内容区可拖拽控件
 * 预览模式：正常折叠面板交互
 */
import { ref, computed, provide, watch } from 'vue'
import type { ComponentSchema } from '@/types/schema'
import { designerStore } from '@/store/designer'

const props = defineProps<{
  accordion?: boolean
  nodeId?: string
  children?: ComponentSchema[]
}>()

/** 展开的折叠项 name 列表 */
const activeNames = ref<string[]>([])

/** 获取子项列表（从 children 中读取 title/name） */
const items = computed(() => {
  if (!props.children || !props.children.length) return []
  return props.children.map(child => ({
    name: child.props.name as string || child.id,
    title: child.props.title as string || '折叠面板',
  }))
})

// 初始化：展开第一项；移除已删除项的展开状态
watch(() => items.value, (newItems) => {
  if (newItems.length > 0 && activeNames.value.length === 0) {
    activeNames.value = [newItems[0].name]
  }
  const validNames = new Set(newItems.map(i => i.name))
  activeNames.value = activeNames.value.filter(n => validNames.has(n))
}, { immediate: true, deep: true })

/** 切换折叠/展开 */
function toggleItem(name: string) {
  if (activeNames.value.includes(name)) {
    activeNames.value = activeNames.value.filter(n => n !== name)
  } else {
    if (props.accordion) {
      activeNames.value = [name]
    } else {
      activeNames.value = [...activeNames.value, name]
    }
  }
  designerStore.schemaRevision++
}

provide('collapseActiveNames', activeNames)
provide('collapseToggle', toggleItem)
</script>

<template>
  <div class="lc-collapse" :class="{ 'lc-collapse-accordion': accordion }">
    <slot />
  </div>
</template>

<style scoped>
.lc-collapse {

  box-sizing: border-box;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
}

.lc-collapse :deep(.container-content) {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 0;
  box-sizing: border-box;
  flex: 1;
  min-height: 0;
  height: auto;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>