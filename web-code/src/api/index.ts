/**
 * API 治理入口
 *
 * 职责：
 * - 把白名单 + 权限校验挂到 HTTP 网关层的请求拦截器
 * - 提供 apiCall(serviceId, params) 供事件代码用（替代 api.get(url)/api.post(url)）
 * - 在 main.ts 调用 setupApiGovernance() 完成安装
 */

import {
  httpRequest,
  interceptors,
  extractPayload,
  extractList,
  type RequestConfig,
} from '@/utils/request'
import {
  isUrlAllowed,
  checkUrlPermission,
  getGovernanceMode,
  resolveApi,
  _recordBlocked,
  _recordWarned,
  type ApiService,
} from './registry'

export * from './registry'

let _installed = false

/** 安装 API 治理拦截器（挂到 HTTP 网关层），幂等 */
export function setupApiGovernance(): void {
  if (_installed) return
  _installed = true

  interceptors.request.use(async (config: RequestConfig) => {
    const mode = getGovernanceMode()
    if (mode === 'off') return config

    // 白名单校验
    if (!isUrlAllowed(config.url)) {
      if (mode === 'strict') {
        _recordBlocked()
        throw new Error(
          `[API 治理] URL 不在白名单内，已拦截: ${config.url}`
        )
      }
      _recordWarned()
      console.warn(`[API 治理] URL 不在白名单内: ${config.url}`)
    }

    // 权限校验（垂直越权）
    const permErr = checkUrlPermission(config.url)
    if (permErr) {
      if (mode === 'strict') {
        _recordBlocked()
        throw new Error(`[API 治理] ${permErr} (${config.url})`)
      }
      _recordWarned()
      console.warn(`[API 治理] ${permErr} (${config.url})`)
    }

    return config
  })
}

// ---- apiCall：供事件代码用，按 serviceId 调用 ----

export interface ApiCallOptions {
  /** GET 查询参数 */
  params?: Record<string, unknown>
  /** POST/PUT 请求体 */
  data?: unknown
  /** 额外请求头 */
  headers?: Record<string, string>
  /** 是否用 FormData 发送（POST + 对象 body 时，true 则转 FormData） */
  formData?: boolean
}

export interface ApiCallResult {
  ok: boolean
  status: number
  data: unknown
  /** 解包后的列表数据（extractList） */
  list: unknown[]
  /** 解包后的分页数据（extractPayload） */
  payload: { data: unknown[]; totalCount: number }
}

/**
 * 按 serviceId 调用 API（供事件代码 api.call(serviceId, opts) 用）
 *
 * 这是事件代码的推荐调用方式，替代 api.get(url)/api.post(url)：
 * - URL 从注册表解析，事件代码不接触 URL，杜绝字面量 URL 泄露
 * - 自动走白名单 + 权限校验
 */
export async function apiCall(
  serviceId: string,
  options: ApiCallOptions = {}
): Promise<ApiCallResult> {
  const { url, method } = resolveApi(serviceId)
  const upperMethod = method.toUpperCase()

  let body: BodyInit | undefined
  let jsonContentType = true
  let finalUrl = url

  if (upperMethod === 'GET' && options.params) {
    const qs = Object.entries(options.params)
      .filter(([, v]) => v !== undefined && v !== null)
      .map(
        ([k, v]) =>
          `${encodeURIComponent(k)}=${encodeURIComponent(
            typeof v === 'object' ? JSON.stringify(v) : String(v)
          )}`
      )
      .join('&')
    if (qs) finalUrl = url + (url.includes('?') ? '&' : '?') + qs
  } else if (options.data !== undefined) {
    if (options.formData && typeof options.data === 'object') {
      const fd = new FormData()
      for (const [k, v] of Object.entries(options.data as Record<string, unknown>)) {
        if (v !== undefined && v !== null) {
          fd.append(k, typeof v === 'object' ? JSON.stringify(v) : String(v))
        }
      }
      body = fd
      jsonContentType = false
    } else {
      body = JSON.stringify(options.data)
    }
  }

  const result = await httpRequest(finalUrl, {
    method: upperMethod,
    headers: options.headers,
    body,
    jsonContentType,
  })

  return {
    ok: result.ok,
    status: result.status,
    data: result.data,
    list: extractList(result.data),
    payload: extractPayload(result.data),
  }
}

/** 获取服务声明（供属性面板 serviceId 选择器展示） */
export function getService(serviceId: string): ApiService | undefined {
  return resolveApi(serviceId).service
}