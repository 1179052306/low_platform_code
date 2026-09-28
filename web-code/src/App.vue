<script setup lang="ts">
import { provide, watch, ref } from 'vue'
import { ElConfigProvider } from 'element-plus'
import { t, currentLang } from '@/store/lang'

const elLocale = ref<any>(undefined)

async function loadElLocale(locale: string): Promise<void> {
  const loaders: Record<string, () => Promise<{ default: unknown }>> = {
    'en-US': () => import('element-plus/es/locale/lang/en'),
    'ja-JP': () => import('element-plus/es/locale/lang/ja'),
    'ko-KR': () => import('element-plus/es/locale/lang/ko'),
    'ar-SA': () => import('element-plus/es/locale/lang/ar'),
  }
  const loader = loaders[locale]
  if (loader) {
    try {
      const mod = await loader()
      elLocale.value = mod.default
    } catch {
      // locale load failed, use default
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
