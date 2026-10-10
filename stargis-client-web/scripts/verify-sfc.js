/**
 * 大屏 .vue 文件静态体检（与 admin-client/scripts/verify-sfc.js 同规则，按大屏约定调整）
 * ------------------------------------------------------------------
 * 用法：
 *   node scripts/verify-sfc.js                 # 校验下面 FILES 清单
 *   node scripts/verify-sfc.js <文件...>        # 只校验指定文件（写绝对路径或相对本工程根目录）
 *
 * 为什么需要它：`.vue` 的模板/样式错误**不会**在 `npm run serve` 之前暴露，
 * 而大屏的 `.eslintignore` 里写着 `/src`（lint 全跳过），
 * 于是「模板写错一个标签闭合」这类问题只能等页面白屏才发现。这个脚本把
 * 「模板能不能编译」「less 能不能编译」「组件有没有 name」提前到提交前。
 *
 * 检查项：
 *   1. <template> 用 vue-template-compiler 编译，必须 0 error；
 *   2. <style lang="less"> 用 less 编译，必须 0 error；
 *   3. 组件必须有 `name`（keep-alive 缓存与 devtools 定位都靠它）；
 *   4. ★ 禁 antd 组件：大屏只有 Screen* 一套视觉语言，
 *      混用 `a-button` / `a-table` 会带进 antd 的浅色皮肤，与深海蓝主题打架；
 *   5. ★ 禁 v-has：大屏的按钮级权限由中台下发，业务后端角色对不上时
 *      `v-has` 会把元素从 DOM 里删掉，表现为「功能写了但按钮不出现」（README 第 6 节第 13 条）。
 */
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const compiler = require(path.join(root, 'node_modules', 'vue-template-compiler'))
const less = require(path.join(root, 'node_modules', 'less'))

/** 默认校验清单：本次新增的三个模块（后续新增模块请登记到这里） */
const FILES = [
  'src/api/land/ledger.js',
  'src/api/land/handover.js',
  'src/api/land/completion.js',
  // ★ constants.js 必须登记：本次两处「../constants 少一层」的构建错误就出在这里，
  //   而不登记就查不到（模板编译与 eslint 都不解析 import 路径）
  'src/views/screen/archive/modules/ledger/constants.js',
  'src/views/screen/archive/modules/handover/constants.js',
  'src/views/screen/archive/modules/completion/constants.js',
  'src/views/screen/archive/modules/ledger/LedgerPanel.vue',
  'src/views/screen/archive/modules/ledger/LedgerSearchForm.vue',
  'src/views/screen/archive/modules/ledger/LedgerTable.vue',
  'src/views/screen/archive/modules/ledger/LedgerFormModal.vue',
  'src/views/screen/archive/modules/ledger/LedgerDetailModal.vue',
  'src/views/screen/archive/modules/ledger/LedgerImportModal.vue',
  'src/views/screen/archive/modules/handover/HandoverPanel.vue',
  'src/views/screen/archive/modules/handover/HandoverSearchForm.vue',
  'src/views/screen/archive/modules/handover/HandoverTable.vue',
  'src/views/screen/archive/modules/handover/HandoverFormModal.vue',
  'src/views/screen/archive/modules/handover/HandoverDetailModal.vue',
  'src/views/screen/archive/modules/completion/CompletionPanel.vue',
  'src/views/screen/archive/modules/completion/CompletionSearchForm.vue',
  'src/views/screen/archive/modules/completion/CompletionTable.vue',
  'src/views/screen/archive/modules/completion/CompletionFormModal.vue',
  'src/views/screen/archive/modules/completion/CompletionDetailModal.vue',

  // ===== 数据管理（方案 2.3.1（三）第 1/2/3/6 项 + 配套导入/附件）=====
  // ★ 与上面同一约定：新增模块必须登记到这里，否则模板/less/import 路径三类问题
  //   都只能等 npm run serve 打包时才暴露（eslint 被 .eslintignore 的 /src 全跳过）。
  'src/api/land/landAdmin.js',
  'src/api/land/facilityAdmin.js',
  'src/api/land/facilityImport.js',
  'src/api/land/attachment.js',
  'src/api/land/dataRecycle.js',
  'src/api/land/landImport.js',
  'src/views/screen/data/constants.js',
  'src/views/screen/data/index.vue',
  'src/views/screen/data/modules/LandSearchForm.vue',
  'src/views/screen/data/modules/LandTable.vue',
  'src/views/screen/data/modules/LandFormModal.vue',
  'src/views/screen/data/modules/LandDetailModal.vue',
  'src/views/screen/data/modules/LandEntryPanel.vue',
  'src/views/screen/data/modules/LandImportModal.vue',
  'src/views/screen/data/modules/LandImportPanel.vue',
  'src/views/screen/data/modules/FacilitySearchForm.vue',
  'src/views/screen/data/modules/FacilityTable.vue',
  'src/views/screen/data/modules/FacilityStageChips.vue',
  'src/views/screen/data/modules/FacilityFormModal.vue',
  'src/views/screen/data/modules/FacilityDetailModal.vue',
  'src/views/screen/data/modules/FacilityEntryPanel.vue',
  'src/views/screen/data/modules/ProcessTreePanel.vue',
  'src/views/screen/data/modules/ProcessItemModal.vue',
  'src/views/screen/data/modules/FacilityImportModal.vue',
  'src/views/screen/data/modules/FacilityImportPanel.vue',
  'src/views/screen/data/modules/AttachmentSearchForm.vue',
  'src/views/screen/data/modules/AttachmentTable.vue',
  'src/views/screen/data/modules/AttachmentUploadModal.vue',
  'src/views/screen/data/modules/AttachmentPreviewModal.vue',
  'src/views/screen/data/modules/AttachmentPanel.vue',
  'src/views/screen/data/modules/AttachmentTreeView.vue',
  'src/views/screen/data/modules/AttachmentTreeNode.vue',
  'src/views/screen/data/modules/RecycleTable.vue',
  'src/views/screen/data/modules/ChangeLogTable.vue',
  'src/views/screen/data/modules/HistoryDrawer.vue',
  'src/views/screen/data/modules/DataRecyclePanel.vue',
]

const problems = []
const checked = []

function fail (file, message) {
  problems.push(`${file}\n    ${message}`)
}

function extractBlocks (source, tag) {
  const blocks = []
  const regex = new RegExp(`<${tag}([^>]*)>([\\s\\S]*?)</${tag}>`, 'g')
  let match
  while ((match = regex.exec(source)) !== null) {
    blocks.push({ attrs: match[1] || '', content: match[2] || '' })
  }
  return blocks
}

/**
 * 取根 <template> 的内容（★ 必须按嵌套配对，不能用非贪婪正则）。
 *
 * 原实现是 `/<template([^>]*)>([\s\S]*?)<\/template>/`，非贪婪匹配到**第一个**
 * `</template>` 就收尾 —— 于是任何用到具名插槽（`<template #extra>`）或
 * `<template v-if>` 的组件，取到的都是被截断的半截模板，报出
 * 「tag <template> has no matching end tag」这类**假报错**，
 * 而且 antd / v-has 两项扫描也因此漏掉截断点之后的所有内容。
 *
 * 证据：本仓既有的 src/views/screen/archive/modules/ArchiveQuery.vue（未被本次改动
 * 碰过）用旧实现校验同样报错，可见是脚本的缺陷而非页面的问题。
 *
 * 这里改为「从根标签开始数嵌套深度」，深度归零处的 `</template>` 才是它的闭合。
 * @param {string} source
 * @returns {string|null} 根模板内容；没有 <template> 时返回 null
 */
function extractRootTemplate (source) {
  const start = source.indexOf('<template')
  if (start < 0) return null
  const headEnd = source.indexOf('>', start)
  if (headEnd < 0) return null

  // 从根标签之后开始扫，遇到 <template…> 深度 +1，遇到 </template> 深度 -1
  const token = /<template[\s/>]|<\/template>/g
  token.lastIndex = headEnd + 1
  let depth = 1
  let match
  while ((match = token.exec(source)) !== null) {
    depth += match[0] === '</template>' ? -1 : 1
    if (depth === 0) return source.slice(headEnd + 1, match.index)
  }
  return null
}

/**
 * 把 webpack 别名 `~@/` 还原成真实路径，供纯 less 编译器解析。
 *
 * 为什么必须做：`.vue` 里的 `@import '~@/components/screen/styles/screen-mixins.less';`
 * 是**webpack 别名语法**（README 第 6 节第 2 条要求必须带 `~`，否则 less-loader 不走别名），
 * 而这里用的是裸 `less` 编译器，它不认识 `~@`，会去找字面量文件名
 * `~@/components/screen/styles/screen-mixins.less` 并报 "wasn't found"。
 * 于是**每个带样式的大屏组件都过不了这一项**（连未被改动的 ArchiveQuery.vue 也一样），
 * 属于脚本缺陷而非页面缺陷。
 * @param {string} content
 * @returns {string}
 */
function resolveLessAliases (content) {
  const src = path.join(root, 'src').replace(/\\/g, '/')
  return content.replace(/~@\//g, `${src}/`)
}

function checkVue (file, source) {
  // 1. 模板编译
  const rootTemplate = extractRootTemplate(source)
  const templates = rootTemplate === null ? [] : [{ attrs: '', content: rootTemplate }]
  if (!templates.length) {
    fail(file, '没有找到 <template> 块')
  } else {
    const result = compiler.compile(templates[0].content)
    if (result.errors && result.errors.length) {
      fail(file, `模板编译失败：${result.errors.join('; ')}`)
    }
    // 4. 禁 antd 组件
    const antdTags = []
    const tagRegex = /<(a-[a-z-]+)[\s/>]/g
    let tagMatch
    while ((tagMatch = tagRegex.exec(templates[0].content)) !== null) {
      if (antdTags.indexOf(tagMatch[1]) === -1) antdTags.push(tagMatch[1])
    }
    if (antdTags.length) {
      fail(file, `模板里使用了 antd 组件（${antdTags.join(', ')}）：大屏只允许 components/screen 下的 Screen* 组件 + 原生标签`)
    }
    // 5. 禁 v-has
    if (/v-has\s*=/.test(templates[0].content)) {
      fail(file, '模板里使用了 v-has：大屏的按钮级权限由中台下发，v-has 会把按钮整块从 DOM 删掉（见 components/screen/README.md 第 6 节第 13 条）')
    }
  }

  // 3. name（同步项，先做掉，保证它不依赖下面的异步结果）
  if (!/\n\s*name\s*:\s*['"][^'"]+['"]/.test(source)) {
    fail(file, '组件缺少 name 选项')
  }

  /**
   * 2. less 编译。
   * ★ less.render 返回的是 Promise：原实现把它放进 try/catch 里，
   *   编译失败时 catch 根本不会触发（异步异常），于是
   *     · 真实的 less 语法错误被静默吞掉；
   *     · 未处理的 rejection 会在脚本「已经打印通过」之后把 Node 打挂，
   *       退出码变成 1 —— 表现为「明明全部通过却 exit 1」。
   *   这里改为收集 Promise 并在所有文件检查完后统一 await，失败计入 problems。
   */
  const styles = extractBlocks(source, 'style').filter((block) => /lang\s*=\s*["']less["']/.test(block.attrs))
  const tasks = styles.map((block, index) => less
    .render(resolveLessAliases(block.content), { javascriptEnabled: true })
    .catch((e) => {
      fail(file, `第 ${index + 1} 个 <style lang="less"> 编译失败：${e.message}`)
    }))
  return Promise.all(tasks)
}

/** 非 .vue 的 js 文件只做最基本的语法检查 */
function checkJs (file, source) {
  try {
    // eslint-disable-next-line no-new-func
    new Function(source.replace(/^\s*(import|export)\s/gm, '// $&'))
  } catch (e) {
    // ESM 语法用 Function 解析会误报，这里只拦住明显的括号不平衡
    const counts = { '(': 0, '{': 0, '[': 0 }
    const pairs = { ')': '(', '}': '{', ']': '[' }
    const stripped = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '')
    let balanced = true
    for (const ch of stripped) {
      if (counts[ch] !== undefined) counts[ch] += 1
      else if (pairs[ch]) {
        counts[pairs[ch]] -= 1
        if (counts[pairs[ch]] < 0) balanced = false
      }
    }
    if (!balanced || Object.values(counts).some((value) => value !== 0)) {
      fail(file, '括号不平衡（脚本解析失败，可能是语法错误）')
    }
  }
}

/**
 * ★★ 校验 import 路径能否解析（2026-10-08 补：用户真实踩到的坑）
 *
 * 事故：三个新模块放在 `modules/<模块>/` 下，但照抄的参考文件 `ArchiveQuery.vue`
 * 直接在 `modules/` 下、写的是 `from '../constants'`。于是 `modules/ledger/constants.js`
 * 里的 `'../constants'` 指到了不存在的 `modules/constants.js`：
 *
 *   ERROR  Failed to compile with 2 errors
 *   This relative module was not found:
 *   * ../constants in ./src/views/screen/archive/modules/handover/HandoverSearchForm.vue
 *
 * **模板编译、less 编译、eslint 三项全都发现不了它** —— 只有 webpack 真正打包时才报。
 * 校验脚本的价值就在于"比 webpack 更早发现问题"，所以这一类必须自己查。
 *
 * 规则：只校验**项目内**的引用（`./`、`../`、`@/`）；第三方包（vue、axios…）不解析，
 * 因为要模拟 node_modules 的解析算法，收益低且容易误报。
 *
 * @param {string} spec import 的模块说明符
 * @param {string} fromFile 发起 import 的文件**绝对路径**
 * @returns {boolean} 能否解析到实体文件
 */
function resolveImport (spec, fromFile) {
  if (!spec) return true
  const isRelative = spec.startsWith('./') || spec.startsWith('../')
  const isAlias = spec.startsWith('@/')
  if (!isRelative && !isAlias) return true

  const base = isAlias
    ? path.join(root, 'src', spec.slice(2))
    : path.resolve(path.dirname(fromFile), spec)

  // 与 webpack resolve.extensions / 目录 index 一致的候选顺序
  const candidates = [
    base,
    `${base}.js`,
    `${base}.vue`,
    `${base}.json`,
    path.join(base, 'index.js'),
    path.join(base, 'index.vue'),
  ]
  return candidates.some((item) => fs.existsSync(item))
}

/** 抽取并校验文件里的所有 import / export from / require */
function checkImports (file, absolutePath, source) {
  const regex = /(?:from\s*|import\s*|require\s*\(\s*)['"]([^'"]+)['"]/g
  let match
  const missing = []
  while ((match = regex.exec(source)) !== null) {
    const spec = match[1]
    if (!resolveImport(spec, absolutePath) && missing.indexOf(spec) === -1) {
      missing.push(spec)
    }
  }
  if (missing.length) {
    fail(file, `import 的模块路径解析不到（webpack 会报 "This relative module was not found"）：${missing.join(', ')}`)
  }
}

const targets = process.argv.length > 2 ? process.argv.slice(2) : FILES
/** less 编译是异步的，先收集再统一收尾（见 checkVue 里关于 Promise 的说明） */
const pending = []
targets.forEach((relative) => {
  const file = path.isAbsolute(relative) ? relative : path.join(root, relative)
  if (!fs.existsSync(file)) {
    fail(relative, '文件不存在（清单里的路径与实际文件不一致）')
    return
  }
  const source = fs.readFileSync(file, 'utf8')
  // 6. import 路径（.vue 与 .js 都要查）
  checkImports(relative, file, source)
  if (file.endsWith('.vue')) {
    const task = checkVue(relative, source)
    if (task) pending.push(task)
  } else {
    checkJs(relative, source)
  }
  checked.push(relative)
})

/** 所有异步项收敛后再出结论，避免「先打印通过、后追加问题」 */
function report () {
  if (problems.length) {
    console.error(`✗ ${checked.length} 个文件中有 ${problems.length} 处问题：\n`)
    problems.forEach((item) => console.error(`  - ${item}\n`))
    process.exit(1)
  }
  console.log(`✓ ${checked.length} 个文件：模板编译、less 编译、name、组件与权限约定、import 路径 全部通过。`)
}

Promise.all(pending).then(report).catch((e) => {
  console.error(`✗ 校验脚本自身异常：${e && e.message}`)
  process.exit(1)
})
