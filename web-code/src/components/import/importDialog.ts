/**
 * 导入弹窗服务（模块单例，模式对齐 pageModal）：
 * open({ bizCode, pageTitle, onFinished }) 打开三步导入弹窗，
 * 任务终态（SUCCESS/PARTIAL）时回调 onFinished（触发表格刷新）。
 */
import { ref } from 'vue'

export interface ImportDialogState {
  visible: boolean
  bizCode: string
  pageTitle: string
  onFinished?: () => void
}

const state = ref<ImportDialogState>({
  visible: false,
  bizCode: '',
  pageTitle: '',
})

export const importDialog = {
  state,

  /** 打开导入弹窗；onFinished 在导入终态（成功/部分成功）时回调 */
  open(options: { bizCode: string; pageTitle?: string; onFinished?: () => void }): void {
    if (!options.bizCode) return
    state.value = {
      visible: true,
      bizCode: options.bizCode,
      pageTitle: options.pageTitle || options.bizCode,
      onFinished: options.onFinished,
    }
  },

  /** 关闭弹窗（不终止后台任务） */
  close(): void {
    state.value = { ...state.value, visible: false }
  },
}