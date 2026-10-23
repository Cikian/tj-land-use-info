/**
 * 大屏端文件预览公共工具
 * ===============================================================
 * 这里只做两件事：
 *   1. 把「文件直链」拼成 kkFileView 的在线预览地址（Office / WPS / OFD 用）；
 *   2. 在没有后端 previewMode 时，按扩展名兜底判断预览方式。
 *
 * ★ 为什么预览方式以**后端** previewMode 为准：档案、收发文、数据附件、提级材料
 *   四个模块共用后端 `FilePreviewUtils` 这一份判定；前端再维护第二份白名单，
 *   迟早出现「同一个 .wps 在附件管理能看、在档案详情说不能看」。
 *   本文件的 resolvePreviewMode 只在两种情况下兜底：
 *     · 刚上传、还没落库的临时文件（后端没有记录可查）；
 *     · 历史接口没返回 previewMode。
 *
 * ★ 为什么 Office 不自己渲染：浏览器没有原生 doc/xls/wps 渲染能力。
 *   kkFileView 服务端用 LibreOffice 转 PDF/图片，前端只负责 iframe 展示。
 *   地址形如：`http://host:8012/onlinePreview?url=<base64(文件直链)>`。
 *   url 参数必须是 Base64 + encodeURIComponent 双编码，否则中文文件名/特殊字符会丢。
 */

/** 图片：浏览器原生可渲染 */
const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'ico']
/** Office / WPS / OFD：浏览器不能直接渲染，交 kkFileView */
const OFFICE_EXTS = [
  'doc', 'docx', 'docm', 'dot', 'dotx', 'dotm',
  'xls', 'xlsx', 'xlsm', 'xlt', 'xltx', 'xltm',
  'ppt', 'pptx', 'pptm', 'pot', 'potx',
  'rtf',
  'wps', 'wpt', 'et', 'ett', 'dps', 'dpt',
  'ofd'
]
/** 纯文本 */
const TEXT_EXTS = ['txt', 'csv', 'log', 'md', 'xml', 'json']
/** 压缩包（kkFileView 支持目录浏览） */
const ARCHIVE_EXTS = ['zip', 'rar', '7z', 'tar', 'gz', 'tgz', 'bz2', 'jar']
/** CAD 图纸 */
const CAD_EXTS = ['dwg', 'dxf', 'dwf', 'dwt', 'plt', 'cf2']
/** 音视频 */
const MEDIA_EXTS = ['mp4', 'avi', 'mov', 'wmv', 'mkv', 'flv', 'webm', 'mp3', 'wav', 'm4a', 'ogg']

/** 取小写扩展名（兼容 `docx` / `.docx` / `规划许可证.docx` / 带路径的文件名） */
export function extOf (extOrFileName) {
  let value = String(extOrFileName == null ? '' : extOrFileName).trim()
  if (!value) return ''
  const slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'))
  if (slash >= 0) value = value.slice(slash + 1)
  const dot = value.lastIndexOf('.')
  if (dot >= 0) {
    if (dot === value.length - 1) return ''
    value = value.slice(dot + 1)
  }
  return value.trim().toLowerCase()
}

/**
 * 扩展名 / 文件名 → 预览方式（**仅用于界面标签**）。
 *
 * ★ 2026-10-22 起所有文件统一交 kkFileView，前端不再按 mode 选择渲染器；
 *   mode 只决定「预览方式」标签，以及 kkFileView 打不开时的兜底文案。
 * @returns {'image'|'pdf'|'office'|'text'|'archive'|'cad'|'media'|'online'}
 */
export function resolvePreviewMode (extOrFileName) {
  const ext = extOf(extOrFileName)
  if (!ext) return 'online'
  if (IMAGE_EXTS.indexOf(ext) >= 0) return 'image'
  if (ext === 'pdf') return 'pdf'
  if (OFFICE_EXTS.indexOf(ext) >= 0) return 'office'
  if (TEXT_EXTS.indexOf(ext) >= 0) return 'text'
  if (ARCHIVE_EXTS.indexOf(ext) >= 0) return 'archive'
  if (CAD_EXTS.indexOf(ext) >= 0) return 'cad'
  if (MEDIA_EXTS.indexOf(ext) >= 0) return 'media'
  return 'online'
}

/** 预览方式 → 中文文案（表格标签 / 弹窗页头共用） */
export function previewModeText (mode) {
  switch (mode) {
    case 'image':
      return '图片预览'
    case 'pdf':
      return 'PDF 预览'
    case 'office':
      return 'Office / WPS 在线预览'
    case 'text':
      return '文本预览'
    case 'archive':
      return '压缩包预览'
    case 'cad':
      return 'CAD 预览'
    case 'media':
      return '音视频预览'
    case 'online':
    case 'download':
      return '在线预览'
    default:
      return '在线预览'
  }
}

/**
 * 该方式能否在线打开。
 * ★ 现在恒为 true：所有文件都交 kkFileView，不支持由它自己的错误页反馈。
 */
export function isPreviewableMode (mode) {
  return true
}

/** 当前构建/运行时配置的预览服务地址（例如 http://127.0.0.1:8012/onlinePreview） */
export function onlinePreviewBase () {
  const config = (typeof window !== 'undefined' && window._CONFIG) || {}
  return String(config.onlinePreviewDomainURL || '').trim()
}

/** 是否已经配置了在线预览服务（没配置时 Office/WPS 只能下载） */
export function hasOnlinePreviewService () {
  return !!onlinePreviewBase()
}

/** 字节 → 可读大小（各模块统一展示口径） */
export function formatFileSize (bytes) {
  const value = Number(bytes)
  if (!value || value <= 0) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = value
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit++
  }
  return unit === 0 ? `${Math.round(size)} ${units[unit]}` : `${size.toFixed(2)} ${units[unit]}`
}

/**
 * UTF-8 → Base64。
 * ★ 不能直接 btoa(fileUrl)：btoa 只接受 Latin-1，中文路径会抛 InvalidCharacterError。
 */
function base64EncodeUtf8 (text) {
  if (typeof TextEncoder !== 'undefined') {
    const bytes = new TextEncoder().encode(text)
    let binary = ''
    // 分块拼接，避免超长 URL 触发 apply 参数个数上限
    const chunkSize = 0x8000
    for (let i = 0; i < bytes.length; i += chunkSize) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize))
    }
    return window.btoa(binary)
  }
  // 老浏览器兜底（Chrome 64 以下没有 TextEncoder；大屏实际不会走到这里）
  return window.btoa(unescape(encodeURIComponent(text)))
}

/**
 * 文件直链 → kkFileView 在线预览地址。
 *
 * @param {string} fileUrl  文件的可直接访问地址（http/https 开头，或同源相对路径）
 * @param {string} [fileName] 原始文件名；用于 fullfilename 参数，帮助 kkFileView 判断类型
 * @returns {string} 未配置预览服务时返回 ''
 */
export function buildOnlinePreviewUrl (fileUrl, fileName) {
  const base = onlinePreviewBase()
  if (!base || !fileUrl) return ''

  const url = String(fileUrl)
  let source = url
  // 下载流接口通常没有扩展名，kkFileView 靠 fullfilename 识别类型；静态资源已有扩展名，这里补上也无害
  if (fileName) {
    const separator = source.indexOf('?') >= 0 ? '&' : '?'
    source += `${separator}fullfilename=${encodeURIComponent(fileName)}`
  }
  const encoded = encodeURIComponent(base64EncodeUtf8(source))
  const join = base.indexOf('?') >= 0 ? '&' : '?'
  return `${base}${join}url=${encoded}`
}

export default {
  extOf,
  resolvePreviewMode,
  previewModeText,
  isPreviewableMode,
  onlinePreviewBase,
  hasOnlinePreviewService,
  buildOnlinePreviewUrl,
  formatFileSize
}
