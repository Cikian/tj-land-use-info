<template>
  <a-card :bordered="false" class="archive-stat-page">
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">档案统计</h2>
        <p class="page-head__desc">
          按项目对各项目的档案信息进行统计：总量与归档率、按档案类别分布、年度归档趋势、按项目明细。
          统计口径与档案维护/查询列表完全一致——上方筛选什么，下面的图与表就统计什么。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button icon="reload" :loading="loading" @click="loadStat">刷新</a-button>
        <a-button v-has="'land:archive:export'" type="primary" icon="download" :disabled="!stat.total" @click="handleExport">
          导出统计范围档案 ZIP
        </a-button>
      </div>
    </div>

    <!-- 统计过滤：只保留最关键的三个维度，避免统计条件过于复杂让人对不上口径 -->
    <div class="stat-filter">
      <a-form layout="inline">
        <a-form-item label="档案年度">
          <a-select v-model="query.archiveYear" allowClear placeholder="全部" style="width: 130px" @change="loadStat">
            <a-select-option v-for="year in yearOptions" :key="year" :value="year">{{ year }} 年</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="行政区划">
          <a-select v-model="query.xzqh" allowClear placeholder="全部" style="width: 150px" @change="loadStat">
            <a-select-option v-for="item in xzqhOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="责任部门">
          <a-select v-model="query.responsibleDept" allowClear placeholder="全部" style="width: 170px" @change="loadStat">
            <a-select-option v-for="item in depts" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="link" @click="handleResetFilter">清除筛选</a-button>
        </a-form-item>
      </a-form>
    </div>

    <a-spin :spinning="loading">
      <!-- ============ 关键指标 ============ -->
      <div class="stat-cards">
        <div class="stat-card">
          <div class="stat-card__label">档案总数</div>
          <div class="stat-card__value">{{ stat.total }}</div>
          <div class="stat-card__foot">覆盖 {{ stat.projectCount }} 个配套项目</div>
        </div>
        <div class="stat-card stat-card--ok">
          <div class="stat-card__label">已归档</div>
          <div class="stat-card__value">{{ stat.archivedCount }}</div>
          <div class="stat-card__foot">归档率 {{ archivedRate }}%</div>
        </div>
        <div class="stat-card stat-card--warn">
          <div class="stat-card__label">未归档</div>
          <div class="stat-card__value">{{ stat.unarchivedCount }}</div>
          <div class="stat-card__foot">未归档 / 归档中 / 审核中</div>
        </div>
        <div class="stat-card stat-card--info">
          <div class="stat-card__label">卷内文件</div>
          <div class="stat-card__value">{{ stat.fileTotal }}</div>
          <div class="stat-card__foot">合计 {{ formatSize(stat.totalSize) }}</div>
        </div>
      </div>

      <!-- ============ 图表区 ============ -->
      <div class="stat-charts">
        <div class="stat-panel stat-panel--pie">
          <div class="stat-panel__title">归档情况</div>
          <div class="stat-ring">
            <svg viewBox="0 0 120 120" class="stat-ring__svg" role="img" aria-label="归档率环形图">
              <circle
                cx="60"
                cy="60"
                r="48"
                fill="none"
                stroke="#eef2f7"
                stroke-width="14" />
              <circle
                cx="60"
                cy="60"
                r="48"
                fill="none"
                stroke="#10b981"
                stroke-width="14"
                stroke-linecap="round"
                :stroke-dasharray="ringDash"
                transform="rotate(-90 60 60)" />
              <text x="60" y="56" text-anchor="middle" class="stat-ring__value">{{ archivedRate }}%</text>
              <text x="60" y="74" text-anchor="middle" class="stat-ring__label">已归档</text>
            </svg>
          </div>
          <div class="stat-legend">
            <div class="stat-legend__item">
              <span class="stat-legend__dot stat-legend__dot--ok"></span>
              已归档 <b>{{ stat.archivedCount }}</b>
            </div>
            <div class="stat-legend__item">
              <span class="stat-legend__dot stat-legend__dot--idle"></span>
              未归档 <b>{{ stat.unarchivedCount }}</b>
            </div>
          </div>
        </div>

        <div class="stat-panel stat-panel--bar">
          <div class="stat-panel__title">按档案类别统计（顶级类别）</div>
          <div v-if="categoryRows.length" class="stat-bars">
            <div v-for="item in categoryRows" :key="item.name" class="stat-bars__row">
              <div class="stat-bars__name" :title="item.name">{{ item.name }}</div>
              <div class="stat-bars__track">
                <div class="stat-bars__fill" :style="{ width: item.percent + '%' }"></div>
              </div>
              <div class="stat-bars__value">{{ item.count }}</div>
            </div>
          </div>
          <a-empty v-else class="stat-panel__empty" description="暂无档案文件数据" />
        </div>

        <div class="stat-panel stat-panel--line">
          <div class="stat-panel__title">档案年度趋势</div>
          <div v-if="yearRows.length" class="stat-trend">
            <svg :viewBox="`0 0 ${trendWidth} ${trendHeight}`" class="stat-trend__svg" role="img" aria-label="年度归档趋势">
              <polyline :points="trendPoints" fill="none" stroke="#2e7cf6" stroke-width="2" />
              <circle
                v-for="point in trendCircles"
                :key="point.name"
                :cx="point.x"
                :cy="point.y"
                r="4"
                fill="#fff"
                stroke="#2e7cf6"
                stroke-width="2" />
              <text
                v-for="point in trendCircles"
                :key="'t-' + point.name"
                :x="point.x"
                :y="trendHeight - 6"
                text-anchor="middle"
                class="stat-trend__label">{{ point.name }}</text>
            </svg>
          </div>
          <a-empty v-else class="stat-panel__empty" description="暂无年度数据（请补填归档日期）" />
        </div>
      </div>

      <!-- ============ 按项目统计 ============ -->
      <div class="stat-table">
        <div class="stat-table__head">
          <h4>按项目统计（方案要求重点）</h4>
          <span class="stat-table__hint">仅展示档案数最多的前 {{ stat.byProject.length }} 个项目</span>
        </div>
        <a-table
          size="small"
          rowKey="rowKey"
          :columns="projectColumns"
          :dataSource="projectRows"
          :pagination="false"
          :scroll="{ x: 1100 }"
          :locale="{ emptyText: '当前筛选条件下没有档案数据' }">
          <template slot="progress" slot-scope="text, record">
            <div class="stat-progress">
              <div class="stat-progress__track">
                <div class="stat-progress__fill" :style="{ width: record.archivedRate + '%' }"></div>
              </div>
              <span class="stat-progress__text">{{ record.archivedRate }}%</span>
            </div>
          </template>
          <template slot="action" slot-scope="text, record">
            <a @click="goQuery(record)">查看该项目的档案</a>
          </template>
        </a-table>
      </div>
    </a-spin>
  </a-card>
</template>

<script>
  import { queryArchiveStat, exportArchiveZip } from '@/api/land/archive'
  import { queryXzqhOptions } from '@/api/land/landData'

  /**
   * 档案统计（方案 2.3.2 第 5 项：按照项目对各个项目的档案信息进行统计）
   *
   * 图表刻意<b>不用第三方图表库</b>，而是内联 SVG + CSS 绘制：
   *   · 环形图：SVG 的 stroke-dasharray 画一段圆弧，比引入 chart 组件更可控，
   *     也避免在弹窗/页签切换时出现 canvas 尺寸为 0 的经典问题；
   *   · 横向条形图：CSS 宽度百分比，配合表格化的对齐，长类别名不会挤压图形；
   *   · 年度趋势：SVG polyline，横轴固定为年份，纵轴为档案数。
   *
   * 统计口径与列表一致（共用同一套查询条件），保证「列表里看到的」与「图上统计的」能对上。
   */
  export default {
    name: 'ArchiveStatistics',
    data () {
      const now = new Date().getFullYear()
      const years = []
      for (let y = now; y >= now - 12; y--) {
        years.push(y)
      }
      return {
        loading: false,
        query: {},
        stat: this.buildEmptyStat(),
        xzqhOptions: [],
        yearOptions: years,
        depts: ['发改（行政审批）', '规划', '财政', '住建', '其他'],
        projectColumns: [
          { title: '配套项目', dataIndex: 'ptxmmc', width: 260, ellipsis: true, customRender: text => text || '（未关联项目）' },
          { title: '出让宗地编号', dataIndex: 'crzdbh', width: 170, customRender: text => text || '—' },
          { title: '行政区', dataIndex: 'xzqh', width: 90, customRender: text => text || '—' },
          { title: '档案数', dataIndex: 'total', width: 80, align: 'right' },
          { title: '已归档', dataIndex: 'archivedCount', width: 80, align: 'right' },
          { title: '未归档', dataIndex: 'unarchivedCount', width: 80, align: 'right' },
          { title: '文件数', dataIndex: 'fileCount', width: 80, align: 'right' },
          { title: '归档率', dataIndex: 'archivedRate', width: 180, scopedSlots: { customRender: 'progress' } },
          { title: '操作', width: 150, scopedSlots: { customRender: 'action' } }
        ],
        // 年度趋势画布尺寸（viewBox 坐标系，与实际像素无关，保证自适应缩放）
        trendWidth: 520,
        trendHeight: 200
      }
    },
    computed: {
      archivedRate () {
        if (!this.stat.total) {
          return 0
        }
        return Math.round((this.stat.archivedCount / this.stat.total) * 100)
      },
      /** 环形图：周长 2πr = 301.6，用 dasharray 表示已归档占比 */
      ringDash () {
        const circumference = 2 * Math.PI * 48
        const filled = (this.archivedRate / 100) * circumference
        return `${filled} ${circumference - filled}`
      },
      categoryRows () {
        const rows = this.stat.byCategory || []
        const max = rows.reduce((m, item) => Math.max(m, item.count), 0)
        return rows.map(item => ({
          name: item.name || '未分类',
          count: item.count,
          percent: max ? Math.max(2, Math.round((item.count / max) * 100)) : 0
        }))
      },
      yearRows () {
        return this.stat.byYear || []
      },
      trendPoints () {
        return this.trendCircles.map(point => `${point.x},${point.y}`).join(' ')
      },
      trendCircles () {
        const rows = this.yearRows
        if (!rows.length) {
          return []
        }
        const padding = { left: 40, right: 20, top: 20, bottom: 32 }
        const innerWidth = this.trendWidth - padding.left - padding.right
        const innerHeight = this.trendHeight - padding.top - padding.bottom
        const max = rows.reduce((m, item) => Math.max(m, item.count), 0) || 1
        const step = rows.length > 1 ? innerWidth / (rows.length - 1) : 0
        return rows.map((item, index) => ({
          name: item.name,
          x: rows.length > 1 ? padding.left + step * index : padding.left + innerWidth / 2,
          y: padding.top + innerHeight - (item.count / max) * innerHeight
        }))
      },
      projectRows () {
        return (this.stat.byProject || []).map((item, index) => {
          const total = Number(item.total) || 0
          const archived = Number(item.archivedCount) || 0
          return Object.assign({}, item, {
            rowKey: item.facilityId || item.ptxmmc || ('row-' + index),
            archivedRate: total ? Math.round((archived / total) * 100) : 0
          })
        })
      }
    },
    created () {
      this.loadXzqh()
      this.loadStat()
    },
    methods: {
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
          byProject: []
        }
      },
      loadXzqh () {
        queryXzqhOptions().then(res => {
          if (res.success) {
            this.xzqhOptions = res.result || []
          }
        })
      },
      loadStat () {
        this.loading = true
        return queryArchiveStat(this.query, 20).then(res => {
          if (res.success) {
            this.stat = Object.assign(this.buildEmptyStat(), res.result || {})
          } else {
            this.$message.warning(res.message)
          }
        }).finally(() => {
          this.loading = false
        })
      },
      handleResetFilter () {
        this.query = {}
        this.loadStat()
      },
      handleExport () {
        exportArchiveZip(this.query)
        this.$message.success('已开始导出，请稍候…')
      },
      /** 跳到档案查询页并带上项目条件 */
      goQuery (record) {
        const query = {}
        if (record.ptxmmc) {
          query.ptxmmc = record.ptxmmc
        } else if (record.crzdbh) {
          query.crzdbh = record.crzdbh
        }
        this.$router.push({ path: '/land/archive/query', query: query })
      },
      formatSize (bytes) {
        const value = Number(bytes)
        if (!value) {
          return '0 B'
        }
        const units = ['B', 'KB', 'MB', 'GB', 'TB']
        let size = value
        let unit = 0
        while (size >= 1024 && unit < units.length - 1) {
          size /= 1024
          unit++
        }
        return unit === 0 ? `${size} ${units[unit]}` : `${size.toFixed(1)} ${units[unit]}`
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .archive-stat-page {
    /deep/ .ant-card-body {
      padding: 20px 24px 24px;
    }
  }

  .page-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    padding-bottom: 16px;
    border-bottom: 1px solid @border-color;

    &__title {
      margin: 0 0 6px;
      font-size: 18px;
      font-weight: 600;
      color: #0f172a;
    }

    &__desc {
      margin: 0;
      max-width: 860px;
      font-size: 13px;
      line-height: 20px;
      color: @text-muted;
    }

    &__actions {
      display: flex;
      gap: 8px;
      flex-shrink: 0;
    }
  }

  .stat-filter {
    padding: 14px 0 0;

    /deep/ .ant-form-item {
      margin-bottom: 12px;
    }
  }

  /* ---------------- 关键指标卡 ---------------- */
  .stat-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
    gap: 16px;
    margin-bottom: 16px;
  }

  .stat-card {
    padding: 16px 18px;
    background: #fff;
    border: 1px solid @border-color;
    border-left: 3px solid #cbd5e1;
    border-radius: 8px;

    &__label {
      font-size: 13px;
      color: @text-muted;
    }

    &__value {
      margin: 6px 0 4px;
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 30px;
      line-height: 34px;
      letter-spacing: 1px;
      color: #0f172a;
    }

    &__foot {
      font-size: 12px;
      color: @text-weak;
    }

    &--ok {
      border-left-color: #10b981;

      .stat-card__value {
        color: #0f766e;
      }
    }

    &--warn {
      border-left-color: #f59e0b;

      .stat-card__value {
        color: #b45309;
      }
    }

    &--info {
      border-left-color: #2e7cf6;

      .stat-card__value {
        color: #1d4ed8;
      }
    }
  }

  /* ---------------- 图表区 ---------------- */
  .stat-charts {
    display: grid;
    grid-template-columns: 300px minmax(320px, 1fr) minmax(320px, 1fr);
    gap: 16px;
    margin-bottom: 20px;
  }

  .stat-panel {
    display: flex;
    flex-direction: column;
    padding: 16px;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;
    min-height: 280px;

    &__title {
      flex: none;
      margin-bottom: 12px;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
    }

    &__empty {
      flex: 1 1 auto;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
  }

  .stat-ring {
    flex: 1 1 auto;
    display: flex;
    align-items: center;
    justify-content: center;

    &__svg {
      width: 168px;
      height: 168px;
    }

    &__value {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 22px;
      fill: #0f172a;
    }

    &__label {
      font-size: 10px;
      fill: @text-weak;
    }
  }

  .stat-legend {
    display: flex;
    justify-content: center;
    gap: 20px;
    font-size: 12px;
    color: @text-muted;

    b {
      color: #0f172a;
    }

    &__item {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    &__dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;

      &--ok {
        background: #10b981;
      }

      &--idle {
        background: #cbd5e1;
      }
    }
  }

  .stat-bars {
    flex: 1 1 auto;
    display: flex;
    flex-direction: column;
    gap: 10px;

    &__row {
      display: grid;
      grid-template-columns: 110px 1fr 44px;
      align-items: center;
      gap: 10px;
    }

    &__name {
      font-size: 12px;
      color: #334155;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__track {
      height: 12px;
      background: #eef2f7;
      border-radius: 6px;
      overflow: hidden;
    }

    &__fill {
      height: 100%;
      background: linear-gradient(90deg, #60a5fa, #2e7cf6);
      border-radius: 6px;
      transition: width 0.4s ease;
    }

    &__value {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 14px;
      text-align: right;
      color: #0f172a;
    }
  }

  .stat-trend {
    flex: 1 1 auto;
    display: flex;
    align-items: center;

    &__svg {
      width: 100%;
      height: 200px;
    }

    &__label {
      font-size: 10px;
      fill: @text-weak;
    }
  }

  /* ---------------- 按项目统计表 ---------------- */
  .stat-table {
    &__head {
      display: flex;
      align-items: baseline;
      gap: 10px;
      margin-bottom: 10px;

      h4 {
        margin: 0;
        font-size: 14px;
        font-weight: 600;
        color: #0f172a;
      }
    }

    &__hint {
      font-size: 12px;
      color: @text-weak;
    }
  }

  .stat-progress {
    display: flex;
    align-items: center;
    gap: 8px;

    &__track {
      flex: 1 1 auto;
      height: 8px;
      background: #eef2f7;
      border-radius: 4px;
      overflow: hidden;
    }

    &__fill {
      height: 100%;
      background: #10b981;
      border-radius: 4px;
    }

    &__text {
      flex: none;
      width: 38px;
      font-size: 12px;
      text-align: right;
      color: @text-muted;
    }
  }

  @media (max-width: 1400px) {
    .stat-charts {
      grid-template-columns: 300px 1fr;
    }

    .stat-panel--line {
      grid-column: 1 / -1;
    }
  }

  @media (max-width: 992px) {
    .stat-charts {
      grid-template-columns: 1fr;
    }
  }
</style>
