const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel,
  Table, TableRow, TableCell, WidthType, AlignmentType,
  BorderStyle, ExternalHyperlink, ShadingType, LevelFormat,
} = require('docx');

const inputPath = process.argv[2];
const outputPath = process.argv[3];
if (!inputPath || !outputPath) {
  console.error('用法: node convert.cjs <input.md> <output.docx>');
  process.exit(1);
}

const md = fs.readFileSync(inputPath, 'utf8');
const lines = md.split(/\r?\n/);

const CODE_FONT = 'Consolas';
const CODE_FILL = 'F5F5F5';
const TABLE_HEAD_FILL = 'D9E2F3';
const QUOTE_FILL = 'EFEFEF';

function parseInline(text) {
  const runs = [];
  const regex = /(\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\))/g;
  let last = 0;
  let m;
  while ((m = regex.exec(text)) !== null) {
    if (m.index > last) {
      runs.push(new TextRun({ text: text.slice(last, m.index) }));
    }
    if (m[2] !== undefined) {
      runs.push(new TextRun({ text: m[2], bold: true }));
    } else if (m[3] !== undefined) {
      runs.push(new TextRun({ text: m[3], font: CODE_FONT, shading: { type: ShadingType.SOLID, color: 'auto', fill: 'ECECEC' } }));
    } else if (m[4] !== undefined) {
      runs.push(new ExternalHyperlink({
        children: [new TextRun({ text: m[4], color: '0563C1', underline: {} })],
        link: m[5],
      }));
    }
    last = regex.lastIndex;
  }
  if (last < text.length) {
    runs.push(new TextRun({ text: text.slice(last) }));
  }
  if (runs.length === 0) runs.push(new TextRun({ text: '' }));
  return runs;
}

function splitTableRow(line) {
  return line.split('|').slice(1, -1).map(c => c.trim());
}

function makeTableCell(text, isHeader) {
  return new TableCell({
    children: [new Paragraph({
      children: parseInline(text),
      spacing: { before: 40, after: 40 },
    })],
    shading: isHeader ? { type: ShadingType.SOLID, color: 'auto', fill: TABLE_HEAD_FILL } : undefined,
  });
}

const children = [];
let i = 0;
while (i < lines.length) {
  const line = lines[i];

  // 代码块
  if (line.trim().startsWith('```')) {
    const codeLines = [];
    i++;
    while (i < lines.length && !lines[i].trim().startsWith('```')) {
      codeLines.push(lines[i]);
      i++;
    }
    i++; // 跳过结束 ```
    for (const cl of codeLines) {
      children.push(new Paragraph({
        children: [new TextRun({ text: cl || ' ', font: CODE_FONT, size: 18 })],
        shading: { type: ShadingType.SOLID, color: 'auto', fill: CODE_FILL },
        spacing: { before: 0, after: 0 },
        indent: { left: 200 },
      }));
    }
    children.push(new Paragraph({ children: [new TextRun({ text: '' })] }));
    continue;
  }

  // 标题
  const h = line.match(/^(#{1,6})\s+(.*)$/);
  if (h) {
    const level = h[1].length;
    const content = h[2].replace(/\s+$/, '');
    const headingMap = {
      1: HeadingLevel.HEADING_1, 2: HeadingLevel.HEADING_2, 3: HeadingLevel.HEADING_3,
      4: HeadingLevel.HEADING_4, 5: HeadingLevel.HEADING_5, 6: HeadingLevel.HEADING_6,
    };
    children.push(new Paragraph({
      heading: headingMap[level],
      children: [new TextRun({ text: content, bold: true, font: '微软雅黑' })],
    }));
    i++;
    continue;
  }

  // 表格
  if (line.startsWith('|') && i + 1 < lines.length && /^\|[-:\s|]+\|/.test(lines[i + 1])) {
    const headerCells = splitTableRow(line);
    i += 2; // 跳过表头和分隔行
    const dataRows = [];
    while (i < lines.length && lines[i].startsWith('|')) {
      dataRows.push(splitTableRow(lines[i]));
      i++;
    }
    const rows = [];
    rows.push(new TableRow({
      children: headerCells.map(c => makeTableCell(c, true)),
      tableHeader: true,
    }));
    for (const r of dataRows) {
      rows.push(new TableRow({
        children: r.map(c => makeTableCell(c, false)),
      }));
    }
    children.push(new Table({
      width: { size: 100, type: WidthType.PERCENTAGE },
      rows,
    }));
    children.push(new Paragraph({ children: [new TextRun({ text: '' })] }));
    continue;
  }

  // 水平线
  if (/^---+\s*$/.test(line)) {
    children.push(new Paragraph({
      children: [new TextRun({ text: '' })],
      border: { bottom: { color: 'BFBFBF', space: 1, style: BorderStyle.SINGLE, size: 6 } },
    }));
    i++;
    continue;
  }

  // 引用
  if (line.startsWith('> ')) {
    const quoteLines = [];
    while (i < lines.length && lines[i].startsWith('> ')) {
      quoteLines.push(lines[i].slice(2));
      i++;
    }
    for (const ql of quoteLines) {
      children.push(new Paragraph({
        children: parseInline(ql),
        indent: { left: 360 },
        shading: { type: ShadingType.SOLID, color: 'auto', fill: QUOTE_FILL },
        border: { left: { color: '808080', space: 8, style: BorderStyle.SINGLE, size: 12 } },
        spacing: { before: 40, after: 40 },
      }));
    }
    continue;
  }

  // 无序列表
  if (/^\s*[-*]\s+/.test(line)) {
    while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
      const indent = (lines[i].match(/^\s*/)[0].length) / 2;
      const content = lines[i].replace(/^\s*[-*]\s+/, '');
      children.push(new Paragraph({
        children: parseInline(content),
        bullet: { level: Math.min(indent, 2) },
      }));
      i++;
    }
    continue;
  }

  // 有序列表
  if (/^\s*\d+\.\s+/.test(line)) {
    while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) {
      const content = lines[i].replace(/^\s*\d+\.\s+/, '');
      children.push(new Paragraph({
        children: parseInline(content),
        numbering: { reference: 'num', level: 0 },
      }));
      i++;
    }
    continue;
  }

  // 空行
  if (line.trim() === '') {
    children.push(new Paragraph({ children: [new TextRun({ text: '' })] }));
    i++;
    continue;
  }

  // 普通段落
  children.push(new Paragraph({
    children: parseInline(line),
    spacing: { before: 40, after: 40 },
  }));
  i++;
}

const doc = new Document({
  creator: 'CodeArts',
  title: '低代码通用后台与插件架构设计文档',
  styles: {
    default: {
      document: { run: { font: '宋体', size: 21 } },
      heading1: { run: { font: '微软雅黑', size: 36, bold: true, color: '1F4E79' } },
      heading2: { run: { font: '微软雅黑', size: 30, bold: true, color: '2E74B5' } },
      heading3: { run: { font: '微软雅黑', size: 26, bold: true, color: '2E74B5' } },
      heading4: { run: { font: '微软雅黑', size: 22, bold: true, color: '5B9BD5' } },
      heading5: { run: { font: '微软雅黑', size: 21, bold: true } },
      heading6: { run: { font: '微软雅黑', size: 21, bold: true } },
    },
  },
  numbering: {
    config: [{
      reference: 'num',
      levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.START }],
    }],
  },
  sections: [{ properties: {}, children }],
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(outputPath, buf);
  console.log('生成成功: ' + outputPath + ' (' + buf.length + ' bytes)');
}).catch(e => {
  console.error('生成失败:', e.message);
  process.exit(1);
});