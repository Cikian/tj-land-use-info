<template>
  <!--
    EscalationEntry 项目录入（方案 2.3.3 第 1 项）
    --------------------------------
    「标准化录入提级论证项目的基本信息、关联地块信息（选填）、论证信息与论证结果，
      并上传论证材料。」

    页面结构照档案维护页（ArchiveMaintain.vue）：
      上排：检索条件（可折叠）
      下排：列表面板（工具条 + 表格 + 分页），列表吃掉剩余高度
      新增 / 编辑走三步向导（EscalationFormModal），详情走只读弹窗（EscalationDetailModal）

    项目编号默认自动生成（TJ-{年份}-{4位流水}），允许按历史编号手工改写；
    本系统只做台账与记录，实际论证流程在线下办理。
  -->
  <div class="escalation-entry">
    <screen-panel class="escalation-entry__search" title="项目检索" collapsible>
      <escalation-search-form ref="search" @search="handleSearch" />
    </screen-panel>

    <screen-panel class="escalation-entry__list" title="项目列表">
      <template #extra>
        <span class="escalation-entry__total">
          共 <b>{{ total }}</b> 个项目
          <template v-if="selectedRowKeys.length">
            · 已选 <b class="is-accent">{{ selectedRowKeys.length }}</b> 项
          </template>
        </span>

        <screen-button type="primary" size="sm" icon="plus" @click="handleAdd">新增项目</screen-button>

        <screen-button size="sm" icon="download" :disabled="!total" @click="handleExport">
          导出 Excel
        </screen-button>

        <screen-button size="sm" icon="bar-chart" @click="$emit('open-query')">
          查询统计
        </screen-button>

        <screen-popconfirm
          v-if="selectedRowKeys.length"
          title="删除后不可恢复，确定批量删除选中的项目吗？"
          :description="`共 ${selectedRowKeys.length} 个项目及其材料会被删除`"
          width="286"
          @confirm="handleBatchDelete"
        >
          <screen-button type="danger" size="sm" icon="trash">批量删除</screen-button>
        </screen-popconfirm>

        <screen-button size="sm" icon="reload" :loading="loading" @click="loadData">刷新</screen-button>
      </template>

      <escalation-table
        :data-source="dataSource"
        :loading="loading"
        selectable
        :selected-row-keys="selectedRowKeys"
        @select-change="handleSelectChange"
        @detail="handleDetail"
        @edit="handleEdit"
        @delete="handleDelete"
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

    <escalation-form-modal ref="formModal" @ok="handleSaved" />
    <escalation-detail-modal ref="detailModal" @edit="handleEditFromDetail" />
  </div>
</template>

<script>
import { ScreenPanel, ScreenButton, ScreenPagination, ScreenPopconfirm } from '@/components/screen'
import { toast } from '@/components/screen/toast'
import EscalationSearchForm from './EscalationSearchForm.vue'
import EscalationTable from './EscalationTable.vue'
import EscalationFormModal from './EscalationFormModal.vue'
import EscalationDetailModal from './EscalationDetailModal.vue'
import {
  queryProjectList,
  deleteProject,
  deleteProjectBatch,
  exportProjectXls,
} from '@/api/land/escalation'
import { defaultPagination } from '../constants'

export default {
  name: 'EscalationEntry',
  components: {
    ScreenPanel,
    ScreenButton,
    ScreenPagination,
    ScreenPopconfirm,
    EscalationSearchForm,
    EscalationTable,
    EscalationFormModal,
    EscalationDetailModal,
  },
  data () {
    return {
      loading: false,
      query: {},
      dataSource: [],
      total: 0,
      selectedRowKeys: [],
      pagination: defaultPagination(10),
    }
  },
  created () {
    this.loadData()
  },
  methods: {
    loadData () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize,
      })

      return queryProjectList(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '项目列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })

          // 当前页被删空时自动回退一页，避免用户停在空白页以为数据没了
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadData()
          }
        })
        .finally(() => {
          this.loading = false
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

    handleAdd () {
      this.$refs.formModal.showAdd()
    },

    handleEdit (record) {
      if (!record || !record.id) return
      this.$refs.formModal.showEdit(record)
    },

    /** 详情弹窗里的「编辑」：先关详情再开向导，避免两个弹窗叠在一起 */
    handleEditFromDetail (record) {
      if (this.$refs.detailModal) this.$refs.detailModal.close()
      this.handleEdit(record)
    },

    handleSaved () {
      this.loadData()
    },

    handleDetail (record) {
      this.$refs.detailModal.open(record)
    },

    handleDelete (record) {
      deleteProject(record.id).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '删除失败')
          return
        }
        toast.success(res.message || '删除成功')
        this.selectedRowKeys = this.selectedRowKeys.filter((key) => key !== record.id)
        this.loadData()
      })
    },

    handleBatchDelete () {
      if (!this.selectedRowKeys.length) return
      deleteProjectBatch(this.selectedRowKeys).then((res) => {
        if (!res || !res.success) {
          toast.error((res && res.message) || '批量删除失败')
          return
        }
        toast.success(res.message || '批量删除成功')
        this.selectedRowKeys = []
        this.loadData()
      })
    },

    /** 按当前检索条件导出（与列表用同一份条件，避免「看到的」和「导出的」不一致） */
    handleExport () {
      const query = this.$refs.search ? this.$refs.search.getQuery() : this.query
      exportProjectXls(query)
      toast.info('已开始导出，请稍候…')
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.escalation-entry {
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
    // flex-basis 必须是 0：用 auto 会按内容撑开，导致正文超出模块高度、分页被挤出可见区
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
}

// 工具条上的按钮较多，窄屏允许换行而不是被压扁
.escalation-entry__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
