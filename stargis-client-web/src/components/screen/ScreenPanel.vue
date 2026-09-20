<template>
  <!--
    ScreenPanel 大屏面板容器
    --------------------------------
    设计稿中所有卡片（出让地块情况统计 / 各区出让排行 / 需要落实配套 / 预警表格）
    共用的外框：青绿描边玻璃面板 + 标题栏 + 内容区 + 可选底栏。

    用法：
      <screen-panel title="出让地块情况统计">
        <template #extra>...</template>
        内容
        <template #footer>合计 215宗 · 388条道路</template>
      </screen-panel>
  -->
  <section
    class="screen-panel"
    :class="[
      `screen-panel--${variant}`,
      { 'is-collapsed': innerCollapsed, 'is-decorated': decorated, 'is-flat': flat },
    ]"
  >
    <header v-if="title || $slots.title || $slots.extra || collapsible" class="screen-panel__head">
      <span v-if="bar" class="screen-panel__bar" aria-hidden="true" />
      <h3 v-if="!$slots.title" class="screen-panel__title">{{ title }}</h3>
      <div v-else class="screen-panel__title is-slot">
        <slot name="title" />
      </div>
      <span v-if="subTitle" class="screen-panel__sub">{{ subTitle }}</span>
      <div class="screen-panel__extra">
        <slot name="extra" />
      </div>
      <button
        v-if="collapsible"
        type="button"
        class="screen-panel__toggle"
        :aria-expanded="String(!innerCollapsed)"
        :aria-label="innerCollapsed ? '展开面板' : '收起面板'"
        @click="toggleCollapse"
      >
        <svg viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
          <path
            d="M3 6l5 5 5-5"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
    </header>

    <div v-show="!innerCollapsed" class="screen-panel__body" :class="{ 'is-scroll': scrollable }">
      <slot />
    </div>

    <footer v-if="$slots.footer" v-show="!innerCollapsed" class="screen-panel__foot">
      <slot name="footer" />
    </footer>

    <!-- 四角装饰（可选） -->
    <template v-if="decorated">
      <i class="screen-panel__corner is-tl" aria-hidden="true" />
      <i class="screen-panel__corner is-tr" aria-hidden="true" />
      <i class="screen-panel__corner is-bl" aria-hidden="true" />
      <i class="screen-panel__corner is-br" aria-hidden="true" />
    </template>
  </section>
</template>

<script>
export default {
  name: 'ScreenPanel',
  props: {
    /** 面板标题 */
    title: { type: String, default: '' },
    /** 标题右侧的补充说明，例如单位、统计口径 */
    subTitle: { type: String, default: '' },
    /** 外观变体：default 玻璃面板 / plain 无底（用于嵌套） */
    variant: { type: String, default: 'default' },
    /** 内容区超出时是否滚动 */
    scrollable: { type: Boolean, default: false },
    /** 是否显示标题前的高亮竖条 */
    bar: { type: Boolean, default: true },
    /** 是否显示四角科技装饰 */
    decorated: { type: Boolean, default: false },
    /** 去掉阴影，用于平铺场景 */
    flat: { type: Boolean, default: false },
    /** 是否允许折叠 */
    collapsible: { type: Boolean, default: false },
    /** 折叠状态（支持 .sync） */
    collapsed: { type: Boolean, default: false },
  },
  data () {
    return {
      innerCollapsed: this.collapsed,
    }
  },
  watch: {
    collapsed (val) {
      this.innerCollapsed = val
    },
    innerCollapsed (val) {
      this.$emit('update:collapsed', val)
      this.$emit('collapse-change', val)
    },
  },
  methods: {
    toggleCollapse () {
      this.innerCollapsed = !this.innerCollapsed
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  .screen-glass();

  &--plain {
    background: transparent;
    border-color: transparent;
    box-shadow: none;
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  &.is-flat {
    box-shadow: none;
  }

  &__head {
    display: flex;
    align-items: center;
    flex: 0 0 auto;
    gap: var(--screen-space-2);
    height: 40px;
    padding: 0 var(--screen-space-4) 0 var(--screen-space-3);
    background: var(--screen-panel-head-bg);
    border-bottom: 1px solid var(--screen-border-soft);
  }

  // 标题左侧竖条，设计稿中每个面板标题前的高亮短条
  &__bar {
    flex: 0 0 auto;
    width: 3px;
    height: 14px;
    border-radius: var(--screen-radius-pill);
    background: linear-gradient(180deg, var(--screen-accent) 0%, var(--screen-accent-deep) 100%);
    box-shadow: 0 0 8px var(--screen-accent-glow);
  }

  &__title {
    margin: 0;
    font-size: var(--screen-font-md);
    font-weight: 600;
    line-height: 1;
    letter-spacing: 0.04em;
    color: var(--screen-text);
    .screen-ellipsis();

    // 标题槽位（例如放入标签页）不裁剪、不省略
    &.is-slot {
      display: flex;
      align-items: center;
      overflow: visible;
      white-space: normal;
    }
  }

  &__sub {
    flex: 0 0 auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__extra {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin-left: auto;
    min-width: 0;
  }

  &__toggle {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    padding: 0;
    color: var(--screen-text-sub);
    background: transparent;
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease),
      transform var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: var(--screen-accent);
      border-color: var(--screen-border-strong);
    }
  }

  &.is-collapsed &__toggle svg {
    transform: rotate(-90deg);
  }

  &__body {
    position: relative;
    display: flex;
    flex-direction: column;
    flex: 1 1 auto;
    min-height: 0;
    padding: var(--screen-space-3) var(--screen-space-4);

    &.is-scroll {
      overflow-y: auto;
      overflow-x: hidden;
    }
  }

  &__foot {
    flex: 0 0 auto;
    padding: 8px var(--screen-space-4);
    border-top: 1px solid var(--screen-border-soft);
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
  }

  &__corner {
    .screen-corner(10px, 1px, var(--screen-border-strong));

    &.is-tl {
      top: 0;
      left: 0;
      border-top-width: 1px;
      border-left-width: 1px;
      border-top-left-radius: var(--screen-radius);
    }

    &.is-tr {
      top: 0;
      right: 0;
      border-top-width: 1px;
      border-right-width: 1px;
      border-top-right-radius: var(--screen-radius);
    }

    &.is-bl {
      bottom: 0;
      left: 0;
      border-bottom-width: 1px;
      border-left-width: 1px;
      border-bottom-left-radius: var(--screen-radius);
    }

    &.is-br {
      bottom: 0;
      right: 0;
      border-bottom-width: 1px;
      border-right-width: 1px;
      border-bottom-right-radius: var(--screen-radius);
    }
  }
}

// 细滚动条，避免破坏大屏观感
.screen-panel__body.is-scroll {
  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--screen-border);
    border-radius: var(--screen-radius-pill);
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }
}
</style>
