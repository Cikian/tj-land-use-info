<template>
  <!--
    LandEntryPanel 经营性用地信息录入（方案 2.3.1（三）第 1 项）
    --------------------------------
    「逐条录入经营性用地信息，并支持修改与移除」。

    版式（2026-10 改版，与 FacilityEntryPanel 保持一致）：
      右侧检索列   right:30 / top:104 / bottom:40 / 宽 200 —— 与左侧二级导航**左右对称**，
                   检索条件竖排（窄列里字段改成「标签在上、控件在下」）
      底部列表     left:250 / right:250 / 贴底 bottom:40，**默认只露 3~4 行**，
                   上沿有 ScreenResizeBar 可以上下拖动改高
      中间        留给地图 —— 这是本模块「边看地块边填」的前提

    ★ 为什么列表默认要压到这么矮、而不是像以前那样吃掉整块内容区：
      这个模块的正事是「对着地图录宗地」，列表只是定位工具。
      列表默认占满屏时，地图只剩一条缝，用户得先手动收起才看得见地块。

    ★ 为什么用 ScreenResizeBar 而不是 v-if 折叠列表：
      折叠只有「全开/全关」两态，而不同人的屏前工作不一样（有人要一次比对十几条），
      拖动能给出连续区间；键盘用户也能用 ↑/↓/PgUp/PgDn 调。

    弹窗：新增/编辑、详情（含操作记录）、移除原因确认。

    ★ 移除为什么必须带「原因」：
      宗地移除是软删，数据还在回收站里。半年后有人问「这块地怎么没了」，
      只有原因能回答。所以移除弹窗里原因虽然可选，但按钮上写明了「原因会写入操作记录」，
      默认聚焦在原因输入框上，引导用户填一句。

    ★ 为什么列表里的「配套数 / 附件数 / 变更次数」默认是「—」：
      后端的宗地分页接口返回的就是 `t_land` 实体，没有这三个聚合值。
      「配套数」一次调用 `/land/data/land/options` 就能拿到（它带 facilityCount），
      但那个接口只返回前 N 条且按编号排序，与本页的筛选/分页对不上；
      所以本页只在「按宗地编号精确筛出一条」时把配套数补上，
      其余情况老实显示「—」——宁可显示「未统计」，也不要假装统计过了。
      真正需要看配套数时，点表格里的配套列会直接跳到配套录入面板（那是权威口径）。

    事件：
      drill (payload)  跳到别的面板（如 { tab: 'facility', crzdbh }）
  -->
  <div class="land-entry" :class="{ 'is-search-collapsed': layout.right.collapsed }">
    <div class="land-entry__search">
      <screen-panel class="land-entry__search-panel" title="宗地检索" collapsible scrollable>
        <land-search-form
          ref="search"
          :initial-query="initialQuery"
          vertical
          @search="handleSearch"
        />
      </screen-panel>
    </div>

    <div class="land-entry__list" :style="{ height: listHeight + 'px' }">
      <screen-resize-bar
        :value="listHeight"
        :min="listMinHeight"
        :max="listMaxHeight"
        aria-label="拖动调整经营性用地列表高度"
        @input="handleResize"
      />

      <screen-panel class="land-entry__list-panel" title="经营性用地列表">
        <template #extra>
          <span class="land-entry__total">
            共 <b>{{ pagination.total }}</b> 宗
            <template v-if="selectedRowKeys.length">
              · 已选 <b class="is-accent">{{ selectedRowKeys.length }}</b> 宗
            </template>
          </span>

          <screen-button type="primary" size="sm" icon="plus" @click="handleAdd">新增宗地</screen-button>

          <screen-button size="sm" icon="upload" @click="handleGeoImport">导入地块图形</screen-button>

          <screen-popconfirm
            v-if="selectedRowKeys.length"
            title="移除后可在「数据更新与移除」里恢复，确定批量移除选中的宗地吗？"
            :description="`共 ${selectedRowKeys.length} 宗宗地会被软删并逐条留痕`"
            width="320"
            @confirm="handleBatchRemove"
          >
            <screen-button type="danger" size="sm" icon="trash">批量移除</screen-button>
          </screen-popconfirm>

          <screen-button size="sm" icon="database" @click="$emit('open-recycle')">去回收站</screen-button>

          <screen-button size="sm" icon="reload" :loading="loading" @click="loadData">刷新</screen-button>
        </template>

        <land-table
          :data-source="dataSource"
          :loading="loading"
          selectable
          :selected-row-keys="selectedRowKeys"
          @select-change="handleSelectChange"
          @detail="handleDetail"
          @history="handleHistory"
          @attachments="handleAttachments"
          @edit="handleEdit"
          @remove="handleRemove"
          @facilities="handleFacilities"
          @locate="handleLocate"
        />

        <template #footer>
          <screen-pagination
            :current="pagination.current"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            :page-size-options="pagination.pageSizeOptions"
            @change="handlePageChange"
          />
        </template>
      </screen-panel>
    </div>

    <land-form-modal ref="formModal" @ok="handleSaved" />
    <land-geo-import-modal ref="geoImportModal" @ok="handleGeoImported" />
    <land-detail-modal
      ref="detailModal"
      @edit="handleEdit"
      @facilities="handleFacilities"
    />

    <!-- 移除原因：软删必须留一句「为什么」，否则半年后没人说得清这块地怎么没了 -->
    <screen-modal
      v-if="removeVisible"
      :visible.sync="removeVisible"
      title="移除经营性用地"
      :width="560"
      ok-text="确认移除"
      cancel-text="取消"
      :confirm-loading="removing"
      @ok="confirmRemove"
      @cancel="closeRemove"
    >
      <div class="land-entry__remove">
        <p class="land-entry__remove-hint">
          <screen-icon name="info" :size="14" />
          移除是<b>软删</b>：这条宗地会进入「数据更新与移除」的回收站，可以恢复。
          原因会写入该宗地的操作记录，供日后追溯。
        </p>
        <screen-field label="移除原因" label-width="88px" html-for="land-remove-reason">
          <screen-input
            id="land-remove-reason"
            v-model="removeReason"
            type="textarea"
            :rows="3"
            :maxlength="500"
            placeholder="例如：编号书写有误，已按正确编号重新录入"
          />
        </screen-field>
        <p v-if="removeTargets.length > 1" class="land-entry__remove-hint is-warning">
          本次将移除 <b>{{ removeTargets.length }}</b> 宗宗地，原因会写进每一条的操作记录。
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
  ScreenModal,
  ScreenField,
  ScreenInput,
  ScreenIcon,
  usePanelLayout
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import LandSearchForm from './LandSearchForm.vue'
import LandTable from './LandTable.vue'
import LandFormModal from './LandFormModal.vue'
import LandDetailModal from './LandDetailModal.vue'
import LandGeoImportModal from './LandGeoImportModal.vue'
import { queryLandAdminPage, deleteLandAdmin, deleteLandAdminBatch, queryLandGeoList, queryLandGeoDetail } from '@/api/land/landAdmin'
import { queryLandOptions } from '@/api/land/landData'
// 宗地几何图层：进菜单把全部已画图宗地面铺到地图上，列表双击定位 + 高亮
import { createGeoLayer } from '@/views/screen/landGeoLayer'
import { defaultPagination, compactQuery } from '../constants'

/*
 * 列表高度（**设计画布像素**）现在由 panelLayout 的 data.bottom 统一持有：
 * 默认 360（实测可见 3 整行、第 4 行露一半）、下限 200、上限 936。
 * ★ 2026-11 起改成共用状态而不是本组件的 data：一是配套面板也读同一份高度，
 *   切页签回来高度不跳；二是 views/screen/index.vue 的氛围蒙版要跟着列表高度伸缩。
 */

/**
 * 概览数据本地缓存（模块级，浏览器不刷新就一直在）。
 *
 * ★ 两级加载 + 版本化增量，进页面耗时与「宗地总数」解耦：
 *   1. 概览档后端预计算（dk_geojson_overview 列，每环 ≤40 点、总体 <1MB），
 *      渲染也不贴 S3M（减负）；精确边界由双击定位按 id 拉 /geoDetail 替换；
 *   2. 接口协议：传上次拿到的 version（后端 = 全表 MAX(update_time)，任何
 *      导入/编辑/移除/恢复都会推高）——图形没变时只回 { unchanged:true }，
 *      面板从这份缓存直接铺图（毫秒级、零数据传输）；变了才全量刷新并换版本。
 */
const geoOverviewCache = { version: '', lands: null }

export default {
  name: 'LandEntryPanel',
  components: {
    ScreenPanel,
    ScreenResizeBar,
    ScreenButton,
    ScreenPagination,
    ScreenPopconfirm,
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenIcon,
    LandSearchForm,
    LandTable,
    LandFormModal,
    LandDetailModal,
    LandGeoImportModal
  },
  props: {
    /** 下钻条件（例如从别处带 crzdbh 过来）；只在首次创建时生效 */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      /**
       * 数据管理页的三块面板状态（与 data/index.vue 是同一份模块级单例）。
       * 本组件只用 right（检索列宽度 / 折叠）与 bottom（列表高度）——
       * 左导航宽度由页面持，列表左偏移通过父页面的 --data-nav-offset 变量拿到。
       */
      layout: usePanelLayout('data'),
      loading: false,
      query: Object.assign({}, this.initialQuery || {}),
      dataSource: [],
      selectedRowKeys: [],
      pagination: defaultPagination(10),
      removeVisible: false,
      removing: false,
      removeReason: '',
      removeTargets: [],
      /** 当前定位目标（连点保护：飞行落定后只替换最后一次点的那个） */
      locateTargetId: ''
    }
  },
  computed: {
    /*
     * 列表高度 / 上下限都从 panelLayout 取（不再写模块级常量）。
     * ★ 模板只能访问**实例上**的东西，所以仍要用 computed 暴露；
     *   直接写 :min="LIST_MIN_HEIGHT" 会取到 undefined。
     */
    listHeight () {
      return this.layout.bottom.size
    },
    listMinHeight () {
      return this.layout.bottom.min
    },
    listMaxHeight () {
      return this.layout.bottom.max
    }
  },
  created () {
    // 图层实例不用做成响应式（纯 Cesium 实体管理，与 Vue 渲染无关）
    this.geoLayer = createGeoLayer('land')
    this.loadData()
  },
  activated () {
    /*
     * keep-alive 的激活钩子（首次挂载也会触发一次）：
     * 每次进「经营性用地信息录入」菜单都重铺宗地几何图层 ——
     * 这样别处新增/画图的宗地回到本菜单就能看到最新状态。
     * 切走页签时由 deactivated 清掉（图层别压在别的菜单上面）。
     * 普通激活仍走版本化增量；只有本页写操作完成后才强制重建。
     */
    this.loadGeoLayer(false)
  },
  deactivated () {
    this.geoLayer.clearAll()
  },
  beforeDestroy () {
    this.geoLayer.clearAll()
  },
  methods: {
    /* ---------------- 数据 ---------------- */

    loadData () {
      this.loading = true
      const params = Object.assign(compactQuery(this.query), {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize
      })

      return queryLandAdminPage(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '宗地列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.pagination = Object.assign({}, this.pagination, {
            total: Number(page.total || 0)
          })
          this.fillCounts()

          // 当前页被删空时自动回退一页，避免用户停在空白页以为数据没了
          if (!this.dataSource.length && this.pagination.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadData()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    /**
     * 补「配套数」。
     * ★ 只在**按宗地编号精确筛出一条**时才查：这种查询的响应必然是单条，
     *   与 `/land/data/land/options` 返回的那一条能对上号，不存在错配风险；
     *   列表态下那个接口按编号排序、只返回前 N 条，与本页的筛选对不上，
     *   硬套会显示成「另一条宗地的配套数」—— 那比不显示更糟。
     */
    fillCounts () {
      const code = String(this.query.crzdbh || '').trim()
      if (!code || this.dataSource.length !== 1) return
      const row = this.dataSource[0]
      queryLandOptions({ keyword: code, limit: 5 })
        .then((res) => {
          if (!res || !res.success) return
          const hit = (res.result || []).find((item) => item && item.crzdbh === row.crzdbh)
          if (!hit) return
          this.$set(row, 'facilityCount', hit.facilityCount === undefined ? null : hit.facilityCount)
        })
        .catch(() => {
          // 只是给一个数字，失败就不显示（表格里仍是「—」）
        })
    },

    /**
     * 加载宗地几何图层（全部已画图宗地的面）。
     * 只取轻量列（id/编号/名称/GeoJSON），失败静默 —— 图层是辅助视图，
     * 列表本身不受影响；?map=0 预览时 landGeoLayer 内部会安全空转。
     */
    loadGeoLayer (forceRefresh) {
      // 写操作成功后同时作废浏览器缓存并要求后端重建，避免同秒写入时版本号未变仍铺旧图。
      if (forceRefresh) {
        geoOverviewCache.version = ''
        geoOverviewCache.lands = null
      }
      // 版本化增量：图形没变（unchanged）时直接用本地缓存铺图（零数据传输）；
      // 变了才拉全量概览并更新缓存。概览档后端预计算（40 点/环），无需传参。
      queryLandGeoList(geoOverviewCache.version, forceRefresh === true)
        .then((res) => {
          if (!res || !res.success) return
          const data = res.result || {}
          if (data.unchanged && geoOverviewCache.lands) {
            this.geoLayer.renderAll(geoOverviewCache.lands, false)
            return
          }
          geoOverviewCache.version = data.version || ''
          geoOverviewCache.lands = data.lands || []
          this.geoLayer.renderAll(geoOverviewCache.lands, false)
        })
        .catch(() => { /* 同上：辅助视图，失败不打扰 */ })
    },

    /**
     * 列表**双击**某条：地图定位 + 高亮 + **按需换精确图形**。
     *
     * ★ 两级加载的第二级：先用概览几何立即飞行定位（等不了网络），
     *   同时按 id 拉该宗地的精确图形（几 KB）替换概览面 —— 替换成功后
     *   重新高亮，边界即测绘精度；拉取失败静默，概览面仍可定位查看。
     * ★ 只有画过图形的宗地能定位 —— 没画过图的行不在图层里，
     *   明确告诉用户为什么没反应，而不是让他以为点了没生效。
     */
    handleLocate (record) {
      if (!record || !record.id) return
      if (!this.geoLayer.has(record.id)) {
        toast.warning('该宗地还没有地块图形：可在编辑里「添加地块」绘制，或用工具条「导入地块图形」导入')
        return
      }
      const t0 = performance.now()
      console.log('[定位计时] 双击: ' + record.crzdbh)
      this.locateTargetId = record.id
      this.geoLayer.highlight(record.id)
      console.log('[定位计时] 高亮调用返回: ' + Math.round(performance.now() - t0) + 'ms')
      // 先用概览几何立即飞行（不等网络）；「换精确图形」推迟到飞行落定之后 ——
      // 800 点贴地面的建面开销会阻塞主线程，放在飞行中就是肉眼可见的定位迟滞
      // 图形请求与飞行并行（提前 ~1.2s），飞行落定即可应用
      const detailPromise = queryLandGeoDetail(record.id)
      this.geoLayer.flyTo(record.id, () => {
        console.log('[定位计时] 飞行落定: ' + Math.round(performance.now() - t0) + 'ms')
        detailPromise.then((res) => {
          console.log('[定位计时] 图形数据到手: ' + Math.round(performance.now() - t0) + 'ms')
          // 连点了别的行 / 已切走面板：放弃这次替换
          if (this.locateTargetId !== record.id) return
          if (!res || !res.success || !res.result || !res.result.dkGeojson) return
          this.geoLayer.applyPreciseOverlay(record.id, res.result.dkGeojson)
          console.log('[定位计时] 定位档覆盖层应用完成: 距双击 ' + Math.round(performance.now() - t0) + 'ms')
        }).catch(() => { /* 概览面仍可查看，失败不打扰 */ })
      })
    },

    handleSearch (query) {
      this.query = query || {}
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.loadData()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    handleSelectChange (keys) {
      this.selectedRowKeys = keys || []
    },

    /**
     * 拖动/键盘调整列表高度。
     * 只记高度，不重新请求数据 —— 表格可见行数由高度决定，数据本身没变；
     * 每次拖动都发请求会让大屏在拖动过程中疯狂打后端。
     * ★ 高度写进 panelLayout（持久化），配套面板与氛围蒙版读同一份值。
     */
    handleResize (height) {
      this.layout.setSize('bottom', height)
    },

    /* ---------------- 增删改 ---------------- */

    handleAdd () {
      this.$refs.formModal.showAdd()
    },

    /** 打开「导入地块图形（GeoJSON）」弹窗 */
    handleGeoImport () {
      this.$refs.geoImportModal.open()
    },

    /**
     * GeoJSON 图形导入完成：刷新列表，并**重铺几何图层** ——
     * 刚导进来的图形要立刻在地球上可见、可双击定位，这是大屏端导图形单独的价值。
     */
    handleGeoImported () {
      this.loadData()
      this.loadGeoLayer(true)
    },

    handleEdit (record) {
      if (!record || !record.id) return
      this.$refs.formModal.showEdit(record)
    },

    handleSaved () {
      this.loadData()
      // 新增/编辑可能刚画了图形（或改了编号），几何图层同步重铺
      this.loadGeoLayer(true)
    },

    handleDetail (record) {
      this.$refs.detailModal.open(record, 'base')
    },

    handleHistory (record) {
      this.$refs.detailModal.open(record, 'history')
    },

    /** 附件：直接打开详情弹窗的「附件」页签（按材料类型分组的目录树） */
    handleAttachments (record) {
      this.$refs.detailModal.open(record, 'files')
    },

    /** 单条移除：也走原因弹窗（原因会写进操作记录） */
    handleRemove (record) {
      if (!record || !record.id) return
      this.removeTargets = [record]
      this.removeReason = ''
      this.removeVisible = true
    },

    /** 批量移除：一次原因，逐条留痕 */
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
      const reason = String(this.removeReason || '').trim()
      this.removing = true
      const request = targets.length === 1
        ? deleteLandAdmin(targets[0].id, reason || undefined)
        : deleteLandAdminBatch(targets.map((row) => row.id), reason || undefined)

      request
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
          } else {
            toast.success(res.message || '已移除，可在「数据更新与移除」中恢复')
          }
          this.selectedRowKeys = this.selectedRowKeys.filter(
            (key) => !targets.some((row) => row.id === key)
          )
          this.closeRemove()
          this.loadData()
          // 被移除的宗地要从地图图层上撤下来，重铺即同步
          this.loadGeoLayer(true)
        })
        .catch(() => {
          // 请求层已提示
        })
        .finally(() => {
          this.removing = false
        })
    },

    /* ---------------- 跨界跳转 ---------------- */

    /**
     * 跳到「配套地块数据录入」面板看该宗地的配套。
     * 走父组件的 drill 通道（而不是路由）：同一个数据管理页内的面板切换
     * 不需要换地址，也就不会触发一次路由 watcher 的重复下发。
     */
    handleFacilities (record) {
      if (!record) return
      if (!record.crzdbh) {
        toast.warning('该宗地没有出让宗地编号，无法按编号查配套')
        return
      }
      this.$emit('drill', { tab: 'facility', crzdbh: record.crzdbh })
    },

    /* ---------------- 对外（父组件调用） ---------------- */

    /**
     * 应用下钻条件。
     * ★ 为什么需要：面板被 keep-alive 缓存后 created 不会再跑，
     *   initialQuery 改不动了，必须由父组件在切换后显式调用这个方法。
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
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.land-entry {
  /*
   * 铺满整个设计画布，内部按设计稿坐标摆放「右侧检索列 + 贴底列表」。
   * 坐标系与 data/index.vue 的左侧栏（left:30 / top:104 / bottom:40 / 宽 200）
   * 完全一致 —— 「与左侧边栏对称」在代码里也是同一组数字。
   *
   * ★ pointer-events: none 是必须的：本节点已经铺满整屏，
   *   若在这里开 auto 就会把地图的鼠标事件全吃掉（拖不动、滚不动）。
   *   交互只由下面两块区域各自显式开启。
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
    // 折叠 = 整列向右滑出屏幕（位移 = 列宽 + 30，与右侧 rail 同一口径）
    transition: transform var(--screen-duration-slow) var(--screen-ease);
  }

  /* 右侧检索列折叠（由 data/index.vue 的右屏边把手切换） */
  &.is-search-collapsed &__search {
    transform: translateX(calc(var(--data-search-w, 200px) + 30px));
  }

  /*
   * 面板撑满这一列。
   * ★ scrollable 是必须的：竖排的检索条件展开「更多条件」后会超出列高，
   *   不能滚就只能被裁掉，用户会以为条件没渲染出来。
   */
  &__search-panel {
    flex: 1 1 0;
    min-height: 0;
  }

  /* ---------------- 底部列表（贴底 + 可拖高） ---------------- */
  &__list {
    position: absolute;
    // 左偏移跟着左导航宽度 / 折叠走（--data-nav-offset：展开 = 导航宽 + 50，折叠 = 30）
    left: var(--data-nav-offset, 250px);
    // 与右侧检索列留出与左侧同样的 20px 间距（检索列宽 + 边距 30 + 间距 20）
    right: calc(var(--data-search-w, 200px) + 50px);
    bottom: 40px;
    // 高度由 listHeight 内联控制（拖动改变），这里只兜底一个最小值
    display: flex;
    flex-direction: column;
    min-height: 0;
    pointer-events: auto;
    // 侧栏 / 检索列拖宽或折叠时，列表左右跟着让位（与面板滑出同一个 320ms）
    transition: left var(--screen-duration-slow) var(--screen-ease),
      right var(--screen-duration-slow) var(--screen-ease);
  }

  /* 右侧检索列折叠后，列表铺到屏边 30，不再给它留 250 */
  &.is-search-collapsed &__list {
    right: 30px;
  }

  /* 调节条占顶部 12px，剩下的全部给面板 */
  &__list-panel {
    flex: 1 1 0;
    min-height: 0;
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
.land-entry__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
