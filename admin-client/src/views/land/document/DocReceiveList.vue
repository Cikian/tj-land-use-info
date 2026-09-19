<template>
  <a-card :bordered="false" class="doc-receive-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">收文管理</h2>
        <p class="page-head__desc">
          收文登记与收文中心内的流转：登记（可指定承办人）→ 承办 → 办结，支持在流转过程中转办与退回，
          每步都会留下「谁、何时、什么意见」的痕迹。办结时可选择是否归档到档案管理。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:docReceive:add'" type="primary" icon="plus" @click="handleAdd">收文登记</a-button>
        <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
      </div>
    </div>

    <!-- 统计卡 -->
    <div class="stat-cards">
      <button
        v-for="card in statCards"
        :key="card.key"
        type="button"
        class="stat-card"
        :class="[{ 'stat-card--active': activeStat === card.key }, 'stat-card--' + card.tone]"
        @click="handleStatClick(card.key)">
        <span class="stat-card__label">{{ card.label }}</span>
        <span class="stat-card__value">{{ card.value }}</span>
      </button>
    </div>

    <!-- 查询条件 -->
    <div class="query-panel">
      <a-form layout="inline">
        <a-row :gutter="16" type="flex">
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="收文登记号">
              <a-input v-model="query.docNo" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="文件标题">
              <a-input v-model="query.docTitle" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="来文单位">
              <a-input v-model="query.fromDept" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8" :lg="6">
            <a-form-item label="状态">
              <a-select v-model="query.status" allowClear placeholder="全部" style="width: 100%">
                <a-select-option v-for="item in statuses" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>

          <template v-if="expanded">
            <a-col :xs="24" :sm="12" :md="8" :lg="6">
              <a-form-item label="来文字号">
                <a-input v-model="query.fromDocNo" placeholder="模糊匹配" allowClear @pressEnter="handleSearch" />
              </a-form-item>
            </a-col>
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
              <a-form-item label="收文日期">
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
            <a-col :xs="24" :sm="12" :md="8" :lg="6">
              <a-form-item label="紧急程度">
                <a-select v-model="query.urgency" allowClear placeholder="全部" style="width: 100%">
                  <a-select-option v-for="item in urgencies" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
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

    <!-- 列表 -->
    <div class="list-panel">
      <div class="list-panel__toolbar">
        <span class="list-panel__total">共 <b>{{ total }}</b> 条收文</span>
        <a-button icon="reload" :loading="loading" @click="loadData">刷新</a-button>
      </div>

      <a-table
        rowKey="id"
        :columns="columns"
        :dataSource="dataSource"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1600 }"
        size="middle"
        @change="handleTableChange">
        <template slot="docNo" slot-scope="text, record">
          <a class="doc-table__no" @click="handleDetail(record)">{{ text }}</a>
        </template>
        <template slot="docTitle" slot-scope="text">
          <div class="doc-table__title" :title="text">{{ text }}</div>
        </template>
        <template slot="status" slot-scope="text">
          <a-tag :color="statusColor(text)">{{ text }}</a-tag>
        </template>
        <template slot="handler" slot-scope="text">
          <span v-if="text">{{ text }}</span>
          <span v-else class="doc-table__muted">待分办</span>
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
          <a v-has="'land:docReceive:list'" @click="handleDetail(record)">详情</a>
          <span v-has="'land:docReceive:list'">
            <a-divider type="vertical" />
            <a @click="handleFlow(record)">{{ isClosed(record) ? '流转痕迹' : '流转' }}</a>
          </span>
          <span v-has="'land:docReceive:edit'">
            <a-divider type="vertical" />
            <a @click="handleEdit(record)">编辑</a>
          </span>
          <span v-if="record.status === '已办结' && !record.archiveId" v-has="'land:docReceive:archive'">
            <a-divider type="vertical" />
            <a @click="handleArchive(record)">归档</a>
          </span>
          <span v-has="'land:docReceive:delete'">
            <a-divider type="vertical" />
            <a-popconfirm title="删除后不可恢复，确定删除该收文吗？" okText="确定" cancelText="取消" @confirm="handleDelete(record)">
              <a class="doc-table__danger">删除</a>
            </a-popconfirm>
          </span>
        </template>
      </a-table>
    </div>

    <doc-receive-modal ref="modal" @ok="onChanged" />
    <doc-flow-modal ref="flow" @finished="handleFinished" />
    <doc-archive-modal ref="archive" @ok="onChanged" />
    <doc-detail-modal ref="detail" @open-flow="handleFlow" />
  </a-card>
</template>

<script>
  import DocReceiveModal from './modules/DocReceiveModal'
  import DocFlowModal from './modules/DocFlowModal'
  import DocArchiveModal from './modules/DocArchiveModal'
  import DocDetailModal from './modules/DocDetailModal'
  import {
    queryDocReceiveList, deleteDocReceive, queryDocReceiveStat, queryDocReceiveById
  } from '@/api/land/document'

  /**
   * 收文管理（方案 2.3.2 第 9 项）
   *
   * 流转模型（精简三级 + 退回）：
   *   登记 ─► 待承办 ─转办/分办─► 承办中 ─办结─► 已办结 ─归档─► 已归档
   *                     ▲              │
   *                     └─── 退回 ─────┘
   *
   * ★ 需求约定：办结时先<b>提示是否归档</b>，用户选择归档后必须选择档案类别。
   *   这里的实现是：流转弹窗办结成功 → 弹 Modal.confirm「是否立即归档」→
   *   选择「立即归档」就打开归档弹窗（档案类别必填）。
   */
  export default {
    name: 'DocReceiveList',
    components: { DocReceiveModal, DocFlowModal, DocArchiveModal, DocDetailModal },
    data () {
      return {
        loading: false,
        expanded: false,
        dateRange: [],
        query: this.buildEmptyQuery(),
        activeStat: '',
        stat: {
          total: 0, pending: 0, handling: 0, rejected: 0, finished: 0, archived: 0, myTodo: 0
        },
        dataSource: [],
        total: 0,
        statuses: ['待承办', '承办中', '已退回', '已办结', '已归档'],
        secretLevels: ['一般', '内部', '秘密', '机密'],
        urgencies: ['普通', '急件', '特急'],
        columns: [
          { title: '收文登记号', dataIndex: 'docNo', width: 150, fixed: 'left', scopedSlots: { customRender: 'docNo' } },
          { title: '文件标题', dataIndex: 'docTitle', width: 260, ellipsis: true, scopedSlots: { customRender: 'docTitle' } },
          { title: '来文单位', dataIndex: 'fromDept', width: 180, ellipsis: true, customRender: text => text || '—' },
          { title: '来文字号', dataIndex: 'fromDocNo', width: 150, ellipsis: true, customRender: text => text || '—' },
          { title: '收文日期', dataIndex: 'receiveDate', width: 110, customRender: text => text || '—' },
          { title: '配套项目', dataIndex: 'ptxmmc', width: 200, ellipsis: true, customRender: text => text || '—' },
          { title: '当前处理人', dataIndex: 'currentHandlerName', width: 110, scopedSlots: { customRender: 'handler' } },
          { title: '状态', dataIndex: 'status', width: 90, scopedSlots: { customRender: 'status' } },
          { title: '附件', dataIndex: 'attachmentCount', width: 70, align: 'center', scopedSlots: { customRender: 'attachmentCount' } },
          { title: '归档', dataIndex: 'archiveId', width: 80, scopedSlots: { customRender: 'archiveFlag' } },
          { title: '操作', width: 250, fixed: 'right', scopedSlots: { customRender: 'action' } }
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
    computed: {
      statCards () {
        return [
          { key: '', label: '收文总数', value: this.stat.total, tone: 'default' },
          { key: 'mine', label: '我的待办', value: this.stat.myTodo, tone: 'primary' },
          { key: '待承办', label: '待承办', value: this.stat.pending, tone: 'warn' },
          { key: '承办中', label: '承办中', value: this.stat.handling, tone: 'info' },
          { key: '已退回', label: '已退回', value: this.stat.rejected, tone: 'danger' },
          { key: '已办结', label: '已办结', value: this.stat.finished, tone: 'ok' }
        ]
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
          fromDept: '',
          fromDocNo: '',
          ptxmmc: '',
          crzdbh: '',
          status: undefined,
          secretLevel: undefined,
          urgency: undefined,
          archived: undefined,
          beginDate: undefined,
          endDate: undefined,
          onlyMine: undefined
        }
      },
      loadData () {
        this.loading = true
        const params = Object.assign({}, this.query, {
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        })
        // 只查「我的待办」时不需要再叠加状态条件，否则会自相矛盾
        if (params.onlyMine) {
          delete params.status
        }
        return queryDocReceiveList(params).then(res => {
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
        return queryDocReceiveStat().then(res => {
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
        this.activeStat = ''
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
      /** 点统计卡做快捷筛选 */
      handleStatClick (key) {
        if (key === 'mine') {
          this.activeStat = this.activeStat === 'mine' ? '' : 'mine'
          this.query.onlyMine = this.activeStat === 'mine' ? true : undefined
        } else {
          this.activeStat = this.activeStat === key ? '' : key
          this.query.status = this.activeStat || undefined
        }
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
      handleFlow (record) {
        this.$refs.flow.open(record)
      },
      handleArchive (record) {
        this.$refs.archive.open(record, 'receive')
      },
      handleDetail (record) {
        queryDocReceiveById(record.id).then(res => {
          if (res.success) {
            this.$refs.detail.open(res.result, 'receive')
          } else {
            this.$message.warning(res.message)
          }
        })
      },
      /**
       * 办结成功 → 先提示是否归档（需求：给出提示，让用户选择是否归档）。
       * 选择归档后打开归档弹窗，其中档案类别是必填项。
       */
      handleFinished (doc) {
        this.onChanged()
        this.$confirm({
          title: '收文已办结',
          content: `「${doc.docNo} ${doc.docTitle}」已办结，是否立即归档到档案管理？归档时可以指定档案类别。`,
          okText: '立即归档',
          cancelText: '暂不归档',
          onOk: () => {
            this.handleArchive(doc)
          },
          onCancel: () => {
            this.$message.info('已办结。可稍后在列表点「归档」补办归档。')
          }
        })
      },
      handleDelete (record) {
        deleteDocReceive(record.id).then(res => {
          if (res.success) {
            this.$message.success(res.message || '删除成功')
            this.onChanged()
          } else {
            this.$message.warning(res.message)
          }
        })
      },
      isClosed (record) {
        return record.status === '已办结' || record.status === '已归档'
      },
      statusColor (status) {
        if (status === '已办结' || status === '已归档') {
          return 'green'
        }
        if (status === '已退回') {
          return 'red'
        }
        if (status === '承办中') {
          return 'cyan'
        }
        return 'orange'
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .doc-receive-page {
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

  /* ---------------- 统计卡（可点击做快捷筛选） ---------------- */
  .stat-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    gap: 12px;
    margin: 16px 0;
  }

  .stat-card {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    padding: 12px 14px;
    text-align: left;
    background: #fff;
    border: 1px solid @border-color;
    border-left: 3px solid #cbd5e1;
    border-radius: 8px;
    cursor: pointer;
    transition: box-shadow 0.2s ease, border-color 0.2s ease;

    &:hover {
      border-color: #93c5fd;
      box-shadow: 0 2px 8px rgba(46, 124, 246, 0.12);
    }

    &__label {
      font-size: 12px;
      color: @text-muted;
    }

    &__value {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 24px;
      line-height: 28px;
      letter-spacing: 1px;
      color: #0f172a;
    }

    &--primary {
      border-left-color: #2e7cf6;

      .stat-card__value {
        color: #1d4ed8;
      }
    }

    &--warn {
      border-left-color: #f59e0b;

      .stat-card__value {
        color: #b45309;
      }
    }

    &--info {
      border-left-color: #22d3ee;

      .stat-card__value {
        color: #0e7490;
      }
    }

    &--danger {
      border-left-color: #ef4444;

      .stat-card__value {
        color: #b91c1c;
      }
    }

    &--ok {
      border-left-color: #10b981;

      .stat-card__value {
        color: #0f766e;
      }
    }

    &--active {
      border-color: #2e7cf6;
      box-shadow: 0 0 0 2px rgba(46, 124, 246, 0.16);
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
