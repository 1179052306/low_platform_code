/**
 * 多语言 store —— 后端资源加载方案（禁止使用 vue-i18n 等前端 i18n 框架）。
 *
 * 核心流程：
 *   后端存翻译文本 → 前端拉取 → 响应式 ref 存字典 → t('中文原文') 查表
 *
 * t('保存') 在中文语言下直接返回原文，非中文查后端字典表，无匹配返回原文兜底。
 */
import { ref } from 'vue'
import { httpRequest } from '@/utils/request'

export interface LangInfo {
  locale: string
  name: string
  sortOrder: number
  enabled: boolean
  isRtl: boolean
}

const LANG_KEY = 'lowcode:lang'

export const currentLang = ref<string>('zh-CN')
export const langList = ref<LangInfo[]>([])
export const langLoading = ref(false)
const dict = ref<Record<string, string>>({})
const loadedModules = ref<Set<string>>(new Set())

/**
 * 翻译函数：用中文原文做 key。
 * - 中文语言下直接返回原文（零开销）
 * - 非中文查 dict 字典，有则返回译文，无则返回原文兜底
 */
export function t(text: string): string {
  if (currentLang.value === 'zh-CN') return text
  return dict.value[text] ?? text
}

/** 应用启动时初始化语言 */
export async function initLang(): Promise<void> {
  await loadLangList()

  const saved = localStorage.getItem(LANG_KEY)
  if (saved && langList.value.some((l) => l.locale === saved)) {
    currentLang.value = saved
  } else {
    currentLang.value = 'zh-CN'
  }

  if (currentLang.value !== 'zh-CN') {
    await loadModule('common')
  }

  await syncDevExtreme(currentLang.value)
  syncRtl(currentLang.value)
}

/** 切换语言 */
export async function switchLang(locale: string): Promise<void> {
  if (locale === currentLang.value) return

  langLoading.value = true
  try {
    let newDict: Record<string, string> = {}
    const newLoaded = new Set<string>()

    if (locale !== 'zh-CN') {
      try {
        const res = await httpRequest('/api/lowcode/lang/load', {
          method: 'POST',
          body: JSON.stringify({ locale, modules: ['common'] }),
        })
        const body = res.data as { res?: boolean; data?: Record<string, string> }
        if (body && body.res !== false && body.data) {
          newDict = body.data
          newLoaded.add('common')
        }
      } catch (e) {
        console.error('[lang] switchLang preload failed:', e)
      }
    }

    dict.value = newDict
    loadedModules.value = newLoaded
    currentLang.value = locale
    localStorage.setItem(LANG_KEY, locale)

    await syncDevExtreme(locale)
    syncRtl(locale)
  } finally {
    langLoading.value = false
  }
}

/** 按模块加载翻译（已加载的模块自动跳过） */
export async function loadModule(...modules: string[]): Promise<void> {
  if (currentLang.value === 'zh-CN') return

  const unloaded = modules.filter((m) => !loadedModules.value.has(m))
  if (unloaded.length === 0) return

  try {
    const res = await httpRequest('/api/lowcode/lang/load', {
      method: 'POST',
      body: JSON.stringify({ locale: currentLang.value, modules: unloaded }),
    })
    const body = res.data as { res?: boolean; data?: Record<string, string> }
    if (body && body.res !== false && body.data) {
      Object.assign(dict.value, body.data)
      for (const m of unloaded) {
        loadedModules.value.add(m)
      }
    }
  } catch (e) {
    console.error('[lang] loadModule failed:', e)
  }
}

/** 加载所有翻译（不按模块过滤）—— 预览页面/设计器用 */
export async function loadAllTranslations(): Promise<void> {
  if (currentLang.value === 'zh-CN') return
  try {
    const res = await httpRequest('/api/lowcode/lang/load', {
      method: 'POST',
      body: JSON.stringify({ locale: currentLang.value }),
    })
    const body = res.data as { res?: boolean; data?: Record<string, string> }
    if (body && body.res !== false && body.data) {
      Object.assign(dict.value, body.data)
    }
  } catch (e) {
    console.error('[lang] loadAllTranslations failed:', e)
  }
}

/** 从后端加载语言列表 */
async function loadLangList(): Promise<void> {
  try {
    const res = await httpRequest('/api/lowcode/lang/list', { method: 'POST' })
    const body = res.data as { res?: boolean; data?: LangInfo[] }
    if (body && body.res !== false && body.data) {
      langList.value = body.data
    }
  } catch (e) {
    console.error('[lang] loadLangList failed:', e)
  }
}

/** 同步 DevExtreme locale + 加载对应语言包 */
async function syncDevExtreme(locale: string): Promise<void> {
  const langCode = locale.split('-')[0]
  try {
    const dx = await import('devextreme/localization')
    const messageLoaders: Record<string, () => Promise<{ default: unknown }>> = {
      ar: () => import('devextreme/localization/messages/ar.json'),
      bg: () => import('devextreme/localization/messages/bg.json'),
      ca: () => import('devextreme/localization/messages/ca.json'),
      cs: () => import('devextreme/localization/messages/cs.json'),
      da: () => import('devextreme/localization/messages/da.json'),
      de: () => import('devextreme/localization/messages/de.json'),
      el: () => import('devextreme/localization/messages/el.json'),
      en: () => import('devextreme/localization/messages/en.json'),
      es: () => import('devextreme/localization/messages/es.json'),
      fa: () => import('devextreme/localization/messages/fa.json'),
      fi: () => import('devextreme/localization/messages/fi.json'),
      fr: () => import('devextreme/localization/messages/fr.json'),
      hu: () => import('devextreme/localization/messages/hu.json'),
      it: () => import('devextreme/localization/messages/it.json'),
      ja: () => import('devextreme/localization/messages/ja.json'),
      ko: () => import('devextreme/localization/messages/ko.json'),
      lt: () => import('devextreme/localization/messages/lt.json'),
      lv: () => import('devextreme/localization/messages/lv.json'),
      nb: () => import('devextreme/localization/messages/nb.json'),
      nl: () => import('devextreme/localization/messages/nl.json'),
      pl: () => import('devextreme/localization/messages/pl.json'),
      pt: () => import('devextreme/localization/messages/pt.json'),
      ro: () => import('devextreme/localization/messages/ro.json'),
      ru: () => import('devextreme/localization/messages/ru.json'),
      sl: () => import('devextreme/localization/messages/sl.json'),
      sv: () => import('devextreme/localization/messages/sv.json'),
      tr: () => import('devextreme/localization/messages/tr.json'),
      uk: () => import('devextreme/localization/messages/uk.json'),
      vi: () => import('devextreme/localization/messages/vi.json'),
      zh: () => import('devextreme/localization/messages/zh.json'),
    }
    const loader = messageLoaders[langCode]
    if (loader) {
      try {
        const messages = await loader()
        dx.loadMessages(messages.default)
      } catch {
        // 语言包加载失败，降级使用默认英文
      }
    }
    dx.locale(langCode)
  } catch (e) {
    console.error('[lang] syncDevExtreme failed:', e)
  }
}

/** 同步 RTL 方向 */
function syncRtl(locale: string): void {
  const lang = langList.value.find((l) => l.locale === locale)
  const dir = lang?.isRtl ? 'rtl' : 'ltr'
  document.documentElement.dir = dir
  document.documentElement.lang = locale
}