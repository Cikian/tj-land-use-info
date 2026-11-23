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
  tree: '/land/data/attachment/tree',
  byProject: '/land/data/attachment/byProject',
  summary: '/land/data/attachment/summary',
  typeDistribution: '/land/data/attachment/typeDistribution',
  allowedTypes: '/land/data/attachment/allowedTypes',
  objectOptions: '/land/data/attachment/objectOptions',
  save: '/land/data/attachment/save',
  download: '/land/data/attachment/download',
  previewUrl: '/land/data/attachment/previewUrl',
  delete: '/land/data/attachment/delete'
}

/**
 * 附件目录树（按**材料类型**分组，两层）。
 *
 * @param {string} bizType land / facility / process
 *                        ★ 必传：宗地与配套是两套不同的材料清单（宗地 4 类 / 配套 13 类）
 * @param {string} bizId   业务对象 id
 * @returns {Promise} result 为
 *   { bizType, bizKey,
 *     groups:[{ key, fileType, fileTypeName, files:[附件], fileCount, totalSize }],
 *     totalFiles, totalSize, typeCount }
 *
 * ★ 只返回「有附件的材料类型」——空类型不进树，所以前端不需要处理空节点。
 * ★ 树是派生结果（目录名 = 材料类型名，类型码存在附件的 file_type 里），
 *   没有任何需要单独维护的目录数据。
 */
export function queryAttachmentTree (bizType, bizId) {
  return javaGetAction(attachmentUrl.tree, { bizType, bizId })
}

/**
 * 上传弹窗归属候选。入口在附件权限域内，避免附件角色被档案/配套 Admin 权限拦截。
 */
export function queryAttachmentObjectOptions (target, params) {
  return javaGetAction(attachmentUrl.objectOptions, Object.assign({}, params, { target }))
}

/**
 * 跨项目的附件树（附件管理页用）：项目 → 材料类型 → 文件。
 *
 * @param {string} [bizType] 只取某一类业务；为空表示全部
 * @param {number} [limit]   最多返回多少个项目（按附件数倒序，默认 200）
 * @returns {Promise} result 为
 *   [{ bizId, bizKey, totalFiles, totalSize, typeCount,
 *      tree:{ groups:[{ key, fileType, fileTypeName, files, fileCount, totalSize }] } }]
 *
 * ★ 不返回没有任何附件的项目，所以前端不需要过滤空项目。
 * ★ 一次返回整棵树：单类业务的附件量可控（实测几百条），
 *   这样「展开即见文件」，也不会出现某棵子树被分页截断的假象。
 */
export function queryAttachmentTreeByProject (bizType, limit) {
  return javaGetAction(attachmentUrl.byProject, { bizType, limit })
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
 *   fileType（精确：宗地 01~04；配套 / 环节 01~13；迁移遗留的 99 也可检索）
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
 * 可用的附件类型（材料类型）列表 —— **按业务类型取对应那套**。
 *
 * ★ 必须传 bizType：宗地与配套是两套不同的材料清单
 *   （宗地 4 类：土地整理计划 / 配套情况函 / 配套筹备函 / 出让宗地图形数据（shp）；
 *    配套 13 类：01 配套项目核定用地与地籍调查 → 13 移交文件，P1-08 报告图 3 口径）。
 *   不区分的话用户会在下拉里看到一半无关选项，更糟的是两套 01~04 重号，
 *   同一个码值在两套里是两种材料 —— 名字必定错一半。
 *
 * @param {string} bizType land / facility / process（环节挂配套下，与 facility 同一套）
 * @returns {Promise} result 为 [{ value, text }]，顺序即材料目录顺序
 */
export function queryAllowedAttachmentTypes (bizType) {
  return javaGetAction(attachmentUrl.allowedTypes, { bizType })
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
 * 本地兜底的材料类型目录（**唯一基准**：P1-08 报告图 3）。
 *
 * ★ 为什么分两套常量而不是一份合并清单：宗地 4 类、配套 13 类，
 *   两套的 01~04 是**重号**（宗地 01=土地整理计划，配套 01=核定用地与地籍调查）。
 *   合并成一份平铺下拉会出现两条 value='01'，选中后显示的名字永远错一半。
 * ★ 运行时权威来源是后端 `/land/data/attachment/allowedTypes`（按 bizType 返回）；
 *   这里只在接口不可用时兜底，两处必须逐字一致（后端改白名单要同步改这里）。
 * ★ 环节进度（process）跟随挂靠侧 = 配套，所以用配套那 13 类。
 * ★ 迁移遗留的 99「其他（历史）」不在任何一套里：它只用于历史附件回显，
 *   新上传落 99 会被后端拒绝。
 */
export const ATTACHMENT_TYPES_LAND = [
  { value: '01', text: '土地整理计划' },
  { value: '02', text: '配套情况函' },
  { value: '03', text: '配套筹备函' },
  { value: '04', text: '出让宗地图形数据（shp）' }
]

export const ATTACHMENT_TYPES_FACILITY = [
  { value: '01', text: '配套项目核定用地与地籍调查' },
  { value: '02', text: '项建批复' },
  { value: '03', text: '可研批复' },
  { value: '04', text: '初设及概算批复' },
  { value: '05', text: '道路规划' },
  { value: '06', text: '规划工程许可' },
  { value: '07', text: '专业配套方案' },
  { value: '08', text: '专业管理意见' },
  { value: '09', text: '规划用地许可与划拨手续办理' },
  { value: '10', text: '施工许可' },
  { value: '11', text: '不动产登记' },
  { value: '12', text: '竣工文件' },
  { value: '13', text: '移交文件' }
]

/**
 * 按归属取兜底目录。
 * @param {string} [bizType] land / facility / process；未选时与后端同规则退回宗地那套
 */
export function attachTypeOptionsOf (bizType) {
  return (bizType === 'facility' || bizType === 'process'
    ? ATTACHMENT_TYPES_FACILITY
    : ATTACHMENT_TYPES_LAND).slice()
}

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
  ATTACHMENT_BIZ_TYPES,
  ATTACHMENT_TYPES_LAND,
  ATTACHMENT_TYPES_FACILITY,
  attachTypeOptionsOf,
  buildAttachmentDownloadUrl,
  buildPreviewHttpUrl,
  attachmentUploadAction,
  attachmentUploadHeaders,
  queryAttachmentPage,
  queryAttachmentsByBiz,
  queryAttachmentSummary,
  queryAttachmentTypeDistribution,
  queryAllowedAttachmentTypes,
  queryAttachmentObjectOptions,
  queryAttachmentTree,
  queryAttachmentTreeByProject,
  saveAttachment,
  deleteAttachment,
  queryAttachmentPreviewUrl,
  stripExt,
  resolveExt
}
