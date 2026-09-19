<template>
    <div class="home-body">
      <query-place-name-pop v-if="commonConfig && commonConfig.QueryPlaceName"
                            :configMin="commonConfig && commonConfig.QueryPlaceName"></query-place-name-pop>
      <query-road-pop v-if="commonConfig && commonConfig.QueryRoad"
                      :configMin="commonConfig && commonConfig.QueryRoad"></query-road-pop>
      <query-spatial-pop ref="spatialPop" v-if="commonConfig && commonConfig.QuerySpatial"
                         :configMin="commonConfig && commonConfig.QuerySpatial"></query-spatial-pop>
      <query-attribute-pop v-if="commonConfig && commonConfig.QueryAttribute"
                           :configMin="commonConfig && commonConfig.QueryAttribute"></query-attribute-pop>
      <query-comprehensive-pop ref="comprehensivePop" v-if="commonConfig && commonConfig.QueryComprehensive"
                               :configMin="commonConfig && commonConfig.QueryComprehensive"></query-comprehensive-pop>
      <query-statistic-pop ref="statisticPop" v-if="commonConfig && commonConfig.QueryStatistic"
                           :configMin="commonConfig && commonConfig.QueryStatistic"></query-statistic-pop>
      <search-object-pop v-if="commonConfig && commonConfig.SearchObject"
                           :configMin="commonConfig && commonConfig.SearchObject"></search-object-pop>
      <search-video-pop v-if="commonConfig && commonConfig.SearchVideo"
                         :configMin="commonConfig && commonConfig.SearchVideo"></search-video-pop>
      <space-hidden-pop v-if="commonConfig && commonConfig.SpaceHidden"
                        :configMin="commonConfig && commonConfig.SpaceHidden"></space-hidden-pop>
      <attribute-hidden-pop v-if="commonConfig && commonConfig.AttributeHidden"
                        :configMin="commonConfig && commonConfig.AttributeHidden"></attribute-hidden-pop>

      <surveillance-video-pop
        ref="surveillanceVideoPop"
        v-if="commonConfig && commonConfig.SurveillanceVideo"
        :configMin="commonConfig && commonConfig.SurveillanceVideo"
      ></surveillance-video-pop>

    </div>
</template>

<script>

 export default {
    name: 'HomeQueryStatisticBody',
    components: {
    },
    data () {
      return {
        commonConfig: {}
      }
    },
    created() {
      this.getCommonConfig()
      this.$bus.$on('removeQueryStatisticPopClickEvent', this.controlAll)
    },
    methods: {
      getCommonConfig () {
        let arr = this.$store.getters.sysConfig['QueryStatistics']
        console.log(arr)
        arr.children.map((item, index)=>{
          this.commonConfig[item.componentname] = item
        })
      },
      controlAll () { // 控制所有按钮的显示隐藏
        this.$refs.spatialPop && this.$refs.spatialPop.resetAll()
        this.$refs.comprehensivePop && this.$refs.comprehensivePop.resetAll()
        this.$refs.statisticPop && this.$refs.statisticPop.resetAll()
      },
    }
  }
</script>

<style scoped lang="less">
@import "~@/assets/less/common_btn.less";
@import "~@/assets/less/common_pop.less";
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