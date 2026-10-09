<template>
  <!--
    LedgerSearchForm 台账检索条件（方案 2.3.2 第 7 项）
    --------------------------------
    清单 §6.3.7 要求「可快速检索查询」并明列了道路名称 / 行政区 / 验收类型 /
    验收日期区间 / 验收结果 / 状态六个条件。默认只露出其中最常用的
    CORE_QUERY_FIELDS 四个（道路名称 / 行政区划 / 验收类型 / 状态），
    其余 13 个收在「更多条件」后面 —— 大屏首屏高度有限，
    一进来铺 17 个输入框会把列表挤到看不见。

    事件：
      search (query)  点「查询」时抛出，query 已剔除空值（compactQuery）
      reset           点「重置」时抛出（同时也抛一次 search，父组件只监听 search 即可）

    公开方法：
      getQuery()        取当前条件（已剔除空值、已把「资料齐全与否」翻成区间）
      setQuery(query)   用一组新条件覆盖当前表单（父级下钻时调用；非核心字段会自动展开）
  -->
  <div class="ledger-search">
    <div class="ledger-search__grid">
      <!-- ================= 常驻的 4 个核心条件 ================= -->
      <screen-field label="道路名称" label-width="84px" html-for="ls-roadName">
        <screen-input
          id="ls-roadName"
          v-model="query.roadName"
          clearable
          placeholder="按名称模糊检索"
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

      <screen-field label="验收类型" label-width="84px">
        <screen-select
          v-model="query.acceptanceType"
          :options="typeOptions"
          placeholder="全部类型"
          aria-label="验收类型"
        />
      </screen-field>

      <screen-field label="状态" label-width="84px">
        <screen-select
          v-model="query.status"
          :options="statusOptions"
          placeholder="全部状态"
          aria-label="状态"
        />
      </screen-field>

      <!-- ================= 更多条件 ================= -->
      <template v-if="expanded">
        <screen-field label="台账编号" label-width="84px" html-for="ls-ledgerNo">
          <screen-input
            id="ls-ledgerNo"
            v-model="query.ledgerNo"
            clearable
            placeholder="例如 YS-2026-0001"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="功能区" label-width="84px">
          <screen-select
            v-model="query.gnq"
            :options="gnqOptions"
            placeholder="全部功能区"
            aria-label="功能区"
          />
        </screen-field>

        <screen-field label="设施类别" label-width="84px">
          <screen-select
            v-model="query.ptsslb"
            :options="ptsslbOptions"
            placeholder="全部类别"
            aria-label="配套设施类别"
          />
        </screen-field>

        <screen-field label="验收结果" label-width="84px">
          <screen-select
            v-model="query.acceptanceResult"
            :options="resultOptions"
            placeholder="全部结果"
            aria-label="验收结果"
          />
        </screen-field>

        <screen-field label="出让宗地编号" label-width="84px" html-for="ls-crzdbh">
          <screen-input
            id="ls-crzdbh"
            v-model="query.crzdbh"
            clearable
            placeholder="宗地编号"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="地块名称" label-width="84px" html-for="ls-dkmc">
          <screen-input
            id="ls-dkmc"
            v-model="query.dkmc"
            clearable
            placeholder="地块名称"
            @enter="handleSearch"
          />
        </screen-field>

        <!-- 关联配套项目：远程搜索（选项由后端按关键词返回，不做本地过滤） -->
        <screen-field label="配套项目" label-width="84px">
          <screen-select
            v-model="query.facilityId"
            :options="facilityOptions"
            placeholder="输入项目名称搜索"
            empty-text="没有找到配套项目"
            searchable
            :filter-local="false"
            aria-label="关联配套项目"
            @search="handleFacilitySearch"
            @open="handleFacilityOpen"
          />
        </screen-field>

        <screen-field label="缺少资料" label-width="84px">
          <screen-select
            v-model="query.missingMaterial"
            :options="materialOptions"
            placeholder="某类资料未归集"
            aria-label="缺少某类资料"
          />
        </screen-field>

        <screen-field label="资料齐全" label-width="84px">
          <screen-select
            v-model="query.materialCompleteness"
            :options="completenessOptions"
            placeholder="不限"
            aria-label="资料齐全与否"
          />
        </screen-field>

        <screen-field label="验收日期" label-width="84px">
          <screen-date-input
            v-model="acceptanceRange"
            mode="range"
            @change="handleAcceptanceChange"
          />
        </screen-field>

        <screen-field label="移交日期" label-width="84px">
          <screen-date-input
            v-model="handoverRange"
            mode="range"
            @change="handleHandoverChange"
          />
        </screen-field>

        <screen-field label="关键词" label-width="84px" html-for="ls-keyword">
          <screen-input
            id="ls-keyword"
            v-model="query.keyword"
            clearable
            placeholder="道路/宗地/地块/编号"
            @enter="handleSearch"
          />
        </screen-field>
      </template>
    </div>

    <div class="ledger-search__foot">
      <button
        type="button"
        class="ledger-search__toggle"
        :aria-expanded="expanded ? 'true' : 'false'"
        @click="expanded = !expanded"
      >
        <screen-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="12" />
        {{ expanded ? '收起条件' : '更多条件' }}
      </button>

      <div class="ledger-search__actions">
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
import { queryXzqhOptions, queryFacilityOptions } from '@/api/land/landData'
import { queryLedgerMaterials } from '@/api/land/ledger'
import {
  ACCEPTANCE_RESULTS,
  ACCEPTANCE_TYPES,
  CORE_QUERY_FIELDS,
  GNQ_OPTIONS,
  LEDGER_STATUS,
  MATERIAL_COMPLETENESS_OPTIONS,
  MATERIAL_TOTAL,
  PTSSLB_OPTIONS,
  LEDGER_MATERIALS_FALLBACK,
  normalizeMaterials,
  toMaterialOptions,
  compactQuery,
  toOptions
} from './constants'

/** 下拉一次拉取的最大条数（后端 limit 参数） */
const OPTION_LIMIT = 50

/**
 * 所有检索字段的初始空值，重置与下钻覆盖都以这份结构为基准
 * （这样新加一个条件只改这一处，不会出现「重置后残留上一次的筛选」）。
 */
function buildEmptyQuery () {
  return {
    ledgerNo: '',
    roadName: '',
    xzqh: '',
    gnq: '',
    acceptanceType: '',
    acceptanceResult: '',
    status: '',
    ptsslb: '',
    crzdbh: '',
    dkmc: '',
    facilityId: '',
    /**
     * 关联档案ID：没有独立输入控件，只作为**下钻条件**存在
     * （从档案详情点「查看该项台账」时由页签壳带进来）。
     * 放在这里是为了让它参与「重置」与「是否展开更多条件」的判断，
     * 否则它会以「用户看不见的残留条件」形式留在查询里。
     */
    archiveId: '',
    missingMaterial: '',
    /** 前端语义字段（full / partial / empty），提交前翻成资料数区间 */
    materialCompleteness: '',
    beginMaterialCount: '',
    endMaterialCount: '',
    beginAcceptanceDate: '',
    endAcceptanceDate: '',
    beginHandoverDate: '',
    endHandoverDate: '',
    keyword: ''
  }
}

export default {
  name: 'LedgerSearchForm',
  components: {
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon
  },
  props: {
    /** 是否默认展开全部条件（面板从指标卡下钻时会用到） */
    defaultExpanded: { type: Boolean, default: false },
    /** 初始条件（下钻带的 facilityId / missingMaterial / status 等） */
    initialQuery: { type: Object, default: () => ({}) },
    /** 13 类资料定义（面板已拉过就传进来，避免每个组件各拉一次） */
    materials: { type: Array, default: () => [] }
  },
  data () {
    return {
      expanded: this.defaultExpanded,
      query: Object.assign(buildEmptyQuery(), this.initialQuery || {}),
      acceptanceRange: [],
      handoverRange: [],
      xzqhOptions: [],
      facilityOptions: [],
      localMaterials: LEDGER_MATERIALS_FALLBACK.slice(),
      statusOptions: toOptions(LEDGER_STATUS),
      typeOptions: toOptions(ACCEPTANCE_TYPES),
      resultOptions: toOptions(ACCEPTANCE_RESULTS),
      gnqOptions: toOptions(GNQ_OPTIONS),
      ptsslbOptions: toOptions(PTSSLB_OPTIONS),
      completenessOptions: MATERIAL_COMPLETENESS_OPTIONS
    }
  },
  computed: {
    /** 资料定义：父级传入优先，否则用本组件拉到（或兜底）的 */
    materialList () {
      return this.materials && this.materials.length ? this.materials : this.localMaterials
    },
    materialOptions () {
      return toMaterialOptions(this.materialList)
    }
  },
  created () {
    this.loadXzqh()
    if (!this.materials || !this.materials.length) {
      this.loadMaterials()
    }
    // 回显日期区间：条件里是分开的 begin/end，界面上是一个 range 控件
    this.acceptanceRange = this.buildRange('beginAcceptanceDate', 'endAcceptanceDate')
    this.handoverRange = this.buildRange('beginHandoverDate', 'endHandoverDate')
    // 下钻条件里如果带了资料数区间，反推回「资料齐全与否」下拉，避免两处条件互相矛盾
    this.query.materialCompleteness = this.deriveCompleteness(this.query)
    // 带了非核心条件时直接展开，否则用户会以为条件没生效
    this.syncExpanded()
  },
  methods: {
    /** 'YYYY-MM-DD' 起止 → ScreenDateInput 的 range 数组 */
    buildRange (beginKey, endKey) {
      if (!this.query[beginKey] && !this.query[endKey]) return []
      return [this.query[beginKey] || '', this.query[endKey] || '']
    },

    /** 资料数区间 → 「资料齐全与否」下拉值（反过来保持一致） */
    deriveCompleteness (query) {
      if (query.materialCompleteness) return query.materialCompleteness
      const begin = query.beginMaterialCount
      const end = query.endMaterialCount
      if (begin === '' || begin === undefined || begin === null) return ''
      const beginValue = Number(begin)
      const endValue = end === '' || end === undefined || end === null ? beginValue : Number(end)
      if (beginValue >= MATERIAL_TOTAL && endValue >= MATERIAL_TOTAL) return 'full'
      if (beginValue === 0 && endValue === 0) return 'empty'
      if (beginValue >= 1 && endValue <= MATERIAL_TOTAL - 1) return 'partial'
      return ''
    },

    loadXzqh () {
      queryXzqhOptions()
        .then((res) => {
          if (!res || !res.success) return
          // 后端返回 [{ value, label }]，做一次兜底以兼容 [{ xzqh }] 形态
          this.xzqhOptions = (res.result || []).map((item) => {
            if (item && item.value !== undefined) return { value: item.value, label: item.label }
            const value = item && (item.xzqh || item.name)
            return { value, label: value }
          })
        })
        .catch(() => {
          // 区划只是辅助筛选条件，失败时静默降级为空选项，不影响主流程
          this.xzqhOptions = []
        })
    },

    /** 13 类资料定义（矩阵表头 / 缺少资料下拉共用） */
    loadMaterials () {
      return queryLedgerMaterials()
        .then((res) => {
          if (res && res.success) {
            this.localMaterials = normalizeMaterials(res.result)
          }
        })
        .catch(() => {
          this.localMaterials = LEDGER_MATERIALS_FALLBACK.slice()
        })
    },

    /** 首次展开「配套项目」时才拉选项，避免打开页面就发一次请求 */
    handleFacilityOpen () {
      if (!this.facilityOptions.length) this.loadFacilityOptions('')
    },

    handleFacilitySearch (keyword) {
      this.loadFacilityOptions(keyword)
    },

    loadFacilityOptions (keyword) {
      queryFacilityOptions({ keyword: keyword || undefined, limit: OPTION_LIMIT })
        .then((res) => {
          if (!res || !res.success) return
          this.facilityOptions = (res.result || []).map((item) => ({
            value: item.id || item.facilityId,
            label: item.ptxmmc || item.name || item.id
          }))
          // 拉回来的候选里可能没有当前已选中的项目（下钻只带了一个 id），
          // 不补进去下拉会显示空白，用户以为条件丢了（与 ProjectPicker 同一处理）
          this.ensureFacilityOption(this.query.facilityId, this.query.ptxmmc)
        })
        .catch(() => {
          this.facilityOptions = []
          this.ensureFacilityOption(this.query.facilityId, this.query.ptxmmc)
        })
    },

    /**
     * 保证「当前选中的配套项目」一定在下拉选项里。
     * @param {string} id 配套项目 id
     * @param {string} [label] 可读名称（下钻时若带了 ptxmmc 就用它，否则退化为 id）
     */
    ensureFacilityOption (id, label) {
      if (!id) return
      const exists = this.facilityOptions.some((item) => item.value === id)
      if (exists) return
      this.facilityOptions = [{ value: id, label: label || id }].concat(this.facilityOptions)
    },

    handleAcceptanceChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginAcceptanceDate = values[0] || ''
      this.query.endAcceptanceDate = values[1] || ''
    },

    handleHandoverChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginHandoverDate = values[0] || ''
      this.query.endHandoverDate = values[1] || ''
    },

    handleSearch () {
      this.$emit('search', this.getQuery())
    },

    handleReset () {
      // 整体替换 query 而不是逐字段赋值：少一个字段就是一次「重置后残留」
      this.query = buildEmptyQuery()
      this.acceptanceRange = []
      this.handoverRange = []
      this.$emit('reset')
      // 重置后立即检索一次，让列表回到全量状态
      this.$emit('search', this.getQuery())
    },

    /**
     * 对外暴露当前条件，供「按条件导出 Excel」和状态页签复用同一份条件。
     *
     * ★「资料齐全与否」在这里翻成后端认识的资料数区间，
     *   并把前端语义字段删掉 —— 后端 DTO 里没有 materialCompleteness，
     *   多传一个未知参数虽然不会报错，但会让「条件是什么」变得含糊。
     */
    getQuery () {
      const out = compactQuery(this.query)
      const completeness = out.materialCompleteness
      if (completeness === 'full') {
        out.beginMaterialCount = MATERIAL_TOTAL
        out.endMaterialCount = MATERIAL_TOTAL
      } else if (completeness === 'partial') {
        out.beginMaterialCount = 1
        out.endMaterialCount = MATERIAL_TOTAL - 1
      } else if (completeness === 'empty') {
        out.beginMaterialCount = 0
        out.endMaterialCount = 0
      }
      delete out.materialCompleteness
      return out
    },

    /** 命中非核心字段就展开，用户才看得到「是按什么筛出来的」 */
    syncExpanded () {
      const expandedFields = Object.keys(buildEmptyQuery()).filter(
        (key) => CORE_QUERY_FIELDS.indexOf(key) === -1
      )
      const next = this.query || {}
      const hasHidden = expandedFields.some((key) => {
        const value = next[key]
        return value !== '' && value !== undefined && value !== null
      })
      if (hasHidden) this.expanded = true
    },

    /**
     * 用一组新条件覆盖当前表单（父级从指标卡 / 资料分布 / 档案详情下钻时调用）。
     *
     * 为什么必须有这个方法：initialQuery 只在 data() 里读一次，
     * 面板被 keep-alive 缓存后再改 props 是不会重跑 created 的。
     */
    setQuery (query) {
      const next = Object.assign(buildEmptyQuery(), compactQuery(query || {}))
      next.materialCompleteness = this.deriveCompleteness(next)
      this.query = next
      this.acceptanceRange = this.buildRange('beginAcceptanceDate', 'endAcceptanceDate')
      this.handoverRange = this.buildRange('beginHandoverDate', 'endHandoverDate')
      // 下钻可能只带 facilityId（选项还没拉过），先把当前值补进选项并预取一批，
      // 否则「配套项目」下拉会显示空白，用户以为条件没生效
      if (next.facilityId) {
        this.ensureFacilityOption(next.facilityId, next.ptxmmc)
        this.loadFacilityOptions('')
      }
      this.syncExpanded()
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.ledger-search {
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
}

// 大屏常见宽度下的列数降级：保证每个控件仍有足够可用宽度
@media (max-width: 1800px) {
  .ledger-search__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1360px) {
  .ledger-search__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .ledger-search__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .ledger-search__foot {
    flex-wrap: wrap;
  }
}
</style>
