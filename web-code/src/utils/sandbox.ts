/**
 * 事件代码沙箱化工具
 *
 * 通过在 new Function 函数体开头注入 var 声明，遮蔽危险全局变量
 * 使事件代码无法直接访问 window/document/fetch/localStorage 等
 * 只能通过注入的白名单 api 对象与外界交互
 *
 * 治理的安全漏洞：事件代码无沙箱（#7）
 * - 事件代码用 new Function 执行，原本能通过闭包访问全局 window/document/fetch
 * - 可偷 localStorage、改 DOM、发任意请求、跳转页面等
 * - 沙箱化后，事件代码只能用 api 对象提供的能力
 *
 * 注意：这是"软沙箱"，降低误操作和非恶意风险，不能防御刻意逃逸的攻击者
 * （globalThis 仍可访问，但低代码事件代码由用户自己编写，非恶意注入）
 */

/** 需要遮蔽的危险全局变量名 */
const BLOCKED_GLOBALS = [
  'window',
  'document',
  'fetch',
  'XMLHttpRequest',
  'self',
  'eval',
  'Function',
  'location',
  'localStorage',
  'sessionStorage',
  'navigator',
  'history',
  'top',
  'parent',
  'frames',
  'open',
  'alert',
  'confirm',
  'prompt',
  'close',
  'postMessage',
  'WebSocket',
  'EventSource',
  'indexedDB',
  'caches',
  'cookieStore',
  'importScripts',
]

/**
 * 构建沙箱化的函数体：在原始 body 前注入遮蔽声明
 * 事件代码中引用被遮蔽的变量将得到 undefined
 */
export function buildSandboxedBody(body: string): string {
  const decls = BLOCKED_GLOBALS.map((g) => `var ${g}=undefined;`).join('')
  return decls + body
}

/**
 * 创建沙箱化的函数（封装 new Function）
 * @param paramNames 参数名列表（如 ['e', 'api']）
 * @param body 函数体（会被沙箱化处理）
 */
export function createSandboxedFunction(
  paramNames: string[],
  body: string
): Function {
  return new Function(...paramNames, buildSandboxedBody(body))
}