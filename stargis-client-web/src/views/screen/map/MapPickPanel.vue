<template>
  <!--
    MapPickPanel 地图管理页右面板「拾取查询」
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
          v-for="(row, index) in rows"
          :key="row.name"
          class="map-pick__row"
          :style="{ top: `${index * rowHeight}px` }"
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

/** 字段行高（设计稿 46px，首行文字距行顶 12px） */
const ROW_HEIGHT = 46

export default {
  name: 'MapPickPanel',
  data () {
    return {
      hf,
      /** 字段行高（设计稿 46px） */
      rowHeight: ROW_HEIGHT,
      /** 当前图层名：设计稿演示值，接入拾取功能后由地图选中要素替换 */
      currentLayer: '图层001',
      /** 字段表：设计稿演示数据（静态展示，不取接口） */
      rows: [
        { name: 'OID', value: '153' },
        { name: 'FID', value: '152' },
        { name: 'CRZDBH', value: '津武（挂）2023-014' },
        { name: '地块名', value: '地块001' },
        { name: 'Shape_Leng', value: '1047.673' },
        { name: 'Shape_Area', value: '34017.238' },
      ],
    }
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
    overflow: hidden;
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
    position: absolute;
    left: 0;
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
