<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElDialog, ElButton, ElInput, ElSelect, ElOption, ElSwitch, ElTable, ElTableColumn, ElInputNumber, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import { designerStore, setFieldEventRules, collectAllNodes } from '@/store/designer'
import { getConfig } from '@/materials'
import type { FieldBChangeEventRule, FieldEventTriggerType, FieldEventFirePhase } from '@/types/schema'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const rules = ref<FieldBChangeEventRule[]>([])
const editingIdx = ref<number | null>(null)
const editingRule = ref<FieldBChangeEventRule | null>(null)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      rules.value = JSON.parse(JSON.stringify(designerStore.fieldChangeEvents ?? []))
      editingIdx.value = null
      editingRule.value = null
    }
  },
  { immediate: true },
)

/** 可监听字段列表（仅表单控件字段，排除布局/容器控件） */
const watchableFields = computed(() =>
  collectAllNodes(designerStore.schema)
    .filter((n) => {
      if (!n.fieldName) return false
      const c = getConfig(n.type)
      return c?.category === '表单'
    })
    .map((n) => n.fieldName as string),
)

const triggerTypes: { label: string; value: FieldEventTriggerType }[] = [
  { label: '任意字段变更', value: 'ANY_CHANGE' },
  { label: '全部字段变更', value: 'ALL_CHANGE' },
  { label: '表达式满足', value: 'EXPRESSION' },
]

const firePhases: { label: string; value: FieldEventFirePhase }[] = [
  { label: '同步插件', value: 'SYNC' },
  { label: '异步消息', value: 'ASYNC' },
  { label: '两者', value: 'BOTH' },
]

function addRule() {
  const sortNo = rules.value.length
  editingIdx.value = null
  editingRule.value = {
    eventCode: `EVT_${Date.now().toString(36)}`,
    eventName: '',
    triggerType: 'ANY_CHANGE',
    watchFields: [],
    firePhase: 'SYNC',
    enabled: true,
    sortNo,
  }
}

function editRule(idx: number) {
  editingIdx.value = idx
  editingRule.value = JSON.parse(JSON.stringify(rules.value[idx]))
}

function saveEditing() {
  if (!editingRule.value) return
  if (!editingRule.value.eventName) {
    ElMessage.warning(t('请填写事件名称'))
    return
  }
  if (!editingRule.value.watchFields.length) {
    ElMessage.warning(t('请选择监听字段'))
    return
  }
  const rule = editingRule.value
  if (editingIdx.value === null) {
    rules.value.push(rule)
  } else {
    rules.value[editingIdx.value] = rule
  }
  editingIdx.value = null
  editingRule.value = null
}

function cancelEditing() {
  editingIdx.value = null
  editingRule.value = null
}

function removeRule(idx: number) {
  rules.value.splice(idx, 1)
}

function onSave() {
  setFieldEventRules(rules.value)
  emit('update:visible', false)
}

function onClose() {
  emit('update:visible', false)
}

const { isMaximized, toggleMaximize, resetDialog } = useDialogDragAndMax()
watch(() => props.visible, (v) => { if (!v) resetDialog() })
</script>

<template>
  <ElDialog
    :model-value="visible"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    width="820px"
    append-to-body
    draggable
    :fullscreen="isMaximized"
  >
    <template #header>
      <DialogMaxHeader :title="t('字段变更事件配置')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <div class="event-rules">
      <ElTable :data="rules" border size="small" style="width: 100%">
        <ElTableColumn :label="t('事件编码')" prop="eventCode" width="140" />
        <ElTableColumn :label="t('事件名称')" prop="eventName" width="140" />
        <ElTableColumn :label="t('触发类型')" prop="triggerType" width="120" />
        <ElTableColumn :label="t('监听字段')" width="180">
          <template #default="{ row }">
            {{ (row.watchFields || []).join(', ') }}
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('阶段')" prop="firePhase" width="90" />
        <ElTableColumn :label="t('启用')" width="70">
          <template #default="{ row }">
            <ElSwitch v-model="row.enabled" disabled />
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('操作')" width="140">
          <template #default="{ $index }">
            <ElButton type="primary" link @click="editRule($index)">{{ t('编辑') }}</ElButton>
            <ElButton type="danger" link @click="removeRule($index)">{{ t('删除') }}</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElButton type="primary" link @click="addRule" style="margin-top: 8px">+ {{ t('新增规则') }}</ElButton>

      <!-- 编辑表单 -->
      <div v-if="editingRule" class="rule-edit-form">
        <h4>{{ editingIdx === null ? t('新增规则') : t('编辑规则') }}</h4>
        <div class="form-row">
          <ElInput v-model="editingRule.eventCode" :placeholder="t('事件编码')" style="width: 180px" />
          <ElInput v-model="editingRule.eventName" :placeholder="t('事件名称')" style="width: 180px" />
          <ElSelect v-model="editingRule.triggerType" style="width: 150px">
            <ElOption v-for="tp in triggerTypes" :key="tp.value" :label="t(tp.label)" :value="tp.value" />
          </ElSelect>
          <ElSelect v-model="editingRule.firePhase" style="width: 130px">
            <ElOption v-for="fp in firePhases" :key="fp.value" :label="t(fp.label)" :value="fp.value" />
          </ElSelect>
        </div>
        <div class="form-row">
          <ElSelect v-model="editingRule.watchFields" multiple :placeholder="t('监听字段')" style="width: 400px">
            <ElOption v-for="f in watchableFields" :key="f" :label="f" :value="f" />
          </ElSelect>
          <div class="sort-no-wrap">
            <label class="sort-no-label">{{ t('排序号') }}</label>
            <ElInputNumber v-model="editingRule.sortNo" :min="0" style="width: 100px" />
          </div>
          <ElSwitch v-model="editingRule.enabled" :active-text="t('启用')" />
        </div>
        <div v-if="editingRule.triggerType === 'EXPRESSION'" class="form-row">
          <ElInput v-model="editingRule.conditionExpr" :placeholder="t('触发条件表达式（后端 SpEL）')" style="width: 100%" />
        </div>
        <div class="form-row">
          <ElInput
            v-if="editingRule.firePhase === 'SYNC' || editingRule.firePhase === 'BOTH'"
            v-model="editingRule.syncPluginId"
            :placeholder="t('同步插件 ID')"
            style="width: 240px"
          />
          <ElInput
            v-if="editingRule.firePhase === 'ASYNC' || editingRule.firePhase === 'BOTH'"
            v-model="editingRule.asyncTopic"
            :placeholder="t('异步消息 Topic')"
            style="width: 240px"
          />
        </div>
        <div class="form-row">
          <ElButton type="primary" @click="saveEditing">{{ t('保存规则') }}</ElButton>
          <ElButton @click="cancelEditing">{{ t('取消') }}</ElButton>
        </div>
      </div>
    </div>

    <template #footer>
      <ElButton @click="onClose">{{ t('关闭') }}</ElButton>
      <ElButton type="primary" @click="onSave">{{ t('保存全部') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.event-rules {
  width: 100%;
}
.rule-edit-form {
  margin-top: 16px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.rule-edit-form h4 {
  margin: 0 0 12px;
}
.form-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.sort-no-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
}
.sort-no-label {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
</style>