import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    open: false,
    proxy: {
      // P1 分流：认证/系统管理走网关(8000)，其余低代码接口暂直连 bcd-code(3015)
      // P3 权限闭环后可全量切 8000
      '/api/auth': {
        target: 'http://localhost:7000',
        changeOrigin: true,
      },
      '/api/sys': {
        target: 'http://localhost:7000',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:3015',
        changeOrigin: true,
      },
    },
  },
  // build: {
  //   chunkSizeWarningLimit: 2000,
  //   rollupOptions: {
  //     output: {
  //       manualChunks(id: string) {
  //         if (!id.includes('node_modules')) return undefined
  //         if (id.includes('element-plus')) return 'element-plus'
  //         if (id.includes('devextreme-vue')) return 'devextreme-vue'
  //         if (id.includes('devextreme')) return 'devextreme'
  //         if (id.includes('vue') || id.includes('@vue')) return 'vue'
  //         return 'vendor'
  //       },
  //     },
  //   },
  // },
})
