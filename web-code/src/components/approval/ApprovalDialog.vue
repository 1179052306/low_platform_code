<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElInput, ElButton, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import { useApproval } from '@/composables/useApproval'

const props = defineProps<{
  visible: boolean
  bizCode?: string
  bizId?: string
  action: 'approve' | 'reject'
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  done: []
}>()

const { approvalAction } = useApproval()
const comment = ref('')

watch(() => props.visible, (v) => {
  if (v) comment.value = ''
})

async function onConfirm() {
  if (!props.bizCode || !props.bizId) return
  try {
    await approvalAction(props.bizCode, props.bizId, props.action, comment.value)
    ElMessage.success(t('操作成功'))
    emit('done')
    emit('update:visible', false)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    :title="action === 'approve' ? t('审批同意') : t('审批驳回')"
    width="440px"
    append-to-body
  >
    <ElForm label-width="80px">
      <ElFormItem :label="t('审批意见')">
        <ElInput v-model="comment" type="textarea" :rows="3" :placeholder="t('请输入审批意见')" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="emit('update:visible', false)">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onConfirm">{{ t('确认') }}</ElButton>
    </template>
  </ElDialog>
</template>