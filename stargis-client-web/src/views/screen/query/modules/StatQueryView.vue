<template>
  <!--
    StatQueryView 单个数据集的查询视图（经营用地 / 用地配套项目共用一套）
    ===============================================================
    版式（对齐高保真 `查询统计.html`）：
      条件面板    横向排开的基础条件 + 清除 / 查询 / 导出
      分析结果    指标格子（StatSummaryPanel）
      结果表      自定义列 + 表格 + 分页（QueryResultPanel）

    ★ 为什么两个数据集共用一个组件、靠 `data-type` 分支：
      它们的骨架完全一样（条件 → 统计 → 明细 → 导出），差别只在「有哪些条件字段」
      和「分析结果有哪些指标」。写成两个组件的话，分页、列设置、导出、错误提示
      这四段逻辑要各写一遍，日后必然漂移 —— 而「两个页签的数字口径不同」
      是后端 StatSummaryVO 已经讲清楚的事，不需要前端再用两份代码强化。

    ★ 「多值条件」在界面上先做单选：
      后端条件接受多值（逗号分隔 / 数组），因为旧系统 getCnd 就是多选拼 OR；
      但大屏的下拉是单选组件，一次选一个最直观。要「几个区一起查」时，
      用「查询导出」页签的高级查询（关系符 OR）即可 —— 能力不缺，只是入口不同。
  -->
  <div class="stat-query">
    <!-- ---------- 条件 ---------- -->
    <screen-panel class="stat-query__condition" title="基础查询">
      <div class="stat-query__grid">
        <screen-field
          v-for="field in conditionFields"
          :key="field.key"
          :label="field.label"
          :label-width="field.labelWidth || 96"
          :html-for="`sq-${dataType}-${field.key}`"
        >
          <screen-select
            v-if="field.options"
            :id="`sq-${dataType}-${field.key}`"
            v-model="conditions[field.key]"
            :options="field.options"
            :placeholder="field.placeholder || '全部'"
            :searchable="field.searchable !== false"
            clearable
            :aria-label="field.label"
          />
          <screen-input
            v-else
            :id="`sq-${dataType}-${field.key}`"
            v-model="conditions[field.key]"
            :placeholder="field.placeholder || '输入后回车查询'"
            clearable
            @enter="handleSearch"
          />
        </screen-field>
      </div>

      <template #footer>
        <div class="stat-query__actions">
          <screen-button size="sm" icon="close" @click="handleReset">清除</screen-button>
          <screen-button size="sm" type="primary" icon="search" :loading="loading" @click="handleSearch">
            查询
          </screen-button>
          <screen-button size="sm" icon="download" :disabled="!pagination.total" @click="handleExport">
            导出 Excel
          </screen-button>
          <span class="stat-query__tip">
            导出的 Excel 含两个页签：当前筛选结果（按自定义列）+ 分析结果
          </span>
        </div>
      </template>
    </screen-panel>

    <!-- ---------- 分析结果 ---------- -->
    <stat-summary-panel
      class="stat-query__summary"
      :metrics="metrics"
      :summary="summary"
      :loading="loading"
    />

    <!-- ---------- 结果表 ---------- -->
    <query-result-panel
      class="stat-query__result"
      :title="dataType === 'facility' ? '配套查询结果' : '查询结果'"
      :columns="columns"
      :selected-keys="selectedKeys"
      :rows="rows"
      :total="pagination.total"
      :loading="loading"
      :page-no="pagination.current"
      :page-size="pagination.pageSize"
      :page-size-options="pageSizeOptions"
      :link-keys="linkKeys"
      @toggle-column="handleToggleColumn"
      @reset-columns="handleResetColumns"
      @page-change="handlePageChange"
      @refresh="loadAll"
      @export="handleExport"
      @open-detail="handleOpenDetail"
    />

    <!-- 经营性用地：点宗地编号 → 看这块地下有哪些配套（不受查询条件影响） -->
    <land-facility-detail-modal
      v-if="detailEnabled"
      :visible="detailVisible"
      :crzdbh="detailCrzdbh"
      :columns="facilityColumns"
      @close="detailVisible = false"
    />

    <!-- 用地配套项目：点配套项目名称 → 看该配套的环节进展 / 时间节点 / 责任单位 -->
    <facility-detail-modal
      v-if="!detailEnabled"
      :visible="facilityDetailVisible"
      :facility-id="facilityDetailId"
      :columns="columns"
      @close="facilityDetailVisible = false"
    />
  </div>
</template>

<script>
import {
  ScreenButton,
  ScreenField,
  ScreenInput,
  ScreenPanel,
  ScreenSelect
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  buildExportUrl,
  queryColumns,
  queryFacilityPage,
  queryFacilityStats,
  queryLandPage,
  queryLandStats,
  queryOptions
} from '@/api/land/statQuery'
import {
  APPROVAL_OPTIONS,
  BUILD_NATURE_OPTIONS,
  PAGE_SIZE_OPTIONS,
  PROJECT_TYPE_OPTIONS,
  YES_NO_OPTIONS,
  metricsOf
} from '../constants'
import QueryResultPanel from './QueryResultPanel.vue'
import StatSummaryPanel from './StatSummaryPanel.vue'
import LandFacilityDetailModal from './LandFacilityDetailModal.vue'
import FacilityDetailModal from './FacilityDetailModal.vue'

export default {
  name: 'StatQueryView',
  components: {
    FacilityDetailModal,
    LandFacilityDetailModal,
    QueryResultPanel,
    ScreenButton,
    ScreenField,
    ScreenInput,
    ScreenPanel,
    ScreenSelect,
    StatSummaryPanel
  },
  props: {
    /** 'land' | 'facility' */
    dataType: { type: String, required: true }
  },
  data () {
    return {
      /** 后端字段元数据（条件下拉 / 自定义列 / 导出列的唯一来源） */
      columns: [],
      /** 条件候选值 */
      options: {},
      /** 当前条件（值是字符串；空串表示不限） */
      conditions: {},
      /** 选中的展示列 */
      selectedKeys: [],
      rows: [],
      summary: {},
      loading: false,
      pagination: { current: 1, pageSize: 20, total: 0 },
      pageSizeOptions: PAGE_SIZE_OPTIONS,
      /** 地块配套详情（经营性用地：点宗地编号） */
      detailVisible: false,
      detailCrzdbh: '',
      /** 配套项目详情（用地配套项目：点配套项目名称） */
      facilityDetailVisible: false,
      facilityDetailId: '',
      /** 配套字段元数据（两个页签都可能用：宗地页签的地块配套详情也用它取中文名） */
      facilityColumns: []
    }
  },
  computed: {
    metrics () {
      return metricsOf(this.dataType)
    },
    /** 只有经营性用地需要「点编号看该宗地配套」；配套页签点的是配套项目名称 */
    detailEnabled () {
      return this.dataType === 'land'
    },
    /**
     * 结果表里哪一列做成可点链接（点开对应详情）。
     * 宗地 → 宗地编号（看该宗地下的配套）；配套 → 配套项目名称（看该配套的进展）。
     */
    linkKeys () {
      return this.detailEnabled ? ['crzdbh'] : ['ptxmmc']
    },
    conditionFields () {
      if (this.dataType === 'facility') {
        return [
          this.selectField('ptxmmc', '配套项目名称', this.multiOptions('ptxmmc'), { searchable: true, labelWidth: 104 }),
          this.selectField('ptsslb', '配套设施类别', this.multiOptions('ptsslb')),
          this.selectField('dkmc', '地块名称', this.multiOptions('dkmc'), { searchable: true }),
          this.selectField('xzqh', '行政区划', this.multiOptions('xzqh')),
          this.selectField('xmfl', '项目分类', PROJECT_TYPE_OPTIONS),
          this.selectField('jsxx', '建设性质', BUILD_NATURE_OPTIONS),
          this.selectField('sfkg', '是否开工', YES_NO_OPTIONS),
          this.selectField('sfjg', '是否竣工', YES_NO_OPTIONS),
          this.selectField('sfyj', '是否移交', YES_NO_OPTIONS),
          this.selectField('sfzsjtjlz', '涉及提级论证', YES_NO_OPTIONS, { labelWidth: 104 }),
          this.selectField('tjlzsftg', '提级论证通过', YES_NO_OPTIONS, { labelWidth: 104 }),
          this.selectField('xjpfsfwc', '项建批复完成', APPROVAL_OPTIONS, { labelWidth: 104 }),
          this.selectField('kypfsfwc', '可研批复完成', APPROVAL_OPTIONS, { labelWidth: 104 }),
          this.selectField('csjgspfwc', '初设概算批复', APPROVAL_OPTIONS, { labelWidth: 104 }),
          // 资金落实情况：候选值取自库里实际出现过的值（已落实 / 未落实 …），
          // ★ 与首页看板「资金未落实」指标同源字段（zjlsqk='未落实'），所以查询与看板能对上
          this.selectField('zjlsqk', '资金落实情况', this.multiOptions('zjlsqk'), { labelWidth: 104 })
        ]
      }
      return [
        this.selectField('dkmc', '地块名称', this.multiOptions('dkmc'), { searchable: true, labelWidth: 88 }),
        this.selectField('xzqh', '行政区划', this.multiOptions('xzqh'), { labelWidth: 88 }),
        this.selectField('crsjYears', '出让时间', this.multiOptions('crsjYears'), {
          labelWidth: 88,
          placeholder: '全部年份'
        }),
        this.selectField('srr', '建设单位', this.multiOptions('srr'), { searchable: true, labelWidth: 88 }),
        this.selectField('xmfl', '项目分类', PROJECT_TYPE_OPTIONS, { labelWidth: 88 })
      ]
    }
  },
  created () {
    // ★ 必须先把每个条件的 key 都初始化成 ''：
    //   Vue 2 对「对象上原本不存在的 key」赋值不会触发响应，
    //   而 ScreenSelect 的显示值来自 `:value="conditions[key]"` ——
    //   没初始化的话，用户选完下拉会「选了但显示不出来」，可条件已经进了请求。
    this.conditions = this.emptyConditions()
    this.loadMeta()
  },
  methods: {
    /** 空条件对象（key 取当前数据集的条件字段） */
    emptyConditions () {
      const empty = {}
      this.conditionFields.forEach((field) => { empty[field.key] = '' })
      return empty
    },
    /** 候选值 → ScreenSelect 的 options 结构 */
    multiOptions (key) {
      const list = this.options[key] || []
      return list.map((item) => ({ value: String(item), label: String(item) }))
    },
    selectField (key, label, options, extra) {
      return Object.assign({ key, label, options }, extra || {})
    },

    /**
     * 加载元数据：字段清单 + 条件候选值。
     * ★ 先拿字段清单再查数据：自定义列的默认值来自 `defaultColumn`，
     *   写死在前端就会与后端字段定义漂移。
     */
    async loadMeta () {
      this.loading = true
      try {
        const [colRes, optRes] = await Promise.all([
          queryColumns(this.dataType),
          queryOptions(this.dataType)
        ])
        if (!colRes || !colRes.success) {
          toast.error((colRes && colRes.message) || '字段定义加载失败')
          return
        }
        this.columns = colRes.result || []
        this.selectedKeys = this.columns
          .filter((column) => column.defaultColumn)
          .map((column) => column.key)
        this.options = (optRes && optRes.result) || {}
        // 配套字段元数据：宗地页签的「地块配套详情」与配套页签的「配套项目详情」都用它取中文名，
        // 所以两个数据集都取（配套页签只是恰好与主 columns 相同）。
        const facilityRes = await queryColumns('facility')
        this.facilityColumns = (facilityRes && facilityRes.success && facilityRes.result) || []
      } finally {
        this.loading = false
        await this.loadAll(1)
      }
    },

    /**
     * 查询：明细与分析结果一起发。
     * ★ 一定要同一个条件对象：两次调用各拼一次条件，稍有差异就会出现
     *   「表格 12 条、统计说 15 条」这种最难解释的问题。
     */
    async loadAll (pageNo) {
      const current = typeof pageNo === 'number' ? pageNo : this.pagination.current
      const params = this.buildParams(current)
      this.loading = true
      try {
        const pagePromise = this.dataType === 'facility'
          ? queryFacilityPage(params)
          : queryLandPage(params)
        const statsPromise = this.dataType === 'facility'
          ? queryFacilityStats(params)
          : queryLandStats(params)
        const [pageRes, statsRes] = await Promise.all([pagePromise, statsPromise])

        if (!pageRes || !pageRes.success) {
          toast.error((pageRes && pageRes.message) || '查询失败')
          return
        }
        const page = pageRes.result || {}
        this.rows = page.rows || []
        this.pagination = Object.assign({}, this.pagination, {
          current: Number(page.pageNo || current),
          pageSize: Number(page.pageSize || this.pagination.pageSize),
          total: Number(page.total || 0)
        })
        this.summary = (statsRes && statsRes.success && statsRes.result) || {}
      } catch (e) {
        toast.error('查询失败：' + (e && e.message ? e.message : '网络异常'))
      } finally {
        this.loading = false
      }
    },

    /** 条件 → 请求参数（空值不下发；多值在界面上是单选，所以直接单值传） */
    buildParams (pageNo) {
      const params = {
        dataType: this.dataType,
        pageNo,
        pageSize: this.pagination.pageSize,
        columns: this.selectedKeys.join(',')
      }
      Object.keys(this.conditions).forEach((key) => {
        const value = this.conditions[key]
        if (value !== null && value !== undefined && String(value).trim() !== '') {
          params[key] = String(value).trim()
        }
      })
      return params
    },

    handleSearch () {
      this.loadAll(1)
    },

    handleReset () {
      const cleared = {}
      Object.keys(this.conditions).forEach((key) => { cleared[key] = '' })
      this.conditions = cleared
      this.loadAll(1)
    },

    handlePageChange (payload) {
      const current = payload && payload.current ? payload.current : this.pagination.current
      const pageSize = payload && payload.pageSize ? payload.pageSize : this.pagination.pageSize
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadAll(current)
    },

    handleToggleColumn (key) {
      const index = this.selectedKeys.indexOf(key)
      if (index > -1) {
        // 至少留一列：全取消后表格会变成一条空行，看起来像「没查到数据」
        if (this.selectedKeys.length === 1) {
          toast.warning('至少要保留一列')
          return
        }
        this.selectedKeys = this.selectedKeys.filter((item) => item !== key)
      } else {
        this.selectedKeys = this.selectedKeys.concat(key)
      }
    },

    handleResetColumns () {
      this.selectedKeys = this.columns
        .filter((column) => column.defaultColumn)
        .map((column) => column.key)
    },

    /**
     * 导出：走 `window.open` + token 查询串（与档案导出同一套）。
     * 条件与列表页**完全一致**（含自定义列），所以「导出的行 = 屏幕上的行」。
     */
    handleExport () {
      const params = this.buildParams(1)
      delete params.pageNo
      delete params.pageSize
      window.open(buildExportUrl(params), '_blank')
    },

    handleOpenDetail (row) {
      if (!row) {
        return
      }
      if (this.detailEnabled) {
        // 经营性用地：按宗地编号查该宗地下的全部配套
        if (!row.crzdbh) {
          return
        }
        this.detailCrzdbh = row.crzdbh
        this.detailVisible = true
        return
      }
      // 用地配套项目：按行标识（id）查该配套的详情
      if (!row.id) {
        toast.warning('这一行没有行标识，无法打开详情（请刷新后重试）')
        return
      }
      this.facilityDetailId = String(row.id)
      this.facilityDetailVisible = true
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

/*
  纵向三段：条件（自适应高度）/ 分析结果（自适应）/ 结果表（吃掉剩余空间）。
  ★ 结果表用 flex: 1 1 0 而不是固定高度：屏幕矮的时候它自己缩，
    屏幕高的时候它多显示几行 —— 定死高度在 1080p 与 1440p 上必有一边难看。
*/
.stat-query {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;

  &__condition {
    flex: 0 0 auto;
  }

  &__summary {
    flex: 0 0 auto;
  }

  &__result {
    flex: 1 1 0;
    min-height: 220px;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px 16px;

    @media (max-width: 1600px) {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__tip {
    margin-left: auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }
}
</style>
