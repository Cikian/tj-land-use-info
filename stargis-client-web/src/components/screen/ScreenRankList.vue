<template>
  <!--
    ScreenRankList 排行条形列表
    --------------------------------
    设计稿中的「各区出让排行」：序号 + 名称 + 横向条形 + 数值。
    条形长度按 max 归一化；颜色之外同时给出数值文本，不依赖颜色单独传达信息。
  -->
  <ul class="screen-rank-list" :class="{ 'is-clickable': clickable }">
    <li
      v-for="(item, index) in normalized"
      :key="item.name || index"
      class="screen-rank-list__row"
      :class="{ 'is-clickable': clickable }"
      :tabindex="clickable ? 0 : undefined"
      :role="clickable ? 'button' : undefined"
      @click="handleClick(item, index)"
      @keydown.enter.prevent="handleClick(item, index)"
      @keydown.space.prevent="handleClick(item, index)"
    >
      <span v-if="showIndex" class="screen-rank-list__index">{{ padIndex(index + 1) }}</span>
      <span class="screen-rank-list__name">{{ item.name }}</span>
      <span
        class="screen-rank-list__track"
        role="img"
        :aria-label="`${item.name} ${item.display}${item.unit}`"
      >
        <i
          class="screen-rank-list__fill"
          :class="`is-${item.tone}`"
          :style="{ width: barReady ? `${item.ratio}%` : '0%' }"
        />
      </span>
      <span class="screen-rank-list__value">
        {{ item.display }}<em v-if="item.unit">{{ item.unit }}</em>
      </span>
    </li>

    <li v-if="!normalized.length" class="screen-rank-list__empty">{{ emptyText }}</li>
  </ul>
</template>

<script>
import { formatNumber, padIndex, prefersReducedMotion, raf, toNumber } from './utils'

export default {
  name: 'ScreenRankList',
  props: {
    /** 数据项：[{ name, value, unit, tone }] */
    items: { type: Array, default: () => [] },
    /** 归一化基准值，缺省取最大值 */
    max: { type: [Number, String], default: null },
    /** 单位，数据项未指定时生效 */
    unit: { type: String, default: '' },
    /** 数值小数位 */
    precision: { type: Number, default: 0 },
    /** 是否显示两位序号 */
    showIndex: { type: Boolean, default: true },
    /** 是否按数值降序展示 */
    sortDesc: { type: Boolean, default: false },
    /** 行是否可点击（点击后抛出 item-click） */
    clickable: { type: Boolean, default: false },
    /** 是否入场动画 */
    animated: { type: Boolean, default: true },
    /** 空数据文案 */
    emptyText: { type: String, default: '暂无数据' },
  },
  data () {
    return {
      barReady: false,
    }
  },
  computed: {
    normalized () {
      const list = this.sortDesc
        ? [...this.items].sort((a, b) => toNumber(b.value) - toNumber(a.value))
        : this.items
      const maxValue =
        this.max === null || this.max === undefined
          ? list.reduce((max, item) => Math.max(max, toNumber(item.value)), 0)
          : toNumber(this.max)
      const safeMax = maxValue > 0 ? maxValue : 1

      return list.map((item) => ({
        name: item.name,
        unit: item.unit === undefined ? this.unit : item.unit,
        tone: item.tone || 'accent',
        display: formatNumber(item.value, item.precision === undefined ? this.precision : item.precision),
        ratio: Number(((toNumber(item.value) / safeMax) * 100).toFixed(2)),
        raw: item,
      }))
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
    padIndex,
    handleClick (item, index) {
      if (!this.clickable) return
      this.$emit('item-click', item.raw, index)
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-rank-list {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 3px;
  // 撑满面板内容区：父级为 flex 时按 flex 分配，否则退化为 100% 高度
  flex: 1 1 auto;
  height: 100%;
  min-height: 0;
  margin: 0;
  padding: 0;
  list-style: none;

  &__row {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    flex: 1 1 0;
    min-height: 22px;
    max-height: 40px;
    min-width: 0;
    padding: 0 2px;
    border-radius: var(--screen-radius-sm);
    font-size: var(--screen-font-sm);

    &.is-clickable {
      cursor: pointer;
      transition: background-color var(--screen-duration) var(--screen-ease);
      .screen-focus-ring();

      &:hover {
        background: var(--screen-elevate);
      }
    }
  }

  &__index {
    flex: 0 0 auto;
    width: 20px;
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__name {
    flex: 0 0 auto;
    width: 58px;
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__track {
    position: relative;
    flex: 1 1 auto;
    min-width: 30px;
    height: 7px;
    border-radius: var(--screen-radius-pill);
    background: var(--screen-bar-track);
    overflow: hidden;
  }

  &__fill {
    display: block;
    height: 100%;
    border-radius: inherit;
    transition: width 760ms var(--screen-ease);
    .screen-bar-fill();

    &.is-soft {
      background-image: linear-gradient(90deg, var(--screen-accent-soft) 0%, var(--screen-accent-deep) 100%);
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

  &__value {
    flex: 0 0 auto;
    min-width: 40px;
    text-align: right;
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);

    em {
      margin-left: 1px;
      font-style: normal;
      font-size: var(--screen-font-xs);
      color: var(--screen-text-mute);
    }
  }

  &__empty {
    flex: 1 1 auto;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--screen-text-mute);
    font-size: var(--screen-font-sm);
  }
}
</style>
