<template>
  <a-card :bordered="false" class="data-recycle">
    <!-- ============ 页头 ============ -->
    <div class="page-head">
      <div class="page-head__text">
        <h2 class="page-head__title">数据更新与移除</h2>
        <p class="page-head__desc">
          方案 2.3.1（三）第 6 项。<b>回收站</b>里是被移除（软删）的宗地与配套项目，可直接恢复；
          <b>变更留痕</b>记录每一条数据的字段级改动（改了哪个字段、改前改后是什么、谁在什么时候改的）。
          移除不做物理删除 —— 宗地编号被配套、档案、收发文多处引用，物理删会留下孤儿数据。
        </p>
      </div>
      <div class="page-head__actions">
        <a-button icon="reload" :loading="loading" @click="refresh">刷新</a-button>
      </div>
    </div>

    <!-- ============ 顶部计数 ============ -->
    <div class="recycle-stats">
      <a-row :gutter="12">
        <a-col v-for="card in statCards" :key="card.key" :xs="12" :sm="8" :md="6">
          <div class="stat-card" :class="card.cls" @click="handleStatClick(card.key)">
            <div class="stat-card__label">{{ card.label }}</div>
            <div class="stat-card__value">{{ card.value }}</div>
            <div class="stat-card__extra">{{ card.extra }}</div>
          </div>
        </a-col>
      </a-row>
    </div>

    <!-- ============ 两个页签 ============ -->
    <a-tabs v-model="activeTab" class="recycle-tabs">
      <!-- ---------- 回收站 ---------- -->
      <a-tab-pane key="recycle">
        <template slot="tab">
          <span>回收站</span>
          <a-badge v-if="recycleTotal" :count="recycleTotal" :overflow-count="999" class="tab-badge" />
        </template>

        <div class="filter-bar">
          <a-select v-model="recycleQuery.bizType" style="width: 150px" :options="bizTypeOptions" />
          <a-input
            v-model="recycleQuery.keyword"
            placeholder="按宗地编号 / 名称搜索"
            allow-clear
            style="width: 260px"
            @pressEnter="loadRecycle" />
          <a-button type="primary" icon="search" @click="loadRecycle">查询</a-button>
          <a-button icon="reload" @click="resetRecycle">重置</a-button>
          <span class="filter-bar__hint">
            ★ 恢复宗地时会检测「同编号是否已被另一条有效记录占用」——
            冲突会被拦下并说明要先处理哪条记录，而不是抛出数据库错误
          </span>
        </div>

        <a-table
          rowKey="id"
          size="small"
          :columns="recycleColumns"
          :dataSource="recycleRows"
          :loading="loading"
          :pagination="{ pageSize: 10, size: 'small', showTotal: total => `共 ${total} 条` }">
          <template slot="bizType" slot-scope="text">
            <a-tag :color="bizTypeColor(text)">{{ bizTypeText(text) }}</a-tag>
          </template>
          <template slot="name" slot-scope="text, record">
            <div class="cell-main">{{ record.bizKey || '—' }}</div>
            <div v-if="record.name && record.name !== record.bizKey" class="cell-sub">{{ record.name }}</div>
          </template>
          <template slot="operator" slot-scope="text, record">
            {{ record.lrr || record.lrdw || '—' }}
          </template>
          <template slot="action" slot-scope="text, record">
            <a-button size="small" type="link" icon="rollback" @click="handleRestore(record)">恢复</a-button>
          </template>
        </a-table>
      </a-tab-pane>

      <!-- ---------- 变更留痕 ---------- -->
      <a-tab-pane key="changeLog">
        <template slot="tab">
          <span>变更留痕</span>
        </template>

        <div class="filter-bar">
          <a-select v-model="logQuery.bizType" style="width: 140px" :options="bizTypeOptions" placeholder="业务类型" />
          <a-select v-model="logQuery.action" style="width: 140px" :options="actionOptions" placeholder="动作" allow-clear />
          <a-input v-model="logQuery.operator" placeholder="操作人账号" allow-clear style="width: 160px" />
          <a-input
            v-model="logQuery.keyword"
            placeholder="按编号 / 名称 / 摘要搜索"
            allow-clear
            style="width: 240px"
            @pressEnter="searchLog" />
          <a-button type="primary" icon="search" @click="searchLog">查询</a-button>
          <a-button icon="reload" @click="resetLog">重置</a-button>
        </div>

        <a-table
          rowKey="id"
          size="small"
          :columns="logColumns"
          :dataSource="logRows"
          :loading="logLoading"
          :pagination="logPagination"
          @change="handleLogTableChange">
          <template slot="action" slot-scope="text">
            <a-tag :color="actionColor(text)">{{ actionText(text) }}</a-tag>
          </template>
          <template slot="biz" slot-scope="text, record">
            <div class="cell-main">{{ record.bizKey || '—' }}</div>
            <div class="cell-sub">{{ bizTypeText(record.bizType) }}</div>
          </template>
          <template slot="summary" slot-scope="text">
            <span class="cell-summary">{{ text || '—' }}</span>
          </template>
          <template slot="changeCount" slot-scope="text">
            <a-tag v-if="text" color="blue">{{ text }} 项</a-tag>
            <span v-else>—</span>
          </template>
          <template slot="operator" slot-scope="text, record">
            {{ record.operatorName || record.operator || '—' }}
          </template>
          <template slot="detail" slot-scope="text, record">
            <a-button size="small" type="link" icon="eye" @click="showDetail(record)">明细</a-button>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- ============ 字段级明细弹窗 ============ -->
    <a-modal
      title="变更明细"
      :width="760"
      :visible="detailVisible"
      :footer="null"
      @cancel="detailVisible = false">
      <div v-if="currentLog" class="detail-head">
        <a-tag :color="actionColor(currentLog.action)">{{ actionText(currentLog.action) }}</a-tag>
        <span class="detail-head__key">{{ currentLog.bizKey }}</span>
        <span class="detail-head__meta">
          {{ currentLog.operatorName || currentLog.operator || '未知' }} ·
          {{ currentLog.createTime }}
        </span>
      </div>
      <a-alert
        v-if="currentLog && (!currentLog.details || !currentLog.details.length)"
        type="info"
        showIcon
        message="本次操作没有字段级变化（例如移除与恢复这类只改状态的动作用于留痕）" />
      <a-table
        v-else
        rowKey="field"
        size="small"
        :columns="detailColumns"
        :dataSource="currentLog ? currentLog.details : []"
        :pagination="false"
        :scroll="{ y: 320 }">
        <template slot="before" slot-scope="text">
          <span class="detail-before">{{ text || '（空）' }}</span>
        </template>
        <template slot="after" slot-scope="text">
          <span class="detail-after">{{ text || '（空）' }}</span>
        </template>
      </a-table>
    </a-modal>
  </a-card>
</template>

<script>
  import {
    queryRecycleList,
    queryRecycleSummary,
    restoreData,
    queryChangeLogPage,
    queryActionOptions,
    actionColor,
    actionText,
    bizTypeText,
    bizTypeColor,
    BIZ_TYPE_OPTIONS,
    ACTION_OPTIONS_FALLBACK
  } from '@/api/land/dataRecycle'

  /**
   * 数据更新与移除（方案 2.3.1（三）第 6 项）
   *
   * 两个页签：回收站 + 变更留痕。
   *
   * ★ 交互上的三个刻意设计：
   *  1. 恢复失败时**原样展示后端的 message**，不替换成「恢复失败」。
   *     因为后端的提示里带着「被哪条记录占用」这个关键信息 ——
   *     盖掉它，用户就只看到一个红色的「失败」而不知道下一步该干什么。
   *  2. 恢复返回 `warning` 时用 info 提示而不是 error：
   *     配套项目同名恢复是**成功**的，只是需要用户确认一遍。
   *     用 error 弹会把成功说成失败。
   *  3. 明细弹窗对「没有字段级变化」的操作（移除/恢复）明确说明原因，
   *     而不是显示一个空表格 —— 空表格看起来像加载失败。
   */
  export default {
    name: 'DataRecycle',
    data () {
      return {
        activeTab: 'recycle',
        loading: false,
        // ---- 回收站 ----
        recycleQuery: { bizType: '', keyword: '' },
        recycleRows: [],
        recycleTotal: 0,
        summary: { landDeleted: 0, facilityDeleted: 0, total: 0 },
        bizTypeOptions: BIZ_TYPE_OPTIONS,
        recycleColumns: [
          { title: '类型', dataIndex: 'bizType', width: 110, scopedSlots: { customRender: 'bizType' } },
          { title: '编号 / 名称', dataIndex: 'bizKey', width: 240, scopedSlots: { customRender: 'name' } },
          { title: '行政区划', dataIndex: 'xzqh', width: 100, customRender: text => text || '—' },
          { title: '项目分类', dataIndex: 'xmfl', width: 100, customRender: text => text || '—' },
          { title: '原录入人', dataIndex: 'lrr', width: 120, scopedSlots: { customRender: 'operator' } },
          { title: '移除时间', dataIndex: 'updateTime', width: 170, customRender: text => text || '—' },
          { title: '操作', dataIndex: 'action', width: 100, fixed: 'right', scopedSlots: { customRender: 'action' } }
        ],
        // ---- 变更留痕 ----
        logLoading: false,
        logQuery: { bizType: '', action: '', operator: '', keyword: '' },
        logRows: [],
        logTotal: 0,
        logActionOptions: ACTION_OPTIONS_FALLBACK,
        logPagination: {
          current: 1,
          pageSize: 10,
          total: 0,
          size: 'small',
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: total => `共 ${total} 条变更记录`
        },
        logColumns: [
          { title: '操作时间', dataIndex: 'createTime', width: 170 },
          { title: '动作', dataIndex: 'action', width: 100, scopedSlots: { customRender: 'action' } },
          { title: '业务对象', dataIndex: 'bizKey', width: 220, scopedSlots: { customRender: 'biz' } },
          { title: '摘要', dataIndex: 'changeSummary', scopedSlots: { customRender: 'summary' } },
          { title: '变更字段', dataIndex: 'changeCount', width: 100, scopedSlots: { customRender: 'changeCount' } },
          { title: '操作人', dataIndex: 'operator', width: 120, scopedSlots: { customRender: 'operator' } },
          { title: '操作', dataIndex: 'detail', width: 90, fixed: 'right', scopedSlots: { customRender: 'detail' } }
        ],
        detailColumns: [
          { title: '字段', dataIndex: 'label', width: 200, customRender: (text, row) => text || row.field },
          { title: '改前', dataIndex: 'before', width: 220, scopedSlots: { customRender: 'before' } },
          { title: '改后', dataIndex: 'after', scopedSlots: { customRender: 'after' } }
        ],
        // ---- 明细弹窗 ----
        detailVisible: false,
        currentLog: null
      }
    },
    computed: {
      actionOptions () {
        return this.logActionOptions
      },
      /**
       * 指标卡：只返回「要显示什么」。
       *
       * ★ 点击行为放在 methods 里，而不是写进 computed 的 onClick 闭包 ——
       *   在 computed 里赋值会被 `vue/no-side-effects-in-computed-properties`
       *   判为错误，而且 computed 一旦因任何依赖变化而重算，
       *   里面的闭包就会重新创建，行为不稳定。
       */
      statCards () {
        return [
          {
            key: 'land',
            label: '回收站 · 经营性用地',
            value: this.summary.landDeleted || 0,
            extra: '点此只看宗地',
            cls: 'is-land'
          },
          {
            key: 'facility',
            label: '回收站 · 配套项目',
            value: this.summary.facilityDeleted || 0,
            extra: '点此只看配套',
            cls: 'is-facility'
          },
          {
            key: 'total',
            label: '回收站合计',
            value: this.summary.total || 0,
            extra: '全部可恢复；移除不做物理删除',
            cls: ''
          },
          {
            key: 'log',
            label: '变更留痕条数',
            value: this.logTotal || 0,
            extra: '当前筛选条件下的记录数',
            cls: ''
          }
        ]
      }
    },
    mounted () {
      this.loadSummary()
      this.loadRecycle()
      this.loadActionOptions()
      this.loadLog()
    },
    methods: {
      actionColor: actionColor,
      actionText: actionText,
      bizTypeText: bizTypeText,
      bizTypeColor: bizTypeColor,

      refresh () {
        this.loadSummary()
        this.loadRecycle()
        this.loadLog()
      },

      /** 指标卡点击：切到对应页签并按业务类型过滤 */
      handleStatClick (key) {
        if (key === 'log') {
          this.activeTab = 'changeLog'
          return
        }
        this.activeTab = 'recycle'
        this.recycleQuery.bizType = key === 'land' ? 'land' : (key === 'facility' ? 'facility' : '')
        this.loadRecycle()
      },

      // ---------------- 回收站 ----------------

      loadSummary () {
        queryRecycleSummary().then(res => {
          if (res.success && res.result) {
            this.summary = res.result
          }
        }).catch(() => { /* 概览失败不影响主流程 */ })
      },
      loadRecycle () {
        this.loading = true
        queryRecycleList(this.recycleQuery.bizType, this.recycleQuery.keyword).then(res => {
          this.recycleRows = res.success && res.result ? res.result : []
          this.recycleTotal = this.recycleRows.length
        }).catch(() => {
          this.recycleRows = []
          this.recycleTotal = 0
        }).finally(() => {
          this.loading = false
        })
      },
      resetRecycle () {
        this.recycleQuery = { bizType: '', keyword: '' }
        this.loadRecycle()
      },
      handleRestore (record) {
        const label = record.bizKey || record.name || ''
        const self = this
        this.$confirm({
          title: `确认恢复「${label}」吗？`,
          content: '恢复后该记录会重新出现在对应的录入列表中。',
          okText: '恢复',
          cancelText: '取消',
          onOk () {
            return restoreData(record.bizType, record.id).then(res => {
              if (!res.success) {
                // ★ 原样展示后端提示（含「被哪条记录占用」这种关键信息）
                self.$error({ title: '无法恢复', content: res.message })
                return
              }
              const warning = res.result && res.result.warning
              if (warning) {
                // ★ 恢复成功但有提示：用 warning 而不是 error
                self.$warning({ title: '已恢复，但有需要注意的地方', content: warning })
              } else {
                self.$message.success('已恢复')
              }
              self.refresh()
            }).catch(e => {
              self.$error({ title: '恢复失败', content: (e && e.message) || '请稍后重试' })
            })
          }
        })
      },

      // ---------------- 变更留痕 ----------------

      loadActionOptions () {
        queryActionOptions().then(res => {
          if (res.success && res.result && res.result.length) {
            this.logActionOptions = res.result
          }
        }).catch(() => { /* 用本地兜底 */ })
      },
      loadLog () {
        this.logLoading = true
        const params = Object.assign({}, this.logQuery, {
          pageNo: this.logPagination.current,
          pageSize: this.logPagination.pageSize
        })
        queryChangeLogPage(params).then(res => {
          if (res.success && res.result) {
            this.logRows = res.result.records || []
            this.logTotal = Number(res.result.total || 0)
            this.logPagination.total = this.logTotal
          } else {
            this.logRows = []
            this.logTotal = 0
            this.logPagination.total = 0
          }
        }).catch(() => {
          this.logRows = []
        }).finally(() => {
          this.logLoading = false
        })
      },
      searchLog () {
        this.logPagination.current = 1
        this.loadLog()
      },
      resetLog () {
        this.logQuery = { bizType: '', action: '', operator: '', keyword: '' }
        this.searchLog()
      },
      handleLogTableChange (pagination) {
        this.logPagination.current = pagination.current
        this.logPagination.pageSize = pagination.pageSize
        this.loadLog()
      },
      showDetail (record) {
        this.currentLog = record
        this.detailVisible = true
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .data-recycle {
    /deep/ .ant-card-body {
      padding: 16px;
    }

    .page-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      padding-bottom: 14px;
      margin-bottom: 14px;
      border-bottom: 1px solid @border-color;

      &__title {
        margin: 0 0 6px;
        font-size: 18px;
        font-weight: 600;
        color: #0f172a;
      }

      &__desc {
        max-width: 960px;
        margin: 0;
        font-size: 12px;
        line-height: 20px;
        color: @text-muted;

        b {
          color: #0f172a;
        }
      }
    }

    .recycle-stats {
      margin-bottom: 8px;
    }

    .stat-card {
      padding: 10px 12px;
      margin-bottom: 12px;
      cursor: pointer;
      background: #fff;
      border: 1px solid @border-color;
      border-radius: 8px;
      transition: box-shadow 0.2s;

      &:hover {
        box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
      }

      &__label {
        font-size: 12px;
        color: @text-weak;
      }

      &__value {
        margin: 4px 0 2px;
        font-family: 'DIN Alternate', 'Bebas Neue', monospace;
        font-size: 22px;
        line-height: 26px;
        color: #0f172a;
      }

      &__extra {
        font-size: 11px;
        line-height: 16px;
        color: @text-weak;
      }
    }

    .recycle-tabs {
      /deep/ .ant-tabs-bar {
        margin-bottom: 12px;
      }
    }

    .tab-badge {
      margin-left: 6px;
    }

    .filter-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      margin-bottom: 12px;

      &__hint {
        flex: 1 1 auto;
        min-width: 240px;
        font-size: 12px;
        line-height: 18px;
        color: @text-weak;
      }
    }

    .cell-main {
      color: #0f172a;
    }

    .cell-sub {
      font-size: 12px;
      color: @text-weak;
    }

    .cell-summary {
      font-size: 12px;
      color: @text-muted;
    }

    .detail-head {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      margin-bottom: 12px;
      font-size: 13px;

      &__key {
        font-weight: 600;
        color: #0f172a;
      }

      &__meta {
        color: @text-weak;
      }
    }

    .detail-before {
      color: @text-muted;
      text-decoration: line-through;
    }

    .detail-after {
      font-weight: 600;
      color: #cf1322;
    }
  }
</style>
