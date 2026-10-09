<template>
  <!--
    EscalationSearchForm 提级论证检索条件（9 个条件，设计文档 5.5）
    --------------------------------
    「项目录入 / 查询统计 / 资料及台账管理」三个页面共用同一套检索条件。

    默认只露出 4 个最常用的条件（项目名称 / 申报单位 / 申报时间 / 论证结果），
    其余 5 个（项目类型 / 行政区划 / 办理状态 / 是否土地整理 / 投资额区间）
    收在「更多条件」后面，避免一进页面就被 9 个控件淹没；
    查询统计页用 default-expanded 直接全展开。

    条件键名与后端 EscalationQueryDTO 严格对齐（申=declare、论证=arg、地块=dk），
    尤其注意投资额是 **beginInvestment / endInvestment**：
    admin-client 的表单里写成了 minInvestment / maxInvestment，后端 DTO 里没有这两个字段，
    静默失效（筛不出来也不报错），这里按 DTO 修正。

    事件：
      search (query)  点击「查询」或按回车时抛出，query 已剔除空值
      reset           点击「重置」时抛出（同时也会抛 search，父组件只需监听 search）

    公开方法：
      getQuery()        取当前条件（已剔除空值），供导出复用同一份条件
      setQuery(query)   用一组新条件覆盖当前表单（统计图表下钻 / 台账回填时调用）
  -->
  <div class="escalation-search">
    <div class="escalation-search__grid">
      <!-- ---------- 常驻的 4 个核心条件 ---------- -->
      <screen-field label="项目名称" label-width="80px" html-for="esc-search-projectName">
        <screen-input
          id="esc-search-projectName"
          v-model="query.projectName"
          clearable
          placeholder="按项目名称模糊检索"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="申报单位" label-width="80px" html-for="esc-search-declareDept">
        <screen-input
          id="esc-search-declareDept"
          v-model="query.declareDept"
          clearable
          placeholder="按申报单位模糊检索"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="申报时间" label-width="80px">
        <screen-date-input
          v-model="declareRange"
          mode="range"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="handleDeclareRangeChange"
        />
      </screen-field>

      <screen-field label="论证结果" label-width="80px">
        <screen-select
          v-model="query.argResult"
          :options="argResultOptions"
          placeholder="全部结果"
          aria-label="论证结果"
        />
      </screen-field>

      <!-- ---------- 更多条件（5 个） ---------- -->
      <template v-if="expanded">
        <screen-field label="项目类型" label-width="80px">
          <screen-select
            v-model="query.projectType"
            :options="projectTypeOptions"
            placeholder="全部类型"
            aria-label="项目类型"
          />
        </screen-field>

        <screen-field label="行政区划" label-width="80px">
          <screen-select
            v-model="query.xzqh"
            :options="xzqhOptions"
            placeholder="全部区划"
            aria-label="行政区划"
          />
        </screen-field>

        <screen-field label="办理状态" label-width="80px">
          <screen-select
            v-model="query.status"
            :options="statusOptions"
            placeholder="全部状态"
            aria-label="办理状态"
          />
        </screen-field>

        <screen-field label="土地整理" label-width="80px">
          <screen-select
            v-model="query.tdzlProject"
            :options="ynOptions"
            placeholder="全部"
            aria-label="是否土地整理项目"
          />
        </screen-field>

        <screen-field label="总投资" label-width="80px">
          <div class="escalation-search__range">
            <screen-input
              v-model="query.beginInvestment"
              type="number"
              placeholder="下限"
              @enter="handleSearch"
            />
            <span class="escalation-search__range-sep" aria-hidden="true">—</span>
            <screen-input
              v-model="query.endInvestment"
              type="number"
              placeholder="上限"
              @enter="handleSearch"
            />
            <span class="escalation-search__range-unit">亿元</span>
          </div>
        </screen-field>
      </template>
    </div>

    <div class="escalation-search__foot">
      <button
        type="button"
        class="escalation-search__toggle"
        :aria-expanded="expanded ? 'true' : 'false'"
        @click="expanded = !expanded"
      >
        <screen-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="12" />
        {{ expanded ? '收起条件' : '更多条件' }}
      </button>

      <div class="escalation-search__actions">
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
  ScreenIcon,
} from '@/components/screen'
import { queryXzqhOptions } from '@/api/land/landData'
import {
  STATUS_OPTIONS,
  ARG_RESULT_OPTIONS,
  PROJECT_TYPE_OPTIONS,
  YN_OPTIONS,
  toOptions,
  compactQuery,
} from '../constants'

/** 所有检索字段的初始空值，重置与 setQuery 都回到这份结构 */
function buildEmptyQuery () {
  return {
    projectName: '',
    declareDept: '',
    beginDeclareDate: '',
    endDeclareDate: '',
    argResult: '',
    projectType: '',
    xzqh: '',
    status: '',
    tdzlProject: '',
    beginInvestment: '',
    endInvestment: '',
  }
}

/** 常驻（未折叠）条件下不展示、但确实存在的字段：setQuery 时据此自动展开 */
const HIDDEN_FIELDS = [
  'projectType', 'xzqh', 'status', 'tdzlProject', 'beginInvestment', 'endInvestment',
]

export default {
  name: 'EscalationSearchForm',
  components: {
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon,
  },
  props: {
    /** 是否默认展开全部条件（查询统计页传 true） */
    defaultExpanded: { type: Boolean, default: false },
    /**
     * 初始条件（例如从查询统计的图表下钻带的 argResult / status）。
     * 只在 data() 初始化时应用一次，之后完全由本组件自己维护；
     * 组件已挂载后再换条件请调 setQuery()。
     */
    initialQuery: { type: Object, default: () => ({}) },
  },
  data () {
    const initial = Object.assign(buildEmptyQuery(), this.initialQuery || {})
    return {
      expanded:
        this.defaultExpanded ||
        HIDDEN_FIELDS.some((key) => initial[key] !== '' && initial[key] !== undefined && initial[key] !== null),
      query: initial,
      /** 日期区间是数组，单独存一份，回显时再由 query 里的两段拼出来 */
      declareRange:
        initial.beginDeclareDate || initial.endDeclareDate
          ? [initial.beginDeclareDate || '', initial.endDeclareDate || '']
          : [],
      xzqhOptions: [],
      argResultOptions: toOptions(ARG_RESULT_OPTIONS),
      projectTypeOptions: toOptions(PROJECT_TYPE_OPTIONS),
      statusOptions: toOptions(STATUS_OPTIONS),
      ynOptions: YN_OPTIONS,
    }
  },
  created () {
    this.loadXzqh()
  },
  methods: {
    /**
     * 区划下拉。
     * 失败只静默降级为空选项：它只是一个辅助筛选条件，
     * 拉不到区划不应该拦住用户查列表。
     */
    loadXzqh () {
      queryXzqhOptions()
        .then((res) => {
          if (!res || !res.success) return
          const list = res.result || []
          this.xzqhOptions = list.map((item) => {
            if (item && item.value !== undefined) return { value: item.value, label: item.label }
            const value = item && (item.xzqh || item.name)
            return { value, label: value }
          })
        })
        .catch(() => {
          this.xzqhOptions = []
        })
    },

    handleDeclareRangeChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginDeclareDate = values[0] || ''
      this.query.endDeclareDate = values[1] || ''
    },

    handleSearch () {
      this.$emit('search', this.getQuery())
    },

    handleReset () {
      this.query = buildEmptyQuery()
      this.declareRange = []
      this.$emit('reset')
      // 重置后立即检索一次，让列表回到全量状态（与 admin-client 行为一致）
      this.$emit('search', this.getQuery())
    },

    /** 对外暴露当前条件，供「导出 Excel」使用同一份过滤条件（看到什么就导出什么） */
    getQuery () {
      return compactQuery(this.query)
    },

    /**
     * 用一组新条件覆盖当前表单（查询统计的图表下钻 → 台账时调用）。
     * 需要这个方法的原因：initialQuery 只在 data() 里读一次，
     * 组件已经挂载后就改不动了；
     * 同时新条件里若带了非常驻字段，顺手展开「更多条件」，
     * 否则用户会以为条件没生效。
     */
    setQuery (query) {
      const next = Object.assign(buildEmptyQuery(), query || {})
      this.query = next
      this.declareRange =
        next.beginDeclareDate || next.endDeclareDate
          ? [next.beginDeclareDate || '', next.endDeclareDate || '']
          : []
      if (HIDDEN_FIELDS.some((key) => next[key] !== '' && next[key] !== undefined && next[key] !== null)) {
        this.expanded = true
      }
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.escalation-search {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  &__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  &__range {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    min-width: 0;
  }

  &__range-sep,
  &__range-unit {
    flex: none;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
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
  .escalation-search__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1360px) {
  .escalation-search__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .escalation-search__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .escalation-search__foot {
    flex-wrap: wrap;
  }
}
</style>
