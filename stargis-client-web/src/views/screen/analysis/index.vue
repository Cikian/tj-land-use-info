<template>
  <!--
    配套分析管理页
    ==================================================================
    版式与地图管理页同一家族：真实地图保持可见、可交互（容器不开启
    pointer-events、不加 stage-hit，各面板自行开启），画布上叠两块 UI：
      ├─ 左面板  AnalysisToolPanel（工具箱：方案 2.3.1（四）11 项分析工具）
      └─ 底部    「属性表」收起态横条（与首页 / 地图管理同一条底 bar）

    右面板：激活任一工具时显示与地图管理同位的面板壳（标题装饰 + 工具名），
    .analysis-screen__host 是各功能面板的统一接入点。

    已接入功能（模式与地图管理页一致：右面板宿主渲染功能面板）：
      · 管线统计 pipeline —— 引擎 <pipe-statistics> 页面级挂载（查询表单
        在引擎组件内部，用 __engine-host 包裹：闲置藏 / 激活显）；
        五个统计结果弹窗（属性 / 管径 / 材质 / 管径分布 / 埋深分布）在
        __host 内挂载。弹窗是自含组件，内部自行监听以配置键命名的总线事件
        （xzanalyse / gjanalyse / czanalyse / gjfdanalyse / msfdanalyse）
        显隐，宿主只喂 configMin——挂法照抄老工作台 HomePipeBody。
        配置来自中台功能配置 sysConfig['PipeManager']（按 componentname
        摊平），没配置时点卡片只提示不进模式（同地图管理道路定位兜底）。
      · 标注管理 label —— 三枚 road 式引擎隐藏挂载（管线标注 <pipe-mark> /
        坐标标注 <coordinate-label> / 清除标注 <delete-label>，btnClick 进 /
        退），面板里放四枚自绘功能按钮；管线标注 / 坐标标注的表单弹窗
        （PipeMarkPop / CoordinateLabelPop）在 __host 内挂载，由引擎经总线
        拉起。清除标注 = 一键清扫：先清自绘埋深标注，再触发 DeleteLabel
        引擎（插件自带「确定清扫全部？」确认框）。
        配置来自 sysConfig['PipeManager'] + sysConfig['Common'] 两个模块
        （坐标标注在 Common 家族），一并摊平。
      · 埋深标注 depthLabel —— **页面自实现**（参照旧系统 soilLabel.js：
        插件这版没打包 DepthLabel 组件，混淆字符串表解码已核实）。进入
        拾取模式后点管线 → scene.pick 读 QSMS / ZZMS 属性 → 取折线首末
        顶点（取不到退化拾取点）画「起点埋深 / 终点埋深」两个 Cesium
        文字标注，实体 id 记入 _depthLabels；「清除标注」按钮先清这批
        自绘标注，再切插件的删除模式删插件标注。
      · 断面分析 sectionH / sectionV —— 插件 SectionalAnalysis 引擎
        （与管线统计同族，同挂 __engine-host）。横断 / 纵断是同一组件的
        内建双模式：卡片 → 引擎 btnClick（弹出模式清单）→ 经内嵌
        PopSectionList 弹层 startDraw 直发对应模式 → 地图画线 → POST
        VUE_DATA_JAVA_URL/pipeline/drawHSection 出图 → 结果浮层
        .pop.bottom-table（1024×720，右下，自带导出 / 关闭）。出图接口
        依赖部署侧 /pipeline/* 服务族（真机验证项）。

    待接入（爆管 / 开挖 / 流向 / 净距 / 覆土等）：引擎与弹窗组件
    同样全局注册自 stargis-function，按同款模式逐个补，写法参照本页
    pipeline / label 分支与 map/index.vue 的 PLUGIN_TOOLS 注册表。
  -->
  <div class="analysis-screen">
    <analysis-tool-panel :active-tool="activeTool" @toggle-tool="toggleToolByKey" />

    <!--
      管线统计引擎（stargis-function 全局组件，老工作台工具栏同款）：
      与 road 类引擎不同，管线统计的查询表单在引擎组件内部（插件清单里
      只有 4 个结果弹窗、没有表单弹窗），因此不能整体 v-show="false"——
      用 __engine-host 包一层：未激活时藏（闲置态只剩按钮壳），激活后
      显示，让引擎自己画的表单/面板落在右面板框里。btnClick() 进 / 退
      统计模式，checked / cancelChecked() 是状态契约；激活前引擎会调
      $parent.cancelAll() 做互斥（见本页 cancelAll）。没配数据源不挂载。
    -->
    <div
      v-if="pipeConfig || sectionConfig"
      ref="engineHost"
      class="analysis-screen__engine-host stage-hit"
      :class="{ 'is-idle': !pipeActive }"
    >
      <pipe-statistics v-if="pipeConfig" ref="pipeStatisticsEngine" :config-min="pipeConfig" />
      <sectional-analysis v-if="sectionConfig" ref="sectionalEngine" :config-min="sectionConfig" />
    </div>

    <!--
      标注管理的三枚引擎（stargis-function 全局组件，road 式按钮引擎）：
      表单在各自的弹窗组件里（PipeMarkPop / CoordinateLabelPop；清除标注
      无弹窗、直接在地图上点选），因此可以整体 v-show="false" 隐藏。
      btnClick() 进 / 退对应标注模式，checked / cancelChecked() 是状态
      契约；激活前引擎会调 $parent.cancelAll() 做互斥（见本页 cancelAll）。
      没配数据源不挂载（弹窗 created 直读 configMin，缺了会崩）。
    -->
    <pipe-mark
      v-if="pipePopConfig.PipeMark"
      v-show="false"
      ref="pipeMarkEngine"
      :config-min="pipePopConfig.PipeMark"
      aria-hidden="true"
    />
    <coordinate-label
      v-if="pipePopConfig.CoordinateLabel"
      v-show="false"
      ref="coordinateLabelEngine"
      :config-min="pipePopConfig.CoordinateLabel"
      aria-hidden="true"
    />
    <delete-label
      v-if="pipePopConfig.DeleteLabel"
      v-show="false"
      ref="deleteLabelEngine"
      :config-min="pipePopConfig.DeleteLabel"
      aria-hidden="true"
    />
    <!--
      纵断面分析 = ProfileAnalysis（剖面分析，SpatialAnalysis 家族）：
      road 式引擎 + ProfileAnalysisPop（带「绘制 / 清除」操作按钮的弹窗，
      drawProfile / cleanProfile）。配置在 sysConfig['SpatialAnalysis']。
    -->
    <profile-analysis
      v-if="profileConfig"
      v-show="false"
      ref="profileAnalysisEngine"
      :config-min="profileConfig"
      aria-hidden="true"
    />
    <!--
      净距分析 = DistanceAnalysis（PipeManager 家族）：road 式引擎 +
      DistanceAnalysisPop 结果弹窗（showDistancePop 总线事件开合），
      拾取两根管线后出净距分析结果。配置键 DistanceAnalysis。
    -->
    <distance-analysis
      v-if="pipePopConfig.DistanceAnalysis"
      v-show="false"
      ref="clearanceEngine"
      :config-min="pipePopConfig.DistanceAnalysis"
      aria-hidden="true"
    />
    <!--
      覆土分析 = CoverageAnalysis（PipeManager 家族）：road 式引擎 +
      CoverageAnalysisPop 结果弹窗（showCoveragePop 总线事件开合），
      拾取管线后出覆土分析结果。配置键 CoverageAnalysis。
    -->
    <coverage-analysis
      v-if="pipePopConfig.CoverageAnalysis"
      v-show="false"
      ref="coverEngine"
      :config-min="pipePopConfig.CoverageAnalysis"
      aria-hidden="true"
    />
    <!--
      流向分析 = FlowDirectionAnalysis（PipeManager 家族）：road 式引擎 +
      FlowDirectionAnalysisPop 结果弹窗（showFlowDirectionAnalysisPop
      总线事件开合），拾取管线后出流向分析结果。配置键 FlowDirectionAnalysis。
    -->
    <flowDirection-analysis
      v-if="pipePopConfig.FlowDirectionAnalysis"
      v-show="false"
      ref="flowEngine"
      :config-min="pipePopConfig.FlowDirectionAnalysis"
      aria-hidden="true"
    />
    <!--
      爆管分析 = BurstAnalysis（PipeManager 家族）：road 式引擎 +
      BurstAnalysisPop 结果弹窗（showBurstPop 总线事件开合），拾取爆管
      点后出关阀方案与影响分析。配置键 BurstAnalysis。
    -->
    <burst-analysis
      v-if="pipePopConfig.BurstAnalysis"
      v-show="false"
      ref="burstEngine"
      :config-min="pipePopConfig.BurstAnalysis"
      aria-hidden="true"
    />
    <!--
      开挖分析 = ExcavationAnalysis（PipeManager 家族）：road 式引擎 +
      ExcavationAnalysisPop 结果弹窗（showExcavatePop / showExcatatePop
      两个事件名拼写并存，接入时都发）。配置键 ExcavationAnalysis。
    -->
    <excavation-analysis
      v-if="pipePopConfig.ExcavationAnalysis"
      v-show="false"
      ref="excavationEngine"
      :config-min="pipePopConfig.ExcavationAnalysis"
      aria-hidden="true"
    />

    <!-- 右面板壳：只负责标题与容器，功能面板由各工具分支填充 -->
    <template v-if="activeToolDef">
      <!-- 管线统计的引擎面板自带标题栏与关闭钮，画布层标题不再重复渲染 -->
      <img v-if="activeTool !== 'pipeline'" class="analysis-screen__title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
      <h2 v-if="activeTool !== 'pipeline'" class="analysis-screen__title">{{ activeToolDef.label }}</h2>
      <div
        class="analysis-screen__host stage-hit"
        :class="{ 'has-typestrip': (activeTool === 'pipeline' && pipeConfig) || activeTool === 'label' }"
      >
        <!-- ★ 管线统计：五个统计结果弹窗（自含，自行监听总线显隐）。
             弹窗无自己的配置就不挂载（v-if 守卫，同引擎口径）。 -->
        <template v-if="activeTool === 'pipeline'">
          <property-pop v-if="pipePopConfig.xzanalyse" :config-min="pipePopConfig.xzanalyse" />
          <diameter-pop v-if="pipePopConfig.gjanalyse" :config-min="pipePopConfig.gjanalyse" />
          <material-pop v-if="pipePopConfig.czanalyse" :config-min="pipePopConfig.czanalyse" />
          <diameterSect-pop v-if="pipePopConfig.gjfdanalyse" :config-min="pipePopConfig.gjfdanalyse" />
          <depthSect-pop v-if="pipePopConfig.msfdanalyse" :config-min="pipePopConfig.msfdanalyse" />
        </template>
        <!-- ★ 净距分析：DistanceAnalysisPop 结果弹窗（引擎拾取后经
             showDistancePop 拉起） -->
        <template v-else-if="activeTool === 'clearance'">
          <distance-analysis-pop v-if="pipePopConfig.DistanceAnalysis" :config-min="pipePopConfig.DistanceAnalysis" />
        </template>
        <!-- ★ 覆土分析：CoverageAnalysisPop 结果弹窗（引擎拾取后经
             showCoveragePop 拉起） -->
        <template v-else-if="activeTool === 'cover'">
          <coverage-analysis-pop v-if="pipePopConfig.CoverageAnalysis" :config-min="pipePopConfig.CoverageAnalysis" />
        </template>
        <!-- ★ 流向分析：FlowDirectionAnalysisPop 结果弹窗（引擎拾取后经
             showFlowDirectionAnalysisPop 拉起） -->
        <template v-else-if="activeTool === 'flow'">
          <flow-direction-analysis-pop v-if="pipePopConfig.FlowDirectionAnalysis" :config-min="pipePopConfig.FlowDirectionAnalysis" />
        </template>
        <!-- ★ 爆管分析：BurstAnalysisPop 结果弹窗（引擎拾取爆管点后经
             showBurstPop 拉起） -->
        <template v-else-if="activeTool === 'burst'">
          <burst-analysis-pop v-if="pipePopConfig.BurstAnalysis" :config-min="pipePopConfig.BurstAnalysis" />
        </template>
        <!-- ★ 开挖分析：ExcavationAnalysisPop 结果弹窗（引擎拾取后经
             showExcavatePop / showExcatatePop 拉起） -->
        <template v-else-if="activeTool === 'excavation'">
          <excavation-analysis-pop v-if="pipePopConfig.ExcavationAnalysis" :config-min="pipePopConfig.ExcavationAnalysis" />
        </template>
        <!-- ★ 标注管理：管线标注 / 坐标标注的表单弹窗（引擎 btnClick 经
             总线拉起）；清除标注无弹窗，直接在地图上点选删除。 -->
        <template v-else-if="activeTool === 'label'">
          <pipe-mark-pop v-if="pipePopConfig.PipeMark" :config-min="pipePopConfig.PipeMark" />
          <coordinate-label-pop v-if="pipePopConfig.CoordinateLabel" :config-min="pipePopConfig.CoordinateLabel" />
        </template>
        <!-- ★ 成熟度分析（预留面板）：图层开关列表来自 MATURITY_LAYERS
             配置点，图层显隐调用在 toggleMaturityLayer（TODO 接入点）；
             无图层时显示空态提示。 -->
        <template v-else-if="activeTool === 'maturity'">
          <div v-if="maturityLayers.length" class="analysis-screen__maturity-list">
            <div
              v-for="item in maturityLayers"
              :key="item.label"
              class="analysis-screen__maturity-row"
            >
              <span class="analysis-screen__maturity-label">{{ item.label }}</span>
              <button
                type="button"
                class="analysis-screen__type-btn"
                :class="{ 'is-active': activeMaturityLabel === item.label }"
                @click="toggleMaturityLayer(item)"
              >{{ activeMaturityLabel === item.label ? '隐藏图层' : '显示图层' }}</button>
            </div>
          </div>
          <div v-else class="analysis-screen__maturity-empty">暂无成熟度图层，待图层服务接入后在此配置</div>
        </template>
        <!-- ★ 配套估算（页面自实现，前端复刻旧系统 ptgsManager 区间公式）：
             拾取地块 → 读出让金 → 出配套费估算区间（出让金折半的
             35% ~ 65%），结果显示在本面板。 -->
        <template v-else-if="activeTool === 'estimate'">
          <div class="analysis-screen__estimate">
            <template v-if="estimateResult">
              <div class="analysis-screen__estimate-row">
                <span>出让宗地编号</span>
                <b>{{ estimateResult.crzdbh || '—' }}</b>
              </div>
              <div class="analysis-screen__estimate-row">
                <span>地块名称</span>
                <b>{{ estimateResult.dkmc || '—' }}</b>
              </div>
              <div class="analysis-screen__estimate-interval">
                <span>市政配套费估算区间</span>
                <b>{{ estimateResult.low }} ~ {{ estimateResult.high }}</b>
                <i>万元</i>
              </div>
              <p class="analysis-screen__estimate-note">按出让金折半的 35% ~ 65% 区间估算（旧系统同款公式）</p>
            </template>
            <div v-else class="analysis-screen__estimate-empty">请在地图上点击一个出让地块</div>
          </div>
        </template>
      </div>
    </template>

    <!--
      管线统计的五个类型入口 + 标注管理的四个功能入口（自绘按钮）。
      ★ 标注子模式进行中（activeLabelTool 非空）时容器隐藏：插件会弹出
        自己的编辑工具条（选管线/管点/管井 → 确定/重置/清扫），占据面板
        顶部同一区域，两套按钮会互相遮挡（真机已复现）；插件流程结束
        （引擎 checked 退 false，轮询感知）后按钮排布自动还原。
      ★ 容器必须 v-show 不能用 v-if：停靠区里的插件节点属于这个容器，
        v-if 切走时会把它们连同插件的事件监听一起销毁。
    -->
    <div
      v-show="(activeTool === 'pipeline' && pipeConfig) || activeTool === 'label'"
      class="analysis-screen__type-buttons stage-hit"
    >
      <!-- 管线统计：五个统计类型（直发总线事件开弹窗） -->
      <template v-if="activeTool === 'pipeline'">
        <button
          v-for="item in pipelineTypeLabels"
          :key="item.label"
          type="button"
          class="analysis-screen__type-btn"
          @click="openTypePop(item)"
        >{{ item.label }}</button>
      </template>
      <!-- 标注管理：四个标注功能（埋深标注组件缺失时点击 toast 说明）；
           激活中的功能高亮（activeLabelTool 跟随），清除标注是即时动作不高亮 -->
      <template v-else-if="activeTool === 'label'">
        <button
          v-for="item in labelToolButtons"
          :key="item.label"
          type="button"
          class="analysis-screen__type-btn"
          :class="{ 'is-active': activeLabelTool === item.key }"
          @click="toggleLabelTool(item)"
        >{{ item.label }}</button>
      </template>
      <div ref="pluginParking" class="analysis-screen__plugin-parking" aria-hidden="true"></div>
    </div>

    <!-- 底部「属性表」收起条（设计稿 (460,983) 1000×47，下贴 50；与地图管理同位） -->
    <section class="analysis-screen__attr stage-hit">
      <img class="analysis-screen__attr-bar" :src="hf.attrBar" alt="" aria-hidden="true" />
      <h3 class="analysis-screen__attr-title">属性表</h3>
      <img class="analysis-screen__attr-toggle" :src="hf.attrExpand" alt="" aria-hidden="true" />
    </section>
  </div>
</template>

<script>
import AnalysisToolPanel, { ANALYSIS_TOOLS } from './AnalysisToolPanel.vue'
import { hf } from '@/assets/screen-blue'

/**
 * ★ 成熟度分析预留配置点（插件无此组件，面板为页面自实现）：
 * 图层服务接入后，把成熟度分级图层填进这个数组，面板自动渲染开关列表——
 *   { label: '图层显示名', code: '图层服务里的图层标识' }
 * 开关的图层显隐调用在 toggleMaturityLayer（已留 TODO 接入点）。
 * 旧系统语义：成熟度按钮 = 互斥切换按成熟度等级分色的宗地专题图层
 * （showQueryData('11'/'19'/'20'/'21'/'25'/'26'/'28'…)），接入时按此填。
 */
const MATURITY_LAYERS = [
  // 示例：{ label: '成熟度一级地块', code: 'MATURE_LEVEL_1' },
]

export default {
  name: 'AnalysisScreen',
  components: { AnalysisToolPanel },
  data () {
    return {
      hf,
      /**
       * 当前激活的分析工具 key（'' = 无）。
       * 右面板壳跟随它切换；再次点击同一卡片 = 退出（与地图管理一致）。
       */
      activeTool: '',
      /**
       * 管线统计引擎配置（sysConfig['PipeManager'] → PipeStatistics 项）。
       * 没配置时为 null：管线统计卡片点击只提示，不挂载引擎
       * （统计弹窗 created 直读 configMin 字段，缺了会崩）。
       */
      pipeConfig: null,
      /** 管线统计五个弹窗各自的配置（componentname → 配置项），同一次摊平 */
      pipePopConfig: {},
      /** 管线统计是否处于统计模式（= 引擎 checked，随卡片开合同步） */
      pipeActive: false,
      /**
       * 横断面分析引擎配置（sysConfig['PipeManager'] → SectionalAnalysis 项）。
       * 没配置时为 null：横断面卡片点击只提示，不挂载引擎。
       */
      sectionConfig: null,
      /**
       * 纵断面分析引擎配置（sysConfig['SpatialAnalysis'] → ProfileAnalysis 项，
       * 老系统 sectionZ 的「绘制 / 清除」弹窗就是 ProfileAnalysisPop）。
       * 没配置时为 null：纵断面卡片点击只提示，不挂载引擎与弹窗。
       */
      profileConfig: null,
      /**
       * 断面分析当前模式（'' = 无 / 'horizontal' 横断面 / 'vertical' 纵断面）。
       * startDraw('horizontal' | 'vertical') 在引擎内嵌的 PopSectionList
       * 弹层组件上（引擎本体只有 btnClick / checked / cancelChecked 契约，
       * 混淆字符串表已核实），两枚断面卡片经弹层直发驱动两种模式。
       */
      activeSectionMode: '',
      /**
       * 当前打开的统计弹窗事件名（'' = 无）。五个弹窗压平后都是面板全幅、
       * 互相不排斥（互斥本来由引擎 checkedALL 管，直发事件绕过了它），
       * 页面自己记这个状态：切弹窗先发 false 收上一枚，关卡片/切工具时
       * 也要发 false 复位，否则下次进入会带着上次的开态直接铺开。
       */
      activePopEvent: '',
      /**
       * 成熟度分析（页面自实现预留面板）：图层开关列表来自
       * MATURITY_LAYERS 配置点（当前为空，图层服务接入后填入）；
       * activeMaturityLabel = 当前显示的图层（互斥，同旧系统按钮语义）。
       */
      maturityLayers: MATURITY_LAYERS,
      activeMaturityLabel: '',
      /**
       * 配套估算（页面自实现，前端复刻旧系统 ptgsManager 的区间公式）：
       * estimateResult = { crzdbh 出让宗地编号, dkmc 地块名称,
       * low/high 配套费估算区间（万元）}，拾取地块后填充。
       */
      estimateResult: null,
      /**
       * 五个统计类型入口：label 是界面文案，event 是对应统计弹窗监听的
       * 总线事件（= 中台配置键）。弹窗内部 $on(event, payload)，
       * payload === true 显示 / false 隐藏（UMD 已证实该契约）；
       * 插件浮层按钮的点击最终也是发同一批事件，这里直接发，
       * 绕开对插件按钮节点（混淆、有嵌套包裹）的依赖。
       */
      pipelineTypeLabels: [
        { label: '管径分段统计', event: 'gjfdanalyse' },
        { label: '埋深分段统计', event: 'msfdanalyse' },
        { label: '材质分类统计', event: 'czanalyse' },
        { label: '管径分类统计', event: 'gjanalyse' },
        { label: '点性质分类统计', event: 'xzanalyse' }
      ],
      /**
       * 标注管理当前激活的子功能（'' = 无）。四枚功能按钮里三枚对应
       * road 式引擎（pipeMark / coordinateLabel / deleteLabel），互斥
       * 靠引擎的 $parent.cancelAll() 契约 + 本页 resetEngineStates。
       */
      activeLabelTool: '',
      /**
       * 标注管理的四个功能入口。埋深标注（DepthLabel）当前 stargis-function
       * 包未打包组件（混淆字符串表已全量解码核实：表里没有该名字）——
       * 仍乐观挂载 <depth-label> 并做运行时能力检测（typeof btnClick）：
       * 插件升级带上组件后，按钮无需改代码直接可用。
       */
      labelToolButtons: [
        { label: '管线标注', key: 'pipeMark' },
        { label: '埋深标注', key: 'depthLabel' },
        { label: '坐标标注', key: 'coordinateLabel' },
        { label: '清除标注', key: 'deleteLabel' }
      ]
    }
  },
  computed: {
    /** 激活工具的定义（右面板标题用）；未激活时为 null */
    activeToolDef () {
      return ANALYSIS_TOOLS.find((tool) => tool.key === this.activeTool) || null
    }
  },
  created () {
    this.loadPipeConfig()
    // 自绘埋深标注的实体 id 列表（非响应式，避免 Vue 深度观察 Cesium 对象）
    this._depthLabels = []
  },
  mounted () {
    // 引擎随页面挂载（v-if pipeConfig），按钮可能已经渲染：先搬一次，
    // 没搬到（异步未渲染）再由 startTypeButtonCollector 轮询兜底
    if (this.pipeConfig) this.collectTypeButtons()
  },
  watch: {
    // 引擎激活后五枚类型入口才可能渲染出来，开一次搬运流程
    pipeActive (val) {
      if (val) this.startTypeButtonCollector()
    }
  },
  beforeDestroy () {
    if (this._btnCollectTimer) {
      clearInterval(this._btnCollectTimer)
      this._btnCollectTimer = null
    }
    if (this._btnObserver) {
      this._btnObserver.disconnect()
      this._btnObserver = null
    }
    // 埋深标注 / 配套估算的拾取句柄与自绘标注随页面销毁清理（地图是跨页共享的）
    this.stopDepthLabelPick()
    this.clearDepthLabels()
    this.stopEstimatePick()
  },
  methods: {
    /**
     * 把插件渲染的五枚统计类型入口（.analyse-pipe）搬进 __plugin-parking
     * 停靠区隐藏保存：浮层入口不再出现在页头 / 面板里。搬移前只清停靠区
     * （不能清整个容器——容器里还有本组件自己渲染的类型按钮）。
     * ★ 引擎重渲染会重新生成按钮：由 MutationObserver 触发本方法重搬。
     */
    collectTypeButtons () {
      const engineHost = this.$refs.engineHost
      const parking = this.$refs.pluginParking
      if (!engineHost || !parking) return false
      const buttons = engineHost.querySelectorAll('.analyse-pipe')
      if (!buttons.length) return false
      parking.innerHTML = ''
      buttons.forEach((btn) => parking.appendChild(btn))
      return true
    },

    /** 轮询 + MutationObserver 双保险：引擎异步渲染 / 重渲染出新按钮时再搬 */
    startTypeButtonCollector () {
      if (this._btnCollectTimer || this._btnObserver) return
      let tries = 0
      this._btnCollectTimer = setInterval(() => {
        if (this.collectTypeButtons() || ++tries > 20) {
          clearInterval(this._btnCollectTimer)
          this._btnCollectTimer = null
        }
      }, 300)
      const engineHost = this.$refs.engineHost
      if (engineHost && window.MutationObserver) {
        this._btnObserver = new window.MutationObserver(() => this.collectTypeButtons())
        this._btnObserver.observe(engineHost, { childList: true, subtree: true })
      }
    },

    /**
     * 自绘类型按钮 → 直接走插件的总线契约打开对应统计弹窗：
     * 五个弹窗各自 $on 以配置键命名的事件（gjfdanalyse / msfdanalyse /
     * czanalyse / gjanalyse / xzanalyse），payload === true 显示、false 隐藏
     * （UMD 已证实）。插件浮层按钮的点击最终也是发同一批事件，这里直接发，
     * 绕开对插件按钮节点（混淆、有嵌套包裹）的依赖。
     * ★ 五个弹窗压平后都是面板全幅且互不排斥，先收上一枚再开当前这枚；
     *   再点同一枚 = 收起。
     */
    openTypePop (item) {
      if (this.activePopEvent === item.event) {
        this.$bus.$emit(item.event, false)
        this.activePopEvent = ''
        return
      }
      if (this.activePopEvent) {
        this.$bus.$emit(this.activePopEvent, false)
      }
      this.$bus.$emit(item.event, true)
      this.activePopEvent = item.event
    },

    /**
     * 配置摊平：管线统计与标注管理的配置分属中台两个模块——
     * PipeManager（统计五弹窗 + 管线标注 / 清除标注）与 Common
     * （坐标标注），按 componentname 递归摊平进同一张表。
     * 引擎 / 弹窗逐个取自己的键：PipeStatistics、gjanalyse / czanalyse /
     * gjfdanalyse / msfdanalyse / xzanalyse、PipeMark、DeleteLabel、
     * CoordinateLabel。
     */
    loadPipeConfig () {
      const sysConfig = this.$store.getters.sysConfig || {}
      const map = {}
      const walk = (items) => {
        (items || []).forEach((item) => {
          map[item.componentname] = item
          if (item.nodetype === 'group') walk(item.children)
        })
      }
      walk(sysConfig.PipeManager && sysConfig.PipeManager.children)
      walk(sysConfig.Common && sysConfig.Common && sysConfig.Common.children)
      walk(sysConfig.SpatialAnalysis && sysConfig.SpatialAnalysis.children)
      this.pipePopConfig = map
      this.pipeConfig = map.PipeStatistics || null
      this.sectionConfig = map.SectionalAnalysis || null
      this.profileConfig = map.ProfileAnalysis || null
    },

    /**
     * 复位全部已接引擎的 checked 与子状态（keepKey 对应的引擎 / 工具保留）。
     * 不动 activeTool——右面板的挂载由各 toggle 流程自己管理。
     */
    resetEngineStates (keepKey) {
      // 管线统计的对话框强收（不 gated 在 pipeActive 上——开态失真时会漏收，
      // 真机复现过统计标题叠在断面对话框上）
      if (keepKey !== 'pipeline') {
        const engine = this.$refs.pipeStatisticsEngine
        try { if (engine && engine.checked) engine.cancelChecked() } catch (error) { /* 地图未就绪时复位失败可忽略 */ }
        this.pipeActive = false
        this.hidePluginFloat('管线统计')
      }
      ;['pipeMark', 'coordinateLabel', 'deleteLabel', 'sectional', 'cover', 'flow', 'burst', 'excavation'].forEach((key) => {
        if (key === keepKey) return
        const engine = this.$refs[key + 'Engine']
        try { if (engine && engine.checked) engine.cancelChecked() } catch (error) { /* 地图未就绪时复位失败可忽略 */ }
      })
      // 埋深标注 / 配套估算的拾取模式（页面自实现）随其它引擎一起复位
      if (keepKey !== 'depthLabel' && this._depthLabelHandler) this.stopDepthLabelPick()
      if (keepKey !== 'estimate' && this._estimateHandler) this.stopEstimatePick()
      this.activeLabelTool = ''
      // 断面已画的剖面随其它引擎一起清掉
      if (keepKey !== 'sectional') {
        const sectional = this.$refs.sectionalEngine
        try { if (sectional && typeof sectional.cleanProfile === 'function') sectional.cleanProfile() } catch (error) { /* 忽略 */ }
      }
      // 断面模式（横 / 纵）随其它引擎一起复位
      if (keepKey !== 'sectional') this.activeSectionMode = ''
      // 打开中的统计弹窗发 false 复位：弹窗宿主随 v-if 卸载，但内部开态
      // 还在，不复位下次进入会直接铺开
      if (this.activePopEvent) {
        this.$bus.$emit(this.activePopEvent, false)
        this.activePopEvent = ''
      }
    },

    /** 插件引擎激活契约：btnClick() 激活前会调 $parent.cancelAll()。
     *  只复位引擎与子状态，不动 activeTool——右面板挂载/收起由
     *  toggle 流程自己管理（先挂宿主再 btnClick 的顺序不能反，见
     *  地图管理页同名注释）。 */
    cancelAll () {
      this.resetEngineStates(null)
    },

    /** 复位除指定工具外的全部已接引擎 + 收起其它工具的面板 */
    closeOtherTools (keepKey) {
      this.resetEngineStates(keepKey)
      if (keepKey !== 'pipeline' && this.activeTool === 'pipeline') this.activeTool = ''
      if (keepKey !== 'label' && this.activeTool === 'label') this.activeTool = ''
    },

    /**
     * 标注管理的四枚功能按钮：
     *   管线标注 / 坐标标注 —— road 式引擎（btnClick 进 / 退地图标注模式），
     *   再点同一枚 = 退出；表单弹窗由引擎经总线拉起；
     *   埋深标注 —— 页面自实现（toggleDepthLabel，插件无此组件）；
     *   清除标注 —— 一键清空全部标注（旧系统 labelManager.clearLabel 的
     *   语义）：先清本页自绘的埋深标注，再触发插件的 DeleteLabel 引擎——
     *   插件内含「确定清扫全部？」确认框，清扫它自绘的管线 / 坐标标注。
     */
    toggleLabelTool (item) {
      if (item.key === 'depthLabel') {
        this.toggleDepthLabel()
        return
      }
      if (item.key === 'deleteLabel') {
        // 清除标注 = 一键清空全部标注：
        //   ① 本页自绘的埋深标注（clearDepthLabels 逐个移除，计数）；
        //   ② 关掉插件还开着的标注工具条（PipeMarkPop，showPipeMarkPop
        //      false 收起——已证实的总线契约）；
        //   ③ viewer.entities 里带文字标注的实体做静默卫生清扫（不计数——
        //      同一标注可能同时有实体与浮层两份，计两次会虚高）；
        //   ④ 插件的 HTML 浮层标注（clearPluginLabelOverlays 找到每个
        //      浮层**真正的 X 关闭钮**点它——插件自己清理状态与 DOM，
        //      后续标注不受影响。真机教训：直接删节点会误删插件放标注的
        //      共享容器，之后新标注全部 append 进脱离文档的容器、再也
        //      显示不出来；所以绝不删节点、只点关闭钮）。
        const cleared = this.clearDepthLabels()
        this.$bus.$emit('showPipeMarkPop', false)
        const viewer = window.viewer
        if (viewer) {
          const toRemove = []
          viewer.entities.values.forEach((entity) => {
            if (entity.label) toRemove.push(entity.id)
          })
          toRemove.forEach((id) => {
            try { viewer.entities.removeById(id) } catch (error) { /* 忽略 */ }
          })
        }
        const engine = this.$refs.deleteLabelEngine
        if (engine && typeof engine.btnClick === 'function') {
          try { engine.btnClick() } catch (error) { /* 插件清扫失败不影响前面 */ }
        }
        const overlayCleared = this.clearPluginLabelOverlays()
        const total = cleared + overlayCleared
        if (total) {
          this.$screenToast.success('已清除标注 ' + total + ' 处')
        } else {
          this.$screenToast.info('当前没有可清除的标注')
        }
        return
      }
      // 管线标注 / 坐标标注：road 式引擎（表单弹窗由引擎经总线拉起）
      const engine = this.$refs[item.key + 'Engine']
      if (!engine) {
        this.$screenToast.info('「' + item.label + '」未配置数据源，请在 GIS 功能配置「管线管理」里配置后使用')
        return
      }
      if (typeof engine.btnClick !== 'function') {
        this.$screenToast.info('当前 stargis-function 包未包含「' + item.label + '」组件，待插件升级后接入')
        return
      }
      if (this.activeLabelTool === item.key) {
        // 再点同一枚 = 退出标注模式
        try { engine.btnClick() } catch (error) { /* 与开启同款兜底 */ }
        this.activeLabelTool = ''
        return
      }
      this.resetEngineStates(item.key)
      try {
        engine.btnClick()
      } catch (error) {
        // 引擎进标注模式要访问 window.viewer.scene：?map=0 预览或
        // Cesium 未就绪时会抛错，兜住并复位，避免卡片亮着但实际没生效
        try { if (engine.checked) engine.cancelChecked() } catch (ignored) { /* 复位失败不必再处理 */ }
        this.$screenToast.info('地图未就绪，暂不可用')
        return
      }
      this.activeLabelTool = engine.checked ? item.key : ''
    },

    toggleToolByKey (key) {
      if (key === 'pipeline') {
        this.togglePipeline()
        return
      }
      if (key === 'sectionH' || key === 'sectionV') {
        // 横 / 纵断面：同一枚 SectionalAnalysis 引擎的两种 startDraw 模式
        this.toggleSectional(key === 'sectionH' ? 'horizontal' : 'vertical')
        return
      }
      if (key === 'clearance') {
        this.toggleClearance()
        return
      }
      if (key === 'cover') {
        this.toggleCover()
        return
      }
      if (key === 'flow') {
        this.toggleFlow()
        return
      }
      if (key === 'burst') {
        this.toggleBurst()
        return
      }
      if (key === 'excavation') {
        this.toggleExcavation()
        return
      }
      if (key === 'estimate') {
        this.toggleEstimate()
        return
      }
      this.closeOtherTools(key)
      this.activeTool = this.activeTool === key ? '' : key
    },

    /**
     * 横 / 纵断面分析（两个不同的功能，按旧系统的交互拆开）：
     *   · 横断面（sectionH）→ SectionalAnalysis：右侧不弹任何面板，
     *     点卡片直接在地图上画切线（引擎面板隐藏在宿主里，模式按钮由
     *     代码程序化点中——display:none 的 DOM 照常派发 click）；
     *   · 纵断面（sectionV）→ ProfileAnalysis + ProfileAnalysisPop：
     *     右侧弹出带「绘制 / 清除」操作按钮的弹窗（老系统 sectionZ 的
     *     交互），由引擎经 showProfilePop 总线事件拉起。
     * 再点同一枚卡片 = 退出。
     */
    toggleSectional (mode) {
      if (mode === 'vertical') {
        this.toggleVerticalProfile()
        return
      }
      this.toggleHorizontalSection()
    },

    /** 横断面分析：SectionalAnalysis，右侧无面板，直接在地图上画切线 */
    toggleHorizontalSection () {
      const engine = this.$refs.sectionalEngine
      if (!engine) {
        this.$screenToast.info('「横断面分析」未配置数据源，请在 GIS 功能配置「管线管理 → 断面分析」里配置后使用')
        return
      }
      if (typeof engine.btnClick !== 'function') {
        this.$screenToast.info('当前 stargis-function 包未包含断面分析组件，待插件升级后接入')
        return
      }
      if (this.activeSectionMode === 'horizontal') {
        // 再点同一枚 = 退出：复位引擎
        try { if (engine.checked) engine.cancelChecked() } catch (error) { /* 忽略 */ }
        this.activeSectionMode = ''
        this.activeTool = ''
        return
      }
      this.resetEngineStates('sectional')
      try {
        // 激活后右侧面板保持隐藏（宿主 is-idle），模式按钮在隐藏 DOM 里
        // 照常可点：程序化点中「横断面分析」直接进入绘制
        engine.btnClick()
        let tries = 0
        const timer = setInterval(() => {
          const btn = this.findSectionalModeButton(engine, '横断面分析')
          if (btn || ++tries > 25) {
            clearInterval(timer)
            if (btn) {
              btn.click()
              this.$screenToast.info('横断面分析：请在地图上画一条切线（点两个点）')
            } else {
              this.$screenToast.info('已进入断面分析，如未开始绘制请稍候重试')
              this.hidePluginFloat('断面分析')
            }
            this.activeSectionMode = 'horizontal'
            this.activeTool = 'sectionH'
          }
        }, 200)
      } catch (error) {
        this.$screenToast.info('地图未就绪，暂不可用')
      }
    },

    /**
     * 纵断面分析（老系统 enablePipeSectionAnalysis 的交互）：拾取管线——
     * 点卡片后进入拾取模式，在地图上点一条管线，出该管线沿走向的纵断面
     * 图（埋深起伏）。实现走 SectionalAnalysis 的纵断面模式：激活后自动
     * 点面板里的「纵断面分析」模式按钮（老系统 sectionZ 对话框的
     * 「开始分析 / 清除分析」就是这个面板的后续操作），面板随引擎宿主显示。
     */
    toggleVerticalProfile () {
      const engine = this.$refs.sectionalEngine
      if (!engine) {
        this.$screenToast.info('「纵断面分析」未配置数据源，请在 GIS 功能配置「管线管理 → 断面分析」里配置后使用')
        return
      }
      if (typeof engine.btnClick !== 'function') {
        this.$screenToast.info('当前 stargis-function 包未包含断面分析组件，待插件升级后接入')
        return
      }
      if (this.activeSectionMode === 'vertical') {
        // 再点同一枚 = 退出：复位引擎并收面板
        try { if (engine.checked) engine.cancelChecked() } catch (error) { /* 忽略 */ }
        this.activeSectionMode = ''
        this.activeTool = ''
        return
      }
      this.resetEngineStates('sectional')
      try {
        // 激活后自动点中面板里的「纵断面分析」模式按钮进入拾取
        engine.btnClick()
        let tries = 0
        const timer = setInterval(() => {
          const btn = this.findSectionalModeButton(engine, '纵断面分析')
          if (btn || ++tries > 25) {
            clearInterval(timer)
            if (btn) {
              btn.click()
              this.$screenToast.info('纵断面分析：请在地图上点击一条管线')
              this.hidePluginFloat('断面分析')
            } else {
              this.$screenToast.info('已打开断面分析，如未进入纵断面请点面板中的「纵断面分析」按钮')
            }
            this.activeSectionMode = 'vertical'
            this.activeTool = 'sectionV'
          }
        }, 200)
      } catch (error) {
        this.$screenToast.info('地图未就绪，暂不可用')
      }
    },

    /**
     * 埋深标注（页面自实现，参照旧系统 soilLabel.js 的逻辑）：
     * 进拾取模式 → 点管线 → 读 QSMS / ZZMS 属性 → 在管线首末端点各画一个
     * 文字标注。旧系统用点号查管点层拿起止坐标；新系统管线本身就是折线，
     * 直接取折线首末顶点（取不到时退化到拾取点）。再点一次按钮 = 退出。
     */
    toggleDepthLabel () {
      if (this.activeLabelTool === 'depthLabel') {
        this.stopDepthLabelPick()
        this.activeLabelTool = ''
        return
      }
      this.resetEngineStates('depthLabel')
      try {
        this.startDepthLabelPick()
      } catch (error) {
        this.$screenToast.info('地图未就绪，暂不可用')
        return
      }
      this.activeLabelTool = 'depthLabel'
      this.$screenToast.info('埋深标注：请在地图上点击一条管线')
    },

    startDepthLabelPick () {
      const Cesium = window.Cesium
      const viewer = window.viewer
      if (!Cesium || !viewer || !viewer.scene) throw new Error('map not ready')
      if (this._depthLabelHandler) this.stopDepthLabelPick()
      this._depthLabelHandler = new Cesium.ScreenSpaceEventHandler(viewer.scene.canvas)
      this._depthLabelHandler.setInputAction((movement) => {
        this.handleDepthPick(movement.position)
      }, Cesium.ScreenSpaceEventType.LEFT_CLICK)
    },

    stopDepthLabelPick () {
      if (this._depthLabelHandler) {
        try { this._depthLabelHandler.destroy() } catch (error) { /* 已销毁则忽略 */ }
        this._depthLabelHandler = null
      }
    },

    handleDepthPick (position) {
      const viewer = window.viewer
      let picked = null
      try { picked = viewer.scene.pick(position) } catch (error) { return }
      if (!picked) {
        this.$screenToast.info('未拾取到要素，请点击管线')
        return
      }
      const info = this.extractPickInfo(picked)
      const read = (names) => {
        for (let i = 0; i < names.length; i++) {
          const v = info.props ? info.props[names[i]] : null
          if (v !== undefined && v !== null && v !== '') return String(v)
        }
        return null
      }
      const qsms = read(['QSMS', 'qsms', '起点埋深'])
      const zzms = read(['ZZMS', 'zzms', '终点埋深'])
      if (qsms === null && zzms === null) {
        this.$screenToast.info('该要素属性中没有埋深字段（QSMS / ZZMS），请确认点击的是管线')
        return
      }
      if (info.endpoints) {
        if (qsms !== null) this.addDepthLabelEntity(info.endpoints[0], '起点埋深：' + qsms)
        if (zzms !== null) this.addDepthLabelEntity(info.endpoints[1], '终点埋深：' + zzms)
        return
      }
      // 兜底：折线顶点不可读时，退化到拾取点各画一条
      let picked3 = null
      try {
        picked3 = viewer.scene.pickPosition
          ? viewer.scene.pickPosition(position)
          : viewer.camera.pickEllipsoid(position, viewer.scene.globe.ellipsoid)
      } catch (error) { picked3 = null }
      if (!picked3) {
        this.$screenToast.info('未能读取管线坐标，埋深标注失败')
        return
      }
      if (qsms !== null) this.addDepthLabelEntity(picked3, '起点埋深：' + qsms)
      if (zzms !== null) this.addDepthLabelEntity(picked3, '终点埋深：' + zzms)
    },

    /**
     * 从拾取结果里读属性与折线首末顶点：
     *   属性 —— entity.properties（PropertyBag）优先，3D Tiles 要素走
     *           getProperty；字段名做大小写 / 中文名兜底；
     *   顶点 —— entity.polyline.positions（其它渲染方式拿不到则 null）。
     */
    extractPickInfo (picked) {
      const Cesium = window.Cesium
      const target = (picked && (picked.id || picked.primitive)) || picked
      let props = null
      if (target && typeof target.getProperty === 'function') {
        props = {}
        ;['QSMS', 'qsms', 'ZZMS', 'zzms', 'QSDH', 'ZZDH', '起点埋深', '终点埋深'].forEach((n) => {
          try { props[n] = target.getProperty(n) } catch (error) { /* 忽略 */ }
        })
      } else if (target && target.properties) {
        try {
          const bag = typeof target.properties.getValue === 'function'
            ? target.properties.getValue(Cesium.JulianDate.now())
            : target.properties
          props = bag && typeof bag === 'object' ? bag : null
        } catch (error) { props = null }
      }
      let endpoints = null
      if (target && target.polyline && target.polyline.positions && typeof target.polyline.positions.getValue === 'function') {
        try {
          const v = target.polyline.positions.getValue(Cesium.JulianDate.now())
          if (v && v.length >= 2) endpoints = [v[0], v[v.length - 1]]
        } catch (error) { endpoints = null }
      }
      return { props, endpoints }
    },

    /** 画一枚埋深文字标注（Cesium Entity，禁深度测试保证不被管线盖住） */
    addDepthLabelEntity (cartesian, text) {
      const Cesium = window.Cesium
      const viewer = window.viewer
      const entity = viewer.entities.add({
        position: cartesian,
        label: {
          text: text,
          font: '16pt "Microsoft YaHei"',
          fillColor: Cesium.Color.fromCssColorString('#00c0ff'),
          outlineColor: Cesium.Color.BLACK,
          outlineWidth: 2,
          style: Cesium.LabelStyle.FILL_AND_OUTLINE,
          verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
          pixelOffset: new Cesium.Cartesian2(0, -6),
          showBackground: true,
          backgroundColor: Cesium.Color.fromCssColorString('#001432'),
          backgroundPadding: new Cesium.Cartesian2(8, 6),
          // 旧实现把标注点 Z 抬高 4 米避免贴地；Cesium 里等价做法是禁深度测试
          disableDepthTestDistance: Number.POSITIVE_INFINITY
        }
      })
      this._depthLabels.push(entity.id)
    },

    /** 清除全部自绘埋深标注，返回数量（供「清除标注」按钮 toast） */
    clearDepthLabels () {
      const viewer = window.viewer
      const count = this._depthLabels.length
      if (viewer) {
        this._depthLabels.forEach((id) => {
          try { viewer.entities.removeById(id) } catch (error) { /* 忽略 */ }
        })
      }
      this._depthLabels = []
      return count
    },

    /**
     * 清除插件画在地图上的 HTML 浮层标注（坐标标注等）：它们不在
     * viewer.entities 里，是覆盖层 DOM。文案特征带**数值**（经度: 117.…），
     * 坐标标注的表单只有字段名没有值（input 的值不进 textContent），所以
     * 不会误伤表单。
     * ★ 文本会沿祖先链上冒（#app 的 textContent 也含这段文字）——
     *   取「最外层匹配」会把整个应用容器删掉（真机白屏）。正确做法：
     *   取「最内层匹配」，再向上小步爬到浮层根；三重护栏确保不越界：
     *   含 canvas 的（地图）、后代超 300 个的（应用容器）、类名带
     *   analysis-screen 的（本页结构）一律不碰。
     */
    /**
     * 按标题隐藏插件手动挂到 body 层的浮层（断面模式面板 / 管线统计
     * 对话框等）：插件浮层的根节点是 body 直接子级（或浅层挂载），
     * 引擎宿主的 display:none 管不到它们。查找规则：body 子级里含目标
     * 标题、且**不含 .analysis-screen**（排除本页自己的卡片文案）的
     * 元素置 display:none。
     */
    hidePluginFloat (label) {
      let hidden = 0
      Array.prototype.forEach.call(document.body.children, (el) => {
        if ((el.textContent || '').indexOf(label) === -1) return
        if (el.querySelector && el.querySelector('.analysis-screen')) return
        if (el.style.display !== 'none') {
          el.style.display = 'none'
          hidden++
        }
      })
      return hidden
    },
    clearPluginLabelOverlays () {
      const isMine = (el) => (('' + (el.className || ''))).indexOf('analysis-screen') > -1
      const isBig = (el) => !el.querySelector || !!el.querySelector('canvas') || el.querySelectorAll('*').length > 300 || isMine(el)
      const matches = []
      document.querySelectorAll('div').forEach((el) => {
        const text = el.textContent || ''
        if (/经度\s*[:：]?\s*\d/.test(text) && /纬度\s*[:：]?\s*\d/.test(text) && !isBig(el)) matches.push(el)
      })
      // 最内层匹配（没有任何匹配后代）= 文字容器；从它向上爬小节点取浮层根
      const innermost = matches.filter((el) => !matches.some((m) => m !== el && el.contains(m)))
      let count = 0
      innermost.forEach((node) => {
        let root = node
        for (let i = 0; i < 6; i++) {
          const parent = root.parentNode
          if (!parent || parent === document.body || parent === document.documentElement) break
          if (isBig(parent) || isMine(parent)) break
          root = parent
        }
        try { root.remove(); count++ } catch (error) { /* 忽略 */ }
      })
      return count
    },

    /**
     * 开挖分析（ExcavationAnalysis 引擎 + ExcavationAnalysisPop 结果
     * 弹窗）：点卡片 → 引擎 btnClick 进入开挖拾取模式 → 在地图上拾取 →
     * 结果弹窗展示。再点同一枚卡片 = 退出（关弹窗 + 复位引擎）。
     * ★ 事件名双拼写并存（showExcavatePop / showExcatatePop，插件历史
     *   拼写变体），开合都双发，命中其一即可。
     */
    toggleExcavation () {
      if (!this.pipePopConfig.ExcavationAnalysis) {
        this.$screenToast.info('「开挖分析」未配置数据源，请在 GIS 功能配置「管线管理 → 开挖分析」里配置后使用')
        return
      }
      const engine = this.$refs.excavationEngine
      if (!engine || typeof engine.btnClick !== 'function') {
        this.$screenToast.info('当前 stargis-function 包未包含开挖分析组件，待插件升级后接入')
        return
      }
      if (this.activeTool === 'excavation') {
        // 再点同一枚 = 退出：关结果弹窗并复位引擎
        this.$bus.$emit('showExcavatePop', false)
        this.$bus.$emit('showExcatatePop', false)
        try { if (engine.checked) engine.cancelChecked() } catch (error) { /* 忽略 */ }
        this.activeTool = ''
        return
      }
      this.resetEngineStates('excavation')
      // 先切 activeTool 挂载结果弹窗，下一拍再让引擎进拾取模式
      this.activeTool = 'excavation'
      this.$nextTick(() => {
        try {
          engine.btnClick()
        } catch (error) {
          // 引擎进开挖模式要访问 window.viewer.scene：?map=0 预览或
          // Cesium 未就绪时会抛错，兜住并复位，避免卡片亮着但实际没生效
          try { if (engine.checked) engine.cancelChecked() } catch (ignored) { /* 复位失败不必再处理 */ }
          if (this.activeTool === 'excavation') this.activeTool = ''
          this.$screenToast.info('地图未就绪，暂不可用')
        }
      })
    },

    /**
     * 爆管分析（BurstAnalysis 引擎 + BurstAnalysisPop 结果弹窗）：
     * 点卡片 → 引擎 btnClick 进入爆管拾取模式 → 在地图上拾取爆管点 →
     * 关阀方案与影响分析经 showBurstPop 总线事件拉起弹窗展示。
     * 再点同一枚卡片 = 退出（关弹窗 + 复位引擎）。
     */
    toggleBurst () {
      if (!this.pipePopConfig.BurstAnalysis) {
        this.$screenToast.info('「爆管分析」未配置数据源，请在 GIS 功能配置「管线管理 → 爆管分析」里配置后使用')
        return
      }
      const engine = this.$refs.burstEngine
      if (!engine || typeof engine.btnClick !== 'function') {
        this.$screenToast.info('当前 stargis-function 包未包含爆管分析组件，待插件升级后接入')
        return
      }
      if (this.activeTool === 'burst') {
        // 再点同一枚 = 退出：关结果弹窗并复位引擎
        this.$bus.$emit('showBurstPop', false)
        try { if (engine.checked) engine.cancelChecked() } catch (error) { /* 忽略 */ }
        this.activeTool = ''
        return
      }
      this.resetEngineStates('burst')
      // 先切 activeTool 挂载结果弹窗，下一拍再让引擎进拾取模式
      this.activeTool = 'burst'
      this.$nextTick(() => {
        try {
          engine.btnClick()
        } catch (error) {
          // 引擎进爆管模式要访问 window.viewer.scene：?map=0 预览或
          // Cesium 未就绪时会抛错，兜住并复位，避免卡片亮着但实际没生效
          try { if (engine.checked) engine.cancelChecked() } catch (ignored) { /* 复位失败不必再处理 */ }
          if (this.activeTool === 'burst') this.activeTool = ''
          this.$screenToast.info('地图未就绪，暂不可用')
        }
      })
    },

    /**
     * 流向分析（FlowDirectionAnalysis 引擎 + FlowDirectionAnalysisPop
     * 结果弹窗）：点卡片 → 引擎 btnClick 进入流向拾取模式 → 在地图上
     * 拾取管线 → 结果经 showFlowDirectionAnalysisPop 总线事件拉起弹窗
     * 展示。再点同一枚卡片 = 退出（关弹窗 + 复位引擎）。
     */
    toggleFlow () {
      if (!this.pipePopConfig.FlowDirectionAnalysis) {
        this.$screenToast.info('「流向分析」未配置数据源，请在 GIS 功能配置「管线管理 → 流向分析」里配置后使用')
        return
      }
      const engine = this.$refs.flowEngine
      if (!engine || typeof engine.btnClick !== 'function') {
        this.$screenToast.info('当前 stargis-function 包未包含流向分析组件，待插件升级后接入')
        return
      }
      if (this.activeTool === 'flow') {
        // 再点同一枚 = 退出：关结果弹窗并复位引擎
        this.$bus.$emit('showFlowDirectionAnalysisPop', false)
        try { if (engine.checked) engine.cancelChecked() } catch (error) { /* 忽略 */ }
        this.activeTool = ''
        return
      }
      this.resetEngineStates('flow')
      // 先切 activeTool 挂载结果弹窗，下一拍再让引擎进拾取模式
      this.activeTool = 'flow'
      this.$nextTick(() => {
        try {
          engine.btnClick()
        } catch (error) {
          // 引擎进流向模式要访问 window.viewer.scene：?map=0 预览或
          // Cesium 未就绪时会抛错，兜住并复位，避免卡片亮着但实际没生效
          try { if (engine.checked) engine.cancelChecked() } catch (ignored) { /* 复位失败不必再处理 */ }
          if (this.activeTool === 'flow') this.activeTool = ''
          this.$screenToast.info('地图未就绪，暂不可用')
        }
      })
    },

    /**
     * 覆土分析（CoverageAnalysis 引擎 + CoverageAnalysisPop 结果弹窗）：
     * 点卡片 → 引擎 btnClick 进入覆土拾取模式 → 在地图上拾取管线 →
     * 结果经 showCoveragePop 总线事件拉起弹窗展示。再点同一枚卡片 =
     * 退出（关弹窗 + 复位引擎）。
     */
    toggleCover () {
      if (!this.pipePopConfig.CoverageAnalysis) {
        this.$screenToast.info('「覆土分析」未配置数据源，请在 GIS 功能配置「管线管理 → 覆土分析」里配置后使用')
        return
      }
      const engine = this.$refs.coverEngine
      if (!engine || typeof engine.btnClick !== 'function') {
        this.$screenToast.info('当前 stargis-function 包未包含覆土分析组件，待插件升级后接入')
        return
      }
      if (this.activeTool === 'cover') {
        // 再点同一枚 = 退出：关结果弹窗并复位引擎
        this.$bus.$emit('showCoveragePop', false)
        try { if (engine.checked) engine.cancelChecked() } catch (error) { /* 忽略 */ }
        this.activeTool = ''
        return
      }
      this.resetEngineStates('cover')
      // 先切 activeTool 挂载结果弹窗，下一拍再让引擎进拾取模式
      this.activeTool = 'cover'
      this.$nextTick(() => {
        try {
          engine.btnClick()
        } catch (error) {
          // 引擎进覆土模式要访问 window.viewer.scene：?map=0 预览或
          // Cesium 未就绪时会抛错，兜住并复位，避免卡片亮着但实际没生效
          try { if (engine.checked) engine.cancelChecked() } catch (ignored) { /* 复位失败不必再处理 */ }
          if (this.activeTool === 'cover') this.activeTool = ''
          this.$screenToast.info('地图未就绪，暂不可用')
        }
      })
    },

    /**
     * 净距分析（DistanceAnalysis 引擎 + DistanceAnalysisPop 结果弹窗）：
     * 点卡片 → 引擎 btnClick 进入净距拾取模式 → 在地图上拾取管线 →
     * 结果经 showDistancePop 总线事件拉起弹窗展示。再点同一枚卡片 =
     * 退出（关弹窗 + 复位引擎）。
     */
    toggleClearance () {
      if (!this.pipePopConfig.DistanceAnalysis) {
        this.$screenToast.info('「净距分析」未配置数据源，请在 GIS 功能配置「管线管理 → 净距分析」里配置后使用')
        return
      }
      const engine = this.$refs.clearanceEngine
      if (!engine || typeof engine.btnClick !== 'function') {
        this.$screenToast.info('当前 stargis-function 包未包含净距分析组件，待插件升级后接入')
        return
      }
      if (this.activeTool === 'clearance') {
        // 再点同一枚 = 退出：关结果弹窗并复位引擎
        this.$bus.$emit('showDistancePop', false)
        try { if (engine.checked) engine.cancelChecked() } catch (error) { /* 忽略 */ }
        this.activeTool = ''
        return
      }
      this.resetEngineStates('clearance')
      // 先切 activeTool 挂载结果弹窗，下一拍再让引擎进拾取模式
      this.activeTool = 'clearance'
      this.$nextTick(() => {
        try {
          engine.btnClick()
        } catch (error) {
          // 引擎进净距模式要访问 window.viewer.scene：?map=0 预览或
          // Cesium 未就绪时会抛错，兜住并复位，避免卡片亮着但实际没生效
          try { if (engine.checked) engine.cancelChecked() } catch (ignored) { /* 复位失败不必再处理 */ }
          if (this.activeTool === 'clearance') this.activeTool = ''
          this.$screenToast.info('地图未就绪，暂不可用')
        }
      })
    },

    /**
     * 成熟度图层开关（互斥，同旧系统按钮语义）：点一层 = 显示它并隐藏
     * 其它成熟度图层；再点同一枚 = 全部隐藏。
     * ★ 图层显隐调用接入点（TODO）：图层服务接入后，在此调用地图的
     *   图层显隐 API 传入 item.code（参照 HomeLayerTree 的图层勾选 /
     *   插件 LayerTree 的显隐接口），下方 UI 状态翻转保持不变。
     */
    toggleMaturityLayer (item) {
      this.activeMaturityLabel = this.activeMaturityLabel === item.label ? '' : item.label
      // TODO：图层显隐 API 接入点——
      //   this.activeMaturityLabel 即当前应显示的图层标识（'' = 全隐藏）
    },

    /**
     * 配套估算（页面自实现，前端复刻旧系统 ptgsManager 的区间公式）：
     * 点卡片 → 进拾取模式 → 在地图上点一个出让地块 → 读出让宗地编号 /
     * 地块名称 / 出让金（亿元）→ 按旧公式出配套费估算区间
     * （出让金×10000 的一半，上下浮动 30%，即 35% ~ 65%）。
     * 结果显示在右面板；再点一次卡片 = 退出拾取（不清已显示结果）。
     */
    toggleEstimate () {
      if (this.activeTool === 'estimate') {
        this.stopEstimatePick()
        this.activeTool = ''
        return
      }
      this.resetEngineStates('estimate')
      try {
        this.startEstimatePick()
      } catch (error) {
        this.$screenToast.info('地图未就绪，暂不可用')
        return
      }
      this.activeTool = 'estimate'
      this.$screenToast.info('配套估算：请在地图上点击一个出让地块')
    },

    startEstimatePick () {
      const Cesium = window.Cesium
      const viewer = window.viewer
      if (!Cesium || !viewer || !viewer.scene) throw new Error('map not ready')
      if (this._estimateHandler) this.stopEstimatePick()
      this._estimateHandler = new Cesium.ScreenSpaceEventHandler(viewer.scene.canvas)
      this._estimateHandler.setInputAction((movement) => {
        this.handleEstimatePick(movement.position)
      }, Cesium.ScreenSpaceEventType.LEFT_CLICK)
    },

    stopEstimatePick () {
      if (this._estimateHandler) {
        try { this._estimateHandler.destroy() } catch (error) { /* 已销毁则忽略 */ }
        this._estimateHandler = null
      }
    },

    handleEstimatePick (position) {
      const viewer = window.viewer
      let picked = null
      try { picked = viewer.scene.pick(position) } catch (error) { return }
      if (!picked) {
        this.$screenToast.info('未拾取到要素，请点击出让地块')
        return
      }
      const info = this.extractPickInfo(picked)
      const props = info.props || {}
      // 字段名宽松匹配（别名可能是中文名 / 编码，全半角括号不定）
      const findVal = (patterns) => {
        const key = Object.keys(props).find((k) => patterns.some((ptn) => k.indexOf(ptn) > -1))
        const v = key ? props[key] : null
        return (v === undefined || v === null || v === '') ? null : String(v)
      }
      const crzdbh = findVal(['出让宗地编号', 'CRZDBH', 'crzdbh'])
      const dkmc = findVal(['地块名称', 'DKMC', 'dkmc'])
      const crzjRaw = findVal(['出让金'])
      if (crzjRaw === null) {
        this.$screenToast.info('该地块属性中没有出让金字段，请确认点击的是经营性用地地块')
        return
      }
      const crzj = Number(crzjRaw)
      if (isNaN(crzj) || crzj <= 0) {
        this.$screenToast.info('出让金字段不是有效数值：' + crzjRaw)
        return
      }
      // 旧系统 ptgsManager 同款公式：ptf = 出让金（亿元）× 10000（万元），
      // 估算区间 = ptf/2 ∓ ptf/2 × 0.3（即 ptf 的 35% ~ 65%）
      const ptf = crzj * 10000
      const num = ptf / 2 * 0.3
      const fmt = (v) => Math.round(v).toLocaleString('zh-CN')
      this.estimateResult = {
        crzdbh: crzdbh || '',
        dkmc: dkmc || '',
        low: fmt(ptf / 2 - num),
        high: fmt(ptf / 2 + num)
      }
    },

    /**
     * 在 SectionalAnalysis 引擎的 DOM（及 body 层兜底）里找文案精确等于
     * 目标模式的叶子节点——模式面板里那两枚按钮的文本层；click 冒泡到
     * 绑定处理器的任意祖先。document 兜底搜索时排除本页子树（左面板
     * 的卡片文案与按钮同名，不能点错）。
     */
    findSectionalModeButton (engine, label) {
      // 模式按钮带图标（按钮内有 img/span 子元素），不能用「纯文本叶子」
      // 条件找——收集文案精确等于目标的所有元素，取**最内层**（没有任何
      // 匹配后代的）即按钮本体，click 冒泡触发绑定处理器。
      const collect = (root) => {
        const out = []
        if (!root || !root.querySelectorAll) return out
        root.querySelectorAll('button, div, span, a, p, li').forEach((node) => {
          if ((node.textContent || '').trim() === label) out.push(node)
        })
        return out
      }
      const pick = (matches, excludeMyPage) => {
        const scoped = excludeMyPage
          ? matches.filter((el) => {
            let p = el
            let mine = false
            while (p) {
              if (p.classList && (('' + p.className).indexOf('analysis-screen') > -1)) { mine = true; break }
              p = p.parentNode
            }
            return !mine
          })
          : matches
        const innermost = scoped.filter((el) => !scoped.some((m) => m !== el && el.contains(m)))
        return innermost[0] || null
      }
      const inEngine = pick(collect(engine && engine.$el), false)
      if (inEngine) return inEngine
      return pick(collect(document), true)
    },

    /**
     * 「管线统计」卡片 → 切换插件统计模式，并把状态同步回卡片。
     * 顺序遵循地图管理页的坑：先切 activeTool 挂载弹窗（弹窗的 $on
     * 监听在挂载后才注册），下一拍再让引擎发「开」事件。
     */
    togglePipeline () {
      if (!this.pipeConfig) {
        this.$screenToast.info('管线统计未配置数据源，请在 GIS 功能配置「管线管理 → 管线统计」里配置后使用')
        return
      }
      const engine = this.$refs.pipeStatisticsEngine
      if (!engine) {
        this.$screenToast.info('管线统计组件未就绪，请稍后再试')
        return
      }
      const turningOn = this.activeTool !== 'pipeline'
      this.closeOtherTools(turningOn ? 'pipeline' : null)
      if (!turningOn) {
        // 关闭：再走一次 btnClick（引擎内部走 cancelChecked 并发 false 收起弹窗）
        try { engine.btnClick() } catch (error) { /* 与开启同款兜底，见下 */ }
        this.pipeActive = false
        this.activeTool = ''
        return
      }
      this.activeTool = 'pipeline'
      this.$nextTick(() => {
        try {
          engine.btnClick()
        } catch (error) {
          // 引擎进入统计模式要访问 window.viewer.scene：?map=0 预览或
          // Cesium 还没就绪时会抛错，兜住并复位，避免「卡片亮着但实际没生效」
          try { if (engine.checked) engine.cancelChecked() } catch (ignored) { /* 复位失败不必再处理 */ }
          if (this.activeTool === 'pipeline') this.activeTool = ''
          this.$screenToast.info('地图未就绪，管线统计暂不可用')
          return
        }
        this.pipeActive = !!engine.checked
        if (!this.pipeActive && this.activeTool === 'pipeline') this.activeTool = ''
      })
    }
  }
}
</script>

<style scoped lang="less">
.analysis-screen {
  position: absolute;
  inset: 0;
  // 整页容器不拦截鼠标：地图必须保持可交互，各面板自己 stage-hit
  pointer-events: none;

  /* ---------------- 右面板壳（与地图管理插件弹窗外壳同位） ---------------- */
  &__title-deco {
    position: absolute;
    right: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    // 与地图管理右面板标题同一位置：文字右缘距右边界 83px
    right: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  // ★ 功能面板接入点：面板框（400×881，panel-right-bg 切图底）。
  //   接入时功能组件渲染在本容器内（引擎弹窗的深度覆盖适配照抄
  //   map/index.vue 的 __road-host / __spatial-host 写法）。
  &__host {
    position: absolute;
    right: 30px;
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;
  }

  /* ---------------- 管线统计：引擎宿主 + 老式浮层壳压平 ----------------
   * 引擎闲置时只剩一个老工具栏按钮壳，整块藏掉（is-idle）；激活后引擎
   * 自画的表单/面板与五个统计结果弹窗都在本面板框内。
   * 五类弹窗（property / diameter / material / diameterSect / depthSect）
   * 的根元素都是老工作台 .pop 壳（300px 宽、青色内辉光、旧标题栏、
   * .pop-left/.pop-right 绝对定位），与右面板完全不搭——统一改成：
   * 铺满面板框、去自带底色和辉光、旧标题栏零高、关闭钮挪到面板右上角。
   * 写法与 map/index.vue 的 __road-host / __spatial-host 一致，必须
   * !important 压制 stargis-function.css 的同名规则。 */
  &__engine-host {
    position: absolute;
    right: 30px;
    top: 149px;
    bottom: 50px;
    width: 400px;
    pointer-events: none;

    &.is-idle {
      display: none;
    }

    // 断面模式激活时提到面板壳（__host）之上：断面对话框要在空壳上方可见
    &.is-front {
      z-index: 5;
    }
  }

  // ★ 断面出图浮层（.pop.bottom-table）专属定位：保持插件原生尺寸
  //   （1024×720 结果面板），锚到 __engine-host（面板框）右下、属性条
  //   上方——与旧工作台「视口右下 bottom:100 / right:50」同位；本宿主
  //   右缘在视口右-30、下缘在视口下-50，故 right/bottom 各补 30/50。
  //   压平规则已按 :not(.bottom-table) 豁免它。
  &__engine-host /deep/ .pop.bottom-table {
    top: auto !important;
    left: auto !important;
    right: 20px !important;
    bottom: 50px !important;
    width: auto !important;
    height: auto !important;
  }

  // ★ 断面模式清单（PopSectionList 弹层的 .list-section，横断 / 纵断
  //   两个模式项）：插件锚 top:-80px 到老工具栏按钮上方，本页引擎宿主
  //   顶在 149 → 清单会顶进页头（与统计类型入口同类坑，真机已复现过）
  //   ——改锚到面板框内部、标题装饰下方。
  &__engine-host /deep/ .list-section {
    top: 60px !important;
    right: 8px !important;
  }

  // ★ 断面出图浮层（.pop.bottom-table，1024×720 结果面板 + 导出/关闭）
  //   也带 .pop 类——下面的压平规则把它按 :not(.bottom-table) 豁免：
  //   400px 面板装不下 1024px 出图，压平会把图片挤爆。专属定位规则见
  //   is-front 块之后。
  &__host /deep/ .pop,
  &__engine-host /deep/ .pop:not(.bottom-table) {
    position: absolute !important;
    top: 0 !important;
    left: 0 !important;
    right: 0 !important;
    bottom: 0 !important;
    width: 100% !important;
    height: 100% !important;
    min-height: 0 !important;
    background: transparent !important;
    box-shadow: none !important;
    border: none !important;
  }

  // 旧标题栏压成零高（不是 display:none——关闭钮是它的子元素且自身
  // 绝对定位，压缩 header 不影响它的位置）
  &__host /deep/ .pop-header,
  &__engine-host /deep/ .pop:not(.bottom-table) .pop-header {
    height: 0 !important;
    overflow: visible;
    background: transparent !important;
  }

  // 旧标题（logo 图 + 文字）与画布层「管线统计」标题重复，隐藏
  &__host /deep/ .pop-title,
  &__engine-host /deep/ .pop:not(.bottom-table) .pop-title {
    display: none !important;
  }

  // 关闭钮保留：贴面板右上角（点它经弹窗内部逻辑同步回卡片状态）
  &__host /deep/ .pop-close,
  &__engine-host /deep/ .pop:not(.bottom-table) .pop-close {
    position: absolute !important;
    top: 6px !important;
    right: 6px !important;
    z-index: 5;
  }

  // 内容区铺满面板剩余高度；插件原样式把 .pop-body 写死 min-height:354px，
  // 统计内容一多只能在固定高度里滚，这里改成纵向吃满剩余空间
  // __host 的弹窗内容区顶部让出功能按钮条的高度：__type-buttons 在
  // top:198（面板顶 149 + 49），管线统计三行 / 标注管理两行，底缘约在
  // 面板内 173px 处——插件工具条（管线/管点/管井…）与统计表单都从
  // 按钮条下方开始排，互不遮挡；内容超高走 .pop-body 自身的纵向滚动。
  // __engine-host 的对话框（管线统计工作台）不加让位，保持原生排布。
  &__host /deep/ .pop-body {
    width: 100% !important;
    min-height: 0 !important;
    max-height: 100% !important;
    overflow-y: auto !important;
    padding: 6px 10px !important;
  }

  // 有类型按钮条的模式（管线统计 / 标注管理）：弹窗内容从按钮条下方
  // 开始（按钮条 top:198、高约 173px）；净距等无按钮条的模式内容顶格
  &__host.has-typestrip /deep/ .pop-body {
    padding-top: 174px !important;
  }

  &__engine-host /deep/ .pop-body {
    width: 100% !important;
    min-height: 0 !important;
    max-height: 100% !important;
    overflow-y: auto !important;
    padding: 6px 10px !important;
  }

  // 图层树/控制面板辅助类（.diameter-layertree-pop 等）只带 z-index，
  // 跟随上面的 .pop 压平规则即可，无需单独覆盖

  // 五个统计弹窗的「收起态入口按钮」壳（.analyse-pipe，带标题的浮层按钮）：
  // 弹窗就挂在本面板子树里，若其收起壳用 fixed 行内定位会顶到页头（真机
  // 已复现）。这里只藏 __host 内不带 .pop 的 .analyse-pipe——弹窗打开后的
  // 面板根带 .pop（走上面的压平规则正常显示），引擎内容在 __engine-host
  // 不受影响；body 层浮层入口由文件末尾的全局规则负责。
  &__host /deep/ .analyse-pipe:not(.pop) {
    display: none !important;
  }

  // ★ 五个统计类型入口（.analyse-pipe：管径分段 / 埋深分段 / 材质分类 /
  //   管径分类 / 点性质分类）由插件手动挂到 body 层——真机验证它们落在
  //   视口右上角页头区，scoped /deep/ 够不着，本文件末尾的全局样式块负责
  //   收敛（见文件底部说明）。

  // ★ 五枚统计类型入口：本组件自绘按钮（栅格两列、宽度占满），点击经
  //   openTypePop 直接发总线事件打开对应统计弹窗；容器自身穿透点击，
  //   按钮本体恢复 pointer-events。
  &__type-buttons {
    position: absolute;
    top: 198px;
    right: 44px;
    width: 372px;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    pointer-events: none;
  }

  &__type-btn {
    height: 36px;
    padding: 0 10px;
    font-size: var(--screen-font-sm);
    line-height: 34px;
    color: #ffffff;
    text-align: left;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    background: linear-gradient(180deg, rgba(0, 120, 220, 0.55), rgba(0, 50, 120, 0.65));
    border: 1px solid rgba(0, 216, 255, 0.45);
    cursor: pointer;
    pointer-events: auto;
  }

  &__type-btn:hover {
    border-color: #00d8ff;
    box-shadow: 0 0 8px rgba(0, 216, 255, 0.4);
  }

  // 成熟度分析（预留面板）：图层开关列表
  &__maturity-list {
    padding: 4px 2px;
  }

  &__maturity-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 2px;
  }

  &__maturity-label {
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub, #82c6ff);
  }

  &__maturity-empty {
    padding: 20px 4px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-mute, #4a7396);
    text-align: center;
  }

  // 配套估算（预留面板 → 已实现）：结果展示
  &__estimate {
    padding: 4px 2px;
  }

  &__estimate-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 2px;
    font-size: var(--screen-font-sm);

    span {
      color: var(--screen-text-sub, #82c6ff);
    }

    b {
      color: #ffffff;
      font-weight: 500;
      text-align: right;
    }
  }

  &__estimate-interval {
    margin-top: 10px;
    padding: 12px 10px;
    text-align: center;
    background: linear-gradient(180deg, rgba(0, 120, 220, 0.4), rgba(0, 50, 120, 0.5));
    border: 1px solid rgba(0, 216, 255, 0.5);

    span {
      display: block;
      font-size: var(--screen-font-sm);
      color: var(--screen-text-sub, #82c6ff);
    }

    b {
      display: block;
      margin-top: 6px;
      font-size: 20px;
      font-weight: 500;
      color: #ffffff;
      text-shadow: 0 0 10px rgba(0, 216, 255, 0.6);
    }

    i {
      font-style: normal;
      font-size: var(--screen-font-sm);
      color: var(--screen-text-sub, #82c6ff);
      margin-left: 4px;
    }
  }

  &__estimate-note {
    margin: 10px 2px 0;
    font-size: 12px;
    color: var(--screen-text-mute, #4a7396);
  }

  &__estimate-empty {
    padding: 20px 4px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-mute, #4a7396);
    text-align: center;
  }

  // 激活中的标注功能：与工具卡片选中态同款的高亮
  &__type-btn.is-active {
    color: #ffffff;
    background: linear-gradient(180deg, rgba(0, 150, 230, 0.75), rgba(0, 60, 140, 0.8));
    border-color: #00d8ff;
    box-shadow: 0 0 10px rgba(0, 216, 255, 0.45), 0 0 10px rgba(0, 216, 255, 0.25) inset;
  }

  // 插件原按钮的停靠区：隐藏保存（引擎重渲染时由 MutationObserver 重搬，
  // 防止浮层入口再次出现在页头上）
  &__plugin-parking {
    display: none !important;
  }

  /* ---------------- 底部「属性表」收起条（与地图管理逐像素同位） ---------------- */
  &__attr {
    position: absolute;
    // 设计稿 x=460..1460、下贴 50（与首页属性表收起态同位）
    left: 460px;
    right: 460px;
    bottom: 50px;
    height: 47px;
  }

  &__attr-bar {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 47px;
    display: block;
    pointer-events: none;
  }

  &__attr-title {
    position: absolute;
    left: 48px;
    // 条高 47、字 22 行高：与首页收起态一致微微下沉
    top: 14px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 22px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__attr-toggle {
    position: absolute;
    // 设计稿图标 17×14 @ (963, 17)，即距右边界 20px
    right: 20px;
    top: 17px;
    width: 17px;
    height: 14px;
    display: block;
    pointer-events: none;
  }
}
</style>

<style lang="less">
/* ★ 全局规则（非 scoped）——五个统计类型入口按钮的收敛
 * ------------------------------------------------------------------
 * .analyse-pipe（管径分段 / 埋深分段 / 材质分类 / 管径分类 / 点性质分类）
 * 的浮层入口是 stargis-function 插件手动挂到 body 层的：组件树不在
 * .analysis-screen 子树里，scoped /deep/ 够不着，只能全局覆盖。
 * 老工作台把它们浮在画布右上角，在本页会顶到页头导航上（真机已复现）。
 *
 * ★ 不能一刀切 display:none：引擎对话框的内容包裹层同样带 .analyse-pipe
 *   类（UMD 里有 .analyse-pipe .analyse-pipe 嵌套规则），一刀切会把面板
 *   里的统计表单一起藏掉（真机已复现——面板只剩标题条）。因此用 :not()
 *   限定「只藏不在本页面子树内的」：body 层浮层入口隐藏，面板内引擎
 *   内容与五个结果弹窗不受影响。
 *
 * 影响面：.analyse-pipe 只存在于管线分析插件家族；老工作台页面
 * （views/maps）未注册路由，当前所有页面均不受影响。:not() 内复杂
 * 选择器需要 Chrome 88+，不支持时整条规则失效，退回浮层入口可见。
 */

.analyse-pipe:not(.analysis-screen .analyse-pipe) {
  display: none !important;
}

/* ★ 断面分析的模式面板（含「横断面分析 / 纵断面分析」两枚 pipecard
 *   按钮 + 「断面分析」标题）——插件手动挂到 body 层的浮层，横/纵断面
 *   模式都由代码程序化驱动（按钮在隐藏 DOM 里照常可点），面板本身
 *   不需要展示，全局隐藏。
 * 根类名从插件 CSS 反查（.pipecard 同 data-v 哈希的组件类：
 * police-iot-pop / iotcontainer），:has(.pipecard) 确保只隐藏「装着
 * 断面模式按钮」的这层，避免误伤同名类的其它插件弹窗。
 * :has() 需要 Chrome 105+；不支持时规则失效、面板退回可见。
 * 已知取舍：统计/出图结果若渲染在这块面板里，会一起被隐藏——
 * 真机出图后如需查看，反馈后改成「出结果时显示」。 */
.police-iot-pop:has(.pipecard),
.iotcontainer:has(.pipecard) {
  display: none !important;
}
</style>
