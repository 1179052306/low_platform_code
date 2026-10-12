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
import type { FieldPermissionRule, FieldPermType } from '@/types/schema'

const props = defineProps<{
  visible: boolean
  /** 编辑现有规则时传入，新增时 undefined */
  rule?: FieldPermissionRule
  /** 角色列表（从后端加载） */
  roles?: { code: string; name: string }[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [rule: FieldPermissionRule]
}>()

const permTypeOptions: { label: string; value: FieldPermType }[] = [
  { label: 'hidden', value: 'hidden' },
  { label: 'readonly', value: 'readonly' },
  { label: 'editable', value: 'editable' },
]

const form = ref<FieldPermissionRule>({
  id: '',
  bizCode: '',
  fieldName: '',
  permType: 'editable',
  roles: [],
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
          fieldName: '',
          permType: 'editable',
          roles: [],
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
  if (!form.value.fieldName.trim()) {
    ElMessage.warning(t('请输入字段名'))
    return
  }
  const rule: FieldPermissionRule = {
    ...form.value,
    id: form.value.id || `fp_${Date.now()}`,
    bizCode: form.value.bizCode.trim(),
    fieldName: form.value.fieldName.trim(),
  }
  emit('save', rule)
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
    :title="isEdit ? t('编辑字段权限规则') : t('新增字段权限规则')"
    width="520px"
    append-to-body
  >
    <ElForm label-width="100px">
      <ElFormItem :label="t('业务编码')">
        <ElInput
          v-model="form.bizCode"
          :placeholder="t('如 order')"
        />
      </ElFormItem>
      <ElFormItem :label="t('字段名')">
        <ElInput
          v-model="form.fieldName"
          :placeholder="t('如 amount / status')"
        />
      </ElFormItem>
      <ElFormItem :label="t('权限类型')">
        <ElSelect v-model="form.permType" style="width: 100%">
          <ElOption
            v-for="opt in permTypeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem :label="t('适用角色')">
        <ElSelect
          v-model="form.roles"
          multiple
          filterable
          :placeholder="t('选择适用角色（空表示对所有角色生效）')"
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