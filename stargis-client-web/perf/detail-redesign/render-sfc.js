/**
 * 从真实 SFC 生成一个「等价于浏览器运行时」的静态页面：
 *   - 用 less 编译 <style scoped>，按 vue-loader 的 postcss 规则给选择器加 [data-v-xxx]
 *   - 给每个模板元素加同名属性
 * 然后截图，确认在生产等效的 CSS 下版面是对的。
 * 用法：node perf/detail-redesign/render-sfc.js
 */
const fs = require('fs')
const path = require('path')
const compiler = require('vue-template-compiler')
const less = require('less')

const root = path.resolve(__dirname, '../..')
const SCOPE = 'data-v-7ba5bd90'

const MIXINS = {
  '.screen-placeholder': 'color: var(--screen-text-mute);',
}

function scopeSelectors (css) {
  // 先去注释（真实 vue-loader 走 AST，注释文本不会被当选择器）
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, '')
  let out = ''
  let i = 0
  const addScope = (sel) => sel.split(',').map((s) => {
    const t = s.trim()
    if (!t) return t
    // 伪元素 / 伪类挂到最后一段
    return t.replace(/(::?[a-z-]+(\([^)]*\))?)*$/, (m) => `[${SCOPE}]${m}`)
  }).join(', ')

  while (i < clean.length) {
    const open = clean.indexOf('{', i)
    if (open === -1) { out += clean.slice(i); break }
    const head = clean.slice(i, open)
    const isAtRule = /^\s*@/.test(head)
    out += isAtRule ? head : addScope(head) + ' '
    // 找匹配的右括号
    let depth = 1
    let j = open + 1
    while (j < clean.length && depth > 0) {
      if (clean[j] === '{') depth += 1
      else if (clean[j] === '}') depth -= 1
      j += 1
    }
    const body = clean.slice(open, j)
    if (isAtRule && /^@(media|supports)/.test(head.trim())) {
      out += '{' + scopeSelectors(body.slice(1, -1)) + '}'
    } else {
      out += body
    }
    i = j
  }
  return out
}

function buildMarkup (items, props) {
  // 行内样式单独传，别塞进 attrs 形成重复属性
  const sc = ` ${SCOPE}`
  const attrs = props.style ? ` style="${props.style}"` : ''
  const rows = items.map((it) => {
    const cellCls = ['screen-descriptions__item']
    if (it.stack) cellCls.push('is-stack')
    if (it.rowEnd) cellCls.push('is-row-end')
    const dtCls = 'screen-descriptions__label' + (it.stack ? ' is-stack' : '')
    const ddCls = 'screen-descriptions__value' + (it.stack ? ' is-stack' : '') + (it.tone ? ' is-' + it.tone : '') + (it.empty ? ' is-empty' : '')
    // ★ 标签文字包一层 span（组件里就是这么做省略号的），否则量出来的省略行为是假的
    return `<div class="${cellCls.join(' ')}"${sc}><dt class="${dtCls}"${sc}>` +
      `<span class="screen-descriptions__label-text"${sc}>${it.label}</span></dt>` +
      `<dd class="${ddCls}"${sc}>${it.value}</dd></div>`
  }).join('\n      ')
  // 注意：dl 的 class 只能出现一次，否则浏览器会丢掉前一个（这里踩过坑）
  return `<dl class="screen-descriptions ${props.dlClass || ''}"${sc}${attrs}>\n      ${rows}\n  </dl>`
}

async function main () {
  const file = path.join(root, 'src/components/screen/ScreenDescriptions.vue')
  const sfc = compiler.parseComponent(fs.readFileSync(file, 'utf8'))

  let raw = sfc.styles[0].content.replace(/@import[^;]+;/g, '')
  Object.keys(MIXINS).forEach((k) => {
    raw = raw.replace(new RegExp(k.replace('.', '\\.') + '\\(\\);', 'g'), MIXINS[k])
  })
  // .screen-ellipsis() 的实际内容
  raw = raw.replace(/\.screen-ellipsis\(\);/g, 'overflow: hidden; white-space: nowrap; text-overflow: ellipsis;')

  const compiled = await less.render(raw, { filename: file })
  const scoped = scopeSelectors(compiled.css)

  // 令牌（从 screen-tokens.less 抄关键项）
  const tokens = `
:root{
  --screen-panel-bg:rgba(8,22,42,.92); --screen-panel-bg-solid:#08182e;
  --screen-row-alt:rgba(55,132,215,.08);
  --screen-border:rgba(55,132,215,.32); --screen-border-soft:rgba(55,132,215,.18);
  --screen-border-strong:rgba(130,198,255,.62);
  --screen-accent:#82c6ff; --screen-accent-soft:#66afd4; --screen-accent-deep:#1d335d;
  --screen-text:#fff; --screen-text-sub:#66afd4; --screen-text-mute:#4a7396;
  --screen-radius-sm:3px; --screen-radius:4px; --screen-radius-pill:999px;
  --screen-space-1:4px; --screen-space-2:8px; --screen-space-3:12px; --screen-space-4:16px;
  --screen-font-xs:12px; --screen-font-sm:13px; --screen-font-md:14px;
  --screen-font-family:'Source Han Sans SC','PingFang SC','Microsoft YaHei',-apple-system,sans-serif;
  --screen-font-number-family:var(--screen-font-family);
}
*{box-sizing:border-box}
body{margin:0;padding:20px;background:#04091a;color:var(--screen-text);font-family:var(--screen-font-family)}
h3{font:12px sans-serif;color:#82c6ff;margin:20px 0 8px}
h3:first-child{margin-top:0}
.card{padding:12px 16px 8px;background:rgba(8,22,42,.92);border:1px solid var(--screen-border-soft);border-radius:4px}
`

  const groups = [
    {
      title: 'A. flat + columns=4（提级论证 / 档案详情用）—— 也就是出问题的那种',
      dlClass: 'is-md is-flat',
      props: { style: '--sd-input-cols:4;--sd-label-w:112px' },
      items: [
        { label: '档案号', value: 'DA-2025-0001', tone: 'accent', grid: 'grid-column-start:1;grid-column-end:span 2' },
        { label: '档案类型', value: '电子档案', grid: 'grid-column-start:3;grid-column-end:span 2' },
        { label: '密级', value: '一般', grid: 'grid-column-start:5;grid-column-end:span 2' },
        { label: '保管期限', value: '永久', rowEnd: true, grid: 'grid-column-start:7;grid-column-end:span 2' },
        { label: '档案年度', value: '2025', tone: 'number', grid: 'grid-column-start:1;grid-column-end:span 2' },
        { label: '归档日期', value: '2025-09-19', tone: 'number', grid: 'grid-column-start:3;grid-column-end:span 2' },
        { label: '责任部门', value: '滨海新区住建委', grid: 'grid-column-start:5;grid-column-end:span 2' },
        { label: '配套负责人', value: '管理员', rowEnd: true, grid: 'grid-column-start:7;grid-column-end:span 2' },
        { label: '档案名称', value: '滨海新区智慧产业园项目档案', stack: true, grid: 'grid-column-start:1;grid-column-end:-1' },
        { label: '档案类别', value: '项目立项文件、规划许可文件、施工许可文件', stack: true, rowEnd: true, grid: 'grid-column-start:1;grid-column-end:-1' }
      ]
    },
    {
      title: 'B. flat + columns=3 + 空值（论证结果与办理状态）',
      dlClass: 'is-md is-flat',
      props: { style: '--sd-input-cols:3;--sd-label-w:112px' },
      items: [
        { label: '办理状态', value: '未办理', grid: 'grid-column-start:1;grid-column-end:span 2' },
        { label: '论证结果', value: '—', empty: true, grid: 'grid-column-start:3;grid-column-end:span 2' },
        { label: '论证组织单位', value: '—', empty: true, rowEnd: true, grid: 'grid-column-start:5;grid-column-end:span 2' },
        { label: '材料数 / 意见记录数', value: '2 个 / 0 条', tone: 'number', rowEnd: true, grid: 'grid-column-start:1;grid-column-end:span 2' }
      ]
    },
    {
      title: 'C. bordered 默认（其余页面在用）+ columns=3',
      dlClass: 'is-md is-bordered',
      props: { style: '--sd-input-cols:3;--sd-label-w:96px' },
      items: [
        { label: '类别名称', value: '项目立项文件', grid: 'grid-column-start:1;grid-column-end:span 2' },
        { label: '类别编码', value: 'CAT-001', tone: 'accent', grid: 'grid-column-start:3;grid-column-end:span 2' },
        { label: '状态', value: '启用', rowEnd: true, grid: 'grid-column-start:5;grid-column-end:span 2' },
        { label: '排序号', value: '3', tone: 'number', grid: 'grid-column-start:1;grid-column-end:span 2' },
        { label: '子类别数', value: '2', tone: 'number', grid: 'grid-column-start:3;grid-column-end:span 2' },
        { label: '是否末级', value: '否', rowEnd: true, grid: 'grid-column-start:5;grid-column-end:span 2' },
        { label: '完整路径', value: '项目档案 / 项目立项文件 / 可行性研究报告', stack: true, grid: 'grid-column-start:1;grid-column-end:-1' },
        { label: '备注', value: '本级及以下子类别均可挂接卷内文件。', stack: true, rowEnd: true, grid: 'grid-column-start:1;grid-column-end:-1' }
      ]
    },
    {
      title: 'D. bordered + size=sm（审批页）+ columns=2',
      dlClass: 'is-sm is-bordered',
      props: { style: '--sd-input-cols:2;--sd-label-w:104px' },
      items: [
        { label: '项目编号', value: 'TJ-2026-DEMO01', tone: 'accent', grid: 'grid-column-start:1;grid-column-end:span 2' },
        { label: '项目名称', value: '滨海新区智慧产业园项目', rowEnd: true, grid: 'grid-column-start:3;grid-column-end:span 2' },
        { label: '办理状态', value: '未办理', grid: 'grid-column-start:1;grid-column-end:span 2' },
        { label: '论证结果', value: '—', empty: true, rowEnd: true, grid: 'grid-column-start:3;grid-column-end:span 2' }
      ]
    },
    {
      title: 'E. plain（bordered=false 历史写法）+ columns=3',
      dlClass: 'is-md is-plain',
      props: { style: '--sd-input-cols:3;--sd-label-w:96px' },
      items: [
        { label: '字段 A', value: '值 A', grid: 'grid-column-start:1;grid-column-end:span 2' },
        { label: '字段 B', value: '值 B', grid: 'grid-column-start:3;grid-column-end:span 2' },
        { label: '字段 C', value: '值 C', rowEnd: true, grid: 'grid-column-start:5;grid-column-end:span 2' }
      ]
    }
  ]

  const body = groups.map((g) => {
    return `<h3>${g.title}</h3><div class="card">${buildMarkup(g.items, { dlClass: g.dlClass, style: g.props.style })}</div>`
  }).join('\n')
  const html = `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>ScreenDescriptions 生产等效渲染</title>
<style>${tokens}${scoped}</style></head>
<body>
${body}
<pre id="probe" style="font:11px/1.7 monospace;color:#ffe597;white-space:pre;margin-top:18px"></pre>
<script>
window.addEventListener('load', function () {
  var out = []
  Array.prototype.forEach.call(document.querySelectorAll('.screen-descriptions'), function (dl, i) {
    var it = dl.querySelector('.screen-descriptions__item')
    var dt = it.querySelector('.screen-descriptions__label')
    var dd = it.querySelector('.screen-descriptions__value')
    function b (el) { var r = el.getBoundingClientRect(); return r.width.toFixed(0) + 'x' + r.height.toFixed(0) + '@x' + r.left.toFixed(0) }
    out.push('#' + i + ' dl=[' + getComputedStyle(dl).gridTemplateColumns + ']')
    out.push('    item display=' + getComputedStyle(it).display + '  item=' + b(it) +
      '  dt=' + b(dt) + '  dd=' + b(dd) + '  ddLines=' + Math.round(dd.getBoundingClientRect().height / 18))
    var st = dl.querySelector('.screen-descriptions__item.is-stack')
    if (st) {
      var sdt = st.querySelector('.screen-descriptions__label')
      out.push('    stack item=' + b(st) + '  dt=' + b(sdt) + '  dd=' + b(st.querySelector('.screen-descriptions__value')))
    }
  })
  document.getElementById('probe').textContent = out.join(String.fromCharCode(10))
})
</script>
</body></html>`

  const outFile = path.join(__dirname, 'regression-variants.html')
  fs.writeFileSync(outFile, html, 'utf8')
  console.log('已生成', path.relative(root, outFile), '（真实编译的 scoped CSS）')
  console.log('\n--- 编译后的 style（关键规则）---')
  scoped.split('\n').forEach((l, i) => {
    if (/__item|__label|__value|^\.screen-descriptions \{|is-flat|is-stack|is-row-end|sd-cols|sd-min-pair/.test(l)) {
      console.log(String(i + 1).padStart(4) + ': ' + l)
    }
  })
}

main().catch((e) => { console.error(e); process.exit(1) })
