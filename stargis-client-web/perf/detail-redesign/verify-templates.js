/**
 * 一次性校验脚本：用项目自带的 vue-template-compiler 编译本次改动的 SFC 模板，
 * 确认 <template v-for> 直接渲染 <dt>/<dd> 的写法能被真实编译器接受。
 * 用法：node perf/detail-redesign/verify-templates.js
 */
const fs = require('fs')
const path = require('path')
const compiler = require('vue-template-compiler')

const root = path.resolve(__dirname, '../..')
const files = [
  'src/components/screen/ScreenDescriptions.vue',
  'src/views/screen/escalation/modules/EscalationDetailModal.vue',
  'src/views/screen/archive/modules/ArchiveDetailModal.vue',
  'src/views/screen/archive/modules/doc/DocDetailModal.vue',
]

let failed = 0

files.forEach((rel) => {
  const abs = path.join(root, rel)
  const source = fs.readFileSync(abs, 'utf8')
  const sfc = compiler.parseComponent(source)

  if (!sfc.template) {
    console.log(`SKIP  ${rel} (no <template>)`)
    return
  }

  // resourcePath 让编译器按 .vue 规则处理（与 vue-loader 一致）
  const result = compiler.compile(sfc.template.content, {
    resourcePath: abs,
    outputSourceRange: true,
  })

  const errors = (result.errors || []).filter(Boolean)
  const tips = (result.tips || []).filter(Boolean)

  if (errors.length) {
    failed += 1
    console.log(`FAIL  ${rel}`)
    errors.forEach((e) => {
      console.log(`      ${typeof e === 'string' ? e : e.msg}`)
    })
  } else {
    console.log(`OK    ${rel}`)
  }
  if (tips.length) {
    tips.forEach((t) => console.log(`      tip: ${typeof t === 'string' ? t : t.msg}`))
  }
})

console.log(failed ? `\n${failed} file(s) failed to compile` : '\nAll templates compiled successfully')
process.exit(failed ? 1 : 0)
