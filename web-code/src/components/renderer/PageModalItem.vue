<script setup lang="ts">
import { provide, computed, ref, onBeforeUnmount } from 'vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import RendererNode from './RendererNode.vue'
import { createRenderStore, DESIGNER_STORE_KEY, collectAllNodes, findNodeByFieldNameIn, findNode } from '@/store/designer'
import { GRID_REGISTRY_KEY } from '@/store/designer-shared'
import { GRID_SELECTION_KEY } from '@/store/designer-shared'
import { pageModal, type PageModalInstance } from './pageModal'
import { gatewayCall } from '@/api'
import { t } from '@/store/lang'
import { getValidator, type ValidationRuleConfig } from '@/materials/dx-validation'
import type { ComponentSchema } from '@/types/schema'
import { httpRequest } from '@/utils/request'

const props = defineProps<{
  modal: PageModalInstance
  stackIndex?: number
}>()

const store = createRenderStore(props.modal.primaryKeyField, props.modal.tableName, props.modal.options.selectMode)
store.schema = props.modal.schema
store.computeRules = props.modal.computeRules
store.pageRules = props.modal.pageRules
provide(DESIGNER_STORE_KEY, store)

/** data-grid 组件实例注册表：nodeId → LcTable exposed instance（含 getData） */
const gridRegistry = new Map<string, any>()
provide(GRID_REGISTRY_KEY, gridRegistry)

/** data-grid 选中行注册表：nodeId → 当前选中行（用于表格级父子关联过滤） */
const gridSelectionMap = new Map<string, Record<string, unknown> | null>()
provide(GRID_SELECTION_KEY, gridSelectionMap)

const isDrawer = computed(() => props.modal.options.mode === 'drawer')
const overlayZIndex = computed(() => 10000 + (props.stackIndex ?? 0) * 1000)
const saving = ref(false)

const tableName = computed(() => (props.modal.params?.tableName as string) || '')
const isEdit = computed(() => !!props.modal.params?.rowData)
/** 复制行模式：带数据打开但保存走新增 */
const isCopy = computed(() => props.modal.params?.asCopy === true)

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

// 修改模式：从后端查询完整记录再填充表单
const rowData = props.modal.params?.rowData as Record<string, unknown> | undefined
const masterId = ref<unknown>(undefined)

/** 明细表原始数据（保存时用于先删后插） */
const detailOriginalData = new Map<string, Record<string, unknown>[]>()

/** 查找 schema 中所有有 tableName 的 data-grid 节点 */
function findDetailGrids(nodes: ComponentSchema[]): ComponentSchema[] {
  const result: ComponentSchema[] = []
  for (const node of nodes) {
    if (node.type === 'data-grid' && (node.props.tableName as string)) {
      result.push(node)
    }
    if (node.children) {
      result.push(...findDetailGrids(node.children))
    }
  }
  return result
}

/** 从网关 query 响应中提取行数组（兼容多层嵌套结构） */
function extractQueryRows(res: any): Record<string, unknown>[] {
  if (!res) return []
  // 路径1: res.data.data.data（三层嵌套）
  if (res.data?.data?.data && Array.isArray(res.data.data.data)) return res.data.data.data
  // 路径2: res.data.data（两层嵌套，data 是数组）
  if (res.data?.data && Array.isArray(res.data.data)) return res.data.data
  // 路径3: res.data（直接是数组）
  if (Array.isArray(res.data)) return res.data
  // 路径4: res.payload.data
  if (res.payload?.data && Array.isArray(res.payload.data)) return res.payload.data
  return []
}

/** 加载明细表数据并填充到 data-grid */
async function loadDetailData(mId: unknown) {
  const pk = store.primaryKeyField || 'id'
  const detailGrids = findDetailGrids(store.schema)
  // 按父子关系排序：父表先加载，子表后加载
  const sorted = [...detailGrids].sort((a, b) => {
    const aParent = (a.props.parentType as string) === 'table'
    const bParent = (b.props.parentType as string) === 'table'
    if (aParent === bParent) return 0
    return aParent ? 1 : -1
  })
  // 一级明细表并发加载，子明细表等父表完成后加载
  const firstLevel = sorted.filter(g => (g.props.parentType as string) !== 'table')
  const childLevel = sorted.filter(g => (g.props.parentType as string) === 'table')

  async function loadOneGrid(grid: ComponentSchema) {
    const detailTable = grid.props.tableName as string
    const parentType = (grid.props.parentType as string) || 'page'
    try {
      let rows: Record<string, unknown>[] = []
      if (parentType === 'table') {
        // 子明细表：先尝试用主表PK过滤加载全部数据
        try {
          const filterData = [
            { Symbol: 'and' },
            { Id: pk, Symbol: '=', Val: mId, TableAlias: 'T' },
          ]
          const res = await gatewayCall(detailTable, 'query', { filterData: JSON.stringify(filterData), page: 1, pageSize: 9999 })
          rows = extractQueryRows(res)
        } catch { rows = [] }
        // 若主表PK列不存在或返回空，回退用父明细表FK逐条查询
        if (rows.length === 0) {
          const parentRef = (grid.props.parentRef as string) || ''
          const parentGrid = sorted.find(g => g.id === parentRef)
          if (parentGrid) {
            const parentKeyExpr = (parentGrid.props.keyExpr as string) || pk
            const parentData = (parentGrid.props.dataSource as Record<string, unknown>[]) || []
            for (const parentRow of parentData) {
              const parentPk = parentRow[parentKeyExpr]
              if (parentPk == null) continue
              const fkFilter = [
                { Symbol: 'and' },
                { Id: parentKeyExpr, Symbol: '=', Val: parentPk, TableAlias: 'T' },
              ]
              const fkRes = await gatewayCall(detailTable, 'query', { filterData: JSON.stringify(fkFilter), page: 1, pageSize: 9999 })
              rows.push(...extractQueryRows(fkRes))
            }
          }
        }
      } else {
        // 一级明细表：用主表PK过滤
        const filterData = [
          { Symbol: 'and' },
          { Id: pk, Symbol: '=', Val: mId, TableAlias: 'T' },
        ]
        const res = await gatewayCall(detailTable, 'query', { filterData: JSON.stringify(filterData), page: 1, pageSize: 9999 })
        rows = extractQueryRows(res)
      }
      detailOriginalData.set(detailTable, rows.map(r => ({ ...r })))
      grid.props.dataSource = rows
      grid.props.dataSourceType = 'static'
      delete grid.props.remoteOperations
    } catch (err) {
      console.error(`[明细数据加载失败] ${detailTable}`, err)
      grid.props.dataSource = []
      detailOriginalData.set(detailTable, [])
    }
  }

  // 一级明细表并发加载
  await Promise.all(firstLevel.map(g => loadOneGrid(g)))
  // 子明细表并发加载（此时父表数据已就绪）
  await Promise.all(childLevel.map(g => loadOneGrid(g)))
}

async function loadRecordAndFill(sourceRow: Record<string, unknown>) {
  const pk = store.primaryKeyField || 'id'
  const pkVal = getField(sourceRow, pk)
  if (pkVal == null) {
    for (const node of store.schema) { fillFormValues(node, sourceRow) }
    await loadDetailData(masterId.value)
    initialFormValues.value = snapshotFormValues()
    return
  }
  masterId.value = pkVal
  if (props.modal.params) { props.modal.params.masterId = pkVal }
  try {
    const filterData = [
      { Symbol: 'and' },
      { Id: pk, Symbol: '=', Val: pkVal, TableAlias: 'T' },
    ]
    const res = await gatewayCall(tableName.value, 'query', { filterData: JSON.stringify(filterData), page: 1, pageSize: 1 })
    const rows = extractQueryRows(res)
    if (rows && rows.length > 0) {
      const fullData = rows[0]
      masterId.value = getField(fullData, pk) ?? pkVal
      if (props.modal.params) { props.modal.params.masterId = masterId.value }
      for (const node of store.schema) { fillFormValues(node, fullData) }
    } else {
      for (const node of store.schema) { fillFormValues(node, sourceRow) }
    }
  } catch {
    for (const node of store.schema) { fillFormValues(node, sourceRow) }
  }
  await loadDetailData(masterId.value)
  initialFormValues.value = snapshotFormValues()
}

async function loadFullRecordAndFill() {
  if (!rowData) return
  await loadRecordAndFill(rowData)
}

if (rowData) {
  // 同步设置明细表格为 static 空数据，防止 data-grid 在 async 查询完成前自动发起 remote 请求
  for (const grid of findDetailGrids(store.schema)) {
    grid.props.dataSourceType = 'static'
    grid.props.dataSource = []
    delete grid.props.remoteOperations
  }
  loadFullRecordAndFill()
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
async function switchRow(index: number): Promise<void> {
  if (!rowDataList || index < 0 || index >= rowDataList.length) return
  editIndex.value = index
  const data = rowDataList[index]
  await loadRecordAndFill(data)
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

/** 收集表单中的唯一性校验规则节点 */
function collectUniqueRules(): { node: ComponentSchema; rule: ValidationRuleConfig }[] {
  const allNodes = collectAllNodes(store.schema).filter((n) => n.fieldName)
  const result: { node: ComponentSchema; rule: ValidationRuleConfig }[] = []
  for (const node of allNodes) {
    const rules = node.props.validationRules as ValidationRuleConfig[] | undefined
    if (!Array.isArray(rules)) continue
    const rule = rules.find((r) => r.type === 'unique')
    if (rule) result.push({ node, rule })
  }
  return result
}

/** 唯一性校验：保存前查库确认值不重复（编辑模式排除当前记录，支持联合字段组合唯一） */
async function validateUnique(data: Record<string, unknown>): Promise<string[]> {
  const errors: string[] = []
  const uniqueRules = collectUniqueRules()
  if (!uniqueRules.length) return errors

  let pkCond: { Id: string; Symbol: string; Val: unknown } | undefined
  if (isEdit.value && !isCopy.value) {
    const currentRow = isMultiEdit.value ? rowDataList![editIndex.value] : rowData
    const pk = store.primaryKeyField || 'id'
    const pkVal = currentRow ? getField(currentRow, pk) : undefined
    if (pkVal !== undefined && pkVal !== null) {
      pkCond = { Id: pk, Symbol: '<>', Val: pkVal }
    }
  }

  await Promise.all(
    uniqueRules.map(async ({ node, rule }) => {
      const mainDbField = node.dbField || node.fieldName!
      const mainVal = data[mainDbField]
      if (mainVal === undefined || mainVal === null || mainVal === '') return
      const conditions: Record<string, unknown>[] = [
        { Symbol: 'and' },
        { Id: mainDbField, Symbol: '=', Val: mainVal },
      ]
      for (const cf of rule.combineFields || []) {
        const cfNode = findNodeByFieldNameIn(store.schema, cf)
        const cfDbField = cfNode?.dbField || cf
        const cfVal = data[cfDbField]
        if (cfVal === undefined || cfVal === null || cfVal === '') continue
        conditions.push({ Id: cfDbField, Symbol: '=', Val: cfVal })
      }
      if (pkCond) conditions.push(pkCond)
      try {
        const res = await gatewayCall(tableName.value, 'query', {
          filterData: JSON.stringify(conditions),
          take: 1,
        })
        const totalCount = Number(
          (res.payload as { totalCount?: number } | undefined)?.totalCount ?? 0
        )
        if (totalCount > 0) {
          const label = (node.props.label as string) || node.fieldName
          errors.push(rule.message || `「${label}」${String(mainVal)}${t('已存在')}`)
        }
      } catch (err) {
        console.error('[唯一性校验查询失败]', err)
      }
    })
  )
  return errors
}

/** 保存明细表数据（先删后插策略） */
/** 拓扑排序明细网格：parentType=table 的子网格排在父网格之后 */
function topoSortGrids(grids: ComponentSchema[]): ComponentSchema[] {
  const result: ComponentSchema[] = []
  const visited = new Set<string>()
  const visiting = new Set<string>()
  function visit(grid: ComponentSchema) {
    if (visited.has(grid.id)) return
    if (visiting.has(grid.id)) return
    visiting.add(grid.id)
    const parentType = (grid.props.parentType as string) || 'page'
    if (parentType === 'table') {
      const parentRef = (grid.props.parentRef as string) || ''
      const parentGrid = grids.find(g => g.id === parentRef)
      if (parentGrid) visit(parentGrid)
    }
    visiting.delete(grid.id)
    visited.add(grid.id)
    result.push(grid)
  }
  for (const grid of grids) visit(grid)
  return result
}

/** 从网关响应中提取新生成的主键值 */
function extractPkFromResponse(res: any, keyExpr: string): unknown {
  const data = res?.data?.data ?? res?.data ?? res
  if (data && typeof data === 'object') {
    return data[keyExpr] ?? data[keyExpr.toLowerCase()] ?? data[keyExpr.toUpperCase()] ?? data.id ?? data.ID
  }
  return undefined
}

async function saveDetailData(masterData: Record<string, unknown>) {
  const pk = store.primaryKeyField || 'id'
  const mId = masterData[pk] ?? masterId.value
  const detailGrids = findDetailGrids(store.schema)
  if (mId == null) {
    console.warn('[saveDetailData] mId is null，跳过明细保存')
    return
  }
  const sortedGrids = topoSortGrids(detailGrids)
  const rowIdMap = new Map<string, unknown>()
  for (const grid of sortedGrids) {
    const detailTable = grid.props.tableName as string
    const gridKeyExpr = (grid.props.keyExpr as string) || pk
    const parentType = (grid.props.parentType as string) || 'page'
    const parentRef = (grid.props.parentRef as string) || ''
    const currentRows = (() => {
      const gridInst = gridRegistry.get(grid.id)

      if (gridInst && typeof gridInst.getData === 'function') {
        return gridInst.getData() as Record<string, unknown>[]
      }
      return (grid.props.dataSource as Record<string, unknown>[]) || []
    })()
    const originalRows = detailOriginalData.get(detailTable) || []
    for (const orig of originalRows) {
      const origPk = orig[gridKeyExpr] ?? orig[gridKeyExpr.toLowerCase()] ?? orig[gridKeyExpr.toUpperCase()]
      if (origPk != null) {
        try {
          await httpRequest('/api/lowcode/gateway', {
            method: 'POST',
            body: JSON.stringify({ bizCode: detailTable, action: 'delete', payload: { [gridKeyExpr]: origPk } }),
          })
        } catch (err) {
          console.error(`[明细删除失败] ${detailTable}`, err)
        }
      }
    }
    let fkField = pk
    if (parentType === 'table') {
      const parentNode = findNode(store.schema, parentRef)
      fkField = (parentNode?.node?.props?.keyExpr as string) || pk
    }
    for (const row of currentRows) {
      const ins: Record<string, unknown> = {}
      for (const k of Object.keys(row)) {
        if (!k.startsWith('_') && k !== '__op') ins[k] = row[k]
      }
      if (parentType === 'table') {
        const origFk = ins[fkField]
        if (typeof origFk === 'string' && rowIdMap.has(origFk)) {
          ins[fkField] = rowIdMap.get(origFk)
        }
      } else {
        ins[fkField] = mId
      }
      try {
        const detailRes = await httpRequest('/api/lowcode/gateway', {
          method: 'POST',
          body: JSON.stringify({ bizCode: detailTable, action: 'create', payload: ins }),
        })
        const _rowId = row._rowId as string
        if (_rowId) {
          const realPk = extractPkFromResponse(detailRes, gridKeyExpr)
          if (realPk != null) rowIdMap.set(_rowId, realPk)
        }
      } catch (err) {
        console.error(`[明细新增失败] ${detailTable}`, err)
      }
    }
    detailOriginalData.set(detailTable, currentRows.map(r => ({ ...r })))
  }
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
  const detailGrids = findDetailGrids(store.schema)
  for (const grid of detailGrids) {
    const gridInst = gridRegistry.get(grid.id)
    if (gridInst && typeof gridInst.validateAndSave === 'function') {
      if (!gridInst.validateAndSave()) return
    }
  }
  const data: Record<string, unknown> = {}
  for (const node of store.schema) {
    collectFormValues(node, data)
  }
  saving.value = true
  try {
    const uniqueErrors = await validateUnique(data)
    if (uniqueErrors.length) {
      ElMessage.error(uniqueErrors.join('；'))
      return
    }
    // 唯一性配置传后端：事务内 insert/update 前查重兜底（防并发窗口）
    const uniqueChecks = collectUniqueRules().map(({ node, rule }) => {
      const mainDbField = node.dbField || node.fieldName!
      const fields = [mainDbField]
      for (const cf of rule.combineFields || []) {
        const cfNode = findNodeByFieldNameIn(store.schema, cf)
        fields.push(cfNode?.dbField || cf)
      }
      return { field: mainDbField, fields, message: rule.message }
    })
    if (uniqueChecks.length) {
      data.__uniqueChecks = uniqueChecks
    }
    const action = isEdit.value && !isCopy.value ? 'update' : 'create'
    if (isEdit.value && !isCopy.value) {
      const currentRow = isMultiEdit.value ? rowDataList![editIndex.value] : rowData
      const pk = store.primaryKeyField || 'id'
      const pkVal = currentRow?.[pk] ?? currentRow?.[pk.toLowerCase()] ?? currentRow?.[pk.toUpperCase()]
      if (pkVal != null) data[pk] = pkVal
    }
    // create 时剔除空主键，让后端自动生成（Snowflake/UUID）
    if (action === 'create') {
      const pk = store.primaryKeyField || 'id'
      const pkVal = data[pk]
      if (pkVal == null || pkVal === '') {
        delete data[pk]
      }
      // 兜底：剔除所有空字符串的 *_ID 字段（可能 primaryKeyField 未正确配置）
      for (const key of Object.keys(data)) {
        if (data[key] === '' && key.toUpperCase().endsWith('_ID')) {
          delete data[key]
        }
      }
    }
    const pk = store.primaryKeyField || 'id'
    const sortedGrids = topoSortGrids(detailGrids)
    const _details: Array<Record<string, unknown>> = []
    for (const grid of sortedGrids) {
      const detailTable = grid.props.tableName as string
      const gridInst = gridRegistry.get(grid.id)
      const rows = gridInst && typeof gridInst.getData === 'function'
        ? gridInst.getData() as Record<string, unknown>[]
        : (grid.props.dataSource as Record<string, unknown>[]) || []
      if (rows.length === 0) continue
      const parentType = (grid.props.parentType as string) || 'page'
      const parentRef = (grid.props.parentRef as string) || ''
      let fkField = pk
      if (parentType === 'table') {
        const parentNode = findNode(store.schema, parentRef)
        fkField = (parentNode?.node?.props?.keyExpr as string) || pk
      }
      const parentRefTable = parentType === 'table'
        ? (findNode(store.schema, parentRef)?.node?.props?.tableName as string) || ''
        : ''
      _details.push({
        tableName: detailTable,
        fkField,
        parentType,
        parentRefTable,
        rows: rows.map(r => {
          const c: Record<string, unknown> = {}
          for (const k of Object.keys(r)) {
            if (k !== '__op') c[k] = r[k]
          }
          return c
        }),
      })
    }
    if (_details.length > 0) data._details = _details
    const saveRes = await gatewayCall(tableName.value, action, data)
    if (action === 'create') {
      const newId = extractPkFromResponse(saveRes, pk)
      if (newId != null) { data[pk] = newId; masterId.value = newId }
    }
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
  const multi = (store.selectedRowsList && store.selectedRowsList.length > 0)
    ? store.selectedRowsList
    : (store.selectedRowData ? [store.selectedRowData] : [])
  if (!multi.length) {
    ElMessage.warning(t('请先选择一行数据'))
    return
  }
  // 多选（勾选多行）回传数组；单选回传单行（兼容既有基础资料控件回填）
  if (multi.length > 1) pageModal.closePage(multi)
  else pageModal.closePage(store.selectedRowData)
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
        <ElButton v-else type="primary" :loading="saving" :disabled="saving" @click="onSave">{{ t('保存') }}</ElButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-modal-overlay {
  position: absolute;
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
  max-width: 95%;
  max-height: 95%;
}
.page-modal-content.is-drawer {
  border-radius: 8px 0 0 8px;
  height: 100%;
  max-height: 100%;
  max-width: 95%;
}
.page-modal-content.is-maximized {
  max-width: 100%;
  max-height: 100%;
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
<style>
.page-modal-enter-active,
.page-modal-leave-active {
  transition: opacity 0.3s ease;
}
.page-modal-enter-from,
.page-modal-leave-to {
  opacity: 0;
}
</style>