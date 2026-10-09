import {
  javaGetAction,
  javaPostAction,
  javaAccessToken,
  buildJavaDownloadUrl
} from '@/api/manageJava'

/**
 * 数据管理 · 配套信息批量导入接口（方案 2.3.1（三）「配套信息批量导入管理」）
 * ===============================================================
 * 后端：org.jeecg.modules.land.data.imports.facility.controller.FacilityImportController
 * 权限码：land:data:facilityImport
 *
 * 【为什么用 manageJava 而不是 @/api/manage】
 * 接口在 Java 业务后端（VUE_DATA_JAVA_URL），不在中台。详见 manageJava.js 头注释。
 *
 * 【与 admin-client 的 landImport.js 的关系】
 * 结构照抄那一份（URL 常量对象 + 一个函数一段说明 + buildJavaDownloadUrl 风格的
 * 下载拼装 + 本地兜底常量 + export default），只把请求层换成 manageJava、
 * 并按配套导入特有的三个控制项（重复策略 / 允许孤儿 / 跳过错误行）扩展入参。
 *
 * 关键约定：
 *  1. ★ 必须先「预览校验」再「确认入库」：预览绝不写库，有错误时后端默认
 *     整批不入库（一行都不写），除非显式勾选 skipErrorRows；
 *  2. ★ 判重键是 **(宗地编号, 配套项目名称) 两列一起**，不是宗地导入的「编号即唯一」。
 *     同一块地上可以有很多配套，只要名称不重复；
 *  3. ★ 重复处理策略三选一：reject（默认，重复即报错）/ skip（跳过重复只导新增）/
 *     update（覆盖更新，空单元格不会清空库内原值）；
 *  4. ★ `allowOrphan` 在 **preview 与 confirm 上都要传**：它不是「入库选项」，
 *     而是**校验口径本身**（孤儿算不算错误）。只在 confirm 上传会让用户看到
 *     「预览说 12 行有错」，勾上开关后却导进去了 12 条他从未在预览里确认过的数据；
 *  5. ★ 孤儿 ≠ 错误：孤儿的定义是「这条配套的出让宗地编号在 t_land 里查不到」。
 *     它可能是勾了「允许挂到未登记宗地」的有意为之，也可能是编号写错了。
 *     无论哪种，`orphans` / `orphanRows` 都会有值，界面必须单独渲染（不能混进错误清单）。
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
 * 一、模板与字典（3 个）
 * ========================================================================== */

/**
 * 下载导入模板（两行表头 + 「填表说明」sheet，含孤儿清单的说明）。
 *
 * 走 buildJavaDownloadUrl：原生下载带不了自定义请求头，而 jeecg 的 JwtFilter
 * 支持从 query string 的 `token` 参数取令牌，所以统一把 token 拼在 URL 上
 * （与 api/land/archive.js、api/land/ledger.js 的做法一致）。
 */
export function downloadFacilityTemplate () {
  window.open(buildJavaDownloadUrl(facilityImportUrl.template, {}), '_blank')
}

/**
 * 字段字典（列名 / 中文名 / 类型 / 是否必填 / 取值 / 示例）。
 *
 * ★ 页面不要自己硬编码这几列：后端加了列、前端还写老的，用户按页面提示
 *   填的表会被后端拒绝。接口不可用时退化为 FACILITY_IMPORT_FIELDS_FALLBACK。
 */
export function queryFacilityImportFields () {
  return javaGetAction(facilityImportUrl.fields, {})
}

/**
 * 重复 (宗地编号, 配套项目名称) 的处理策略选项。
 * @returns {Promise} result 为 [{ code, label }]
 */
export function queryFacilityDuplicateStrategies () {
  return javaGetAction(facilityImportUrl.strategies, {})
}

/* ==========================================================================
 * 二、预览 / 入库 / 回执（3 个）
 * ========================================================================== */

/**
 * 预览校验（★ 不写库，含孤儿清单）。
 *
 * 【为什么可以走 javaPostAction 发 multipart】
 * manageJava 的 javaPostAction 只设 X-Access-Token / X-Sign / X-TIMESTAMP 三个头，
 * **不设 Content-Type**，所以传 FormData 时 axios 会自己带上
 * `multipart/form-data; boundary=...`；request.js 的请求拦截器只在 FormData 含
 * `access_token` 字段时才动它（本请求没有）。
 * 这与 api/land/ledger.js 的 previewLedgerImport 是同一做法。
 *
 * @param {File} file .xlsx 文件
 * @param {string} [duplicateStrategy] reject / skip / update，空则后端用默认 reject
 * @param {boolean} [allowOrphan] true = 宗地查不到的行记入孤儿清单并仍按「可导入」预览
 * @returns {Promise} result 为 FacilityImportResultVO：
 *   { totalRows, validRows, insertedRows, updatedRows, skippedDuplicates,
 *     willUpdateRows, willInsertRows, allowOrphan, orphanRows, matchedLandRows,
 *     orphans: [{ rowNum, crzdbh, ptxmmc, reason }],
 *     duplicateStrategy, duplicateStrategyLabel, aborted,
 *     errors: [{ rowNum, crzdbh, column, columnLabel, message }],
 *     warnings: [], notices: [] }
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
  return javaPostAction(facilityImportUrl.preview, formData)
}

/**
 * 确认入库。
 *
 * @param {File} file 与预览同一个 File（后端按同一解析路径，保证「预览看到什么就写什么」）
 * @param {object} [options]
 * @param {string} [options.duplicateStrategy] 必须与预览时一致
 * @param {boolean} [options.skipErrorRows] true = 跳过有错误的行只导入正确的行；
 *                                          false（默认）= 有错就整批不入库
 * @param {boolean} [options.allowOrphan] true = 孤儿行照常入库并记入清单
 */
export function confirmFacilityImport (file, options) {
  const config = options || {}
  const formData = new FormData()
  formData.append('file', file)
  if (config.duplicateStrategy) {
    formData.append('duplicateStrategy', config.duplicateStrategy)
  }
  if (config.skipErrorRows) {
    formData.append('skipErrorRows', 'true')
  }
  if (config.allowOrphan) {
    formData.append('allowOrphan', 'true')
  }
  return javaPostAction(facilityImportUrl.confirm, formData)
}

/**
 * 下载错误回执（Excel 字节流）。
 *
 * ★ 只有 POST 才能带上文件内容，而 window.open 发不了 POST，所以这里用
 *   `fetch + blob + 锚点下载`：把**当次上传的原始文件**原样 POST 给后端，
 *   重新解析一遍并返回带诊断列的 xlsx。
 *
 * ★ 不能用 javaPostAction：axios 会把 xlsx 当 JSON 解析（responseType 默认 json），
 *   二进制流会被破坏。这里必须走原生 fetch 拿 blob（与 admin-client 的
 *   landImport.js downloadErrorReport 是同一做法，只是令牌取 jeecg 的）。
 *
 * ★ 后端出错时返回的是 JSON，必须按 Content-Type 区分，否则用户会下载到一个
 *   坏掉的 xlsx（打开报「文件已损坏」），而看不到真正的错误原因。
 *
 * @param {File} file 当次上传的原始文件
 * @param {object} [options]
 * @param {string} [options.duplicateStrategy] 与预览时相同，保证回执错误清单一致
 * @param {boolean} [options.allowOrphan] 与预览时相同
 */
export function downloadFacilityErrorReport (file, options) {
  const config = options || {}
  const formData = new FormData()
  formData.append('file', file)
  if (config.duplicateStrategy) {
    formData.append('duplicateStrategy', config.duplicateStrategy)
  }
  if (config.allowOrphan) {
    formData.append('allowOrphan', 'true')
  }
  const headers = {}
  const token = javaAccessToken()
  if (token) {
    headers['X-Access-Token'] = token
  }
  return window
    .fetch(buildJavaDownloadUrl(facilityImportUrl.errorReport, {}), {
      method: 'POST',
      headers,
      body: formData
    })
    .then((response) => {
      const contentType = response.headers.get('Content-Type') || ''
      if (contentType.indexOf('application/json') >= 0) {
        return response.json().then((body) => {
          throw new Error((body && body.message) || '生成错误回执失败')
        })
      }
      const disposition = response.headers.get('Content-Disposition') || ''
      return response.blob().then((blob) => {
        saveBlob(blob, parseFileName(disposition) || '配套信息导入错误回执.xlsx')
      })
    })
}

/** 最近导入记录（默认 20 条，后端封顶 100 条） */
export function queryFacilityImportLogs (limit) {
  return javaGetAction(facilityImportUrl.logs, limit ? { limit } : {})
}

/* ==========================================================================
 * 三、下载小工具
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
 * 四、模块内共享常量与纯函数
 * ========================================================================== */

/** 重复策略的本地兜底（接口不可用时用；与后端 FacilityDuplicateStrategy 逐字一致） */
export const DUPLICATE_STRATEGIES_FALLBACK = [
  { code: 'reject', label: '重复即报错（不写库）' },
  { code: 'skip', label: '跳过重复行（只导新增）' },
  { code: 'update', label: '覆盖更新已有配套（空单元格不改动）' }
]

/** 默认策略：与后端一致，取最安全的一档 */
export const DEFAULT_DUPLICATE_STRATEGY = 'reject'

/**
 * 孤儿清单抹平成带稳定 key 的数组（ScreenDataTable 的 row-key 需要唯一值）。
 * @param {object} result 预览/入库结果
 */
export function flattenOrphans (result) {
  if (!result || !result.orphans) {
    return []
  }
  return result.orphans.map((item, index) => Object.assign({ key: `orphan-${index}` }, item))
}

/** 错误清单抹平成带稳定 key 的数组 */
export function flattenErrors (result) {
  if (!result || !result.errors) {
    return []
  }
  return result.errors.map((item, index) => Object.assign({ key: `error-${index}` }, item))
}

/**
 * 入库结果的一句话摘要。
 *
 * ★ 孤儿行数必须进摘要：日志与提示里若只写「导入 300 条」，用户不会知道
 *   其中 17 条挂不到宗地，事后要核对就得再做一次差集。
 */
export function importSummary (result) {
  if (!result || result.aborted) {
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
    parts.push(`其中孤儿 ${result.orphanRows} 条`)
  }
  if (!parts.length) {
    return '本次没有写入任何数据'
  }
  return `导入完成：${parts.join('，')}`
}

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

/**
 * ★ 字段字典的本地兜底（与后端 FacilityImportField 的列名、顺序一一对应）。
 *
 * 页面挂载时会调 queryFacilityImportFields 用后端返回的定义覆盖它，
 * 因此「后端加一列」时页面说明会自动跟上；这份兜底只保证接口不可用时页面仍能渲染。
 * 字段：column / label / type / required / options / optionText / maxLength / sample
 */
export const FACILITY_IMPORT_FIELDS_FALLBACK = [
  { column: 'crzdbh', label: '出让宗地编号', type: 'text', required: true, optionText: '自由文本', sample: '津西青(挂)2024-01号' },
  { column: 'ptxmmc', label: '配套项目名称', type: 'text', required: true, optionText: '自由文本', sample: '侯台片区规划路一道路工程' },
  { column: 'dkmc', label: '地块名称', type: 'text', required: false, optionText: '留空则取宗地的地块名称' },
  { column: 'ptsslb', label: '配套设施类别', type: 'enum', required: false, options: ['道路', '排水', '供水', '中水', '燃气', '路灯', '绿化', '交通设施'], optionText: '道路 / 排水 / 供水 / 中水 / 燃气 / 路灯 / 绿化 / 交通设施' },
  { column: 'xzqh', label: '行政区划', type: 'enum', required: false, optionText: '和平区 / 河东区 / …（16 区）' },
  { column: 'xmfl', label: '项目分类', type: 'enum', required: false, options: ['市级项目', '区级项目'], optionText: '市级项目 / 区级项目' },
  { column: 'jsxx', label: '建设性质', type: 'enum', required: false, options: ['新建', '改建', '扩建', '翻建', '其他'], optionText: '新建 / 改建 / 扩建 / 翻建 / 其他' },
  { column: 'dldj', label: '道路等级', type: 'enum', required: false, options: ['城市主干路', '城市次干路', '城市支路'], optionText: '城市主干路 / 城市次干路 / 城市支路' },
  { column: 'ghhxkd', label: '规划红线宽度（米）', type: 'number', required: false, optionText: '数值' },
  { column: 'cd', label: '长度（米）', type: 'number', required: false, optionText: '数值' },
  { column: 'tzgs', label: '投资估算（万元）', type: 'number', required: false, optionText: '数值' },
  { column: 'zjly', label: '资金来源', type: 'enum', required: false, options: ['土地整理成本', '地块收益', '成本分摊', '区内统筹', '其它'], optionText: '土地整理成本 / 地块收益 / 成本分摊 / 区内统筹 / 其它' },
  { column: 'dkcrscndptjgsj', label: '地块出让时承诺的配套竣工时间', type: 'date', required: false, optionText: '日期' },
  { column: 'sfzsjtjlz', label: '是否涉及提级论证', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'tjlzsftg', label: '提级论证是否通过', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'jsdw', label: '建设单位', type: 'text', required: false, optionText: '自由文本' },
  { column: 'sjdw', label: '设计单位', type: 'text', required: false, optionText: '自由文本' },
  { column: 'kcdw', label: '勘察单位', type: 'text', required: false, optionText: '自由文本' },
  { column: 'jldw', label: '监理单位', type: 'text', required: false, optionText: '自由文本' },
  { column: 'sgdw', label: '施工单位', type: 'text', required: false, optionText: '自由文本' },
  { column: 'jsgydw', label: '接收管养单位', type: 'text', required: false, optionText: '自由文本' },
  { column: 'xjpfsfwc', label: '项建批复是否完成', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'xjpfzt', label: '项建批复状态', type: 'enum', required: false, options: ['正常推进', '有问题'], optionText: '正常推进 / 有问题' },
  { column: 'kypfsfwc', label: '可研批复是否完成', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'kypfzt', label: '可研批复状态', type: 'enum', required: false, options: ['正常推进', '有问题'], optionText: '正常推进 / 有问题' },
  { column: 'csjgspfsfwc', label: '初设及概算批复是否完成', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'csjgspfzt', label: '初设及概算批复状态', type: 'enum', required: false, options: ['正常推进', '有问题'], optionText: '正常推进 / 有问题' },
  { column: 'gspfje', label: '概算批复金额（万元）', type: 'number', required: false, optionText: '数值' },
  { column: 'zjlsqk', label: '资金落实情况', type: 'enum', required: false, options: ['已落实', '未落实'], optionText: '已落实 / 未落实' },
  { column: 'sfkg', label: '是否开工', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'kgzt', label: '开工状态', type: 'enum', required: false, options: ['正常推进', '有问题'], optionText: '正常推进 / 有问题' },
  { column: 'yjkgsj', label: '预计开工时间', type: 'date', required: false, optionText: '日期' },
  { column: 'sjkgsj', label: '实际开工时间', type: 'date', required: false, optionText: '日期' },
  { column: 'sfjg', label: '是否竣工', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'yjjgsj', label: '预计竣工时间', type: 'date', required: false, optionText: '日期' },
  { column: 'sjjgsj', label: '实际竣工时间', type: 'date', required: false, optionText: '日期' },
  { column: 'sfyj', label: '是否移交', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'ptxmhdydydjdc', label: '配套项目核定用地与地籍调查', type: 'yes_no', required: false, optionText: '是 / 否' },
  { column: 'xjpfwj', label: '项建批复文件', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'kypfwj', label: '可研批复文件', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'csjgspfwj', label: '初设及概算批复文件', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'dlgh', label: '道路规划', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'ghgcxk', label: '规划工程许可', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'gxzhslsj', label: '管线综合矢量数据(shp)', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'zyptfa', label: '专业配套方案', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'zyglyj', label: '专业管理意见', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'ghydxkyhbsxbl', label: '规划用地许可与划拨手续办理', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'sgxk', label: '施工许可', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'bdcdj', label: '不动产登记', type: 'yes_no', required: false, optionText: '是 / 否（历史标志位，只读）' },
  { column: 'jtwt', label: '具体问题', type: 'text', required: false, optionText: '自由文本' },
  { column: 'gzjy', label: '工作建议', type: 'text', required: false, optionText: '自由文本' },
  { column: 'zlqsnrjsm', label: '资料缺失内容及说明', type: 'text', required: false, optionText: '自由文本' },
  { column: 'bz', label: '备注', type: 'text', required: false, optionText: '自由文本' },
  { column: 'lrdw', label: '录入单位', type: 'text', required: true, optionText: '自由文本' },
  { column: 'lrr', label: '录入人', type: 'text', required: true, optionText: '自由文本' },
  { column: 'lxdh', label: '联系电话', type: 'text', required: true, optionText: '11 位手机号' }
]

export default {
  facilityImportUrl,
  downloadFacilityTemplate,
  queryFacilityImportFields,
  queryFacilityDuplicateStrategies,
  previewFacilityImport,
  confirmFacilityImport,
  downloadFacilityErrorReport,
  queryFacilityImportLogs,
  flattenOrphans,
  flattenErrors,
  importSummary,
  fieldTypeText,
  DUPLICATE_STRATEGIES_FALLBACK,
  DEFAULT_DUPLICATE_STRATEGY,
  FACILITY_IMPORT_FIELDS_FALLBACK
}
