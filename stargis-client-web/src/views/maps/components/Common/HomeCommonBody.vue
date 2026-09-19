<template>
  <div class="home-body">
    <geometric-pop
      ref="geometricPop"
      v-if="commonConfig && commonConfig.DrawGeometric"
      :configMin="commonConfig && commonConfig.DrawGeometric"
    ></geometric-pop>
    <draw-p-l-s ref="drawpls" :configMin="commonConfig && commonConfig.DrawPLS"></draw-p-l-s>
    <animation-navigation-pop
      ref="animationPop"
      v-if="commonConfig && commonConfig.AnimationNavigation"
      :configMin="commonConfig && commonConfig.AnimationNavigation"
    ></animation-navigation-pop>
    <transparent-ground-pop
      ref="trgroundPop"
      v-if="commonConfig && commonConfig.TransparentGround"
      :configMin="commonConfig && commonConfig.TransparentGround"
    ></transparent-ground-pop>
    <transparent-terrain-pop
      ref="trterrainPop"
      v-if="commonConfig && commonConfig.TransparentTerrain"
      :configMin="commonConfig && commonConfig.TransparentTerrain"
    ></transparent-terrain-pop>
    <coordinate-position-pop
      ref="copositionPop"
      v-if="commonConfig && commonConfig.CoordinatePosition"
      :configMin="commonConfig && commonConfig.CoordinatePosition"
    ></coordinate-position-pop>
    <coordinate-label-pop
      ref="colabelPop"
      v-if="commonConfig && commonConfig.CoordinateLabel"
      :configMin="commonConfig && commonConfig.CoordinateLabel"
    ></coordinate-label-pop>
    <split-screen-pop
      ref="splitScreenPop"
      v-if="commonConfig && commonConfig.SplitScreen"
      :configMin="commonConfig && commonConfig.SplitScreen"
    ></split-screen-pop>
    <line-navigation-pop
      ref="lineNavigationPop"
      v-if="commonConfig && commonConfig.LineNavigation"
      :configMin="commonConfig && commonConfig.LineNavigation"
    ></line-navigation-pop>
    <text-label-pop
      ref="telabelPop"
      v-if="commonConfig && commonConfig.TextLabel"
      :configMin="commonConfig && commonConfig.TextLabel"
    ></text-label-pop>
    <timeline-data-contrast-pop
      ref="TimelineDataContrastPop"
      v-if="commonConfig && commonConfig.TimelineDataContrast"
      :configMin="commonConfig && commonConfig.TimelineDataContrast"
    ></timeline-data-contrast-pop>
  </div>
</template>

<script>

export default {
  name: 'HomeCommonBody',
  components: {
  },
  data() {
    return {
      commonConfig: {},
    }
  },
  created() {
    this.getCommonConfig()
    this.$bus.$on('removeCommonPopClickEvent', this.controlAll)
  },
  methods: {
    getCommonConfig() {
      let arr = this.$store.getters.sysConfig['Common']
      arr.children.map((item, index) => {
        this.commonConfig[item.componentname] = item
      })
    },
    controlAll() {
      // 移除面板中的点击事件
      this.$refs.geometricPop && this.$refs.geometricPop.resetAll()
      this.$refs.telabelPop && this.$refs.telabelPop.removeClick()
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/assets/less/common_btn.less';
@import '~@/assets/less/common_pop.less';

.home-body {
  position: absolute;
  bottom: 88px;
  left: 0;
  width: 100%;
  height: calc(100% - 75px - 80px);
  z-index: 1001;
  display: inline-flex;
  pointer-events: none;
}
</style>