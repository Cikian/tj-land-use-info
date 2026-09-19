<template>
  <div class="home-footer">
    <div class="btn-content">
      <div class="all-btns" v-show="allShow">
        <!-- <query-place-name
          ref="queryPlaceName"
          :style="{ order: commonSort && commonSort.QueryPlaceName ? commonSort.QueryPlaceName : 1 }"
          v-if="
            commonConfig &&
            commonConfig.QueryPlaceName &&
            (commonConfig.QueryPlaceName.functionType == '23' || commonConfig.QueryPlaceName.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.QueryPlaceName"
        ></query-place-name> -->
        <query-road ref="queryRoad" :style="{ order: commonSort && commonSort.QueryRoad ? commonSort.QueryRoad : 1 }"
          v-if="commonConfig &&
            commonConfig.QueryRoad &&
            (commonConfig.QueryRoad.functionType == '23' || commonConfig.QueryRoad.functionType == mode)
            " :configMin="commonConfig && commonConfig.QueryRoad"></query-road>
        <query-spatial ref="querySpatial"
          :style="{ order: commonSort && commonSort.QuerySpatial ? commonSort.QuerySpatial : 1 }" v-if="commonConfig &&
            commonConfig.QuerySpatial &&
            (commonConfig.QuerySpatial.functionType == '23' || commonConfig.QuerySpatial.functionType == mode)
            " :configMin="commonConfig && commonConfig.QuerySpatial"></query-spatial>
        <query-attribute ref="queryAttribute"
          :style="{ order: commonSort && commonSort.QueryAttribute ? commonSort.QueryAttribute : 1 }" v-if="commonConfig &&
            commonConfig.QueryAttribute &&
            (commonConfig.QueryAttribute.functionType == '23' || commonConfig.QueryAttribute.functionType == mode)
            " :configMin="commonConfig && commonConfig.QueryAttribute"></query-attribute>
        <query-comprehensive ref="queryComprehensive"
          :style="{ order: commonSort && commonSort.QueryComprehensive ? commonSort.QueryComprehensive : 1 }" v-if="commonConfig &&
            commonConfig.QueryComprehensive &&
            (commonConfig.QueryComprehensive.functionType == '23' ||
              commonConfig.QueryComprehensive.functionType == mode)
            " :configMin="commonConfig && commonConfig.QueryComprehensive"></query-comprehensive>
        <query-statistic ref="queryStatistic"
          :style="{ order: commonSort && commonSort.QueryStatistic ? commonSort.QueryStatistic : 1 }" v-if="commonConfig &&
            commonConfig.QueryStatistic &&
            (commonConfig.QueryStatistic.functionType == '23' || commonConfig.QueryStatistic.functionType == mode)
            " :configMin="commonConfig && commonConfig.QueryStatistic"></query-statistic>
        <search-object ref="searchObject"
          :style="{ order: commonSort && commonSort.SearchObject ? commonSort.SearchObject : 1 }" v-if="commonConfig &&
            commonConfig.SearchObject &&
            (commonConfig.SearchObject.functionType == '23' || commonConfig.SearchObject.functionType == mode)
            " :configMin="commonConfig && commonConfig.SearchObject"></search-object>

        <search-video ref="searchVideo"
          :style="{ order: commonSort && commonSort.SearchVideo ? commonSort.SearchVideo : 1 }" v-if="commonConfig &&
            commonConfig.SearchVideo &&
            (commonConfig.SearchVideo.functionType == '23' || commonConfig.SearchVideo.functionType == mode)
            " :configMin="commonConfig && commonConfig.SearchVideo"></search-video>

        <space-hidden ref="spaceHidden"
          :style="{ order: commonSort && commonSort.SpaceHidden ? commonSort.SpaceHidden : 1 }" v-if="commonConfig &&
            commonConfig.SpaceHidden &&
            (commonConfig.SpaceHidden.functionType == '23' || commonConfig.SpaceHidden.functionType == mode)
            " :configMin="commonConfig && commonConfig.SpaceHidden"></space-hidden>

        <attribute-hidden ref="attributeHidden"
          :style="{ order: commonSort && commonSort.AttributeHidden ? commonSort.AttributeHidden : 1 }" v-if="commonConfig &&
            commonConfig.AttributeHidden &&
            (commonConfig.AttributeHidden.functionType == '23' || commonConfig.AttributeHidden.functionType == mode)
            " :configMin="commonConfig && commonConfig.AttributeHidden"></attribute-hidden>

        <surveillance-video ref="surveillanceVideo"
          :style="{ order: commonSort && commonSort.SurveillanceVideo ? commonSort.SurveillanceVideo : 1 }" v-if="commonConfig &&
            commonConfig.SurveillanceVideo &&
            (commonConfig.SurveillanceVideo.functionType == '23' || commonConfig.SurveillanceVideo.functionType == mode)
            " :configMin="commonConfig && commonConfig.SurveillanceVideo"></surveillance-video>
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
    this.imageUrl = require(`@/assets/map-btns/BtnsControl/checked.png`)
    this.$bus.$on('cancelQueryStatisticChecked', (val) => {
      val && this.cancelAll()
    })
    this.getCommonConfig()
    this.$bus.$on('change3D2Dmodes', (val) => {
      this.change3D2Dmode(val)
    })
    this.$bus.$on('bkQueryComprehensiveshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.QueryComprehensive &&
        (this.commonConfig.QueryComprehensive.functionType == '23' ||
          this.commonConfig.QueryComprehensive.functionType == this.mode)
      ) {
        this.$bus.$emit('bkQueryComprehensiveBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
  },
  methods: {
    getCommonConfig() {
      try {
        let arr = this.$store.getters.sysConfig['QueryStatistics']
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
        ? require(`@/assets/map-btns/BtnsControl/checked.png`)
        : require(`@/assets/map-btns/BtnsControl/default.png`)
    },
    cancelAll() {
      // 清除所有按钮的选中状态
      this.$refs.queryPlaceName && this.$refs.queryPlaceName.checked && this.$refs.queryPlaceName.cancelChecked()
      this.$refs.queryRoad && this.$refs.queryRoad.checked && this.$refs.queryRoad.cancelChecked()
      this.$refs.querySpatial && this.$refs.querySpatial.checked && this.$refs.querySpatial.cancelChecked()
      this.$refs.queryAttribute && this.$refs.queryAttribute.checked && this.$refs.queryAttribute.cancelChecked()
      this.$refs.queryComprehensive &&
        this.$refs.queryComprehensive.checked &&
        this.$refs.queryComprehensive.cancelChecked()
      this.$refs.queryStatistic && this.$refs.queryStatistic.checked && this.$refs.queryStatistic.cancelChecked()
      this.$refs.searchObject && this.$refs.searchObject.checked && this.$refs.searchObject.cancelChecked()
      this.$refs.searchVideo && this.$refs.searchVideo.checked && this.$refs.searchVideo.cancelChecked()
      this.$refs.spaceHidden && this.$refs.spaceHidden.checked && this.$refs.spaceHidden.cancelChecked()
      this.$refs.attributeHidden && this.$refs.attributeHidden.checked && this.$refs.attributeHidden.cancelChecked()
      this.$refs.surveillanceVideo &&
        this.$refs.surveillanceVideo.checked &&
        this.$refs.surveillanceVideo.cancelChecked()
    },
    change3D2Dmode(val) {
      this.mode = val
    },
  },
}
</script>

<style scoped lang="less">
.common-btn {
  background-image: url('~@/assets/map-btns/default.png');
  background-repeat: no-repeat;
  background-size: 100%;
  justify-content: center;
  cursor: pointer;
  position: relative;
  pointer-events: initial;

  .title {
    color: #ffffff;
    line-height: 20px;
    background-color: rgba(0, 0, 0, 0.4);
    text-align: center;
    border-radius: 20px;
    padding: 0 6px;
    min-width: 80px;
  }

  .btn-img {
    width: 48px;
    height: 48px;
    background-repeat: no-repeat;
    background-position: center;
  }
}
</style>