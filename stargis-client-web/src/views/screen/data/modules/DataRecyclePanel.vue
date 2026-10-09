<template>
  <!--
    DataRecyclePanel 数据更新与移除（方案 2.3.1（三）第 6 项）
    --------------------------------
    两个页签：
      回收站   —— 已移除的宗地与配套，可恢复
      变更留痕 —— 全站的字段级变更记录，可筛可看明细

    ★★ 恢复的两种结局必须分开处理（这是本面板最容易写错的地方）：
      · 宗地有唯一键 (crzdbh, del_flag)：同编号已有有效记录时后端**抛错阻断**
        （success=false，message 是「无法恢复：出让宗地编号「xxx」已被另一条有效记录占用…」）
        → 弹错误提示，不能吞；
      · 配套没有唯一键：同名只**提示不阻断**，后端把话放在 `result.warning` 里，
        同时 `message` 也是这句话 → 弹**信息级**提示并展示原文。
      把 warning 当失败会让用户以为白干了；把阻断当 warning 会让用户以为恢复成功了。

    ★ 为什么「移除」这个动作不在这里：
      移除是录入页上的动作（那里已用 land:data:land / land:data:facility 把关）。
      本页只做「恢复」与「留痕查看」—— 既能恢复数据（等于一次写入）又能看到
      全部变更履历（含别人的操作），敏感度更高，所以单独一个权限码。

    事件：
      drill (payload)  跳到别的面板
  -->
  <div class="recycle-panel">
    <screen-tabs v-model="activeTab" :tabs="tabs" class="recycle-panel__tabs" />

    <!-- ================= 一、回收站 ================= -->
    <template v-if="activeTab === 'bin'">
      <screen-panel class="recycle-panel__filter" title="回收站筛选" collapsible>
        <div class="recycle-panel__filter-body">
          <screen-field label="业务类型" label-width="88px">
            <screen-radio-group
              v-model="binQuery.bizType"
              :options="binTypeOptions"
              aria-label="业务类型"
            />
          </screen-field>

          <screen-field label="关键字" label-width="88px" html-for="rp-keyword">
            <screen-input
              id="rp-keyword"
              v-model="binQuery.keyword"
              clearable
              placeholder="按宗地编号 / 地块名称 / 配套项目名称检索"
              @enter="loadBin"
            />
          </screen-field>

          <div class="recycle-panel__filter-actions">
            <screen-button icon="rotate-ccw" @click="handleResetBin">重置</screen-button>
            <screen-button type="primary" icon="search" @click="loadBin">查询</screen-button>
          </div>
        </div>
      </screen-panel>

      <screen-panel class="recycle-panel__list" title="已移除的数据">
        <template #extra>
          <span class="recycle-panel__summary">
            宗地 <b>{{ summary.landDeleted || 0 }}</b> 条 ·
            配套 <b>{{ summary.facilityDeleted || 0 }}</b> 条 ·
            合计 <b class="is-accent">{{ summary.total || 0 }}</b> 条
            <template v-if="binRows.length">
              · 当前显示 <b>{{ binRows.length }}</b> 条
            </template>
          </span>

          <screen-button size="sm" icon="reload" :loading="binLoading" @click="loadBin">刷新</screen-button>
          <screen-button size="sm" icon="arrow-left" @click="$emit('drill', { tab: 'land' })">
            去录入页移除数据
          </screen-button>
        </template>

        <recycle-table
          :data-source="binRows"
          :loading="binLoading"
          :restoring-key="restoringKey"
          @restore="handleRestore"
          @history="handleBinHistory"
        />
      </screen-panel>
    </template>

    <!-- ================= 二、变更留痕 ================= -->
    <template v-else>
      <screen-panel class="recycle-panel__filter" title="留痕筛选" collapsible>
        <div class="recycle-panel__filter-body">
          <screen-field label="业务类型" label-width="88px">
            <screen-select
              v-model="logQuery.bizType"
              :options="logBizTypeOptions"
              placeholder="全部业务"
              aria-label="业务类型"
            />
          </screen-field>

          <screen-field label="动作" label-width="88px">
            <screen-select
              v-model="logQuery.action"
              :options="actionOptions"
              placeholder="全部动作"
              aria-label="变更动作"
            />
          </screen-field>

          <screen-field label="操作人" label-width="88px" html-for="rp-operator" tip="账号或姓名关键字">
            <screen-input
              id="rp-operator"
              v-model="logQuery.operator"
              clearable
              placeholder="操作人"
              @enter="handleLogSearch"
            />
          </screen-field>

          <screen-field label="关键字" label-width="88px" html-for="rp-logKeyword" tip="业务可读键 / 摘要">
            <screen-input
              id="rp-logKeyword"
              v-model="logQuery.keyword"
              clearable
              placeholder="宗地编号 / 配套名称 / 摘要"
              @enter="handleLogSearch"
            />
          </screen-field>

          <div class="recycle-panel__filter-actions">
            <screen-button icon="rotate-ccw" @click="handleResetLog">重置</screen-button>
            <screen-button type="primary" icon="search" @click="handleLogSearch">查询</screen-button>
          </div>
        </div>
      </screen-panel>

      <screen-panel class="recycle-panel__list" title="变更留痕">
        <template #extra>
          <span class="recycle-panel__summary">
            共 <b>{{ logPagination.total }}</b> 条变更记录
            <template v-if="logRows.length">
              · 本页 <b>{{ logRows.length }}</b> 条
            </template>
          </span>

          <screen-button size="sm" icon="reload" :loading="logLoading" @click="loadLogs">刷新</screen-button>
        </template>

        <change-log-table
          :data-source="logRows"
          :loading="logLoading"
          @detail="handleShowDetail"
        />

        <template #footer>
          <screen-pagination
            :current="logPagination.current"
            :page-size="logPagination.pageSize"
            :total="logPagination.total"
            :page-size-options="logPagination.pageSizeOptions"
            @change="handleLogPageChange"
          />
        </template>
      </screen-panel>
    </template>

    <!-- 单条数据的完整履历（宗地 / 配套 / 环节通用） -->
    <history-drawer ref="historyDrawer" />

    <!--
      字段级明细。
      ★ 用 v-if：这是「弹窗里再开弹窗」，内层隐藏时两个 fixed 层会互相盖住点击。
    -->
    <screen-modal
      v-if="detailVisible"
      :visible.sync="detailVisible"
      :title="detailTitle"
      :width="880"
      :show-footer="false"
      :body-max-height="'calc(100vh - 240px)'"
      @cancel="detailVisible = false"
    >
      <div class="recycle-panel__detail">
        <p class="recycle-panel__detail-hint">
          <screen-icon name="info" :size="14" />
          只列出**真正变化**的字段：一次编辑往往只改一两个字段，
          把整条记录都列出来只会淹没真正的改动。
        </p>

        <screen-data-table
          :columns="detailColumns"
          :data="activeDetails"
          row-key="field"
          :min-width="640"
          :animated="false"
          empty-text="这条记录没有字段级明细"
        >
          <template #label="{ row }">
            <span class="recycle-panel__field-label">{{ row.label || row.field }}</span>
            <span class="recycle-panel__field-key">{{ row.field }}</span>
          </template>
          <template #before="{ row }">
            <span class="recycle-panel__before">{{ row.before || '（空）' }}</span>
          </template>
          <template #after="{ row }">
            <span class="recycle-panel__after">{{ row.after || '（空）' }}</span>
          </template>
        </screen-data-table>
      </div>
    </screen-modal>
  </div>
</template>

<script>
import {
  ScreenTabs,
  ScreenPanel,
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenRadioGroup,
  ScreenButton,
  ScreenPagination,
  ScreenDataTable,
  ScreenModal,
  ScreenIcon
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import RecycleTable from './RecycleTable.vue'
import ChangeLogTable from './ChangeLogTable.vue'
import HistoryDrawer from './HistoryDrawer.vue'
import {
  queryRecycleList,
  queryRecycleSummary,
  restoreRecycle,
  queryChangeLogPage,
  queryActionOptions,
  CHANGE_ACTIONS_FALLBACK,
  RECYCLE_BIZ_TYPES
} from '@/api/land/dataRecycle'
import { defaultPagination, compactQuery } from '../constants'

export default {
  name: 'DataRecyclePanel',
  components: {
    ScreenTabs,
    ScreenPanel,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenRadioGroup,
    ScreenButton,
    ScreenPagination,
    ScreenDataTable,
    ScreenModal,
    ScreenIcon,
    RecycleTable,
    ChangeLogTable,
    HistoryDrawer
  },
  props: {
    /** 下钻条件（可带 bizType / keyword） */
    initialQuery: { type: Object, default: () => ({}) }
  },
  data () {
    return {
      activeTab: 'bin',
      tabs: [
        { key: 'bin', label: '回收站' },
        { key: 'log', label: '变更留痕' }
      ],

      // ---------- 回收站 ----------
      binLoading: false,
      binQuery: { bizType: '', keyword: '' },
      binRows: [],
      summary: { landDeleted: 0, facilityDeleted: 0, total: 0 },
      restoringKey: '',
      // 回收站是「两类都查」的语义，所以多一个「全部」选项
      binTypeOptions: [{ value: '', label: '全部' }].concat(RECYCLE_BIZ_TYPES),

      // ---------- 变更留痕 ----------
      logLoading: false,
      logQuery: { bizType: '', action: '', operator: '', keyword: '' },
      logRows: [],
      logPagination: defaultPagination(10),
      actionOptions: CHANGE_ACTIONS_FALLBACK,
      // 留痕里除了宗地 / 配套，还有环节与附件，所以业务类型选项单独一份
      logBizTypeOptions: [
        { value: 'land', label: '经营性用地' },
        { value: 'facility', label: '配套项目' },
        { value: 'process', label: '环节进度' },
        { value: 'attachment', label: '附件' }
      ],

      // ---------- 字段级明细 ----------
      detailVisible: false,
      detailTitle: '字段变更明细',
      activeDetails: [],
      detailColumns: [
        { key: 'label', title: '字段', width: 220, type: 'slot' },
        { key: 'before', title: '改前', width: 200, type: 'slot' },
        { key: 'after', title: '改后', type: 'slot' }
      ]
    }
  },
  created () {
    this.loadSummary()
    this.loadBin()
    this.loadActionOptions()
  },
  methods: {
    /* ---------------- 回收站 ---------------- */

    loadSummary () {
      return queryRecycleSummary()
        .then((res) => {
          if (!res || !res.success) return
          this.summary = res.result || this.summary
        })
        .catch(() => {
          // 计数值是辅助信息，失败保持上一次的值
        })
    },

    loadBin () {
      this.binLoading = true
      const params = compactQuery(this.binQuery)
      return queryRecycleList(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '回收站加载失败')
            return
          }
          // ★ rowKey 必须由 bizType + id 组成：两张表的主键可能撞号
          //   （都是迁移自旧库的雪花/自增 id），只用 id 做 key 会出现「选中一行，
          //    另一类里同 id 的那行也高亮」。
          this.binRows = (res.result || []).map((item) => Object.assign({}, item, {
            rowKey: `${item.bizType}-${item.id}`
          }))
        })
        .catch(() => {
          this.binRows = []
        })
        .finally(() => {
          this.binLoading = false
        })
    },

    handleResetBin () {
      this.binQuery = { bizType: '', keyword: '' }
      this.loadBin()
    },

    /**
     * 恢复一条。
     * ★★ 两种结局分开处理（见组件头注释）：
     *   success=false → 阻断（宗地编号冲突），弹 error；
     *   success=true 且 result.warning → 非阻断提示（配套同名），弹 info 并展示原文。
     */
    handleRestore (row) {
      if (!row || !row.id) return
      this.restoringKey = row.rowKey
      restoreRecycle(row.bizType, row.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '恢复失败')
            return
          }
          const result = res.result || {}
          if (result.warning) {
            // 「恢复成功但出现了同名记录」这类提示：用 info 而不是 success，
            // 因为用户确实需要看一眼再决定要不要处理
            toast.info(`已恢复，但请注意：${result.warning}`)
          } else {
            toast.success(res.message || '已恢复')
          }
          this.loadBin()
          this.loadSummary()
        })
        .catch(() => {
          // 请求层已提示（后端把阻断原因放在 message 里，request 层会弹出来）
        })
        .finally(() => {
          this.restoringKey = ''
        })
    },

    handleBinHistory (row) {
      if (!row) return
      this.$refs.historyDrawer.open({
        bizType: row.bizType,
        bizId: row.id,
        bizKey: row.bizKey || row.name,
        title: `${row.bizType === 'land' ? '经营性用地' : '配套项目'}履历 · ${row.name || row.bizKey || ''}`
      })
    },

    /* ---------------- 变更留痕 ---------------- */

    loadActionOptions () {
      queryActionOptions()
        .then((res) => {
          if (res && res.success && res.result && res.result.length) {
            this.actionOptions = res.result
          }
        })
        .catch(() => {
          // 用本地兜底（与后端 ChangeLogSupport.actionOptions 逐字一致）
          this.actionOptions = CHANGE_ACTIONS_FALLBACK
        })
    },

    loadLogs () {
      this.logLoading = true
      const params = Object.assign(compactQuery(this.logQuery), {
        pageNo: this.logPagination.current,
        pageSize: this.logPagination.pageSize
      })
      return queryChangeLogPage(params)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '变更留痕加载失败')
            return
          }
          // ★ 这个接口的返回结构不是 jeecg 的 IPage，而是
          //   { records, total, pageNo, pageSize, pages } —— 见 api 层注释
          const page = res.result || {}
          const records = page.records || []
          this.logRows = records.map((item, index) =>
            Object.assign({ key: item.id || `log-${index}` }, item)
          )
          this.logPagination = Object.assign({}, this.logPagination, {
            total: Number(page.total || 0)
          })

          if (!this.logRows.length && this.logPagination.total > 0 && this.logPagination.current > 1) {
            this.logPagination.current -= 1
            return this.loadLogs()
          }
        })
        .finally(() => {
          this.logLoading = false
        })
    },

    handleLogSearch () {
      this.logPagination.current = 1
      this.loadLogs()
    },

    handleResetLog () {
      this.logQuery = { bizType: '', action: '', operator: '', keyword: '' }
      this.logPagination.current = 1
      this.loadLogs()
    },

    handleLogPageChange ({ current, pageSize }) {
      this.logPagination = Object.assign({}, this.logPagination, { current, pageSize })
      this.loadLogs()
    },

    /** 字段级明细：留痕表格与行点击共用同一个出口 */
    handleShowDetail (row) {
      const details = (row && row.details) || []
      if (!details.length) {
        toast.info('这条记录没有字段级明细（例如「移除 / 恢复」只记动作与原因）')
        return
      }
      this.activeDetails = details.map((item, index) =>
        Object.assign({ key: item.field || `d-${index}` }, item)
      )
      this.detailTitle = `字段变更明细 · ${row.actionText || row.action || ''}`
      this.detailVisible = true
    },

    /* ---------------- 对外（父组件调用） ---------------- */

    /**
     * 应用下钻条件（例如从某条数据点「履历 / 留痕」跳过来）。
     * ★ 面板被 keep-alive 缓存后 created 不会再跑，必须由父组件显式调用。
     */
    applyDrill (query) {
      const next = query || {}
      if (next.bizType || next.keyword) {
        this.binQuery = {
          bizType: next.bizType || '',
          keyword: next.keyword || ''
        }
        this.loadBin()
      }
      if (next.bizType) {
        this.logQuery = Object.assign({}, this.logQuery, { bizType: next.bizType })
      }
      if (next.bizKey) {
        this.logQuery = Object.assign({}, this.logQuery, { keyword: next.bizKey })
      }
      this.loadLogs()
    },

    /** 直接落到「变更留痕」页签（供外部入口用） */
    showChangeLog () {
      this.activeTab = 'log'
      this.logPagination.current = 1
      this.loadLogs()
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.recycle-panel {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);
  height: 100%;
  min-height: 0;

  &__tabs {
    flex: 0 0 auto;
  }

  /* 筛选区 / 列表区：与其它面板同一套弹性分工 */
  &__filter {
    flex: 0 0 auto;
  }

  &__list {
    // flex-basis 必须是 0：用 auto 会按内容撑开，导致分页被挤出可见区
    flex: 1 1 0;
    min-height: 0;
  }

  &__filter-body {
    display: flex;
    align-items: center;
    gap: var(--screen-space-4);
    flex-wrap: wrap;

    /deep/ .screen-field {
      flex: 0 1 360px;
      min-width: 220px;
    }
  }

  &__filter-actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin-left: auto;
  }

  &__summary {
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

  /* ---------------- 字段级明细 ---------------- */
  &__detail {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
  }

  &__detail-hint {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-text-sub);

    b {
      color: var(--screen-accent);
      font-weight: 600;
    }
  }

  &__field-label {
    color: var(--screen-text);
  }

  &__field-key {
    display: block;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  /** 改前：删除线 + 弱色；改后：绿色加粗。颜色不是唯一线索（删除线 + 加粗）。 */
  &__before {
    color: var(--screen-text-mute);
    text-decoration: line-through;
  }

  &__after {
    color: var(--screen-success);
    font-weight: 600;
  }
}

// 工具条上的按钮较多，窄屏允许换行而不是被压扁
.recycle-panel__list /deep/ .screen-panel__extra {
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
