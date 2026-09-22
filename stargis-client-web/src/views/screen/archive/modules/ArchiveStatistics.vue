<template>
  <!--
    ArchiveStatistics 档案统计（方案 2.3.2 第 4/5 项）
    --------------------------------
    一次 GET /land/archive/stat 拿到全部统计口径，页面只负责把结果摆成 4 个 KPI、
    3 张图和 1 张按项目明细表。图表全部用手写 SVG / CSS，不引第三方图表库
    （canvas 在大屏里尺寸为 0 的问题不值得为一张图去引一个库）。

    下钻：在明细表点「查看档案」会抛出 drill 事件，由外层切到「档案查询」页签
    并把 ptxmmc / crzdbh 带过去 —— 统计只能说明「哪有问题」，下钻才能拿到案卷。

    无障碍：每个统计块都同时给出文字数值，环形图/条形图的颜色只是辅助线索，
    颜色语义与图例都有对应文本。
  -->
  <div class="archive-stat">
    <!-- ================= 统计范围 ================= -->
    <screen-panel class="archive-stat__filter" title="统计范围" :collapsible="false">
      <template #extra>
        <screen-button size="sm" icon="reload" :loading="loading" @click="loadStat">刷新</screen-button>
        <screen-button
          size="sm"
          icon="download"
          :disabled="!stat.total"
          @click="handleExport"
        >
          导出统计范围档案 ZIP
        </screen-button>
        <screen-button
          v-if="hasFilter"
          size="sm"
          type="text"
          icon="rotate-ccw"
          @click="handleResetFilter"
        >
          清除筛选
        </screen-button>
      </template>

      <div class="archive-stat__filter-row">
        <screen-field label="档案年度" :label-width="'76px'">
          <screen-select
            v-model="query.archiveYear"
            :options="yearOptions"
            placeholder="全部年度"
            aria-label="档案年度"
            @change="loadStat"
          />
        </screen-field>

        <screen-field label="行政区划" :label-width="'76px'">
          <screen-select
            v-model="query.xzqh"
            :options="xzqhOptions"
            placeholder="全部区划"
            aria-label="行政区划"
            @change="loadStat"
          />
        </screen-field>

        <screen-field label="责任部门" :label-width="'76px'">
          <screen-select
            v-model="query.responsibleDept"
            :options="deptOptions"
            placeholder="全部部门"
            aria-label="责任部门"
            @change="loadStat"
          />
        </screen-field>
      </div>
    </screen-panel>

    <!-- ================= KPI ================= -->
    <div class="archive-stat__kpis">
      <div class="archive-stat__kpi">
        <screen-stat-value :value="stat.total" unit="卷" label="档案总数" size="md" />
        <span class="archive-stat__kpi-sub">涉及 {{ stat.projectCount || 0 }} 个配套项目</span>
      </div>

      <div class="archive-stat__kpi">
        <screen-stat-value :value="stat.archivedCount" unit="卷" label="已归档" size="md" tone="success" />
        <span class="archive-stat__kpi-sub">归档率 {{ archivedRate }}%</span>
      </div>

      <div class="archive-stat__kpi">
        <screen-stat-value :value="stat.unarchivedCount" unit="卷" label="未归档" size="md" tone="warning" />
        <span class="archive-stat__kpi-sub">未归档指状态不是「已归档」的档案</span>
      </div>

      <div class="archive-stat__kpi">
        <screen-stat-value :value="stat.fileTotal" unit="个" label="卷内文件" size="md" />
        <span class="archive-stat__kpi-sub">合计 {{ formatSize(stat.totalSize) }}</span>
      </div>
    </div>

    <!-- ================= 图表 ================= -->
    <div class="archive-stat__charts">
      <screen-panel class="archive-stat__chart" title="档案类别分布" sub-title="按一级类别归并">
        <screen-donut
          v-if="categoryData.length"
          :data="categoryData"
          :size="donutSize"
          :thickness="13"
          unit="卷"
          center-label="类别数"
          :center-value="categoryData.length"
          legend-position="right"
        />
        <screen-empty v-else text="暂无类别数据" size="sm" />
      </screen-panel>

      <screen-panel class="archive-stat__chart" title="档案年度趋势" sub-title="按归档年度">
        <screen-line-chart
          :data="yearData"
          :height="lineHeight"
          unit="卷"
          tone="accent"
          label="档案年度趋势"
          empty-text="暂无年度数据"
        />
      </screen-panel>

      <!-- 三个分布口径共用一个面板，用页签切换，避免四张图把纵向空间吃光 -->
      <screen-panel class="archive-stat__chart" :bar="false">
        <template #title>
          <screen-tabs v-model="distTab" :tabs="distTabs" />
        </template>
        <screen-bar-list
          :items="distItems"
          unit="卷"
          :show-percent="true"
          :empty-text="distEmptyText"
        />
      </screen-panel>
    </div>

    <!-- ================= 按项目明细 ================= -->
    <screen-panel class="archive-stat__projects" title="按项目统计" :sub-title="`共 ${projectRows.length} 个项目`">
      <screen-data-table
        :columns="projectColumns"
        :data="projectRows"
        row-key="rowKey"
        :loading="loading"
        :min-width="1240"
        :animated="false"
        empty-text="没有符合条件的档案"
      >
        <template #ptxmmc="{ row }">
          <span class="archive-stat__project-name">{{ row.ptxmmc || '（未关联项目）' }}</span>
        </template>

        <template #archivedRate="{ row }">
          <span class="archive-stat__rate">
            <span class="archive-stat__rate-value">{{ row.archivedRate }}%</span>
            <span class="archive-stat__rate-track" aria-hidden="true">
              <i
                class="archive-stat__rate-fill"
                :class="{ 'is-full': row.archivedRate >= 100 }"
                :style="{ width: `${row.archivedRate}%` }"
              />
            </span>
          </span>
        </template>

        <template #action="{ row }">
          <button
            type="button"
            class="archive-stat__link"
            @click="handleDrill(row)"
          >
            查看档案
          </button>
        </template>
      </screen-data-table>
    </screen-panel>
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenField,
  ScreenSelect,
  ScreenStatValue,
  ScreenDonut,
  ScreenLineChart,
  ScreenBarList,
  ScreenDataTable,
  ScreenTabs,
  ScreenEmpty,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { queryArchiveStat, exportArchiveZip } from '@/api/land/archive'
import { queryXzqhOptions } from '@/api/land/landData'
import { DEPTS, buildYearOptions, toOptions, formatSize, compactQuery } from '../constants'
/** 统计返回的 { name, count } 转成图表需要的 { name, value } */
function toChartData (list) {
  return (list || []).map((item) => ({
    name: item.name || '未分类',
    value: Number(item.count) || 0,
  }))
}

export default {
  name: 'ArchiveStatistics',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenField,
    ScreenSelect,
    ScreenStatValue,
    ScreenDonut,
    ScreenLineChart,
    ScreenBarList,
    ScreenDataTable,
    ScreenTabs,
    ScreenEmpty,
  },
  data () {
    return {
      loading: false,
      query: {
        archiveYear: '',
        xzqh: '',
        responsibleDept: '',
      },
      xzqhOptions: [],
      yearOptions: buildYearOptions().map((year) => ({ value: year, label: `${year} 年` })),
      deptOptions: toOptions(DEPTS),
      stat: this.buildEmptyStat(),
      /** 视口高度：驱动图表尺寸的响应式来源（见 syncViewport） */
      viewportHeight: 1080,
      distTab: 'status',
      distTabs: [
        { key: 'status', label: '按状态' },
        { key: 'secretLevel', label: '按密级' },
        { key: 'xzqh', label: '按区划' },
      ],
      projectColumns: [
        { key: 'ptxmmc', title: '配套项目', width: 240, type: 'slot' },
        { key: 'crzdbh', title: '出让宗地编号', width: 160, ellipsis: true },
        { key: 'xzqh', title: '行政区划', width: 90 },
        { key: 'total', title: '档案总数', width: 100, type: 'number', align: 'right' },
        { key: 'archivedCount', title: '已归档', width: 90, type: 'number', align: 'right' },
        { key: 'unarchivedCount', title: '未归档', width: 90, type: 'number', align: 'right' },
        { key: 'fileCount', title: '卷内文件', width: 100, type: 'number', align: 'right' },
        { key: 'archivedRate', title: '归档率', width: 170, type: 'slot' },
        { key: 'action', title: '操作', width: 110, type: 'slot', align: 'center' },
      ],
    }
  },
  computed: {
    hasFilter () {
      return !!(this.query.archiveYear || this.query.xzqh || this.query.responsibleDept)
    },
    archivedRate () {
      if (!this.stat.total) return 0
      return Math.round(((this.stat.archivedCount || 0) / this.stat.total) * 100)
    },
    categoryData () {
      return toChartData(this.stat.byCategory)
    },
    yearData () {
      return toChartData(this.stat.byYear)
    },
    /** 当前页签对应的分布数据 */
    distItems () {
      const map = {
        status: this.stat.byStatus,
        secretLevel: this.stat.bySecretLevel,
        xzqh: this.stat.byXzqh,
      }
      const list = toChartData(map[this.distTab])
      const total = list.reduce((sum, item) => sum + item.value, 0)
      return list.map((item) => Object.assign({}, item, {
        // 百分比在这里算好，图表组件只负责画，避免组件里再依赖 total
        percent: total ? Number(((item.value / total) * 100).toFixed(1)) : 0,
      }))
    },
    distEmptyText () {
      const label = { status: '状态', secretLevel: '密级', xzqh: '区划' }[this.distTab] || ''
      return `暂无${label}分布数据`
    },
    /**
     * 明细表行：补 rowKey 与归档率。
     * 后端可能返回 facilityId 为空的「未关联项目」聚合行，用项目名兜底当 key。
     */
    projectRows () {
      return (this.stat.byProject || []).map((item, index) => {
        const total = Number(item.total) || 0
        const archived = Number(item.archivedCount) || 0
        return Object.assign({}, item, {
          rowKey: item.facilityId || item.ptxmmc || `row-${index}`,
          archivedRate: total ? Math.round((archived / total) * 100) : 0,
        })
      })
    },
    /** 图表面板高度随视口收缩，避免矮屏上折线图被压扁 */
    lineHeight () {
      return this.viewportHeight < 800 ? 150 : 190
    },
    donutSize () {
      return this.viewportHeight < 800 ? 130 : 156
    },
  },
  created () {
    this.loadXzqh()
    this.loadStat()
  },
  mounted () {
    this.syncViewport()
    window.addEventListener('resize', this.syncViewport)
  },
  beforeDestroy () {
    window.removeEventListener('resize', this.syncViewport)
  },
  methods: {
    /**
     * 视口高度必须存进 data，不能在图表的 computed 里直接读 window.innerHeight：
     * 窗口尺寸变化不会触发重渲染，矮屏压缩图表尺寸的逻辑就会失效。
     */
    syncViewport () {
      this.viewportHeight = window.innerHeight || 1080
    },
    formatSize,
    buildEmptyStat () {
      return {
        total: 0,
        archivedCount: 0,
        unarchivedCount: 0,
        fileTotal: 0,
        totalSize: 0,
        projectCount: 0,
        byCategory: [],
        byYear: [],
        byStatus: [],
        bySecretLevel: [],
        byXzqh: [],
        byProject: [],
      }
    },
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
    loadStat () {
      this.loading = true
      return queryArchiveStat(compactQuery(this.query), 20)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案统计加载失败')
            return
          }
          // 用空结构兜底：后端某些口径没有数据时不会返回对应字段，
          // 直接赋值会让模板读到 undefined，图表拿到 undefined 会报错
          this.stat = Object.assign(this.buildEmptyStat(), res.result || {})
        })
        .finally(() => {
          this.loading = false
        })
    },
    handleResetFilter () {
      this.query = { archiveYear: '', xzqh: '', responsibleDept: '' }
      this.loadStat()
    },
    handleExport () {
      exportArchiveZip(compactQuery(this.query))
      toast.info('已开始导出，请稍候…')
    },
    /** 下钻到档案查询：带上最精确的项目标识 */
    handleDrill (row) {
      const params = row.facilityId
        ? { ptxmmc: row.ptxmmc || undefined }
        : { crzdbh: row.crzdbh || undefined }
      this.$emit('drill', compactQuery(params))
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.archive-stat {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__filter {
    flex: 0 0 auto;
  }

  &__filter-row {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
    max-width: 1080px;
  }

  // ---------- KPI ----------
  &__kpis {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--screen-space-3);
    flex: 0 0 auto;
  }

  &__kpi {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-1);
    padding: var(--screen-space-3) var(--screen-space-4);
    min-width: 0;
    .screen-glass();
  }

  &__kpi-sub {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  // ---------- 图表 ----------
  &__charts {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1fr);
    gap: var(--screen-space-3);
    flex: 42 1 0;  // 42:58 的高度比例，basis 归零避免撑爆容器
    min-height: 240px;
  }

  &__chart {
    min-width: 0;
  }

  // ---------- 按项目明细 ----------
  &__projects {
    flex: 58 1 0;  // 42:58 的高度比例，basis 归零避免撑爆容器
    min-height: 220px;
  }

  &__project-name {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__link {
    .screen-link-action();
  }

  // 归档率：数值 + 细条，颜色不是唯一线索
  &__rate {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }

  &__rate-value {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    line-height: 1.1;
  }

  &__rate-track {
    display: block;
    height: 5px;
    border-radius: var(--screen-radius-pill);
    background: var(--screen-bar-track);
    overflow: hidden;
  }

  &__rate-fill {
    display: block;
    height: 100%;
    border-radius: inherit;
    transition: width 700ms var(--screen-ease);
    .screen-bar-fill(var(--screen-viz-green), #1f9e4a);

    &.is-full {
      .screen-bar-fill(var(--screen-viz-mint), #1f9e4a);
    }
  }
}

// 窄屏：图表改两列、KPI 改两列，避免每个块被压得过窄而失去可读性
@media (max-width: 1600px) {
  .archive-stat__charts {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }
}

@media (max-width: 1200px) {
  .archive-stat__kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .archive-stat__charts {
    grid-template-columns: minmax(0, 1fr);
  }

  .archive-stat__filter-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .archive-stat__rate-fill {
    transition: none;
  }
}
</style>
