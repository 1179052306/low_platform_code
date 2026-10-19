<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElInput, ElButton, ElSelect, ElOption, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import type { AppPackage } from '@/types/schema'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'

const props = defineProps<{
  visible: boolean
  pkg?: AppPackage
  /** 可选页面列表 */
  pages?: { id: string; name: string }[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [pkg: AppPackage]
}>()

const form = ref<AppPackage>({
  id: '',
  name: '',
  version: '1.0.0',
  pageIds: [],
  metaKeys: [],
  description: '',
  createdAt: '',
  updatedAt: '',
})

const isEdit = ref(false)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      if (props.pkg) {
        isEdit.value = true
        form.value = JSON.parse(JSON.stringify(props.pkg))
      } else {
        isEdit.value = false
        form.value = {
          id: '',
          name: '',
          version: '1.0.0',
          pageIds: [],
          metaKeys: [],
          description: '',
          createdAt: '',
          updatedAt: '',
        }
      }
    }
  },
  { immediate: true },
)

function onSave() {
  if (!form.value.name.trim()) {
    ElMessage.warning(t('请输入包名称'))
    return
  }
  const pkg: AppPackage = {
    ...form.value,
    id: form.value.id || `pkg_${Date.now()}`,
    updatedAt: new Date().toISOString(),
  }
  emit('save', pkg)
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
    width="560px"
    append-to-body
    draggable
    :fullscreen="isMaximized"
  >
    <template #header>
      <DialogMaxHeader :title="isEdit ? t('编辑应用包') : t('新建应用包')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <ElForm label-width="100px">
      <ElFormItem :label="t('包名称')">
        <ElInput v-model="form.name" :placeholder="t('如 销售管理包')" />
      </ElFormItem>
      <ElFormItem :label="t('版本号')">
        <ElInput v-model="form.version" placeholder="1.0.0" />
      </ElFormItem>
      <ElFormItem :label="t('包含页面')">
        <ElSelect v-model="form.pageIds" multiple filterable :placeholder="t('选择页面')" style="width: 100%">
          <ElOption v-for="p in (pages ?? [])" :key="p.id" :label="p.name" :value="p.id" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem :label="t('描述')">
        <ElInput v-model="form.description" type="textarea" :rows="2" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="onClose">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onSave">{{ t('保存') }}</ElButton>
    </template>
  </ElDialog>
</template>