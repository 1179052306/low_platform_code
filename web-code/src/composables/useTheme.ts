import type { Theme } from '@/types/schema'
import { httpRequest } from '@/utils/request'
import { t } from '@/store/lang'

async function themeApiCall(url: string, options: { method?: string; body?: string } = {}): Promise<unknown> {
  const res = await httpRequest(url, { method: options.method || 'POST', body: options.body })
  if (!res.ok) {
    throw new Error(t('后端连接异常'))
  }
  const body = res.data as { res?: boolean; msg?: string; data?: unknown }
  if (body && body.res === false) {
    throw new Error(t(body.msg || '操作失败'))
  }
  return body?.data
}

let _themesCache: Theme[] | null = null

export async function loadThemes(keyword?: string): Promise<Theme[]> {
  if (!_themesCache) {
    const data = await themeApiCall('/api/lowcode/theme/list', { body: JSON.stringify({}) })
    _themesCache = (data as Theme[]) || []
  }
  if (!keyword) {
    return _themesCache
  }
  const kw = keyword.toLowerCase()
  return _themesCache.filter((th) => th.name.toLowerCase().includes(kw))
}

export function invalidateThemesCache(): void {
  _themesCache = null
}

export async function getThemeDetail(id: string): Promise<Theme | null> {
  const data = await themeApiCall('/api/lowcode/theme/detail', { body: JSON.stringify({ id }) })
  return (data as Theme) || null
}

export async function saveTheme(
  theme: { id: string; name: string; colors: Theme['colors']; radius: number },
): Promise<string> {
  const data = await themeApiCall('/api/lowcode/theme/save', { body: JSON.stringify(theme) })
  invalidateThemesCache()
  return (data as { id: string }).id
}

export async function deleteTheme(id: string): Promise<void> {
  await themeApiCall('/api/lowcode/theme/delete', { body: JSON.stringify({ id }) })
  invalidateThemesCache()
}

export async function getDefaultThemeId(): Promise<string | null> {
  const data = await themeApiCall('/api/lowcode/theme/default/get', { body: JSON.stringify({}) })
  return (data as { themeId: string | null })?.themeId || null
}

export async function setDefaultThemeId(themeId: string): Promise<void> {
  await themeApiCall('/api/lowcode/theme/default/set', { body: JSON.stringify({ themeId }) })
}

export async function batchApplyTheme(themeId: string, pageIds: string[]): Promise<void> {
  await themeApiCall('/api/lowcode/theme/batch-apply', { body: JSON.stringify({ themeId, pageIds }) })
}

const LC_VARS = [
  '--lc-primary', '--lc-primary-hover', '--lc-success', '--lc-warning', '--lc-danger',
  '--lc-info', '--lc-page-bg', '--lc-card-bg', '--lc-text-primary', '--lc-text-secondary',
  '--lc-border', '--lc-radius',
] as const

export function applyTheme(el: HTMLElement, theme: Theme | null): void {
  if (!theme) {
    clearTheme(el)
    return
  }
  const c = theme.colors
  const s = el.style
  s.setProperty('--lc-primary', c.primary)
  s.setProperty('--lc-primary-hover', c.primaryHover)
  s.setProperty('--lc-success', c.success)
  s.setProperty('--lc-warning', c.warning)
  s.setProperty('--lc-danger', c.danger)
  s.setProperty('--lc-info', c.info)
  s.setProperty('--lc-page-bg', c.pageBg)
  s.setProperty('--lc-card-bg', c.cardBg)
  s.setProperty('--lc-text-primary', c.textPrimary)
  s.setProperty('--lc-text-secondary', c.textSecondary)
  s.setProperty('--lc-border', c.border)
  s.setProperty('--lc-radius', theme.radius + 'px')
}

export function clearTheme(el: HTMLElement): void {
  for (const p of LC_VARS) {
    el.style.removeProperty(p)
  }
}

let _themeContainer: HTMLElement | null = null
export function setThemeContainer(el: HTMLElement | null): void {
  _themeContainer = el
}
export function getThemeContainer(): HTMLElement | null {
  return _themeContainer
}