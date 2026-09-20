/**
 * 档案管理 · 共享枚举与展示辅助
 * ---------------------------------------------------------------
 * 为什么单独抽一个文件：
 *   admin-client 里 `secretLevels` / `depts` / `statuses` 这几个数组被复制在
 *   查询表单、编辑弹窗、统计页三处，`formatSize` 更是有 1 位小数、2 位小数、
 *   2 位小数 + 占位符三种不同实现，`statusColor` / `sourceTypeText` 也重复两份。
 *   大屏这一版全部收敛到这里，页面里不再各写一套，避免同一个值在三个地方
 *   显示成三种样子。
 *
 * 注意：这些枚举是「前端硬编码」的，后端并没有对应的 sys_dict。
 * 后端实体上的常量定义见 org.jeecg.modules.land.archive.entity.Archive，
 * 改动这里时必须同步后端，否则会出现存得进去、筛不出来的情况。
 */

/* ============================ 枚举 ============================ */

/** 密级（后端 secret_level，默认「一般」） */
export const SECRET_LEVELS = ['一般', '内部', '秘密', '机密']

/**
 * 保管期限。
 * 后端 DDL 注释写的是「永久/长期/短期」，但 admin-client 实际下发的是
 * 「永久/长期/定期」，库里已有的数据也是「定期」，所以这里沿用「定期」
 * 以保持历史数据一致（不要改成「短期」，否则老数据在字典里找不到项）。
 */
export const RETENTIONS = ['永久', '长期', '定期']

/** 责任部门（后端未做字典，前端固定枚举） */
export const DEPTS = ['发改（行政审批）', '规划', '财政', '住建', '其他']

/** 档案状态（后端 status，默认「未归档」） */
export const ARCHIVE_STATUSES = ['未归档', '归档中', '审核中', '已归档']

/** 档案类型（后端 archive_type，默认 electronic） */
export const ARCHIVE_TYPES = [
  { value: 'electronic', label: '电子档案' },
  { value: 'paper', label: '纸质档案' },
]

/** 档案来源（后端 source_type，默认 manual） */
export const SOURCE_TYPES = [
  { value: 'manual', label: '手工录入' },
  { value: 'doc_receive', label: '收文归档' },
  { value: 'doc_send', label: '发文归档' },
]

/**
 * 卷内文件允许的扩展名（小写，不含点）。
 * 与 admin-client 的 ArchiveFileTable 保持一致：
 * 办公文档 / 图片 / 文本 / 压缩包 / CAD / GIS 矢量 / 视频。
 */
export const ALLOWED_EXT = [
  'gif', 'jpg', 'jpeg', 'png', 'bmp', 'webp',
  'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf',
  'txt', 'xml', 'md', 'csv',
  'rar', 'zip', '7z',
  'dwg', 'dxf', 'shp', 'dbf', 'shx', 'prj', 'kml', 'kmz',
  'mp4', 'avi', 'mov', 'wmv',
]

/** 卷内文件允许的扩展名的人类可读描述（上传提示文案） */
export const ALLOWED_EXT_TEXT = 'pdf / doc(x) / xls(x) / ppt(x) / 图片 / dwg / shp / zip / rar 等'

/** 单文件大小上限（MB），与后端 multipart 上限一致 */
export const MAX_SIZE_MB = 200

/** 查询表单里「更多条件」折叠后仍然常驻的核心条件数 */
export const CORE_QUERY_FIELDS = ['ptxmmc', 'crzdbh', 'categoryId', 'dateRange']

/* ==================== 收发文管理（方案 2.3.2 第 9 项） ==================== */

/**
 * 收文流转状态。
 * 流转模型（精简三级 + 退回）：
 *   登记 ─► 待承办 ─转办/分办─► 承办中 ─办结─► 已办结 ─归档─► 已归档
 *                    ▲              │
 *                    └─── 退回 ─────┘
 * 发文没有流转，只有「已归档 / 未归档」。
 */
export const DOC_STATUSES = ['待承办', '承办中', '已退回', '已办结', '已归档']

/** 紧急程度（后端 doc_receive.urgency） */
export const DOC_URGENCIES = ['普通', '急件', '特急']

/** 公文文件类型（收发文共用） */
export const DOC_TYPES = ['通知', '函', '批复', '报告', '其他']

/**
 * 归档情况筛选项。
 * 注意：值必须是字符串 —— ScreenSelect 的 value 只接受 String/Number，
 * 用布尔值既会触发 prop 类型告警，也会让「未归档(false)」被当成已选值。
 * 面板在拼查询条件时把它转回布尔。
 */
export const DOC_ARCHIVED_OPTIONS = [
  { value: 'true', label: '已归档' },
  { value: 'false', label: '未归档' },
]

/** 收文流转动作 */
export const DOC_FLOW_ACTIONS = [
  { value: 'transfer', label: '转办 / 分办' },
  { value: 'reject', label: '退回' },
  { value: 'finish', label: '办结' },
]

/**
 * 收发文附件允许的扩展名。
 * 比档案的卷内文件多了 wps / et / ofd（国产办公与版式文件），
 * 因为公文附件里这三类很常见，而档案侧更偏工程图纸。
 */
export const DOC_ALLOWED_EXT = [
  'gif', 'jpg', 'jpeg', 'png', 'bmp', 'webp',
  'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf', 'txt', 'xml', 'md', 'csv',
  'rar', 'zip', '7z', 'wps', 'et', 'ofd', 'dwg', 'dxf',
  'shp', 'dbf', 'shx',
  'mp4', 'avi', 'mov', 'wmv',
]

/** 收发文附件扩展名的人类可读描述 */
export const DOC_ALLOWED_EXT_TEXT = 'pdf / doc(x) / xls(x) / 图片 / ofd / zip / rar 等'

/* ============================ 选项构造 ============================ */

/**
 * 档案年度下拉：从今年往前推 count 年
 * @param {number} count 年数
 * @returns {number[]} 由新到旧
 */
export function buildYearOptions (count = 13) {
  const current = new Date().getFullYear()
  const years = []
  for (let i = 0; i < count; i += 1) {
    years.push(current - i)
  }
  return years
}

/**
 * 把字符串数组转成 ScreenSelect 需要的 { value, label } 结构
 * @param {string[]} list
 */
export function toOptions (list) {
  return list.map((item) => ({ value: item, label: item }))
}

/* ============================ 展示辅助 ============================ */

/**
 * 文件大小格式化（唯一实现）
 * @param {number} bytes 字节数
 * @param {number} precision 小数位
 * @returns {string} 例如 '5.7 MB'；空值返回 '—'
 */
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

/** 档案状态 → ScreenTag 语气 */
export function statusTone (status) {
  switch (status) {
    case '已归档':
      return 'success'
    case '审核中':
      return 'warning'
    case '归档中':
      return 'info'
    case '未归档':
      return 'muted'
    default:
      return 'muted'
  }
}

/** 卷内文件状态 → ScreenTag 语气 */
export function fileStatusTone (status) {
  switch (status) {
    case '审核中':
      return 'warning'
    case '归档中':
      return 'info'
    case '已归档':
      return 'success'
    default:
      return 'muted'
  }
}

/** 密级 → ScreenTag 语气 */
export function secretTone (level) {
  switch (level) {
    case '机密':
      return 'danger'
    case '秘密':
      return 'warning'
    case '内部':
      return 'info'
    case '一般':
      return 'muted'
    default:
      return 'muted'
  }
}

/** 收发文状态 → ScreenTag 语气 */
export function docStatusTone (status) {
  switch (status) {
    case '已办结':
    case '已归档':
      return 'success'
    case '已退回':
      return 'danger'
    case '承办中':
      return 'info'
    default:
      return 'warning'
  }
}

/**
 * 流转记录 → 时间轴节点语气
 * @param {string} action 流转动作（转办/分办/退回/办结/登记）
 * @param {boolean} pending 是否还没处理（handleTime 为空）
 */
export function docFlowTone (action, pending) {
  if (pending) return 'info'
  if (action === '退回') return 'danger'
  if (action === '办结') return 'success'
  return 'muted'
}

/** 紧急程度 → ScreenTag 语气（越紧急越醒目） */
export function docUrgencyTone (urgency) {
  switch (urgency) {
    case '特急':
      return 'danger'
    case '急件':
      return 'warning'
    default:
      return 'muted'
  }
}

/** 档案来源 → 中文文案 */
export function sourceTypeText (type) {
  if (type === 'doc_receive') return '收文归档'
  if (type === 'doc_send') return '发文归档'
  return '手工录入'
}

/** 档案类型 → 中文文案 */
export function archiveTypeText (type) {
  if (type === 'paper') return '纸质档案'
  return '电子档案'
}

/** 兼容后端可能返回 0/1 的布尔型标志 */
export function isTruthyFlag (value) {
  return value === 1 || value === '1' || value === true
}

/**
 * 两个字段拼成一行摘要，都为空时给占位符
 * @param {string} a
 * @param {string} b
 * @param {string} separator
 */
export function joinInfo (a, b, separator = ' · ') {
  const parts = [a, b].filter((item) => item !== undefined && item !== null && item !== '')
  return parts.length ? parts.join(separator) : '—'
}

/* ============================ 日期 ============================ */

/** Date → 'YYYY-MM-DD' */
export function formatDate (date) {
  const d = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(d.getTime())) return ''
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

/** 今天 'YYYY-MM-DD' */
export function today () {
  return formatDate(new Date())
}

/** 当前日期时间 'YYYY-MM-DD HH:mm:ss'（用于附件上传时间的本地展示） */
export function nowText () {
  const d = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 从 'YYYY-MM-DD' 取年份（数字），取不到时回退到今年 */
export function yearOf (dateText) {
  const year = parseInt(String(dateText || '').slice(0, 4), 10)
  return Number.isFinite(year) ? year : new Date().getFullYear()
}

/**
 * 上传业务子目录：/{module}/{yyyy}/{MM}
 * 后端按 biz 字段建立分目录，避免所有文件堆在同一层。
 * 档案用 archive，收文用 receive，发文用 send。
 * @param {Date} [date]
 * @param {string} [module] 业务模块名，缺省 archive（保持既有调用不变）
 */
export function buildBizPath (date = new Date(), module = 'archive') {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `/${module}/${date.getFullYear()}/${month}`
}

/** 去掉扩展名，用于默认「文件题名」 */
export function stripExt (fileName) {
  const text = String(fileName || '')
  const index = text.lastIndexOf('.')
  return index > 0 ? text.slice(0, index) : text
}

/** 取小写扩展名（不含点） */
export function resolveExt (fileName) {
  const text = String(fileName || '')
  const index = text.lastIndexOf('.')
  return index > -1 ? text.slice(index + 1).toLowerCase() : ''
}

/** 去掉查询条件里的 undefined / null / ''，避免拼出 categoryId= 这种空条件 */
export function compactQuery (query) {
  const result = {}
  Object.keys(query || {}).forEach((key) => {
    const value = query[key]
    if (value === undefined || value === null || value === '') return
    if (Array.isArray(value) && !value.length) return
    result[key] = value
  })
  return result
}

/** 分页默认配置（档案维护 / 档案查询共用） */
export function defaultPagination (pageSize = 10) {
  return {
    current: 1,
    pageSize,
    total: 0,
    pageSizeOptions: [10, 20, 50, 100],
  }
}
