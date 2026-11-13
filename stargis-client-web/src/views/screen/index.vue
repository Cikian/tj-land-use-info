<template>
  <!--
    天津市经营性用地市政基础设施配套动态监管工作站 —— 首页工作台
    ===============================================================
    按 UI 同事交付的高保真模型逐像素重做：
      docs/高保真/市政-天津市经营性用地市政配套设施动态监管工作站/首页.html

    结构（全部使用设计稿的定尺坐标，画布 1920×1080）：
      真实地图层（不参与缩放，保证 Cesium 交互坐标正确）
      设计稿画布层
        ├─ 全屏蒙版：上 / 下 / 左 / 右四张切图
        ├─ 顶栏     ScreenHeader（bg-top + 标题切图 + 7 项导航 + 时钟 + 用户区）
        ├─ 左面板   HomeStatPanel side=left（出让地块情况统计）
        ├─ 右面板   HomeStatPanel side=right（落实配套情况统计，默认）
        │           └ 右边缘竖排小页签可切到 HomeLayerTree（地图管理 · 图层树插件，懒挂载）
        ├─ 边缘     ScreenSideRail ×2（折叠把手；右侧另带「统计 / 图层树」小页签）
        ├─ 底部     HomeAttrPanel（属性表，市级 / 区级 / 地块预警信息；两侧折叠时左右内缩跟着收）
        └─ 浮层     HomePlotModal（地块预警信息弹窗）
      档案管理 / 收发文 作为整页模块覆盖在画布正文区
      地图管理 作为静态页面叠在画布上（地图保持可交互）

    ★ 2026-10-27 三处布局调整（用户要求）：
      1. 左侧统计拆开：左 = 出让地块情况统计，右 = 落实配套情况统计（原先是一块
         面板里两个页签）；两块面板共用 HomeStatPanel，由 side 决定镜像位置与底图。
      2. 右面板默认展示统计，屏幕右边缘的竖排小页签可切到「图层树」。
      3. 左右面板都能折叠（整体滑出屏幕，屏边只留把手），折叠时底部属性表的
         左右内缩从 460 收到 30、宽度带过渡一起长出来；两侧那两张氛围蒙版
         同时是面板的暗底，所以也跟着一起淡出（否则边缘会剩两条暗带）。

    数据来源：
      loadDashboard() 并发拉取 LandOverviewVO（出让地块情况统计，/land/stat/home/landOverview）
      与 PendingFacilityVO（待落实配套统计，/land/stat/home/pendingFacility）。
      没有数据或接口失败时对应区域保持空白，不使用设计稿演示数据。
  -->
  <screen-stage :max-scale="4" @resize="handleStageResize">
    <!-- 真实地图（真实像素层） -->
    <template #map>
      <s3dm-viewer v-if="mapEnabled" :paused="mapPaused" />
    </template>

    <!-- ===== 「选取地块」模式 =====
         ScreenPickStage 挂在 body（内部 position: fixed + z-index 1250），
         所以这里传的 loader 只是为了描述「地图此刻可用」这一事实。
         ★ 2026-10 起它**不再隐藏任何页面**（原先 activePick 为真时整块画布连蒙版
           一起 opacity: 0）：表单弹窗自己可以拖到一边、背景也不压暗，地图本来就
           随时可操作，再藏页面只会把填到一半的表单一起藏掉。
           它现在只提供两个按钮（取消选择 / 选择完成）与一句提示。 -->
    <screen-pick-stage
      v-if="activePick"
      :map-ready="mapEnabled"
      :source="pickSession.source"
      :title="pickSession.title"
      @cancel="handlePickCancel"
      @done="handlePickDone"
    />

    <!-- ===== 全屏蒙版（设计稿的四张氛围切图） ===== -->
    <!--
      ★ 左右两张同时也是两侧面板的「暗底」，下面那张是底部面板的暗底：
        2026-11 用户反馈「面板折叠 / 拖宽后蒙版还留在原处」——现在三张都跟着
        当前页面的 panelLayout 一起伸缩：宽度 = 面板宽 + 160（设计稿 400 → 560），
        高度 = 下方面板高 - 35（设计稿 321 → 286），折叠时收到 0。
        顶栏那张与面板无关（顶栏永远在），不参与。
        ★ 页面对不上面板的页面（档案 / 提级论证 / 查询统计）不传 style，
          仍用设计稿定尺 560 / 286。
    -->
    <img
      class="land-screen__vignette is-top"
      :src="hf.vignetteTop"
      alt=""
      aria-hidden="true"
    />
    <img
      class="land-screen__vignette is-bottom"
      :src="hf.vignetteBottom"
      :style="bottomVignetteStyle"
      alt=""
      aria-hidden="true"
    />
    <img
      class="land-screen__vignette is-left"
      :src="hf.vignetteLeft"
      :style="leftVignetteStyle"
      alt=""
      aria-hidden="true"
    />
    <img
      class="land-screen__vignette is-right"
      :src="hf.vignetteRight"
      :style="rightVignetteStyle"
      alt=""
      aria-hidden="true"
    />

    <!-- ===== 顶栏 ===== -->
    <!--
      时钟已在顶栏的导航行内（紧跟最后一项「查询统计」右侧，见 ScreenHeader），
      不再压在地图留白区、也不会和整页模块重叠；所有带顶栏导航的页面统一显示。
    -->
    <screen-header
      :title="title"
      :menus="menus"
      :active-menu="activeMenu"
      :user-name="user.name"
      :online="user.online"
      :show-clock="true"
      @menu-change="handleMenuChange"
      @logout="handleLogout"
    />

    <!-- ===== 档案管理：整页模块（「收发文」是它内部的一个页签） ===== -->
    <archive-screen
      v-if="activeMenu === 'archive'"
      ref="archive"
      class="land-screen__module stage-hit"
      :default-tab="archiveTab"
    />

    <!--
      ===== 数据管理：整页模块（左二级导航 + 右侧面板） =====
      ★ 这里**刻意不加 stage-hit**：数据管理是唯一「地图必须继续可用」的整页模块
        （边看地块边录数据）。stage-hit 会把整屏变成 pointer-events: auto，
        整块画布就会吃掉地图的鼠标事件（缩放、拖动全部失效）。
        交互由 data/index.vue 自己在 __nav / __body 两块上显式开启。
    -->
    <data-screen
      v-else-if="activeMenu === 'data'"
      ref="data"
      class="land-screen__module"
      :default-tab="dataTab"
    />

    <!-- ===== 提级论证管理：整页模块（记录型台账：录入 / 查询统计 / 台账 / 审核意见登记） ===== -->
    <escalation-screen
      v-else-if="activeMenu === 'review'"
      ref="escalation"
      class="land-screen__module stage-hit"
      :default-tab="reviewTab"
    />

    <!--
      ===== 地图管理：静态页面（左工具箱 + 右拾取查询 + 底部属性条） =====
      ★ 与数据管理同理**不加 stage-hit**：地图管理页地图必须保持可交互，
        容器 pointer-events: none，各面板自行开启。
    -->
    <map-screen v-else-if="activeMenu === 'map'" ref="map" class="land-screen__module" />

    <!--
      ===== 配套分析管理：静态页面（左分析工具箱 + 右面板壳 + 底部属性条） =====
      ★ 与地图管理同理**不加 stage-hit**：地图必须保持可交互，容器
        pointer-events: none，各面板自行开启。
        11 项分析工具的功能面板本体待接入 stargis-function 对应引擎，
        右侧只亮面板壳，接入点见 analysis/index.vue 顶部说明。
    -->
    <analysis-screen v-else-if="activeMenu === 'analysis'" ref="analysis" class="land-screen__module" />

    <!--
      ===== 查询统计：整页模块（左二级导航 + 条件/分析结果/结果表） =====
      ★ 与数据管理同理**不加 stage-hit**：结果表里常要照着地图核编号，
        容器保持 pointer-events: none，交互由 query/index.vue 自己开启。
    -->
    <query-screen
      v-else-if="activeMenu === 'query'"
      ref="query"
      class="land-screen__module"
      :default-tab="queryTab"
    />

    <!-- ===== 首页工作台 ===== -->
    <!--
      首页的五块：左右统计面板 + 右侧图层树（与统计共用右面板）+ 两条屏边轨道
      + 底部属性表。★ 「选取地块」模式现在**不再隐藏页面**（见 activePick 的说明），
      所以这里没有额外的隐藏包装。
    -->
    <template v-else>
      <!-- ===== 左面板：出让地块情况统计（折叠 = 整体滑出屏幕，把手在屏边） ===== -->
      <!-- :rank 是 { city, district } 两份榜单：面板双卡切换时跟着换，两级不混排 -->
      <!-- :width / :collapsed 来自 panelLayout（可拖动改宽、可折叠，状态永久记住） -->
      <home-stat-panel
        side="left"
        title="出让地块情况统计"
        :section="homeStats.transfer"
        :rank="transferRank"
        rank-title="各区出让排行"
        :loading="dashboardLoading"
        :collapsed="layout.left.collapsed"
        :width="layout.left.size"
        :width-min="layout.left.min"
        :width-max="layout.left.max"
        @width-change="layout.setSize('left', $event)"
      />
      <screen-side-rail
        side="left"
        :collapsed="layout.left.collapsed"
        collapse-text="收起出让统计"
        expand-text="展开出让统计"
        @toggle="layout.toggle('left')"
      />

      <!-- ===== 右面板：默认统计（落实配套情况统计），小页签可切到图层树 ===== -->
      <home-stat-panel
        v-show="rightTab === 'stat'"
        side="right"
        title="落实配套情况统计"
        :section="homeStats.support"
        :rank="supportingRank"
        rank-title="各区配套落实"
        :loading="dashboardLoading"
        :collapsed="layout.right.collapsed"
        :width="layout.right.size"
        :width-min="layout.right.min"
        :width-max="layout.right.max"
        @width-change="layout.setSize('right', $event)"
      />
      <!--
        图层树懒挂载：只有第一次切到「图层树」页签才挂载（那时才拉起
        stargis-function 引擎、才发图层树请求）；之后用 v-show 保活 ——
        来回切换不会重新拉一遍树，勾选的图层显隐状态也不会丢。
      -->
      <home-layer-tree
        v-if="treeMounted"
        v-show="rightTab === 'tree'"
        :collapsed="layout.right.collapsed"
        :width="layout.right.size"
        :width-min="layout.right.min"
        :width-max="layout.right.max"
        @width-change="layout.setSize('right', $event)"
      />
      <screen-side-rail
        side="right"
        :tabs="rightTabs"
        :active-tab="rightTab"
        :collapsed="layout.right.collapsed"
        tabs-label="右侧面板切换"
        collapse-text="收起右侧面板"
        expand-text="展开右侧面板"
        @select="handleRightTab"
        @toggle="layout.toggle('right')"
      />

      <!-- ===== 底部属性表：左右内缩随两侧折叠/拖宽过渡；上沿可拖动改高 ===== -->
      <home-attr-panel
        :tabs="warningTabs"
        :data="warningData"
        :loading="dashboardLoading"
        :left-inset="attrInsets.left"
        :right-inset="attrInsets.right"
        :height="layout.bottom.size"
        :min-height="layout.bottom.min"
        :max-height="layout.bottom.max"
        :collapsed="layout.bottom.collapsed"
        @update:height="layout.setSize('bottom', $event)"
        @update:collapsed="layout.setCollapsed('bottom', $event)"
        @row-action="handleAttrAction"
        @open-plot-warning="plotVisible = true"
      />

      <home-plot-modal
        v-if="plotVisible"
        :data="plotWarning"
        variant="plot"
        @close="plotVisible = false"
      />
      <home-plot-modal
        v-if="warningDetailVisible"
        :data="warningDetail"
        variant="detail"
        @close="warningDetailVisible = false"
      />
    </template>
  </screen-stage>
</template>

<script>
import S3dmViewer from '@/views/maps/S3dmViewer.vue'
import ArchiveScreen from './archive/index.vue'
import DataScreen from './data/index.vue'
import EscalationScreen from './escalation/index.vue'
import MapScreen from './map/index.vue'
import AnalysisScreen from './analysis/index.vue'
import HomeStatPanel from './home/HomeStatPanel.vue'
import HomeLayerTree from './home/HomeLayerTree.vue'
import HomeAttrPanel from './home/HomeAttrPanel.vue'
import HomePlotModal from './home/HomePlotModal.vue'
import QueryScreen from './query/index.vue'
import {
  ScreenHeader,
  ScreenPickStage,
  ScreenSideRail,
  ScreenStage,
  panelInset,
  usePanelLayout
} from '@/components/screen'
import { hf } from '@/assets/screen-blue'
import { queryFacilityDashboard, queryLandDashboard, queryWarningDetail } from '@/api/land/landData'
import { logoutAndGoToLogin } from '@/utils/session'
// 「选取地块」全屏模式的会话状态：表单弹窗在 body 上、覆盖层也在 body 上，
// 两者不在同一棵组件树里，只能共享这份状态（详见 pickSession.js 的说明）
import { endPick, pickState } from './pickSession'

import { headerMenus, systemTitle, warningTabs } from './config'

/**
 * 空明细表。
 * ★ 合计行按 columns 生成，不再写死 plots/roads：两个页签的数值列不同
 *   （出让情况 = 出让地块 / 涉及道路，落实配套 = 配套总数 / 已完成），
 *   写死会让某个页签的合计行凭空多出不属于它的字段。
 * @param {Array} columns 明细列（见 TRANSFER_COLUMNS / SUPPORT_COLUMNS）
 */
function emptyTable (columns) {
  const total = { name: '合计' }
  columns.forEach((col) => {
    // 序号 / 行政区划不是数值列，合计行不参与累加
    if (col.key === 'index' || col.key === 'name') return
    total[col.key] = 0
  })
  return { rows: [], total }
}

function emptyPanelSection (cube, label, columns) {
  return {
    cube,
    stat: { label, value: 0, unit: '宗' },
    split: [
      { key: 'city', label: '市级', value: 0, unit: '宗', percent: 0, tone: 'green' },
      { key: 'district', label: '区级', value: 0, unit: '宗', percent: 0, tone: 'cyan' }
    ],
    columns,
    tables: {
      city: emptyTable(columns),
      district: emptyTable(columns)
    }
  }
}

/**
 * 首页两块统计面板的空数据。
 * ★ 2026-10-27 起两块面板各放一份统计（左出让 / 右配套），不再有页签，
 *   所以这里只返回两份 section，页签那层结构整体去掉。
 */
function emptyHomeStats () {
  return {
    transfer: emptyPanelSection('a', '已出让土地总数', TRANSFER_COLUMNS),
    support: emptyPanelSection('b', '需要落实配套', SUPPORT_COLUMNS)
  }
}

function emptyPlotWarning () {
  return { title: '地块预警信息', total: 0, rows: [] }
}

/**
 * 已经实现为大屏子页面的菜单项：
 *   home    首页工作台
 *   archive 档案管理（整页模块，「收发文」是它内部的一个页签，不占一级菜单）
 *   data    数据管理（经营性用地添加 / 查询）
 *   review  提级论证管理（整页模块：项目录入 / 查询统计 / 资料及台账管理 / 提级论证审批）
 *   map     地图管理（静态页面：左工具箱 / 右拾取查询 / 底部属性条，工具未接功能）
 *   analysis 配套分析管理（静态页面：左分析工具箱 / 右面板壳 / 底部属性条，
 *            11 项工具的功能面板待接 stargis-function 引擎）
 *   query   查询统计（方案 2.3.1（五）：经营性用地查询 / 用地配套项目查询 / 查询导出）
 * 其余仍是「待接入」状态。
 */
const IMPLEMENTED_MENUS = ['home', 'archive', 'data', 'review', 'map', 'analysis', 'query']

/** 档案页允许直接落到某个页签：/screen/archive?tab=doc
 *  ledger / handover / completion = 方案 2.3.2 第 7 / 6 / 8 项（道路验收移交台账、
 *  道路交付及养护协议移交事项、竣工验收项目历史工程资料数字化档案），
 *  与 archive/index.vue 的 tabs / PANELS 保持一致 */
const ARCHIVE_TABS = ['maintain', 'statistics', 'ledger', 'handover', 'completion', 'doc', 'category']

/**
 * 提级论证页允许直接落到某个页签：/screen/review?tab=audit
 * （与 src/views/screen/escalation/index.vue 的 tabs / PANELS 保持一致）
 */
const REVIEW_TABS = ['entry', 'query', 'ledger', 'audit']

/**
 * 数据管理页允许直接落到某个页签：/screen/data?tab=facility
 * （与 src/views/screen/data/index.vue 的 tabs / PANELS、以及后端菜单 sort_no 保持一致）
 * ★ 两处必须同步改：漏改这里白名单的表现是「深链进不去、静默落回默认页签」，
 *   与档案模块 ARCHIVE_TABS 是同一个坑。
 */
const DATA_TABS = ['landImport', 'land', 'facility', 'facilityImport', 'attachment', 'recycle']

/**
 * 查询统计页允许直接落到某个页签：/screen/query?tab=facility
 * （与 src/views/screen/query/constants.js 的 QUERY_TABS 保持一致）
 */
const QUERY_TABS = ['land', 'facility', 'advanced', 'warning']

/**
 * 氛围蒙版与面板的尺寸关系（取自设计稿）：
 *   左右 560 = 面板 400 + 160（蒙版比面板多留一圈渐变）
 *   下方 286 = 下方面板 321 - 35（顶部让出 35 的过渡带）
 * 面板拖宽 / 拖高 / 折叠时按同一关系算，蒙版才不会与面板脱节。
 */
const VIGNETTE_SIDE_EXTRA = 160
const VIGNETTE_BOTTOM_INSET = 35

/**
 * 右面板的两个页签（由 ScreenSideRail 的竖排小页签切换）：
 *   stat 统计（落实配套情况统计，默认）
 *   tree 图层树（地图管理 · stargis-function 图层树，首次切过去才挂载）
 */
const RIGHT_TABS = [
  { key: 'stat', label: '统计' },
  { key: 'tree', label: '图层树' }
]

/**
 * 两块统计面板的明细列（横向位置见 home/HomeStatPanel.vue 的 TH_OFFSET）。
 * ★ 两套列不能合并：两个统计的数值口径不同，合并就会把「已完成配套数」
 *   渲染到「涉及道路」列下（本轮修的就是这个错位）。
 *   key 必须与 TH_OFFSET 的键一一对应，否则单元格 left 会变成 undefinedpx。
 */
const TRANSFER_COLUMNS = [
  { key: 'index', title: '序号' },
  { key: 'name', title: '行政区划' },
  { key: 'plots', title: '出让地块' },
  { key: 'roads', title: '涉及道路' }
]

const SUPPORT_COLUMNS = [
  { key: 'index', title: '序号' },
  { key: 'name', title: '行政区划' },
  { key: 'facilities', title: '配套总数' },
  { key: 'completed', title: '已完成' }
]

/**
 * 市级/区级占比（%），保留 2 位小数。
 * ★ 圆环的弧长用这个精确值；圆环里的文字按高保真只显示整数百分比（28%），
 *   精确值放在 title 提示里 —— 6 个字符（25.38%）会把 56px 的圆环撑破。
 *   旧系统 homePage.js 也是「比值保留 2 位后再 ×100」的口径（159/431 → 37.00%）。
 */
function percent (value, total) {
  return total > 0 ? Number(((Number(value || 0) / total) * 100).toFixed(2)) : 0
}

function toNumber (value) {
  return Number(value || 0)
}

/**
 * 某一数值列在给定维度 ranks 上的合计。
 * 后端只给每行的数值，没有维度级汇总字段，所以合计只能由明细行累加，
 * 保证「行加起来 = 合计」；不能拿另一口径的总数顶替。
 */
function sumRank (ranks, key) {
  return ranks.reduce((sum, item) => sum + toNumber(item[key]), 0)
}

/**
 * 后端 ranks → 明细表（明细行 + 合计行）。
 * ★ 列值由 valueKeys 决定，函数本身不认识任何业务字段：
 *   出让情况传 { plots: 'value', roads: 'roadCount' }，
 *   落实配套传 { facilities: 'facilityCount', completed: 'completedCount' }。
 *   这样两个页签共用一份渲染逻辑，也不会再把某个字段渲染到别的列下。
 * @param {Array} ranks 后端 ranks
 * @param {Object} valueKeys 明细列 key → ranks 字段名（index / name 由表格自己补）
 * @param {Object} totals 合计行里各明细列的取值，键与 valueKeys 一致
 */
function rankTable (ranks, valueKeys, totals) {
  const rows = ranks.map((item) => {
    const row = { name: item.name }
    Object.keys(valueKeys).forEach((key) => {
      row[key] = toNumber(item[valueKeys[key]])
    })
    return row
  })
  const total = { name: '合计' }
  Object.keys(valueKeys).forEach((key) => {
    total[key] = toNumber(totals[key])
  })
  return { rows, total }
}

/**
 * 后端 ranks → 市级 / 区级两份榜单（「排行」视图用）。
 *
 * ★ 为什么必须拆（2026-11-12 需求）：后端 ranks 是「市级项目 + 区级项目」
 *   一张表、按 value 全局倒序（SQL 是 `ORDER BY value DESC, name`），
 *   实测市级 10 行、区级 25 行会交替出现 —— 同一份榜单里既有「河西区（市级）36」
 *   也有「津南区（区级）66」，榜首永远被区级项目占住，
 *   既看不出「市级里哪个区最多」，条形长度也在拿两个量级不同的集合互相比较。
 *   现在按 projectType 拆成两份，面板的「市级 / 区级」双卡（本来就是明细表的
 *   切换器）同时切排行：同一时刻只展示一份，序号与条形都按该组内部归一。
 *
 * @param {Array} cityRanks 已过滤 + 已排序的市级 ranks
 * @param {Array} districtRanks 已过滤 + 已排序的区级 ranks
 * @returns {{city: Array, district: Array}} 每项 { name, value }
 */
function rankGroups (cityRanks, districtRanks) {
  return {
    city: rankRows(cityRanks),
    district: rankRows(districtRanks)
  }
}

/**
 * 组内按 value 倒序排列（sort 稳定，同值行保持后端给的顺序）。
 * ★ 明细表与排行都读这份排好序的数组：两处顺序永远一致，
 *   不会出现「明细第 3 行」和「排行第 3 名」不是同一个区的情况。
 *   显式排序是为了不再依赖后端 ORDER BY —— 那边一改，榜单会悄悄乱掉。
 */
function sortRankRows (ranks) {
  return (ranks || []).slice().sort((a, b) => toNumber(b.value) - toNumber(a.value))
}

/** 单组 ranks → 排行行（只保留榜单要用的两个字段） */
function rankRows (ranks) {
  return (ranks || []).map((item) => ({ name: item.name, value: toNumber(item.value) }))
}

/** 空排行：市级 / 区级各一份，与 HomeStatPanel 的 EMPTY_RANK 结构一致 */
function emptyRankGroups () {
  return { city: [], district: [] }
}

/**
 * 后端地块预警 → 「地块预警信息」弹窗（第三个页签）。
 *
 * ★ 7 个指标**保持数字**，不在这一层拼成「12.34%」：
 *   弹窗要按数值比档位着色（市级 5 档 / 区级 3 档），先格式化再让弹窗反解析，
 *   迟早会出现「颜色和文字对不上」。projectType 也要带上 —— 这张表里
 *   市/区两级混排，只能逐行选档位。
 */
function toPlotWarning (rows) {
  const list = rows || []
  return {
    title: '地块预警信息',
    total: list.length,
    rows: list.map((item) => ({
      id: item.id,
      projectType: item.projectType,
      district: item.district,
      landNo: item.landNo,
      plotName: item.plotName,
      landAcquisition: item.landAcquisition,
      feasibility: item.feasibility,
      preliminaryDesign: item.preliminaryDesign,
      fund: item.fund,
      notStarted: item.notStarted,
      notCompleted: item.notCompleted,
      notHandedOver: item.notHandedOver
    }))
  }
}

export default {
  name: 'LandUseScreen',
  components: {
    S3dmViewer,
    ArchiveScreen,
    DataScreen,
    EscalationScreen,
    MapScreen,
    AnalysisScreen,
    QueryScreen,
    ScreenPickStage,
    HomeStatPanel,
    ScreenSideRail,
    HomeLayerTree,
    HomeAttrPanel,
    HomePlotModal,
    ScreenHeader,
    ScreenStage
  },
  data () {
    const routeQuery = (this.$route && this.$route.query) || {}
    const routePath = (this.$route && this.$route.path) || ''
    // 通过路由 /screen/archive 直达档案页时，顶栏导航同步高亮到「档案管理」；
    // 也支持 /screen?menu=archive 的等价写法。
    // 「收发文」不再是独立菜单，深链 ?menu=doc 或 ?tab=doc 一律归到「档案管理」，
    // 只是把模块内部的初始页签落到「收发文管理」。
    let initialMenu = 'home'
    if (routePath.indexOf('/screen/archive') > -1 || routeQuery.menu === 'archive') {
      initialMenu = 'archive'
    } else if (routePath.indexOf('/screen/data') > -1 || routeQuery.menu === 'data') {
      initialMenu = 'data'
    } else if (routePath.indexOf('/screen/review') > -1 || routeQuery.menu === 'review') {
      // 提级论证管理：整页模块，内部 4 个页签（?tab=entry|query|ledger|audit）
      initialMenu = 'review'
    } else if (routePath.indexOf('/screen/query') > -1) {
      // 查询统计：整页模块，内部 3 个页签（?tab=land|facility|advanced）
      initialMenu = 'query'
    } else if (routeQuery.menu && IMPLEMENTED_MENUS.indexOf(routeQuery.menu) > -1) {
      initialMenu = routeQuery.menu
    }
    // 档案页初始页签。★ 历史深链 `?tab=query`（原「档案查询」页签，已并入档案维护）
    // 不在白名单里，会落到下面的 'maintain' 兜底 —— 正是期望行为，不必额外映射。
    const initialArchiveTab =
      ARCHIVE_TABS.indexOf(routeQuery.tab) > -1
        ? routeQuery.tab
        : routeQuery.menu === 'doc'
          ? 'doc'
          : 'maintain'

    const initialReviewTab = REVIEW_TABS.indexOf(routeQuery.tab) > -1 ? routeQuery.tab : 'entry'

    /**
     * 数据管理页初始页签（?tab=facility 之类的深链）。
     * ★ 默认落在「经营性用地信息录入」而不是第一个页签（批量导入）：
     *   日常使用最多的是逐条录入；批量导入是一次性的年度动作，
     *   把它做成默认会让每次进数据管理都先看到「下载模板」。
     */
    const initialDataTab = DATA_TABS.indexOf(routeQuery.tab) > -1 ? routeQuery.tab : 'land'

    /** 查询统计页初始页签（?tab=facility / ?tab=advanced 之类的深链） */
    const initialQueryTab = QUERY_TABS.indexOf(routeQuery.tab) > -1 ? routeQuery.tab : 'land'

    return {
      hf,
      title: systemTitle,
      menus: headerMenus,
      activeMenu: initialMenu,
      user: {
        name:
          (this.$store &&
            this.$store.getters.userInfo &&
            (this.$store.getters.userInfo.realname || this.$store.getters.userInfo.username)) ||
          'Admin',
        online: null
      },

      /** 档案页初始页签（?tab=doc 之类的深链） */
      archiveTab: initialArchiveTab,
      /** 查询统计页初始页签 */
      queryTab: initialQueryTab,

      /** 提级论证页初始页签（?tab=audit 之类的深链） */
      reviewTab: initialReviewTab,

      /** 数据管理页初始页签（?tab=facility 之类的深链） */
      dataTab: initialDataTab,

      homeStats: emptyHomeStats(),
      /**
       * 两块面板的「排行」数据：按项目分类分成市级 / 区级两份，
       * 面板的双卡切换时跟着换榜单（见 rankGroups 的说明）。
       */
      transferRank: emptyRankGroups(),
      supportingRank: emptyRankGroups(),
      /** 右面板当前页签：stat 统计（默认）/ tree 图层树 */
      rightTab: 'stat',
      rightTabs: RIGHT_TABS,
      /** 图层树是否已挂载过：首次切到「图层树」才置真（懒挂载，之后 v-show 保活） */
      treeMounted: false,
      /**
       * 三块面板的尺寸与折叠状态（左 / 右 / 下）。
       * ★ 2026-11 需求：左右面板可拖动改宽、下方面板可拖动改高、
       *   三块都能折叠；状态按页面持久化（localStorage），切页/刷新后保持。
       *   共用件见 components/screen/panelLayout.js 与 README 6.4。
       * ★ mapLayout / analysisLayout / dataLayout 取的是**同一份模块级单例**：
       *   页面组件内部也用 usePanelLayout('map') 等，父组件拿同一对象只为
       *   渲染氛围蒙版（蒙版要跟着面板宽高 / 折叠一起变，见下面的 computed）。
       */
      layout: usePanelLayout('home'),
      mapLayout: usePanelLayout('map'),
      analysisLayout: usePanelLayout('analysis'),
      dataLayout: usePanelLayout('data'),
      warningData: { city: [], district: [], plot: [] },
      warningTabs,
      plotWarning: emptyPlotWarning(),
      warningDetailVisible: false,
      warningDetail: { title: '预警详情', total: 0, rows: [] },

      /** 地块预警信息弹窗 */
      plotVisible: false,
      /** 接口加载态 */
      dashboardLoading: false,
      /** 画布缩放比（ScreenStage 抛出，供需要真实像素坐标的浮层使用） */
      stageScale: 1,
      /** 是否挂载真实 Cesium 地图：可通过 ?map=0 关闭（便于无地图服务时预览界面） */
      mapEnabled: routeQuery.map !== '0'
    }
  },
  computed: {
    /**
     * 「选取地块」会话状态（共享单例）。
     *
     * ★ 为什么用 computed 直接返回**单例本身**，而不是把它拷进 data：
     *   早先那版写的是 `data.pickSession = Object.assign({}, pickState)` +
     *   `watch: { pickState }` 去同步，结果**覆盖层永远不出现**。
     *   原因是 `pickState` 是模块作用域的 import，从没挂到组件实例上 ——
     *   Vue 2 的 `vm.$watch('pickState', …)` 会对 `vm.pickState` 求值得到
     *   undefined，**不建立任何依赖**，handler 永不触发，activePick 恒为 false。
     *
     *   正确写法就是下面这样：computed 在取值时读到 Vue.observable 的字段，
     *   依赖收集自然成立，模板跟着更新。**不要再拷贝一份到 data 里。**
     */
    pickSession () {
      return pickState
    },

    /**
     * 是否处于「选取地块」模式。
     *
     * ★ 2026-10 用户实测后改口径：这个模式**不再隐藏任何页面**。
     *   原先它让整块画布（顶栏、四张蒙版、各模块）opacity: 0 让位给地图；
     *   现在表单弹窗自己可以拖到一边、背景也不压暗，地图本来就随时可操作，
     *   再藏页面只会把「填到一半的表单」一起藏掉 —— 用户还得先退出选取才能核对。
     *   所以它现在只用来：① 决定是否渲染 ScreenPickStage（两个按钮 + 提示）；
     *   ② 让 mapPaused 别把地图暂停掉（见下）。
     */
    activePick () {
      return pickState.active
    },

    /**
     * 是否暂停 Cesium 渲染。
     * ===============================================================
     * 判据只有一条：**地图此刻是否被人看见**。看不见就没必要每帧重绘。
     *
     * （1）档案管理 / 提级论证管理是整页模块（.land-screen__module 的 inset: 0
     *     铺满画布），地图只从面板之间的 12px 缝隙里露出一点点，而 Cesium 的
     *     默认渲染循环仍然在按 rAF 每帧重绘整屏。
     *
     *     实测（1920×1080，perf/perf-lag.js）在这两个页面上：
     *       主线程占用 45%（Script 39%）、持续出现 80~100ms 的 Long Task、
     *       帧间隔 p99 从 6ms 涨到 18ms、事件循环延迟 p90 从 6ms 涨到 9~11ms
     *       —— 用户感知就是「点什么都要等一下」。
     *     停掉渲染循环后：主线程 45% → 2%，Long Task 消失，帧间隔 p99 回到 6ms。
     *
     * （2）首页的「地块预警信息」弹窗（HomePlotModal）也是一个 inset: 0 的
     *     全画布遮罩，弹窗开着的时候地图同样一眼都看不到，而用户正在里面
     *     滚动表格、翻页——这正是最不该被渲染循环抢主线程的时刻。
     *
     * 注意不要写成「非 home 即暂停」：顶栏还有若干「待接入」菜单，点它们时
     * activeMenu 会变成那个 key，但页面回落到首页版式、地图是可见的，
     * 那种情况绝不能停。数据管理页也是「只保留菜单 + 地图」（见 data/index.vue
     * 的注释），必须保持渲染。
     *
     * （3）「选取地块」全屏模式**反过来**：整块画布都藏起来了，地图是唯一被看见的东西，
     *     必须保持渲染 —— 所以它不出现在下面任何一条「暂停」判据里。
     *     唯一的例外是 activeMenu 恰好是档案 / 提级论证（地图被整页模块盖住）时
     *     用户又从那里的表单点了「选取地块」：那时先恢复渲染再进选取模式，
     *     否则用户会看到一张完全冻结的地图。见 activePick 的处理。
     */
    mapPaused () {
      // 选取模式优先：地图正在被使用，无论当前是哪个页面都要渲染
      if (this.activePick) return false
      const fullPageModule = this.activeMenu === 'archive' || this.activeMenu === 'review'
      // 这两个弹窗都渲染 HomePlotModal（position:absolute + inset:0 的全画布遮罩）
      const coveredByModal = this.plotVisible || this.warningDetailVisible
      return fullPageModule || coveredByModal
    },

    /**
     * 当前是否首页版式。
     * 首页与「尚未接入」的菜单都落到模板最后的 v-else 分支，共用同一套面板。
     */
    isHomeLayout () {
      return this.activeMenu === 'home' || IMPLEMENTED_MENUS.indexOf(this.activeMenu) === -1
    },

    /**
     * 当前页面用于氛围蒙版的 panelLayout（没有面板的页面返回 null）。
     *
     * ★ 为什么蒙版要放在父组件：四张切图是跨页共用的背景层（在页面模块之前渲染），
     *   而各页面板状态在各自组件里。好在 usePanelLayout 是模块级单例 ——
     *   父组件拿到的 mapLayout / analysisLayout / dataLayout 与子页面是同一个
     *   响应式对象，子页面一拖动，这里就跟着重算。
     *
     * ★ 没有三块面板的页面（档案 / 提级论证 / 查询统计）返回 null：
     *   模板不传 style，蒙版保持设计稿定尺（560 / 286），行为与改造前一致。
     */
    vignetteLayout () {
      if (this.isHomeLayout) return this.layout
      if (this.activeMenu === 'map') return this.mapLayout
      if (this.activeMenu === 'analysis') return this.analysisLayout
      if (this.activeMenu === 'data') return this.dataLayout
      return null
    },

    /**
     * 左右氛围蒙版的宽度：设计稿 560 = 面板 400 + 160（面板占 30..430，
     * 蒙版铺到 560 多留 130 的渐变）。面板拖宽时按同一关系增长；
     * 折叠时收到 0（与面板滑出同一个 320ms）。
     */
    leftVignetteStyle () {
      const layout = this.vignetteLayout
      if (!layout) return null
      return { width: layout.left.collapsed ? '0px' : `${layout.left.size + VIGNETTE_SIDE_EXTRA}px` }
    },

    rightVignetteStyle () {
      const layout = this.vignetteLayout
      if (!layout) return null
      return { width: layout.right.collapsed ? '0px' : `${layout.right.size + VIGNETTE_SIDE_EXTRA}px` }
    },

    /**
     * 底部氛围蒙版的高度：设计稿 286 = 下方面板 321 - 35。面板拖高 / 折叠时
     * 按同一关系变（折叠态 47px 的收起条不带蒙版，直接收到 0）。
     */
    bottomVignetteStyle () {
      const layout = this.vignetteLayout
      if (!layout) return null
      const height = layout.bottom.collapsed ? 0 : Math.max(0, layout.bottom.size - VIGNETTE_BOTTOM_INSET)
      return { height: `${height}px` }
    },

    /**
     * 底部属性表的左右内缩。
     * 展开时 = 面板宽 + 两侧各 30（设计稿 400 + 60 = 460）；
     * 对应侧栏折叠后只剩 30 的屏边留白，属性表因此变宽；面板被拖宽时
     * 内缩也跟着变大（否则属性表会压到面板底下）。口径统一在 panelInset。
     * 过渡写在 HomeAttrPanel 的 CSS 里（height / left / right 各 320ms），
     * 与面板滑出 / 拖动条的时长一致 —— 两者一起动，不会出现错帧。
     */
    attrInsets () {
      return {
        left: panelInset(this.layout, 'left'),
        right: panelInset(this.layout, 'right')
      }
    }
  },
  watch: {
    /**
     * 切换顶栏菜单时把正在进行的选取会话收掉。
     *
     * ★ 必须走 finishPick（而不是直接 endPick）：
     *   finishPick 会「先快照 token/source → 广播结束事件 → 再 endPick」，
     *   发起的那张表单才能收到事件、清掉自己的会话令牌。
     *   直接 endPick 只会静默关掉会话，表单会以为选取还在进行中。
     *
     * ★ 这里曾写错一版：`endPick(this.pickToken)` —— `this.pickToken` 是个
     *   从未被赋值的 data 字段（恒为空串），而 endPick 的守卫是
     *   `if (token && token !== state.token)`，空串会把守卫整个跳过，
     *   于是「碰巧能关掉会话，但绕过了令牌校验、也不广播事件」。
     *   现在统一走 finishPick，不再依赖那个巧合。
     */
    activeMenu () {
      if (this.activePick) this.finishPick(null)
    }
  },
  mounted () {
    this.loadDashboard()
    // 大屏页把 <body> 也刷成深海蓝：窗口边缘万一有 1~3px 没被内容盖住（分数像素 /
    // 浏览器缩放 / 嵌入容器留边），露出来的是这个深色底而不是页面默认白底。
    // ⚠ 只在大屏页挂这个类、离开时摘掉 —— 登录页是浅色主题，不能被一起染黑。
    document.body.classList.add('is-screen-dark')
    // 刷新页面时如果正处于选取模式（会话不落盘，正常不会出现），兜底清一次，
    // 避免留下「一张没有出口的全屏地图」——那种状态用户只能刷新页面。
    if (pickState.active) endPick(pickState.token)
  },
  beforeDestroy () {
    document.body.classList.remove('is-screen-dark')
  },
  methods: {
    /** 并发拉取宗地与配套两个看板；失败或没有数据时对应区域保持空白 */
    loadDashboard () {
      this.dashboardLoading = true
      return Promise.all([queryLandDashboard(), queryFacilityDashboard()])
        .then(([landResponse, facilityResponse]) => {
          if (landResponse && landResponse.success) {
            this.applyLandDashboard(landResponse.result || {})
          } else {
            this.homeStats = Object.assign({}, this.homeStats, { transfer: emptyPanelSection('a', '已出让土地总数', TRANSFER_COLUMNS) })
            this.transferRank = emptyRankGroups()
            this.$screenToast.error((landResponse && landResponse.message) || '宗地统计加载失败')
          }
          if (facilityResponse && facilityResponse.success) {
            this.applyFacilityDashboard(facilityResponse.result || {})
          } else {
            this.homeStats = Object.assign({}, this.homeStats, { support: emptyPanelSection('b', '需要落实配套', SUPPORT_COLUMNS) })
            this.supportingRank = emptyRankGroups()
            this.warningData = { city: [], district: [], plot: [] }
            this.plotWarning = emptyPlotWarning()
            this.$screenToast.error((facilityResponse && facilityResponse.message) || '配套统计加载失败')
          }
        })
        .catch((error) => {
          this.homeStats = emptyHomeStats()
          this.transferRank = emptyRankGroups()
          this.supportingRank = emptyRankGroups()
          this.warningData = { city: [], district: [], plot: [] }
          this.plotWarning = emptyPlotWarning()
          this.$screenToast.error((error && error.message) || '首页数据加载失败')
        })
        .finally(() => {
          this.dashboardLoading = false
        })
    },

    /**
     * 宗地总览 → 左面板「出让情况」。
     * ranks 带 projectType：市级项目进市级子表、区级项目进区级子表；
     * 「排行」视图也按同样的切分各排各的（见 rankGroups）。
     * 每行 value = 出让地块数、roadCount = 涉及道路数（字段名以后端契约为准）。
     */
    applyLandDashboard (data) {
      const total = toNumber(data.total)
      const city = toNumber(data.cityCount)
      const district = toNumber(data.districtCount)
      const ranks = data.ranks || []
      // 先按项目分类过滤、再按 value 倒序：明细表与排行共用这一份顺序
      const cityRanks = sortRankRows(ranks.filter((item) => item.projectType === '市级项目'))
      const districtRanks = sortRankRows(ranks.filter((item) => item.projectType === '区级项目'))

      this.homeStats = Object.assign({}, this.homeStats, {
        transfer: {
          cube: 'a',
          stat: { label: '已出让土地总数', value: total, unit: '宗' },
          split: [
            { key: 'city', label: '市级', value: city, unit: '宗', percent: percent(city, total), tone: 'green' },
            { key: 'district', label: '区级', value: district, unit: '宗', percent: percent(district, total), tone: 'cyan' }
          ],
          columns: TRANSFER_COLUMNS,
          tables: {
            // 合计行的出让地块数用 overview 的 cityCount / districtCount，
            // 涉及道路没有维度级汇总字段，只能由该维度明细行累加
            city: rankTable(cityRanks, { plots: 'value', roads: 'roadCount' }, {
              plots: city,
              roads: sumRank(cityRanks, 'roadCount')
            }),
            district: rankTable(districtRanks, { plots: 'value', roads: 'roadCount' }, {
              plots: district,
              roads: sumRank(districtRanks, 'roadCount')
            })
          }
        }
      })

      this.transferRank = rankGroups(cityRanks, districtRanks)
    },

    /**
     * 待落实配套 → 左面板「落实配套」+ 底部属性表预警。
     * ranks 的 value = 待落实配套的宗地数、facilityCount = 配套总数、
     * completedCount = 已完成配套数——三者口径不同，不能互相顶替
     * （本轮修的就是「已完成」被渲染到「涉及道路」列下）。
     * 顶部统计仍用 landTotal（需要落实配套的宗地数）；
     * 双卡用 cityLandCount / districtLandCount（旧系统口径 = 市级 + 区级 actionable 宗地数）。
     */
    applyFacilityDashboard (data) {
      const total = toNumber(data.landTotal)
      const city = toNumber(data.cityLandCount)
      const district = toNumber(data.districtLandCount)
      const ranks = data.ranks || []
      // 同出让面板：先按项目分类过滤、再按 value 倒序，明细表与排行共用一份顺序
      const cityRanks = sortRankRows(ranks.filter((item) => item.projectType === '市级项目'))
      const districtRanks = sortRankRows(ranks.filter((item) => item.projectType === '区级项目'))

      this.homeStats = Object.assign({}, this.homeStats, {
        support: {
          cube: 'b',
          stat: { label: '需要落实配套', value: total, unit: '宗' },
          split: [
            { key: 'city', label: '市级', value: city, unit: '宗', percent: percent(city, total), tone: 'green' },
            { key: 'district', label: '区级', value: district, unit: '宗', percent: percent(district, total), tone: 'cyan' }
          ],
          columns: SUPPORT_COLUMNS,
          tables: {
            // 配套总数 / 已完成都没有维度级汇总字段，合计行按明细行累加，
            // 保证「行加起来 = 合计」，不拿 landTotal 之类的另一口径顶替
            city: rankTable(cityRanks, { facilities: 'facilityCount', completed: 'completedCount' }, {
              facilities: sumRank(cityRanks, 'facilityCount'),
              completed: sumRank(cityRanks, 'completedCount')
            }),
            district: rankTable(districtRanks, { facilities: 'facilityCount', completed: 'completedCount' }, {
              facilities: sumRank(districtRanks, 'facilityCount'),
              completed: sumRank(districtRanks, 'completedCount')
            })
          }
        }
      })

      this.supportingRank = rankGroups(cityRanks, districtRanks)
      this.warningData = Object.assign({ city: [], district: [], plot: [] }, data.warnings || {})
      this.plotWarning = toPlotWarning(data.warnings && data.warnings.plot)
    },

    handleStageResize (scale) {
      this.stageScale = scale
    },

    /* ---------------- 右侧面板：统计 / 图层树 切换 ---------------- */

    /**
     * 点右边缘竖排小页签切换右面板内容。
     * ★ 顺手展开：折叠状态下点页签，用户意图显然是「看这个内容」，
     *   只切内容不展开会让面板继续留在屏幕外，看起来像点了没反应。
     * ★ 首次切到「图层树」才置 treeMounted（懒挂载），之后 v-show 保活。
     */
    handleRightTab (key) {
      this.rightTab = key
      if (key === 'tree') this.treeMounted = true
      this.layout.setCollapsed('right', false)
    },

    /* ---------------- 「添加地块」绘制模式 ---------------- */

    /**
     * 取消绘制。
     * 结束会话即可：覆盖层是 v-if 绑在 activePick 上，状态一变它就自己卸载，
     * 地图与整块画布原样露回来（画布从未被销毁，表单里填到一半的内容也不会丢）。
     */
    handlePickCancel () {
      this.finishPick(null)
    },

    /**
     * 完成绘制。
     * @param {{geojson: string, vertexCount: number}|null} geometry 画好的地块图形；
     *   结构由 drawPolygon.js 的 getGeoJSON() 定义，null = 没画出有效多边形。
     *
     * ★ 拿不到图形时**保留字段原值**是最安全的做法 —— 静默把坐标字段清空，
     *   用户会以为「画完了但没生效」，而实际是「根本没画」。
     *   本方法只做透传：geometry 原样交给发起方（按 session.source 分发到
     *   表单的对应字段），写回逻辑在 LandFormModal / FacilityFormModal 的
     *   handlePickFinished 里。
     */
    handlePickDone (geometry) {
      this.finishPick(geometry || null)
    },

    /**
     * 结束一次选取会话（取消 / 完成共用）。
     *
     * ★ 读取顺序必须严格是「先快照 token 与 source → 广播 → 最后 endPick」：
     *   pickSession 现在是**单例本身**（computed 直接返回 pickState），
     *   endPick 会把 token/source 一起清空 —— 先 endPick 再读就会拿到空串，
     *   事件名退化成 'landMapPickFinished'，配套表单永远收不到结束通知。
     * @param {{geojson: string, vertexCount: number}|null} geometry 画好的图形（可为 null）
     */
    finishPick (geometry) {
      const token = pickState.token
      const source = pickState.source
      // 先广播再结束：表单要用 token 校验「这是不是我发起的那次会话」
      this.$bus.$emit(this.pickFinishedEvent(source), { token, geometry })
      endPick(token)
    },

    /**
     * 按发起方拼事件名。
     * ★ 为什么要分事件名而不是共用一个：同页可能同时开着「新增宗地」与
     *   「新增配套」两个表单（都是常驻渲染的弹窗），共用一个事件名会让两边
     *   同时收到对方的选取结果。token 校验是第二道保险。
     * @param {string} source 'land' / 'facility'
     * @returns {string}
     */
    pickFinishedEvent (source) {
      return source === 'facility' ? 'facilityMapPickFinished' : 'landMapPickFinished'
    },

    /**
     * 顶栏用户区「退出登录」。
     * 与「登录态失效」共用同一条出口（utils/session.js）：
     * 清掉中台 + jeecg 两套登录态、尽力通知两个后端注销，然后回到登录页。
     */
    handleLogout () {
      logoutAndGoToLogin()
    },

    handleMenuChange (key) {
      this.activeMenu = key
      this.plotVisible = false

      // 从深链 /screen/archive 进来后，点顶栏切走时把地址也换回 /screen，
      // 否则地址栏和界面不一致（同一个组件实例，replace 不会重新挂载，
      // 所以这里的 activeMenu 不会被 data() 覆盖掉）。
      if (
        key !== 'archive' &&
        key !== 'data' &&
        key !== 'review' &&
        this.$route &&
        (this.$route.path.indexOf('/screen/archive') > -1 ||
          this.$route.path.indexOf('/screen/data') > -1 ||
          this.$route.path.indexOf('/screen/review') > -1) &&
        this.$router &&
        this.$router.replace
      ) {
        // vue-router 3.1+ 的 replace 返回 promise，导航被中断时会 reject，静默吃掉即可
        const navigation = this.$router.replace({ path: '/screen' })
        if (navigation && typeof navigation.catch === 'function') navigation.catch(() => {})
      }

      if (IMPLEMENTED_MENUS.indexOf(key) > -1) return

      // 未实现的模块给出明确反馈，而不是静默什么都不发生
      this.$screenToast.info(`「${this.menuLabel(key)}」模块待接入`)
    },

    menuLabel (key) {
      const hit = this.menus.find((item) => item.key === key)
      return hit ? hit.label : key
    },

    /**
     * 预警行「操作 → 查看」：下钻到该行政区下的宗地明细。
     *
     * 对应旧系统 homePage.js 的 seeDataInfo717 / seeDataInfo0805
     * （→ byPageQuery），弹一张「地块预警信息」表，一行 = 一宗地。
     *
     * @param {Object} row    预警行（district 是分组名：市级 = xzqh、区级 = xzqh2）
     * @param {string} tabKey 当前页签 key（city / district），由 HomeAttrPanel 一起抛出。
     *   ★ 为什么用页签而不是从 row.id 里认「区级项目」前缀：靠中文字符串判断业务状态
     *   是旧系统 changeShowInfo() 的老毛病（需求清单里点名要求改掉），
     *   而且「地块预警信息」页签里的行并不属于这两张表。
     */
    handleAttrAction (row, tabKey) {
      const projectType = tabKey === 'district' ? 'district' : 'city'
      const title = `${(row && row.district) || ''}地块预警信息`
      this.warningDetail = { title, total: 0, rows: [] }
      this.warningDetailVisible = true
      queryWarningDetail({ projectType, district: row && row.district })
        .then((response) => {
          if (!response || !response.success) {
            this.$screenToast.error((response && response.message) || '预警详情加载失败')
            return
          }
          const rows = response.result || []
          this.warningDetail = {
            title,
            total: rows.length,
            rows: rows.map((item) => ({
              id: item.id,
              // 逐行带项目分类：弹窗按它选阈值档位（市级 5 档 / 区级 3 档）
              projectType: item.projectType,
              landNo: item.landNo,
              plotName: item.plotName,
              facilityCount: item.facilityCount,
              landAcquisition: item.landAcquisition,
              feasibility: item.feasibility,
              preliminaryDesign: item.preliminaryDesign,
              fund: item.fund,
              notStarted: item.notStarted,
              notCompleted: item.notCompleted,
              notHandedOver: item.notHandedOver
            }))
          }
        })
        .catch((error) => {
          this.$screenToast.error((error && error.message) || '预警详情加载失败')
        })
    }
  }
}
</script>

<style scoped lang="less">
.land-screen {
  /*
     * 四张氛围蒙版：随设计画布拉伸铺满。
     * ⚠ 这些是 <img>（替换元素）：绝对定位下只写 left/right（或 top/bottom）
     *   而 width/height 留 auto 时，浏览器会用图片的**原始尺寸**，
     *   right/bottom 被忽略 —— 表现为蒙版盖不到屏幕右边缘/下边缘，
     *   露出没被压暗的一条亮边。所以宽高必须显式写出来。
     */
  &__vignette {
    position: absolute;
    display: block;
    pointer-events: none;
    /*
     * ★ 2026-11：左右 / 下方蒙版要跟面板一起伸缩与折叠（用户反馈「面板收起了、
     *   黑色遮罩还留在原处」）。宽高由模板内联下发（当前页面的 panelLayout），
     *   过渡时长与面板滑出 / 属性表收起一致；拖动面板时由全局规则关掉过渡，
     *   否则蒙版会用 320ms 追指针。
     */
    transition: width var(--screen-duration-slow) var(--screen-ease),
      height var(--screen-duration-slow) var(--screen-ease),
      opacity var(--screen-duration-slow) var(--screen-ease);

    &.is-top {
      left: 0;
      top: 0;
      width: 100%;
      height: 223px;
    }

    &.is-bottom {
      left: 0;
      bottom: 0;
      width: 100%;
      height: 286px;
    }

    &.is-left {
      left: 0;
      top: 0;
      width: 560px;
      height: 100%;
    }

    &.is-right {
      right: 0;
      top: 0;
      width: 560px;
      height: 100%;
    }
  }

  /*
   * 整页模块（档案管理）铺满整个设计画布：
   * 模块内部自己按设计稿坐标摆放（页标题在 95..141、页签条在 149..195、
   * 正文在 207..(底-40)），这样它和首页的版式共用同一套坐标。
   */
  &__module {
    position: absolute;
    inset: 0;
  }
}
</style>

<style lang="less">
/*
  ⚠ 非 scoped：这条规则要作用到 <body>（大屏页最底层底色）。
  为什么不能写在 scoped 块里：scoped 会给选择器加 data-v 属性，body 上没有那个属性。
  为什么需要：真机截图里窗口四边有一条 1~3px 的白缝 —— 大屏底色是 #04091a，
  那圈白只能是「内容没铺到的那点面积露出了页面默认白底」。body 背景会铺满整个
  文档画布，所以只要它在，露出来的就一定是深色。
  类名由 mounted 挂上、beforeDestroy 摘掉（见 script），登录页等浅色页面不受影响。
*/
body.is-screen-dark {
  background: var(--screen-bg-deep);
}

/*
 * 拖动面板尺寸期间关掉氛围蒙版的宽高过渡：
 * 蒙版要跟着面板实时伸缩，320ms 的缓动会让它明显落后于指针
 * （body 上的 .is-resizing-panel 由 ScreenResizeBar 在 pointerdown 时挂、pointerup 时摘）。
 */
body.is-resizing-panel .land-screen__vignette {
  transition: none;
}
</style>
