<template>
    <div class="home-body">
      <building-check-pop v-if="commonConfig && commonConfig.BuildingCheck"
                          :configMin="commonConfig && commonConfig.BuildingCheck"></building-check-pop>
      <k-g-check-pop v-if="commonConfig && commonConfig.KGCheck"
                     :configMin="commonConfig && commonConfig.KGCheck"></k-g-check-pop>
      <land-check-pop v-if="commonConfig && commonConfig.LandCheck"
                     :configMin="commonConfig && commonConfig.LandCheck"></land-check-pop>
      <comprehensive-check-pop v-if="commonConfig && commonConfig.ComprehensiveCheck"
                               :configMin="commonConfig && commonConfig.ComprehensiveCheck"></comprehensive-check-pop>
      <site-selection-pop ref="siteSelectionPop" v-if="commonConfig && commonConfig.SiteSelection"
                          :configMin="commonConfig && commonConfig.SiteSelection"></site-selection-pop>
      <land-structure-pop ref="landStructurePop" v-if="commonConfig && commonConfig.LandStructure"
                          :configMin="commonConfig && commonConfig.LandStructure"></land-structure-pop>
      <stock-land-pop ref="stockLandPop" v-if="commonConfig && commonConfig.StockLand"
                      :configMin="commonConfig && commonConfig.StockLand"></stock-land-pop>
      <special-land-use-pop ref="specialLandUsePop" v-if="commonConfig && commonConfig.SpecialLandUse"
                            :configMin="commonConfig && commonConfig.SpecialLandUse"></special-land-use-pop>
      <road-statistic-pop ref="roadStatisticPop" v-if="commonConfig && commonConfig.RoadStatistic"
                          :configMin="commonConfig && commonConfig.RoadStatistic"></road-statistic-pop>
      <building-scale-statistic-pop ref="buildingScaleStatisticPop" v-if="commonConfig && commonConfig.BuildingScaleStatistic"
                                    :configMin="commonConfig && commonConfig.BuildingScaleStatistic"></building-scale-statistic-pop>
    </div>
</template>

<script>

 export default {
    name: 'HomeBuildingLandBody',
    components: {
    },
    data () {
      return {
        commonConfig: {}
      }
    },
    created() {
      this.$bus.$on('removeBuildingLandPopClickEvent', this.controlAll)
      this.getCommonConfig()
    },
    methods: {
      getCommonConfig () {
        let arr = this.$store.getters.sysConfig['BuildingLand']
        arr.children.map((item, index)=>{
          this.commonConfig[item.componentname] = item
        })
      },
      controlAll () { // 控制所有按钮的显示隐藏
        this.$refs.roadStatisticPop && this.$refs.roadStatisticPop.resetAll()
        this.$refs.buildingScaleStatisticPop && this.$refs.buildingScaleStatisticPop.resetAll()
        this.$refs.specialLandUsePop && this.$refs.specialLandUsePop.resetAll()
        this.$refs.stockLandPop && this.$refs.stockLandPop.resetAll()
        this.$refs.landStructurePop && this.$refs.landStructurePop.resetAll()
        this.$refs.siteSelectionPop && this.$refs.siteSelectionPop.resetAll()
      },
    }
  }
</script>

<style scoped lang="less">
/* common_btn.less / common_pop.less 已改为 main.js 全局引入一次，此处不再重复 @import */
.home-body{
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