<template>
  <!--
    AnalysisToolPanel 配套分析管理页左面板「工具箱」
    ==================================================================
    版式与地图管理页 MapToolPanel 完全同一套定尺（地图管理.html）：
      标题装饰 大标题-左 panel-title-left.png  (9, 95)    420×46
      标题文字 配套分析管理                     (83, 102) 18px #FFFFFF
      面板底   bg-模块-左 panel-left-bg.png    (30, 149)  400×881
      工具卡片 110×128，设计稿三列 × 四行（内容区 360 = 110×3 + 15×2）：
        卡片底   tool-card.png / 选中 tool-card-active.png
        图标     44×44 统一线性 SVG，卡片内 (33, 18)
        文字     14px 居中，卡片内 y=82；普通 #82C6FF / 选中 #FFFFFF

    ★ 宽度不再写死 400：面板宽由页面从 panelLayout 用 CSS 变量
      （--panel-left-w）下发，卡片改用 flex 换行自动重排列数 ——
      拖宽时不会出现「右边空一条」或「卡片溢出」。

    功能清单来自方案 2.3.1（四）11 项（与 jeecg-module-land 的 analysis 域
    子包一一对应）；图标与地图管理共用 ScreenIcon 的线性几何规范，
    不再混用旧系统的彩色填充图片。

    与 MapToolPanel 不同：管线统计、标注管理已接插件引擎，其余工具的
    功能面板尚未接入 stargis-function，因此不做本地高亮兜底——点击统一
    emit('toggle-tool', key)，选中态由父容器 activeTool 统一决定
    （与地图管理 WIRED_KEYS 口径一致）。
  -->
  <div class="analysis-tools">
    <!-- 面板标题 -->
    <img class="analysis-tools__title-deco" :src="hf.panelTitleLeft" alt="" aria-hidden="true" />
    <h2 class="analysis-tools__title">配套分析管理</h2>

    <!-- 面板主体 -->
    <section class="analysis-tools__panel stage-hit">
      <button
        v-for="tool in tools"
        :key="tool.key"
        type="button"
        class="analysis-tools__card"
        :class="{ 'is-active': tool.key === activeTool }"
        :aria-pressed="String(tool.key === activeTool)"
        @click="$emit('toggle-tool', tool.key)"
      >
        <img class="analysis-tools__card-bg" :src="hf.toolCard" alt="" aria-hidden="true" />
        <img class="analysis-tools__card-bg analysis-tools__card-bg--on" :src="hf.toolCardActive" alt="" aria-hidden="true" />
        <screen-icon class="analysis-tools__icon" :name="tool.icon" :size="44" :stroke-width="1.5" />
        <span class="analysis-tools__label">{{ tool.label }}</span>
      </button>
    </section>
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'
import ScreenIcon from '@/components/screen/ScreenIcon.vue'

/**
 * 配套分析 11 项（方案 2.3.1（四）），行优先排列。
 * ★ key 同时是 analysis/index.vue 右面板切换与后续功能引擎接入的约定值，
 *   与 jeecg-module-land analysis 域子包名保持一致（label/pipeline/section…）。
 */
export const ANALYSIS_TOOLS = [
  { key: 'label', label: '标注管理', icon: 'analysis-label' },
  { key: 'pipeline', label: '管线统计', icon: 'analysis-pipeline' },
  { key: 'sectionH', label: '横断面分析', icon: 'analysis-section-h' },
  { key: 'sectionV', label: '纵断面分析', icon: 'analysis-section-v' },
  { key: 'clearance', label: '净距分析', icon: 'analysis-clearance' },
  { key: 'cover', label: '覆土分析', icon: 'analysis-cover' },
  { key: 'flow', label: '流向分析', icon: 'analysis-flow' },
  { key: 'burst', label: '爆管分析', icon: 'analysis-burst' },
  { key: 'excavation', label: '开挖分析', icon: 'analysis-excavation' },
  { key: 'maturity', label: '成熟度分析', icon: 'analysis-maturity' },
  { key: 'estimate', label: '配套估算', icon: 'analysis-estimate' }
]

export default {
  name: 'AnalysisToolPanel',
  components: { ScreenIcon },
  props: {
    /**
     * 当前激活的工具 key（'' = 无）。
     * 选中态全部由父容器统一管理，本组件不做本地高亮。
     */
    activeTool: { type: String, default: '' }
  },
  data () {
    return {
      hf,
      tools: ANALYSIS_TOOLS
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.analysis-tools {
  /*
   * 定位根：铺满画布（内部仍是设计稿绝对坐标，一条都不用改）。
   * ★ 必须 pointer-events:none —— 铺满画布的容器层开 auto 会让地图点不到、
   *   拖不动（AGENTS §3.6 第 4 条）；交互由面板自身的 .stage-hit 打开。
   * ★ 折叠位移不在这里：父组件把整块包装层整体滑出屏幕（analysis/index.vue
   *   的 __left），所以本组件不必知道自己折没折叠。
   */
  position: absolute;
  inset: 0;
  pointer-events: none;

  &__title-deco {
    position: absolute;
    left: 9px;
    top: 95px;
    // 设计稿 420 = 面板 400 + 20（面板 left 30、装饰 left 9）：面板可拖宽，装饰跟着伸缩
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
    // 宽度由 panelLayout 下发（--panel-left-w，拖动内侧拖动条改变），底图随宽度拉伸
    width: var(--panel-left-w, 400px);
    /*
     * 卡片改用 flex 换行（原先是 JS 逐张算绝对坐标）：面板拖宽 / 拖窄时列数
     * 要自己重排 —— 400 宽时内容区 360 = 110×3 + 15×2，正好与设计稿三列一致；
     * 更宽自动变 4 列、更窄变 2 列，不必再维护一套坐标表（改宽度就得改表，
     * 那正是旧写法的病根）。padding 上 30 / 左右 20 = 设计稿卡片起点 (20, 面板内 30)。
     * ★ box-sizing:border-box 必须显式写：本工程没有全局 border-box 重置，
     *   content-box 下 width + 左右各 20 的内边距会溢出面板底图 40px。
     */
    box-sizing: border-box;
    padding: 30px 20px 20px;
    display: flex;
    flex-wrap: wrap;
    // 行距 11 = 设计稿卡片步进 139 − 卡片高 128（与地图管理工具面板同一口径），
    // 列距仍是 15：110×3 + 15×2 = 360 内容宽，正好与设计稿三列一致。
    column-gap: 15px;
    row-gap: 11px;
    align-content: flex-start;
    // 拖宽后面板宽度不一定正好是 110×n+15×(n-1)：居中排，右侧不留一条空边
    justify-content: center;
    overflow-y: auto;
    overflow-x: hidden;
    background-image: url('~@/assets/screen-blue/panel-left-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;
  }

  &__card {
    // 定宽不伸缩：110×3 + 15×2 正好铺满 360 内容区，拉伸会破坏设计稿比例
    position: relative;
    flex: 0 0 110px;
    width: 110px;
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

  // 选中态底图：默认隐藏，卡片激活时与普通底图互换（两张都常驻，避免切换闪烁）
  &__card-bg--on {
    display: none;
  }

  &__card.is-active &__card-bg {
    display: none;
  }

  &__card.is-active &__card-bg--on {
    display: block;
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
