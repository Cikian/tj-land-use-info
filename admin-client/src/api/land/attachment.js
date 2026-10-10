import Vue from 'vue'
import { getAction, postAction, deleteAction, uploadAction } from '@/api/manage'
import { ACCESS_TOKEN } from '@/store/mutation-types'
import { compact, buildDownloadUrl } from '@/api/land/landAdmin'

/**
 * 数据管理 · 配套附件管理（上传 / 查询 / 预览 / 下载）接口
 * （方案 2.3.1（三）第 5 项）
 *
 * 后端：org.jeecg.modules.land.data.attachment.controller.LandAttachmentController
 * 权限码：land:data:attachment
 *
 * ★★★ 上传是**两步**，不是一步 —— 这一点与常见的「一个接口搞定上传」不同：
 *
 *   第 1 步：文件字节流 → jeecg 通用上传 `/sys/common/upload`
 *            返回一个相对存储路径（jeecg 把它放在 `message` 字段里，
 *            不是 `result`；这是 jeecg 的一个历史约定，踩过的人不少）。
 *   第 2 步：把「上一步返回的路径」+ 业务信息（bizType/bizId/fileType/...）
 *            提交给 `/land/data/attachment/save` 落库。
 *
 *   为什么拆两步：文件存储与业务登记是两件事。同一个文件字节可能被多个
 *   业务对象引用（例如同一份规划条件函同时支撑多个地块），
 *   把「存文件」和「挂业务」耦合在一个接口里，就没法复用了；
 *   而且上传失败与登记失败的原因完全不同，拆开后前端能给出准确的提示。
 *
 * ★ 旧系统的 5 个硬伤（本模块逐条对应修掉）：
 *   1. 类型写死 4 个槽位 file01~file04 → `fileType` 走字典可扩展；
 *   2. 查询靠递归扫描磁盘目录 → 改为查库；
 *   3. 预览前清空整个 temp 目录（并发互相删）→ 直接用 storePath 拼静态地址；
 *   4. 无大小/上传人/上传时间 → 后端都已落库；
 *   5. 目录名带业务语义、改名即丢数据 → fileType 与物理目录解耦。
 */

export const attachmentUrl = {
  list: '/land/data/attachment/list',
  byBiz: '/land/data/attachment/byBiz',
  tree: '/land/data/attachment/tree',
  byProject: '/land/data/attachment/byProject',
  summary: '/land/data/attachment/summary',
  typeDistribution: '/land/data/attachment/typeDistribution',
  allowedTypes: '/land/data/attachment/allowedTypes',
  save: '/land/data/attachment/save',
  download: '/land/data/attachment/download',
  previewUrl: '/land/data/attachment/previewUrl',
  delete: '/land/data/attachment/delete'
}

/* ==========================================================================
 * 一、查询
 * ========================================================================== */

/**
 * 分页查询附件。
 *
 * @param {object} params bizType,bizId,bizKey,fileType,keyword,uploadBy,
 *                        beginDate,endDate,pageNo,pageSize
 */
export function queryAttachmentPage (params) {
  return getAction(attachmentUrl.list, compact(params))
}

/** 某业务对象的全部附件（详情页用；不分页） */
export function queryAttachmentByBiz (bizType, bizId) {
  return getAction(attachmentUrl.byBiz, compact({ bizType, bizId }))
}

/** 概览（附件总数与总大小，页面顶部卡片用） */
export function queryAttachmentSummary (bizType) {
  return getAction(attachmentUrl.summary, compact({ bizType }))
}

/** 按附件类型分布（概览图用） */
export function queryTypeDistribution (bizType) {
  return getAction(attachmentUrl.typeDistribution, compact({ bizType }))
}

/**
 * 允许的附件类型清单。
 *
 * ★ 从后端取而不是前端硬编码：后端的校验白名单与字典
 *   `land_attach_type` 是同一份，前端另写一份必然漂移 ——
 *   漂移的后果是「下拉里能选、提交被拒」。
 */
export function queryAllowedTypes (bizType) {
  return getAction(attachmentUrl.allowedTypes, compact({ bizType }))
}

/**
 * 附件目录树：按**材料类型**分组（单业务对象）。
 *
 * @param {string} bizType land / facility / process
 * @param {string} bizId   业务对象 id
 * @returns {Promise} result 为
 *   { bizType, bizKey, groups:[{ key, fileType, fileTypeName, files, fileCount, totalSize }],
 *     totalFiles, totalSize, typeCount }
 *
 * ★ 只返回「有附件的材料类型」，空类型不进树。
 */
export function queryAttachmentTree (bizType, bizId) {
  return getAction(attachmentUrl.tree, compact({ bizType, bizId }))
}

/**
 * 跨项目的附件树（附件管理页）：项目 → 材料类型 → 文件。
 *
 * @param {string} [bizType] 只取某一类业务；为空表示全部
 * @param {number} [limit]   最多返回多少个项目（按附件数倒序，默认 200）
 */
export function queryAttachmentTreeByProject (bizType, limit) {
  return getAction(attachmentUrl.byProject, compact({ bizType, limit }))
}

/* ==========================================================================
 * 二、上传（两步）
 * ========================================================================== */

/** 第 1 步：文件字节流上传地址（jeecg 通用上传入口） */
export function uploadUrl () {
  const base = window._CONFIG['domianURL'] || ''
  return `${base}/sys/common/upload`
}

/** 第 1 步用的请求头（jeecg 上传接口同样要令牌） */
export function uploadHeaders () {
  const token = Vue.ls.get(ACCESS_TOKEN)
  return token ? { 'X-Access-Token': token } : {}
}

/**
 * 第 1 步：上传文件字节流，返回 { storePath, fileName, fileSize }。
 *
 * @param {File} file
 * @param {string} biz 存储子目录，例如 'land/attachment/2026/10'
 * @returns {Promise<{storePath:string,fileName:string,fileSize:number}>}
 */
export function uploadFileBytes (file, biz) {
  const formData = new FormData()
  formData.append('file', file)
  if (biz) {
    formData.append('biz', biz)
  }
  // ★ 必须用 uploadAction 而不是 postAction：
  //   postAction 会把参数当 JSON body 发，FormData 不会被序列化成 multipart；
  //   uploadAction 显式设了 Content-Type: multipart/form-data
  //   （见 @/api/manage.js 第 183 行），这才是文件上传的正确通道。
  return uploadAction(uploadUrl(), formData).then(res => {
    if (!res || !res.success) {
      throw new Error((res && res.message) || '文件上传失败')
    }
    // ★ jeecg 把存储路径放在 message 里（不是 result）—— 这是它 uploadLocal 的约定
    const storePath = res.message || res.result
    if (!storePath) {
      throw new Error('文件上传成功但未返回存储路径，请联系管理员')
    }
    return {
      storePath: String(storePath),
      fileName: file.name,
      fileSize: file.size
    }
  })
}

/**
 * 第 2 步：把上传结果登记到附件表。
 *
 * @param {object} meta {bizType,bizId,bizKey,fileType,fileName,fileSize,storePath,...}
 */
export function saveAttachmentMeta (meta) {
  return postAction(attachmentUrl.save, meta)
}

/* ==========================================================================
 * 三、预览与下载
 * ========================================================================== */

/**
 * 取预览信息。
 *
 * @returns result: {storePath, previewMode, previewable, url, contentType}
 *          previewMode ∈ image / pdf / office / text / download
 *
 * ★ 预览不走后端流式接口，而是「后端给相对路径 + 前端拼静态地址」：
 *   静态资源由 jeecg 的 `/sys/common/static/**` 直接服务，带宽与缓存都更好；
 *   旧系统把文件拷到 temp 再打开、并且每次预览前清空整个 temp 目录，
 *   并发时会互相删文件 —— 本实现彻底不做临时文件。
 */
export function queryPreviewUrl (id) {
  return getAction(attachmentUrl.previewUrl, { id })
}

/**
 * 拼静态文件访问地址。
 *
 * @param {string} storePath 相对存储路径（/land/attachment/2026/10/x.pdf 或 land/...）
 */
export function buildStaticUrl (storePath) {
  if (!storePath) {
    return undefined
  }
  if (String(storePath).indexOf('http') === 0) {
    return storePath
  }
  const base = (window._CONFIG['domianURL'] || '') + '/sys/common/static'
  const clean = String(storePath).replace(/^\/+/, '')
  return clean ? `${base}/${clean}` : undefined
}

/**
 * 下载附件（走后端流式接口，这样服务端才能累加下载次数）。
 *
 * ★ 用锚点 + query token，而不是 window.open：
 *   两者都发 GET，但锚点不会触发浏览器的弹窗拦截；
 *   token 放 query 是 jeecg JwtFilter 明确支持的方式
 *   （原生下载带不了自定义请求头）。
 */
export function downloadAttachment (id, fileName) {
  const link = document.createElement('a')
  link.style.display = 'none'
  link.href = buildDownloadUrl(attachmentUrl.download, { id })
  if (fileName) {
    link.setAttribute('download', fileName)
  }
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

/** 删除附件（逻辑删除） */
export function deleteAttachment (id) {
  return deleteAction(attachmentUrl.delete, { id })
}

/* ==========================================================================
 * 四、展示辅助
 * ========================================================================== */

/**
 * 预览方式 → 中文说明。
 *
 * ★ 单独给一句「不支持在线预览」的话术，而不是静默什么都不显示：
 *   旧系统是 `window.open` 裸打开，遇到 Office 文件在没装插件的机器上
 *   表现为「下载了一个文件但没打开」，用户以为系统坏了。
 */
export function previewModeText (mode) {
  switch (mode) {
    case 'image':
      return '图片预览'
    case 'pdf':
      return 'PDF 预览'
    case 'office':
      return 'Office 文档（可下载后用本机 Office 打开）'
    case 'text':
      return '文本预览'
    default:
      return '该格式不支持在线预览，请下载查看'
  }
}

/** 扩展名 → 预览方式（前端兜底；后端会给 previewMode 时优先用后端的） */
export function resolvePreviewMode (ext) {
  const value = String(ext || '').toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].indexOf(value) >= 0) {
    return 'image'
  }
  if (value === 'pdf') {
    return 'pdf'
  }
  if (['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].indexOf(value) >= 0) {
    return 'office'
  }
  if (['txt', 'csv', 'log', 'md', 'json', 'xml'].indexOf(value) >= 0) {
    return 'text'
  }
  return 'download'
}

/** 字节 → 可读大小（后端给了 readableSize 时优先用后端的） */
export function formatSize (bytes) {
  const value = Number(bytes)
  if (!value) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = value
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit++
  }
  return unit === 0 ? `${size} ${units[unit]}` : `${size.toFixed(1)} ${units[unit]}`
}

/** 业务类型 → 中文 */
export function bizTypeText (bizType) {
  switch (bizType) {
    case 'land':
      return '经营性用地'
    case 'facility':
      return '配套项目'
    case 'process':
      return '环节进度'
    default:
      return bizType || '—'
  }
}

/**
 * 按业务类型给上传子目录。
 *
 * ★ 目录只按「业务类型 + 年月」分层，**不带业务语义**
 *   （旧系统目录名是 `01-土地整理计划` 这种，改个名称就找不到文件了）。
 */
export function buildUploadBiz (bizType) {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const prefix = bizType === 'facility' ? 'facility' : (bizType === 'process' ? 'process' : 'land')
  return `${prefix}/attachment/${year}/${month}`
}

/** 附件类型下拉的本地兜底（与 sql/data/01_data_dict.sql 的 land_attach_type 逐字一致） */
export const ATTACH_TYPES_FALLBACK = [
  { value: '01', label: '土地整理计划' },
  { value: '02', label: '配套情况函' },
  { value: '03', label: '配套筹备函' },
  { value: '04', label: '出让宗地图形数据' },
  { value: '05', label: '项建批复' },
  { value: '06', label: '可研批复' },
  { value: '07', label: '初设及概算批复' },
  { value: '08', label: '道路规划' },
  { value: '09', label: '管线综合矢量数据' },
  { value: '10', label: '专业配套方案' },
  { value: '11', label: '施工许可' },
  { value: '12', label: '竣工与移交文件' },
  { value: '99', label: '其他' }
]

/** 业务类型下拉 */
export const BIZ_TYPE_OPTIONS = [
  { value: 'land', label: '经营性用地' },
  { value: 'facility', label: '配套项目' },
  { value: 'process', label: '环节进度' }
]

export default {
  attachmentUrl,
  queryAttachmentPage,
  queryAttachmentByBiz,
  queryAttachmentSummary,
  queryTypeDistribution,
  queryAllowedTypes,
  queryAttachmentTree,
  queryAttachmentTreeByProject,
  uploadUrl,
  uploadHeaders,
  uploadFileBytes,
  saveAttachmentMeta,
  queryPreviewUrl,
  buildStaticUrl,
  downloadAttachment,
  deleteAttachment,
  previewModeText,
  resolvePreviewMode,
  formatSize,
  bizTypeText,
  buildUploadBiz,
  ATTACH_TYPES_FALLBACK,
  BIZ_TYPE_OPTIONS
}
