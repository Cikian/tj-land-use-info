<template>
  <!--
    天津市经营性用地市政基础设施配套动态监管工作站 —— 地图大屏
    ---------------------------------------------------------------
    结构（对应 UI 设计稿）：
      地图舞台（Cesium #home 铺满全屏，可拖拽 / 缩放）
      ├─ 顶栏：系统标题 · 导航 · 实时时钟 · 用户 · 在线状态
      └─ 面板浮层
         ├─ 左列：出让地块情况统计（数值 + 分级占比 + 环形图）
         │        各区出让排行（排行条 + 合计）
         ├─ 中部：地图可视区（透明，不拦截地图事件）
         │        市级项目预警（页签 + 数据表格）
         └─ 右列：需要落实配套（数值 + 分级占比 + 完成率表盘）
                  各区配套落实（排行条）

    交互说明：面板浮层整体 pointer-events: none，仅面板自身 auto，
             因此地图在「留白」区域仍然完全可交互。
  -->
  <div class="land-screen">
    <!-- ===== 地图舞台 ===== -->
    <screen-map-stage :enabled="mapEnabled" show-frame>
      <s3dm-viewer v-if="mapEnabled" />
    </screen-map-stage>

    <!-- ===== UI 浮层 ===== -->
    <div class="land-screen__ui">
      <screen-header
        :title="title"
        :menus="menus"
        :active-menu="activeMenu"
        :user-name="user.name"
        :online="user.online"
        @menu-change="handleMenuChange"
      />

      <div class="land-screen__body">
        <!-- ========== 档案管理：整页切换到档案模块 ========== -->
        <!-- 顶栏保留，地图仍在底层；档案页自带各功能页签，用 keep-alive 缓存页签状态 -->
        <archive-screen
          v-if="activeMenu === 'archive'"
          ref="archive"
          class="land-screen__module"
          :default-tab="archiveTab"
        />

        <!-- ========== 首页：统计浮层（原有布局） ========== -->
        <template v-else>
          <!-- ---------- 左列 ---------- -->
          <aside class="land-screen__col">
            <screen-panel class="land-screen__panel land-screen__panel--auto" title="出让地块情况统计">
              <screen-stat-value :value="transferStat.total" :unit="transferStat.unit" />
              <screen-split-stat
                class="land-screen__split"
                :items="transferStat.split"
                :unit="transferStat.unit"
              />
              <screen-donut
                class="land-screen__donut"
                :data="transferStat.donut"
                :center-label="transferStat.donutCenterLabel"
                :size="donutSize"
                :thickness="13"
                legend-position="right"
              />
            </screen-panel>

            <screen-panel class="land-screen__panel land-screen__panel--grow" title="各区出让排行">
              <screen-rank-list :items="transferRank" unit="" />
              <template #footer>
                <span class="land-screen__summary">
                  合计
                  <em>{{ transferStat.footer.total }}</em>
                  宗 ·
                  <em>{{ transferStat.footer.road }}</em>
                  条道路
                </span>
              </template>
            </screen-panel>
          </aside>

          <!-- ---------- 中列 + 右列 ---------- -->
          <section class="land-screen__main">
            <div class="land-screen__upper">
              <!-- 地图可视区：透明，事件穿透到地图 -->
              <div class="land-screen__map-gap" aria-hidden="true" />

              <aside class="land-screen__col">
                <screen-panel class="land-screen__panel land-screen__panel--auto" title="需要落实配套">
                  <screen-stat-value :value="supportingStat.total" :unit="supportingStat.unit" />
                  <screen-split-stat
                    class="land-screen__split"
                    :items="supportingStat.split"
                    :unit="supportingStat.unit"
                  />
                  <div class="land-screen__gauge">
                    <screen-gauge
                      :percent="supportingStat.gauge.percent"
                      :label="supportingStat.gauge.label"
                      :size="gaugeSize"
                      :thickness="11"
                    />
                  </div>
                </screen-panel>

                <screen-panel class="land-screen__panel land-screen__panel--grow" title="各区配套落实">
                  <screen-rank-list :items="supportingRank" unit="" />
                </screen-panel>
              </aside>
            </div>

            <!-- 底部预警表格：页签放在面板标题栏内 -->
            <screen-panel class="land-screen__panel land-screen__panel--warning" :bar="false">
              <template #title>
                <screen-tabs v-model="activeWarning" :tabs="warningTabs" />
              </template>
              <screen-data-table
                :columns="warningColumns"
                :data="warningRows"
                :loading="dashboardLoading"
                row-key="id"
                :min-width="1180"
                @action="handleWarningAction"
              />
            </screen-panel>
          </section>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import S3dmViewer from '@/views/maps/S3dmViewer.vue'
import ArchiveScreen from './archive/index.vue'
import { queryFacilityDashboard, queryLandDashboard } from '@/api/land/landData'
import {
  ScreenHeader,
  ScreenMapStage,
  ScreenPanel,
  ScreenStatValue,
  ScreenSplitStat,
  ScreenDonut,
  ScreenGauge,
  ScreenRankList,
  ScreenTabs,
  ScreenDataTable,
} from '@/components/screen'

import {
  headerMenus,
  systemTitle,
  warningColumns,
  warningTabs,
} from './config'

function percent (value, total) {
  return total > 0 ? Number(((Number(value || 0) / total) * 100).toFixed(2)) : 0
}

function emptyTransferStat () {
  return {
    total: 0,
    unit: '宗',
    split: [
      { label: '市级', value: 0, percent: 0, tone: 'green' },
      { label: '区级', value: 0, percent: 0, tone: 'cyan' },
    ],
    donut: [
      { name: '市级', value: 0 },
      { name: '区级', value: 0 },
    ],
    donutCenterLabel: '市级',
    footer: { total: 0, road: 0 },
  }
}

function emptySupportingStat () {
  return {
    total: 0,
    unit: '宗',
    split: [
      { label: '市级', value: 0, percent: 0, tone: 'green' },
      { label: '区级', value: 0, percent: 0, tone: 'cyan' },
    ],
    gauge: { percent: 0, label: '全部配套完成率' },
  }
}

/** 已经实现为大屏子页面的菜单项；其余仍是「待接入」状态 */
const IMPLEMENTED_MENUS = ['home', 'archive']

/** 档案页允许直接落到某个页签：/screen/archive?tab=doc */
const ARCHIVE_TABS = ['maintain', 'query', 'statistics', 'doc', 'category']

export default {
  name: 'LandUseScreen',
  components: {
    S3dmViewer,
    ArchiveScreen,
    ScreenHeader,
    ScreenMapStage,
    ScreenPanel,
    ScreenStatValue,
    ScreenSplitStat,
    ScreenDonut,
    ScreenGauge,
    ScreenRankList,
    ScreenTabs,
    ScreenDataTable,
  },
  data () {
    const routeQuery = (this.$route && this.$route.query) || {}
    const routePath = (this.$route && this.$route.path) || ''
    // 通过路由 /screen/archive 直达档案页时，顶栏导航同步高亮到「档案管理」；
    // 也支持 /screen?menu=archive 的等价写法
    const initialMenu =
      routePath.indexOf('/screen/archive') > -1 || routeQuery.menu === 'archive' ? 'archive' : 'home'

    return {
      title: systemTitle,
      menus: headerMenus,
      activeMenu: initialMenu,
      user: {
        name: (this.$store && this.$store.getters.userInfo &&
          (this.$store.getters.userInfo.realname || this.$store.getters.userInfo.username)) || '普通用户',
        online: true,
      },

      /** 档案页初始页签（?tab=statistics 之类的深链） */
      archiveTab: ARCHIVE_TABS.indexOf(routeQuery.tab) > -1 ? routeQuery.tab : 'maintain',

      transferStat: emptyTransferStat(),
      transferRank: [],
      supportingStat: emptySupportingStat(),
      supportingRank: [],

      warningTabs,
      warningColumns,
      warningData: { city: [], district: [], plot: [] },
      activeWarning: warningTabs[0].key,
      dashboardLoading: false,

      /** 是否挂载真实 Cesium 地图：可通过 ?map=0 关闭（便于无地图服务时预览界面） */
      mapEnabled: this.$route.query.map !== '0',
      /** 视口高度，用于按屏幕高度微调图表尺寸 */
      viewportHeight: 1080,
    }
  },
  computed: {
    /** 预警表格当前页签的数据 */
    warningRows () {
      return this.warningData[this.activeWarning] || []
    },
    /** 环形图尺寸随面板宽度自适应，避免窄屏溢出 */
    donutSize () {
      return this.viewportHeight < 800 ? 116 : 136
    },
    gaugeSize () {
      return this.viewportHeight < 800 ? 142 : 168
    },
  },
  mounted () {
    this.syncViewport()
    this.loadDashboard()
    window.addEventListener('resize', this.syncViewport)
  },
  beforeDestroy () {
    window.removeEventListener('resize', this.syncViewport)
  },
  methods: {
    loadDashboard () {
      this.dashboardLoading = true
      return Promise.all([queryLandDashboard(), queryFacilityDashboard()])
        .then(([landResponse, facilityResponse]) => {
          if (!landResponse || !landResponse.success) {
            throw new Error((landResponse && landResponse.message) || '宗地统计加载失败')
          }
          if (!facilityResponse || !facilityResponse.success) {
            throw new Error((facilityResponse && facilityResponse.message) || '配套统计加载失败')
          }
          this.applyLandDashboard(landResponse.result || {})
          this.applyFacilityDashboard(facilityResponse.result || {})
        })
        .catch((error) => {
          this.$screenToast.error((error && error.message) || '首页数据加载失败')
        })
        .finally(() => {
          this.dashboardLoading = false
        })
    },
    applyLandDashboard (data) {
      const total = Number(data.total || 0)
      const city = Number(data.cityCount || 0)
      const district = Number(data.districtCount || 0)
      this.transferStat = {
        total,
        unit: '宗',
        split: [
          { label: '市级', value: city, percent: percent(city, total), tone: 'green' },
          { label: '区级', value: district, percent: percent(district, total), tone: 'cyan' },
        ],
        donut: [
          { name: '市级', value: city },
          { name: '区级', value: district },
        ],
        donutCenterLabel: '市级',
        footer: { total, road: Number(data.roadCount || 0) },
      }
      this.transferRank = (data.ranks || []).map((item) => ({
        name: item.name,
        value: Number(item.value || 0),
        unit: '宗',
      }))
    },
    applyFacilityDashboard (data) {
      const total = Number(data.landTotal || 0)
      const city = Number(data.cityLandCount || 0)
      const district = Number(data.districtLandCount || 0)
      this.supportingStat = {
        total,
        unit: '宗',
        split: [
          { label: '市级', value: city, percent: percent(city, total), tone: 'green' },
          { label: '区级', value: district, percent: percent(district, total), tone: 'cyan' },
        ],
        gauge: {
          percent: Number(data.completionRate || 0),
          label: '全部配套完成率',
        },
      }
      this.supportingRank = (data.ranks || []).map((item) => ({
        name: item.name,
        value: Number(item.value || 0),
        unit: '宗',
      }))
      this.warningData = Object.assign({ city: [], district: [], plot: [] }, data.warnings || {})
    },
    syncViewport () {
      this.viewportHeight = window.innerHeight || 1080
    },
    handleMenuChange (key) {
      this.activeMenu = key

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

      if (IMPLEMENTED_MENUS.indexOf(key) > -1) return

      // 未实现的模块给出明确反馈，而不是静默什么都不发生
      this.$screenToast.info(`「${this.menuLabel(key)}」模块待接入`)
    },
    menuLabel (key) {
      const hit = this.menus.find((item) => item.key === key)
      return hit ? hit.label : key
    },
    handleWarningAction (row) {
      this.$screenToast.info(`查看「${row.district}」预警详情`)
    },
  },
}
</script>

<style scoped lang="less">
.land-screen {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 600px;
  overflow: hidden;
  background: var(--screen-bg-deep);
  color: var(--screen-text);
  font-family: var(--screen-font-family);
  font-size: var(--screen-font-md);

  // UI 浮层整体不拦截鼠标事件，保证地图可操作
  &__ui {
    position: absolute;
    inset: 0;
    z-index: 2;
    display: flex;
    flex-direction: column;
    pointer-events: none;
  }

  &__body {
    display: flex;
    flex: 1 1 auto;
    gap: 12px;
    min-height: 0;
    padding: 0 16px 16px;
  }

  &__col {
    display: flex;
    flex-direction: column;
    flex: 0 0 auto;
    gap: 12px;
    width: clamp(300px, 22.5%, 440px);
    min-height: 0;
  }

  &__main {
    display: flex;
    flex-direction: column;
    flex: 1 1 auto;
    gap: 12px;
    min-width: 0;
    min-height: 0;
  }

  // 整页模块（档案管理）占满正文区。
  // 必须显式 pointer-events: auto —— 外层 .land-screen__ui 整体是 none，
  // 只有显式开启的子节点才能接收点击，否则页签和按钮全部点不动。
  &__module {
    flex: 1 1 auto;
    min-width: 0;
    min-height: 0;
    pointer-events: auto;
  }

  &__upper {
    display: flex;
    flex: 1 1 auto;
    gap: 12px;
    min-height: 0;
  }

  // 中部留白：地图从这里透出
  &__map-gap {
    flex: 1 1 auto;
    min-width: 0;
  }

  &__panel {
    // 面板恢复交互
    pointer-events: auto;

    &--auto {
      flex: 0 0 auto;
    }

    &--grow {
      flex: 1 1 auto;
      min-height: 0;
    }

    &--warning {
      flex: 0 0 auto;
      height: clamp(146px, 18vh, 210px);
    }
  }

  &__split {
    margin-top: 14px;
  }

  &__donut {
    margin-top: 12px;
  }

  &__gauge {
    display: flex;
    justify-content: center;
    margin-top: 4px;
  }

  &__summary {
    em {
      margin: 0 2px;
      font-style: normal;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-md);
      color: var(--screen-accent-soft);
    }
  }
}

// 底部预警面板的标题栏承载页签，去掉标题栏左右默认内边距带来的挤压
.land-screen__panel--warning {
  /deep/ .screen-panel__head {
    height: 42px;
    padding-left: 8px;
  }
}

// 矮屏（例如 1366×768 的调试窗口）压缩纵向留白
@media (max-height: 800px) {
  .land-screen__body {
    padding: 0 12px 12px;
    gap: 10px;
  }

  .land-screen__col,
  .land-screen__main,
  .land-screen__upper {
    gap: 10px;
  }

  .land-screen__split {
    margin-top: 10px;
  }

  .land-screen__donut {
    margin-top: 8px;
  }
}
</style>
