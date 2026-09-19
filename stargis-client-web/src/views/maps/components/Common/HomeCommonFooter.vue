<template>
  <div class="home-footer">
    <div class="btn-content">
      <div class="all-btns" v-show="allShow">
        <measure-line ref="measureLine"
          :style="{ order: commonSort && commonSort.MeasureLine ? commonSort.MeasureLine : 1 }" v-if="
            commonConfig &&
            commonConfig.MeasureLine &&
            (commonConfig.MeasureLine.functionType == '23' || commonConfig.MeasureLine.functionType == mode)
          " :configMin="commonConfig && commonConfig.MeasureLine"></measure-line>
        <measure-area ref="measureArea"
          :style="{ order: commonSort && commonSort.MeasureArea ? commonSort.MeasureArea : 1 }" v-if="
            commonConfig &&
            commonConfig.MeasureArea &&
            (commonConfig.MeasureArea.functionType == '23' || commonConfig.MeasureArea.functionType == mode)
          " :configMin="commonConfig && commonConfig.MeasureArea"></measure-area>
        <measure-space ref="measureSpace"
          :style="{ order: commonSort && commonSort.MeasureSpace ? commonSort.MeasureSpace : 1 }" v-if="
            commonConfig &&
            commonConfig.MeasureSpace &&
            (commonConfig.MeasureSpace.functionType == '23' || commonConfig.MeasureSpace.functionType == mode)
          " :configMin="commonConfig && commonConfig.MeasureSpace"></measure-space>
        <!-- <draw-geometric
          ref="drawGeometric"
          :style="{ order: commonSort && commonSort.DrawGeometric ? commonSort.DrawGeometric : 1 }"
          v-if="
            commonConfig &&
            commonConfig.DrawGeometric &&
            (commonConfig.DrawGeometric.functionType == '23' || commonConfig.DrawGeometric.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.DrawGeometric"
        ></draw-geometric>
        <draw-graph
          ref="drawGraph"
          :style="{ order: commonSort && commonSort.DrawGraph ? commonSort.DrawGraph : 1 }"
          v-if="
            commonConfig &&
            commonConfig.DrawGraph &&
            (commonConfig.DrawGraph.functionType == '23' || commonConfig.DrawGraph.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.DrawGraph"
        ></draw-graph> -->
        <bookmarks ref="bookmarks" :style="{ order: commonSort && commonSort.Bookmarks ? commonSort.Bookmarks : 1 }"
          v-if="
            commonConfig &&
            commonConfig.Bookmarks &&
            (commonConfig.Bookmarks.functionType == '23' || commonConfig.Bookmarks.functionType == mode)
          " :configMin="commonConfig && commonConfig.Bookmarks"></bookmarks>
        <animation-navigation ref="animationNav"
          :style="{ order: commonSort && commonSort.AnimationNavigation ? commonSort.AnimationNavigation : 1 }" v-if="
            commonConfig &&
            commonConfig.AnimationNavigation &&
            (commonConfig.AnimationNavigation.functionType == '23' ||
              commonConfig.AnimationNavigation.functionType == mode)
          " :configMin="commonConfig && commonConfig.AnimationNavigation"></animation-navigation>
        <transparent-ground ref="transparentGround"
          :style="{ order: commonSort && commonSort.TransparentGround ? commonSort.TransparentGround : 1 }" v-if="
            commonConfig &&
            commonConfig.TransparentGround &&
            (commonConfig.TransparentGround.functionType == '23' || commonConfig.TransparentGround.functionType == mode)
          " :configMin="commonConfig && commonConfig.TransparentGround"></transparent-ground>
        <transparent-terrain ref="transparentTerrain"
          :style="{ order: commonSort && commonSort.TransparentTerrain ? commonSort.TransparentTerrain : 1 }" v-if="
            commonConfig &&
            commonConfig.TransparentTerrain &&
            (commonConfig.TransparentTerrain.functionType == '23' ||
              commonConfig.TransparentTerrain.functionType == mode)
          " :configMin="commonConfig && commonConfig.TransparentTerrain"></transparent-terrain>
        <split-screen ref="splitScreen"
          :style="{ order: commonSort && commonSort.SplitScreen ? commonSort.SplitScreen : 1 }" v-if="
            commonConfig &&
            commonConfig.SplitScreen &&
            (commonConfig.SplitScreen.functionType == '23' || commonConfig.SplitScreen.functionType == mode)
          " :configMin="commonConfig && commonConfig.SplitScreen"></split-screen>
        <scene-plotting ref="scenePlotting"
          :style="{ order: commonSort && commonSort.ScenePlotting ? commonSort.ScenePlotting : 1 }" v-if="
            commonConfig &&
            commonConfig.ScenePlotting &&
            (commonConfig.ScenePlotting.functionType == '23' || commonConfig.ScenePlotting.functionType == mode)
          " :configMin="commonConfig && commonConfig.ScenePlotting"></scene-plotting>
        <coordinate-position ref="coordinatePosition"
          :style="{ order: commonSort && commonSort.CoordinatePosition ? commonSort.CoordinatePosition : 1 }" v-if="
            commonConfig &&
            commonConfig.CoordinatePosition &&
            (commonConfig.CoordinatePosition.functionType == '23' ||
              commonConfig.CoordinatePosition.functionType == mode)
          " :configMin="commonConfig && commonConfig.CoordinatePosition"></coordinate-position>
        <!-- <coordinate-label ref="coordinateLabel"
          :style="{ order: commonSort && commonSort.CoordinateLabel ? commonSort.CoordinateLabel : 1 }" v-if="
            commonConfig &&
            commonConfig.CoordinateLabel &&
            (commonConfig.CoordinateLabel.functionType == '23' || commonConfig.CoordinateLabel.functionType == mode)
          " :configMin="commonConfig && commonConfig.CoordinateLabel"></coordinate-label>
        <text-configure ref="textConfigure"
          :style="{ order: commonSort && commonSort.TextConfiguration ? commonSort.TextConfiguration : 1 }" v-if="
            commonConfig &&
            commonConfig.TextConfiguration &&
            (commonConfig.TextConfiguration.functionType == '23' || commonConfig.TextConfiguration.functionType == mode)
          " :configMin="commonConfig && commonConfig.TextConfiguration"></text-configure>
        <text-label ref="textLabel" :style="{ order: commonSort && commonSort.TextLabel ? commonSort.TextLabel : 1 }"
          v-if="
            commonConfig &&
            commonConfig.TextLabel &&
            (commonConfig.TextLabel.functionType == '23' || commonConfig.TextLabel.functionType == mode)
          " :configMin="commonConfig && commonConfig.TextLabel"></text-label> -->
        <line-navigation ref="lineNavigation"
          :style="{ order: commonSort && commonSort.LineNavigation ? commonSort.LineNavigation : 1 }" v-if="
            commonConfig &&
            commonConfig.LineNavigation &&
            (commonConfig.LineNavigation.functionType == '23' || commonConfig.LineNavigation.functionType == mode)
          " :configMin="commonConfig && commonConfig.LineNavigation"></line-navigation>
        <element-hidden ref="elementHidden"
          :style="{ order: commonSort && commonSort.ElementHidden ? commonSort.ElementHidden : 1 }" v-if="
            commonConfig &&
            commonConfig.ElementHidden &&
            (commonConfig.ElementHidden.functionType == '23' || commonConfig.ElementHidden.functionType == mode)
          " :configMin="commonConfig && commonConfig.ElementHidden"></element-hidden>
        <view-mode ref="viewMode" :style="{ order: commonSort && commonSort.ViewMode ? commonSort.ViewMode : 1 }" v-if="
          commonConfig &&
          commonConfig.ViewMode &&
          (commonConfig.ViewMode.functionType == '23' || commonConfig.ViewMode.functionType == mode)
        " :configMin="commonConfig && commonConfig.ViewMode"></view-mode>
        <timeline-data-contrast ref="timelineDataContrast"
          :style="{ order: commonSort && commonSort.TimelineDataContrast ? commonSort.TimelineDataContrast : 1 }" v-if="
            commonConfig &&
            commonConfig.TimelineDataContrast &&
            (commonConfig.TimelineDataContrast.functionType == '23' ||
              commonConfig.TimelineDataContrast.functionType == mode)
          " :configMin="commonConfig && commonConfig.TimelineDataContrast"></timeline-data-contrast>
      </div>
    </div>
    <!-- <div class="common-btn"  @click="controlAll" >  :style="{ backgroundImage: `url(${common_imageUrl})` }"-->
    <div class="common-btn" @click="controlAll">
      <div class="title"></div>
      <div class="btn-img"
        :style="{ backgroundImage: `url(${imageUrl})`, position: 'absolute', top: '21px', left: '16px' }"></div>
    </div>
  </div>
</template>

<script>


export default {
  name: 'HomeCommonFooter',
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
    // this.commonbtn_imageUrl=this.GlobalImgFolderSelect(`map-btns/default.png`);
    this.$bus.$on('cancelCommonChecked', (val) => {
      val && this.cancelAll()
    })
    this.$bus.$on('cancelCommonClickEvent', (val) => {
      this.cancelClickEvent(true)
    })
    this.getCommonConfig()
    this.$bus.$on('change3D2Dmodes', (val) => {
      this.change3D2Dmode(val)
    })
    this.$bus.$on('bkSplitScreenshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.SplitScreen &&
        (this.commonConfig.SplitScreen.functionType == '23' || this.commonConfig.SplitScreen.functionType == this.mode)
      ) {
        this.$bus.$emit('bkSplitScreenBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
    this.$bus.$on('bkTimelineDataContrastshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.TimelineDataContrast &&
        (this.commonConfig.TimelineDataContrast.functionType == '23' ||
          this.commonConfig.TimelineDataContrast.functionType == this.mode)
      ) {
        this.$bus.$emit('bkTimelineDataContrastBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
  },
  methods: {
    getCommonConfig() {
      try {
        let arr = this.$store.getters.sysConfig['Common']
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
      } catch (error) { }
    },
    controlAll() {
      // 控制所有按钮的显示隐藏
      this.allShow = !this.allShow
      this.imageUrl = this.allShow
        ? this.GlobalImgFolderSelect(`map-btns/BtnsControl/checked.png`)
        : this.GlobalImgFolderSelect(`map-btns/BtnsControl/default.png`)
    },
    cancelAll() {
      // 清除所有按钮的选中状态
      this.cancelClickEvent(false)

      this.$refs.drawGeometric && this.$refs.drawGeometric.checked && this.$refs.drawGeometric.cancelChecked()
      this.$refs.drawGraph && this.$refs.drawGraph.checked && this.$refs.drawGraph.cancelChecked()
      this.$refs.bookmarks && this.$refs.bookmarks.checked && this.$refs.bookmarks.cancelChecked()

      this.$refs.animationNav && this.$refs.animationNav.checked && this.$refs.animationNav.cancelChecked()
      this.$refs.transparentGround &&
        this.$refs.transparentGround.checked &&
        this.$refs.transparentGround.cancelChecked()
      this.$refs.transparentTerrain &&
        this.$refs.transparentTerrain.checked &&
        this.$refs.transparentTerrain.cancelChecked()
      this.$refs.splitScreen && this.$refs.splitScreen.checked && this.$refs.splitScreen.cancelChecked()
      this.$refs.scenePlotting && this.$refs.scenePlotting.checked && this.$refs.scenePlotting.cancelChecked()
      this.$refs.coordinatePosition &&
        this.$refs.coordinatePosition.checked &&
        this.$refs.coordinatePosition.cancelChecked()

      this.$refs.textConfigure && this.$refs.textConfigure.checked && this.$refs.textConfigure.cancelChecked()
      this.$refs.textLabel && this.$refs.textLabel.checked && this.$refs.textLabel.cancelChecked()
      this.$refs.lineNavigation && this.$refs.lineNavigation.checked && this.$refs.lineNavigation.cancelChecked()
      this.$refs.timelineDataContrast &&
        this.$refs.timelineDataContrast.checked &&
        this.$refs.timelineDataContrast.cancelChecked()
    },
    cancelClickEvent(isOther) {
      // 取消带有点击事件的功能
      this.$refs.measureLine && this.$refs.measureLine.checked && this.$refs.measureLine.cancelChecked()
      this.$refs.measureArea && this.$refs.measureArea.checked && this.$refs.measureArea.cancelChecked()
      this.$refs.measureSpace && this.$refs.measureSpace.checked && this.$refs.measureSpace.cancelChecked()
      this.$refs.coordinateLabel && this.$refs.coordinateLabel.checked && this.$refs.coordinateLabel.cancelChecked()
      this.$refs.elementHidden && this.$refs.elementHidden.checked && this.$refs.elementHidden.cancelChecked()
      if (isOther) {
        // isOther 具体功能中带有点击事件的按钮
        this.$refs.drawGraph &&
          this.$refs.drawGraph.checked &&
          this.$refs.drawGraph.$refs.drawGraphList.controlChecked()
      }
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
// }</style>