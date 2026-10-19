// 批量替换所有文件中的物料 type 字符串：'dx-xxx' → 'xxx'
// 只替换引号内的物料 type 字符串，不影响 import 路径 / CSS 类名 / 组件名
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..', 'src')

// 所有需要替换的物料 type（dx- 前缀 → 去前缀）
const TYPES = [
  'dx-chart', 'dx-pie-chart', 'dx-polar-chart', 'dx-bar-gauge', 'dx-bullet',
  'dx-circular-gauge', 'dx-linear-gauge', 'dx-funnel', 'dx-sankey', 'dx-sparkline',
  'dx-tree-map', 'dx-range-selector', 'dx-data-grid', 'dx-tree-list', 'dx-list',
  'dx-tree-view', 'dx-pivot-grid', 'dx-pivot-field-chooser', 'dx-filter-builder',
  'dx-scheduler', 'dx-gantt', 'dx-file-manager', 'dx-text-box', 'dx-text-area',
  'dx-number-box', 'dx-check-box', 'dx-switch', 'dx-slider', 'dx-range-slider',
  'dx-calendar', 'dx-date-box', 'dx-date-range-box', 'dx-color-box', 'dx-select-box',
  'dx-lookup', 'dx-tag-box', 'dx-autocomplete', 'dx-radio-group', 'dx-drop-down-button',
  'dx-drop-down-box', 'dx-file-uploader', 'dx-html-editor', 'dx-toolbar', 'dx-gallery',
  'dx-speed-dial-action', 'dx-button-group', 'dx-button', 'dx-progress-bar',
  'dx-load-indicator', 'dx-popup', 'dx-toast', 'dx-load-panel', 'dx-base-data',
  'dx-check-box-group',
]

// 需要处理的文件（含 .ts / .vue）
const FILES = [
  'materials/dx-materials.ts',
  'materials/dx-validation.ts',
  'materials/dx-prop-meta.ts',
  'materials/dx-events.ts',
  'materials/index.ts',
  'components/renderer/RendererNode.vue',
  'components/designer/CanvasNode.vue',
  'views/DesignerView.vue',
]

let totalReplaced = 0

for (const rel of FILES) {
  const filePath = path.join(ROOT, rel)
  if (!fs.existsSync(filePath)) {
    console.log(`SKIP (not found): ${rel}`)
    continue
  }
  let content = fs.readFileSync(filePath, 'utf8')
  let count = 0
  for (const oldType of TYPES) {
    const newType = oldType.replace(/^dx-/, '')
    // 匹配引号内的 type 字符串：'dx-xxx' 或 "dx-xxx"
    const singleRe = new RegExp(`'${oldType}'`, 'g')
    const doubleRe = new RegExp(`"${oldType}"`, 'g')
    const singleMatches = (content.match(singleRe) || []).length
    const doubleMatches = (content.match(doubleRe) || []).length
    if (singleMatches + doubleMatches > 0) {
      content = content.replace(singleRe, `'${newType}'`).replace(doubleRe, `"${newType}"`)
      count += singleMatches + doubleMatches
    }
  }
  if (count > 0) {
    fs.writeFileSync(filePath, content, 'utf8')
    console.log(`${rel}: ${count} replacements`)
    totalReplaced += count
  }
}

console.log(`\nTotal: ${totalReplaced} replacements across ${FILES.length} files`)