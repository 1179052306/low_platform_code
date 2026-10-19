<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElInput, ElButton, ElMessage, ElAlert } from 'element-plus'
import { t } from '@/store/lang'
import type { BreakingChange } from '@/types/schema'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'

const props = defineProps<{
  visible: boolean
  changes: BreakingChange[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  confirm: [changes: BreakingChange[]]
}>()

const migrationScripts = ref<Record<string, string>>({})

watch(
  () => props.visible,
  (v) => {
    if (v) {
      migrationScripts.value = {}
    }
  },
)

function onConfirm() {
  const result = props.changes.map((c) => ({
    ...c,
    migrationScript: migrationScripts.value[c.type] || undefined,
  }))
  emit('confirm', result)
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
    width="600px"
    append-to-body
    draggable
    :fullscreen="isMaximized"
  >
    <template #header>
      <DialogMaxHeader :title="t('BREAKING 变更确认')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <ElAlert type="warning" :closable="false" :title="t('检测到 BREAKING 变更，请确认并提供迁移脚本')" style="margin-bottom: 16px;" />
    <ElForm label-width="120px">
      <ElFormItem v-for="c in changes" :key="c.type" :label="c.type">
        <div class="breaking-item">
          <p class="breaking-desc">{{ c.description }}</p>
          <ElInput
            v-model="migrationScripts[c.type]"
            type="textarea"
            :rows="2"
            :placeholder="t('输入迁移脚本（可选）')"
          />
        </div>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="onClose">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onConfirm">{{ t('确认') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.breaking-item {
  width: 100%;
}
.breaking-desc {
  margin: 0 0 8px;
  font-size: 13px;
  color: #e6a23c;
}
</style>