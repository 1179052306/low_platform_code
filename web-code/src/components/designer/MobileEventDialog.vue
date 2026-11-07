<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { t } from '@/store/lang'

interface EventAction {
  action: string
  [key: string]: unknown
}

const props = defineProps<{
  modelValue: string
  visible: boolean
  eventName: string
  events: { name: string; label: string; description?: string }[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:visible': [value: boolean]
  confirm: [value: string]
}>()

const config = ref<EventAction>({ action: '' })

const ACTION_TYPES = [
  { value: '', label: '无' },
  { value: 'openPage', label: '打开页面' },
  { value: 'closePage', label: '关闭页面' },
  { value: 'showSnackbar', label: '显示提示' },
  { value: 'refresh', label: '刷新数据' },
  { value: 'requestApi', label: '请求接口' },
  { value: 'setFormValue', label: '设置表单值' },
]

watch(
  () => props.modelValue,
  (val) => {
    try {
      const parsed = val ? JSON.parse(val) : {}
      const action = parsed?.[props.eventName] ?? parsed
      config.value = action && typeof action === 'object' ? { ...action } : { action: '' }
    } catch {
      config.value = { action: '' }
    }
  },
  { immediate: true },
)

watch(
  () => props.visible,
  (val) => {
    if (val) {
      try {
        const parsed = props.modelValue ? JSON.parse(props.modelValue) : {}
        const action = parsed?.[props.eventName] ?? parsed
        config.value = action && typeof action === 'object' ? { ...action } : { action: '' }
      } catch {
        config.value = { action: '' }
      }
    }
  },
)

const currentAction = computed(() => config.value.action || '')
const currentEventLabel = computed(
  () => props.events.find((event) => event.name === props.eventName)?.label ?? props.eventName,
)

function getAction(): EventAction | null {
  return currentAction.value ? config.value : null
}

function setActionType(action: string) {
  if (!action) {
    config.value = { action: '' }
  } else {
    config.value = { action }
  }
  emitChange()
}

function setActionField(field: string, value: unknown) {
  config.value[field] = value
  emitChange()
}

function emitChange() {
  emit('update:modelValue', currentAction.value ? JSON.stringify(config.value) : '')
}

function close() {
  emit('update:visible', false)
}

function confirm() {
  emit('confirm', currentAction.value ? JSON.stringify(config.value) : '')
  close()
}
</script>

<template>
  <div v-if="visible" class="mobile-event-overlay" @click.self="close">
    <div class="mobile-event-dialog">
      <div class="mobile-event-header">
        <span>{{ t('事件配置') }} · {{ t(currentEventLabel) }}</span>
        <button class="close-btn" @click="close">✕</button>
      </div>
      <div class="mobile-event-body">
        <div class="event-config">
          <div class="config-row">
            <label>{{ t('动作类型') }}</label>
            <select
              :value="currentAction"
              @change="(e) => setActionType((e.target as HTMLSelectElement).value)"
            >
              <option v-for="a in ACTION_TYPES" :key="a.value" :value="a.value">
                {{ t(a.label) }}
              </option>
            </select>
          </div>

          <template v-if="currentAction === 'openPage'">
            <div class="config-row">
              <label>{{ t('页面编码') }}</label>
              <input
                type="text"
                :value="getAction()?.pageCode ?? ''"
                @input="(e) => setActionField('pageCode', (e.target as HTMLInputElement).value)"
                placeholder="如 detail"
              />
            </div>
            <div class="config-row">
              <label>{{ t('传递参数') }}</label>
              <input
                type="text"
                :value="getAction()?.params ?? ''"
                @input="(e) => setActionField('params', (e.target as HTMLInputElement).value)"
                placeholder='如 {"id":"123"}'
              />
            </div>
          </template>
          <template v-if="currentAction === 'showSnackbar'">
            <div class="config-row">
              <label>{{ t('提示内容') }}</label>
              <input
                type="text"
                :value="getAction()?.message ?? ''"
                @input="(e) => setActionField('message', (e.target as HTMLInputElement).value)"
                placeholder="如 操作成功"
              />
            </div>
          </template>

          <template v-if="currentAction === 'requestApi'">
            <div class="config-row">
              <label>{{ t('接口地址') }}</label>
              <input
                type="text"
                :value="getAction()?.url ?? ''"
                @input="(e) => setActionField('url', (e.target as HTMLInputElement).value)"
                placeholder="/api/xxx"
              />
            </div>
            <div class="config-row">
              <label>{{ t('请求方法') }}</label>
              <select
                :value="getAction()?.method ?? 'GET'"
                @change="(e) => setActionField('method', (e.target as HTMLSelectElement).value)"
              >
                <option value="GET">GET</option>
                <option value="POST">POST</option>
                <option value="PUT">PUT</option>
                <option value="DELETE">DELETE</option>
              </select>
            </div>
          </template>

          <template v-if="currentAction === 'setFormValue'">
            <div class="config-row">
              <label>{{ t('字段名') }}</label>
              <input
                type="text"
                :value="getAction()?.field ?? ''"
                @input="(e) => setActionField('field', (e.target as HTMLInputElement).value)"
                placeholder="如 name"
              />
            </div>
            <div class="config-row">
              <label>{{ t('值') }}</label>
              <input
                type="text"
                :value="getAction()?.value ?? ''"
                @input="(e) => setActionField('value', (e.target as HTMLInputElement).value)"
                placeholder="如 张三"
              />
            </div>
          </template>

          <div v-if="!currentAction" class="config-hint">
            {{ t('请选择动作类型') }}
          </div>
        </div>
      </div>
      <div class="mobile-event-footer">
        <button class="dialog-btn dialog-btn-cancel" @click="close">{{ t('取消') }}</button>
        <button class="dialog-btn dialog-btn-confirm" @click="confirm">{{ t('确定') }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mobile-event-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 10001;
  display: flex;
  align-items: center;
  justify-content: center;
}
.mobile-event-dialog {
  width: 560px;
  max-height: 480px;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.mobile-event-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  font-weight: 600;
  font-size: 14px;
}
.close-btn {
  border: none;
  background: none;
  cursor: pointer;
  font-size: 16px;
  color: #999;
}
.mobile-event-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}
.event-config {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}
.mobile-event-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid #eee;
}
.dialog-btn {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  padding: 6px 14px;
  cursor: pointer;
}
.dialog-btn-confirm {
  border-color: #1976d2;
  background: #1976d2;
  color: #fff;
}
.config-row {
  margin-bottom: 12px;
}
.config-row label {
  display: block;
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
}
.config-row input,
.config-row select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 13px;
  box-sizing: border-box;
}
.config-hint {
  color: #999;
  font-size: 13px;
  text-align: center;
  padding: 40px 0;
}
</style>
