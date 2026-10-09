import Vue from 'vue'
import { getAction, uploadAction } from '@/api/manage'
import { ACCESS_TOKEN } from '@/store/mutation-types'

/**
 * 数据管理 · 经营性用地批量导入接口（方案 2.3.1（三）第 3 项）
 *
 * 后端：org.jeecg.modules.land.data.imports.controller.LandImportController
 * 权限码：land:data:import（menu_type=2 的按钮权限，见 sql/land/04_land_data_permission_buttons.sql）
 *
 * 关键约定：
 *  1. ★ 模板是「两行表头」：第 1 行英文字段名（程序按它认列）、第 2 行中文说明，数据从第 3 行起；
 *     列名与旧库 xj_kjkfb_commercial_land 完全一致，所以中心手里的旧 Excel 可以直接传进来。
 *  2. ★ 必须先「预览校验」再「确认入库」：预览绝不写库，
 *     有错误时后端默认整批不入库（一行都不写），除非显式勾选 skipErrorRows。
 *  3. 重复宗地编号有三种策略：reject（默认，重复即报错）/ skip（跳过重复只导新增）/
 *     update（覆盖更新，空单元格不会清空库内原值）。
 *  4. 错误回执里的前 N 列与模板完全一致（用户改完可直接重传），
 *     末尾追加 __excel行号 / __错误字段 / __错误原因 三列。
 */

/* ==========================================================================
 * 一、URL 常量
 * ========================================================================== */

export const landImportUrl = {
  template: '/land/data/import/template',
  fields: '/land/data/import/fields',
  strategies: '/land/data/import/strategies',
  preview: '/land/data/import/preview',
  confirm: '/land/data/import/confirm',
  errorReport: '/land/data/import/errorReport',
  logs: '/land/data/import/logs'
}

/* ==========================================================================
 * 二、下载 URL 拼装
 *
 * 浏览器原生下载（window.open）带不了自定义请求头，而 jeecg 的 JwtFilter
 * 同时支持从 query string 的 token 参数取令牌，所以统一拼上 token。
 * （与 api/land/archive.js、api/land/ledger.js 的 buildDownloadUrl 完全一致）
 * ========================================================================== */

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

/* ==========================================================================
 * 三、接口封装
 * ========================================================================== */

/** 下载导入模板（浏览器直接下载，含「填表说明」sheet） */
export function downloadImportTemplate () {
  window.open(buildDownloadUrl(landImportUrl.template, {}), '_blank')
}

/**
 * 字段字典（列名 / 中文名 / 类型 / 是否必填 / 取值 / 示例）。
 *
 * ★ 页面不自己硬编码这 30 多列：后端加了列、前端还写老的，用户按页面提示
 *   填的表会被后端拒绝。所以列说明一律以这个接口为准。
 *   接口不可用时退化为 {@link LAND_IMPORT_FIELDS_FALLBACK}，页面仍能渲染。
 */
export function queryImportFields () {
  return getAction(landImportUrl.fields, {})
}

/** 重复宗地编号的处理策略选项（reject / skip / update） */
export function queryDuplicateStrategies () {
  return getAction(landImportUrl.strategies, {})
}

/**
 * 预览校验（★ 不写库）。
 *
 * @param {File} file .xlsx / .xls 文件
 * @param {string} duplicateStrategy reject / skip / update
 * @returns {Promise} result 里含 totalRows / validRows / willInsertRows / willUpdateRows /
 *                    skippedDuplicates / errors / warnings / notices / aborted
 */
export function previewLandImport (file, duplicateStrategy) {
  const formData = new FormData()
  formData.append('file', file)
  if (duplicateStrategy) {
    formData.append('duplicateStrategy', duplicateStrategy)
  }
  return uploadAction(landImportUrl.preview, formData)
}

/**
 * 确认入库。
 *
 * @param {File} file
 * @param {string} duplicateStrategy
 * @param {boolean} skipErrorRows true = 跳过有错误的行只导入正确的行；
 *                                false（默认）= 有错就整批不入库
 */
export function confirmLandImport (file, duplicateStrategy, skipErrorRows) {
  const formData = new FormData()
  formData.append('file', file)
  if (duplicateStrategy) {
    formData.append('duplicateStrategy', duplicateStrategy)
  }
  if (skipErrorRows) {
    formData.append('skipErrorRows', 'true')
  }
  return uploadAction(landImportUrl.confirm, formData)
}

/**
 * 下载错误回执（浏览器直接下载）。
 *
 * ★ 只有 POST 才能带上文件内容，而 window.open 发不了 POST，
 *   所以这里用「fetch + blob + file-saver 式锚点下载」的方式：
 *   把文件原样 POST 给后端，拿到 xlsx 字节流再触发保存。
 *
 * @param {File} file 当次上传的原始文件
 * @param {string} duplicateStrategy 与预览时相同的策略（保证回执错误清单与预览一致）
 */
export function downloadErrorReport (file, duplicateStrategy) {
  const formData = new FormData()
  formData.append('file', file)
  if (duplicateStrategy) {
    formData.append('duplicateStrategy', duplicateStrategy)
  }
  const base = window._CONFIG['domianURL'] || ''
  const token = Vue.ls.get(ACCESS_TOKEN)
  return fetch(`${base}${landImportUrl.errorReport}`, {
    method: 'POST',
    headers: token ? { 'X-Access-Token': token } : {},
    body: formData
  }).then(response => {
    const disposition = response.headers.get('Content-Disposition') || ''
    // 后端出错时返回的是 JSON（application/json），这时不能当 xlsx 存
    const contentType = response.headers.get('Content-Type') || ''
    if (contentType.indexOf('application/json') >= 0) {
      return response.json().then(body => {
        throw new Error((body && body.message) || '生成错误回执失败')
      })
    }
    return response.blob().then(blob => {
      saveBlob(blob, parseFileName(disposition) || '经营性用地导入错误回执.xlsx')
    })
  })
}

/** 最近导入记录 */
export function queryImportLogs (limit) {
  return getAction(landImportUrl.logs, limit ? { limit } : {})
}

/* ==========================================================================
 * 四、下载小工具
 * ========================================================================== */

/** 从 Content-Disposition 里解出文件名（后端发的是 RFC 5987 的 filename*） */
function parseFileName (disposition) {
  if (!disposition) {
    return null
  }
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star && star[1]) {
    try {
      return decodeURIComponent(star[1])
    } catch (e) {
      return star[1]
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain && plain[1] ? plain[1] : null
}

/** 触发浏览器保存一个 blob */
function saveBlob (blob, fileName) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.style.display = 'none'
  link.href = url
  link.setAttribute('download', fileName)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/* ==========================================================================
 * 五、模块内共享常量
 * ========================================================================== */

/** 重复策略的本地兜底（接口不可用时用） */
export const DUPLICATE_STRATEGIES_FALLBACK = [
  { code: 'reject', label: '重复即报错（不写库）' },
  { code: 'skip', label: '跳过重复行（只导新增）' },
  { code: 'update', label: '覆盖更新已有宗地（空单元格不改动）' }
]

/**
 * 每行错误对应的一条回执（错误清单表格用）。
 *
 * @param {object} result 预览/入库返回的结果
 * @returns {Array} 抹平后的错误行，附带稳定 key
 */
export function flattenErrors (result) {
  if (!result || !result.errors) {
    return []
  }
  return result.errors.map((item, index) => Object.assign({ key: index }, item))
}

/**
 * 错误集合 → 按 Excel 行号聚合。
 *
 * ★ 为什么要聚合：同一行可能同时踩两个问题（例如「手机号错」+「编号重复」），
 *   平铺展示会让用户以为有 10 行要改，其实只有 6 行。
 *   抽屉里按行聚合，列表里平铺，两种视角都要有。
 */
export function groupErrorsByRow (result) {
  const map = {}
  flattenErrors(result).forEach(item => {
    const row = item.rowNum
    if (!map[row]) {
      map[row] = {
        key: `row-${row}`,
        rowNum: row,
        crzdbh: item.crzdbh,
        columns: [],
        message: item.message
      }
    }
    if (item.columnLabel && map[row].columns.indexOf(item.columnLabel) < 0) {
      map[row].columns.push(item.columnLabel)
    }
    if (map[row].crzdbh === null || map[row].crzdbh === undefined) {
      map[row].crzdbh = item.crzdbh
    }
    if (map[row].message !== item.message) {
      map[row].message += `；${item.message}`
    }
  })
  return Object.keys(map)
    .map(key => map[key])
    .sort((a, b) => a.rowNum - b.rowNum)
}

/** 入库结果的一句话摘要（成功提示用） */
export function importSummary (result) {
  if (!result) {
    return ''
  }
  const parts = []
  if (result.insertedRows) {
    parts.push(`新增 ${result.insertedRows} 条`)
  }
  if (result.updatedRows) {
    parts.push(`覆盖更新 ${result.updatedRows} 条`)
  }
  if (result.skippedDuplicates) {
    parts.push(`跳过重复 ${result.skippedDuplicates} 条`)
  }
  if (!parts.length) {
    return '本次没有写入任何数据'
  }
  return `导入完成：${parts.join('，')}`
}

/**
 * ★ 字段字典的本地兜底（与后端 LandImportField 的顺序、列名一一对应）。
 *
 * 页面挂载时会调 {@link queryImportFields} 用后端返回的定义覆盖它，
 * 因此「后端加一列」时页面说明会自动跟上；这份兜底只保证接口不可用时页面仍能渲染。
 * 字段：column / label / type / required / options / optionText / maxLength / sample
 */
export const LAND_IMPORT_FIELDS_FALLBACK = [
  { column: 'crzdbh', label: '出让宗地编号', type: 'text', required: true, optionText: '自由文本', sample: '津西青(挂)2024-01号' },
  { column: 'xmfl', label: '项目分类', type: 'enum', required: true, options: ['市级项目', '区级项目'], optionText: '市级项目 / 区级项目' },
  { column: 'dkmc', label: '地块名称', type: 'text', required: false, optionText: '自由文本' },
  { column: 'xzqh', label: '行政区划', type: 'enum', required: false, optionText: '和平区 / 河东区 / …（16 区）' },
  { column: 'ghydxz', label: '规划用地性质', type: 'text', required: false, optionText: '自由文本' },
  { column: 'crj', label: '出让金（亿元）', type: 'number', required: false, optionText: '数值' },
  { column: 'crsj', label: '出让时间', type: 'date', required: false, optionText: '日期' },
  { column: 'kjsydmj', label: '可建设用地面积(平方米)', type: 'number', required: false, optionText: '数值' },
  { column: 'zydmj', label: '总用地面积（平方米）', type: 'number', required: false, optionText: '数值' },
  { column: 'jsmj', label: '建设面积（平方米）', type: 'number', required: false, optionText: '数值' },
  { column: 'nrcbdptf', label: '纳入成本的配套费(万元)', type: 'number', required: false, optionText: '数值' },
  { column: 'srr', label: '受让人', type: 'text', required: false, optionText: '自由文本' },
  { column: 'htydjfsj', label: '合同约定交付时间', type: 'date', required: false, optionText: '日期' },
  { column: 'lpmc', label: '楼盘名称', type: 'text', required: false, optionText: '自由文本' },
  { column: 'lpjfsj', label: '楼盘交付时间（或计划交付时间）', type: 'date', required: false, optionText: '日期' },
  { column: 'dz', label: '东至', type: 'text', required: false, optionText: '自由文本' },
  { column: 'xz', label: '西至', type: 'text', required: false, optionText: '自由文本' },
  { column: 'nz', label: '南至', type: 'text', required: false, optionText: '自由文本' },
  { column: 'bz', label: '北至', type: 'text', required: false, optionText: '自由文本' },
  { column: 'tdzldw', label: '土地整理单位', type: 'text', required: false, optionText: '自由文本' },
  { column: 'tdzljhxdwjh', label: '土地整理计划下达文件号', type: 'text', required: false, optionText: '自由文本' },
  { column: 'tdzljh', label: '土地整理计划', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'ptsfqq', label: '配套是否齐全', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'ptqkh', label: '配套情况函', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'ptcbh', label: '配套筹备函', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'crzdtxsj', label: '出让宗地图形数据（shp）', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'ptjsnr', label: '配套建设内容', type: 'text', required: false, optionText: '自由文本' },
  { column: 'lrdw', label: '录入单位', type: 'text', required: true, optionText: '自由文本' },
  { column: 'lrr', label: '录入人', type: 'text', required: true, optionText: '自由文本' },
  { column: 'lxdh', label: '联系电话', type: 'text', required: true, optionText: '11 位手机号' },
  { column: 'zlqsnrsm', label: '资料缺失内容及说明', type: 'text', required: false, optionText: '自由文本' },
  { column: 'beizhu', label: '备注', type: 'text', required: false, optionText: '自由文本' }
]

/** 字段类型 → 中文（列说明表用） */
export function fieldTypeText (type) {
  switch (type) {
    case 'number':
      return '数值'
    case 'date':
      return '日期'
    case 'yes_no':
      return '是/否'
    case 'enum':
      return '枚举'
    default:
      return '文本'
  }
}

/** 字段类型 → a-tag 颜色 */
export function fieldTypeColor (type) {
  switch (type) {
    case 'number':
      return 'blue'
    case 'date':
      return 'purple'
    case 'yes_no':
      return 'green'
    case 'enum':
      return 'orange'
    default:
      return undefined
  }
}

export default {
  landImportUrl,
  buildDownloadUrl,
  downloadImportTemplate,
  queryImportFields,
  queryDuplicateStrategies,
  previewLandImport,
  confirmLandImport,
  downloadErrorReport,
  queryImportLogs,
  flattenErrors,
  groupErrorsByRow,
  importSummary,
  fieldTypeText,
  fieldTypeColor,
  DUPLICATE_STRATEGIES_FALLBACK,
  LAND_IMPORT_FIELDS_FALLBACK
}
