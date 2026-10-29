<script setup lang="ts">
/**
 * 数据导入弹窗（三步向导）：下载模板 → 上传文件 → 进度/结果。
 * 关闭弹窗不终止后台任务；重开自动恢复进行中任务的进度视图。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import {
  cancelImportTask,
  downloadErrorFile,
  downloadImportTemplate,
  executeImport,
  getImportTask,
  listImportTasks,
  type ImportTaskView,
} from '@/api/import'
import { importDialog } from './importDialog'

const MAX_FILE_MB = 20

type Step = 'template' | 'upload' | 'progress' | 'result'

const step = ref<Step>('template')
const taskId = ref('')
const task = ref<ImportTaskView | null>(null)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const cancelling = ref(false)
const polling = ref<number | null>(null)
const finishedNotified = ref(false)

const visible = computed(() => importDialog.state.value.visible)
const bizCode = computed(() => importDialog.state.value.bizCode)
const pageTitle = computed(() => importDialog.state.value.pageTitle)

const isTerminal = computed(() => {
  const s = task.value?.status
  return s === 'SUCCESS' || s === 'PARTIAL' || s === 'FAILED' || s === 'CANCELLED'
})

const progressPercent = computed(() => {
  const tv = task.value
  if (!tv || !tv.totalRows) return 0
  return Math.min(100, Math.round((tv.processedRows / tv.totalRows) * 100))
})

const statusText = computed(() => {
  const map: Record<string, string> = {
    PENDING: t('排队中'),
    RUNNING: t('导入中'),
    SUCCESS: t('导入成功'),
    PARTIAL: t('部分成功'),
    FAILED: t('导入失败'),
    CANCELLED: t('已取消'),
  }
  return map[task.value?.status || ''] || t('排队中')
})

watch(visible, (v) => {
  stopPolling()
  if (v) {
    finishedNotified.value = false
    resumeOrStart()
  }
})

onBeforeUnmount(stopPolling)

/** 打开时：查最新非终态任务，有则恢复进度视图，否则进入模板步 */
async function resumeOrStart(): Promise<void> {
  try {
    const { list } = await listImportTasks(bizCode.value, 1, 10)
    const active = list.find(
      (item) => item.status === 'PENDING' || item.status === 'RUNNING',
    )
    if (active) {
      taskId.value = active.taskId
      task.value = active
      step.value = 'progress'
      startPolling()
      return
    }
  } catch {
    // 恢复查询失败不阻断新建导入
  }
  step.value = 'template'
  taskId.value = ''
  task.value = null
  selectedFile.value = null
}

async function onDownloadTemplate(): Promise<void> {
  try {
    await downloadImportTemplate(bizCode.value)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : t('模板下载失败'))
  }
}

function goUpload(): void {
  step.value = 'upload'
  selectedFile.value = null
}

function onFileChange(e: Event): void {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0] || null
  if (!file) {
    selectedFile.value = null
    return
  }
  const name = file.name.toLowerCase()
  if (!name.endsWith('.xlsx')) {
    ElMessage.warning(t('仅支持 .xlsx 格式文件'))
    input.value = ''
    selectedFile.value = null
    return
  }
  if (file.size > MAX_FILE_MB * 1024 * 1024) {
    ElMessage.warning(t('文件超过大小上限') + ` ${MAX_FILE_MB}MB`)
    input.value = ''
    selectedFile.value = null
    return
  }
  selectedFile.value = file
}

async function onUpload(): Promise<void> {
  if (!selectedFile.value) {
    ElMessage.warning(t('请先选择文件'))
    return
  }
  uploading.value = true
  try {
    const result = await executeImport(selectedFile.value, bizCode.value)
    if (result.sync && result.result) {
      // 同步完成（一期后端统一异步，预留）
      taskId.value = ''
      task.value = {
        taskId: '',
        bizCode: bizCode.value,
        status: result.result.failedRows > 0 ? 'PARTIAL' : 'SUCCESS',
        totalRows: result.result.totalRows,
        processedRows: result.result.totalRows,
        successRows: result.result.successRows,
        failedRows: result.result.failedRows,
        queuePosition: 0,
        hasErrorFile: false,
      }
      step.value = 'result'
      notifyFinished()
      return
    }
    if (result.taskId) {
      taskId.value = result.taskId
      step.value = 'progress'
      await pollOnce()
      startPolling()
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : t('上传失败'))
  } finally {
    uploading.value = false
  }
}

function startPolling(): void {
  stopPolling()
  polling.value = window.setInterval(pollOnce, 2000)
}

function stopPolling(): void {
  if (polling.value !== null) {
    window.clearInterval(polling.value)
    polling.value = null
  }
}

async function pollOnce(): Promise<void> {
  if (!taskId.value || !visible.value) return
  try {
    const tv = await getImportTask(taskId.value)
    task.value = tv
    if (tv.status === 'SUCCESS' || tv.status === 'PARTIAL' || tv.status === 'FAILED' || tv.status === 'CANCELLED') {
      stopPolling()
      step.value = 'result'
      if (tv.status === 'SUCCESS' || tv.status === 'PARTIAL') {
        notifyFinished()
      }
    }
  } catch {
    // 单次轮询失败静默，下轮重试
  }
}

function notifyFinished(): void {
  if (finishedNotified.value) return
  finishedNotified.value = true
  const cb = importDialog.state.value.onFinished
  if (typeof cb === 'function') cb()
}

async function onCancelTask(): Promise<void> {
  if (!taskId.value) return
  cancelling.value = true
  try {
    const ok = await cancelImportTask(taskId.value)
    if (ok) {
      ElMessage.success(t('已发送取消请求，任务将在当前批次完成后停止'))
    } else {
      ElMessage.warning(t('任务已结束，无法取消'))
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : t('取消失败'))
  } finally {
    cancelling.value = false
  }
}

async function onDownloadErrors(): Promise<void> {
  if (!taskId.value) return
  try {
    await downloadErrorFile(taskId.value)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : t('错误报告下载失败'))
  }
}

function onClose(): void {
  stopPolling()
  importDialog.close()
}

function onRetry(): void {
  step.value = 'upload'
  selectedFile.value = null
  taskId.value = ''
  task.value = null
  finishedNotified.value = false
}
</script>

<template>
  <div v-if="visible" class="import-overlay" @click.self="onClose">
    <div class="import-dialog">
      <div class="import-header">
        <span class="import-title">{{ t('数据导入') }}{{ pageTitle ? ` - ${pageTitle}` : '' }}</span>
        <button class="import-close" @click="onClose">x</button>
      </div>

      <div class="import-steps">
        <div class="import-step" :class="{ active: step === 'template' || step === 'upload', done: step === 'progress' || step === 'result' }">
          <span class="import-step-no">1</span>{{ t('准备文件') }}
        </div>
        <div class="import-step" :class="{ active: step === 'progress', done: step === 'result' }">
          <span class="import-step-no">2</span>{{ t('执行导入') }}
        </div>
        <div class="import-step" :class="{ active: step === 'result' }">
          <span class="import-step-no">3</span>{{ t('完成') }}
        </div>
      </div>

      <div class="import-body">
        <!-- 步骤1：下载模板 -->
        <div v-if="step === 'template'" class="import-panel">
          <div class="import-tips">
            <div class="import-tip-title">{{ t('导入须知') }}</div>
            <div class="import-tip">{{ t('1. 点击下方按钮下载导入模板（含填写说明与基础资料对照）') }}</div>
            <div class="import-tip">{{ t('2. 按模板格式填写数据，必填列与基础资料编码列见模板批注') }}</div>
            <div class="import-tip">{{ t('3. 主从表：一条主记录首行填主表列，明细行主表列留空') }}</div>
          </div>
          <div class="import-actions">
            <button class="import-btn import-btn-primary" @click="onDownloadTemplate">
              {{ t('下载模板') }}
            </button>
            <button class="import-btn" @click="goUpload">{{ t('已填好，下一步') }}</button>
          </div>
        </div>

        <!-- 步骤2：上传 -->
        <div v-else-if="step === 'upload'" class="import-panel">
          <div class="import-file-box">
            <input type="file" accept=".xlsx" @change="onFileChange" />
            <div v-if="selectedFile" class="import-file-info">
              {{ selectedFile.name }}（{{ (selectedFile.size / 1024).toFixed(1) }} KB）
            </div>
          </div>
          <div class="import-actions">
            <button class="import-btn" @click="step = 'template'">{{ t('上一步') }}</button>
            <button class="import-btn import-btn-primary" :disabled="!selectedFile || uploading" @click="onUpload">
              {{ uploading ? t('上传中...') : t('开始导入') }}
            </button>
          </div>
        </div>

        <!-- 步骤3a：进度 -->
        <div v-else-if="step === 'progress'" class="import-panel">
          <div class="import-status-line">
            <span class="import-status-tag">{{ statusText }}</span>
            <template v-if="task?.status === 'PENDING'">
              {{ t('排队中，前面还有') }} {{ task?.queuePosition || 0 }} {{ t('个任务') }}
            </template>
            <template v-else>
              {{ t('已处理') }} {{ task?.processedRows || 0 }} / {{ task?.totalRows || 0 }}
            </template>
          </div>
          <div class="import-progress">
            <div class="import-progress-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
          <div class="import-counts">
            {{ t('成功') }} {{ task?.successRows || 0 }} · {{ t('失败') }} {{ task?.failedRows || 0 }}
          </div>
          <div class="import-actions">
            <button class="import-btn import-btn-danger" :disabled="cancelling || isTerminal" @click="onCancelTask">
              {{ cancelling ? t('取消中...') : t('取消任务') }}
            </button>
            <button class="import-btn" @click="onClose">{{ t('后台运行，关闭窗口') }}</button>
          </div>
        </div>

        <!-- 步骤3b：结果 -->
        <div v-else-if="step === 'result'" class="import-panel">
          <div class="import-result-head" :class="'result-' + (task?.status || '').toLowerCase()">
            <span class="import-result-icon">{{
              task?.status === 'SUCCESS' ? '✓' : task?.status === 'CANCELLED' ? '⊘' : task?.status === 'PARTIAL' ? '⚠' : '✗'
            }}</span>
            <span>{{ statusText }}</span>
          </div>
          <div class="import-result-grid">
            <div class="import-result-cell">
              <div class="import-result-num">{{ task?.totalRows || 0 }}</div>
              <div class="import-result-label">{{ t('总记录数') }}</div>
            </div>
            <div class="import-result-cell ok">
              <div class="import-result-num">{{ task?.successRows || 0 }}</div>
              <div class="import-result-label">{{ t('成功') }}</div>
            </div>
            <div class="import-result-cell bad">
              <div class="import-result-num">{{ task?.failedRows || 0 }}</div>
              <div class="import-result-label">{{ t('失败') }}</div>
            </div>
          </div>
          <div v-if="task?.errorMsg" class="import-error-msg">{{ task.errorMsg }}</div>
          <div class="import-actions">
            <button v-if="task?.hasErrorFile" class="import-btn" @click="onDownloadErrors">
              {{ t('下载错误报告') }}
            </button>
            <button class="import-btn" @click="onRetry">{{ t('重新导入') }}</button>
            <button class="import-btn import-btn-primary" @click="onClose">{{ t('完成') }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.import-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 90000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.import-dialog {
  width: 560px;
  max-width: 92vw;
  background: #fff;
  border: 1px solid #dcdfe6;
  display: flex;
  flex-direction: column;
}
.import-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #ebeef5;
}
.import-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.import-close {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  color: #909399;
  padding: 0;
  line-height: 1;
}
.import-close:hover {
  color: #303133;
}
.import-steps {
  display: flex;
  border-bottom: 1px solid #ebeef5;
}
.import-step {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  color: #909399;
  border-bottom: 2px solid transparent;
}
.import-step.active {
  color: #1976d2;
  border-bottom-color: #1976d2;
}
.import-step.done {
  color: #67c23a;
}
.import-step-no {
  display: inline-block;
  width: 16px;
  height: 16px;
  line-height: 16px;
  border: 1px solid currentColor;
  border-radius: 50%;
  font-size: 11px;
  margin-right: 4px;
  text-align: center;
}
.import-body {
  padding: 16px;
  min-height: 260px;
}
.import-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.import-tips {
  border: 1px solid #ebeef5;
  padding: 12px;
}
.import-tip-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 6px;
  color: #303133;
}
.import-tip {
  font-size: 12px;
  color: #606266;
  line-height: 1.9;
}
.import-file-box input[type='file'] {
  width: 100%;
  border: 1px solid #dcdfe6;
  padding: 8px;
  box-sizing: border-box;
  font-size: 13px;
}
.import-file-info {
  font-size: 12px;
  color: #606266;
}
.import-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
.import-btn {
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #606266;
  padding: 6px 16px;
  font-size: 13px;
  cursor: pointer;
}
.import-btn:hover:not(:disabled) {
  color: #1976d2;
  border-color: #1976d2;
}
.import-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.import-btn-primary {
  background: #1976d2;
  border-color: #1976d2;
  color: #fff;
}
.import-btn-primary:hover:not(:disabled) {
  background: #1565c0;
  color: #fff;
}
.import-btn-danger {
  color: #f56c6c;
  border-color: #f56c6c;
}
.import-btn-danger:hover:not(:disabled) {
  background: #fef0f0;
  color: #f56c6c;
  border-color: #f56c6c;
}
.import-status-line {
  font-size: 13px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}
.import-status-tag {
  border: 1px solid #1976d2;
  color: #1976d2;
  font-size: 12px;
  padding: 1px 8px;
}
.import-progress {
  height: 14px;
  border: 1px solid #dcdfe6;
  background: #f5f7fa;
}
.import-progress-fill {
  height: 100%;
  background: #1976d2;
  transition: width 0.4s;
}
.import-counts {
  font-size: 12px;
  color: #606266;
}
.import-result-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  padding: 8px 12px;
  border: 1px solid #ebeef5;
}
.import-result-head.result-success {
  color: #67c23a;
  border-color: #c2e7b0;
  background: #f0f9eb;
}
.import-result-head.result-partial {
  color: #e6a23c;
  border-color: #f3d19e;
  background: #fdf6ec;
}
.import-result-head.result-failed {
  color: #f56c6c;
  border-color: #fbc4c4;
  background: #fef0f0;
}
.import-result-head.result-cancelled {
  color: #909399;
}
.import-result-icon {
  font-size: 18px;
}
.import-result-grid {
  display: flex;
  border: 1px solid #ebeef5;
}
.import-result-cell {
  flex: 1;
  text-align: center;
  padding: 12px 0;
  border-right: 1px solid #ebeef5;
}
.import-result-cell:last-child {
  border-right: none;
}
.import-result-num {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}
.import-result-cell.ok .import-result-num {
  color: #67c23a;
}
.import-result-cell.bad .import-result-num {
  color: #f56c6c;
}
.import-result-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.import-error-msg {
  border: 1px solid #fbc4c4;
  background: #fef0f0;
  color: #f56c6c;
  font-size: 12px;
  padding: 8px 12px;
  max-height: 120px;
  overflow: auto;
  word-break: break-all;
}
</style>