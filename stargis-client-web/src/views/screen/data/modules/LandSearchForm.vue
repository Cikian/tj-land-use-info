<template>
  <!--
    LandSearchForm 经营性用地检索条件
    --------------------------------
    「经营性用地信息录入」面板的检索条件。默认只露出 4 个最常用的
    （出让宗地编号 / 地块名称 / 行政区划 / 项目分类），其余收在「更多条件」后面。

    ★ 条件字段与后端 `LandAdminQueryDTO` **逐个对应**（后端不用 jeecg 的
      QueryGenerator，没声明的字段名会被静默忽略）：
        crzdbh / dkmc / ghydxz / srr  → 模糊
        xzqh / xmfl / ptsfqq          → 精确
        crsjBegin / crsjEnd           → 出让时间区间（yyyy-MM-dd）
        hasFacility                   → ★ 真布尔：true=有配套，false=无配套（孤儿宗地）
        facilityKeyword               → 跨表到配套表，按配套名称/编号反查宗地
    ★ 「更多条件」里刻意**没有**排序方式：后端 orderBy 白名单只接受列名
      （create_time / crzdbh / …），做成下拉要额外维护一份「中文 → 列名」映射，
      而列表本身就带分页，排序收益不大；需要时在表格列上按后端白名单再加。

    事件：
      search (query)  点击「查询」或按回车时抛出，query 已剔除空值
      reset           点击「重置」时抛出（同时也会抛 search，父组件只需监听 search）

    公开方法：
      getQuery()  取当前条件（已剔除空值，hasFacility 已转成布尔）
      setQuery(q) 用一组新条件覆盖（供下钻 / applyDrill 使用）
  -->
  <div class="land-search" :class="{ 'is-vertical': vertical }">
    <div class="land-search__grid">
      <!-- ---------- 常驻的核心条件 ---------- -->
      <screen-field label="出让宗地编号" label-width="104px" :stacked="vertical" html-for="ls-crzdbh">
        <screen-input
          id="ls-crzdbh"
          v-model="query.crzdbh"
          clearable
          placeholder="按宗地编号模糊检索"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="地块名称" label-width="104px" :stacked="vertical" html-for="ls-dkmc">
        <screen-input
          id="ls-dkmc"
          v-model="query.dkmc"
          clearable
          placeholder="按地块名称模糊检索"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="行政区划" label-width="104px" :stacked="vertical">
        <screen-select
          v-model="query.xzqh"
          :options="xzqhOptions"
          placeholder="全部区划"
          aria-label="行政区划"
        />
      </screen-field>

      <screen-field label="项目分类" label-width="104px" :stacked="vertical">
        <screen-select
          v-model="query.xmfl"
          :options="projectTypeOptions"
          placeholder="全部分类"
          aria-label="项目分类"
        />
      </screen-field>

      <!-- ---------- 更多条件 ---------- -->
      <template v-if="expanded">
        <screen-field label="规划用地性质" label-width="104px" :stacked="vertical" html-for="ls-ghydxz">
          <screen-input
            id="ls-ghydxz"
            v-model="query.ghydxz"
            clearable
            placeholder="如：商业用地"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="受让人" label-width="104px" :stacked="vertical" html-for="ls-srr">
          <screen-input
            id="ls-srr"
            v-model="query.srr"
            clearable
            placeholder="受让人名称"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="出让时间" label-width="104px" :stacked="vertical">
          <screen-date-input v-model="dateRange" mode="range" :stacked="vertical" @change="handleDateChange" />
        </screen-field>

        <screen-field label="配套是否齐全" label-width="104px" :stacked="vertical">
          <screen-select
            v-model="query.ptsfqq"
            :options="yesNoOptions"
            placeholder="全部"
            aria-label="配套是否齐全"
          />
        </screen-field>

        <screen-field label="配套情况" label-width="104px" :stacked="vertical">
          <screen-select
            v-model="hasFacility"
            :options="hasFacilityOptions"
            placeholder="全部宗地"
            aria-label="是否有配套项目"
          />
        </screen-field>

        <screen-field label="配套名称/编号" label-width="104px" :stacked="vertical" html-for="ls-facilityKeyword">
          <screen-input
            id="ls-facilityKeyword"
            v-model="query.facilityKeyword"
            clearable
            placeholder="按该宗地下的配套反查"
            @enter="handleSearch"
          />
        </screen-field>
      </template>
    </div>

    <div class="land-search__foot">
      <button
        type="button"
        class="land-search__toggle"
        :aria-expanded="expanded ? 'true' : 'false'"
        @click="expanded = !expanded"
      >
        <screen-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="12" />
        {{ expanded ? '收起条件' : '更多条件' }}
      </button>

      <div class="land-search__actions">
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
import { queryLandDictItems } from '@/api/land/landAdmin'
import {
  XZQH_LIST,
  PROJECT_TYPES,
  YES_NO,
  HAS_FACILITY_OPTIONS,
  dictDefinitions,
  compactQuery,
  toBooleanFlag
} from '../constants'

/** 所有检索字段的初始空值，重置时回到这份结构 */
function buildEmptyQuery () {
  return {
    crzdbh: '',
    dkmc: '',
    xzqh: '',
    xmfl: '',
    ghydxz: '',
    srr: '',
    ptsfqq: '',
    /** 界面用 'true'/'false'（ScreenSelect 只吃 String/Number），getQuery 时转回布尔 */
    hasFacility: '',
    facilityKeyword: '',
    crsjBegin: '',
    crsjEnd: ''
  }
}

export default {
  name: 'LandSearchForm',
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
    /**
     * 竖排版式：条件**单列**铺开、每个字段「标签在上、控件在下」。
     *
     * ★ 为什么需要这个 prop，而不是靠 CSS 媒体查询自动降级：
     *   本表单现在挂在屏幕右侧那条 **200px 宽**的检索列里（与左侧导航对称），
     *   而 CSS 媒体查询看的是**视口**宽度（大屏是 1920），根本感知不到容器只有 200px，
     *   于是网格会一直是 4 列 —— 每个控件被压到 35px 左右，完全不可用。
     *   容器查询（@container）在目标内核上还不能依赖，所以由父组件显式声明版式。
     *
     * ★ 字段必须 stacked（标签换行到控件上方）：200px 列减去面板内边距只剩 168px，
     *   横排的 104px 标签会把控件挤到 50px 左右。
     */
    vertical: { type: Boolean, default: false },
    /** 初始条件（下钻时带进来的 crzdbh 等） */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      expanded: this.defaultExpanded,
      query: Object.assign(buildEmptyQuery(), this.initialQuery || {}),
      dateRange: [],
      // 先给兜底，created 里再用字典覆盖（字典接口挂了页面也能筛）
      xzqhOptions: XZQH_LIST.map((item) => ({ value: item, label: item })),
      projectTypeOptions: PROJECT_TYPES.map((item) => ({ value: item, label: item })),
      yesNoOptions: YES_NO.map((item) => ({ value: item, label: item })),
      hasFacilityOptions: HAS_FACILITY_OPTIONS
    }
  },
  created () {
    this.loadDicts()
    // 回显日期区间：initialQuery 里是分开的 crsjBegin / crsjEnd，这里合成一个数组
    if (this.query.crsjBegin || this.query.crsjEnd) {
      this.dateRange = [this.query.crsjBegin || '', this.query.crsjEnd || '']
    }
  },
  methods: {
    /** 用 jeecg 字典覆盖下拉（失败静默，兜底常量已经把六个字典抄全了） */
    loadDicts () {
      queryLandDictItems(dictDefinitions()).then((dicts) => {
        if (dicts.xzqh) this.xzqhOptions = dicts.xzqh
        if (dicts.projectType) this.projectTypeOptions = dicts.projectType
      })
    },

    handleDateChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.crsjBegin = values[0] || ''
      this.query.crsjEnd = values[1] || ''
    },

    handleSearch () {
      this.$emit('search', this.getQuery())
    },

    handleReset () {
      this.query = buildEmptyQuery()
      this.dateRange = []
      this.$emit('reset')
      // 重置后立即检索一次，让列表回到全量状态（与档案模块行为一致）
      this.$emit('search', this.getQuery())
    },

    /**
     * 对外暴露当前条件，供「按条件导出 / 批量移除」使用同一份过滤条件。
     * ★ hasFacility 必须转成真布尔：后端是 Boolean，传字符串 'false' 会被 Spring
     *   当成非空字符串转成 true，结果是「筛无配套」筛出「有配套」且不报错。
     */
    getQuery () {
      const query = Object.assign({}, this.query)
      const flag = toBooleanFlag(query.hasFacility)
      if (flag === undefined) {
        delete query.hasFacility
      } else {
        query.hasFacility = flag
      }
      return compactQuery(query)
    },

    /**
     * 用一组新条件覆盖当前表单（下钻 / applyDrill 时调用）。
     * ★ 为什么需要这个方法：initialQuery 只在 data() 里读一次，组件挂载后就改不动了。
     *   若新条件里带了非常驻字段，顺手把「更多条件」展开，否则用户会以为条件没生效。
     */
    setQuery (query) {
      const source = Object.assign({}, query || {})
      // 布尔回显成界面用的字符串值
      if (source.hasFacility === true) source.hasFacility = 'true'
      if (source.hasFacility === false) source.hasFacility = 'false'
      const next = Object.assign(buildEmptyQuery(), source)
      this.query = next
      this.dateRange = next.crsjBegin || next.crsjEnd ? [next.crsjBegin || '', next.crsjEnd || ''] : []

      const hidden = ['ghydxz', 'srr', 'ptsfqq', 'hasFacility', 'facilityKeyword', 'crsjBegin', 'crsjEnd']
      if (hidden.some((key) => next[key] !== '' && next[key] !== undefined && next[key] !== null)) {
        this.expanded = true
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.land-search {
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

  /*
   * 竖排版式：屏幕右侧 200px 检索列（见 vertical prop 的说明）。
   * 这里只改「排布」，字段本身改成 stacked 是在模板上通过 :stacked="vertical" 做的
   * —— ★ 不能用 CSS 改：ScreenField 的标签列宽是**内联样式**注入的，外部 CSS 盖不住。
   */
  &.is-vertical {
    .land-search__grid {
      // 单列：200px 列里再分栏就什么都放不下了
      grid-template-columns: minmax(0, 1fr);
      gap: var(--screen-space-3);
    }

    .land-search__foot {
      // 「更多条件」一行、两个按钮一行，避免在 168px 里挤成一条
      flex-direction: column;
      align-items: stretch;
      gap: var(--screen-space-2);
    }

    .land-search__actions {
      margin-left: 0;
      justify-content: flex-end;
    }
  }
}

// 大屏常见宽度下的列数降级：保证每个控件仍有足够可用宽度
@media (max-width: 1800px) {
  .land-search__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1360px) {
  .land-search__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .land-search__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .land-search__foot {
    flex-wrap: wrap;
  }
}
</style>
