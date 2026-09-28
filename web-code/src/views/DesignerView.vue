<script setup lang="ts">
import {
  ref,
  computed,
  onMounted,
  onBeforeUnmount,
  onActivated,
  onDeactivated,
  watch,
} from 'vue'
import type { ComponentSchema } from '@/types/schema'
import DxButton from 'devextreme-vue/button'

import MaterialPanel from '@/components/designer/MaterialPanel.vue'
import CanvasPanel from '@/components/designer/CanvasPanel.vue'
import PropertyPanel from '@/components/designer/PropertyPanel.vue'
import {
  designerStore,
  undo,
  redo,
  canUndo,
  canRedo,
  exportSchema,
  importSchema,
  clearCanvas,
  getSelectionIds,
  removeNodes,
  copyNodesToClipboard,
  cutNodesToClipboard,
  pasteClipboard,
  duplicateNodes,
  collectAllNodes,
  setSelection,
  genId,
  nextFieldName,
  updateNodePosition,
  findNode,
  pushHistory,
  setDesignerMode,
  setBizMode,
  addNode,
  validateFormBeforeSave,
} from '@/store/designer'
import { getConfig } from '@/materials'
import { savePage, loadLatestPage, loadPage } from '@/store/persistence'
import type { PageSchema, PageType } from '@/types/schema'
import { useRoute } from 'vue-router'
import { t, loadAllTranslations } from '@/store/lang'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()

const importInput = ref<HTMLInputElement>()

/** 面板显示/隐藏 */
const showMaterialPanel = ref(true)
const showPropertyPanel = ref(true)

/** 业务形态子模式选项：表单页面 / 表格页面 / 移动端页面 */
const bizModeOptions: { label: string; value: 'form' | 'table' | 'mobile'; icon: string }[] = [
  { label: '表单页面', value: 'form', icon: 'form' },
  { label: '表格页面', value: 'table', icon: 'table' },
  { label: '移动端页面', value: 'mobile', icon: 'mobile' },
]

/** 当前是否为表格模式（表格模式无组件库，画布只有一个固定数据表格） */
const isTableMode = computed(
  () => designerStore.mode === 'biz' && designerStore.bizMode === 'table',
)

/** 切到表格模式且画布为空时，自动生成数据表格骨架（远程数据源、宽高100%） */
watch(
  () => designerStore.bizMode,
  (newMode) => {
    if (
      designerStore.mode === 'biz' &&
      newMode === 'table' &&
      designerStore.schema.length === 0
    ) {
      addNode('dx-data-grid', null, 0, true, { dataSourceType: 'remote' })
      const node = designerStore.schema[0]
      if (node) {
        node.css = { width: '100%', height: '100%', padding: '0', margin: '0' }
        node.heightAuto = true
        node.x = 0
        node.y = 0
      }
    }
  },
)

/** 方向键移动的历史记录防抖：长按方向键时只在开始移动时记录一次历史 */
let lastMoveHistoryTime = 0

/** 判断焦点是否处于可编辑控件中（仅属性面板、工具栏等输入区域，不包含画布内的 DevExtreme 组件） */
function isEditing(): boolean {
  const el = document.activeElement as HTMLElement | null
  if (!el) return false
  // 仅当焦点在属性面板、头部工具栏等编辑区域时才阻止快捷键
  if (el.closest('.prop-panel') || el.closest('.designer-header')) {
    return (
      el.tagName === 'INPUT' ||
      el.tagName === 'TEXTAREA' ||
      el.isContentEditable
    )
  }
  return false
}

function handleKeydown(e: KeyboardEvent): void {
  const ctrlOrMeta = e.ctrlKey || e.metaKey

  // 撤销/重做：弹窗或可编辑控件中不拦截，避免误撤销画布内容
  if (ctrlOrMeta && e.key.toLowerCase() === 'z') {
    const el = document.activeElement as HTMLElement | null
    const inEditable =
      el &&
      (el.tagName === 'INPUT' ||
        el.tagName === 'TEXTAREA' ||
        el.tagName === 'SELECT' ||
        el.isContentEditable ||
        el.closest('.dialog-overlay'))
    if (inEditable) return
    e.preventDefault()
    if (e.shiftKey) redo()
    else undo()
    return
  }

  // 保存：任何时候都响应
  if (ctrlOrMeta && e.key.toLowerCase() === 's') {
    e.preventDefault()
    onSave()
    return
  }

  // 以下编辑类快捷键：输入框中不拦截（保留原生复制粘贴等）
  if (isEditing()) return

  // 复制节点：Ctrl+D 原地复制选中节点
  if (ctrlOrMeta && e.key.toLowerCase() === 'd') {
    e.preventDefault()
    duplicateNodes(getSelectionIds())
    return
  }

  // 全选：Ctrl+A 选中画布上所有节点
  if (ctrlOrMeta && e.key.toLowerCase() === 'a') {
    e.preventDefault()
    const allNodes = collectAllNodes(designerStore.schema)
    if (allNodes.length) {
      setSelection(allNodes.map((n) => n.id))
    }
    return
  }

  const selectionIds = getSelectionIds()
  if (!selectionIds.length) return

  // 方向键移动选中的定位节点（1px/步，Shift 时 10px/步）
  if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(e.key)) {
    const step = e.shiftKey ? 10 : 1
    // 移动自由定位节点（画布根级 + 自由定位容器内的子节点）
    const movableIds = selectionIds.filter((id) => {
      const found = findNode(designerStore.schema, id)
      if (!found) return false
      // 根级节点可移动
      if (!found.parent) return true
      // 自由定位容器内的子节点可移动
      const FREE_POSITION_CONTAINERS = new Set([
        'el-splitter-panel',
        'el-tab-pane',
      ])
      return FREE_POSITION_CONTAINERS.has(found.parent.type)
    })
    if (movableIds.length) {
      // 获取画布基础尺寸（不含控件扩展），用于边界检查
      const canvasScrollEl = document.querySelector(
        '.canvas-scroll'
      ) as HTMLElement | null
      const canvasW = canvasScrollEl
        ? Math.max(900, canvasScrollEl.clientWidth - 20)
        : 1200
      const canvasH = canvasScrollEl
        ? Math.max(700, canvasScrollEl.clientHeight - 20)
        : 800

      const excludeIds = new Set(movableIds)
      let moved = false
      let boundaryHit = false

      for (const id of movableIds) {
        const found = findNode(designerStore.schema, id)!
        const node = found.node
        const x = node.x ?? 0
        const y = node.y ?? 0
        const w = node.width ?? 240
        const h = node.height ?? 120

        // 计算目标位置
        let newX = x
        let newY = y
        if (e.key === 'ArrowLeft') newX = x - step
        else if (e.key === 'ArrowRight') newX = x + step
        else if (e.key === 'ArrowUp') newY = y - step
        else newY = y + step

        // 根级节点：检查是否超出画布边界（宽度固定，高度允许向下扩展）
        if (!found.parent) {
          if (newX < 0 || newX + w > canvasW || newY < 0) {
            boundaryHit = true
            continue
          }
        }

        // 碰撞检测：检查与同级兄弟节点的重叠
        const siblings = found.parent
          ? found.parent.children
          : designerStore.schema
        let collides = false
        for (const other of siblings) {
          if (excludeIds.has(other.id)) continue
          const ox = other.x ?? 0
          const oy = other.y ?? 0
          const ow = other.width ?? 240
          const oh = other.height ?? 120
          if (
            newX < ox + ow &&
            newX + w > ox &&
            newY < oy + oh &&
            newY + h > oy
          ) {
            collides = true
            break
          }
        }
        if (!collides) {
          updateNodePosition(id, newX, newY)
          moved = true
        }
      }
      if (moved) {
        // 长按方向键时只在开始移动时记录一次历史（500ms 防抖）
        const now = Date.now()
        if (now - lastMoveHistoryTime > 500) pushHistory()
        lastMoveHistoryTime = now
        e.preventDefault()
      }
      if (boundaryHit) {
        designerStore.boundaryWarningTick += 1
      }
    }
    return
  }

  if (e.key === 'Delete' || e.key === 'Backspace') {
    e.preventDefault()
    removeNodes(selectionIds)
  } else if (ctrlOrMeta && e.key.toLowerCase() === 'c') {
    e.preventDefault()
    copyNodesToClipboard(selectionIds)
  } else if (ctrlOrMeta && e.key.toLowerCase() === 'x') {
    e.preventDefault()
    cutNodesToClipboard(selectionIds)
  } else if (ctrlOrMeta && e.key.toLowerCase() === 'v') {
    e.preventDefault()
    pasteClipboard()
  }
}

/** 保存为页面元数据（持久化到后端） */
async function onSave(): Promise<void> {
  // 表单模式保存前校验：检查表名和数据库字段
  const validation = validateFormBeforeSave()
  if (!validation.ok) {
    const errorHtml = validation.errors
      .map((e) => `<li style="padding:4px 0;color:#f56c6c;">${e}</li>`)
      .join('')
    ElMessageBox.alert(
      `<div style="font-size:13px;line-height:1.6;"><ul style="margin:0;padding-left:20px;">${errorHtml}</ul></div>`,
      t('保存失败，请修正以下问题'),
      { dangerouslyUseHTMLString: true, type: 'error', confirmButtonText: t('知道了') },
    )
    return
  }
  const schema = exportSchema()
  let meta
  try {
    meta = await savePage(schema)
  } catch (e) {
    ElMessage.error(t('保存失败：') + (e instanceof Error ? e.message : String(e)))
    return
  }
  const time = new Date(meta.savedAt)
  const timeText = `${time.getHours().toString().padStart(2, '0')}:${time
    .getMinutes()
    .toString()
    .padStart(2, '0')}`
  // 建表同步结果提示
  if (meta.tableSyncMsg) {
    const isWarning = meta.tableSyncMsg.includes('跳过') || meta.tableSyncMsg.includes('失败') || meta.tableSyncMsg.includes('不合法')
    if (isWarning) {
      ElMessageBox.alert(meta.tableSyncMsg, t('建表同步提示'), {
        type: 'warning',
        confirmButtonText: t('知道了'),
      })
    } else {
      ElMessage.success(`${t('已保存，')}${meta.tableSyncMsg}`)
    }
  } else {
    ElMessage.success(
      `${t('已保存：')}${meta.pageName}（${meta.pageId}）${t('，保存时间：')}${timeText}`,
    )
  }
}

/** 导出 JSON：下载文件 */
function onExport(): void {
  const schema = exportSchema()
  const blob = new Blob([JSON.stringify(schema, null, 2)], {
    type: 'application/json',
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${schema.pageName || t('页面')}.schema.json`
  a.click()
  URL.revokeObjectURL(url)
}

/** 导入 JSON：读取本地文件 */
function onImport(e: Event): void {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    try {
      const page = JSON.parse(String(reader.result)) as PageSchema
      if (!importSchema(page)) {
        ElMessage.error(t('导入失败：文件不符合页面 Schema 格式'))
      }
    } catch {
      ElMessage.error(t('导入失败：JSON 解析错误'))
    }
  }
  reader.readAsText(file)
  input.value = ''
}

function onClear(): void {
  if (!designerStore.schema.length) return
  ElMessageBox.confirm(t('确定清空画布吗？可通过撤销恢复。'), t('提示'), {
    confirmButtonText: t('确定'),
    cancelButtonText: t('取消'),
    type: 'warning',
  })
    .then(() => clearCanvas())
    .catch(() => {})
}

function onPreview(): void {
  // 将当前 schema 存入 sessionStorage，供预览页面读取（无需先保存）
  const previewData = {
    schema: designerStore.schema,
    pageName: designerStore.pageName,
    tableName: designerStore.tableName,
    pageCss: designerStore.pageCss,
    pageId: designerStore.pageId,
  }
  sessionStorage.setItem('lowcode-preview-data', JSON.stringify(previewData))
  const url = `${window.location.origin}${window.location.pathname}#/preview`
  window.open(url, '_blank')
}

// ---------- 查看 Schema / 源码 ----------

const showSchema = ref(false)
const showVueCode = ref(false)

/** Schema 编辑器文本（打开时初始化，允许用户修改） */
const schemaText = ref('')
const schemaError = ref('')

const schemaJson = computed(() => {
  const schema = exportSchema()
  return JSON.stringify(schema, null, 2)
})

/** 打开 Schema 对话框时初始化可编辑文本 */
function openSchemaDialog(): void {
  schemaText.value = schemaJson.value
  schemaError.value = ''
  showSchema.value = true
}

/** 应用用户编辑的 Schema */
function applySchema(): void {
  try {
    const page = JSON.parse(schemaText.value) as PageSchema
    if (!importSchema(page)) {
      schemaError.value = t('Schema 格式不正确，无法导入')
      return
    }
    schemaError.value = ''
    showSchema.value = false
  } catch (err) {
    schemaError.value =
      t('JSON 解析错误：') +
      `${err instanceof Error ? err.message : String(err)}`
  }
}

// ---------- 源码编辑器（可编辑 + 保存反向解析）----------

/** 源码编辑器文本（打开时初始化，允许用户修改） */
const vueCodeText = ref('')
const vueCodeError = ref('')

/** 打开源码对话框时初始化 */
function openVueCodeDialog(): void {
  vueCodeText.value = vueCode.value
  vueCodeError.value = ''
  showVueCode.value = true
}

/** 自定义组件 PascalCase 名 → type 映射 */
const CUSTOM_COMPONENT_MAP: Record<string, string> = {
  ImageView: 'image',
  LinkView: 'link',
  IframeView: 'iframe',
}

/** 组件名 → 物料 type 的反向映射 */
function nameToType(name: string): string | null {
  // el-* 保持原名
  if (name.startsWith('el-')) return name
  // dx-* 保持原名
  if (name.startsWith('dx-')) return name
  // 自定义组件：ImageView → image, LinkView → link, IframeView → iframe
  if (CUSTOM_COMPONENT_MAP[name]) return CUSTOM_COMPONENT_MAP[name]
  return null
}

/** 解析 Vue 属性值（支持字符串 / 数字 / 布尔 / JSON 对象） */
function parseVueAttrValue(raw: string, isBinding: boolean): unknown {
  const trimmed = raw.trim()
  if (!isBinding) return trimmed
  // 尝试 JSON.parse（处理数字 / 布尔 / 对象 / 数组 / null / 字符串）
  try {
    // 包装为合法的 JSON 值（Vue 绑定的 `123` → 数字, `true` → 布尔, `{a:1}` → 对象）
    const wrapped =
      trimmed.startsWith('{') || trimmed.startsWith('[')
        ? trimmed
        : JSON.stringify(trimmed)
    // 对于纯布尔/数字，直接解析
    if (trimmed === 'true') return true
    if (trimmed === 'false') return false
    if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed)
    if (wrapped !== trimmed) {
      // 原始是 JSON 对象/数组
      return JSON.parse(trimmed)
    }
    return JSON.parse(wrapped)
  } catch {
    // 解析失败时保留原字符串
    return trimmed
  }
}

/** 从 HTML 元素的属性收集 props */
function collectPropsFromElement(el: Element): Record<string, unknown> {
  const props: Record<string, unknown> = {}
  const attrs = el.attributes
  for (let i = 0; i < attrs.length; i += 1) {
    const attr = attrs[i]
    // 跳过 class / style / data-section / data-* 等非 prop 属性
    if (
      attr.name === 'class' ||
      attr.name === 'style' ||
      attr.name.startsWith('data-')
    )
      continue
    if (attr.name.startsWith(':')) {
      // 绑定属性 :prop="value"
      const prop = attr.name.slice(1)
      props[prop] = parseVueAttrValue(attr.value, true)
    } else {
      // 静态属性
      props[attr.name] = parseVueAttrValue(attr.value, false)
    }
  }
  return props
}

/** 递归将 HTML 元素节点转换为 ComponentSchema 树 */
function domToSchema(el: Element, parentType?: string): ComponentSchema | null {
  const type = nameToType(el.tagName.toLowerCase())
  if (!type) return null

  const props = collectPropsFromElement(el)
  // 识别 sectionKey（container-content 上的 data-section 属性）
  const sectionKey = el.getAttribute('data-section') || undefined

  // 跳过纯容器的 data-section 节点（如 [data-section="body"] > .container-content 的 div）
  // 实际节点是有类型的元素
  const cfg = getConfig(type)
  if (!cfg) return null

  const node: ComponentSchema = {
    id: genId(),
    type,
    props,
    children: [],
    fieldName: nextFieldName(type),
    x: 0,
    y: 0,
    width: 240,
    height:
      cfg.defaultProps && cfg.defaultProps.height
        ? Number(cfg.defaultProps.height) || 120
        : 120,
  }
  if (sectionKey) node.sectionKey = sectionKey

  // 处理子节点
  const childElements = Array.from(el.children)
  for (const childEl of childElements) {
    // 跳过纯容器元素（如 <div class="container-content"> 包装 div）
    if (childEl.classList && childEl.classList.contains('container-content')) {
      // container-content 内部可能有真正的组件节点
      for (const inner of Array.from(childEl.children)) {
        const childNode = domToSchema(inner, type)
        if (childNode) node.children.push(childNode)
      }
    } else if (
      childEl.tagName.toLowerCase() === 'div' &&
      !nameToType(childEl.tagName.toLowerCase())
    ) {
      // 普通 div 容器：继续向内部查找
      for (const inner of Array.from(childEl.children)) {
        const childNode = domToSchema(inner, type)
        if (childNode) node.children.push(childNode)
      }
    } else {
      const childNode = domToSchema(childEl, type)
      if (childNode) node.children.push(childNode)
    }
  }

  // 容器节点：确保非空 children 在没有子项时仍保留（用于容器标记）
  void parentType
  return node
}

/** 从 Vue SFC 字符串解析出 template 部分 */
function extractTemplate(sfc: string): string | null {
  // 匹配 <template>...</template>
  const match = sfc.match(/<template[^>]*>([\s\S]*)<\/template>/i)
  if (!match) return null
  return match[1]
}

/** 反向解析 Vue 源码为 Schema 树并应用（支持撤销重做） */
function applyVueCode(): void {
  try {
    const sfc = vueCodeText.value
    const templateStr = extractTemplate(sfc)
    if (!templateStr) {
      vueCodeError.value = t('未找到 <template> 标签')
      return
    }

    // 用 DOMParser 解析模板
    const parser = new DOMParser()
    const wrapped = `<div id="__root">${templateStr}</div>`
    const doc = parser.parseFromString(wrapped, 'text/html')
    const root = doc.getElementById('__root')
    if (!root) {
      vueCodeError.value = t('模板解析失败')
      return
    }

    // 收集根级节点
    const rootChildren: ComponentSchema[] = []
    for (const child of Array.from(root.children)) {
      // 跳过根包装 div（如 <div class="page">）
      if (
        child.tagName.toLowerCase() === 'div' &&
        child.classList.contains('page')
      ) {
        for (const inner of Array.from(child.children)) {
          const node = domToSchema(inner)
          if (node) rootChildren.push(node)
        }
      } else {
        const node = domToSchema(child)
        if (node) rootChildren.push(node)
      }
    }

    if (!rootChildren.length) {
      vueCodeError.value = t('未解析到任何组件节点')
      return
    }

    // 生成新的 PageSchema 并应用（importSchema 内含 pushHistory，支持撤销）
    const page: PageSchema = {
      version: '1.0.0',
      pageId: `page_${Date.now().toString(36)}`,
      pageName: t('从源码导入'),
      root: rootChildren,
    }
    if (!importSchema(page)) {
      vueCodeError.value = t('导入失败，生成的 Schema 不符合格式')
      return
    }
    vueCodeError.value = ''
    showVueCode.value = false
  } catch (err) {
    vueCodeError.value =
      t('解析错误：') + `${err instanceof Error ? err.message : String(err)}`
  }
}

/** 将 Schema 树转换为 Vue SFC 源码字符串 */
const vueCode = computed(() => {
  const schema = exportSchema()
  const INDENT = '  '

  function componentName(type: string): string {
    // el-* / dx-* 保持原名
    if (type.startsWith('el-') || type.startsWith('dx-')) return type
    // 自定义控件转 PascalCase: image → ImageView, link → LinkView, iframe → IframeView
    return (
      type.replace(/(?:^|-)(.)/g, (_, c: string) => c.toUpperCase()) + 'View'
    )
  }

  function formatPropValue(value: unknown): string | null {
    if (value === undefined || value === null || value === '') return null
    if (typeof value === 'string') return `"${value.replace(/"/g, '\\"')}"`
    // 数字 / 布尔 / 对象：需要 v-bind 绑定，统一返回 `:value` 形式供后续拼接为 `:prop="value"`
    if (typeof value === 'number' || typeof value === 'boolean')
      return `:${JSON.stringify(value)}`
    try {
      return `:${JSON.stringify(value).replace(/"([^"]+)":/g, '$1:')}`
    } catch {
      return null
    }
  }

  // 收集远程数据源定义，在 script 中生成 CustomStore
  const remoteStores: {
    varName: string
    url: string
    method: string
    key: string
    body: string
    firstLoad: boolean
  }[] = []
  let remoteStoreIdx = 0

  function nodeToVue(node: ComponentSchema, depth: number): string {
    const indent = INDENT.repeat(depth)
    const comp = componentName(node.type)
    const props: string[] = []

    // dx-data-grid 远程数据源：dataSourceType === 'remote' 时生成 CustomStore
    const hasRemote =
      node.type === 'dx-data-grid' && node.props.dataSourceType === 'remote'

    for (const [key, value] of Object.entries(node.props)) {
      // CSS 已设置 width/height 时，跳过 props 中的 width/height（统一由尺寸 section 控制）
      if (key === 'width' && node.css?.width) continue
      if (key === 'height' && node.css?.height) continue
      // 数据源类型、首次加载、远程配置属性不直接生成到模板
      if (
        key === 'dataSourceType' ||
        key === 'firstLoad' ||
        key === 'remoteUrl' ||
        key === 'remoteMethod' ||
        key === 'remoteBody'
      )
        continue
      // 远程模式时跳过静态 dataSource
      if (hasRemote && key === 'dataSource') continue
      // dx-data-grid 列配置：清理自定义属性（editorType, dropDownGrid），保留 lookup
      if (
        key === 'columns' &&
        node.type === 'dx-data-grid' &&
        Array.isArray(value)
      ) {
        const cleanedCols = (value as Record<string, unknown>[]).map((col) => {
          const { editorType: _et, dropDownGrid: _ddg, ...cleanCol } = col
          void _et
          void _ddg
          return cleanCol
        })
        const formatted = formatPropValue(cleanedCols)
        if (formatted) {
          props.push(`:${key}="${formatted.slice(1).replace(/"/g, '&quot;')}"`)
        }
        continue
      }
      const formatted = formatPropValue(value)
      if (formatted) {
        if (formatted.startsWith(':')) {
          props.push(`:${key}="${formatted.slice(1).replace(/"/g, '&quot;')}"`)
        } else {
          props.push(`${key}=${formatted}`)
        }
      }
    }

    // 如果有远程数据源，添加 :data-source 绑定和 remote-operations
    if (hasRemote) {
      const varName = `gridDataSource_${++remoteStoreIdx}`
      remoteStores.push({
        varName,
        url: String(node.props.remoteUrl),
        method: String(node.props.remoteMethod || 'GET'),
        key: String(node.props.keyExpr || 'id'),
        body: String(node.props.remoteBody || '{}'),
        firstLoad: node.props.firstLoad === true,
      })
      props.push(`:data-source="${varName}"`)
      if (!('remoteOperations' in node.props)) {
        props.push(':remote-operations="true"')
      }
    }

    const propsStr = props.length ? ' ' + props.join(' ') : ''

    if (!node.children || node.children.length === 0) {
      return `${indent}<${comp}${propsStr} />`
    }

    const children = node.children
      .map((child) => nodeToVue(child, depth + 1))
      .join('\n')
    return `${indent}<${comp}${propsStr}>\n${children}\n${indent}</${comp}>`
  }

  const nodes = schema.root.map((node) => nodeToVue(node, 2)).join('\n')

  // 收集用到的组件类型
  const usedTypes = new Set<string>()
  function collectTypes(node: ComponentSchema): void {
    usedTypes.add(node.type)
    node.children.forEach(collectTypes)
  }
  schema.root.forEach(collectTypes)

  const imports: string[] = []
  for (const type of usedTypes) {
    const name = componentName(type)
    if (name.startsWith('dx-')) {
      const dxPkg = type.replace(/^dx-/, 'devextreme-vue/')
      imports.push(`import ${name} from '${dxPkg}'`)
    } else if (name.startsWith('el-')) {
      // el-* 组件对应我们自己的布局组件，从 materials/components 导入
      imports.push(`import ${name} from '@/materials/components/${name}.vue'`)
    } else {
      // 自定义组件从 materials/components 导入
      imports.push(`import ${name} from '@/materials/components/${name}.vue'`)
    }
  }

  // 远程数据源：生成 CustomStore 导入和变量
  if (remoteStores.length) {
    imports.push(`import CustomStore from 'devextreme/data/custom_store'`)
  }
  const storeDefs = remoteStores
    .map((s) => {
      return `function mapOperator(op) {
  switch (op) {
    case 'contains': return 'like'
    case 'notcontains': return 'not like'
    case 'startswith': return 'like'
    case 'endswith': return 'like'
    default: return op
  }
}
function getColumnTableAlias(_column) {
  return 'T'
}
function convertFilter(dxFilter) {
  const result = []
  if (!Array.isArray(dxFilter)) return result
  const hasLogic = dxFilter.some((item) => typeof item === 'string' && (item === 'and' || item === 'or' || item === '!'))
  if (!hasLogic && dxFilter.length >= 2 && typeof dxFilter[0] === 'string') {
    const field = dxFilter[0]
    const op = dxFilter.length >= 3 ? String(dxFilter[1]) : '='
    const val = dxFilter.length >= 3 ? dxFilter[2] : dxFilter[1]
    if (val !== '' && val !== null && val !== undefined) {
      result.push({ Symbol: 'and' })
      result.push({ Id: field, Symbol: mapOperator(op), Val: val, TableAlias: getColumnTableAlias(field) })
    }
  } else {
    for (let i = 0; i < dxFilter.length; i++) {
      const item = dxFilter[i]
      if (Array.isArray(item)) {
        const inner = convertFilter(item)
        if (inner.length > 0) {
          let logic = 'and'
          if (i > 0 && typeof dxFilter[i - 1] === 'string' && (dxFilter[i - 1] === 'and' || dxFilter[i - 1] === 'or')) {
            logic = dxFilter[i - 1]
          }
          result.push({ Symbol: logic })
          for (let j = 1; j < inner.length; j++) {
            result.push(inner[j])
          }
        }
      }
    }
  }
  return result
}
function buildLoadParams(loadOptions) {
  const params = {}
  if (loadOptions.skip !== undefined) params.skip = loadOptions.skip
  if (loadOptions.take !== undefined) params.take = loadOptions.take
  if (loadOptions.sort && Array.isArray(loadOptions.sort) && loadOptions.sort.length > 0) {
    const sortItem = { ...loadOptions.sort[0] }
    if (sortItem.selector) sortItem.TableAlias = getColumnTableAlias(String(sortItem.selector))
    params.sort = JSON.stringify(sortItem)
  }
  if (loadOptions.filter) {
    const filterData = convertFilter(loadOptions.filter)
    if (filterData.length > 0) params.filterData = JSON.stringify(filterData)
  }
  if (loadOptions.searchValue !== undefined && loadOptions.searchValue !== '') params.searchValue = loadOptions.searchValue
  if (loadOptions.totalSummary && Array.isArray(loadOptions.totalSummary) && loadOptions.totalSummary.length > 0) {
    params.totalSummary = JSON.stringify(loadOptions.totalSummary.map((item) => ({ selector: item.selector })))
  }
  return params
}
${
  s.firstLoad
    ? `let _firstLoadDone_${s.varName} = false
`
    : ''
}const ${s.varName} = new CustomStore({
  key: '${s.key}',
  load: async (loadOptions) => {
    ${
      s.firstLoad
        ? `if (!_firstLoadDone_${s.varName}) { _firstLoadDone_${s.varName} = true; return { data: [], totalCount: 0 } }`
        : ''
    }
    const params = buildLoadParams(loadOptions)
    let resp
    if ('${s.method}' === 'POST') {
      let userBody = {}
      try { userBody = JSON.parse(\`${s.body.replace(/`/g, '\\`')}\`) } catch {}
      const formData = new FormData()
      const allParams = { ...userBody, ...params }
      for (const [k, v] of Object.entries(allParams)) {
        if (v !== undefined && v !== null) formData.append(k, typeof v === 'object' ? JSON.stringify(v) : String(v))
      }
      resp = await fetch('${s.url}', {
        method: 'POST',
        headers: { 'tenant': 'WMS_TEST', 'usertype': '5' },
        body: formData,
      })
    } else {
      const qs = Object.entries(params)
        .filter(([, v]) => v !== undefined && v !== null)
        .map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(typeof v === 'object' ? JSON.stringify(v) : String(v)))
        .join('&')
      resp = await fetch('${s.url}' + (qs ? ('${
        s.url
      }'.includes('?') ? '&' : '?') + qs : ''))
    }
    const data = await resp.json()
    const extract = (raw) => {
      if (Array.isArray(raw)) return { data: raw, totalCount: raw.length }
      if (raw && typeof raw === 'object') {
        const o = raw
        if (o.data && typeof o.data === 'object' && !Array.isArray(o.data)) {
          const i = o.data
          const tc = Number(i.totalCount ?? i.total ?? i.count ?? 0)
          if (Array.isArray(i.data)) return { data: i.data, totalCount: tc || i.data.length }
          if (Array.isArray(i.rows)) return { data: i.rows, totalCount: tc || i.rows.length }
          if (Array.isArray(i.list)) return { data: i.list, totalCount: tc || i.list.length }
          if (Array.isArray(i.items)) return { data: i.items, totalCount: tc || i.items.length }
        }
        const tc = Number(o.totalCount ?? o.total ?? o.count ?? 0)
        if (Array.isArray(o.data)) return { data: o.data, totalCount: tc || o.data.length }
        if (Array.isArray(o.items)) return { data: o.items, totalCount: tc || o.items.length }
        if (Array.isArray(o.rows)) return { data: o.rows, totalCount: tc || o.rows.length }
        if (Array.isArray(o.list)) return { data: o.list, totalCount: tc || o.list.length }
        if (Array.isArray(o.results)) return { data: o.results, totalCount: tc || o.results.length }
      }
      return { data: [], totalCount: 0 }
    }
    const p = extract(data)
    return { data: p.data, totalCount: p.totalCount }
  },
})`
    })
    .join('\n')

  const tpl = [
    '<template>',
    '  <div class="page">',
    nodes,
    '  </div>',
    '</template>',
    '',
    '<script setup lang="ts">',
    imports.join('\n'),
    storeDefs,
    '</' + 'script>',
    '',
    '<style scoped>',
    '.page {',
    '  width: 100%;',
    '  min-height: 100vh;',
    '  padding: 16px;',
    '  box-sizing: border-box;',
    '}',
    '</style>',
  ].join('\n')
  return tpl
})

function closeDialogs(): void {
  showSchema.value = false
  showVueCode.value = false
}

/** 加载指定 pageId 的页面到设计器 */
async function loadPageById(pageId: string): Promise<boolean> {
  const page = await loadPage(pageId)
  if (page) {
    importSchema(page)
    return true
  }
  return false
}

/** 启动时自动恢复页面：优先从 URL query 加载指定 pageId，否则恢复最近保存 */
async function restoreLastPage(): Promise<void> {
  const queryMode = route.query.mode as string | undefined
  const queryPageType = route.query.pageType as string | undefined
  setDesignerMode(queryMode === 'biz' ? 'biz' : 'default')
  if (queryPageType) {
    designerStore.pageType = queryPageType as PageType
  }
  const queryPageId = route.query.pageId as string | undefined
  if (queryPageId) {
    if (await loadPageById(queryPageId)) return
  }
  if (designerStore.schema.length) return
  const page = await loadLatestPage()
  if (page) importSchema(page)
}

onMounted(async () => {
  window.addEventListener('keydown', handleKeydown)
  await loadAllTranslations()
  restoreLastPage()
  if (import.meta.env.DEV) {
    ;(window as unknown as Record<string, unknown>).__designerStore =
      designerStore
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})

// Tab 页签场景（keep-alive）：停用时卸载全局键盘监听，激活时重新挂载，
// 避免切到其他 Tab 后 Delete/Ctrl 组合键仍操作设计器
onActivated(() => {
  window.addEventListener('keydown', handleKeydown)
})

onDeactivated(() => {
  window.removeEventListener('keydown', handleKeydown)
})

// 监听 URL pageId 变化，设计器已挂载时切换页面也能加载
watch(
  () => route.query.pageId,
  (newPageId) => {
    if (newPageId && typeof newPageId === 'string') {
      loadPageById(newPageId)
    }
  }
)
</script>

<template>
  <div class="designer">
    <header class="designer-header">
      <div class="header-left">
        <span class="logo">{{ t('低代码设计器') }}</span>
        <input
          v-model="designerStore.pageName"
          class="page-name-input"
          :placeholder="t('页面名称')"
        />
      </div>
      <div v-if="designerStore.mode === 'biz'" class="header-biz-mode">
        <button
          v-for="m in bizModeOptions"
          :key="m.value"
          class="biz-mode-btn"
          :class="{ 'biz-mode-active': designerStore.bizMode === m.value }"
          @click="setBizMode(m.value)"
        >
          <svg class="biz-mode-icon" viewBox="0 0 16 16" width="14" height="14">
            <!-- 表单图标：带横线的文档 -->
            <template v-if="m.icon === 'form'">
              <rect x="3" y="2" width="10" height="12" rx="1" fill="none" stroke="currentColor" stroke-width="1.2" />
              <line x1="5" y1="5" x2="11" y2="5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
              <line x1="5" y1="8" x2="11" y2="8" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
              <line x1="5" y1="11" x2="9" y2="11" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
            </template>
            <!-- 表格图标：网格 -->
            <template v-else-if="m.icon === 'table'">
              <rect x="2.5" y="3" width="11" height="10" rx="1" fill="none" stroke="currentColor" stroke-width="1.2" />
              <line x1="2.5" y1="6" x2="13.5" y2="6" stroke="currentColor" stroke-width="1.2" />
              <line x1="6" y1="3" x2="6" y2="13" stroke="currentColor" stroke-width="1.2" />
              <line x1="9.5" y1="3" x2="9.5" y2="13" stroke="currentColor" stroke-width="1.2" />
            </template>
            <!-- 移动端图标：手机轮廓 -->
            <template v-else-if="m.icon === 'mobile'">
              <rect x="5" y="1.5" width="6" height="13" rx="1.2" fill="none" stroke="currentColor" stroke-width="1.2" />
              <line x1="7" y1="12.5" x2="9" y2="12.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
            </template>
          </svg>
          <span class="biz-mode-text">{{ t(m.label) }}</span>
        </button>
      </div>
      <div v-else class="header-biz-mode-placeholder"></div>
      <div class="header-tools">
        <DxButton :text="t('保存')" icon="save" type="normal" @click="onSave" />
        <DxButton
          :text="t('导入')"
          icon="upload"
          styling-mode="outlined"
          @click="importInput?.click()"
        />
        <input
          ref="importInput"
          type="file"
          accept=".json,application/json"
          style="display: none"
          @change="onImport"
        />
        <DxButton
          :text="t('导出')"
          icon="download"
          styling-mode="outlined"
          @click="onExport"
        />
        <DxButton
          :text="t('预览')"
          icon="export"
          type="default"
          @click="onPreview"
        />
      </div>
    </header>

    <div class="designer-body">
      <MaterialPanel v-if="showMaterialPanel && !isTableMode" />
      <div class="canvas-column">
        <div class="canvas-toolbar">
          <div class="canvas-toolbar-left">
            <button
              v-if="!isTableMode"
              class="panel-toggle-btn"
              :class="{ 'panel-toggle-active': showMaterialPanel }"
              :title="showMaterialPanel ? t('点击隐藏组件库') : t('点击显示组件库')"
              @click="showMaterialPanel = !showMaterialPanel"
            >
              {{ showMaterialPanel ? t('隐藏组件库') : t('显示组件库') }}
            </button>
            <button
              class="panel-toggle-btn"
              :class="{ 'panel-toggle-active': showPropertyPanel }"
              :title="
                showPropertyPanel ? t('点击隐藏属性面板') : t('点击显示属性面板')
              "
              @click="showPropertyPanel = !showPropertyPanel"
            >
              {{ showPropertyPanel ? t('隐藏属性') : t('显示属性') }}
            </button>
          </div>
          <div class="canvas-toolbar-center">
            <DxButton
              :text="t('撤销')"
              icon="undo"
              :disabled="!canUndo"
              @click="undo()"
            />
            <DxButton
              :text="t('重做')"
              icon="redo"
              :disabled="!canRedo"
              @click="redo()"
            />
            <DxButton
              v-if="!isTableMode"
              :text="t('清空')"
              icon="trash"
              styling-mode="outlined"
              @click="onClear"
            />
          </div>
          <div class="canvas-toolbar-right">
            <DxButton
              :text="t('源码')"
              icon="doc"
              styling-mode="outlined"
              @click="openVueCodeDialog()"
            />
            <DxButton
              text="Schema"
              styling-mode="outlined"
              @click="openSchemaDialog"
            />
          </div>
        </div>
        <CanvasPanel />
      </div>
      <PropertyPanel v-if="showPropertyPanel" />
    </div>

    <!-- 查看 Schema 对话框 -->
    <Teleport to="body">
      <div v-if="showSchema" class="modal-overlay" @click.self="closeDialogs">
        <div class="modal-dialog">
          <div class="modal-header">
            <span class="modal-title">{{ t('页面 Schema (JSON)') }}</span>
            <button class="modal-close" @click="closeDialogs">✕</button>
          </div>
          <textarea
            class="modal-content"
            :value="schemaText"
            @input="schemaText = ($event.target as HTMLTextAreaElement).value"
          />
          <div class="modal-footer">
            <span v-if="schemaError" class="schema-error">{{
              schemaError
            }}</span>
            <DxButton :text="t('应用')" type="default" @click="applySchema" />
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 查看源码对话框（可编辑 + 保存） -->
    <Teleport to="body">
      <div v-if="showVueCode" class="modal-overlay" @click.self="closeDialogs">
        <div class="modal-dialog">
          <div class="modal-header">
            <span class="modal-title">{{
              t('Vue 源码（可编辑，保存后生效）')
            }}</span>
            <button class="modal-close" @click="closeDialogs">✕</button>
          </div>
          <textarea
            class="modal-content code-font"
            :value="vueCodeText"
            @input="vueCodeText = ($event.target as HTMLTextAreaElement).value"
          />
          <div class="modal-footer">
            <span v-if="vueCodeError" class="schema-error">{{
              vueCodeError
            }}</span>
            <span
              class="items-tip"
              style="flex: 0 0 auto; color: #909399; margin-right: auto"
              >{{ t('保存后可通过撤销回滚') }}</span
            >
            <DxButton :text="t('保存')" type="default" @click="applyVueCode" />
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.designer {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.designer-header {
  height: 48px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  padding: 0 12px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.header-biz-mode {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #f0f2f5;
  border-radius: 6px;
  padding: 3px;
  margin: 0 auto;
}
.header-biz-mode-placeholder {
  flex: 1;
}
.biz-mode-btn {
  border: none;
  background: transparent;
  padding: 5px 12px;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  gap: 5px;
}
.biz-mode-icon {
  flex-shrink: 0;
}
.biz-mode-text {
  font-size: 13px;
}
.biz-mode-btn:hover {
  background: #e6f0ff;
  color: #1976d2;
}
.biz-mode-active {
  background: #1976d2;
  color: #fff;
}
.biz-mode-active:hover {
  background: #1976d2;
  color: #fff;
}
.logo {
  font-size: 15px;
  font-weight: 600;
  color: #1976d2;
  white-space: nowrap;
}
.page-name-input {
  border: 1px solid transparent;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 13px;
  outline: none;
  width: 140px;
  transition: border-color 0.15s;
}
.page-name-input:hover,
.page-name-input:focus {
  border-color: #c0c4cc;
}
.panel-toggle-btn {
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #606266;
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.panel-toggle-btn:hover {
  border-color: #1976d2;
  color: #1976d2;
}
.panel-toggle-active {
  border-color: #1976d2;
  color: #1976d2;
  background: #f0f7ff;
}
.header-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.tool-divider {
  width: 1px;
  height: 20px;
  background: #e4e7ed;
  margin: 0 2px;
  flex-shrink: 0;
}
.designer-body {
  flex: 1;
  display: flex;
  gap: 10px;
  min-height: 0;
  padding: 10px;
  background: #e8ebef;
}
.canvas-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 10px;
}
.canvas-toolbar {
  flex: 0 0 40px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  background: #fff;
  border-radius: 6px;
  border-bottom: 1px solid #e4e7ed;
}
.canvas-toolbar-left {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.canvas-toolbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}
.canvas-toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

/* 模态对话框 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal-dialog {
  width: 720px;
  max-width: 90vw;
  height: 70vh;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.2);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}
.modal-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.modal-close {
  border: none;
  background: none;
  font-size: 16px;
  color: #909399;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}
.modal-close:hover {
  color: #303133;
  background: #f0f2f5;
}
.modal-content {
  flex: 1;
  margin: 0;
  padding: 12px 16px;
  border: none;
  resize: none;
  overflow: auto;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
  background: #fafbfc;
  outline: none;
}
.modal-content.code-font {
  font-family: 'Fira Code', Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  tab-size: 2;
  white-space: pre;
}
.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 10px 16px;
  border-top: 1px solid #e4e7ed;
}
.schema-error {
  color: #f56c6c;
  font-size: 12px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
