<template>
  <a-card :bordered="false" class="decision-opinion-page">
    <!-- ============ 页头 ============ -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">决策意见维护</h2>
        <p class="page-head__desc">
          按「预警等级 × 问题类型」维护逾期环节的处置建议。匹配顺序为
          精确组合（问题类型 + 等级）→ 该等级的通用意见 → 无意见（前端显示「—」）；
          问题类型留空的那条就是该等级的通用兜底意见。
          另外只有环节预警会给决策意见，阶段 / 总体预警没有「问题类型」，不会套用通用话术。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button v-has="'land:stat:decisionOpinion:edit'" type="primary" icon="plus" @click="handleAdd()">
          新增决策意见
        </a-button>
        <a-button icon="reload" :loading="loading" @click="loadData()">刷新</a-button>
      </div>
    </div>

    <!-- ============ 筛选区 ============ -->
    <div class="search-bar">
      <a-form layout="inline" class="search-bar__form">
        <a-form-item label="预警等级">
          <a-select
            v-model="filter.levelId"
            style="width: 190px"
            placeholder="全部"
            allowClear
            @change="handleSearch()">
            <a-select-option v-for="level in levels" :key="level.id" :value="level.id">
              {{ level.levelName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="问题类型">
          <a-select
            v-model="filter.problemType"
            style="width: 220px"
            placeholder="全部"
            allowClear
            @change="handleSearch()">
            <a-select-option :value="generalOption">通用（所有问题类型）</a-select-option>
            <a-select-option v-for="type in problemTypes" :key="type" :value="type">{{ type }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item class="search-bar__actions">
          <a-button type="primary" icon="search" @click="handleSearch()">查询</a-button>
          <a-button icon="reload" @click="handleReset()">重置</a-button>
        </a-form-item>
      </a-form>
    </div>

    <!-- ============ 列表 ============ -->
    <div class="list-panel">
      <div class="list-panel__toolbar">
        <span class="list-panel__total">共 <b>{{ total }}</b> 条决策意见</span>
        <span class="list-panel__hint">同一「问题类型 + 等级」只允许一条，重复保存会提示「已存在，请直接编辑」</span>
      </div>

      <a-table
        rowKey="id"
        :columns="columns"
        :dataSource="dataSource"
        :loading="loading"
        :pagination="pagination"
        :locale="{ emptyText: emptyText }"
        @change="handleTableChange">
        <template slot="levelName" slot-scope="text, record">
          <span class="level-name">{{ levelNameOf(record) }}</span>
        </template>
        <template slot="problemType" slot-scope="text">
          <span v-if="text" class="problem-type">{{ text }}</span>
          <a-tag v-else class="general-tag">通用（所有问题类型）</a-tag>
        </template>
        <!--
          决策意见是整页的核心内容（最长 1000 字），用 3 行截断 + 原生 title 看全文，
          比单行省略号可读，也避免长文本把整行撑到半屏高。
        -->
        <template slot="opinion" slot-scope="text">
          <div class="opinion-cell" :title="text || ''">{{ text || '—' }}</div>
        </template>
        <template slot="action" slot-scope="text, record">
          <a v-has="'land:stat:decisionOpinion:edit'" @click="handleEdit(record)">编辑</a>
          <!-- 竖线与删除按钮放在同一个 v-has 容器里：没权限时一起摘掉，否则会剩一条孤零零的「|」 -->
          <span v-has="'land:stat:decisionOpinion:edit'">
            <a-divider type="vertical" />
            <a-popconfirm
              title="删除后该组合将回落到通用意见，确定删除吗？"
              okText="确定"
              cancelText="取消"
              @confirm="handleDelete(record)">
              <a class="danger-link">删除</a>
            </a-popconfirm>
          </span>
        </template>
      </a-table>
    </div>

    <decision-opinion-modal
      ref="modal"
      :levels="levels"
      :problemTypes="problemTypes"
      @ok="loadData()" />
  </a-card>
</template>

<script>
  import { listDecisionOpinion, deleteDecisionOpinion, warningOptions } from '@/api/land/statWarning'
  import DecisionOpinionModal from './modules/DecisionOpinionModal'

  /**
   * 决策意见维护（方案 2.3.1（五）· 契约 §5.7 / §6.2）
   *
   * 数据语义：problem_type 为空串 = 该等级的「通用兜底意见」（见 sql/stat/02 的头注释）。
   * 空串是**有效筛选值**，不是「不过滤」，所以筛选下拉里用内部哨兵 __general__ 表示它：
   *   ① antd 的 a-select 把 '' 当作「未选择/显示 placeholder」，直接绑空串会让「通用」选不中；
   *   ② 「全部」与「通用」在 UI 上是两个不同选项，必须能区分。
   * 发请求前再把哨兵换回空串。
   */
  const GENERAL_OPTION = '__general__'

  export default {
    name: 'DecisionOpinionList',
    components: { DecisionOpinionModal },
    data () {
      return {
        loading: false,
        /** 删除请求进行中：防止连点重复提交 */
        deleting: false,
        /** 「通用（所有问题类型）」在界面上的哨兵值 */
        generalOption: GENERAL_OPTION,
        /** 下拉数据：等级与问题类型都来自 /land/stat/warning/options（问题类型来自字典，前端不写死） */
        levels: [],
        problemTypes: [],
        /** 当前已生效的筛选条件（问题类型用哨兵值，undefined = 全部） */
        filter: {
          levelId: undefined,
          problemType: undefined
        },
        dataSource: [],
        total: 0,
        columns: [
          { title: '预警等级', dataIndex: 'levelName', width: 170, scopedSlots: { customRender: 'levelName' } },
          { title: '问题类型', dataIndex: 'problemType', width: 210, scopedSlots: { customRender: 'problemType' } },
          { title: '决策意见', dataIndex: 'opinion', scopedSlots: { customRender: 'opinion' } },
          { title: '操作', width: 150, align: 'center', scopedSlots: { customRender: 'action' } }
        ],
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
    computed: {
      emptyText () {
        const filtering = !!this.filter.levelId ||
          (this.filter.problemType !== undefined && this.filter.problemType !== null)
        if (filtering) {
          return '当前筛选条件下没有决策意见'
        }
        return '还没有决策意见，点击右上角「新增决策意见」开始'
      }
    },
    created () {
      // 先拿选项再拉列表：表格的等级列要靠 options 兜底翻译 levelName
      this.loadOptions()
      this.loadData()
    },
    methods: {
      loadOptions () {
        return warningOptions().then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            return
          }
          const result = res.result || {}
          this.levels = result.levels || []
          this.problemTypes = result.problemTypes || []
        }).catch(() => {
          // 具体错误提示由请求拦截器统一给出
        })
      },
      loadData () {
        this.loading = true
        return listDecisionOpinion(this.buildParams()).then(res => {
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
        }).catch(() => {
          // 同上
        }).finally(() => {
          this.loading = false
        })
      },
      /** 拼请求参数：哨兵 → 空串；空条件不下发（空串 problemType 例外，它是有效值） */
      buildParams () {
        const params = {
          pageNo: this.pagination.current,
          pageSize: this.pagination.pageSize
        }
        if (this.filter.levelId) {
          params.levelId = this.filter.levelId
        }
        if (this.filter.problemType !== undefined && this.filter.problemType !== null) {
          params.problemType = this.filter.problemType === GENERAL_OPTION ? '' : this.filter.problemType
        }
        return params
      },
      handleSearch () {
        this.pagination.current = 1
        this.loadData()
      },
      handleReset () {
        this.filter = {
          levelId: undefined,
          problemType: undefined
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
      handleAdd () {
        // 把当前筛选条件带进弹窗（少点两次下拉）；问题类型若是「通用」哨兵要换回真实值语义
        this.$refs.modal.show(null, {
          levelId: this.filter.levelId,
          problemType: this.filter.problemType === GENERAL_OPTION ? '' : this.filter.problemType
        })
      },
      handleEdit (record) {
        if (!record || !record.id) {
          return
        }
        this.$refs.modal.show(record)
      },
      handleDelete (record) {
        if (this.deleting || !record || !record.id) {
          return
        }
        this.deleting = true
        deleteDecisionOpinion(record.id).then(res => {
          if (res.success) {
            this.$message.success(res.message || '删除成功')
            this.loadData()
          } else {
            this.$message.warning(res.message || '删除失败')
          }
        }).catch(() => {
          // 同上
        }).finally(() => {
          this.deleting = false
        })
      },
      /** 等级名：优先用后端的冗余列，缺了再按 levelId 回查 options，避免显示空白 */
      levelNameOf (record) {
        if (!record) {
          return '—'
        }
        if (record.levelName) {
          return record.levelName
        }
        const matched = (this.levels || []).filter(level => level.id === record.levelId)[0]
        return matched ? matched.levelName : '—'
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-strong: #0f172a;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .decision-opinion-page {
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
      color: @text-strong;
    }

    &__desc {
      margin: 0;
      max-width: 900px;
      font-size: 13px;
      line-height: 20px;
      color: @text-muted;
    }

    &__actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      flex-shrink: 0;
    }
  }

  .search-bar {
    margin-top: 16px;
    padding: 16px 16px 0;
    background: #fff;
    border: 1px solid @border-color;
    border-radius: 8px;

    &__form {
      /deep/ .ant-form-item {
        margin-bottom: 16px;
      }
    }

    &__actions {
      /deep/ .ant-btn + .ant-btn {
        margin-left: 8px;
      }
    }
  }

  .list-panel {
    margin-top: 16px;

    &__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 12px;
      font-size: 13px;
      color: @text-muted;

      b {
        color: @text-strong;
      }
    }

    &__hint {
      font-size: 12px;
      color: @text-weak;
    }
  }

  .level-name {
    font-weight: 600;
    color: @text-strong;
  }

  .general-tag {
    margin: 0;
    color: #1d4ed8;
    background: #eff6ff;
    border-color: #bfdbfe;
  }

  /* 3 行截断：整页最关键的一列，既不能单行省略到看不懂，也不能让超长文本把行撑高 */
  .opinion-cell {
    display: -webkit-box;
    overflow: hidden;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    line-height: 20px;
    word-break: break-word;
    white-space: pre-wrap;
    color: @text-muted;
  }

  .danger-link {
    color: #f5222d;

    &:hover {
      color: #ff4d4f;
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
