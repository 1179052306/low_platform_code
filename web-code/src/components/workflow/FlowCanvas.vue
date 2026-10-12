<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElButton, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import type {
  WorkflowDef,
  WorkflowNode,
  WorkflowTransition,
  WorkflowNodeType,
} from '@/types/workflow'
import { NODE_TYPE_LIST } from '@/types/workflow'
import FlowNode from './FlowNode.vue'

const props = defineProps<{
  workflow: WorkflowDef
}>()

const emit = defineEmits<{
  (e: 'update', workflow: WorkflowDef): void
  (e: 'select', node: WorkflowNode | null): void
}>()

const NODE_W = 120
const NODE_H = 36

const localWf = ref<WorkflowDef>(JSON.parse(JSON.stringify(props.workflow)))
const selectedNodeId = ref<string | null>(null)
const selectedTransId = ref<string | null>(null)

/** 画布平移与缩放 */
const panX = ref(0)
const panY = ref(0)
const zoom = ref(1)
const canvasRef = ref<HTMLElement | null>(null)

watch(
  () => props.workflow,
  (wf) => {
    localWf.value = JSON.parse(JSON.stringify(wf))
  },
)

/** 确保节点有坐标，没有则自动网格布局 */
function ensurePositions() {
  const nodes = localWf.value.nodes
  nodes.forEach((n, idx) => {
    if (n.x == null || n.y == null) {
      n.x = 80 + Math.floor(idx / 8) * 220
      n.y = 40 + (idx % 8) * 90
    }
  })
}
ensurePositions()
watch(() => props.workflow, ensurePositions)

function emitUpdate() {
  emit('update', JSON.parse(JSON.stringify(localWf.value)))
}

function selectNode(node: WorkflowNode | null) {
  selectedNodeId.value = node?.id || null
  selectedTransId.value = null
  emit('select', node)
}

function selectTransition(trId: string) {
  selectedTransId.value = trId
  selectedNodeId.value = null
  emit('select', null)
}

/** 屏幕坐标 → 画布坐标 */
function screenToCanvas(screenX: number, screenY: number): { x: number; y: number } {
  const rect = canvasRef.value!.getBoundingClientRect()
  return {
    x: (screenX - rect.left - panX.value) / zoom.value,
    y: (screenY - rect.top - panY.value) / zoom.value,
  }
}

/** 从工具箱拖入节点 */
function onDragOver(ev: DragEvent) {
  ev.preventDefault()
  ev.dataTransfer!.dropEffect = 'copy'
}
function onDrop(ev: DragEvent) {
  ev.preventDefault()
  const type = ev.dataTransfer!.getData('application/x-workflow-node') as WorkflowNodeType
  if (!type) return
  const pos = screenToCanvas(ev.clientX, ev.clientY)
  const meta = NODE_TYPE_LIST.find((m) => m.type === type)!
  const node: WorkflowNode = {
    id: `node_${Date.now()}`,
    type,
    name: t(meta.label),
    x: Math.round(pos.x - NODE_W / 2),
    y: Math.round(pos.y - NODE_H / 2),
  }
  if (type === 'userTask') node.approverRoles = []
  if (type === 'gateway' || type === 'exceptionGateway') node.gatewayType = 'exclusive'
  localWf.value.nodes.push(node)
  selectNode(node)
  emitUpdate()
}

/** 拖拽移动节点 */
let dragMoveData: { nodeId: string; startCX: number; startCY: number; startX: number; startY: number } | null = null
function onNodeStartDrag(node: WorkflowNode, ev: MouseEvent) {
  dragMoveData = {
    nodeId: node.id,
    startCX: ev.clientX,
    startCY: ev.clientY,
    startX: node.x || 0,
    startY: node.y || 0,
  }
  selectNode(node)
  document.addEventListener('mousemove', onMoveNode)
  document.addEventListener('mouseup', onMoveEnd)
}
function onMoveNode(ev: MouseEvent) {
  if (!dragMoveData) return
  const node = localWf.value.nodes.find((n) => n.id === dragMoveData!.nodeId)
  if (!node) return
  node.x = Math.round(dragMoveData.startX + (ev.clientX - dragMoveData.startCX) / zoom.value)
  node.y = Math.round(dragMoveData.startY + (ev.clientY - dragMoveData.startCY) / zoom.value)
}
function onMoveEnd() {
  if (dragMoveData) {
    emitUpdate()
    dragMoveData = null
  }
  document.removeEventListener('mousemove', onMoveNode)
  document.removeEventListener('mouseup', onMoveEnd)
}

/** 从端口拖拽创建连线 */
const connectData = ref<{ fromId: string; curX: number; curY: number } | null>(null)
function onNodeStartConnect(node: WorkflowNode, ev: MouseEvent) {
  ev.preventDefault()
  const sx = (node.x || 0) + NODE_W
  const sy = (node.y || 0) + NODE_H / 2
  connectData.value = { fromId: node.id, curX: sx, curY: sy }
  document.addEventListener('mousemove', onConnectMove)
  document.addEventListener('mouseup', onConnectEnd)
}
function onConnectMove(ev: MouseEvent) {
  if (!connectData.value) return
  const pos = screenToCanvas(ev.clientX, ev.clientY)
  connectData.value.curX = pos.x
  connectData.value.curY = pos.y
}
function onConnectEnd(ev: MouseEvent) {
  document.removeEventListener('mousemove', onConnectMove)
  document.removeEventListener('mouseup', onConnectEnd)
  if (!connectData.value) return
  const target = (ev.target as HTMLElement)?.closest('[data-node-id]') as HTMLElement | null
  const toId = target?.dataset.nodeId
  if (toId && toId !== connectData.value.fromId) {
    const exists = localWf.value.transitions.some(
      (tr) => tr.from === connectData.value!.fromId && tr.to === toId,
    )
    if (!exists) {
      localWf.value.transitions.push({
        id: `tr_${Date.now()}`,
        from: connectData.value.fromId,
        to: toId,
      })
      emitUpdate()
    }
  }
  connectData.value = null
}

/** 临时连线路径 */
const tempConnectPath = computed(() => {
  if (!connectData.value) return null
  const from = localWf.value.nodes.find((n) => n.id === connectData.value!.fromId)
  if (!from) return null
  const fromX = from.x || 0
  const fromY = from.y || 0
  const ex = connectData.value.curX
  const ey = connectData.value.curY
  const dx = Math.abs(ex - (fromX + NODE_W / 2))
  const dy = Math.abs(ey - (fromY + NODE_H / 2))
  if (dy > dx) {
    const sx = fromX + NODE_W / 2
    const sy = fromY + NODE_H
    return `M ${sx} ${sy} C ${sx} ${sy + 40}, ${ex} ${ey - 40}, ${ex} ${ey}`
  }
  const sx = fromX + NODE_W
  const sy = fromY + NODE_H / 2
  return `M ${sx} ${sy} C ${sx + 40} ${sy}, ${ex - 40} ${ey}, ${ex} ${ey}`
})

/** 连线路径计算 */
const transitionPaths = computed(() => {
  return localWf.value.transitions
    .map((tr) => {
      const from = localWf.value.nodes.find((n) => n.id === tr.from)
      const to = localWf.value.nodes.find((n) => n.id === tr.to)
      if (!from || !to) return null
      const fromX = from.x || 0
      const fromY = from.y || 0
      const toX = to.x || 0
      const toY = to.y || 0
      const dx = Math.abs(toX - fromX)
      const dy = Math.abs(toY - fromY)
      let sx: number, sy: number, ex: number, ey: number, path: string
      if (dy > dx) {
        sx = fromX + NODE_W / 2
        sy = fromY + NODE_H
        ex = toX + NODE_W / 2
        ey = toY
        path = `M ${sx} ${sy} C ${sx} ${sy + 40}, ${ex} ${ey - 40}, ${ex} ${ey}`
      } else {
        sx = fromX + NODE_W
        sy = fromY + NODE_H / 2
        ex = toX
        ey = toY + NODE_H / 2
        path = `M ${sx} ${sy} C ${sx + 40} ${sy}, ${ex - 40} ${ey}, ${ex} ${ey}`
      }
      return {
        id: tr.id,
        from,
        to,
        path,
        midX: (sx + ex) / 2,
        midY: (sy + ey) / 2,
        label: tr.label || tr.condition,
      }
    })
    .filter(Boolean) as NonNullable<ReturnType<typeof Object>>[]
})

/** 画布平移 */
let panData: { startCX: number; startCY: number; startPX: number; startPY: number } | null = null
function onCanvasMouseDown(ev: MouseEvent) {
  if (ev.target === ev.currentTarget || (ev.target as HTMLElement).classList.contains('flow-canvas-grid')) {
    selectNode(null)
    selectedTransId.value = null
    panData = { startCX: ev.clientX, startCY: ev.clientY, startPX: panX.value, startPY: panY.value }
    document.addEventListener('mousemove', onPanMove)
    document.addEventListener('mouseup', onPanEnd)
  }
}
function onPanMove(ev: MouseEvent) {
  if (!panData) return
  panX.value = panData.startPX + (ev.clientX - panData.startCX)
  panY.value = panData.startPY + (ev.clientY - panData.startCY)
}
function onPanEnd() {
  panData = null
  document.removeEventListener('mousemove', onPanMove)
  document.removeEventListener('mouseup', onPanEnd)
}

/** 滚轮缩放 */
function onWheel(ev: WheelEvent) {
  ev.preventDefault()
  const rect = canvasRef.value!.getBoundingClientRect()
  const mx = ev.clientX - rect.left
  const my = ev.clientY - rect.top
  const delta = ev.deltaY > 0 ? 0.9 : 1.1
  const newZoom = Math.max(0.3, Math.min(2.5, zoom.value * delta))
  panX.value = mx - (mx - panX.value) * (newZoom / zoom.value)
  panY.value = my - (my - panY.value) * (newZoom / zoom.value)
  zoom.value = newZoom
}

/** 删除选中节点 */
function deleteSelectedNode() {
  if (!selectedNodeId.value) return
  const id = selectedNodeId.value
  if (id === 'start' || id === 'end') {
    ElMessage.warning(t('开始/结束节点不能删除'))
    return
  }
  localWf.value.nodes = localWf.value.nodes.filter((n) => n.id !== id)
  localWf.value.transitions = localWf.value.transitions.filter(
    (tr) => tr.from !== id && tr.to !== id,
  )
  selectedNodeId.value = null
  emit('select', null)
  emitUpdate()
}

/** 删除选中连线 */
function deleteSelectedTransition() {
  if (!selectedTransId.value) return
  localWf.value.transitions = localWf.value.transitions.filter(
    (tr) => tr.id !== selectedTransId.value,
  )
  selectedTransId.value = null
  emitUpdate()
}

/** 键盘删除 */
function onKeyDown(ev: KeyboardEvent) {
  if (ev.key === 'Delete' || ev.key === 'Backspace') {
    const tag = (ev.target as HTMLElement)?.tagName
    if (tag === 'INPUT' || tag === 'TEXTAREA') return
    if (selectedNodeId.value) {
      ev.preventDefault()
      deleteSelectedNode()
    } else if (selectedTransId.value) {
      ev.preventDefault()
      deleteSelectedTransition()
    }
  }
}

onMounted(() => document.addEventListener('keydown', onKeyDown))
onUnmounted(() => document.removeEventListener('keydown', onKeyDown))

/** 适应画布（重置缩放和平移） */
function fitCanvas() {
  panX.value = 20
  panY.value = 20
  zoom.value = 1
}

const selectedNode = computed(
  () => localWf.value.nodes.find((n) => n.id === selectedNodeId.value) || null,
)
</script>

<template>
  <div class="flow-canvas">
    <div class="flow-canvas-toolbar">
      <ElButton size="small" @click="fitCanvas">{{ t('适应画布') }}</ElButton>
      <span class="flow-canvas-zoom">{{ Math.round(zoom * 100) }}%</span>
      <ElButton size="small" :disabled="!selectedNodeId" type="danger" @click="deleteSelectedNode">
        {{ t('删除节点') }}
      </ElButton>
      <ElButton size="small" :disabled="!selectedTransId" type="danger" @click="deleteSelectedTransition">
        {{ t('删除连线') }}
      </ElButton>
    </div>
    <div
      ref="canvasRef"
      class="flow-canvas-area"
      @dragover="onDragOver"
      @drop="onDrop"
      @mousedown="onCanvasMouseDown"
      @wheel="onWheel"
    >
      <div class="flow-canvas-grid" :style="{ transform: `translate(${panX}px, ${panY}px) scale(${zoom})` }">
        <svg class="flow-canvas-svg" :width="4000" :height="3000">
          <defs>
            <marker id="arrow" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto">
              <polygon points="0 0, 10 4, 0 8" fill="#909399" />
            </marker>
            <marker id="arrow-sel" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto">
              <polygon points="0 0, 10 4, 0 8" fill="#409eff" />
            </marker>
          </defs>
          <!-- 连线 -->
          <g v-for="trp in transitionPaths" :key="trp.id">
            <path
              :d="trp.path"
              :stroke="trp.id === selectedTransId ? '#409eff' : '#909399'"
              :stroke-width="trp.id === selectedTransId ? 2.5 : 1.8"
              fill="none"
              :marker-end="trp.id === selectedTransId ? 'url(#arrow-sel)' : 'url(#arrow)'"
              style="pointer-events: stroke; cursor: pointer"
              @click.stop="selectTransition(trp.id)"
            />
            <text
              v-if="trp.label"
              :x="trp.midX"
              :y="trp.midY - 6"
              fill="#e6a23c"
              font-size="11"
              text-anchor="middle"
            >
              {{ trp.label }}
            </text>
          </g>
          <!-- 临时连线 -->
          <path
            v-if="tempConnectPath"
            :d="tempConnectPath"
            stroke="#409eff"
            stroke-width="2"
            stroke-dasharray="5 3"
            fill="none"
          />
        </svg>
        <!-- 节点 -->
        <div
          v-for="node in localWf.nodes"
          :key="node.id"
          class="flow-canvas-node-wrapper"
          :data-node-id="node.id"
          :style="{ left: `${node.x}px`, top: `${node.y}px`, width: `${NODE_W}px` }"
        >
          <FlowNode
            :node="node"
            :selected="node.id === selectedNodeId"
            @select="selectNode"
            @start-drag="onNodeStartDrag"
            @start-connect="onNodeStartConnect"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.flow-canvas {
  display: flex;
  flex-direction: column;
  height: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
  overflow: hidden;
}
.flow-canvas-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
  flex-shrink: 0;
}
.flow-canvas-zoom {
  font-size: 12px;
  color: #909399;
  min-width: 40px;
}
.flow-canvas-area {
  flex: 1;
  position: relative;
  overflow: hidden;
  cursor: grab;
  background: #f5f7fa;
  background-image:
    radial-gradient(circle, #dcdfe6 1px, transparent 1px);
  background-size: 20px 20px;
}
.flow-canvas-area:active {
  cursor: grabbing;
}
.flow-canvas-grid {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
}
.flow-canvas-svg {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
  overflow: visible;
}

.flow-canvas-node-wrapper {
  position: absolute;
}
</style>