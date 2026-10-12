<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElInput, ElButton, ElSelect, ElOption, ElSwitch, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import type { MasterDetailConfig, DetailTableConfig } from '@/types/schema'

const props = defineProps<{
  visible: boolean
  config?: MasterDetailConfig
  /** 主表名（从 designerStore.tableName 传入） */
  masterTable?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [config: MasterDetailConfig]
}>()

const masterTableRef = ref('')
const details = ref<DetailTableConfig[]>([])

watch(
  () => props.visible,
  (v) => {
    if (v) {
      masterTableRef.value = props.config?.masterTable ?? props.masterTable ?? ''
      details.value = JSON.parse(JSON.stringify(props.config?.details ?? []))
    }
  },
  { immediate: true },
)

function addDetail() {
  details.value.push({
    tableName: '',
    label: '',
    foreignKey: '',
    columns: [],
    editable: true,
    allowAdd: true,
    allowDelete: true,
  })
}

function removeDetail(idx: number) {
  details.value.splice(idx, 1)
}

function onSave() {
  if (!masterTableRef.value) {
    ElMessage.warning(t('请填写主表名'))
    return
  }
  const config: MasterDetailConfig = {
    masterTable: masterTableRef.value,
    details: details.value,
  }
  emit('save', config)
  emit('update:visible', false)
}

function onClose() {
  emit('update:visible', false)
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    :title="t('主从表配置')"
    width="640px"
    append-to-body
  >
    <ElForm label-width="100px">
      <ElFormItem :label="t('主表名')">
        <ElInput v-model="masterTableRef" :placeholder="t('主表名')" />
      </ElFormItem>

      <ElFormItem :label="t('从表')">
        <div class="detail-list">
          <div v-for="(d, idx) in details" :key="idx" class="detail-row">
            <ElInput v-model="d.tableName" :placeholder="t('从表名')" style="width: 120px" />
            <ElInput v-model="d.label" :placeholder="t('显示名')" style="width: 100px" />
            <ElInput v-model="d.foreignKey" :placeholder="t('外键字段')" style="width: 120px" />
            <ElSwitch v-model="d.editable" :active-text="t('可编辑')" />
            <ElButton type="danger" link @click="removeDetail(idx)">{{ t('删除') }}</ElButton>
          </div>
          <ElButton type="primary" link @click="addDetail">+ {{ t('添加从表') }}</ElButton>
        </div>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="onClose">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onSave">{{ t('保存') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.detail-list {
  width: 100%;
}
.detail-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
</style>