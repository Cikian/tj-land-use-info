<template>
  <!--
    ScreenLoading 大屏加载指示器（含遮罩），替代 antd 的 a-spin
    --------------------------------
    a-spin 的指示器是蓝色实心圆点，且遮罩是浅色半透明，在青绿暗色大屏上既突兀又会
    「洗白」底下的地图/表格。这里给出一套同族实现：青绿描边圆环 + 深色遮罩。

    【重要】overlay 模式下的遮罩是 position: absolute，本组件根节点刻意不做定位，
       因此**直接父级必须设置 position: relative**（例如 ScreenPanel__body 这类容器），
       否则遮罩会一直向上找到最近的定位祖先，盖住整块区域。
       父级若没有定位，请改用 :overlay="false" 的内联模式。

    用法：
      【遮罩模式】包住需要被遮住的区域，其直接父级必须是 position: relative：
        <div class="panel-body" style="position: relative">
          <screen-loading :loading="loading" text="加载中…">
            <screen-data-table :columns="columns" :data="rows" />
          </screen-loading>
        </div>

      【内联模式】默认插槽之外再渲染一行「圆环 + 文案」，不 loading 时只渲染默认插槽：
        <screen-loading :loading="loading" :overlay="false">加载完成</screen-loading>

    无障碍：
      - 两种模式的指示器都是 role="status" + aria-live="polite" + aria-busy="true"；
      - 圆环 SVG 本身 aria-hidden="true"，语义由可见文案承担（text 建议不要置空）；
      - 减少动态效果时不冻结动画，而是放慢到 2400ms，仍能被识别为「正在忙」。
  -->
  <div class="screen-loading" :class="{ 'is-overlay': overlay }">
    <!-- 默认插槽始终渲染（被包裹的内容） -->
    <slot />

    <!-- 遮罩模式：盖住整个父级 -->
    <div
      v-if="overlay && loading"
      class="screen-loading__mask"
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      <!-- 圆环与内联模式各写一份，保持每个分支自包含，避免为一小段 SVG 再抽一个组件 -->
      <svg
        class="screen-loading__spinner"
        :width="spinnerSize"
        :height="spinnerSize"
        viewBox="0 0 50 50"
        fill="none"
        aria-hidden="true"
        focusable="false"
      >
        <circle
          class="screen-loading__track"
          cx="25"
          cy="25"
          r="20"
          stroke="var(--screen-border)"
          stroke-width="4"
        />
        <circle
          class="screen-loading__ring"
          cx="25"
          cy="25"
          r="20"
          stroke="var(--screen-accent)"
          stroke-width="4"
          stroke-linecap="round"
        />
      </svg>
      <span v-if="text" class="screen-loading__text">{{ text }}</span>
    </div>

    <!-- 内联模式：不 loading 时什么都不渲染 -->
    <div
      v-else-if="loading"
      class="screen-loading__inline"
      role="status"
      aria-live="polite"
      aria-busy="true"
    >
      <svg
        class="screen-loading__spinner"
        :width="spinnerSize"
        :height="spinnerSize"
        viewBox="0 0 50 50"
        fill="none"
        aria-hidden="true"
        focusable="false"
      >
        <circle
          class="screen-loading__track"
          cx="25"
          cy="25"
          r="20"
          stroke="var(--screen-border)"
          stroke-width="4"
        />
        <circle
          class="screen-loading__ring"
          cx="25"
          cy="25"
          r="20"
          stroke="var(--screen-accent)"
          stroke-width="4"
          stroke-linecap="round"
        />
      </svg>
      <span v-if="text" class="screen-loading__text">{{ text }}</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ScreenLoading',
  props: {
    /** 是否处于加载中；false 时遮罩 / 内联指示器都不渲染（默认插槽照常渲染） */
    loading: { type: Boolean, default: false },
    /** 圆环旁/下方的文案，同时承担无障碍语义，建议保留默认值 */
    text: { type: String, default: '加载中…' },
    /** 是否使用遮罩模式（true 覆盖父级，false 内联一行） */
    overlay: { type: Boolean, default: true },
    /** 圆环直径（px） */
    size: { type: Number, default: 22 },
  },
  computed: {
    /** 防御非法尺寸，保证圆环至少有可辨识的边长 */
    spinnerSize () {
      return Number.isFinite(this.size) && this.size > 0 ? this.size : 22
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-loading {
  // 注意：这里刻意不写 position: relative，
  // 遮罩要以「父级」为定位基准（父级必须 position: relative）。

  &__mask {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 6;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--screen-space-2);
    // 深色遮罩：压暗而不「洗白」底下的地图 / 表格
    background: rgba(2, 19, 22, 0.62);
  }

  &__inline {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    padding: var(--screen-space-2) 0;
  }

  // 文案强制单行：内联模式常出现在工具栏里，换行会撑高整行
  &__text {
    font-size: var(--screen-font-sm);
    line-height: 1.5;
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__spinner {
    animation: screen-loading-rotate 900ms linear infinite;
  }

  // 环形缺口：dasharray 由小到大再收回，配合旋转读起来才像「转动中的圆环」
  &__ring {
    stroke-dasharray: 90 150;
    animation: screen-loading-dash 1400ms ease-in-out infinite;
  }
}

@keyframes screen-loading-rotate {
  to {
    transform: rotate(360deg);
  }
}

@keyframes screen-loading-dash {
  0% {
    stroke-dasharray: 1 150;
    stroke-dashoffset: 0;
  }

  50% {
    stroke-dasharray: 90 150;
    stroke-dashoffset: -35;
  }

  100% {
    stroke-dasharray: 90 150;
    stroke-dashoffset: -124;
  }
}

// 减少动态效果：不冻结（冻结后与静态图标无法区分），只大幅放慢转速与扫动节奏
@media (prefers-reduced-motion: reduce) {
  .screen-loading__spinner {
    animation-duration: 2400ms;
  }

  .screen-loading__ring {
    animation-duration: 2400ms;
  }
}
</style>
