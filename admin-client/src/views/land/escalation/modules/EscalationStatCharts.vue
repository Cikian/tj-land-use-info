<template>
  <a-spin :spinning="loading">
    <div class="stat-charts">
      <!-- ============ ① 按论证结果分布（饼图） ============ -->
      <div class="stat-panel stat-panel--pie">
        <div class="stat-panel__title">按论证结果分布</div>
        <div v-if="resultSlices.length" class="stat-pie">
          <svg viewBox="0 0 200 200" class="stat-pie__svg" role="img" aria-label="按论证结果分布饼图">
            <g v-for="slice in resultSlices" :key="slice.name" class="stat-pie__slice" @click="drill({ argResult: slice.name })">
              <title>{{ slice.name }}：{{ slice.count }}（{{ slice.percent }}%）</title>
              <path :d="slice.path" :fill="slice.color" />
            </g>
          </svg>
          <div class="stat-legend stat-legend--column">
            <div
              v-for="slice in resultSlices"
              :key="'legend-' + slice.name"
              class="stat-legend__item stat-legend__item--clickable"
              @click="drill({ argResult: slice.name })">
              <span class="stat-legend__dot" :style="{ background: slice.color }"></span>
              <span class="stat-legend__name" :title="slice.name">{{ slice.name }}</span>
              <b>{{ slice.count }}</b>
              <span class="stat-legend__percent">{{ slice.percent }}%</span>
            </div>
          </div>
        </div>
        <a-empty v-else class="stat-panel__empty" description="暂无论证结果数据" />
      </div>

      <!-- ============ ② 按办理状态分布（环形图） ============ -->
      <div class="stat-panel stat-panel--ring">
        <div class="stat-panel__title">按办理状态分布</div>
        <div v-if="statusSegments.length" class="stat-ring">
          <svg viewBox="0 0 120 120" class="stat-ring__svg" role="img" aria-label="按办理状态分布环形图">
            <circle cx="60" cy="60" r="48" fill="none" stroke="#eef2f7" stroke-width="14" />
            <circle
              v-for="segment in statusSegments"
              :key="segment.name"
              class="stat-ring__segment"
              cx="60"
              cy="60"
              r="48"
              fill="none"
              :stroke="segment.color"
              stroke-width="14"
              :stroke-dasharray="segment.dasharray"
              :stroke-dashoffset="segment.dashoffset"
              transform="rotate(-90 60 60)"
              @click="drill({ status: segment.name })">
              <title>{{ segment.name }}：{{ segment.count }}（{{ segment.percent }}%）</title>
            </circle>
            <text x="60" y="56" text-anchor="middle" class="stat-ring__value">{{ statusTotal }}</text>
            <text x="60" y="74" text-anchor="middle" class="stat-ring__label">项目总数</text>
          </svg>
          <div class="stat-legend stat-legend--column">
            <div
              v-for="segment in statusSegments"
              :key="'legend-' + segment.name"
              class="stat-legend__item stat-legend__item--clickable"
              @click="drill({ status: segment.name })">
              <span class="stat-legend__dot" :style="{ background: segment.color }"></span>
              <span class="stat-legend__name">{{ segment.name }}</span>
              <b>{{ segment.count }}</b>
              <span class="stat-legend__percent">{{ segment.percent }}%</span>
            </div>
          </div>
        </div>
        <a-empty v-else class="stat-panel__empty" description="暂无办理状态数据" />
      </div>

      <!-- ============ ③ 按申报单位排行（横向条形图） ============ -->
      <div class="stat-panel stat-panel--bar">
        <div class="stat-panel__title">按申报单位排行</div>
        <div v-if="deptRows.length" class="stat-bars">
          <div
            v-for="item in deptRows"
            :key="item.name"
            class="stat-bars__row stat-bars__row--clickable"
            @click="drill({ declareDept: item.name })">
            <div class="stat-bars__name" :title="item.name">{{ item.name }}</div>
            <div class="stat-bars__track">
              <div class="stat-bars__fill" :style="{ width: item.percent + '%' }"></div>
            </div>
            <div class="stat-bars__value">{{ item.count }}</div>
          </div>
        </div>
        <a-empty v-else class="stat-panel__empty" description="暂无申报单位数据" />
      </div>

      <!-- ============ ④ 按行政区划分布（纵向柱状图） ============ -->
      <div class="stat-panel stat-panel--column">
        <div class="stat-panel__title">按行政区划分布</div>
        <div v-if="xzqhColumns.length" class="stat-column">
          <svg :viewBox="`0 0 ${columnWidth} ${columnHeight}`" class="stat-column__svg" role="img" aria-label="按行政区划分布柱状图">
            <g v-for="item in xzqhColumns" :key="item.name" class="stat-column__group" @click="drill({ xzqh: item.name })">
              <title>{{ item.name }}：{{ item.count }}</title>
              <rect
                :x="item.x"
                :y="item.y"
                :width="item.barWidth"
                :height="item.barHeight"
                rx="3"
                fill="#2e7cf6" />
              <text :x="item.centerX" :y="item.y - 6" text-anchor="middle" class="stat-column__value">{{ item.count }}</text>
              <text :x="item.centerX" :y="columnHeight - 8" text-anchor="middle" class="stat-column__label">{{ item.name }}</text>
            </g>
          </svg>
        </div>
        <a-empty v-else class="stat-panel__empty" description="暂无行政区划数据" />
      </div>

      <!-- ============ ⑤ 按申报时间趋势（折线图） ============ -->
      <div class="stat-panel stat-panel--line">
        <div class="stat-panel__title">按申报时间趋势</div>
        <div v-if="monthPoints.length" class="stat-trend">
          <svg :viewBox="`0 0 ${trendWidth} ${trendHeight}`" class="stat-trend__svg" role="img" aria-label="按申报时间趋势折线图">
            <line
              v-for="tick in trendTicks"
              :key="'tick-' + tick.value"
              :x1="trendPadding.left"
              :y1="tick.y"
              :x2="trendWidth - trendPadding.right"
              :y2="tick.y"
              stroke="#eef2f7"
              stroke-width="1" />
            <polyline :points="trendPolyline" fill="none" stroke="#2e7cf6" stroke-width="2" />
            <g v-for="point in monthPoints" :key="point.name" class="stat-trend__point" @click="drill(monthDrill(point.name))">
              <title>{{ point.name }}：{{ point.count }}</title>
              <circle :cx="point.x" :cy="point.y" r="4" fill="#fff" stroke="#2e7cf6" stroke-width="2" />
              <text :x="point.x" :y="point.y - 10" text-anchor="middle" class="stat-trend__value">{{ point.count }}</text>
              <text :x="point.x" :y="trendHeight - 6" text-anchor="middle" class="stat-trend__label">{{ point.name }}</text>
            </g>
          </svg>
        </div>
        <a-empty v-else class="stat-panel__empty" description="暂无申报时间数据（请检查申报时间是否填写）" />
      </div>
    </div>

    <!--
      设计文档 4.2 的统计接口有 5 个（byResult / byDept / byXzqh / byMonth / byType），
      而 5.5 点名的 5 张图里第 5 张是「办理状态分布（环形）」（走 ledger/countByStatus）。
      为不浪费 byType 接口、也不擅自加第 6 张图，这里把项目类型分布做成补充信息条。
    -->
    <div v-if="typeRows.length" class="stat-extra">
      <span class="stat-extra__label">项目类型分布</span>
      <span
        v-for="item in typeRows"
        :key="item.name"
        class="stat-extra__chip"
        @click="drill({ projectType: item.name })">
        {{ dictLabel(DICT.projectType, item.name) }} <b>{{ item.count }}</b>
      </span>
    </div>
  </a-spin>
</template>

<script>
  import { DICT, dictText } from '@/api/land/escalation'

  /**
   * 提级论证统计图（设计文档 5.2 / 5.5）
   *
   * 5 张图，全部用内联 SVG + CSS 绘制，**不引 ECharts**（照 ArchiveStatistics.vue 的写法）：
   *   ① 按论证结果分布 —— SVG 扇形饼图（path 弧段）
   *   ② 按办理状态分布 —— SVG 环形图（stroke-dasharray 分段）
   *   ③ 按申报单位排行 —— CSS 百分比横向条形
   *   ④ 按行政区划分布 —— SVG 矩形纵向柱状
   *   ⑤ 按申报时间趋势 —— SVG polyline 折线
   *
   * 每张图的图元都可点击，向上抛出同一套查询条件（drill），由查询页跳到台账页下钻，
   * 与 ArchiveStatistics.vue 的「图表下钻」一致。
   */
  export default {
    name: 'EscalationStatCharts',
    props: {
      loading: {
        type: Boolean,
        default: false
      },
      /** 以下 5 个统计结果都是 NameCount 数组（name / count） */
      byResult: {
        type: Array,
        default: () => []
      },
      byDept: {
        type: Array,
        default: () => []
      },
      byXzqh: {
        type: Array,
        default: () => []
      },
      byMonth: {
        type: Array,
        default: () => []
      },
      byType: {
        type: Array,
        default: () => []
      },
      /** 办理状态计数：NameCount 数组，或 { 状态: 数量 } 对象（兼容后端两种返回） */
      byStatus: {
        type: [Array, Object],
        default: () => []
      },
      /** 横向排行最多显示几条 */
      rankLimit: {
        type: Number,
        default: 8
      }
    },
    data () {
      return {
        DICT,
        palette: ['#2e7cf6', '#10b981', '#f59e0b', '#8b5cf6', '#ef4444', '#06b6d4', '#84cc16', '#ec4899'],
        // 折线画布（viewBox 坐标系，与实际像素无关，保证自适应缩放）
        trendWidth: 520,
        trendHeight: 210,
        trendPadding: { left: 40, right: 24, top: 26, bottom: 32 },
        // 柱状画布
        columnHeight: 210,
        columnMinWidth: 380,
        columnBarMax: 44
      }
    },
    computed: {
      normalized () {
        return {
          byResult: this.normalizeRows(this.byResult),
          byDept: this.normalizeRows(this.byDept),
          byXzqh: this.normalizeRows(this.byXzqh),
          byMonth: this.normalizeRows(this.byMonth).slice().sort((a, b) => String(a.name).localeCompare(String(b.name))),
          byType: this.normalizeRows(this.byType),
          byStatus: this.normalizeRows(this.byStatus)
        }
      },
      // ---------------- ① 饼图 ----------------
      resultSlices () {
        const rows = this.normalized.byResult
        const total = rows.reduce((sum, item) => sum + item.count, 0)
        if (!total) {
          return []
        }
        const cx = 100
        const cy = 100
        const r = 88
        let angle = -90
        return rows.map((item, index) => {
          const ratio = item.count / total
          const sweep = ratio * 360
          const start = angle
          const end = angle + sweep
          angle = end
          return {
            name: item.name,
            count: item.count,
            percent: Math.round(ratio * 100),
            color: this.palette[index % this.palette.length],
            path: sweep >= 359.99
              ? this.fullCirclePath(cx, cy, r)
              : this.arcPath(cx, cy, r, start, end)
          }
        })
      },
      // ---------------- ② 环形 ----------------
      statusSegments () {
        const rows = this.normalized.byStatus
        const total = rows.reduce((sum, item) => sum + item.count, 0)
        if (!total) {
          return []
        }
        const circumference = 2 * Math.PI * 48
        let offset = 0
        return rows.map((item, index) => {
          const ratio = item.count / total
          const length = ratio * circumference
          const segment = {
            name: item.name,
            count: item.count,
            percent: Math.round(ratio * 100),
            color: this.statusPalette(item.name, index),
            dasharray: `${length} ${circumference - length}`,
            dashoffset: -offset
          }
          offset += length
          return segment
        })
      },
      statusTotal () {
        return this.normalized.byStatus.reduce((sum, item) => sum + item.count, 0)
      },
      /** 项目类型分布（补充信息条，不占 5 张图的名额） */
      typeRows () {
        return this.normalized.byType
      },
      // ---------------- ③ 横向条形 ----------------
      deptRows () {
        const rows = this.normalized.byDept.slice(0, this.rankLimit)
        const max = rows.reduce((m, item) => Math.max(m, item.count), 0)
        return rows.map(item => ({
          name: item.name,
          count: item.count,
          percent: max ? Math.max(2, Math.round((item.count / max) * 100)) : 0
        }))
      },
      // ---------------- ④ 纵向柱状 ----------------
      columnWidth () {
        return Math.max(this.columnMinWidth, this.normalized.byXzqh.length * 56)
      },
      xzqhColumns () {
        const rows = this.normalized.byXzqh
        if (!rows.length) {
          return []
        }
        const padding = { left: 28, right: 20, top: 26, bottom: 30 }
        const innerWidth = this.columnWidth - padding.left - padding.right
        const innerHeight = this.columnHeight - padding.top - padding.bottom
        const max = rows.reduce((m, item) => Math.max(m, item.count), 0) || 1
        const slot = innerWidth / rows.length
        const barWidth = Math.min(this.columnBarMax, slot * 0.55)
        return rows.map((item, index) => {
          const barHeight = Math.max(2, (item.count / max) * innerHeight)
          const centerX = padding.left + slot * index + slot / 2
          return {
            name: item.name,
            count: item.count,
            x: centerX - barWidth / 2,
            y: padding.top + innerHeight - barHeight,
            centerX: centerX,
            barWidth: barWidth,
            barHeight: barHeight
          }
        })
      },
      // ---------------- ⑤ 折线 ----------------
      monthPoints () {
        const rows = this.normalized.byMonth
        if (!rows.length) {
          return []
        }
        const innerWidth = this.trendWidth - this.trendPadding.left - this.trendPadding.right
        const innerHeight = this.trendHeight - this.trendPadding.top - this.trendPadding.bottom
        const max = rows.reduce((m, item) => Math.max(m, item.count), 0) || 1
        const step = rows.length > 1 ? innerWidth / (rows.length - 1) : 0
        return rows.map((item, index) => ({
          name: item.name,
          count: item.count,
          x: rows.length > 1 ? this.trendPadding.left + step * index : this.trendPadding.left + innerWidth / 2,
          y: this.trendPadding.top + innerHeight - (item.count / max) * innerHeight
        }))
      },
      trendPolyline () {
        return this.monthPoints.map(point => `${point.x},${point.y}`).join(' ')
      },
      /** 3 条水平网格线（0 / 中值 / 最大值） */
      trendTicks () {
        const rows = this.normalized.byMonth
        const max = rows.reduce((m, item) => Math.max(m, item.count), 0) || 1
        const innerHeight = this.trendHeight - this.trendPadding.top - this.trendPadding.bottom
        return [0, 0.5, 1].map(ratio => ({
          value: ratio,
          y: this.trendPadding.top + innerHeight - ratio * innerHeight,
          label: Math.round(max * ratio)
        }))
      }
    },
    methods: {
      // ------------------------------------------------------------------
      // 下钻
      // ------------------------------------------------------------------
      drill (query) {
        this.$emit('drill', query)
      },
      /** 折线点下钻：按该月首末日转成申报时间区间 */
      monthDrill (name) {
        const text = String(name)
        const matched = /^(\d{4})-(\d{2})$/.exec(text)
        if (!matched) {
          return {}
        }
        const year = Number(matched[1])
        const month = Number(matched[2])
        const lastDay = new Date(year, month, 0).getDate()
        const pad = n => String(n).padStart(2, '0')
        return {
          beginDeclareDate: `${year}-${pad(month)}-01`,
          endDeclareDate: `${year}-${pad(month)}-${pad(lastDay)}`
        }
      },
      // ------------------------------------------------------------------
      // 数据归一化
      // ------------------------------------------------------------------
      /** 兼容 NameCount 数组 与 { name: count } 对象；过滤计数为 0 的项 */
      normalizeRows (source) {
        let rows = []
        if (Array.isArray(source)) {
          rows = source.map(item => ({
            name: item.name !== undefined && item.name !== null && item.name !== '' ? String(item.name) : '未填写',
            count: Number(item.count) || 0
          }))
        } else if (source && typeof source === 'object') {
          rows = Object.keys(source).map(key => ({ name: key, count: Number(source[key]) || 0 }))
        }
        return rows.filter(item => item.count > 0)
      },
      dictLabel (dictCode, value) {
        return dictText(dictCode, value) || value
      },
      statusPalette (name, index) {
        // 与台账标签同一套语义色：未办理灰 / 办理中蓝 / 已办结绿 / 已归档青
        if (name === '办理中') {
          return '#2e7cf6'
        }
        if (name === '已办结') {
          return '#10b981'
        }
        if (name === '已归档') {
          return '#06b6d4'
        }
        if (name === '未办理') {
          return '#cbd5e1'
        }
        return this.palette[index % this.palette.length]
      },
      // ------------------------------------------------------------------
      // SVG 路径
      // ------------------------------------------------------------------
      polar (cx, cy, r, angle) {
        const radian = (angle * Math.PI) / 180
        return { x: cx + r * Math.cos(radian), y: cy + r * Math.sin(radian) }
      },
      arcPath (cx, cy, r, startAngle, endAngle) {
        const start = this.polar(cx, cy, r, startAngle)
        const end = this.polar(cx, cy, r, endAngle)
        const largeArc = endAngle - startAngle > 180 ? 1 : 0
        return `M ${cx} ${cy} L ${start.x.toFixed(2)} ${start.y.toFixed(2)} ` +
          `A ${r} ${r} 0 ${largeArc} 1 ${end.x.toFixed(2)} ${end.y.toFixed(2)} Z`
      },
      fullCirclePath (cx, cy, r) {
        return `M ${cx} ${cy - r} A ${r} ${r} 0 1 1 ${cx - 0.01} ${cy - r} Z`
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .stat-charts {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 16px;
    margin-bottom: 16px;
  }

  .stat-panel {
    display: flex;
    flex-direction: column;
    padding: 16px;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;
    min-height: 288px;

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

    /* 折线与柱状需要更宽的画布，跨两列 */
    &--line,
    &--column {
      grid-column: span 2;
    }
  }

  @media (max-width: 992px) {
    .stat-panel--line,
    .stat-panel--column {
      grid-column: span 1;
    }
  }

  /* ---------------- 饼图 ---------------- */
  .stat-pie {
    flex: 1 1 auto;
    display: flex;
    align-items: center;
    gap: 16px;

    &__svg {
      flex: none;
      width: 150px;
      height: 150px;
    }

    &__slice {
      cursor: pointer;
      transition: opacity 0.2s ease;

      &:hover {
        opacity: 0.78;
      }
    }
  }

  /* ---------------- 环形图 ---------------- */
  .stat-ring {
    flex: 1 1 auto;
    display: flex;
    align-items: center;
    gap: 16px;

    &__svg {
      flex: none;
      width: 150px;
      height: 150px;
    }

    &__segment {
      cursor: pointer;
      transition: opacity 0.2s ease;

      &:hover {
        opacity: 0.78;
      }
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

  /* ---------------- 图例 ---------------- */
  .stat-legend {
    flex: 1 1 auto;
    min-width: 0;

    &--column {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    &__item {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: @text-muted;

      b {
        color: #0f172a;
      }

      &--clickable {
        cursor: pointer;

        &:hover {
          color: #1d4ed8;
        }
      }
    }

    &__name {
      flex: 1 1 auto;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__percent {
      flex: none;
      width: 42px;
      text-align: right;
      color: @text-weak;
    }

    &__dot {
      flex: none;
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }
  }

  /* ---------------- 横向条形 ---------------- */
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

      &--clickable {
        cursor: pointer;

        &:hover .stat-bars__name {
          color: #1d4ed8;
        }
      }
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

  /* ---------------- 纵向柱状 ---------------- */
  .stat-column {
    flex: 1 1 auto;
    display: flex;
    align-items: center;
    overflow-x: auto;

    &__svg {
      width: 100%;
      height: 210px;
    }

    &__group {
      cursor: pointer;

      &:hover rect {
        fill: #1d4ed8;
      }
    }

    &__value {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 11px;
      fill: @text-muted;
    }

    &__label {
      font-size: 11px;
      fill: @text-weak;
    }
  }

  /* ---------------- 折线 ---------------- */
  .stat-trend {
    flex: 1 1 auto;
    display: flex;
    align-items: center;
    overflow-x: auto;

    &__svg {
      width: 100%;
      height: 210px;
    }

    &__point {
      cursor: pointer;

      &:hover circle {
        stroke: #1d4ed8;
      }
    }

    &__value {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 11px;
      fill: @text-muted;
    }

    &__label {
      font-size: 10px;
      fill: @text-weak;
    }
  }

  /* ---------------- 补充信息条 ---------------- */
  .stat-extra {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    padding: 10px 14px;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;

    &__label {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
    }

    &__chip {
      padding: 2px 10px;
      font-size: 12px;
      color: @text-muted;
      background: #f1f5f9;
      border-radius: 12px;
      cursor: pointer;

      &:hover {
        color: #1d4ed8;
        background: #e0ecff;
      }

      b {
        color: #0f172a;
      }
    }
  }
</style>
