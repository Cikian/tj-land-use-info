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
        ├─ 顶栏     ScreenHeader（bg-top + 标题切图 + 8 项导航 + 时钟）
        ├─ 左面板   HomeLeftPanel（出让地块情况统计，双页签）
        ├─ 右面板   HomeLayerTree（地图管理 · 图层面板）
        ├─ 底部     HomeAttrPanel（属性表，市级 / 区级 / 地块预警信息）
        └─ 浮层     HomePlotModal（地块预警信息弹窗）
      档案管理 / 收发文 作为整页模块覆盖在画布正文区

    数据来源：
      loadDashboard() 并发拉取 LandDashboardVO（宗地）与
      FacilityDashboardVO（配套）。没有数据或接口失败时对应区域保持空白，
      不使用设计稿演示数据。
  -->
  <screen-stage :max-scale="4" @resize="handleStageResize">
    <!-- 真实地图（真实像素层） -->
    <template #map>
      <s3dm-viewer v-if="mapEnabled" />
    </template>

    <!-- ===== 全屏蒙版（设计稿的四张氛围切图） ===== -->
    <img class="land-screen__vignette is-top" :src="hf.vignetteTop" alt="" aria-hidden="true" />
    <img class="land-screen__vignette is-bottom" :src="hf.vignetteBottom" alt="" aria-hidden="true" />
    <img class="land-screen__vignette is-left" :src="hf.vignetteLeft" alt="" aria-hidden="true" />
    <img class="land-screen__vignette is-right" :src="hf.vignetteRight" alt="" aria-hidden="true" />

    <!-- ===== 顶栏 ===== -->
    <!--
      时钟只在首页显示：高保真里时钟位于 (477,115)，落在中间的地图留白区；
      档案管理 / 收发文是整页模块，横向铺满后会和时钟重叠，因此隐藏。
    -->
    <screen-header
      :title="title"
      :menus="menus"
      :active-menu="activeMenu"
      :user-name="user.name"
      :online="user.online"
      :show-clock="activeMenu === 'home'"
      @menu-change="handleMenuChange"
    />

    <!-- ===== 档案管理：整页模块（「收发文」是它内部的一个页签） ===== -->
    <archive-screen
      v-if="activeMenu === 'archive'"
      ref="archive"
      class="land-screen__module stage-hit"
      :default-tab="archiveTab"
    />

    <!-- ===== 数据管理：只保留左侧菜单和地图，点击菜单项再弹窗 ===== -->
    <data-screen v-else-if="activeMenu === 'data'" />

    <!-- ===== 首页工作台 ===== -->
    <template v-else>
      <home-left-panel
        :panel="leftPanel"
        :transfer-rank="transferRank"
        :supporting-rank="supportingRank"
        :loading="dashboardLoading"
      />
      <home-layer-tree @layer-visibility-change="handleLayerVisibility" />
      <home-attr-panel
        :tabs="warningTabs"
        :data="warningData"
        :loading="dashboardLoading"
        @row-action="handleAttrAction"
      />

      <home-plot-modal
        v-if="plotVisible"
        :data="plotWarning"
        @close="plotVisible = false"
        @row-action="handleAttrAction"
      />
      <home-plot-modal
        v-if="warningDetailVisible"
        :data="warningDetail"
        @close="warningDetailVisible = false"
      />
    </template>
  </screen-stage>
</template>

<script>
import S3dmViewer from '@/views/maps/S3dmViewer.vue'
import ArchiveScreen from './archive/index.vue'
import DataScreen from './data/index.vue'
import HomeLeftPanel from './home/HomeLeftPanel.vue'
import HomeLayerTree from './home/HomeLayerTree.vue'
import HomeAttrPanel from './home/HomeAttrPanel.vue'
import HomePlotModal from './home/HomePlotModal.vue'
import { ScreenHeader, ScreenStage } from '@/components/screen'
import { hf } from '@/assets/screen-blue'
import { queryFacilityDashboard, queryLandDashboard, queryWarningDetail } from '@/api/land/landData'

import { headerMenus, systemTitle, warningTabs } from './config'

function emptyPanelSection (cube, label) {
  return {
    cube,
    stat: { label, value: 0, unit: '宗' },
    split: [
      { key: 'city', label: '市级', value: 0, unit: '宗', percent: 0, tone: 'green' },
      { key: 'district', label: '区级', value: 0, unit: '宗', percent: 0, tone: 'cyan' },
    ],
    columns: PANEL_COLUMNS,
    tables: {
      city: { rows: [], total: { name: '合计', plots: 0, roads: 0 } },
      district: { rows: [], total: { name: '合计', plots: 0, roads: 0 } },
    },
  }
}

function emptyLeftPanel () {
  return {
    tabs: [
      { key: 'transfer', label: '出让情况' },
      { key: 'support', label: '落实配套情况' },
    ],
    transfer: emptyPanelSection('a', '已出让土地总数'),
    support: emptyPanelSection('b', '需要落实配套'),
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
 * 其余仍是「待接入」状态。
 */
const IMPLEMENTED_MENUS = ['home', 'archive', 'data']

/** 档案页允许直接落到某个页签：/screen/archive?tab=doc */
const ARCHIVE_TABS = ['maintain', 'query', 'statistics', 'doc', 'category']

/** 左面板明细表的列（位置见 home/HomeLeftPanel.vue 的 TH_OFFSET） */
const PANEL_COLUMNS = [
  { key: 'index', title: '序号' },
  { key: 'name', title: '行政区划' },
  { key: 'plots', title: '出让地块' },
  { key: 'roads', title: '涉及道路' },
]

function percent (value, total) {
  return total > 0 ? Number(((Number(value || 0) / total) * 100).toFixed(2)) : 0
}

function toNumber (value) {
  return Number(value || 0)
}

function percentText (value) {
  return `${toNumber(value)}%`
}

function rankTable (ranks, roadKey, plotTotal) {
  const rows = ranks.map((item) => ({
    name: item.name,
    plots: toNumber(item.value),
    roads: toNumber(item[roadKey]),
  }))
  return {
    rows,
    total: {
      name: '合计',
      plots: plotTotal,
      roads: rows.reduce((sum, item) => sum + item.roads, 0),
    },
  }
}

function toPlotWarning (rows) {
  const list = rows || []
  return {
    title: '地块预警信息',
    total: list.length,
    rows: list.map((item) => ({
      id: item.id,
      district: item.district,
      landNo: item.landNo,
      plotName: item.plotName,
      landAcquisition: percentText(item.landAcquisition),
      feasibility: percentText(item.feasibility),
      preliminaryDesign: percentText(item.preliminaryDesign),
      fund: percentText(item.fund),
      notStarted: percentText(item.notStarted),
      notCompleted: percentText(item.notCompleted),
      notHandedOver: percentText(item.notHandedOver),
    })),
  }
}

export default {
  name: 'LandUseScreen',
  components: {
    S3dmViewer,
    ArchiveScreen,
    DataScreen,
    HomeLeftPanel,
    HomeLayerTree,
    HomeAttrPanel,
    HomePlotModal,
    ScreenHeader,
    ScreenStage,
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
    } else if (routeQuery.menu && IMPLEMENTED_MENUS.indexOf(routeQuery.menu) > -1) {
      initialMenu = routeQuery.menu
    }
    const initialArchiveTab =
      ARCHIVE_TABS.indexOf(routeQuery.tab) > -1
        ? routeQuery.tab
        : routeQuery.menu === 'doc'
          ? 'doc'
          : 'maintain'

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
        online: null,
      },

      /** 档案页初始页签（?tab=doc 之类的深链） */
      archiveTab: initialArchiveTab,

      leftPanel: emptyLeftPanel(),
      transferRank: [],
      supportingRank: [],
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
      mapEnabled: routeQuery.map !== '0',
    }
  },
  mounted () {
    this.loadDashboard()
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
            this.leftPanel = Object.assign({}, this.leftPanel, { transfer: emptyPanelSection('a', '已出让土地总数') })
            this.transferRank = []
            this.$screenToast.error((landResponse && landResponse.message) || '宗地统计加载失败')
          }
          if (facilityResponse && facilityResponse.success) {
            this.applyFacilityDashboard(facilityResponse.result || {})
          } else {
            this.leftPanel = Object.assign({}, this.leftPanel, { support: emptyPanelSection('b', '需要落实配套') })
            this.supportingRank = []
            this.warningData = { city: [], district: [], plot: [] }
            this.plotWarning = emptyPlotWarning()
            this.$screenToast.error((facilityResponse && facilityResponse.message) || '配套统计加载失败')
          }
        })
        .catch((error) => {
          this.leftPanel = emptyLeftPanel()
          this.transferRank = []
          this.supportingRank = []
          this.warningData = { city: [], district: [], plot: [] }
          this.plotWarning = emptyPlotWarning()
          this.$screenToast.error((error && error.message) || '首页数据加载失败')
        })
        .finally(() => {
          this.dashboardLoading = false
        })
    },

    /**
     * 宗地统计 → 左面板「出让情况」。
     * ranks 带 projectType：市级项目进市级子表，区级项目进区级子表。
     */
    applyLandDashboard (data) {
      const total = toNumber(data.total)
      const city = toNumber(data.cityCount)
      const district = toNumber(data.districtCount)
      const ranks = data.ranks || []
      const cityRanks = ranks.filter((item) => item.projectType === '市级项目')
      const districtRanks = ranks.filter((item) => item.projectType === '区级项目')

      this.leftPanel = Object.assign({}, this.leftPanel, {
        transfer: {
          cube: 'a',
          stat: { label: '已出让土地总数', value: total, unit: '宗' },
          split: [
            { key: 'city', label: '市级', value: city, unit: '宗', percent: percent(city, total), tone: 'green' },
            { key: 'district', label: '区级', value: district, unit: '宗', percent: percent(district, total), tone: 'cyan' },
          ],
          columns: PANEL_COLUMNS,
          tables: {
            city: rankTable(cityRanks, 'roadCount', city),
            district: rankTable(districtRanks, 'roadCount', district),
          },
        },
      })

      this.transferRank = ranks.map((item) => ({ name: item.name, value: toNumber(item.value) }))
    },

    /**
     * 配套统计 → 左面板「落实配套情况」+ 底部属性表预警。
     * ranks 的 value 是「待落实配套的宗地数」，completedCount 是已完成配套数。
     */
    applyFacilityDashboard (data) {
      const total = toNumber(data.landTotal)
      const city = toNumber(data.cityLandCount)
      const district = toNumber(data.districtLandCount)
      const ranks = data.ranks || []
      const cityRanks = ranks.filter((item) => item.projectType === '市级项目')
      const districtRanks = ranks.filter((item) => item.projectType === '区级项目')

      this.leftPanel = Object.assign({}, this.leftPanel, {
        support: {
          cube: 'b',
          stat: { label: '需要落实配套', value: total, unit: '宗' },
          split: [
            { key: 'city', label: '市级', value: city, unit: '宗', percent: percent(city, total), tone: 'green' },
            { key: 'district', label: '区级', value: district, unit: '宗', percent: percent(district, total), tone: 'cyan' },
          ],
          columns: PANEL_COLUMNS,
          tables: {
            city: rankTable(cityRanks, 'completedCount', city),
            district: rankTable(districtRanks, 'completedCount', district),
          },
        },
      })

      this.supportingRank = ranks.map((item) => ({ name: item.name, value: toNumber(item.value) }))
      this.warningData = Object.assign({ city: [], district: [], plot: [] }, data.warnings || {})
      this.plotWarning = toPlotWarning(data.warnings && data.warnings.plot)
    },

    handleStageResize (scale) {
      this.stageScale = scale
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
        this.$route &&
        (this.$route.path.indexOf('/screen/archive') > -1 || this.$route.path.indexOf('/screen/data') > -1) &&
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

    handleLayerVisibility (node) {
      this.$screenToast.info(`图层「${node.label}」已${node.visible ? '显示' : '隐藏'}`)
    },

    handleAttrAction (row) {
      this.warningDetail = {
        title: `${row.district || ''}地块预警信息`,
        total: 0,
        rows: [],
      }
      this.warningDetailVisible = true
      queryWarningDetail({ projectType: this.warningActiveType(row), district: row.district })
        .then((response) => {
          if (!response || !response.success) {
            this.$screenToast.error((response && response.message) || '预警详情加载失败')
            return
          }
          const rows = response.result || []
          this.warningDetail = {
            title: `${row.district || ''}地块预警信息`,
            total: rows.length,
            rows: rows.map((item) => ({
              id: item.id,
              landNo: item.crzdbh,
              plotName: item.dkmc,
              projectName: item.ptxmmc,
              landAcquisition: item.xjpfsfwc,
              feasibility: item.kypfsfwc,
              preliminaryDesign: item.csjgspfsfwc,
              fund: item.zjlsqk,
              notStarted: item.sfkg,
              notCompleted: item.sfjg,
              notHandedOver: item.sfyj,
            })),
          }
        })
        .catch((error) => {
          this.$screenToast.error((error && error.message) || '预警详情加载失败')
        })
    },

    warningActiveType (row) {
      const id = String((row && row.id) || '')
      if (id.indexOf('区级项目') === 0) return 'district'
      return 'city'
    },
  },
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
