<template>
  <a-card :bordered="false" class="escalation-entry-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">提级论证项目录入</h2>
        <p class="page-head__desc">
          标准化录入提级论证项目的基本信息、关联地块信息（选填）、论证信息与论证结果，并上传论证材料。
          项目编号默认自动生成（TJ-{年份}-{4位流水}），允许按历史编号手工改写；本系统只做台账与记录，
          实际论证流程在线下办理。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:escalation:add'" type="primary" icon="plus" @click="handleAdd">新增项目</a-button>
        <a-button v-has="'land:escalation:export'" icon="download" :disabled="!total" @click="handleExport">导出 Excel</a-button>
        <a-button icon="bar-chart" @click="goQuery">查询统计</a-button>
      </div>
    </div>

    <!-- 查询条件 -->
    <escalation-search-form ref="search" @search="handleSearch" />

    <!-- 列表 -->
    <div class="list-panel">
      <div class="list-panel__toolbar">
        <div class="list-panel__toolbar-left">
          <span class="list-panel__total">共 <b>{{ total }}</b> 个项目</span>
          <span v-if="selectedRowKeys.length" class="list-panel__selected">
            已选 <b>{{ selectedRowKeys.length }}</b> 项
          </span>
        </div>
        <div class="list-panel__toolbar-right">
          <a-popconfirm
            v-if="selectedRowKeys.length"
            v-has="'land:escalation:delete'"
            title="删除后不可恢复，确定批量删除选中的项目吗？"
            okText="确定"
            cancelText="取消"
            @confirm="handleBatchDelete">
            <a-button type="danger" icon="delete" :loading="deleting">批量删除</a-button>
          </a-popconfirm>
          <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
        </div>
      </div>

      <escalation-table
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

    <escalation-form-modal ref="modal" @ok="loadData" />
    <escalation-detail-modal ref="detail" @edit="handleEdit" />
  </a-card>
</template>

<script>
  import EscalationSearchForm from './modules/EscalationSearchForm'
  import EscalationTable from './modules/EscalationTable'
  import EscalationFormModal from './modules/EscalationFormModal'
  import EscalationDetailModal from './modules/EscalationDetailModal'
  import {
    deleteProject,
    deleteProjectBatch,
    exportProjectXls,
    loadAllDicts,
    queryProjectList
  } from '@/api/land/escalation'

  /**
   * 提级论证项目录入（方案 2.3.3 第 1 项）
   *
   * 页面结构照 ArchiveList.vue：page-head（标题 + 说明 + 主操作）→ 查询条件 → list-panel（工具条 + 表格 + 分页）。
   * 新增/编辑走三步向导（EscalationFormModal），详情走只读弹窗（EscalationDetailModal）。
   */
  export default {
    name: 'EscalationEntryList',
    components: { EscalationSearchForm, EscalationTable, EscalationFormModal, EscalationDetailModal },
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
      // 字典（项目类型 / 论证结果 / 材料类型 / yn）预热一次，表格列与下拉都靠它翻译文本
      loadAllDicts()
      this.loadData()
    },
    methods: {
      loadData () {
        this.loading = true
        const params = Object.assign({}, this.query, {
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        })
        return queryProjectList(params).then(res => {
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
        // 详情弹窗里点「编辑」时，先关掉详情再打开向导
        if (this.$refs.detail) {
          this.$refs.detail.close()
        }
        this.$refs.modal.showEdit(record)
      },
      handleDetail (record) {
        this.$refs.detail.open(record)
      },
      handleDelete (record) {
        deleteProject(record.id).then(res => {
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
        deleteProjectBatch(this.selectedRowKeys).then(res => {
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
      /** 按当前查询条件导出项目 Excel */
      handleExport () {
        const query = this.$refs.search ? this.$refs.search.getQuery() : {}
        exportProjectXls(query)
        this.$message.success('已开始导出，请稍候…')
      },
      goQuery () {
        this.$router.push({ path: '/land/escalation/query' })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;

  .escalation-entry-page {
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
