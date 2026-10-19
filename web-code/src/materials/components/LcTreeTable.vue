<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElTable, ElTableColumn } from 'element-plus'

export interface LcTreeColumn {
  dataField?: string
  caption?: string
  width?: number | string
  alignment?: 'left' | 'center' | 'right'
  visible?: boolean
  [k: string]: unknown
}

const props = defineProps<{
  dataSource?: any[]
  columns?: LcTreeColumn[]
  keyExpr?: string
  parentIdExpr?: string
  height?: number | string
  showBorders?: boolean
  showColumnHeaders?: boolean
  showColumnLines?: boolean
  allowColumnReordering?: boolean
  wordWrapEnabled?: boolean
  expandedRowKeys?: any[]
  hasItemsExpr?: string
}>()

const keyField = computed(() => props.keyExpr || 'id')
const parentField = computed(() => props.parentIdExpr || 'parentId')

const treeData = computed(() => {
  const raw = props.dataSource || []
  if (!raw.length) return []
  const map = new Map<any, any>()
  const roots: any[] = []
  for (const item of raw) {
    map.set(item[keyField.value], { ...item, children: [] })
  }
  for (const item of raw) {
    const node = map.get(item[keyField.value])
    const pid = item[parentField.value]
    const parent = map.get(pid)
    if (parent && pid !== 0 && pid !== '0' && pid !== null && pid !== undefined) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }
  const clean = (nodes: any[]) => {
    for (const n of nodes) {
      if (!n.children.length) delete n.children
      else clean(n.children)
    }
  }
  clean(roots)
  return roots
})

const visibleColumns = computed(() => (props.columns || []).filter(c => c.visible !== false))

function getCellValue(row: any, col: LcTreeColumn) {
  return row[col.dataField as string]
}

const tableRef = ref<any>(null)

defineExpose({
  refresh: () => {},
  getDataSource: () => treeData.value,
})
</script>

<template>
  <div class="lc-tree-table-wrapper" :class="{ 'lc-tree-bordered': showBorders }">
    <el-table
      ref="tableRef"
      :data="treeData"
      :height="height"
      :border="showBorders"
      :row-key="keyField"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      default-expand-all
      style="width: 100%"
    >
      <el-table-column
        v-for="(col, idx) in visibleColumns"
        :key="col.dataField"
        :prop="col.dataField"
        :label="col.caption || col.dataField"
        :width="typeof col.width === 'number' ? col.width : undefined"
        :align="col.alignment || 'left'"
        :show-overflow-tooltip="!wordWrapEnabled"
      >
        <template #default="{ row }">
          <span :style="{ paddingLeft: idx === 0 ? '0' : '0' }">{{ getCellValue(row, col) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="!visibleColumns.length" label="暂无列" width="120">
        <template #default>—</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.lc-tree-table-wrapper { width: 100%; }
.lc-tree-bordered { border: 1px solid #ebeef5; border-radius: 4px; overflow: hidden; }
:deep(.el-table) { font-size: 13px; }
</style>