/**
 * 构造主从表测试页面（模拟设计器保存，触发：建主表+建明细表+元数据同步）
 * 页面：导入主从测试（单据类型）
 * 主表 import_test_main：主键 order_key(UUID 系统生成)，业务字段 order_no(唯一必填)/cust_name
 * 明细表 import_test_detail：data-grid，keyExpr=id(UUID)，外键 order_key 自动添加，列 item_code/qty
 */
const XLSX = require('D:/code/web-code/node_modules/xlsx');

const formNodes = [
  {
    id: 'node_it_1', type: 'el-row', props: { gutter: 0 }, children: [
      {
        id: 'node_it_2', type: 'el-col', props: { span: 24 }, children: [
          {
            id: 'node_it_3', type: 'text-box', dbField: 'order_no', fieldName: 'txtOrderNo',
            props: {
              label: '订单编号', labelVisible: true, labelPosition: 'top',
              validationRules: [
                { type: 'required', message: '订单编号不能为空' },
                { type: 'unique', message: '订单编号已存在' },
              ],
            },
          },
          {
            id: 'node_it_4', type: 'text-box', dbField: 'cust_name', fieldName: 'txtCustName',
            props: { label: '客户名称', labelVisible: true, labelPosition: 'top' },
          },
          {
            id: 'node_it_5', type: 'data-grid', fieldName: 'gridDetail',
            props: {
              tableName: 'import_test_detail',
              tableComment: '订单明细',
              keyExpr: 'id',
              parentType: 'page',
              parentRef: '',
              columns: [
                { dataField: 'item_code', caption: '物料编码', dataType: 'string' },
                { dataField: 'qty', caption: '数量', dataType: 'number' },
              ],
            },
          },
        ],
      },
    ],
  },
];

const schema = {
  pageId: 'import_test',
  pageName: '导入主从测试',
  tableName: 'import_test_main',
  systemId: 'sys',
  moduleId: '',
  pageType: 'document',
  lastBizMode: 'form',
  primaryKeyField: 'order_key',
  keyStrategy: 'UUID',
  enabled: true,
  root: JSON.parse(JSON.stringify(formNodes)),
  bizSchemas: {
    form: JSON.parse(JSON.stringify(formNodes)),
    table: [],
    mobile: [],
  },
};

// 主从导入测试数据（与真实模板同构：区块行+表头行）：2 主单（2/1 明细）+ 1 单号重复 + 1 悬空明细
const importAoa = [
  ['主表：导入主从测试（import_test_main）', '', '明细：订单明细', ''],
  ['*订单编号', '客户名称', '*物料编码', '*数量'],
  ['IT-ORDER-001', '客户甲', 'MAT-A', 10],
  ['', '', 'MAT-B', 20],
  ['IT-ORDER-002', '客户乙', 'MAT-A', 5],
  ['IT-ORDER-001', '客户丙', 'MAT-C', 3],
  ['', '', 'MAT-D', 1],
];

async function main() {
  const saveResp = await fetch('http://127.0.0.1:3015/api/lowcode/page', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(schema),
  }).then(r => r.json());
  console.log('保存页面:', JSON.stringify(saveResp).slice(0, 400));

  const tmplResp = await fetch('http://127.0.0.1:3015/api/lowcode/import/template?bizCode=import_test_main');
  const tmplBuf = Buffer.from(await tmplResp.arrayBuffer());
  require('fs').writeFileSync('D:/code/docs/import-design/samples/test-md-template.xlsx', tmplBuf);
  console.log('模板下载:', tmplResp.status, tmplBuf.length, 'bytes');
  const wb = XLSX.read(tmplBuf);
  console.log('模板 Sheets:', wb.SheetNames.join(' | '));
  const rows = XLSX.utils.sheet_to_json(wb.Sheets['数据导入'], { header: 1 });
  rows.slice(0, 2).forEach((r, i) => console.log('模板行' + (i + 1) + ':', (r || []).join(' | ')));

  const ws = XLSX.utils.aoa_to_sheet(importAoa);
  const wb2 = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb2, ws, '数据导入');
  XLSX.writeFile(wb2, 'D:/code/docs/import-design/samples/test-md-import.xlsx');
  console.log('导入数据文件已生成（2合法主单+1单号Excel内重复+1悬空明细行）');
}

main().catch(e => console.error('ERR:', e.message));