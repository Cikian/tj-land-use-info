<template>
  <!--
    LandEntryPanel 经营性用地信息录入（方案 2.3.1（三）第 1 项）
    --------------------------------
    「逐条录入经营性用地信息，并支持修改与移除」——
    布局照档案维护（ArchiveMaintain）：
      上排：检索条件（可折叠）
      下排：列表面板（工具条 + 表格 + 分页），列表吃掉剩余高度
    弹窗：新增/编辑、详情（含变更履历）、移除原因确认。

    ★ 移除为什么必须带「原因」：
      宗地移除是软删，数据还在回收站里。半年后有人问「这块地怎么没了」，
      只有原因能回答。所以移除弹窗里原因虽然可选，但按钮上写明了「原因会写入变更履历」，
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
  <div class="land-entry">
    <screen-panel class="land-entry__search" title="宗地检索" collapsible>
      <land-search-form ref="search" :initial-query="initialQuery" @search="handleSearch" />
    </screen-panel>

    <screen-panel class="land-entry__list" title="经营性用地列表">
      <template #extra>
        <span class="land-entry__total">
          共 <b>{{ pagination.total }}</b> 宗
          <template v-if="selectedRowKeys.length">
            · 已选 <b class="is-accent">{{ selectedRowKeys.length }}</b> 宗
          </template>
        </span>

        <screen-button type="primary" size="sm" icon="plus" @click="handleAdd">新增宗地</screen-button>

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
        @edit="handleEdit"
        @remove="handleRemove"
        @facilities="handleFacilities"
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

    <land-form-modal ref="formModal" @ok="handleSaved" />
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
          原因会写入该宗地的变更履历，供日后追溯。
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
          本次将移除 <b>{{ removeTargets.length }}</b> 宗宗地，原因会写进每一条的履历。
        </p>
      </div>
    </screen-modal>
  </div>
</template>

<script>
import {
  ScreenPanel,
  ScreenButton,
  ScreenPagination,
  ScreenPopconfirm,
  ScreenModal,
  ScreenField,
  ScreenInput,
  ScreenIcon
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import LandSearchForm from './LandSearchForm.vue'
import LandTable from './LandTable.vue'
import LandFormModal from './LandFormModal.vue'
import LandDetailModal from './LandDetailModal.vue'
import { queryLandAdminPage, deleteLandAdmin, deleteLandAdminBatch } from '@/api/land/landAdmin'
import { queryLandOptions } from '@/api/land/landData'
import { defaultPagination, compactQuery } from '../constants'

export default {
  name: 'LandEntryPanel',
  components: {
    ScreenPanel,
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
    LandDetailModal
  },
  props: {
    /** 下钻条件（例如从别处带 crzdbh 过来）；只在首次创建时生效 */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
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
  created () {
    this.loadData()
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

    /* ---------------- 增删改 ---------------- */

    handleAdd () {
      this.$refs.formModal.showAdd()
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

    /** 单条移除：也走原因弹窗（原因会写进履历） */
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
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  // 检索条件按内容高度，列表吃掉剩余高度
  &__search {
    flex: 0 0 auto;
  }

  &__list {
    // flex-basis 必须是 0：用 auto 会按内容撑开，导致分页被挤出可见区
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
