<script lang="ts">
import { defineComponent, computed } from 'vue'

interface EnvLabel {
  text: string
  color: string
}

function resolveEnv(): EnvLabel {
  const env =
    (import.meta.env.VITE_APP_ENV as string | undefined) || import.meta.env.MODE
  if (env === 'test' || env === 'testing')
    return { text: '测试环境', color: '#dc2626' }
  if (env === 'development' || env === 'dev')
    return { text: '开发环境', color: '#ea580c' }
  return { text: '生产环境', color: '#6b7280' }
}

export default defineComponent({
  name: 'GlobalWatermark',
  setup() {
    const env = resolveEnv()
    const backgroundImage = computed(() => {
      const svg =
        `<svg xmlns="http://www.w3.org/2000/svg" width="240" height="160" ` +
        `viewBox="0 0 240 160">` +
        `<text x="20" y="90" fill="${env.color}" font-size="20" ` +
        `font-weight="600" font-family="sans-serif" ` +
        `transform="rotate(-22 20 90)" opacity="0.14">${env.text}</text>` +
        `</svg>`
      return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`
    })
    return { backgroundImage }
  },
})
</script>

<template>
  <div class="global-watermark" :style="{ backgroundImage }"></div>
</template>

<style scoped>
.global-watermark {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 9998;
  background-repeat: repeat;
}
</style>