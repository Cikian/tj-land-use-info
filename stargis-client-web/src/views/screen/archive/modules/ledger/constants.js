/**
 * 道路设施验收及移交资料台账 · 共享枚举与展示辅助（大屏版）
 * ===============================================================
 * 与档案模块 `views/screen/archive/constants.js` 同一分工：
 *   接口只放 `src/api/land/ledger.js`，**枚举与展示辅助一律收敛到本文件**，
 *   页面与组件不再各写一套（admin-client 那版把这些散在 6 个组件里，
 *   `statusColor` / 资料计数 / 资料缺失判断都各有一份实现）。
 *
 * ★ 与后端的对应关系（改这里必须同步后端，否则「存得进去、筛不出来」）：
 *   状态         → org.jeecg.modules.land.archive.ledger.enums.LedgerStatus
 *   13 类资料    → ...enums.LedgerMaterial（也是台账表的 13 个物理列）
 *   验收类型/结果 → 数据字典 land_road_acceptance_type / land_road_acceptance_result
 *                 （建表见 server/…/sql/ledger/02_ledger_dict.sql）
 *
 * 【为什么验收类型/结果用前端硬编码而不是调字典接口】
 * 与档案模块处理密级 / 责任部门的方式一致：大屏端的请求层只走 Java 业务后端，
 * 而 jeecg 的字典组件（@/components/dict/JDictSelectUtil）走的是中台，
 * 在大屏里取不到值。这两个字典的取值由我们的建表脚本创建、中心确认后才会调整，
 * 因此在前端固化并在下方注释里写明来源；真改了字典，这里同步一次即可。
 */

/* ============================ 枚举 ============================ */

/** 台账状态（固定 4 值，不入字典；与后端 LedgerStatus 逐字一致） */
export const LEDGER_STATUS = ['未验收', '验收中', '已验收', '已移交']

/**
 * 功能区取值。
 * 旧数据把功能区值混写在 `xzqh` 里（192 条：生态城 / 经开区 / 高新区 / 保税区 /
 * 土地发展中心），迁移脚本已按清单 §3.0 第 4 条把它们拆到 `gnq` 列，
 * 因此行政区划下拉里不会再有这些值。
 */
export const GNQ_OPTIONS = ['生态城', '经开区', '高新区', '保税区', '土地发展中心']

/** 配套设施类别（旧库 ptsslb 里与道路相关的取值） */
export const PTSSLB_OPTIONS = ['道路', '市政道路', '道路及管线']

/** 验收类型（字典 land_road_acceptance_type，4 项） */
export const ACCEPTANCE_TYPES = ['竣工验收', '规划验收', '档案专项验收', '移交验收']

/** 验收结果（字典 land_road_acceptance_result，3 项） */
export const ACCEPTANCE_RESULTS = ['合格', '不合格', '整改后合格']

/**
 * 「资料齐全与否」的快捷筛选。
 * 后端没有 fullMaterial 之类的布尔条件，只有已归集资料数的上下限
 * （beginMaterialCount / endMaterialCount），这里在前端把语义翻成区间，
 * 避免用户自己去填「13 ~ 13」这种数字。
 */
export const MATERIAL_COMPLETENESS_OPTIONS = [
  { value: 'full', label: '资料齐全(13/13)' },
  { value: 'partial', label: '部分归集(1-12)' },
  { value: 'empty', label: '一类未归集(0)' }
]

/** 查询表单里「更多条件」折叠后仍然常驻的核心条件（清单 §6.3.7 最常用的 4 个） */
export const CORE_QUERY_FIELDS = ['roadName', 'xzqh', 'acceptanceType', 'status']

/** 「显示资料矩阵」开关在 localStorage 里的键（大屏是长驻页面，用户偏好要跨刷新保留） */
export const MATERIAL_MATRIX_KEY = 'screen:ledger:materialMatrix'

/* ==================== ★ 13 类资料（与后端 LedgerMaterial 同源） ==================== */

/**
 * 13 类资料定义（本地兜底）。
 *
 * 面板挂载时会调 `queryLedgerMaterials()` 用后端返回的定义覆盖 label / source，
 * 因此「后端加一类资料」时表头会自动跟上；本列表只保证接口不可用时页面仍能渲染。
 *
 * key    —— 提交给后端的驼峰属性名（0/1）
 * column —— 数据库列名（仅用于排查与文档对照，前端不拼 SQL）
 * label  —— 中文名
 * source —— 迁移来源说明（表格与勾选处的悬停提示）
 */
export const LEDGER_MATERIALS_FALLBACK = [
  { seq: 1, key: 'hasSgxk', column: 'has_sgxk', label: '施工许可证', source: '旧配套表 sgxk=是（旧附件目录 11-施工许可）' },
  { seq: 2, key: 'hasYsbg', column: 'has_ysbg', label: '竣工验收报告', source: '旧配套表 sfjg=是 推断（已竣工 ⇒ 必有竣工验收报告）' },
  { seq: 3, key: 'hasJgtc', column: 'has_jgtc', label: '竣工图测', source: '旧配套表 sjjgsj 非空推断（有实际竣工日期 ⇒ 应有竣工图测）' },
  { seq: 4, key: 'hasZljdbg', column: 'has_zljdbg', label: '质量监督报告', source: '旧库无对应标志，待中心按实际资料补录' },
  { seq: 5, key: 'hasCljybg', column: 'has_cljybg', label: '材料检验报告', source: '旧库无对应标志，待中心按实际资料补录' },
  { seq: 6, key: 'hasAjbg', column: 'has_ajbg', label: '安全监督报告', source: '旧库无对应标志，待中心按实际资料补录' },
  { seq: 7, key: 'hasGhyshgz', column: 'has_ghyshgz', label: '规划验收合格证', source: '旧流程「建设工程规划验收合格证核发」产物，待补录' },
  { seq: 8, key: 'hasJgbaba', column: 'has_jgbaba', label: '竣工验收备案表', source: '旧流程「建设工程竣工验收备案」产物，待补录' },
  { seq: 9, key: 'hasDazxys', column: 'has_dazxys', label: '档案专项验收意见', source: '旧流程「建设项目档案专项验收」产物，待补录' },
  { seq: 10, key: 'hasDlyjd', column: 'has_dlyjd', label: '道路工程移交单', source: '旧流程「道路工程移交」产物，待补录' },
  { seq: 11, key: 'hasYhxy', column: 'has_yhxy', label: '养护协议', source: '方案 2.3.2 第 6 项（道路交付及养护协议移交）的产物，待补录' },
  { seq: 12, key: 'hasJgwj', column: 'has_jgwj', label: '竣工文件', source: '旧配套表 jgwj=是（旧附件目录 13-竣工文件）' },
  { seq: 13, key: 'hasYjwj', column: 'has_yjwj', label: '移交文件', source: '旧配套表 yjwj=是（旧附件目录 14-移交文件）' }
]

/** 13 类资料总数（「资料 3/13」的分母） */
export const MATERIAL_TOTAL = LEDGER_MATERIALS_FALLBACK.length

/**
 * ★「移交类」资料的键（判断状态与资料是否自相矛盾时用）。
 * 这三类是「已移交」状态的直接凭证：状态置成已移交，却没有移交单/养护协议/移交文件，
 * 台账就自相矛盾了 —— 台账是给中心看进度的，这类矛盾必须显式提示而不是静默放过。
 */
export const HANDOVER_MATERIAL_KEYS = ['hasDlyjd', 'hasYhxy', 'hasYjwj']

/**
 * 把接口返回的资料定义归一化（排序 + 补齐缺省字段）。
 * @param {Array} list 后端 /land/archive/ledger/materials 的 result
 * @returns {Array} 归一化后的资料定义；入参为空时返回本地兜底列表
 */
export function normalizeMaterials (list) {
  if (!Array.isArray(list) || !list.length) {
    return LEDGER_MATERIALS_FALLBACK.slice()
  }
  return list
    .filter((item) => item && item.key)
    .map((item, index) => ({
      seq: Number(item.seq) || index + 1,
      key: item.key,
      column: item.column || '',
      label: item.label || item.key,
      source: item.source || ''
    }))
    .sort((a, b) => a.seq - b.seq)
}

/** 资料定义 → ScreenSelect 选项（「缺少某类资料」筛选与勾选处共用） */
export function toMaterialOptions (materials) {
  const list = materials && materials.length ? materials : LEDGER_MATERIALS_FALLBACK
  return list.map((item) => ({ value: item.key, label: `${padSeq(item.seq)} ${item.label}` }))
}

/** 序号补零（01 / 02 … 13），表头与矩阵列头共用，保证列宽稳定不跳动 */
export function padSeq (seq) {
  return String(Number(seq) || 0).padStart(2, '0')
}

/* ==================== 资料归集度的派生展示 ==================== */

/**
 * 已归集资料类数。
 * 优先用后端算好的 materialCount（SQL 侧一次算完），没有时按 13 个勾选列本地数一遍。
 */
export function countMaterials (record, materials) {
  if (!record) return 0
  if (record.materialCount !== null && record.materialCount !== undefined) {
    return Number(record.materialCount)
  }
  const list = materials && materials.length ? materials : LEDGER_MATERIALS_FALLBACK
  return list.reduce((sum, item) => sum + (Number(record[item.key]) === 1 ? 1 : 0), 0)
}

/** 资料归集摘要：「3/13」 */
export function materialSummary (record, materials) {
  return `${countMaterials(record, materials)}/${MATERIAL_TOTAL}`
}

/** 该台账缺失的资料名列表（「待补资料」提示用） */
export function missingMaterialLabels (record, materials) {
  if (!record) return []
  const list = materials && materials.length ? materials : LEDGER_MATERIALS_FALLBACK
  return list.filter((item) => Number(record[item.key]) !== 1).map((item) => item.label)
}

/** 资料名（key → label），供提示语拼装 */
export function materialLabel (key, materials) {
  const list = materials && materials.length ? materials : LEDGER_MATERIALS_FALLBACK
  const hit = list.filter((item) => item.key === key)[0]
  return hit ? hit.label : key
}

/**
 * 资料与状态的一致性检查（返回提示语，无问题时返回空串）。
 *
 * ★ 只做「提示」，不做硬校验：台账的验收与移交都是线下办完才回系统登记，
 * 资料扫描件往往滞后于状态（现实里常见「先移交、后补资料」），
 * 因此不能在保存时阻断，只在列表与详情上给一条黄色提示，让中心一眼看出还要补什么。
 *
 * 三条规则：
 *  1. 状态「已移交」但移交类资料（移交单/养护协议/移交文件）未归集齐 → 提示缺哪些；
 *  2. 状态「已验收」但没有竣工验收报告 → 提示；
 *  3. 反过来：已归集「移交文件」但状态还不是「已移交」→ 提示状态可能漏改。
 */
export function statusMaterialWarning (record, materials) {
  if (!record) return ''
  const status = record.status
  if (status === '已移交') {
    const missing = HANDOVER_MATERIAL_KEYS.filter((key) => Number(record[key]) !== 1)
    if (missing.length) {
      return `状态为「已移交」，但「${missing.map((key) => materialLabel(key, materials)).join('、')}」尚未归集`
    }
  }
  if (status === '已验收' && Number(record.hasYsbg) !== 1) {
    return '状态为「已验收」，但「竣工验收报告」尚未归集'
  }
  if (status !== '已移交' && Number(record.hasYjwj) === 1) {
    return `已归集「${materialLabel('hasYjwj', materials)}」，但状态仍是「${status || '未验收'}」，请确认是否漏改状态`
  }
  return ''
}

/** 宗地未匹配的提示（迁移数据里 remark 带「无匹配记录」的 53 条） */
export function isOrphan (record) {
  return !!(record && record.remark && String(record.remark).indexOf('无匹配记录') >= 0)
}

/** 该台账是否「迁移生成」（source_facility_id 非空，人工新增的为空） */
export function isMigrated (record) {
  return !!(record && record.sourceFacilityId)
}

/* ============================ 语气（ScreenTag tone） ============================ */

/**
 * 状态 → ScreenTag 语气。
 * 只用组件库定义的 6 个 tone（accent / success / warning / danger / info / muted），
 * 传别的值会被 ScreenTag 回退成 accent，出现「红的变成正色」这种错误表达。
 *
 * 配色口径（与 admin-client 的 antd 色一一对应）：
 *   未验收 muted（灰）· 验收中 info（蓝）· 已验收 success（绿）· 已移交 success（绿）
 * 注：admin-client 里「已验收」用蓝色 antd tag、「已移交」用绿色；
 * 大屏 tone 只有一组语义色，两者都是「已完成」，统一用 success，
 * 具体状态文本本身就是区分线索（不依赖颜色单独传达信息）。
 */
export function statusTone (status) {
  switch (status) {
    case '已移交':
      return 'success'
    case '已验收':
      return 'success'
    case '验收中':
      return 'info'
    default:
      return 'muted'
  }
}

/** 验收结果 → ScreenTag 语气（合格=绿、整改后合格=橙、不合格=红） */
export function acceptanceResultTone (result) {
  switch (result) {
    case '合格':
      return 'success'
    case '整改后合格':
      return 'warning'
    case '不合格':
      return 'danger'
    default:
      return 'muted'
  }
}

/** 资料归集度 → ScreenTag 语气（齐全=绿、有缺=橙、一条都没有=灰） */
export function materialTone (count) {
  const value = Number(count) || 0
  if (value >= MATERIAL_TOTAL) return 'success'
  if (value > 0) return 'warning'
  return 'muted'
}

/** 档案状态 → ScreenTag 语气（详情页「关联档案」列表用） */
export function archiveStatusTone (status) {
  switch (status) {
    case '已归档':
      return 'success'
    case '审核中':
      return 'warning'
    case '归档中':
      return 'info'
    default:
      return 'muted'
  }
}

/* ============================ 前端下载/跳转链接 ============================ */

/**
 * 台账 → 档案查询页的下钻链接（双向跳转的「台账 → 档案」方向）。
 * 大屏的档案查询页（ArchiveQuery）读 $route.query 作为初始条件，
 * 带上档案号即可；没有档案号时退化为按配套项目名过滤。
 */
export function archiveQueryUrl (record) {
  const params = []
  if (record && record.archiveNo) {
    params.push(`archiveNo=${encodeURIComponent(record.archiveNo)}`)
  } else if (record && record.ptxmmc) {
    params.push(`ptxmmc=${encodeURIComponent(record.ptxmmc)}`)
  }
  return `/screen/archive${params.length ? '?' + params.join('&') : ''}`
}

/* ==================== 从档案模块的共享常量再导出 ====================
 * 这些是所有业务模块通用的（日期、分页、条件清洗、扩展名…），
 * 档案模块已经收敛过一份（views/screen/archive/constants.js），
 * 这里只做「转发」，让 ledger 下的组件统一从 './constants' 取，
 * 避免每个组件都要记两套相对路径，也避免再抄一份 formatSize 之类的实现。
 */
export {
  formatSize,
  toOptions,
  buildYearOptions,
  compactQuery,
  defaultPagination,
  formatDate,
  today,
  nowText,
  yearOf,
  joinInfo,
  isTruthyFlag
} from '../../constants'
