<template>
  <div class="measure-line-btn" :class="checked ? 'btn-checked' : ''" @click="btnClick">
    <div class="title">{{configMin ? configMin.name : '直线量测'}}</div>
    <div class="btn-img" :style="{backgroundImage: `url(${imageUrl})`}"></div>
  </div>
</template>

<script>
  export default {
    name: 'index',
    props: {
      configMin: Object
    },
    data () {
      return {
        checked: false,
        centerImage: 'map-btns/MeasureLine',
        imageUrl: ''
      }
    },
    created() {
      if(this.configMin){
        this.centerImage = this.configMin.icon
      }
      this.imageUrl = this.GlobalImgFolderSelect(`${this.centerImage}/default.png`)
    },
    methods: {
      btnClick () {
        if(!this.checked){
          this.$parent.cancelAll()
        }
        this.checked = !this.checked
        this.checked ? this.onCheckedBtn() : this.cancelChecked()
      },
      onCheckedBtn () {
        this.checked = true
        this.imageUrl = this.GlobalImgFolderSelect(`${this.centerImage}/checked.png`)
      },
      cancelChecked () {
        this.checked = false
        this.imageUrl = this.GlobalImgFolderSelect(`${this.centerImage}/default.png`)
      }
    }
  }
</script>

<style scoped lang="less">
  @import "~@/assets/less/common_btn.less";
</style>