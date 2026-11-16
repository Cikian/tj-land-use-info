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
import { queryLandAdminPage, deleteLandAdmin, deleteLandAdminBatch } from '@/api/land/landAdmin'
import { queryLandOptions } from '@/api/land/landData'
import { defaultPagination, compactQuery } from '../constants'
// 移除记录后联动清切片库图形（fire-and-forget，失败只提示不阻断）
import { purgeFeatureGroups } from '../sliceInfo'

/*
 * 列表高度（**设计画布像素**）现在由 panelLayout 的 data.bottom 统一持有：
 * 默认 360（实测可见 3 整行、第 4 行露一半）、下限 200、上限 936。
 * ★ 2026-11 起改成共用状态而不是本组件的 data：一是配套面板也读同一份高度，
 *   切页签回来高度不跳；二是 views/screen/index.vue 的氛围蒙版要跟着列表高度伸缩。
 */

/* ★ 2026-11-14 起进入菜单不再自动铺地块图层（用户要求：地图保持干净）——
 *   双击列表行定位到 MVT 切图层上的该宗地（见 handleLocate）；
 *   「导入地块图形」仍可用。概览缓存随之移除。 */

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
      removeTargets: []
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
    // 首次进入与「切页签回来」走同一个入口，两处共用同一套同 tick 去重（见 ensureListLoaded）
    this.ensureListLoaded()
  },
  activated () {
    /*
     * keep-alive 的激活钩子（首次挂载也会触发一次）只做一件事：**重拉列表**（测试清单 P1-02）。
     * 页签被 keep-alive 缓存后 created 不再跑，会出现「回收站里已经恢复了这条宗地、
     * 切回录入页列表里还是没有」。与 created 那一次由 ensureListLoaded 合并，不会重复请求。
     *
     * ★ 合并说明（2026-11-17）：这里不再铺地块图层 —— 2026-11-14 起进入菜单不自动铺图
     *   （地图保持干净），定位靠双击列表行（handleLocate）、图形靠「管理地块图形」
     *   （FeatureGroupModal + sliceInfo）。所以 activated / deactivated / beforeDestroy
     *   里原本的 geoLayer 生命周期整块随远端一起移除，只保留列表刷新这一项。
     */
    this.ensureListLoaded()
  },
  methods: {
    /* ---------------- 数据 ---------------- */

    /**
     * 首次挂载（created）与 keep-alive 激活（activated）共用的列表拉取入口。
     *
     * ★ 为什么必须去重：首次进入时 created 与 activated 在同一 tick 内先后触发，
     *   不去重就会对同一页发两次请求（列表 + 配套数各两遍）。
     *   写法照 DataRecyclePanel.ensureLoaded：标记 + $nextTick 把同 tick 的多次调用合成一次。
     * ★ 为什么还要比 listLoadSeq：激活排队的这次刷新可能还没跑，别处就已经显式加载过
     *   （父页 drill 进来会立刻 applyDrill → loadData；保存/移除后也是 loadData），
     *   那时再补一次就是纯重复请求。loadData 每次自增序号，回调里序号变了就放弃。
     * ★ 页码与查询条件原样保留：激活刷新是「拿最新数据」，不是新查询 ——
     *   重置页码会把用户正在看的那一页抢走；条目变少让当前页空掉时，
     *   loadData 里已有「当前页被删空自动回退一页」的兜底，不需要在这里重置。
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
    /**
     * 列表**双击**某条：**定位到 MVT 切图层上的该宗地**。
     *
     * ★ 2026-11-14 起定位走「唯一值 → 切片源表查范围 → 相机飞行」——
     *   不再依赖 dk_geojson（GeoJSON 字段即将废弃）。实际查询与飞行由
     *   父组件（data/index.vue）统一处理，本面板只上报记录。
     */
    handleLocate (record) {
      if (!record || !record.id) return
      this.$emit('locate-record', record)
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

    /** GeoJSON 图形导入完成：刷新列表。★ 图层不再自动重铺（双击定位时才按需渲染）。 */
    handleGeoImported () {
      this.loadData()
    },

    handleEdit (record) {
      if (!record || !record.id) return
      if (this.$refs.detailModal) this.$refs.detailModal.close()
      this.$refs.formModal.showEdit(record)
    },

    handleSaved () {
      this.loadData()
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
            // 联动清掉切片库里这些宗地名下的图形（fire-and-forget；失败只提示，
            // 业务记录已软删成功，不能因图形清理失败而阻断列表刷新）
            purgeFeatureGroups('land', targets, toast)
          }
          this.selectedRowKeys = this.selectedRowKeys.filter(
            (key) => !targets.some((row) => row.id === key)
          )
          this.closeRemove()
          this.loadData()
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
      if (this.$refs.detailModal) this.$refs.detailModal.close()
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
    },

    /**
     * P2-01 统一契约：父页在「普通离开再进入」时调它清空检索。
     * 与 applyDrill({}) 等价（`setQuery({})` 会回到 buildEmptyQuery，连 bizId/bizKey
     * 这类隐藏条件一起清），单列一个方法是为了让四个域的调用点同名、可读。
     */
    resetFilters () {
      this.applyDrill({})
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
