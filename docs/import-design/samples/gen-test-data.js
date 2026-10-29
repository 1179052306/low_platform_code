const XLSX = require('D:/code/web-code/node_modules/xlsx');
const aoa = [
  ['ORG_ID', 'ORG_NAME', 'ACTVIE', 'dates', 'nums'],
  ['TEST_IMP_001', '导入测试组织一', 'true', '2026-10-21', 100],
  ['TEST_IMP_002', '导入测试组织二', 'true', '2026-10-21', 200],
  ['TEST_IMP_003', '导入测试组织三', 'false', '', 300],
  ['TEST_IMP_001', '重复编码组织', 'true', '', 400],
  ['', '缺少编码的组织', 'true', '', 500],
];
const ws = XLSX.utils.aoa_to_sheet(aoa);
const wb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(wb, ws, '数据导入');
XLSX.writeFile(wb, 'D:/code/docs/import-design/samples/test-import-data.xlsx');
console.log('测试数据文件已生成（含 1 条 Excel 内重复 + 1 条必填为空行）');