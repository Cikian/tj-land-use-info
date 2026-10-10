<template>
  <!--
    MapPickPanel 地图管理页右面板「拾取查询」（高保真还原 + 接原插件拾取）
    ==================================================================
    定尺取自高保真模型 地图管理.html（右面板与首页图层面板同一框架）：
      标题装饰 大标题-右_u24.png   (1491, 95)   420×46
      标题文字 拾取查询（右对齐）    (1765, 100)  18px #FFFFFF
      面板底   panel-right-bg.png  (1490, 149)  400×881
      当前图层框 pick-layer-box.png (1510, 169) 360×120
        立方体图标 pick-layer-icon.png (1526, 186) 131×93
        「当前图层」 16px #82C6FF  (1669, 194)
        「图层001」  18px #FFFFFF 500 (1669, 224)
      字段表头 pick-table-head.png (1510, 299) 360×36
        「字段名」(1540, 307) /「字段值」(1671, 307) 14px #FFFFFF
      字段表底 pick-table-body.png (1510, 345) 360×651
        行高 46；首行文字 y=357；字段名 x=1540 / 字段值 x=1671，14px #82C6FF
        隔行高亮 table-row.png（与首页明细表同一张切图），落在第 1/3/5 行

    数据来源：父组件监听 stargis-function 拾取结果（总线事件 showPickQueryPop，
    payload 即 {name, value} 行数组）后经 props 传入；没有拾取结果时回退到
    设计稿演示数据，保证空态下版式与高保真一致。
  -->
  <div class="map-pick">
    <img class="map-pick__title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
    <h2 class="map-pick__title">拾取查询</h2>

    <section class="map-pick__panel stage-hit">
      <!-- 当前图层 -->
      <div class="map-pick__layer">
        <img class="map-pick__layer-bg" :src="hf.pickLayerBox" alt="" aria-hidden="true" />
        <img class="map-pick__layer-icon" :src="hf.pickLayerIcon" alt="" aria-hidden="true" />
        <span class="map-pick__layer-label">当前图层</span>
        <span class="map-pick__layer-name">{{ currentLayer }}</span>
      </div>

      <!-- 字段表 -->
      <div class="map-pick__head">
        <img class="map-pick__head-bg" :src="hf.pickTableHead" alt="" aria-hidden="true" />
        <span class="map-pick__th is-name">字段名</span>
        <span class="map-pick__th is-value">字段值</span>
      </div>
      <div class="map-pick__body">
        <img class="map-pick__body-bg" :src="hf.pickTableBody" alt="" aria-hidden="true" />
        <div
          v-for="(row, index) in displayRows"
          :key="`${index}-${row.name}`"
          class="map-pick__row"
        >
          <img
            v-if="index % 2 === 0"
            class="map-pick__row-bg"
            :src="hf.tableRow"
            alt=""
            aria-hidden="true"
          />
          <span class="map-pick__td is-name">{{ row.name }}</span>
          <span class="map-pick__td is-value">{{ row.value }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'

/** 设计稿演示数据：仅在还没有真实拾取结果时展示（空态版式与高保真一致） */
const DEMO_ROWS = [
  { name: 'OID', value: '153' },
  { name: 'FID', value: '152' },
  { name: 'CRZDBH', value: '津武（挂）2023-014' },
  { name: '地块名', value: '地块001' },
  { name: 'Shape_Leng', value: '1047.673' },
  { name: 'Shape_Area', value: '34017.238' },
]
const DEMO_LAYER = '图层001'

export default {
  name: 'MapPickPanel',
  props: {
    /**
     * 真实拾取结果：{name, value} 行数组（stargis-function 的 QueryPick
     * 插件拾中要素后经总线抛出）。null / 空数组 = 尚未拾中或已取消。
     */
    result: { type: Array, default: null },
    /**
     * 拾中要素所在图层名（父组件在地图点击时从 Cesium pick 结果里取，
     * 取不到时保持 null，面板显示演示图层名）。
     */
    layerName: { type: String, default: '' },
  },
  data () {
    return {
      hf,
    }
  },
  computed: {
    displayRows () {
      return this.result && this.result.length ? this.result : DEMO_ROWS
    },
    currentLayer () {
      return this.layerName || DEMO_LAYER
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.map-pick {
  &__title-deco {
    position: absolute;
    right: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    // 设计稿标题文字右缘止于 x=1837，即距右边界 83px
    right: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  &__panel {
    position: absolute;
    right: 30px;
    // 上贴 149、下贴 50：与首页右面板（图层树）同一套画布坐标
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;
  }

  /* ---------------- 当前图层 ---------------- */
  &__layer {
    position: absolute;
    left: 20px;
    top: 20px;
    width: 360px;
    height: 120px;
  }

  &__layer-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 360px;
    height: 120px;
    display: block;
    pointer-events: none;
  }

  &__layer-icon {
    position: absolute;
    left: 16px;
    top: 17px;
    width: 131px;
    height: 93px;
    display: block;
    pointer-events: none;
  }

  &__layer-label {
    position: absolute;
    left: 159px;
    top: 25px;
    font-size: var(--screen-font-md);
    line-height: 23px;
    color: #82c6ff;
    white-space: nowrap;
  }

  &__layer-name {
    position: absolute;
    left: 159px;
    top: 55px;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 26px;
    color: #ffffff;
    white-space: nowrap;
  }

  /* ---------------- 字段表 ---------------- */
  &__head {
    position: absolute;
    left: 20px;
    top: 150px;
    width: 360px;
    height: 36px;
  }

  &__head-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 360px;
    height: 36px;
    display: block;
    pointer-events: none;
  }

  &__th {
    position: absolute;
    top: 8px;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: #ffffff;
    white-space: nowrap;

    &.is-name { left: 30px; }
    &.is-value { left: 161px; }
  }

  &__body {
    position: absolute;
    left: 20px;
    top: 196px;
    width: 360px;
    // 面板高 881 - 196 - 底部留白 30（同图层树的 body 收边）
    bottom: 30px;
    overflow-y: auto;
    overflow-x: hidden;
  }

  &__body-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 360px;
    height: 651px;
    display: block;
    pointer-events: none;
  }

  &__row {
    // 文档流布局（而非绝对定位）：真实拾取结果的字段数不定，
    // 行数超过表体高度时靠 body 的 overflow-y 滚动；行高仍是设计稿的 46px
    position: relative;
    width: 100%;
    height: 46px;
  }

  &__row-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 46px;
    display: block;
    pointer-events: none;
  }

  &__td {
    position: absolute;
    top: 12px;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: #82c6ff;
    white-space: nowrap;
    font-variant-numeric: tabular-nums;

    &.is-name { left: 30px; }
    &.is-value { left: 161px; }
  }
}
</style>
