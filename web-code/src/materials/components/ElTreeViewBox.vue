<script setup lang="ts">
/**
 * ElTreeViewBox — 替换 DxTreeView
 * 用 ElTree 实现。支持扁平数组（keyExpr + parentIdExpr）或已嵌套的树形数组。
 */
import { computed, ref, watch } from 'vue'
import { ElTree } from 'element-plus'
import { resolveDataSource } from '../dx-to-el'

const props = defineProps<{
  value?: any
  modelValue?: any
  dataSource?: any
  items?: any[]
  displayExpr?: string | ((item: any) => string)
  keyExpr?: string
  parentIdExpr?: string
  childrenExpr?: string
  selectedRowKeys?: any[]
  expandedRowKeys?: any[]
  selectionMode?: 'single' | 'multiple' | 'none'
  readOnly?: boolean
  disabled?: boolean
  showCheckBoxes?: boolean
  expandEnabled?: boolean
}>()

const emit = defineEmits<{
  'update:value': [val: any[]]
  'update:modelValue': [val: any[]]
  valueChanged: [e: { value: any[] }]
  selectionChanged: [e: { selectedRowKeys: any[]; selectedItems: any[] }]
  itemClick: [e: { item: any; key: any }]
  itemExpanded: [e: { key: any; expanded: boolean }]
}>()

const raw = ref<any[]>([])
const checkedKeys = ref<any[]>([])
const expandedKeys = ref<any[]>([])
const currentKey = ref<any>(null)

function getKey(item: any): any {
  if (props.keyExpr) return item?.[props.keyExpr]
  return item?.id ?? item
}
function getLabel(item: any): string {
  if (typeof props.displayExpr === 'function') return props.displayExpr(item)
  if (props.displayExpr) return item?.[props.displayExpr] ?? ''
  return item?.label ?? item?.text ?? String(item)
}
function getChildren(item: any): any[] | undefined {
  const ce = props.childrenExpr ?? 'children'
  return item?.[ce]
}
function getParentId(item: any): any {
  if (props.parentIdExpr) return item?.[props.parentIdExpr]
  return item?.parentId ?? item?.pid
}

async function loadData() {
  const ds = props.dataSource ?? props.items
  if (!ds) { raw.value = []; return }
  raw.value = await resolveDataSource(ds)
}
watch(() => [props.dataSource, props.items], loadData, { immediate: true })

function syncSelected() {
  const k = props.selectedRowKeys ?? (Array.isArray(props.modelValue) ? props.modelValue : (props.modelValue != null ? [props.modelValue] : (Array.isArray(props.value) ? props.value : (props.value != null ? [props.value] : []))))
  checkedKeys.value = Array.isArray(k) ? [...k] : []
}
function syncExpanded() {
  expandedKeys.value = props.expandedRowKeys ? [...props.expandedRowKeys] : []
}
watch(() => [props.selectedRowKeys, props.modelValue, props.value], syncSelected, { immediate: true })
watch(() => props.expandedRowKeys, syncExpanded, { immediate: true })

interface TreeNode { id: any; label: string; children?: TreeNode[]; raw: any }

const treeData = computed<TreeNode[]>(() => {
  const items = raw.value
  if (!items.length) return []
  const hasParent = items.some((it) => getParentId(it) != null)
  if (hasParent && props.parentIdExpr) {
    const map = new Map<any, TreeNode>()
    items.forEach((it) => {
      map.set(getKey(it), { id: getKey(it), label: getLabel(it), raw: it })
    })
    const roots: TreeNode[] = []
    items.forEach((it) => {
      const pid = getParentId(it)
      const node = map.get(getKey(it))!
      if (pid == null || !map.has(pid)) roots.push(node)
      else {
        const parent = map.get(pid)!
        ;(parent.children ??= []).push(node)
      }
    })
    return roots
  }
  return items.map(buildNested)
})

function buildNested(item: any): TreeNode {
  const node: TreeNode = { id: getKey(item), label: getLabel(item), raw: item }
  const kids = getChildren(item)
  if (kids?.length) node.children = kids.map(buildNested)
  return node
}

const treeProps = { label: 'label', children: 'children' }
const showCheckbox = computed(() => props.showCheckBoxes !== false && props.selectionMode !== 'single' && props.selectionMode !== 'none')
const isDisabled = computed(() => props.readOnly || props.disabled)

function findRawByKey(nodes: TreeNode[], key: any): any {
  for (const n of nodes) {
    if (n.id === key) return n.raw
    if (n.children) {
      const r = findRawByKey(n.children, key)
      if (r) return r
    }
  }
  return null
}

function onCheck(_data: any, info: { checkedKeys: any[] }) {
  checkedKeys.value = info.checkedKeys
  const items = info.checkedKeys.map((k) => findRawByKey(treeData.value, k)).filter(Boolean)
  emit('update:value', info.checkedKeys)
  emit('update:modelValue', info.checkedKeys)
  emit('valueChanged', { value: info.checkedKeys })
  emit('selectionChanged', { selectedRowKeys: info.checkedKeys, selectedItems: items })
}

function onNodeClick(data: TreeNode) {
  currentKey.value = data.id
  const item = data.raw
  emit('itemClick', { item, key: data.id })
  if (props.selectionMode === 'single' && !showCheckbox.value) {
    checkedKeys.value = [data.id]
    emit('update:value', [data.id])
    emit('update:modelValue', [data.id])
    emit('valueChanged', { value: [data.id] })
    emit('selectionChanged', { selectedRowKeys: [data.id], selectedItems: [item] })
  }
}

function onNodeExpand(data: TreeNode, node: any) {
  emit('itemExpanded', { key: data.id, expanded: !!node?.expanded })
}
</script>

<template>
  <div class="el-tree-view-box">
    <ElTree
      :data="treeData"
      :props="treeProps"
      :node-key="keyExpr ?? 'id'"
      :show-checkbox="showCheckbox"
      :default-expanded-keys="expandedKeys"
      :default-checked-keys="checkedKeys"
      :current-node-key="currentKey"
      :check-strictly="true"
      :expand-on-click-node="expandEnabled !== false"
      :disabled="isDisabled"
      highlight-current
      @check="onCheck"
      @node-click="onNodeClick"
      @node-expand="onNodeExpand"
    />
  </div>
</template>

<style scoped>
.el-tree-view-box {
  width: 100%;
  box-sizing: border-box;
}
</style>