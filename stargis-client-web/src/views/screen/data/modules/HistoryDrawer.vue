<template>
  <!--
    HistoryDrawer 单条数据的完整履历
    --------------------------------
    名字里带 Drawer，实现上是 ScreenModal —— 组件库**没有** ScreenDrawer
    （见 components/screen/index.js 的清单），而为了一个「侧滑」去新增一个
    基础组件会连带改 index.js 的两处登记与文档，收益不成比例。
    全屏以外的中等宽度弹窗同样能达到「不离开当前页面看履历」的目的。

    用途：从宗地 / 配套 / 回收站的某一行点「履历」时打开，
    回答「这一条被谁在什么时候改了什么」。

    ★ 与「变更留痕」页签的分工：
      页签是**全量**视角（按时间排的全站变更，用来做审计与排查）；
      这里是**单对象**视角（这一条的历史），用来回答「它怎么变成现在这样的」。

    公开方法：
      open(payload)  payload = { bizType, bizId, bizKey, title }
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="960"
    :show-footer="false"
    :body-max-height="'calc(100vh - 200px)'"
    @cancel="handleClose"
  >
    <div class="history-drawer">
      <header class="history-drawer__head">
        <span class="history-drawer__type">
          <screen-tag tone="info" size="sm">{{ bizTypeText(context.bizType) }}</screen-tag>
        </span>
        <h3 class="history-drawer__key">{{ context.bizKey || '—' }}</h3>
        <span class="history-drawer__count">共 {{ rows.length }} 条变更记录</span>
      </header>

      <screen-data-table
        :columns="columns"
        :data="rows"
        :loading="loading"
        row-key="key"
        :min-width="880"
        :animated="false"
        row-clickable
        empty-text="这条记录还没有变更履历"
        @row-click="handleRowClick"
      >
        <template #createTime="{ row }">
          <span class="history-drawer__time">{{ formatTime(row.createTime) }}</span>
        </template>

        <template #action="{ row }">
          <screen-tag :tone="actionTone(row.action)" size="sm">{{ row.actionText || row.action }}</screen-tag>
        </template>

        <template #changeCount="{ row }">
          <span class="history-drawer__num">{{ row.changeCount || 0 }} 项</span>
        </template>

        <template #summary="{ row }">
          <span class="history-drawer__summary" :title="row.summary || ''">{{ row.summary || '—' }}</span>
        </template>

        <template #operatorName="{ row }">
          <span class="history-drawer__operator">
            {{ row.operatorName || row.operator || '—' }}
          </span>
        </template>

        <template #detailAction="{ row }">
          <button
            type="button"
            class="history-drawer__link"
            :disabled="!((row.details || []).length)"
            @click.stop="handleShowDetail(row)"
          >
            查看改了什么
          </button>
        </template>
      </screen-data-table>

      <!--
        字段级明细。
        ★ 用 v-if：这是「弹窗里再开弹窗」，内层隐藏时两个 fixed 层会互相盖住点击。
      -->
      <screen-modal
        v-if="detailVisible"
        :visible.sync="detailVisible"
        :title="detailTitle"
        :width="860"
        :show-footer="false"
        :body-max-height="'calc(100vh - 260px)'"
        @cancel="detailVisible = false"
      >
        <screen-data-table
          :columns="detailColumns"
          :data="activeDetails"
          row-key="field"
          :min-width="640"
          :animated="false"
          empty-text="这条记录没有字段级明细"
        >
          <template #label="{ row }">
            <span class="history-drawer__field-label">{{ row.label || row.field }}</span>
            <span class="history-drawer__field-key">{{ row.field }}</span>
          </template>
          <template #before="{ row }">
            <span class="history-drawer__before">{{ row.before || '（空）' }}</span>
          </template>
          <template #after="{ row }">
            <span class="history-drawer__after">{{ row.after || '（空）' }}</span>
          </template>
        </screen-data-table>
      </screen-modal>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenTag,
  ScreenDataTable
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { queryChangeHistory } from '@/api/land/dataRecycle'
import { formatTime, actionTone, bizTypeText } from '../constants'

export default {
  name: 'HistoryDrawer',
  components: { ScreenModal, ScreenTag, ScreenDataTable },
  data () {
    return {
      visible: false,
      loading: false,
      context: {},
      history: [],
      detailVisible: false,
      detailTitle: '字段变更明细',
      activeDetails: [],
      columns: [
        { key: 'createTime', title: '时间', width: 160, type: 'slot' },
        { key: 'action', title: '动作', width: 100, type: 'slot', align: 'center' },
        { key: 'changeCount', title: '变更字段', width: 100, type: 'slot', align: 'center' },
        { key: 'summary', title: '摘要', width: 300, type: 'slot' },
        { key: 'operatorName', title: '操作人', width: 120, type: 'slot' },
        { key: 'detailAction', title: '明细', width: 130, type: 'slot', align: 'center' }
      ],
      detailColumns: [
        { key: 'label', title: '字段', width: 220, type: 'slot' },
        { key: 'before', title: '改前', width: 200, type: 'slot' },
        { key: 'after', title: '改后', type: 'slot' }
      ]
    }
  },
  computed: {
    title () {
      return this.context.title || `变更履历 · ${this.context.bizKey || ''}`
    },
    /** 履历行加稳定 key（ScreenDataTable 的 row-key 需要唯一值） */
    rows () {
      return (this.history || []).map((item, index) => Object.assign({ key: item.id || `h-${index}` }, item))
    }
  },
  methods: {
    formatTime,
    actionTone,
    bizTypeText,

    /* ---------------- 对外入口 ---------------- */

    open (payload) {
      const context = payload || {}
      if (!context.bizType || !context.bizId) {
        toast.warning('缺少业务类型或业务主键，无法查看履历')
        return
      }
      this.context = context
      this.history = []
      this.detailVisible = false
      this.visible = true
      this.load()
    },

    load () {
      this.loading = true
      queryChangeHistory(this.context.bizType, this.context.bizId, 100)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '履历加载失败')
            return
          }
          this.history = res.result || []
        })
        .catch(() => {
          this.history = []
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 明细 ---------------- */

    handleRowClick (row) {
      if (row && (row.details || []).length) {
        this.handleShowDetail(row)
      }
    },

    handleShowDetail (row) {
      const details = (row && row.details) || []
      if (!details.length) {
        toast.info('这条记录没有字段级明细（例如「移除 / 恢复」只记动作）')
        return
      }
      this.activeDetails = details.map((item, index) =>
        Object.assign({ key: item.field || `d-${index}` }, item)
      )
      this.detailTitle = `字段变更明细 · ${row.actionText || row.action || ''} ${formatTime(row.createTime)}`
      this.detailVisible = true
    },

    handleClose () {
      this.visible = false
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.history-drawer {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-2);
  min-height: 0;

  &__head {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
    padding-bottom: var(--screen-space-2);
    border-bottom: 1px solid var(--screen-border-soft);
  }

  &__type {
    display: inline-flex;
    align-items: center;
  }

  &__key {
    margin: 0;
    min-width: 0;
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-lg);
    font-weight: 600;
    color: var(--screen-accent);
  }

  &__count {
    margin-left: auto;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__time {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }

  &__num {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);
  }

  &__summary {
    display: block;
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__operator {
    color: var(--screen-text-sub);
  }

  &__link {
    .screen-link-action();
  }

  /* ---------------- 字段级明细 ---------------- */
  &__field-label {
    color: var(--screen-text);
  }

  &__field-key {
    display: block;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__before {
    color: var(--screen-text-mute);
    text-decoration: line-through;
  }

  &__after {
    color: var(--screen-success);
    font-weight: 600;
  }
}
</style>
