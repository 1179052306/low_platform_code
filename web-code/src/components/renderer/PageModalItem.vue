<script setup lang="ts">
import { provide, computed, ref, onBeforeUnmount } from 'vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import RendererNode from './RendererNode.vue'
import { createRenderStore, DESIGNER_STORE_KEY, collectAllNodes } from '@/store/designer'
import { pageModal, type PageModalInstance } from './pageModal'
import { gatewayCall } from '@/api'
import { t } from '@/store/lang'
import { getValidator } from '@/materials/dx-validation'
import type { ComponentSchema } from '@/types/schema'

const props = defineProps<{
  modal: PageModalInstance
  stackIndex?: number
}>()

const store = createRenderStore(props.modal.primaryKeyField, props.modal.tableName, props.modal.options.selectMode)
store.schema = props.modal.schema
store.computeRules = props.modal.computeRules
provide(DESIGNER_STORE_KEY, store)

const isDrawer = computed(() => props.modal.options.mode === 'drawer')
const overlayZIndex = computed(() => 10000 + (props.stackIndex ?? 0) * 1000)
const saving = ref(false)

const tableName = computed(() => (props.modal.params?.tableName as string) || '')
const isEdit = computed(() => !!props.modal.params?.rowData)

/** 逐条编辑：多行选中时依次编辑 */
const rowDataList = props.modal.params?.rowDataList as Record<string, unknown>[] | undefined
const editIndex = ref(0)
const isMultiEdit = computed(() => !!rowDataList && rowDataList.length > 1)

/** 从行数据中按 dbField 取值，兼容大小写与 snake_case/camelCase 命名差异 */
function getField(data: Record<string, unknown>, dbField: string): unknown {
  if (data[dbField] !== undefined) return data[dbField]
  const lower = dbField.toLowerCase()
  const upper = dbField.toUpperCase()
  if (data[lower] !== undefined) return data[lower]
  if (data[upper] !== undefined) return data[upper]
  const snake = dbField.replace(/[A-Z]/g, (m) => '_' + m.toLowerCase())
  if (snake !== dbField && data[snake] !== undefined) return data[snake]
  const camel = dbField.replace(/_([a-z])/g, (_, c) => c.toUpperCase())
  if (camel !== dbField && data[camel] !== undefined) return data[camel]
  return undefined
}

/** 递归填充表单控件值 */
function fillFormValues(node: ComponentSchema, data: Record<string, unknown>): void {
  if (node.dbField) {
    const v = getField(data, node.dbField)
    if (v !== undefined) {
      if (node.type === 'select-box') {
        const items = node.props.items as Record<string, unknown>[] | undefined
        const valueExpr = (node.props.valueExpr as string) || 'value'
        if (Array.isArray(items) && items.length > 0) {
          const firstVal = items[0][valueExpr]
          if (typeof firstVal === 'string' && typeof v !== 'string') {
            node.props.value = String(v)
          } else if (typeof firstVal === 'number' && typeof v !== 'number') {
            node.props.value = Number(v)
          } else {
            node.props.value = v
          }
        } else {
          node.props.value = v
        }
      } else {
        node.props.value = v
      }
    }
  }
  if (node.children) {
    for (const child of node.children) {
      fillFormValues(child, data)
    }
  }
}

/** 递归收集表单控件值 */
function collectFormValues(node: ComponentSchema, data: Record<string, unknown>): void {
  if (node.dbField && node.props.value !== undefined) {
    data[node.dbField] = node.props.value
  }
  if (node.children) {
    for (const child of node.children) {
      collectFormValues(child, data)
    }
  }
}

// 修改模式：填充表单数据
const rowData = props.modal.params?.rowData as Record<string, unknown> | undefined
if (rowData) {
  for (const node of store.schema) {
    fillFormValues(node, rowData)
  }
}

/** 收集当前全部表单控件值并序列化，用于脏检测 */
function snapshotFormValues(): string {
  const data: Record<string, unknown> = {}
  for (const node of store.schema) {
    collectFormValues(node, data)
  }
  return JSON.stringify(data)
}

/** 表单初始值快照：新增时为空表单，修改时为填充后的行数据 */
const initialFormValues = ref(snapshotFormValues())

/** 表单是否有改动（新增已填写 / 修改已变更） */
function isDirty(): boolean {
  return snapshotFormValues() !== initialFormValues.value
}

/** 逐条编辑：切换到指定行 */
function switchRow(index: number): void {
  if (!rowDataList || index < 0 || index >= rowDataList.length) return
  editIndex.value = index
  const data = rowDataList[index]
  for (const node of store.schema) {
    fillFormValues(node, data)
  }
  initialFormValues.value = snapshotFormValues()
}

function prevRow(): void {
  if (editIndex.value > 0) switchRow(editIndex.value - 1)
}

function nextRow(): void {
  if (rowDataList && editIndex.value < rowDataList.length - 1) switchRow(editIndex.value + 1)
}

const isSelectMode = computed(() => props.modal.options.selectMode === true)

async function close() {
  if (!isSelectMode.value && isDirty()) {
    try {
      await ElMessageBox.confirm(
        t('当前内容尚未保存，确认要关闭吗？'),
        t('关闭确认'),
        { confirmButtonText: t('确认'), cancelButtonText: t('取消'), type: 'warning', customClass: 'lc-modal-confirm' },
      )
    } catch {
      return
    }
  }
  pageModal.closePage()
}

async function onSave() {
  if (!tableName.value) {
    ElMessage.warning(t('未配置数据表名'))
    return
  }
  const allNodes = collectAllNodes(store.schema).filter(n => n.fieldName)
  let hasError = false
  for (const node of allNodes) {
    const reg = getValidator(node.fieldName!)
    if (reg && !reg.validate()) hasError = true
  }
  if (hasError) {
    ElMessage.warning(t('表单验证未通过，请检查必填项'))
    return
  }
  const data: Record<string, unknown> = {}
  for (const node of store.schema) {
    collectFormValues(node, data)
  }
  saving.value = true
  try {
    const action = isEdit.value ? 'update' : 'create'
    if (isEdit.value) {
      const currentRow = isMultiEdit.value ? rowDataList![editIndex.value] : rowData
      const pk = store.primaryKeyField || 'id'
      const pkVal = currentRow?.[pk] ?? currentRow?.[pk.toLowerCase()] ?? currentRow?.[pk.toUpperCase()]
      if (pkVal != null) data[pk] = pkVal
    }
    await gatewayCall(tableName.value, action, data)
    ElMessage.success(t('保存成功'))
    if (isMultiEdit.value && editIndex.value < rowDataList!.length - 1) {
      nextRow()
    } else {
      pageModal.closePage({ saved: true })
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  } finally {
    saving.value = false
  }
}

function onConfirmSelect() {
  if (!store.selectedRowData) {
    ElMessage.warning(t('请先选择一行数据'))
    return
  }
  pageModal.closePage(store.selectedRowData)
}

// ==================== 弹窗拖动 + 最大化 ====================
const isMaximized = ref(false)

const canMaximize = computed(() => props.modal.options.maximizable !== false && !isDrawer.value)
const canDrag = computed(() => props.modal.options.draggable !== false && !isDrawer.value)

const dragOffset = ref({ x: 0, y: 0 })
const dragging = ref(false)
let dragStart = { x: 0, y: 0, offsetX: 0, offsetY: 0 }

function onHeaderMouseDown(e: MouseEvent) {
  if (!canDrag.value || isMaximized.value) return
  if ((e.target as HTMLElement).closest('button')) return
  dragging.value = true
  dragStart = { x: e.clientX, y: e.clientY, offsetX: dragOffset.value.x, offsetY: dragOffset.value.y }
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}

function onDragMove(e: MouseEvent) {
  if (!dragging.value) return
  dragOffset.value = {
    x: dragStart.offsetX + (e.clientX - dragStart.x),
    y: dragStart.offsetY + (e.clientY - dragStart.y),
  }
}

function onDragEnd() {
  dragging.value = false
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
}

function toggleMaximize() {
  isMaximized.value = !isMaximized.value
  if (isMaximized.value) {
    dragOffset.value = { x: 0, y: 0 }
  }
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
})

const modalStyle = computed(() => {
  if (isMaximized.value) {
    return { width: '100%', height: '100%', transform: 'none', borderRadius: '0' }
  }
  const style: Record<string, string> = {}
  const opts = props.modal.options
  if (isDrawer.value) {
    style.width = typeof opts.width === 'number' ? `${opts.width}px` : String(opts.width)
    style.height = '100%'
  } else {
    style.width = typeof opts.width === 'number' ? `${opts.width}px` : String(opts.width)
    style.height = typeof opts.height === 'number' ? `${opts.height}px` : String(opts.height)
  }
  if (dragOffset.value.x !== 0 || dragOffset.value.y !== 0) {
    style.transform = `translate(${dragOffset.value.x}px, ${dragOffset.value.y}px)`
  }
  return style
})
</script>

<template>
  <Teleport to="body">
    <div
      class="page-modal-overlay"
      :class="{ 'is-drawer': isDrawer }"
      :style="{ zIndex: overlayZIndex }"

    >
      <div
        class="page-modal-content"
        :class="{ 'is-drawer': isDrawer, 'is-maximized': isMaximized }"
        :style="modalStyle"
      >
        <div
          class="page-modal-header"
          :class="{ draggable: canDrag && !isMaximized }"
          @mousedown="onHeaderMouseDown"
        >
          <span class="page-modal-title">{{ modal.options.title }}</span>
          <span v-if="isMultiEdit" class="multi-edit-nav">
            <button class="nav-btn" :disabled="editIndex === 0" @click="prevRow">‹</button>
            <span class="multi-edit-info">{{ editIndex + 1 }} / {{ rowDataList!.length }}</span>
            <button class="nav-btn" :disabled="!rowDataList || editIndex >= rowDataList.length - 1" @click="nextRow">›</button>
          </span>
          <div class="page-modal-header-actions">
            <button v-if="canMaximize" class="header-action-btn" @click="toggleMaximize">{{ isMaximized ? '❐' : '☐' }}</button>
            <button class="page-modal-close" @click="close">✕</button>
          </div>
        </div>
        <div class="page-modal-body">
          <RendererNode
            v-for="node in store.schema"
            :key="node.id"
            :node="node"
            :positioned="true"
          />
        </div>
        <div class="page-modal-footer">
          <ElButton @click="close">{{ t('取消') }}</ElButton>
          <ElButton v-if="isSelectMode" type="primary" :disabled="!store.selectedRowData" @click="onConfirmSelect">{{ t('确认选择') }}</ElButton>
          <ElButton v-else type="primary" :loading="saving" @click="onSave">{{ t('保存') }}</ElButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.page-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}
.page-modal-overlay.is-drawer {
  justify-content: flex-end;
}
.page-modal-content {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-width: 95vw;
  max-height: 95vh;
}
.page-modal-content.is-drawer {
  border-radius: 8px 0 0 8px;
  height: 100%;
  max-height: 100%;
  max-width: 95vw;
}
.page-modal-content.is-maximized {
  max-width: 100vw;
  max-height: 100vh;
  border-radius: 0;
}
.page-modal-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
  user-select: none;
}
.page-modal-header.draggable {
  cursor: move;
}
.page-modal-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}
.multi-edit-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 12px;
}
.multi-edit-info {
  font-size: 13px;
  color: #888;
  min-width: 40px;
  text-align: center;
}
.nav-btn {
  border: none;
  background: none;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 16px;
  color: #666;
  line-height: 1;
}
.nav-btn:hover:not(:disabled) {
  background: #f0f0f0;
  color: #333;
}
.nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.page-modal-header-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
  align-items: center;
}
.header-action-btn {
  border: none;
  background: none;
  font-size: 14px;
  color: #999;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}
.header-action-btn:hover {
  background: #f0f0f0;
  color: #333;
}
.page-modal-close {
  border: none;
  background: none;
  font-size: 16px;
  color: #999;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}
.page-modal-close:hover {
  background: #f0f0f0;
  color: #333;
}
.page-modal-body {
  position: relative;
  flex: 1;
  overflow: auto;
  padding: 12px;
}
.page-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid #eee;
  flex-shrink: 0;
}
</style>