/**
 * 提级论证管理 · 共享枚举、配色语气与展示辅助（**唯一实现**）
 * ===============================================================
 * 为什么单独抽一个文件：
 *   admin-client 的版本把这批常量放在 api/land/escalation.js 里，页面再各抄一份
 *   statusColor / formatSize / opinionSummary；大屏这一版全部收敛到本文件，
 *   页面里不再各写一套，避免同一个值在列表、台账、详情三处显示成三种样子。
 *
 * 【枚举来源与「为什么硬编码」】
 *   · 办理状态 4 值（未办理/办理中/已办结/已归档）：方案 2.2 明确它是**固定枚举、不入字典**
 *     （后台误改会让状态与配色对不上），后端常量见 EscalationStatus。
 *   · 论证结果 5 值、项目类型 4 值、材料类型 4 值：后端确实建了 sys_dict
 *     （land_escalation_arg_result / _project_type / _material_type，见
 *     server/.../sql/escalation/03_escalation_dict.sql），但 **item_value 就是中文文本**，
 *     且本工程没有 admin-client 那套 JDictSelectTag / getDictItems 字典组件
 *     （任务书也明确不要引入），因此这里按字典脚本的取值固化一份，
 *     与线上字典逐字一致：改字典必须同步改这里，否则会出现「存得进去、下拉里选不到」。
 *   · 是否土地整理项目复用库内既有字典 yn（1 是 / 0 否）。
 *
 * 【配色约定】ScreenTag 只接受 accent / success / warning / danger / info / muted
 *   六个 tone（见 components/screen/ScreenTag.vue 的 TONES），
 *   所以本模块的语义色一律映射到这六个值，**不出现任何颜色字面量**，
 *   颜色全部来自 --screen-* 令牌（见 README §1 与硬性规则 3）。
 */

/* ============================ 枚举 ============================ */

/** 办理状态（固定 4 值，不入字典） */
export const STATUS_OPTIONS = ['未办理', '办理中', '已办结', '已归档']

/**
 * 论证结果（字典 land_escalation_arg_result 的 5 个 item_value）。
 * ★ 与审核意见的 5 个快捷短语同一套词表，但语义不同：
 *   这里是「项目最终论证结论」，快捷短语是「本次意见的结论短语」，两者不要互相推导。
 */
export const ARG_RESULT_OPTIONS = ['通过', '基本通过', '需补充材料', '需进一步论证', '不通过']

/**
 * 项目类型（字典 land_escalation_project_type：市级项目 / 区级项目 / 待确认）
 *
 * ★ 2026-11 口径变更（清单 P2-10）：中心要的是「市级项目 / 区级项目」两级，
 *   旧的「产业类 / 基础设施类 / 民生类 / 其他」四类不再作为候选值。
 *   三类的关系是：
 *     · 市级项目 / 区级项目 —— 新录入与检索只用这两个值；
 *     · 待确认             —— **历史过渡值**，不是业务分类。
 *
 * ★ 为什么不把历史四类「猜」成市/区级：
 *   产业类 / 基础设施类 / 民生类 与「市级/区级」不是同一个分类维度
 *   （前者是项目属性、后者是审批层级），任何映射都是编造。
 *   用户已确认：无法可靠映射的历史值一律置为「待确认」，由业务人工逐条确认后改写。
 *   历史数据的批量置值与人工确认清单见
 *   `server/.../sql/escalation/07_escalation_project_type_v2.sql`
 *   （字典项改成这三项 + 存量值置「待确认」+ 核对/清单查询）。
 *
 * ★ 改这里必须同步三处：字典 SQL（03 与 07）、后端 EscalationProject 的取值常量、
 *   admin-client 的字典项（管理端走 JDictSelectTag，改字典即生效）。
 */
export const PROJECT_TYPE_OPTIONS = ['市级项目', '区级项目', '待确认']

/** 历史过渡值（人工确认清单里的那一档），单独导出便于调用点写判断而不是硬编码中文 */
export const PROJECT_TYPE_PENDING = '待确认'

/** 旧的四类取值：只用于「识别历史值」，**不再作为候选值**（清单 P2-10 第 3 条） */
export const PROJECT_TYPE_LEGACY = ['产业类', '基础设施类', '民生类', '其他']

/**
 * 项目类型展示文本。
 *
 * ★ 为什么展示层也要兜一道：数据迁移脚本是幂等的，但「脚本没跑 / 跑到一半」
 *   的时候列表里仍可能出现历史四类。此时**不能原样显示成一种新的项目类型**，
 *   否则界面就出现了清单明确要求去掉的旧口径；统一显示「待确认」，
 *   与迁移后的库内值一致，也提示这条记录需要人工确认。
 *
 * @param {string} value 库里的 project_type
 * @param {string} [dictTextFromServer] 后端 @Dict 给的文本（有则以它为准）
 * @returns {string} 三值之一或原值（空值返回空串，由调用点决定占位符）
 */
export function projectTypeText (value, dictTextFromServer) {
  if (value === null || value === undefined || value === '') return ''
  const text = dictTextFromServer || String(value)
  return PROJECT_TYPE_OPTIONS.indexOf(text) > -1 ? text : PROJECT_TYPE_PENDING
}

/** 材料类型（字典 land_escalation_material_type：申请材料/论证报告/支撑材料/其他） */
export const MATERIAL_TYPE_OPTIONS = ['申请材料', '论证报告', '支撑材料', '其他']

/** 功能区（后端未做字典，按 admin-client 版本的既有枚举固化） */
export const GNQ_OPTIONS = ['生态城', '经开区', '高新区', '保税区', '其他']

/**
 * 是否土地整理项目（复用字典 yn，取值是字符串 '1'/'0'）。
 * 必须用字符串：ScreenSelect / ScreenRadioGroup 的 value 只接受 String/Number，
 * 提交时再转成后端要的 Integer（见 ynNumber）。
 */
export const YN_OPTIONS = [
  { value: '1', label: '是' },
  { value: '0', label: '否' }
]

/** 字典编码（如需在页面上直连字典接口时使用；本模块选项数组已固化，仅作备注） */
export const DICT = {
  projectType: 'land_escalation_project_type',
  argResult: 'land_escalation_arg_result',
  materialType: 'land_escalation_material_type',
  yn: 'yn'
}

/** 记录类型（t_escalation_record.record_type） */
export const RECORD_TYPES = ['审核意见', '补充说明', '其他']

/** 意见正文上限（原型硬性要素：0/1000 实时计数） */
export const RECORD_MAX_LENGTH = 1000

/** 5 个快捷短语（点击回填意见框，回填后仍可编辑） */
export const RECORD_QUICK_PHRASES = ['同意', '基本同意', '请补充材料', '需进一步论证', '不同意']

/** 材料上传白名单与上限（设计文档 3.2 第 9 条 / Q5：50MB） */
export const MATERIAL_MAX_SIZE_MB = 50
export const MATERIAL_ALLOWED_EXT = [
  'pdf', 'doc', 'docx', 'xls', 'xlsx',
  'wps', 'wpt', 'et', 'ett', 'dps', 'dpt', 'ofd',
  'jpg', 'jpeg', 'png'
]
export const MATERIAL_ALLOWED_EXT_TEXT = 'pdf / doc(x) / xls(x) / wps / et / dps / ofd / jpg / png'

/** 台账顶部状态页签（'all' 是「全部」，其余取 STATUS_OPTIONS） */
export const STATUS_TABS = ['all'].concat(STATUS_OPTIONS)

/** 上传业务子目录的根：/escalation/{yyyy}/{MM}，意见附件落在 /escalation/opinion/{yyyy}/{MM} */
export const BIZ_ROOT = 'escalation'

/* ============================ 选项构造 ============================ */

/** 字符串数组 → ScreenSelect / ScreenRadioGroup 需要的 { value, label } */
export function toOptions (list) {
  return (list || []).map((item) => ({ value: item, label: item }))
}

/* ============================ 配色语气 ============================ */

/**
 * 办理状态 → ScreenTag tone。
 * 需求侧的口径是「灰 / 蓝 / 绿 / 青」，ScreenTag 没有 green / cyan 这两个名字，
 * 所以落到同义的 success（绿）与 info（青）；accent 承担「蓝」。
 */
export function statusTone (status) {
  switch (status) {
    case '办理中':
      return 'accent'
    case '已办结':
      return 'success'
    case '已归档':
      return 'info'
    default:
      // 未办理 / 未知 → 弱化灰
      return 'muted'
  }
}

/** 论证结果 → ScreenTag tone（通过=绿、基本通过=蓝、需补充材料=橙、需进一步论证=蓝、不通过=红） */
export function argResultTone (result) {
  switch (result) {
    case '通过':
      return 'success'
    case '基本通过':
      return 'accent'
    case '需补充材料':
      return 'warning'
    case '需进一步论证':
      return 'info'
    case '不通过':
      return 'danger'
    default:
      return 'muted'
  }
}

/** 结论短语（快捷短语）→ ScreenTag tone；与论证结果是两套词表，所以单独一份 */
export function actionTone (action) {
  switch (action) {
    case '同意':
      return 'success'
    case '基本同意':
      return 'accent'
    case '请补充材料':
      return 'warning'
    case '需进一步论证':
      return 'info'
    case '不同意':
      return 'danger'
    default:
      return 'muted'
  }
}

/* ============================ 展示辅助 ============================ */

/** 字典值 → 文本；后端 @Dict 给的 _dictText 优先（dictTextFromServer） */
export function dictText (dictCode, value, dictTextFromServer) {
  if (value === null || value === undefined || value === '') return ''
  if (dictTextFromServer) return dictTextFromServer
  // 本模块的字典取值就是中文文本，翻译即恒等；保留函数是为了调用点统一，
  // 将来若字典改成「编码 + 文本」两层，只需要改这里。
  return value
}

/** 是否土地整理项目（1/0 或 '1'/'0' 或 true）→ '是' / '否' / '' */
export function ynText (value) {
  if (value === null || value === undefined || value === '') return ''
  if (value === 1 || value === '1' || value === true) return '是'
  if (value === 0 || value === '0' || value === false) return '否'
  return String(value)
}

/** 是否土地整理项目 → 后端要的 Integer（取不到时给 null，保持「未填写」而不是 0） */
export function ynNumber (value) {
  if (value === null || value === undefined || value === '') return null
  if (value === true) return 1
  if (value === false) return 0
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

/**
 * 投资额格式化：12.8000 → '12.8'，12.0000 → '12'。
 * 不能直接用 toFixed：总投资是亿元小数，尾部 0 全留着会让台账列宽被撑开。
 */
export function formatInvestment (value) {
  if (value === null || value === undefined || value === '') return ''
  const num = Number(value)
  if (!Number.isFinite(num)) return String(value)
  return num.toFixed(4).replace(/\.?0+$/, '')
}

/** 字节 → 可读大小；空值返回 '—'（0 字节不是有效文件，按空处理） */
export function formatSize (bytes, precision = 1) {
  const size = Number(bytes)
  if (!size || size <= 0) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = size
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(unitIndex === 0 ? 0 : precision)} ${units[unitIndex]}`
}

/** 取小写扩展名（不含点）；没有扩展名时返回空串（调用方据此拒绝上传） */
export function resolveExt (fileName) {
  const text = String(fileName || '')
  const index = text.lastIndexOf('.')
  return index > -1 ? text.slice(index + 1).toLowerCase() : ''
}

/** 去掉扩展名（材料默认「文件题名」用） */
export function stripExt (fileName) {
  const text = String(fileName || '')
  const index = text.lastIndexOf('.')
  return index > 0 ? text.slice(0, index) : text
}

/** 意见正文摘要（台账「最新审核意见」列）；不做摘要时返回原文 */
export function opinionSummary (text, max = 40) {
  if (text === null || text === undefined || text === '') return ''
  const value = String(text).replace(/\s+/g, ' ').trim()
  return value.length > max ? `${value.slice(0, max)}…` : value
}

/** 两个字段拼一行摘要，都为空时返回占位符 */
export function joinInfo (a, b, separator = ' · ', placeholder = '—') {
  const parts = [a, b].filter((item) => item !== undefined && item !== null && item !== '')
  return parts.length ? parts.join(separator) : placeholder
}

/**
 * 意见记录的附件：attachment_ids 是逗号分隔的存储路径串。
 * 文件名从路径末段取（后端记录接口没有单独的下载端点，附件按静态资源地址回看）。
 */
export function parseAttachments (ids) {
  if (!ids) return []
  return String(ids)
    .split(',')
    .filter(Boolean)
    .map((path) => {
      const clean = path.split('?')[0]
      const segments = clean.split('/')
      return { path: clean, name: segments[segments.length - 1] }
    })
}

/**
 * 「已办结但论证结果为空」——设计文档 10-Risk7 的轻量校验。
 * 这不是错误，只是一个需要提醒的数据缺口，所以在列表里用**图标 + 文字**提示，
 * 不靠颜色（README §5：不能只靠颜色传达信息）。
 */
export function isClosedWithoutResult (row) {
  const status = row && row.status
  const result = row && row.argResult
  return status === '已办结' && (result === null || result === undefined || result === '')
}

/* ============================ 日期 / 路径 ============================ */

/** Date → 'YYYY-MM-DD' */
export function formatDate (date) {
  const d = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(d.getTime())) return ''
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

/** 今天 'YYYY-MM-DD'（申报时间默认值） */
export function today () {
  return formatDate(new Date())
}

/** 当前日期时间 'YYYY-MM-DD HH:mm:ss'（打印抬头、上传时间本地展示） */
export function nowText () {
  const d = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 当前日期时间 'YYYY-MM-DD HH:mm'（打印抬头用，秒没有意义） */
export function nowMinuteText () {
  return nowText().slice(0, 16)
}

/**
 * 上传业务子目录：/escalation[/sub]/{yyyy}/{MM}
 * 后端按 biz 字段建分目录，避免所有文件堆在同一层。
 * @param {Date} [date]
 * @param {string} [sub] 二级业务名，例如 'opinion'
 */
export function buildBizPath (date = new Date(), sub = '') {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const suffix = sub ? `/${sub}` : ''
  return `/${BIZ_ROOT}${suffix}/${date.getFullYear()}/${month}`
}

/** 从 'YYYY-MM-DD' 取年份（数字），取不到回退今年（预生成项目编号用） */
export function yearOf (dateText) {
  const year = parseInt(String(dateText || '').slice(0, 4), 10)
  return Number.isFinite(year) ? year : new Date().getFullYear()
}

/* ============================ 查询 / 分页 ============================ */

/** 去掉 undefined / null / '' 的条件，避免拼出 status= 这种空条件 */
export function compactQuery (query) {
  const result = {}
  Object.keys(query || {}).forEach((key) => {
    const value = query[key]
    if (value === undefined || value === null || value === '') return
    result[key] = value
  })
  return result
}

/** 分页默认配置（录入 / 查询 / 台账共用） */
export function defaultPagination (pageSize = 10) {
  return {
    current: 1,
    pageSize,
    total: 0,
    pageSizeOptions: [10, 20, 50, 100]
  }
}

/* ============================ 统计归一化 ============================ */

/**
 * 把统计接口的返回统一成 `[{ name, count }]`。
 * 兼容三种形态：NameCount 数组、`{ name: count }` 对象、以及 count/status 别名。
 * 计数为 0 的项会被过滤（环形图与排行条上 0 值只会产生噪声）。
 */
export function toNameCountList (source) {
  let rows = []
  if (Array.isArray(source)) {
    rows = source.map((item) => ({
      name: item && item.name !== undefined && item.name !== null && item.name !== ''
        ? String(item.name)
        : '未填写',
      count: Number(item && item.count) || 0
    }))
  } else if (source && typeof source === 'object') {
    rows = Object.keys(source).map((key) => ({ name: key, count: Number(source[key]) || 0 }))
  }
  return rows.filter((item) => item.count > 0)
}

/**
 * 台账 countByStatus → `{ 状态: 数量 }`。
 * ★ 后端 StatusCount 的键名是 **status**（不是 name），也没有直接返回 Map，
 *   所以这里同时兼容 status / name / 对象三种形态，少一层就少一次「角标永远是 0」的排查。
 */
export function normalizeStatusCounts (source) {
  const result = {}
  if (Array.isArray(source)) {
    source.forEach((item) => {
      if (!item) return
      const key = item.status !== undefined ? item.status : item.name
      if (key === undefined || key === null) return
      result[key] = Number(item.count) || 0
    })
    return result
  }
  if (source && typeof source === 'object') {
    Object.keys(source).forEach((key) => {
      result[key] = Number(source[key]) || 0
    })
  }
  return result
}

/** 求和（KPI 用） */
export function sumCounts (rows) {
  return (rows || []).reduce((sum, item) => sum + (Number(item && item.count) || 0), 0)
}

/** 给统计行补上占比（ScreenBarList 需要 percent 才会显示百分比） */
export function withPercent (rows) {
  const total = sumCounts(rows)
  return (rows || []).map((item) => Object.assign({}, item, {
    percent: total ? Number(((item.count / total) * 100).toFixed(1)) : 0
  }))
}
