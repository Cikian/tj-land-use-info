import { getAction, postAction } from '@/api/manage'
import { compact } from '@/api/land/landAdmin'

/**
 * 数据管理 · 数据更新与移除（软删 / 恢复 / 变更留痕）接口
 * （方案 2.3.1（三）第 6 项）
 *
 * 后端：org.jeecg.modules.land.data.controller.DataRecycleController
 * 权限码：land:data:recycle
 *
 * ★★★ 本模块解决旧系统的两个具体缺口：
 *
 *   1）**没有回收站**。旧系统「移除」是用字符串拼 SQL 把 delFlag 改成 '1'，
 *      误删之后只能去数据库里手工改回来。本模块提供可查、可恢复的回收站。
 *
 *   2）**恢复会撞唯一键，而错误原样甩给用户**。
 *      `t_land` 的唯一键是 `(crzdbh, del_flag)`，所以「同编号最多一条有效
 *      + 一条已删」。用户「移除 A → 重新录入同编号 B → 再想恢复 A」时，
 *      恢复必然撞唯一键。后端已做了冲突检测并给出人话提示，
 *      **前端必须把这条提示完整展示出来**（不要用统一的「恢复失败」盖掉）——
 *      否则用户只会看到「恢复失败」而不知道要先处理那条占用编号的记录。
 *
 *      配套项目**没有唯一键**（旧表就没有），所以同名恢复不会失败，
 *      只会返回 `warning` 字段；前端应把它当**提示**而不是错误弹。
 *
 * ★ 变更留痕的形态：后端返回 `details` 数组（已解析好的 JSON），
 *   每项是 `{field, label, before, after}`。前端直接渲染表格即可，
 *   不需要自己 parse `changeDetail` 字符串。
 */

export const dataRecycleUrl = {
  list: '/land/data/recycle/list',
  summary: '/land/data/recycle/summary',
  restore: '/land/data/recycle/restore',
  restoreBatch: '/land/data/recycle/restoreBatch',
  changeLog: '/land/data/recycle/changeLog',
  history: '/land/data/recycle/history',
  actionDistribution: '/land/data/recycle/actionDistribution',
  actionOptions: '/land/data/recycle/actionOptions'
}

/* ==========================================================================
 * 一、回收站
 * ========================================================================== */

/**
 * 回收站列表（宗地 + 配套合并返回）。
 *
 * @param {string} bizType land / facility / 空(全部)
 * @param {string} keyword 宗地编号 / 名称 模糊
 * @returns result: [{bizType,id,bizKey,name,xzqh,xmfl,lrdw,lrr,projectName,updateTime}]
 */
export function queryRecycleList (bizType, keyword) {
  return getAction(dataRecycleUrl.list, compact({ bizType, keyword }))
}

/** 回收站计数（页面顶部卡片） */
export function queryRecycleSummary () {
  return getAction(dataRecycleUrl.summary, {})
}

/* ==========================================================================
 * 二、恢复
 * ========================================================================== */

/**
 * 恢复一条数据。
 *
 * @returns result: {success, bizType, id, bizKey, message, warning?}
 *          ★ success=true 且带 warning 时：恢复成功但有提示（配套同名），
 *            前端应弹 info/warning 而不是 error。
 *          ★ 冲突时后端返回 success=false + message（说明被哪条记录占用），
 *            前端应原样展示 message，不要替换成「恢复失败」。
 */
export function restoreData (bizType, id) {
  return postAction(`${dataRecycleUrl.restore}?bizType=${encodeURIComponent(bizType)}&id=${encodeURIComponent(id)}`)
}

/** 批量恢复（逐个恢复并返回失败明细） */
export function restoreBatch (bizType, ids) {
  const value = Array.isArray(ids) ? ids.join(',') : ids
  return postAction(`${dataRecycleUrl.restoreBatch}?bizType=${encodeURIComponent(bizType)}&ids=${encodeURIComponent(value)}`)
}

/* ==========================================================================
 * 三、变更留痕
 * ========================================================================== */

/**
 * 分页查询变更留痕。
 *
 * @param {object} params bizType,action,operator,keyword,pageNo,pageSize
 * @returns result: {records, total, pageNo, pageSize, pages}
 */
export function queryChangeLogPage (params) {
  return getAction(dataRecycleUrl.changeLog, compact(params))
}

/**
 * 某业务对象的完整履历。
 *
 * @param {string} bizType land / facility / process / attachment
 * @param {string} bizId
 */
export function queryHistory (bizType, bizId, limit) {
  return getAction(dataRecycleUrl.history, compact({ bizType, bizId, limit }))
}

/** 变更动作分布（动作计数卡） */
export function queryActionDistribution (bizType) {
  return getAction(dataRecycleUrl.actionDistribution, compact({ bizType }))
}

/** 动作下拉（含中文名，后端与 t_data_change_log.action 同源） */
export function queryActionOptions () {
  return getAction(dataRecycleUrl.actionOptions, {})
}

/* ==========================================================================
 * 四、展示辅助（唯一实现，页面与导出共用）
 *
 * ★ 为什么放在 api 层而不是各页面里：
 *   动作配色与文案在「回收站」「变更留痕」「宗地履历弹窗」「配套履历弹窗」
 *   至少四处要用；各写一份必然漂移（典型症状：同一个动作在两个页面颜色不同，
 *   用户以为是两种状态）。
 * ========================================================================== */

/** 业务类型下拉（空值=全部，供筛选用） */
export const BIZ_TYPE_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'land', label: '经营性用地' },
  { value: 'facility', label: '配套项目' },
  { value: 'process', label: '环节进度' },
  { value: 'attachment', label: '附件' }
]

/**
 * 业务类型下拉（不含「全部」，供表单必选场景用）。
 *
 * ★ 单独给一份而不是让调用方 filter 掉空值：
 *   表单里第一项是「全部」会让人以为可以不选，结果提交一个空 bizType
 *   被后端拒绝 —— 这类「下拉第一项是空」的坑在录入表单里最常见。
 */
export const BIZ_TYPE_REQUIRED_OPTIONS = [
  { value: 'land', label: '经营性用地' },
  { value: 'facility', label: '配套项目' },
  { value: 'process', label: '环节进度' }
]

/**
 * 动作 → a-tag 颜色。
 *
 * ★ 绝不能返回 'default'：它不是 antd 预设色，会被当成自定义色 →
 *   白字 + 非法背景被丢弃 → 白底白字看不见（档案模块踩过，见实现说明 7.5）。
 *   无强调色就返回 undefined（默认灰底）。
 */
export function actionColor (action) {
  switch (action) {
    case 'CREATE':
      return 'green'
    case 'UPDATE':
      return 'blue'
    case 'DELETE':
      return 'red'
    case 'RESTORE':
      return 'orange'
    case 'IMPORT':
      return 'cyan'
    case 'UPLOAD':
      return 'purple'
    default:
      return undefined
  }
}

/** 动作 → 中文（后端也会给 actionText，这里做兜底与图标场景使用） */
export function actionText (action) {
  switch (action) {
    case 'CREATE':
      return '新增'
    case 'UPDATE':
      return '修改'
    case 'DELETE':
      return '移除'
    case 'RESTORE':
      return '恢复'
    case 'IMPORT':
      return '批量导入'
    case 'UPLOAD':
      return '附件上传'
    case 'ATTACH_DELETE':
      return '附件删除'
    default:
      return action || '变更'
  }
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
    case 'attachment':
      return '附件'
    default:
      return bizType || '—'
  }
}

/** 业务类型 → 标签色 */
export function bizTypeColor (bizType) {
  switch (bizType) {
    case 'land':
      return 'blue'
    case 'facility':
      return 'purple'
    case 'process':
      return 'cyan'
    default:
      return undefined
  }
}

/** 动作下拉的本地兜底（接口不可用时用） */
export const ACTION_OPTIONS_FALLBACK = [
  { value: 'CREATE', label: '新增' },
  { value: 'UPDATE', label: '修改' },
  { value: 'DELETE', label: '移除' },
  { value: 'RESTORE', label: '恢复' },
  { value: 'IMPORT', label: '批量导入' },
  { value: 'UPLOAD', label: '附件上传' },
  { value: 'ATTACH_DELETE', label: '附件删除' }
]

export default {
  dataRecycleUrl,
  queryRecycleList,
  queryRecycleSummary,
  restoreData,
  restoreBatch,
  queryChangeLogPage,
  queryHistory,
  queryActionDistribution,
  queryActionOptions,
  BIZ_TYPE_OPTIONS,
  BIZ_TYPE_REQUIRED_OPTIONS,
  actionColor,
  actionText,
  bizTypeText,
  bizTypeColor,
  ACTION_OPTIONS_FALLBACK
}
