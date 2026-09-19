<template>
  <div class="home-footer">
    <div class="btn-content">
      <div class="all-btns" v-show="allShow">
        <project-code
          ref="projectCode"
          :style="{ order: commonSort && commonSort.ProjectCode ? commonSort.ProjectCode : 1 }"
          v-if="
            commonConfig &&
            commonConfig.ProjectCode &&
            (commonConfig.ProjectCode.functionType == '23' || commonConfig.ProjectCode.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.ProjectCode"
        ></project-code>
        <project-attribute
          ref="projectAttribute"
          :style="{ order: commonSort && commonSort.ProjectAttribute ? commonSort.ProjectAttribute : 1 }"
          v-if="
            commonConfig &&
            commonConfig.ProjectAttribute &&
            (commonConfig.ProjectAttribute.functionType == '23' || commonConfig.ProjectAttribute.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.ProjectAttribute"
        ></project-attribute>
        <project-like
          ref="projectLike"
          :style="{ order: commonSort && commonSort.ProjectLike ? commonSort.ProjectLike : 1 }"
          v-if="
            commonConfig &&
            commonConfig.ProjectLike &&
            (commonConfig.ProjectLike.functionType == '23' || commonConfig.ProjectLike.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.ProjectLike"
        ></project-like>
        <project-contrast
          ref="projectContrast"
          :style="{ order: commonSort && commonSort.ProjectContrast ? commonSort.ProjectContrast : 1 }"
          v-if="
            commonConfig &&
            commonConfig.ProjectContrast &&
            (commonConfig.ProjectContrast.functionType == '23' || commonConfig.ProjectContrast.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.ProjectContrast"
        ></project-contrast>
        <project-cycle
          ref="projectCycle"
          :style="{ order: commonSort && commonSort.ProjectCycle ? commonSort.ProjectCycle : 1 }"
          v-if="
            commonConfig &&
            commonConfig.ProjectCycle &&
            (commonConfig.ProjectCycle.functionType == '23' || commonConfig.ProjectCycle.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.ProjectCycle"
        ></project-cycle>
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
  name: 'HomePlanningProjectFooter',
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
    this.$bus.$on('cancelPlanningProjectChecked', (val) => {
      val && this.cancelAll()
    })
    this.getCommonConfig()
    this.$bus.$on('change3D2Dmodes', (val) => {
      this.change3D2Dmode(val)
    })
  },
  methods: {
    getCommonConfig() {
      try {
        let arr = this.$store.getters.sysConfig['PlanningProject']
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
      this.$refs.projectCode && this.$refs.projectCode.checked && this.$refs.projectCode.cancelChecked()
      this.$refs.projectAttribute && this.$refs.projectAttribute.checked && this.$refs.projectAttribute.cancelChecked()
      this.$refs.projectLike && this.$refs.projectLike.checked && this.$refs.projectLike.cancelChecked()
      this.$refs.projectContrast && this.$refs.projectContrast.checked && this.$refs.projectContrast.cancelChecked()
      this.$refs.projectCycle && this.$refs.projectCycle.checked && this.$refs.projectCycle.cancelChecked()
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