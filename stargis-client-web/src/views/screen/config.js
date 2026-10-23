/**
 * 首页静态展示配置；业务数据由 Java 业务后端实时加载（见 index.vue 的 loadDashboard）。
 *
 * ⚠ 本文件只放「不随接口变化」的常量。
 *   业务数据只来自接口；没有数据时页面保持空白。
 */

/**
 * 顶栏一级导航。
 * 高保真模型是 6 项（首页 / 地图管理 / 数据管理 / 配套分析管理 / 查询统计 / 收发文）。
 * 本项目的口径：
 *   · 「收发文」不占一级导航 —— 它已经是「档案管理」模块内的一个页签
 *     （/screen/archive?tab=doc），重复放一级菜单会让同一功能出现两个入口；
 *   · 保留高保真的「查询统计」；
 *   · 补上本项目已实现的两个模块入口「档案管理」「提级论证管理」。
 * 因此最终为 7 项。
 */
export const headerMenus = [
  { key: 'home', label: '首页' },
  { key: 'map', label: '地图管理' },
  { key: 'data', label: '数据管理' },
  { key: 'analysis', label: '配套分析管理' },
  { key: 'archive', label: '档案管理' },
  { key: 'review', label: '提级论证管理' },
  { key: 'query', label: '查询统计' }
]

export const systemTitle = '天津市经营性用地市政配套设施动态监管工作站'

/**
 * 底部「属性表」的页签。
 *
 * 与高保真设计稿、原型（prototype/home.html）一致的三项：
 *   市级项目预警 / 区级项目预警 / 地块预警信息。
 *
 * ★ 第三个页签**不切换面板内容**：它点击后开一张独立弹窗
 *   （home/HomePlotModal.vue 的 variant="plot"），与设计稿里那块浮层一致 ——
 *   前两个页签是一行一个行政区（10 / 25 行），地块预警是一行一宗地（几百行），
 *   塞进同一块定尺表格里既放不下也没有可比性。
 *   key 必须是 'plot'：HomeAttrPanel 的 plotTabKey 默认值就是它。
 */
export const warningTabs = [
  { key: 'city', label: '市级项目预警' },
  { key: 'district', label: '区级项目预警' },
  { key: 'plot', label: '地块预警信息' }
]

/**
 * 预警行的字段契约（与后端 PendingFacilityVO.WarningItem 一一对应；
 * 2026-10 从 data 域 FacilityDashboardVO 迁到 stat/home 后字段名未变）。
 * 列宽与横坐标属于定尺画布的一部分，写在 home/HomeAttrPanel.vue 的 COL 常量里，
 * 不再放在这里（旧的 warningColumns 是给已下线的 ScreenDataTable 版底部面板用的，已删除）。
 */
export const warningFields = [
  'district',
  'landAcquisition',
  'feasibility',
  'preliminaryDesign',
  'fund',
  'notStarted',
  'notCompleted',
  'notHandedOver'
]

/**
 * 预警百分比的分档阈值。
 *
 * ★★ 两套档位**故意不一样**，不是笔误，必须照搬旧系统 ★★
 *   · 市级 5 档：0–20 绿 → 20–40 蓝 → 40–60 黄 → 60–80 橙 → ≥80 红
 *   · 区级 3 档：0–33.33 绿 → 33.33–66.66 黄 → ≥66.66 红
 * 旧实现分别在 homePage.js 的 GetColumn717()(:410) 与 GetColumn0805()(:512)，
 * 也就是「市级项目预警 / 区级项目预警」两张表的单元格着色规则。
 * 见 docs/升级改造工作内容清单.md（C）节「必须照搬，否则视觉验收不过」。
 *
 * 边界语义与旧代码逐条对齐（旧代码取百分比字符串前 4 个字符再比大小）：
 *   · 市级：< 20 绿、< 40 蓝、< 60 黄、< 80 橙、其余（含 80 及以上）红；
 *   · 区级：< 33.33 绿、< 66.66 黄、其余红 —— 所以「恰好 33.33」是黄、
 *     「恰好 66.66」是红。旧代码因为 substring(0,4) 把 33.33 截成 33.3，
 *     边界值会差一档；这里按**真实数值**比较，属有意修正（D-612）。
 *
 * 颜色值不写在这里：档位 → CSS 类（is-tone-green 等），
 * 色值取 screen-tokens.less 的 --screen-level-*，两处各自维护不会打架。
 */
export const warningTiers = {
  city: [
    { lt: 20, tone: 'green', label: '0–20%' },
    { lt: 40, tone: 'blue', label: '20–40%' },
    { lt: 60, tone: 'yellow', label: '40–60%' },
    { lt: 80, tone: 'orange', label: '60–80%' },
    { lt: Infinity, tone: 'red', label: '≥80%' }
  ],
  district: [
    { lt: 33.33, tone: 'green', label: '0–33.33%' },
    { lt: 66.66, tone: 'yellow', label: '33.33–66.66%' },
    { lt: Infinity, tone: 'red', label: '≥66.66%' }
  ]
}

/**
 * 取某个百分比该用的档位（tone）。
 *
 * @param {string} projectType 项目分类：city / district，或「市级项目 / 区级项目」；
 *   为空时按市级档位（市级 5 档是旧系统的主表口径，缺省不会「看起来没颜色」）
 * @param {number|string} value 百分比数值（后端给的是 2 位小数的数字）
 * @returns {string} green / blue / yellow / orange / red；值不可解析时返回空串（不着色）
 */
export function warningTone (projectType, value) {
  const tiers = warningTiers[warningTierKey(projectType)]
  const num = Number(value)
  // NaN 不能参与比较（NaN < 20 恒为 false，会一路落到红色），先挡掉
  if (!tiers || value === null || value === undefined || value === '' || Number.isNaN(num)) return ''
  for (let i = 0; i < tiers.length; i += 1) {
    if (num < tiers[i].lt) return tiers[i].tone
  }
  return tiers[tiers.length - 1].tone
}

/** 项目分类归一：'区级项目' / 'district' → district，其余 → city */
export function warningTierKey (projectType) {
  const value = String(projectType || '')
  return value === 'district' || value === '区级项目' ? 'district' : 'city'
}
