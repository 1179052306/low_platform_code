<script setup lang="ts">
import { computed } from 'vue'
import type { ComponentSchema } from '@/types/schema'
import { designerStore, resolveNodeStyle } from '@/store/designer'
import { getConfig } from '@/materials'
import {
  Button as TButton,
  Cell as TCell,
  Navbar as TNavbar,
  Image as TImage,
  TabBar as TTabBar,
  Fab as TFab,
  Avatar as TAvatar,
  Tag as TTag,
  Empty as TEmpty,
  Input as TInput,
  Switch as TSwitch,
  Stepper as TStepper,
  Rate as TRate,
  Search as TSearch,
  Slider as TSlider,
  Badge as TBadge,
  NoticeBar as TNoticeBar,
  Progress as TProgress,
  Divider as TDivider,
  Loading as TLoading,
  Result as TResult,
  Textarea as TTextarea,
  Popup as TPopup,
} from 'tdesign-mobile-vue'

const props = withDefaults(
  defineProps<{
    node: ComponentSchema
    parentId?: string | null
    index?: number
    positioned?: boolean
    selected?: boolean
  }>(),
  { parentId: null, index: 0, positioned: false, selected: false }
)

const emit = defineEmits<{
  (e: 'drag-start', payload: { id: string; x: number; y: number }): void
  (e: 'resize-start', payload: { id: string; dir: string; x: number; y: number }): void
  (e: 'select-single', id: string): void
}>()

const cfg = computed(() => getConfig(props.node.type))
const isContainer = computed(() => !!cfg.value?.isContainer)
const isSelected = computed(
  () => props.selected ?? designerStore.selectedId === props.node.id
)
const isPositioned = computed(() => props.positioned === true)

const mobileLayout = computed(() => {
  void designerStore.schemaRevision
  const layout = (props.node.props.layout as string) || 'vertical'
  const gap = (props.node.props.gap as number) ?? 8
  const padding = (props.node.props.padding as number) ?? 0
  return { layout, gap, padding }
})

const mobileChildrenPositioned = computed(
  () => mobileLayout.value.layout === 'free'
)

const mobileContainerStyle = computed(() => {
  const l = mobileLayout.value
  if (l.layout === 'free') {
    return { position: 'relative' as const, padding: `${l.padding}px` }
  }
  return {
    display: 'flex' as const,
    flexDirection: (l.layout === 'horizontal' ? 'row' : 'column') as 'row' | 'column',
    gap: `${l.gap}px`,
    padding: `${l.padding}px`,
  }
})

const wrapperStyle = computed(() => {
  void designerStore.schemaRevision
  const style: Record<string, string> = {
    maxWidth: '100%',
    boxSizing: 'border-box',
  }
  if (isPositioned.value) {
    style.position = 'absolute'
    style.left = `${props.node.x ?? 0}px`
    style.top = `${props.node.y ?? 0}px`
  }
  const w = props.node.width as unknown
  if (w) style.width = typeof w === 'string' ? w : `${w}px`
  const h = props.node.height as unknown
  if (h) style.height = typeof h === 'string' ? h : `${h}px`
  Object.assign(style, resolveNodeStyle(props.node))
  return style
})

/** TabBar 项 */
const tabBarItems = computed(() => {
  const tabs = props.node.props.tabs as Array<Record<string, unknown>> | undefined
  if (!tabs || !Array.isArray(tabs)) return []
  return tabs.map((t) => ({ label: String(t.label ?? ''), icon: String(t.icon ?? '') }))
})

/** 底部导航项 */
const bottomNavItems = computed(() => {
  const items = props.node.props.items as Array<Record<string, unknown>> | undefined
  if (!items || !Array.isArray(items)) return []
  return items.map((it) => ({ label: String(it.label ?? ''), icon: String(it.icon ?? '') }))
})

function isNodeSelected(id: string): boolean {
  return designerStore.selectedId === id
}

function onClick(e: MouseEvent): void {
  e.stopPropagation()
  emit('select-single', props.node.id)
}

function onDragStart(e: MouseEvent): void {
  if (e.button !== 0) return
  e.stopPropagation()
  emit('drag-start', { id: props.node.id, x: e.clientX, y: e.clientY })
}

function onResizeStart(e: MouseEvent, dir: string): void {
  e.stopPropagation()
  e.preventDefault()
  emit('resize-start', { id: props.node.id, dir, x: e.clientX, y: e.clientY })
}

const RESIZE_DIRS = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w']
</script>

<template>
  <div
    class="mobile-node"
    :class="{
      'mobile-node-selected': isSelected,
      'mobile-node-positioned': isPositioned,
      'mobile-node-container': isContainer,
    }"
    :style="wrapperStyle"
    @mousedown="onDragStart"
    @click="onClick"
  >
    <div class="mwp" :class="'mwp-' + node.type">
      <!-- 页面骨架 -->
      <template v-if="node.type === 'm-scaffold'">
        <t-navbar :title="String(node.props.title || '页面标题')" :show-back="true" />
        <div class="mwp-body" :style="mobileContainerStyle">
          <div v-if="!node.children?.length" class="mwp-empty">
            拖入组件到此区域
          </div>
          <MobileCanvasNode
            v-for="(child, i) in node.children"
            :key="child.id"
            :node="child"
            :parent-id="node.id"
            :index="i"
            :positioned="mobileChildrenPositioned"
            :selected="isNodeSelected(child.id)"
            @drag-start="emit('drag-start', $event)"
            @resize-start="emit('resize-start', $event)"
            @select-single="emit('select-single', $event)"
          />
        </div>
      </template>

      <!-- 列表 -->
      <template v-else-if="node.type === 'm-list-view'">
        <t-cell
          v-for="i in 4"
          :key="i"
          title="列表项标题"
          description="列表项描述内容"
          arrow
        />
      </template>

      <!-- 卡片容器 -->
      <template v-else-if="node.type === 'm-card'">
        <div class="mwp-card-body" :style="mobileContainerStyle">
          <div v-if="!node.children?.length" class="mwp-empty">
            拖入组件到此卡片
          </div>
          <MobileCanvasNode
            v-for="(child, i) in node.children"
            :key="child.id"
            :node="child"
            :parent-id="node.id"
            :index="i"
            :positioned="mobileChildrenPositioned"
            :selected="isNodeSelected(child.id)"
            @drag-start="emit('drag-start', $event)"
            @resize-start="emit('resize-start', $event)"
            @select-single="emit('select-single', $event)"
          />
        </div>
      </template>

      <!-- 导航栏 -->
      <template v-else-if="node.type === 'm-app-bar'">
        <t-navbar :title="String(node.props.title || '标题')" :show-back="true" />
      </template>

      <!-- 底部导航 -->
      <template v-else-if="node.type === 'm-bottom-nav'">
        <t-tab-bar
          :value="0"
          split
        >
          <t-tab-bar-item
            v-for="(item, i) in bottomNavItems"
            :key="i"
            :value="i"
            :label="item.label || `标签${i + 1}`"
          />
        </t-tab-bar>
      </template>

      <!-- 悬浮按钮 -->
      <template v-else-if="node.type === 'm-fab'">
        <t-fab text="+" />
      </template>

      <!-- 头像 -->
      <template v-else-if="node.type === 'm-avatar'">
        <t-avatar image="https://tdesign.gtimg.com/mobile/demos/avatar1.png" />
      </template>

      <!-- 顶部标签栏 -->
      <template v-else-if="node.type === 'm-tab-bar'">
        <div class="mwp-tab-header">
          <div
            v-for="(item, i) in tabBarItems.length ? tabBarItems : [{ label: '标签1' }, { label: '标签2' }, { label: '标签3' }]"
            :key="i"
            class="mwp-tab-item"
            :class="{ 'mwp-tab-active': i === 0 }"
          >
            {{ item.label }}
          </div>
        </div>
        <div class="mwp-tab-body" :style="mobileContainerStyle">
          <div v-if="!node.children?.length" class="mwp-empty">
            拖入组件到此选项卡
          </div>
          <MobileCanvasNode
            v-for="(child, i) in node.children"
            :key="child.id"
            :node="child"
            :parent-id="node.id"
            :index="i"
            :positioned="mobileChildrenPositioned"
            :selected="isNodeSelected(child.id)"
            @drag-start="emit('drag-start', $event)"
            @resize-start="emit('resize-start', $event)"
            @select-single="emit('select-single', $event)"
          />
        </div>
      </template>

      <!-- 图片 -->
      <template v-else-if="node.type === 'm-image'">
        <t-image
          src="https://tdesign.gtimg.com/mobile/demos/image1.png"
          fit="cover"
          style="width: 100%; height: 100px; border-radius: 4px;"
        />
      </template>

      <!-- 按钮 -->
      <template v-else-if="node.type === 'button'">
        <t-button block>{{ node.props.label || node.props.text || '按钮' }}</t-button>
      </template>

      <!-- 输入框 -->
      <template v-else-if="node.type === 'text-box' || node.type === 'text-area'">
        <t-input
          :placeholder="String(node.props.placeholder || '请输入')"
          :label="String(node.props.label || '')"
        />
      </template>

      <!-- 开关 -->
      <template v-else-if="node.type === 'switch' || node.type === 'switch-field'">
        <div class="mwp-switch-row">
          <span>{{ node.props.label || '开关' }}</span>
          <t-switch :value="true" />
        </div>
      </template>

      <!-- 标签 -->
      <template v-else-if="node.type === 'tag' || node.type === 'tag-box'">
        <t-tag>{{ node.props.text || node.props.label || '标签' }}</t-tag>
      </template>

      <!-- 移动端按钮 -->
      <template v-else-if="node.type === 'm-button'">
        <t-button
          :theme="(node.props.theme || 'primary') as any"
          :size="(node.props.size || 'small') as any"
          :block="!!node.props.block"
        >{{ node.props.text || '按钮' }}</t-button>
      </template>

      <!-- 移动端输入框 -->
      <template v-else-if="node.type === 'm-input'">
        <t-input
          :placeholder="String(node.props.placeholder || '请输入')"
          :label="String(node.props.label || '')"
          :clearable="!!node.props.clearable"
        />
      </template>

      <!-- 移动端多行输入 -->
      <template v-else-if="node.type === 'm-textarea'">
        <t-textarea
          :placeholder="String(node.props.placeholder || '请输入内容')"
          :maxlength="Number(node.props.maxlength || 200)"
        />
      </template>

      <!-- 移动端开关 -->
      <template v-else-if="node.type === 'm-switch'">
        <div class="mwp-switch-row">
          <span>{{ node.props.label || '开关' }}</span>
          <t-switch :value="!!node.props.value" />
        </div>
      </template>

      <!-- 移动端步进器 -->
      <template v-else-if="node.type === 'm-stepper'">
        <t-stepper
          :value="Number(node.props.value || 0)"
          :min="Number(node.props.min || 0)"
          :max="Number(node.props.max || 10)"
          :step="Number(node.props.step || 1)"
        />
      </template>

      <!-- 移动端评分 -->
      <template v-else-if="node.type === 'm-rate'">
        <t-rate
          :value="Number(node.props.value || 0)"
          :max="Number(node.props.max || 5)"
          :allow-half="!!node.props.allowHalf"
        />
      </template>

      <!-- 移动端搜索框 -->
      <template v-else-if="node.type === 'm-search'">
        <t-search :placeholder="String(node.props.placeholder || '搜索关键词')" />
      </template>

      <!-- 移动端滑块 -->
      <template v-else-if="node.type === 'm-slider'">
        <t-slider
          :value="Number(node.props.value || 0)"
          :min="Number(node.props.min || 0)"
          :max="Number(node.props.max || 100)"
        />
      </template>

      <!-- 移动端单选组 -->
      <template v-else-if="node.type === 'm-radio'">
        <div class="mwp-options">
          <div
            v-for="(opt, i) in ((node.props.options as Array<Record<string, unknown>> | undefined) || [])"
            :key="i"
            class="mwp-option-row"
          >
            <span class="mwp-radio-dot" :class="{ 'mwp-checked': String(opt.value) === String(node.props.value) }"></span>
            <span>{{ opt.label }}</span>
          </div>
        </div>
      </template>

      <!-- 移动端多选组 -->
      <template v-else-if="node.type === 'm-checkbox'">
        <div class="mwp-options">
          <div
            v-for="(opt, i) in ((node.props.options as Array<Record<string, unknown>> | undefined) || [])"
            :key="i"
            class="mwp-option-row"
          >
            <span class="mwp-checkbox-box" :class="{ 'mwp-checked': (node.props.value as string[] | undefined)?.includes(String(opt.value)) }"></span>
            <span>{{ opt.label }}</span>
          </div>
        </div>
      </template>

      <!-- 移动端上传 -->
      <template v-else-if="node.type === 'm-upload'">
        <div class="mwp-upload-placeholder">
          <span class="mwp-upload-icon">+</span>
          <span>上传文件</span>
        </div>
      </template>

      <!-- 移动端单元格 -->
      <template v-else-if="node.type === 'm-cell'">
        <t-cell
          :title="String(node.props.title || '标题')"
          :description="String(node.props.description || '')"
          :arrow="!!node.props.arrow"
        />
      </template>

      <!-- 移动端标签 -->
      <template v-else-if="node.type === 'm-tag'">
        <t-tag :theme="(node.props.theme || 'primary') as any">{{ node.props.text || '标签' }}</t-tag>
      </template>

      <!-- 移动端徽标 -->
      <template v-else-if="node.type === 'm-badge'">
        <t-badge :dot="!!node.props.dot" :content="String(node.props.text || '')" />
      </template>

      <!-- 移动端公告栏 -->
      <template v-else-if="node.type === 'm-notice-bar'">
        <t-notice-bar :content="String(node.props.text || '公告信息')" />
      </template>

      <!-- 移动端进度条 -->
      <template v-else-if="node.type === 'm-progress'">
        <t-progress
          :percentage="Number(node.props.value || 0)"
          :max="Number(node.props.max || 100)"
        />
      </template>

      <!-- 移动端分割线 -->
      <template v-else-if="node.type === 'm-divider'">
        <t-divider>{{ node.props.text || '' }}</t-divider>
      </template>

      <!-- 移动端折叠面板 -->
      <template v-else-if="node.type === 'm-collapse'">
        <div class="mwp-collapse-body" :style="mobileContainerStyle">
          <div v-if="!node.children?.length" class="mwp-empty">
            拖入组件到此折叠面板
          </div>
          <MobileCanvasNode
            v-for="(child, i) in node.children"
            :key="child.id"
            :node="child"
            :parent-id="node.id"
            :index="i"
            :positioned="mobileChildrenPositioned"
            :selected="isNodeSelected(child.id)"
            @drag-start="emit('drag-start', $event)"
            @resize-start="emit('resize-start', $event)"
            @select-single="emit('select-single', $event)"
          />
        </div>
      </template>

      <!-- 移动端空状态 -->
      <template v-else-if="node.type === 'm-empty'">
        <t-empty :description="String(node.props.description || '暂无数据')" />
      </template>

      <!-- 移动端加载 -->
      <template v-else-if="node.type === 'm-loading'">
        <t-loading :text="String(node.props.text || '加载中...')" />
      </template>

      <!-- 移动端结果页 -->
      <template v-else-if="node.type === 'm-result'">
        <t-result
          :title="String(node.props.title || '操作成功')"
          :description="String(node.props.description || '')"
        />
      </template>

      <!-- 移动端轻提示（占位） -->
      <template v-else-if="node.type === 'm-toast'">
        <div class="mwp-toast-placeholder">轻提示：{{ node.props.text || '提示内容' }}</div>
      </template>

      <!-- 移动端对话框（占位） -->
      <template v-else-if="node.type === 'm-dialog'">
        <div class="mwp-dialog-placeholder">对话框：{{ node.props.title || '标题' }}</div>
      </template>

      <!-- 移动端动作面板（占位） -->
      <template v-else-if="node.type === 'm-action-sheet'">
        <div class="mwp-dialog-placeholder">动作面板</div>
      </template>

      <!-- 移动端弹出层 -->
      <template v-else-if="node.type === 'm-popup'">
        <div class="mwp-popup-body" :style="mobileContainerStyle">
          <div v-if="!node.children?.length" class="mwp-empty">
            拖入组件到此弹出层
          </div>
          <MobileCanvasNode
            v-for="(child, i) in node.children"
            :key="child.id"
            :node="child"
            :parent-id="node.id"
            :index="i"
            :positioned="mobileChildrenPositioned"
            :selected="isNodeSelected(child.id)"
            @drag-start="emit('drag-start', $event)"
            @resize-start="emit('resize-start', $event)"
            @select-single="emit('select-single', $event)"
          />
        </div>
      </template>

      <template v-else>
        <div class="mwp-unknown">{{ node.type }}</div>
      </template>
    </div>

    <div v-if="isSelected" class="mobile-selection-border"></div>

    <div v-if="isSelected" class="mobile-resize-handles">
      <div
        v-for="dir in RESIZE_DIRS"
        :key="dir"
        :class="'mobile-rh-' + dir"
        @mousedown="onResizeStart($event, dir)"
      ></div>
    </div>
  </div>
</template>

<style scoped>
.mobile-node {
  position: relative;
  max-width: 100%;
  box-sizing: border-box;
  cursor: move;
  user-select: none;
}
.mobile-node-positioned {
  position: absolute;
}
.mobile-node-selected {
  z-index: 5;
}
.mobile-selection-border {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 9999;
  box-sizing: border-box;
  border: 1px solid #1976d2;
  box-shadow: 0 0 0 2px rgba(25, 118, 210, 0.12);
}
.mobile-resize-handles > div {
  position: absolute;
  width: 8px;
  height: 8px;
  background: #fff;
  border: 1px solid #1976d2;
  z-index: 10000;
}
.mobile-rh-nw { top: -4px; left: -4px; cursor: nw-resize; }
.mobile-rh-n  { top: -4px; left: 50%; margin-left: -4px; cursor: n-resize; }
.mobile-rh-ne { top: -4px; right: -4px; cursor: ne-resize; }
.mobile-rh-e  { top: 50%; right: -4px; margin-top: -4px; cursor: e-resize; }
.mobile-rh-se { bottom: -4px; right: -4px; cursor: se-resize; }
.mobile-rh-s  { bottom: -4px; left: 50%; margin-left: -4px; cursor: s-resize; }
.mobile-rh-sw { bottom: -4px; left: -4px; cursor: sw-resize; }
.mobile-rh-w  { top: 50%; left: -4px; margin-top: -4px; cursor: w-resize; }

.mwp {
  width: 100%;
  min-height: 20px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  position: relative;
  overflow: hidden;
  font-size: 13px;
}
.mwp-empty {
  text-align: center;
  padding: 16px 8px;
  border: 1px dashed #c0c4cc;
  border-radius: 4px;
  background: #fafbfc;
  color: #909399;
  font-size: 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 4px 0;
}
.mwp-body { flex: 1; min-height: 120px; }
.mwp-m-scaffold {
  width: 100% !important;
  height: 100% !important;
  min-height: 100% !important;
  background: #fff;
}
.mwp-m-scaffold .mwp-body { flex: 1; min-height: 0; overflow-y: auto; }
.mwp-card-body {
  background: #fff;
  border-radius: 6px;
  padding: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  min-height: 48px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid #ebeef5;
}
.mwp-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  font-size: 13px;
}
.mwp-tab-header { display: flex; border-bottom: 1px solid #e0e0e0; }
.mwp-tab-body { flex: 1; min-height: 0; overflow: auto; }
.mwp-tab-item { flex: 1; text-align: center; padding: 6px 0; font-size: 12px; color: #546e7a; border-bottom: 2px solid transparent; }
.mwp-tab-active { border-bottom-color: #1976d2; color: #1976d2; }
.mwp-m-bottom-nav { min-height: 40px; }
.mwp-m-fab { align-items: flex-end; justify-content: flex-end; min-height: 40px; }
.mwp-m-avatar { align-items: center; justify-content: center; min-height: 40px; }
.mwp-unknown { padding: 4px; color: #f56c6c; font-size: 11px; text-align: center; }
.mwp-options { display: flex; flex-direction: column; gap: 4px; padding: 6px 10px; }
.mwp-option-row { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.mwp-radio-dot { width: 14px; height: 14px; border-radius: 50%; border: 2px solid #dcdfe6; box-sizing: border-box; flex-shrink: 0; }
.mwp-radio-dot.mwp-checked { border-color: #1976d2; background: radial-gradient(circle, #1976d2 40%, transparent 45%); }
.mwp-checkbox-box { width: 14px; height: 14px; border-radius: 3px; border: 2px solid #dcdfe6; box-sizing: border-box; flex-shrink: 0; }
.mwp-checkbox-box.mwp-checked { border-color: #1976d2; background: #1976d2; }
.mwp-upload-placeholder { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; padding: 10px; border: 1px dashed #dcdfe6; border-radius: 4px; color: #909399; font-size: 11px; }
.mwp-upload-icon { font-size: 18px; color: #c0c4cc; }
.mwp-collapse-body { background: #fff; border-radius: 6px; padding: 8px; min-height: 48px; display: flex; flex-direction: column; gap: 6px; border: 1px solid #ebeef5; }
.mwp-popup-body { background: #fff; border-radius: 6px; padding: 10px; min-height: 48px; display: flex; flex-direction: column; gap: 6px; border: 1px dashed #1976d2; }
.mwp-toast-placeholder { padding: 6px 12px; background: #f0f2f5; border-radius: 4px; font-size: 12px; color: #606266; text-align: center; }
.mwp-dialog-placeholder { padding: 8px 12px; background: #f0f7ff; border: 1px solid #1976d2; border-radius: 4px; font-size: 12px; color: #1976d2; text-align: center; }

/* 紧凑风格：覆盖 TDesign Mobile 组件默认尺寸 */
.mwp :deep(.t-button) { padding: 0 12px; font-size: 13px; height: 28px; line-height: 28px; }
.mwp :deep(.t-button--large) { height: 32px; line-height: 32px; }
.mwp :deep(.t-input) { padding: 4px 8px; font-size: 13px; }
.mwp :deep(.t-input__label) { font-size: 13px; min-width: 44px; }
.mwp :deep(.t-input__control) { font-size: 13px; }
.mwp :deep(.t-cell) { padding: 5px 10px; font-size: 13px; min-height: 36px; }
.mwp :deep(.t-cell__title) { font-size: 13px; }
.mwp :deep(.t-cell__description) { font-size: 11px; }
.mwp :deep(.t-navbar) { height: 38px; font-size: 14px; }
.mwp :deep(.t-navbar__title) { font-size: 14px; }
.mwp :deep(.t-tab-bar) { height: 38px; }
.mwp :deep(.t-tab-bar-item) { font-size: 11px; }
.mwp :deep(.t-tag) { font-size: 11px; padding: 0 6px; height: 18px; line-height: 18px; }
.mwp :deep(.t-avatar) { width: 32px !important; height: 32px !important; }
.mwp :deep(.t-avatar__image) { width: 32px !important; height: 32px !important; }
.mwp :deep(.t-empty) { padding: 12px 0; }
.mwp :deep(.t-empty__description) { font-size: 12px; }
.mwp :deep(.t-fab) { width: 36px; height: 36px; min-width: 36px; min-height: 36px; }
.mwp :deep(.t-fab__icon) { font-size: 16px; }
.mwp :deep(.t-image) { border-radius: 4px; }
.mwp :deep(.t-notice-bar) { height: 30px; font-size: 12px; }
.mwp :deep(.t-notice-bar__content) { font-size: 12px; }
.mwp :deep(.t-progress) { font-size: 12px; }
.mwp :deep(.t-progress__track) { height: 4px !important; }
.mwp :deep(.t-divider) { font-size: 12px; margin: 4px 0; }
.mwp :deep(.t-loading) { font-size: 12px; }
.mwp :deep(.t-loading__text) { font-size: 12px; }
.mwp :deep(.t-search) { padding: 2px 8px; }
.mwp :deep(.t-search__input) { font-size: 13px; height: 28px; }
.mwp :deep(.t-stepper) { font-size: 13px; }
.mwp :deep(.t-stepper__input) { font-size: 13px; width: 36px; }
.mwp :deep(.t-rate) { font-size: 14px; }
.mwp :deep(.t-rate__icon) { font-size: 14px; }
.mwp :deep(.t-slider) { margin: 2px 8px; }
.mwp :deep(.t-badge) { font-size: 10px; }
.mwp :deep(.t-textarea) { padding: 4px 8px; font-size: 13px; }
.mwp :deep(.t-textarea__inner) { font-size: 13px; }
.mwp :deep(.t-result) { padding: 12px 0; }
.mwp :deep(.t-result__title) { font-size: 14px; }
.mwp :deep(.t-result__description) { font-size: 12px; }
.mwp :deep(.t-popup__content) { padding: 8px; font-size: 13px; }
</style>