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
        「点开六阶段」，点进去是权威视图。不假装「未开启」。

    ★ 为什么两个模式不做成一个接口：
      后端明确把「按宗地取配套 + 阶段汇总」（/byLand）与「跨宗地分页检索」
      （/list）分开：前者为这条主线服务、不带分页；后者是给「我要找某条配套」
      用的。硬合成一个接口会让分页口径与汇总口径互相牵制。

    事件：
      drill (payload)  跳到别的面板（如 { tab: 'attachment', bizType, bizKey }）
  -->
  <div class="facility-entry">
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
            打开六阶段进度
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
    <facility-detail-modal
      ref="detailModal"
      @edit="handleEdit"
      @remove="handleRemove"
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
          原因会写入变更履历，供日后追溯。
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
          本次将移除 <b>{{ removeTargets.length }}</b> 个配套项目，原因会写进每一条的履历。
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
  ScreenIcon
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
  deleteFacilityAdmin,
  deleteFacilityAdminBatch
} from '@/api/land/facilityAdmin'
import { queryLandOptions } from '@/api/land/landData'
import { defaultPagination, compactQuery } from '../constants'

/*
 * 列表高度（**设计画布像素**）—— 与 LandEntryPanel 同一套口径，
 * 改一处要同步改另一处（两个面板版式必须一致）。
 *
 * ★ 画布尺寸是自适应的（ScreenStage：高度贴合 1080、宽度 = 视口宽/scale），
 *   所以这里只写纵向值：底部留白 40、顶栏下沿 104 —— 都与画布宽度无关。
 * 默认 360：**实测**（1894×987 视口，Edge 无头，k=0.9139）可见 3 整行
 * （表格行高实测 56.5px，不是 --screen-row-height 的 48 —— 行内有进度条/标签会撑高）。
 *   · 下限 200：再矮放不下表头 + 1 行；
 *   · 上限 936：1080 - 104 - 40，即最多长到顶栏下面。
 */
const LIST_DEFAULT_HEIGHT = 360
const LIST_MIN_HEIGHT = 200
const LIST_MAX_HEIGHT = 936

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
    FacilityDetailModal,
    ProcessTreePanel
  },
  props: {
    /** 下钻条件（例如从宗地表点「该宗地配套」带 crzdbh 过来） */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      loading: false,
      query: Object.assign({}, this.initialQuery || {}),
      dataSource: [],
      total: 0,
      selectedRowKeys: [],
      pagination: defaultPagination(10),
      /** 宗地模式下不分页，这里记一下「当前是不是宗地模式」由 computed 判定 */
      landOptions: [],
      landSearchTimer: null,
      /** 列表高度（画布像素）：初始值即「默认只露 3~4 行」，之后由拖动/键盘改变 */
      listHeight: LIST_DEFAULT_HEIGHT,
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
    /** 上限直接取常量；做成 computed 是为了将来若要按画布实测高度自适应时只改一处 */
    listMaxHeight () {
      return LIST_MAX_HEIGHT
    },
    /*
     * ★ 上下限必须用 computed 暴露给模板：模板只能访问**实例上**的东西，
     *   直接写 :min="LIST_MIN_HEIGHT" 会取到 undefined（模块级常量不在实例上），
     *   组件退回自己的默认值 —— 不报错，但设置静默失效。
     */
    listMinHeight () {
      return LIST_MIN_HEIGHT
    }
  },
  created () {
    this.loadLandOptions('')
    this.loadData()
  },
  beforeDestroy () {
    if (this.landSearchTimer) {
      clearTimeout(this.landSearchTimer)
      this.landSearchTimer = null
    }
  },
  methods: {
    /* ---------------- 数据 ---------------- */

    loadData () {
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

    /** 选中宗地 → 清掉分页与选中行，按该宗地重查 */
    handleLandPicked (value) {
      this.pagination.current = 1
      this.selectedRowKeys = []
      if (this.$refs.search) {
        this.$refs.search.setQuery(Object.assign({}, this.query, { crzdbh: value || '' }))
        this.query = this.$refs.search.getQuery()
      }
      this.loadData()
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
    },

    /* ---------------- 检索与分页 ---------------- */

    handleSearch (query) {
      this.query = query || {}
      this.pagination.current = 1
      this.selectedRowKeys = []
      this.loadData()
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
      this.listHeight = height
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

    handleProcess (record) {
      if (!record || !record.id) return
      this.$refs.processPanel.open(record)
    },

    /** 标题栏的「打开六阶段进度」：宗地模式下默认打开第一个配套 */
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
            return
          }
          toast.success(res.message || '已移除，可在「数据更新与移除」中恢复')
          this.selectedRowKeys = this.selectedRowKeys.filter(
            (key) => !targets.some((row) => row.id === key)
          )
          this.closeRemove()
          this.loadData()
        })
        .catch(() => {
          // 请求层已提示
        })
    },

    /* ---------------- 对外（父组件调用） ---------------- */

    /**
     * 应用下钻条件。
     * ★ 面板被 keep-alive 缓存后 created 不会再跑，必须由父组件显式调用。
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
    width: 200px;
    display: flex;
    flex-direction: column;
    min-height: 0;
    pointer-events: auto;
  }

  /* scrollable：竖排条件展开后超出列高时必须能滚，否则会被裁掉 */
  &__search-panel {
    flex: 1 1 0;
    min-height: 0;
  }

  /* ---------------- 底部列表（贴底 + 可拖高） ---------------- */
  &__list {
    position: absolute;
    left: 250px;
    // 与右侧检索列留出与左侧同样的 20px 间距
    right: 250px;
    bottom: 40px;
    // 高度由 listHeight 内联控制（拖动改变）
    display: flex;
    flex-direction: column;
    min-height: 0;
    pointer-events: auto;
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
