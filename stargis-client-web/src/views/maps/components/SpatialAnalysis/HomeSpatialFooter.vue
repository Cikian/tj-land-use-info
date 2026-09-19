<template>
  <div class="home-footer">
    <div class="btn-content">
      <div class="all-btns" v-show="allShow">
        <terrain-relief ref="terrainreliefPop"
          :style="{ order: commonSort && commonSort.TerrainRelief ? commonSort.TerrainRelief : 1 }" v-if="commonConfig &&
        commonConfig.TerrainRelief &&
        (commonConfig.TerrainRelief.functionType == '23' || commonConfig.TerrainRelief.functionType == mode)
        " :configMin="commonConfig && commonConfig.TerrainRelief"></terrain-relief>
        <measure-spear ref="measureSpear"
          :style="{ order: commonSort && commonSort.MeasureSpear ? commonSort.MeasureSpear : 1 }" v-if="commonConfig &&
        commonConfig.MeasureSpear &&
        (commonConfig.MeasureSpear.functionType == '23' || commonConfig.MeasureSpear.functionType == mode)
        " :configMin="commonConfig && commonConfig.MeasureSpear"></measure-spear>
        <openness-analysis ref="opennessAnalysis"
          :style="{ order: commonSort && commonSort.OpennessAnalysis ? commonSort.OpennessAnalysis : 1 }" v-if="commonConfig &&
        commonConfig.OpennessAnalysis &&
        (commonConfig.OpennessAnalysis.functionType == '23' || commonConfig.OpennessAnalysis.functionType == mode)
        " :configMin="commonConfig && commonConfig.OpennessAnalysis"></openness-analysis>
        <video-put ref="videoPut" :style="{ order: commonSort && commonSort.VideoPut ? commonSort.VideoPut : 1 }" v-if="commonConfig &&
        commonConfig.VideoPut &&
        (commonConfig.VideoPut.functionType == '23' || commonConfig.VideoPut.functionType == mode)
        " :configMin="commonConfig && commonConfig.VideoPut"></video-put>
        <visual-range-analysis ref="visualRangeAnalysis"
          :style="{ order: commonSort && commonSort.VisualRangeAnalysis ? commonSort.VisualRangeAnalysis : 1 }" v-if="commonConfig &&
        commonConfig.VisualRangeAnalysis &&
        (commonConfig.VisualRangeAnalysis.functionType == '23' ||
          commonConfig.VisualRangeAnalysis.functionType == mode)
        " :configMin="commonConfig && commonConfig.VisualRangeAnalysis"></visual-range-analysis>
        <polygon-clip ref="polygonClip"
          :style="{ order: commonSort && commonSort.PolygonClip ? commonSort.PolygonClip : 1 }" v-if="commonConfig &&
        commonConfig.PolygonClip &&
        (commonConfig.PolygonClip.functionType == '23' || commonConfig.PolygonClip.functionType == mode)
        " :configMin="commonConfig && commonConfig.PolygonClip"></polygon-clip>
        <facade-analysis ref="facadeAnalysis"
          :style="{ order: commonSort && commonSort.FacadeAnalysis ? commonSort.FacadeAnalysis : 1 }" v-if="commonConfig &&
        commonConfig.FacadeAnalysis &&
        (commonConfig.FacadeAnalysis.functionType == '23' || commonConfig.FacadeAnalysis.functionType == mode)
        " :configMin="commonConfig && commonConfig.FacadeAnalysis"></facade-analysis>
        <box-clip ref="boxClip" :style="{ order: commonSort && commonSort.BoxClip ? commonSort.BoxClip : 1 }" v-if="commonConfig &&
        commonConfig.BoxClip &&
        (commonConfig.BoxClip.functionType == '23' || commonConfig.BoxClip.functionType == mode)
        " :configMin="commonConfig && commonConfig.BoxClip"></box-clip>
        <profile-analysis ref="profileAnalysis"
          :style="{ order: commonSort && commonSort.ProfileAnalysis ? commonSort.ProfileAnalysis : 1 }" v-if="commonConfig &&
        commonConfig.ProfileAnalysis &&
        (commonConfig.ProfileAnalysis.functionType == '23' || commonConfig.ProfileAnalysis.functionType == mode)
        " :configMin="commonConfig && commonConfig.ProfileAnalysis"></profile-analysis>
        <terrain-flatten ref="terrainFlatten"
          :style="{ order: commonSort && commonSort.TerrainFlatten ? commonSort.TerrainFlatten : 1 }" v-if="commonConfig &&
        commonConfig.TerrainFlatten &&
        (commonConfig.TerrainFlatten.functionType == '23' || commonConfig.TerrainFlatten.functionType == mode)
        " :configMin="commonConfig && commonConfig.TerrainFlatten"></terrain-flatten>
        <terrain-excavate ref="terrainExcavate"
          :style="{ order: commonSort && commonSort.TerrainExcavate ? commonSort.TerrainExcavate : 1 }" v-if="commonConfig &&
        commonConfig.TerrainExcavate &&
        (commonConfig.TerrainExcavate.functionType == '23' || commonConfig.TerrainExcavate.functionType == mode)
        " :configMin="commonConfig && commonConfig.TerrainExcavate"></terrain-excavate>
        <inundation-analysis ref="inundationAnalysis"
          :style="{ order: commonSort && commonSort.InundationAnalysis ? commonSort.InundationAnalysis : 1 }" v-if="commonConfig &&
        commonConfig.InundationAnalysis &&
        (commonConfig.InundationAnalysis.functionType == '23' ||
          commonConfig.InundationAnalysis.functionType == mode)
        " :configMin="commonConfig && commonConfig.InundationAnalysis"></inundation-analysis>
        <terrain-inundation ref="terrainInundation"
          :style="{ order: commonSort && commonSort.TerrainInundation ? commonSort.TerrainInundation : 1 }" v-if="commonConfig &&
        commonConfig.TerrainInundation &&
        (commonConfig.TerrainInundation.functionType == '23' || commonConfig.TerrainInundation.functionType == mode)
        " :configMin="commonConfig && commonConfig.TerrainInundation"></terrain-inundation>
        <building-services-scope ref="buildingServicesScope"
          :style="{ order: commonSort && commonSort.BuildingServicesScope ? commonSort.BuildingServicesScope : 1 }"
          v-if="commonConfig &&
        commonConfig.BuildingServicesScope &&
        (commonConfig.BuildingServicesScope.functionType == '23' ||
          commonConfig.BuildingServicesScope.functionType == mode)
        " :configMin="commonConfig && commonConfig.BuildingServicesScope"></building-services-scope>

        <layer-animation ref="LayerAnimation"
          :style="{ order: commonSort && commonSort.LayerAnimation ? commonSort.LayerAnimation : 1 }" v-if="commonConfig &&
        commonConfig.LayerAnimation &&
        (commonConfig.LayerAnimation.functionType == '23' || commonConfig.LayerAnimation.functionType == mode)
        " :configMin="commonConfig && commonConfig.LayerAnimation"></layer-animation>

        <drawer-effect ref="DrawerEffect"
          :style="{ order: commonSort && commonSort.DrawerEffect ? commonSort.DrawerEffect : 1 }" v-if="commonConfig &&
        commonConfig.DrawerEffect &&
        (commonConfig.DrawerEffect.functionType == '23' || commonConfig.DrawerEffect.functionType == mode)
        " :configMin="commonConfig && commonConfig.DrawerEffect"></drawer-effect>
        <model-cut ref="ModelCut" :style="{ order: commonSort && commonSort.ModelCut ? commonSort.ModelCut : 1 }" v-if="commonConfig &&
        commonConfig.ModelCut &&
        (commonConfig.ModelCut.functionType == '23' || commonConfig.ModelCut.functionType == mode)
        " :configMin="commonConfig && commonConfig.ModelCut"></model-cut>
      </div>
    </div>
    <div class="common-btn" style="float: right" @click="controlAll">
      <div class="title"></div>
      <div class="btn-img"
        :style="{ backgroundImage: `url(${imageUrl})`, position: 'absolute', top: '21px', left: '16px' }"></div>
    </div>
  </div>
</template>

<script>

export default {
  name: 'HomeSpatialFooter',
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

    this.$bus.$on('cancelSpatialChecked', (val) => {
      val && this.cancelAll()
    })
    this.$bus.$on('cancelSpatialClickEvent', this.cancelClickEvent)
    this.getCommonConfig()
    this.$bus.$on('change3D2Dmodes', (val) => {
      this.change3D2Dmode(val)
    })
    this.$bus.$on('bkDrawerEffectshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.DrawerEffect &&
        (this.commonConfig.DrawerEffect.functionType == '23' ||
          this.commonConfig.DrawerEffect.functionType == this.mode)
      ) {
        this.$bus.$emit('bkDrawerEffectBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
    this.$bus.$on('bkLayerAnimationshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.LayerAnimation &&
        (this.commonConfig.LayerAnimation.functionType == '23' ||
          this.commonConfig.LayerAnimation.functionType == this.mode)
      ) {
        this.$bus.$emit('bkLayerAnimationBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
  },
  methods: {
    getCommonConfig() {
      try {
        let arr = this.$store.getters.sysConfig['SpatialAnalysis']
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
        // ? require(`@/assets/map-btns/BtnsControl/checked.png`)
        // : require(`@/assets/map-btns/BtnsControl/default.png`)
    },
    cancelAll() {
      // 清除所有按钮的选中状态
      this.$refs.terrainreliefPop && this.$refs.terrainreliefPop.checked && this.$refs.terrainreliefPop.cancelChecked()
      this.$refs.measureSpear && this.$refs.measureSpear.checked && this.$refs.measureSpear.cancelChecked()
      this.$refs.opennessAnalysis && this.$refs.opennessAnalysis.checked && this.$refs.opennessAnalysis.cancelChecked()
      this.$refs.videoPut && this.$refs.videoPut.checked && this.$refs.videoPut.cancelChecked()
      this.$refs.visualRangeAnalysis &&
        this.$refs.visualRangeAnalysis.checked &&
        this.$refs.visualRangeAnalysis.cancelChecked()
      this.$refs.polygonClip && this.$refs.polygonClip.checked && this.$refs.polygonClip.cancelChecked()
      this.$refs.facadeAnalysis && this.$refs.facadeAnalysis.checked && this.$refs.facadeAnalysis.cancelChecked()
      this.$refs.boxClip && this.$refs.boxClip.checked && this.$refs.boxClip.cancelChecked()
      this.$refs.profileAnalysis && this.$refs.profileAnalysis.checked && this.$refs.profileAnalysis.cancelChecked()
      this.$refs.inundationAnalysis &&
        this.$refs.inundationAnalysis.checked &&
        this.$refs.inundationAnalysis.cancelChecked()
      this.$refs.terrainInundation &&
        this.$refs.terrainInundation.checked &&
        this.$refs.terrainInundation.cancelChecked()

      this.$refs.terrainExcavate && this.$refs.terrainExcavate.checked && this.$refs.terrainExcavate.cancelChecked()
      this.$refs.buildingServicesScope &&
        this.$refs.buildingServicesScope.checked &&
        this.$refs.buildingServicesScope.cancelChecked()
      this.$refs.LayerAnimation && this.$refs.LayerAnimation.checked && this.$refs.LayerAnimation.cancelChecked()
      this.$refs.DrawerEffect && this.$refs.DrawerEffect.checked && this.$refs.DrawerEffect.cancelChecked()
      this.$refs.ModelCut && this.$refs.ModelCut.checked && this.$refs.ModelCut.cancelChecked()
      this.cancelClickEvent()
    },
    cancelClickEvent() {
      // 取消带有点击事件的功能
      this.$refs.terrainFlatten && this.$refs.terrainFlatten.checked && this.$refs.terrainFlatten.cancelChecked()
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