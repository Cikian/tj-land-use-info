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
      2026-11 起本页也提供了「自定义条件」（同一个 QueryConditionBuilder），
      所以在这个页签里就能拼 AND/OR，不必再切页签。

    ★ 对外契约（父组件 query/index.vue 按名字调用，改名要同步改父级）：
      resetFilters()   清条件（常用 + 自定义）、页码回 1、清定位高亮，然后按空条件重查
      applyDrill(o)    路由带着业务条件下钻进来时调用：覆盖式设置条件后重查
      这两个方法也是清单 P2-01「普通离开再进入模块时清空检索条件」的落点。

    ★ 定位（清单 P1-12）：结果行的「定位」按行 id 取 geoDetail，再用
      createGeoLayer 在**共享地图**（window.viewer）上铺单块高亮 + 相机飞行。
      不新建 Cesium Viewer；切页签 / 销毁时 clearAll，不留残影给数据管理页。
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
            :filter-local="field.filterLocal !== false"
            clearable
            :aria-label="field.label"
            @search="handleOptionSearch(field.key, $event)"
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

      <!--
        「当前筛选」：常用条件 + 自定义条件合并后的**实际生效条件**。
        ★ 为什么必须有这一行（清单 P2-01 第 4 条）：自定义条件收起后，
          用户看到的只是几个下拉，真正在下发的 AND/OR 组合是隐藏的；
          没有这行就是「看似全量、实际仍过滤」。
      -->
      <div class="stat-query__filters">
        <span class="stat-query__filters-label">当前筛选</span>
        <span class="stat-query__filters-text">{{ filterSummary }}</span>
        <button
          v-if="hasFilters"
          type="button"
          class="stat-query__filters-clear"
          @click="handleReset"
        >
          全部清除
        </button>
      </div>

      <!--
        自定义条件（清单 P2-11 第 2 条）：与「查询导出」页签同一个构造器。
        ★ 用 v-show 不用 v-if：构造器里的 validate() 要按 ref 调用，
          收起后再点「查询」就取不到校验结果了（等于没有校验）。
      -->
      <div v-show="conditionEditorOpen" class="stat-query__custom">
        <query-condition-builder
          ref="builder"
          :columns="columns"
          :options="options"
          :value="customConditions"
          @input="customConditions = $event"
          @search="handleSearch"
        />
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
          <screen-button
            size="sm"
            :icon="conditionEditorOpen ? 'chevron-up' : 'plus'"
            @click="conditionEditorOpen = !conditionEditorOpen"
          >
            自定义条件{{ customCount ? `（${customCount}）` : '' }}
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
      show-locate
      :locating-id="locatingId"
      @toggle-column="handleToggleColumn"
      @reset-columns="handleResetColumns"
      @page-change="handlePageChange"
      @refresh="loadAll"
      @export="handleExport"
      @open-detail="handleOpenDetail"
      @locate="handleLocate"
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
import { queryLandOptions } from '@/api/land/landData'
import {
  buildExportUrl,
  queryAdvancedPage,
  queryAdvancedSummary,
  queryColumns,
  queryFacilityPage,
  queryFacilityStats,
  queryLandPage,
  queryLandStats,
  queryOptions
} from '@/api/land/statQuery'
// 定位用「数据管理」已有的 geoDetail（按 id 取单条地块图形），不另开接口：
// 图形只在数据管理里维护，查询页只需要读同一条记录的最新边界。
import { queryLandGeoDetail } from '@/api/land/landAdmin'
import { queryFacilityGeoDetail } from '@/api/land/facilityAdmin'
// 宗地/配套几何图层工厂：地图实例由 views/screen/index.vue 共享（window.viewer），
// 这里只往共享地图上加/删自己的实体，**不新建 Cesium Viewer**。
import { createGeoLayer } from '@/views/screen/landGeoLayer'
import {
  APPROVAL_OPTIONS,
  BUILD_NATURE_OPTIONS,
  PAGE_SIZE_OPTIONS,
  PROJECT_TYPE_OPTIONS,
  YES_NO_OPTIONS,
  metricsOf
} from '../constants'
import QueryConditionBuilder from './QueryConditionBuilder.vue'
import QueryResultPanel from './QueryResultPanel.vue'
import StatSummaryPanel from './StatSummaryPanel.vue'
import LandFacilityDetailModal from './LandFacilityDetailModal.vue'
import FacilityDetailModal from './FacilityDetailModal.vue'

export default {
  name: 'StatQueryView',
  components: {
    FacilityDetailModal,
    LandFacilityDetailModal,
    QueryConditionBuilder,
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
      /** 宗地编号远程搜索防抖；只用于配套查询页 */
      landSearchTimer: null,
      /** 当前条件（值是字符串；空串表示不限） */
      conditions: {},
      /** 自定义条件（与「查询导出」页签同一个构造器的输出） */
      customConditions: [],
      /** 自定义条件编辑区是否展开（收起后靠「当前筛选」那行兜底披露） */
      conditionEditorOpen: false,
      /** 选中的展示列 */
      selectedKeys: [],
      rows: [],
      summary: {},
      loading: false,
      /** 正在定位的行 id（空串 = 空闲）；连点保护与按钮态共用 */
      locatingId: '',
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
          // ★ multiple: true 的字段后端是 List（多值 OR / IN），
          //   走 POST 合并自定义条件时必须发数组；其余字段是单值标量。
          this.selectField('ptxmmc', '配套项目名称', this.multiOptions('ptxmmc'), { searchable: true, labelWidth: 104, multiple: true }),
          this.selectField('ptsslb', '配套设施类别', this.multiOptions('ptsslb'), { multiple: true }),
          // 候选必须来自宗地表：配套表的 dkmc 可为空，且 /options?dataType=facility 本就不返回 dkmc。
          // 远程搜索而不是一次拉全表，200 条只是每批上限，全部编号都可按关键字检索到。
          this.selectField('crzdbh', '出让宗地编号', this.multiOptions('crzdbh'), {
            searchable: true,
            labelWidth: 112,
            multiple: true,
            filterLocal: false,
            placeholder: '输入编号搜索'
          }),
          this.selectField('xzqh', '行政区划', this.multiOptions('xzqh'), { multiple: true }),
          // 功能区与行政区划并列：2026-11 拆列后，生态城/经开区等记录只能按 gnq 检索
          this.selectField('gnq', '功能区', this.multiOptions('gnq'), { labelWidth: 88, multiple: true }),
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
        this.selectField('dkmc', '地块名称', this.multiOptions('dkmc'), { searchable: true, labelWidth: 88, multiple: true }),
        this.selectField('xzqh', '行政区划', this.multiOptions('xzqh'), { labelWidth: 88, multiple: true }),
        this.selectField('crsjYears', '出让时间', this.multiOptions('crsjYears'), {
          labelWidth: 88,
          placeholder: '全部年份',
          multiple: true
        }),
        this.selectField('srr', '建设单位', this.multiOptions('srr'), { searchable: true, labelWidth: 88, multiple: true }),
        this.selectField('xmfl', '项目分类', PROJECT_TYPE_OPTIONS, { labelWidth: 88 })
      ]
    },
    /** 真正会下发的自定义条件（只有「字段 + 值」都有才算） */
    activeCustomConditions () {
      return this.customConditions.filter((row) => row &&
        row.field &&
        row.value !== null && row.value !== undefined && String(row.value).trim() !== '')
    },
    customCount () {
      return this.activeCustomConditions.length
    },
    /** 「当前筛选」文案：把常用条件与自定义条件合成一句话，与请求参数同源 */
    filterSummary () {
      const parts = []
      this.conditionFields.forEach((field) => {
        const value = this.trimmed(this.conditions[field.key])
        if (value) {
          parts.push(`${field.label}：${value}`)
        }
      })
      if (this.customCount) {
        parts.push(`自定义条件 ${this.customCount} 条`)
      }
      return parts.length ? parts.join(' · ') : '全部数据（未设条件）'
    },
    hasFilters () {
      if (this.customCount) {
        return true
      }
      return this.conditionFields.some((field) => !!this.trimmed(this.conditions[field.key]))
    }
  },
  created () {
    // ★ 必须先把每个条件的 key 都初始化成 ''：
    //   Vue 2 对「对象上原本不存在的 key」赋值不会触发响应，
    //   而 ScreenSelect 的显示值来自 `:value="conditions[key]"` ——
    //   没初始化的话，用户选完下拉会「选了但显示不出来」，可条件已经进了请求。
    this.conditions = this.emptyConditions()
    /*
     * 定位图层：一个实例管一类地块（宗地红橙 / 配套青色）。
     * ★ 不用响应式（data）：它只持有 Cesium 实体句柄，与 Vue 渲染无关；
     *   放进 data 会被 Vue 递归 observe，白搭性能还可能报警告。
     */
    this.geoLayer = createGeoLayer(this.dataType === 'facility' ? 'facility' : 'land')
    // 非响应式序号：丢弃较慢的旧搜索响应，避免后输入的关键字被旧候选覆盖。
    this.landOptionRequest = 0
    this.loadMeta()
  },
  /** 切走页签（keep-alive 缓存）时清掉定位实体与高亮，避免污染数据管理页的地图 */
  deactivated () {
    this.clearLocate()
  },
  beforeDestroy () {
    this.clearLocate()
    if (this.landSearchTimer) {
      clearTimeout(this.landSearchTimer)
    }
    this.landOptionRequest += 1
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

    /** 只有宗地编号走远程搜索；其它下拉仍由 ScreenSelect 在本地过滤。 */
    handleOptionSearch (key, keyword) {
      if (key !== 'crzdbh' || this.dataType !== 'facility') {
        return
      }
      if (this.landSearchTimer) {
        clearTimeout(this.landSearchTimer)
      }
      this.landSearchTimer = setTimeout(() => {
        this.loadLandOptions(keyword)
      }, 300)
    },

    /**
     * 复用数据管理已有宗地候选接口，候选值与筛选值都统一为 crzdbh。
     * 接口每次最多返回 200 条，因此保留远程搜索入口，不能把首批当成全部数据。
     */
    async loadLandOptions (keyword) {
      const request = ++this.landOptionRequest
      try {
        const res = await queryLandOptions({ keyword: keyword || undefined, limit: 200 })
        if (request !== this.landOptionRequest) {
          return
        }
        if (!res || !res.success) {
          toast.error((res && res.message) || '出让宗地编号候选加载失败')
          return
        }
        const codes = (res.result || [])
          .map((item) => item && item.crzdbh)
          .filter((code) => code !== null && code !== undefined && String(code).trim() !== '')
        this.$set(this.options, 'crzdbh', codes)
      } catch (e) {
        if (request === this.landOptionRequest) {
          toast.error('出让宗地编号候选加载失败：' + (e && e.message ? e.message : '网络异常'))
        }
      }
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
        if (this.dataType === 'facility') {
          await this.loadLandOptions('')
        }
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
     * ★ 一定要同一个对象：两次调用各拼一次条件，稍有差异就会出现
     *   「表格 12 条、统计说 15 条」这种最难解释的问题。
     *
     * ★ 有自定义条件时改走 POST（/page + /summary）：
     *   自由组合条件是数组结构，GET 查询串装不下（与「查询导出」页签同一条路径），
     *   而两条路径最终落到同一个 Service，口径不会分叉。
     *   没有自定义条件时仍走原来的 GET：行为与改动前完全一致（不回归）。
     */
    async loadAll (pageNo) {
      const current = typeof pageNo === 'number' ? pageNo : this.pagination.current
      const useCustom = this.activeCustomConditions.length > 0
      const params = this.buildParams(current)
      const postPayload = useCustom ? this.buildPostPayload(current) : null
      this.loading = true
      try {
        const pagePromise = useCustom
          ? queryAdvancedPage(postPayload)
          : (this.dataType === 'facility' ? queryFacilityPage(params) : queryLandPage(params))
        const statsPromise = useCustom
          ? queryAdvancedSummary(postPayload)
          : (this.dataType === 'facility' ? queryFacilityStats(params) : queryLandStats(params))
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

    /** 条件 → GET 请求参数（空值不下发；多值在界面上是单选，所以直接单值传） */
    buildParams (pageNo) {
      const params = {
        dataType: this.dataType,
        pageNo,
        pageSize: this.pagination.pageSize,
        columns: this.selectedKeys.join(',')
      }
      Object.keys(this.conditions).forEach((key) => {
        const value = this.trimmed(this.conditions[key])
        if (value) {
          params[key] = value
        }
      })
      return params
    },

    /**
     * 条件 → POST 请求体（常用条件 + 自定义条件合并，清单 P2-11 第 2 条）。
     * ★ 多值字段发数组：后端 StatQueryDTO 里 dkmc / xzqh 这些是 List，
     *   发字符串也能绑上（单元素），但语义上该是数组，且多于一个值时才不会丢。
     */
    buildPostPayload (pageNo) {
      const payload = {
        dataType: this.dataType,
        pageNo,
        pageSize: this.pagination.pageSize,
        columns: this.selectedKeys.slice(),
        conditions: this.buildCustomConditions()
      }
      this.conditionFields.forEach((field) => {
        const value = this.trimmed(this.conditions[field.key])
        if (value) {
          payload[field.key] = field.multiple ? [value] : value
        }
      })
      return payload
    },

    /** 自定义条件 → 后端 AdvancedCondition[]{field, operator, value, relation} */
    buildCustomConditions () {
      const builder = this.$refs.builder
      if (builder && builder.buildConditions) {
        return builder.buildConditions()
      }
      // 构造器还没渲染（理论上不会，v-show 保活）：退回按父组件持有的行自行过滤，
      // 免得「条件已经在界面上但没进请求」。规则与构造器一致：字段与值都要有。
      return this.activeCustomConditions.map((row) => ({
        field: row.field,
        operator: row.operator || 'like',
        value: String(row.value).trim(),
        relation: row.relation === 'OR' ? 'OR' : 'AND'
      }))
    },

    /** 提交前校验自定义条件（数值/日期格式）；返回 false 表示已提示过、不要继续 */
    validateCustomConditions () {
      const builder = this.$refs.builder
      const message = builder && builder.validate ? builder.validate() : ''
      if (message) {
        toast.warning(message)
        return false
      }
      return true
    },

    handleSearch () {
      if (!this.validateCustomConditions()) {
        return
      }
      this.loadAll(1)
    },

    /** 界面上的「清除」：清条件（含自定义条件）并重查 */
    handleReset () {
      this.conditions = this.emptyConditions()
      this.customConditions = []
      this.clearLocate()
      this.loadAll(1)
    },

    /**
     * 清单 P2-01 的统一契约：清条件、页码回 1、清选中行，并同步「当前筛选」。
     * 由父级（query/index.vue）在页签切换 / 页面激活时调用。
     *
     * ★ 为什么连自定义条件一起清：它是**条件**，不是视图偏好。
     *   只清可见下拉而留着 AND/OR 组合，就是清单里说的「看似全量、实际仍过滤」。
     * ★ 为什么不重设 selectedKeys（列选择）：那是显示偏好，与「检索条件」无关，
     *   每次切页签把列设置也抹掉，用户得反复重新勾。
     */
    async resetFilters () {
      this.conditions = this.emptyConditions()
      this.customConditions = []
      this.conditionEditorOpen = false
      this.pagination = Object.assign({}, this.pagination, { current: 1 })
      this.clearLocate()
      return this.loadAll(1)
    },

    /**
     * 外部下钻（路由带条件进来，例如 /screen/query?tab=land&dkmc=xxx）。
     * ★ 语义是「换一批条件」而不是叠加：先清空再灌入，避免上一次的条件残留。
     * ★ 参数里没有一个属于本数据集时按「清空后全量」处理 —— 例如从配套页签
     *   带着 ptxmmc 下钻到宗地页签，宗地数据集没有这个条件，不能假装生效。
     */
    applyDrill (params) {
      const next = this.emptyConditions()
      let matched = 0
      Object.keys(params || {}).forEach((key) => {
        if (!Object.prototype.hasOwnProperty.call(next, key)) {
          return
        }
        const value = this.trimmed(params[key])
        if (value) {
          next[key] = value
          matched += 1
        }
      })
      if (!matched) {
        return this.resetFilters()
      }
      this.conditions = next
      this.customConditions = []
      this.conditionEditorOpen = false
      this.clearLocate()
      return this.loadAll(1)
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
     * 条件与列表页**完全一致**（含自定义列 / 自定义条件），所以「导出的行 = 屏幕上的行」。
     *
     * ★ 有自定义条件时把条件序列化进 `conditionsJson`（GET 装不下数组结构），
     *   后端反序列化成同一套 AdvancedCondition 再落到同一个 Service。
     */
    handleExport () {
      if (!this.validateCustomConditions()) {
        return
      }
      const params = this.buildParams(1)
      delete params.pageNo
      delete params.pageSize
      const conditions = this.buildCustomConditions()
      if (conditions.length) {
        params.conditionsJson = JSON.stringify(conditions)
      }
      window.open(buildExportUrl(params), '_blank')
    },

    /**
     * 定位到某一行的地块（清单 P1-12）。
     *
     * 流程：按行 id 取单条 geoDetail → 用它重铺**本面板自己的** GeoLayer →
     * 高亮 → 相机飞行。整条链路复用数据管理的 `createGeoLayer` 与共享地图
     * （`window.viewer`），不新建 Viewer、也不往共享地图上留别人的实体。
     *
     * ★ 先 renderAll 再高亮：renderAll 内部先 clearAll，天然满足
     *   「连续定位只保留当前高亮」（清单 P1-12 第 3 条）。
     * ★ 无图形必须给反馈：后端对「没画过图形」返回 success=false，
     *   这里一律 toast 并把上一次的高亮清掉（不留残影）。
     */
    async handleLocate (row) {
      if (!row || !row.id) {
        toast.warning('这一行没有行标识，无法定位（请刷新后重试）')
        return
      }
      if (!window.Cesium || !window.viewer) {
        toast.warning('地图还没准备好，暂时无法定位')
        return
      }
      const id = String(row.id)
      if (this.locatingId === id) {
        return
      }
      this.locatingId = id
      try {
        const request = this.dataType === 'facility'
          ? queryFacilityGeoDetail(id)
          : queryLandGeoDetail(id)
        const res = await request
        // 连点了别的行：这次的结果已经过时，丢掉（否则会出现「点了 A，停了 C」）
        if (this.locatingId !== id) {
          return
        }
        if (!res || !res.success) {
          // 后端把「没有图形」和「无权限」都表达成 success=false，
          // 原文透出来比自定义一句话更有用（否则「查得到记录却定位不了」无从解释）
          toast.warning((res && res.message) || '该地块没有地块图形，无法定位')
          this.clearLocate()
          return
        }
        const geojson = res.result && res.result.dkGeojson
        if (!geojson) {
          toast.warning('该地块没有地块图形，无法定位')
          this.clearLocate()
          return
        }
        this.geoLayer.renderAll([{
          id,
          crzdbh: res.result.crzdbh || row.crzdbh || '',
          dkmc: res.result.dkmc || row.dkmc || '',
          dkGeojson: geojson
        }], false)
        this.geoLayer.highlight(id)
        this.geoLayer.flyTo(id)
      } catch (e) {
        toast.error('定位失败：' + (e && e.message ? e.message : '网络异常'))
        this.clearLocate()
      } finally {
        if (this.locatingId === id) {
          this.locatingId = ''
        }
      }
    },

    /** 清掉本面板在地图上留下的实体与高亮（切页签 / 销毁 / 重置条件时调用） */
    clearLocate () {
      if (this.geoLayer) {
        this.geoLayer.clearAll()
      }
      this.locatingId = ''
    },

    /** 条件值统一去空白：'' 表示不限（null/undefined 也当空） */
    trimmed (value) {
      if (value === null || value === undefined) {
        return ''
      }
      return String(value).trim()
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

  /* 「当前筛选」：一行文字 + 一键清除，说明「现在到底在下发什么条件」 */
  &__filters {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-top: 10px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__filters-label {
    flex: 0 0 auto;
    color: var(--screen-text-sub);
  }

  &__filters-text {
    flex: 1 1 auto;
    min-width: 0;
    .screen-ellipsis();
  }

  &__filters-clear {
    flex: 0 0 auto;
    padding: 0;
    font: inherit;
    color: var(--screen-accent);
    background: none;
    border: 0;
    cursor: pointer;
    text-decoration: underline dotted;
    .screen-focus-ring();

    &:hover {
      color: var(--screen-text);
    }
  }

  /* 自定义条件区：与常用条件之间留一条分隔，视觉上明确是「附加条件」 */
  &__custom {
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px dashed var(--screen-border);
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  &__tip {
    margin-left: auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }
}
</style>
