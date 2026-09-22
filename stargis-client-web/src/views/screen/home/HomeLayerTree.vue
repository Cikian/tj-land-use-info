<template>
  <!--
    HomeLayerTree 首页右面板「地图管理」图层树（高保真还原）
    ==================================================================
    定尺取自高保真模型 首页.html：
      标题装饰 大标题-右_u24.png   (1491, 95)   420×46
      标题文字 地图管理（右对齐）    (1763, 104)  18px #FFFFFF
      面板底   panel-right-bg.png  (1490, 149)  400×881
      检索框   field-search.svg    (1510, 169)  360×28
      树底     tree-bg.png         (1510, 218)  360×782
      树行     高 36，步进 42；开关 14×14 固定在最右 x=326（树内坐标）
  -->
  <div class="home-tree">
    <img class="home-tree__title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
    <h2 class="home-tree__title">地图管理</h2>

    <section class="home-tree__panel stage-hit">
      <!-- 检索框 -->
      <div class="home-tree__search">
        <img class="home-tree__search-bg" :src="hf.fieldSearch" alt="" aria-hidden="true" />
        <input
          v-model="keyword"
          class="home-tree__search-input"
          type="text"
          placeholder="请输入关键字"
          aria-label="图层检索"
        />
        <img class="home-tree__search-icon" :src="hf.fieldSearchIcon" alt="" aria-hidden="true" />
      </div>

      <!-- 图层树 -->
      <div class="home-tree__body">
        <img class="home-tree__body-bg" :src="hf.treeBg" alt="" aria-hidden="true" />
        <div
          v-for="(node, index) in visibleNodes"
          :key="node.id"
          class="home-tree__row"
          :class="{ 'is-selected': node.id === activeId, 'is-expanded': node.expanded }"
          :style="{ top: `${10 + index * 42}px` }"
          @click="activeId = node.id"
        >
          <img
            v-if="node.id === activeId"
            class="home-tree__row-bg"
            :src="hf.treeRow"
            alt=""
            aria-hidden="true"
          />
          <!-- 展开三角：level 0 / 1 的分组行才有（高保真里二级子级没有三角） -->
          <img
            v-if="node.level <= 1"
            class="home-tree__caret"
            :src="hf.treeCaret"
            :style="{ left: `${node.level === 0 ? 30 : 40}px` }"
            alt=""
            aria-hidden="true"
            @click.stop="toggleExpand(node)"
          />
          <!-- 类型图标：level 0 是文件夹，level 1 / 2 是图层图标（选中行用高亮版） -->
          <img
            v-if="node.level === 0"
            class="home-tree__folder"
            :src="hf.treeFolder"
            alt=""
            aria-hidden="true"
          />
          <img
            v-else
            class="home-tree__layer"
            :src="node.id === activeId ? hf.treeLayerActive : hf.treeLayer"
            :style="{ left: `${node.level === 1 ? 60 : 70}px` }"
            alt=""
            aria-hidden="true"
          />
          <span class="home-tree__label" :style="{ left: `${74 + node.level * 10}px` }">
            {{ node.label }}
          </span>
          <img
            class="home-tree__switch"
            :src="node.visible ? hf.treeSwitchOn : hf.treeSwitchOff"
            alt=""
            :aria-label="`${node.label} ${node.visible ? '已显示' : '已隐藏'}`"
            role="button"
            tabindex="0"
            @click.stop="toggleVisible(node)"
            @keydown.enter.prevent="toggleVisible(node)"
            @keydown.space.prevent="toggleVisible(node)"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'
import { demoLayerTree } from '../mock'

export default {
  name: 'HomeLayerTree',
  data () {
    return {
      hf,
      keyword: '',
      /** 当前选中的图层节点（高保真默认选中第二个「一级子级」） */
      activeId: 'l1-1',
      /**
       * 高保真底稿里「一级子级」的展开三角指向右侧（收起态）却仍然展示了子级，
       * 属 Axure 静态渲染的不一致。这里默认展开 level 0 / 1，
       * 保证三角方向与可见的子级一致（内容与设计稿完全相同）。
       */
      nodes: demoLayerTree.map((node) => ({ ...node, expanded: node.level <= 1 })),
    }
  },
  computed: {
    /** 折叠父节点后隐藏其后代（按 level 判定，数据保持扁平） */
    visibleNodes () {
      const list = []
      let hiddenBelow = null
      this.nodes.forEach((node) => {
        if (hiddenBelow !== null && node.level > hiddenBelow) return
        hiddenBelow = null
        if (this.keyword && node.label.indexOf(this.keyword) === -1) return
        list.push(node)
        // 只有带展开三角的分组（level 0 / 1）才会折叠后代
        if (node.level <= 1 && !node.expanded) hiddenBelow = node.level
      })
      return list
    },
  },
  methods: {
    toggleExpand (node) {
      if (node.level > 1) return
      node.expanded = !node.expanded
    },
    toggleVisible (node) {
      node.visible = !node.visible
      this.$emit('layer-visibility-change', node)
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.home-tree {
  &__title-deco {
    position: absolute;
    // 设计稿 x=1491，即距右边界 429px（自适应：右侧锚定）
    right: 429px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    // 设计稿标题文字右缘止于 x=1835，即距右边界 85px
    right: 85px;
    top: 104px;
    width: 72px;
    margin: 0;
    font-size: 18px;
    font-weight: 500;
    line-height: 18px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  &__panel {
    position: absolute;
    right: 30px;
    top: 149px;
    // 定尺高度：纵向多出来的空间留给地图，避免切图被拉伸变形
    height: 881px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;
  }

  /* ---------------- 检索框 ---------------- */
  &__search {
    position: absolute;
    left: 20px;
    right: 20px;
    top: 20px;
    height: 28px;
  }

  &__search-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 28px;
    display: block;
    pointer-events: none;
  }

  &__search-input {
    position: absolute;
    left: 20px;
    top: 0;
    width: 290px;
    height: 28px;
    padding: 0;
    font-family: inherit;
    font-size: 14px;
    line-height: 28px;
    color: #ffffff;
    background: transparent;
    border: 0;
    outline: none;

    &::placeholder {
      color: #4a7396;
    }
  }

  &__search-icon {
    position: absolute;
    left: 326px;
    top: 7px;
    width: 14px;
    height: 14px;
    display: block;
    pointer-events: none;
  }

  /* ---------------- 图层树 ---------------- */
  &__body {
    position: absolute;
    left: 20px;
    top: 69px;
    right: 20px;
    // 设计稿 881 - 69 - 782 = 30
    bottom: 30px;
    overflow: hidden;
  }

  &__body-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    display: block;
    pointer-events: none;
  }

  &__row {
    position: absolute;
    left: 0;
    width: 100%;
    height: 36px;
  }

  &__row-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 36px;
    display: block;
    pointer-events: none;
  }

  /* 展开三角：8×6，收起时旋转 -90° 指向右侧（高保真收起态即此形态） */
  &__caret {
    position: absolute;
    top: 15px;
    width: 8px;
    height: 6px;
    cursor: pointer;
    transition: transform var(--screen-duration) var(--screen-ease);
  }

  &__row:not(.is-expanded) &__caret {
    transform: rotate(-90deg);
  }

  /* 文件夹图标：15×14，仅 level 0 */
  &__folder {
    position: absolute;
    left: 49px;
    top: 11px;
    width: 15px;
    height: 14px;
  }

  /* 图层类型图标：14×14，level 1 / 2 */
  &__layer {
    position: absolute;
    top: 11px;
    width: 14px;
    height: 14px;
  }

  &__label {
    position: absolute;
    top: 11px;
    font-size: 14px;
    font-weight: 500;
    line-height: 14px;
    color: #82c6ff;
    white-space: nowrap;
  }

  &__switch {
    position: absolute;
    // 设计稿 x=1836，即距面板（400 宽）右边界 64px
    right: 60px;
    top: 11px;
    width: 14px;
    height: 14px;
    cursor: pointer;
    .screen-focus-ring();
  }
}
</style>
