<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElButton, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElMessage, ElMessageBox, ElDialog } from 'element-plus'
import DxDataGrid, { DxColumn, DxToolbar, DxItem } from 'devextreme-vue/data-grid'
import DxButton from 'devextreme-vue/button'
import { t } from '@/store/lang'
import type { WorkflowDef, WorkflowNode, WorkflowType } from '@/types/workflow'
import { listWorkflows, saveWorkflow, deleteWorkflow, validateWorkflow, publishWorkflow } from '@/store/workflow'
import { httpRequest } from '@/utils/request'
import FlowCanvas from '@/components/workflow/FlowCanvas.vue'
import FlowNodePalette from '@/components/workflow/FlowNodePalette.vue'
import FlowPropertyPanel from '@/components/workflow/FlowPropertyPanel.vue'

const workflowList = ref<WorkflowDef[]>([])
const selectedWf = ref<WorkflowDef | undefined>(undefined)
const showEditDialog = ref(false)
const editingWf = ref<WorkflowDef | null>(null)
const roleList = ref<string[]>([])
const billList = ref<{ code: string; name: string }[]>([])

const canvasWf = ref<WorkflowDef | null>(null)
const selectedNode = ref<WorkflowNode | null>(null)

const route = useRoute()
const isBizFlowMode = computed(() => route.name === 'biz-flow-designer')
const defaultWorkflowType = computed<WorkflowType>(() => isBizFlowMode.value ? 'bizFlow' : 'approvalFlow')

async function refresh() {
  try {
    const all = await listWorkflows()
    workflowList.value = all.filter((w) => w.workflowType === defaultWorkflowType.value)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
    workflowList.value = []
  }
}

async function loadRoles() {
  try {
    const res = await httpRequest('/api/lowcode/role/list', { method: 'POST' })
    if (res.ok) {
      const body = res.data as { data?: Array<{ id: string; name: string }> }
      roleList.value = (body.data || []).map((r) => r.name)
    }
  } catch {
    roleList.value = []
  }
}

async function loadBillList() {
  try {
    const res = await httpRequest('/api/lowcode/biz-schema/list', { method: 'POST' })
    if (res.ok) {
      const body = res.data as { data?: Array<{ code: string; name: string }> }
      billList.value = body.data || []
    }
  } catch {
    billList.value = []
  }
}

onMounted(() => {
  refresh()
  loadRoles()
  loadBillList()
})

function onSelectionChanged(e: { selectedRowsData: WorkflowDef[] }) {
  const wf = e.selectedRowsData.length > 0 ? e.selectedRowsData[0] : undefined
  selectedWf.value = wf
  if (wf) {
    canvasWf.value = JSON.parse(JSON.stringify(wf))
    selectedNode.value = null
  } else {
    canvasWf.value = null
  }
}

function addWf() {
  editingWf.value = {
    id: '',
    code: '',
    name: '',
    nodes: [
      { id: 'start', type: 'start', name: '开始', x: 100, y: 40 },
      { id: 'end', type: 'end', name: '结束', x: 100, y: 220 },
    ],
    transitions: [],
    enabled: true,
    workflowType: defaultWorkflowType.value,
    params: [],
    control: { startByBillAction: [], startByEvent: [] },
  }
  showEditDialog.value = true
}

async function onSaveNewWf() {
  if (!editingWf.value) return
  if (!editingWf.value.code.trim() || !editingWf.value.name.trim()) {
    ElMessage.warning(t('编码和名称不能为空'))
    return
  }
  try {
    const wf = { ...editingWf.value, id: editingWf.value.id || `wf_${Date.now()}` }
    await saveWorkflow(wf)
    await refresh()
    showEditDialog.value = false
    ElMessage.success(t('保存成功'))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}

async function onDeleteWf() {
  if (!selectedWf.value) return
  try {
    await ElMessageBox.confirm(t('确认删除此流程？'), t('提示'), { type: 'warning' })
    await deleteWorkflow(selectedWf.value.id)
    await refresh()
    canvasWf.value = null
    selectedWf.value = undefined
    ElMessage.success(t('删除成功'))
  } catch {
    /* 取消 */
  }
}

function onCanvasUpdate(wf: WorkflowDef) {
  canvasWf.value = wf
}

function onNodeSelect(node: WorkflowNode | null) {
  selectedNode.value = node
}

function onNodeUpdate(node: WorkflowNode) {
  if (!canvasWf.value) return
  const idx = canvasWf.value.nodes.findIndex((n) => n.id === node.id)
  if (idx >= 0) {
    canvasWf.value.nodes[idx] = node
    selectedNode.value = node
  }
}

function onWorkflowUpdate(wf: WorkflowDef) {
  canvasWf.value = wf
}

function onNodeDelete() {
  if (!canvasWf.value || !selectedNode.value) return
  const nodeId = selectedNode.value.id
  if (nodeId === 'start' || nodeId === 'end') {
    ElMessage.warning(t('开始/结束节点不能删除'))
    return
  }
  canvasWf.value.nodes = canvasWf.value.nodes.filter((n) => n.id !== nodeId)
  canvasWf.value.transitions = canvasWf.value.transitions.filter(
    (tr) => tr.from !== nodeId && tr.to !== nodeId,
  )
  selectedNode.value = null
}

async function saveCanvas() {
  if (!canvasWf.value) return
  try {
    await saveWorkflow(canvasWf.value)
    await refresh()
    ElMessage.success(t('保存成功'))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}

/** 前端流程校验（即时反馈） */
function validateWorkflowLocal(): string[] {
  const errors: string[] = []
  if (!canvasWf.value) return [t('无流程数据')]
  const wf = canvasWf.value
  if (!wf.code.trim()) errors.push(t('流程编码不能为空'))
  if (!wf.name.trim()) errors.push(t('流程名称不能为空'))
  const hasStart = wf.nodes.some((n) => n.type === 'start')
  const hasEnd = wf.nodes.some((n) => n.type === 'end')
  if (!hasStart) errors.push(t('缺少开始节点'))
  if (!hasEnd) errors.push(t('缺少结束节点'))
  for (const n of wf.nodes) {
    if (n.type === 'userTask') {
      const hasApprover = (n.approverRoles?.length || 0) + (n.approverUsers?.length || 0) > 0
      if (!hasApprover) errors.push(t(`人工节点"${n.name}"未配置审批人`))
    }
  }
  for (const n of wf.nodes) {
    if (n.type !== 'end') {
      const hasOut = wf.transitions.some((tr) => tr.from === n.id)
      if (!hasOut) errors.push(t(`节点"${n.name}"没有出口连线`))
    }
    if (n.type !== 'start') {
      const hasIn = wf.transitions.some((tr) => tr.to === n.id)
      if (!hasIn) errors.push(t(`节点"${n.name}"没有入口连线`))
    }
  }
  return errors
}

async function onValidate() {
  if (!canvasWf.value) return
  try {
    const errors = await validateWorkflow(canvasWf.value)
    if (errors.length === 0) {
      ElMessage.success(t('校验通过'))
    } else {
      ElMessageBox.alert(errors.join('\n'), t('校验失败'), { type: 'warning' })
    }
  } catch (err) {
    const localErrors = validateWorkflowLocal()
    if (localErrors.length === 0) {
      ElMessage.success(t('校验通过'))
    } else {
      ElMessageBox.alert(localErrors.join('\n'), t('校验失败'), { type: 'warning' })
    }
  }
}

async function onPublish() {
  if (!canvasWf.value) return
  try {
    await ElMessageBox.confirm(t('确认发布此流程？发布后将生效。'), t('提示'), { type: 'warning' })
    await publishWorkflow(canvasWf.value)
    await refresh()
    ElMessage.success(t('发布成功'))
  } catch (err) {
    if (err === 'cancel' || (err instanceof Error && err.message === 'cancel')) return
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}

const hasCanvas = computed(() => !!canvasWf.value)

/** 左分割条（流程列表 | 中栏） */
const leftWidth = ref(280)
const isLeftDragging = ref(false)
function onLeftSplitMouseDown(e: MouseEvent): void {
  e.preventDefault()
  isLeftDragging.value = true
  const startX = e.clientX
  const startW = leftWidth.value
  const onMove = (ev: MouseEvent): void => {
    leftWidth.value = Math.max(200, Math.min(450, startW + ev.clientX - startX))
  }
  const onUp = (): void => {
    isLeftDragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

/** 右分割条（中栏 | 属性面板） */
const rightWidth = ref(340)
const isRightDragging = ref(false)
function onRightSplitMouseDown(e: MouseEvent): void {
  e.preventDefault()
  isRightDragging.value = true
  const startX = e.clientX
  const startW = rightWidth.value
  const onMove = (ev: MouseEvent): void => {
    rightWidth.value = Math.max(260, Math.min(500, startW - ev.clientX + startX))
  }
  const onUp = (): void => {
    isRightDragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

const workflowTypes: { value: WorkflowType; label: string }[] = [
  { value: 'bizFlow', label: '业务流' },
  { value: 'approvalFlow', label: '审批流' },
  { value: 'dataFlow', label: '数据流' },
]
</script>

<template>
  <div class="wf-designer">
    <!-- 左栏：流程列表 -->
    <div class="wf-list-panel" :style="{ width: leftWidth + 'px' }">
      <DxDataGrid
        :data-source="workflowList"
        :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
        :hover-state-enabled="true"
        :show-borders="true"
        :show-column-lines="false"
        :show-row-lines="true"
        :search-panel="{ visible: true, width: 120, placeholder: '搜索...' }"
        :filter-row="{ visible: true, showOperationChooser: false }"
        :paging="{ enabled: false }"
        height="100%"
        key-expr="id"
        @selection-changed="onSelectionChanged"
      >
        <DxColumn data-field="code" :caption="t('编码')" :width="100" />
        <DxColumn data-field="name" :caption="t('名称')" />
        <DxColumn data-field="enabled" :caption="t('启用')" :width="50" data-type="boolean" />
        <DxToolbar>
          <DxItem location="after">
            <template #default>
              <DxButton :text="t('新建流程')" type="default" @click="addWf" />
            </template>
          </DxItem>
          <DxItem location="after">
            <template #default>
              <DxButton :text="t('删除')" :disabled="!selectedWf" @click="onDeleteWf" />
            </template>
          </DxItem>
        </DxToolbar>
      </DxDataGrid>
    </div>

    <!-- 左分割条 -->
    <div class="wf-splitter" :class="{ dragging: isLeftDragging }" @mousedown="onLeftSplitMouseDown">
      <div class="wf-splitter-grip"></div>
    </div>

    <!-- 中栏：工具箱 + 画布 -->
    <div class="wf-center-panel">
      <div v-if="!hasCanvas" class="wf-empty">
        {{ t('请从左侧选择或新建流程') }}
      </div>
      <template v-else>
        <!-- 顶部工具栏 -->
        <div class="wf-editor-toolbar">
          <span class="wf-editor-title">{{ canvasWf!.name }} ({{ canvasWf!.code }})</span>
          <div class="wf-editor-actions">
            <ElButton size="small" @click="onValidate">{{ t('校验') }}</ElButton>
            <ElButton size="small" @click="onPublish">{{ t('发布') }}</ElButton>
            <ElButton type="primary" size="small" @click="saveCanvas">{{ t('保存') }}</ElButton>
          </div>
        </div>
        <div class="wf-editor-body">
          <!-- 节点工具箱 -->
          <div class="wf-palette-area">
            <FlowNodePalette :workflow-type="canvasWf?.workflowType" />
          </div>
          <!-- 画布 -->
          <div class="wf-canvas-area">
            <FlowCanvas
              :workflow="canvasWf!"
              @update="onCanvasUpdate"
              @select="onNodeSelect"
            />
          </div>
        </div>
      </template>
    </div>

    <!-- 右分割条 -->
    <div class="wf-splitter" :class="{ dragging: isRightDragging }" @mousedown="onRightSplitMouseDown">
      <div class="wf-splitter-grip"></div>
    </div>

    <!-- 右栏：属性面板 -->
    <div class="wf-prop-panel" :style="{ width: rightWidth + 'px' }">
      <template v-if="hasCanvas">
        <FlowPropertyPanel
          :workflow="canvasWf!"
          :node="selectedNode"
          :roles="roleList"
          :bill-list="billList"
          @update-node="onNodeUpdate"
          @update-workflow="onWorkflowUpdate"
          @delete-node="onNodeDelete"
        />
      </template>
      <div v-else class="wf-prop-empty">{{ t('属性面板') }}</div>
    </div>

    <!-- 新建流程弹窗 -->
    <ElDialog v-model="showEditDialog" :title="t('新建流程')" width="480px" append-to-body>
      <ElForm v-if="editingWf" label-width="80px">
        <ElFormItem :label="t('编码')">
          <ElInput v-model="editingWf.code" :placeholder="t('如 order_approval')" />
        </ElFormItem>
        <ElFormItem :label="t('名称')">
          <ElInput v-model="editingWf.name" :placeholder="t('如 订单审批流程')" />
        </ElFormItem>
        <ElFormItem :label="t('类型')">
          <ElSelect v-model="editingWf.workflowType" style="width: 100%">
            <ElOption v-for="w in workflowTypes" :key="w.value" :label="t(w.label)" :value="w.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('启用')">
          <ElSelect v-model="editingWf.enabled" style="width: 100px">
            <ElOption :label="t('是')" :value="true" />
            <ElOption :label="t('否')" :value="false" />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showEditDialog = false">{{ t('取消') }}</ElButton>
        <ElButton type="primary" @click="onSaveNewWf">{{ t('保存') }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.wf-designer {
  display: flex;
  height: 100%;
  padding: 8px;
  background: #f0f2f5;
  gap: 0;
}
.wf-list-panel {
  flex-shrink: 0;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
}
.wf-splitter {
  width: 8px;
  flex-shrink: 0;
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 10;
}
.wf-splitter::before {
  content: '';
  width: 2px;
  height: 100%;
  background: #dcdfe6;
  border-radius: 1px;
  transition: background 0.15s;
}
.wf-splitter:hover::before,
.wf-splitter.dragging::before {
  background: #409eff;
}
.wf-splitter-grip {
  width: 4px;
  height: 32px;
  background: #c0c4cc;
  border-radius: 2px;
  position: absolute;
  transition: background 0.15s;
}
.wf-splitter:hover .wf-splitter-grip,
.wf-splitter.dragging .wf-splitter-grip {
  background: #409eff;
}
.wf-center-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
  min-width: 0;
}
.wf-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 14px;
}
.wf-editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
  flex-shrink: 0;
}
.wf-editor-title {
  font-size: 14px;
  font-weight: 500;
}
.wf-editor-actions {
  display: flex;
  gap: 8px;
}
.wf-editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.wf-palette-area {
  width: 160px;
  flex-shrink: 0;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  background: #fafafa;
}
.wf-canvas-area {
  flex: 1;
  overflow: hidden;
  padding: 8px;
}
.wf-prop-panel {
  flex-shrink: 0;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
}
.wf-prop-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #c0c4cc;
  font-size: 13px;
}
</style>