<template>
  <div
    :ref="id"
    :key="key"
    style="height: 100%"
    data-bind="dxReportViewer: $data"
  ></div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import ko from 'knockout'
import 'devexpress-reporting/dx-webdocumentviewer'
import {
  ActionId,
  PreviewElements,
} from 'devexpress-reporting/dx-webdocumentviewer'
import { REPORT_CONFIG } from '@/constants/report-config'

// 报表设置项结构（与后端 reportUrl 序列化内容对应）
interface ReportSeting {
  reportName?: string
  reportType?: number
  loadType?: number
  path?: string
  data?: unknown
  fileName?: string
  column?: Record<string, unknown>
  tenant?: string | null
}

// CustomizeElements 回调的 elements 集合参数类型
interface CustomizeElementsArg {
  GetById: (id: string) => unknown
  Elements: unknown[]
}

// CustomizeMenuActions 回调的 actions 集合参数类型
interface CustomizeMenuActionsArg {
  GetById: (id: string) => { visible: boolean } | undefined
}

export default defineComponent({
  name: 'ReportViewer',
  props: {
    seting: {
      type: Object as () => ReportSeting,
    },
  },
  data() {
    return {
      contentheight: '0px',
      // 使用原生 crypto.randomUUID 生成唯一标识，替代原 this.DevCommon.guid()
      id: crypto.randomUUID(),
      key: crypto.randomUUID(),
      reportSeting: {
        reportName: 'default',
        reportType: 1,
        loadType: 1,
        fileName: '',
        column: {},
      } as ReportSeting,
    }
  },
  created() {
    this.setData(this.seting)
  },
  methods: {
    async setData(seting: ReportSeting | undefined): Promise<void> {
      if (!seting) return
      this.reportSeting = {
        reportName: seting.reportName ?? this.reportSeting.reportName,
        reportType: seting.reportType ?? this.reportSeting.reportType,
        loadType: seting.loadType ?? this.reportSeting.loadType,
        path: seting.path ?? this.reportSeting.path,
        data: seting.data ?? this.reportSeting.data,
        fileName: seting.fileName ?? this.reportSeting.fileName,
        tenant: localStorage.getItem('tenant'),
      }
    },
  },
  mounted() {
    const viewerOptions = {
      reportUrl: JSON.stringify(this.reportSeting),
      requestOptions: {
        host: REPORT_CONFIG.reportUrl,
        invokeAction: REPORT_CONFIG.invokeAction,
      },
      exportSettings: {
        showPrintNotificationDialog: false,
      },
      callbacks: {
        CustomizeElements(_s: unknown, e: CustomizeElementsArg): void {
          const panelPart = e.GetById(PreviewElements.RightPanel)
          const index = e.Elements.indexOf(panelPart)
          e.Elements.splice(index, 1)
        },
        CustomizeMenuActions(_s: unknown, e: CustomizeMenuActionsArg): void {
          const actionPrevPage = e.GetById(ActionId.Search)
          if (actionPrevPage) actionPrevPage.visible = false
          const exporto = e.GetById(ActionId.ExportTo)
          if (exporto) exporto.visible = false
          const fullscreen = e.GetById(ActionId.FullScreen)
          if (fullscreen) fullscreen.visible = false
          const multipage = e.GetById(ActionId.MultipageToggle)
          if (multipage) multipage.visible = false
          const zoomselect = e.GetById(ActionId.ZoomSelector)
          if (zoomselect) zoomselect.visible = false
        },
      },
    }
    // 动态 ref 在 Vue3 下通过 this.$refs[id] 访问，断言为 HTMLElement 供 knockout 使用
    const node = this.$refs[this.id] as HTMLElement | undefined
    if (node) {
      ko.applyBindings(viewerOptions, node)
    }
  },
  beforeUnmount() {
    const node = this.$refs[this.id] as HTMLElement | undefined
    if (node) {
      ko.cleanNode(node)
    }
  },
})
</script>

<style>
.fromClass {
  overflow-y: auto;
}
.fromClass::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}
</style>