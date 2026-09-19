<template>
  <div class="home-body">
    <line-split-pop
      ref="LineSplitPop"
      v-if="commonConfig && commonConfig.LineSplit"
      :configMin="commonConfig && commonConfig.LineSplit"
    ></line-split-pop>

  </div>
</template>

<script>

  export default {
    name: 'HomeGeologyBody',
    components: {

    },
    data() {
      return {
        commonConfig: {}
      }
    },
    created() {
      this.getCommonConfig()
    },
    methods: {
      getCommonConfig() {
        let arr = this.$store.getters.sysConfig['GeologyManager']
        arr.children.map((item, index) => {
          this.commonConfig[item.componentname] = item
        })
      },
      controlAll() {
        // 控制所有按钮的显示隐藏
      }
    }
  }
</script>

<style scoped lang="less">
  @import '~@/assets/less/common_btn.less';
  @import '~@/assets/less/common_pop.less';

  .home-body {
    position: absolute;
    bottom: 88px;
    left: 0;
    width: 100%;
    height: calc(100% - 90px - 80px);
    z-index: 1001;
    display: inline-flex;
    pointer-events: none;
  }
</style>