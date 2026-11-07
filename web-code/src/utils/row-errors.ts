import { ElMessageBox } from 'element-plus'

interface ResRow {
  res: boolean
  data: string
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export async function showRowErrors(rowResults: ResRow[]): Promise<void> {
  const rows = rowResults
    .map(
      (r, i) =>
        `<tr><td style="padding:8px 12px;border:1px solid #ebeef5;width:60px;text-align:center;">${i + 1}</td><td style="padding:8px 12px;border:1px solid #ebeef5;color:#f56c6c;">${escapeHtml(r.data || '')}</td></tr>`,
    )
    .join('')
  const html =
    `<div style="max-height:400px;overflow-y:auto;margin:-10px 0;">` +
    `<table style="width:100%;border-collapse:collapse;font-size:14px;">` +
    `<thead><tr><th style="padding:8px 12px;border:1px solid #ebeef5;background:#f5f7fa;width:60px;">序号</th>` +
    `<th style="padding:8px 12px;border:1px solid #ebeef5;background:#f5f7fa;text-align:left;">错误信息</th></tr></thead>` +
    `<tbody>${rows}</tbody></table></div>`

  await ElMessageBox.alert(html, '错误详情', {
    confirmButtonText: '关闭',
    dangerouslyUseHTMLString: true,
    customClass: 'row-errors-dialog',
    type: 'error',
  })
}