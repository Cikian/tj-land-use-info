<template>
  <a-card :bordered="false" class="archive-list-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">档案维护</h2>
        <p class="page-head__desc">
          把已上传的配套项目档案登记成案卷：选择出让宗地与配套项目、填写档案基本信息、上传档案文件。
          每个文件必须关联一个档案类别（末级类别）；档案号默认自动生成，也允许按历史编号手工改写。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:archive:add'" type="primary" icon="plus" @click="handleAdd">新增档案</a-button>
        <a-button v-has="'land:archive:export'" icon="download" :disabled="!total" @click="handleExport">按条件导出 ZIP</a-button>
        <a-button v-has="'land:archive:stat'" icon="pie-chart" @click="goStatistics">档案统计</a-button>
      </div>
    </div>

    <!-- 查询条件 -->
    <archive-search-form ref="search" @search="handleSearch" />

    <!-- 列表 -->
    <div class="list-panel">
      <div class="list-panel__toolbar">
        <div class="list-panel__toolbar-left">
          <span class="list-panel__total">
            共 <b>{{ total }}</b> 个档案
          </span>
          <span v-if="selectedRowKeys.length" class="list-panel__selected">
            已选 <b>{{ selectedRowKeys.length }}</b> 项
          </span>
        </div>
        <div class="list-panel__toolbar-right">
          <a-popconfirm
            v-if="selectedRowKeys.length"
            v-has="'land:archive:delete'"
            title="删除后不可恢复，确定批量删除选中的档案吗？"
            okText="确定"
            cancelText="取消"
            @confirm="handleBatchDelete">
            <a-button type="danger" icon="delete" :loading="deleting">批量删除</a-button>
          </a-popconfirm>
          <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
        </div>
      </div>

      <archive-table
        :dataSource="dataSource"
        :loading="loading"
        :pagination="pagination"
        :selectable="true"
        :selectedRowKeys="selectedRowKeys"
        @change="handleTableChange"
        @select-change="handleSelectChange"
        @detail="handleDetail"
        @edit="handleEdit"
        @delete="handleDelete" />
    </div>

    <archive-modal ref="modal" @ok="loadData" />
    <archive-detail-modal ref="detail" @edit="handleEdit" @export="handleExportOne" />
  </a-card>
</template>

<script>
  import ArchiveSearchForm from './modules/ArchiveSearchForm'
  import ArchiveTable from './modules/ArchiveTable'
  import ArchiveModal from './modules/ArchiveModal'
  import ArchiveDetailModal from './modules/ArchiveDetailModal'
  import { queryArchiveList, deleteArchive, deleteArchiveBatch, exportArchiveZip } from '@/api/land/archive'

  /**
   * 档案维护（方案 2.3.2 第 2 项）
   *
   * 「可在已上传的配套项目中添加档案信息，并记录相关操作」——
   * 新增/编辑弹窗里的配套项目是下拉选择已有记录，不能手工输入新项目名；
   * 所有写操作都会在后端写 t_archive_log 操作记录。
   */
  export default {
    name: 'ArchiveList',
    components: { ArchiveSearchForm, ArchiveTable, ArchiveModal, ArchiveDetailModal },
    data () {
      return {
        loading: false,
        deleting: false,
        query: {},
        dataSource: [],
        total: 0,
        selectedRowKeys: [],
        pagination: {
          current: 1,
          pageSize: 10,
          total: 0,
          showSizeChanger: true,
          showQuickJumper: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: total => `共 ${total} 条`
        }
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
          pageSize: this.pagination.pageSize
        })
        return queryArchiveList(params).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            return
          }
          const page = res.result || {}
          this.dataSource = page.records || []
          this.total = page.total || 0
          this.pagination = Object.assign({}, this.pagination, { total: this.total })
          // 当前页被删空时自动回退一页，避免停在空白页
          if (!this.dataSource.length && this.total > 0 && this.pagination.current > 1) {
            this.pagination.current -= 1
            return this.loadData()
          }
        }).finally(() => {
          this.loading = false
        })
      },
      handleSearch (query) {
        this.query = query || {}
        this.pagination.current = 1
        this.selectedRowKeys = []
        this.loadData()
      },
      handleTableChange (pagination) {
        this.pagination = Object.assign({}, this.pagination, {
          current: pagination.current,
          pageSize: pagination.pageSize
        })
        this.loadData()
      },
      handleSelectChange (keys) {
        this.selectedRowKeys = keys
      },
      handleAdd () {
        this.$refs.modal.showAdd()
      },
      handleEdit (record) {
        this.$refs.modal.showEdit(record)
      },
      handleDetail (record) {
        this.$refs.detail.open(record)
      },
      handleDelete (record) {
        deleteArchive(record.id).then(res => {
          if (res.success) {
            this.$message.success(res.message || '删除成功')
            this.selectedRowKeys = this.selectedRowKeys.filter(key => key !== record.id)
            this.loadData()
          } else {
            this.$message.warning(res.message)
          }
        })
      },
      handleBatchDelete () {
        if (!this.selectedRowKeys.length) {
          return
        }
        this.deleting = true
        deleteArchiveBatch(this.selectedRowKeys).then(res => {
          if (res.success) {
            this.$message.success(res.message || '批量删除成功')
            this.selectedRowKeys = []
            this.loadData()
          } else {
            this.$message.warning(res.message)
          }
        }).finally(() => {
          this.deleting = false
        })
      },
      /** 按当前查询条件导出 */
      handleExport () {
        const query = this.$refs.search ? this.$refs.search.getQuery() : {}
        exportArchiveZip(query)
        this.$message.success('已开始导出，请稍候…')
      },
      /** 从详情页导出「该项目」的全部档案 */
      handleExportOne (record) {
        exportArchiveZip({
          facilityId: record.facilityId || undefined,
          crzdbh: record.facilityId ? undefined : (record.crzdbh || undefined)
        })
        this.$message.success('已开始按项目导出，请稍候…')
      },
      goStatistics () {
        this.$router.push({ path: '/land/archive/statistics' })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;

  .archive-list-page {
    /deep/ .ant-card-body {
      padding: 20px 24px 24px;
    }
  }

  .page-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    padding-bottom: 16px;
    border-bottom: 1px solid @border-color;

    &__title {
      margin: 0 0 6px;
      font-size: 18px;
      font-weight: 600;
      color: #0f172a;
    }

    &__desc {
      margin: 0;
      max-width: 820px;
      font-size: 13px;
      line-height: 20px;
      color: #475569;
    }

    &__actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      flex-shrink: 0;
    }
  }

  .list-panel {
    margin-top: 4px;

    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 12px;
    }

    &__toolbar-left {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 13px;
      color: #475569;

      b {
        color: #0f172a;
      }
    }

    &__selected {
      color: #1d4ed8;
    }

    &__toolbar-right {
      display: flex;
      gap: 8px;
    }
  }

  @media (max-width: 576px) {
    .page-head__actions {
      width: 100%;

      /deep/ .ant-btn {
        flex: 1 1 auto;
      }
    }
  }
</style>
