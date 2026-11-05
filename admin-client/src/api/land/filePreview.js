/**
 * 文件在线预览（kkFileView）通用工具。
 *
 * 数据管理附件、档案卷内文件、收发文附件都会用到「怎么打开这个文件」的判断，
 * 因此预览方式的兜底映射、kkFileView 地址拼装集中在这里，避免各页面各写一套后漂移
 * （典型症状是同一个 docx 在附件管理能预览、在档案详情里却提示只能下载）。
 *
 * 技术路线：图片 / PDF 由浏览器直接渲染静态资源；Office / WPS / OFD 浏览器无法渲染，
 * 统一交 kkFileView 转换后再用 iframe 展示。前端不引入任何 npm 包。
 */

/**
 * UTF-8 字符串 → Base64。
 *
 * ★ 不能直接用 `btoa(fileUrl)`：btoa 只接受 Latin-1 字符，遇到中文路径（如
 *   `/archive/2026/10/规划许可证.docx`）会抛 InvalidCharacterError。
 *   优先用 TextEncoder 取 UTF-8 字节；老浏览器没有 TextEncoder 时退回
 *   `unescape(encodeURIComponent())` 这个经典写法（两者得到的字节序列一致）。
 */
function utf8ToBase64 (value) {
  const text = String(value == null ? '' : value)
  let binary = ''
  if (typeof window !== 'undefined' && window.TextEncoder) {
    const bytes = new window.TextEncoder().encode(text)
    for (let i = 0; i < bytes.length; i++) {
      binary += String.fromCharCode(bytes[i])
    }
  } else {
    // encodeURIComponent 先按 UTF-8 转义，再 percent-decode 成单字节字符串
    binary = window.unescape(encodeURIComponent(text))
  }
  return window.btoa(binary)
}

/**
 * 拼 kkFileView 在线预览地址。
 *
 * @param {string} fileUrl  文件的**完整可访问地址**（后端返回相对路径时先经
 *                          `getFileAccessHttpUrl` 拼接）
 * @param {string} [fileName] 原始文件名。带上它后 kkFileView 才能按真实扩展名选转换器，
 *                            尤其是下载接口这类 URL 后缀看不出类型的地址。
 * @returns {string} 未配置预览服务时返回空串，由调用方给出「请联系管理员」的提示
 */
export function buildOnlinePreviewUrl (fileUrl, fileName) {
  if (!fileUrl) {
    return ''
  }
  const config = (typeof window !== 'undefined' && window._CONFIG) || {}
  const base = config['onlinePreviewDomainURL']
  if (!base) {
    return ''
  }
  let target = String(fileUrl)
  if (fileName) {
    // fullfilename 是 kkFileView 识别扩展名的依据；原 URL 可能已有查询参数（例如带 token）
    const joiner = target.indexOf('?') >= 0 ? '&' : '?'
    target += `${joiner}fullfilename=${encodeURIComponent(fileName)}`
  }
  const encoded = encodeURIComponent(utf8ToBase64(target))
  // 预览服务地址本身可能已是 `/fileview/onlinePreview?xxx`，所以不能硬拼 `?`
  const separator = String(base).indexOf('?') >= 0 ? '&' : '?'
  return `${base}${separator}url=${encoded}`
}

/**
 * 扩展名 / 文件名 → 预览方式。
 *
 * ★ 前端只在两种场景用它兜底：① 临时文件（还没落库，后端没给 previewMode）；
 *   ② 后端返回里没有 previewMode。正常流程以后端的 previewMode 为准 ——
 *   两边各猜一套必然漂移，最终表现为「后端说能预览、前端硬塞 iframe 白屏」。
 *
 * ★ 映射与后端 FilePreviewUtils 保持同一口径（含 WPS 私有格式与 OFD）。
 *
 * @param {string} extOrFileName 扩展名（可带点）或完整文件名
 * @returns {'image'|'pdf'|'office'|'text'|'archive'|'cad'|'media'|'online'}
 */
export function resolvePreviewMode (extOrFileName) {
  let value = String(extOrFileName == null ? '' : extOrFileName).trim()
  if (!value) {
    return 'online'
  }
  // 完整路径里目录名可能带点，先取最后一段文件名再取扩展名
  const slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'))
  if (slash >= 0) {
    value = value.substring(slash + 1)
  }
  const dot = value.lastIndexOf('.')
  if (dot >= 0) {
    if (dot === value.length - 1) {
      return 'online'
    }
    value = value.substring(dot + 1)
  }
  value = value.trim().toLowerCase()
  if (!value) {
    return 'online'
  }
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'ico'].indexOf(value) >= 0) {
    return 'image'
  }
  if (value === 'pdf') {
    return 'pdf'
  }
  // Office / WPS / OFD
  if ([
    'doc', 'docx', 'docm', 'dot', 'dotx', 'dotm',
    'xls', 'xlsx', 'xlsm', 'xlt', 'xltx', 'xltm',
    'ppt', 'pptx', 'pptm', 'pot', 'potx', 'rtf',
    'wps', 'wpt', 'et', 'ett', 'dps', 'dpt', 'ofd'
  ].indexOf(value) >= 0) {
    return 'office'
  }
  if (['txt', 'csv', 'log', 'md', 'xml', 'json'].indexOf(value) >= 0) {
    return 'text'
  }
  if (['zip', 'rar', '7z', 'tar', 'gz', 'tgz', 'bz2', 'jar'].indexOf(value) >= 0) {
    return 'archive'
  }
  if (['dwg', 'dxf', 'dwf', 'dwt', 'plt', 'cf2'].indexOf(value) >= 0) {
    return 'cad'
  }
  if (['mp4', 'avi', 'mov', 'wmv', 'mkv', 'flv', 'webm', 'mp3', 'wav', 'm4a', 'ogg'].indexOf(value) >= 0) {
    return 'media'
  }
  return 'online'
}

/** 预览方式 → 中文说明（各页面共用同一套文案，避免口径不一致） */
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
    default:
      return '在线预览'
  }
}

/** 该预览方式是否可以在线预览（现在所有文件都交 kkFileView，恒为 true） */
export function isPreviewableMode (mode) {
  return true
}

export default {
  buildOnlinePreviewUrl,
  resolvePreviewMode,
  previewModeText,
  isPreviewableMode
}
