<template>
  <div class="home-footer">
    <div class="btn-content">
      <div class="all-btns" v-show="allShow">
        <pipe-collision
          ref="pipeCollision"
          :style="{ order: commonSort && commonSort.PipeCollision ? commonSort.PipeCollision : 1 }"
          v-if="
            commonConfig &&
            commonConfig.PipeCollision &&
            (commonConfig.PipeCollision.functionType == '23' || commonConfig.PipeCollision.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.PipeCollision"
        ></pipe-collision>
        <distance-analysis
          ref="distanceAnalysis"
          :style="{ order: commonSort && commonSort.DistanceAnalysis ? commonSort.DistanceAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.DistanceAnalysis &&
            (commonConfig.DistanceAnalysis.functionType == '23' || commonConfig.DistanceAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.DistanceAnalysis"
        ></distance-analysis>
        <excavation-analysis
          ref="excavationAnalysis"
          :style="{ order: commonSort && commonSort.ExcavationAnalysis ? commonSort.ExcavationAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.ExcavationAnalysis &&
            (commonConfig.ExcavationAnalysis.functionType == '23' ||
              commonConfig.ExcavationAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.ExcavationAnalysis"
        ></excavation-analysis>
        <flowDirection-analysis
          ref="flowDirectionAnalysis"
          :style="{ order: commonSort && commonSort.FlowDirectionAnalysis ? commonSort.FlowDirectionAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.FlowDirectionAnalysis &&
            (commonConfig.FlowDirectionAnalysis.functionType == '23' ||
              commonConfig.FlowDirectionAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.FlowDirectionAnalysis"
        ></flowDirection-analysis>
        <pipe-query
          ref="pipeQuery"
          :style="{ order: commonSort && commonSort.PipeQuery ? commonSort.PipeQuery : 1 }"
          v-if="
            commonConfig &&
            commonConfig.PipeQuery &&
            (commonConfig.PipeQuery.functionType == '23' || commonConfig.PipeQuery.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.PipeQuery"
        ></pipe-query>
        <pipeContion-query
          ref="pipeContionQuery"
          :style="{ order: commonSort && commonSort.PipeContionQuery ? commonSort.PipeContionQuery : 1 }"
          v-if="
            commonConfig &&
            commonConfig.PipeContionQuery &&
            (commonConfig.PipeContionQuery.functionType == '23' || commonConfig.PipeContionQuery.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.PipeContionQuery"
        ></pipeContion-query>
        <burst-analysis
          ref="burstAnalysis"
          :style="{ order: commonSort && commonSort.BurstAnalysis ? commonSort.BurstAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.BurstAnalysis &&
            (commonConfig.BurstAnalysis.functionType == '23' || commonConfig.BurstAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.BurstAnalysis"
        ></burst-analysis>
        <pipe-statistics
          ref="pipeStatistics"
          :style="{ order: commonSort && commonSort.PipeStatistics ? commonSort.PipeStatistics : 1 }"
          v-if="
            commonConfig &&
            commonConfig.PipeStatistics &&
            (commonConfig.PipeStatistics.functionType == '23' || commonConfig.PipeStatistics.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.PipeStatistics"
        ></pipe-statistics>
        <label-tool
          ref="labelTool"
          :style="{ order: commonSort && commonSort.LabelTool ? commonSort.LabelTool : 1 }"
          v-if="
            commonConfig &&
            commonConfig.LabelTool &&
            (commonConfig.LabelTool.functionType == '23' || commonConfig.LabelTool.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.LabelTool"
        ></label-tool>
        <hydrant-search
          ref="hydrantSearch"
          :style="{ order: commonSort && commonSort.HydrantSearch ? commonSort.HydrantSearch : 1 }"
          v-if="
            commonConfig &&
            commonConfig.HydrantSearch &&
            (commonConfig.HydrantSearch.functionType == '23' || commonConfig.HydrantSearch.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.HydrantSearch"
        ></hydrant-search>
        <pipe-legend
          ref="PipeLegend"
          :style="{ order: commonSort && commonSort.PipeLegend ? commonSort.PipeLegend : 1 }"
          v-if="
            commonConfig &&
            commonConfig.PipeLegend &&
            (commonConfig.PipeLegend.functionType == '23' || commonConfig.PipeLegend.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.PipeLegend"
        ></pipe-legend>
        <pipe-mark
          ref="pipeMark"
          :style="{ order: commonSort && commonSort.PipeMark ? commonSort.PipeMark : 1 }"
          v-if="
            commonConfig &&
            commonConfig.PipeMark &&
            (commonConfig.PipeMark.functionType == '23' || commonConfig.PipeMark.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.PipeMark"
        ></pipe-mark>
        <coverage-analysis
          ref="coverageAnalysis"
          :style="{ order: commonSort && commonSort.CoverageAnalysis ? commonSort.CoverageAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.CoverageAnalysis &&
            (commonConfig.CoverageAnalysis.functionType == '23' || commonConfig.CoverageAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.CoverageAnalysis"
        ></coverage-analysis>

        <sectional-analysis
          ref="sectionalAnalysis"
          :style="{ order: commonSort && commonSort.SectionalAnalysis ? commonSort.SectionalAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.SectionalAnalysis &&
            (commonConfig.SectionalAnalysis.functionType == '23' || commonConfig.SectionalAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.SectionalAnalysis"
        ></sectional-analysis>
        <delete-label
          ref="deleteLabel"
          :style="{ order: commonSort && commonSort.DeleteLabel ? commonSort.DeleteLabel : 1 }"
          v-if="
            commonConfig &&
            commonConfig.DeleteLabel &&
            (commonConfig.DeleteLabel.functionType == '23' || commonConfig.DeleteLabel.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.DeleteLabel"
        ></delete-label>
        <depth-label
          ref="depthLabel"
          :style="{ order: commonSort && commonSort.DepthLabel ? commonSort.DepthLabel : 1 }"
          v-if="
            commonConfig &&
            commonConfig.DepthLabel &&
            (commonConfig.DepthLabel.functionType == '23' || commonConfig.DepthLabel.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.DepthLabel"
        ></depth-label>
        <connect-analysis
          ref="connectAnalysis"
          :style="{ order: commonSort && commonSort.ConnectAnalysis ? commonSort.ConnectAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.ConnectAnalysis &&
            (commonConfig.ConnectAnalysis.functionType == '23' || commonConfig.ConnectAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.ConnectAnalysis"
        ></connect-analysis>
        <tracking-analysis
          ref="trackingAnalysis"
          :style="{ order: commonSort && commonSort.TrackingAnalysis ? commonSort.TrackingAnalysis : 1 }"
          v-if="
            commonConfig &&
            commonConfig.TrackingAnalysis &&
            (commonConfig.TrackingAnalysis.functionType == '23' || commonConfig.TrackingAnalysis.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.TrackingAnalysis"
        ></tracking-analysis>
        <range-statistics
          ref="rangeStatistics"
          :style="{ order: commonSort && commonSort.RangeStatistics ? commonSort.RangeStatistics : 1 }"
          v-if="
            commonConfig &&
            commonConfig.RangeStatistics &&
            (commonConfig.RangeStatistics.functionType == '23' || commonConfig.RangeStatistics.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.RangeStatistics"
        ></range-statistics>
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
  name: 'HomePipeFooter',
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
    this.$bus.$on('cancelPipeChecked', (val) => {
      val && this.cancelAll()
    })
    this.$bus.$on('cancelPipeClickEvent', (val) => {
      this.cancelClickEvent(true)
    })
    this.getCommonConfig()
    this.$bus.$on('change3D2Dmodes', (val) => {
      this.change3D2Dmode(val)
    })
    this.$bus.$on('bkHydrantSearchshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.HydrantSearch &&
        (this.commonConfig.HydrantSearch.functionType == '23' ||
          this.commonConfig.HydrantSearch.functionType == this.mode)
      ) {
        this.$bus.$emit('bkHydrantSearchBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
  },
  methods: {
    getCommonConfig() {
      try {
        let arr = this.$store.getters.sysConfig['PipeManager']
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
      this.cancelClickEvent(false)
      //
      this.$refs.pipeCollision && this.$refs.pipeCollision.checked && this.$refs.pipeCollision.cancelChecked()
      this.$refs.distanceAnalysis && this.$refs.distanceAnalysis.checked && this.$refs.distanceAnalysis.cancelChecked()
      this.$refs.excavationAnalysis &&
        this.$refs.excavationAnalysis.checked &&
        this.$refs.excavationAnalysis.cancelChecked()
      this.$refs.flowDirectionAnalysis &&
        this.$refs.flowDirectionAnalysis.checked &&
        this.$refs.flowDirectionAnalysis.cancelChecked()
      this.$refs.pipeQuery && this.$refs.pipeQuery.checked && this.$refs.pipeQuery.cancelChecked()
      this.$refs.pipeContionQuery && this.$refs.pipeContionQuery.checked && this.$refs.pipeContionQuery.cancelChecked()
      this.$refs.burstAnalysis && this.$refs.burstAnalysis.checked && this.$refs.burstAnalysis.cancelChecked()
      this.$refs.labelTool && this.$refs.labelTool.checked && this.$refs.labelTool.cancelChecked()
      this.$refs.pipeStatistics && this.$refs.pipeStatistics.checked && this.$refs.pipeStatistics.cancelChecked()
      this.$refs.hydrantSearch && this.$refs.hydrantSearch.checked && this.$refs.hydrantSearch.cancelChecked()
      this.$refs.pipeMark && this.$refs.pipeMark.checked && this.$refs.pipeMark.cancelChecked()
      this.$refs.coverageAnalysis && this.$refs.coverageAnalysis.checked && this.$refs.coverageAnalysis.cancelChecked()
      this.$refs.PipeLegend && this.$refs.PipeLegend.checked && this.$refs.PipeLegend.cancelChecked()
      this.$refs.sectionalAnalysis &&
        this.$refs.sectionalAnalysis.checked &&
        this.$refs.sectionalAnalysis.cancelChecked()
      this.$refs.connectAnalysis && this.$refs.connectAnalysis.checked && this.$refs.connectAnalysis.cancelChecked()
      this.$refs.trackingAnalysis && this.$refs.trackingAnalysis.checked && this.$refs.trackingAnalysis.cancelChecked()
      this.$refs.rangeStatistics && this.$refs.rangeStatistics.checked && this.$refs.rangeStatistics.cancelChecked()
    },
    cancelClickEvent(isOther) {
      // 取消带有点击事件的功能
      this.$refs.deleteLabel && this.$refs.deleteLabel.checked && this.$refs.deleteLabel.cancelChecked()
      this.$refs.depthLabel && this.$refs.depthLabel.checked && this.$refs.depthLabel.cancelChecked()
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