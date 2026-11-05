import { javaDeleteAction, javaGetAction, javaPostAction, javaPutAction } from '@/api/manageJava'

/**
 * 数据管理 · 宗地 / 配套项目 基础查询接口 + 首页看板统计
 *
 * 后端：
 *   · 首页看板（landDashboard / facilityDashboard）→
 *     org.jeecg.modules.land.stat.home.controller.HomeStatController
 *     ★ 2026-10 起大屏首页改走 /land/stat/home/*：统计口径集中到 stat 域，
 *       不再复用 /land/data/dashboard 与 /land/data/facility/dashboard。
 *   · 其余下拉 / 列表 / 增删改 → org.jeecg.modules.land.data.controller.LandDataController
 * 权限：land:archive:* / land:docReceive:* / land:docSend:*（任一即可）
 *
 * 【重要】接口在 **Java 业务后端**（javaBaseUrl() = window._CONFIG.VUE_BUSINESS_JAVA_URL，
 * 旧配置兜底 VUE_DATA_JAVA_URL），必须用 `@/api/manageJava`，不能用走中台的 `@/api/manage`。
 * ⚠ 2026-10 起 VUE_DATA_JAVA_URL 已改指 JNI 后台服务（38190），业务接口**不要**显式传它。
 *
 * 用途：档案管理与收发文管理的「两级联动下拉」——
 *   第一步 选出让宗地（t_land）
 *   第二步 联动选该宗地下的配套项目（xj_kjkfb_supporting_facilities）
 *
 * 与 admin-client/src/api/land/landData.js 一一对应（共用同一套后端）；
 * 例外是上面两个首页看板接口——只有大屏首页调用，管理端没有同名函数。
 */
export const landDataUrl = {
  landOptions: '/land/data/land/options',
  landById: '/land/data/land/queryById',
  xzqhOptions: '/land/data/land/xzqhOptions',
  facilityOptions: '/land/data/facility/options',
  facilitySearch: '/land/data/facility/search',
  facilityById: '/land/data/facility/queryById',
  landDashboard: '/land/stat/home/landOverview',
  facilityDashboard: '/land/stat/home/pendingFacility',
  warningDetail: '/land/stat/home/warningDetail',
  landList: '/land/data/land/list',
  landAdd: '/land/data/land/add',
  landEdit: '/land/data/land/edit',
  landDelete: '/land/data/land/delete',
  facilityList: '/land/data/facility/list',
  facilityAdd: '/land/data/facility/add',
  facilityEdit: '/land/data/facility/edit',
  facilityDelete: '/land/data/facility/delete'
}

/**
 * 宗地下拉（远程搜索）
 * @param {{keyword?: string, xzqh?: string, limit?: number}} params
 */
export function queryLandOptions (params) {
  return javaGetAction(landDataUrl.landOptions, params)
}

/**
 * 宗地详情（选中后带出地块名称、行政区划、项目分类等）
 * @param {{id?: string, crzdbh?: string}} params
 */
export function queryLandById (params) {
  return javaGetAction(landDataUrl.landById, params)
}

/** 行政区划下拉（由已有宗地数据聚合，无需单独维护字典） */
export function queryXzqhOptions () {
  return javaGetAction(landDataUrl.xzqhOptions, {})
}

/**
 * 配套项目下拉（必须先选宗地）
 * @param {{crzdbh: string, keyword?: string, limit?: number}} params
 */
export function queryFacilityOptions (params) {
  return javaGetAction(landDataUrl.facilityOptions, params)
}

/**
 * 配套项目全局搜索（不限宗地，供档案查询条件使用）
 * @param {{keyword?: string, limit?: number}} params
 */
export function searchFacilityOptions (params) {
  return javaGetAction(landDataUrl.facilitySearch, params)
}

/** 配套项目详情 */
export function queryFacilityById (id) {
  return javaGetAction(landDataUrl.facilityById, { id })
}

/**
 * 首页宗地总览（已出让土地总数 / 市级区级 / 涉及道路 + 行政区排行）。
 * 契约：total / cityCount / districtCount / roadCount /
 *       ranks[{ projectType, name, value, roadCount }]。
 */
export function queryLandDashboard () {
  return javaGetAction(landDataUrl.landDashboard)
}

/**
 * 首页待落实配套（宗地数 / 配套总数 / 已完成 + 行政区排行 + 预警明细）。
 * 契约：landTotal / cityLandCount / districtLandCount / facilityTotal /
 *       completedFacilityCount / completionRate /
 *       ranks[{ projectType, name, value, facilityCount, completedCount }] /
 *       warnings{ city, district, plot }。
 */
export function queryFacilityDashboard () {
  return javaGetAction(landDataUrl.facilityDashboard)
}

/**
 * 首页预警下钻：某个行政区下的宗地明细（一行 = 一宗地）。
 *
 * 契约：`[{ id, landNo, plotName, projectType, facilityCount,
 *           landAcquisition, feasibility, preliminaryDesign, fund,
 *           notStarted, notCompleted, notHandedOver }]`
 * —— 7 个比率是**数字**（后端 2 位小数），不是「12.34%」字符串，
 * 前端要拿数值比阈值档位（市级 5 档 / 区级 3 档，见 views/screen/config.js）。
 *
 * @param {{projectType: 'city'|'district', district: string}} params
 *   district 传预警行的分组名：市级 = xzqh 值、区级 = xzqh2 值。
 */
export function queryWarningDetail (params) {
  return javaGetAction(landDataUrl.warningDetail, params)
}

/** 经营性用地分页列表。 */
export function queryLandList (params) {
  return javaGetAction(landDataUrl.landList, params)
}

/** 新增经营性用地。出让宗地编号必填且不可重复。 */
export function addLand (data) {
  return javaPostAction(landDataUrl.landAdd, data)
}

/** 编辑经营性用地。 */
export function editLand (data) {
  return javaPutAction(landDataUrl.landEdit, data)
}

/** 删除经营性用地（逻辑删除）。 */
export function deleteLand (id) {
  return javaDeleteAction(landDataUrl.landDelete, { id })
}

/** 配套项目分页列表。 */
export function queryFacilityList (params) {
  return javaGetAction(landDataUrl.facilityList, params)
}

/** 新增配套项目。必须挂到已有宗地，项目名称不可重复。 */
export function addFacility (data) {
  return javaPostAction(landDataUrl.facilityAdd, data)
}

/** 编辑配套项目。 */
export function editFacility (data) {
  return javaPutAction(landDataUrl.facilityEdit, data)
}

/** 删除配套项目（逻辑删除）。 */
export function deleteFacility (id) {
  return javaDeleteAction(landDataUrl.facilityDelete, { id })
}

export default {
  landDataUrl,
  queryLandOptions,
  queryLandById,
  queryXzqhOptions,
  queryFacilityOptions,
  searchFacilityOptions,
  queryFacilityById,
  queryLandDashboard,
  queryFacilityDashboard,
  queryWarningDetail,
  queryLandList,
  addLand,
  editLand,
  deleteLand,
  queryFacilityList,
  addFacility,
  editFacility,
  deleteFacility
}
