import {
  javaDeleteAction,
  javaGetAction,
  javaPostAction,
  javaPutAction
} from '@/api/manageJava'

/**
 * 数据管理 · 经营性用地信息录入（逐条）接口（方案 2.3.1（三）第 1 项）
 * ===============================================================
 * 后端：org.jeecg.modules.land.data.controller.LandAdminController
 * 权限码：land:data:land（一个码管全部读写，菜单能不能打开才是真正的边界）
 *
 * 【为什么用 manageJava 而不是 @/api/manage】
 * 本工程有**两个后端**（见 public/static/config.js）：
 *   VUE_APP_API_BASE_URL = http://127.0.0.1:4548      中台（网关）
 *   VUE_DATA_JAVA_URL    = http://127.0.0.1:9802/api  Java 业务后端
 * 而 `@/api/manage` 走的是 `domianURL`，它在 src/config/index.js 里被赋成
 * **中台**地址。所以凡是要打在 Java 后端的请求，都必须走 `@/api/manageJava`
 * （它显式用 VUE_DATA_JAVA_URL），否则会打到中台 4548 上 404。
 *
 * ★★ `/sys/dict/getDictItems/**` 也必须走 manageJava
 *   这是本项目踩过的一个坑：jeecg 的字典接口是 **Java 后端**提供的
 *   （`org.jeecg.modules.system.controller.SysDictController#getDictItems`），
 *   不在中台上。早期这里误用了 `@/api/manage` 的 `getAction`，
 *   结果字典请求打到 http://127.0.0.1:4548/sys/dict/getDictItems/...，
 *   下拉框取不到值 —— 而且因为「失败静默」的设计，
 *   表现是「下拉里只剩本地兜底常量、后台改字典不生效」这种不报错的软故障。
 *
 *   注意：`window._CONFIG['staticDomainURL']` 也由中台地址拼成
 *   （config/index.js 第 9 行），所以大屏端的**附件静态访问**同样不能用它 ——
 *   附件走 `getJavaFileAccessHttpUrl()`（在 manageJava 里）。
 *
 *   另外 `@/api/manage` 会给每个请求塞 `parameter.access_token`，
 *   触发 request.js 多打一次中台探针接口；manageJava 只带 X-Access-Token 请求头。
 *   详见 src/api/manageJava.js 的文件头注释。
 *
 * 【与 admin-client 的关系】
 * 与 admin-client/src/api/land/landAdmin.js 一一对应、共用同一套后端：
 * 接口路径、参数名、取值口径必须保持同步，否则会出现
 * 「一边能存、另一边筛不出来」的契约漂移。
 * ★ 但两端的 `domianURL` 指向不同：admin-client 的 VUE_APP_API_BASE_URL
 *   是空串（退化为 9802 的 Java 后端），大屏端是 4548 的中台 ——
 *   所以同一份代码在大屏端必须显式走 manageJava。
 *
 * 关键约定：
 *  1. ★ 列表条件是后端显式声明的 `LandAdminQueryDTO`，**不是 jeecg 的
 *     QueryGenerator**：传它没声明的字段名不会被拼进 SQL，也不会报错，
 *     只是静默不生效 —— 所以下面的函数签名里把这些字段逐一列了出来；
 *  2. ★ 模糊条件（crzdbh/dkmc/ghydxz/srr）传纯文本即可，后端自己拼 LIKE。
 *     早期原型（data/LandList.vue）按 jeecg 的 `*xx*` 写法传，只会匹配到
 *     字面星号，表现为「搜什么都搜不到」；
 *  3. ★ hasFacility 是**真布尔**：true=只看有配套的宗地，false=只看没配套
 *     的「孤儿宗地」。ScreenSelect 只吃 String/Number，界面用 'true'/'false'，
 *     由面板转回布尔再调这里；
 *  4. /delete、/deleteBatch 的 id 与 ids 都是 query 参数（@RequestParam），
 *     不是 JSON body，javaDeleteAction 正好把参数拼在 query 上。
 */

export const landAdminUrl = {
  list: '/land/data/landAdmin/list',
  detail: '/land/data/landAdmin/detail',
  history: '/land/data/landAdmin/history',
  codeOptions: '/land/data/landAdmin/codeOptions',
  checkCrzdbh: '/land/data/landAdmin/checkCrzdbh',
  add: '/land/data/landAdmin/add',
  edit: '/land/data/landAdmin/edit',
  delete: '/land/data/landAdmin/delete',
  deleteBatch: '/land/data/landAdmin/deleteBatch'
}

/* ==========================================================================
 * 一、查询（5 个）
 * ========================================================================== */

/**
 * 分页列表。
 *
 * @param {object} params LandAdminQueryDTO：
 *   crzdbh / dkmc / xzqh / xmfl / ghydxz / srr（文本，前四个与 xmfl 为精确或模糊见后端）
 *   crsjBegin / crsjEnd（yyyy-MM-dd）
 *   ptsfqq（是 / 否）
 *   hasFacility（Boolean）
 *   facilityKeyword（跨表到配套表：按配套名称/编号反查宗地）
 *   orderBy（白名单：create_time / crzdbh / dkmc / xzqh / crsj / crj / update_time）
 *   asc（Boolean）
 *   pageNo / pageSize（pageSize 后端封顶 500）
 * @returns {Promise} result 为 IPage<Land>：{ records, total, current, size }
 */
export function queryLandAdminPage (params) {
  return javaGetAction(landAdminUrl.list, params)
}

/**
 * 宗地详情。
 *
 * ★ 后端返回的就是 `t_land` 实体本身（`getById`），**不含** facilityCount /
 *   attachmentCount / changeCount 这类聚合值 —— 需要时由页面另行调用
 *   `/land/data/facilityAdmin/byLand`、`/land/data/attachment/summary`、
 *   `/land/data/recycle/history` 取。不要在前端假造这些数字。
 *
 * @param {string} id
 */
export function queryLandAdminById (id) {
  return javaGetAction(landAdminUrl.detail, { id })
}

/**
 * 变更履历（字段级）。
 *
 * @param {string} id 宗地主键
 * @param {number} [limit] 默认 50，后端封顶 200
 * @returns {Promise} result 为数组，每项：
 *   { id, action, actionText, actionColor, summary, changeCount,
 *     details: [{ field, label, before, after }], operator, operatorName, createTime }
 */
export function queryLandHistory (id, limit) {
  return javaGetAction(landAdminUrl.history, limit ? { id, limit } : { id })
}

/**
 * 宗地编号下拉（表单联动与跨模块选择共用）。
 *
 * @param {{keyword?: string, limit?: number}} params limit 默认 50，封顶 300
 * @returns {Promise} result 为 [{ id, value, label, crzdbh, dkmc, xzqh, xmfl }]
 *   注意 value 就是 crzdbh（不是 id），label 已拼好「编号（地块名称）」
 */
export function queryLandCodeOptions (params) {
  return javaGetAction(landAdminUrl.codeOptions, params || {})
}

/**
 * 出让宗地编号唯一性校验（表单失焦时实时调）。
 *
 * @param {string} crzdbh
 * @param {string} [excludeId] 编辑时传自身 id，排除自己
 * @returns {Promise} result 为 { available, message, occupiedBy?, occupiedName? }
 *   ★ 即使 available=true，message 里也可能提示「回收站里有一条同编号的历史记录」，
 *     这种提示要透给用户看，不要只判断 available。
 */
export function checkCrzdbh (crzdbh, excludeId) {
  return javaGetAction(
    landAdminUrl.checkCrzdbh,
    excludeId ? { crzdbh, excludeId } : { crzdbh }
  )
}

/**
 * 一次性取本项目数据管理要用的 6 个业务字典选项，供下拉直接使用。
 *
 * ★ 为什么放在 api 层而不是 views/screen/data/constants.js：
 *   constants.js 被「纯展示组件」（表格、标签）引用，那些组件只需要
 *   formatSize / formatTime 这类纯函数。若在那里 import 字典请求，
 *   每次引用一个格式化函数都会连带拉起 api 层与 store 的字典缓存。
 *   请求归请求、常量归常量，两边各司其职。
 *
 * ★ 为什么用 `javaGetAction`（Java 后端通道）而不是 `@/api/manage` 的 getAction：
 *   `/sys/dict/getDictItems/**` 由 **jeecg Java 后端**提供，路径本身虽然不在
 *   `/land/**` 命名空间下，但后端是同一个（9802/api）。
 *   而 `@/api/manage` 走的是中台 `domianURL`（本工程为 4548），
 *   打在那边会 404 —— 这就是本文件修正前的实际 bug：
 *   请求发到 http://127.0.0.1:4548/sys/dict/getDictItems/land_facility_category。
 *   判据很简单：**只要一个接口是 jeecg（Spring Boot）提供的，就走 manageJava**；
 *   只有中台自己的接口（/app/**、场景/图层等）才走 @/api/manage。
 *
 * ★ 为什么失败要静默（resolve({}) 而不是 reject）：
 *   字典只决定「下拉里有哪些取值」。它挂了不该让整个录入页面打不开 ——
 *   各页面都有与 sql/data/01_data_dict.sql 逐字一致的本地兜底常量。
 *
 * @param {Array<{key: string, code: string}>} definitions 见 constants 的 dictDefinitions()
 * @returns {Promise<object>} { [key]: [{ value, label }] }，未取到的键不出现在结果里
 */
export function queryLandDictItems (definitions) {
  const list = definitions || []
  if (!list.length) {
    return Promise.resolve({})
  }
  const tasks = list.map((definition) =>
    javaGetAction(`/sys/dict/getDictItems/${definition.code}`, {})
      .then((res) => {
        if (!res || !res.success) return null
        const options = dictItemsToOptions(res.result)
        return options.length ? { key: definition.key, options } : null
      })
      .catch(() => null)
  )
  return Promise.all(tasks).then((results) => {
    const merged = {}
    results.forEach((item) => {
      if (item) merged[item.key] = item.options
    })
    return merged
  })
}

/**
 * jeecg 字典项 → 选项。
 *
 * ★ 兼容两种形态：`/sys/dict/getDictItems` 返回 `[{ value, text }]`，
 *   而部分老接口返回 `item_value / item_text`。只认一种的话，
 *   换个接口就静默筛不出数据（下拉空白、查询无结果）。
 */
function dictItemsToOptions (items) {
  return (items || []).map((item) => {
    if (!item) return { value: '', label: '' }
    const value = item.value !== undefined ? item.value : item.itemValue
    const label = item.text !== undefined ? item.text : item.itemText
    return {
      value: String(value === undefined || value === null ? '' : value),
      label: String(label === undefined || label === null ? value : label)
    }
  })
}

/* ==========================================================================
 * 二、写入（4 个）
 * ========================================================================== */

/**
 * 新增宗地。
 *
 * @param {object} data LandSaveDTO：Land 的全部 34 个业务字段 + removeReason。
 *   ★ 后端 Service **显式逐字段赋值**，客户端塞 id / delFlag / createTime 一律被忽略
 *     （防越权写入），所以前端不必也不该传审计字段。
 *   ★ 服务端强校验：crzdbh 非空且唯一、xmfl ∈ {市级项目, 区级项目}、
 *     lxdh 命中 ^1[3456789]\d{9}$。前端做同样的校验只为少一次往返。
 *   ★ 数值字段后端是 BigDecimal，空串会解析失败 —— 空值必须传 null。
 * @returns {Promise} result 为新建宗地的 id
 */
export function addLandAdmin (data) {
  return javaPostAction(landAdminUrl.add, data)
}

/** 编辑宗地（后端接受 PUT 与 POST，这里按规格用 PUT） */
export function editLandAdmin (data) {
  return javaPutAction(landAdminUrl.edit, data)
}

/**
 * 移除宗地（软删，可在「数据更新与移除」恢复）。
 *
 * @param {string} id
 * @param {string} [reason] 移除原因，会写进变更留痕的摘要
 */
export function deleteLandAdmin (id, reason) {
  return javaDeleteAction(
    landAdminUrl.delete,
    reason ? { id, reason } : { id }
  )
}

/**
 * 批量移除宗地（后端逐条软删 + 逐条留痕）。
 *
 * @param {string[]|string} ids
 * @param {string} [reason]
 * @returns {Promise} result 为 { successCount, failCount, failures: [{ id, reason }] }
 *   ★ 失败明细要展示给用户，只报「成功 N 条」会让失败的记录没有交代。
 */
export function deleteLandAdminBatch (ids, reason) {
  const params = { ids: Array.isArray(ids) ? ids.join(',') : ids }
  if (reason) {
    params.reason = reason
  }
  return javaDeleteAction(landAdminUrl.deleteBatch, params)
}

export default {
  landAdminUrl,
  queryLandAdminPage,
  queryLandAdminById,
  queryLandHistory,
  queryLandCodeOptions,
  checkCrzdbh,
  queryLandDictItems,
  addLandAdmin,
  editLandAdmin,
  deleteLandAdmin,
  deleteLandAdminBatch
}
