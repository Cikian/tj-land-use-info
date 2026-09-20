<template>
  <!--
    ScreenMapStage 地图舞台
    --------------------------------
    左侧/右侧/底部面板之外的整屏地图承载层：
      · 默认插槽放真实地图（Cesium #home 容器）
      · 底层内置青绿暗色兜底底图（网格 + 辉光），地图未加载时界面依然完整
      · 可选的取景框角标与暗角，弱化边缘、突出中心

    注意：本组件自身铺满全屏，但装饰层一律 pointer-events: none，
         保证地图可以正常拖拽 / 缩放 / 点选。
  -->
  <div class="screen-map-stage" :class="{ 'is-offline': !enabled }">
    <!-- 兜底底图 -->
    <div class="screen-map-stage__backdrop" aria-hidden="true">
      <span v-if="showGlow" class="screen-map-stage__glow" />
      <span v-if="showGrid" class="screen-map-stage__grid" />
    </div>

    <!-- 真实地图 -->
    <div v-if="enabled" class="screen-map-stage__canvas">
      <slot />
    </div>

    <!-- 地图锚定浮层（指北针 / 比例尺 / 图例等由使用方提供） -->
    <div class="screen-map-stage__overlay">
      <slot name="overlay" />
    </div>

    <!-- 取景框角标 -->
    <template v-if="showFrame">
      <i class="screen-map-stage__frame is-tl" aria-hidden="true" />
      <i class="screen-map-stage__frame is-tr" aria-hidden="true" />
      <i class="screen-map-stage__frame is-bl" aria-hidden="true" />
      <i class="screen-map-stage__frame is-br" aria-hidden="true" />
    </template>

    <!-- 暗角 -->
    <span v-if="showVignette" class="screen-map-stage__vignette" aria-hidden="true" />

    <!-- 状态提示 -->
    <span v-if="status" class="screen-map-stage__status" :class="`is-${status}`" role="status">
      <i class="screen-map-stage__status-dot" aria-hidden="true" />
      {{ statusLabel }}
    </span>
  </div>
</template>

<script>
const STATUS_TEXT = {
  ready: '地图已就绪',
  loading: '地图加载中…',
  offline: '地图未接入',
}

export default {
  name: 'ScreenMapStage',
  props: {
    /** 是否挂载真实地图（默认插槽） */
    enabled: { type: Boolean, default: true },
    /** 兜底底图：中心辉光 */
    showGlow: { type: Boolean, default: true },
    /** 兜底底图：网格 */
    showGrid: { type: Boolean, default: true },
    /** 暗角 */
    showVignette: { type: Boolean, default: true },
    /** 取景框四角标 */
    showFrame: { type: Boolean, default: false },
    /** 状态：'' 不展示 / ready / loading / offline */
    status: { type: String, default: '' },
    /** 自定义状态文案 */
    statusTextMap: { type: Object, default: () => STATUS_TEXT },
  },
  computed: {
    statusLabel () {
      return this.statusTextMap[this.status] || this.status
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-map-stage {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: var(--screen-bg-deep);

  &__backdrop {
    position: absolute;
    inset: 0;
    pointer-events: none;
  }

  &__glow {
    position: absolute;
    inset: 0;
    background:
      radial-gradient(60% 55% at 50% 46%, rgba(47, 227, 192, 0.14) 0%, rgba(2, 26, 30, 0) 68%),
      radial-gradient(38% 40% at 50% 52%, rgba(20, 130, 140, 0.22) 0%, rgba(2, 26, 30, 0) 70%);
  }

  &__grid {
    position: absolute;
    inset: 0;
    opacity: 0.5;
    background-image: linear-gradient(rgba(47, 227, 192, 0.06) 1px, transparent 1px),
      linear-gradient(90deg, rgba(47, 227, 192, 0.06) 1px, transparent 1px);
    background-size: 64px 64px;
    mask-image: radial-gradient(70% 70% at 50% 50%, #000 0%, transparent 100%);
    -webkit-mask-image: radial-gradient(70% 70% at 50% 50%, #000 0%, transparent 100%);
  }

  &__canvas {
    position: absolute;
    inset: 0;
    // 地图必须可交互
    pointer-events: auto;

    /deep/ canvas {
      outline: none;
    }
  }

  &__overlay {
    position: absolute;
    inset: 0;
    pointer-events: none;

    > * {
      pointer-events: auto;
    }
  }

  &__vignette {
    position: absolute;
    inset: 0;
    pointer-events: none;
    background: radial-gradient(
      78% 78% at 50% 50%,
      rgba(0, 0, 0, 0) 42%,
      rgba(1, 17, 20, 0.55) 82%,
      rgba(1, 17, 20, 0.82) 100%
    );
  }

  &__frame {
    position: absolute;
    width: 26px;
    height: 26px;
    border: 0 solid var(--screen-border-strong);
    pointer-events: none;
    opacity: 0.75;

    &.is-tl {
      top: 12px;
      left: 12px;
      border-top-width: 1px;
      border-left-width: 1px;
    }

    &.is-tr {
      top: 12px;
      right: 12px;
      border-top-width: 1px;
      border-right-width: 1px;
    }

    &.is-bl {
      bottom: 12px;
      left: 12px;
      border-bottom-width: 1px;
      border-left-width: 1px;
    }

    &.is-br {
      bottom: 12px;
      right: 12px;
      border-bottom-width: 1px;
      border-right-width: 1px;
    }
  }

  &__status {
    position: absolute;
    left: var(--screen-space-4);
    bottom: var(--screen-space-4);
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 3px 10px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    background: rgba(2, 26, 30, 0.72);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-pill);
    pointer-events: none;

    &.is-ready {
      color: var(--screen-success);
    }

    &.is-loading {
      color: var(--screen-warning);
    }

    &.is-offline {
      color: var(--screen-text-mute);
    }
  }

  &__status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
    box-shadow: 0 0 6px currentColor;
  }
}
</style>
