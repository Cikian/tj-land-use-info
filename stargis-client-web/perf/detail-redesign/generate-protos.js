/**
 * 用「真实 SFC 编译出来的 scoped CSS」渲染整页样例（提级论证详情 / 档案详情）。
 * 与 render-sfc.js 的区别：这里出的是完整页面（弹窗外壳 + 分组卡 + 描述列表），
 * 用于交付前肉眼验收，而不是只看组件本身。
 * 用法：node perf/detail-redesign/generate-protos.js
 */
const fs = require('fs')
const path = require('path')
const compiler = require('vue-template-compiler')
const less = require('less')

const root = path.resolve(__dirname, '../..')
const SCOPE = 'data-v-proto01'
const MIXIN_STUBS = [
  [/\.screen-ellipsis\(\);/g, 'overflow:hidden;white-space:nowrap;text-overflow:ellipsis;'],
  [/\.screen-placeholder\(\);/g, 'color:var(--screen-text-mute);'],
]

function scopeSelectors (css) {
  // 先把注释去掉：真实的 vue-loader 走 postcss AST，注释里的 `1fr` 之类不会被当选择器；
  // 这里的字符串实现会把注释文本也当选择器，必须先去注释（否则会把 minmax(0, 1fr) 改坏）。
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, '')
  let out = ''
  let i = 0
  const addScope = (sel) => sel.split(',').map((s) => {
    const t = s.trim()
    if (!t) return t
    return t.replace(/(::?[a-z-]+(\([^)]*\))?)*$/, (m) => `[${SCOPE}]${m}`)
  }).join(', ')
  while (i < clean.length) {
    const open = clean.indexOf('{', i)
    if (open === -1) { out += clean.slice(i); break }
    const head = clean.slice(i, open)
    const isAtRule = /^\s*@/.test(head)
    out += isAtRule ? head : addScope(head) + ' '
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

async function compileScoped (rel) {
  const file = path.join(root, rel)
  const sfc = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
  let raw = sfc.styles[0].content.replace(/@import[^;]+;/g, '')
  MIXIN_STUBS.forEach(([re, to]) => { raw = raw.replace(re, to) })
  const out = await less.render(raw, { filename: file })
  return scopeSelectors(out.css)
}

/** 描述列表的条目：{ label, value, tone, stack, width } */
function cell (it) {
  const sc = ` ${SCOPE}`
  const cls = ['screen-descriptions__item']
  if (it.stack) cls.push('is-stack')
  if (it.rowEnd) cls.push('is-row-end')
  const dt = 'screen-descriptions__label' + (it.stack ? ' is-stack' : '')
  const dd = 'screen-descriptions__value' + (it.stack ? ' is-stack' : '') +
    (it.tone ? ' is-' + it.tone : '') + (it.empty ? ' is-empty' : '')
  // 标签文字包一层 span（与组件一致），否则量不到真实的省略行为
  return `<div class="${cls.join(' ')}"${sc} data-label="${it.label}">` +
    `<dt class="${dt}"${sc}><span class="screen-descriptions__label-text"${sc}>${it.label}</span></dt>` +
    `<dd class="${dd}"${sc}>${it.value}</dd></div>`
}

/**
 * labelMax 对应组件新增的 --sd-label-max（标签列封顶宽度）。
 * 详情页各分组的最长标签差别很大，逐组给不同的封顶值：
 * 宽标签组给 96px，窄标签组给 76px，避免短标签后面拖一大段空白。
 */
function descList (items, { variant = 'flat', columns = 4, labelMax = '96px' }) {
  const cls = ['screen-descriptions', 'is-md', variant === 'flat' ? 'is-flat' : 'is-bordered']
  const style = `--sd-input-cols:${columns};--sd-label-max:${labelMax}`
  return `<dl class="${cls.join(' ')}" ${SCOPE} style="${style}" data-cols="${columns}">\n        ` +
    items.map(cell).join('\n        ') + `\n      </dl>`
}

const TOKENS = `:root{
  --screen-bg:rgba(1,14,17,.72); --screen-panel-bg:rgba(8,22,42,.92); --screen-panel-bg-solid:#08182e;
  --screen-panel-head-bg:rgba(9,36,66,.85); --screen-row-alt:rgba(55,132,215,.08);
  --screen-border:rgba(55,132,215,.32); --screen-border-soft:rgba(55,132,215,.18);
  --screen-border-strong:rgba(130,198,255,.62);
  --screen-accent:#82c6ff; --screen-accent-soft:#66afd4; --screen-accent-deep:#1d335d; --screen-accent-glow:rgba(130,198,255,.32);
  --screen-text:#fff; --screen-text-sub:#66afd4; --screen-text-mute:#4a7396;
  --screen-success:#43e972; --screen-warning:#f5a524; --screen-danger:#f2624c; --screen-info:#55b9ff;
  --screen-radius-sm:3px; --screen-radius:4px; --screen-radius-lg:6px; --screen-radius-pill:999px;
  --screen-space-1:4px; --screen-space-2:8px; --screen-space-3:12px; --screen-space-4:16px; --screen-space-5:20px;
  --screen-font-xs:12px; --screen-font-sm:13px; --screen-font-md:14px; --screen-font-lg:16px; --screen-font-xl:18px;
  --screen-shadow:0 8px 24px rgba(0,0,0,.45); --screen-shadow-inset:inset 0 0 24px rgba(130,198,255,.06);
  --screen-blur:12px;
  --screen-font-family:'Source Han Sans SC','PingFang SC','Microsoft YaHei',-apple-system,sans-serif;
  --screen-font-number-family:var(--screen-font-family);
}`

/** 弹窗外壳 + 分组卡等「页面骨架」CSS（与组件内联样式保持一致） */
const SHELL = `*{box-sizing:border-box}
body{margin:0;font-family:var(--screen-font-family);color:var(--screen-text);
  background:radial-gradient(1200px 600px at 50% -10%,rgba(55,132,215,.18),transparent 70%),#04091a}
.screen-modal{position:fixed;inset:0;display:flex;align-items:center;justify-content:center;padding:var(--screen-space-5)}
.screen-modal__dialog{display:flex;flex-direction:column;width:100%;height:100%;min-height:0;
  background:var(--screen-panel-bg-solid);border:1px solid var(--screen-border);border-radius:var(--screen-radius);
  box-shadow:0 20px 60px rgba(0,0,0,.55),var(--screen-shadow-inset)}
.screen-modal__head{display:flex;align-items:center;gap:var(--screen-space-3);flex:0 0 auto;min-height:46px;
  padding:0 var(--screen-space-4) 0 var(--screen-space-3);background:var(--screen-panel-head-bg);
  border-bottom:1px solid var(--screen-border-soft)}
.screen-modal__bar{flex:0 0 auto;width:3px;height:15px;border-radius:var(--screen-radius-pill);
  background:linear-gradient(180deg,var(--screen-accent) 0%,var(--screen-accent-deep) 100%);box-shadow:0 0 8px var(--screen-accent-glow)}
.screen-modal__title{margin:0;font-size:var(--screen-font-lg);font-weight:600;letter-spacing:.03em}
.screen-modal__head-extra{display:flex;align-items:center;gap:var(--screen-space-2);margin-left:auto;min-width:0}
.screen-modal__body{flex:1 1 auto;min-height:0;padding:var(--screen-space-4);overflow-y:auto}
.screen-btn{display:inline-flex;align-items:center;gap:5px;min-height:28px;padding:0 10px;font-family:inherit;
  font-size:var(--screen-font-sm);color:var(--screen-text);background:rgba(6,20,40,.72);
  border:1px solid var(--screen-border);border-radius:var(--screen-radius-sm)}
.screen-tag{display:inline-flex;align-items:center;padding:0 6px;font-size:var(--screen-font-xs);line-height:16px;
  color:var(--screen-accent-soft);background:rgba(130,198,255,.08);border:1px solid var(--screen-border);
  border-radius:var(--screen-radius-pill);white-space:nowrap}
.screen-tag.is-muted{color:var(--screen-text-mute);background:var(--screen-row-alt);border-color:var(--screen-border-soft)}
.screen-tag.is-info{color:var(--screen-info);background:rgba(130,198,255,.08)}
.screen-tag.is-success{color:var(--screen-success);background:rgba(67,233,114,.1)}
.screen-tabs{display:flex;gap:var(--screen-space-1)}
.screen-tabs__item{padding:3px 12px;font-family:inherit;font-size:var(--screen-font-sm);color:var(--screen-text-sub);
  background:transparent;border:1px solid transparent;border-radius:var(--screen-radius-sm)}
.screen-tabs__item.is-active{color:var(--screen-accent);background:rgba(130,198,255,.12);border-color:var(--screen-border-strong)}
/* 页面骨架 */
.pg{display:flex;flex-direction:column;gap:var(--screen-space-2);min-width:0;height:100%}
.pg__head{display:flex;align-items:baseline;gap:var(--screen-space-3);flex-wrap:wrap;min-width:0}
.pg__no{flex:none;font-variant-numeric:tabular-nums;font-size:var(--screen-font-sm);letter-spacing:.02em;color:var(--screen-accent)}
.pg__name{margin:0;min-width:0;font-size:var(--screen-font-xl);font-weight:600}
.pg__pane{display:flex;flex-direction:column;gap:var(--screen-space-2);min-width:0}
.pg__row{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));align-items:start;gap:var(--screen-space-2);min-width:0}
.pg__block{min-width:0;padding:var(--screen-space-2) var(--screen-space-3) var(--screen-space-1);
  background:var(--screen-panel-bg);border:1px solid var(--screen-border-soft);border-radius:var(--screen-radius);
  box-shadow:var(--screen-shadow-inset);-webkit-backdrop-filter:blur(var(--screen-blur));backdrop-filter:blur(var(--screen-blur))}
.pg__block--tight{--sd-label-max:76px}
.pg__title{display:flex;align-items:center;gap:var(--screen-space-2);margin:0 0 var(--screen-space-1);
  font-size:var(--screen-font-sm);font-weight:600;color:var(--screen-text)}
.pg__title::before{content:'';flex:none;width:3px;height:12px;border-radius:var(--screen-radius-pill);
  background:linear-gradient(180deg,var(--screen-accent) 0%,var(--screen-accent-deep) 100%)}
.pg__hint{font-size:var(--screen-font-xs);font-weight:400;color:var(--screen-text-mute)}
.pg__modalhead{display:flex;align-items:center;gap:var(--screen-space-3);flex:0 0 auto;min-height:46px;
  padding:0 var(--screen-space-4) 0 var(--screen-space-3);background:var(--screen-panel-head-bg);border-bottom:1px solid var(--screen-border-soft)}`

function page (title, headerExtra, inner) {
  const sc = ` ${SCOPE}`
  return `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>${title}</title>
<style>${TOKENS}${SHELL}</style>
</head><body>
<div class="screen-modal">
  <div class="screen-modal__dialog"${sc}>
    <header class="screen-modal__head"${sc}>
      <span class="screen-modal__bar"${sc}></span>
      <h2 class="screen-modal__title"${sc}>${title}</h2>
      <div class="screen-modal__head-extra"${sc}>${headerExtra}</div>
    </header>
    <div class="screen-modal__body"${sc}>
${inner}
    </div>
  </div>
</div>
</body></html>`
}

const escHeader = `
        <span class="screen-tag is-muted" ${SCOPE}>未办理</span>
        <span class="screen-tag is-muted" ${SCOPE}>论证结果：未登记</span>
        <span class="screen-tag is-info" ${SCOPE}>2 个材料</span>
        <span class="screen-tag is-info" ${SCOPE}>0 条意见记录</span>
        <button class="screen-btn" ${SCOPE}>编辑</button>
        <button class="screen-btn" ${SCOPE}>打印</button>
        <button class="screen-btn" ${SCOPE}>刷新</button>`

const archHeader = `
        <span class="screen-tag is-success" ${SCOPE}>已归档</span>
        <span class="screen-tag is-muted" ${SCOPE}>项目档案</span>
        <span class="screen-tag is-muted" ${SCOPE}>密级：内部</span>
        <span class="screen-tag is-info" ${SCOPE}>6 个卷内文件</span>
        <button class="screen-btn" ${SCOPE}>编辑</button>
        <button class="screen-btn" ${SCOPE}>导出该项目档案</button>`

async function main () {
  const css = await compileScoped('src/components/screen/ScreenDescriptions.vue')
  const sc = ` ${SCOPE}`

  // ---------- 提级论证项目详情 ----------
  const escBase = descList([
    { label: '项目编号', value: 'TJ-2026-DEMO01', tone: 'accent' },
    { label: '项目类型', value: '产业类' },
    { label: '申报单位', value: '滨海新区发展和改革局' },
    { label: '申报时间', value: '2026-09-10', tone: 'number' },
    { label: '项目规模', value: '总建筑面积 18.6 万㎡' },
    { label: '总投资（亿元）', value: '12.8', tone: 'number' },
    { label: '项目名称', value: '滨海新区智慧产业园项目', stack: true },
    { label: '建设地点', value: '高新区创新大道 88 号', stack: true },
    { label: '项目概述', value: '项目拟建设高新技术产业基地，主要包括研发办公楼、生产车间、配套设施等，旨在打造集研发、生产、孵化于一体的高新技术产业集聚区。', stack: true }
  ], { columns: 2, labelMax: '96px' })

  const escLand = descList([
    { label: '行政区划', value: '滨海新区' },
    { label: '功能区', value: '高新区' },
    { label: '规划用地性质', value: '工业用地' },
    { label: '是否土地整理项目', value: '否' },
    { label: '地块面积（㎡）', value: '62800', tone: 'number' },
    { label: '关联出让宗地编号', value: '津滨高（挂）2018-17', tone: 'accent' },
    { label: '地块名称', value: '高新区创新大道 88 号地块', stack: true },
    { label: '关联配套项目', value: '海缘东路道路、排水、照明工程', stack: true }
  ], { columns: 3, labelMax: '96px' })

  const escArg = descList([
    { label: '提级论证事由', value: '项目总投资 12.8 亿元，超出滨海新区自行审批权限，需报市发展改革委提级论证。', stack: true },
    { label: '提级论证依据', value: '《天津市政府投资项目管理办法》第十七条；《滨海新区重点项目管理实施细则》第九条。', stack: true },
    { label: '必要性说明', value: '项目建成后可承接高新区 30 余家高新技术企业入驻，年产值预计 25 亿元。', stack: true },
    { label: '可行性说明', value: '用地已取得出让合同，周边道路、排水、照明配套工程已进入实施阶段。', stack: true },
    { label: '论证事项内容', value: '建设规模、投资估算、资金来源、建设时序、配套条件落实情况。', stack: true }
  ], { columns: 2, labelMax: '96px' })

  const escResult = descList([
    { label: '办理状态', value: '<span class="screen-tag is-muted" ' + SCOPE + '>未办理</span>' },
    { label: '论证结果', value: '—', empty: true },
    { label: '论证组织单位', value: '—', empty: true },
    { label: '论证会日期', value: '—', empty: true },
    { label: '论证完成日期', value: '—', empty: true },
    { label: '材料数 / 意见记录数', value: '2 个 / 0 条', tone: 'number' },
    { label: '创建信息', value: '系统管理员 · 2026-09-10 09:12' }
  ], { columns: 2, labelMax: '120px' })

  const escInner = `      <div class="pg"${sc}>
        <header class="pg__head"${sc}>
          <span class="pg__no"${sc}>TJ-2026-DEMO01</span>
          <h3 class="pg__name"${sc}>滨海新区智慧产业园项目</h3>
        </header>
        <div class="screen-tabs"${sc}>
          <button class="screen-tabs__item is-active"${sc}>基本信息</button>
          <button class="screen-tabs__item"${sc}>提级论证材料</button>
          <button class="screen-tabs__item"${sc}>审核意见记录</button>
        </div>
        <div class="pg__pane"${sc}>
          <section class="pg__block"${sc}>
            <h4 class="pg__title"${sc}>项目基本信息<span class="pg__hint"${sc}>项目维度，共 9 项</span></h4>
            ${escBase}
          </section>
          <section class="pg__block"${sc}>
            <h4 class="pg__title"${sc}>关联地块信息（选填）<span class="pg__hint"${sc}>论证对象是项目，地块信息仅作上下文</span></h4>
            ${escLand}
          </section>
          <section class="pg__block"${sc}>
            <h4 class="pg__title"${sc}>提级论证信息<span class="pg__hint"${sc}>长文本逐条展示，两列并排</span></h4>
            ${escArg}
          </section>
          <section class="pg__block"${sc}>
            <h4 class="pg__title"${sc}>论证结果与办理状态<span class="pg__hint"${sc}>流程在线下办理，以下为人工登记</span></h4>
            ${escResult}
          </section>
        </div>
      </div>`

  // ---------- 档案详情 ----------
  // 版式：档案属性 2 列（值列够宽，长文本条目 stack 独占整行）；
  //       关联项目 / 其他信息 各 3 列并排，标签窄，标签列封顶 76px。
  const archAttr = descList([
    { label: '档案号', value: 'DA-2026-BH-0007', tone: 'accent' },
    { label: '档案类型', value: '项目档案' },
    { label: '密级', value: '内部' },
    { label: '保管期限', value: '永久' },
    { label: '档案年度', value: '2026', tone: 'number' },
    { label: '归档日期', value: '2026-09-12', tone: 'number' },
    { label: '责任部门', value: '滨海新区住建委' },
    { label: '配套负责人', value: '张伟' },
    { label: '档案状态', value: '已归档' },
    { label: '卷内文件', value: '6 个 · 12.4 MB', tone: 'number' },
    { label: '档案名称', value: '滨海新区智慧产业园项目档案', stack: true },
    { label: '档案类别', value: '项目立项文件、规划许可文件、施工许可文件', stack: true }
  ], { columns: 2, labelMax: '96px' })

  const archProject = descList([
    { label: '出让宗地编号', value: '津滨高（挂）2018-17', tone: 'accent' },
    { label: '所属行政区', value: '滨海新区' },
    { label: '配套设施类别', value: '道路、排水、照明' },
    { label: '项目来源', value: '政府投资' },
    { label: '地块名称', value: '高新区创新大道 88 号地块', stack: true },
    { label: '配套项目', value: '海缘东路道路、排水、照明工程', stack: true }
  ], { columns: 3, labelMax: '76px' })

  const archOther = descList([
    { label: '创建信息', value: '系统管理员 · 2026-09-12 10:24' },
    { label: '最后更新', value: '张伟 · 2026-09-14 09:02' },
    { label: '备注', value: '纸质原件已移交档案室，电子件与纸质件一致。', stack: true }
  ], { columns: 3, labelMax: '76px' })

  const archInner = `      <div class="pg"${sc}>
        <header class="pg__head"${sc} style="padding-bottom:8px;border-bottom:1px solid var(--screen-border-soft)">
          <span class="pg__no"${sc} style="font-size:16px;font-weight:600">DA-2026-BH-0007</span>
          <h3 class="pg__name"${sc} style="font-size:16px">滨海新区智慧产业园项目档案</h3>
        </header>
        <div class="screen-tabs"${sc}>
          <button class="screen-tabs__item is-active"${sc}>基本信息</button>
          <button class="screen-tabs__item"${sc}>档案文件</button>
          <button class="screen-tabs__item"${sc}>收发文情况</button>
          <button class="screen-tabs__item"${sc}>操作记录</button>
        </div>
        <div class="pg__pane"${sc}>
          <section class="pg__block"${sc}>
            <h4 class="pg__title"${sc}>档案属性<span class="pg__hint"${sc}>档案本体信息，共 12 项</span></h4>
            ${archAttr}
          </section>
          <div class="pg__row"${sc}>
            <section class="pg__block pg__block--tight"${sc}>
              <h4 class="pg__title"${sc}>关联项目</h4>
              ${archProject}
            </section>
            <section class="pg__block pg__block--tight"${sc}>
              <h4 class="pg__title"${sc}>其他信息</h4>
              ${archOther}
            </section>
          </div>
        </div>
      </div>`

  const escHtml = page('项目详情', escHeader, escInner).replace('</style>', css + '\n</style>')
  const archHtml = page('档案详情', archHeader, archInner).replace('</style>', css + '\n</style>')

  fs.writeFileSync(path.join(__dirname, 'final-escalation.html'), escHtml, 'utf8')
  fs.writeFileSync(path.join(__dirname, 'final-archive.html'), archHtml, 'utf8')
  console.log('已生成 final-escalation.html / final-archive.html（使用真实编译的 scoped CSS）')
}

main().catch((e) => { console.error(e); process.exit(1) })
