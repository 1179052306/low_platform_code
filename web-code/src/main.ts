import { createApp } from 'vue'

import 'devextreme/dist/css/dx.light.css'
import 'element-plus/dist/index.css'
import 'vxe-pc-ui/lib/style.css'
import 'vxe-table/lib/style.css'
import '@/styles/global.css'
import '@/styles/theme-variables.css'
import '@/styles/dialog-drag.css'

import VxeUI from 'vxe-pc-ui'
import VxeTable from 'vxe-table'
import App from './App.vue'
import router from './router'
import ReportViewer from '@/components/ReportViewer.vue'
import { setupApiGovernance } from '@/api'
import { loadApiRegistry } from '@/store/api-store'
import { initLang } from '@/store/lang'

VxeUI.use(VxeTable as any)

setupApiGovernance()
loadApiRegistry().catch((e) => console.error('[main] loadApiRegistry failed:', e))
initLang().catch((e) => console.error('[main] initLang failed:', e))

const app = createApp(App)
app.component('ReportViewer', ReportViewer)
app.use(router)
app.use(VxeUI as any)

app.mount('#app')
