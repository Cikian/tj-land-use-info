<template>
  <div class="home-footer">
    <div class="btn-content">
      <div class="all-btns" v-show="allShow">
        <building-check
          ref="buildingCheck"
          :style="{ order: commonSort && commonSort.BuildingCheck ? commonSort.BuildingCheck : 1 }"
          v-if="
            commonConfig &&
            commonConfig.BuildingCheck &&
            (commonConfig.BuildingCheck.functionType == '23' || commonConfig.BuildingCheck.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.BuildingCheck"
        ></building-check>
        <k-g-check
          ref="kgCheck"
          :style="{ order: commonSort && commonSort.KGCheck ? commonSort.KGCheck : 1 }"
          v-if="
            commonConfig &&
            commonConfig.KGCheck &&
            (commonConfig.KGCheck.functionType == '23' || commonConfig.KGCheck.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.KGCheck"
        ></k-g-check>
        <land-check
          ref="landCheck"
          :style="{ order: commonSort && commonSort.LandCheck ? commonSort.LandCheck : 1 }"
          v-if="
            commonConfig &&
            commonConfig.LandCheck &&
            (commonConfig.LandCheck.functionType == '23' || commonConfig.LandCheck.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.LandCheck"
        ></land-check>
        <comprehensive-check
          ref="comprehensiveCheck"
          :style="{ order: commonSort && commonSort.ComprehensiveCheck ? commonSort.ComprehensiveCheck : 1 }"
          v-if="
            commonConfig &&
            commonConfig.ComprehensiveCheck &&
            (commonConfig.ComprehensiveCheck.functionType == '23' ||
              commonConfig.ComprehensiveCheck.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.ComprehensiveCheck"
        ></comprehensive-check>
        <site-selection
          ref="siteSelection"
          :style="{ order: commonSort && commonSort.SiteSelection ? commonSort.SiteSelection : 1 }"
          v-if="
            commonConfig &&
            commonConfig.SiteSelection &&
            (commonConfig.SiteSelection.functionType == '23' || commonConfig.SiteSelection.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.SiteSelection"
        ></site-selection>
        <land-structure
          ref="landStructure"
          :style="{ order: commonSort && commonSort.LandStructure ? commonSort.LandStructure : 1 }"
          v-if="
            commonConfig &&
            commonConfig.LandStructure &&
            (commonConfig.LandStructure.functionType == '23' || commonConfig.LandStructure.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.LandStructure"
        ></land-structure>
        <stock-land
          ref="stockLand"
          :style="{ order: commonSort && commonSort.StockLand ? commonSort.StockLand : 1 }"
          v-if="
            commonConfig &&
            commonConfig.StockLand &&
            (commonConfig.StockLand.functionType == '23' || commonConfig.StockLand.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.StockLand"
        ></stock-land>
        <special-land-use
          ref="specialLandUse"
          :style="{ order: commonSort && commonSort.SpecialLandUse ? commonSort.SpecialLandUse : 1 }"
          v-if="
            commonConfig &&
            commonConfig.SpecialLandUse &&
            (commonConfig.SpecialLandUse.functionType == '23' || commonConfig.SpecialLandUse.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.SpecialLandUse"
        ></special-land-use>
        <road-statistic
          ref="roadStatistic"
          :style="{ order: commonSort && commonSort.RoadStatistic ? commonSort.RoadStatistic : 1 }"
          v-if="
            commonConfig &&
            commonConfig.RoadStatistic &&
            (commonConfig.RoadStatistic.functionType == '23' || commonConfig.RoadStatistic.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.RoadStatistic"
        ></road-statistic>
        <building-scale-statistic
          ref="buildingScaleStatistic"
          :style="{ order: commonSort && commonSort.BuildingScaleStatistic ? commonSort.BuildingScaleStatistic : 1 }"
          v-if="
            commonConfig &&
            commonConfig.BuildingScaleStatistic &&
            (commonConfig.BuildingScaleStatistic.functionType == '23' ||
              commonConfig.BuildingScaleStatistic.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.BuildingScaleStatistic"
        ></building-scale-statistic>
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
  name: 'HomeBuildingLandFooter',
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
    this.imageUrl = this.GlobalImgFolderSelect(`map-btns/BtnsControl/checked.png`)
    // this.imageUrl = require(`@/assets/map-btns/BtnsControl/checked.png`)
    this.$bus.$on('cancelBuildingLandChecked', (val) => {
      val && this.cancelAll()
    })
    this.$bus.$on('cancelBuildingLandClickEvent', this.cancelClickEvent)
    this.getCommonConfig()
    this.$bus.$on('change3D2Dmodes', (val) => {
      this.change3D2Dmode(val)
    })
    this.$bus.$on('bkBuildingScaleStatisticshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.BuildingScaleStatistic &&
        (this.commonConfig.BuildingScaleStatistic.functionType == '23' ||
          this.commonConfig.BuildingScaleStatistic.functionType == this.mode)
      ) {
        this.$bus.$emit('bkBuildingScaleStatisticBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
    this.$bus.$on('bkLandStructureshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.LandStructure &&
        (this.commonConfig.LandStructure.functionType == '23' ||
          this.commonConfig.LandStructure.functionType == this.mode)
      ) {
        this.$bus.$emit('bkLandStructureBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
    this.$bus.$on('bkSiteSelectionshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.SiteSelection &&
        (this.commonConfig.SiteSelection.functionType == '23' ||
          this.commonConfig.SiteSelection.functionType == this.mode)
      ) {
        this.$bus.$emit('bkSiteSelectionBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
  },
  methods: {
    getCommonConfig() {
      try {
        let arr = this.$store.getters.sysConfig['BuildingLand']
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
    },
    cancelAll() {
      // 清除所有按钮的选中状态
      this.cancelClickEvent()
      this.$refs.siteSelection && this.$refs.siteSelection.checked && this.$refs.siteSelection.cancelChecked()
      this.$refs.landStructure && this.$refs.landStructure.checked && this.$refs.landStructure.cancelChecked()
      this.$refs.stockLand && this.$refs.stockLand.checked && this.$refs.stockLand.cancelChecked()
      this.$refs.specialLandUse && this.$refs.specialLandUse.checked && this.$refs.specialLandUse.cancelChecked()
      this.$refs.roadStatistic && this.$refs.roadStatistic.checked && this.$refs.roadStatistic.cancelChecked()
      this.$refs.buildingScaleStatistic &&
        this.$refs.buildingScaleStatistic.checked &&
        this.$refs.buildingScaleStatistic.cancelChecked()
    },
    cancelClickEvent() {
      this.$refs.buildingCheck && this.$refs.buildingCheck.checked && this.$refs.buildingCheck.cancelChecked()
      this.$refs.kgCheck && this.$refs.kgCheck.checked && this.$refs.kgCheck.cancelChecked()
      this.$refs.landCheck && this.$refs.landCheck.checked && this.$refs.landCheck.cancelChecked()
      this.$refs.comprehensiveCheck &&
        this.$refs.comprehensiveCheck.checked &&
        this.$refs.comprehensiveCheck.cancelChecked()
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