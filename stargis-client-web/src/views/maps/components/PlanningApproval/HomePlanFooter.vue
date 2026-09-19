<template>
  <div class="home-footer">
    <div class="btn-content">
      <div class="all-btns" v-show="allShow">
        <visibility-analysis
          ref="visibilityAnalysis"
          :style="{ order: commonSort && commonSort.VisibilityAnalysis ? commonSort.VisibilityAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.VisibilityAnalysis &&
            (commonConfig.VisibilityAnalysis.functionType == '23' ||
              commonConfig.VisibilityAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.VisibilityAnalysis"
        ></visibility-analysis>
        <shadow-analysis
          ref="shadowAnalysis"
          :style="{ order: commonSort && commonSort.ShadowAnalysis ? commonSort.ShadowAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.ShadowAnalysis &&
            (commonConfig.ShadowAnalysis.functionType == '23' || commonConfig.ShadowAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.ShadowAnalysis"
        ></shadow-analysis>
        <height-control
          ref="heightControl"
          :style="{ order: commonSort && commonSort.HeightControl ? commonSort.HeightControl : 1 }"
          v-if="
            commonConfig &&
            commonConfig.HeightControl &&
            (commonConfig.HeightControl.functionType == '23' || commonConfig.HeightControl.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.HeightControl"
        ></height-control>
        <skyline-analysis
          ref="skylineAnalysis"
          :style="{ order: commonSort && commonSort.SkylineAnalysis ? commonSort.SkylineAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.SkylineAnalysis &&
            (commonConfig.SkylineAnalysis.functionType == '23' || commonConfig.SkylineAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.SkylineAnalysis"
        ></skyline-analysis>
        <contrast-skyline-analysis
          ref="contrastSkylineAnalysis"
          :style="{ order: commonSort && commonSort.ContrastSkylineAnalysis ? commonSort.ContrastSkylineAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.ContrastSkylineAnalysis &&
            (commonConfig.ContrastSkylineAnalysis.functionType == '23' ||
              commonConfig.ContrastSkylineAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.ContrastSkylineAnalysis"
        ></contrast-skyline-analysis>
        <plan-adjust
          ref="planAdjust"
          :style="{ order: commonSort && commonSort.PlanAdjust ? commonSort.PlanAdjust : 1 }"
          v-if="
            commonConfig &&
            commonConfig.PlanAdjust &&
            (commonConfig.PlanAdjust.functionType == '23' || commonConfig.PlanAdjust.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.PlanAdjust"
        ></plan-adjust>
        <building-set-back
          ref="buildingSetBack"
          :style="{ order: commonSort && commonSort.BuildingSetBack ? commonSort.BuildingSetBack : 1 }"
          v-if="
            commonConfig &&
            commonConfig.BuildingSetBack &&
            (commonConfig.BuildingSetBack.functionType == '23' || commonConfig.BuildingSetBack.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.BuildingSetBack"
        ></building-set-back>
        <plan-split-screen
          ref="planSplitScreen"
          :style="{ order: commonSort && commonSort.PlanSplitScreen ? commonSort.PlanSplitScreen : 1 }"
          v-if="
            commonConfig &&
            commonConfig.PlanSplitScreen &&
            (commonConfig.PlanSplitScreen.functionType == '23' || commonConfig.PlanSplitScreen.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.PlanSplitScreen"
        ></plan-split-screen>
        <move-out-house
          ref="moveOutHouse"
          :style="{ order: commonSort && commonSort.MoveOutHouse ? commonSort.MoveOutHouse : 1 }"
          v-if="
            commonConfig &&
            commonConfig.MoveOutHouse &&
            (commonConfig.MoveOutHouse.functionType == '23' || commonConfig.MoveOutHouse.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.MoveOutHouse"
        ></move-out-house>
      </div>
    </div>
    <div class="common-btn" style="float: right" @click="controlAll">
      <div class="title"></div>
      <div
        class="btn-img"
        :style="{ backgroundImage: `url(${imageUrl})`, position: 'absolute', top: '21px', left: '16px' }"
      ></div>
    </div>
  </div>
</template>

<script>

export default {
  name: 'HomePlanFooter',
  components: {

  },
  data() {
    return {
      allShow: true,
      imageUrl: '',
      commonConfig: {},
      commonSort: {},
      mode: 3,
    }
  },
  created() {
    // this.imageUrl = require(`@/assets/map-btns/BtnsControl/checked.png`)
    this.imageUrl = this.GlobalImgFolderSelect(`map-btns/BtnsControl/checked.png`)
    this.$bus.$on('cancelPlanChecked', (val) => {
      val && this.cancelAll()
    })
    this.$bus.$on('cancelPlanningApprovalClickEvent', (val) => {
      this.cancelClickEvent()
    })
    this.getCommonConfig()
    this.$bus.$on('change3D2Dmodes', (val) => {
      this.change3D2Dmode(val)
    })
    this.$bus.$on('bkContrastSkylineAnalysisshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.ContrastSkylineAnalysis &&
        (this.commonConfig.ContrastSkylineAnalysis.functionType == '23' ||
          this.commonConfig.ContrastSkylineAnalysis.functionType == this.mode)
      ) {
        this.$bus.$emit('bkContrastSkylineAnalysisBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
    this.$bus.$on('bkPlanSplitScreenshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.PlanSplitScreen &&
        (this.commonConfig.PlanSplitScreen.functionType == '23' ||
          this.commonConfig.PlanSplitScreen.functionType == this.mode)
      ) {
        this.$bus.$emit('bkPlanSplitScreenBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
    this.$bus.$on('bkMoveOutHouseshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.MoveOutHouse &&
        (this.commonConfig.MoveOutHouse.functionType == '23' ||
          this.commonConfig.MoveOutHouse.functionType == this.mode)
      ) {
        this.$bus.$emit('bkMoveOutHouseBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
    this.$bus.$on('bkBuildingSetBackshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.BuildingSetBack &&
        (this.commonConfig.BuildingSetBack.functionType == '23' ||
          this.commonConfig.BuildingSetBack.functionType == this.mode)
      ) {
        this.$bus.$emit('bkBuildingSetBackBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
    this.$bus.$on('bkHeightControlshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.HeightControl &&
        (this.commonConfig.HeightControl.functionType == '23' ||
          this.commonConfig.HeightControl.functionType == this.mode)
      ) {
        this.$bus.$emit('bkHeightControlBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
  },
  methods: {
    getCommonConfig() {
      try {
        let arr = this.$store.getters.sysConfig['PlanningApproval']
        //兼容老中台可能没有functionType全部无法显示的问题
        for (let i = 0; i < arr.children.length; i++) {
          if (!arr.children[i].functionType) {
            arr.children[i].functionType = 23
          }
        }
        arr.children.map((item, index) => {
          this.commonConfig[item.componentname] = item
          this.commonSort[item.componentname] = index + 1
        })
      } catch (error) {}
    },
    controlAll() {
      // 控制所有按钮的显示隐藏
      this.allShow = !this.allShow
      this.imageUrl = this.allShow
      ? this.GlobalImgFolderSelect(`map-btns/BtnsControl/checked.png`)
      : this.GlobalImgFolderSelect(`map-btns/BtnsControl/default.png`)
        // ? require(`@/assets/map-btns/BtnsControl/checked.png`)
        // : require(`@/assets/map-btns/BtnsControl/default.png`)
    },
    cancelAll() {
      // 清除所有按钮的选中状态
      this.cancelClickEvent()
      this.$refs.visibilityAnalysis &&
        this.$refs.visibilityAnalysis.checked &&
        this.$refs.visibilityAnalysis.cancelChecked()
      this.$refs.shadowAnalysis && this.$refs.shadowAnalysis.checked && this.$refs.shadowAnalysis.cancelChecked()
      this.$refs.heightControl && this.$refs.heightControl.checked && this.$refs.heightControl.cancelChecked()
      this.$refs.skylineAnalysis && this.$refs.skylineAnalysis.checked && this.$refs.skylineAnalysis.cancelChecked()
      this.$refs.contrastSkylineAnalysis &&
        this.$refs.contrastSkylineAnalysis.checked &&
        this.$refs.contrastSkylineAnalysis.cancelChecked()

      this.$refs.buildingSetBack && this.$refs.buildingSetBack.checked && this.$refs.buildingSetBack.cancelChecked()
      this.$refs.planSplitScreen && this.$refs.planSplitScreen.checked && this.$refs.planSplitScreen.cancelChecked()
      this.$refs.moveOutHouse && this.$refs.moveOutHouse.checked && this.$refs.moveOutHouse.cancelChecked()
    },
    cancelClickEvent() {
      // 取消带有点击事件的功能
      this.$refs.planAdjust && this.$refs.planAdjust.checked && this.$refs.planAdjust.cancelChecked()
    },
    change3D2Dmode(val) {
      this.mode = val
    },
  },
}
</script>

<style scoped lang="less">
// .common-btn {
//   background-image: url('~@/assets/map-btns/default.png');
//   background-repeat: no-repeat;
//   background-size: 100%;
//   justify-content: center;
//   cursor: pointer;
//   position: relative;
//   pointer-events: initial;
//   .title {
//     color: #ffffff;
//     line-height: 20px;
//     background-color: rgba(0, 0, 0, 0.4);
//     text-align: center;
//     border-radius: 20px;
//     padding: 0 6px;
//     min-width: 80px;
//   }
//   .btn-img {
//     width: 48px;
//     height: 48px;
//     background-repeat: no-repeat;
//     background-position: center;
//   }
// }
</style>