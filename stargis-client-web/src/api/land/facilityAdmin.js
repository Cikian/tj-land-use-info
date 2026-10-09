import {
  javaDeleteAction,
  javaGetAction,
  javaPostAction,
  javaPutAction
} from '@/api/manageJava'

/**
 * 数据管理 · 配套地块数据录入（1 宗地 N 配套 + 29 环节进度）接口
 * ===============================================================
 * 后端：org.jeecg.modules.land.data.controller.FacilityAdminController
 * 权限码：land:data:facility
 *
 * 【为什么用 manageJava 而不是 @/api/manage】
 * 接口在 Java 业务后端（VUE_DATA_JAVA_URL），不在中台。详见 manageJava.js 头注释。
 *
 * 【与 admin-client 的关系】一一对应、共用同一套后端，参数名与取值口径必须同步。
 *
 * 关键约定：
 *  1. ★ `GET /list` 复用的是宗地的 `LandAdminQueryDTO`，字段名相同但语义有两处
 *     被后端「借用」：`srr` 实际承载的是**配套项目名称**模糊条件，
 *     `facilityKeyword` 是「名称或宗地编号」的模糊条件。别按字段名猜语义。
 *  2. ★ `/process/save` 与 `/process/saveBatch` 的 ptId / lcId 是 **query 参数**，
 *     而进度字段在 JSON body 里；manageJava 的 javaPostAction 第二个参数只进 body，
 *     所以这里手工把 query 拼在 URL 上（与 ledger.js 的 changeLedgerStatus 同一做法）。
 *  3. ★ `/byLand` 返回的每个配套带 `processStat`（环节进度汇总）与
 *     `stageStatus`（六大阶段各自的 { stageId, stageName, status, percent, filledCount }），
 *     列表页的阶段状态标签直接用它，**不要在前端重算汇总** —— 汇总口径
 *     （尤其「不涉及」怎么算）在后端 ProcessStatus 里是唯一实现。
 *  4. ★ `/expectEnd` 的 `calendarBased=false` 表示该年度没有人工维护的节假日日历，
 *     后端只按「周末 + 配置的节假日」推算，结果会偏乐观，必须提示用户。
 */

export const facilityAdminUrl = {
  list: '/land/data/facilityAdmin/list',
  detail: '/land/data/facilityAdmin/detail',
  byLand: '/land/data/facilityAdmin/byLand',
  checkPtxmmc: '/land/data/facilityAdmin/checkPtxmmc',
  add: '/land/data/facilityAdmin/add',
  edit: '/land/data/facilityAdmin/edit',
  delete: '/land/data/facilityAdmin/delete',
  deleteBatch: '/land/data/facilityAdmin/deleteBatch',
  processTree: '/land/data/facilityAdmin/processTree',
  processList: '/land/data/facilityAdmin/processList',
  processConfig: '/land/data/facilityAdmin/processConfig',
  processSave: '/land/data/facilityAdmin/process/save',
  processSaveBatch: '/land/data/facilityAdmin/process/saveBatch',
  processDelete: '/land/data/facilityAdmin/process/delete',
  expectEnd: '/land/data/facilityAdmin/expectEnd'
}

/* ==========================================================================
 * 一、配套项目（8 个）
 * ========================================================================== */

/**
 * 配套项目分页列表。
 *
 * @param {object} params 复用 LandAdminQueryDTO：
 *   crzdbh（模糊）/ dkmc（模糊）/ xzqh（精确）/ xmfl（精确）
 *   srr（★ 实际是配套项目名称的模糊条件）
 *   facilityKeyword（★ 名称或宗地编号的模糊条件）
 *   pageNo / pageSize（后端封顶 500）
 */
export function queryFacilityAdminPage (params) {
  return javaGetAction(facilityAdminUrl.list, params)
}

/** 配套项目详情（返回 Facility 实体；已移除的返回 success=false） */
export function queryFacilityAdminById (id) {
  return javaGetAction(facilityAdminUrl.detail, { id })
}

/**
 * 某宗地下的全部配套 + 各自的环节进度汇总（「1 宗地 N 配套」的入口）。
 *
 * @param {string} crzdbh
 * @returns {Promise} result 为数组，每项：
 *   { id, crzdbh, ptxmmc, dkmc, ptsslb, xzqh, xmfl, jsdw, tzgs,
 *     sfkg, sfjg, sfyj, lrr, createTime,
 *     processStat: { filled, finished, running, notInvolved, overdue, status, percent },
 *     stageStatus: [{ stageId, stageName, status, percent, filledCount }] }
 */
export function queryFacilityByLand (crzdbh) {
  return javaGetAction(facilityAdminUrl.byLand, { crzdbh })
}

/**
 * 配套项目名称唯一性 + 宗地存在性校验（一次调用给两个结论）。
 *
 * @param {string} crzdbh
 * @param {string} ptxmmc
 * @param {string} [excludeId] 编辑时排除自身
 * @returns {Promise} result 为 { available, message, occupiedBy? }
 *   ★ 宗地不存在时的 message 是「出让宗地编号「xxx」不存在，请先录入该宗地」，
 *     这是最常见的失败原因（旧库有 60 行孤儿配套），要原样展示给用户。
 */
export function checkPtxmmc (crzdbh, ptxmmc, excludeId) {
  const params = { crzdbh, ptxmmc }
  if (excludeId) {
    params.excludeId = excludeId
  }
  return javaGetAction(facilityAdminUrl.checkPtxmmc, params)
}

/**
 * 新增配套项目。
 *
 * @param {object} data FacilitySaveDTO：Facility 的全部 53 个业务字段 + removeReason
 *   ★ 服务端硬校验「宗地必须存在」（旧系统不校验，产生过 60 行孤儿配套），
 *     所以 crzdbh 一定要先通过宗地下拉选中，不能手打一个不存在的编号。
 *   ★ 数值字段（ghhxkd / cd / tzgs / gspfje）空值必须传 null。
 * @returns {Promise} result 为新建配套的 id
 */
export function addFacilityAdmin (data) {
  return javaPostAction(facilityAdminUrl.add, data)
}

/** 编辑配套项目 */
export function editFacilityAdmin (data) {
  return javaPutAction(facilityAdminUrl.edit, data)
}

/** 移除配套项目（软删；本表 delFlag 是 varchar，后端手工置 '1'） */
export function deleteFacilityAdmin (id, reason) {
  return javaDeleteAction(
    facilityAdminUrl.delete,
    reason ? { id, reason } : { id }
  )
}

/** 批量移除配套项目，返回 { successCount, failCount, failures } */
export function deleteFacilityAdminBatch (ids, reason) {
  const params = { ids: Array.isArray(ids) ? ids.join(',') : ids }
  if (reason) {
    params.reason = reason
  }
  return javaDeleteAction(facilityAdminUrl.deleteBatch, params)
}

/* ==========================================================================
 * 二、29 环节进度（6 个）
 * ========================================================================== */

/**
 * 阶段进度树（6 阶段 × 各事项 + 阶段与顶层汇总）。
 *
 * ★ 未录入的环节**也会返回**（filled=false、lcqk=未开启）——
 *   录入界面必须列出全部事项，否则用户不知道还有哪些没填。
 *
 * @param {string} ptId 配套项目 id
 * @returns {Promise} result 为 FacilityProcessTreeVO：
 *   { ptId, crzdbh, facilityName, stages: [...],
 *     totalItems, finishedItems, runningItems, notInvolvedItems,
 *     notStartedItems, filledItems, overdueItems, percent }
 *   stages[i] = { stageId, stageName, path, processTime, status, percent,
 *                 filledCount, itemCount, overdueCount, items: [...] }
 *   items[j]  = { lcId, lcName, path, zgbm, processTime, location, isParallel,
 *                 progressId, lcqk, lckssj, yjjssj, lcjssj, czwtlx, jtwt, gzjy,
 *                 lrdw, lrr, lxdh, overdue, overdueDays, attachmentCount, filled }
 */
export function queryProcessTree (ptId) {
  return javaGetAction(facilityAdminUrl.processTree, { ptId })
}

/**
 * 环节进度扁平列表（喜欢表格视图的人用，或导出用）。
 *
 * @param {string} ptId
 * @returns {Promise} result 为数组，每项比树多出 stageName / stageStatus /
 *   stagePercent / index，比树少 path / lrdw / location / isParallel。
 */
export function queryProcessList (ptId) {
  return javaGetAction(facilityAdminUrl.processList, { ptId })
}

/**
 * 环节配置骨架（阶段 + 事项 + 标准时长 + 主管部门）。
 *
 * 用途：在进度数据到达之前先把「六个阶段、每个阶段有哪些事项」渲染出来，
 * 让用户先看到骨架而不是一块空白。
 */
export function queryProcessConfig () {
  return javaGetAction(facilityAdminUrl.processConfig, {})
}

/**
 * 保存单个环节进度（有则更新、无则新增）。
 *
 * @param {string} ptId 配套项目 id
 * @param {string} lcId 环节 id
 * @param {object} payload 进度字段：
 *   lcqk（必填，∈ land_process_status 四个取值）、lckssj、yjjssj、lcjssj、
 *   czwtlx、jtwt、gzjy、lrdw、lrr、lxdh（日期一律 yyyy-MM-dd）
 *   ★ 服务端会做状态与时间的自洽校验：
 *     「已完成」必须填 lcjssj、lcjssj 不得早于 lckssj、lcqk 必须合法。
 * @returns {Promise} result 为进度记录 id
 */
export function saveProcess (ptId, lcId, payload) {
  const query = `?ptId=${encodeURIComponent(ptId)}&lcId=${encodeURIComponent(lcId)}`
  return javaPostAction(facilityAdminUrl.processSave + query, payload || {})
}

/**
 * 整屏保存：一次提交多个环节，服务端逐个保存并返回失败明细。
 *
 * @param {string} ptId
 * @param {object[]} items 每项 = 单条 payload + { lcId, lcName }
 *   ★ 没有 lcId 的项会被服务端静默跳过，所以要先把 lcId 补上。
 * @returns {Promise} result 为 { successCount, failCount, failures: [{ lcId, lcName, reason }] }
 *   ★ 部分成功是常态，失败明细必须逐条展示。
 */
export function saveProcessBatch (ptId, items) {
  const query = `?ptId=${encodeURIComponent(ptId)}`
  return javaPostAction(facilityAdminUrl.processSaveBatch + query, items || [])
}

/** 撤回某个环节的进度（回到「未填报」；后端是物理删除，会留一条 DELETE 履历） */
export function deleteProcess (ptId, lcId) {
  return javaDeleteAction(facilityAdminUrl.processDelete, { ptId, lcId })
}

/**
 * 按工作日推算预计结束时间。
 *
 * @param {string} startDate yyyy-MM-dd
 * @param {number} days 标准办理时长（工作日）
 * @returns {Promise} result 为 { endDate, workdays, calendarBased, maintainedYears }
 *   ★ calendarBased=false → 该年度没有人工维护的节假日日历，推算结果只剔除了周末，
 *     会偏乐观，界面必须给一条明确的警告。
 */
export function queryExpectEnd (startDate, days) {
  return javaGetAction(facilityAdminUrl.expectEnd, { startDate, days })
}

export default {
  facilityAdminUrl,
  queryFacilityAdminPage,
  queryFacilityAdminById,
  queryFacilityByLand,
  checkPtxmmc,
  addFacilityAdmin,
  editFacilityAdmin,
  deleteFacilityAdmin,
  deleteFacilityAdminBatch,
  queryProcessTree,
  queryProcessList,
  queryProcessConfig,
  saveProcess,
  saveProcessBatch,
  deleteProcess,
  queryExpectEnd
}
