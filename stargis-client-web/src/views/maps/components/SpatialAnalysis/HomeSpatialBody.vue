<template>
  <div class="home-body">
    <terrain-relief-pop ref="terrainreliefPop" v-if="commonConfig && commonConfig.TerrainRelief"
      :configMin="commonConfig && commonConfig.TerrainRelief"></terrain-relief-pop>
    <openness-analysis-pop ref="opennessanalysisPop" v-if="commonConfig && commonConfig.OpennessAnalysis"
      :configMin="commonConfig && commonConfig.OpennessAnalysis"></openness-analysis-pop>
    <video-put-pop ref="videoputPop" v-if="commonConfig && commonConfig.VideoPut"
      :configMin="commonConfig && commonConfig.VideoPut"></video-put-pop>
    <visual-range-analysis-pop ref="VisualRangeAnalysisPop" v-if="commonConfig && commonConfig.VisualRangeAnalysis"
      :configMin="commonConfig && commonConfig.VisualRangeAnalysis"></visual-range-analysis-pop>
    <polygon-clip-pop ref="polygonclipPop" v-if="commonConfig && commonConfig.PolygonClip"
      :configMin="commonConfig && commonConfig.PolygonClip"></polygon-clip-pop>
    <facade-analysis-pop ref="facadeAnalysisPop" v-if="commonConfig && commonConfig.FacadeAnalysis"
      :configMin="commonConfig && commonConfig.FacadeAnalysis"></facade-analysis-pop>
    <box-clip-pop ref="boxclipPop" v-if="commonConfig && commonConfig.BoxClip"
      :configMin="commonConfig && commonConfig.BoxClip"></box-clip-pop>
    <profile-analysis-pop ref="profileanalysisPop" v-if="commonConfig && commonConfig.ProfileAnalysis"
      :configMin="commonConfig && commonConfig.ProfileAnalysis"></profile-analysis-pop>
    <terrain-excavate-pop ref="terrainexcavatePop" v-if="commonConfig && commonConfig.TerrainExcavate"
      :configMin="commonConfig && commonConfig.TerrainExcavate"></terrain-excavate-pop>
    <inundation-analysis-pop ref="inundationanalysisPop" v-if="commonConfig && commonConfig.InundationAnalysis"
      :configMin="commonConfig && commonConfig.InundationAnalysis"></inundation-analysis-pop>
    <terrain-inundation-pop ref="terraininundationPop" v-if="commonConfig && commonConfig.TerrainInundation"
      :configMin="commonConfig && commonConfig.TerrainInundation"></terrain-inundation-pop>
    <building-services-scope-pop ref="buildingServicesScopePop"
      v-if="commonConfig && commonConfig.BuildingServicesScope"
      :configMin="commonConfig && commonConfig.BuildingServicesScope"></building-services-scope-pop>
    <model-cut-pop ref="modelCutPop" v-if="commonConfig && commonConfig.ModelCut"
      :configMin="commonConfig && commonConfig.ModelCut"></model-cut-pop>
  </div>
</template>

<script>
export default {
  name: 'HomeSpatialBody',
  components: {
  },
  data() {
    return {
      commonConfig: {},
    }
  },
  created() {
    this.$bus.$on('removeSpatialPopClickEvent', this.controlAll)
    this.getCommonConfig()
  },
  methods: {
    getCommonConfig() {
      let arr = this.$store.getters.sysConfig['SpatialAnalysis']
      arr.children.map((item, index) => {
        this.commonConfig[item.componentname] = item
      })
    },
    controlAll() {
      // 控制所有按钮的显示隐藏
      this.$refs.boxclipPop && this.$refs.boxclipPop.resetAll()
      this.$refs.buildingServicesScopePop && this.$refs.buildingServicesScopePop.resetAll()
      this.$refs.facadeAnalysisPop && this.$refs.facadeAnalysisPop.resetAll()
      this.$refs.opennessanalysisPop && this.$refs.opennessanalysisPop.resetAll()
      this.$refs.polygonclipPop && this.$refs.polygonclipPop.resetAll()
      this.$refs.VisualRangeAnalysisPop && this.$refs.VisualRangeAnalysisPop.resetAll()
      this.$refs.modelCutPop && this.$refs.modelCutPop.resetAll()
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