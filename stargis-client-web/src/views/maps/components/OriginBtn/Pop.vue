<template>
    <div class="pop pop-left" v-show="isShow">
      <div class="pop-header">
        <div class="pop-title">
          <div class="pop-title-logo"></div>
          <div class="pop-title-font">{{configMin ? configMin.name : '绘制几何体'}}</div>
        </div>
        <div class="pop-close" @click="closePop"></div>
      </div>
      <div class="pop-body">
      </div>
    </div>
</template>

<script>
  export default {
    name: 'Pop',
    props: {
      configMin: Object
    },
    data () {
      return {
        isShow: false
      }
    },
    created() {
      // 根据当前功能需要，下面方法要重新命名
      this.popShowControl()
    },
    methods: {
      closePop () {
        this.isShow = false
      },
      showPop () {
        this.isShow = true
      },
      popShowControl () {
        this.$bus.$once('showPop', (val)=>{
          val === true ? this.showPop() : this.closePop()
        })
      }
    },
    watch: {
      isShow (newVal, oldVal) {
        if(!newVal){
          this.popShowControl()
        } else {
          this.popShowControl()
        }
      }
    }
  }
</script>

<style scoped lang="less">
  @import "~@/assets/less/common_pop.less";
</style>