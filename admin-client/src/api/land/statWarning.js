import { getAction, postAction, deleteAction } from '@/api/manage'

/**
 * 统计预警 · 预警等级 / 决策意见维护 接口（方案 2.3.1（五）· 差异清单 D-603）
 *
 * 对应后端三个 Controller（契约 docs/分级预警-实现契约.md §5.6 / §5.7 / §5.3）：
 *   org.jeecg.modules.land.stat.warning.controller.WarningLevelController     → /land/stat/warning/level
 *   org.jeecg.modules.land.stat.warning.controller.DecisionOpinionController  → /land/stat/warning/opinion
 *   org.jeecg.modules.land.stat.warning.controller.WarningController          → /land/stat/warning/options
 *
 * 权限码（**只拦写操作**）：land:stat:warningLevel:edit / land:stat:decisionOpinion:edit。
 * 查询类接口刻意不加权限：两张表都是页面首屏数据，拦了页面直接白屏（契约 §5.7 末段）。
 *
 * 约定：GET 用 query string（删除用 ?id=），写操作统一 JSON body，返回体是 jeecg 的 Result<T>。
 * 注意：这里**不能**用 landAdmin 的 compact 去掉空参数 —— 决策意见的 problemType 用**空串**
 * 表示「该等级的通用兜底意见」，把空串过滤掉就变成了「不过滤」，语义完全不同。
 */
export const statWarningUrl = {
  levelList: '/land/stat/warning/level/list',
  levelSave: '/land/stat/warning/level/save',
  levelDelete: '/land/stat/warning/level/delete',
  opinionList: '/land/stat/warning/opinion/list',
  opinionSave: '/land/stat/warning/opinion/save',
  opinionDelete: '/land/stat/warning/opinion/delete',
  options: '/land/stat/warning/options'
}

/**
 * 预警等级列表（按 sortNo 升序，已过滤 del_flag=1）
 * 返回 Result<List<WarningLevelVO>>，不是分页 —— 等级是配置项，正常只有 4 条。
 */
export function listWarningLevel () {
  return getAction(statWarningUrl.levelList, {})
}

/**
 * 新增 / 编辑预警等级（id 有值=编辑，空=新增）
 * @param {{id?: string, levelName: string, levelCode: string, minDays: number, maxDays: number, sortNo?: number, remark?: string}} data
 * 后端 Result.error 的 message 是给用户看的中文原因（别名重复 / 区间重叠等），页面原样提示。
 */
export function saveWarningLevel (data) {
  return postAction(statWarningUrl.levelSave, data)
}

/** 删除（软删）预警等级 */
export function deleteWarningLevel (id) {
  return deleteAction(statWarningUrl.levelDelete, { id })
}

/**
 * 决策意见分页列表
 * @param {{levelId?: string, problemType?: string, pageNo?: number, pageSize?: number}} params
 *        problemType 传空串 = 只看通用兜底意见（不传 = 全部）
 * 返回 Result<IPage<DecisionOpinionVO>>（records / total / size / current）
 */
export function listDecisionOpinion (params) {
  return getAction(statWarningUrl.opinionList, params)
}

/**
 * 新增 / 编辑决策意见（id 有值=编辑）
 * @param {{id?: string, levelId: string, problemType: string, opinion: string}} data
 * 同一 (problemType, levelId) 只允许一条，重复时后端返回「已存在，请直接编辑」。
 */
export function saveDecisionOpinion (data) {
  return postAction(statWarningUrl.opinionSave, data)
}

/** 删除（软删）决策意见 */
export function deleteDecisionOpinion (id) {
  return deleteAction(statWarningUrl.opinionDelete, { id })
}

/**
 * 下拉选项：等级 / 预警分类 / 问题类型
 * 返回 Result<{levels: [], yjTypes: [], problemTypes: []}>
 * problemTypes 来自字典 land_process_issue（查不到时后端回落 5 项常量），
 * 所以前端**不要**自己写死类型清单，否则字典改了页面不跟。
 */
export function warningOptions () {
  return getAction(statWarningUrl.options, {})
}

export default {
  statWarningUrl,
  listWarningLevel,
  saveWarningLevel,
  deleteWarningLevel,
  listDecisionOpinion,
  saveDecisionOpinion,
  deleteDecisionOpinion,
  warningOptions
}
