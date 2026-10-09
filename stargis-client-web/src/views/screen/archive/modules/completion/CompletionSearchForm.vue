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
    endAcceptanceDate: ''
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
    initialQuery: { type: Object, default: () => ({}) }
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
      hasArchiveOptions: HAS_ARCHIVE_OPTIONS
    }
  },
  created () {
    this.normalizeHasArchive()
    this.loadXzqh()
    this.syncRanges(this.query)
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
     * 区划下拉来自 Java 业务后端的共享接口（与「档案查询」用同一个）。
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
      this.$emit('reset')
      // 重置后立即检索一次，让列表回到全量状态
      this.$emit('search', this.getQuery())
    },

    /** 对外暴露当前条件（已归一化），供「导出查询结果」使用同一份过滤条件 */
    getQuery () {
      return normalizeQuery(this.query)
    },

    /**
     * 用一组新条件覆盖当前表单（外部下钻时调用）。
     * 之所以需要这个方法：initialQuery 只在 data() 里读一次，组件挂载后就改不动了。
     * 若新条件里带了非常驻字段，顺手展开「更多条件」——否则用户会以为条件没生效。
     */
    setQuery (query) {
      const next = Object.assign(buildEmptyQuery(), query || {})
      this.query = next
      this.normalizeHasArchive()
      this.syncRanges(next)
      if (needsExpand(next)) this.expanded = true
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
