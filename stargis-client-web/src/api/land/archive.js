import {
  javaGetAction,
  javaPostAction,
  javaPutAction,
  javaDeleteAction,
  buildJavaDownloadUrl,
} from '@/api/manageJava'

/**
 * 档案管理 · 档案维护 / 档案查询 / 档案统计 接口
 *
 * 后端：org.jeecg.modules.land.archive.controller.ArchiveController
 * 权限码：land:archive:*
 *
 * 【重要】这些接口在 **Java 业务后端**（window._CONFIG.VUE_DATA_JAVA_URL，
 * 例如 http://127.0.0.1:9802/api），不在中台（VUE_APP_API_BASE_URL，4548）上。
 * 所以这里统一用 `@/api/manageJava` 的请求方法，而不是 `@/api/manage`。
 * 判断依据：admin-client 能正常调这些接口，而它的 domianURL 正好等于 Java 后端地址。
 *
 * 与 admin-client 共用同一套后端，本文件与
 * admin-client/src/api/land/archive.js 保持一一对应，只做必要注释补充。
 * 两边如需改动接口，请同步修改，避免契约漂移。
 *
 * 关键约定：
 *  1. 档案类别挂在「卷内文件」上，所以 files 里每个元素都必须带 categoryId；
 *  2. 档案号默认由后端生成（DA-{yyyy}-{4位}），但允许用户手工改写；
 *  3. 导出 ZIP / 下载单文件走浏览器原生下载（不走 axios），URL 里拼 token。
 */

export const archiveUrl = {
  list: '/land/archive/list',
  queryById: '/land/archive/queryById',
  add: '/land/archive/add',
  edit: '/land/archive/edit',
  delete: '/land/archive/delete',
  deleteBatch: '/land/archive/deleteBatch',
  status: '/land/archive/status',
  generateNo: '/land/archive/generateNo',
  checkNo: '/land/archive/checkNo',
  stat: '/land/archive/stat',
  export: '/land/archive/export',
  logs: '/land/archive/logs',
  fileList: '/land/archive/file/list',
  fileDownload: '/land/archive/file/download',
  fileDelete: '/land/archive/file/delete',
  relatedDocuments: '/land/archive/relatedDocuments'
}

/** 档案分页列表（档案维护 / 档案查询共用） */
export function queryArchiveList (params) {
  return javaGetAction(archiveUrl.list, params)
}

/** 档案详情（含卷内文件） */
export function queryArchiveById (id) {
  return javaGetAction(archiveUrl.queryById, { id })
}

/** 新增档案（body 里带 files） */
export function addArchive (data) {
  return javaPostAction(archiveUrl.add, data)
}

/** 编辑档案（body 里带 files，服务端做增/改/删三向同步） */
export function editArchive (data) {
  return javaPutAction(archiveUrl.edit, data)
}

/** 删除档案 */
export function deleteArchive (id) {
  return javaDeleteAction(archiveUrl.delete, { id })
}

/** 批量删除档案 */
export function deleteArchiveBatch (ids) {
  return javaDeleteAction(archiveUrl.deleteBatch, { ids: Array.isArray(ids) ? ids.join(',') : ids })
}

/** 变更档案状态 */
export function changeArchiveStatus (id, status) {
  return javaPostAction(archiveUrl.status, { id, status })
}

/** 生成档案号（仅预览，不落库） */
export function generateArchiveNo (year) {
  return javaGetAction(archiveUrl.generateNo, year ? { year } : {})
}

/** 档案号唯一校验 */
export function checkArchiveNo (params) {
  return javaGetAction(archiveUrl.checkNo, params)
}

/**
 * 档案统计
 * @param {object} params 与列表同一套查询条件
 * @param {number} [projectLimit] 按项目统计的条数
 */
export function queryArchiveStat (params, projectLimit) {
  return javaGetAction(archiveUrl.stat, Object.assign({}, params, { projectLimit: projectLimit || 20 }))
}

/** 档案操作记录 */
export function queryArchiveLogs (archiveId) {
  return javaGetAction(archiveUrl.logs, { archiveId })
}

/** 某档案的卷内文件 */
export function queryArchiveFiles (archiveId) {
  return javaGetAction(archiveUrl.fileList, { archiveId })
}

/** 删除单个卷内文件 */
export function deleteArchiveFile (id) {
  return javaDeleteAction(archiveUrl.fileDelete, { id })
}

/**
 * 该项目的收发文情况（档案详情页的「收发文情况」块）
 * @param {{facilityId?: string, landId?: string, crzdbh?: string, limit?: number}} params
 */
export function queryRelatedDocuments (params) {
  return javaGetAction(archiveUrl.relatedDocuments, params)
}

/** 下载单个卷内文件（浏览器直接下载，服务端会写「下载」操作记录） */
export function downloadArchiveFile (id) {
  window.open(buildJavaDownloadUrl(archiveUrl.fileDownload, { id }), '_blank')
}

/**
 * 按当前查询条件导出 ZIP。
 * 用 window.open 而不是 axios：文件较大，且需要浏览器的原生下载行为；
 * URL 里会带上 token（JwtFilter 支持从 query string 取令牌）。
 * @param {object} params 与列表同一套查询条件
 */
export function exportArchiveZip (params) {
  window.open(buildJavaDownloadUrl(archiveUrl.export, params), '_blank')
}

export { buildJavaDownloadUrl }

export default {
  archiveUrl,
  buildJavaDownloadUrl,
  queryArchiveList,
  queryArchiveById,
  addArchive,
  editArchive,
  deleteArchive,
  deleteArchiveBatch,
  changeArchiveStatus,
  generateArchiveNo,
  checkArchiveNo,
  queryArchiveStat,
  queryArchiveLogs,
  queryArchiveFiles,
  deleteArchiveFile,
  queryRelatedDocuments,
  downloadArchiveFile,
  exportArchiveZip
}
