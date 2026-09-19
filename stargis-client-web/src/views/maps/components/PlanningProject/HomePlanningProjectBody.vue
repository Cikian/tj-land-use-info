<template>
    <div class="home-body">
      <project-code-pop v-if="commonConfig && commonConfig.ProjectCode"
                          :configMin="commonConfig && commonConfig.ProjectCode"></project-code-pop>
      <project-attribute-pop v-if="commonConfig && commonConfig.ProjectAttribute"
                             :configMin="commonConfig && commonConfig.ProjectAttribute"></project-attribute-pop>
      <project-like-pop v-if="commonConfig && commonConfig.ProjectLike"
                        :configMin="commonConfig && commonConfig.ProjectLike"></project-like-pop>
      <project-contrast-pop v-if="commonConfig && commonConfig.ProjectContrast"
                            :configMin="commonConfig && commonConfig.ProjectContrast"></project-contrast-pop>
      <project-cycle-pop v-if="commonConfig && commonConfig.ProjectCycle"
                        :configMin="commonConfig && commonConfig.ProjectCycle"></project-cycle-pop>
    </div>
</template>

<script>


 export default {
    name: 'HomePlanningProjectBody',
    components: {
    },
    data () {
      return {
        commonConfig: {}
      }
    },
    created() {
      this.getCommonConfig()
    },
    methods: {
      getCommonConfig () {
        let arr = this.$store.getters.sysConfig['PlanningProject']
        arr.children.map((item, index)=>{
          this.commonConfig[item.componentname] = item
        })
      },
      controlAll () { // 控制所有按钮的显示隐藏
      },
    }
  }
</script>

<style scoped lang="less">
@import "~@/assets/less/common_btn.less";
@import "~@/assets/less/common_pop.less";
.home-body{
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