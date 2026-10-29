<template>
  <ElDialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :title="t('页面插件配置')"
    width="800px"
    :close-on-click-modal="false"
    append-to-body
  >
    <div class="plugin-binding-dialog">
      <div class="plugin-left">
        <div class="plugin-search">
          <ElInput
            v-model="searchKeyword"
            :placeholder="t('搜索插件')"
            clearable
            @input="filterPlugins"
          />
        </div>
        <div class="plugin-list">
          <div
            v-for="p in filteredPlugins"
            :key="p.pluginId"
            class="plugin-item"
            :class="{ 'is-bound': isBound(p.pluginId) }"
            @click="addBinding(p)"
          >
            <div class="plugin-item-name">{{ p.pluginId }}</div>
            <div class="plugin-item-desc">{{ p.name || p.version || '' }}</div>
          </div>
          <div v-if="filteredPlugins.length === 0" class="plugin-empty">
            {{ loading ? t('加载中...') : t('暂无可用插件') }}
          </div>
        </div>
      </div>
      <div class="plugin-right">
        <div class="plugin-bound-header">{{ t('已绑定插件') }}</div>
        <div class="plugin-bound-list">
          <div
            v-for="(b, idx) in localBindings"
            :key="b.pluginId"
            class="plugin-bound-item"
          >
            <div class="plugin-bound-info">
              <span class="plugin-bound-no">{{ idx + 1 }}</span>
              <span class="plugin-bound-name">{{ b.pluginId }}</span>
              <span v-if="b.pluginName" class="plugin-bound-desc">{{ b.pluginName }}</span>
            </div>
            <div class="plugin-bound-actions">
              <ElSwitch v-model="b.enabled" size="small" />
              <ElButton text size="small" :disabled="idx === 0" @click="moveUp(idx)">↑</ElButton>
              <ElButton text size="small" :disabled="idx === localBindings.length - 1" @click="moveDown(idx)">↓</ElButton>
              <ElButton text size="small" type="danger" @click="removeBinding(idx)">×</ElButton>
            </div>
          </div>
          <div v-if="localBindings.length === 0" class="plugin-empty">
            {{ t('未绑定任何插件，从左侧选择插件添加') }}
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <ElButton @click="$emit('update:modelValue', false)">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onConfirm">{{ t('确定') }}</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ElDialog, ElInput, ElButton, ElSwitch, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import { apiCall } from '@/api'
import type { PluginBinding } from '@/types/schema'

const props = defineProps<{
  modelValue: boolean
  bindings: PluginBinding[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [plugins: PluginBinding[]]
}>()

interface AvailablePlugin {
  pluginId: string
  name?: string
  version?: string
}

const searchKeyword = ref('')
const loading = ref(false)
const availablePlugins = ref<AvailablePlugin[]>([])
const filteredPlugins = ref<AvailablePlugin[]>([])
const localBindings = ref<PluginBinding[]>([])

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      localBindings.value = props.bindings.map((b) => ({ ...b }))
      loadPlugins()
    }
  },
)

async function loadPlugins() {
  loading.value = true
  try {
    const result = await apiCall('lowcodePluginList', {})
    const data = (result as any)?.data ?? (result as any)?.result?.data ?? result
    const list = Array.isArray(data) ? data : (data as any)?.list ?? []
    availablePlugins.value = list.map((p: any) => ({
      pluginId: p.pluginId || p.plugin_id || p.id,
      name: p.name || p.plugin_name,
      version: p.version,
    }))
    filterPlugins()
  } catch (e) {
    availablePlugins.value = []
  } finally {
    loading.value = false
  }
}

function filterPlugins() {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) {
    filteredPlugins.value = availablePlugins.value
  } else {
    filteredPlugins.value = availablePlugins.value.filter(
      (p) => p.pluginId.toLowerCase().includes(kw) || (p.name ?? '').toLowerCase().includes(kw),
    )
  }
}

function isBound(pluginId: string): boolean {
  return localBindings.value.some((b) => b.pluginId === pluginId)
}

function addBinding(p: AvailablePlugin) {
  if (isBound(p.pluginId)) return
  localBindings.value.push({
    pluginId: p.pluginId,
    pluginName: p.name,
    sortOrder: localBindings.value.length + 1,
    enabled: true,
  })
}

function removeBinding(idx: number) {
  localBindings.value.splice(idx, 1)
  localBindings.value.forEach((b, i) => (b.sortOrder = i + 1))
}

function moveUp(idx: number) {
  if (idx === 0) return
  const tmp = localBindings.value[idx - 1]
  localBindings.value[idx - 1] = localBindings.value[idx]
  localBindings.value[idx] = tmp
  localBindings.value.forEach((b, i) => (b.sortOrder = i + 1))
}

function moveDown(idx: number) {
  if (idx === localBindings.value.length - 1) return
  const tmp = localBindings.value[idx + 1]
  localBindings.value[idx + 1] = localBindings.value[idx]
  localBindings.value[idx] = tmp
  localBindings.value.forEach((b, i) => (b.sortOrder = i + 1))
}

function onConfirm() {
  emit('confirm', localBindings.value.map((b, i) => ({ ...b, sortOrder: i + 1 })))
  emit('update:modelValue', false)
}
</script>

<style scoped>
.plugin-binding-dialog {
  display: flex;
  gap: 16px;
  min-height: 400px;
}
.plugin-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}
.plugin-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}
.plugin-search {
  padding: 8px;
  border-bottom: 1px solid #e4e7ed;
}
.plugin-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}
.plugin-item {
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.2s;
}
.plugin-item:hover {
  background: #f5f7fa;
}
.plugin-item.is-bound {
  opacity: 0.5;
  cursor: not-allowed;
}
.plugin-item-name {
  font-size: 13px;
  font-weight: 500;
}
.plugin-item-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.plugin-bound-header {
  padding: 8px 12px;
  border-bottom: 1px solid #e4e7ed;
  font-size: 13px;
  font-weight: 500;
  background: #f5f7fa;
}
.plugin-bound-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}
.plugin-bound-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 4px;
}
.plugin-bound-item:hover {
  background: #f5f7fa;
}
.plugin-bound-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.plugin-bound-no {
  font-size: 12px;
  color: #909399;
  width: 20px;
}
.plugin-bound-name {
  font-size: 13px;
  font-weight: 500;
}
.plugin-bound-desc {
  font-size: 12px;
  color: #909399;
}
.plugin-bound-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.plugin-empty {
  padding: 20px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
</style>