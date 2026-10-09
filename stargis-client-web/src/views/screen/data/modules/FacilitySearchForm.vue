<template>
  <!--
    FacilitySearchForm 配套项目检索条件
    --------------------------------
    「配套地块数据录入」面板的检索条件。默认只露出 4 个最常用的
    （出让宗地编号 / 配套项目名称 / 配套设施类别 / 所属行政区），
    其余 3 个收在「更多条件」后面，避免一进来就被输入框淹没。
    ★ 「更多条件」里刻意**没有**「是否开工」这类条件：后端配套列表的查询 DTO 只认
      crzdbh / dkmc / xzqh / xmfl / srr / facilityKeyword 六个字段（其余会被静默忽略），
      放一个筛不出东西的下拉比不放更糟 —— 用户会以为「这批配套都没开工」。

    ★ 字段名与后端的对应关系（`LandAdminQueryDTO` 被配套列表复用，字段名相同、
      语义有两处被「借用」，这里必须写清楚否则后人一定会接错）：
        crzdbh          → 出让宗地编号（模糊）
        srr             → ★ 实际承载「配套项目名称」模糊条件（后端复用）
        facilityKeyword → ★「配套项目名称或宗地编号」模糊条件（另一个入口）
        xzqh            → 精确
        xmfl            → 精确（市级项目 / 区级项目）
        dkmc            → 地块名称模糊

    事件：
      search (query)  点击「查询」或按回车时抛出，query 已剔除空值
      reset           点击「重置」时抛出（同时也会抛 search，父组件只需监听 search）

    公开方法：
      getQuery()  取当前条件（已剔除空值）
      setQuery(q) 用一组新条件覆盖（供下钻 / applyDrill 使用）
  -->
  <div class="facility-search">
    <div class="facility-search__grid">
      <!-- ---------- 常驻的 4 个核心条件 ---------- -->
      <screen-field label="出让宗地编号" label-width="104px" html-for="fs-crzdbh">
        <screen-input
          id="fs-crzdbh"
          v-model="query.crzdbh"
          clearable
          placeholder="按宗地编号模糊检索"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="配套项目名称" label-width="104px" html-for="fs-ptxmmc">
        <screen-input
          id="fs-ptxmmc"
          v-model="query.srr"
          clearable
          placeholder="按配套项目名称模糊检索"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="配套设施类别" label-width="104px">
        <screen-select
          v-model="query.ptsslb"
          :options="categoryOptions"
          placeholder="全部类别"
          aria-label="配套设施类别"
        />
      </screen-field>

      <screen-field label="所属行政区" label-width="104px">
        <screen-select
          v-model="query.xzqh"
          :options="xzqhOptions"
          placeholder="全部区划"
          aria-label="所属行政区"
        />
      </screen-field>

      <!-- ---------- 更多条件 ---------- -->
      <template v-if="expanded">
        <screen-field label="地块名称" label-width="104px" html-for="fs-dkmc">
          <screen-input
            id="fs-dkmc"
            v-model="query.dkmc"
            clearable
            placeholder="地块名称模糊检索"
            @enter="handleSearch"
          />
        </screen-field>

        <screen-field label="项目分类" label-width="104px">
          <screen-select
            v-model="query.xmfl"
            :options="projectTypeOptions"
            placeholder="全部分类"
            aria-label="项目分类"
          />
        </screen-field>

        <screen-field label="名称或编号" label-width="104px" html-for="fs-keyword">
          <screen-input
            id="fs-keyword"
            v-model="query.facilityKeyword"
            clearable
            placeholder="配套名称或宗地编号"
            @enter="handleSearch"
          />
        </screen-field>
      </template>
    </div>

    <div class="facility-search__foot">
      <button
        type="button"
        class="facility-search__toggle"
        :aria-expanded="expanded ? 'true' : 'false'"
        @click="expanded = !expanded"
      >
        <screen-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="12" />
        {{ expanded ? '收起条件' : '更多条件' }}
      </button>

      <div class="facility-search__actions">
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
  ScreenButton,
  ScreenIcon
} from '@/components/screen'
import { queryLandDictItems } from '@/api/land/landAdmin'
import {
  XZQH_LIST,
  FACILITY_CATEGORIES,
  PROJECT_TYPES,
  dictDefinitions,
  compactQuery
} from '../constants'

/** 所有检索字段的初始空值，重置时回到这份结构 */
function buildEmptyQuery () {
  return {
    crzdbh: '',
    /** ★ 后端复用 srr 承载『配套项目名称』模糊条件，界面标签写的是配套项目名称 */
    srr: '',
    ptsslb: '',
    xzqh: '',
    dkmc: '',
    xmfl: '',
    facilityKeyword: ''
  }
}

export default {
  name: 'FacilitySearchForm',
  components: {
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenButton,
    ScreenIcon
  },
  props: {
    /** 是否默认展开全部条件 */
    defaultExpanded: { type: Boolean, default: false },
    /** 初始条件（下钻时带进来的 crzdbh 等） */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      expanded: this.defaultExpanded,
      query: Object.assign(buildEmptyQuery(), this.initialQuery || {}),
      // 先给兜底，created 里再用字典覆盖（字典接口挂了页面也能筛）
      xzqhOptions: XZQH_LIST.map((item) => ({ value: item, label: item })),
      categoryOptions: FACILITY_CATEGORIES.map((item) => ({ value: item, label: item })),
      projectTypeOptions: PROJECT_TYPES.map((item) => ({ value: item, label: item }))
    }
  },
  created () {
    this.loadDicts()
  },
  methods: {
    /** 用 jeecg 字典覆盖下拉（失败静默，兜底常量已经把六个字典抄全了） */
    loadDicts () {
      queryLandDictItems(dictDefinitions()).then((dicts) => {
        if (dicts.xzqh) this.xzqhOptions = dicts.xzqh
        if (dicts.facilityCategory) this.categoryOptions = dicts.facilityCategory
        if (dicts.projectType) this.projectTypeOptions = dicts.projectType
      })
    },

    handleSearch () {
      this.$emit('search', this.getQuery())
    },

    handleReset () {
      this.query = buildEmptyQuery()
      this.$emit('reset')
      // 重置后立即检索一次，让列表回到全量状态（与档案模块行为一致）
      this.$emit('search', this.getQuery())
    },

    /** 对外暴露当前条件，供「按条件导出 / 批量移除」使用同一份过滤条件 */
    getQuery () {
      return compactQuery(this.query)
    },

    /**
     * 用一组新条件覆盖当前表单（下钻 / applyDrill 时调用）。
     * ★ 为什么需要这个方法：initialQuery 只在 data() 里读一次，组件挂载后就改不动了。
     *   若新条件里带了非常驻字段，顺手把「更多条件」展开，否则用户会以为条件没生效。
     */
    setQuery (query) {
      const next = Object.assign(buildEmptyQuery(), query || {})
      this.query = next
      const hidden = ['dkmc', 'xmfl', 'facilityKeyword']
      if (hidden.some((key) => next[key] !== '' && next[key] !== undefined && next[key] !== null)) {
        this.expanded = true
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.facility-search {
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
  .facility-search__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1360px) {
  .facility-search__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .facility-search__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .facility-search__foot {
    flex-wrap: wrap;
  }
}
</style>
