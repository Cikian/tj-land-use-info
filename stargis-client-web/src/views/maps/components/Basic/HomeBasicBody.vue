<template>
  <div class="home-body">
    <layer-tree-pop ref="layerTreePop" @changeStyle="changeStyle"></layer-tree-pop>
    <manager-approval-pop ref="managerApprovalPop" :style="{ right: rightPosition }"></manager-approval-pop>
    <query-pick-pop ref="queryPickPop" />
    <bookmarks-pop
      ref="bookmarks"
      :style="{
        right:
          ($refs.layerTreePop && $refs.layerTreePop.isShow ? 320 : 0) +
          ($refs.managerApprovalPop && $refs.managerApprovalPop.isShow ? 320 : 0) +
          30 +
          'px',
      }"
    >
    </bookmarks-pop>
    <scene-plotting-pop
      ref="scenePlottingPop"
      :style="{
        right:
          ($refs.layerTreePop && $refs.layerTreePop.isShow ? 320 : 0) +
          ($refs.managerApprovalPop && $refs.managerApprovalPop.isShow ? 320 : 0) +
          ($refs.bookmarks && $refs.bookmarks.isShow ? 320 : 0) +
          30 +
          'px',
      }"
    >
    </scene-plotting-pop>
    <!-- <div
      style="
        width: 576px;
        height: 324px;
        background: red;
        position: absolute;
        top: 0px;
        z-index: 1000000;
        pointer-events: auto;
      "
      v-if="showBZvideo"
      :style="{
        right:
          ($refs.layerTreePop && $refs.layerTreePop.isShow ? 320 : 0) +
          ($refs.managerApprovalPop && $refs.managerApprovalPop.isShow ? 320 : 0) +
          ($refs.bookmarks && $refs.bookmarks.isShow ? 320 : 0) +
          30 +
          'px',
      }"
    >
      <div style="z-index: 100000" class="closeBZ" @click="closeBZ"></div>
      <video
        src="@/assets/bengzhan/bengzhan.mp4"
        style="width: 100%; height: 100%"
        controls="controls"
        autoplay
      ></video>
    </div> -->
  </div>
</template>

<script>

export default {
  name: 'HomeBasicBody',
  components: {
  },
  data() {
    return {
      rightPosition: '',
      showBZvideo: false,
    }
  },
  mounted() {
    if (this.$refs.layerTreePop) {
      // 解决三个弹框面板冲突问题
      this.$refs.layerTreePop.isShow = true
      setTimeout(() => {
        this.$refs.layerTreePop.isShow = false
      }, 50)
    }
    this.$bus.$on('showBZvideo', () => {
      this.showBZvideo = true
    })
  },
  methods: {
    controlAll() {
      // 控制所有按钮的显示隐藏
    },
    changeStyle(isShow) {
      this.rightPosition = isShow ? '350px' : ''
    },
    closeBZ() {
      this.showBZvideo = false
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
  z-index: 1002;
  display: inline-flex;
  pointer-events: none;
}
.closeBZ {
  width: 26px;
  height: 26px;
  background-image: url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABoAAAAaCAYAAACpSkzOAAAABHNCSVQICAgIfAhkiAAABJNJREFUSIndlkuI3UUWxn/n1P99TXeiSB4GphkawTZRZpdoXDjiQnQRQRiYxWxmGIRBXYguVBAXIoLgIiBEF64GRkF05WPcCD4wuDEBW5Dr0EJrMkGH2KT/z6pTLm5fc+9NgzKLWcyBWlTVqfr4Tp3znYL/N5O5WYyy6/rO7mRH4oL/r/K94nQFRAFdZazTrYzVuA7GZMSZs7oG2jMWTyIJPl7luwO2CKRr4C5xPmnJkyVUAbYwK+j8Xg76nUviGrgtNl1NlV6Dc4ZIgtk2gx+x7TdY8YAhYotACrhDnE+VUeHo8wFNQESx4Mn7nr4r6LwSYkuRZOS50ucBl0QQh/lA1nnadi+X+jGrU7D4c3gAVhlrS544+jyQjc405UOC7BN0X4ItV8Q9xp6RsTwqYY/DlgXdB3Ld6Tae+LTJHxZ8lZNnLYXOEkkWX3EJ1R5JzzTpX6+N8bH1Ojt+pOqegKEDbRxtN/F0WcRX4KoPm+TkwSiPADiGVwY6B+kcCV0Emtqxsvu7h48SOHGuzp8DvS6i1ytuv+L2G7o/otf/s0kemIJclPiiIbveObeYsRq3MFMsKDEcqfone/g4g9vP1elTiu2HeAi4QbAD77bpfb+J+jeATbFTd5TtPxypd1SWMsxm6BxQXAcr6Lwn7yOujYT2aNU922Gf5Ohtn9f5Q4YehHjorTa787emDwJ8I/bS78vwFmhrhK7AD0scDsyk9+Ib2V4O+gv8p0uIDWgT0e7Wajj1eZ1KgR4/W2cy1vjVTSZ/AvhaOX1v4d8DqyGpI9L2/Nj/i31zjK4AiURiZB3sEJ337OkdbacwQIy3VP71s3UmJRw7anIMYF3jq/cX3WdAD9pGfNtCf2DCxn4pGaISohKvSM2klnSs8avpygCbJ4t+LKgJmBCDYuYItr6LBC0CyRq4liJxdJniMkOzCO7NNl87uhMuYCOFw+fq/D4mD+ECmgZcMiJNduRLZmRtBmhHgrbYdBl5DmkZsRFY8U6bnrjJ5M/TcN1Yda802KcFHD9bZ38RJBVCAaGs6fMfWU4XScxOZA20pkqVPo9YBa58v0nvmWbX18rp+4vuY0XO/67yL3fwSQ63nauzxwVXQFI5sqIkS39mtVvoesZyDc55XBrQ9IMmPTmtk2/EXrq36N8GvgX7NhIv3Fr1z0/rbL1On1FC6tC0xyU1IzcbvjlGnkQMEYAzTfLHacVvip26uwxvGHYxohcicj6g/w7EH45Uw9NTBfliu3gBOolsX9XXZusoJvgIZoaFL4mfnUC4KPHFu8ruNUGbSZ34VjATSCCUEalurppHP2qKP6hgjswbanB5PstmkkHXILnI97kjlEpeKj6PIEoWIr6NpG3L0DuCjUiTmj53ZIVjyAAcmYehFZIGLrUbrAzstIk5RutgK1wetliSEhcG+nZCW0KBDC2XhwOT5heXGQ+RZZ8ifU+ZRLbFUMtIfE/Tf8fKnAQtdlhh0sZdzcgFVABSBlvicJhp0dOzusKG68k1oOKwmNHZxgTEpmzmgebBFj8dcWZM5eqXfXf9nFwNOG+zv5//1vd/YT8BORc465+otLcAAAAASUVORK5CYII=');
  position: absolute;
  right: 10px;
  top: 10px;
  cursor: pointer;
}
</style>