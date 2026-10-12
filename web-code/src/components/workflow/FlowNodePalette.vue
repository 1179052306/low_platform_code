<script setup lang="ts">
import { computed } from 'vue'
import { t } from '@/store/lang'
import { NODE_TYPE_LIST } from '@/types/workflow'
import type { WorkflowNodeType, WorkflowType } from '@/types/workflow'

const props = defineProps<{
  workflowType?: WorkflowType
}>()

const emit = defineEmits<{
  (e: 'dragStart', type: WorkflowNodeType, ev: DragEvent): void
}>()

/** 审批流简化模式：只显示开始/结束/人工 */
const approvalTypes = new Set<WorkflowNodeType>(['start', 'end', 'userTask'])

/** 分组：流程控制 / 任务节点 / 网关 / 集成 */
const allGroups: { label: string; types: WorkflowNodeType[] }[] = [
  { label: '流程控制', types: ['start', 'end'] },
  { label: '任务节点', types: ['userTask', 'billTask', 'autoTask', 'messageTask', 'waitTask'] },
  { label: '网关', types: ['gateway', 'exceptionGateway'] },
  { label: '集成', types: ['subProcess', 'broadcastTask', 'rpaTask'] },
]

const groups = computed(() => {
  if (props.workflowType === 'approvalFlow') {
    return allGroups
      .map((g) => ({ ...g, types: g.types.filter((t) => approvalTypes.has(t)) }))
      .filter((g) => g.types.length > 0)
  }
  return allGroups
})

function onDragStart(type: WorkflowNodeType, ev: DragEvent): void {
  ev.dataTransfer!.setData('application/x-workflow-node', type)
  ev.dataTransfer!.effectAllowed = 'copy'
  emit('dragStart', type, ev)
}
</script>

<template>
  <div class="flow-palette">
    <div class="flow-palette-title">{{ t('节点工具箱') }}</div>
    <div
      v-for="grp in groups"
      :key="grp.label"
      class="flow-palette-group"
    >
      <div class="flow-palette-group-label">{{ t(grp.label) }}</div>
      <div class="flow-palette-items">
        <div
          v-for="type in grp.types"
          :key="type"
          class="flow-palette-item"
          :style="{ '--node-color': NODE_TYPE_LIST.find((m) => m.type === type)!.color }"
          :draggable="NODE_TYPE_LIST.find((m) => m.type === type)!.draggable"
          @dragstart="onDragStart(type, $event)"
        >
          <span class="flow-palette-item-icon">{{ NODE_TYPE_LIST.find((m) => m.type === type)!.icon }}</span>
          <span class="flow-palette-item-label">{{ t(NODE_TYPE_LIST.find((m) => m.type === type)!.label) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.flow-palette {
  padding: 8px 6px;
  overflow-y: auto;
  height: 100%;
}
.flow-palette-title {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  padding: 4px 6px 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.flow-palette-group {
  margin-bottom: 10px;
}
.flow-palette-group-label {
  font-size: 11px;
  color: #c0c4cc;
  padding: 2px 6px 4px;
}
.flow-palette-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.flow-palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
  cursor: grab;
  transition: all 0.15s;
  border-left: 3px solid var(--node-color);
}
.flow-palette-item:hover {
  border-color: var(--node-color);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
  transform: translateX(2px);
}
.flow-palette-item:active {
  cursor: grabbing;
}
.flow-palette-item[draggable="false"] {
  opacity: 0.5;
  cursor: not-allowed;
}
.flow-palette-item[draggable="false"]:hover {
  transform: none;
  box-shadow: none;
}
.flow-palette-item-icon {
  font-size: 14px;
  width: 18px;
  text-align: center;
}
.flow-palette-item-label {
  font-size: 13px;
  color: #303133;
}
</style>