<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElButton, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import { useApproval } from '@/composables/useApproval'

const props = defineProps<{
  visible: boolean
  bizCode?: string
  bizId?: string
  type: 'transfer' | 'countersign'
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  done: []
}>()

const { approvalAction } = useApproval()
const targetUser = ref('')
const comment = ref('')

watch(() => props.visible, (v) => {
  if (v) {
    targetUser.value = ''
    comment.value = ''
  }
})

async function onConfirm() {
  if (!props.bizCode || !props.bizId) return
  if (!targetUser.value.trim()) {
    ElMessage.warning(t('请输入目标用户'))
    return
  }
  try {
    await approvalAction(props.bizCode, props.bizId, props.type, comment.value, { targetUser: targetUser.value })
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
    :title="type === 'transfer' ? t('转办') : t('加签')"
    width="440px"
    append-to-body
  >
    <ElForm label-width="80px">
      <ElFormItem :label="t('目标用户')">
        <ElInput v-model="targetUser" :placeholder="t('请输入用户名')" />
      </ElFormItem>
      <ElFormItem :label="t('备注')">
        <ElInput v-model="comment" type="textarea" :rows="2" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="emit('update:visible', false)">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onConfirm">{{ t('确认') }}</ElButton>
    </template>
  </ElDialog>
</template>