<template>
  <!--
    ArchiveTable 档案列表表格
    --------------------------------
    「档案维护」与「档案查询」共用同一个表格，差别只有两点：
      readonly=true 时不渲染编辑/删除（档案查询是只读的）；
      selectable=true 时出现行选择列（档案维护支持批量删除）。

    这是一个**纯展示**组件：不发任何请求，全部交互通过事件抛给父组件，
    这样维护页与查询页才能零成本复用。

    事件：
      change        (pagination)  分页变化
      select-change (keys, rows)  行选择变化
      detail        (row)         查看详情
      edit          (row)         编辑
      delete        (row)         删除（父组件负责发请求）
  -->
  <screen-data-table
    :columns="columns"
    :data="dataSource"
    :loading="loading"
    row-key="id"
    :min-width="1560"
    :selectable="selectable"
    :selected-keys="selectedRowKeys"
    :empty-text="emptyText"
    @select-change="handleSelectChange"
  >
    <!-- 档案名称：主标题 + 来源副行（收文/发文归档时提示档案不是手工录入的） -->
    <template #archiveName="{ row }">
      <span class="archive-table__name" :title="row.archiveName">{{ row.archiveName || '—' }}</span>
      <span v-if="row.sourceType && row.sourceType !== 'manual'" class="archive-table__source">
        {{ sourceTypeText(row.sourceType) }}
      </span>
    </template>

    <!-- 档案类别：可能聚合了多个类别，超长时用 title 给出完整值 -->
    <template #categoryNames="{ row }">
      <span class="archive-table__ellipsis" :title="row.categoryNames || ''">
        {{ row.categoryNames || '未分类' }}
      </span>
    </template>

    <!-- 卷内文件：数量 + 总体积，两者都是决策信息，不能只给数量 -->
    <template #fileCount="{ row }">
      <span class="archive-table__files">
        <em>{{ row.fileCount || 0 }}</em>
        <span class="archive-table__files-size">{{ formatSize(row.totalSize) }}</span>
      </span>
    </template>

    <!-- 操作列：用插槽而不是内置 actions，因为删除要挂二次确认气泡 -->
    <template #action="{ row }">
      <span class="archive-table__actions">
        <button type="button" class="archive-table__link" @click.stop="$emit('detail', row)">详情</button>

        <template v-if="!readonly">
          <button
            type="button"
            class="archive-table__link"
            @click.stop="$emit('edit', row)"
          >
            编辑
          </button>

          <screen-popconfirm
            title="删除后不可恢复，确定删除该档案吗？"
            :description="row.archiveNo ? `档案号：${row.archiveNo}` : ''"
            width="264"
            @confirm="$emit('delete', row)"
          >
            <button type="button" class="archive-table__link is-danger">删除</button>
          </screen-popconfirm>
        </template>
      </span>
    </template>
  </screen-data-table>
</template>

<script>
import { ScreenDataTable, ScreenPopconfirm } from '@/components/screen'
import { formatSize, sourceTypeText, statusTone } from '../constants'
export default {
  name: 'ArchiveTable',
  components: { ScreenDataTable, ScreenPopconfirm },
  props: {
    /** 行数据 */
    dataSource: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    /** 是否显示行选择列 */
    selectable: { type: Boolean, default: false },
    /** 已选行的 id 集合 */
    selectedRowKeys: { type: Array, default: () => [] },
    /** 只读模式：隐藏编辑/删除 */
    readonly: { type: Boolean, default: false },
    emptyText: { type: String, default: '暂无档案' },
  },
  data () {
    return {
      columns: [
        { key: 'archiveNo', title: '档案号', width: 150, type: 'link' },
        { key: 'archiveName', title: '档案名称', width: 220, type: 'slot' },
        { key: 'categoryNames', title: '档案类别', width: 200, type: 'slot' },
        { key: 'ptxmmc', title: '配套项目', width: 190, ellipsis: true },
        { key: 'crzdbh', title: '出让宗地编号', width: 150, ellipsis: true },
        { key: 'xzqh', title: '行政区划', width: 90 },
        { key: 'responsibleDept', title: '责任部门', width: 120, ellipsis: true },
        { key: 'responsibleUser', title: '配套负责人', width: 100 },
        { key: 'archiveDate', title: '归档日期', width: 110 },
        { key: 'fileCount', title: '卷内文件', width: 130, type: 'slot' },
        // tone 支持函数：按状态值决定标签语气，颜色 + 文字双重线索
        { key: 'status', title: '状态', width: 90, type: 'tag', tone: statusTone },
        { key: 'action', title: '操作', width: 150, type: 'slot', align: 'center' },
      ],
    }
  },
  methods: {
    formatSize,
    sourceTypeText,
    handleSelectChange (keys, rows) {
      this.$emit('select-change', keys, rows)
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.archive-table {
  &__name {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__source {
    display: inline-block;
    margin-top: 1px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__ellipsis {
    display: block;
    .screen-ellipsis();
  }

  &__files {
    display: inline-flex;
    align-items: baseline;
    gap: 5px;

    em {
      font-style: normal;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      color: var(--screen-text);
    }
  }

  &__files-size {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    // 三个操作在窄列里可能换行，允许换行避免横向溢出
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
