<template>
  <!--
    CompletionTable 竣工验收历史档案 · 列表表格
    ---------------------------------------------------------------
    纯展示组件：不发任何请求，全部交互通过事件抛给面板，便于面板在「概览下钻」等
    场景里原样复用。

    事件：
      detail (row)   查看详情（点档案编号或操作列的「详情」）
      edit   (row)   编辑
      link   (row)   关联扫描件（打开详情并直接进入挑档案区）
      remove (row)   删除（父组件负责发请求）
  -->
  <screen-data-table
    :columns="columns"
    :data="dataSource"
    :loading="loading"
    row-key="id"
    :min-width="1578"
    :empty-text="emptyText"
    @cell-click="handleCellClick"
  >
    <!-- 序号：用后端下发的 seq（跨页连续），比按当前页重新编号更可信 -->
    <template #seq="{ row }">
      <span class="completion-table__seq">{{ row.seq }}</span>
    </template>

    <!-- 历史项目名称：主行 + 原编号副行（本系统编号与原编号是两回事，必须都能看到） -->
    <template #projectName="{ row }">
      <span class="completion-table__name" :title="row.projectName">{{ row.projectName || '—' }}</span>
      <span v-if="row.projectCode" class="completion-table__sub" :title="row.projectCode">
        原编号 {{ row.projectCode }}
      </span>
    </template>

    <!-- 建设 / 施工单位：两行并列，避免再占一列把表格撑出横向滚动 -->
    <template #units="{ row }">
      <span class="completion-table__unit" :title="row.buildUnit">
        <em>建设</em>{{ row.buildUnit || '—' }}
      </span>
      <span class="completion-table__unit" :title="row.constructUnit">
        <em>施工</em>{{ row.constructUnit || '—' }}
      </span>
    </template>

    <!-- 页数 / 文件数：两个都是判断数字化工作量的核心量，合并在一列但都给出 -->
    <template #pages="{ row }">
      <span class="completion-table__pages">
        <span class="completion-table__pages-main">{{ formatCount(row.pageCount) }} 页</span>
        <span class="completion-table__muted">/ {{ formatCount(row.fileCount) }} 个</span>
      </span>
    </template>

    <!-- 扫描件：显示已挂接的档案数；本档案已指定关联档案时高亮 -->
    <template #archiveCount="{ row }">
      <span
        class="completion-table__scan"
        :class="{ 'is-linked': !!row.archiveId }"
        :title="row.archiveId ? '已指定关联档案' : '尚未指定关联档案'"
      >
        {{ row.archiveCount || 0 }}
      </span>
    </template>

    <!-- 操作列：用插槽而不是内置 actions，因为删除要挂二次确认气泡 -->
    <template #action="{ row }">
      <span class="completion-table__actions">
        <button type="button" class="completion-table__link" @click.stop="$emit('detail', row)">详情</button>
        <button type="button" class="completion-table__link" @click.stop="$emit('edit', row)">编辑</button>
        <button type="button" class="completion-table__link" @click.stop="$emit('link', row)">关联档案</button>

        <screen-popconfirm
          title="删除后不可恢复，确定删除该历史档案吗？"
          :description="row.archiveNo ? `档案编号：${row.archiveNo}` : ''"
          width="272"
          @confirm="$emit('remove', row)"
        >
          <button type="button" class="completion-table__link is-danger">删除</button>
        </screen-popconfirm>
      </span>
    </template>
  </screen-data-table>
</template>

<script>
import { ScreenDataTable, ScreenPopconfirm } from '@/components/screen'
import { digitizeTone, formatCount } from './constants'

export default {
  name: 'CompletionTable',
  components: { ScreenDataTable, ScreenPopconfirm },
  props: {
    /** 行数据 */
    dataSource: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    emptyText: { type: String, default: '暂无历史档案' }
  },
  data () {
    return {
      columns: [
        { key: 'seq', title: '序号', width: 64, type: 'slot' },
        { key: 'archiveNo', title: '档案编号', width: 150, type: 'link' },
        { key: 'projectName', title: '历史项目名称', width: 250, type: 'slot' },
        { key: 'projectType', title: '项目类型', width: 104 },
        { key: 'xzqh', title: '行政区划', width: 92 },
        { key: 'units', title: '建设 / 施工单位', width: 190, type: 'slot' },
        { key: 'completeDate', title: '竣工日期', width: 106 },
        { key: 'acceptanceDate', title: '验收日期', width: 106 },
        // tone 支持函数：按状态值决定标签语气，颜色 + 文字双重线索
        { key: 'digitizeStatus', title: '数字化状态', width: 116, type: 'tag', tone: digitizeTone },
        { key: 'pages', title: '页数 / 文件数', width: 120, type: 'slot' },
        { key: 'archiveCount', title: '扫描件', width: 84, type: 'slot', align: 'center' },
        { key: 'action', title: '操作', width: 196, type: 'slot', align: 'center' }
      ]
    }
  },
  methods: {
    formatCount,
    /** 表格内可点单元格（目前只有档案编号）：统一按「点档案编号=看详情」处理 */
    handleCellClick (row, col) {
      if (col && col.key === 'archiveNo') {
        this.$emit('detail', row)
      }
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.completion-table {
  &__seq {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-mute);
  }

  &__name {
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

  &__unit {
    display: block;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    .screen-ellipsis();

    em {
      margin-right: 4px;
      font-style: normal;
      color: var(--screen-text-mute);
    }
  }

  &__pages {
    display: inline-flex;
    align-items: baseline;
    gap: 4px;
    white-space: nowrap;
  }

  &__pages-main {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);
  }

  &__muted {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  // 扫描件数：未指定关联档案时用弱化色，已指定时用强调色
  &__scan {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-mute);

    &.is-linked {
      color: var(--screen-accent);
    }
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    // 四个操作在窄列里会换行，允许换行避免横向溢出
    flex-wrap: wrap;
    justify-content: center;
  }

  &__link {
    .screen-link-action();

    &.is-danger {
      color: var(--screen-danger);

      &:hover {
        color: #ffab9f;
      }
    }
  }
}
</style>
