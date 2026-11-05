import { getAction, postAction, putAction, deleteAction, uploadAction } from '@/api/manage'
import { compact } from '@/api/land/landAdmin'

/**
 * 数据管理 · 配套地块数据录入（1 宗地 N 配套 + 29 环节进度）接口
 * （方案 2.3.1（三）第 2 项）
 *
 * 后端：org.jeecg.modules.land.data.controller.FacilityAdminController
 * 权限码：land:data:facility
 *
 * 关键约定：
 *  1. ★ 新增必须挂到**真实存在**的宗地（后端会校验；旧系统不校验，
 *     设计文档实测产生了 60 行「孤儿配套」）。所以表单里宗地要用下拉选，
 *     不要手输 —— 手输是孤儿数据的主要来源；
 *  2. 同一宗地下「配套项目名称」不可重复，但**跨宗地可以同名**
 *     （不同地块各有一条同名道路是正常的）；
 *  3. 环节进度用 (ptId, lcId) 唯一定位：`saveProcess` 有则更新、无则新增；
 *  4. 阶段节点（六大阶段）**不能直接录进度**，只能录它下面的「事项」；
 *  5. 「预计结束时间」不填时后端按 `开始日 + 环节标准时长(工作日)` 自动推算，
 *     也可以调 `calcExpectEnd` 在前端先算出来给用户看。
 */

export const facilityAdminUrl = {
  list: '/land/data/facilityAdmin/list',
  geoDetail: '/land/data/facilityAdmin/geoDetail',
  detail: '/land/data/facilityAdmin/detail',
  byLand: '/land/data/facilityAdmin/byLand',
  checkPtxmmc: '/land/data/facilityAdmin/checkPtxmmc',
  add: '/land/data/facilityAdmin/add',
  edit: '/land/data/facilityAdmin/edit',
  delete: '/land/data/facilityAdmin/delete',
  deleteBatch: '/land/data/facilityAdmin/deleteBatch',
  // ---- 29 环节进度 ----
  processTree: '/land/data/facilityAdmin/processTree',
  processList: '/land/data/facilityAdmin/processList',
  processConfig: '/land/data/facilityAdmin/processConfig',
  processSave: '/land/data/facilityAdmin/process/save',
  processSaveBatch: '/land/data/facilityAdmin/process/saveBatch',
  processDelete: '/land/data/facilityAdmin/process/delete',
  expectEnd: '/land/data/facilityAdmin/expectEnd',
  // ---- 配套地块图形（GeoJSON）导入 ----
  // 后端是另一个 Controller（FacilityGeoImportController），不挂在 facilityAdmin 前缀下
  geoImportPreview: '/land/data/facilityGeoImport/preview',
  geoImportConfirm: '/land/data/facilityGeoImport/confirm'
}

/* ==========================================================================
 * 一、配套项目
 * ========================================================================== */

/** 分页列表 */
export function queryFacilityPage (params) {
  return getAction(facilityAdminUrl.list, compact(params))
}

/** 详情 */
export function queryFacilityDetail (id) {
  return getAction(facilityAdminUrl.detail, { id })
}

/**
 * 某宗地下的全部配套项目 + 各自的六大阶段进度汇总。
 *
 * ★ 「1 宗地 N 配套」的入口查询：一次拿到全部配套，含
 *   processStat（环节进度汇总）与 stageStatus（六个阶段各自的状态/完成度）。
 *   不要在前端对每个配套再查一次 processTree —— 那是 N+1。
 */
export function queryFacilityByLand (crzdbh) {
  return getAction(facilityAdminUrl.byLand, { crzdbh })
}

/** 名称唯一性 + 宗地存在性校验 */
export function checkPtxmmc (crzdbh, ptxmmc, excludeId) {
  return getAction(facilityAdminUrl.checkPtxmmc, compact({ crzdbh, ptxmmc, excludeId }))
}

/** 新增（必须挂到已有宗地） */
export function addFacility (data) {
  return postAction(facilityAdminUrl.add, normalizeFacility(data))
}

/** 编辑 */
export function editFacility (data) {
  return putAction(facilityAdminUrl.edit, normalizeFacility(data))
}

/** 移除（软删，delFlag 置 1，可恢复） */
export function deleteFacility (id, reason) {
  return deleteAction(facilityAdminUrl.delete, compact({ id, reason }))
}

/** 批量移除 */
export function deleteFacilityBatch (ids, reason) {
  const value = Array.isArray(ids) ? ids.join(',') : ids
  return deleteAction(facilityAdminUrl.deleteBatch, compact({ ids: value, reason }))
}

/* ==========================================================================
 * 二、29 环节进度
 * ========================================================================== */

/**
 * 阶段进度树（六大阶段 × 24 事项 + 汇总）。
 *
 * ★ 未录入的环节也会返回（filled=false、lcqk=未开启）——
 *   录入界面必须显示全部事项，否则用户不知道还有哪些没填。
 */
export function queryProcessTree (ptId) {
  return getAction(facilityAdminUrl.processTree, { ptId })
}

/** 环节进度扁平列表（表格展示用，24 行，含阶段名与阶段汇总） */
export function queryProcessList (ptId) {
  return getAction(facilityAdminUrl.processList, { ptId })
}

/**
 * 环节配置骨架（阶段 + 事项）。
 *
 * ★ 页面**先**用它在数据加载前渲染出 29 环节的界面骨架，
 *   再用 processTree 填值 —— 这样用户不会先看到一片空白再「跳」出内容。
 */
export function queryProcessConfig () {
  return getAction(facilityAdminUrl.processConfig, {})
}

/**
 * 保存单个环节进度。
 *
 * @param {string} ptId 配套项目ID
 * @param {string} lcId 环节ID
 * @param {object} payload lcqk,lckssj,yjjssj,lcjssj,czwtlx,jtwt,gzjy,lrdw,lrr,lxdh
 */
export function saveProcess (ptId, lcId, payload) {
  return postAction(`${facilityAdminUrl.processSave}?ptId=${encodeURIComponent(ptId)}&lcId=${encodeURIComponent(lcId)}`,
    normalizeProcess(payload))
}

/**
 * 整屏保存环节进度。
 *
 * ★ 逐个保存并返回失败明细，**部分失败不影响其它环节** ——
 *   所以必须把 failures 展示给用户，不能只报「保存成功」。
 *
 * @param {string} ptId
 * @param {Array} items [{lcId, lcName, lcqk, ...}]
 */
export function saveProcessBatch (ptId, items) {
  const payload = (items || []).map(item => Object.assign(normalizeProcess(item), {
    lcId: item.lcId,
    lcName: item.lcName
  }))
  return postAction(`${facilityAdminUrl.processSaveBatch}?ptId=${encodeURIComponent(ptId)}`, payload)
}

/** 撤回某环节进度（回到「未填报」） */
export function deleteProcess (ptId, lcId) {
  return deleteAction(facilityAdminUrl.processDelete, { ptId, lcId })
}

/**
 * 按工作日推算预计结束时间。
 *
 * @param {string} startDate yyyy-MM-dd
 * @param {number} days 环节标准时长（工作日天数）
 * @returns result: {startDate, workdays, endDate, actualWorkdays, calendarBased, maintainedYears}
 *          ★ calendarBased=false 表示该年份未维护节假日日历，
 *            结果按「周末 + 内置法定节假日」估算，界面应给出提示。
 */
export function calcExpectEnd (startDate, days) {
  return getAction(facilityAdminUrl.expectEnd, { startDate, days })
}

/* ==========================================================================
 * 三、提交前清洗
 * ========================================================================== */

/** 配套项目的数值列（BigDecimal） */
const FACILITY_NUMBER_FIELDS = ['ghhxkd', 'cd', 'tzgs', 'gspfje']
/** 配套项目的日期列 */
const FACILITY_DATE_FIELDS = [
  'dkcrscndptjgsj', 'yjkgsj', 'sjkgsj', 'yjjgsj', 'sjjgsj'
]

/** 空串转 null、数值转 Number（与 landAdmin.normalize 同一口径） */
export function normalizeFacility (data) {
  if (!data) {
    return data
  }
  const payload = Object.assign({}, data)
  // 文本列：把 '' 转 null（只处理显然的字符串字段，避免误伤 id）
  Object.keys(payload).forEach(key => {
    if (payload[key] === '' && FACILITY_NUMBER_FIELDS.indexOf(key) < 0 &&
      FACILITY_DATE_FIELDS.indexOf(key) < 0) {
      payload[key] = null
    }
  })
  FACILITY_DATE_FIELDS.forEach(field => {
    if (payload[field] === '') {
      payload[field] = null
    }
  })
  FACILITY_NUMBER_FIELDS.forEach(field => {
    const value = payload[field]
    if (value === '' || value === null || value === undefined) {
      payload[field] = null
    } else {
      const num = Number(value)
      payload[field] = isNaN(num) ? null : num
    }
  })
  return payload
}

/** 环节进度的日期列 */
const PROCESS_DATE_FIELDS = ['lckssj', 'yjjssj', 'lcjssj']

/** 环节进度提交前清洗 */
export function normalizeProcess (payload) {
  const data = Object.assign({}, payload)
  PROCESS_DATE_FIELDS.forEach(field => {
    if (data[field] === '') {
      data[field] = null
    }
  })
  return data
}

/* ==========================================================================
 * 四、配套地块图形（GeoJSON）导入
 *
 * 后端：org.jeecg.modules.land.data.imports.facilitygeo.controller.FacilityGeoImportController
 * 权限码：land:data:facility（入口是「配套地块数据录入」页工具条上的「导入地块图形」，
 *         与大屏端同名按钮走同一套接口）
 *
 * 约定（与 landGeoImport.js 结构相同，差异只有匹配键）：
 *  1. ★ 必须先「预览校验」再「确认导入」：预览绝不写库，有错误时整批不写；
 *  2. 接口无状态：预览与确认各传一次同一文件（确认时后端重新解析，
 *     保证写进去的就是此刻文件里的内容）；
 *  3. ★ 配套按 properties.PTXMMC（配套项目名称，归一后）匹配
 *     t_supporting_facilities.ptxmmc，同名称跨宗地的多条记录**全部**更新
 *     （道路类配套「一条路穿过好几宗地」是业务常态）；宗地那边按 CRZDBH 匹配，别照抄；
 *  4. 留痕走每条配套的变更记录（ACTION_IMPORT，含来源文件/时间/操作人），
 *     不写 t_facility_import_log —— 那是配套 Excel 导入的「导入记录」表。
 */

/**
 * 预览校验（★ 不写库）。
 *
 * @param {File} file .geojson / .json 文件
 * @returns {Promise} result 含 totalFeatures / validFeatures / willUpdateRows /
 *   updatedRows / unchangedRows / unmatchedCount / multiRecordNames / errors / warnings / notices
 */
export function previewFacilityGeoImport (file) {
  const formData = new FormData()
  formData.append('file', file)
  // 几十 MB 文件解析 + 批量写远程库：远超全局超时，单独放宽到 5 分钟
  return uploadAction(facilityAdminUrl.geoImportPreview, formData, { timeout: 300000 })
}

/**
 * 确认入库（重新解析文件后按配套项目名称匹配更新）。
 *
 * @param {File} file 与预览时相同的文件
 * @returns {Promise} result 含 updatedRows 等实际写入计数
 */
export function confirmFacilityGeoImport (file) {
  const formData = new FormData()
  formData.append('file', file)
  return uploadAction(facilityAdminUrl.geoImportConfirm, formData, { timeout: 300000 })
}

export default {
  facilityAdminUrl,
  queryFacilityPage,
  queryFacilityDetail,
  queryFacilityByLand,
  checkPtxmmc,
  addFacility,
  editFacility,
  deleteFacility,
  deleteFacilityBatch,
  queryProcessTree,
  queryProcessList,
  queryProcessConfig,
  saveProcess,
  saveProcessBatch,
  deleteProcess,
  calcExpectEnd,
  normalizeFacility,
  normalizeProcess,
  previewFacilityGeoImport,
  confirmFacilityGeoImport
}
