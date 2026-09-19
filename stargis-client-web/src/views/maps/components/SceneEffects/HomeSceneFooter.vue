<template>
  <div class="home-footer">
    <div class="btn-content">
      <div class="all-btns" v-show="allShow">
        <sensors ref="Sensors"
          :style="{ order: commonSort && commonSort.sensors? commonSort.sensors : 1 }" v-if="commonConfig &&
        commonConfig.sensors &&
        (commonConfig.sensors.functionType == '23' || commonConfig.sensors.functionType == mode)
        " :configMin="commonConfig && commonConfig.sensors"></sensors>

        <iot-perception ref="iotPerception"
          :style="{ order: commonSort && commonSort.IotPerception ? commonSort.IotPerception : 1 }" v-if="commonConfig &&
        commonConfig.IotPerception &&
        (commonConfig.IotPerception.functionType == '23' || commonConfig.IotPerception.functionType == mode)
        " :configMin="commonConfig && commonConfig.IotPerception"></iot-perception>

        <wind-farm ref="windFarm" :style="{ order: commonSort && commonSort.WindFarm ? commonSort.WindFarm : 1 }" v-if="commonConfig &&
        commonConfig.WindFarm &&
        (commonConfig.WindFarm.functionType == '23' || commonConfig.WindFarm.functionType == mode)
        " :configMin="commonConfig && commonConfig.WindFarm"></wind-farm>
        <rain ref="rain" :style="{ order: commonSort && commonSort.Rain ? commonSort.Rain : 1 }" v-if="commonConfig &&
        commonConfig.Rain &&
        (commonConfig.Rain.functionType == '23' || commonConfig.Rain.functionType == mode)
        " :configMin="commonConfig && commonConfig.Rain"></rain>
        <snow ref="snow" :style="{ order: commonSort && commonSort.Snow ? commonSort.Snow : 1 }" v-if="commonConfig &&
        commonConfig.Snow &&
        (commonConfig.Snow.functionType == '23' || commonConfig.Snow.functionType == mode)
        " :configMin="commonConfig && commonConfig.Snow"></snow>
        <roller-compare ref="rollerCompare"
          :style="{ order: commonSort && commonSort.RollerCompare ? commonSort.RollerCompare : 1 }" v-if="commonConfig &&
        commonConfig.RollerCompare &&
        (commonConfig.RollerCompare.functionType == '23' || commonConfig.RollerCompare.functionType == mode)
        " :configMin="commonConfig && commonConfig.RollerCompare"></roller-compare>
        <fire ref="fire" :style="{ order: commonSort && commonSort.Fire ? commonSort.Fire : 1 }" v-if="commonConfig &&
        commonConfig.Fire &&
        (commonConfig.Fire.functionType == '23' || commonConfig.Fire.functionType == mode)
        " :configMin="commonConfig && commonConfig.Fire"></fire>
        <fountain-effect ref="fountainEffect"
          :style="{ order: commonSort && commonSort.FountainEffect ? commonSort.FountainEffect : 1 }" v-if="commonConfig &&
        commonConfig.FountainEffect &&
        (commonConfig.FountainEffect.functionType == '23' || commonConfig.FountainEffect.functionType == mode)
        " :configMin="commonConfig && commonConfig.FountainEffect"></fountain-effect>
        <scan-lines ref="scanLines" :style="{ order: commonSort && commonSort.ScanLines ? commonSort.ScanLines : 1 }"
          v-if="commonConfig &&
        commonConfig.ScanLines &&
        (commonConfig.ScanLines.functionType == '23' || commonConfig.ScanLines.functionType == mode)
        " :configMin="commonConfig && commonConfig.ScanLines"></scan-lines>
        <fly-lines ref="flyLines" :style="{ order: commonSort && commonSort.FlyLines ? commonSort.FlyLines : 1 }" v-if="commonConfig &&
        commonConfig.FlyLines &&
        (commonConfig.FlyLines.functionType == '23' || commonConfig.FlyLines.functionType == mode)
        " :configMin="commonConfig && commonConfig.FlyLines"></fly-lines>
        <migration-map ref="migrationMap"
          :style="{ order: commonSort && commonSort.MigrationMap ? commonSort.MigrationMap : 1 }" v-if="commonConfig &&
        commonConfig.MigrationMap &&
        (commonConfig.MigrationMap.functionType == '23' || commonConfig.MigrationMap.functionType == mode)
        " :configMin="commonConfig && commonConfig.MigrationMap"></migration-map>

        <roller-model-compare ref="rollerModelCompare"
          :style="{ order: commonSort && commonSort.RollerModelCompare ? commonSort.RollerModelCompare : 1 }" v-if="commonConfig &&
        commonConfig.RollerModelCompare &&
        (commonConfig.RollerModelCompare.functionType == '23' ||
          commonConfig.RollerModelCompare.functionType == mode)
        " :configMin="commonConfig && commonConfig.RollerModelCompare"></roller-model-compare>
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
  name: 'HomeSceneFooter',
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
    this.$bus.$on('cancelSceneChecked', (val) => {
      val && this.cancelAll()
    })
    this.$bus.$on('cancelSceneClickEvent', (val) => {
      this.cancelClickEvent(true)
    })
    this.getCommonConfig()
    this.$bus.$on('change3D2Dmodes', (val) => {
      this.change3D2Dmode(val)
    })
    this.$bus.$on('bkRollerCompareshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.RollerCompare &&
        (this.commonConfig.RollerCompare.functionType == '23' ||
          this.commonConfig.RollerCompare.functionType == this.mode)
      ) {
        this.$bus.$emit('bkRollerCompareBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
    this.$bus.$on('bkRollerModelCompareshow', (val) => {
      if (
        this.commonConfig &&
        this.commonConfig.RollerModelCompare &&
        (this.commonConfig.RollerModelCompare.functionType == '23' ||
          this.commonConfig.RollerModelCompare.functionType == this.mode)
      ) {
        this.$bus.$emit('bkRollerModelCompareBK', val)
      } else {
        this.$message.error(`${this.mode}维模式不包含此书签绑定的功能`)
      }
    })
  },
  methods: {
    getCommonConfig() {
      try {
        // console.log(this.$store.getters.sysConfig)
        let arr = this.$store.getters.sysConfig['SceneEffects']
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
      this.cancelClickEvent(false)
      // 清除所有按钮的选中状态
      this.$refs.Sensors && this.$refs.Sensors.checked && this.$refs.Sensors.cancelChecked()

      this.$refs.windFarm && this.$refs.windFarm.checked && this.$refs.windFarm.cancelChecked()
      this.$refs.rain && this.$refs.rain.checked && this.$refs.rain.cancelChecked()
      this.$refs.snow && this.$refs.snow.checked && this.$refs.snow.cancelChecked()
      this.$refs.rollerCompare && this.$refs.rollerCompare.checked && this.$refs.rollerCompare.cancelChecked()

      this.$refs.scanLines && this.$refs.scanLines.checked && this.$refs.scanLines.cancelChecked()
      this.$refs.flyLines && this.$refs.flyLines.checked && this.$refs.flyLines.cancelChecked()
      this.$refs.migrationMap && this.$refs.migrationMap.checked && this.$refs.migrationMap.cancelChecked()
      this.$refs.iotPerception && this.$refs.iotPerception.checked && this.$refs.iotPerception.cancelChecked()
      this.$refs.rollerModelCompare &&
        this.$refs.rollerModelCompare.checked &&
        this.$refs.rollerModelCompare.cancelChecked()
    },
    cancelClickEvent(isOther) {
      // 取消带有点击事件的功能
      this.$refs.fire && this.$refs.fire.checked && this.$refs.fire.cancelChecked()
      this.$refs.fountainEffect && this.$refs.fountainEffect.checked && this.$refs.fountainEffect.cancelChecked()
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