/**
 * 预警信息查询 · 前端常量与展示辅助
 * ===============================================================
 * 只放「不随后端数据变化」的东西：
 *   · 四档预警等级的**固定顺序与兜底色**（后端 sortNo 1~4，由轻到重）；
 *   · 预警分类的兜底候选（首选仍取 `/options` 的 yjTypes）；
 *   · 分页档位；
 *   · 三个纯函数：等级着色类名、预警值文本、空值占位。
 *
 * ★ 为什么等级要有「固定四档」而不是直接遍历 byLevel：
 *   契约 §5.2 要求 byLevel 四档齐全，但那是**后端承诺**。前端如果直接渲染后端数组，
 *   一旦某个等级被停用 / 接口只返回有数据的档位，卡片就会从四格变成三格（跳色），
 *   版面和颜色语义一起乱。所以这里以固定四档为骨架，用 levelCode 去后端结果里取数，
 *   取不到就显示 0 —— 版面恒定，颜色语义也恒定。
 *
 * ★ 颜色不写在这里：levelCode → CSS 类（is-blue / is-yellow / …），
 *   色值统一取 screen-tokens.less 的 `--screen-level-*`，与首页看板同一套令牌，
 *   不要另造一套颜色（契约 §6.1）。
 */

/** 四档预警等级（顺序 = 由轻到重，与 t_process_warning_level.sort_no 一致） */
export const WARNING_LEVEL_SLOTS = [
  { code: 'blue', label: '蓝色预警' },
  { code: 'yellow', label: '黄色预警' },
  { code: 'orange', label: '橙色预警' },
  { code: 'red', label: '红色预警' }
]

/** 允许着色的 levelCode（其余取值一律不着色，避免拼出无效类名） */
const LEVEL_CODES = WARNING_LEVEL_SLOTS.map((slot) => slot.code)

/**
 * levelCode → 着色类名。
 * @param {string} levelCode blue / yellow / orange / red
 * @returns {string} 'is-blue' …；未知取值返回空串（不着色，不写死默认色）
 */
export function levelToneClass (levelCode) {
  const code = String(levelCode || '').toLowerCase()
  return LEVEL_CODES.indexOf(code) > -1 ? `is-${code}` : ''
}

/** 预警分类兜底候选（`/options` 没返回 yjTypes 时用它；顺序与旧系统一致） */
export const YJ_TYPE_FALLBACK = ['环节预警', '阶段预警', '总体预警']

/**
 * 分页档位：契约 §6.1 要求默认每页 10、可切 20/50/100。
 * ★ 10 必须留在选项里：ScreenPagination 的「每页 N 条」下拉显示的是**当前值**，
 *   选项里没有 10 时下拉会显示空白，看起来像没选中。
 */
export const WARNING_PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

/** 默认每页条数 */
export const WARNING_DEFAULT_PAGE_SIZE = 10

/**
 * 预警值文本：`yjPercent + '%'`。
 * ★ 契约 §3.5 明确后端返回的 yjPercent **已经乘过 100**（scale=1），前端只拼百分号，
 *   绝不能再乘 100 —— 否则 110% 会显示成 11000%。
 * @param {number|string} value yjPercent
 * @returns {string} 例如 '110.0%'；空值返回 '—'
 */
export function percentText (value) {
  if (value === null || value === undefined || value === '') return '—'
  const num = Number(value)
  return Number.isNaN(num) ? String(value) : `${num.toFixed(1)}%`
}

/**
 * 空值占位。
 * 详情里「字段是空的」和「没这个字段」必须能区分，所以空值也渲染成「—」而不是留白。
 */
export function orDash (value) {
  if (value === null || value === undefined || String(value).trim() === '') return '—'
  return value
}
