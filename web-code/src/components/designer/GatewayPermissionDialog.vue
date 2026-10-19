<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElButton,
  ElSelect,
  ElOption,
  ElSwitch,
  ElMessage,
} from 'element-plus'
import { t } from '@/store/lang'
import type { GatewayPermissionRule } from '@/types/schema'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'

const props = defineProps<{
  visible: boolean
  /** 编辑现有规则时传入，新增时 undefined */
  rule?: GatewayPermissionRule
  /** 角色列表（从后端加载，供选择允许/拒绝角色） */
  roles?: { code: string; name: string }[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [rule: GatewayPermissionRule]
}>()

const form = ref<GatewayPermissionRule>({
  id: '',
  bizCode: '',
  action: '',
  allowedRoles: [],
  deniedRoles: [],
  description: '',
  enabled: true,
})

const isEdit = ref(false)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      if (props.rule) {
        isEdit.value = true
        form.value = JSON.parse(JSON.stringify(props.rule))
      } else {
        isEdit.value = false
        form.value = {
          id: '',
          bizCode: '',
          action: '',
          allowedRoles: [],
          deniedRoles: [],
          description: '',
          enabled: true,
        }
      }
    }
  },
  { immediate: true },
)

function onSave() {
  if (!form.value.bizCode.trim()) {
    ElMessage.warning(t('请输入业务编码'))
    return
  }
  if (!form.value.action.trim()) {
    ElMessage.warning(t('请输入操作动作'))
    return
  }
  const rule: GatewayPermissionRule = {
    ...form.value,
    id: form.value.id || `gp_${Date.now()}`,
    bizCode: form.value.bizCode.trim(),
    action: form.value.action.trim(),
  }
  emit('save', rule)
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
      <DialogMaxHeader :title="isEdit ? t('编辑网关权限规则') : t('新增网关权限规则')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <ElForm label-width="100px">
      <ElFormItem :label="t('业务编码')">
        <ElInput
          v-model="form.bizCode"
          :placeholder="t('如 order')"
        />
      </ElFormItem>
      <ElFormItem :label="t('操作动作')">
        <ElInput
          v-model="form.action"
          :placeholder="t('如 create / query / submit / approve')"
        />
      </ElFormItem>
      <ElFormItem :label="t('允许角色')">
        <ElSelect
          v-model="form.allowedRoles"
          multiple
          filterable
          :placeholder="t('选择允许的角色')"
          style="width: 100%"
        >
          <ElOption
            v-for="r in (roles ?? [])"
            :key="r.code"
            :label="r.name || r.code"
            :value="r.code"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem :label="t('拒绝角色')">
        <ElSelect
          v-model="form.deniedRoles"
          multiple
          filterable
          :placeholder="t('选择拒绝的角色（优先级高于允许）')"
          style="width: 100%"
        >
          <ElOption
            v-for="r in (roles ?? [])"
            :key="r.code"
            :label="r.name || r.code"
            :value="r.code"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem :label="t('描述')">
        <ElInput
          v-model="form.description"
          type="textarea"
          :rows="2"
          :placeholder="t('规则描述（可选）')"
        />
      </ElFormItem>
      <ElFormItem :label="t('启用')">
        <ElSwitch v-model="form.enabled" />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="onClose">{{ t('取消') }}</ElButton>
      <ElButton type="primary" @click="onSave">{{ t('保存') }}</ElButton>
    </template>
  </ElDialog>
</template>