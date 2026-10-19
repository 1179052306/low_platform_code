<script setup lang="ts">
import { provide, watch, ref } from 'vue'
import { ElConfigProvider } from 'element-plus'
import { t, currentLang } from '@/store/lang'

const elLocale = ref<any>(undefined)

async function loadElLocale(locale: string): Promise<void> {
  const langCode = locale.split('-')[0]
  const loaders: Record<string, () => Promise<{ default: unknown }>> = {
    ar: () => import('element-plus/es/locale/lang/ar'),
    bg: () => import('element-plus/es/locale/lang/bg'),
    ca: () => import('element-plus/es/locale/lang/ca'),
    cs: () => import('element-plus/es/locale/lang/cs'),
    da: () => import('element-plus/es/locale/lang/da'),
    de: () => import('element-plus/es/locale/lang/de'),
    el: () => import('element-plus/es/locale/lang/el'),
    en: () => import('element-plus/es/locale/lang/en'),
    es: () => import('element-plus/es/locale/lang/es'),
    fa: () => import('element-plus/es/locale/lang/fa'),
    fi: () => import('element-plus/es/locale/lang/fi'),
    fr: () => import('element-plus/es/locale/lang/fr'),
    hu: () => import('element-plus/es/locale/lang/hu'),
    it: () => import('element-plus/es/locale/lang/it'),
    ja: () => import('element-plus/es/locale/lang/ja'),
    ko: () => import('element-plus/es/locale/lang/ko'),
    lt: () => import('element-plus/es/locale/lang/lt'),
    lv: () => import('element-plus/es/locale/lang/lv'),
    nb: () => import('element-plus/es/locale/lang/nb-no'),
    nl: () => import('element-plus/es/locale/lang/nl'),
    pl: () => import('element-plus/es/locale/lang/pl'),
    pt: () => import('element-plus/es/locale/lang/pt-br'),
    ro: () => import('element-plus/es/locale/lang/ro'),
    ru: () => import('element-plus/es/locale/lang/ru'),
    sl: () => import('element-plus/es/locale/lang/sl'),
    sv: () => import('element-plus/es/locale/lang/sv'),
    tr: () => import('element-plus/es/locale/lang/tr'),
    uk: () => import('element-plus/es/locale/lang/uk'),
    vi: () => import('element-plus/es/locale/lang/vi'),
    zh: () => import('element-plus/es/locale/lang/zh-cn'),
  }
  const loader = loaders[langCode]
  if (loader) {
    try {
      const mod = await loader()
      elLocale.value = mod.default
    } catch {
      elLocale.value = undefined
    }
  } else {
    elLocale.value = undefined
  }
}

watch(currentLang, (lang) => loadElLocale(lang), { immediate: true })

provide('t', t)
</script>

<template>
  <el-config-provider :locale="elLocale">
    <router-view />
  </el-config-provider>
</template>
