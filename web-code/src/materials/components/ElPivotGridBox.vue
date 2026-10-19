<script setup lang="ts">
/**
 * ElPivotGridBox — 替换 DxPivotGrid
 * 前端聚合透视表：按 rowFields × columnFields 对 dataFields 做 sum/count/avg/max/min。
 * 用 ElTable 渲染结果。本期降级：固定行列维度配置，无拖拽切换。
 */
import { computed, ref, watch } from 'vue'
import { ElTable, ElTableColumn } from 'element-plus'
import { resolveDataSource } from '../dx-to-el'

type AggType = 'sum' | 'count' | 'avg' | 'max' | 'min'

interface DataField {
  field: string
  label?: string
  aggregationType?: AggType
}

const props = defineProps<{
  dataSource?: any
  items?: any[]
  rowFields?: string[]
  columnFields?: string[]
  dataFields?: (string | DataField)[]
  aggregationType?: AggType
  showTotals?: boolean
  height?: number | string
}>()

defineEmits<{
  valueChanged: [e: { value: any }]
}>()

const rows = computed(() => props.rowFields ?? [])
const cols = computed(() => props.columnFields ?? [])
const dataFieldsNorm = computed<DataField[]>(() =>
  (props.dataFields ?? []).map((d) => (typeof d === 'string' ? { field: d, label: d } : d)),
)

function agg(values: number[], type: AggType): number {
  if (type === 'count') return values.length
  if (!values.length) return 0
  const nums = values.map(Number).filter((n) => !isNaN(n))
  if (!nums.length) return 0
  switch (type) {
    case 'sum': return nums.reduce((a, b) => a + b, 0)
    case 'avg': return nums.reduce((a, b) => a + b, 0) / nums.length
    case 'max': return Math.max(...nums)
    case 'min': return Math.min(...nums)
    default: return nums.reduce((a, b) => a + b, 0)
  }
}

function keyOf(item: any, fields: string[]): string {
  return fields.map((f) => String(item?.[f] ?? '')).join(' / ')
}

interface PivotResult {
  rowKeys: string[]
  colKeys: string[]
  rowKeyParts: Record<string, any[]>
  colKeyParts: Record<string, any[]>
  cells: Map<string, Record<string, number>>
  rowTotals: Record<string, number>
  colTotals: Record<string, number>
  grandTotal: number
}

const EMPTY_PIVOT: PivotResult = {
  rowKeys: [], colKeys: [], rowKeyParts: {}, colKeyParts: {},
  cells: new Map(), rowTotals: {}, colTotals: {}, grandTotal: 0,
}

const pivot = ref<PivotResult>(EMPTY_PIVOT)

async function computePivot() {
  const ds = props.dataSource ?? props.items
  const data = ds ? await resolveDataSource(ds) : []
  const rfs = rows.value
  const cfs = cols.value
  const dfs = dataFieldsNorm.value
  const defaultAgg = props.aggregationType ?? 'sum'

  const rowSet = new Set<string>()
  const colSet = new Set<string>()
  const rowKeyParts: Record<string, any[]> = {}
  const colKeyParts: Record<string, any[]> = {}
  const buckets = new Map<string, Map<string, number[]>>()

  data.forEach((item) => {
    const rk = keyOf(item, rfs)
    const ck = keyOf(item, cfs)
    rowSet.add(rk); colSet.add(ck)
    if (!rowKeyParts[rk]) rowKeyParts[rk] = rfs.map((f) => item?.[f])
    if (!colKeyParts[ck]) colKeyParts[ck] = cfs.map((f) => item?.[f])
    const key = `${rk}__${ck}`
    if (!buckets.has(key)) buckets.set(key, new Map())
    const m = buckets.get(key)!
    dfs.forEach((df) => {
      const v = item?.[df.field]
      const arr = m.get(df.field) ?? []
      arr.push(v)
      m.set(df.field, arr)
    })
  })

  const rowKeys = [...rowSet].sort()
  const colKeys = [...colSet].sort()
  const cells = new Map<string, Record<string, number>>()
  let grandTotal = 0
  const rowTotals: Record<string, number> = {}
  const colTotals: Record<string, number> = {}

  rowKeys.forEach((rk) => {
    colKeys.forEach((ck) => {
      const key = `${rk}__${ck}`
      const m = buckets.get(key)
      const cell: Record<string, number> = {}
      let cellSum = 0
      dfs.forEach((df) => {
        const vals = m?.get(df.field) ?? []
        const v = agg(vals, df.aggregationType ?? defaultAgg)
        cell[df.field] = v
        cellSum += v
      })
      cells.set(key, cell)
      grandTotal += cellSum
      rowTotals[rk] = (rowTotals[rk] ?? 0) + cellSum
      colTotals[ck] = (colTotals[ck] ?? 0) + cellSum
    })
  })

  pivot.value = { rowKeys, colKeys, rowKeyParts, colKeyParts, cells, rowTotals, colTotals, grandTotal }
}

watch(
  () => [props.dataSource, props.items, props.rowFields, props.columnFields, props.dataFields, props.aggregationType],
  computePivot,
  { immediate: true },
)

const tableData = computed(() => {
  const p = pivot.value
  return p.rowKeys.map((rk) => {
    const row: Record<string, any> = { _rowKey: rk }
    p.rowKeyParts[rk].forEach((v, i) => { row[`_r${i}`] = v })
    p.colKeys.forEach((ck) => {
      const cell = p.cells.get(`${rk}__${ck}`) ?? {}
      dataFieldsNorm.value.forEach((df) => { row[`${ck}__${df.field}`] = cell[df.field] })
    })
    row._total = p.rowTotals[rk]
    return row
  })
})

const heightStyle = computed(() => {
  const h = props.height
  return h == null ? '400px' : (typeof h === 'number' ? `${h}px` : h)
})
</script>

<template>
  <div class="el-pivot-grid-box">
    <ElTable :data="tableData" :height="heightStyle" border stripe size="small">
      <ElTableColumn
        v-for="(rf, i) in rows"
        :key="`r${i}`"
        :prop="`_r${i}`"
        :label="rf"
        fixed="left"
        min-width="120"
      />
      <ElTableColumn
        v-for="ck in pivot.colKeys"
        :key="ck"
        :label="ck"
        min-width="100"
      >
        <ElTableColumn
          v-for="df in dataFieldsNorm"
          :key="`${ck}__${df.field}`"
          :prop="`${ck}__${df.field}`"
          :label="df.label ?? df.field"
          min-width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ row[`${ck}__${df.field}`] ?? 0 }}
          </template>
        </ElTableColumn>
      </ElTableColumn>
      <ElTableColumn
        v-if="showTotals !== false"
        prop="_total"
        label="总计"
        fixed="right"
        align="right"
        min-width="100"
      />
    </ElTable>
  </div>
</template>

<style scoped>
.el-pivot-grid-box {
  width: 100%;
  box-sizing: border-box;
}
</style>