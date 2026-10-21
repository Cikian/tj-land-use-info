<template>
  <!--
    AnalysisToolPanel 配套分析管理页左面板「工具箱」
    ==================================================================
    版式与地图管理页 MapToolPanel 完全同一套定尺（地图管理.html）：
      标题装饰 大标题-左 panel-title-left.png  (9, 95)    420×46
      标题文字 配套分析管理                     (83, 102) 18px #FFFFFF
      面板底   bg-模块-左 panel-left-bg.png    (30, 149)  400×881
      工具卡片 110×128，三列（x=50/175/300）、四行（y=179 起步进 139）：
        卡片底   tool-card.png / 选中 tool-card-active.png
        图标     68×54 槽位居中 40×40（卡片内 (35, 18)）
        文字     14px 居中，卡片内 y=82；普通 #82C6FF / 选中 #FFFFFF

    功能清单来自方案 2.3.1（四）11 项（与 jeecg-module-land 的 analysis 域
    子包一一对应）；图标取自旧工作台配套分析（旧库 stargis_menu 的
    功能 → 图标映射），配色按现行系统工具图标规则重上色，见
    assets/screen-blue/index.js 的「配套分析管理页」一节。

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
        v-for="(tool, index) in tools"
        :key="tool.key"
        type="button"
        class="analysis-tools__card"
        :class="{ 'is-active': tool.key === activeTool }"
        :style="cardStyle(index)"
        :aria-pressed="String(tool.key === activeTool)"
        @click="$emit('toggle-tool', tool.key)"
      >
        <img class="analysis-tools__card-bg" :src="hf.toolCard" alt="" aria-hidden="true" />
        <img class="analysis-tools__card-bg analysis-tools__card-bg--on" :src="hf.toolCardActive" alt="" aria-hidden="true" />
        <img class="analysis-tools__icon" :src="tool.icon" alt="" aria-hidden="true" />
        <span class="analysis-tools__label">{{ tool.label }}</span>
      </button>
    </section>
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'

/** 三列卡片的左边缘（设计稿 x=50/175/300，卡片 110 宽、列间隙 15） */
const CARD_LEFT = [20, 145, 270]
/** 卡片纵向：设计稿 y=179 起步进 139，换算成面板内坐标（面板顶 149） */
const CARD_TOP = 30
const CARD_PITCH = 139

/**
 * 配套分析 11 项（方案 2.3.1（四）），行优先排列。
 * ★ key 同时是 analysis/index.vue 右面板切换与后续功能引擎接入的约定值，
 *   与 jeecg-module-land analysis 域子包名保持一致（label/pipeline/section…）。
 */
export const ANALYSIS_TOOLS = [
  { key: 'label', label: '标注管理', icon: hf.analysisLabel },
  { key: 'pipeline', label: '管线统计', icon: hf.analysisPipeline },
  { key: 'sectionH', label: '横断面分析', icon: hf.analysisSectionH },
  { key: 'sectionV', label: '纵断面分析', icon: hf.analysisSectionV },
  { key: 'clearance', label: '净距分析', icon: hf.analysisClearance },
  { key: 'cover', label: '覆土分析', icon: hf.analysisCover },
  { key: 'flow', label: '流向分析', icon: hf.analysisFlow },
  { key: 'burst', label: '爆管分析', icon: hf.analysisBurst },
  { key: 'excavation', label: '开挖分析', icon: hf.analysisExcavation },
  { key: 'maturity', label: '成熟度分析', icon: hf.analysisMaturity },
  { key: 'estimate', label: '配套估算', icon: hf.analysisEstimate }
]

export default {
  name: 'AnalysisToolPanel',
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
  },
  methods: {
    cardStyle (index) {
      const row = Math.floor(index / 3)
      const col = index % 3
      return { left: `${CARD_LEFT[col]}px`, top: `${CARD_TOP + row * CARD_PITCH}px` }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.analysis-tools {
  &__title-deco {
    position: absolute;
    left: 9px;
    top: 95px;
    width: 420px;
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
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-left-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;
  }

  &__card {
    position: absolute;
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
    left: 35px;
    top: 18px;
    width: 40px;
    height: 40px;
    object-fit: contain;
    display: block;
    pointer-events: none;
  }

  &__label {
    position: absolute;
    left: 0;
    top: 82px;
    width: 100%;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: #82c6ff;
    text-align: center;
    white-space: nowrap;
  }

  &__card.is-active &__label {
    color: #ffffff;
  }
}
</style>
