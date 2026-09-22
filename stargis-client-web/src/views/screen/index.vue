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
      SupportingFacilitiesDashboardVO（配套），成功后覆盖 ./mock.js 的兜底演示数据；
      接口不可用时保留兜底数据，保证没有业务后端时界面仍按设计稿完整呈现。
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

    <!-- ===== 档案管理 / 收发文：整页模块 ===== -->
    <archive-screen
      v-if="activeMenu === 'archive' || activeMenu === 'doc'"
      ref="archive"
      class="land-screen__module stage-hit"
      :default-tab="archiveTab"
    />

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
        @open-plot-warning="plotVisible = true"
        @row-action="handleAttrAction"
      />

      <home-plot-modal
        v-if="plotVisible"
        :data="plotWarning"
        @close="plotVisible = false"
        @row-action="handleAttrAction"
      />
    </template>
  </screen-stage>
</template>

<script>
import S3dmViewer from '@/views/maps/S3dmViewer.vue'
import ArchiveScreen from './archive/index.vue'
import HomeLeftPanel from './home/HomeLeftPanel.vue'
import HomeLayerTree from './home/HomeLayerTree.vue'
import HomeAttrPanel from './home/HomeAttrPanel.vue'
import HomePlotModal from './home/HomePlotModal.vue'
import { ScreenStage, ScreenHeader } from '@/components/screen'
import { hf } from '@/assets/screen-blue'
import { queryFacilityDashboard, queryLandDashboard } from '@/api/land/landData'

import { headerMenus, systemTitle, warningTabs } from './config'
import {
  demoLeftPanel,
  demoPlotWarning,
  demoSupportingRank,
  demoTransferRank,
  demoWarningData,
} from './mock'

/**
 * 已经实现为大屏子页面的菜单项：
 *   home    首页工作台
 *   archive 档案管理（整页模块）
 *   doc     收发文（复用档案管理模块，初始页签落到「收发文管理」）
 * 其余仍是「待接入」状态。
 */
const IMPLEMENTED_MENUS = ['home', 'archive', 'doc']

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

export default {
  name: 'LandUseScreen',
  components: {
    S3dmViewer,
    ArchiveScreen,
    HomeLeftPanel,
    HomeLayerTree,
    HomeAttrPanel,
    HomePlotModal,
    ScreenStage,
    ScreenHeader,
  },
  data () {
    const routeQuery = (this.$route && this.$route.query) || {}
    const routePath = (this.$route && this.$route.path) || ''
    // 通过路由 /screen/archive 直达档案页时，顶栏导航同步高亮到「档案管理」；
    // 也支持 /screen?menu=archive 的等价写法
    let initialMenu = 'home'
    if (routePath.indexOf('/screen/archive') > -1) {
      initialMenu = routeQuery.tab === 'doc' ? 'doc' : 'archive'
    } else if (routeQuery.menu && IMPLEMENTED_MENUS.indexOf(routeQuery.menu) > -1) {
      initialMenu = routeQuery.menu
    }

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

      /** 档案页初始页签（?tab=statistics 之类的深链） */
      archiveTab:
        ARCHIVE_TABS.indexOf(routeQuery.tab) > -1
          ? routeQuery.tab
          : initialMenu === 'doc'
            ? 'doc'
            : 'maintain',

      // 先铺兜底演示数据，接口成功后再整段覆盖
      leftPanel: demoLeftPanel,
      transferRank: demoTransferRank,
      supportingRank: demoSupportingRank,
      warningData: demoWarningData,
      warningTabs,
      plotWarning: demoPlotWarning,

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
    /** 并发拉取宗地与配套两个看板；任一失败都保留兜底数据并提示 */
    loadDashboard () {
      this.dashboardLoading = true
      return Promise.all([queryLandDashboard(), queryFacilityDashboard()])
        .then(([landResponse, facilityResponse]) => {
          if (landResponse && landResponse.success) {
            this.applyLandDashboard(landResponse.result || {})
          } else {
            this.$screenToast.error((landResponse && landResponse.message) || '宗地统计加载失败')
          }
          if (facilityResponse && facilityResponse.success) {
            this.applyFacilityDashboard(facilityResponse.result || {})
          } else {
            this.$screenToast.error((facilityResponse && facilityResponse.message) || '配套统计加载失败')
          }
        })
        .catch((error) => {
          this.$screenToast.error((error && error.message) || '首页数据加载失败，已显示演示数据')
        })
        .finally(() => {
          this.dashboardLoading = false
        })
    },

    /**
     * 宗地统计 → 左面板「出让情况」。
     * ⚠ 后端 ranks 是「行政区划维度」的汇总（对应设计稿的市级子表）；
     *   街镇维度的区级子表后端暂未提供，因此区级子表留空并由面板显示「暂无数据」，
     *   不用市级数据顶替（避免展示口径错误）。后端补充分级明细后只需改这里。
     */
    applyLandDashboard (data) {
      const total = toNumber(data.total)
      const city = toNumber(data.cityCount)
      const district = toNumber(data.districtCount)
      const roadTotal = toNumber(data.roadCount)
      const ranks = data.ranks || []

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
            city: {
              rows: ranks.map((item) => ({
                name: item.name,
                plots: toNumber(item.value),
                roads: toNumber(item.roadCount),
              })),
              total: { name: '合计', plots: city, roads: roadTotal },
            },
            district: {
              rows: [],
              total: { name: '合计', plots: district, roads: 0 },
            },
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
            city: {
              rows: ranks.map((item) => ({
                name: item.name,
                plots: toNumber(item.value),
                roads: toNumber(item.completedCount),
              })),
              total: { name: '合计', plots: city, roads: ranks.reduce((sum, item) => sum + toNumber(item.completedCount), 0) },
            },
            district: {
              rows: [],
              total: { name: '合计', plots: district, roads: 0 },
            },
          },
        },
      })

      this.supportingRank = ranks.map((item) => ({ name: item.name, value: toNumber(item.value) }))
      this.warningData = Object.assign({ city: [], district: [], plot: [] }, data.warnings || {})
      // 地块预警弹窗沿用兜底数据（后端暂无该粒度明细）
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
        this.$route &&
        this.$route.path.indexOf('/screen/archive') > -1 &&
        this.$router &&
        this.$router.replace
      ) {
        // vue-router 3.1+ 的 replace 返回 promise，导航被中断时会 reject，静默吃掉即可
        const navigation = this.$router.replace({ path: '/screen' })
        if (navigation && typeof navigation.catch === 'function') navigation.catch(() => {})
      }

      if (key === 'doc') {
        // 收发文复用档案管理模块，切到「收发文管理」页签
        this.archiveTab = 'doc'
        this.$nextTick(() => {
          const archive = this.$refs.archive
          if (archive && typeof archive.setTab === 'function') archive.setTab('doc')
        })
        return
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
      this.$screenToast.info(`查看「${row.district}」预警详情`)
    },
  },
}
</script>

<style scoped lang="less">
.land-screen {
  &__vignette {
    position: absolute;
    display: block;
    pointer-events: none;

    &.is-top {
      left: 0;
      top: 0;
      width: 1920px;
      height: 223px;
    }

    &.is-bottom {
      left: 0;
      top: 794px;
      width: 1920px;
      height: 286px;
    }

    &.is-left {
      left: 0;
      top: 0;
      width: 560px;
      height: 1080px;
    }

    &.is-right {
      left: 1360px;
      top: 0;
      width: 560px;
      height: 1080px;
    }
  }

  /* 整页模块（档案管理 / 收发文）占满设计稿的正文区（与左右面板同一纵向区间） */
  &__module {
    position: absolute;
    left: 30px;
    top: 149px;
    width: 1860px;
    height: 881px;
  }
}
</style>
