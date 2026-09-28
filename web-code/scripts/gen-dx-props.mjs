/**
 * 自动从 devextreme-vue 类型声明中提取每个组件的全量可配置属性。
 * 输出 src/materials/dx-prop-meta.ts，供属性面板「全部属性」页签使用。
 *
 * 用法：node scripts/gen-dx-props.mjs
 */
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const cjsDir = join(root, 'node_modules/devextreme-vue/cjs')
const dxRoot = join(root, 'node_modules/devextreme')
const dxMaterialsPath = join(root, 'src/materials/dx-materials.ts')

/** 1. 从 dx-materials.ts 解析 物料type -> DX模块名 的映射 */
const src = readFileSync(dxMaterialsPath, 'utf8')
const varToModule = {}
for (const m of src.matchAll(/import\s+(\w+)\s+from\s+'devextreme-vue\/([\w-]+)'/g)) {
  varToModule[m[1]] = m[2]
}
const typeToModule = {}
for (const m of src.matchAll(/^\s*'([\w-]+)':\s*(\w+)\s+as\s+Component/gm)) {
  const mod = varToModule[m[2]]
  if (mod) typeToModule[m[1]] = mod
}

/** 跳过的属性：事件回调、插槽模板、内部实例引用 */
function isSkipped(name) {
  return /^on[A-Z]/.test(name) || name === 'template' || name === 'instance'
}

/** 递归收集 devextreme 官方类型声明文件 */
function walkDts(dir) {
  const files = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, entry.name)
    if (entry.isDirectory()) files.push(...walkDts(p))
    else if (entry.isFile() && entry.name.endsWith('.d.ts')) files.push(p)
  }
  return files
}

/** 从 devextreme 官方 .d.ts 中提取「字符串枚举」类型（name -> 可选值数组） */
function buildEnumMap() {
  const map = new Map()
  const re = /export\s+type\s+([A-Za-z_$][\w$]*)\s*=\s*([^;]+);/gs
  for (const file of walkDts(dxRoot)) {
    const text = readFileSync(file, 'utf8')
    let m
    while ((m = re.exec(text)) !== null) {
      const name = m[1]
      const body = m[2].replace(/\s+/g, ' ')
      const literals = [...body.matchAll(/'([^']*)'/g)].map((x) => x[1])
      if (literals.length >= 2 && literals.every((v) => /^[a-zA-Z0-9_.-]+$/.test(v))) {
        map.set(name, [...new Set([...(map.get(name) || []), ...literals])])
      }
    }
  }
  return map
}

const enumMap = buildEnumMap()

/** 若类型是字符串枚举（命名枚举或内联字符串字面量联合），返回可选值 */
function resolveEnumOptions(inner0) {
  const inner = inner0.replace(/\s*\|\s*(?:undefined|null)\b/g, '').trim()
  // 含函数、泛型、数组等复杂类型时，不作为枚举处理
  if (!inner || inner.includes('=>') || /[<>[\]]/.test(inner)) return null
  const literals = [...inner.matchAll(/'([^']+)'/g)].map((m) => m[1])
  const tokens = inner.split('|').map((s) => s.trim()).filter(Boolean)
  const options = new Set(literals)
  for (const token of tokens) {
    if (/^'[^']*'$/.test(token)) continue
    if (/^[A-Za-z_$][\w$]*$/.test(token) && enumMap.has(token)) {
      for (const v of enumMap.get(token)) options.add(v)
    } else {
      return null
    }
  }
  const arr = [...options]
  return arr.length ? arr : null
}

/** 去除箭头函数片段，用于区分“纯回调”与“含函数联合的数据类型” */
function stripFunctions(inner) {
  let s = inner
  let prev = ''
  while (s !== prev) {
    prev = s
    // (() => T) 与 (args) => T（含 () => number | string 这类联合返回值）
    s = s
      .replace(/\(\(\)\s*=>\s*[^()]*\)/g, '')
      .replace(/\([^()]*\)\s*=>\s*[^()]*/g, '')
  }
  return s
}

/** 根据类型声明推断编辑器类型 */
function classify(type) {
  const t = type.replace(/;\s*$/, '').trim()
  if (t === 'StringConstructor') return { kind: 'string' }
  if (t === 'NumberConstructor') return { kind: 'number' }
  if (t === 'BooleanConstructor') return { kind: 'boolean' }
  // (DateConstructor | NumberConstructor | StringConstructor)[] 等日期/数字联合 → 文本输入
  if (/^(\([^()]*\)|[\w ]+)(\[\])?$/.test(t) && /Constructor/.test(t)) return { kind: 'string' }

  const m = t.match(/PropType<([\s\S]+)>\s*$/)
  if (!m) return { kind: 'json' }
  const options = resolveEnumOptions(m[1])
  if (options) return { kind: 'select', options }
  // 日期/数字联合（string | number | Date | null）以字符串形式编辑
  const inner = m[1]
    .trim()
    .replace(/\s*\|\s*undefined/g, '')
    .replace(/\s*\|\s*Date\b/g, '')
    .replace(/\s*\|\s*null\b/g, '')
  // 纯回调函数 → 跳过
  const stripped = stripFunctions(inner).trim()
  if (!stripped || /^[\s|]+$/.test(stripped)) return null
  if (stripped === 'boolean') return { kind: 'boolean' }
  if (stripped === 'string') return { kind: 'string' }
  if (stripped === 'number') return { kind: 'number' }
  // 简单联合：仅由 string / number 组成 → 文本输入（DX 均接受字符串）
  if (/^(string|number|\||\s)+$/.test(stripped)) {
    return { kind: stripped.includes('string') ? 'string' : 'number' }
  }
  // 其余（对象、数组、枚举联合等）统一 JSON 编辑
  return { kind: 'json' }
}

/** 解析单个 d.ts 文件，返回主组件 props 列表（忽略文件内后续子组件声明） */
function parseDts(file) {
  const text = readFileSync(file, 'utf8')
  const marker = 'DefineComponent<{'
  const start = text.indexOf(marker)
  if (start === -1) return []

  // 只截取第一个 DefineComponent 的 props 对象（花括号平衡扫描）
  const body = text.slice(start + marker.length)
  let depth = 1
  let end = 0
  for (; end < body.length; end++) {
    const ch = body[end]
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) break
    }
  }
  const propsBlock = body.slice(0, end)

  const props = []
  let current = null
  let braceDepth = 0
  let parenDepth = 0
  for (const line of propsBlock.split('\n')) {
    const nameMatch = line.match(/^ {4}(\w+):\s*(.*)$/)
    if (!current && nameMatch && braceDepth === 0 && parenDepth === 0) {
      current = { name: nameMatch[1], type: nameMatch[2] }
    } else if (current) {
      current.type += ' ' + line.trim()
    }
    braceDepth += (line.match(/{/g) || []).length - (line.match(/}/g) || []).length
    parenDepth += (line.match(/\(/g) || []).length - (line.match(/\)/g) || []).length
    if (current && braceDepth === 0 && parenDepth === 0 && line.trimEnd().endsWith(';')) {
      props.push(current)
      current = null
    }
  }

  const result = []
  const seen = new Set()
  for (const p of props) {
    if (isSkipped(p.name)) continue
    const classified = classify(p.type)
    if (!classified) continue
    if (seen.has(p.name)) continue
    seen.add(p.name)
    result.push({
      name: p.name,
      kind: classified.kind,
      ...(classified.options ? { options: classified.options.map((v) => ({ label: v, value: v })) } : {}),
    })
  }
  return result
}

/** 2. 生成元数据 */
const meta = {}
let total = 0
let missing = []
for (const [type, mod] of Object.entries(typeToModule)) {
  const file = join(cjsDir, `${mod}.d.ts`)
  if (!existsSync(file)) {
    missing.push(`${type} -> ${mod}.d.ts`)
    continue
  }
  const props = parseDts(file)
  if (props.length) {
    meta[type] = props
    total += props.length
  }
}

/** 3. 输出 TS 文件 */
const out = [
  '/**',
  ' * 由 scripts/gen-dx-props.mjs 自动生成，请勿手动编辑。',
  ' * 每个 DevExtreme 物料的全量可配置属性（不含事件回调）。',
  ' */',
  "export type DxPropKind = 'string' | 'number' | 'boolean' | 'select' | 'json'",
  '',
  'export interface DxPropMeta {',
  '  name: string',
  '  kind: DxPropKind',
  '  options?: { label: string; value: string }[]',
  '}',
  '',
  'export const dxPropMeta: Record<string, DxPropMeta[]> = ' +
    JSON.stringify(meta, null, 2).replace(/"name": /g, 'name: ').replace(/"kind": /g, 'kind: ').replace(/"(\w+)": \[/g, "'$1': ["),
  '',
]
writeFileSync(join(root, 'src/materials/dx-prop-meta.ts'), out.join('\n'), 'utf8')

console.log(`组件数: ${Object.keys(meta).length}, 属性总数: ${total}`)
if (missing.length) console.log('缺失 d.ts:', missing.join(', '))
