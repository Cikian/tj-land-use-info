/* global Atomics, SharedArrayBuffer */
/**
 * 把 **真实的** DocDetailModal.vue 模板拿去在浏览器里渲染一遍，用于验证：
 *   ① 去掉 a-spin（原先引用未定义的 loading）后，模板依然能正常渲染；
 *   ② a-descriptions 不再触发 “Sum of column `span` …” 告警；
 *   ③ 收文/发文两个分支的字段与附件表都正常。
 *
 * 做法：用 vue-template-compiler 抽出组件模板 → 交给 vue.js（完整版，含运行时编译器）
 *      在浏览器里编译渲染；antd 用 dist 的 UMD 构建。
 *      组件里的 scoped less 也真实编译进来（把 /deep/ 换成后代选择器、去掉 data-v 作用域，
 *      因为这一页只有这一个组件，效果与生产一致）。
 *
 * 用法：node scripts/preview-doc-detail.js [receive|send]
 *      结果图输出到 %TEMP%\doc-detail-receive.png / doc-detail-send.png
 */
const fs = require('fs')
const path = require('path')
const os = require('os')
const { execFileSync } = require('child_process')
const compiler = require('vue-template-compiler')
const less = require('less')

const edge = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const vueFile = 'src/views/land/document/modules/DocDetailModal.vue'
const antdCss = path.resolve('node_modules/ant-design-vue/dist/antd.css')
const vueJs = path.resolve('node_modules/vue/dist/vue.js')
const antdJs = path.resolve('node_modules/ant-design-vue/dist/antd.js')
const fileUrl = p => 'file:///' + p.replace(/\\/g, '/')

/** 把 scoped less 变成等价的全局部样式：`/deep/` → 后代选择器，去掉 [data-v-x] */
function globalize (css) {
  return css.replace(/(^|\})\s*([^{}]+)\{/g, (m, brace, selector) => {
    const s = selector
      .split(',')
      .map(sel => sel.replace(/\/deep\/\s*/g, ' ').replace(/\s+/g, ' ').trim())
      .join(', ')
    return `${brace}\n${s} {`
  })
}

const mode = process.argv[2] === 'send' ? 'send' : 'receive'

const parsed = compiler.parseComponent(fs.readFileSync(vueFile, 'utf8'))
const template = parsed.template.content
const scopedStyle = parsed.styles.find(s => s.scoped)

// 与生产一致地编译模板，确认无编译错误
const compiled = compiler.compile(template)
if (compiled.errors && compiled.errors.length) {
  console.error('模板编译失败：')
  compiled.errors.forEach(e => console.error('  ' + e))
  process.exit(1)
}

less.render(scopedStyle.content, { filename: vueFile }).then(out => {
  const css = globalize(out.css)

  const page = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<link rel="stylesheet" href="${fileUrl(antdCss)}">
<style>${css}</style>
<style>
  html, body { margin: 0; background: #eef2f7; font: 13px/1.5 -apple-system, "Microsoft YaHei", sans-serif; }
  /* 弹窗用绝对定位，这里给它一个固定的画布 */
  .ant-modal-wrap { position: relative !important; inset: auto !important; }
  .ant-modal { margin: 0 auto; }
  #warnings { position: fixed; left: 0; bottom: 0; right: 0; background: #0f172a; color: #ffd591;
              font: 12px/1.6 Consolas, monospace; padding: 8px 12px; white-space: pre-wrap; max-height: 92px; overflow: hidden; }
</style>
</head>
<body>
<div id="app">
  <doc-detail-modal ref="detail" />
</div>
<pre id="warnings">（还没有告警）</pre>

<script>window.process = { env: { NODE_ENV: 'development' } }</script>
<script src="${fileUrl(vueJs)}"></script>
<script src="${fileUrl(antdJs)}"></script>
<script>
  var captured = []
  var origError = console.error
  console.error = function () {
    captured.push(Array.prototype.map.call(arguments, String).join(' '))
    origError.apply(console, arguments)
    document.getElementById('warnings').textContent =
      captured.length ? ('捕获到 ' + captured.length + ' 条告警：\\n' + captured.slice(0, 4).join('\\n')) : '（没有告警）'
  }
  Vue.use(antd)

  var DOC = ${JSON.stringify({
    docNo: mode === 'send' ? 'FW-2026-0003' : 'SW-2026-0012',
    docTitle: mode === 'send' ? '关于呈报富锦路（北旺道—兴业道）道路工程竣工验收情况的报告' : '关于转发天津市经营性用地出让工作有关要求的通知',
    status: mode === 'send' ? '已归档' : '承办中',
    archiveId: mode === 'send' ? 'demo-ar-01' : null,
    secretLevel: '一般',
    urgency: mode === 'send' ? '普通' : '加急',
    fromDept: '天津市规划和自然资源局',
    fromDocNo: '津规资发〔2026〕15号',
    receiveDate: '2026-03-11',
    docType: mode === 'send' ? '报告' : '通知',
    pageCount: 4,
    copies: 2,
    handleDeadline: '2026-03-20',
    currentHandlerName: '李伟',
    finishTime: null,
    finishOpinion: null,
    toDept: '天津市住房和城乡建设委员会',
    ccDept: '天津市发展和改革委员会',
    issueDate: '2026-03-09',
    signer: '王建国',
    drafter: '张珊',
    crzdbh: '津丽（挂）2019-02',
    ptxmmc: '富锦路（北旺道—兴业道）道路工程',
    createBy: 'zhangsan',
    createTime: '2026-03-11 09:20:00',
    updateBy: 'lisi',
    updateTime: '2026-03-12 15:40:00',
    remark: '请按期限完成承办并反馈办理结果；涉及规划条件部分需与规划科会商后一并答复。',
    attachments: [
      { id: 'a1', fileName: '天津市经营性用地出让工作有关要求.pdf', fileSize: 1048576, uploadName: '张三', uploadTime: '2026-03-11 09:20:00', storePath: 'doc/2026/03/a.pdf' },
      { id: 'a2', fileName: '附件1-任务分解表.docx', fileSize: 20480, uploadName: '张三', uploadTime: '2026-03-11 09:21:00', storePath: 'doc/2026/03/b.docx' }
    ]
  }, null, 2)}

  // 把真实模板挂上去（模板里用到的方法/计算属性在这里补齐桩实现）
  var Ctor = Vue.extend({
    template: ${JSON.stringify(template)},
    data: function () {
      return {
        visible: true,
        docType: ${JSON.stringify(mode)},
        doc: DOC,
        attachmentColumns: [
          { title: '#', width: 48, align: 'center', scopedSlots: { customRender: 'index' } },
          { title: '文件名', dataIndex: 'fileName', ellipsis: true, scopedSlots: { customRender: 'fileName' } },
          { title: '大小', dataIndex: 'fileSize', width: 110, scopedSlots: { customRender: 'fileSize' } },
          { title: '上传人', dataIndex: 'uploadName', width: 120 },
          { title: '上传时间', dataIndex: 'uploadTime', width: 165 }
        ]
      }
    },
    computed: {
      title: function () { return this.docType === 'send' ? '发文详情' : '收文详情' },
      attachments: function () { return (this.doc && this.doc.attachments) || [] }
    },
    methods: {
      close: function () {}, download: function () { return false },
      handleOpenFlow: function () { return false },
      joinInfo: function (a, b) {
        var p = [a, b].filter(function (v) { return v !== null && v !== undefined && v !== '' })
        return p.length ? p.join(' · ') : '—'
      },
      statusColor: function (s) {
        if (s === '已办结' || s === '已归档') return 'green'
        if (s === '已退回') return 'red'
        if (s === '承办中') return 'cyan'
        return 'orange'
      },
      secretColor: function (l) {
        if (l === '机密') return 'red'
        if (l === '秘密') return 'volcano'
        if (l === '内部') return 'orange'
        return undefined
      },
      formatSize: function (bytes) {
        var v = Number(bytes); if (!v) return '—'
        var u = ['B', 'KB', 'MB', 'GB', 'TB'], s = v, i = 0
        while (s >= 1024 && i < u.length - 1) { s /= 1024; i++ }
        return i === 0 ? s + ' ' + u[i] : s.toFixed(2) + ' ' + u[i]
      }
    }
  })
  new Ctor().$mount('#app')
  setTimeout(function () {
    var box = document.getElementById('warnings')
    if (!captured.length) box.textContent = '✓ 没有捕获到任何 console.error 告警'
  }, 600)
</script>
</body>
</html>`

  const tmpHtml = path.join(os.tmpdir(), `doc-detail-${mode}.html`)
  fs.writeFileSync(tmpHtml, page, 'utf8')

  const outPng = path.join(os.tmpdir(), `doc-detail-${mode}.png`)
  if (fs.existsSync(outPng)) fs.unlinkSync(outPng)

  const profileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'edge-shot-'))
  execFileSync(edge, [
    '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
    '--force-device-scale-factor=1', '--allow-file-access-from-files',
    '--virtual-time-budget=6000',
    `--user-data-dir=${profileDir}`,
    '--window-size=1000,900',
    `--screenshot=${outPng}`,
    fileUrl(tmpHtml)
  ], { stdio: 'inherit' })

  // Edge 提权时会 RunDeElevated 后秒退、退出码仍是 0，必须轮询等文件
  const sleep = ms => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms)
  const deadline = Date.now() + 30000
  let lastSize = -1
  while (Date.now() < deadline) {
    const size = fs.existsSync(outPng) ? fs.statSync(outPng).size : 0
    if (size > 0 && size === lastSize) break
    lastSize = size
    sleep(300)
  }
  if (!fs.existsSync(outPng) || fs.statSync(outPng).size === 0) {
    throw new Error('等待 30s 仍没有产出截图：' + outPng)
  }
  fs.rmSync(profileDir, { recursive: true, force: true })
  console.log('截图：' + outPng + '（' + fs.statSync(outPng).size + ' 字节）')
}).catch(e => {
  console.error('渲染失败：', e && e.message ? e.message : e)
  process.exit(1)
})
