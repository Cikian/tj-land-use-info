<template>
  <a-card :bordered="false" class="archive-query-page">
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">档案查询</h2>
        <p class="page-head__desc">
          按配套项目名称、出让宗地编号、档案类别、时间、档案年度、行政区划、责任部门、
          配套负责人、状态、密级与文件名进行组合检索。档案类别选到父类别时，会连同它下面所有子类一起检索。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:archive:export'" type="primary" icon="download" :disabled="!total" @click="handleExport">
          导出查询结果 ZIP
        </a-button>
        <a-button v-has="'land:archive:stat'" icon="pie-chart" @click="$router.push({ path: '/land/archive/statistics' })">
          档案统计
        </a-button>
      </div>
    </div>

    <archive-search-form
      ref="search"
      :defaultExpanded="true"
      :initialQuery="initialQuery"
      @search="handleSearch" />

    <div class="list-panel">
      <div class="list-panel__toolbar">
        <span class="list-panel__total">共命中 <b>{{ total }}</b> 个档案</span>
        <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
      </div>

      <archive-table
        :dataSource="dataSource"
        :loading="loading"
        :pagination="pagination"
        :readonly="true"
        @change="handleTableChange"
        @detail="handleDetail" />
    </div>

    <archive-detail-modal ref="detail" @export="handleExportOne" />
  </a-card>
</template>

<script>
  import ArchiveSearchForm from './modules/ArchiveSearchForm'
  import ArchiveTable from './modules/ArchiveTable'
  import ArchiveDetailModal from './modules/ArchiveDetailModal'
  import { queryArchiveList, exportArchiveZip } from '@/api/land/archive'

  /**
   * 档案查询（方案 2.3.2 第 3 项）
   *
   * 与「档案维护」共用同一套列表与查询条件，差别只有两点：
   *  1. 查询条件默认全部展开（这个页面就是为检索而来的）；
   *  2. 列表只读（不提供编辑/删除），但要能导出查询结果。
   */
  export default {
    name: 'ArchiveQuery',
    components: { ArchiveSearchForm, ArchiveTable, ArchiveDetailModal },
    data () {
      return {
        loading: false,
        /** 从档案统计页跳转过来时带的初始条件（如 ptxmmc / crzdbh） */
        initialQuery: {},
        query: {},
        dataSource: [],
        total: 0,
        pagination: {
          current: 1,
          pageSize: 20,
          total: 0,
          showSizeChanger: true,
          showQuickJumper: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: total => `共 ${total} 条`
        }
      }
    },
    created () {
      // 支持从「档案统计」按项目下钻：/land/archive/query?ptxmmc=xxx
      const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
      this.initialQuery = Object.assign({}, routeQuery)
      this.query = Object.assign({}, routeQuery)
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
        }).finally(() => {
          this.loading = false
        })
      },
      handleSearch (query) {
        this.query = query || {}
        this.pagination.current = 1
        this.loadData()
      },
      handleTableChange (pagination) {
        this.pagination = Object.assign({}, this.pagination, {
          current: pagination.current,
          pageSize: pagination.pageSize
        })
        this.loadData()
      },
      handleDetail (record) {
        this.$refs.detail.open(record)
      },
      handleExport () {
        exportArchiveZip(this.query)
        this.$message.success('已开始导出，请稍候…')
      },
      handleExportOne (record) {
        exportArchiveZip({
          facilityId: record.facilityId || undefined,
          crzdbh: record.facilityId ? undefined : (record.crzdbh || undefined)
        })
        this.$message.success('已开始按项目导出，请稍候…')
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;

  .archive-query-page {
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
      max-width: 860px;
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
    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
      font-size: 13px;
      color: #475569;

      b {
        color: #0f172a;
      }
    }
  }
</style>
