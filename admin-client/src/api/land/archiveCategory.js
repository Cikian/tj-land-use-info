import { getAction, postAction, httpAction, deleteAction } from '@/api/manage'

/**
 * 档案管理 · 档案类别管理 接口（方案 2.3.2 第 1 项）
 *
 * 后端：org.jeecg.modules.land.archive.controller.ArchiveCategoryController
 * 权限码：archive:category:manage
 * 约定：GET 用 query string；写操作统一用 JSON body（jeecg Result<T> 返回体）
 */
export const archiveCategoryUrl = {
  tree: '/land/archive/category/tree',
  queryById: '/land/archive/category/queryById',
  checkName: '/land/archive/category/checkName',
  add: '/land/archive/category/add',
  edit: '/land/archive/category/edit',
  delete: '/land/archive/category/delete',
  sort: '/land/archive/category/sort',
  status: '/land/archive/category/status'
}

/**
 * 类别树查询
 * @param {{keyword?: string, status?: number, excludeId?: string}} params
 */
export function queryArchiveCategoryTree (params) {
  return getAction(archiveCategoryUrl.tree, params)
}

/**
 * 类别详情（附带父类别名称、全路径名称、档案数量）
 * @param {string} id
 */
export function queryArchiveCategoryById (id) {
  return getAction(archiveCategoryUrl.queryById, { id })
}

/**
 * 同级类别名称重复校验
 * @param {{parentId?: string, name: string, id?: string}} params
 */
export function checkArchiveCategoryName (params) {
  return getAction(archiveCategoryUrl.checkName, params)
}

/** 新建类别 */
export function addArchiveCategory (data) {
  return postAction(archiveCategoryUrl.add, data)
}

/** 编辑类别（更名 / 编码别名说明 / 排序号 / 启停 / 移动到其它上级） */
export function editArchiveCategory (data) {
  return httpAction(archiveCategoryUrl.edit, data, 'put')
}

/** 移除类别（后端含「有子类别 / 有档案」前置校验） */
export function deleteArchiveCategory (id) {
  return deleteAction(archiveCategoryUrl.delete, { id })
}

/**
 * 同级排序 / 拖拽移动后的顺序落库
 * @param {{parentId?: string, orderedIds: string[]}} data
 */
export function sortArchiveCategory (data) {
  return postAction(archiveCategoryUrl.sort, data)
}

/**
 * 启用 / 停用。停用会级联停用整棵子树。
 * @param {string} id
 * @param {number} status 1启用 0停用
 */
export function changeArchiveCategoryStatus (id, status) {
  return postAction(archiveCategoryUrl.status, { id, status })
}

export default {
  archiveCategoryUrl,
  queryArchiveCategoryTree,
  queryArchiveCategoryById,
  checkArchiveCategoryName,
  addArchiveCategory,
  editArchiveCategory,
  deleteArchiveCategory,
  sortArchiveCategory,
  changeArchiveCategoryStatus
}
