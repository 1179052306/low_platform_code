/**
 * 页面弹窗服务：支持在运行时打开另一个平台配置的页面作为弹窗/抽屉，
 * 父子页面通过 params 传参，子页面通过 closePage 回调父页面。
 * 每个弹窗拥有独立的渲染 store，互不干扰。
 */
import { ref } from 'vue'
import type { ComponentSchema } from '@/types/schema'
import { loadPage } from '@/store/persistence'

export interface PageModalOptions {
  /** 展现形式：弹窗（默认）或抽屉 */
  mode?: 'popup' | 'drawer'
  /** 标题 */
  title?: string
  /** 宽度 */
  width?: number | string
  /** 高度 */
  height?: number | string
  /** 业务模式：table=用表格页面 schema，form=用表单页面 schema（默认） */
  bizMode?: 'form' | 'table' | 'mobile'
}

export interface PageModalInstance {
  id: string
  pageId: string
  pageName: string
  schema: ComponentSchema[]
  params: Record<string, unknown>
  options: Required<PageModalOptions>
  visible: boolean
  resolve: (value: unknown) => void
  reject: (reason?: unknown) => void
}

const modalStack = ref<PageModalInstance[]>([])

/** 生成弹窗唯一 id */
function genModalId(): string {
  return `modal_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}}`
}

export const pageModal = {
  /** 弹窗栈（只读引用，供 PageModalHost 渲染） */
  stack: modalStack,

  /**
   * 打开一个页面弹窗，返回 Promise，子页面 closePage 时 resolve。
   * @param pageId 要打开的页面 ID
   * @param params 传给子页面的参数
   * @param options 弹窗选项（形式/标题/尺寸）
   */
  async openPage(
    pageId: string,
    params: Record<string, unknown> = {},
    options: PageModalOptions = {},
  ): Promise<unknown> {
    const page = await loadPage(pageId)
    if (!page) {
      throw new Error(`页面 ${pageId} 不存在，请先保存该页面`)
    }
    const mergedOptions: Required<PageModalOptions> = {
      mode: options.mode ?? 'popup',
      title: options.title ?? page.pageName ?? pageId,
      width: options.width ?? (options.mode === 'drawer' ? 600 : 800),
      height: options.height ?? (options.mode === 'drawer' ? '100%' : 600),
      bizMode: options.bizMode ?? 'form',
    }
    const bizMode = options.bizMode ?? 'form'
    const schema = (bizMode !== 'form' && page.bizSchemas)
      ? (page.bizSchemas[bizMode] as ComponentSchema[]) ?? page.root
      : page.root
    return new Promise<unknown>((resolve, reject) => {
      modalStack.value.push({
        id: genModalId(),
        pageId,
        pageName: page.pageName ?? pageId,
        schema,
        params,
        options: mergedOptions,
        visible: true,
        resolve,
        reject,
      })
    })
  },

  /**
   * 关闭当前栈顶弹窗并返回结果给父页面。
   * @param result 返回给父页面的数据
   */
  closePage(result?: unknown): void {
    const top = modalStack.value[modalStack.value.length - 1]
    if (!top) return
    top.visible = false
    top.resolve(result)
    setTimeout(() => {
      const idx = modalStack.value.findIndex((m) => m.id === top.id)
      if (idx >= 0) modalStack.value.splice(idx, 1)
    }, 300)
  },

  /** 获取当前（栈顶）弹窗的全部参数 */
  getParams(): Record<string, unknown> {
    const top = modalStack.value[modalStack.value.length - 1]
    return top?.params ?? {}
  },

  /** 获取当前（栈顶）弹窗的指定参数 */
  getParam(key: string): unknown {
    const top = modalStack.value[modalStack.value.length - 1]
    return top?.params?.[key]
  },
}