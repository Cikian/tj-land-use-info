<template>
  <div class="home-footer">
    <div class="btn-content">
      <div class="all-btns" v-show="allShow">
        <line-split
          ref="lineSplit"
          :style="{ order: commonSort && commonSort.LineSplit ? commonSort.LineSplit : 1 }"
          v-if="
            commonConfig &&
            commonConfig.LineSplit &&
            (commonConfig.LineSplit.functionType == '23' || commonConfig.LineSplit.functionType == mode)
          "
          :configMin="commonConfig && commonConfig.LineSplit"
        ></line-split>
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
// import LineSplit from './LineSplit/index'

export default {
  name: 'HomeGeologyFooter',
  components: {
    // LineSplit,
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
    this.$bus.$on('cancelLineSplitChecked', (val) => {
      val && this.cancelAll()
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
        let arr = this.$store.getters.sysConfig['GeologyManager']
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
      this.cancelClickEvent(false)
      // 清除所有按钮的选中状态
      this.$refs.lineSplit && this.$refs.lineSplit.checked && this.$refs.lineSplit.cancelChecked()
    },
    cancelClickEvent(isOther) {
      // 取消带有点击事件的功能
      this.$refs.lineSplit && this.$refs.lineSplit.checked && this.$refs.lineSplit.cancelChecked()
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