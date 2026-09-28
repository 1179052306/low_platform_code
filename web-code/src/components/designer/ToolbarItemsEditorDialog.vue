<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import EventCodeDialog from './EventCodeDialog.vue'
import PageSelectDialog from './PageSelectDialog.vue'
import { designerStore } from '@/store/designer'
import { t } from '@/store/lang'

export interface ToolbarItemConfig {
  name: string
  caption: string
  icon: string
  location: 'before' | 'center' | 'after'
  visible: boolean
  permCode?: string
  isCustom?: boolean
  onClick?: string
  openPage?: {
    pageId?: string
    params?: Record<string, unknown>
    mode?: string
    title?: string
  }
}

const PRESET_ITEMS: {
  name: string
  caption: string
  icon: string
  defaultLocation: 'before' | 'center' | 'after'
}[] = [
  { name: 'add', caption: t('新增'), icon: 'plus', defaultLocation: 'before' },
  {
    name: 'delete',
    caption: t('删除'),
    icon: 'trash',
    defaultLocation: 'before',
  },
  { name: 'edit', caption: t('编辑'), icon: 'edit', defaultLocation: 'before' },
  { name: 'save', caption: t('保存'), icon: 'save', defaultLocation: 'after' },
  {
    name: 'cancel',
    caption: t('取消'),
    icon: 'revert',
    defaultLocation: 'after',
  },
  {
    name: 'export',
    caption: t('导出'),
    icon: 'export',
    defaultLocation: 'after',
  },
  {
    name: 'refresh',
    caption: t('刷新'),
    icon: 'refresh',
    defaultLocation: 'after',
  },
]

const props = defineProps<{
  modelValue: string
  visible: boolean
  title?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:visible': [value: boolean]
}>()

const items = ref<ToolbarItemConfig[]>([])
const searchKeyword = ref('')
const expandedIdx = ref<number | null>(null)

// 事件代码编辑弹窗
const eventDialogVisible = ref(false)
const eventDialogValue = ref('')
const eventDialogItemIndex = ref<number | null>(null)

const filteredItems = computed(() => {
  if (!searchKeyword.value.trim()) return items.value
  const kw = searchKeyword.value.toLowerCase()
  return items.value.filter(
    (i) =>
      i.caption?.toLowerCase().includes(kw) ||
      i.name?.toLowerCase().includes(kw)
  )
})

const showSearch = computed(() => items.value.length > 5)

function getRealIndex(filteredIdx: number): number {
  const filtered = filteredItems.value
  if (filtered === items.value) return filteredIdx
  const target = filtered[filteredIdx]
  return items.value.indexOf(target)
}

watch(
  () => props.modelValue,
  (val) => {
    try {
      const parsed = JSON.parse(val || '[]')
      if (Array.isArray(parsed) && parsed.length > 0) {
        items.value = parsed.map((p: Record<string, unknown>) => ({
          name: String(p.name ?? ''),
          caption: String(p.caption ?? p.name ?? ''),
          icon: String(p.icon ?? ''),
          location: (p.location as 'before' | 'center' | 'after') || 'after',
          visible: p.visible !== false,
          permCode: p.permCode ? String(p.permCode) : String(p.name ?? ''),
          isCustom: Boolean(p.isCustom),
          onClick: p.onClick !== undefined ? String(p.onClick) : undefined,
          openPage: p.openPage as ToolbarItemConfig['openPage'],
        }))
      } else {
        // 默认显示预设按钮（全部不显示）
        items.value = PRESET_ITEMS.map((p) => ({
          name: p.name,
          caption: p.caption,
          icon: p.icon,
          location: p.defaultLocation,
          visible: false,
          permCode: p.name,
          isCustom: false,
        }))
      }
    } catch {
      items.value = PRESET_ITEMS.map((p) => ({
        name: p.name,
        caption: p.caption,
        icon: p.icon,
        location: p.defaultLocation,
        visible: false,
        permCode: p.name,
        isCustom: false,
      }))
    }
    expandedIdx.value = null
  },
  { immediate: true }
)

function toggleExpand(realIdx: number) {
  expandedIdx.value = expandedIdx.value === realIdx ? null : realIdx
}

function locationLabel(loc: string): string {
  switch (loc) {
    case 'before':
      return t('左侧')
    case 'center':
      return t('中间')
    case 'after':
      return t('右侧')
    default:
      return loc
  }
}

function addCustomItem() {
  const idx = items.value.length + 1
  const name = `custom_${Date.now()}`
  items.value.push({
    name,
    caption: t(`自定义按钮${idx}`),
    icon: 'more',
    location: 'after',
    visible: true,
    permCode: name,
    isCustom: true,
    onClick: '',
  })
  expandedIdx.value = items.value.length - 1
  searchKeyword.value = ''
}

function removeItem(realIdx: number) {
  const item = items.value[realIdx]
  if (!item.isCustom) return
  items.value.splice(realIdx, 1)
  if (expandedIdx.value === realIdx) {
    expandedIdx.value = null
  } else if (expandedIdx.value !== null && expandedIdx.value > realIdx) {
    expandedIdx.value--
  }
}

function moveUp(realIdx: number) {
  if (realIdx <= 0) return
  const tmp = items.value[realIdx]
  items.value[realIdx] = items.value[realIdx - 1]
  items.value[realIdx - 1] = tmp
  if (expandedIdx.value === realIdx) {
    expandedIdx.value = realIdx - 1
  } else if (expandedIdx.value === realIdx - 1) {
    expandedIdx.value = realIdx
  }
}

function moveDown(realIdx: number) {
  if (realIdx >= items.value.length - 1) return
  const tmp = items.value[realIdx]
  items.value[realIdx] = items.value[realIdx + 1]
  items.value[realIdx + 1] = tmp
  if (expandedIdx.value === realIdx) {
    expandedIdx.value = realIdx + 1
  } else if (expandedIdx.value === realIdx + 1) {
    expandedIdx.value = realIdx
  }
}

function openEventDialog(realIdx: number) {
  const item = items.value[realIdx]
  eventDialogItemIndex.value = realIdx
  eventDialogValue.value = item.onClick || ''
  eventDialogVisible.value = true
}

function onEventDialogConfirm(value: string) {
  if (eventDialogItemIndex.value !== null) {
    items.value[eventDialogItemIndex.value].onClick = value
  }
  eventDialogVisible.value = false
  eventDialogItemIndex.value = null
}

/** 页面选择弹窗 */
const pageSelectDialogVisible = ref(false)
const pageSelectItemIndex = ref<number | null>(null)
const pageSelectPageId = ref('')

function openPageSelectDialog(realIdx: number) {
  pageSelectItemIndex.value = realIdx
  pageSelectPageId.value = items.value[realIdx].openPage?.pageId || ''
  pageSelectDialogVisible.value = true
}

function onPageSelect(pageId: string) {
  if (pageSelectItemIndex.value === null) return
  const item = items.value[pageSelectItemIndex.value]
  if (!item.openPage) item.openPage = {}
  item.openPage.pageId = pageId
}

function clearPageSelect(realIdx: number) {
  const item = items.value[realIdx]
  if (item.openPage) item.openPage.pageId = ''
}

function setPageOpenMode(realIdx: number, mode: string) {
  const item = items.value[realIdx]
  if (!item.openPage) item.openPage = {}
  item.openPage.mode = mode
}

function onConfirm() {
  const result = items.value.map((i) => ({
    name: i.name,
    caption: i.caption,
    icon: i.icon,
    location: i.location,
    visible: i.visible,
    permCode: i.permCode || undefined,
    isCustom: i.isCustom,
    onClick: i.onClick || undefined,
    openPage: i.openPage,
  }))
  emit('update:modelValue', JSON.stringify(result))
  emit('update:visible', false)
}

function onCancel() {
  emit('update:visible', false)
}
</script>

<template>
  <div v-if="visible" class="dialog-overlay" @click.self="onCancel">
    <div class="dialog-container">
      <div class="dialog-header">
        <span class="dialog-title">{{ title || t('工具栏按钮配置') }}</span>
        <button class="dialog-close" @click="onCancel">x</button>
      </div>
      <div class="dialog-body">
        <div class="toolbar-editor">
          <!-- 搜索栏 + 添加按钮 -->
          <div class="toolbar">
            <input
              v-if="showSearch"
              class="search-input"
              v-model="searchKeyword"
              :placeholder="t('搜索按钮名称/标题...')"
            />
            <button class="action-btn-add" @click="addCustomItem">
              {{ t('+ 添加按钮') }}
            </button>
          </div>

          <div v-if="items.length === 0" class="table-empty-tip">
            <span>{{ t('暂无按钮配置，点击上方按钮添加') }}</span>
          </div>

          <!-- 按钮列表 -->
          <div class="item-list">
            <template
              v-for="(item, fidx) in filteredItems"
              :key="getRealIndex(fidx)"
            >
              <div
                class="item-card"
                :class="{ expanded: expandedIdx === getRealIndex(fidx) }"
              >
                <div
                  class="item-card-header"
                  @click="toggleExpand(getRealIndex(fidx))"
                >
                  <div class="item-card-summary">
                    <span
                      class="item-badge"
                      :class="item.isCustom ? 'badge-custom' : 'badge-preset'"
                    >
                      {{ item.isCustom ? t('自定义') : t('预设') }}
                    </span>
                    <span class="item-name">{{
                      item.caption || item.name
                    }}</span>
                    <span
                      class="item-field-tag"
                      v-if="item.caption !== item.name"
                      >({{ item.name }})</span
                    >
                    <span class="item-loc-tag">{{
                      locationLabel(item.location)
                    }}</span>
                    <span class="item-tag-hide" v-if="!item.visible">{{
                      t('隐藏')
                    }}</span>
                    <span class="item-tag-event" v-if="item.onClick">{{
                      t('有事件')
                    }}</span>
                  </div>
                  <div class="item-card-actions">
                    <label
                      class="vis-toggle"
                      :title="item.visible ? t('点击隐藏') : t('点击显示')"
                    >
                      <input
                        type="checkbox"
                        :checked="item.visible"
                        @change.stop="item.visible = !item.visible"
                      />
                      <span>{{ item.visible ? t('显示') : t('隐藏') }}</span>
                    </label>
                    <button
                      class="row-move"
                      @click.stop="moveUp(getRealIndex(fidx))"
                      :disabled="getRealIndex(fidx) === 0"
                      :title="t('上移')"
                    >
                      ↑
                    </button>
                    <button
                      class="row-move"
                      @click.stop="moveDown(getRealIndex(fidx))"
                      :disabled="getRealIndex(fidx) === items.length - 1"
                      :title="t('下移')"
                    >
                      ↓
                    </button>
                    <span class="expand-icon">{{
                      expandedIdx === getRealIndex(fidx) ? '▼' : '▶'
                    }}</span>
                    <button
                      v-if="item.isCustom"
                      class="row-delete"
                      @click.stop="removeItem(getRealIndex(fidx))"
                    >
                      {{ t('删除') }}
                    </button>
                  </div>
                </div>
                <div
                  v-if="expandedIdx === getRealIndex(fidx)"
                  class="item-card-body"
                >
                  <!-- 核心配置 -->
                  <div class="form-grid-2col">
                    <div class="form-row">
                      <label class="form-label">{{ t('按钮名称') }}</label>
                      <input
                        class="form-input"
                        v-model="item.name"
                        :disabled="!item.isCustom"
                        placeholder="name"
                      />
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('显示文本') }}</label>
                      <input
                        class="form-input"
                        v-model="item.caption"
                        placeholder="caption"
                      />
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('图标名称') }}</label>
                      <input
                        class="form-input"
                        v-model="item.icon"
                        :placeholder="t('DevExtreme 图标名')"
                      />
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('位置') }}</label>
                      <select class="form-select" v-model="item.location">
                        <option value="before">{{ t('左侧') }}</option>
                        <option value="center">{{ t('中间') }}</option>
                        <option value="after">{{ t('右侧') }}</option>
                      </select>
                    </div>
                  </div>

                  <!-- 开关组 -->
                  <div class="switch-group">
                    <label class="switch-item">
                      <input
                        type="checkbox"
                        v-model="item.visible"
                        :true-value="true"
                        :false-value="false"
                      />
                      <span>显示</span>
                    </label>
                  </div>

                  <!-- 权限码 -->
                  <div class="form-row" style="margin-top: 8px">
                    <label class="form-label">权限码</label>
                    <input
                      class="form-input"
                      v-model="item.permCode"
                      placeholder="留空则不鉴权"
                    />
                  </div>

                  <!-- 事件代码配置 -->
                  <div class="event-section">
                    <div class="event-section-header">
                      <span class="event-section-title"
                        >点击事件 (onClick)</span
                      >
                      <button
                        class="event-edit-btn"
                        @click="openEventDialog(getRealIndex(fidx))"
                      >
                        {{ item.onClick ? '编辑代码' : '编写事件' }}
                      </button>
                    </div>
                    <div v-if="item.onClick" class="event-code-preview">
                      <pre class="event-code-text">{{ item.onClick }}</pre>
                    </div>
                    <div v-else class="event-empty-tip">
                      暂无事件代码，点击右侧按钮编写
                    </div>
                  </div>

                  <!-- 打开页面配置 -->
                  <div class="event-section">
                    <div class="event-section-header">
                      <span class="event-section-title"
                        >打开页面 (openPage)</span
                      >
                      <button
                        class="event-edit-btn"
                        @click="openPageSelectDialog(getRealIndex(fidx))"
                      >
                        {{ item.openPage?.pageId ? '更换页面' : '选择页面' }}
                      </button>
                    </div>
                    <div
                      v-if="item.openPage?.pageId"
                      class="event-code-preview"
                    >
                      <pre class="event-code-text">
页面：{{ item.openPage.pageId }}</pre
                      >
                      <select
                        class="form-select page-mode-select"
                        :value="item.openPage.mode || 'popup'"
                        @change="setPageOpenMode(getRealIndex(fidx), ($event.target as HTMLSelectElement).value)"
                      >
                        <option value="popup">弹窗</option>
                        <option value="drawer">抽屉</option>
                      </select>
                      <button
                        class="event-edit-btn"
                        @click="clearPageSelect(getRealIndex(fidx))"
                      >
                        清除
                      </button>
                    </div>
                    <div v-else class="event-empty-tip">
                      未配置，点击右侧按钮选择要打开的页面
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
      <div class="dialog-footer">
        <button class="dialog-btn dialog-btn-cancel" @click="onCancel">
          取消
        </button>
        <button class="dialog-btn dialog-btn-confirm" @click="onConfirm">
          确定
        </button>
      </div>
    </div>

    <!-- 事件代码编辑弹窗 -->
    <EventCodeDialog
      v-model:model-value="eventDialogValue"
      v-model:visible="eventDialogVisible"
      title="按钮点击事件"
      @update:model-value="onEventDialogConfirm"
    />

    <!-- 页面选择弹窗 -->
    <PageSelectDialog
      v-model:visible="pageSelectDialogVisible"
      v-model:model-value="pageSelectPageId"
      :exclude-page-id="designerStore.pageId"
      @select="onPageSelect"
    />
  </div>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.dialog-container {
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  width: 680px;
  max-width: 95vw;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}
.dialog-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.dialog-close {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  color: #909399;
  padding: 0;
  line-height: 1;
}
.dialog-close:hover {
  color: #303133;
}
.dialog-body {
  padding: 12px 16px;
  flex: 1;
  overflow: auto;
}
.toolbar-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.table-empty-tip {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 20px;
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: space-between;
}
.search-input {
  flex: 1;
  max-width: 300px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 5px 10px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
}
.search-input:focus {
  border-color: #1976d2;
}

.action-btn-add {
  border: 1px dashed #1976d2;
  background: #ecf5ff;
  color: #1976d2;
  padding: 6px 14px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}
.action-btn-add:hover {
  background: #1976d2;
  color: #fff;
}

/* 按钮卡片列表 */
.item-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.item-card {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  transition: border-color 0.15s;
}
.item-card.expanded {
  border-color: #1976d2;
}
.item-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 12px;
  background: #f5f7fa;
  cursor: pointer;
}
.item-card-header:hover {
  background: #ecf5ff;
}
.item-card-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  flex-wrap: wrap;
}
.item-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  color: #fff;
}
.badge-preset {
  background: #909399;
}
.badge-custom {
  background: #e6a23c;
}
.item-name {
  font-weight: 500;
  color: #303133;
}
.item-field-tag {
  color: #909399;
  font-size: 12px;
}
.item-loc-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  background: #e8f4ff;
  color: #1976d2;
  border: 1px solid #b3d8ff;
}
.item-tag-hide {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  background: #f0f0f0;
  color: #909399;
}
.item-tag-event {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  background: #f0f9eb;
  color: #67c23a;
  border: 1px solid #c2e7b0;
}
.item-card-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.row-move {
  width: 22px;
  height: 22px;
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 3px;
  font-size: 11px;
  color: #606266;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.row-move:hover:not(:disabled) {
  border-color: #1976d2;
  color: #1976d2;
}
.row-move:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.vis-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  padding: 2px 6px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  background: #fff;
  user-select: none;
}
.vis-toggle:hover {
  border-color: #1976d2;
  color: #1976d2;
}
.vis-toggle input {
  width: 13px;
  height: 13px;
  cursor: pointer;
  margin: 0;
}
.expand-icon {
  color: #909399;
  font-size: 12px;
}
.row-delete {
  border: none;
  background: #fef0f0;
  color: #f56c6c;
  padding: 3px 8px;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
}
.row-delete:hover {
  background: #fde2e2;
}

/* 展开内容 */
.item-card-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 双列网格布局 */
.form-grid-2col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
}
.form-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.form-label {
  width: 80px;
  font-size: 12px;
  color: #606266;
  padding-top: 6px;
  flex-shrink: 0;
  text-align: right;
}
.form-input {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  min-width: 0;
}
.form-input:focus {
  border-color: #1976d2;
}
.form-input:disabled {
  background: #f5f7fa;
  color: #909399;
  cursor: not-allowed;
}
.form-select {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  background: #fff;
  min-width: 0;
}

/* 开关组 - 紧凑横排 */
.switch-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 20px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 4px;
}
.switch-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
}
.switch-item input {
  width: 15px;
  height: 15px;
  cursor: pointer;
}

/* 事件配置区域 */
.event-section {
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
}
.event-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.event-section-title {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}
.event-edit-btn {
  border: 1px solid #1976d2;
  background: #ecf5ff;
  color: #1976d2;
  padding: 3px 10px;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
}
.event-edit-btn:hover {
  background: #1976d2;
  color: #fff;
}
.event-code-preview {
  background: #f9fafc;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px 12px;
  max-height: 120px;
  overflow: auto;
}
.event-code-text {
  margin: 0;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
}
.event-empty-tip {
  font-size: 12px;
  color: #909399;
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 4px;
  border: 1px dashed #e4e7ed;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
}
.dialog-btn {
  padding: 6px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.dialog-btn-cancel {
  background: #fff;
  color: #606266;
}
.dialog-btn-cancel:hover {
  border-color: #1976d2;
  color: #1976d2;
}
.dialog-btn-confirm {
  background: #1976d2;
  color: #fff;
  border-color: #1976d2;
}
.dialog-btn-confirm:hover {
  background: #1565c0;
  border-color: #1565c0;
}
</style>
