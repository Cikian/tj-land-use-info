<template>
  <div class="home-body">
    <excavation-analysis-pop ref="excatatePop"
      :configMin="commonConfig && commonConfig.ExcavationAnalysis"></excavation-analysis-pop>
    <pipe-collision-pop ref="collisionPop" :configMin="commonConfig && commonConfig.PipeCollision"></pipe-collision-pop>
    <burst-analysis-pop ref="burstPop" :configMin="commonConfig && commonConfig.BurstAnalysis"></burst-analysis-pop>
    <pipe-query-pop ref="queryPop" :configMin="commonConfig && commonConfig.PipeQuery"></pipe-query-pop>
    <distance-analysis-pop ref="distancePop"
      :configMin="commonConfig && commonConfig.DistanceAnalysis"></distance-analysis-pop>
    <property-pop ref="propertyPop" :configMin="commonConfig && commonConfig.xzanalyse"></property-pop>
    <diameter-pop ref="diameterPop" :configMin="commonConfig && commonConfig.gjanalyse"></diameter-pop>
    <material-pop ref="materialPop" :configMin="commonConfig && commonConfig.czanalyse"></material-pop>
    <propertyQuery-pop ref="propertyQueryPop" :configMin="commonConfig && commonConfig.dxzQuery"></propertyQuery-pop>
    <diameterQuery-pop ref="diameterQueryPop" :configMin="commonConfig && commonConfig.gjQuery"></diameterQuery-pop>
    <diameterSect-pop ref="diameterSectPop" :configMin="commonConfig && commonConfig.gjfdanalyse"></diameterSect-pop>
    <depthSect-pop ref="depthSectPop" :configMin="commonConfig && commonConfig.msfdanalyse"></depthSect-pop>
    <feildQuery-pop ref="diameterQueryPop" :configMin="commonConfig && commonConfig.gjzQuery"></feildQuery-pop>
    <materialQuery-pop ref="materialQueryPop" :configMin="commonConfig && commonConfig.czQuery"></materialQuery-pop>
    <managerPanel-pop ref="managerPanelPop"></managerPanel-pop>
    <hydrantSearch-pop ref="hydrantSearchPop"
      :configMin="commonConfig && commonConfig.HydrantSearch"></hydrantSearch-pop>
    <pipeMark-pop ref="pipeMarkPop" :configMin="commonConfig && commonConfig.PipeMark"></pipeMark-pop>
    <coverage-analysis-pop ref="coverageAnalysisPop"
      :configMin="commonConfig && commonConfig.CoverageAnalysis"></coverage-analysis-pop>
    <iconLabel-pop ref="iconLabelPop" :configMin="commonConfig && commonConfig.LabelTool"></iconLabel-pop>
    <pipe-legend-pop ref="PipeLegendPop" :configMin="commonConfig && commonConfig.PipeLegend"></pipe-legend-pop>
    <connect-analysis-pop ref="ConnectAnalysis"
      :configMin="commonConfig && commonConfig.ConnectAnalysis"></connect-analysis-pop>
    <tracking-analysis-pop ref="TrackingAnalysis"
      :configMin="commonConfig && commonConfig.TrackingAnalysis"></tracking-analysis-pop>
    <flow-direction-analysis-pop ref="flowDirectionAnalysisPop"
      :configMin="commonConfig && commonConfig.FlowDirectionAnalysis"></flow-direction-analysis-pop>
  </div>
</template>

<script>
export default {
  name: 'HomePipeFooter',
  components: {

  },
  data() {
    return {
      commonConfig: {},
    }
  },
  created() {
    this.getCommonConfig()
  },
  methods: {
    getCommonConfig() {
      let arr = this.$store.getters.sysConfig['PipeManager']
      arr.children.map((item, index) => {
        this.commonConfig[item.componentname] = item
        if (item.nodetype === 'group') {
          for (let i in item.children) {
            this.commonConfig[item.children[i].componentname] = item.children[i]
          }
        }
      })
    },
    data() {
      return {
        commonConfig: {},
      }
    },
    created() {
      this.getCommonConfig()
    },
    controlAll() {
      // 控制所有按钮的显示隐藏
    },
  },
}
</script>

<style scoped lang="less">
/* common_btn.less / common_pop.less 已改为 main.js 全局引入一次，此处不再重复 @import */

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