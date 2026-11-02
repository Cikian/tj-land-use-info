<template>
  <!--
    WarningQueryView 预警信息查询（查询统计页第 4 个页签，契约 §6.1）
    ===============================================================
    三段式（自上而下）：
      筛选条     预警分类 / 等级 / 行政区划 / 地块名称 / 配套项目 / 宗地编号 / 关键词
                 + 查询 · 重置 · 重新计算 · 导出          （WarningFilterBar）
      四色统计卡 蓝 / 黄 / 橙 / 红 + 合计                （WarningSummaryCards）
      结果表     分页 + 行内「查看」开详情               （WarningResultPanel + WarningDetailModal）

    ★ 为什么列表与统计**永远一起发、用同一个条件对象**：
      两次调用各拼一次条件，稍有差异就会出现「表格 12 条、统计说 15 条」这种
      最难解释的问题（查询统计页已有同样注释）。所以一次查询只允许有一条条件路径：
      筛选条抛出拍平后的条件 → 存进 `query` → 列表与汇总都用它。

    ★ 为什么统计卡不在 ScreenPanel 里：它是「抬头数字」，四张卡自带玻璃底
      （.screen-glass），再套一层面板会出现双层描边与双份内边距。

    ★ pointer-events：本组件不是铺满画布的容器，它挂在 `query/index.vue` 的
      `__body`（那里已显式 `pointer-events: auto`）里，所以这里不再改命中测试。
      新增铺满画布的层时请回看 components/screen/README.md 第 6 节第 4 条。
  -->
  <div class="warning-query">
    <warning-filter-bar
      class="warning-query__filter"
      :level-options="levelOptions"
      :type-options="typeOptions"
      :loading="loading"
      :recalc-loading="recalcLoading"
      :export-disabled="!pagination.total"
      @search="handleSearch"
      @recalc="handleRecalc"
      @export="handleExport"
    />

    <warning-summary-cards
      class="warning-query__summary"
      :by-level="byLevel"
      :total="summary.total"
      :calc-time="summary.calcTime"
      :loading="loading"
    />

    <warning-result-panel
      class="warning-query__result"
      :rows="rows"
      :loading="loading"
      :total="pagination.total"
      :page-no="pagination.current"
      :page-size="pagination.pageSize"
      :page-size-options="pageSizeOptions"
      @page-change="handlePageChange"
      @refresh="loadAll()"
      @open-detail="handleOpenDetail"
    />

    <warning-detail-modal
      :visible="detailVisible"
      :row="detailRow"
      @close="detailVisible = false"
    />
  </div>
</template>

<script>
import { toast } from '@/components/screen/toast'
import {
  exportWarning,
  listWarning,
  recalcWarning,
  summaryWarning,
  warningOptions
} from '@/api/land/statWarning'
import WarningDetailModal from './warning/WarningDetailModal.vue'
import WarningFilterBar from './warning/WarningFilterBar.vue'
import WarningResultPanel from './warning/WarningResultPanel.vue'
import WarningSummaryCards from './warning/WarningSummaryCards.vue'
import {
  WARNING_DEFAULT_PAGE_SIZE,
  WARNING_PAGE_SIZE_OPTIONS,
  YJ_TYPE_FALLBACK
} from './warning/constants'

export default {
  name: 'WarningQueryView',
  components: {
    WarningDetailModal,
    WarningFilterBar,
    WarningResultPanel,
    WarningSummaryCards
  },
  data () {
    return {
      /** 当前生效的条件（已拍平，空值不出现）—— 列表与汇总共用这一份 */
      query: {},
      rows: [],
      summary: {},
      loading: false,
      /** 重算中：与查询分开，否则重算期间列表会被「查询中」的遮罩误伤 */
      recalcLoading: false,
      /** 页码字段与契约 §5.1 一致：pageNo / pageSize */
      pagination: { current: 1, pageSize: WARNING_DEFAULT_PAGE_SIZE, total: 0 },
      pageSizeOptions: WARNING_PAGE_SIZE_OPTIONS,
      /** 下拉候选（来自 /options；加载失败时分类回落到兜底三分类，等级留空） */
      levelOptions: [],
      typeOptions: YJ_TYPE_FALLBACK.map((type) => ({ value: type, label: type })),
      detailVisible: false,
      detailRow: null
    }
  },
  computed: {
    /** `/summary` 的 byLevel（四档，可能缺项，卡片组件会补 0） */
    byLevel () {
      return this.summary.byLevel || []
    }
  },
  created () {
    this.loadOptions()
  },
  methods: {
    /**
     * 下拉候选。
     * ★ 等级下拉的 value 用 levelId（唯一键）；预警分类直接用中文字符串，
     *   与 `t_process_warning.yj_type` 的存值一致，不做枚举映射。
     */
    async loadOptions () {
      try {
        const res = await warningOptions()
        if (!res || !res.success) {
          toast.error((res && res.message) || '预警下拉选项加载失败')
          return
        }
        const result = res.result || {}
        this.levelOptions = (result.levels || []).map((level) => ({
          value: level.id,
          label: `${level.levelName}（${level.minDays}-${level.maxDays} 天）`
        }))
        if (result.yjTypes && result.yjTypes.length) {
          this.typeOptions = result.yjTypes.map((type) => ({ value: type, label: type }))
        }
      } catch (e) {
        toast.error('预警下拉选项加载失败：' + (e && e.message ? e.message : '网络异常'))
      } finally {
        // 选项失败也照常查一次列表：能出数据就先给人看，别让下拉的错误把整页拦住
        await this.loadAll(1)
      }
    },

    /**
     * 查询：列表 + 汇总一起发（同一个条件对象，见文件头说明）。
     * @param {number} [pageNo] 不传时沿用当前页（翻页 / 刷新的场景）
     */
    async loadAll (pageNo) {
      const current = typeof pageNo === 'number' ? pageNo : this.pagination.current
      const params = this.buildParams(current)
      this.loading = true
      try {
        const [pageRes, summaryRes] = await Promise.all([
          listWarning(params),
          summaryWarning(params)
        ])

        if (!pageRes || !pageRes.success) {
          toast.error((pageRes && pageRes.message) || '预警列表查询失败')
          return
        }
        // 契约 §4 用的是 jeecg 的 IPage（records/total/size/current）；
        // 这里对 records/rows 做一次兜底，避免后端换了包装类就静默变成空表
        const page = pageRes.result || {}
        this.rows = page.records || page.rows || []
        this.pagination = {
          current: Number(page.current || current),
          pageSize: Number(page.size || this.pagination.pageSize),
          total: Number(page.total || 0)
        }
        this.summary = (summaryRes && summaryRes.success && summaryRes.result) || {}
      } catch (e) {
        toast.error('预警列表查询失败：' + (e && e.message ? e.message : '网络异常'))
      } finally {
        this.loading = false
      }
    },

    /** 条件 + 分页 → 请求参数（导出时不带分页，后端忽略分页参数） */
    buildParams (pageNo) {
      const params = Object.assign({}, this.query)
      if (typeof pageNo === 'number') {
        params.pageNo = pageNo
        params.pageSize = this.pagination.pageSize
      }
      return params
    },

    /** 筛选条抛出条件（含「重置」后的空条件）：换条件一律回到第 1 页 */
    handleSearch (params) {
      this.query = params || {}
      this.loadAll(1)
    },

    handlePageChange (payload) {
      const current = payload && payload.current ? payload.current : this.pagination.current
      const pageSize = payload && payload.pageSize ? payload.pageSize : this.pagination.pageSize
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadAll(current)
    },

    /**
     * 重新计算（全量：ptId 传 null）。
     * ★ 成功后必须刷新统计与列表：重算会按当前库里的数据**增删**预警行，
     *   不刷新的话屏幕上还是旧快照，看起来像「点了没反应」。
     */
    async handleRecalc () {
      this.recalcLoading = true
      try {
        const res = await recalcWarning({ ptId: null })
        if (!res || !res.success) {
          toast.error((res && res.message) || '重新计算失败')
          return
        }
        const result = res.result || {}
        toast.success(
          `重新计算完成：新增 ${result.created || 0} 条、更新 ${result.updated || 0} 条、` +
          `删除 ${result.removed || 0} 条`
        )
        // 等级表为空 / 逾期天数低于最小下界时不产生预警，这时「0 新增」不是失败，
        // 但用户需要知道原因，否则会反复点重算
        if (result.levelMissing) {
          toast.warning('预警等级表为空，本次没有生成任何预警；请先到管理端维护预警等级')
        }
        await this.loadAll(1)
      } catch (e) {
        toast.error('重新计算失败：' + (e && e.message ? e.message : '网络异常'))
      } finally {
        this.recalcLoading = false
      }
    },

    /**
     * 导出：`window.open(带 token 的下载地址)`，与档案 / 查询统计导出同一套。
     * 条件取筛选条**当前**的条件（屏幕上看到什么就导出什么）；
     * ★ 不写回 `this.query` —— 那是「已生效、列表正在用的条件」，
     *   用户改了条件但还没点查询时导出，不应该顺手把列表的口径也改掉。
     */
    handleExport (params) {
      window.open(exportWarning(params || this.query), '_blank')
    },

    handleOpenDetail (row) {
      if (!row) {
        return
      }
      this.detailRow = row
      this.detailVisible = true
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

/*
  纵向三段：筛选条 / 四色卡 / 结果表。
  ★ 结果表用 flex: 1 1 0 吃掉剩余空间（定死高度在 1080p 与 1440p 上必有一边难看），
    与前三个页签的分布完全一致，切页签时版面不会跳。
*/
.warning-query {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;

  &__filter {
    flex: 0 0 auto;
  }

  &__summary {
    flex: 0 0 auto;
  }

  &__result {
    flex: 1 1 0;
    min-height: 220px;
  }
}
</style>
