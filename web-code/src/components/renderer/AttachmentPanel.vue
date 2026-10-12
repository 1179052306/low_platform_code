<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElButton, ElUpload, ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { httpRequest } from '@/utils/request'
import { t } from '@/store/lang'
import type { AttachmentConfig } from '@/types/schema'

const props = defineProps<{
  /** 业务编码 */
  bizCode?: string
  /** 业务数据 ID */
  bizId?: string
  /** 附件配置 */
  config?: AttachmentConfig
}>()

interface AttachmentItem {
  id: string
  fileName: string
  fileSize: number
  fileType: string
  uploadTime: string
}

const fileList = ref<AttachmentItem[]>([])
const uploading = ref(false)

async function refreshList() {
  if (!props.bizCode || !props.bizId) return
  try {
    const res = await httpRequest('/api/lowcode/attachment/list', {
      method: 'POST',
      body: JSON.stringify({ bizCode: props.bizCode, bizId: props.bizId }),
    })
    if (res.ok) {
      const body = res.data as { data?: AttachmentItem[] }
      fileList.value = body.data || []
    }
  } catch {
    fileList.value = []
  }
}

function beforeUpload(file: File): boolean {
  const cfg = props.config
  if (cfg?.fileTypes?.length) {
    const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!cfg.fileTypes.includes(ext)) {
      ElMessage.warning(t('不支持的文件类型'))
      return false
    }
  }
  if (cfg?.maxSize) {
    const sizeMB = file.size / 1024 / 1024
    if (sizeMB > cfg.maxSize) {
      ElMessage.warning(t('文件大小超过限制'))
      return false
    }
  }
  if (cfg?.maxCount && fileList.value.length >= cfg.maxCount) {
    ElMessage.warning(t('附件数量超过限制'))
    return false
  }
  return true
}

async function handleUpload(file: File): Promise<void> {
  if (!beforeUpload(file)) return
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('bizCode', props.bizCode || '')
    fd.append('bizId', props.bizId || '')
    const res = await httpRequest('/api/lowcode/attachment/upload', {
      method: 'POST',
      body: fd,
      jsonContentType: false,
    })
    if (res.ok) {
      ElMessage.success(t('上传成功'))
      await refreshList()
    } else {
      ElMessage.error(t('上传失败'))
    }
  } catch {
    ElMessage.error(t('上传失败'))
  } finally {
    uploading.value = false
  }
}

async function handleDelete(item: AttachmentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(t('确认删除此附件？'), t('提示'), { type: 'warning' })
    await httpRequest('/api/lowcode/attachment/delete', {
      method: 'POST',
      body: JSON.stringify({ id: item.id }),
    })
    ElMessage.success(t('删除成功'))
    await refreshList()
  } catch {
    /* 取消删除 */
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

onMounted(() => {
  refreshList()
})

watch(() => props.bizId, () => {
  refreshList()
})
</script>

<template>
  <div class="attachment-panel">
    <div class="att-header">
      <ElUpload
        :show-file-list="false"
        :before-upload="() => false"
        @change="(file: UploadFile) => { if (file.raw) handleUpload(file.raw) }"
      >
        <ElButton type="primary" :loading="uploading">{{ t('上传附件') }}</ElButton>
      </ElUpload>
      <span v-if="config?.maxCount" class="att-tip">
        {{ t('最多') }} {{ config.maxCount }} {{ t('个') }}
      </span>
    </div>
    <div class="att-list">
      <div v-for="item in fileList" :key="item.id" class="att-item">
        <span class="att-name">{{ item.fileName }}</span>
        <span class="att-size">{{ formatSize(item.fileSize) }}</span>
        <span class="att-time">{{ item.uploadTime }}</span>
        <ElButton type="danger" link @click="handleDelete(item)">{{ t('删除') }}</ElButton>
      </div>
      <div v-if="!fileList.length" class="att-empty">{{ t('暂无附件') }}</div>
    </div>
  </div>
</template>

<style scoped>
.attachment-panel {
  padding: 8px 0;
}
.att-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.att-tip {
  font-size: 12px;
  color: #999;
}
.att-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.att-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
}
.att-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.att-size {
  color: #999;
  width: 80px;
}
.att-time {
  color: #999;
  width: 140px;
}
.att-empty {
  text-align: center;
  color: #ccc;
  padding: 20px;
  font-size: 13px;
}
</style>