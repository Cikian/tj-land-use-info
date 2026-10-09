<template>
  <!--
    RecycleTable 回收站表格
    --------------------------------
    后端把两张表（宗地 / 配套）的软删记录统一成同一个结构返回，所以这里是一张表：
      bizType  业务类型（land / facility）
      bizKey   ★ 业务可读键 —— 宗地是**宗地编号**、配套是**所属宗地编号**
      name     宗地是地块名称、配套是配套项目名称
      xzqh / xmfl / lrdw / lrr / updateTime
      delFlag  各自的软删列值（t_land 是 tinyint、配套是 varchar，后端已统一）

    ★ 为什么「编号」与「名称」要分两列显示：
      两类的 `bizKey` 语义不同（宗地是自己的编号、配套是**所属宗地**的编号），
      把它们混在一列里，用户会以为配套的编号就是它自己的编号。
      所以列标题写「编号 / 名称」并把语义写进 title。

    事件：
      restore (row)  恢复
      history (row)  看这条记录的完整履历
  -->
  <screen-data-table
    :columns="columns"
    :data="dataSource"
    :loading="loading"
    row-key="rowKey"
    :min-width="1360"
    :empty-text="emptyText"
  >
    <template #bizType="{ row }">
      <screen-tag :tone="row.bizType === 'land' ? 'success' : 'info'" size="sm">
        {{ bizTypeText(row.bizType) }}
      </screen-tag>
    </template>

    <template #bizKey="{ row }">
      <span class="recycle-table__key" :title="keyTitle(row)">{{ row.bizKey || '—' }}</span>
      <span v-if="row.projectName && row.bizType === 'facility'" class="recycle-table__sub">
        所属配套：{{ row.projectName }}
      </span>
    </template>

    <template #name="{ row }">
      <span class="recycle-table__name" :title="row.name || ''">{{ row.name || '—' }}</span>
    </template>

    <template #xzqh="{ row }">
      <span>{{ row.xzqh || '—' }}</span>
    </template>

    <template #xmfl="{ row }">
      <screen-tag v-if="row.xmfl" :tone="row.xmfl === '市级项目' ? 'success' : 'info'" size="sm">
        {{ row.xmfl }}
      </screen-tag>
      <span v-else class="recycle-table__mute">—</span>
    </template>

    <template #lrdw="{ row }">
      <span class="recycle-table__ellipsis" :title="row.lrdw || ''">{{ row.lrdw || '—' }}</span>
    </template>

    <template #lrr="{ row }">
      <span>{{ row.lrr || row.updateBy || '—' }}</span>
    </template>

    <template #updateTime="{ row }">
      <span class="recycle-table__time">{{ formatTime(row.updateTime) }}</span>
    </template>

    <template #action="{ row }">
      <span class="recycle-table__actions">
        <screen-popconfirm
          title="确定恢复这条记录吗？"
          :description="restoreDescription(row)"
          width="340"
          :confirm-loading="restoringKey === row.rowKey"
          @confirm="$emit('restore', row)"
        >
          <button type="button" class="recycle-table__link">恢复</button>
        </screen-popconfirm>

        <button type="button" class="recycle-table__link" @click.stop="$emit('history', row)">履历</button>
      </span>
    </template>
  </screen-data-table>
</template>

<script>
import { ScreenDataTable, ScreenTag, ScreenPopconfirm } from '@/components/screen'
import { formatTime, bizTypeText } from '../constants'

export default {
  name: 'RecycleTable',
  components: { ScreenDataTable, ScreenTag, ScreenPopconfirm },
  props: {
    dataSource: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    /** 正在恢复的那一行的 key（用于按钮 loading 态；后端没有批量恢复的单行状态） */
    restoringKey: { type: String, default: '' },
    emptyText: { type: String, default: '回收站是空的（没有已移除的宗地或配套）' }
  },
  data () {
    return {
      columns: [
        { key: 'bizType', title: '类型', width: 110, type: 'slot', align: 'center' },
        { key: 'bizKey', title: '编号 / 名称', width: 280, type: 'slot' },
        { key: 'name', title: '记录名称', width: 240, type: 'slot' },
        { key: 'xzqh', title: '行政区划', width: 100, type: 'slot' },
        { key: 'xmfl', title: '项目分类', width: 110, type: 'slot', align: 'center' },
        { key: 'lrdw', title: '原录入单位', width: 190, type: 'slot' },
        { key: 'lrr', title: '原录入人', width: 110, type: 'slot' },
        { key: 'updateTime', title: '移除时间', width: 170, type: 'slot' },
        { key: 'action', title: '操作', width: 140, type: 'slot', align: 'center' }
      ]
    }
  },
  methods: {
    formatTime,
    bizTypeText,

    /**
     * 编号列的 title。
     * ★ 必须把语义写清楚：宗地的 bizKey 是它自己的编号，
     *   配套的 bizKey 是它**所属宗地**的编号 —— 不看说明一定会认错。
     */
    keyTitle (row) {
      if (row.bizType === 'land') {
        return `出让宗地编号：${row.bizKey || '—'}`
      }
      return `所属出让宗地编号：${row.bizKey || '—'}`
    },

    /**
     * 恢复前的提示文案。
     * ★ 两类的后果不同，必须分别说：
     *   宗地有唯一键 (crzdbh, del_flag)，同编号已有有效记录时会**恢复失败**（后端阻断）；
     *   配套没有唯一键，同名只会出现两条记录（后端只提示不阻断）。
     */
    restoreDescription (row) {
      if (row.bizType === 'land') {
        return `宗地编号：${row.bizKey || '—'}。若该编号已被另一条有效记录占用，恢复会被拒绝。`
      }
      return `配套项目：${row.name || '—'}（所属宗地 ${row.bizKey || '—'}）。恢复后如该宗地下已有同名配套，会出现同名记录。`
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.recycle-table {
  &__key {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__sub {
    display: block;
    margin-top: 1px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  &__name {
    display: block;
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__ellipsis {
    display: block;
    .screen-ellipsis();
  }

  &__mute {
    color: var(--screen-text-mute);
  }

  &__time {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
    justify-content: center;
  }

  &__link {
    .screen-link-action();
  }
}
</style>
