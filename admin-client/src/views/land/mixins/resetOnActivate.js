/**
 * resetOnActivate —— 普通离开再进入模块时「清空检索条件 + 页码回 1 + 重新取数」。
 *
 * 对应《升级改造工作内容清单》P2-01 在**管理端**的那一半。
 *
 * 一、为什么需要它
 *   管理端路由缓存由菜单的「是否缓存」控制（`sys_permission.keep_alive` →
 *   `components/layouts/RouteView.vue` / `TabLayout.vue` 的 `<keep-alive :include>`），
 *   而线上 21 个 land 菜单**全部 keep_alive=1**。于是切到别的菜单再切回来时，
 *   组件实例没有被销毁，`created` 不会重跑，`data()` 里的初始条件也不会重新赋一遍 ——
 *   上一轮输入的检索条件、停在第 3 页的页码、旧结果全都还在。
 *   用户看到的是「一进来就只有这些数据」，却看不出是被旧条件筛过的，这就是清单说的
 *   「旧条件成为隐性过滤」。所以缓存本身要保留（页签/展开态/滚动位置这些状态是有价值的），
 *   但要**在每次重新进入时把「检索条件 + 页码 + 结果」复位并重查**。
 *
 * 二、契约（谁调用、何时清、为什么下钻不清）
 *   1. 谁调用：本 mixin 自己挂 `activated` 钩子，页面只要 `mixins: [resetOnActivate]`
 *      并实现 `resetSearch()` 即可，不需要再写 `activated`。
 *   2. 何时清（三种情况）：
 *      · **首次挂载不清**：`activated` 在首次进入时也会紧跟着 `mounted` 触发一次，
 *        而页面已经在 `created` / `mounted` 里取过数了。此时清条件再查 = 白跑一遍请求
 *        （还会把用户从「带条件下钻」刚拿到的条件抹掉），所以第一次 `activated` 只记标记、直接 return。
 *      · **带业务下钻不清**：`$route.query` 里出现下钻键（见下面 DRILL_QUERY_KEYS）时，
 *        说明用户是「从别处点进来定位某一条数据 / 某一批数据」的，这些条件是**别人注入的上下文**，
 *        抹掉就等于把用户带丢了。这时只重查（刷新数据），不动条件。
 *      · **普通返回（既非首次、也非下钻）**：调用页面的 `resetSearch()` ——
 *        条件恢复初始值、页码回 1、清掉选中/展开行，然后重新取数。
 *   3. 为什么只认下钻键名、而不是「query 非空就算下钻」：
 *      query 里混着控制参数（`tab` / `menu` / `token` 等），它们与业务无关；
 *      而「凡 query 非空即视为下钻」会让任何一次带参数的跳转都把旧条件永久保留下来，
 *      P2-01 要修的隐性过滤就原地复活。所以下钻键名是**白名单**，新增下钻入口时
 *      要在 DRILL_QUERY_KEYS 里登记（口径与本仓库 `scripts/verify-sfc.js` 的硬编码清单一致：
 *      宁可多一步登记，也不要靠猜键名）。
 *   4. `resetSearch()` 由页面实现，**允许它内部就是一次取数**（多数页面是委托给查询表单的
 *      `handleReset()`，表单清完自己 emit('search')，父页面 handleSearch 里已经带了页码复位与取数）。
 *      所以 mixin 在普通分支里**不再额外取数**，否则这里会连发两次请求。
 *      页面没有实现 `resetSearch()` 时安全跳过（不报错），退化成「只重查」。
 *
 * 三、与缓存的关系
 *   本 mixin **不**要求关缓存、也不动 `sys_permission.keep_alive`：
 *   页签、检索面板展开态、视图模式（项目视图/平铺列表）这些「不是检索条件」的状态照旧保留，
 *   保留它们正是缓存的价值。
 */
const DRILL_QUERY_KEYS = [
  /*
   * 主键 / 业务标识型：本身不是表单可见字段，出现即代表「从别处定位到这条数据」。
   * 依据（本工程实际在用的下钻入口）：
   *   · archiveId —— 收发文列表「归档」列 → 档案维护
   *     （DocReceiveList.vue / DocSendList.vue 的 `$router.push({ path: '/land/archive/list', query: { archiveId } })`，
   *      ArchiveList.vue 的 created 读它并按 id 打开详情）
   *   · facilityId / archiveId / crzdbh / status —— api/land/land/ledger.js 的 ledgerPageUrl 注释里的四种下钻参数
   *     （档案、配套项目 → 道路验收台账）
   *   · archiveNo / ptxmmc —— api/land/ledger.js 的 archiveQueryUrl（台账 → 档案查询）
   *   · ptxmmc / crzdbh —— ArchiveStatistics.vue 的 goQuery（档案统计按项目 → 档案查询）
   */
  'archiveId',
  'archiveNo',
  'facilityId',
  'crzdbh',
  'ptxmmc',
  /*
   * 条件型下钻：图表/卡片点进来时带的筛选条件（条件在表单上可见，抹掉就等于换了一份数据）。
   *   · argResult / status / declareDept / xzqh / projectType / beginDeclareDate / endDeclareDate
   *     —— EscalationStatCharts.vue 的 drill(...) 与 EscalationQuery.vue 的 drill 手法（提级论证查询 → 台账）
   *   · digitizeStatus —— CompletionArchiveList.vue created 注释里的下钻链接
   *   · missingMaterial —— RoadAcceptanceLedger.vue created 注释里的下钻链接
   *   · hasArchive —— 台账/竣工档案的「已挂扫描件 / 仅看未关联档案」下钻
   */
  'argResult',
  'status',
  'declareDept',
  'xzqh',
  'projectType',
  'beginDeclareDate',
  'endDeclareDate',
  'digitizeStatus',
  'missingMaterial',
  'hasArchive'
]

/**
 * 控制参数：与业务无关，**永远不算下钻**。
 * `tab` / `menu` / `token` 是清单里点名的；`ticke` 是 TabLayout 关闭页签判定里出现的旧参数，
 * `redirect` / `_t` 是登录回跳与防缓存时间戳的老习惯，一并排除，避免它们把「普通返回」误判成下钻。
 */
const CONTROL_QUERY_KEYS = ['tab', 'menu', 'token', 'ticke', 'redirect', '_t']

/**
 * 取数方法名兜底顺序。
 *
 * 为什么要有兜底：这几个页面历史上命名不统一 —— 大多数是 `loadData()`，
 * 但 RoadHandoverList / RoadAcceptanceLedger / CompletionArchiveList 的完整刷新叫 `refresh()`
 * （= loadData + 状态页签计数 + 指标卡，是本页口径的「全量刷新」），
 * ArchiveStatistics 叫 `loadStat()`，ArchiveCategoryList 叫 `loadTree()`，
 * EscalationAudit 叫 `loadProjects()`。
 *
 * ★ `refresh` 排第一：它是 `loadData` 的超集，下钻分支用它可以避免
 *   「列表刷新了、页签计数与指标卡还是上一轮的」这种自相矛盾。
 * ★ 页面若实现了 `resetSearch()`，普通分支走不到这里（resetSearch 自己取数）；
 *   只有「下钻 / 页面没实现 resetSearch」这两条分支需要它。
 */
const DEFAULT_RELOAD_METHODS = ['refresh', 'loadData', 'loadStat', 'loadProjects', 'loadTree']

/** 空值（空串 / null / undefined）不算「有下钻条件」 */
function isFilled (value) {
  if (value === undefined || value === null || value === '') {
    return false
  }
  return !(Array.isArray(value) && !value.length)
}

/** 从路由 query 里挑出下钻键（控制参数一律排除） */
export function pickDrillQuery (routeQuery) {
  const result = {}
  Object.keys(routeQuery || {}).forEach(key => {
    if (CONTROL_QUERY_KEYS.indexOf(key) !== -1) {
      return
    }
    if (DRILL_QUERY_KEYS.indexOf(key) === -1) {
      return
    }
    if (!isFilled(routeQuery[key])) {
      return
    }
    result[key] = routeQuery[key]
  })
  return result
}

/** 当前路由是否带业务下钻参数（导出给需要的页面/自检脚本复用） */
export function hasDrillQuery (routeQuery) {
  return Object.keys(pickDrillQuery(routeQuery)).length > 0
}

export default {
  created () {
    /*
     * 用普通实例属性而不是 data：它不参与渲染，也不需要响应式；
     * 放 data 里只会多一个无意义的 watcher，还可能被 devtools 当成业务数据。
     */
    this.resetOnActivateEntered = false
  },
  activated () {
    // ① 首次挂载：created / mounted 已经取过数（也可能已经带着下钻条件取过数），这里什么都不做
    if (!this.resetOnActivateEntered) {
      this.resetOnActivateEntered = true
      return
    }
    const routeQuery = (this.$route && this.$route.query) || {}
    // ② 带业务下钻：只重查，不清条件（理由见文件头「契约」第 2 条）
    if (hasDrillQuery(routeQuery)) {
      return this.reloadOnActivate()
    }
    // ③ 普通返回：清条件 + 页码回 1 + 清选中 + 重查，全部由页面自己的 resetSearch() 收口
    if (typeof this.resetSearch === 'function') {
      this.resetSearch()
      return
    }
    // 页面没实现 resetSearch()：安全跳过（不报错），至少把数据刷新一遍
    return this.reloadOnActivate()
  },
  methods: {
    /** 按当前条件重新取数（不碰检索条件），供下钻分支与「页面没实现 resetSearch」兜底使用 */
    reloadOnActivate () {
      const name = DEFAULT_RELOAD_METHODS.filter(item => typeof this[item] === 'function')[0]
      if (!name) {
        return undefined
      }
      return this[name]()
    }
  }
}
