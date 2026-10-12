<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import EventCodeDialog from './EventCodeDialog.vue'
import PageSelectDialog from './PageSelectDialog.vue'
import { designerStore } from '@/store/designer'
import { t } from '@/store/lang'
import { ElSelect, ElOption } from 'element-plus'

export interface ToolbarItemConfig {
  name: string
  caption: string
  icon: string
  location: 'before' | 'center' | 'after'
  visible: boolean
  permCode?: string
  isCustom?: boolean
  onClick?: string
  type?: 'default' | 'normal' | 'success' | 'danger' | 'warning'
  stylingMode?: 'contained' | 'outlined' | 'text'
  showIcon?: boolean
  bgColor?: string
  textColor?: string
  hoverColor?: string
  itemType?: 'button' | 'dropdown'
  children?: ToolbarItemConfig[]
  openPage?: {
    pageId?: string
    params?: Record<string, unknown>
    mode?: string
    title?: string
    bizMode?: 'form' | 'table' | 'mobile'
    passSelectedRow?: boolean
  }
}

const PRESET_ITEMS: {
  name: string
  caption: string
  icon: string
  defaultLocation: 'before' | 'center' | 'after'
  itemType?: 'button' | 'dropdown'
  children?: ToolbarItemConfig[]
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
    itemType: 'dropdown',
    children: [
      { name: 'export_selected', caption: t('选中导出'), icon: 'export', location: 'after', visible: true, isCustom: false },
      { name: 'export_query', caption: t('查询导出'), icon: 'export', location: 'after', visible: true, isCustom: false },
      { name: 'export_template_selected', caption: t('模板选中导出'), icon: 'download', location: 'after', visible: true, isCustom: false },
      { name: 'export_template_query', caption: t('模板查询导出'), icon: 'download', location: 'after', visible: true, isCustom: false },
    ],
  },
  {
    name: 'refresh',
    caption: t('刷新'),
    icon: 'refresh',
    defaultLocation: 'after',
  },
]

/** 按钮类型对应的默认三色（背景/字体/悬停） */
const TYPE_DEFAULT_COLORS: Record<string, { bg: string; text: string; hover: string }> = {
  normal: { bg: '#909399', text: '#ffffff', hover: '#737373' },
  default: { bg: '#1976d2', text: '#ffffff', hover: '#1565c0' },
  success: { bg: '#67c23a', text: '#ffffff', hover: '#529b2e' },
  danger: { bg: '#f56c6c', text: '#ffffff', hover: '#dd4f4f' },
  warning: { bg: '#e6a23c', text: '#ffffff', hover: '#cf8a23' },
}

/** DevExtreme 常用图标列表（供可搜索下拉选择） */
const DX_ICONS: string[] = [
  'plus', 'add', 'minus', 'remove', 'trash', 'edit', 'save', 'revert', 'close', 'check',
  'checkmarkcircle', 'clear', 'refresh', 'export', 'download', 'upload', 'print', 'copy', 'paste', 'cut',
  'undo', 'redo', 'filter', 'find', 'search', 'group', 'column', 'card', 'chart', 'clock',
  'user', 'info', 'help', 'warning', 'favorites', 'menu', 'more', 'message', 'chat', 'comment',
  'link', 'image', 'photo', 'video', 'doc', 'folder', 'file', 'csv', 'attach', 'send',
  'share', 'bookmark', 'map', 'home', 'globe', 'lock', 'unlock', 'key', 'expand', 'collapse',
  'back', 'right', 'box', 'cart', 'bell', 'coffee', 'like', 'pin', 'diagram', 'codeblock',
  'arrowup', 'arrowdown', 'arrowleft', 'arrowright', 'chevrondown', 'chevronup', 'chevronleft', 'chevronright',
  'bold', 'alignleft', 'alignright', 'aligncenter', 'alignjustify', 'bulletlist',
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
const childExpandedIdx = ref<string | null>(null)
const dragIdx = ref<number | null>(null)
const dragOverIdx = ref<number | null>(null)

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

function defaultOpenPageForPreset(name: string): ToolbarItemConfig['openPage'] {
  if (name === 'add') return { pageId: '__current__', bizMode: 'form', title: t('新增') }
  if (name === 'edit') return { pageId: '__current__', bizMode: 'form', passSelectedRow: true, title: t('修改') }
  return undefined
}

function defaultChildrenForPreset(name: string): ToolbarItemConfig[] | undefined {
  if (name === 'export') return [
    { name: 'export_selected', caption: t('选中导出'), icon: 'export', location: 'after', visible: true, isCustom: false },
    { name: 'export_query', caption: t('查询导出'), icon: 'export', location: 'after', visible: true, isCustom: false },
    { name: 'export_template_selected', caption: t('模板选中导出'), icon: 'download', location: 'after', visible: true, isCustom: false },
    { name: 'export_template_query', caption: t('模板查询导出'), icon: 'download', location: 'after', visible: true, isCustom: false },
  ]
  return undefined
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
          type: p.type as ToolbarItemConfig['type'],
          stylingMode: p.stylingMode as ToolbarItemConfig['stylingMode'],
          bgColor: p.bgColor ? String(p.bgColor) : undefined,
          textColor: p.textColor ? String(p.textColor) : undefined,
          hoverColor: p.hoverColor ? String(p.hoverColor) : undefined,
          showIcon: p.showIcon !== false,
          itemType: (p.itemType as 'button' | 'dropdown') || (p.children ? 'dropdown' : (!Boolean(p.isCustom) && defaultChildrenForPreset(String(p.name ?? '')) ? 'dropdown' : 'button')),
          children: (p.children as ToolbarItemConfig[])?.map((c) => ({ ...c, visible: c.visible !== false, showIcon: c.showIcon !== false })) ?? (!Boolean(p.isCustom) ? defaultChildrenForPreset(String(p.name ?? '')) : undefined),
          openPage: (p.openPage as ToolbarItemConfig['openPage']) ?? (!Boolean(p.isCustom) ? defaultOpenPageForPreset(String(p.name ?? '')) : undefined),
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
          itemType: p.itemType || 'button',
          children: p.children,
          openPage: defaultOpenPageForPreset(p.name),
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
        itemType: p.itemType || 'button',
        children: p.children,
        openPage: defaultOpenPageForPreset(p.name),
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

function onDragStart(realIdx: number) {
  dragIdx.value = realIdx
}
function onDragOver(realIdx: number) {
  if (dragIdx.value === null || dragIdx.value === realIdx) return
  dragOverIdx.value = realIdx
}
function onDrop(realIdx: number) {
  if (dragIdx.value === null || dragIdx.value === realIdx) {
    dragIdx.value = null
    dragOverIdx.value = null
    return
  }
  const from = dragIdx.value
  const moved = items.value.splice(from, 1)[0]
  items.value.splice(realIdx, 0, moved)
  if (expandedIdx.value === from) expandedIdx.value = realIdx
  dragIdx.value = null
  dragOverIdx.value = null
}
function onDragEnd() {
  dragIdx.value = null
  dragOverIdx.value = null
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

function setPageOpenBizMode(realIdx: number, bizMode: string) {
  const item = items.value[realIdx]
  if (!item.openPage) item.openPage = {}
  item.openPage.bizMode = bizMode as 'form' | 'table' | 'mobile'
}

function setPageOpenTitle(realIdx: number, title: string) {
  const item = items.value[realIdx]
  if (!item.openPage) item.openPage = {}
  item.openPage.title = title
}

function useCurrentPage(realIdx: number) {
  const item = items.value[realIdx]
  if (!item.openPage) item.openPage = {}
  item.openPage.pageId = '__current__'
}

function togglePassSelectedRow(realIdx: number, val: boolean) {
  const item = items.value[realIdx]
  if (!item.openPage) item.openPage = {}
  item.openPage.passSelectedRow = val
}

function pageIdDisplay(pageId: string): string {
  return pageId === '__current__' ? t('当前页面') : pageId
}

function addChild(realIdx: number) {
  const item = items.value[realIdx]
  if (!item.children) item.children = []
  const idx = item.children.length + 1
  const name = `custom_export_${Date.now()}`
  item.children.push({
    name,
    caption: t(`子按钮${idx}`),
    icon: 'export',
    location: 'after',
    visible: true,
    isCustom: true,
  })
}

function onItemTypeChange(item: ToolbarItemConfig): void {
  if (item.itemType === 'dropdown' && (!item.children || item.children.length === 0)) {
    item.children = []
  }
}

function toggleChildExpand(realIdx: number, childIdx: number): void {
  const key = `${realIdx}-${childIdx}`
  childExpandedIdx.value = childExpandedIdx.value === key ? null : key
}

function removeChild(realIdx: number, childIdx: number) {
  const item = items.value[realIdx]
  if (!item.children) return
  item.children.splice(childIdx, 1)
}

/** 切换按钮类型时自动填充该类型的默认三色 */
function onTypeChange(item: ToolbarItemConfig): void {
  const defaults = TYPE_DEFAULT_COLORS[item.type || 'normal']
  if (defaults) {
    item.bgColor = defaults.bg
    item.textColor = defaults.text
    item.hoverColor = defaults.hover
  }
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
    type: i.type,
    stylingMode: i.stylingMode,
    bgColor: i.bgColor || undefined,
    textColor: i.textColor || undefined,
    hoverColor: i.hoverColor || undefined,
    showIcon: i.showIcon,
    itemType: i.itemType || 'button',
    children: i.children,
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
                :class="{ expanded: expandedIdx === getRealIndex(fidx), 'drag-over': dragOverIdx === getRealIndex(fidx), dragging: dragIdx === getRealIndex(fidx) }"
                draggable="true"
                @dragstart="onDragStart(getRealIndex(fidx))"
                @dragover.prevent="onDragOver(getRealIndex(fidx))"
                @drop="onDrop(getRealIndex(fidx))"
                @dragend="onDragEnd"
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
                      <label class="form-label">{{ t('显示图标') }}</label>
                      <select class="form-select" v-model="item.showIcon">
                        <option :value="true">{{ t('显示') }}</option>
                        <option :value="false">{{ t('隐藏') }}</option>
                      </select>
                    </div>
                    <div class="form-row" v-if="item.showIcon !== false">
                      <label class="form-label">{{ t('图标') }}</label>
                      <ElSelect
                        v-model="item.icon"
                        filterable
                        :placeholder="t('搜索选择图标')"
                        popper-class="lc-icon-select-popper"
                        style="flex: 1; min-width: 0"
                      >
                        <ElOption
                          v-for="ic in DX_ICONS"
                          :key="ic"
                          :label="ic"
                          :value="ic"
                        >
                          <i
                            :class="`dx-icon-${ic}`"
                            style="margin-right: 8px; vertical-align: middle"
                          />
                          <span>{{ ic }}</span>
                        </ElOption>
                      </ElSelect>
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('位置') }}</label>
                      <select class="form-select" v-model="item.location">
                        <option value="before">{{ t('左侧') }}</option>
                        <option value="center">{{ t('中间') }}</option>
                        <option value="after">{{ t('右侧') }}</option>
                      </select>
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('按钮类型') }}</label>
                      <select class="form-select" v-model="item.type" @change="onTypeChange(item)">
                        <option value="normal">{{ t('默认') }}</option>
                        <option value="default">{{ t('标准') }}</option>
                        <option value="success">{{ t('成功') }}</option>
                        <option value="danger">{{ t('危险') }}</option>
                        <option value="warning">{{ t('警告') }}</option>
                      </select>
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('样式模式') }}</label>
                      <select class="form-select" v-model="item.stylingMode">
                        <option value="text">{{ t('文字') }}</option>
                        <option value="contained">{{ t('实心') }}</option>
                        <option value="outlined">{{ t('描边') }}</option>
                      </select>
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('控件类型') }}</label>
                      <select class="form-select" v-model="item.itemType" @change="onItemTypeChange(item)">
                        <option value="button">{{ t('按钮') }}</option>
                        <option value="dropdown">{{ t('下拉按钮') }}</option>
                      </select>
                    </div>
                  </div>

                  <!-- 下拉子按钮配置 -->
                  <div v-if="item.itemType === 'dropdown'" class="children-section">
                    <div class="children-header">
                      <span class="event-section-title">{{ t('子按钮') }}</span>
                      <button class="event-edit-btn" @click.stop="addChild(getRealIndex(fidx))">{{ t('+ 添加') }}</button>
                    </div>
                    <div v-if="!item.children || item.children.length === 0" class="event-empty-tip">
                      {{ t('暂无子按钮，点击上方按钮添加') }}
                    </div>
                    <div v-for="(child, ci) in item.children" :key="ci" class="child-card">
                      <div class="child-card-header" @click.stop="toggleChildExpand(getRealIndex(fidx), ci)">
                        <label class="compact-check" @click.stop>
                          <input type="checkbox" v-model="child.visible" />
                          <span></span>
                        </label>
                        <input class="compact-input" v-model="child.caption" :placeholder="t('显示文本')" />
                        <i v-if="child.showIcon !== false && child.icon" :class="`dx-icon-${child.icon}`" class="child-icon-preview" />
                        <span class="expand-icon">{{ childExpandedIdx === `${getRealIndex(fidx)}-${ci}` ? '▼' : '▶' }}</span>
                        <button v-if="child.isCustom" class="row-delete" @click.stop="removeChild(getRealIndex(fidx), ci)">{{ t('删除') }}</button>
                      </div>
                      <div v-if="childExpandedIdx === `${getRealIndex(fidx)}-${ci}`" class="child-card-body">
                        <div class="child-form-row">
                          <label class="open-page-label">{{ t('图标') }}</label>
                          <label class="compact-check">
                            <input type="checkbox" v-model="child.showIcon" />
                            <span>{{ t('显示') }}</span>
                          </label>
                          <ElSelect
                            v-if="child.showIcon !== false"
                            v-model="child.icon"
                            filterable
                            clearable
                            :placeholder="t('选择图标')"
                            popper-class="lc-icon-select-popper"
                            style="flex: 1; min-width: 0"
                          >
                            <ElOption v-for="ic in DX_ICONS" :key="ic" :label="ic" :value="ic">
                              <i :class="`dx-icon-${ic}`" style="margin-right: 8px; vertical-align: middle" />
                              <span>{{ ic }}</span>
                            </ElOption>
                          </ElSelect>
                        </div>
                        <div class="color-inline-row">
                          <span class="color-inline-label">{{ t('配色') }}</span>
                          <label class="color-inline-item">
                            <span class="color-inline-tag">{{ t('背景') }}</span>
                            <input class="color-picker-sm" type="color" :value="child.bgColor || '#1976d2'" @input="child.bgColor = ($event.target as HTMLInputElement).value" />
                            <button v-if="child.bgColor" class="color-clear-xs" type="button" @click="child.bgColor = undefined">×</button>
                          </label>
                          <label class="color-inline-item">
                            <span class="color-inline-tag">{{ t('字体') }}</span>
                            <input class="color-picker-sm" type="color" :value="child.textColor || '#ffffff'" @input="child.textColor = ($event.target as HTMLInputElement).value" />
                            <button v-if="child.textColor" class="color-clear-xs" type="button" @click="child.textColor = undefined">×</button>
                          </label>
                          <label class="color-inline-item">
                            <span class="color-inline-tag">{{ t('悬停') }}</span>
                            <input class="color-picker-sm" type="color" :value="child.hoverColor || '#1565c0'" @input="child.hoverColor = ($event.target as HTMLInputElement).value" />
                            <button v-if="child.hoverColor" class="color-clear-xs" type="button" @click="child.hoverColor = undefined">×</button>
                          </label>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- 配色：背景/字体/悬停 三色一行 -->
                  <div class="color-inline-row">
                    <span class="color-inline-label">{{ t('配色') }}</span>
                    <label class="color-inline-item">
                      <span class="color-inline-tag">{{ t('背景') }}</span>
                      <input class="color-picker-sm" type="color" :value="item.bgColor || '#1976d2'" @input="item.bgColor = ($event.target as HTMLInputElement).value" />
                      <button v-if="item.bgColor" class="color-clear-xs" type="button" @click="item.bgColor = undefined">×</button>
                    </label>
                    <label class="color-inline-item">
                      <span class="color-inline-tag">{{ t('字体') }}</span>
                      <input class="color-picker-sm" type="color" :value="item.textColor || '#ffffff'" @input="item.textColor = ($event.target as HTMLInputElement).value" />
                      <button v-if="item.textColor" class="color-clear-xs" type="button" @click="item.textColor = undefined">×</button>
                    </label>
                    <label class="color-inline-item">
                      <span class="color-inline-tag">{{ t('悬停') }}</span>
                      <input class="color-picker-sm" type="color" :value="item.hoverColor || '#1565c0'" @input="item.hoverColor = ($event.target as HTMLInputElement).value" />
                      <button v-if="item.hoverColor" class="color-clear-xs" type="button" @click="item.hoverColor = undefined">×</button>
                    </label>
                  </div>

                  <!-- 显示 + 权限码 一行 -->
                  <div class="compact-row">
                    <label class="compact-check">
                      <input type="checkbox" v-model="item.visible" :true-value="true" :false-value="false" />
                      <span>{{ t('显示') }}</span>
                    </label>
                    <label class="compact-label">{{ t('权限码') }}</label>
                    <input class="compact-input" v-model="item.permCode" :placeholder="t('留空不鉴权')" />
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
                      <div class="open-page-btns">
                        <button
                          class="event-edit-btn"
                          @click="useCurrentPage(getRealIndex(fidx))"
                        >
                          {{ t('当前页面') }}
                        </button>
                        <button
                          class="event-edit-btn"
                          @click="openPageSelectDialog(getRealIndex(fidx))"
                        >
                          {{ item.openPage?.pageId && item.openPage.pageId !== '__current__' ? '更换页面' : '选择页面' }}
                        </button>
                      </div>
                    </div>
                    <div
                      v-if="item.openPage?.pageId"
                      class="open-page-config"
                    >
                      <div class="open-page-row">
                        <span class="open-page-label">{{ t('页面') }}</span>
                        <span class="open-page-value">{{ pageIdDisplay(item.openPage.pageId) }}</span>
                        <button
                          class="event-edit-btn"
                          @click="clearPageSelect(getRealIndex(fidx))"
                        >
                          清除
                        </button>
                      </div>
                      <div class="open-page-row">
                        <label class="open-page-label">{{ t('模式') }}</label>
                        <select
                          class="form-select page-mode-select"
                          :value="item.openPage.bizMode || 'form'"
                          @change="setPageOpenBizMode(getRealIndex(fidx), ($event.target as HTMLSelectElement).value)"
                        >
                          <option value="form">{{ t('表单') }}</option>
                          <option value="table">{{ t('表格') }}</option>
                          <option value="mobile">{{ t('移动端') }}</option>
                        </select>
                        <label class="open-page-label">{{ t('形式') }}</label>
                        <select
                          class="form-select page-mode-select"
                          :value="item.openPage.mode || 'popup'"
                          @change="setPageOpenMode(getRealIndex(fidx), ($event.target as HTMLSelectElement).value)"
                        >
                          <option value="popup">{{ t('弹窗') }}</option>
                          <option value="drawer">{{ t('抽屉') }}</option>
                        </select>
                      </div>
                      <div class="open-page-row">
                        <label class="open-page-label">{{ t('标题') }}</label>
                        <input
                          class="compact-input"
                          type="text"
                          :value="item.openPage.title || ''"
                          :placeholder="item.caption || t('留空用默认')"
                          @input="setPageOpenTitle(getRealIndex(fidx), ($event.target as HTMLInputElement).value)"
                        />
                        <template v-if="item.name === 'edit'">
                          <label class="open-page-label">{{ t('传行') }}</label>
                          <select
                            class="form-select page-mode-select"
                            :value="item.openPage.passSelectedRow === false ? 'false' : 'true'"
                            @change="togglePassSelectedRow(getRealIndex(fidx), ($event.target as HTMLSelectElement).value === 'true')"
                          >
                            <option value="true">{{ t('是') }}</option>
                            <option value="false">{{ t('否') }}</option>
                          </select>
                        </template>
                      </div>
                    </div>

                    <div v-else class="event-empty-tip">
                      {{ t('未配置，点击上方按钮选择要打开的页面') }}
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
.item-card.dragging {
  opacity: 0.5;
}
.item-card.drag-over {
  border-color: #1976d2;
  border-style: dashed;
  box-shadow: 0 0 0 2px rgba(25, 118, 210, 0.1);
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
.color-row {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.color-text-input {
  flex: 1;
  min-width: 0;
}
.color-picker {
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  flex-shrink: 0;
}
.color-clear-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: #f5f7fa;
  border-radius: 4px;
  cursor: pointer;
  color: #909399;
  font-size: 14px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.color-clear-btn:hover {
  background: #f0f0f0;
  color: #f56c6c;
}
/* 紧凑配色行 */
.color-inline-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #fafafa;
  border-radius: 4px;
}
/* 下拉子按钮配置区 */
.children-section {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
  background: #fafafa;
}
.children-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.child-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.child-card {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  margin-bottom: 4px;
  overflow: hidden;
}
.child-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  background: #f5f7fa;
  cursor: pointer;
}
.child-card-header:hover {
  background: #ecf5ff;
}
.child-icon-preview {
  font-size: 14px;
  color: #606266;
  flex-shrink: 0;
}
.child-card-body {
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #fff;
}
.child-form-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.color-inline-label {
  font-size: 12px;
  color: #606266;
  flex-shrink: 0;
}
.color-inline-item {
  display: flex;
  align-items: center;
  gap: 3px;
  cursor: pointer;
}
.color-inline-tag {
  font-size: 11px;
  color: #909399;
}
.color-picker-sm {
  width: 24px;
  height: 24px;
  padding: 0;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  background: #fff;
  cursor: pointer;
}
.color-clear-xs {
  width: 16px;
  height: 16px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: #c0c4cc;
  font-size: 12px;
  line-height: 1;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.color-clear-xs:hover {
  color: #f56c6c;
}
/* 紧凑行：显示+权限码 */
.compact-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.compact-check {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}
.compact-check input {
  width: 14px;
  height: 14px;
  cursor: pointer;
  margin: 0;
}
.compact-label {
  font-size: 12px;
  color: #606266;
  flex-shrink: 0;
}
.compact-input {
  flex: 1;
  min-width: 0;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 3px 8px;
  font-size: 12px;
  outline: none;
  box-sizing: border-box;
}
.compact-input:focus {
  border-color: #1976d2;
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
.open-page-btns {
  display: flex;
  gap: 6px;
}
.open-page-config {
  background: #f9fafc;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.open-page-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.open-page-label {
  font-size: 12px;
  color: #606266;
  width: 70px;
  flex-shrink: 0;
  text-align: right;
}
.open-page-value {
  font-size: 12px;
  color: #303133;
  font-weight: 500;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.page-mode-select {
  flex: 1;
  min-width: 0;
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
