import { javaGetAction, javaPostAction } from '@/api/manageJava'

/**
 * 数据管理 · 数据更新与移除（软删 / 恢复 / 变更留痕）接口（方案 2.3.1（三）第 6 项）
 * ===============================================================
 * 后端：org.jeecg.modules.land.data.controller.DataRecycleController
 * 权限码：land:data:recycle
 *
 * 【为什么「移除」不在这里、而在 landAdmin / facilityAdmin】
 * 移除是录入页上的动作（那里已用 land:data:land / land:data:facility 把关），
 * 而本页是**回收站与留痕** —— 它既能恢复数据（等于一次写入），
 * 又能看到全部变更履历（含别人的操作），敏感度高于普通查看，所以单独一个码。
 *
 * 关键约定：
 *  1. ★ 两张表的软删列名不同：`t_land.del_flag`（下划线 tinyint）vs
 *     `xj_kjkfb_supporting_facilities.delFlag`（驼峰 varchar）。后端已经统一成
 *     同一结构返回，前端只管 `bizType`；
 *  2. ★ **恢复的两种结局必须分开处理**：
 *     · 宗地有唯一键 (crzdbh, del_flag)，同编号冲突时后端**抛错阻断**
 *       （success=false，message 是「无法恢复：出让宗地编号「xxx」已被另一条有效记录占用…」）
 *       → 前端弹 error，不要吞掉；
 *     · 配套没有唯一键，同名只**提示不阻断**：后端把话放在 `result.warning` 里，
 *       同时 `message` 也是这句话（Result.OK(warning, result)）
 *       → 前端弹 info 级别的提示，且要把 warning 原文展示出来。
 *  3. ★ 变更留痕的字段级明细在 `details` 里（后端已把 change_detail 的 JSON 解析好）：
 *     `[{ field, label, before, after }]`，前端直接渲染「字段 / 改前 / 改后」三列表格。
 */

export const dataRecycleUrl = {
  list: '/land/data/recycle/list',
  summary: '/land/data/recycle/summary',
  restore: '/land/data/recycle/restore',
  restoreBatch: '/land/data/recycle/restoreBatch',
  changeLog: '/land/data/recycle/changeLog',
  history: '/land/data/recycle/history',
  actionDistribution: '/land/data/recycle/actionDistribution',
  actionOptions: '/land/data/recycle/actionOptions'
}

/* ==========================================================================
 * 一、回收站（2 个）
 * ========================================================================== */

/**
 * 回收站列表（宗地 + 配套合并，可筛类型 / 关键字）。
 *
 * @param {{bizType?: string, keyword?: string}} params
 *   bizType 空 = 两类都查；keyword 命中编号或名称
 * @returns {Promise} result 为数组，每项（两类结构统一）：
 *   { bizType: 'land'|'facility', id, bizKey, name, xzqh, xmfl, lrdw, lrr,
 *     projectName, updateBy, updateTime, delFlag }
 *   ★ 展示口径：`name` 是宗地的地块名称 / 配套的配套项目名称；
 *     `bizKey` 是宗地编号 / 配套的宗地编号 —— 两者语义不同，不要混用一列。
 */
export function queryRecycleList (params) {
  return javaGetAction(dataRecycleUrl.list, params || {})
}

/**
 * 回收站计数。
 * @returns {Promise} result 为 { landDeleted, facilityDeleted, total }
 */
export function queryRecycleSummary () {
  return javaGetAction(dataRecycleUrl.summary, {})
}

/* ==========================================================================
 * 二、恢复（2 个）
 * ========================================================================== */

/**
 * 恢复一条（★ 两种结局，见文件头注释第 2 条）。
 *
 * @param {string} bizType land / facility
 * @param {string} id
 * @returns {Promise} result 为 { success, bizType, id, bizKey, message, warning? }
 */
export function restoreRecycle (bizType, id) {
  return javaPostAction(
    `${dataRecycleUrl.restore}?bizType=${encodeURIComponent(bizType)}&id=${encodeURIComponent(id)}`,
    {}
  )
}

/**
 * 批量恢复（后端逐条恢复并返回失败明细）。
 *
 * @param {string} bizType
 * @param {string[]|string} ids
 * @returns {Promise} result 为 { successCount, failCount, failures: [{ id, reason }],
 *   warnings: string[] }
 *   ★ warnings 是「同名配套」这类不阻断的提示，failures 才是真的没恢复成功，
 *     两者要分别展示（把 warning 当成失败会让用户以为白干了）。
 */
export function restoreRecycleBatch (bizType, ids) {
  const value = Array.isArray(ids) ? ids.join(',') : ids
  return javaPostAction(
    `${dataRecycleUrl.restoreBatch}?bizType=${encodeURIComponent(bizType)}&ids=${encodeURIComponent(value)}`,
    {}
  )
}

/* ==========================================================================
 * 三、变更留痕（4 个）
 * ========================================================================== */

/**
 * 变更留痕分页。
 *
 * ★ 这个接口的返回结构与其它分页接口**不一样**：不是 jeecg 的 IPage，
 *   而是 { records, total, pageNo, pageSize, pages }，取值时别按
 *   `result.records` 之外的结构猜。
 *
 * @param {object} params bizType / action / operator / keyword / pageNo / pageSize
 * @returns {Promise} result 为 { records: DataChangeLog[], total, pageNo, pageSize, pages }
 *   DataChangeLog 除库内字段外还有展示字段：
 *   actionText（中文动作）/ actionColor（antd 预设色）/ details（字段级明细数组）
 */
export function queryChangeLogPage (params) {
  return javaGetAction(dataRecycleUrl.changeLog, params)
}

/**
 * 某一条数据的完整履历（列表页「履历」入口用）。
 *
 * @param {string} bizType land / facility / process / attachment
 * @param {string} bizId
 * @param {number} [limit] 默认 50，后端封顶 200
 */
export function queryChangeHistory (bizType, bizId, limit) {
  return javaGetAction(
    dataRecycleUrl.history,
    limit ? { bizType, bizId, limit } : { bizType, bizId }
  )
}

/**
 * 动作分布（统计卡用）。
 * @returns {Promise} result 为 [{ action, actionText, num }]
 */
export function queryActionDistribution (bizType) {
  return javaGetAction(dataRecycleUrl.actionDistribution, bizType ? { bizType } : {})
}

/**
 * 动作下拉（后端给的是 value/label，label 已是中文）。
 * @returns {Promise} result 为 [{ value: 'CREATE', label: '新增' }, …]
 */
export function queryActionOptions () {
  return javaGetAction(dataRecycleUrl.actionOptions, {})
}

/* ==========================================================================
 * 四、本地兜底常量与展示辅助
 * ========================================================================== */

/**
 * 动作的本地兜底（与后端 ChangeLogSupport.actionOptions 逐字一致）。
 * ★ value 是英文常量（CREATE/UPDATE/DELETE/RESTORE/IMPORT/UPLOAD/ATTACH_DELETE），
 *   不是中文 —— 后端按英文常量筛，传中文会筛出 0 条。
 */
export const CHANGE_ACTIONS_FALLBACK = [
  { value: 'CREATE', label: '新增' },
  { value: 'UPDATE', label: '修改' },
  { value: 'DELETE', label: '移除' },
  { value: 'RESTORE', label: '恢复' },
  { value: 'IMPORT', label: '批量导入' },
  { value: 'UPLOAD', label: '附件上传' },
  { value: 'ATTACH_DELETE', label: '附件删除' }
]

/** 业务类型下拉（回收站与留痕共用） */
export const RECYCLE_BIZ_TYPES = [
  { value: 'land', label: '经营性用地' },
  { value: 'facility', label: '配套项目' }
]

/** 业务类型 → 中文 */
export function bizTypeText (bizType) {
  switch (bizType) {
    case 'land':
      return '经营性用地'
    case 'facility':
      return '配套项目'
    case 'process':
      return '环节进度'
    case 'attachment':
      return '附件'
    default:
      return bizType || '—'
  }
}

/**
 * 说明：本文件**没有删除接口**。移除动作在 landAdmin / facilityAdmin 里
 * （录入页上的动作，用各自的权限码把关）；回收站这一页只做「恢复」与「留痕查看」，
 * 这也是后端只给 DataRecycleController 一个 restore/restoreBatch 的原因。
 */

export default {
  dataRecycleUrl,
  CHANGE_ACTIONS_FALLBACK,
  RECYCLE_BIZ_TYPES,
  bizTypeText,
  queryRecycleList,
  queryRecycleSummary,
  restoreRecycle,
  restoreRecycleBatch,
  queryChangeLogPage,
  queryChangeHistory,
  queryActionDistribution,
  queryActionOptions
}
