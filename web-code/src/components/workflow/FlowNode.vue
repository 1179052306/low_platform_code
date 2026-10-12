<script setup lang="ts">
import { computed } from 'vue'
import { t } from '@/store/lang'
import type { WorkflowNode } from '@/types/workflow'
import { NODE_TYPE_LIST } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowNode
  selected?: boolean
}>()

const emit = defineEmits<{
  (e: 'select', node: WorkflowNode): void
  (e: 'startDrag', node: WorkflowNode, ev: MouseEvent): void
  (e: 'startConnect', node: WorkflowNode, ev: MouseEvent): void
}>()

const meta = computed(
  () => NODE_TYPE_LIST.find((m) => m.type === props.node.type) || NODE_TYPE_LIST[0],
)

const isTerminal = computed(
  () => props.node.type === 'start' || props.node.type === 'end',
)
</script>

<template>
  <div
    class="flow-node"
    :class="[`flow-node--${node.type}`, { selected, terminal: isTerminal }]"
    :style="{ '--node-color': meta.color }"
    @click.stop="emit('select', node)"
    @mousedown.stop="emit('startDrag', node, $event)"
  >
    <div class="flow-node-bar"></div>
    <span class="flow-node-icon">{{ meta.icon }}</span>
    <span class="flow-node-name">{{ node.name || t('未命名') }}</span>
    <!-- 右侧连接点（end 节点无出口） -->
    <div
      v-if="node.type !== 'end'"
      class="flow-node-port flow-node-port--out"
      @mousedown.stop="emit('startConnect', node, $event)"
    >
      <div class="flow-node-port-dot"></div>
    </div>
    <!-- 左侧连接点（start 节点无入口） -->
    <div
      v-if="node.type !== 'start'"
      class="flow-node-port flow-node-port--in"
    >
      <div class="flow-node-port-dot"></div>
    </div>
  </div>
</template>

<style scoped>
.flow-node {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px 8px 10px;
  border: 1.5px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  cursor: grab;
  user-select: none;
  transition: box-shadow 0.15s, border-color 0.15s;
  min-width: 90px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.flow-node:hover {
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.12);
  border-color: var(--node-color);
}
.flow-node.selected {
  border-color: var(--node-color);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--node-color) 30%, transparent);
}
.flow-node:active {
  cursor: grabbing;
}
.flow-node.terminal {
  border-radius: 20px;
}
.flow-node-bar {
  width: 4px;
  height: 20px;
  border-radius: 2px;
  background: var(--node-color);
  flex-shrink: 0;
}
.flow-node-icon {
  font-size: 15px;
  line-height: 1;
}
.flow-node-name {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
}
.flow-node-port {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: crosshair;
}
.flow-node-port--out {
  right: -8px;
}
.flow-node-port--in {
  left: -8px;
}
.flow-node-port-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--node-color);
  transition: background 0.15s;
}
.flow-node-port:hover .flow-node-port-dot {
  background: var(--node-color);
}
</style>