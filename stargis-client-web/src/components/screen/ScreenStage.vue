<template>
  <!--
    ScreenStage 高保真自适应舞台
    ==================================================================
    高保真模型（docs/高保真/.../首页.html）是 1920×1080 的定尺设计稿，
    面板边框、标题装饰、表格底纹全部是定尺切图。

    早期实现是「固定 1920×1080 画布 + 等比缩放 + 居中」，在非 16:9 的屏幕上
    必然出现左右或上下黑边（大屏窗口比例千差万别，实测 1912×962 就留了约 100px
    的黑边）。现在改成：

      k  = clamp(min(视口高 / 1080, 视口宽 / minDesignWidth), minScale, maxScale)
      设计画布 = 视口宽 / k  ×  视口高 / k
      画布 transform: scale(k)

    因为 画布宽 × k 恒等于视口宽，**任何比例下都铺满、不留黑边**。
    同时 视口高 / k 恒等于 1080（只要 k 取到 高/1080 这一支），
    所以纵向仍然严格等于设计稿，只有横向的设计宽度会大于/小于 1920：

      · 比 16:9 更宽的屏幕 → 设计宽度 > 1920，多出来的宽度给中间的
        「地图 + 属性表 + 底栏」，两侧面板保持 400px 设计宽不变；
      · 比 16:9 更窄的屏幕 → minDesignWidth 兜底，纵向会略微拉长。

    因此画布内的元素约定（各面板组件必须遵守）：
      · 靠左的元素用 left，靠右的用 right，靠下的用 bottom，靠上的用 top；
      · 禁止把 right/bottom 位置写成 1920-x / 1080-y 的固定值；
      · 定尺切图用 background-size: 100% 100% 或 img width:100% 跟随拉伸；
      · 需要知道设计画布尺寸时读 CSS 变量 --stage-w / --stage-h（无单位数字）。

    ⚠ 地图为什么单独一层、不放进缩放画布：
      Cesium 的拾取与相机控制依赖 canvas.clientWidth（布局尺寸，不受
      CSS transform 影响）与事件 clientX（视觉尺寸，受 transform 影响），
      两者在 transform: scale 下不一致，会导致点击/拖拽偏移。
      因此真实地图层直接铺满舞台（真实像素、不施加 transform），
      UI 画布缩放后与它完全重合，地图交互坐标也保持正确。

    用法：
      <screen-stage>
        <template #map><s3dm-viewer /></template>
        ... 按设计稿坐标绝对定位的界面内容 ...
      </screen-stage>

    作用域插槽会抛出 { scale, designWidth, designHeight }。
  -->
  <div ref="stage" class="screen-stage">
    <!-- 真实像素层：地图 / 需要在非缩放坐标下渲染的内容 -->
    <div v-if="$slots.map" class="screen-stage__map">
      <slot
        name="map"
        :scale="scale"
        :design-width="designWidth"
        :design-height="designHeight"
      />
    </div>

    <!-- 设计稿画布：整体等比缩放，铺满舞台 -->
    <div class="screen-stage__canvas" :style="canvasStyle">
      <slot :scale="scale" :design-width="designWidth" :design-height="designHeight" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'ScreenStage',
  props: {
    /** 设计稿宽度（仅作为坐标基准，实际设计宽度会随视口比例变化） */
    width: { type: Number, default: 1920 },
    /** 设计稿高度（严格保持） */
    height: { type: Number, default: 1080 },
    /**
     * 设计画布的最小宽度。
     * 取 1920（即设计稿原宽）是为了让「横向永远不小于设计稿」：
     *   · 左/右面板 400 + 属性表 1000 + 30px 间距刚好铺满 1920，
     *     再窄就会让属性表的 10 列表头互相挤压重叠；
     *   · 顶栏的标题切图（止于 633）与 7 项导航（980 宽）也需要这个宽度。
     * 因此比 16:9 更"窄高"的窗口改由纵向拉长承担：画布高度会大于 1080，
     * 多出来的纵向空间留给地图（各面板与属性表仍按设计稿的定尺高度摆放）。
     * 反过来，比 16:9 更宽的窗口，多出来的横向空间同样给中间的
     * 地图与属性表，两侧面板保持 400 宽不变。
     */
    minDesignWidth: { type: Number, default: 1920 },
    /** 缩放上限（防止超宽屏把界面放得过大） */
    maxScale: { type: Number, default: 3 },
    /** 缩放下限（防止超矮窗口把界面压得看不清） */
    minScale: { type: Number, default: 0.4 },
  },
  data () {
    return {
      scale: 1,
      designWidth: 1920,
      designHeight: 1080,
    }
  },
  computed: {
    canvasStyle () {
      return {
        left: '0px',
        top: '0px',
        width: `${this.designWidth}px`,
        height: `${this.designHeight}px`,
        transform: `scale(${this.scale})`,
        // 供画布内组件读取设计画布尺寸（无单位数字，配合 calc 使用）
        '--stage-w': String(this.designWidth),
        '--stage-h': String(this.designHeight),
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

      // 纵向严格贴合高度，横向不得把设计宽度压到 minDesignWidth 以下
      const raw = Math.min(vh / this.height, vw / this.minDesignWidth)
      const scale = Math.max(this.minScale, Math.min(raw, this.maxScale))

      this.scale = scale
      // 画布尺寸 × scale 恒等于视口尺寸 → 铺满、无黑边
      this.designWidth = vw / scale
      this.designHeight = vh / scale
      this.$nextTick(this.emitResize)
    },
    emitResize () {
      this.$emit('resize', this.scale, this.designWidth, this.designHeight)
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
    inset: 0;
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
