<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElInput, ElButton, ElSelect, ElOption, ElSwitch, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import type { FieldLinkageRule } from '@/types/schema'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'

const props = defineProps<{
  visible: boolean
  rules?: FieldLinkageRule[]
  /** 可选字段列表 */
  fields?: { fieldName: string; label: string }[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [rules: FieldLinkageRule[]]
}>()

const linkageRules = ref<FieldLinkageRule[]>([])

watch(
  () => props.visible,
  (v) => {
    if (v) {
      linkageRules.value = JSON.parse(JSON.stringify(props.rules ?? []))
    }
  },
  { immediate: true },
)

const availableFields = computed(() => props.fields ?? [])

const linkageTypes = [
  { label: '赋值', value: 'value' },
  { label: '显隐', value: 'visible' },
  { label: '可编辑', value: 'editable' },
  { label: '选项', value: 'options' },
]

function addRule() {
  linkageRules.value.push({
    id: `lr_${Date.now()}`,
    triggerField: '',
    targetField: '',
    type: 'value',
    conditionExpr: '',
    value: '',
    enabled: true,
  })
}

function removeRule(idx: number) {
  linkageRules.value.splice(idx, 1)
}

function onSave() {
  emit('save', linkageRules.value)
  emit('update:visible', false)
}

const { isMaximized, toggleMaximize, resetDialog } = useDialogDragAndMax()
watch(() => props.visible, (v) => { if (!v) resetDialog() })
</script>

<template>
  <ElDialog
    :model-value="visible"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    width="720px"
    append-to-body
    draggable
    :fullscreen="isMaximized"
  >
    <template #header>
      <DialogMaxHeader :title="t('字段联动配置')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <div class="linkage-list">
      <div v-for="(rule, idx) in linkageRules" :key="rule.id" class="linkage-row">
        <ElSelect v-model="rule.triggerField" :placeholder="t('触发字段')" style="width: 130px">
          <ElOption v-for="f in availableFields" :key="f.fieldName" :label="f.label || f.fieldName" :value="f.fieldName" />
        </ElSelect>
        <ElSelect v-model="rule.targetField" :placeholder="t('目标字段')" style="width: 130px">
          <ElOption v-for="f in availableFields" :key="f.fieldName" :label="f.label || f.fieldName" :value="f.fieldName" />
        </ElSelect>
        <ElSelect v-model="rule.type" style="width: 90px">
          <ElOption v-for="lt in linkageTypes" :key="lt.value" :label="t(lt.label)" :value="lt.value" />
        </ElSelect>
        <ElInput v-model="rule.conditionExpr" :placeholder="t('条件表达式')" style="width: 160px" />
        <ElInput v-model="rule.value" :placeholder="t('联动值')" style="width: 100px" />
        <ElSwitch v-model="rule.enabled" />
        <ElButton type="danger" link @click="removeRule(idx)">{{ t('删除') }}</ElButton>
      </div>
      <ElButton type="primary" link @click="addRule">+ {{ t('添加联动规则') }}</ElButton>
    </div>
    <template #footer>
      <ElButton @click="emit('update:visible', false)">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onSave">{{ t('保存') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.linkage-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.linkage-row {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}
</style>