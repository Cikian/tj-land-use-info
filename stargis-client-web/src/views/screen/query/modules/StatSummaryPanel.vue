<template>
  <!--
    StatSummaryPanel 分析结果（查询统计 · 两个数据集共用）
    --------------------------------
    高保真 `查询统计.html` 的「分析结果」是两列（字段名 / 字段值）的一长串指标；
    这里用「标签 + 数值」的小格子铺成三列，指标顺序与旧面板一致
    （指标定义在 views/screen/query/constants.js，顺序改了就是版面改了）。

    ★ 指标值全部来自后端 `StatSummaryVO`，前端**只做格式化与拼单位**：
      不在前端算比率、不在前端求和 —— 页面上的数、Excel 里的数、首页看板的数
      必须同源，否则中心对账时会出现「同一个指标三个值」。
  -->
  <screen-panel class="stat-summary" title="分析结果" scrollable>

    <div v-if="loading" class="stat-summary__loading">
      <screen-loading inline text="正在统计…" />
    </div>

    <div v-else class="stat-summary__grid">
      <div v-for="metric in metrics" :key="metric.key" class="stat-summary__item">
        <span class="stat-summary__label">{{ metric.label }}</span>
        <span class="stat-summary__value">
          <b>{{ display(metric) }}</b>
          <em v-if="metric.unit">{{ metric.unit }}</em>
        </span>
      </div>
    </div>
  </screen-panel>
</template>

<script>
import { ScreenIcon, ScreenLoading, ScreenPanel } from '@/components/screen'

export default {
  name: 'StatSummaryPanel',
  components: { ScreenIcon, ScreenLoading, ScreenPanel },
  props: {
    /** 指标定义：[{ key, label, unit, percent?, precision? }] */
    metrics: { type: Array, default: () => [] },
    /** 后端 StatSummaryVO */
    summary: { type: Object, default: () => ({}) },
    loading: { type: Boolean, default: false }
  },
  methods: {
    display (metric) {
      const raw = this.summary ? this.summary[metric.key] : null
      // 后端没返回这个指标时显示「—」而不是 0：0 会被误读成「真的是 0」
      if (raw === null || raw === undefined || raw === '') {
        return '—'
      }
      if (metric.percent) {
        return `${this.fixed(raw, 1)}%`
      }
      if (metric.precision) {
        return this.fixed(raw, metric.precision)
      }
      return this.fixed(raw, 0)
    },
    fixed (value, precision) {
      const num = Number(value)
      if (Number.isNaN(num)) {
        return String(value)
      }
      return num.toFixed(precision)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.stat-summary {
  &__hint {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__loading {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 120px;
  }

  /* 指标格子：小屏下退成两列，避免标签被压成两行 */
  &__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px 16px;

    @media (max-width: 1500px) {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  &__item {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 8px 10px;
    background: var(--screen-elevate);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);
  }

  &__label {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__value {
    display: flex;
    align-items: baseline;
    gap: 4px;
    font-family: var(--screen-font-number, inherit);

    b {
      font-size: 20px;
      font-weight: 600;
      color: var(--screen-text);
      line-height: 1.1;
    }

    em {
      font-size: var(--screen-font-xs);
      font-style: normal;
      color: var(--screen-text-mute);
    }
  }
}
</style>
