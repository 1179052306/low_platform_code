<script setup lang="ts">
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { createSandboxedFunction } from '@/utils/sandbox'
import { t } from '@/store/lang'

const props = defineProps<{
  modelValue: string
  visible: boolean
  title?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:visible': [value: boolean]
}>()

const localValue = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const syntaxError = ref('')
const syntaxOk = ref(false)
const showApiRef = ref(true)
const activeCategory = ref('控件操作')

watch(
  () => props.modelValue,
  (val) => {
    localValue.value = val
  },
  { immediate: true }
)

watch(
  () => props.visible,
  (val) => {
    if (val) {
      localValue.value = props.modelValue
      activeCategory.value = '控件操作'
      validateSyntax()
      nextTick(() => textareaRef.value?.focus())
    }
  }
)

function validateSyntax() {
  const code = localValue.value.trim()
  if (!code) {
    syntaxError.value = ''
    syntaxOk.value = false
    return
  }
  try {
    createSandboxedFunction(['e', 'api'], `return (async () => { ${code} })()`)
    syntaxError.value = ''
    syntaxOk.value = true
  } catch (err) {
    syntaxError.value = err instanceof Error ? err.message : String(err)
    syntaxOk.value = false
  }
}

function onInput() {
  validateSyntax()
}

function handleTabKey(e: KeyboardEvent) {
  e.stopPropagation()
  if (e.key === 'Tab') {
    e.preventDefault()
    const textarea = e.target as HTMLTextAreaElement
    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    localValue.value =
      localValue.value.substring(0, start) +
      '  ' +
      localValue.value.substring(end)
    nextTick(() => {
      textarea.selectionStart = textarea.selectionEnd = start + 2
    })
  }
}

function onConfirm() {
  emit('update:modelValue', localValue.value)
  emit('update:visible', false)
}

function onCancel() {
  emit('update:visible', false)
}

function captureKeydown(e: KeyboardEvent) {
  const ctrl = e.ctrlKey || e.metaKey
  if (!ctrl) return
  const key = e.key.toLowerCase()
  if (['z', 'y', 'a', 'c', 'x', 'v', 's', 'd'].includes(key)) {
    e.stopImmediatePropagation()
  }
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      window.addEventListener('keydown', captureKeydown, true)
    } else {
      window.removeEventListener('keydown', captureKeydown, true)
    }
  }
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', captureKeydown, true)
})

function insertSnippet(snippet: string) {
  const textarea = textareaRef.value
  if (!textarea) {
    localValue.value += snippet
    validateSyntax()
    return
  }
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  localValue.value =
    localValue.value.substring(0, start) +
    snippet +
    localValue.value.substring(end)
  nextTick(() => {
    textarea.focus()
    const newPos = start + snippet.length
    textarea.selectionStart = textarea.selectionEnd = newPos
    validateSyntax()
  })
}

// ---- API 分类（标签页式，精简） ----

interface ApiItem {
  sig: string
  desc: string
  insert?: string
}
interface ApiCategory {
  name: string
  icon: string
  items: ApiItem[]
}

const apiCategories: ApiCategory[] = [
  {
    name: '控件操作',
    icon: '🔌',
    items: [
      {
        sig: 'getValue(field)',
        desc: '获取值',
        insert: 'api.getValue("field_1")',
      },
      {
        sig: 'setValue(field, val)',
        desc: '设置值',
        insert: 'api.setValue("field_1", e.value)',
      },
      {
        sig: 'getProp(field, prop)',
        desc: '获取属性（支持 a.b.c）',
        insert: 'api.getProp("field_1", "disabled")',
      },
      {
        sig: 'setProp(field, prop, val)',
        desc: '设置属性（支持 a.b.c）',
        insert: 'api.setProp("field_1", "disabled", true)',
      },
      {
        sig: 'setVisible(field, bool)',
        desc: '显示/隐藏',
        insert: 'api.setVisible("field_1", true)',
      },
      {
        sig: 'setRequired(field, bool)',
        desc: '设置必填',
        insert: 'api.setRequired("field_1", true)',
      },
      {
        sig: 'setDataSource(field, data)',
        desc: '设置数据源',
        insert:
          'api.setDataSource("field_1", [\n  { id: 1, text: "选项A" },\n  { id: 2, text: "选项B" }\n])',
      },
      {
        sig: 'setStyle(field, key, val)',
        desc: '设置CSS样式',
        insert: 'api.setStyle("field_1", "color", "red")',
      },
    ],
  },
  {
    name: '网络请求',
    icon: '🌐',
    items: [
      {
        sig: 'await api.call(serviceId, opts?)',
        desc: '⭐推荐：按服务ID调用（URL从注册表解析，走白名单+权限校验）\nopts: { params, data, formData, onSuccess, onError, fieldName }\n返回: { ok, status, data, list, payload }',
        insert:
          'var result = await api.call("order.list", {\n  data: { page: 1 },\n  onSuccess: function(res) {\n    api.setValue("field_1", res.list)\n  },\n  onError: function(err) {\n    api.showMessage("请求失败: " + err.message)\n  }\n})',
      },
      {
        sig: 'await api.get(url, opts?)',
        desc: '同步等待 GET（等后台返回再继续）\nopts: { params, onSuccess, onError, fieldName }',
        insert:
          'await api.get("https://example.com/api", {\n  onSuccess: function(data) {\n    api.setValue("field_1", data)\n  },\n  onError: function(err) {\n    api.showMessage("请求失败: " + err.message)\n  }\n})',
      },
      {
        sig: 'await api.post(url, data, opts?)',
        desc: '同步等待 POST（等后台返回再继续）\nopts: { onSuccess, onError, fieldName }',
        insert:
          'await api.post("https://example.com/api", {\n  name: api.getValue("field_1")\n}, {\n  onSuccess: function(data) {\n    api.showMessage("提交成功")\n  },\n  onError: function(err) {\n    api.showMessage("提交失败: " + err.message)\n  }\n})',
      },
      {
        sig: 'api.get(url, opts?)',
        desc: '异步回调 GET（不等后台，触发后继续）\n用 onSuccess/onError 处理结果',
        insert:
          'api.get("https://example.com/api", {\n  onSuccess: function(data) {\n    api.setValue("field_1", data)\n  },\n  onError: function(err) {\n    api.showMessage("请求失败: " + err.message)\n  }\n})',
      },
      {
        sig: 'api.post(url, data, opts?)',
        desc: '异步回调 POST（不等后台，触发后继续）\n用 onSuccess/onError 处理结果',
        insert:
          'api.post("https://example.com/api", {\n  name: api.getValue("field_1")\n}, {\n  onSuccess: function(data) {\n    api.showMessage("提交成功")\n  },\n  onError: function(err) {\n    api.showMessage("提交失败: " + err.message)\n  }\n})',
      },
    ],
  },
  {
    name: '消息与查询',
    icon: '💬',
    items: [
      {
        sig: 'showMessage(text)',
        desc: '弹出提示',
        insert: 'api.showMessage("操作成功")',
      },
      {
        sig: 'confirm(text)',
        desc: '确认框，返回 true/false',
        insert: 'api.confirm("确认要删除吗？")',
      },
      {
        sig: 'getAllFields()',
        desc: '获取所有字段名',
        insert: 'api.getAllFields()',
      },
      {
        sig: 'getAllControls()',
        desc: '获取所有控件',
        insert: 'api.getAllControls()',
      },
    ],
  },
  {
    name: '表单验证',
    icon: '✓',
    items: [
      {
        sig: 'validate(field)',
        desc: '验证单个控件\n返回 { isValid: boolean, errors: string[] }',
        insert:
          'var result = api.validate("field_1")\nif (!result.isValid) {\n  api.showMessage(result.errors.join(", "))\n}',
      },
      {
        sig: 'validateAll()',
        desc: '验证所有表单控件\n返回 { isValid: boolean, errors: Record<string, string[]> }',
        insert:
          'var result = api.validateAll()\nif (!result.isValid) {\n  api.showMessage("表单验证失败")\n} else {\n  api.showMessage("验证通过")\n}',
      },
    ],
  },
  {
    name: '页面弹窗',
    icon: '🪟',
    items: [
      {
        sig: 'await api.openPage(pageId, params?, opts?)',
        desc: '打开子页面弹窗并等待返回\nparams: 传给子页面的参数\nopts: { title, width, mode }\n返回子页面 closePage(result) 的 result',
        insert:
          'var result = await api.openPage("page_xxx", {\n  id: api.getValue("field_1")\n}, {\n  title: "选择数据",\n  width: 800\n})\nif (result) {\n  api.setValue("field_2", result.name)\n}',
      },
      {
        sig: 'api.closePage(result?)',
        desc: '关闭当前子页面弹窗\nresult 传回父页面（openPage 的返回值）',
        insert:
          'api.closePage({\n  id: api.getValue("field_1"),\n  name: api.getValue("field_2")\n})',
      },
      {
        sig: 'api.getParam(key?)',
        desc: '获取父页面传入的参数\n不传 key 返回全部参数对象',
        insert: 'var id = api.getParam("id")\napi.setValue("field_1", id)',
      },
      {
        sig: 'api.params',
        desc: '父页面传入的全部参数（对象）',
        insert: 'var params = api.params\napi.setValue("field_1", params.id)',
      },
    ],
  },
  {
    name: '事件对象',
    icon: '⚡',
    items: [
      { sig: 'e', desc: '事件参数对象' },
      { sig: 'e.value', desc: '当前控件值（部分事件）' },
      { sig: 'e.event', desc: '原生 DOM 事件（部分事件）' },
    ],
  },
]

const currentItems = ref<ApiItem[]>(apiCategories[0].items)

function selectCategory(cat: ApiCategory) {
  activeCategory.value = cat.name
  currentItems.value = cat.items
}

const examples = [
  {
    label: '联动设值',
    code: '// 值变化时设置另一个控件\napi.setValue("input_2", e.value)',
  },
  {
    label: '显示/隐藏',
    code: '// 条件控制显示\nif (e.value === "yes") {\n  api.setVisible("input_2", true)\n  api.setRequired("input_2", true)\n} else {\n  api.setVisible("input_2", false)\n}',
  },
  {
    label: 'GET请求',
    code: '// GET 请求并回调处理\nawait api.get("https://httpbin.org/get", {\n  fieldName: "input_2",\n  onSuccess: function(data) {\n    api.showMessage("加载成功")\n  },\n  onError: function(err) {\n    api.showMessage("失败: " + err.message)\n  }\n})',
  },
  {
    label: 'POST提交',
    code: '// POST 提交数据\nawait api.post("https://httpbin.org/post", {\n  name: api.getValue("input_1")\n}, {\n  onSuccess: function(data) {\n    api.showMessage("提交成功")\n  },\n  onError: function(err) {\n    api.showMessage("失败: " + err.status)\n  }\n})',
  },
  {
    label: '设置数据源',
    code: '// 动态设置下拉框数据\napi.setDataSource("select_1", [\n  { id: 1, text: "选项A" },\n  { id: 2, text: "选项B" }\n])',
  },
  {
    label: '表单验证',
    code: '// 验证所有表单控件\nvar result = api.validateAll()\nif (!result.isValid) {\n  var msgs = []\n  for (var f in result.errors) {\n    msgs.push(f + ": " + result.errors[f].join(", "))\n  }\n  api.showMessage(msgs.join("\\n"))\n} else {\n  api.showMessage("验证通过")\n}',
  },
  {
    label: '打开子页面',
    code: '// 打开子页面弹窗，等待返回结果\nvar result = await api.openPage("page_xxx", {\n  id: api.getValue("field_1")\n}, {\n  title: "选择数据",\n  width: 800\n})\nif (result) {\n  api.setValue("field_2", result.name)\n}',
  },
  {
    label: '关闭并回传',
    code: '// 子页面关闭并回传数据给父页面\napi.closePage({\n  id: api.getValue("field_1"),\n  name: api.getValue("field_2")\n})',
  },
  {
    label: '接收父参数',
    code: '// 子页面接收父页面传入的参数\nvar id = api.getParam("id")\nif (id) {\n  api.setValue("field_1", id)\n}',
  },
]
</script>

<template>
  <div v-if="visible" class="dialog-overlay" @keydown.stop>
    <div class="dialog-container">
      <div class="dialog-header">
        <span class="dialog-title">{{ title || t('事件代码编辑') }}</span>
        <div class="dialog-header-actions">
          <button
            class="header-btn"
            :class="{ active: showApiRef }"
            @click="showApiRef = !showApiRef"
            :title="t('切换 API 参考')"
          >
            API
          </button>
          <button class="dialog-close" @click="onCancel">✕</button>
        </div>
      </div>

      <div class="dialog-body">
        <!-- 左侧：代码编辑器 -->
        <div class="editor-section">
          <div class="editor-toolbar">
            <span class="editor-status">
              <template v-if="syntaxError">
                <span class="status-error">✗ {{ syntaxError }}</span>
              </template>
              <template v-else-if="syntaxOk">
                <span class="status-ok">✓ {{ t('语法正确') }}</span>
              </template>
              <template v-else>
                <span class="status-hint">{{
                  t('参数: e（事件对象）, api（运行时 API）· 支持 async/await')
                }}</span>
              </template>
            </span>
            <div class="editor-examples">
              <button
                v-for="ex in examples"
                :key="ex.label"
                class="example-btn"
                @click="insertSnippet(ex.code)"
              >
                {{ t(ex.label) }}
              </button>
            </div>
          </div>
          <textarea
            ref="textareaRef"
            v-model="localValue"
            class="code-editor-area"
            :placeholder="
              t(`// 在此输入 JavaScript 代码
// 参数 e 为事件对象，api 为运行时 API
// 例如: api.setValue('input_2', e.value)`)
            "
            spellcheck="false"
            @input="onInput"
            @keydown="handleTabKey"
          />
        </div>

        <!-- 右侧：API 参考（分类标签 + 列表） -->
        <div v-if="showApiRef" class="api-section">
          <div class="api-tabs">
            <button
              v-for="cat in apiCategories"
              :key="cat.name"
              :class="['api-tab', activeCategory === cat.name ? 'active' : '']"
              @click="selectCategory(cat)"
            >
              {{ cat.icon }} {{ t(cat.name) }}
            </button>
          </div>
          <div class="api-list">
            <div
              v-for="item in currentItems"
              :key="item.sig"
              class="api-item"
              @click="
                item.insert
                  ? insertSnippet(item.insert)
                  : insertSnippet(item.sig)
              "
            >
              <code class="api-sig">{{ item.sig }}</code>
              <span
                class="api-desc"
                v-html="t(item.desc).replace(/\n/g, '<br>')"
              ></span>
            </div>
          </div>
        </div>
      </div>

      <div class="dialog-footer">
        <span class="footer-hint">{{
          t('点击 API 可插入代码 · Tab 键缩进')
        }}</span>
        <div class="footer-btns">
          <button class="dialog-btn dialog-btn-cancel" @click="onCancel">
            {{ t('取消') }}
          </button>
          <button class="dialog-btn dialog-btn-confirm" @click="onConfirm">
            {{ t('确定') }}
          </button>
        </div>
      </div>
    </div>
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
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  width: 1080px;
  max-width: 96vw;
  max-height: 92vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.dialog-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.dialog-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-btn {
  padding: 4px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.header-btn.active {
  background: #ecf5ff;
  border-color: #1976d2;
  color: #1976d2;
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
  display: flex;
  flex: 1;
  overflow: hidden;
  min-height: 520px;
}

/* 编辑器区域 */
.editor-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
  gap: 8px;
  flex-wrap: wrap;
}
.editor-status {
  font-size: 12px;
  flex: 1;
  min-width: 0;
}
.status-ok {
  color: #67c23a;
}
.status-error {
  color: #e53935;
  word-break: break-all;
}
.status-hint {
  color: #909399;
}
.editor-examples {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.example-btn {
  padding: 3px 10px;
  border: 1px dashed #1976d2;
  background: #ecf5ff;
  color: #1976d2;
  border-radius: 3px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.example-btn:hover {
  background: #1976d2;
  color: #fff;
  border-style: solid;
}
.code-editor-area {
  flex: 1;
  width: 100%;
  padding: 14px 18px;
  border: none;
  border-top: 1px solid #f0f0f0;
  font-size: 14px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  line-height: 1.7;
  resize: none;
  box-sizing: border-box;
  outline: none;
  tab-size: 2;
}
.code-editor-area:focus {
  background: #fafbff;
}

/* API 参考面板 */
.api-section {
  width: 300px;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}
.api-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 8px 8px 6px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
  background: #f9fafc;
}
.api-tab {
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.api-tab:hover {
  background: #f0f7ff;
}
.api-tab.active {
  background: #1976d2;
  color: #fff;
  border-color: #1976d2;
}
.api-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;
}
.api-item {
  padding: 8px 14px;
  cursor: pointer;
  transition: background 0.15s;
  display: flex;
  flex-direction: column;
  gap: 3px;
  border-bottom: 1px solid #f5f5f5;
}
.api-item:hover {
  background: #ecf5ff;
}
.api-sig {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #1976d2;
  font-weight: 500;
}
.api-desc {
  font-size: 11px;
  color: #909399;
  line-height: 1.5;
}

/* 底部 */
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-top: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.footer-hint {
  font-size: 11px;
  color: #c0c4cc;
}
.footer-btns {
  display: flex;
  gap: 8px;
}
.dialog-btn {
  padding: 8px 20px;
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
