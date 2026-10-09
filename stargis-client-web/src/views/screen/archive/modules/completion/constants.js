/**
 * 竣工验收项目历史工程资料数字化档案 · 模块内共享枚举与展示辅助
 * ---------------------------------------------------------------------------
 * 为什么单独抽一个文件（与 views/screen/archive/constants.js 同一出发点）：
 *   检索表单、列表、新增/编辑弹窗、详情弹窗四处都要用「数字化状态」「项目类型」
 *   「保管期限」这三组取值，散着写必然出现「一个地方 5 项、另一个地方 4 项」，
 *   于是存得进去、筛不出来。这里收敛成唯一一份。
 *
 * ★ 为什么字典取值写死在前端、不走中台字典接口：
 *   大屏不依赖中台字典接口（见 components/screen/README.md 末节：业务数据全部走
 *   Java 业务后端）。这两个字典在后端 sys_dict 里是**真实存在**的（脚本
 *   sql/completion/02_completion_dict.sql，字典项分别 5 项 / 4 项），
 *   取值与这里逐字一致；中心在「系统管理 → 数据字典」里增改字典项后，
 *   **这里也要同步**，否则下拉里选不到新值。
 *
 * ★ 数字化状态是**代码枚举**（后端 enums/DigitizeStatus.java），不入字典：
 *   三个固定取值驱动配色、统计口径与进度判断，放代码里比放字典表稳
 *   （避免后台误改导致「状态与配色/统计对不上」）。后端对写入值做白名单校验。
 */

/* ======================= 一、固定代码枚举 ======================= */

/** 数字化状态（与后端 DigitizeStatus 的 value 逐字一致；顺序 = 业务推进顺序） */
export const DIGITIZE_STATUSES = ['未数字化', '数字化中', '已数字化']

/** 新增时的默认状态（与后端 DigitizeStatus.defaultValue() 一致，也等于建表默认值） */
export const DIGITIZE_DEFAULT = '未数字化'

/* ==================== 二、两个字典（与 sys_dict 对应） ==================== */

/** 字典编码；注释里写明后端字典名，便于中心核对 */
export const DICT_CODES = {
  /** sys_dict: 竣工验收历史档案-项目类型（5 项） */
  projectType: 'land_completion_project_type',
  /** sys_dict: 竣工验收历史档案-保管期限（4 项） */
  retention: 'land_completion_retention'
}

/**
 * 项目类型 5 项。
 * 为什么是这 5 项：本项目配套设施的 8 个真实类别是「道路/排水/中水/供水/燃气/
 * 路灯/绿化/交通设施」，历史竣工项目绝大多数集中在道路、排水、给水、燃气这四类，
 * 其余历史上量很少，统一归入「其他工程」。中心要细拆时，在数据字典里加项即可。
 */
export const PROJECT_TYPES = ['道路工程', '排水工程', '给水工程', '燃气工程', '其他工程']

/** 保管期限 4 项（档案管理规范取值） */
export const RETENTIONS = ['永久', '定期30年', '定期10年', '长期']

/* ======================= 三、录入辅助候选 ======================= */

/**
 * 扫描分辨率候选档位（DPI）。
 * 只作提示，允许填其它整数（后端是 Integer 列，不做白名单）。
 */
export const SCAN_DPI_OPTIONS = [200, 300, 400, 600]

/** 配套设施类别候选（与 t_archive / 台账模块口径一致，仅作录入提示） */
export const PTSSLB_OPTIONS = ['道路', '市政排水', '给水', '中水', '燃气', '路灯', '绿化', '交通设施', '其他']

/* ========================= 四、检索条件 ========================= */

/**
 * 默认常驻的核心条件（其余收进「更多条件」）。
 * 选这四个的理由：这页的日常问法是「某个历史项目、某个编号的档案，数字化到哪一步了」，
 * 因此「项目名称 + 档案编号 + 项目类型 + 数字化状态」最常用；
 * 行政区划、保管期限、参建单位、日期区间都是复查时才用的次频条件。
 */
export const CORE_QUERY_FIELDS = ['projectName', 'archiveNo', 'projectType', 'digitizeStatus']

/**
 * 扫描件挂接情况筛选项。
 * ★ value 必须是字符串：ScreenSelect 的 value 只接受 String/Number，
 *   用布尔值既触发 prop 类型告警，也会让「未挂(false)」被当成已选值。
 *   面板拼查询条件时由 normalizeQuery 转回真布尔（后端 hasArchive 是 Boolean）。
 */
export const HAS_ARCHIVE_OPTIONS = [
  { value: 'true', label: '已挂扫描件' },
  { value: 'false', label: '未挂扫描件' }
]

/* ========================= 五、配色 ========================= */

/**
 * 数字化状态 → ScreenTag / 表格标签列的语气。
 * 三个取值都落在 ScreenTag 允许的语气里（accent/success/warning/danger/info/muted），
 * 不存在「没有样式的裸标签」。
 */
export function digitizeTone (status) {
  if (status === '已数字化') return 'success'
  if (status === '数字化中') return 'info'
  return 'muted'
}

/** 数字化状态 → 环形图色（一律用 --screen-* 令牌，不写死颜色） */
export function digitizeColor (status) {
  if (status === '已数字化') return 'var(--screen-viz-green)'
  if (status === '数字化中') return 'var(--screen-bar-from)'
  return 'var(--screen-bar-muted-from)'
}

/** 环形图配色数组：**顺序必须与 DIGITIZE_STATUSES 一致** */
export const DIGITIZE_DONUT_COLORS = DIGITIZE_STATUSES.map(digitizeColor)

/* ======================= 六、展示小工具 ======================= */

/**
 * 数字化状态 → 进度百分比（0 / 50 / 100）。
 * 口径与后端 CompletionSupport.digitizePercent 一致，只用于进度条视觉表达，
 * 不参与任何统计。
 */
export function digitizePercent (status) {
  if (status === '已数字化') return 100
  if (status === '数字化中') return 50
  return 0
}

/** 页数 / 文件数 → 千分位文本（历史档案动辄几万页，不分组读不出量级） */
export function formatCount (value) {
  const num = Number(value)
  if (value === null || value === undefined || value === '') return '—'
  return Number.isFinite(num) ? num.toLocaleString('zh-CN') : String(value)
}

/** 投资额（万元）→ 千分位 + 2 位小数 */
export function formatAmount (value) {
  const num = Number(value)
  if (value === null || value === undefined || value === '') return '—'
  if (!Number.isFinite(num)) return String(value)
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** DPI → 文本（0 / 空值都算未填，避免显示「0 DPI」） */
export function formatDpi (value) {
  const num = Number(value)
  return num > 0 ? `${num} DPI` : '—'
}

/**
 * 把检索表单的原始条件整理成可直接发给后端的查询对象：
 *   1. 剔除 undefined / null / '' / 空数组（避免拼出 projectType= 这种空条件）；
 *   2. hasArchive 由 'true'/'false' 字符串转回真布尔 —— 不转的话
 *      「未挂扫描件」会筛出「已挂」并且不报错（ScreenSelect 只能给字符串）。
 * @param {object} query
 * @returns {object}
 */
export function normalizeQuery (query) {
  const result = {}
  Object.keys(query || {}).forEach((key) => {
    const value = query[key]
    if (value === undefined || value === null || value === '') return
    if (Array.isArray(value) && !value.length) return
    result[key] = value
  })
  if (result.hasArchive === 'true') {
    result.hasArchive = true
  } else if (result.hasArchive === 'false') {
    result.hasArchive = false
  } else if (result.hasArchive === '') {
    delete result.hasArchive
  }
  return result
}

/** 判断某条件下是否需要展开「更多条件」（核心条件之外的字段有值时） */
export function needsExpand (query) {
  return Object.keys(query || {}).some((key) => {
    if (CORE_QUERY_FIELDS.indexOf(key) > -1) return false
    const value = query[key]
    return value !== undefined && value !== null && value !== ''
  })
}
