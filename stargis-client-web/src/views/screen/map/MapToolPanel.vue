<template>
  <!--
    MapToolPanel 地图管理页左面板「工具箱」（高保真还原）
    ==================================================================
    定尺取自高保真模型 地图管理.html：
      标题装饰 大标题-左_u21.png   (9, 95)    420×46
      标题文字 地图管理             (83, 100)  18px #FFFFFF
      面板底   bg-模块-左_u23.png  (30, 149)  400×881
      工具卡片 110×128，三列（x=50/175/300）、六行（y=179 起步进 139）：
        卡片底   tool-card.png / 选中 tool-card-active.png
        图标     44×44 统一线性 SVG，卡片内 (33, 18)
        文字     14px 居中，卡片内 y=82；普通 #82C6FF / 选中 #FFFFFF

    卡片选中态分两类：
      · WIRED_KEYS 里的工具（已接入插件/面板）——选中态由父容器的
        activeTool 统一决定（插件引擎可能被其它工具经总线间接取消），
        点击统一 emit('toggle-tool', key) 交给父组件分发；
      · 其余（如高清出图）——本地高亮，点击 emit('toggle-hd') 由父组件提示。

    ★ 2026-11 起卡片不再按设计稿逐张绝对定位（原来由 cardStyle() 算
      left/top），改成面板内 flex 换行：左面板现在可以拖宽（400 → 340~560），
      写死 x=20/145/270 会在变宽后右边空一大片、变窄时直接溢出。
      400 宽时内容区 360 = 110×3 + 15×2，与设计稿三列完全一致。
  -->
  <div class="map-tools">
    <!-- 面板标题 -->
    <img class="map-tools__title-deco" :src="hf.panelTitleLeft" alt="" aria-hidden="true" />
    <h2 class="map-tools__title">地图管理</h2>

    <!-- 面板主体 -->
    <section class="map-tools__panel stage-hit">
      <button
        v-for="(tool, index) in tools"
        :key="tool.key"
        type="button"
        class="map-tools__card"
        :class="{ 'is-active': isCardActive(index, tool) }"
        :aria-pressed="String(isCardActive(index, tool))"
        @click="handleCardClick(index, tool)"
      >
        <img class="map-tools__card-bg" :src="isCardActive(index, tool) ? hf.toolCardActive : hf.toolCard" alt="" aria-hidden="true" />
        <screen-icon class="map-tools__icon" :name="tool.icon" :size="44" :stroke-width="1.5" />
        <span class="map-tools__label">{{ tool.label }}</span>
      </button>
    </section>
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'
import ScreenIcon from '@/components/screen/ScreenIcon.vue'

/** 已接入插件/面板的工具 key：卡片选中态跟随父容器的 activeTool */
const WIRED_KEYS = [
  'pick', 'locateName', 'locateRoad', 'querySpatial', 'queryAttr',
  'queryCombined', 'statistics', 'measureLine', 'measureHorizontal',
  'measureVertical', 'measureArea', 'bookmark', 'tour', 'hdExport',
  'terrainHeight', 'underground', 'layers', 'terrainAlpha'
]

export default {
  name: 'MapToolPanel',
  components: { ScreenIcon },
  props: {
    /**
     * 当前激活的工具 key（'' = 无）。
     * 已接入工具的选中态全部由父容器统一管理，本地不再维护。
     */
    activeTool: { type: String, default: '' }
  },
  data () {
    return {
      hf,
      /** 本地选中：全部 18 个工具均已接入，选中态由父容器统一管理 */
      activeIndex: -1,
      /** 18 个工具，按设计稿行优先排列；icon 对应 ScreenIcon 的业务图标名 */
      tools: [
        { key: 'pick', label: '拾取查询', icon: 'map-pick' },
        { key: 'locateName', label: '地名定位', icon: 'map-locate-name' },
        { key: 'locateRoad', label: '道路定位', icon: 'map-locate-road' },
        { key: 'querySpatial', label: '空间查询', icon: 'map-query-spatial' },
        { key: 'queryAttr', label: '属性查询', icon: 'map-query-attr' },
        { key: 'queryCombined', label: '综合查询', icon: 'map-query-combined' },
        { key: 'statistics', label: '综合统计', icon: 'map-statistics' },
        { key: 'measureLine', label: '直线量测', icon: 'map-measure-line' },
        { key: 'measureHorizontal', label: '水平量测', icon: 'map-measure-horizontal' },
        { key: 'measureVertical', label: '垂直量测', icon: 'map-measure-vertical' },
        { key: 'measureArea', label: '面积量测', icon: 'map-measure-area' },
        { key: 'bookmark', label: '视图书签', icon: 'map-bookmark' },
        { key: 'tour', label: '动画导航', icon: 'map-tour' },
        { key: 'hdExport', label: '高清出图', icon: 'map-hd-export' },
        { key: 'terrainHeight', label: '开启高程', icon: 'map-terrain-height' },
        { key: 'underground', label: '开启地下模式', icon: 'map-underground' },
        { key: 'layers', label: '图层管理', icon: 'map-layers' },
        { key: 'terrainAlpha', label: '地形透明', icon: 'map-terrain-alpha' }
      ]
    }
  },
  methods: {
    isCardActive (index, tool) {
      if (WIRED_KEYS.indexOf(tool.key) > -1) return tool.key === this.activeTool
      return index === this.activeIndex
    },
    handleCardClick (index, tool) {
      if (WIRED_KEYS.indexOf(tool.key) > -1) {
        // 已接入工具：统一交给父组件按 key 分发
        this.$emit('toggle-tool', tool.key)
        return
      }
      this.activeIndex = index
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.map-tools {
  /*
   * 铺满画布的定位根：子元素仍是设计稿的绝对坐标（面板 left:30 / top:149），
   * 而本组件现在被页面的 .map-screen__left 包着（折叠时整块滑出屏幕），
   * 定位基准必须与改前一致（都是整块画布），所以根节点自己撑满画布。
   * ★ 铺满画布的容器层必须 pointer-events: none（AGENTS §3.6 第 4 条），
   *   交互由面板的 .stage-hit 打开。
   */
  position: absolute;
  inset: 0;
  pointer-events: none;

  &__title-deco {
    position: absolute;
    left: 9px;
    top: 95px;
    // 标题装饰比面板宽 20（设计稿 400 → 420）；面板可拖宽，跟着走
    width: calc(var(--panel-left-w, 400px) + 20px);
    height: 46px;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    left: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__panel {
    position: absolute;
    left: 30px;
    // 上贴 149、下贴 50：与首页左面板同一套画布坐标
    top: 149px;
    bottom: 50px;
    // 宽度由页面根的 --panel-left-w 下发（可拖动 340~560），不再写死 400
    width: var(--panel-left-w, 400px);
    // padding 不吃宽度：内容区 = 400 - 20×2 = 360 = 110×3 + 15×2（与设计稿三列一致）
    box-sizing: border-box;
    padding: 30px 20px 20px;
    // 卡片改 flex 换行（原来是逐张绝对定位）：面板拖宽 / 拖窄都自动重排。
    // 18 张卡片在 400 宽时正好 3 列 × 6 行；多出来的高度走纵向滚动。
    // ★ 行距取 11（= 设计稿卡片步进 139 − 卡片高 128），不是 15：
    //   6 行 × 128 + 5 × 11 = 823，加 padding 50 = 873 < 面板 881，默认零滚动；
    //   列距仍是 15（110×3 + 15×2 = 360 内容宽，与设计稿三列一致）。
    display: flex;
    flex-wrap: wrap;
    column-gap: 15px;
    row-gap: 11px;
    align-content: flex-start;
    // 拖宽后面板宽度不一定正好是 110×n+15×(n-1)：居中排，右侧不留一条空边
    justify-content: center;
    overflow-y: auto;
    overflow-x: hidden;
    /*
     * 隐藏滚动条本体（滚轮照常可滚）：
     * 拖窄到 340 时卡片掉成两列（9 行）、高度必然超出面板，需要滚动；
     * 而 Windows Chrome 的经典滚动条占 15px 宽，会把内容区从 360 挤到 345，
     * 三列（110×3 + 15×2 = 360）当场掉成两列 —— 与设计稿不符。
     * 大屏上隐藏滚动条本来也是常规做法，拖窄后仍可用滚轮纵向浏览。
     */
    scrollbar-width: none;

    &::-webkit-scrollbar {
      width: 0;
      height: 0;
    }

    background-image: url('~@/assets/screen-blue/panel-left-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;
  }

  &__card {
    // 相对定位：卡片底图 / 图标 / 文字仍是卡片内的绝对坐标（设计稿 110×128）
    position: relative;
    flex: 0 0 110px;
    height: 128px;
    padding: 0;
    background: transparent;
    border: 0;
    cursor: pointer;
    .screen-focus-ring();
  }

  &__card-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 110px;
    height: 128px;
    display: block;
    pointer-events: none;
  }

  &__icon {
    position: absolute;
    left: 33px;
    top: 18px;
    width: 44px;
    height: 44px;
    color: var(--screen-accent);
    pointer-events: none;
  }

  &__label {
    position: absolute;
    left: 0;
    top: 82px;
    width: 100%;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: var(--screen-accent);
    text-align: center;
    white-space: nowrap;
  }

  &__card.is-active &__icon,
  &__card.is-active &__label {
    color: var(--screen-text);
  }
}
</style>
