/* global Atomics, SharedArrayBuffer */
/**
 * 用 Edge 无头模式把「档案详情弹窗」的页头与信息表渲染出来截图，
 * 用来**实测**两个 CSS 修复，而不是靠推算：
 *  ① 页头：按钮组是否真的落在关闭按钮 × 的下一行、且不被遮挡；
 *  ② 信息表：标签列是否还被超长内容挤到换行。
 *
 * 做法：用项目自带的 antd.css（真实的 .ant-modal-close / descriptions 规则）
 *      + 组件里真实编译出来的 less（并施加与 vue-loader 一致的 /deep/ 作用域变换），
 *      在本地拼一个最简 DOM 渲染。不依赖登录与后端。
 *
 * 用法：node scripts/preview-detail-header.js
 */
const fs = require('fs')
const path = require('path')
const os = require('os')
const { execFileSync } = require('child_process')
const compiler = require('vue-template-compiler')
const less = require('less')

const vueFile = 'src/views/land/archive/modules/ArchiveDetailModal.vue'
const antdCss = path.resolve('node_modules/ant-design-vue/dist/antd.css')
const edge = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'

/**
 * 复刻 vue-loader 对 scoped CSS 的处理：
 *   `.a /deep/ .b`  →  `.a[data-v-x] .b`
 * 这样渲染时选择器优先级与生产环境完全一致（否则测不出与 antd 的优先级之争）。
 */
function scopeTransform (css, scopeId) {
  return css.replace(/(^|\})\s*([^{}]+)\{/g, (m, brace, selector) => {
    const scoped = selector
      .split(',')
      .map(sel => {
        if (sel.includes('/deep/')) {
          const [before, ...rest] = sel.split('/deep/')
          return `${before.trim()}[${scopeId}] ${rest.join('/deep/').trim()}`
        }
        return `${sel.trim()}[${scopeId}]`
      })
      .join(', ')
    return `${brace}\n${scoped} {`
  })
}

const SCOPE = 'data-v-detailmock'

const parsed = compiler.parseComponent(fs.readFileSync(vueFile, 'utf8'))
const scopedStyle = parsed.styles.find(s => s.scoped)
const globalStyle = parsed.styles.find(s => !s.scoped)

Promise.all([
  less.render(scopedStyle.content, { filename: vueFile }),
  less.render(globalStyle.content, { filename: vueFile })
]).then(([scopedOut, globalOut]) => {
  const css = [
    scopeTransform(scopedOut.css, SCOPE),
    globalOut.css
  ].join('\n')

  // ---- 与组件模板一致的最小 DOM ----
  //  ★ 关键：vue-loader 会把 scope 属性加到组件模板里的**每一个**元素上，
  //    所以要给下面每个自有元素都带上 data-v-xxx，否则 `&__xxx[data-v-x]` 这类规则匹配不到，
  //    测出来的就不是生产环境的样式了（第一次就是这么把页头样式测丢的）。
  const S = ` ${SCOPE}`
  const tag = (text, color) => `<span class="ant-tag ant-tag-${color}"${S}>${text}</span>`
  // dist 里没有 anticon 字体文件，用占位方块代替图标；宽度尽量贴近真实图标
  const btn = (icon, text) =>
    `<button class="ant-btn"${S}><i class="anticon anticon-${icon}"></i><span>${text}</span></button>`

  const headMain = `
    <div class="archive-detail__head-main"${S}>
      <div class="archive-detail__no"${S}>DA-2026-0001</div>
      <div class="archive-detail__name" title="测试"${S}>测试</div>
    </div>`

  // 标签与按钮共用一行（.archive-detail__head-bar），标签靠左、按钮靠右
  const tags = `
    <div class="archive-detail__tags"${S}>
      <!-- 复现用户截图的场景：状态「未归档」与密级「一般」原先都是 color="default" → 隐形 -->
      <span class="ant-tag"${S}>未归档</span>
      <span class="ant-tag"${S}>电子档案</span>
      <span class="ant-tag"${S}>密级：一般</span>
      ${tag('1 个文件', 'blue')}
    </div>`

  const headActions = `
    <div class="archive-detail__head-actions"${S}>
      ${btn('edit', '编辑')}
      ${btn('download', '导出该项目档案')}
      ${btn('reload', '刷新收发文')}
    </div>`

  const headBar = `
    <div class="archive-detail__head-bar"${S}>${tags}${headActions}</div>`

  // 修复前 / 修复后 对比条：把「非预设色」的坑直接摆在图上。
  // 「修复前」这一行的 class 与内联样式是照 antd Tag.js 的真实输出写的：
  //   getTagClassName → 'ant-tag' + 'ant-tag-has-color'（非预设色时**不会**加 ant-tag-<color>）
  //   getTagStyle     → { backgroundColor: 'default' | 'processing' }  ← 非法 CSS 值，浏览器丢弃
  // 于是 .ant-tag-has-color{color:#fff} 生效 + 背景缺失 = 白底白字。
  const broken = (text, bogus) =>
    `<span class="ant-tag ant-tag-has-color" style="background-color:${bogus}">${text}</span>`
  const compare = `
    <div style="padding:16px 24px;border-top:1px dashed #d9d9d9;background:#fff">
      <div style="font-size:13px;color:#475569;margin-bottom:8px">修复前（把「非预设色」当 color 传）→ 加 ant-tag-has-color 变白字，背景色又是非法值被丢弃，整个标签白底白字看不见：</div>
      <div style="display:flex;gap:8px;margin-bottom:14px">
        ${broken('未归档', 'default')}
        ${broken('待办', 'processing')}
        ${broken('密级：一般', 'default')}
      </div>
      <div style="font-size:13px;color:#475569;margin-bottom:8px">修复后（不传 color 走默认灰 / 改用预设色）→ 正常显示：</div>
      <div style="display:flex;gap:8px">
        <span class="ant-tag">未归档</span>
        <span class="ant-tag ant-tag-blue">待办</span>
        <span class="ant-tag">密级：一般</span>
        <span class="ant-tag ant-tag-green">已归档</span>
        <span class="ant-tag ant-tag-orange">审核中</span>
      </div>
    </div>`

  // 信息表：按真实数据 10 项（3+3+3+1），含超长的「档案类别」值
  const descRow = cells => `<tr class="ant-descriptions-row">${cells}</tr>`
  const label = t => `<td class="ant-descriptions-item-label">${t}</td>`
  const content = (t, span) => `<td class="ant-descriptions-item-content"${span ? ` colspan="${span}"` : ''}>${t}</td>`
  const longCategory = '工程建设手续 / 施工图审查 / 施工图审查合格书、工程建设手续 / 竣工验收备案 / 竣工验收报告、工程建设手续 / 移交 / 道路工程移交'

  const descriptions = `
    <div class="ant-descriptions ant-descriptions-bordered ant-descriptions-small detail-desc">
      <div class="ant-descriptions-view">
        <table>
          <tbody>
            ${descRow(label('档案号') + content('DA-2025-DEMO01') + label('档案名称') + content('仁昌路、诚盛道、诚润道道路工程竣工档案') + label('档案年度') + content('2025'))}
            ${descRow(label('责任部门') + content('住建') + label('配套负责人') + content('李伟') + label('归档日期') + content('2025-03-18'))}
            ${descRow(label('档案类别') + content(longCategory) + label('文件数 / 总大小') + content('5 个 / 14.90 MB') + label('创建信息') + content('zhangsan · 2025-03-18 10:15:00'))}
            ${descRow(label('备注') + content('竣工阶段全要件（含竣工图）', 5))}
          </tbody>
        </table>
      </div>
    </div>`

  const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<link rel="stylesheet" href="file:///${antdCss.replace(/\\/g, '/')}">
<style>${css}</style>
<style>
  /* 只为截图方便：把弹窗放进一个固定尺寸的容器里 */
  html, body { margin: 0; padding: 0; background: #cfd8e3; }
  .stage { width: 1280px; height: 360px; position: relative; }
  .stage .ant-modal-content { height: 360px; }
  .stage-compare { width: 1280px; }
  /* dist 里没有 anticon 字体文件，用占位方块代替图标，占位宽度尽量贴近真实图标 */
  .anticon { display: inline-block; width: 14px; height: 14px; }
</style>
</head>
<body>
<div class="stage archive-detail-modal">
  <div class="ant-modal" style="width:1280px">
    <div class="ant-modal-content">
      <button type="button" class="ant-modal-close">
        <!-- 字体缺失，直接用文字 × 代替图标，位置与 .ant-modal-close-x 的 56×56 完全一致 -->
        <span class="ant-modal-close-x">×</span>
      </button>
      <div class="ant-modal-body">
        <div class="archive-detail" ${SCOPE}>
          <header class="archive-detail__head"${S}>${headMain}${headBar}</header>
          <div class="archive-detail__tabs"${S}>
            <div class="archive-detail__panel"${S}>${descriptions}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>
<div class="stage-compare">${compare}</div>
</body>
</html>`

  const tmpHtml = path.join(os.tmpdir(), 'detail-mock.html')
  fs.writeFileSync(tmpHtml, html, 'utf8')

  const outPng = path.join(os.tmpdir(), 'detail-mock.png')
  if (fs.existsSync(outPng)) fs.unlinkSync(outPng)

  // ★ 独占的 --user-data-dir：不去动用户正在用的 Edge 配置目录。
  const profileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'edge-shot-'))

  const args = [
    '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
    '--force-device-scale-factor=1',
    `--user-data-dir=${profileDir}`,
    '--window-size=1280,640',
    `--screenshot=${outPng}`,
    'file:///' + tmpHtml.replace(/\\/g, '/')
  ]
  execFileSync(edge, args, { stdio: 'inherit' })

  // ★ 不能一看进程退出就认为截图好了。
  //   日志（--enable-logging=stderr）里写得很清楚：
  //     WARNING:chrome_browser_main_win.cc  Edge is running elevated: 1
  //     VERBOSE1:base\win\elevation_util.cc  RunDeElevated: Started process, PID: xxx
  //   Edge 拒绝以管理员身份运行，会**再拉起一个降权子进程**后自己立刻退出（~150ms）。
  //   真正的截图是那个子进程稍后写的 —— 早先「偶发失败」就是这么来的（其实只是查得太早）。
  //   所以这里改为轮询等文件出现，并且等大小稳定，而不是信退出码。
  const deadline = Date.now() + 30000
  const sleep = ms => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms)
  let lastSize = -1
  while (Date.now() < deadline) {
    const size = fs.existsSync(outPng) ? fs.statSync(outPng).size : 0
    if (size > 0 && size === lastSize) break
    lastSize = size
    sleep(300)
  }

  const finalSize = fs.existsSync(outPng) ? fs.statSync(outPng).size : 0
  if (finalSize === 0) {
    throw new Error('等待 30s 仍没有产出截图：' + outPng)
  }
  fs.rmSync(profileDir, { recursive: true, force: true })
  console.log('截图：' + outPng + '（' + finalSize + ' 字节）')
}).catch(e => {
  console.error('渲染失败：', e && e.message ? e.message : e)
  process.exit(1)
})
