/**
 * 校验新增的 .vue 单文件组件：
 *  ① template 能否被 vue-template-compiler 编译（捕获模板语法/指令错误）
 *  ② style 中 lang="less" 的块能否被 less 编译（捕获样式语法错误）
 *  ③ 每个组件是否都有 name 选项（jeecg 的 keep-alive 与调试依赖 name）
 *  ④ ★ a-descriptions 的 column/span 用法体检（见下方 checkDescriptions）
 *
 * 用法：node scripts/verify-sfc.js
 * 注意：本脚本只在开发期用于自检，不参与打包。
 */
const fs = require('fs')
const path = require('path')
const compiler = require('vue-template-compiler')
const less = require('less')

const files = [
  'src/views/land/archive/ArchiveList.vue',
  'src/views/land/archive/ArchiveQuery.vue',
  'src/views/land/archive/ArchiveStatistics.vue',
  'src/views/land/archive/modules/ArchiveDetailModal.vue',
  'src/views/land/archive/modules/ArchiveFileTable.vue',
  'src/views/land/archive/modules/ArchiveModal.vue',
  'src/views/land/archive/modules/ArchiveSearchForm.vue',
  'src/views/land/archive/modules/ArchiveTable.vue',
  'src/views/land/archive/modules/CategoryPicker.vue',
  'src/views/land/archive/modules/ProjectPicker.vue',
  'src/views/land/document/DocReceiveList.vue',
  'src/views/land/document/DocSendList.vue',
  'src/views/land/document/modules/DocArchiveModal.vue',
  'src/views/land/document/modules/DocAttachmentTable.vue',
  'src/views/land/document/modules/DocDetailModal.vue',
  'src/views/land/document/modules/DocFlowModal.vue',
  'src/views/land/document/modules/DocReceiveModal.vue',
  'src/views/land/document/modules/DocSendModal.vue',
  'src/views/land/document/modules/UserSelect.vue'
]

let failed = 0
const tasks = []

/**
 * ④ a-descriptions 的 column / span 体检。
 *
 * antd（ant-design-vue/lib/descriptions/index.js）按「本行 span 之和 == column」来切行，
 * 一旦某个 span 超过它所在行的剩余列数，就会 console.error：
 *   [antdv: Descriptions] Sum of column `span` in a line exceeds `column` of Descriptions.
 *
 * 两个容易踩的坑：
 *   坑1 —— column 写成响应式对象（{ xxl: 2, ..., xs: 1 }）时，screens 在**首次渲染**还是空的，
 *          getColumn() 会兜底返回 3。于是「配置里最大只有 2」也可能按 3 列去切而溢出。
 *          结论：**只要用了 >1 的 span，column 就应该写成数字**（除非该 span 恰好是最后一项）。
 *   坑2 —— 最后一项的 span 其实会被 antd 强制改写成「本行剩余列数」
 *          （cloneElement(itemNode, { props: { span: leftSpans } })），
 *          所以最后一项写 :span 对渲染毫无影响，却会让记账溢出。**最后一项不要写 span**。
 *
 * 这里做静态近似：取 a-descriptions 块，看它的 column 写法 + 各 item 的 span。
 */
function checkDescriptions (file, template) {
  const descRe = /<a-descriptions\b([^>]*)>([\s\S]*?)<\/a-descriptions>/g
  let m
  while ((m = descRe.exec(template)) !== null) {
    const attrs = m[1]
    const inner = m[2]
    const columnAttr = /\s:column\s*=\s*"([^"]*)"/.exec(attrs) || /\scolumn\s*=\s*"([^"]*)"/.exec(attrs)
    const columnIsObject = columnAttr && columnAttr[1].trim().startsWith('{')

    // 逐个 item 取 span（只关心显式写了 :span 的）
    const itemRe = /<a-descriptions-item\b([^>]*?)\/?>/g
    const items = []
    let im
    while ((im = itemRe.exec(inner)) !== null) {
      const spanMatch = /\s:span\s*=\s*"(\d+)"/.exec(im[1])
      items.push({ span: spanMatch ? parseInt(spanMatch[1], 10) : 1, hasSpan: !!spanMatch, raw: im[1].trim() })
    }
    if (!items.length) {
      continue
    }

    // 坑2：最后一项不该写 :span
    const last = items[items.length - 1]
    if (last.hasSpan) {
      console.error(`✗ ${file}：a-descriptions 的**最后一项**写了 :span="${last.span}"。` +
        'antd 会把最后一项的 span 强制改写成本行剩余列数，写它不影响渲染，却会导致' +
        '「span 之和超过 column」误报告警。请去掉。')
      failed++
    }

    // 坑1：存在非末项的 span>1，却把 column 写成响应式对象
    const nonLastBigSpan = items.slice(0, -1).some(it => it.span > 1)
    if (nonLastBigSpan && columnIsObject) {
      console.error(`✗ ${file}：a-descriptions 里有一项非末项的 span>1，但 column 写成了响应式对象 ` +
        `${columnAttr[1]}。首次渲染时 antd 的 getColumn() 会兜底成 3，容易与实际列数不一致而告警；` +
        '请把 column 改成数字。')
      failed++
    }
    // 非末项 span 必须 <= 所有断点的最小列数（这里只能对对象写法做粗略检查）
    if (nonLastBigSpan && columnIsObject) {
      const nums = (columnAttr[1].match(/:\s*(\d+)/g) || []).map(s => parseInt(s.replace(/[^\d]/g, ''), 10))
      const min = nums.length ? Math.min(...nums) : 3
      const biggest = Math.max(...items.slice(0, -1).map(it => it.span))
      if (biggest > min) {
        console.error(`✗ ${file}：a-descriptions 非末项的 span=${biggest} 大于断点最小列数 ${min}，` +
          '在窄屏断点下必然告警。')
        failed++
      }
    }
  }
}

files.forEach(file => {
  const full = path.resolve(process.cwd(), file)
  if (!fs.existsSync(full)) {
    console.error(`✗ 文件不存在：${file}`)
    failed++
    return
  }
  const source = fs.readFileSync(full, 'utf8')
  const parsed = compiler.parseComponent(source)

  // ① 模板编译
  if (!parsed.template) {
    console.error(`✗ ${file}：缺少 <template>`)
    failed++
  } else {
    const result = compiler.compile(parsed.template.content)
    if (result.errors && result.errors.length) {
      console.error(`✗ ${file}：模板编译错误`)
      result.errors.forEach(msg => console.error(`    ${msg}`))
      failed++
    }
    if (result.tips && result.tips.length) {
      result.tips.forEach(msg => console.warn(`! ${file}：${msg}`))
    }
    // ④ a-descriptions 的 column/span 体检
    checkDescriptions(file, parsed.template.content)
  }

  // ② 脚本里是否有 name
  const script = parsed.script ? parsed.script.content : ''
  if (!/name\s*:\s*['"]/.test(script)) {
    console.error(`✗ ${file}：缺少 name 选项`)
    failed++
  }

  // ③ less 编译（可能有多个 style 块，其中一个是非 scoped 的全局块）
  ;(parsed.styles || []).forEach((style, index) => {
    if (style.lang !== 'less') {
      return
    }
    tasks.push(
      less.render(style.content, { filename: full })
        .catch(err => {
          console.error(`✗ ${file}：style[${index}] less 编译失败 —— ${err.message}`)
          failed++
        })
    )
  })
})

Promise.all(tasks).then(() => {
  if (failed) {
    console.error(`\n共有 ${failed} 个问题。`)
    process.exit(1)
  }
  console.log(`✓ ${files.length} 个 .vue 文件：模板编译、less 编译、name 检查全部通过。`)
})
