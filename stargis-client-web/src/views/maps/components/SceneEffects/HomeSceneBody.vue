<template>
  <div class="home-body">
    <sensors-pop ref="sensorsPop" v-if="commonConfig && commonConfig.sensors"
      :configMin="commonConfig && commonConfig.sensors"></sensors-pop>
    <iot-perception-pop ref="IotPerceptionPop" v-if="commonConfig && commonConfig.IotPerception"
      :configMin="commonConfig && commonConfig.IotPerception"></iot-perception-pop>
    <wind-farm-pop ref="windfarmPop" v-if="commonConfig && commonConfig.WindFarm"
      :configMin="commonConfig && commonConfig.WindFarm"></wind-farm-pop>
    <roller-compare-pop ref="rollerComparePop" v-if="commonConfig && commonConfig.RollerCompare"
      :configMin="commonConfig && commonConfig.RollerCompare"></roller-compare-pop>
    <fire-pop ref="firePop" v-if="commonConfig && commonConfig.Fire"
      :configMin="commonConfig && commonConfig.Fire"></fire-pop>
    <fountain-effect-pop ref="fountaineffectPop" v-if="commonConfig && commonConfig.FountainEffect"
      :configMin="commonConfig && commonConfig.FountainEffect"></fountain-effect-pop>
    <scan-lines-pop ref="scanlinesPop" v-if="commonConfig && commonConfig.ScanLines"
      :configMin="commonConfig && commonConfig.ScanLines"></scan-lines-pop>
    <roller-model-compare-pop ref="rollerModelComparePop" v-if="commonConfig && commonConfig.RollerModelCompare"
      :configMin="commonConfig && commonConfig.RollerModelCompare"></roller-model-compare-pop>
  </div>
</template>

<script>
// import sensorsPop from './Sensors/sensorsPop.vue'
export default {
  name: 'HomeSceneBody',
  components: {
    // sensorsPop,
  },
  data() {
    return {
      commonConfig: {},
    }
  },
  created() {
    this.getCommonConfig()
    this.$bus.$on('removeScenePopClickEvent', this.controlAll)
  },
  methods: {
    getCommonConfig() {
      let arr = this.$store.getters.sysConfig['SceneEffects']
      arr.children.map((item, index) => {
        this.commonConfig[item.componentname] = item
      })
    },
    controlAll() {
      // 移除面板中的点击事件
      this.$refs.scanlinesPop && this.$refs.scanlinesPop.controlChecked()
    },
  },
}
</script>

<style scoped lang="less">
/* common_btn.less / common_pop.less 已改为 main.js 全局引入一次，此处不再重复 @import */

.home-body {
  position: absolute;
  bottom: 88px;
  left: 0;
  width: 100%;
  height: calc(100% - 75px - 80px);
  z-index: 1001;
  display: inline-flex;
  pointer-events: none;
}
</style>