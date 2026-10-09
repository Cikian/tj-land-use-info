import { toOptions } from '../../constants'

/**
 * 道路交付及养护协议移交事项 · 共享枚举与展示辅助
 * ---------------------------------------------------------------
 * 为什么单独抽一个文件：
 *   admin-client 那一版把状态数组、道路等级候选项、`statusColor`、`missingAgreement`、
 *   `maintenanceWarning` 等散在 api 层与 4 个组件里，同一个判断在列表与详情里各写一遍。
 *   大屏这一版按 `views/screen/archive/constants.js` 的做法全部收敛到这里，
 *   页面里不再各写一套，避免同一个值在三处显示成三种样子。
 *
 * 【与后端 sys_dict 的对应关系】（重要）
 *   · 移交类型 → 后端字典 `land_road_handover_type`（3 项：道路交付 / 养护协议 / 正式移交）。
 *     大屏按既有惯例**不依赖中台字典接口**（`ArchiveFormModal` 的密级 / 部门同样是前端枚举），
 *     所以这里硬编码一份。中心若在后台改了字典取值，改这里一处即可；
 *     但注意：后端对迁移类型**不做白名单校验**（那组措辞中心会调整），
 *     因此老数据里出现不在本列表里的取值时，界面照原样显示，不会被吞掉。
 *   · 状态 → 后端**不是**字典，是固定 3 值的代码枚举
 *     （org.jeecg.modules.land.archive.handover.enums.HandoverStatus），
 *     这里必须与它逐字一致：待移交 / 移交中 / 已移交。
 */

/* ============================ 枚举 ============================ */

/** 移交状态（与后端 HandoverStatus 逐字一致，3 值固定） */
export const HANDOVER_STATUSES = ['待移交', '移交中', '已移交']

/** 移交类型（对应后端字典 land_road_handover_type） */
export const HANDOVER_TYPES = ['道路交付', '养护协议', '正式移交']

/** 功能区取值（与台账模块、迁移脚本口径一致；非功能区留空） */
export const GNQ_OPTIONS = ['生态城', '经开区', '高新区', '保税区', '土地发展中心']

/**
 * 道路等级常见取值。
 * 旧库 `dldj` 里同时存在「城市主干道」与「城市主干路」两种写法，
 * 所以这里只是**下拉候选**（ScreenSelect 允许输入不上的值就留空），不做白名单。
 */
export const DLDJ_OPTIONS = ['城市主干道', '城市主干路', '城市次干路', '城市支路', '快速路', '支路']

/**
 * 「是否已关联档案」筛选项。
 * 注意：值必须是字符串 —— ScreenSelect 的 value 只接受 String/Number
 * （用布尔值既会触发 prop 类型告警，也会让「未关联(false)」被当成已选值）。
 * 面板在拼查询条件时把它转回布尔。
 */
export const ARCHIVE_LINK_OPTIONS = [
  { value: 'true', label: '已关联档案' },
  { value: 'false', label: '未关联档案' }
]

/** 业务提示里的固定文案（列表与详情共用，避免两处措辞不一致） */
export const MISSING_AGREEMENT_TEXT = '协议信息待补录'
export const ORPHAN_LAND_TEXT = '出让宗地待核对'

/** 养护期「将到期」的判定天数（与后端 /stat 的口径一致：90 天） */
export const MAINTENANCE_EXPIRING_DAYS = 90

/* ============================ 选项构造 ============================ */

/** 移交类型下拉选项（ScreenSelect 需要 { value, label }） */
export function handoverTypeOptions () {
  return toOptions(HANDOVER_TYPES)
}

/** 状态下拉选项 */
export function handoverStatusOptions () {
  return toOptions(HANDOVER_STATUSES)
}

/* ============================ 展示辅助 ============================ */

/** 移交状态 → ScreenTag 语气 */
export function statusTone (status) {
  switch (status) {
    case '已移交':
      return 'success'
    case '移交中':
      return 'warning'
    case '待移交':
      return 'info'
    default:
      return 'muted'
  }
}

/**
 * 移交状态 → 状态圆点样式类。
 * 表格里的状态是「圆点 + 下拉」的组合：下拉的文本已经表达了状态，
 * 颜色只是**辅助**线索（不依赖颜色单独传达信息，见 README 第 5 节）。
 */
export function statusDotClass (status) {
  switch (status) {
    case '已移交':
      return 'is-done'
    case '移交中':
      return 'is-doing'
    default:
      return 'is-waiting'
  }
}

/**
 * 是否「协议信息待补录」：协议编号与协议签订日期**都**为空。
 *
 * 迁移来的 84 条全部属于这种 —— 旧库只有「是否移交=是」一个标志位，
 * 协议编号 / 签订日期 / 实际移交日期 / 养护期压根没有对应字段。
 * 不在界面上提示的话，这批数据会一直「看起来已移交、实际没有任何协议凭证」。
 */
export function missingAgreement (record) {
  if (!record) return false
  return !record.agreementNo && !record.agreementDate
}

/**
 * 是否「出让宗地待核对」：迁移时在备注里标注了「无匹配记录」。
 * 根因是旧库先录配套、后录宗地，或中括号 /「号」字写法不一致。
 */
export function orphanLand (record) {
  if (!record || !record.remark) return false
  return String(record.remark).indexOf('无匹配记录') >= 0
}

/**
 * 养护期提示：已过期 / 90 天内到期 / 正常。
 * @returns {string} 提示文案；无养护截止日期或日期不可解析时返回空串（不提示）
 */
export function maintenanceWarning (record) {
  if (!record || !record.maintenanceEnd) return ''
  // iOS / 部分内核不认 'yyyy-MM-dd'，统一换成 '/' 再解析
  const end = new Date(String(record.maintenanceEnd).replace(/-/g, '/'))
  if (isNaN(end.getTime())) return ''

  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const days = Math.floor((end.getTime() - today.getTime()) / 86400000)

  if (days < 0) {
    return `养护期已于 ${record.maintenanceEnd} 到期（逾期 ${-days} 天），请确认是否续签`
  }
  if (days <= MAINTENANCE_EXPIRING_DAYS) {
    return `养护期将于 ${record.maintenanceEnd} 到期（还有 ${days} 天）`
  }
  return ''
}

/** 养护期提示 → ScreenTag 语气（已过期 danger / 将到期 warning） */
export function maintenanceTone (record) {
  if (!record || !record.maintenanceEnd) return 'muted'
  const end = new Date(String(record.maintenanceEnd).replace(/-/g, '/'))
  if (isNaN(end.getTime())) return 'muted'
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const days = Math.floor((end.getTime() - today.getTime()) / 86400000)
  if (days < 0) return 'danger'
  if (days <= MAINTENANCE_EXPIRING_DAYS) return 'warning'
  return 'muted'
}

/** 空值统一显示为 '—'（描述列表 / 表格文案共用） */
export function textOrDash (value) {
  if (value === undefined || value === null || value === '') return '—'
  return String(value)
}

/**
 * 数值型字段的空值处理。
 * 不要用 `value || '—'`：`0 米` / `0 篇` 都是合法值，会被误显示成「—」。
 */
export function numberOrDash (value) {
  if (value === undefined || value === null || value === '') return '—'
  return value
}

/**
 * 养护起止 → 一行文案（表格「养护期」列用）。
 * 两端都空返回空串，由调用方决定显示什么占位符。
 */
export function maintenanceRangeText (record) {
  if (!record) return ''
  const start = record.maintenanceStart || ''
  const end = record.maintenanceEnd || ''
  if (!start && !end) return ''
  return `${start || '—'} ~ ${end || '—'}`
}
