<template>
  <!--
    ArchiveSearchForm 档案检索条件
    --------------------------------
    「档案维护」与「档案查询」共用的一套检索条件。默认只露出 4 个最常用的条件
    （配套项目 / 出让宗地编号 / 档案类别 / 归档日期），其余 10 个收在「更多条件」后面，
    避免一进来就被 14 个输入框淹没。

    事件：
      search (query)  点击「查询」或按回车时抛出，query 已剔除空值
      reset           点击「重置」时抛出（同时也会抛 search，父组件只需监听 search）

    公开方法：
      getQuery()  取当前条件（已剔除空值），供「按条件导出 ZIP」复用同一份条件
  -->
  <div class="archive-search">
    <div class="archive-search__grid">
      <!-- ---------- 常驻的 4 个核心条件 ---------- -->
      <screen-field label="配套项目" label-width="76px" html-for="search-ptxmmc">
        <screen-input
          id="search-ptxmmc"
          v-model="query.ptxmmc"
          clearable
          placeholder="按项目名称模糊检索"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="宗地编号" label-width="76px" html-for="search-crzdbh">
        <screen-input
          id="search-crzdbh"
          v-model="query.crzdbh"
          clearable
          placeholder="出让宗地编号"
          @enter="handleSearch"
        />
      </screen-field>

      <screen-field label="档案类别" label-width="76px">
        <category-picker
          ref="category"
          v-model="query.categoryId"
          :leaf-only="false"
          placeholder="可选父类别（含子类）"
          @change="handleCategoryChange"
        />
      </screen-field>

      <screen-field label="归档日期" label-width="76px">
        <screen-date-input v-model="dateRange" mode="range" @change="handleDateChange" />
      </screen-field>

      <!-- ---------- 更多条件 ---------- -->
      <template v-if="expanded">
        <screen-field label="档案号" label-width="76px" html-for="search-archiveNo">
          <screen-input id="search-archiveNo" v-model="query.archiveNo" clearable placeholder="档案号" @enter="handleSearch" />
        </screen-field>

        <screen-field label="档案名称" label-width="76px" html-for="search-archiveName">
          <screen-input id="search-archiveName" v-model="query.archiveName" clearable placeholder="档案名称" @enter="handleSearch" />
        </screen-field>

        <screen-field label="文件名" label-width="76px" html-for="search-fileName">
          <screen-input id="search-fileName" v-model="query.fileName" clearable placeholder="卷内文件名" @enter="handleSearch" />
        </screen-field>

        <screen-field label="行政区划" label-width="76px">
          <screen-select v-model="query.xzqh" :options="xzqhOptions" placeholder="全部区划" aria-label="行政区划" />
        </screen-field>

        <screen-field label="档案年度" label-width="76px">
          <screen-select v-model="query.archiveYear" :options="yearOptions" placeholder="全部年度" aria-label="档案年度" />
        </screen-field>

        <screen-field label="责任部门" label-width="76px">
          <screen-select v-model="query.responsibleDept" :options="deptOptions" placeholder="全部部门" aria-label="责任部门" />
        </screen-field>

        <screen-field label="配套负责人" label-width="76px" html-for="search-responsibleUser">
          <screen-input id="search-responsibleUser" v-model="query.responsibleUser" clearable placeholder="负责人姓名" @enter="handleSearch" />
        </screen-field>

        <screen-field label="档案状态" label-width="76px">
          <screen-select v-model="query.status" :options="statusOptions" placeholder="全部状态" aria-label="档案状态" />
        </screen-field>

        <screen-field label="密级" label-width="76px">
          <screen-select v-model="query.secretLevel" :options="secretOptions" placeholder="全部密级" aria-label="密级" />
        </screen-field>

        <screen-field label="来源" label-width="76px">
          <screen-select v-model="query.sourceType" :options="SOURCE_TYPES" placeholder="全部来源" aria-label="档案来源" />
        </screen-field>
      </template>
    </div>

    <div class="archive-search__foot">
      <button
        type="button"
        class="archive-search__toggle"
        :aria-expanded="expanded ? 'true' : 'false'"
        @click="expanded = !expanded"
      >
        <screen-icon :name="expanded ? 'chevron-up' : 'chevron-down'" :size="12" />
        {{ expanded ? '收起条件' : '更多条件' }}
      </button>

      <div class="archive-search__actions">
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
import CategoryPicker from './CategoryPicker.vue'
import { queryXzqhOptions } from '@/api/land/landData'
import {
  DEPTS,
  ARCHIVE_STATUSES,
  SECRET_LEVELS,
  SOURCE_TYPES,
  buildYearOptions,
  toOptions,
  compactQuery,
} from '../constants'

/** 所有检索字段的初始空值，重置时回到这份结构 */
function buildEmptyQuery () {
  return {
    archiveNo: '',
    archiveName: '',
    ptxmmc: '',
    crzdbh: '',
    dkmc: '',
    fileName: '',
    categoryId: '',
    categoryPath: '',
    xzqh: '',
    archiveYear: '',
    responsibleDept: '',
    responsibleUser: '',
    status: '',
    secretLevel: '',
    sourceType: '',
    beginDate: '',
    endDate: '',
  }
}

export default {
  name: 'ArchiveSearchForm',
  components: {
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon,
    CategoryPicker,
  },
  props: {
    /** 是否默认展开全部条件（档案查询页传 true） */
    defaultExpanded: { type: Boolean, default: false },
    /** 初始条件（例如从统计页按项目下钻带的 ptxmmc / crzdbh） */
    initialQuery: { type: Object, default: () => ({}) },
  },
  data () {
    return {
      expanded: this.defaultExpanded,
      query: Object.assign(buildEmptyQuery(), this.initialQuery || {}),
      dateRange: [],
      xzqhOptions: [],
      yearOptions: buildYearOptions().map((year) => ({ value: year, label: `${year} 年` })),
      deptOptions: toOptions(DEPTS),
      statusOptions: toOptions(ARCHIVE_STATUSES),
      secretOptions: toOptions(SECRET_LEVELS),
      SOURCE_TYPES,
    }
  },
  created () {
    this.loadXzqh()
    // 回显日期区间：initialQuery 里是分开的 beginDate / endDate，这里合成一个数组
    if (this.query.beginDate || this.query.endDate) {
      this.dateRange = [this.query.beginDate || '', this.query.endDate || '']
    }
  },
  methods: {
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
          // 区划只是辅助筛选条件，加载失败不影响主流程，静默降级为空选项
          this.xzqhOptions = []
        })
    },
    /**
     * 选中类别后同时记录 categoryPath。
     * 后端约定：有 categoryPath 时按路径前缀检索（父类别 = 含所有子类），
     * 只有 categoryId 时是精确匹配。
     */
    handleCategoryChange (categoryId) {
      if (!categoryId) {
        this.query.categoryPath = ''
        return
      }
      const node = this.$refs.category ? this.$refs.category.findNode(categoryId) : null
      this.query.categoryPath = node && node.path ? node.path : ''
    },
    handleDateChange (range) {
      const values = Array.isArray(range) ? range : ['', '']
      this.query.beginDate = values[0] || ''
      this.query.endDate = values[1] || ''
    },
    handleSearch () {
      this.$emit('search', this.getQuery())
    },
    handleReset () {
      // 整体替换 query 会把 categoryId 置空，CategoryPicker 是受控的，会跟着清掉选中态
      this.query = buildEmptyQuery()
      this.dateRange = []
      this.$emit('reset')
      // 重置后立即检索一次，让列表回到全量状态（与 admin-client 行为一致）
      this.$emit('search', this.getQuery())
    },
    /** 对外暴露当前条件，供「按条件导出 ZIP」使用同一份过滤条件 */
    getQuery () {
      return compactQuery(this.query)
    },

    /**
     * 用一组新条件覆盖当前表单（统计页按项目下钻时调用）。
     * 之所以需要这个方法：initialQuery 只在 data() 里读一次，
     * 组件已经挂载后就改不动了。如果新条件里带了非常驻字段，
     * 顺手把「更多条件」展开，否则用户会以为条件没生效。
     */
    setQuery (query) {
      const next = Object.assign(buildEmptyQuery(), query || {})
      this.query = next
      this.dateRange = next.beginDate || next.endDate ? [next.beginDate || '', next.endDate || ''] : []

      const hiddenFields = ['archiveNo', 'archiveName', 'fileName', 'xzqh', 'archiveYear',
        'responsibleDept', 'responsibleUser', 'status', 'secretLevel', 'sourceType']
      if (hiddenFields.some((key) => next[key] !== '' && next[key] !== undefined && next[key] !== null)) {
        this.expanded = true
      }
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.archive-search {
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
  .archive-search__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1360px) {
  .archive-search__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .archive-search__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .archive-search__foot {
    flex-wrap: wrap;
  }
}
</style>
