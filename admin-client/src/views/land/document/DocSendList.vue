<template>
  <a-card :bordered="false" class="doc-send-page">
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">发文管理</h2>
        <p class="page-head__desc">
          发文信息记录台账：登记发文登记号、标题、主送与抄送单位、签发人、拟稿人、日期与附件。
          发文没有流转环节，登记后可直接归档到档案管理（归档时必须选择档案类别）。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:docSend:add'" type="primary" icon="plus" @click="handleAdd">发文登记</a-button>
        <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
      </div>
    </div>

    <div class="stat-cards">
      <div class="stat-card">
        <span class="stat-card__label">发文总数</span>
        <span class="stat-card__value">{{ stat.total }}</span>
      </div>
      <div class="stat-card stat-card--ok">
        <span class="stat-card__label">已归档</span>
        <span class="stat-card__value">{{ stat.archived }}</span>
      </div>
      <div class="stat-card stat-card--warn">
        <span class="stat-card__label">未归档</span>
        <span class="stat-card__value">{{ Math.max(0, (stat.total || 0) - (stat.archived || 0)) }}</span>
      </div>
    </div>

    <div class="query-panel">
      <a-form layout="inline">
        <a-row :gutter="16" type="flex">
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="发文登记号">
              <a-input v-model="query.docNo" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="文件标题">
              <a-input v-model="query.docTitle" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="主送单位">
              <a-input v-model="query.toDept" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="归档情况">
              <a-select v-model="query.archived" allowClear placeholder="全部" style="width: 100%">
                <a-select-option :value="true">已归档</a-select-option>
                <a-select-option :value="false">未归档</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>

          <template v-if="expanded">
            <a-col :xs="24" :sm="12" :md="8" :lg="6">
              <a-form-item label="配套项目">
                <a-input v-model="query.ptxmmc" placeholder="项目名称，模糊匹配" allowClear @pressEnter="handleSearch" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="8" :lg="6">
              <a-form-item label="出让宗地编号">
                <a-input v-model="query.crzdbh" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="8" :lg="6">
              <a-form-item label="发文日期">
                <a-range-picker
                  v-model="dateRange"
                  style="width: 100%"
                  valueFormat="YYYY-MM-DD"
                  :placeholder="['开始', '结束']"
                  @change="handleDateChange" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="8" :lg="6">
              <a-form-item label="密级">
                <a-select v-model="query.secretLevel" allowClear placeholder="全部" style="width: 100%">
                  <a-select-option v-for="item in secretLevels" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-item>
            </a-col>
          </template>

          <a-col :xs="24" class="query-panel__actions">
            <a-button type="primary" icon="search" @click="handleSearch">查询</a-button>
            <a-button icon="reload" @click="handleReset">重置</a-button>
            <a-button type="link" @click="expanded = !expanded">
              {{ expanded ? '收起' : '更多条件' }}
              <a-icon :type="expanded ? 'up' : 'down'" />
            </a-button>
          </a-col>
        </a-row>
      </a-form>
    </div>

    <div class="list-panel">
      <div class="list-panel__toolbar">
        <span class="list-panel__total">共 <b>{{ total }}</b> 条发文</span>
        <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
      </div>

      <a-table
        rowKey="id"
        :columns="columns"
        :dataSource="dataSource"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1500 }"
        size="middle"
        @change="handleTableChange">
        <template slot="docNo" slot-scope="text, record">
          <a class="doc-table__no" @click="handleDetail(record)">{{ text }}</a>
        </template>
        <template slot="docTitle" slot-scope="text">
          <div class="doc-table__title" :title="text">{{ text }}</div>
        </template>
        <template slot="attachmentCount" slot-scope="text">
          <a-tag v-if="text" color="blue">{{ text }}</a-tag>
          <span v-else class="doc-table__muted">—</span>
        </template>
        <template slot="archiveFlag" slot-scope="text">
          <a-tag v-if="text" color="green">已归档</a-tag>
          <span v-else class="doc-table__muted">未归档</span>
        </template>
        <template slot="action" slot-scope="text, record">
          <a v-has="'land:docSend:list'" @click="handleDetail(record)">详情</a>
          <span v-has="'land:docSend:edit'">
            <a-divider type="vertical" />
            <a @click="handleEdit(record)">编辑</a>
          </span>
          <span v-if="!record.archiveId" v-has="'land:docSend:archive'">
            <a-divider type="vertical" />
            <a @click="handleArchive(record)">归档</a>
          </span>
          <span v-has="'land:docSend:delete'">
            <a-divider type="vertical" />
            <a-popconfirm title="删除后不可恢复，确定删除该发文吗？" okText="确定" cancelText="取消" @confirm="handleDelete(record)">
              <a class="doc-table__danger">删除</a>
            </a-popconfirm>
          </span>
        </template>
      </a-table>
    </div>

    <doc-send-modal ref="modal" @ok="onChanged" />
    <doc-archive-modal ref="archive" @ok="onChanged" />
    <doc-detail-modal ref="detail" />
  </a-card>
</template>

<script>
  import DocSendModal from './modules/DocSendModal'
  import DocArchiveModal from './modules/DocArchiveModal'
  import DocDetailModal from './modules/DocDetailModal'
  import { queryDocSendList, deleteDocSend, queryDocSendStat, queryDocSendById } from '@/api/land/document'

  /**
   * 发文管理（方案 2.3.2 第 9 项：发文的信息记录）
   *
   * 发文是纯台账：登记 / 编辑 / 附件 / 删除 / 可选归档，没有流转。
   * 归档同样<b>必须选择档案类别</b>（与收文一致，需求方明确要求）。
   */
  export default {
    name: 'DocSendList',
    components: { DocSendModal, DocArchiveModal, DocDetailModal },
    data () {
      return {
        loading: false,
        expanded: false,
        dateRange: [],
        query: this.buildEmptyQuery(),
        stat: { total: 0, archived: 0 },
        dataSource: [],
        total: 0,
        secretLevels: ['一般', '内部', '秘密', '机密'],
        columns: [
          { title: '发文登记号', dataIndex: 'docNo', width: 150, fixed: 'left', scopedSlots: { customRender: 'docNo' } },
          { title: '文件标题', dataIndex: 'docTitle', width: 260, ellipsis: true, scopedSlots: { customRender: 'docTitle' } },
          { title: '主送单位', dataIndex: 'toDept', width: 200, ellipsis: true, customRender: text => text || '—' },
          { title: '抄送单位', dataIndex: 'ccDept', width: 160, ellipsis: true, customRender: text => text || '—' },
          { title: '发文日期', dataIndex: 'issueDate', width: 110, customRender: text => text || '—' },
          { title: '配套项目', dataIndex: 'ptxmmc', width: 200, ellipsis: true, customRender: text => text || '—' },
          { title: '签发人', dataIndex: 'signer', width: 100, customRender: text => text || '—' },
          { title: '附件', dataIndex: 'attachmentCount', width: 70, align: 'center', scopedSlots: { customRender: 'attachmentCount' } },
          { title: '归档', dataIndex: 'archiveId', width: 80, scopedSlots: { customRender: 'archiveFlag' } },
          { title: '操作', width: 210, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ],
        pagination: {
          current: 1,
          pageSize: 10,
          total: 0,
          showSizeChanger: true,
          showQuickJumper: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: total => `共 ${total} 条`
        }
      }
    },
    created () {
      this.loadData()
      this.loadStat()
    },
    methods: {
      buildEmptyQuery () {
        return {
          docNo: '',
          docTitle: '',
          toDept: '',
          ptxmmc: '',
          crzdbh: '',
          secretLevel: undefined,
          archived: undefined,
          beginDate: undefined,
          endDate: undefined
        }
      },
      loadData () {
        this.loading = true
        const params = Object.assign({}, this.query, {
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        })
        return queryDocSendList(params).then(res => {
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
      loadStat () {
        return queryDocSendStat().then(res => {
          if (res.success && res.result) {
            this.stat = Object.assign({}, this.stat, res.result)
          }
        })
      },
      handleSearch () {
        this.pagination.current = 1
        this.loadData()
      },
      handleReset () {
        this.query = this.buildEmptyQuery()
        this.dateRange = []
        this.pagination.current = 1
        this.loadData()
      },
      handleDateChange (values) {
        if (values && values.length === 2) {
          this.query.beginDate = values[0]
          this.query.endDate = values[1]
        } else {
          this.query.beginDate = undefined
          this.query.endDate = undefined
        }
      },
      handleTableChange (pagination) {
        this.pagination = Object.assign({}, this.pagination, {
          current: pagination.current,
          pageSize: pagination.pageSize
        })
        this.loadData()
      },
      onChanged () {
        this.loadData()
        this.loadStat()
      },
      handleAdd () {
        this.$refs.modal.showAdd()
      },
      handleEdit (record) {
        this.$refs.modal.showEdit(record)
      },
      handleArchive (record) {
        this.$refs.archive.open(record, 'send')
      },
      handleDetail (record) {
        queryDocSendById(record.id).then(res => {
          if (res.success) {
            this.$refs.detail.open(res.result, 'send')
          } else {
            this.$message.warning(res.message)
          }
        })
      },
      handleDelete (record) {
        deleteDocSend(record.id).then(res => {
          if (res.success) {
            this.$message.success(res.message || '删除成功')
            this.onChanged()
          } else {
            this.$message.warning(res.message)
          }
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .doc-send-page {
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
      max-width: 880px;
      font-size: 13px;
      line-height: 20px;
      color: @text-muted;
    }

    &__actions {
      display: flex;
      gap: 8px;
      flex-shrink: 0;
    }
  }

  .stat-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 12px;
    margin: 16px 0;
  }

  .stat-card {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px 14px;
    background: #fff;
    border: 1px solid @border-color;
    border-left: 3px solid #2e7cf6;
    border-radius: 8px;

    &__label {
      font-size: 12px;
      color: @text-muted;
    }

    &__value {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 24px;
      line-height: 28px;
      letter-spacing: 1px;
      color: #1d4ed8;
    }

    &--ok {
      border-left-color: #10b981;

      .stat-card__value {
        color: #0f766e;
      }
    }

    &--warn {
      border-left-color: #f59e0b;

      .stat-card__value {
        color: #b45309;
      }
    }
  }

  .query-panel {
    padding: 16px 16px 0;
    margin-bottom: 16px;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;

    /deep/ .ant-form-item {
      display: flex;
      margin-bottom: 16px;
    }

    /deep/ .ant-form-item-label {
      flex: none;
      width: 92px;
      text-align: right;
    }

    /deep/ .ant-form-item-control-wrapper {
      flex: 1 1 auto;
      min-width: 0;
    }

    &__actions {
      text-align: right;
      margin-bottom: 16px;

      /deep/ .ant-btn {
        margin-left: 8px;
      }
    }
  }

  .list-panel {
    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
      font-size: 13px;
      color: @text-muted;

      b {
        color: #0f172a;
      }
    }
  }

  .doc-table {
    &__no {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      letter-spacing: 0.5px;
    }

    &__title {
      font-weight: 500;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__muted {
      color: @text-weak;
      font-size: 12px;
    }

    &__danger {
      color: #ff4d4f;
    }
  }
</style>
