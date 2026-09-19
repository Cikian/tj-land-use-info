import { getAction, postAction, putAction, deleteAction } from '@/api/manage'

/**
 * 收发文管理接口（方案 2.3.2 第 9 项）
 *
 * 后端：
 *   org.jeecg.modules.land.archive.document.controller.DocReceiveController（/land/doc/receive）
 *   org.jeecg.modules.land.archive.document.controller.DocSendController（/land/doc/send）
 * 权限码：doc:receive:manage / doc:send:manage
 *
 * 流转模型（收文，精简三级 + 退回）：
 *   登记 ─► 待承办 ─转办/分办─► 承办中 ─办结─► 已办结 ─归档─► 已归档
 *                     ▲              │
 *                     └─── 退回 ─────┘
 * 发文是纯台账：登记 / 编辑 / 删除 / 归档，没有流转接口。
 */
export const docReceiveUrl = {
  list: '/land/doc/receive/list',
  queryById: '/land/doc/receive/queryById',
  add: '/land/doc/receive/add',
  edit: '/land/doc/receive/edit',
  delete: '/land/doc/receive/delete',
  deleteBatch: '/land/doc/receive/deleteBatch',
  generateNo: '/land/doc/receive/generateNo',
  stat: '/land/doc/receive/stat',
  flowList: '/land/doc/receive/flowList',
  transfer: '/land/doc/receive/transfer',
  reject: '/land/doc/receive/reject',
  finish: '/land/doc/receive/finish',
  archive: '/land/doc/receive/archive'
}

export const docSendUrl = {
  list: '/land/doc/send/list',
  queryById: '/land/doc/send/queryById',
  add: '/land/doc/send/add',
  edit: '/land/doc/send/edit',
  delete: '/land/doc/send/delete',
  deleteBatch: '/land/doc/send/deleteBatch',
  generateNo: '/land/doc/send/generateNo',
  stat: '/land/doc/send/stat',
  archive: '/land/doc/send/archive'
}

// ----------------------------------------------------------------------
// 收文
// ----------------------------------------------------------------------

/** 收文分页列表；onlyMine=true 只看我的待办 */
export function queryDocReceiveList (params) {
  return getAction(docReceiveUrl.list, params)
}

/** 收文详情（含附件与流转记录） */
export function queryDocReceiveById (id) {
  return getAction(docReceiveUrl.queryById, { id })
}

/** 收文登记 */
export function addDocReceive (data) {
  return postAction(docReceiveUrl.add, data)
}

/** 收文编辑（只改信息，不推进流转） */
export function editDocReceive (data) {
  return putAction(docReceiveUrl.edit, data)
}

/** 删除收文 */
export function deleteDocReceive (id) {
  return deleteAction(docReceiveUrl.delete, { id })
}

/** 批量删除收文 */
export function deleteDocReceiveBatch (ids) {
  return deleteAction(docReceiveUrl.deleteBatch, { ids: Array.isArray(ids) ? ids.join(',') : ids })
}

/** 收文登记号（仅预览） */
export function generateDocReceiveNo (year) {
  return getAction(docReceiveUrl.generateNo, year ? { year } : {})
}

/** 收文统计（总数/待承办/承办中/已退回/已办结/已归档/我的待办） */
export function queryDocReceiveStat () {
  return getAction(docReceiveUrl.stat, {})
}

/** 收文流转记录（时间轴） */
export function queryDocReceiveFlows (docId) {
  return getAction(docReceiveUrl.flowList, { docId })
}

/** 转办 / 分办：{docId, toUsername, opinion} */
export function transferDocReceive (data) {
  return postAction(docReceiveUrl.transfer, data)
}

/** 退回：{docId, opinion}（opinion 必填） */
export function rejectDocReceive (data) {
  return postAction(docReceiveUrl.reject, data)
}

/** 办结：{docId, opinion} */
export function finishDocReceive (data) {
  return postAction(docReceiveUrl.finish, data)
}

/** 收文归档：{docId, categoryId, archiveName?, archiveDate?, secretLevel?} */
export function archiveDocReceive (data) {
  return postAction(docReceiveUrl.archive, data)
}

// ----------------------------------------------------------------------
// 发文
// ----------------------------------------------------------------------

/** 发文分页列表 */
export function queryDocSendList (params) {
  return getAction(docSendUrl.list, params)
}

/** 发文详情（含附件） */
export function queryDocSendById (id) {
  return getAction(docSendUrl.queryById, { id })
}

/** 发文登记 */
export function addDocSend (data) {
  return postAction(docSendUrl.add, data)
}

/** 发文编辑 */
export function editDocSend (data) {
  return putAction(docSendUrl.edit, data)
}

/** 删除发文 */
export function deleteDocSend (id) {
  return deleteAction(docSendUrl.delete, { id })
}

/** 批量删除发文 */
export function deleteDocSendBatch (ids) {
  return deleteAction(docSendUrl.deleteBatch, { ids: Array.isArray(ids) ? ids.join(',') : ids })
}

/** 发文登记号（仅预览） */
export function generateDocSendNo (year) {
  return getAction(docSendUrl.generateNo, year ? { year } : {})
}

/** 发文统计（总数 / 已归档数） */
export function queryDocSendStat () {
  return getAction(docSendUrl.stat, {})
}

/** 发文归档：{docId, categoryId, archiveName?, archiveDate?, secretLevel?} */
export function archiveDocSend (data) {
  return postAction(docSendUrl.archive, data)
}

export default {
  docReceiveUrl,
  docSendUrl,
  queryDocReceiveList,
  queryDocReceiveById,
  addDocReceive,
  editDocReceive,
  deleteDocReceive,
  deleteDocReceiveBatch,
  generateDocReceiveNo,
  queryDocReceiveStat,
  queryDocReceiveFlows,
  transferDocReceive,
  rejectDocReceive,
  finishDocReceive,
  archiveDocReceive,
  queryDocSendList,
  queryDocSendById,
  addDocSend,
  editDocSend,
  deleteDocSend,
  deleteDocSendBatch,
  generateDocSendNo,
  queryDocSendStat,
  archiveDocSend
}
