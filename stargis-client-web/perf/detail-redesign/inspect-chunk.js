/**
 * 从 dev server 真编出来的 chunk 里，把 ScreenDescriptions 的运行时 CSS 抠出来做自检。
 * 这比「离线样例对了」更硬：证明改动真的编译进了浏览器要下载的那个 chunk。
 *
 * 用法：node perf/detail-redesign/inspect-chunk.js [--base http://127.0.0.1:3000] [--chunk /0.js]
 */
/* global fetch */
const argv = process.argv.slice(2)
const getArg = (name, def) => {
  const i = argv.indexOf(name)
  return i > -1 && argv[i + 1] ? argv[i + 1] : def
}

const base = getArg('--base', 'http://127.0.0.1:3000')
const chunk = getArg('--chunk', '/0.js')

const RULES = [
  ['外层等宽轨', /grid-template-columns:\s*repeat\(var\(--sd-cols\),\s*minmax\(0,\s*1fr\)\)/],
  ['标签轨 auto + 值轨 1fr', /grid-template-columns:\s*auto\s+minmax\(0,\s*1fr\)/],
  ['基线对齐', /align-items:\s*baseline/],
  ['标签封顶变量', /max-width:\s*var\(--sd-label-max\)/],
  ['行高变量', /line-height:\s*var\(--sd-line-h/],
  ['值单行省略', /white-space:\s*nowrap/],
  ['省略号', /text-overflow:\s*ellipsis/],
  ['stack 占满整行（Less 转义生效）', /grid-column:\s*1\s*\/\s*-1/]
]

const ANTI = [
  ['旧写法：外层 label+1fr 轨道', /var\(--sd-label-w,\s*108px\)\s*1fr/],
  ['旧写法：--sd-label-w 变量', /--sd-label-w/],
  // ★ 只能匹配「值本体」那一条规则：CSS 在 JS 字符串里，`}` 是 `\n}`，
  //   用 [^}]* 会跨规则匹配（is-stack 的 pre-wrap 是故意留的，会被误判）。
  //   这里用 [^\\{}]* 把匹配限制在同一段规则文本内。
  ['旧写法：值本体 pre-wrap（会折行撑高行）',
    /\.screen-descriptions__value\[[^\]]*\]\s*\{[^\\{}]*white-space:\s*pre-wrap/]
]

async function main () {
  const res = await fetch(base + chunk)
  const text = await res.text()
  console.log('chunk ' + chunk + ' = ' + text.length + ' bytes')

  // CSS 是 css-loader 注入的 JS 字符串；锚点用带 scope id 的选择器，
  // 从它开始截到下一条 CSS 模块边界，避免把别处的样式混进来。
  const anchorRe = /\.screen-descriptions\[data-v-[0-9a-f]+\]\s*\{/
  const m = anchorRe.exec(text)
  if (!m) {
    console.error('该 chunk 里找不到带 scope id 的 .screen-descriptions 规则')
    process.exit(2)
  }
  const start = m.index
  const tail = text.slice(start)
  // CSS 模块在 `\"]);` 这类地方结束；取足够长的一段即可
  const slice = tail.slice(0, 12000)

  let bad = 0
  console.log('\n--- 必须存在的规则 ---')
  RULES.forEach(([name, re]) => {
    const ok = re.test(slice)
    if (!ok) bad += 1
    console.log((ok ? '  OK   ' : '  MISS ') + name)
  })

  console.log('\n--- 不应该再出现的旧写法 ---')
  ANTI.forEach(([name, re]) => {
    const hit = re.test(slice)
    if (hit) bad += 1
    console.log((hit ? '  HIT  ' : '  OK   ') + name)
  })

  console.log('\n' + (bad === 0 ? 'chunk 内的 CSS 与源码契约一致' : '有 ' + bad + ' 项不符合'))
  process.exit(bad === 0 ? 0 : 1)
}

main().catch((e) => { console.error(e); process.exit(1) })
