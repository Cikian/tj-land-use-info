<template>
  <div class="home-body">
    <visibility-analysis-pop
      ref="VisibilityAnalysisPop"
      v-if="commonConfig && commonConfig.VisibilityAnalysis"
      :configMin="commonConfig && commonConfig.VisibilityAnalysis"
    ></visibility-analysis-pop>
    <shadow-analysis-pop ref="ShadowAnalysisPop"
                v-if="commonConfig && commonConfig.ShadowAnalysis"
                :configMin="commonConfig && commonConfig.ShadowAnalysis"></shadow-analysis-pop>
    <height-control-pop ref="heightControlPop"
                        v-if="commonConfig && commonConfig.HeightControl"
                        :configMin="commonConfig && commonConfig.HeightControl"></height-control-pop>
    <skyline-analysis-pop
      ref="SkylineAnalysisPop"
      v-if="commonConfig && commonConfig.SkylineAnalysis"
      :configMin="commonConfig && commonConfig.SkylineAnalysis"
    ></skyline-analysis-pop>
    <contrast-skyline-analysis-pop
      ref="ContrastSkylineAnalysisPop"
      v-if="commonConfig && commonConfig.ContrastSkylineAnalysis"
      :configMin="commonConfig && commonConfig.ContrastSkylineAnalysis"
    ></contrast-skyline-analysis-pop>
    <plan-adjust-pop ref="planAdjustPop"
                     v-if="commonConfig && commonConfig.PlanAdjust"
                     :configMin="commonConfig && commonConfig.PlanAdjust"
    ></plan-adjust-pop>
    <building-set-back-pop ref="buildingSetBackPop"
                           v-if="commonConfig && commonConfig.BuildingSetBack"
                           :configMin="commonConfig && commonConfig.BuildingSetBack"></building-set-back-pop>
    <plan-split-screen-pop ref="planSplitScreenPop"
                           v-if="commonConfig && commonConfig.PlanSplitScreen"
                           :configMin="commonConfig && commonConfig.PlanSplitScreen"></plan-split-screen-pop>
    <move-out-house-pop ref="moveOutHousePop"
                        v-if="commonConfig && commonConfig.MoveOutHouse"
                        :configMin="commonConfig && commonConfig.MoveOutHouse"></move-out-house-pop>

  </div>
</template>

<script>

export default {
  name: 'HomePlanBody',
  components: {
  },
  data() {
    return {
      commonConfig: {},
    }
  },
  created() {
    this.getCommonConfig()
    this.$bus.$on('removePlanningApprovalPopClickEvent', this.controlAll)
  },
  methods: {
    getCommonConfig() {
      let arr = this.$store.getters.sysConfig['PlanningApproval']
      arr.children.map((item, index) => {
        this.commonConfig[item.componentname] = item
      })
    },
    controlAll() {
      // 控制所有按钮的显示隐藏
      this.$refs.heightControlPop && this.$refs.heightControlPop.resetAll()
      this.$refs.buildingSetBackPop && this.$refs.buildingSetBackPop.resetAll()
      this.$refs.moveOutHousePop && this.$refs.moveOutHousePop.resetAll()
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