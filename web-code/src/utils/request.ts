/**
 * 统一 HTTP 网关层
 *
 * 职责：
 * - 收编所有散落的 window.fetch 调用，统一收口
 * - 统一注入基礎请求头（tenant/usertype/Authorization），替代各处硬编码
 * - 预留请求/响应拦截器（供服务注册表挂白名单、权限校验）
 * - 预留审计点（供调用日志记录，治"审计缺失"漏洞）
 * - 提供统一响应解包工具（extractPayload / extractList）
 *
 * 安全说明：
 * 前端治理能收敛攻击面、防误操作、提升审计能力，但真正的安全保证要靠后端
 * （后端必须校验权限 + 数据归属 + tenant）。前端不可作为安全边界。
 */

/** 基礎请求头配置（tenant/usertype/Authorization 等） */
export interface BaseHeadersConfig {
  tenant?: string
  usertype?: string
  authorization?: string
  [key: string]: string | undefined
}

let _baseHeadersConfig: BaseHeadersConfig = {
  tenant: 'WMS_TEST',
  usertype: '5',
}

/** 配置基礎请求头（运行时从登录态注入，替代硬编码） */
export function setBaseHeadersConfig(cfg: BaseHeadersConfig): void {
  _baseHeadersConfig = { ..._baseHeadersConfig, ...cfg }
}

/** 获取基礎请求头（拷贝，防止调用方篡改内部状态） */
export function getBaseHeaders(): Record<string, string> {
  const headers: Record<string, string> = {}
  for (const [k, v] of Object.entries(_baseHeadersConfig)) {
    if (v !== undefined && v !== null) headers[k] = v
  }
  return headers
}

// ---- 拦截器机制（供 B 层挂白名单 / 权限 / 日志） ----

/** 请求拦截器上下文 */
export interface RequestConfig {
  url: string
  method: string
  headers: Record<string, string>
  body?: BodyInit
}

/** 请求拦截器：可改写 url/method/headers/body，或抛错阻断（白名单用） */
export type RequestInterceptor = (
  config: RequestConfig
) => RequestConfig | Promise<RequestConfig>

/** 响应拦截器上下文 */
export interface ResponseContext {
  url: string
  method: string
  status: number
  ok: boolean
  data: unknown
}

/** 响应拦截器：可改写 data，或抛错（权限校验用） */
export type ResponseInterceptor = (
  ctx: ResponseContext
) => ResponseContext | Promise<ResponseContext>

const _requestInterceptors: RequestInterceptor[] = []
const _responseInterceptors: ResponseInterceptor[] = []

/** 拦截器注册 */
export const interceptors = {
  request: {
    use(fn: RequestInterceptor): void {
      _requestInterceptors.push(fn)
    },
    eject(fn: RequestInterceptor): void {
      const i = _requestInterceptors.indexOf(fn)
      if (i >= 0) _requestInterceptors.splice(i, 1)
    },
  },
  response: {
    use(fn: ResponseInterceptor): void {
      _responseInterceptors.push(fn)
    },
    eject(fn: ResponseInterceptor): void {
      const i = _responseInterceptors.indexOf(fn)
      if (i >= 0) _responseInterceptors.splice(i, 1)
    },
  },
}

// ---- 审计点 ----

/** 审计记录 */
export interface AuditRecord {
  url: string
  method: string
  status: number
  ok: boolean
  timestamp: number
  durationMs: number
}

let _onAudit: ((record: AuditRecord) => void) | null = null

/** 设置审计处理器（供调用日志记录） */
export function setAuditHandler(
  fn: ((record: AuditRecord) => void) | null
): void {
  _onAudit = fn
}

// ---- 核心请求函数 ----

/** 请求选项 */
export interface RequestOptions {
  method?: string
  headers?: Record<string, string>
  body?: BodyInit
  /** 是否自动添加 Content-Type: application/json，默认 true；FormData 场景设 false */
  jsonContentType?: boolean
}

/** 请求结果 */
export interface RequestResult {
  ok: boolean
  status: number
  data: unknown
}

/**
 * 核心请求函数：封装 window.fetch，统一注入基礎头、走拦截器、记审计
 *
 * 网络错误时抛出原异常（由调用方决定是否 catch），与 window.fetch 语义一致
 */
export async function httpRequest(
  url: string,
  options: RequestOptions = {}
): Promise<RequestResult> {
  const method = options.method || 'GET'
  const headers = getBaseHeaders()
  if (options.jsonContentType !== false) {
    if (!('Content-Type' in headers)) headers['Content-Type'] = 'application/json'
  }
  if (options.headers) {
    Object.assign(headers, options.headers)
  }

  let config: RequestConfig = { url, method, headers, body: options.body }
  for (const fn of _requestInterceptors) {
    config = await fn(config)
  }

  const start = Date.now()
  let resp: Response
  try {
    resp = await window.fetch(config.url, {
      method: config.method,
      headers: config.headers,
      body: config.body,
    })
  } catch (networkErr) {
    _onAudit?.({
      url: config.url,
      method: config.method,
      status: 0,
      ok: false,
      timestamp: start,
      durationMs: Date.now() - start,
    })
    throw networkErr
  }

  const text = await resp.text()
  let data: unknown
  try {
    data = JSON.parse(text)
  } catch {
    data = text
  }

  let ctx: ResponseContext = {
    url: config.url,
    method: config.method,
    status: resp.status,
    ok: resp.ok,
    data,
  }
  for (const fn of _responseInterceptors) {
    ctx = await fn(ctx)
  }

  _onAudit?.({
    url: ctx.url,
    method: ctx.method,
    status: ctx.status,
    ok: ctx.ok,
    timestamp: start,
    durationMs: Date.now() - start,
  })

  return { ok: ctx.ok, status: ctx.status, data: ctx.data }
}

// ---- 响应解包工具 ----

/**
 * 从后端响应中提取 { data, totalCount }
 * 兼容多种后端格式：数组、{data:[]}/{items:[]}/{rows:[]}/{list:[]}/{results:[]}、
 * {data:{data:[],totalCount:N}} 嵌套、{res,data,totalCount} 等
 */
export function extractPayload(raw: unknown): {
  data: unknown[]
  totalCount: number
} {
  if (Array.isArray(raw)) return { data: raw, totalCount: raw.length }
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    // 嵌套格式 { data: { data: [...], totalCount: N } }
    if (obj.data && typeof obj.data === 'object' && !Array.isArray(obj.data)) {
      const inner = obj.data as Record<string, unknown>
      const tc = Number(inner.totalCount ?? inner.total ?? inner.count ?? 0)
      if (Array.isArray(inner.data))
        return { data: inner.data, totalCount: tc || inner.data.length }
      if (Array.isArray(inner.rows))
        return { data: inner.rows, totalCount: tc || inner.rows.length }
      if (Array.isArray(inner.list))
        return { data: inner.list, totalCount: tc || inner.list.length }
      if (Array.isArray(inner.items))
        return { data: inner.items, totalCount: tc || inner.items.length }
    }
    const tc = Number(obj.totalCount ?? obj.total ?? obj.count ?? 0)
    if (Array.isArray(obj.data))
      return { data: obj.data, totalCount: tc || obj.data.length }
    if (Array.isArray(obj.items))
      return { data: obj.items, totalCount: tc || obj.items.length }
    if (Array.isArray(obj.rows))
      return { data: obj.rows, totalCount: tc || obj.rows.length }
    if (Array.isArray(obj.list))
      return { data: obj.list, totalCount: tc || obj.list.length }
    if (Array.isArray(obj.results))
      return { data: obj.results, totalCount: tc || obj.results.length }
  }
  return { data: [], totalCount: 0 }
}

/**
 * 从后端响应中提取数组（lookup 下拉数据用）
 * 兼容数组、{data:[]}/{items:[]}/{rows:[]}/{list:[]}/{results:[]}
 */
export function extractList(raw: unknown): unknown[] {
  if (Array.isArray(raw)) return raw
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    if (Array.isArray(obj.data)) return obj.data
    if (Array.isArray(obj.items)) return obj.items
    if (Array.isArray(obj.rows)) return obj.rows
    if (Array.isArray(obj.list)) return obj.list
    if (Array.isArray(obj.results)) return obj.results
  }
  return []
}