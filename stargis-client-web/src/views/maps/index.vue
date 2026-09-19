<template>
  <!--地图图层-->
  <div class="main-box">
    <div class="s3dmViewer">
      <S3dmViewer ref="S3dmViewer"></S3dmViewer>
      <!--      <video id="trailer" style="position:absolute;top:0px;left:0;width: 1920px;height:1000px;overflow: hidden" autoplay loop crossorigin controls>-->
      <!--        <source src="~@/assets/imgs/MP4/UE-海河new.mp4" type="video/mp4">-->
      <!--      </video>-->
    </div>
    <home-header ref="homeHeader"></home-header>
    <home-basic-body ref="homeBasicBody"></home-basic-body>
    <home-common-body ref="homeCommonBody" v-show="showFun['Common']"></home-common-body>
    <home-common-footer ref="homeCommonFooter" v-show="showFun['Common']"></home-common-footer>
    <home-pipe-body ref="homePipeManagerBody" v-if="showFun['PipeManager']"></home-pipe-body>
    <home-pipe-footer ref="homePipeManagerFooter" v-show="showFun['PipeManager']"></home-pipe-footer>
    <home-query-body ref="homeQueryStatisticsBody" v-if="showFun['QueryStatistics']"></home-query-body>
    <home-query-footer ref="homeQueryStatisticsFooter" v-show="showFun['QueryStatistics']"></home-query-footer>
    <home-plan-body ref="homePlanningApprovalBody" v-if="showFun['PlanningApproval']"></home-plan-body>
    <home-plan-footer ref="homePlanningApprovalFooter" v-show="showFun['PlanningApproval']"></home-plan-footer>
    <home-scene-body ref="homeSceneEffectsBody" v-if="showFun['SceneEffects']"></home-scene-body>
    <home-scene-footer ref="homeSceneEffectsFooter" v-show="showFun['SceneEffects']"></home-scene-footer>
    <home-spatial-body ref="homeSpatialAnalysisBody" v-if="showFun['SpatialAnalysis']"></home-spatial-body>
    <home-spatial-footer ref="homeSpatialAnalysisFooter" v-show="showFun['SpatialAnalysis']"></home-spatial-footer>
    <home-building-land-body ref="homeBuildingLandBody" v-if="showFun['BuildingLand']"></home-building-land-body>
    <home-building-land-footer ref="homeBuildingLandFooter"
      v-show="showFun['BuildingLand']"></home-building-land-footer>
    <home-planning-project-body ref="homePlanningProjectBody"
      v-if="showFun['PlanningProject']"></home-planning-project-body>
    <home-planning-project-footer ref="homePlanningProjectFooter"
      v-show="showFun['PlanningProject']"></home-planning-project-footer>


    <home-geology-body ref="homeGeologyBody" v-if="showFun['GeologyManager']"></home-geology-body>
    <home-geology-footer ref="homeGeologyFooter" v-if="showFun['GeologyManager']"></home-geology-footer>



    <!-- 备注： ref 的命名与showFun中的key值要对应，形式参照以上 -->
  </div>
</template>

<script>
import S3dmViewer from './S3dmViewer'
import HomeHeader from './HomeHeader'
import HomeBasicBody from './components/Basic/HomeBasicBody'
import HomePipeFooter from './components/PipeManager/HomePipeFooter'
import HomePipeBody from './components/PipeManager/HomePipeBody'
import HomeCommonBody from './components/Common/HomeCommonBody'
import HomeCommonFooter from './components/Common/HomeCommonFooter'
import HomeQueryBody from './components/QueryStatistics/HomeQueryBody'
import HomeQueryFooter from './components/QueryStatistics/HomeQueryFooter'
import HomePlanningProjectBody from './components/PlanningProject/HomePlanningProjectBody'
import HomePlanningProjectFooter from './components/PlanningProject/HomePlanningProjectFooter'
import HomePlanBody from './components/PlanningApproval/HomePlanBody'
import HomePlanFooter from './components/PlanningApproval/HomePlanFooter'
import HomeSceneBody from './components/SceneEffects/HomeSceneBody.vue'
import HomeSceneFooter from './components/SceneEffects/HomeSceneFooter.vue'
import HomeSpatialBody from './components/SpatialAnalysis/HomeSpatialBody'
import HomeSpatialFooter from './components/SpatialAnalysis/HomeSpatialFooter'
import HomeBuildingLandBody from './components/BuildingLand/HomeBuildingLandBody'
import HomeBuildingLandFooter from './components/BuildingLand/HomeBuildingLandFooter'

import HomeGeologyBody from './components/GeologyManager/HomeGeologyBody'
import HomeGeologyFooter from './components/GeologyManager/HomeGeologyFooter'
export default {
  name: 'index',
  components: {
    S3dmViewer,
    HomeHeader,
    HomeBasicBody,
    HomePipeFooter,
    HomePipeBody,
    HomeCommonFooter,
    HomeCommonBody,
    HomeQueryFooter,
    HomeQueryBody,
    HomePlanFooter,
    HomePlanBody,
    HomeSceneBody,
    HomeSceneFooter,
    HomeSpatialBody,
    HomeSpatialFooter,
    HomeBuildingLandBody,
    HomeBuildingLandFooter,
    HomePlanningProjectBody,
    HomePlanningProjectFooter,

    HomeGeologyBody,
    HomeGeologyFooter,

  },
  data() {
    return {
      showFun: {
        Common: false,
        PipeManager: false,
        QueryStatistics: false,
        PlanningApproval: false,
        SceneEffects: false,
        SpatialAnalysis: false,
        BuildingLand: false,
        PlanningProject: false,
        GrandCanal: false,
      },
    }
  },
  created() {
    this.$bus.$on('changeFun', (val) => {
      this.changeFunction(val)
    })
  },
  methods: {
    changeFunction(val) {
      for (let item in this.showFun) {
        if (this.$refs[`home${item}Footer`]) {
          this.$refs[`home${item}Footer`].cancelAll()
        }
        this.showFun[item] = val === item
      }
    },
  },
}
</script>

<style scoped lang="less">
.main-box {
  margin: 0;
  padding: 0;
  height: 100%;
  z-index: -2;
  background-size: 100% 100%;
}

.s3dmViewer {
  width: 100%;
  height: 100%;
}
</style>