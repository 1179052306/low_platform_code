<script setup lang="ts">
import { ElUpload, ElMessage } from 'element-plus'


const props = defineProps<{
  value?: any[]
  modelValue?: any[]
  readOnly?: boolean
  disabled?: boolean
  accept?: string
  multiple?: boolean
  maxFileSize?: number
  allowedFileExtensions?: string[]
  uploadUrl?: string
  uploadMode?: 'instantly' | 'useForm'
  showFileList?: boolean
}>()

const emit = defineEmits<{
  'update:value': [val: any[]]
  'update:modelValue': [val: any[]]
  valueChanged: [e: { value: any[] }]
  fileUploaded: [e: { file: any }]
  uploadError: [e: { error: any }]
}>()

function beforeUpload(file: File): boolean {
  if (props.maxFileSize && file.size > props.maxFileSize * 1024) {
    ElMessage.error(`文件大小不能超过 ${props.maxFileSize}KB`)
    return false
  }
  if (props.allowedFileExtensions?.length) {
    const ext = file.name.split('.').pop()?.toLowerCase()
    if (!ext || !props.allowedFileExtensions.map((e) => e.toLowerCase()).includes(ext)) {
      ElMessage.error(`仅支持 ${props.allowedFileExtensions.join(', ')} 格式`)
      return false
    }
  }
  return true
}

function onSuccess(response: any, file: any) {
  emit('fileUploaded', { file: { ...file, response } })
}

function onError(error: any, file: any) {
  emit('uploadError', { error })
}
</script>

<template>
  <ElUpload
    :action="uploadUrl ?? '#'"
    :accept="accept"
    :multiple="multiple"
    :disabled="readOnly || disabled"
    :show-file-list="showFileList !== false"
    :before-upload="beforeUpload"
    :on-success="onSuccess"
    :on-error="onError"
    :auto-upload="uploadMode !== 'useForm'"
  >
    <slot>
      <span class="el-upload-trigger">点击上传</span>
    </slot>
  </ElUpload>
</template>

<style scoped>
.el-upload-trigger {
  display: inline-block;
  padding: 8px 16px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}
</style>