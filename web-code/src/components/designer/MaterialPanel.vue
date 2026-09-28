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
]

/** 各业务模式允许的物料分类白名单（default 模式不过滤，显示全部） */
const BIZ_MODE_CATEGORIES: Record<string, string[]> = {
  form: ['布局', '表单', '操作', '基础', '弹层'],
  table: ['布局', '表单', '数据', '操作', '基础', '图表'],
  mobile: ['布局', '表单', '操作', '基础'],
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
}

// ---------- 字段绑定 tab ----------

/** 当前表单 schema 中有 dbField 的控件列表 */
const dbFieldNodes = computed<ComponentSchema[]>(() => {
  if (!isFormMode.value) return []
  return collectAllNodes(designerStore.schema).filter(
    (n) => n.dbField && n.dbField.trim(),
  )
})

/** 点击字段绑定项：选中画布中的控件 */
function onDbFieldClick(node: ComponentSchema): void {
  selectSingleNode(node.id)
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
    const dbFields = dbFieldNodes.value
      .map((n) => n.dbField!)
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
</script>

<template>
  <aside class="material-panel">
    <!-- 表单模式：tab 切换 -->
    <template v-if="isFormMode">
      <div class="panel-title">{{ panelTitle }}</div>
      <div class="panel-tabs">
        <button
          :class="['panel-tab-btn', panelTab === 'materials' ? 'tab-active' : '']"
          @click="panelTab = 'materials'"
        >
          {{ t('组件库') }}
        </button>
        <button
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

      <!-- 字段绑定 tab -->
      <div v-if="panelTab === 'fields'" class="tab-content">
        <div v-if="dbFieldNodes.length" class="db-field-list">
          <div
            v-for="node in dbFieldNodes"
            :key="node.id"
            class="db-field-item"
            @click="onDbFieldClick(node)"
          >
            <div class="db-field-name">{{ node.dbField }}</div>
            <div class="db-field-meta">
              <span class="db-field-type">{{ node.type }}</span>
              <span v-if="node.fieldName" class="db-field-fn"
                >{{ node.fieldName }}</span
              >
            </div>
          </div>
        </div>
        <div
          v-if="!dbFieldNodes.length && !orphanFields.length"
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
          <template v-for="node in designerStore.schema" :key="node.id">
            <div
              class="tree-item"
              :class="{
                'tree-selected': designerStore.selectedId === node.id,
              }"
              :style="{ paddingLeft: '8px' }"
              @click="onTreeNodeClick(node)"
            >
              <span
                v-if="node.children.length"
                class="tree-expand"
                @click.stop="toggleTreeNode(node.id)"
                >{{ isTreeExpanded(node.id) ? '▼' : '▶' }}</span
              >
              <span v-else class="tree-expand-placeholder"></span>
              <span class="tree-label">{{ nodeLabel(node) }}</span>
            </div>
            <!-- 递归子节点（一层层展开） -->
            <template
              v-if="node.children.length && isTreeExpanded(node.id)"
            >
              <div
                v-for="child in node.children"
                :key="child.id"
                class="tree-item"
                :class="{
                  'tree-selected': designerStore.selectedId === child.id,
                }"
                :style="{ paddingLeft: '24px' }"
                @click="onTreeNodeClick(child)"
              >
                <span
                  v-if="child.children.length"
                  class="tree-expand"
                  @click.stop="toggleTreeNode(child.id)"
                  >{{ isTreeExpanded(child.id) ? '▼' : '▶' }}</span
                >
                <span v-else class="tree-expand-placeholder"></span>
                <span class="tree-label">{{ nodeLabel(child) }}</span>
              </div>
              <!-- 三层嵌套 -->
              <template
                v-for="child in node.children"
                :key="'sub-' + child.id"
              >
                <template
                  v-if="child.children.length && isTreeExpanded(child.id)"
                >
                  <div
                    v-for="grandchild in child.children"
                    :key="grandchild.id"
                    class="tree-item"
                    :class="{
                      'tree-selected':
                        designerStore.selectedId === grandchild.id,
                    }"
                    :style="{ paddingLeft: '40px' }"
                    @click="onTreeNodeClick(grandchild)"
                  >
                    <span class="tree-expand-placeholder"></span>
                    <span class="tree-label">{{ nodeLabel(grandchild) }}</span>
                  </div>
                </template>
              </template>
            </template>
          </template>
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

/* 字段绑定 tab */
.db-field-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.db-field-item {
  padding: 8px 10px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: pointer;
  background: #fafbfc;
  transition: all 0.15s;
}
.db-field-item:hover {
  border-color: #1976d2;
  background: #f0f7ff;
}
.db-field-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 2px;
}
.db-field-meta {
  display: flex;
  gap: 8px;
  align-items: center;
}
.db-field-type {
  font-size: 11px;
  color: #1976d2;
  background: #e6f0ff;
  padding: 1px 6px;
  border-radius: 3px;
}
.db-field-fn {
  font-size: 11px;
  color: #909399;
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
