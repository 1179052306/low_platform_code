<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { groupByCategory, getConfig } from '@/materials'
import type { MaterialConfig, ComponentSchema } from '@/types/schema'
import { t } from '@/store/lang'
import {
  designerStore,
  collectAllNodes,
  addNodeFromForm,
  selectSingleNode,
} from '@/store/designer'
import { getOrphanFields, dropField, type OrphanField } from '@/store/persistence'
import { ElMessage, ElMessageBox } from 'element-plus'


const allGroups = groupByCategory()
const searchKeyword = ref('')

/** 分类显示顺序 */
const CATEGORY_ORDER = [
  '布局',
  '表单',
  '操作',
  '基础',
  '数据',
  '图表',
  '导航',
  '弹层',
  '移动端布局',
  '移动端表单',
  '移动端展示',
  '移动端弹层',
]

/** 各业务模式允许的物料分类白名单（default 模式不过滤，显示全部） */
const BIZ_MODE_CATEGORIES: Record<string, string[]> = {
  form: ['布局', '表单', '数据', '操作', '基础', '图表', '弹层'],
  table: ['布局', '表单', '数据', '操作', '基础', '图表'],
  mobile: ['移动端布局', '移动端表单', '移动端展示', '移动端弹层'],
}

/** 当前模式允许的物料分类（default 模式返回 null 表示不过滤） */
const allowedCategories = computed<string[] | null>(() => {
  if (designerStore.mode !== 'biz') return null
  return BIZ_MODE_CATEGORIES[designerStore.bizMode] ?? null
})

/** 物料面板标题：按模式区分 */
const panelTitle = computed(() => {
  if (designerStore.mode !== 'biz') return t('组件库')
  if (designerStore.bizMode === 'table') return t('表格组件库')
  if (designerStore.bizMode === 'mobile') return t('移动端组件库')
  return t('表单组件库')
})

/** 当前是否为表单模式（biz + form） */
const isFormMode = computed(
  () => designerStore.mode === 'biz' && designerStore.bizMode === 'form',
)

/** 当前是否为移动端模式 */
const isMobileMode = computed(
  () => designerStore.mode === 'biz' && designerStore.bizMode === 'mobile',
)

/** 当前是否为业务模式（表单或移动端，有 tab 切换） */
const isBizMode = computed(() => isFormMode.value || isMobileMode.value)

/** 面板 tab（仅表单模式使用） */
const panelTab = ref<'materials' | 'fields' | 'tree'>('materials')

/** 表单模式中带 fieldName 的表单输入类控件列表（供移动端"从表单添加字段"） */
const formFieldNodes = computed<ComponentSchema[]>(() => {
  if (!isMobileMode.value) return []
  const formSchema = designerStore.bizSchemas.form
  return collectAllNodes(formSchema).filter((n) => {
    if (!n.fieldName) return false
    const c = getConfig(n.type)
    return c?.category === '表单'
  })
})

/** 从表单添加控件到移动端画布 */
function onAddFromForm(node: ComponentSchema): void {
  addNodeFromForm(node)
}

/** 根据关键字与模式白名单过滤后的分组 */
const filteredGroups = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  const allow = allowedCategories.value
  const result: Record<string, MaterialConfig[]> = {}
  for (const [category, items] of Object.entries(allGroups)) {
    if (allow && !allow.includes(category)) continue
    const matched = kw
      ? items.filter(
          (item) =>
            item.title.toLowerCase().includes(kw) ||
            (item.enName && item.enName.toLowerCase().includes(kw)) ||
            item.type.toLowerCase().includes(kw),
        )
      : items
    if (matched.length) result[category] = matched
  }
  return result
})

/** 按固定顺序排列分类 */
const sortedCategories = computed(() => {
  const cats = Object.keys(filteredGroups.value)
  return CATEGORY_ORDER.filter((c) => cats.includes(c)).concat(
    cats.filter((c) => !CATEGORY_ORDER.includes(c)),
  )
})

/** 拖拽开始：把物料类型写入 dataTransfer */
function onDragStart(e: DragEvent, type: string): void {
  if (!e.dataTransfer) return
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('application/x-material', type)
  console.debug('[drag-debug] 物料 dragstart:', type, 'effectAllowed=', e.dataTransfer.effectAllowed)
}

// ---------- 字段绑定 tab ----------

const collapsedGroups = ref<Set<string>>(new Set())
function toggleGroup(key: string): void {
  if (collapsedGroups.value.has(key)) collapsedGroups.value.delete(key)
  else collapsedGroups.value.add(key)
}

interface FieldGroupItem {
  dbField: string
  caption?: string
  type: string
  nodeId?: string
}
interface FieldTreeNode {
  key: string
  tableName: string
  tableComment?: string
  isMain: boolean
  depth: number
  fields: FieldGroupItem[]
  hasChildren: boolean
}

/** 构建 data-grid 树形节点 */
interface GridNode {
  id: string
  tableName: string
  tableComment: string
  parentType: string
  parentRef: string
  fields: FieldGroupItem[]
}
function buildGridTree(grids: GridNode[], mainKey: string): FieldTreeNode[] {
  const result: FieldTreeNode[] = []
  const byId = new Map(grids.map(g => [g.id, g]))
  const added = new Set<string>()

  function emit(grid: GridNode, depth: number) {
    if (added.has(grid.id)) return
    added.add(grid.id)
    const children = grids.filter(g => g.parentType === 'table' && g.parentRef === grid.id)
    result.push({
      key: `grid-${grid.id}`,
      tableName: grid.tableName,
      tableComment: grid.tableComment,
      isMain: false,
      depth,
      fields: grid.fields,
      hasChildren: children.length > 0,
    })
    for (const child of children) emit(child, depth + 1)
  }

  // 一级明细表（parentType=page 或无 parentType）直接挂在主表下
  for (const g of grids) {
    if (g.parentType === 'table') continue
    emit(g, 1)
  }
  return result
}

/** 当前表单 schema 中有 dbField 的控件列表，按表分组（主表 + 各明细表，支持嵌套层级） */
const dbFieldGroups = computed<FieldTreeNode[]>(() => {
  if (!isFormMode.value) return []
  const result: FieldTreeNode[] = []
  const seenPerTable = new Map<string, Set<string>>()
  const getSeen = (table: string) => {
    let s = seenPerTable.get(table)
    if (!s) { s = new Set(); seenPerTable.set(table, s) }
    return s
  }

  // 1. 主表字段
  const mainTable = designerStore.tableName || ''
  const mainFields: FieldGroupItem[] = []
  const mainSeen = getSeen(mainTable)
  for (const n of collectAllNodes(designerStore.schema)) {
    if (n.type === 'data-grid' || n.type === 'dx-data-grid') continue
    if (n.dbField && n.dbField.trim() && !mainSeen.has(n.dbField)) {
      mainSeen.add(n.dbField)
      mainFields.push({ dbField: n.dbField, caption: n.fieldName || '', type: n.type, nodeId: n.id })
    }
  }

  // 2. 收集所有 data-grid 节点
  const grids: GridNode[] = []
  for (const n of collectAllNodes(designerStore.schema)) {
    if (n.type !== 'data-grid' && n.type !== 'dx-data-grid') continue
    const detailTable = (n.props.tableName as string) || ''
    const cols = (n.props.columns ?? []) as Record<string, unknown>[]
    if (!cols.length) continue
    const seen = getSeen(detailTable)
    const fields: FieldGroupItem[] = []
    for (const col of cols) {
      const df = col.dataField as string
      if (df && !seen.has(df)) {
        seen.add(df)
        fields.push({ dbField: df, caption: (col.caption as string) || '', type: 'column' })
      }
    }
    grids.push({
      id: n.id,
      tableName: detailTable,
      tableComment: (n.props.tableComment as string) || '',
      parentType: (n.props.parentType as string) || 'page',
      parentRef: (n.props.parentRef as string) || '',
      fields,
    })
  }

  // 3. 构建主表节点
  const childGrids = grids.filter(g => g.parentType !== 'table')
  result.push({
    key: '__main__',
    tableName: mainTable,
    isMain: true,
    depth: 0,
    fields: mainFields,
    hasChildren: childGrids.length > 0 || grids.some(g => g.parentType === 'table'),
  })

  // 4. 构建明细表树（含嵌套）
  result.push(...buildGridTree(grids, '__main__'))

  // 5. 表格页面字段
  const tableSchema = designerStore.bizSchemas.table?.[0]
  const tableProps = (tableSchema?.props ?? {}) as Record<string, unknown>
  if (Array.isArray(tableProps.columns) && !mainFields.length && !grids.length) {
    const tableName = (tableProps.tableName as string) || designerStore.tableName || ''
    const seen = getSeen(tableName)
    const fields: FieldGroupItem[] = []
    for (const col of tableProps.columns as Record<string, unknown>[]) {
      const df = col.dataField as string
      if (df && !seen.has(df)) {
        seen.add(df)
        fields.push({ dbField: df, caption: (col.caption as string) || '', type: 'column' })
      }
    }
    if (fields.length) {
      result.push({ key: '__table__', tableName, isMain: true, depth: 0, fields, hasChildren: false })
    }
  }
  return result
})

/** 点击字段绑定项：选中画布中的控件 */
function onDbFieldClick(item: FieldGroupItem): void {
  if (item.nodeId) selectSingleNode(item.nodeId)
}

// ---------- 孤儿字段（数据库有但 schema 已删除） ----------

const orphanFields = ref<OrphanField[]>([])
const loadingOrphan = ref(false)

/** 加载孤儿字段 */
async function loadOrphanFields(): Promise<void> {
  const tableName = designerStore.tableName?.trim()
  if (!tableName || !isFormMode.value) {
    orphanFields.value = []
    return
  }
  loadingOrphan.value = true
  try {
    const dbFields = dbFieldGroups.value
      .flatMap((g) => g.fields.map((f) => f.dbField))
      .filter(Boolean)
    orphanFields.value = await getOrphanFields(tableName, dbFields)
  } catch {
    orphanFields.value = []
  } finally {
    loadingOrphan.value = false
  }
}

/** 删除孤儿字段 */
async function onDropField(field: OrphanField): Promise<void> {
  if (field.hasData) {
    ElMessage.warning(t('该字段有数据，不允许删除'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t(`确认删除字段 "${field.name}"？此操作不可恢复。`),
      t('删除字段'),
      { confirmButtonText: t('删除'), cancelButtonText: t('取消'), type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await dropField(designerStore.tableName.trim(), field.name)
    ElMessage.success(t('字段删除成功'))
    await loadOrphanFields()
  } catch (e) {
    ElMessage.error(t((e as Error).message || '删除失败'))
  }
}

watch(panelTab, (tab) => {
  if (tab === 'fields') loadOrphanFields()
})

// ---------- 控件树 tab ----------

/** 控件树节点显示标签 */
function nodeLabel(node: ComponentSchema): string {
  const c = getConfig(node.type)
  const title = c?.title ?? node.type
  const field = node.fieldName ? ` (${node.fieldName})` : ''
  return `${title}${field}`
}

/** 点击控件树节点：选中画布中的控件 */
function onTreeNodeClick(node: ComponentSchema): void {
  selectSingleNode(node.id)
}

/** 控件树展开/折叠状态 */
const expandedTreeNodes = ref<Set<string>>(new Set())

function toggleTreeNode(id: string): void {
  const s = new Set(expandedTreeNodes.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  expandedTreeNodes.value = s
}

function isTreeExpanded(id: string): boolean {
  return expandedTreeNodes.value.has(id)
}

/** 拍平控件树为带深度的列表（支持任意层级递归） */
interface FlatTreeNode {
  node: ComponentSchema
  depth: number
  hasChildren: boolean
}

const flatTreeNodes = computed<FlatTreeNode[]>(() => {
  const result: FlatTreeNode[] = []
  const walk = (nodes: ComponentSchema[], depth: number) => {
    for (const node of nodes) {
      const hasChildren = !!(node.children && node.children.length)
      result.push({ node, depth, hasChildren })
      if (hasChildren && isTreeExpanded(node.id)) {
        walk(node.children!, depth + 1)
      }
    }
  }
  walk(designerStore.schema, 0)
  console.log('[控件树] flatTreeNodes:', result.length, 'items', result.map(r => `${r.depth}:${r.node.type}:${r.hasChildren}`))
  return result
})
</script>

<template>
  <aside class="material-panel">
    <!-- 表单/移动端模式：tab 切换 -->
    <template v-if="isBizMode">
      <div class="panel-title">{{ panelTitle }}</div>
      <div class="panel-tabs">
        <button
          :class="['panel-tab-btn', panelTab === 'materials' ? 'tab-active' : '']"
          @click="panelTab = 'materials'"
        >
          {{ t('组件库') }}
        </button>
        <button
          v-if="isFormMode"
          :class="['panel-tab-btn', panelTab === 'fields' ? 'tab-active' : '']"
          @click="panelTab = 'fields'"
        >
          {{ t('字段绑定') }}
        </button>
        <button
          :class="['panel-tab-btn', panelTab === 'tree' ? 'tab-active' : '']"
          @click="panelTab = 'tree'"
        >
          {{ t('控件树') }}
        </button>
      </div>

      <!-- 组件库 tab -->
      <div v-if="panelTab === 'materials'" class="tab-content">
        <!-- 移动端：从表单添加字段 -->
        <div v-if="isMobileMode" class="form-fields-section">
          <div class="group-title">{{ t('从表单添加字段') }}</div>
          <div v-if="formFieldNodes.length" class="form-fields-list">
            <div
              v-for="node in formFieldNodes"
              :key="node.id"
              class="form-field-item"
              :title="`${node.type} (fieldName: ${node.fieldName})`"
              @click="onAddFromForm(node)"
            >
              <span class="form-field-type">{{ node.type }}</span>
              <span class="form-field-name">{{ node.fieldName }}</span>
            </div>
          </div>
          <div v-else class="panel-empty" style="padding: 12px 0">
            {{ t('表单页面暂无字段，请先在表单模式添加控件') }}
          </div>
        </div>
        <input
          v-model="searchKeyword"
          class="panel-search-input"
          :placeholder="t('搜索组件（中文名/英文名）')"
        />
        <div
          v-for="category in sortedCategories"
          :key="category"
          class="material-group"
        >
          <div class="group-title">{{ t(category) }}</div>
          <div class="group-items">
            <div
              v-for="item in filteredGroups[category]"
              :key="item.type"
              class="material-item"
              draggable="true"
              :title="
                item.enName ? `${t(item.title)} (${item.enName})` : t(item.title)
              "
              @dragstart="onDragStart($event, item.type)"
            >
              <span class="material-icon">{{ item.icon }}</span>
              <span class="material-name">{{ t(item.title) }}</span>
              <span v-if="item.enName" class="material-en-name">{{
                item.enName
              }}</span>
            </div>
          </div>
        </div>
        <div v-if="!sortedCategories.length" class="panel-empty">
          {{ t('没有匹配的组件') }}
        </div>
        <div class="panel-tip">{{ t('拖拽组件到中间画布') }}</div>
      </div>

      <!-- 字段绑定 tab (仅表单模式) -->
      <div v-if="panelTab === 'fields' && isFormMode" class="tab-content">
        <div v-if="dbFieldGroups.length" class="field-tree">
          <template v-for="node in dbFieldGroups" :key="node.key">
            <div
              class="field-tree-header"
              :class="{ 'is-main': node.isMain, 'is-detail': !node.isMain }"
              :style="{ paddingLeft: `${8 + node.depth * 16}px` }"
              @click="toggleGroup(node.key)"
            >
              <span class="field-tree-arrow" :class="{ collapsed: collapsedGroups.has(node.key) }">▾</span>
              <span class="field-tree-label">{{ node.tableName || t('未命名') }}</span>
              <span v-if="node.tableComment" class="field-tree-comment">{{ node.tableComment }}</span>
              <span class="field-tree-count">{{ node.fields.length }}</span>
            </div>
            <div
              v-if="!collapsedGroups.has(node.key) && node.fields.length"
              class="field-tree-body"
              :style="{ paddingLeft: `${8 + (node.depth + 1) * 16}px` }"
            >
              <div
                v-for="item in node.fields"
                :key="`${node.key}-${item.dbField}`"
                class="field-tree-leaf"
                @click="onDbFieldClick(item)"
              >
                <span class="field-leaf-name">{{ item.dbField }}</span>
                <span class="field-leaf-type">{{ item.type }}</span>
                <span v-if="item.caption" class="field-leaf-caption">{{ item.caption }}</span>
              </div>
            </div>
          </template>
        </div>
        <div
          v-if="!dbFieldGroups.length && !orphanFields.length"
          class="panel-empty"
          style="padding: 40px 0"
        >
          {{ t('暂无数据库字段绑定，请在控件属性中填写数据库字段') }}
        </div>

        <!-- 孤儿字段（数据库有但 schema 已删除） -->
        <div v-if="orphanFields.length" class="orphan-section">
          <div class="orphan-title">{{ t('未绑定字段（可删除）') }}</div>
          <div
            v-for="f in orphanFields"
            :key="f.name"
            class="orphan-item"
          >
            <span class="orphan-name">{{ f.name }}</span>
            <span v-if="f.hasData" class="orphan-badge has-data">{{
              t('有数据')
            }}</span>
            <button
              v-else
              class="orphan-drop-btn"
              @click="onDropField(f)"
            >
              {{ t('删除') }}
            </button>
          </div>
        </div>
      </div>

      <!-- 控件树 tab -->
      <div v-if="panelTab === 'tree'" class="tab-content">
        <div v-if="designerStore.schema.length" class="tree-list">
          <div
            v-for="item in flatTreeNodes"
            :key="item.node.id"
            class="tree-item"
            :class="{
              'tree-selected': designerStore.selectedId === item.node.id,
            }"
            :style="{ paddingLeft: 8 + item.depth * 16 + 'px' }"
            @click="onTreeNodeClick(item.node)"
          >
            <span
              v-if="item.hasChildren"
              class="tree-expand"
              @click.stop="toggleTreeNode(item.node.id)"
              >{{ isTreeExpanded(item.node.id) ? '▼' : '▶' }}</span
            >
            <span v-else class="tree-expand-placeholder"></span>
            <span class="tree-label">{{ nodeLabel(item.node) }}</span>
          </div>
        </div>
        <div v-else class="panel-empty" style="padding: 40px 0">
          {{ t('画布上暂无控件') }}
        </div>
      </div>
    </template>

    <!-- 非表单模式：原有布局 -->
    <template v-else>
      <div class="panel-title">{{ panelTitle }}</div>
      <input
        v-model="searchKeyword"
        class="panel-search-input"
        :placeholder="t('搜索组件（中文名/英文名）')"
      />
      <!-- 移动端模式：从表单添加字段 -->
      <div v-if="isMobileMode" class="form-fields-section">
        <div class="group-title">{{ t('从表单添加字段') }}</div>
        <div v-if="formFieldNodes.length" class="form-fields-list">
          <div
            v-for="node in formFieldNodes"
            :key="node.id"
            class="form-field-item"
            :title="`${node.type} (fieldName: ${node.fieldName})`"
            @click="onAddFromForm(node)"
          >
            <span class="form-field-type">{{ node.type }}</span>
            <span class="form-field-name">{{ node.fieldName }}</span>
          </div>
        </div>
        <div v-else class="panel-empty" style="padding: 12px 0">
          {{ t('表单页面暂无字段，请先在表单模式添加控件') }}
        </div>
      </div>
      <div
        v-for="category in sortedCategories"
        :key="category"
        class="material-group"
      >
        <div class="group-title">{{ t(category) }}</div>
        <div class="group-items">
          <div
            v-for="item in filteredGroups[category]"
            :key="item.type"
            class="material-item"
            draggable="true"
            :title="
              item.enName ? `${t(item.title)} (${item.enName})` : t(item.title)
            "
            @dragstart="onDragStart($event, item.type)"
          >
            <span class="material-icon">{{ item.icon }}</span>
            <span class="material-name">{{ t(item.title) }}</span>
            <span v-if="item.enName" class="material-en-name">{{
              item.enName
            }}</span>
          </div>
        </div>
      </div>
      <div v-if="!sortedCategories.length" class="panel-empty">
        {{ t('没有匹配的组件') }}
      </div>
      <div class="panel-tip">{{ t('拖拽组件到中间画布') }}</div>
    </template>
  </aside>
</template>

<style scoped>
.material-panel {
  width: 240px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 6px;
  overflow-y: auto;
  padding: 12px;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}
.panel-tabs {
  display: flex;
  gap: 2px;
  margin-bottom: 10px;
  background: #f0f2f5;
  border-radius: 6px;
  padding: 3px;
}
.panel-tab-btn {
  flex: 1;
  border: none;
  background: transparent;
  padding: 5px 4px;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.panel-tab-btn:hover {
  color: #1976d2;
}
.tab-active {
  background: #fff;
  color: #1976d2;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.tab-content {
  min-height: 100px;
}
.panel-search-input {
  width: 100%;
  box-sizing: border-box;
  height: 30px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
  margin-bottom: 8px;
}
.panel-search-input:focus {
  border-color: #1976d2;
}
.group-title {
  font-size: 12px;
  color: #909399;
  margin: 12px 0 8px;
}
.group-items {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.material-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 8px 4px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: grab;
  background: #fafbfc;
  transition: all 0.15s;
  user-select: none;
}
.material-item:hover {
  border-color: #1976d2;
  color: #1976d2;
  background: #f0f7ff;
}
.material-icon {
  font-size: 18px;
}
.material-name {
  font-size: 12px;
}
.material-en-name {
  font-size: 10px;
  color: #909399;
  line-height: 1;
}
.panel-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  padding: 24px 0;
}
.panel-tip {
  margin-top: 20px;
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
}
.form-fields-section {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #e4e7ed;
}
.form-fields-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.form-field-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  background: #fafbfc;
  transition: all 0.15s;
}
.form-field-item:hover {
  border-color: #1976d2;
  color: #1976d2;
  background: #f0f7ff;
}
.form-field-type {
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
}
.form-field-name {
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 字段绑定 tab - 扁平化树形结构 */
.field-tree {
  display: flex;
  flex-direction: column;
}
.field-tree-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  cursor: pointer;
  user-select: none;
  transition: background 0.1s;
  white-space: nowrap;
  overflow: hidden;
  font-size: 12px;
}
.field-tree-header.is-main {
  background: #f0f9eb;
  color: #303133;
  font-weight: 600;
  border-bottom: 1px solid #e1f3d8;
}
.field-tree-header.is-detail {
  color: #606266;
  border-bottom: 1px solid #f0f2f5;
}
.field-tree-header:hover {
  background: #ecf5ff;
}
.field-tree-arrow {
  font-size: 10px;
  color: #c0c4cc;
  transition: transform 0.1s;
  flex-shrink: 0;
  display: inline-block;
  width: 10px;
}
.field-tree-arrow.collapsed {
  transform: rotate(-90deg);
}
.field-tree-label {
  color: inherit;
  overflow: hidden;
  text-overflow: ellipsis;
}
.field-tree-comment {
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
}
.field-tree-count {
  font-size: 10px;
  color: #fff;
  background: #c0c4cc;
  border-radius: 8px;
  padding: 0 5px;
  min-width: 14px;
  text-align: center;
  flex-shrink: 0;
  line-height: 14px;
}
.is-main .field-tree-count {
  background: #67c23a;
}
.field-tree-body {
  display: flex;
  flex-direction: column;
}
.field-tree-leaf {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  cursor: pointer;
  transition: background 0.1s;
  white-space: nowrap;
  overflow: hidden;
}
.field-tree-leaf:hover {
  background: #ecf5ff;
}
.field-leaf-name {
  font-size: 12px;
  color: #303133;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}
.field-leaf-type {
  font-size: 10px;
  color: #909399;
  background: #f5f7fa;
  padding: 0 4px;
  border-radius: 2px;
  flex-shrink: 0;
}
.field-leaf-caption {
  font-size: 11px;
  color: #c0c4cc;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-left: auto;
}
.field-tree-node {
  margin-bottom: 2px;
}
.field-tree-node.is-detail {
  margin-left: 12px;
}
.field-tree-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  cursor: pointer;
  user-select: none;
  border-radius: 4px;
  transition: background 0.15s;
  white-space: nowrap;
  overflow: hidden;
}
.field-tree-header:hover {
  background: #f0f7ff;
}
.is-main .field-tree-header {
  background: #f0f9eb;
  font-weight: 600;
}
.is-main .field-tree-header:hover {
  background: #e1f3d8;
}
.is-detail .field-tree-header {
  background: #f5f7fa;
  color: #606266;
}
.is-detail .field-tree-header:hover {
  background: #e6f0ff;
}
.field-tree-arrow {
  font-size: 10px;
  color: #909399;
  transition: transform 0.15s;
  flex-shrink: 0;
  display: inline-block;
}
.field-tree-arrow.collapsed {
  transform: rotate(-90deg);
}
.field-tree-icon {
  font-size: 13px;
  flex-shrink: 0;
}
.field-tree-label {
  font-size: 12px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
}
.field-tree-comment {
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
}
.field-tree-count {
  font-size: 10px;
  color: #fff;
  background: #c0c4cc;
  border-radius: 8px;
  padding: 0 5px;
  min-width: 16px;
  text-align: center;
  flex-shrink: 0;
}
.is-main .field-tree-count {
  background: #67c23a;
}
.field-tree-body {
  padding: 2px 0 2px 20px;
  display: flex;
  flex-direction: column;
}
.field-tree-leaf {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  border-radius: 3px;
  cursor: pointer;
  transition: background 0.1s;
  white-space: nowrap;
  overflow: hidden;
}
.field-tree-leaf:hover {
  background: #ecf5ff;
}
.field-leaf-name {
  font-size: 12px;
  color: #303133;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}
.field-leaf-type {
  font-size: 10px;
  color: #1976d2;
  background: #ecf2ff;
  padding: 0 4px;
  border-radius: 2px;
  flex-shrink: 0;
}
.field-leaf-caption {
  font-size: 11px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-left: auto;
}

/* 孤儿字段 */
.orphan-section {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #e4e7ed;
}
.orphan-title {
  font-size: 12px;
  color: #e6a23c;
  margin-bottom: 6px;
  font-weight: 600;
}
.orphan-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  background: #fef0f0;
  margin-bottom: 4px;
}
.orphan-name {
  font-size: 12px;
  color: #303133;
  flex: 1;
}
.orphan-badge.has-data {
  font-size: 10px;
  color: #f56c6c;
  background: #fde2e2;
  padding: 1px 6px;
  border-radius: 3px;
}
.orphan-drop-btn {
  font-size: 11px;
  color: #f56c6c;
  border: 1px solid #f56c6c;
  background: transparent;
  padding: 2px 8px;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.15s;
}
.orphan-drop-btn:hover {
  background: #f56c6c;
  color: #fff;
}

/* 控件树 tab */
.tree-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.tree-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 4px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.1s;
  font-size: 12px;
}
.tree-item:hover {
  background: #f0f7ff;
  color: #1976d2;
}
.tree-selected {
  background: #e6f0ff;
  color: #1976d2;
  font-weight: 600;
}
.tree-expand {
  font-size: 10px;
  color: #909399;
  cursor: pointer;
  width: 12px;
  text-align: center;
  flex-shrink: 0;
}
.tree-expand-placeholder {
  width: 12px;
  flex-shrink: 0;
}
.tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
