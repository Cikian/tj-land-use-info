<template>
  <!--
    FacilityEntryPanel 配套地块数据录入（方案 2.3.1（三）第 2 项）
    --------------------------------
    ★★ 这是本模块的旗舰页：**1 宗地 N 配套** + 每个配套的 29 环节进度。

    为什么把它做成「先选宗地，再展开配套」而不是一张平表：
      中心的工作次序就是「这块地 → 配套落实了哪些」。旧系统两个页面
      （宗地列表 / 配套列表）各自独立，用户在两边来回找；这里把入口收成一条线索：
      检索条件里选一个出让宗地 → 该宗地下的全部配套 + 每个配套的六大阶段状态
      一次性铺开（`/byLand` 一次返回，含 stageStatus 汇总）。

    两种模式（由「出让宗地」这个条件是否**精确命中一条**决定）：
      · 宗地模式（crzdbh 非空且列表命中 1 条）：表格行带 `stageStatus`，
        「六大阶段」列展开六段状态标签 + 完成度，并可按需切到只看这一宗地的配套。
      · 总览模式（未指定宗地 / 匹配多条）：走分页列表 `/facilityAdmin/list`，
        该接口返回的是 Facility 实体，**没有**阶段汇总 —— 表格里那列会显示
        「点开配套流程」，点进去是权威视图。不假装「未开启」。

    ★ 为什么两个模式不做成一个接口：
      后端明确把「按宗地取配套 + 阶段汇总」（/byLand）与「跨宗地分页检索」
      （/list）分开：前者为这条主线服务、不带分页；后者是给「我要找某条配套」
      用的。硬合成一个接口会让分页口径与汇总口径互相牵制。

    事件：
      drill (payload)  跳到别的面板（如 { tab: 'attachment', bizType, bizKey }）
  -->
  <div class="facility-entry" :class="{ 'is-search-collapsed': layout.right.collapsed }">
    <div class="facility-entry__search">
      <screen-panel class="facility-entry__search-panel" title="配套检索" collapsible scrollable>
        <facility-search-form
          ref="search"
          :initial-query="initialQuery"
          vertical
          @search="handleSearch"
        />

        <!--
          出让宗地选择器：与上面的「出让宗地编号」输入框是两条路 ——
          输入框适合「我知道编号，直接粘」，选择器适合「我大概记得是哪块地」。
          两者写的是同一个 query.crzdbh 字段，谁后改谁生效。
          ★ 窄列里必须 stacked：150px 的标签 + 控件在 168px 可用宽度里放不下。
        -->
        <div class="facility-entry__land-picker">
          <screen-field
            label="快速定位出让宗地"
            stacked
            tip="选中后只看这一宗地下的配套，并展开六大阶段进度"
          >
            <screen-select
              v-model="query.crzdbh"
              :options="landOptions"
              searchable
              :filter-local="false"
              clearable
              placeholder="输入宗地编号或地块名称搜索"
              aria-label="出让宗地"
              @search="handleSearchLand"
              @change="handleLandPicked"
            />
          </screen-field>

          <span class="facility-entry__mode">
            <screen-tag :tone="isLandMode ? 'success' : 'muted'" size="sm">
              {{ isLandMode ? '已定位到 1 宗地，显示该宗地配套与阶段进度' : '总览模式（分页），阶段进度需逐条点开' }}
            </screen-tag>
            <screen-button v-if="isLandMode" size="sm" icon="close" @click="handleClearLand">
              退出定位
            </screen-button>
          </span>
        </div>
      </screen-panel>
    </div>

    <div class="facility-entry__list" :style="{ height: listHeight + 'px' }">
      <screen-resize-bar
        :value="listHeight"
        :min="listMinHeight"
        :max="listMaxHeight"
        aria-label="拖动调整配套项目列表高度"
        @input="handleResize"
      />

      <screen-panel class="facility-entry__list-panel" :title="listTitle">
        <template #extra>
          <span class="facility-entry__total">
            共 <b>{{ total }}</b> 个配套
            <template v-if="selectedRowKeys.length">
              · 已选 <b class="is-accent">{{ selectedRowKeys.length }}</b> 个
            </template>
          </span>

          <screen-button type="primary" size="sm" icon="plus" @click="handleAdd">新增配套</screen-button>

          <screen-button size="sm" icon="upload" @click="handleGeoImport">导入地块图形</screen-button>

          <screen-popconfirm
            v-if="selectedRowKeys.length"
            title="移除后可在「数据更新与移除」里恢复，确定批量移除选中的配套吗？"
            :description="`共 ${selectedRowKeys.length} 个配套会被软删并逐条留痕`"
            width="320"
            @confirm="handleBatchRemove"
          >
            <screen-button type="danger" size="sm" icon="trash">批量移除</screen-button>
          </screen-popconfirm>

          <screen-button size="sm" icon="layers" :disabled="!isLandMode" @click="handleOpenFirstProcess">
            打开配套流程管理
          </screen-button>

          <screen-button size="sm" icon="reload" :loading="loading" @click="loadData">刷新</screen-button>
        </template>

        <facility-table
          :data-source="dataSource"
          :loading="loading"
          :selectable="!isLandMode"
          :selected-row-keys="selectedRowKeys"
          :empty-text="isLandMode ? '该宗地还没有录入配套项目' : '暂无配套项目'"
          @select-change="handleSelectChange"
          @byLand="handleByLand"
          @detail="handleDetail"
          @process="handleProcess"
          @history="handleHistory"
          @attachments="handleAttachments"
          @edit="handleEdit"
          @remove="handleRemove"
          @locate="handleLocate"
        />

        <template #footer>
          <span v-if="isLandMode" class="facility-entry__footer-hint">
            当前是「按宗地」视图，不分页 —— 该宗地下的全部配套一次列出，便于横向比较各阶段的进展。
          </span>
          <screen-pagination
            v-else
            :current="pagination.current"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            :page-size-options="pagination.pageSizeOptions"
            @change="handlePageChange"
          />
        </template>
      </screen-panel>
    </div>

    <facility-form-modal ref="formModal" @ok="handleSaved" />
    <facility-geo-import-modal ref="geoImportModal" @ok="handleGeoImported" />
    <!--
      详情弹窗只上抛 open-process，**面板本体必须挂在这里**（P1-01）：
      本页签是常驻组件，而详情弹窗的根节点随 visible 销毁 ——
      面板挂在详情里就会被「先关详情」一起销毁，表现是点了没反应。
    -->
    <facility-detail-modal
      ref="detailModal"
      @edit="handleEdit"
      @remove="handleRemove"
      @open-process="handleProcess"
      @changed="loadData"
    />
    <process-tree-panel ref="processPanel" @changed="handleProcessChanged" />

    <!--
      移除原因：软删必须留一句「为什么」。
      ★ 与 LandEntryPanel 同一套交互，刻意不用 window.prompt ——
        原生 prompt 是浅色系统弹窗，压在大屏暗色界面上会突然闪一块白，
        而且在部分内嵌 WebView 里会被直接屏蔽（表现为「点了删除没反应」）。
    -->
    <screen-modal
      v-if="removeVisible"
      :visible.sync="removeVisible"
      title="移除配套项目"
      :width="580"
      ok-text="确认移除"
      cancel-text="取消"
      :confirm-loading="removing"
      @ok="confirmRemove"
      @cancel="closeRemove"
    >
      <div class="facility-entry__remove">
        <p class="facility-entry__remove-hint">
          <screen-icon name="info" :size="14" />
          移除是<b>软删</b>：这些配套会进入「数据更新与移除」的回收站，可以恢复。
          原因会写入操作记录，供日后追溯。
        </p>
        <screen-field label="移除原因" label-width="88px" html-for="facility-remove-reason">
          <screen-input
            id="facility-remove-reason"
            v-model="removeReason"
            type="textarea"
            :rows="3"
            :maxlength="500"
            placeholder="例如：该配套并入相邻道路工程，重复录入"
          />
        </screen-field>
        <p v-if="removeTargets.length > 1" class="facility-entry__remove-hint is-warning">
          本次将移除 <b>{{ removeTargets.length }}</b> 个配套项目，原因会写进每一条的操作记录。
        </p>
      </div>
    </screen-modal>
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenResizeBar,
  ScreenButton,
  ScreenPagination,
  ScreenPopconfirm,
  ScreenSelect,
  ScreenField,
  ScreenInput,
  ScreenTag,
  ScreenModal,
  ScreenIcon,
  usePanelLayout
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import FacilitySearchForm from './FacilitySearchForm.vue'
import FacilityTable from './FacilityTable.vue'
import FacilityFormModal from './FacilityFormModal.vue'
import FacilityDetailModal from './FacilityDetailModal.vue'
import ProcessTreePanel from './ProcessTreePanel.vue'
import {
  queryFacilityAdminPage,
  queryFacilityByLand,
  queryFacilityGeoList,
  deleteFacilityAdmin,
  deleteFacilityAdminBatch
} from '@/api/land/facilityAdmin'
import { queryLandOptions } from '@/api/land/landData'
// 快速定位出让宗地时要用到宗地自己的图形（P2-05），接口与宗地面板同源
import { queryLandGeoList, queryLandGeoDetail } from '@/api/land/landAdmin'
// 配套 / 宗地几何图层：**只在「快速定位某宗地」（P2-05）时按需渲染**，
// 进菜单不再自动铺图（2026-11-14 口径：地图保持干净，双击定位交给父页切片源）
import { createGeoLayer } from '@/views/screen/landGeoLayer'
// 合并包围盒要按顶点算：本文件把两类图形的 GeoJSON 解析成 Cartesian3 顶点
import { parsePolygons } from '@/views/screen/geoPolygons'
import FacilityGeoImportModal from './FacilityGeoImportModal'
import { defaultPagination, compactQuery } from '../constants'
// 移除记录后联动清切片库图形（fire-and-forget，失败只提示不阻断）
import { purgeFeatureGroups } from '../sliceInfo'

/*
 * 列表高度（**设计画布像素**）现在与 LandEntryPanel 共用 panelLayout 的
 * data.bottom：默认 360、下限 200、上限 936（口径见 panelLayout.js 的注释）。
 */

/**
 * 概览数据本地缓存（模块级）——与 LandEntryPanel 同一套「两级加载 + 版本化增量」：
 * 概览档后端预计算（dk_geojson_overview，每环 ≤40 点）；接口传上次 version，
 * 图形没变只回 { unchanged:true }，面板从缓存直接铺图（零数据传输）；
 * 变了才全量刷新并换版本。双击定位时按 id 拉 /geoDetail 替换概览轮廓。
 */
const facilityGeoCache = { version: '', lands: null }

/**
 * 「快速定位出让宗地」用的宗地概览缓存（模块级，**独立于 LandEntryPanel 里那份**）。
 *
 * ★ 为什么不能借用 LandEntryPanel 的 geoOverviewCache：
 *   createGeoLayer 实例之间是隔离的（items / highlightId / 覆盖层 / 颜色解析
 *   全在工厂函数的闭包里），但「概览数据 + 版本号」是**面板文件的模块级变量**。
 *   两个面板共用同一个 version 就会互相覆盖：宗地面板做了一次强制刷新（把版本作废），
 *   本面板再带着空版本去请求会拿到全量、或者反过来拿到 unchanged 却本地无数据。
 *   所以这里另立一份，各自持自己的 version / lands。
 */
const locatorLandGeoCache = { version: '', lands: null }

export default {
  name: 'FacilityEntryPanel',
  components: {
    ScreenPanel,
    ScreenResizeBar,
    ScreenButton,
    ScreenPagination,
    ScreenPopconfirm,
    ScreenSelect,
    ScreenField,
    ScreenInput,
    ScreenTag,
    ScreenModal,
    ScreenIcon,
    FacilitySearchForm,
    FacilityTable,
    FacilityFormModal,
    FacilityGeoImportModal,
    FacilityDetailModal,
    ProcessTreePanel
  },
  props: {
    /** 下钻条件（例如从宗地表点「该宗地配套」带 crzdbh 过来） */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      /**
       * 数据管理页的三块面板状态（与 data/index.vue 同一份模块级单例）。
       * 本组件只用 right（检索列宽度 / 折叠）与 bottom（列表高度）。
       */
      layout: usePanelLayout('data'),
      loading: false,
      query: Object.assign({}, this.initialQuery || {}),
      dataSource: [],
      total: 0,
      selectedRowKeys: [],
      pagination: defaultPagination(10),
      /** 宗地模式下不分页，这里记一下「当前是不是宗地模式」由 computed 判定 */
      landOptions: [],
      landSearchTimer: null,
      // ---------- 移除原因弹窗 ----------
      removeVisible: false,
      removing: false,
      removeReason: '',
      removeTargets: []
    }
  },
  computed: {
    /**
     * 是否「按宗地」模式。
     * ★ 判据是「查出来的结果全部属于同一个宗地编号」而不是「crzdbh 非空」：
     *   用户在输入框里打了半截编号（模糊匹配）时也会命中多条，
     *   那种情况下按宗地渲染会把不同宗地的配套混在一起展示，看起来像同一块地的。
     */
    isLandMode () {
      if (!this.dataSource.length) return false
      const codes = this.dataSource.map((row) => row.crzdbh)
      return codes.every((code) => code && code === codes[0]) && !!String(this.query.crzdbh || '').trim()
    },
    listTitle () {
      if (this.isLandMode && this.dataSource.length) {
        return `该宗地下 ${this.dataSource.length} 个配套项目与六大阶段进展`
      }
      return '配套项目列表'
    },
    /*
     * 列表高度 / 上下限都从 panelLayout 取。
     * ★ 模板只能访问**实例上**的东西，必须用 computed 暴露；
     *   直接写 :min="LIST_MIN_HEIGHT" 会取到 undefined，设置静默失效。
     */
    listHeight () {
      return this.layout.bottom.size
    },
    listMaxHeight () {
      return this.layout.bottom.max
    },
    listMinHeight () {
      return this.layout.bottom.min
    }
  },
  created () {
    // 图层实例不用做成响应式（纯 Cesium 实体管理，与 Vue 渲染无关）
    this.geoLayer = createGeoLayer('facility')
    /*
     * P2-05：再建一个「宗地」图层实例（红橙配色），专门用来摆「快速定位」选中
     * 那一宗地自己的面。landGeoLayer.js 的注释写明两个面板各 createGeoLayer
     * 一个实例、互不干扰 —— 同一面板里建两个不同配色的实例同理：
     * 状态全在闭包里，配色由 STYLE_PRESETS 决定，不会串。
     * 它与 LandEntryPanel 的宗地图层是两回事：那个实例由那个面板 clearAll，
     * 本实例的清理由本面板的「退出定位 / deactivated / beforeDestroy」负责。
     */
    this.landGeoLayer = createGeoLayer('land')
    /** 当前「快速定位」的宗地编号（空串 = 普通视图）。非响应式：只影响 Cesium 与相机 */
    this.locateLandCode = ''
    this.loadLandOptions('')
    // 首次进入与「切页签回来」走同一个入口，两处共用同一套同 tick 去重（见 ensureListLoaded）
    this.ensureListLoaded()
  },
  activated () {
    /*
     * keep-alive 的激活钩子（首次挂载也会触发一次）只做一件事：**重拉列表**（测试清单 P1-02）。
     * 页签被 keep-alive 缓存后 created 不再跑，会出现「回收站里已经恢复了这条配套、
     * 切回录入页列表里还是没有」。与 created 那一次由 ensureListLoaded 合并，不会重复请求。
     *
     * ★ 合并说明（2026-11-17）：激活时**不再铺全部配套面** —— 同事 2026-11-14 口径
     *   「进菜单不自动铺图，地图保持干净」，定位改由父页 handleLocateRecord 走切片源。
     *   P2-05「选中宗地看宗地面 + 配套面」是用户主动触发，仍按需铺（见 showLandWithFacilities）。
     *   切走页签时由 deactivated 清掉两类定位实体（与宗地图层互不残留）。
     */
    this.ensureListLoaded()
  },
  deactivated () {
    // 两类实体都自己清（P2-05）：不指望别的面板的生命周期替本面板删图层
    this.clearLocatorLayers()
  },
  beforeDestroy () {
    this.clearLocatorLayers()
    if (this.landSearchTimer) {
      clearTimeout(this.landSearchTimer)
      this.landSearchTimer = null
    }
  },
  methods: {
    /* ---------------- 数据 ---------------- */

    /**
     * 首次挂载（created）与 keep-alive 激活（activated）共用的列表拉取入口。
     *
     * ★ 为什么必须去重：首次进入时 created 与 activated 在同一 tick 内先后触发，
     *   不去重就会发两套请求（byLand / 分页各两遍）。
     *   写法照 DataRecyclePanel.ensureLoaded：标记 + $nextTick 把同 tick 的多次调用合成一次。
     * ★ 为什么还要比 listLoadSeq：激活排队的这次刷新可能还没跑，别处就已经显式加载过
     *   （父页 drill 进来会立刻 applyDrill / handleLandPicked → loadData；保存/移除后也是
     *   loadData），那时再补一次就是纯重复请求。loadData 每次自增序号，序号变了就放弃。
     * ★ 页码与查询条件原样保留：激活刷新是「拿最新数据」，不是新查询 ——
     *   重置页码会把用户正在看的那一页抢走；条目变少让当前页空掉时，
     *   loadPage 里已有「当前页被删空自动回退一页」的兜底，不需要在这里重置。
     *   宗地模式（不分页）本来就按 query.crzdbh 重查，条件同样保留。
     */
    ensureListLoaded () {
      if (this.listLoadScheduled) return
      this.listLoadScheduled = true
      const seq = this.listLoadSeq || 0
      this.$nextTick(() => {
        this.listLoadScheduled = false
        if ((this.listLoadSeq || 0) !== seq) return
        this.loadData()
      })
    },

    loadData () {
      // 记一次「已发生的加载」，ensureListLoaded 靠它判断排队的刷新是否已被顶掉
      this.listLoadSeq = (this.listLoadSeq || 0) + 1
      const code = String(this.query.crzdbh || '').trim()
      // 宗地模式下先试 byLand：它一次返回该宗地全部配套 + 阶段汇总，比分页接口信息更全。
      // 但编号可能是模糊的（命中多条宗地），byLand 只按精确编号查 —— 所以命中为空时
      // 自动回落到分页接口，避免「输入框里打了两个字就变成空列表」。
      if (code) {
        return this.loadByLand(code)
      }
      return this.loadPage()
    },

    loadByLand (code) {
      this.loading = true
      return queryFacilityByLand(code)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '按宗地查配套失败')
            return
          }
          const rows = res.result || []
          if (rows.length) {
            this.dataSource = rows
            this.total = rows.length
            this.pagination = Object.assign({}, this.pagination, { total: rows.length })
            return
          }
          return this.loadPage()
        })
        .catch(() => this.loadPage())
        .finally(() => {
          this.loading = false
        })
    },

    loadPage () {
      this.loading = true
      const params = Object.assign(compactQuery(this.query), {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize
      })
      return queryFacilityAdminPage(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '配套项目列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = Number(page.total || 0)
          this.pagination = Object.assign({}, this.pagination, { total: this.total })

          // 当前页被删空时自动回退一页，避免用户停在空白页以为数据没了
          if (!this.dataSource.length && this.pagination.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadPage()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 宗地下拉 ---------------- */

    /** 远程搜索宗地（300ms 防抖，避免每敲一个字都打一次接口） */
    handleSearchLand (keyword) {
      if (this.landSearchTimer) clearTimeout(this.landSearchTimer)
      this.landSearchTimer = setTimeout(() => {
        this.loadLandOptions(keyword)
      }, 300)
    },

    loadLandOptions (keyword) {
      queryLandOptions({ keyword: keyword || undefined, limit: 50 })
        .then((res) => {
          if (!res || !res.success) return
          this.landOptions = (res.result || []).map((item) => ({
            value: item.crzdbh,
            label: item.label || item.crzdbh
          }))
        })
        .catch(() => {
          this.landOptions = []
        })
    },

    /**
     * 选中宗地 → 清掉分页与选中行，按该宗地重查；
     * **同时**在地图上摆出该宗地的面与它下属的配套面并定位（测试清单 P2-05）。
     * ★ 列表与图形互不阻塞：列表请求照旧发出去，图形失败只给 toast，不拦列表。
     */
    handleLandPicked (value) {
      const code = String(value || '').trim()
      this.pagination.current = 1
      this.selectedRowKeys = []
      if (this.$refs.search) {
        this.$refs.search.setQuery(Object.assign({}, this.query, { crzdbh: value || '' }))
        this.query = this.$refs.search.getQuery()
      }
      this.loadData()
      this.showLandWithFacilities(code)
    },

    handleClearLand () {
      if (this.$refs.search) {
        this.$refs.search.setQuery(Object.assign({}, this.query, { crzdbh: '' }))
        this.query = this.$refs.search.getQuery()
      } else {
        this.$set(this.query, 'crzdbh', '')
      }
      this.pagination.current = 1
      this.loadData()
      // 退出定位：地图回到「全部已画图配套」的普通视图，两类定位实体都撤掉（P2-05）
      this.exitLandLocator()
    },

    /* ---------------- 检索与分页 ---------------- */

    handleSearch (query) {
      this.query = query || {}
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.loadData()
      /*
       * 检索条件变了就核对定位态：宗地编号被清掉、或换成了另一宗地时，
       * 必须把定位态整体退出 —— 否则地图上还留着上一宗地的宗地面 / 配套面，
       * 与列表对不上，用户会以为列表筛错了（P2-05）。
       * 同一宗地再查一次不动定位，避免重复飞行。
       */
      const code = String(this.query.crzdbh || '').trim()
      if (!code || code !== this.locateLandCode) {
        this.exitLandLocator()
      }
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadPage()
    },

    handleSelectChange (keys) {
      this.selectedRowKeys = keys || []
    },

    /**
     * 拖动/键盘调整列表高度。
     * 只记高度，不重新请求数据 —— 可见行数由高度决定，数据本身没变；
     * 每次拖动都发请求会让大屏在拖动过程中疯狂打后端。
     */
    handleResize (height) {
      // 高度写进 panelLayout（持久化）；与宗地面板共用同一份值，切页签不跳
      this.layout.setSize('bottom', height)
    },

    /* ---------------- 行操作 ---------------- */

    /** 点编号 = 只看这一宗地的配套（与「快速定位」等价，只是从行上发起） */
    handleByLand (row) {
      if (!row || !row.crzdbh) return
      this.handleLandPicked(row.crzdbh)
    },

    handleAdd () {
      // 宗地模式下列表全是同一个宗地，新增时直接把宗地预置上，省一次选择
      const code = this.isLandMode && this.dataSource.length ? this.dataSource[0].crzdbh : ''
      this.$refs.formModal.showAdd(code)
    },

    handleEdit (record) {
      if (!record || !record.id) return
      if (this.$refs.detailModal) this.$refs.detailModal.close()
      this.$refs.formModal.showEdit(record)
    },

    handleSaved () {
      this.loadData()
      // 新增/编辑可能刚画了图形（或改了编号），几何图层同步重铺
      this.loadGeoLayer(true)
    },

    /**
     * 重铺配套地块几何图层。**只在定位态（P2-05）下铺「本宗地的配套面」**：
     * 2026-11-14 起进菜单不自动铺图（地图保持干净），普通视图下本方法直接空转，
     * 只作刷新数据用（保存 / 移除后调用，其余路径不会画出多余的几百个面）。
     */
    loadGeoLayer (forceRefresh) {
      // 无定位态：普通视图的配套面由切片图层负责，本方法什么都不画
      if (!this.locateLandCode) {
        return Promise.resolve()
      }
      // 与宗地图层一致：写操作后的刷新不依赖同秒时间戳是否变化。
      if (forceRefresh) {
        facilityGeoCache.version = ''
        facilityGeoCache.lands = null
      }
      // 版本化增量：图形没变（unchanged）时直接用本地缓存铺图（零数据传输）；
      // 变了才拉全量概览并更新缓存。概览档后端预计算（40 点/环）。
      return this.ensureFacilityOverview(forceRefresh)
        .then((lands) => {
          // 定位态下只重铺「本宗地的配套面」（P2-05）：把全量铺回来会冲掉
          // 「只看这一宗地」的语义，也会在用户刚定位好的画面上多画几百个面
          this.renderLandLocatorFacilities(this.locateLandCode, lands)
        })
        .catch(() => { /* 同上：辅助视图，失败不打扰 */ })
    },

    /**
     * 确保拿到配套概览列表（内存缓存优先，没有才按版本号增量拉一次）。
     * ★ 单独抽出来是因为「进菜单铺全部配套面」与「快速定位某宗地的配套面」
     *   都要这份数据：两条路径共用同一个缓存源，避免同一次激活发两遍全量请求。
     * @param {boolean} [forceRefresh] 写操作后强制后端重建概览（refresh=1）
     */
    ensureFacilityOverview (forceRefresh) {
      if (facilityGeoCache.lands) {
        return Promise.resolve(facilityGeoCache.lands)
      }
      return queryFacilityGeoList(facilityGeoCache.version, forceRefresh === true)
        .then((res) => {
          if (!res || !res.success) return []
          const data = res.result || {}
          if (!data.unchanged) {
            facilityGeoCache.version = data.version || ''
            facilityGeoCache.lands = data.lands || []
          }
          return facilityGeoCache.lands || []
        })
        .catch(() => [])
    },

    /* ---------------- 快速定位出让宗地：宗地面 + 配套面同屏（P2-05） ---------------- */

    /**
     * 选中宗地后同时摆出「该宗地的面 + 它下属的配套面」，相机按**两者合并范围**定位。
     *
     * ★ 为什么不能只对配套 flyTo（改造前的表现）：配套图层的 flyTo 只按**那一条**
     *   配套的全部块算包围球，宗地面根本进不了画面 —— 用户看到的是一堆青色小块，
     *   说不清这块地本身在哪、边界到哪。测试清单 P2-05 要求两者同时可见、能分辨图例
     *   （宗地红橙 / 配套青色本来就是两套配色，见 landGeoLayer.js 的 STYLE_PRESETS）。
     * ★ 图形来源沿用现有的两级加载口径：先用概览档（毫秒级、多数已在缓存）铺面并定位，
     *   飞行落定后再按 id 拉该宗地的 /geoDetail 精确图形替换高亮覆盖层 ——
     *   与 handleLocate 的「先概览后精确」完全同一套，不新造第三种取图方式。
     * ★ 没有图形不拦列表：列表请求在上面已经发出去，这里只给一句明确提示。
     */
    showLandWithFacilities (code) {
      if (!code) {
        this.exitLandLocator()
        return
      }
      // 先记目标编号：后面每个异步回调都靠它判断「期间有没有换宗地 / 有没有退出定位」
      this.locateLandCode = code
      // 上一个宗地的面先撤掉；配套面也清空，下面按本宗地重铺（renderAll 自己也会先清）
      this.landGeoLayer.clearAll()
      this.geoLayer.clearAll()

      Promise.all([this.ensureFacilityOverview(), this.ensureLocatorLandOverview()])
        .then(([facilityLands, landLands]) => {
          // 期间换了宗地 / 点了退出定位：这批结果作废，别把旧宗地的面画回来
          if (this.locateLandCode !== code) return
          const landHit = (landLands || []).find((item) => item && item.crzdbh === code && item.dkGeojson)
          const facilities = (facilityLands || []).filter((item) => item && item.crzdbh === code && item.dkGeojson)

          // 配套面：只留本宗地的（先清后画，幂等）
          this.geoLayer.renderAll(facilities, false)
          if (landHit) {
            // 宗地面铺到宗地图层并高亮（白描边 + 加深填充），与「双击定位」同一视觉口径
            this.landGeoLayer.renderAll([landHit], false)
            this.landGeoLayer.highlight(landHit.id)
          }

          const positions = this.collectLocatorPositions(landHit, facilities)
          if (!positions.length) {
            toast.warning(
              `宗地 ${code} 与其下属配套都还没有图形：可在编辑里绘制或用「导入地块图形」导入，列表照常可用`
            )
            return
          }
          if (!landHit) {
            toast.info(`宗地 ${code} 还没有地块图形，已定位到它下属的 ${facilities.length} 个配套面`)
          }
          this.flyToMergedBounds(positions, () => {
            if (this.locateLandCode !== code || !landHit) return
            // 飞行落定后才换精确图形：几百点贴地面的建面开销放飞行中会肉眼可见地卡动画
            queryLandGeoDetail(landHit.id)
              .then((res) => {
                if (this.locateLandCode !== code) return
                if (!res || !res.success || !res.result || !res.result.dkGeojson) return
                this.landGeoLayer.applyPreciseOverlay(landHit.id, res.result.dkGeojson)
              })
              .catch(() => { /* 概览面仍可查看，失败不打扰 */ })
          })
        })
        .catch(() => { /* 辅助视图，失败不打扰列表 */ })
    },

    /** 退出「快速定位」：两个图层的定位实体都撤掉，地图回到干净视图（幂等） */
    exitLandLocator () {
      if (!this.locateLandCode) return
      this.locateLandCode = ''
      // 两类实体都清：定位用的宗地面（含高亮覆盖层）与「只有本宗地配套」的配套面
      // ★ 不再「回铺全部配套面」：进菜单本来就不自动铺图，退出定位同样保持地图干净
      this.landGeoLayer.clearAll()
      this.geoLayer.clearAll()
    },

    /** 清掉本面板铺在地图上的一切（配套面 + 高亮 + 定位的宗地面），幂等 */
    clearLocatorLayers () {
      this.locateLandCode = ''
      this.geoLayer.clearAll()
      this.landGeoLayer.clearAll()
    },

    /** 只重铺「本宗地的配套面」—— 定位态下被 loadGeoLayer 复用（保存 / 移除后刷新） */
    renderLandLocatorFacilities (code, facilityLands) {
      const list = (facilityLands || []).filter((item) => item && item.crzdbh === code && item.dkGeojson)
      this.geoLayer.renderAll(list, false)
    },

    /**
     * 确保拿到宗地概览列表（独立缓存，隔离原因见 locatorLandGeoCache 的注释）。
     * 与配套那份同一套版本化增量协议：带上次 version，没变只回 unchanged。
     */
    ensureLocatorLandOverview () {
      if (locatorLandGeoCache.lands) {
        return Promise.resolve(locatorLandGeoCache.lands)
      }
      return queryLandGeoList(locatorLandGeoCache.version, false)
        .then((res) => {
          if (!res || !res.success) return []
          const data = res.result || {}
          if (!data.unchanged) {
            locatorLandGeoCache.version = data.version || ''
            locatorLandGeoCache.lands = data.lands || []
          }
          return locatorLandGeoCache.lands || []
        })
        .catch(() => [])
    },

    /**
     * 宗地面 + 全部配套面的顶点合集，用来算**合并包围盒**。
     *
     * ★ 为什么自己算：图层模块公开的定位能力只有 flyTo(id)（内部按单条的全部块算
     *   包围球），而这两类实体分属两个图层实例、没有跨实例的合并 bounds API。
     *   本次只允许改面板，不扩图层导出接口，所以在面板侧把两边 GeoJSON 解析成
     *   Cartesian3 顶点，合并后交给相机——与 flyTo 用的是同一个 BoundingSphere 口径。
     */
    collectLocatorPositions (landItem, facilityList) {
      const Cesium = window.Cesium
      if (!Cesium) return []
      const boxes = [landItem].concat(facilityList || []).filter(Boolean)
      const positions = []
      boxes.forEach((item) => {
        parsePolygons(item.dkGeojson).forEach((ring) => {
          ring.forEach((coord) => positions.push(Cesium.Cartesian3.fromDegrees(coord[0], coord[1], 0)))
        })
      })
      return positions
    },

    /**
     * 相机飞到合并包围球。
     * 俯角与倍距照抄 landGeoLayer.flyTo（-45°、半径 2.4 倍），
     * 让「快速定位宗地」与「双击定位某条」的落点观感一致，不会一个远一个近。
     */
    flyToMergedBounds (positions, onComplete) {
      const Cesium = window.Cesium
      const viewer = window.viewer
      if (!Cesium || !viewer || !positions.length) return
      const sphere = Cesium.BoundingSphere.fromPoints(positions)
      viewer.camera.flyToBoundingSphere(sphere, {
        duration: 1.2,
        offset: new Cesium.HeadingPitchRange(0, Cesium.Math.toRadians(-45), sphere.radius * 2.4),
        complete: typeof onComplete === 'function' ? onComplete : undefined
      })
    },

    /**
     * 列表**双击**某条：**定位到 MVT 切图层上的该配套**。
     *
     * ★ 合并说明（2026-11-17）：定位统一交给父页 handleLocateRecord ——
     *   同事 2026-11-14 起走「唯一值 → 切片源表查范围 → 相机飞行」，不再依赖
     *   dk_geojson（该字段即将废弃）。原实现里还有一段「概览定位 + dk_geojson
     *   精确替换」的 geoLayer 流程，与父页的新定位会同时飞两次相机，
     *   而且进菜单已不铺配套面（geoLayer.has 恒为 false，只会误报「还没有图形」），
     *   所以整段移除，本面板只上报记录。
     * ★ 「看这一宗地的宗地面 + 配套面」由检索列选中宗地触发（P2-05，见 showLandWithFacilities），
     *   与双击行定位是两条不同的入口，互不覆盖。
     */
    handleLocate (record) {
      if (!record || !record.id) return
      this.$emit('locate-record', record)
    },

    /** 打开「导入配套地块图形（GeoJSON）」弹窗 */
    handleGeoImport () {
      this.$refs.geoImportModal.open()
    },

    /** GeoJSON 图形导入完成：刷新列表，并重铺几何图层（新图形立即可见、可定位） */
    handleGeoImported () {
      this.loadData()
      this.loadGeoLayer(true)
    },

    handleDetail (record) {
      this.$refs.detailModal.open(record, 'base')
    },

    handleHistory (record) {
      this.$refs.detailModal.open(record, 'history')
    },

    /** 附件：跳到附件面板并按该配套过滤（这是「配套 ↔ 附件」最自然的往返） */
    handleAttachments (record) {
      if (!record) return
      this.$emit('drill', {
        tab: 'attachment',
        bizType: 'facility',
        bizId: record.id,
        bizKey: record.ptxmmc || '',
        // 显式给名称：上传弹窗用它回显「这个附件挂到哪个配套」
        bizName: record.ptxmmc || ''
      })
    },

    /**
     * 打开配套流程管理（列表行「配套流程」与详情页「配套流程管理」共用本方法）。
     * ★ 顺序固定：先关详情再开面板 —— 两层都是同层 fixed 弹窗，
     *   详情留着不收会截获面板上的点击（看起来像面板点不动）。
     */
    handleProcess (record) {
      if (!record || !record.id) return
      if (this.$refs.detailModal) this.$refs.detailModal.close()
      this.$refs.processPanel.open(record)
    },

    /** 标题栏的「打开配套流程管理」：宗地模式下默认打开第一个配套 */
    handleOpenFirstProcess () {
      if (!this.dataSource.length) {
        toast.warning('该宗地还没有配套项目')
        return
      }
      this.handleProcess(this.dataSource[0])
    },

    /** 环节进度落库后重拉列表：整体完成度与逾期数都该跟着变 */
    handleProcessChanged () {
      this.loadData()
    },

    /* ---------------- 移除 ---------------- */

    handleRemove (record) {
      if (!record || !record.id) return
      this.removeTargets = [record]
      this.removeReason = ''
      this.removeVisible = true
    },

    handleBatchRemove () {
      if (!this.selectedRowKeys.length) return
      this.removeTargets = this.dataSource.filter((row) => this.selectedRowKeys.indexOf(row.id) > -1)
      this.removeReason = ''
      this.removeVisible = true
    },

    closeRemove () {
      this.removeVisible = false
      this.removeTargets = []
      this.removeReason = ''
    },

    confirmRemove () {
      const targets = this.removeTargets || []
      if (!targets.length) {
        this.closeRemove()
        return
      }
      this.removing = true
      this.doRemove(targets, this.removeReason)
        .finally(() => {
          this.removing = false
        })
    },

    /**
     * 真正发请求。
     * ★ 返回 Promise 而不是在这里复位弹窗：调用方（confirmRemove）需要知道
     *   请求什么时候结束，才能在「全部成功」时关闭弹窗、在「部分失败」时留着
     *   让用户看到失败明细并重试。
     */
    doRemove (targets, reason) {
      if (!targets.length) return Promise.resolve()
      const text = String(reason || '').trim()
      const request = targets.length === 1
        ? deleteFacilityAdmin(targets[0].id, text || undefined)
        : deleteFacilityAdminBatch(targets.map((row) => row.id), text || undefined)

      return request
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '移除失败')
            return
          }
          // 批量移除的失败明细必须逐条说清楚，只报「成功 N 条」会让失败的记录没有交代
          const result = res.result || {}
          if (result.failCount) {
            const first = (result.failures || [])[0]
            toast.warning(
              `${res.message || '部分移除失败'}${first ? `；首条失败原因：${first.reason}` : ''}`
            )
            // 有失败时**不关弹窗**：用户的重试动作就在眼前，关掉会让他重新找入口
            this.loadData()
            this.loadGeoLayer(true)
            return
          }
          toast.success(res.message || '已移除，可在「数据更新与移除」中恢复')
          // 联动清掉切片库里这些项目名下的图形（fire-and-forget；失败只提示，
          // 业务记录已软删成功，不能因图形清理失败而阻断列表刷新）
          purgeFeatureGroups('facility', targets, toast)
          this.selectedRowKeys = this.selectedRowKeys.filter(
            (key) => !targets.some((row) => row.id === key)
          )
          this.closeRemove()
          this.loadData()
          // 被移除的配套要从定位图层上撤下来（无定位态时 loadGeoLayer 自己空转）
          this.loadGeoLayer(true)
        })
        .catch(() => {
          // 请求层已提示
        })
    },

    /* ---------------- 对外（父组件调用） ---------------- */

    /**
     * 应用下钻条件。
     * ★ 面板被 keep-alive 缓存后 created 不会再跑，必须由父组件显式调用。
     * ★ 下钻带 crzdbh（从宗地表点「该宗地配套」过来）本身就是「定位这一宗地」，
     *   所以和快速定位一样把宗地面与配套面一起摆出来（P2-05）；没带编号则退出定位态。
     */
    applyDrill (query) {
      const next = query || {}
      if (this.$refs.search) {
        this.$refs.search.setQuery(next)
        this.query = this.$refs.search.getQuery()
      } else {
        this.query = compactQuery(next)
      }
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.loadData()
      const code = String(this.query.crzdbh || '').trim()
      if (code) {
        this.showLandWithFacilities(code)
      } else {
        this.exitLandLocator()
      }
    },

    /**
     * P2-01 统一契约：父页在「普通离开再进入」时调它清空检索。
     * 等价于 applyDrill({})：`setQuery({})` 回到 buildEmptyQuery（含 bizId/bizKey 隐藏条件），
     * 同时因为不带 crzdbh，会走 exitLandLocator() 把宗地 + 配套两类实体一起收掉。
     */
    resetFilters () {
      this.applyDrill({})
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.facility-entry {
  /*
   * 铺满整个设计画布，内部按设计稿坐标摆放「右侧检索列 + 贴底列表」。
   * 与 LandEntryPanel 同一套版式与坐标（改一处要同步改另一处）。
   *
   * ★ pointer-events: none 是必须的：本节点已铺满整屏，
   *   若在这里开 auto 就会把地图的鼠标事件全吃掉。
   */
  position: absolute;
  inset: 0;
  pointer-events: none;

  /* ---------------- 右侧检索列（与左侧二级导航左右对称） ---------------- */
  &__search {
    position: absolute;
    right: 30px;
    top: 104px;
    bottom: 40px;
    // 宽度可拖（160~340），由 data/index.vue 的 --data-search-w 下发
    width: var(--data-search-w, 200px);
    display: flex;
    flex-direction: column;
    min-height: 0;
    pointer-events: auto;
    // 折叠 = 整列向右滑出屏幕（位移 = 列宽 + 30）
    transition: transform var(--screen-duration-slow) var(--screen-ease);
  }

  /* 右侧检索列折叠（由 data/index.vue 的右屏边把手切换） */
  &.is-search-collapsed &__search {
    transform: translateX(calc(var(--data-search-w, 200px) + 30px));
  }

  /* scrollable：竖排条件展开后超出列高时必须能滚，否则会被裁掉 */
  &__search-panel {
    flex: 1 1 0;
    min-height: 0;
  }

  /* ---------------- 底部列表（贴底 + 可拖高） ---------------- */
  &__list {
    position: absolute;
    // 左偏移跟着左导航宽度 / 折叠走（--data-nav-offset）
    left: var(--data-nav-offset, 250px);
    // 与右侧检索列留出与左侧同样的 20px 间距（检索列宽 + 30 + 20）
    right: calc(var(--data-search-w, 200px) + 50px);
    bottom: 40px;
    // 高度由 listHeight 内联控制（拖动改变）
    display: flex;
    flex-direction: column;
    min-height: 0;
    pointer-events: auto;
    // 侧栏 / 检索列拖宽或折叠时，列表左右跟着让位
    transition: left var(--screen-duration-slow) var(--screen-ease),
      right var(--screen-duration-slow) var(--screen-ease);
  }

  /* 右侧检索列折叠后，列表铺到屏边 30 */
  &.is-search-collapsed &__list {
    right: 30px;
  }

  &__list-panel {
    flex: 1 1 0;
    min-height: 0;
  }

  /*
   * 宗地选择器：窄列里改成上下两块（控件在上、模式提示在下）。
   * ★ 不能用左右排：200px 列里可用宽度只有 168px，并排会把下拉压到不可用。
   */
  &__land-picker {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: var(--screen-space-3);
    margin-top: var(--screen-space-3);
    padding-top: var(--screen-space-3);
    border-top: 1px dashed var(--screen-border-soft);

    // 覆盖旧的 520px 定宽（那是宽版式下的设置，窄列里必须撑满）
    /deep/ .screen-field {
      flex: 1 1 auto;
      min-width: 0;
    }
  }

  &__mode {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: var(--screen-space-2);
    min-width: 0;
  }

  &__total {
    margin-right: var(--screen-space-3);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    white-space: nowrap;

    b {
      margin: 0 2px;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-sm);
      color: var(--screen-text);
    }

    b.is-accent {
      color: var(--screen-accent);
    }
  }

  /* 宗地模式不分页，底栏放一句解释代替分页器，避免用户以为分页坏了 */
  &__footer-hint {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  /* 移除原因弹窗：提示 + 原因输入 + 批量数量提示 */
  &__remove {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
  }

  &__remove-hint {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-text-sub);

    b {
      color: var(--screen-accent);
      font-weight: 600;
    }

    &.is-warning {
      color: var(--screen-warning);

      b {
        color: var(--screen-warning);
      }
    }
  }
}

// 工具条上的按钮较多，窄屏允许换行而不是被压扁
.facility-entry__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
