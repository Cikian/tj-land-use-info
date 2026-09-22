<template>
  <!--
    ScreenStage 高保真等比缩放舞台
    ==================================================================
    高保真模型（docs/高保真/.../首页.html）是 1920×1080 的定尺设计稿，
    面板边框、标题装饰、表格底纹全部是定尺切图。为了让界面在任意窗口
    尺寸下都与设计稿逐像素对齐，这里采用「固定画布 + 整体等比缩放」：
      scale = min(视口宽 / 1920, 视口高 / 1080)
      画布居中显示，四周留底色（不裁切、不拉伸）。

    ⚠ 地图为什么单独一层、不放进缩放画布：
      Cesium 的拾取与相机控制依赖 canvas.clientWidth（布局尺寸，不受
      CSS transform 影响）与事件 clientX（视觉尺寸，受 transform 影响），
      两者在 transform: scale 下不一致，会导致点击/拖拽偏移。
      因此真实地图层用「真实像素」铺在设计稿矩形上（宽高 = 1920*scale），
      不施加 transform；UI 画布层施加 transform 覆盖在同一矩形上，
      两层视觉完全重合，地图交互坐标也保持正确。

    用法：
      <screen-stage>
        <template #map><s3dm-viewer /></template>
        ... 按 1920×1080 绝对定位的界面内容 ...
      </screen-stage>

    作用域插槽会抛出 { scale, offsetX, offsetY }，供需要在真实像素层
    定位的浮层（例如贴边弹出的下拉）使用。
  -->
  <div ref="stage" class="screen-stage" :class="{ 'is-letterbox': letterbox }">
    <!-- 真实像素层：地图 / 需要在非缩放坐标下渲染的内容 -->
    <div v-if="$slots.map" class="screen-stage__map" :style="mapStyle">
      <slot name="map" :scale="scale" :offset-x="offsetX" :offset-y="offsetY" />
    </div>

    <!-- 设计稿画布：1920×1080，整体等比缩放 -->
    <div class="screen-stage__canvas" :style="canvasStyle">
      <slot :scale="scale" :offset-x="offsetX" :offset-y="offsetY" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'ScreenStage',
  props: {
    /** 设计稿宽度 */
    width: { type: Number, default: 1920 },
    /** 设计稿高度 */
    height: { type: Number, default: 1080 },
    /**
     * 缩放上限。默认 4（相当于不限制）——大屏经常跑在 2560×1440 / 3840×2160 上，
     * 此时必须等比放大铺满，否则会在画面中间留出一圈黑边。
     * DOM 的 transform 缩放是矢量的，文字不会糊；只有切图会被放大后略微变软。
     */
    maxScale: { type: Number, default: 4 },
    /** 是否在地图层之外显示画布底色（关闭后画布外为透明） */
    letterbox: { type: Boolean, default: true },
  },
  data () {
    return {
      scale: 1,
      offsetX: 0,
      offsetY: 0,
    }
  },
  computed: {
    canvasStyle () {
      return {
        left: `${this.offsetX}px`,
        top: `${this.offsetY}px`,
        width: `${this.width}px`,
        height: `${this.height}px`,
        transform: `scale(${this.scale})`,
      }
    },
    mapStyle () {
      return {
        left: `${this.offsetX}px`,
        top: `${this.offsetY}px`,
        width: `${this.width * this.scale}px`,
        height: `${this.height * this.scale}px`,
      }
    },
  },
  mounted () {
    this.resize()
    window.addEventListener('resize', this.resize)
    if (window.ResizeObserver && this.$refs.stage) {
      this.observer = new ResizeObserver(this.resize)
      this.observer.observe(this.$refs.stage)
    }
    // 画布尺寸变化后地图容器也要跟着重新计算，通知使用方
    this.$nextTick(this.emitResize)
  },
  beforeDestroy () {
    window.removeEventListener('resize', this.resize)
    if (this.observer) {
      this.observer.disconnect()
      this.observer = null
    }
  },
  methods: {
    resize () {
      const el = this.$refs.stage
      if (!el) return
      const vw = el.clientWidth || window.innerWidth
      const vh = el.clientHeight || window.innerHeight
      const raw = Math.min(vw / this.width, vh / this.height)
      const scale = Math.max(0.05, Math.min(raw, this.maxScale))
      this.scale = scale
      this.offsetX = Math.round((vw - this.width * scale) / 2)
      this.offsetY = Math.round((vh - this.height * scale) / 2)
      this.$nextTick(this.emitResize)
    },
    emitResize () {
      this.$emit('resize', this.scale, this.offsetX, this.offsetY)
    },
  },
}
</script>

<style scoped lang="less">
.screen-stage {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: var(--screen-bg-deep);

  &__map {
    position: absolute;
    z-index: 0;
    overflow: hidden;
  }

  &__canvas {
    position: absolute;
    z-index: 1;
    transform-origin: 0 0;
    // 画布整体不拦截事件：全屏蒙版层（vignette）必须让地图可拖拽。
    // 需要交互的浮层/面板自行声明 pointer-events: auto（挂全局类 .stage-hit）。
    pointer-events: none;
  }
}

/* 画布内需要接收鼠标事件的元素挂全局类 .stage-hit（定义在 screen-tokens.less） */
</style>
