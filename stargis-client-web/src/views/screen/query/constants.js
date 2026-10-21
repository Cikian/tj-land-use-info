/**
 * 查询统计 · 页面常量
 * ===============================================================
 * 只放「不随接口变化」的东西：
 *   · 三个页签（经营性用地查询 / 用地配套项目查询 / 查询导出）；
 *   · 「分析结果」指标的展示顺序与单位（与高保真 `查询统计.html` 一致）；
 *   · 若干枚举下拉的候选值（是/否、批复状态、项目分类）。
 *
 * ★ 字段清单**不**在这里：它来自后端 `/land/stat/query/columns`。
 *   后端的 `QueryFieldRegistry` 是唯一来源，前端再抄一份就会出现
 *   「下拉里有、实际查不到」这种最难排查的漂移。
 */

/** 三个页签（与 views/screen/query/index.vue 的 activeTab 对应） */
export const QUERY_TABS = [
  { key: 'land', label: '经营性用地查询' },
  { key: 'facility', label: '用地配套项目查询' },
  { key: 'advanced', label: '查询导出' }
]

/** 项目分类（旧系统 getCnd 的 xmfl 取值） */
export const PROJECT_TYPE_OPTIONS = [
  { value: '市级项目', label: '市级项目' },
  { value: '区级项目', label: '区级项目' }
]

/** 是 / 否 */
export const YES_NO_OPTIONS = [
  { value: '是', label: '是' },
  { value: '否', label: '否' }
]

/** 批复完成情况（旧系统里「不涉及」是合法值，不能只给是/否） */
export const APPROVAL_OPTIONS = [
  { value: '是', label: '是' },
  { value: '否', label: '否' },
  { value: '不涉及', label: '不涉及' }
]

/** 建设性质（旧配套数据里的常见取值；「现状/不涉及」会被统计口径排除） */
export const BUILD_NATURE_OPTIONS = [
  { value: '新建', label: '新建' },
  { value: '改扩建', label: '改扩建' },
  { value: '现状', label: '现状' },
  { value: '不涉及', label: '不涉及' }
]

/**
 * 经营性用地「分析结果」指标。
 *
 * 顺序 = 旧面板 `queryImplementationSituation.html` 的字段顺序
 * （地块总数 / 配套总数 / 新建道路 / 配套费总额 / 已立项 / 立项率 / 已开工 / 开工率 /
 *   已竣工 / 竣工率），高保真在此基础上补了「配套项目名称为空」。
 *
 * `percent: true` 的项后端返回的是百分数（19.7 表示 19.7%），前端只负责拼 `%`。
 */
export const LAND_METRICS = [
  { key: 'landTotal', label: '地块总数', unit: '宗' },
  { key: 'facilityTotal', label: '配套总数', unit: '个' },
  { key: 'facilityNameBlank', label: '配套项目名称为空', unit: '条' },
  { key: 'newRoadCount', label: '新建道路', unit: '条' },
  { key: 'supportingFeeTotal', label: '配套费总额', unit: '万元', precision: 3 },
  { key: 'approvedCount', label: '已立项', unit: '个' },
  { key: 'approvedRate', label: '立项率', percent: true },
  { key: 'startedCount', label: '已开工', unit: '个' },
  { key: 'startedRate', label: '开工率', percent: true },
  { key: 'completedCount', label: '已竣工', unit: '个' },
  { key: 'completedRate', label: '竣工率', percent: true }
]

/**
 * 用地配套项目「分析结果」指标。
 *
 * 与上面的**不是同一套口径**（见后端 StatSummaryVO 的类注释）：
 * 这里统计对象是配套项目本身，不去重、不漏斗，每个环节各算各的。
 */
export const FACILITY_METRICS = [
  { key: 'landTotal', label: '涉及宗地数', unit: '宗' },
  { key: 'facilityTotal', label: '配套项目总数', unit: '个' },
  { key: 'facilityNameBlank', label: '配套项目名称为空', unit: '条' },
  { key: 'newRoadCount', label: '新建道路', unit: '条' },
  { key: 'approvedCount', label: '已立项', unit: '个' },
  { key: 'approvedRate', label: '立项率', percent: true },
  { key: 'startedCount', label: '已开工', unit: '个' },
  { key: 'startedRate', label: '开工率', percent: true },
  { key: 'completedCount', label: '已竣工', unit: '个' },
  { key: 'completedRate', label: '竣工率', percent: true },
  { key: 'handedOverCount', label: '已移交', unit: '个' },
  { key: 'handedOverRate', label: '移交率', percent: true }
]

/** 按数据集取指标定义 */
export function metricsOf (dataType) {
  return dataType === 'facility' ? FACILITY_METRICS : LAND_METRICS
}

/** 分页可选条数 */
export const PAGE_SIZE_OPTIONS = [20, 50, 100]
