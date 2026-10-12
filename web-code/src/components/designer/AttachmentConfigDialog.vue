<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElButton,
  ElInputNumber,
  ElSwitch,
  ElSelect,
  ElOption,
  ElMessage,
} from 'element-plus'
import { t } from '@/store/lang'
import type { AttachmentConfig } from '@/types/schema'

const props = defineProps<{
  visible: boolean
  config?: AttachmentConfig
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [config: AttachmentConfig]
}>()

const form = ref<AttachmentConfig>({
  fileTypes: [],
  maxSize: 10,
  maxCount: 5,
  required: false,
  bizCode: '',
})

watch(
  () => props.visible,
  (v) => {
    if (v) {
      form.value = JSON.parse(JSON.stringify(props.config ?? {
        fileTypes: [],
        maxSize: 10,
        maxCount: 5,
        required: false,
        bizCode: '',
      }))
    }
  },
  { immediate: true },
)

const commonTypes = ['jpg', 'png', 'gif', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'zip']

function onSave() {
  emit('save', { ...form.value })
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
    :title="t('附件配置')"
    width="480px"
    append-to-body
  >
    <ElForm label-width="100px">
      <ElFormItem :label="t('允许类型')">
        <ElSelect v-model="form.fileTypes" multiple filterable allow-create :placeholder="t('文件类型')" style="width: 100%">
          <ElOption v-for="ft in commonTypes" :key="ft" :label="ft" :value="ft" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem :label="t('最大大小')">
        <ElInputNumber v-model="form.maxSize" :min="1" :max="100" />
        <span style="margin-left: 8px; color: #999;">MB</span>
      </ElFormItem>
      <ElFormItem :label="t('最大数量')">
        <ElInputNumber v-model="form.maxCount" :min="1" :max="50" />
      </ElFormItem>
      <ElFormItem :label="t('必填')">
        <ElSwitch v-model="form.required" />
      </ElFormItem>
      <ElFormItem :label="t('业务编码')">
        <ElInput v-model="form.bizCode" :placeholder="t('附件存储业务编码')" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="onClose">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onSave">{{ t('保存') }}</ElButton>
    </template>
  </ElDialog>
</template>