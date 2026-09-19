/**
 * 校验新增的 .vue 单文件组件：
 *  ① template 能否被 vue-template-compiler 编译（捕获模板语法/指令错误）
 *  ② style 中 lang="less" 的块能否被 less 编译（捕获样式语法错误）
 *  ③ 每个组件是否都有 name 选项（jeecg 的 keep-alive 与调试依赖 name）
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
