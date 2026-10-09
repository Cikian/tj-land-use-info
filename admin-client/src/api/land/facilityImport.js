import Vue from 'vue'
import { getAction, uploadAction } from '@/api/manage'
import { ACCESS_TOKEN } from '@/store/mutation-types'
import { compact, buildDownloadUrl } from '@/api/land/landAdmin'

/**
 * 数据管理 · 配套信息批量导入（宗地存在性校验 + 孤儿清单）接口
 * （方案 2.3.1（三）第 4 项）
 *
 * 后端：org.jeecg.modules.land.data.imports.facility.controller.FacilityImportController
 * 权限码：land:data:facilityImport
 *
 * ★★★ 与「经营性用地批量导入」最重要的差异：**孤儿清单**
 *
 *   配套项目必须挂在宗地（`crzdbh`）下，但中心的 Excel 常常来自别的系统导出，
 *   里面的宗地编号可能：
 *     · 系统里还没录入（新出让地块，配套先做）；
 *     · 编号书写不一致（设计文档实测：`津南长（挂）G2024-01` 缺「号」字、
 *       `津西青楚(挂)` 用半角括号 —— 与库里的全角括号不匹配）。
 *
 *   旧系统对前者**没有任何校验**，直接插进去，于是产生了设计文档实测的
 *   **60 行「孤儿配套」**（配套表里有、宗地表里查不到）——
 *   这些配套在按宗地关联的档案/收发文/台账里永远挂不上，也永远不会出现在
 *   宗地维度的统计里，等于数据静默丢失。
 *
 *   本模块的处理：
 *     ① 默认 `allowOrphan=false`：宗地不存在 → 硬错误，整批不入库，
 *        并给出**孤儿清单**让用户看到完整清单（哪几行的宗地没录入）；
 *     ② 用户可以勾选「允许挂到未登记宗地（记入孤儿清单）」：
 *        孤儿行**照常入库**（业务上确实存在「配套先于宗地登记」的情况），
 *        但会在回执与导入记录里留下孤儿清单，供事后催录宗地；
 *     ③ 编号「疑似写错」时给近失配提示（指出库里最可能的那个编号），
 *        但**绝不自动改写** —— 自动纠正会把配套静默挂到别的宗地上，
 *        比报错危险得多。
 */

export const facilityImportUrl = {
  template: '/land/data/facilityImport/template',
  fields: '/land/data/facilityImport/fields',
  strategies: '/land/data/facilityImport/strategies',
  preview: '/land/data/facilityImport/preview',
  confirm: '/land/data/facilityImport/confirm',
  errorReport: '/land/data/facilityImport/errorReport',
  logs: '/land/data/facilityImport/logs'
}

/* ==========================================================================
 * 一、模板与字典
 * ========================================================================== */

/** 下载导入模板（浏览器直接下载，含「填表说明」sheet） */
export function downloadImportTemplate () {
  window.open(buildDownloadUrl(facilityImportUrl.template, {}), '_blank')
}

/** 字段字典（列名/中文名/类型/是否必填/取值/示例）—— 页面不硬编码列定义 */
export function queryImportFields () {
  return getAction(facilityImportUrl.fields, {})
}

/** 重复处理策略选项（键是 (crzdbh, ptxmmc)，与宗地导入的 crzdbh 单键不同） */
export function queryDuplicateStrategies () {
  return getAction(facilityImportUrl.strategies, {})
}

/* ==========================================================================
 * 二、预览与入库
 * ========================================================================== */

/**
 * 预览校验（★ 不写库）。
 *
 * @param {File} file .xlsx / .xls
 * @param {string} duplicateStrategy reject / skip / update
 * @param {boolean} allowOrphan 是否允许挂到未登记宗地（true 时孤儿行记入清单并照常入库）
 * @returns result 里除常规计数外，还有 orphans（孤儿清单）与 orphanRows（孤儿行数）
 */
export function previewFacilityImport (file, duplicateStrategy, allowOrphan) {
  const formData = new FormData()
  formData.append('file', file)
  if (duplicateStrategy) {
    formData.append('duplicateStrategy', duplicateStrategy)
  }
  if (allowOrphan) {
    formData.append('allowOrphan', 'true')
  }
  return uploadAction(facilityImportUrl.preview, formData)
}

/**
 * 确认入库。
 *
 * @param {File} file
 * @param {string} duplicateStrategy
 * @param {boolean} skipErrorRows 跳过有错误的行，只导入正确的行
 * @param {boolean} allowOrphan 允许挂到未登记宗地
 */
export function confirmFacilityImport (file, duplicateStrategy, skipErrorRows, allowOrphan) {
  const formData = new FormData()
  formData.append('file', file)
  if (duplicateStrategy) {
    formData.append('duplicateStrategy', duplicateStrategy)
  }
  if (skipErrorRows) {
    formData.append('skipErrorRows', 'true')
  }
  if (allowOrphan) {
    formData.append('allowOrphan', 'true')
  }
  return uploadAction(facilityImportUrl.confirm, formData)
}

/**
 * 下载错误回执（POST + blob，因为 window.open 发不了 POST）。
 *
 * ★ 有孤儿清单时后端会**额外生成一个「孤儿清单」sheet** ——
 *   因为 allowOrphan=true 时孤儿不是错误，按错误回执导不出来。
 */
export function downloadErrorReport (file, duplicateStrategy, allowOrphan) {
  const formData = new FormData()
  formData.append('file', file)
  if (duplicateStrategy) {
    formData.append('duplicateStrategy', duplicateStrategy)
  }
  if (allowOrphan) {
    formData.append('allowOrphan', 'true')
  }
  const base = window._CONFIG['domianURL'] || ''
  const token = Vue.ls.get(ACCESS_TOKEN)
  return fetch(`${base}${facilityImportUrl.errorReport}`, {
    method: 'POST',
    headers: token ? { 'X-Access-Token': token } : {},
    body: formData
  }).then(response => {
    const contentType = response.headers.get('Content-Type') || ''
    if (contentType.indexOf('application/json') >= 0) {
      return response.json().then(body => {
        throw new Error((body && body.message) || '生成错误回执失败')
      })
    }
    return response.blob().then(blob => {
      saveBlob(blob, parseFileName(response.headers.get('Content-Disposition')) ||
        '配套信息导入错误回执.xlsx')
    })
  })
}

/** 最近导入记录 */
export function queryImportLogs (limit) {
  return getAction(facilityImportUrl.logs, compact({ limit }))
}

/* ==========================================================================
 * 三、结果解析小工具（与 landImport.js 的同类方法保持一致的用法）
 * ========================================================================== */

/** 把 errors 抹平成表格行（带稳定 key） */
export function flattenErrors (result) {
  if (!result || !result.errors) {
    return []
  }
  return result.errors.map((item, index) => Object.assign({ key: index }, item))
}

/** 把 orphans 抹平成表格行（带稳定 key） */
export function flattenOrphans (result) {
  if (!result || !result.orphans) {
    return []
  }
  return result.orphans.map((item, index) => Object.assign({ key: index }, item))
}

/** 按 Excel 行号聚合错误（同一行多个问题合成一条，避免看起来要改很多行） */
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
    if (map[row].message !== item.message) {
      map[row].message += `；${item.message}`
    }
  })
  return Object.keys(map).map(key => map[key]).sort((a, b) => a.rowNum - b.rowNum)
}

/** 入库结果摘要 */
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
  if (result.orphanRows) {
    parts.push(`孤儿 ${result.orphanRows} 条`)
  }
  return parts.length ? `导入完成：${parts.join('，')}` : '本次没有写入任何数据'
}

/* ==========================================================================
 * 四、下载小工具
 * ========================================================================== */

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

/** 重复策略本地兜底（接口不可用时用） */
export const DUPLICATE_STRATEGIES_FALLBACK = [
  { code: 'reject', label: '重复即报错（不写库）' },
  { code: 'skip', label: '跳过重复行（只导新增）' },
  { code: 'update', label: '覆盖更新已有配套（空单元格不改动）' }
]

export default {
  facilityImportUrl,
  downloadImportTemplate,
  queryImportFields,
  queryDuplicateStrategies,
  previewFacilityImport,
  confirmFacilityImport,
  downloadErrorReport,
  queryImportLogs,
  flattenErrors,
  flattenOrphans,
  groupErrorsByRow,
  importSummary,
  DUPLICATE_STRATEGIES_FALLBACK
}
