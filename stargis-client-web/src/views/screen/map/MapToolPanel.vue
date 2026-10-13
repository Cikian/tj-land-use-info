<template>
  <!--
    MapToolPanel 地图管理页左面板
  -->
  <div class="map-tools">
    <!-- 面板标题 -->
    <img class="map-tools__title-deco" :src="hf.panelTitleLeft" alt="" aria-hidden="true" />
    <h2 class="map-tools__title">地图管理</h2>

    <!-- 面板主体 -->
    <section class="map-tools__panel stage-hit">
      <button
        v-for="(tool, index) in tools"
        :key="tool.key"
        type="button"
        class="map-tools__card"
        :class="{ 'is-active': isCardActive(index, tool) }"
        :style="cardStyle(index)"
        :aria-pressed="String(isCardActive(index, tool))"
        @click="handleCardClick(index, tool)"
      >
        <img class="map-tools__card-bg" :src="isCardActive(index, tool) ? hf.toolCardActive : hf.toolCard" alt="" aria-hidden="true" />
        <img class="map-tools__icon" :src="tool.icon" alt="" aria-hidden="true" />
        <span class="map-tools__label">{{ tool.label }}</span>
      </button>
    </section>
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'

/** 三列卡片的左边缘（设计稿 x=50/175/300，卡片 110 宽、列间隙 15） */
const CARD_LEFT = [20, 145, 270]
/** 卡片纵向：设计稿 y=179 起步进 139，换算成面板内坐标（面板顶 149） */
const CARD_TOP = 30
const CARD_PITCH = 139

export default {
  name: 'MapToolPanel',
  props: {
    /**
     * 「拾取查询」「地名定位」「道路定位」是否处于激活态。
     * 与其余 15 个未接功能的工具不同，这三个工具的选中态由页面容器
     * 统一管理（拾取跟随插件引擎开关，还可能被其它工具经总线间接取消），
     * 所以由父组件传入而不是本地维护。
     */
    pickActive: { type: Boolean, default: false },
    placeActive: { type: Boolean, default: false },
    roadActive: { type: Boolean, default: false },
    spatialActive: { type: Boolean, default: false },
    attrActive: { type: Boolean, default: false },
    compActive: { type: Boolean, default: false },
    statActive: { type: Boolean, default: false },
  },
  data () {
    return {
      hf,
      /** 本地选中：只服务尚未接功能的 11 个工具（默认无选中） */
      activeIndex: -1,
      /** 18 个工具，按设计稿行优先排列；icon 用 hf 里的 key 动态取 */
      tools: [
        { key: 'pick', label: '拾取查询', icon: hf.toolPick },
        { key: 'locateName', label: '地名定位', icon: hf.toolLocateName },
        { key: 'locateRoad', label: '道路定位', icon: hf.toolLocateRoad },
        { key: 'querySpatial', label: '空间查询', icon: hf.toolQuerySpatial },
        { key: 'queryAttr', label: '属性查询', icon: hf.toolQueryAttr },
        { key: 'queryCombined', label: '综合查询', icon: hf.toolQueryCombined },
        { key: 'statistics', label: '综合统计', icon: hf.toolStatistics },
        { key: 'measureLine', label: '直线量测', icon: hf.toolMeasureLine },
        { key: 'measureHorizontal', label: '水平量测', icon: hf.toolMeasureHorizontal },
        { key: 'measureVertical', label: '垂直量测', icon: hf.toolMeasureVertical },
        { key: 'measureArea', label: '面积量测', icon: hf.toolMeasureArea },
        { key: 'bookmark', label: '视图书签', icon: hf.toolBookmark },
        { key: 'tour', label: '动画导航', icon: hf.toolTour },
        { key: 'hdExport', label: '高清出图', icon: hf.toolHdExport },
        { key: 'terrainHeight', label: '开启高程', icon: hf.toolTerrainHeight },
        { key: 'underground', label: '开启地下模式', icon: hf.toolUnderground },
        { key: 'layers', label: '图层管理', icon: hf.toolLayers },
        { key: 'terrainAlpha', label: '地形透明', icon: hf.toolTerrainAlpha },
      ],
    }
  },
  methods: {
    isCardActive (index, tool) {
      if (tool.key === 'pick') return this.pickActive
      if (tool.key === 'locateName') return this.placeActive
      if (tool.key === 'locateRoad') return this.roadActive
      if (tool.key === 'querySpatial') return this.spatialActive
      if (tool.key === 'queryAttr') return this.attrActive
      if (tool.key === 'queryCombined') return this.compActive
      if (tool.key === 'statistics') return this.statActive
      return index === this.activeIndex
    },
    handleCardClick (index, tool) {
      if (tool.key === 'pick') {
        // 拾取查询：交给父组件去切换 stargis-function 的拾取引擎
        this.$emit('toggle-pick')
        return
      }
      if (tool.key === 'locateName') {
        // 地名定位：交给父组件切换右侧的地名定位面板
        this.$emit('toggle-place')
        return
      }
      if (tool.key === 'locateRoad') {
        // 道路定位：交给父组件切换插件道路定位弹窗
        this.$emit('toggle-road')
        return
      }
      if (tool.key === 'querySpatial') {
        // 空间查询：交给父组件切换插件空间查询面板
        this.$emit('toggle-spatial')
        return
      }
      if (tool.key === 'queryAttr') {
        // 属性查询：交给父组件切换插件属性查询面板
        this.$emit('toggle-attr')
        return
      }
      if (tool.key === 'queryCombined') {
        // 综合查询：交给父组件切换插件综合查询面板
        this.$emit('toggle-comp')
        return
      }
      if (tool.key === 'statistics') {
        // 综合统计：交给父组件切换插件综合统计面板
        this.$emit('toggle-stat')
        return
      }
      this.activeIndex = index
    },
    cardStyle (index) {
      const row = Math.floor(index / 3)
      const col = index % 3
      return { left: `${CARD_LEFT[col]}px`, top: `${CARD_TOP + row * CARD_PITCH}px` }
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.map-tools {
  &__title-deco {
    position: absolute;
    left: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    left: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__panel {
    position: absolute;
    left: 30px;
    // 上贴 149、下贴 50：与首页左面板同一套画布坐标
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-left-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;
  }

  &__card {
    position: absolute;
    width: 110px;
    height: 128px;
    padding: 0;
    background: transparent;
    border: 0;
    cursor: pointer;
    .screen-focus-ring();
  }

  &__card-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 110px;
    height: 128px;
    display: block;
    pointer-events: none;
  }

  &__icon {
    position: absolute;
    left: 18px;
    top: 11px;
    width: 68px;
    height: 54px;
    display: block;
    pointer-events: none;
  }

  &__label {
    position: absolute;
    left: 0;
    top: 82px;
    width: 100%;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: #82c6ff;
    text-align: center;
    white-space: nowrap;
  }

  &__card.is-active &__label {
    color: #ffffff;
  }
}
</style>
