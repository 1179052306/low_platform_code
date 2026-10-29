const XLSX = require('D:/code/web-code/node_modules/xlsx');
const path = 'D:/code/docs/import-design/samples/test-template.xlsx';
const wb = XLSX.readFile(path);
console.log('Sheets:', wb.SheetNames.join(' | '));
for (const name of wb.SheetNames) {
  const ws = wb.Sheets[name];
  const rows = XLSX.utils.sheet_to_json(ws, { header: 1 });
  console.log(`\n===== ${name} (${rows.length} 行) =====`);
  for (let i = 0; i < Math.min(rows.length, 12); i++) {
    const cells = (rows[i] || []).map((c) => (c === undefined ? '' : String(c))).slice(0, 8);
    if (cells.some((c) => c !== '')) console.log(`行${i + 1}: ${cells.join(' | ')}`);
  }
}