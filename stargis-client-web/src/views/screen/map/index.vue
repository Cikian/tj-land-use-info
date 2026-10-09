<template>
  <!--
    地图管理页
    ==================================================================
    与首页同一版式：真实地图保持可见、可交互（工具、拾取均未接功能），
    画布上叠三块 UI：
      ├─ 左面板  MapToolPanel（工具箱：18 个工具卡片，3 列 × 6 行）
      ├─ 右面板  MapPickPanel（拾取查询：当前图层 + 字段名/字段值表）
      └─ 底部    「属性表」收起态横条（与首页属性表同一条底 bar）
    注意：容器本身不开启 pointer-events（不加 stage-hit），
    地图的缩放 / 拖动不能被整页模块挡住，由各面板自行 stage-hit。
  -->
  <div class="map-screen">
    <map-tool-panel />
    <map-pick-panel />

    <!-- 底部「属性表」收起条（设计稿 (460,983) 1000×47，下贴 50） -->
    <section class="map-screen__attr stage-hit">
      <img class="map-screen__attr-bar" :src="hf.attrBar" alt="" aria-hidden="true" />
      <h3 class="map-screen__attr-title">属性表</h3>
      <img class="map-screen__attr-toggle" :src="hf.attrExpand" alt="" aria-hidden="true" />
    </section>
  </div>
</template>

<script>
import MapToolPanel from './MapToolPanel.vue'
import MapPickPanel from './MapPickPanel.vue'
import { hf } from '@/assets/screen-blue'

export default {
  name: 'MapScreen',
  components: { MapToolPanel, MapPickPanel },
  data () {
    return { hf }
  },
}
</script>

<style scoped lang="less">
.map-screen {
  position: absolute;
  inset: 0;
  // 整页容器不拦截鼠标：地图必须保持可交互，各面板自己 stage-hit
  pointer-events: none;

  &__attr {
    position: absolute;
    // 设计稿 x=460..1460、下贴 50（与首页属性表收起态同位）
    left: 460px;
    right: 460px;
    bottom: 50px;
    height: 47px;
  }

  &__attr-bar {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 47px;
    display: block;
    pointer-events: none;
  }

  &__attr-title {
    position: absolute;
    left: 48px;
    // 条高 47、字 22 行高：与首页收起态一致微微下沉
    top: 14px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 22px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__attr-toggle {
    position: absolute;
    // 设计稿图标 17×14 @ (963, 17)，即距右边界 20px
    right: 20px;
    top: 17px;
    width: 17px;
    height: 14px;
    display: block;
    pointer-events: none;
  }
}
</style>
