/**
 * 数据管理 · 共享枚举与展示辅助
 * ---------------------------------------------------------------
 * 为什么单独抽一个文件（与 views/screen/archive/constants.js 同一出发点）：
 *   同样几个枚举（项目分类 / 行政区划 / 配套设施类别 / 环节情况 / 环节问题类型 /
 *   附件类型）会被五个面板的检索表单、编辑弹窗、详情页反复引用；
 *   若各写一份，必然出现「检索条件用 10 个区、录入表单用另外 10 个区」这类漂移，
 *   表现是「按某个区筛，明明有数据却筛不出来」。
 *   大屏这一版全部收敛到这里，页面里不再各写一套。
 *
 * ★ 取值来源（唯一权威）：jeecg-module-land/src/main/resources/sql/data/01_data_dict.sql
 *   六个字典 **逐字** 抄进下面的兜底常量：
 *     land_project_type      项目分类        2 项
 *     land_xzqh              行政区划        16 项
 *     land_facility_category 配套设施类别    8 项
 *     land_process_status    环节情况        4 项（★ 与后端 ProcessStatus 枚举必须一致）
 *     land_process_issue     环节问题类型    5 项
 *     land_attach_type       附件类型        13 项（item_value 是编码 01/02/…/99）
 *   页面挂载时会用 `queryLandDictItems(dictDefinitions())`（在 api/land/landAdmin.js，
 *   见本文件末尾「字典定义」一节）从字典接口取一次覆盖，取不到的键保留下面的兜底值。
 *   兜底只保证「字典接口不可用时页面仍然能渲染、能选」。
 *
 * ★ 建设性质 / 道路等级 / 资金来源 **不在字典里**：它们的权威定义在
 *   `FacilityImportField`（Java 常量），与批量导入模板的取值提示同源。
 *   这里按那份清单逐字抄，改动时必须两边同改 —— 否则会出现
 *   「页面能选、导入却不认」这种最难查的问题。
 */

/* ============================ 字典编码 ============================ */

export const DICT_CODES = {
  projectType: 'land_project_type',
  xzqh: 'land_xzqh',
  facilityCategory: 'land_facility_category',
  processStatus: 'land_process_status',
  processIssue: 'land_process_issue',
  attachType: 'land_attach_type'
}

/* ============================ 兜底枚举 ============================ */

/** 项目分类（后端强校验只能填这两个值） */
export const PROJECT_TYPES = ['市级项目', '区级项目']

/** 天津市 16 个行政区（与旧库 xzqh 实测取值逐字一致） */
export const XZQH_LIST = [
  '和平区', '河东区', '河西区', '南开区', '河北区', '红桥区',
  '东丽区', '西青区', '津南区', '北辰区', '武清区', '宝坻区',
  '滨海新区', '宁河区', '静海区', '蓟州区'
]

/**
 * 配套设施类别（8 项）。
 * ★ 字典写「燃气」而旧录入页写「供气」，这里统一为「燃气」；
 *   同义异写由批量导入的别名表兜住，前端下拉只给规范值。
 */
export const FACILITY_CATEGORIES = ['道路', '排水', '供水', '中水', '燃气', '路灯', '绿化', '交通设施']

/**
 * 环节情况（4 项）。
 * ★ 与后端 org.jeecg.modules.land.data.process.enums.ProcessStatus 逐字一致：
 *   它驱动状态配色与阶段汇总口径，两边漂移会让阶段状态算错或标签空白。
 */
export const PROCESS_STATUSES = ['未开启', '进行中', '已完成', '不涉及']

/** 环节问题类型（5 项） */
export const PROCESS_ISSUES = ['审批问题', '资金问题', '权属问题', '管线问题', '地质问题']

/**
 * 附件类型（13 项）。
 * ★ value 是编码（01~13、99）而不是中文：附件类型是文件分类码，
 *   会被拼进存储目录名与导出文件名，必须稳定且与旧系统目录名可对照。
 */
export const ATTACH_TYPES = [
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

/** 建设性质（FacilityImportField.JSXX_OPTIONS） */
export const BUILD_NATURES = ['新建', '改建', '扩建', '翻建', '其他']

/**
 * 道路等级（FacilityImportField.DLDJ_OPTIONS）。
 * ★ 刻意是 3 档：旧库实测还有「快速路」，它既不是主干路也不是次干路，
 *   硬归位会改掉业务事实，所以不放进下拉。
 */
export const ROAD_LEVELS = ['城市主干路', '城市次干路', '城市支路']

/** 资金来源（FacilityImportField.ZJLY_OPTIONS） */
export const FUND_SOURCES = ['土地整理成本', '地块收益', '成本分摊', '区内统筹', '其它']

/** 审批 / 开工状态（三阶段批复与开工状态共用） */
export const PROGRESS_STATES = ['正常推进', '有问题']

/** 资金落实情况 */
export const FUND_STATES = ['已落实', '未落实']

/** 是 / 否（宗地与配套的多个标志位共用） */
export const YES_NO = ['否', '是']

/**
 * 是/否 筛选下拉。
 * ★ 值必须是字符串 —— ScreenSelect 的 value 只接受 String/Number，
 *   用布尔既会触发 prop 类型告警，也会让「否(false)」被当成未选值。
 *   面板在拼查询条件时把它转回布尔（见 toBooleanFlag）。
 */
export const YES_NO_OPTIONS = [
  { value: 'true', label: '是' },
  { value: 'false', label: '否' }
]

/** 「是否有配套」筛选（宗地列表用；语义与 YES_NO_OPTIONS 不同，单独一份避免误用） */
export const HAS_FACILITY_OPTIONS = [
  { value: 'true', label: '有配套' },
  { value: 'false', label: '无配套（孤儿宗地）' }
]

/* ============================ 选项构造 ============================ */

/**
 * 把字符串数组转成 ScreenSelect 需要的 { value, label } 结构
 * @param {string[]} list
 */
export function toOptions (list) {
  return (list || []).map((item) => ({ value: item, label: item }))
}

/**
 * 把后端字典项（{ value, text } 或 { itemValue, itemText }）转成选项。
 *
 * ★ 两种形态都要兼容：jeecg 的 /sys/dict/getDictItems 返回的是
 *   `[{ value, text }]`，而部分旧接口返回 `item_value/item_text`。
 *   只认一种的话，换个接口就静默筛不出数据。
 */
export function dictToOptions (items) {
  return (items || []).map((item) => {
    if (!item) return { value: '', label: '' }
    const value = item.value !== undefined ? item.value : item.itemValue
    const label = item.text !== undefined ? item.text : item.itemText
    return { value: String(value === undefined ? '' : value), label: String(label === undefined ? value : label) }
  })
}

/**
 * 'true' / 'false' 界面值 → 布尔（真布尔才传后端）。
 *
 * ★ 为什么必须转：后端的 hasFacility 是 Boolean，传字符串 'false' 会被
 *   Spring 转成 true（非空字符串），结果是「筛无配套」筛出「有配套」且不报错。
 * @returns {boolean|undefined} 未选择时返回 undefined（条件不下发）
 */
export function toBooleanFlag (value) {
  if (value === 'true' || value === true) return true
  if (value === 'false' || value === false) return false
  return undefined
}

/* ============================ 展示辅助 ============================ */

/**
 * 文件大小格式化（唯一实现，与 archive/constants.js 同一口径）
 * @param {number} bytes
 * @param {number} precision 小数位
 * @returns {string} 例如 '5.7 MB'；空值返回 '—'
 */
export function formatSize (bytes, precision = 1) {
  const size = Number(bytes)
  if (!size || size <= 0) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = size
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value.toFixed(unitIndex === 0 ? 0 : precision)} ${units[unitIndex]}`
}

/**
 * Date → 'YYYY-MM-DD'
 * @param {Date|string} date
 */
export function formatDate (date) {
  if (!date) return ''
  const d = date instanceof Date ? date : new Date(String(date).replace(/-/g, '/'))
  if (Number.isNaN(d.getTime())) return String(date).slice(0, 10)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

/** 今天 'YYYY-MM-DD' */
export function today () {
  return formatDate(new Date())
}

/**
 * 后端返回的 'yyyy-MM-dd HH:mm:ss' → 'yyyy-MM-dd HH:mm'。
 *
 * ★ 为什么不直接用 formatDate：那个函数只取日期部分，而「导入记录 / 变更留痕」
 *   这类列表看的是「哪一次操作」，同一天可能有好几次，丢掉时间就没法区分了。
 */
export function formatTime (value) {
  if (!value) return '—'
  const match = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/.exec(String(value))
  if (match) return `${match[1]} ${match[2]}`
  return formatDate(value) || '—'
}

/**
 * 两个字段拼成一行摘要，都为空时给占位符。
 * 详情页的「创建信息 / 最后更新」用：时间与操作人分属两列，展示时要合成一行。
 */
export function joinInfo (a, b, separator = ' · ') {
  const parts = [a, b].filter((item) => item !== undefined && item !== null && item !== '')
  return parts.length ? parts.join(separator) : '—'
}

/**
 * 环节情况 → ScreenTag 语气。
 * ★ 与后端 ProcessStatus.tagColor 的分档对齐（已完成绿 / 进行中蓝 /
 *   未开启与不涉及灰），并同时给出文字，不依赖颜色单独传达信息。
 */
export function processStatusTone (status) {
  switch (status) {
    case '已完成':
      return 'success'
    case '进行中':
      return 'info'
    case '不涉及':
      return 'muted'
    case '未开启':
      return 'muted'
    default:
      return 'muted'
  }
}

/**
 * 阶段汇总状态 → ScreenTag 语气。
 * 与环节情况同一套颜色，但「未开启」在有事项的语境下稍亮一档，
 * 好让用户一眼区分「整个阶段没开始」与「这个阶段不涉及」。
 */
export function stageStatusTone (status) {
  if (status === '未开启') return 'info'
  return processStatusTone(status)
}

/**
 * 变更动作 → ScreenTag 语气。
 * ★ 后端 actionColor 返回的是 **antd 预设色**（green/blue/red/orange/cyan/purple），
 *   而大屏只有 accent/success/warning/danger/info/muted 六档，不能直接透传；
 *   这里按动作语义映射一次，并保留 actionText 作为文字线索。
 */
export function actionTone (action) {
  switch (action) {
    case 'CREATE':
    case 'IMPORT':
      return 'success'
    case 'UPDATE':
    case 'UPLOAD':
      return 'info'
    case 'DELETE':
    case 'ATTACH_DELETE':
      return 'danger'
    case 'RESTORE':
      return 'warning'
    default:
      return 'muted'
  }
}

/**
 * 预览方式 → 中文文案（后端 previewMode ∈ image / pdf / office / text / download）。
 * ★ 不自己从扩展名猜：扩展名归一化（大写、多个点、无扩展名）与预览方式的对应关系
 *   只应该有一份实现，就在后端的 previewMode 里。
 */
export function previewModeText (mode) {
  switch (mode) {
    case 'image':
      return '图片预览'
    case 'pdf':
      return 'PDF 预览'
    case 'text':
      return '文本预览'
    case 'office':
      return 'Office 文档（需下载）'
    case 'download':
      return '仅支持下载'
    default:
      return '未知格式'
  }
}

/** 该预览方式能否在浏览器里直接打开（否则只能下载） */
export function isPreviewableMode (mode) {
  return mode === 'image' || mode === 'pdf' || mode === 'text'
}

/**
 * 业务类型 → 中文。
 * ★ 与 api/land/dataRecycle.js 的 bizTypeText 是同一个映射：附件/环节/变更留痕
 *   三处都要显示它。放在 constants 里让「纯展示组件」不必为了四个中文字
 *   去依赖接口层（api 层那份保留给服务端口径的注释引用）。
 */
export function bizTypeText (bizType) {
  switch (bizType) {
    case 'land':
      return '经营性用地'
    case 'facility':
      return '配套项目'
    case 'process':
      return '环节进度'
    case 'attachment':
      return '附件'
    default:
      return bizType || '—'
  }
}

/**
 * 上传业务子目录：/{module}/{yyyy}/{MM}
 *
 * 后端按 biz 字段建立分目录，避免所有文件堆在同一层。
 * 数据管理三个业务各用一段路径：宗地用 land、配套用 facility、环节用 process，
 * 不要与档案的 archive / 收文的 receive / 发文的 send 混用
 * （混用会让运维按目录清理时误删别人的文件）。
 *
 * @param {string} module land / facility / process
 * @param {Date} [date]
 */
export function buildBizPath (module = 'land', date = new Date()) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `/${module}/attachment/${date.getFullYear()}/${month}`
}

/** 去掉扩展名，用于默认「文件题名」 */
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

/**
 * 去掉查询条件里的 undefined / null / ''，避免拼出 `xmfl=` 这种空条件。
 *
 * ★ 注意 0 与 false 要保留：`hasFacility=false` 是「只看无配套的宗地」，
 *   被当成空值丢掉就变成「全部」，用户会以为筛选没生效。
 */
export function compactQuery (query) {
  const result = {}
  Object.keys(query || {}).forEach((key) => {
    const value = query[key]
    if (value === undefined || value === null || value === '') return
    if (Array.isArray(value) && !value.length) return
    result[key] = value
  })
  return result
}

/** 分页默认配置（五个面板共用） */
export function defaultPagination (pageSize = 10) {
  return {
    current: 1,
    pageSize,
    total: 0,
    pageSizeOptions: [10, 20, 50, 100]
  }
}

/* ============================ 字典定义 ============================ */

/**
 * 六个业务字典的定义清单。
 *
 * ★ 这里**只声明「要取哪些字典」与「取不到时用什么兜底」，不发请求**：
 *   本文件被纯展示组件（表格、标签）引用，它们只需要 formatSize / formatTime
 *   这类纯函数；把请求放进来会让「引用一个格式化函数」也连带拉起 api 层与
 *   store 的字典缓存。请求归请求（`queryLandDictItems` 在 api/land/landAdmin.js）、
 *   常量归常量，两边各司其职。
 *
 * ★★ 这些字典由 **jeecg Java 后端**提供，所以 `queryLandDictItems` 必须走
 *   `@/api/manageJava`（9802），**不能用 `@/api/manage`** —— 后者走的是中台
 *   `domianURL`（本工程 4548），实测 `GET 4548/sys/dict/getDictItems/…` 返回 404，
 *   而 `GET 9802/api/sys/dict/getDictItems/…` 返回 401（路由存在、需要令牌）。
 *   踩过的表现很隐蔽：因为本模块「字典失败静默」，页面照常渲染，
 *   只是下拉里永远只有下面的兜底常量、中心在后台改字典不生效。
 *
 * ★ 各面板的用法：
 *     queryLandDictItems(dictDefinitions()).then((dicts) => {
 *       if (dicts.xzqh) this.xzqhOptions = dicts.xzqh
 *     })
 *   `dicts` 里只包含**接口成功返回**的键，所以 `if (dicts.xxx)` 这个判断
 *   本身就是「取不到就保留本地兜底」的实现。
 *
 * ★ `fallback` 里的取值与 sql/data/01_data_dict.sql **逐字一致**；
 *   建设性质 / 道路等级 / 资金来源 三个不在字典里（权威定义在
 *   FacilityImportField 的 Java 常量），所以不在这份清单里。
 */
export function dictDefinitions () {
  return [
    { key: 'projectType', code: DICT_CODES.projectType, fallback: toOptions(PROJECT_TYPES) },
    { key: 'xzqh', code: DICT_CODES.xzqh, fallback: toOptions(XZQH_LIST) },
    { key: 'facilityCategory', code: DICT_CODES.facilityCategory, fallback: toOptions(FACILITY_CATEGORIES) },
    { key: 'processStatus', code: DICT_CODES.processStatus, fallback: toOptions(PROCESS_STATUSES) },
    { key: 'processIssue', code: DICT_CODES.processIssue, fallback: toOptions(PROCESS_ISSUES) },
    { key: 'attachType', code: DICT_CODES.attachType, fallback: ATTACH_TYPES }
  ]
}
