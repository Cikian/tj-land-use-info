<template>
  <!--
    ChangeLogTable 变更留痕表格
    --------------------------------
    纯展示组件。列的设计围绕「谁在什么时候改了什么」这一句话：
      时间 / 动作 / 业务类型 / 业务可读键 / 摘要 / 变更字段数 / 操作人 / 明细入口

    ★ 为什么「变更字段数」要单独一列：
      摘要里只有一句话（后端在单字段变更时会给「修改：出让金 1.20 → 2.00」，
      多字段时只给「3 个字段（出让金 等）」）。
      用户扫列表时先用「变更字段数」判断这次改动大不大，再决定点不点明细。

    ★ 为什么点击整行等价于点「查看改了什么」：
      留痕的用法就是「顺着看一眼细节」，行本身没有别的语义。
      但按钮仍然保留 —— 键盘用户与读屏用户需要一个可预测的操作名。

    事件：
      detail (row)  查看字段级明细
  -->
  <screen-data-table
    :columns="columns"
    :data="dataSource"
    :loading="loading"
    row-key="key"
    :min-width="1420"
    :animated="false"
    row-clickable
    :empty-text="emptyText"
    @row-click="handleRowClick"
  >
    <template #createTime="{ row }">
      <span class="change-log-table__time">{{ formatTime(row.createTime) }}</span>
    </template>

    <template #action="{ row }">
      <screen-tag :tone="actionTone(row.action)" size="sm">{{ row.actionText || row.action }}</screen-tag>
    </template>

    <template #bizType="{ row }">
      <screen-tag tone="muted" size="sm">{{ bizTypeText(row.bizType) }}</screen-tag>
    </template>

    <template #bizKey="{ row }">
      <span class="change-log-table__key" :title="row.bizKey || ''">{{ row.bizKey || '—' }}</span>
    </template>

    <template #changeCount="{ row }">
      <span class="change-log-table__num">{{ row.changeCount || 0 }}</span>
    </template>

    <template #summary="{ row }">
      <span class="change-log-table__summary" :title="row.summary || row.changeSummary || ''">
        {{ row.summary || row.changeSummary || '—' }}
      </span>
    </template>

    <template #operatorName="{ row }">
      <span>{{ row.operatorName || row.operator || '—' }}</span>
    </template>

    <template #detailAction="{ row }">
      <button
        type="button"
        class="change-log-table__link"
        :disabled="!((row.details || []).length)"
        @click.stop="$emit('detail', row)"
      >
        查看改了什么
      </button>
    </template>
  </screen-data-table>
</template>

<script>
import { ScreenDataTable, ScreenTag } from '@/components/screen'
import { formatTime, actionTone, bizTypeText } from '../constants'

export default {
  name: 'ChangeLogTable',
  components: { ScreenDataTable, ScreenTag },
  props: {
    dataSource: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    emptyText: { type: String, default: '暂无变更记录' }
  },
  data () {
    return {
      columns: [
        { key: 'createTime', title: '时间', width: 170, type: 'slot' },
        { key: 'action', title: '动作', width: 110, type: 'slot', align: 'center' },
        { key: 'bizType', title: '业务对象', width: 110, type: 'slot', align: 'center' },
        { key: 'bizKey', title: '业务可读键', width: 200, type: 'slot' },
        { key: 'changeCount', title: '变更字段', width: 100, type: 'slot', align: 'center' },
        { key: 'summary', title: '摘要', width: 360, type: 'slot' },
        { key: 'operatorName', title: '操作人', width: 120, type: 'slot' },
        { key: 'operatorIp', title: 'IP', width: 140, formatter: (value) => value || '—' },
        { key: 'detailAction', title: '明细', width: 140, type: 'slot', align: 'center' }
      ]
    }
  },
  methods: {
    formatTime,
    actionTone,
    bizTypeText,
    handleRowClick (row) {
      if (row && (row.details || []).length) {
        this.$emit('detail', row)
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.change-log-table {
  &__time {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }

  &__key {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
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

  &__link {
    .screen-link-action();
  }
}
</style>
