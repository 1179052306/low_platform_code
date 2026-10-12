<script setup lang="ts">
import { ElDialog, ElButton, ElMessage } from 'element-plus'
import { t } from '@/store/lang'

const props = defineProps<{
  visible: boolean
  /** 操作类型（如 submit/approve/freeze/delete） */
  action?: string
  /** 选中行数量 */
  selectedCount?: number
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  confirm: [action: string]
}>()

const actionLabels: Record<string, string> = {
  submit: '批量下达',
  approve: '批量审批',
  freeze: '批量冻结',
  unfreeze: '批量解冻',
  delete: '批量删除',
}

function onConfirm() {
  if (!props.action) {
    ElMessage.warning(t('请选择操作类型'))
    return
  }
  emit('confirm', props.action)
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
    :title="t('批量操作确认')"
    width="420px"
    append-to-body
  >
    <div class="batch-confirm">
      <p class="batch-info">
        {{ t('操作类型') }}：<strong>{{ action ? t(actionLabels[action] || action) : '' }}</strong>
      </p>
      <p class="batch-info">
        {{ t('选中数量') }}：<strong>{{ selectedCount ?? 0 }}</strong> {{ t('条') }}
      </p>
      <p class="batch-warning">{{ t('批量操作将同事务执行，全成功或全回滚。确认继续？') }}</p>
    </div>
    <template #footer>
      <ElButton @click="onClose">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onConfirm">{{ t('确认') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.batch-confirm {
  padding: 8px 0;
}
.batch-info {
  margin: 0 0 12px;
  font-size: 14px;
}
.batch-warning {
  margin: 16px 0 0;
  font-size: 13px;
  color: #e6a23c;
}
</style>