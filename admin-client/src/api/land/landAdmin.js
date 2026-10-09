import Vue from 'vue'
import { getAction, postAction, putAction, deleteAction, uploadAction } from '@/api/manage'
import { ACCESS_TOKEN } from '@/store/mutation-types'

/**
 * 数据管理 · 经营性用地信息录入（逐条）接口（方案 2.3.1（三）第 1 项）
 *
 * 后端：org.jeecg.modules.land.data.controller.LandAdminController
 * 权限码：land:data:land（menu_type=2 的按钮权限，见 sql/data/08_data_permission_buttons.sql）
 *
 * ★ 与 `@/api/land/landData.js` 的分工：
 *   landData.js 是「基础只读」（下拉、详情、首页看板），被档案/收发文复用；
 *   本文件是「数据管理的写入面」—— 完整 34 字段落库、唯一性校验、变更留痕、软删。
 *   拆开是为了让「只读依赖」的模块不必感知写操作。
 *
 * 关键约定：
 *  1. ★ 编辑时必须回传 id；新增时不要带 id（后端会忽略，但带上容易让人误以为是「覆盖」）；
 *  2. 空字符串在提交前要转成 null（数值/日期列），否则后端 BigDecimal 解析会失败；
 *  3. 移除是**软删**，可在「数据更新与移除」里恢复；
 *  4. 「变更履历」返回的是字段级明细：[{field,label,before,after}]。
 */

export const landAdminUrl = {
  list: '/land/data/landAdmin/list',
  detail: '/land/data/landAdmin/detail',
  history: '/land/data/landAdmin/history',
  codeOptions: '/land/data/landAdmin/codeOptions',
  checkCrzdbh: '/land/data/landAdmin/checkCrzdbh',
  add: '/land/data/landAdmin/add',
  edit: '/land/data/landAdmin/edit',
  delete: '/land/data/landAdmin/delete',
  deleteBatch: '/land/data/landAdmin/deleteBatch'
}

/* ==========================================================================
 * 一、查询
 * ========================================================================== */

/**
 * 分页列表。
 *
 * @param {object} params crzdbh,dkmc,xzqh,xmfl,ghydxz,srr,crsjBegin,crsjEnd,
 *                        ptsfqq,hasFacility,facilityKeyword,orderBy,asc,pageNo,pageSize
 */
export function queryLandPage (params) {
  return getAction(landAdminUrl.list, compact(params))
}

/** 详情 */
export function queryLandDetail (id) {
  return getAction(landAdminUrl.detail, { id })
}

/** 变更履历（字段级：改了哪个字段、改前改后、谁改的） */
export function queryLandHistory (id, limit) {
  return getAction(landAdminUrl.history, limit ? { id, limit } : { id })
}

/**
 * 宗地编号下拉（跨模块选择共用）。
 *
 * ★ 用 `@/api/land/landData.js` 的 `queryLandOptions` 也可以，
 *   但那个接口只返回 id/crzdbh/dkmc/xzqh/xmfl；本接口额外返回 label
 *   已经拼好的「编号（名称）」形式，表单里直接绑 label 更省事。
 */
export function queryLandCodeOptions (keyword, limit) {
  return getAction(landAdminUrl.codeOptions, compact({ keyword, limit }))
}

/**
 * 出让宗地编号唯一性校验（表单 onBlur 实时校验）。
 *
 * @returns result: {available, message, occupiedBy, occupiedName}
 */
export function checkCrzdbh (crzdbh, excludeId) {
  return getAction(landAdminUrl.checkCrzdbh, compact({ crzdbh, excludeId }))
}

/* ==========================================================================
 * 二、写入
 * ========================================================================== */

/** 新增（出让宗地编号必填且不可重复） */
export function addLand (data) {
  return postAction(landAdminUrl.add, normalize(data))
}

/** 编辑（会记字段级变更履历） */
export function editLand (data) {
  return putAction(landAdminUrl.edit, normalize(data))
}

/** 移除（软删，可恢复） */
export function deleteLand (id, reason) {
  return deleteAction(landAdminUrl.delete, compact({ id, reason }))
}

/** 批量移除（逐条软删并逐条留痕，返回成功/失败明细） */
export function deleteLandBatch (ids, reason) {
  const value = Array.isArray(ids) ? ids.join(',') : ids
  return deleteAction(landAdminUrl.deleteBatch, compact({ ids: value, reason }))
}

/* ==========================================================================
 * 三、提交前清洗
 *
 * ★ 为什么必须转 null 而不是传空串：
 *   后端的数值列是 BigDecimal、日期列是 Date。空串 `""` 会被 Spring 当作
 *   「有值但无法转换」而抛 `MethodArgumentTypeMismatchException` /
 *   Jackson 反序列化异常，界面上看到的是一句看不懂的 400 错误。
 *   这里统一把空串转成 null，语义也更准确（「没填」而不是「填了个空」）。
 * ========================================================================== */

/** 数值列（BigDecimal） */
const NUMBER_FIELDS = ['crj', 'kjsydmj', 'zydmj', 'jsmj', 'nrcbdptf', 'wcd']
/** 日期列 */
const DATE_FIELDS = ['crsj', 'htydjfsj', 'lpjfsj']
/** 文本列（空串转 null） */
const TEXT_FIELDS = [
  'crzdbh', 'dkmc', 'xzqh', 'ghydxz', 'ptsfqq', 'ptjsnr', 'srr', 'lpmc',
  'tdzldw', 'tdzljhxdwjh', 'tdzljh', 'dz', 'xz', 'nz', 'bz', 'ptqkh', 'ptcbh',
  'crzdtxsj', 'zlqsnrsm', 'lrdw', 'lrr', 'lxdh', 'beizhu', 'xzqh2'
]

/** 提交前把空串转 null、数值转 Number */
export function normalize (data) {
  if (!data) {
    return data
  }
  const payload = Object.assign({}, data)
  TEXT_FIELDS.forEach(field => {
    if (payload[field] === '') {
      payload[field] = null
    }
  })
  DATE_FIELDS.forEach(field => {
    if (payload[field] === '') {
      payload[field] = null
    }
  })
  NUMBER_FIELDS.forEach(field => {
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

/** 去掉空查询条件（空串/undefined 不发给后端） */
export function compact (params) {
  const result = {}
  Object.keys(params || {}).forEach(key => {
    const value = params[key]
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value
    }
  })
  return result
}

/** 供错误回执等场景拼下载地址（token 放 query，后端 JwtFilter 支持） */
export function buildDownloadUrl (path, params) {
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

/** 供别的 data 模块复用（避免各写一份） */
export { uploadAction }

export default {
  landAdminUrl,
  queryLandPage,
  queryLandDetail,
  queryLandHistory,
  queryLandCodeOptions,
  checkCrzdbh,
  addLand,
  editLand,
  deleteLand,
  deleteLandBatch,
  normalize,
  compact,
  buildDownloadUrl
}
