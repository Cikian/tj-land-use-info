<template>
  <!--
    ArchiveQuery 档案查询（方案 2.3.2 第 3 项）
    --------------------------------
    与「档案维护」共用同一套检索条件与同一个列表组件，差别只有两点：
      1. 检索条件默认全部展开（这个页签就是为检索而来的）；
      2. 列表只读（不提供编辑/删除），但可以导出查询结果。

    支持从「档案统计」按项目下钻：父级调用 applyDrill({ ptxmmc }) 或 applyDrill({ crzdbh })。
  -->
  <div class="archive-query">
    <screen-panel class="archive-query__search" title="检索条件">
      <archive-search-form
        ref="search"
        :default-expanded="true"
        :initial-query="initialQuery"
        @search="handleSearch"
      />
    </screen-panel>

    <screen-panel class="archive-query__list" title="查询结果">
      <template #extra>
        <span class="archive-query__total">共命中 <b>{{ total }}</b> 个档案</span>

        <screen-button
          type="primary"
          size="sm"
          icon="download"
          :disabled="!total"
          @click="handleExport"
        >
          导出查询结果 ZIP
        </screen-button>

        <screen-button size="sm" icon="pie-chart" @click="$emit('open-statistics')">
          档案统计
        </screen-button>

        <screen-button size="sm" icon="reload" :loading="loading" @click="loadData">刷新</screen-button>
      </template>

      <archive-table
        :data-source="dataSource"
        :loading="loading"
        readonly
        empty-text="没有符合条件的档案，试试放宽检索条件"
        @detail="handleDetail"
        @action="handleTableAction"
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

    <archive-detail-modal ref="detailModal" @export="handleExportOne" />
  </div>
</template>

<script>
import { ScreenPanel, ScreenButton, ScreenPagination } from '@/components/screen'
import { toast } from '@/components/screen/toast'
import ArchiveSearchForm from './ArchiveSearchForm.vue'
import ArchiveTable from './ArchiveTable.vue'
import ArchiveDetailModal from './ArchiveDetailModal.vue'
import { queryArchiveList, exportArchiveZip } from '@/api/land/archive'
import { defaultPagination } from '../constants'
export default {
  name: 'ArchiveQuery',
  components: { ScreenPanel, ScreenButton, ScreenPagination, ArchiveSearchForm, ArchiveTable, ArchiveDetailModal },
  props: {
    /** 外部（统计页下钻 / 路由 query）带进来的初始条件 */
    initialQuery: { type: Object, default: () => ({}) },
  },
  data () {
    return {
      loading: false,
      query: {},
      dataSource: [],
      total: 0,
      pagination: defaultPagination(20),
    }
  },
  created () {
    this.query = Object.assign({}, this.initialQuery || {})
    this.loadData()
  },
  methods: {
    loadData () {
      this.loading = true
      const params = Object.assign({}, this.query, {
        pageNo: this.pagination.current,
        pageSize: this.pagination.pageSize,
      })

      return queryArchiveList(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案列表加载失败')
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })
        })
        .finally(() => {
          this.loading = false
        })
    },

    handleSearch (query) {
      this.query = query || {}
      this.pagination.current = 1
      this.loadData()
    },

    handlePageChange ({ current, pageSize }) {
      this.pagination = Object.assign({}, this.pagination, { current, pageSize })
      this.loadData()
    },

    handleTableAction (row, col) {
      if (col && col.key === 'archiveNo') {
        this.handleDetail(row)
      }
    },

    handleDetail (record) {
      this.$refs.detailModal.open(record)
    },

    handleExport () {
      exportArchiveZip(this.query)
      toast.info('已开始导出，请稍候…')
    },

    handleExportOne (record) {
      exportArchiveZip({
        facilityId: record.facilityId || undefined,
        crzdbh: record.facilityId ? undefined : record.crzdbh || undefined,
      })
      toast.info('已开始按项目导出，请稍候…')
    },

    /**
     * 按项目下钻（统计页点击「查看档案」时由父级调用）。
     * 同时把条件写回检索表单，用户能看到是「按什么筛出来的」，
     * 而不是面对一个结果被悄悄过滤却看不出原因的列表。
     * @param {{ptxmmc?: string, crzdbh?: string}} params
     */
    applyDrill (params) {
      const query = Object.assign({}, params || {})
      this.query = query
      this.pagination.current = 1
      if (this.$refs.search && this.$refs.search.setQuery) {
        this.$refs.search.setQuery(query)
      }
      this.loadData()
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.archive-query {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

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
      color: var(--screen-accent);
    }
  }
}

.archive-query__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
