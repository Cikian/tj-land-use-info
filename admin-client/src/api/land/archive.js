import Vue from 'vue'
import { getAction, postAction, putAction, deleteAction } from '@/api/manage'
import { ACCESS_TOKEN } from '@/store/mutation-types'

/**
 * 浏览器原生下载（文件流）不能带自定义请求头，而 jeecg 的 JwtFilter 同时支持
 * 从 query string 的 token 参数取令牌，所以这里统一拼上 token。
 */
function buildDownloadUrl (path, params) {
  const query = []
  Object.keys(params || {}).forEach(key => {
    const value = params[key]
    if (value !== undefined && value !== null && value !== '') {
      query.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    }
  })
  const token = Vue.ls.get(ACCESS_TOKEN)
  if (token) {
    query.push(`token=${encodeURIComponent(token)}`)
  }
  const base = window._CONFIG['domianURL'] || ''
  return `${base}${path}${query.length ? '?' + query.join('&') : ''}`
}

/**
 * 档案管理接口（方案 2.3.2 第 2/3/4/5 项）
 *
 * 后端：org.jeecg.modules.land.archive.controller.ArchiveController
 * 权限码：archive:manage
 *
 * 关键约定：
 *  1. 档案类别挂在「卷内文件」上，所以 files 里每个元素都必须带 categoryId；
 *  2. 档案号默认由后端生成（DA-{yyyy}-{4位}），但允许用户手工改写；
 *  3. 导出 ZIP 走 {@link exportArchiveZip}（浏览器直接下载，不走 axios）。
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
  return getAction(archiveUrl.list, params)
}

/** 档案详情（含卷内文件） */
export function queryArchiveById (id) {
  return getAction(archiveUrl.queryById, { id })
}

/** 新增档案（body 里带 files） */
export function addArchive (data) {
  return postAction(archiveUrl.add, data)
}

/** 编辑档案（body 里带 files，服务端做增/改/删三向同步） */
export function editArchive (data) {
  return putAction(archiveUrl.edit, data)
}

/** 删除档案 */
export function deleteArchive (id) {
  return deleteAction(archiveUrl.delete, { id })
}

/** 批量删除档案 */
export function deleteArchiveBatch (ids) {
  return deleteAction(archiveUrl.deleteBatch, { ids: Array.isArray(ids) ? ids.join(',') : ids })
}

/** 变更档案状态 */
export function changeArchiveStatus (id, status) {
  return postAction(archiveUrl.status, { id, status })
}

/** 生成档案号（仅预览） */
export function generateArchiveNo (year) {
  return getAction(archiveUrl.generateNo, year ? { year } : {})
}

/** 档案号唯一校验 */
export function checkArchiveNo (params) {
  return getAction(archiveUrl.checkNo, params)
}

/**
 * 档案统计
 * @param {object} params 与列表同一套查询条件
 * @param {number} [projectLimit] 按项目统计的条数
 */
export function queryArchiveStat (params, projectLimit) {
  return getAction(archiveUrl.stat, Object.assign({}, params, { projectLimit: projectLimit || 20 }))
}

/** 档案操作记录 */
export function queryArchiveLogs (archiveId) {
  return getAction(archiveUrl.logs, { archiveId })
}

/** 某档案的卷内文件 */
export function queryArchiveFiles (archiveId) {
  return getAction(archiveUrl.fileList, { archiveId })
}

/** 删除单个卷内文件 */
export function deleteArchiveFile (id) {
  return deleteAction(archiveUrl.fileDelete, { id })
}

/**
 * 该项目的收发文情况（档案详情页的「收发文情况」块）
 * @param {{facilityId?: string, landId?: string, crzdbh?: string, limit?: number}} params
 */
export function queryRelatedDocuments (params) {
  return getAction(archiveUrl.relatedDocuments, params)
}

/** 下载单个卷内文件（浏览器直接下载，服务端会写「下载」操作记录） */
export function downloadArchiveFile (id) {
  window.open(buildDownloadUrl(archiveUrl.fileDownload, { id }), '_blank')
}

/**
 * 按当前查询条件导出 ZIP。
 * 用 window.open 而不是 axios：文件较大，且需要浏览器的原生下载行为；
 * URL 里会带上 token（JwtFilter 支持从 query string 取令牌）。
 * @param {object} params 与列表同一套查询条件
 */
export function exportArchiveZip (params) {
  window.open(buildDownloadUrl(archiveUrl.export, params), '_blank')
}

export { buildDownloadUrl }

export default {
  archiveUrl,
  buildDownloadUrl,
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
