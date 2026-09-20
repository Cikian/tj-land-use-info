<template>
  <!--
    ScreenBarList 堆叠式条形列表
    --------------------------------
    「档案统计」面板里的分类 / 状态分布（例如按档案门类、按归档状态的数量与占比）。

    与 ScreenRankList 的区别（刻意的分工）：
      ScreenRankList 是「序号 + 名称 + 条形 + 数值」的单行排行，适合短标签的高密度榜单；
      ScreenBarList 是两行堆叠：第一行标签 + 数值 + 占比，第二行整条通栏进度条。
      中文分类名较长、又需要同时展示数值与百分比时，两行结构不会把条形挤成一条缝。

    用法：
      <screen-bar-list
        :items="[
          { name: '国有建设用地使用权出让', value: 168, percent: 62.4, tone: 'accent', hint: '含挂牌与拍卖' },
          { name: '划拨决定书', value: 41, percent: 15.2, tone: 'cyan' },
        ]"
        unit="件"
        sort-desc
        clickable
        @item-click="onItemClick"
      />

    无障碍：
      - 每行的数值、占比都以文本呈现，颜色不是唯一线索；
      - 条形轨道带 role="img" + aria-label（名称 + 数值 + 占比），读屏可读；
      - 可点击行带 role="button" + tabindex="0"，支持 Enter / Space，并有可见焦点环。
  -->
  <ul class="screen-bar-list" :class="{ 'is-clickable': clickable }">
    <li
      v-for="(item, index) in normalized"
      :key="item.key"
      class="screen-bar-list__row"
      :class="{ 'is-clickable': clickable }"
      :tabindex="clickable ? 0 : undefined"
      :role="clickable ? 'button' : undefined"
      @click="handleClick(item, index)"
      @keydown.enter.prevent="handleClick(item, index)"
      @keydown.space.prevent="handleClick(item, index)"
    >
      <!-- 第一行：标签 + 数值 + 占比 -->
      <div class="screen-bar-list__head">
        <span class="screen-bar-list__name" :title="item.name">{{ item.name }}</span>
        <span v-if="showValue" class="screen-bar-list__value">
          {{ item.display }}<em v-if="item.unit">{{ item.unit }}</em>
        </span>
        <span v-if="showPercent && item.hasPercent" class="screen-bar-list__percent">
          {{ item.percentText }}
        </span>
      </div>

      <!-- 第二行：通栏进度条 -->
      <span class="screen-bar-list__track" role="img" :aria-label="item.barLabel">
        <i
          class="screen-bar-list__fill"
          :class="`is-${item.tone}`"
          :style="{ width: barReady ? `${item.ratio}%` : '0%' }"
        />
      </span>

      <!-- 可选的补充说明 -->
      <p v-if="item.hint" class="screen-bar-list__hint">{{ item.hint }}</p>
    </li>

    <li v-if="!normalized.length" class="screen-bar-list__empty">{{ emptyText }}</li>
  </ul>
</template>

<script>
import { formatNumber, formatPercent, prefersReducedMotion, raf, toNumber } from './utils'

export default {
  name: 'ScreenBarList',
  props: {
    /** 数据项：[{ name, value, percent, tone, hint }] */
    items: { type: Array, default: () => [] },
    /** 归一化基准值；为空时取最大 value（全为 0 时退化为 1，避免除零） */
    max: { type: [Number, String], default: null },
    /** 单位，数据项未指定时生效 */
    unit: { type: String, default: '' },
    /** 数值小数位 */
    precision: { type: Number, default: 0 },
    /** 百分比小数位 */
    percentPrecision: { type: Number, default: 1 },
    /** 是否显示数值 */
    showValue: { type: Boolean, default: true },
    /** 是否显示百分比（仅对携带 percent 的数据项生效） */
    showPercent: { type: Boolean, default: true },
    /** 是否按数值降序展示 */
    sortDesc: { type: Boolean, default: false },
    /** 是否入场动画（条形从 0 展开） */
    animated: { type: Boolean, default: true },
    /** 空数据文案 */
    emptyText: { type: String, default: '暂无数据' },
    /** 行是否可点击（点击 / 回车 / 空格后抛出 item-click） */
    clickable: { type: Boolean, default: false },
  },
  data () {
    return {
      barReady: false,
    }
  },
  computed: {
    normalized () {
      const list = this.sortDesc
        ? [...this.items].sort((a, b) => toNumber(b && b.value) - toNumber(a && a.value))
        : this.items

      const maxValue = this.max === null || this.max === undefined
        ? list.reduce((max, item) => Math.max(max, toNumber(item && item.value)), 0)
        : toNumber(this.max)
      // 基准值非法（0 / 负数）时退化为 1，保证 ratio 恒为有限数字
      const safeMax = maxValue > 0 ? maxValue : 1

      return list.map((item, index) => {
        const source = item || {}
        const name = source.name === undefined || source.name === null ? '' : String(source.name)
        const rawPercent = source.percent
        const hasPercent = rawPercent !== undefined && rawPercent !== null && rawPercent !== ''
        const percent = hasPercent ? toNumber(rawPercent) : 0
        const display = formatNumber(source.value, source.precision === undefined ? this.precision : source.precision)
        const percentText = hasPercent ? formatPercent(percent, this.percentPrecision) : ''
        const unit = source.unit === undefined ? this.unit : source.unit

        return {
          key: `${name || 'row'}-${index}`,
          name,
          unit,
          hint: source.hint || '',
          tone: source.tone || 'accent',
          display,
          hasPercent,
          percentText,
          ratio: Number(((toNumber(source.value) / safeMax) * 100).toFixed(2)),
          // 读屏文案：名称 + 数值 + 占比，不依赖视觉条形
          barLabel: hasPercent
            ? `${name} ${display}${unit}，占比 ${percentText}`
            : `${name} ${display}${unit}`,
          raw: source,
        }
      })
    },
  },
  watch: {
    // 数据整体变化后重播一次条形展开动画（仅在允许动效时）
    items () {
      if (!this.animated || prefersReducedMotion()) {
        this.barReady = true
        return
      }
      this.barReady = false
      this.$nextTick(() => {
        raf(() => {
          this.barReady = true
        })
      })
    },
  },
  mounted () {
    if (!this.animated || prefersReducedMotion()) {
      this.barReady = true
      return
    }
    this.$nextTick(() => {
      raf(() => {
        this.barReady = true
      })
    })
  },
  methods: {
    handleClick (item, index) {
      if (!this.clickable) return
      this.$emit('item-click', item.raw, index)
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-bar-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  // 撑满面板内容区；同时给出最大高度，长分类列表不会把面板顶破
  flex: 1 1 auto;
  min-height: 0;
  max-height: 260px;
  margin: 0;
  padding: 0 2px 0 0;
  list-style: none;
  overflow-y: auto;
  overflow-x: hidden;
  .screen-scrollbar();

  &__row {
    display: flex;
    flex-direction: column;
    gap: 5px;
    flex: 0 0 auto;
    min-width: 0;
    padding: 5px 6px;
    border-radius: var(--screen-radius-sm);
    transition: background-color var(--screen-duration) var(--screen-ease);

    &.is-clickable {
      cursor: pointer;
      .screen-focus-ring();

      &:hover {
        background: var(--screen-elevate);
      }

      &:active {
        background: rgba(47, 227, 192, 0.12);
      }
    }
  }

  &__head {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-2);
    min-width: 0;
  }

  &__name {
    flex: 1 1 auto;
    min-width: 0;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__value {
    flex: 0 0 auto;
    .screen-number-font(var(--screen-font-sm), 600);
    color: var(--screen-text);

    em {
      margin-left: 1px;
      font-style: normal;
      font-size: var(--screen-font-xs);
      font-weight: 400;
      color: var(--screen-text-mute);
    }
  }

  &__percent {
    flex: 0 0 auto;
    min-width: 44px;
    text-align: right;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    font-variant-numeric: tabular-nums;
    color: var(--screen-accent-soft);
  }

  &__track {
    display: block;
    height: 6px;
    border-radius: var(--screen-radius-pill);
    background: var(--screen-bar-track);
    overflow: hidden;
  }

  &__fill {
    display: block;
    height: 100%;
    transition: width 760ms var(--screen-ease);
    .screen-bar-fill();

    &.is-cyan {
      background-image: linear-gradient(90deg, var(--screen-viz-cyan) 0%, var(--screen-viz-blue) 100%);
    }

    &.is-muted {
      background-image: linear-gradient(
        90deg,
        var(--screen-bar-muted-from) 0%,
        var(--screen-bar-muted-to) 100%
      );
    }

    &.is-warning {
      background-image: linear-gradient(90deg, var(--screen-viz-amber) 0%, #b9761c 100%);
    }

    &.is-danger {
      background-image: linear-gradient(90deg, #ff9d8d 0%, #c9432f 100%);
    }
  }

  &__hint {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.4;
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  &__empty {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: 1 1 auto;
    min-height: 72px;
    font-size: var(--screen-font-sm);
    .screen-placeholder();
  }
}
</style>
