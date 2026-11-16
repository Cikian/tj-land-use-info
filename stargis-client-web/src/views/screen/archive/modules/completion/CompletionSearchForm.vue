<template>
  <!--
    CompletionSearchForm 竣工验收历史档案 · 检索条件
    ---------------------------------------------------------------
    与「历史档案列表」配套使用，默认只露出 4 个最常用的条件
    （项目名称 / 档案编号 / 项目类型 / 数字化状态），其余 10 个收在「更多条件」后面。

    事件：
      search (query)  点「查询」或控件回车时抛出，query 已剔除空值、已把
                      hasArchive 从 'true'/'false' 转回真布尔
      reset           点「重置」时抛出（同时也会抛一次 search，父组件只监听 search 即可）

    公开方法：
      getQuery()        取当前条件（已归一化），供「按条件导出 Excel」复用同一份条件
      setQuery(query)   用一组新条件覆盖表单（外部下钻时调用）

    ★ 从档案详情「查看该项目竣工档案」下钻进来时，条件是**两条不同的关联链**（不得互换）：
      `t_completion_archive.archive_id`   —— 历史竣工档案指定的扫描件档案（本条档案）
      `t_archive.facility_id`             —— 档案属于哪个配套项目（项目归属）
    因此优先用 archiveId 反查「哪些历史归档记录引用了本条档案」，
    查不到再由本表单把条件退回 facilityId，并在下方用一行小字说明**当前生效的是哪个条件**。
  -->
  <div class="completion-search">
    <div class="completion-search__grid">
      <!-- ---------- 常驻的 4 个核心条件 ---------- -->
      <screen-field label="项目名称" label-width="84px" html-for="cs-projectName">
        <screen-input
          id="cs-projectName"
          v-model="query.projectName"
          clearable
          placeholder="历史项目名称，模糊匹配"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="档案编号" label-width="84px" html-for="cs-archiveNo">
        <screen-input
          id="cs-archiveNo"
          v-model="query.archiveNo"
          clearable
          placeholder="例如 JG-2026-0001"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="项目类型" label-width="84px">
        <screen-select
          v-model="query.projectType"
          :options="projectTypeOptions"
          placeholder="全部类型"
          aria-label="项目类型"
        />
      </screen-field>

      <screen-field label="数字化状态" label-width="84px">
        <screen-select
          v-model="query.digitizeStatus"
          :options="statusOptions"
          placeholder="全部状态"
          aria-label="数字化状态"
        />
      </screen-field>

      <!-- ---------- 更多条件 ---------- -->
      <template v-if="expanded">
        <screen-field label="项目原编号" label-width="84px" html-for="cs-projectCode">
          <screen-input
            id="cs-projectCode"
            v-model="query.projectCode"
            clearable
            placeholder="历史项目自带编号"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="行政区划" label-width="84px">
          <screen-select
            v-model="query.xzqh"
            :options="xzqhOptions"
            placeholder="全部区划"
            aria-label="行政区划"
          />
        </screen-field>

        <screen-field label="保管期限" label-width="84px">
          <screen-select
            v-model="query.retention"
            :options="retentionOptions"
            placeholder="全部期限"
            aria-label="保管期限"
          />
        </screen-field>

        <screen-field label="扫描件" label-width="84px">
          <screen-select
            v-model="query.hasArchive"
            :options="hasArchiveOptions"
            placeholder="不限"
            aria-label="扫描件挂接情况"
          />
        </screen-field>

        <screen-field label="建设单位" label-width="84px" html-for="cs-buildUnit">
          <screen-input
            id="cs-buildUnit"
            v-model="query.buildUnit"
            clearable
            placeholder="模糊匹配"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="施工单位" label-width="84px" html-for="cs-constructUnit">
          <screen-input
            id="cs-constructUnit"
            v-model="query.constructUnit"
            clearable
            placeholder="模糊匹配"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="出让宗地" label-width="84px" html-for="cs-crzdbh">
          <screen-input
            id="cs-crzdbh"
            v-model="query.crzdbh"
            clearable
            placeholder="出让宗地编号"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="竣工日期" label-width="84px">
          <screen-date-input v-model="completeRange" mode="range" @change="handleCompleteChange" />
        </screen-field>

        <screen-field label="验收日期" label-width="84px">
          <screen-date-input v-model="acceptanceRange" mode="range" @change="handleAcceptanceChange" />
        </screen-field>

        <screen-field label="关键词" label-width="84px" html-for="cs-keyword">
          <screen-input
            id="cs-keyword"
            v-model="query.keyword"
            clearable
            placeholder="编号 / 项目 / 参建单位"
            @enter="handleSearch"
          />
        </screen-field>
      </template>
    </div>

    <!--
      下钻来源说明：一行小字写清「当前生效的是哪个关联条件」。
      清单 P1-09 明确要求这一行——否则用户看到空列表分不清是
      「档案没关联历史归档记录」还是「按错了字段查」。
    -->
    <p v-if="drillNote" class="completion-search__drill">
      <span class="completion-search__drill-text">{{ drillNote }}</span>
      <!--
        0 条时的「下一步怎么办」（清单 P1-09）：光说「按什么条件查」不够，
        用户看到空表还得知道是缺哪条关联数据、该去哪个页面补。
      -->
      <span v-if="drillEmptyHint" class="completion-search__drill-hint">{{ drillEmptyHint }}</span>
      <button
        v-if="drillFallback"
        type="button"
        class="completion-search__drill-switch"
        @click="switchToArchive"
      >
        仍按本条档案反查
      </button>
      <button
        v-else-if="drillCanFallback"
        type="button"
        class="completion-search__drill-switch"
        @click="switchToFacility"
      >
        改按配套项目反查
      </button>
    </p>

    <div class="completion-search__foot">
      <button
        type="button"
        class="completion-search__toggle"
        :aria-expanded="expanded ? 'true' : 'false'"
        @click="expanded = !expanded"
      >
        <screen-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="12" />
        {{ expanded ? '收起条件' : '更多条件' }}
      </button>

      <div class="completion-search__actions">
        <screen-button icon="rotate-ccw" @click="handleReset">重置</screen-button>
        <screen-button type="primary" icon="search" @click="handleSearch">查询</screen-button>
      </div>
    </div>
  </div>
</template>

<script>
import {
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenDateInput,
  ScreenButton,
  ScreenIcon
} from '@/components/screen'
import { queryXzqhOptions } from '@/api/land/landData'
import { queryCompletionList } from '@/api/land/completion'
import {
  DIGITIZE_STATUSES,
  HAS_ARCHIVE_OPTIONS,
  PROJECT_TYPES,
  RETENTIONS,
  needsExpand,
  normalizeQuery
} from './constants'

/** 所有检索字段的初始空值；重置时回到这份结构 */
function buildEmptyQuery () {
  return {
    projectName: '',
    archiveNo: '',
    projectType: '',
    digitizeStatus: '',
    projectCode: '',
    xzqh: '',
    retention: '',
    hasArchive: '',
    buildUnit: '',
    constructUnit: '',
    crzdbh: '',
    keyword: '',
    beginCompleteDate: '',
    endCompleteDate: '',
    beginAcceptanceDate: '',
    endAcceptanceDate: '',
    /**
     * 两个「关联链」条件（都是后端 CompletionQueryDTO 里真实存在的字段）：
     *   archiveId  —— t_completion_archive.archive_id（指定扫描件档案）
     *   facilityId —— t_archive.facility_id（项目归属）
     * 放在空结构里，是为了让「重置」把它们一起清掉，不留看不见的残留条件。
     */
    archiveId: '',
    facilityId: ''
  }
}

/** 字符串数组 → ScreenSelect 需要的 [{ value, label }] */
function toOptions (list) {
  return (list || []).map((item) => ({ value: item, label: item }))
}

export default {
  name: 'CompletionSearchForm',
  components: {
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon
  },
  props: {
    /** 是否默认展开全部条件 */
    defaultExpanded: { type: Boolean, default: false },
    /** 初始条件（外部下钻带入） */
    initialQuery: { type: Object, default: () => ({}) },
    /**
     * 当前条件下的结果条数（父面板的 total）。
     * ★ 只用于「0 条时说清缺哪条关联数据」，不参与任何检索逻辑。
     *   传 null 表示「还没查」—— 那时不该断言「查不到」，所以只在 0 时给结论。
     */
    resultTotal: { type: Number, default: null }
  },
  data () {
    return {
      expanded: this.defaultExpanded,
      query: Object.assign(buildEmptyQuery(), this.initialQuery || {}),
      /** 两个日期区间各自单独存数组：ScreenDateInput 的 range 值是 [start, end] */
      completeRange: [],
      acceptanceRange: [],
      xzqhOptions: [],
      projectTypeOptions: toOptions(PROJECT_TYPES),
      retentionOptions: toOptions(RETENTIONS),
      statusOptions: toOptions(DIGITIZE_STATUSES),
      hasArchiveOptions: HAS_ARCHIVE_OPTIONS,
      /* ---------------- 下钻来源（只用于展示，不参与检索） ---------------- */
      /** 当前生效的关联条件：archive = 按本条档案，facility = 按配套项目，crzdbh = 按宗地编号 */
      drillCondition: '',
      /** 下钻带来的可读项目名（档案详情的 ptxmmc） */
      drillLabel: '',
      /** 下钻来源的档案号（档案详情带的，只用于那行小字） */
      drillSourceArchiveNo: '',
      /** 下钻来源的档案 id（回退到配套项目后仍可切回按档案反查） */
      drillArchiveId: '',
      /** 是否因为「本条档案暂无历史归档记录」自动退回到了配套项目条件 */
      drillFallback: false,
      /** 探测是否还有历史归档记录引用本条档案（false = 还没探测/不适用） */
      drillCanFallback: false,
      drillProbing: false
    }
  },
  computed: {
    /** 那行小字：明确写出当前生效的是哪个条件（清单 P1-09 第 4 条） */
    drillNote () {
      if (this.drillCondition === 'archive') {
        const no = this.drillSourceArchiveNo ? `（${this.drillSourceArchiveNo}）` : ''
        return `当前条件：按本条档案${no}反查历史归档记录`
      }
      if (this.drillCondition === 'facility') {
        const name = this.drillLabel || this.query.crzdbh || '关联配套项目'
        const prefix = this.drillFallback
          ? '当前条件：已从「按本条档案」退回项目归属，按配套项目'
          : '当前条件：按配套项目'
        return `${prefix}「${name}」反查历史归档记录`
      }
      if (this.drillCondition === 'crzdbh') {
        return `当前条件：按出让宗地编号「${this.query.crzdbh}」反查历史归档记录`
      }
      return ''
    },
    /**
     * 0 条时的可执行结论（清单 P1-09）。
     *
     * ★ 为什么要按「当前生效的条件」分开说：两条关联链断在不同的地方，
     *   给一句笼统的「没有数据」等于让用户自己猜：
     *     · 按 archive_id 反查 0 条、且没有 facility_id 可退 → 档案侧没维护项目归属；
     *     · 按 facility 条件 0 条 → 该项目下确实没有「指定扫描件」的历史归档记录。
     */
    drillEmptyHint () {
      if (this.resultTotal !== 0) return ''
      if (this.drillCondition === 'archive' && !this.query.facilityId) {
        return '该档案未维护「配套项目归属（facility_id）」，请在档案维护里补录后再试'
      }
      if (this.drillCondition === 'facility') {
        return '该项目下没有指定扫描件档案（t_completion_archive.archive_id 为空）'
      }
      return ''
    }
  },
  created () {
    this.normalizeHasArchive()
    this.loadXzqh()
    this.syncRanges(this.query)
    // 首次创建时 initialQuery 已并入 query，这里补一次「生效条件」判定
    this.applyDrillContext(this.initialQuery)
  },
  methods: {
    /**
     * 修正 hasArchive 的类型：ScreenSelect 的 value 只接受 String/Number。
     * 外部下钻可能直接给真布尔（父组件里存的是规范化后的条件），
     * 不转的话下拉会「有值但显示占位符」，而且用户一选就变字符串，前后不一致。
     */
    normalizeHasArchive () {
      if (this.query.hasArchive === true) this.query.hasArchive = 'true'
      if (this.query.hasArchive === false) this.query.hasArchive = 'false'
    },

    /**
     * 区划下拉来自 Java 业务后端的共享接口（与档案维护的检索条件用同一个）。
     * 它只是辅助筛选条件，加载失败就静默降级为空选项，不阻塞主流程。
     */
    loadXzqh () {
      queryXzqhOptions()
        .then((res) => {
          if (!res || !res.success) return
          this.xzqhOptions = (res.result || []).map((item) => {
            if (item && item.value !== undefined) return { value: item.value, label: item.label }
            const value = item && (item.xzqh || item.name)
            return { value, label: value }
          })
        })
        .catch(() => {
          this.xzqhOptions = []
        })
    },

    /** 把 query 里的日期区间同步到两个 range 组件的值上 */
    syncRanges (query) {
      const source = query || {}
      this.completeRange = source.beginCompleteDate || source.endCompleteDate
        ? [source.beginCompleteDate || '', source.endCompleteDate || '']
        : []
      this.acceptanceRange = source.beginAcceptanceDate || source.endAcceptanceDate
        ? [source.beginAcceptanceDate || '', source.endAcceptanceDate || '']
        : []
    },

    handleCompleteChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginCompleteDate = values[0] || ''
      this.query.endCompleteDate = values[1] || ''
    },

    handleAcceptanceChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginAcceptanceDate = values[0] || ''
      this.query.endAcceptanceDate = values[1] || ''
    },

    handleSearch () {
      this.$emit('search', this.getQuery())
    },

    handleReset () {
      this.query = buildEmptyQuery()
      this.completeRange = []
      this.acceptanceRange = []
      // 重置后「当前生效条件」就不再成立了，否则会留下一行和结果不符的说明
      this.drillCondition = ''
      this.drillFallback = false
      this.drillCanFallback = false
      this.drillLabel = ''
      this.drillSourceArchiveNo = ''
      this.drillArchiveId = ''
      this.$emit('reset')
      // 重置后立即检索一次，让列表回到全量状态
      this.$emit('search', this.getQuery())
    },

    /** 对外暴露当前条件（已归一化），供「导出查询结果」使用同一份过滤条件 */
    getQuery () {
      const out = normalizeQuery(this.query)
      // 只用于回显/说明的字段，后端 DTO 里没有，不能发出去
      delete out.facilityLabel
      delete out.ptxmmc
      delete out.sourceArchiveNo
      return out
    },

    /**
     * 判定「当前生效的关联条件」并按需回退（清单 P1-09 第 4 条）。
     *
     * 顺序固定，两条关联链**不得互换**：
     *   ① archiveId  → t_completion_archive.archive_id（本条档案被哪些历史归档记录引用）；
     *   ② 查不到时退到 facilityId → t_archive.facility_id（项目归属）。
     * 探测失败（接口不通）时**不改条件**：宁可显示「按本条档案」也不能悄悄换成另一个口径。
     *
     * @param {object} source 下钻条件（可能带 facilityLabel / sourceArchiveNo 等展示字段）
     */
    applyDrillContext (source) {
      const src = source || {}
      const archiveId = src.archiveId || ''
      const facilityId = src.facilityId || ''
      this.drillLabel = src.facilityLabel || src.ptxmmc || ''
      this.drillSourceArchiveNo = src.sourceArchiveNo || ''
      this.drillArchiveId = archiveId
      this.drillFallback = false
      this.drillCanFallback = false

      if (!archiveId) {
        this.drillCondition = facilityId ? 'facility' : (src.crzdbh ? 'crzdbh' : '')
        return
      }

      this.drillCondition = 'archive'
      // 没有备选条件时不需要探测（退无可退）
      if (!facilityId) return
      this.drillProbing = true
      queryCompletionList({ archiveId, pageNo: 1, pageSize: 1 })
        .then((res) => {
          if (!res || !res.success) return
          const total = Number((res.result && res.result.total) || 0)
          this.drillCanFallback = true
          if (total > 0) return
          // 本条档案还没有被任何历史归档记录引用 → 退回项目归属条件并重新取数
          this.drillFallback = true
          this.drillCondition = 'facility'
          this.dropArchiveCondition()
          this.$emit('search', this.getQuery())
        })
        .catch(() => {
          // 探测失败：保持「按本条档案」的口径，不做静默改写
        })
        .finally(() => {
          this.drillProbing = false
        })
    },

    /** 去掉 archiveId 条件（只改本表单的 query，不动 facilityId / crzdbh） */
    dropArchiveCondition () {
      const next = Object.assign({}, this.query)
      delete next.archiveId
      this.query = next
    },

    /** 手动切回「按本条档案反查」（自动回退后仍允许用户核对另一种口径） */
    switchToArchive () {
      if (!this.drillArchiveId) return
      this.query = Object.assign({}, this.query, { archiveId: this.drillArchiveId })
      this.drillFallback = false
      this.drillCondition = 'archive'
      this.$emit('search', this.getQuery())
    },

    /** 手动切到「按配套项目反查」 */
    switchToFacility () {
      if (!this.query.facilityId) return
      this.dropArchiveCondition()
      this.drillFallback = true
      this.drillCondition = 'facility'
      this.$emit('search', this.getQuery())
    },

    /**
     * 用一组新条件覆盖当前表单（外部下钻时调用）。
     * 之所以需要这个方法：initialQuery 只在 data() 里读一次，组件挂载后就改不动了。
     * 若新条件里带了非常驻字段，顺手展开「更多条件」——否则用户会以为条件没生效。
     *
     * @param {object} query 可下发给后端的条件
     * @param {object} [displayContext] 只用于说明行的展示字段
     *        （{ facilityLabel, sourceArchiveNo }）—— 清单 P1-09：
     *        展示名不能混进 query 发给后端，但说明行又必须拿到它，
     *        所以由父面板单独作为第二个参数传下来。
     */
    setQuery (query, displayContext) {
      const source = Object.assign({}, query || {})
      const next = Object.assign(buildEmptyQuery(), source)
      // 展示字段不留在 query 里，避免它们被当成检索条件带到后端
      delete next.facilityLabel
      delete next.ptxmmc
      delete next.sourceArchiveNo
      this.query = next
      this.normalizeHasArchive()
      this.syncRanges(next)
      if (needsExpand(next)) {
        this.expanded = true
      } else if (!Object.keys(normalizeQuery(next)).length) {
        // 空条件 = 重置：把「更多条件」一起收回初始形态，否则清空后表单还是摊开的
        this.expanded = this.defaultExpanded
      }
      this.applyDrillContext(Object.assign({}, source, displayContext || {}))
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.completion-search {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  &__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-3);
    padding-top: var(--screen-space-2);
    border-top: 1px solid var(--screen-border-soft);
  }

  &__toggle {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent);
    background: none;
    border: 0;
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
    }
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin-left: auto;
  }

  /* 下钻来源那一行小字：只承载「当前按什么条件查」，不抢条件区的视觉重量 */
  &__drill {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin: 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent-soft);
  }

  &__drill-text {
    .screen-ellipsis();
  }

  /* 0 条时的可执行结论：warning 语气（这是「缺数据」，不是「一切正常」） */
  &__drill-hint {
    flex: none;
    color: var(--screen-warning);
  }

  &__drill-switch {
    flex: none;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    color: var(--screen-accent);
    background: none;
    border: 0;
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent-bright);
    }
  }
}

// 窄屏逐级降列，保证每个控件仍有足够可用宽度
@media (max-width: 1800px) {
  .completion-search__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1360px) {
  .completion-search__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .completion-search__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .completion-search__foot {
    flex-wrap: wrap;
  }
}
</style>
