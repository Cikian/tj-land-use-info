import {
  javaDeleteAction,
  javaGetAction,
  javaPostAction,
  buildJavaDownloadUrl,
  getJavaFileAccessHttpUrl,
  javaUploadUrl,
  javaAccessToken
} from '@/api/manageJava'

/**
 * 数据管理 · 配套附件管理（上传 / 查询 / 预览 / 下载）接口
 * ===============================================================
 * 后端：org.jeecg.modules.land.data.attachment.controller.LandAttachmentController
 * 权限码：land:data:attachment（列表 / 上传 / 下载 / 删除共用一个码）
 *
 * 【为什么用 manageJava 而不是 @/api/manage】
 * 接口在 Java 业务后端（VUE_DATA_JAVA_URL），不在中台。详见 manageJava.js 头注释。
 *
 * 关键约定：
 *  1. ★ **上传分两步**：文件字节走 jeecg 通用接口 POST /sys/common/upload 落盘，
 *     再把返回的相对路径交给 `/save` 落库。这样「上传」这件事全局只有一套实现
 *     （目录规则、大小限制都在那里），而「这个文件挂在哪条业务上」由本模块负责。
 *     ScreenUpload 组件负责第一步，`saveAttachment` 负责第二步。
 *  2. ★ ScreenUpload 的 success 事件里，存储路径在响应的 **message** 里
 *     （`storePath = res.message || res.result`），不是 result —— 照抄档案模块。
 *  3. ★ **预览返回的是相对路径**（`storePath`），不是完整 URL：静态资源地址要按
 *     部署环境拼（反向代理后域名与后端接口不同），所以后端只保证相对路径安全，
 *     前端用 getJavaFileAccessHttpUrl() 拼。这也意味着预览不需要带 token
 *     （jeecg 的 /sys/common/static/** 是 anon 放行的），适合直接塞进 img src。
 *  4. ★ **预览方式以 `previewMode` 为准**，前端不要自己从扩展名猜：
 *     扩展名归一化（大写、多个点、无扩展名）与预览方式的对应关系只应该有一份实现。
 *  5. ★ **下载只接受 id**：磁盘路径由服务端查库 + 前缀校验得到，绝不接受前端传入的
 *     路径（旧系统的 /pdf/render?filePath= 就是因此存在任意文件读取漏洞）。
 *     下载要走带 token 的 query string（见 buildAttachmentDownloadUrl），
 *     这样服务端才能统计下载次数。
 */

export const attachmentUrl = {
  list: '/land/data/attachment/list',
  byBiz: '/land/data/attachment/byBiz',
  summary: '/land/data/attachment/summary',
  typeDistribution: '/land/data/attachment/typeDistribution',
  allowedTypes: '/land/data/attachment/allowedTypes',
  save: '/land/data/attachment/save',
  download: '/land/data/attachment/download',
  previewUrl: '/land/data/attachment/previewUrl',
  delete: '/land/data/attachment/delete'
}

/** 附件目录相关接口（目录树 + 目录维护，见 LandAttachmentDirController） */
export const attachmentDirUrl = {
  tree: '/land/data/attachmentDir/tree',
  list: '/land/data/attachmentDir/list',
  add: '/land/data/attachmentDir/add',
  rename: '/land/data/attachmentDir/rename',
  delete: '/land/data/attachmentDir/delete'
}

/**
 * 附件目录树（含每个目录下的文件）。
 *
 * @param {string} bizType land / facility / process
 * @param {string} bizId   业务对象 id
 * @param {boolean} [withFile=true] 是否带文件节点；只要目录骨架时传 false
 * @returns {Promise} result 为
 *   { nodes:[{key,nodeType,label,dirPath,depth,fileCount,totalSize,empty,file,children}],
 *     rootFiles:[附件实体], totalFiles, totalSize, totalDirs }
 *
 * ★ 一次返回整棵树（不逐层懒加载）：单个业务对象下的附件量可控（几十到几百），
 *   一次给全能让「展开即见文件」，省掉每层一个 loading 态。
 */
export function queryAttachmentTree (bizType, bizId, withFile) {
  return javaGetAction(attachmentDirUrl.tree, {
    bizType,
    bizId,
    // 默认 true；只有显式传 false 才只要目录骨架
    withFile: withFile !== false
  })
}

/** 某业务对象的目录列表（扁平，供「上传时选目录」用） */
export function queryAttachmentDirs (bizType, bizId) {
  return javaGetAction(attachmentDirUrl.list, { bizType, bizId })
}

/**
 * 新建目录（可多级，例如 招标文件/2024；中间层由服务端自动补建，幂等）。
 * 路径里带 `/` 表示建多级，不是错误。
 */
export function createAttachmentDir (bizType, bizId, dirPath, bizKey) {
  return javaPostAction(attachmentDirUrl.add, { bizType, bizId, dirPath, bizKey })
}

/** 重命名目录（只改末段名；子目录与目录下附件的路径由服务端一起改） */
export function renameAttachmentDir (bizType, bizId, oldPath, newName, bizKey) {
  return javaPostAction(attachmentDirUrl.rename, { bizType, bizId, oldPath, newName, bizKey })
}

/**
 * 删除目录（**不删文件**：目录下的文件移到父目录）。
 *
 * @param {boolean} [recursive=false] 有子目录时必须是 true，否则服务端会拒绝并提示
 */
export function deleteAttachmentDir (bizType, bizId, dirPath, recursive) {
  return javaDeleteAction(attachmentDirUrl.delete, {
    bizType,
    bizId,
    dirPath,
    recursive: recursive === true
  })
}

/** 业务类型取值（与后端 LandAttachment 的常量一致） */
export const ATTACHMENT_BIZ_TYPES = [
  { value: 'land', label: '经营性用地' },
  { value: 'facility', label: '配套项目' },
  { value: 'process', label: '环节进度' }
]

/**
 * 下载地址（token 拼在 query 上）。
 *
 * 浏览器原生下载（window.open / a[href]）带不了 X-Access-Token 请求头，
 * 而 jeecg 的 JwtFilter 支持从 query string 的 `token` 参数取令牌。
 * ★ 必须走这个地址而不是静态资源地址，否则服务端统计不到下载次数。
 */
export function buildAttachmentDownloadUrl (id) {
  return buildJavaDownloadUrl(attachmentUrl.download, { id })
}

/** 上传接口地址与请求头（供 ScreenUpload 的 :action / :headers 使用） */
export function attachmentUploadAction () {
  return javaUploadUrl()
}

export function attachmentUploadHeaders () {
  const token = javaAccessToken()
  return token ? { 'X-Access-Token': token } : {}
}

/* ==========================================================================
 * 一、查询（5 个）
 * ========================================================================== */

/**
 * 分页查询附件。
 *
 * @param {object} params AttachmentQueryDTO：
 *   bizType（land / facility / process，空 = 全部）
 *   bizId（精确）
 *   bizKey（模糊：宗地编号 / 配套项目名称）
 *   fileType（精确：01~13、99，见字典 land_attach_type）
 *   keyword（★ 文件名模糊，后端字段名是 keyword 不是 fileName）
 *   uploadBy（上传人账号，精确）
 *   beginDate / endDate（yyyy-MM-dd，后端会补成当天 00:00:00 ~ 23:59:59）
 *   pageNo / pageSize
 * @returns {Promise} result 为 IPage<LandAttachment>；每行除库内字段外还有展示字段：
 *   url（相对路径）/ readableSize / fileTypeText / previewable / previewMode
 */
export function queryAttachmentPage (params) {
  return javaGetAction(attachmentUrl.list, params)
}

/**
 * 某个业务对象的全部附件（宗地详情 / 配套详情 / 环节进度详情三处共用）。
 * @param {string} bizType land / facility / process
 * @param {string} bizId
 */
export function queryAttachmentsByBiz (bizType, bizId) {
  return javaGetAction(attachmentUrl.byBiz, { bizType, bizId })
}

/**
 * 附件概览。
 * @param {string} [bizType] 不传 = 全部业务
 * @returns {Promise} result 为 { num, totalSize, readableSize, typeCount }
 */
export function queryAttachmentSummary (bizType) {
  return javaGetAction(attachmentUrl.summary, bizType ? { bizType } : {})
}

/**
 * 按附件类型的分布（页面顶部的「附件概览」用）。
 * @returns {Promise} result 为 [{ fileType, fileTypeText, num, totalSize, readableSize }]
 */
export function queryAttachmentTypeDistribution (bizType) {
  return javaGetAction(attachmentUrl.typeDistribution, bizType ? { bizType } : {})
}

/**
 * 允许的附件类型（码 + 中文名），上传表单的类型下拉用它。
 *
 * ★ 不硬编码这 13 类：后端可以在「系统管理 → 数据字典」里调整（附件类型是最会扩的一类），
 *   前端写死会出现「字典里加了 14 类、页面还只能选 13 类」。
 * @returns {Promise} result 为 [{ value, text }]
 */
export function queryAllowedAttachmentTypes () {
  return javaGetAction(attachmentUrl.allowedTypes, {})
}

/* ==========================================================================
 * 二、保存 / 删除（2 个）
 * ========================================================================== */

/**
 * 保存附件元数据（文件本体已由 /sys/common/upload 落盘）。
 *
 * @param {object} data 归属 + 元数据：
 *   bizType / bizId / bizKey / fileType / fileName / fileExt / fileSize /
 *   fileMd5 / contentType / storeType / storePath / remark / sortNo
 *   ★ id / delFlag / uploadBy / uploadName / uploadTime / downloadCount
 *     一律由服务端重新赋值，客户端传了也会被忽略。
 *   ★ storePath 直接把上传接口返回的值传进来即可（它以 `/` 开头，因为 jeecg
 *     的上传接口会把 biz 原样拼进返回路径）；后端会**归一成不带前导斜杠**的相对路径，
 *     并对 `..`（路径穿越）与盘符绝对路径仍然直接报错不写库。
 *     前端不需要自己去斜杠 —— 由服务端统一处理，避免各调用方写法不一致。
 * @returns {Promise} result 为补齐了展示字段的附件实体（可直接渲染，不必再查列表）
 */
export function saveAttachment (data) {
  return javaPostAction(attachmentUrl.save, data)
}

/** 删除附件（逻辑删除 + 变更留痕；磁盘文件保留，同一文件可能被多条业务引用） */
export function deleteAttachment (id) {
  return javaDeleteAction(attachmentUrl.delete, { id })
}

/* ==========================================================================
 * 三、预览（1 个）
 * ========================================================================== */

/**
 * 取预览所需的相对存储路径与预览方式。
 *
 * @param {string} id
 * @returns {Promise} result 为 { id, storePath, fileName, fileExt, contentType,
 *   fileTypeText, previewMode, previewable }
 *   previewMode ∈ image / pdf / office / text / download
 */
export function queryAttachmentPreviewUrl (id) {
  return javaGetAction(attachmentUrl.previewUrl, { id })
}

/**
 * 把相对 storePath 拼成静态资源地址。
 * 独立导出一次是为了让 view 层不必再 import 一次 manageJava，
 * 也避免各处拼法不一致（多一个前导斜杠就是 404）。
 */
export function buildPreviewHttpUrl (storePath) {
  return getJavaFileAccessHttpUrl(storePath)
}

/**
 * 本地兜底的附件类型（接口不可用时用；与 sql/data/01_data_dict.sql 的
 * land_attach_type 逐字一致）。
 *
 * ★ item_value 存的是**编码**（01/02/…/13、99）而不是中文 —— 附件类型是文件分类码，
 *   会被拼进存储目录名与导出文件名，必须稳定且与旧系统目录名可对照。
 */
export const ATTACHMENT_TYPES_FALLBACK = [
  { value: '01', text: '土地整理计划' },
  { value: '02', text: '配套情况函' },
  { value: '03', text: '配套筹备函' },
  { value: '04', text: '出让宗地图形数据' },
  { value: '05', text: '项建批复' },
  { value: '06', text: '可研批复' },
  { value: '07', text: '初设及概算批复' },
  { value: '08', text: '道路规划' },
  { value: '09', text: '管线综合矢量数据' },
  { value: '10', text: '专业配套方案' },
  { value: '11', text: '施工许可' },
  { value: '12', text: '竣工与移交文件' },
  { value: '99', text: '其他' }
]

/**
 * 去掉扩展名（用于「文件题名」这类默认值）。
 * 放这里而不是 constants：附件以外的调用方不需要它，就近放减少跨文件跳转。
 */
export function stripExt (fileName) {
  const text = String(fileName || '')
  const index = text.lastIndexOf('.')
  return index > 0 ? text.slice(0, index) : text
}

/** 取小写扩展名（不含点） */
export function resolveExt (fileName) {
  const text = String(fileName || '')
  const index = text.lastIndexOf('.')
  return index > -1 ? text.slice(index + 1).toLowerCase() : ''
}

export default {
  attachmentUrl,
  attachmentDirUrl,
  ATTACHMENT_BIZ_TYPES,
  ATTACHMENT_TYPES_FALLBACK,
  buildAttachmentDownloadUrl,
  buildPreviewHttpUrl,
  attachmentUploadAction,
  attachmentUploadHeaders,
  queryAttachmentPage,
  queryAttachmentsByBiz,
  queryAttachmentSummary,
  queryAttachmentTypeDistribution,
  queryAllowedAttachmentTypes,
  queryAttachmentTree,
  queryAttachmentDirs,
  createAttachmentDir,
  renameAttachmentDir,
  deleteAttachmentDir,
  saveAttachment,
  deleteAttachment,
  queryAttachmentPreviewUrl,
  stripExt,
  resolveExt
}
