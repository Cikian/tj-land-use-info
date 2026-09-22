/**
 * 首页静态展示配置；业务数据由 Java 业务后端实时加载（见 index.vue 的 loadDashboard）。
 *
 * ⚠ 本文件只放「不随接口变化」的常量。
 *   接口不可用时的兜底演示数据在 ./mock.js（高保真设计稿的数字，便于无后端时评审界面）。
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
  { key: 'query', label: '查询统计' },
]

export const systemTitle = '天津市经营性用地市政配套设施动态监管工作站'

/**
 * 底部「属性表」的页签。
 * 高保真只有「市级项目预警 / 区级项目预警」两个页签；
 * 「地块预警信息」是设计稿里的独立浮层，按确认结论保留为第三个页签（点击开弹窗）。
 */
export const warningTabs = [
  { key: 'city', label: '市级项目预警' },
  { key: 'district', label: '区级项目预警' },
  { key: 'plot', label: '地块预警信息' },
]

/**
 * 预警行的字段契约（与后端 SupportingFacilitiesDashboardVO.WarningItem 一一对应）。
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
  'notHandedOver',
]
