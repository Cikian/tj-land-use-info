<template>
  <!--
    LedgerTable 台账列表表格
    --------------------------------
    这是一个**纯展示**组件：不发任何请求，交互全部通过事件抛给父组件。

    ★ 两种列形态（同一份数据，切换不重新请求）：
      精简列（默认）：17 列，横向滚动 ≈ 2100px，适合「看整体」；
      资料矩阵：在精简列后追加 13 列逐项 √/×，适合「看某一类资料缺在哪几条」。
      矩阵列的 tone 随取值变（已归集=success，未归集=muted），
      颜色之外同时有 √/× 文本，不依赖颜色单独传达信息。

    事件：
      detail        (row)          查看详情
      edit          (row)          编辑
      delete        (row)          删除（父组件负责发请求，本组件只挂二次确认）
      select-change (keys, rows)   行选择变化
  -->
  <screen-data-table
    :columns="columns"
    :data="dataSource"
    :loading="loading"
    row-key="id"
    :min-width="minWidth"
    :selectable="selectable"
    :selected-keys="selectedRowKeys"
    empty-text="没有符合条件的台账记录"
    @select-change="handleSelectChange"
  >
    <!-- 台账编号：点击进详情 -->
    <template #ledgerNo="{ row }">
      <button type="button" class="ledger-table__link" @click.stop="$emit('detail', row)">
        {{ row.ledgerNo || '—' }}
      </button>
    </template>

    <!-- 道路名称：主标题 + 资料/状态矛盾提示（第二行小字，不阻断操作） -->
    <template #roadName="{ row }">
      <span class="ledger-table__name" :title="row.roadName || ''">{{ row.roadName || '—' }}</span>
      <span v-if="warningOf(row)" class="ledger-table__warn">
        <screen-icon name="alert-triangle" :size="12" />
        {{ warningOf(row) }}
      </span>
      <span v-else-if="isOrphan(row)" class="ledger-table__warn">
        <screen-icon name="alert-triangle" :size="12" />
        出让宗地待核对
      </span>
    </template>

    <!-- 行政区划：功能区值不在行政区划里，缺区划时退化显示功能区 -->
    <template #xzqh="{ row }">
      <template v-if="row.xzqh">{{ row.xzqh }}</template>
      <span v-else-if="row.gnq" class="ledger-table__muted">{{ row.gnq }}</span>
      <span v-else class="ledger-table__muted">—</span>
    </template>

    <!-- 资料归集：x/13 + 语气标签（齐全=绿、有缺=橙、一条未归集=灰） -->
    <template #materialCount="{ row }">
      <span class="ledger-table__material">
        <screen-tag :tone="materialTone(countOf(row))" size="sm">
          {{ countOf(row) }}/{{ materialTotal }}
        </screen-tag>
        <span v-if="countOf(row) < materialTotal" class="ledger-table__muted">
          缺 {{ materialTotal - countOf(row) }} 类
        </span>
        <span v-else class="ledger-table__ok">已齐全</span>
      </span>
    </template>

    <!-- 验收结果：字典值，语气按结果给（不合格标红） -->
    <template #acceptanceResult="{ row }">
      <screen-tag v-if="row.acceptanceResult" :tone="acceptanceResultTone(row.acceptanceResult)" size="sm">
        {{ row.acceptanceResult }}
      </screen-tag>
      <span v-else class="ledger-table__muted">—</span>
    </template>

    <!-- 状态：标签 + 编辑入口（大屏不做行内下拉，状态变更在编辑弹窗里统一完成） -->
    <template #status="{ row }">
      <screen-tag :tone="statusTone(row.status)" size="sm">{{ row.status || '未验收' }}</screen-tag>
    </template>

    <!-- 操作列：用插槽而不是内置 actions，因为删除要挂二次确认气泡 -->
    <template #action="{ row }">
      <span class="ledger-table__actions">
        <button type="button" class="ledger-table__link" @click.stop="$emit('detail', row)">详情</button>

        <template v-if="!readonly">
          <button type="button" class="ledger-table__link" @click.stop="$emit('edit', row)">编辑</button>

          <screen-popconfirm
            title="删除后不可恢复，确定删除该台账吗？"
            :description="row.ledgerNo ? `台账编号：${row.ledgerNo}` : ''"
            width="264"
            @confirm="$emit('delete', row)"
          >
            <button type="button" class="ledger-table__link is-danger">删除</button>
          </screen-popconfirm>
        </template>
      </span>
    </template>
  </screen-data-table>
</template>

<script>
import { ScreenDataTable, ScreenPopconfirm, ScreenTag, ScreenIcon } from '@/components/screen'
import {
  MATERIAL_TOTAL,
  acceptanceResultTone,
  countMaterials,
  isOrphan,
  materialTone,
  padSeq,
  statusMaterialWarning,
  statusTone
} from './constants'

/** 精简列的固定列定义（资料矩阵列在 computed 里按资料定义动态追加） */
const BASE_COLUMNS = [
  { key: 'index', title: '序号', width: 64, type: 'index', align: 'center' },
  { key: 'ledgerNo', title: '台账编号', width: 142, type: 'slot' },
  { key: 'roadName', title: '道路名称', width: 268, type: 'slot' },
  { key: 'xzqh', title: '行政区划', width: 96, type: 'slot' },
  { key: 'crzdbh', title: '出让宗地编号', width: 178, ellipsis: true },
  { key: 'dkmc', title: '地块名称', width: 168, ellipsis: true },
  { key: 'ptsslb', title: '设施类别', width: 100 },
  { key: 'acceptanceType', title: '验收类型', width: 110 },
  { key: 'acceptanceDate', title: '验收日期', width: 108 },
  { key: 'acceptanceResult', title: '验收结果', width: 100, type: 'slot' },
  { key: 'completeDate', title: '竣工日期', width: 108 },
  { key: 'handoverDate', title: '移交日期', width: 108 },
  { key: 'receiveUnit', title: '接收管养单位', width: 150, ellipsis: true },
  { key: 'materialCount', title: '资料归集', width: 150, type: 'slot' },
  { key: 'status', title: '状态', width: 96, type: 'slot' },
  { key: 'action', title: '操作', width: 140, type: 'slot', align: 'center' }
]

/** 资料矩阵单列宽（13 列 × 96 ≈ 1248px） */
const MATRIX_COLUMN_WIDTH = 96

export default {
  name: 'LedgerTable',
  components: { ScreenDataTable, ScreenPopconfirm, ScreenTag, ScreenIcon },
  props: {
    /** 行数据 */
    dataSource: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    /** 是否插入 13 列资料矩阵 */
    showMatrix: { type: Boolean, default: false },
    /** 13 类资料定义（矩阵列头来源；缺省用本地兜底） */
    materials: { type: Array, default: () => [] },
    /** 是否显示行选择列 */
    selectable: { type: Boolean, default: false },
    /** 已选行的 id 集合 */
    selectedRowKeys: { type: Array, default: () => [] },
    /** 只读模式：隐藏编辑 / 删除（详情弹窗内嵌时用得上） */
    readonly: { type: Boolean, default: false }
  },
  computed: {
    columns () {
      if (!this.showMatrix) return BASE_COLUMNS
      const matrix = (this.materials || []).map((item) => ({
        key: item.key,
        title: `${padSeq(item.seq)} ${item.label}`,
        width: MATRIX_COLUMN_WIDTH,
        type: 'tag',
        align: 'center',
        // formatter 对 tag 列同样生效：把 tinyint 的 1/0 渲染成 √ / ×
        formatter: (value) => (Number(value) === 1 ? '√' : '×'),
        tone: (value) => (Number(value) === 1 ? 'success' : 'muted')
      }))
      // 矩阵插在「资料归集」列之前，读起来是「先看逐项 √/×、再看汇总」
      const summaryIndex = BASE_COLUMNS.findIndex((col) => col.key === 'materialCount')
      return BASE_COLUMNS.slice(0, summaryIndex).concat(matrix, BASE_COLUMNS.slice(summaryIndex))
    },
    minWidth () {
      const base = BASE_COLUMNS.reduce((sum, col) => sum + (col.width || 100), 0)
      if (!this.showMatrix) return base
      return base + (this.materials || []).length * MATRIX_COLUMN_WIDTH
    },
    materialTotal () {
      return (this.materials && this.materials.length) || MATERIAL_TOTAL
    }
  },
  methods: {
    statusTone,
    acceptanceResultTone,
    materialTone,
    isOrphan,
    /** 已归集资料类数（后端 materialCount 优先，缺失时本地数） */
    countOf (row) {
      return countMaterials(row, this.materials)
    },
    /** 资料与状态的矛盾提示（无问题时返回空串） */
    warningOf (row) {
      return statusMaterialWarning(row, this.materials)
    },
    handleSelectChange (keys, rows) {
      this.$emit('select-change', keys, rows)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.ledger-table {
  &__name {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__warn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-top: 2px;
    font-size: var(--screen-font-xs);
    line-height: 1.4;
    color: var(--screen-warning);

    /deep/ .screen-icon {
      flex: 0 0 auto;
    }
  }

  &__material {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  &__ok {
    font-size: var(--screen-font-xs);
    color: var(--screen-success);
  }

  &__muted {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-3);
    // 三个操作在窄列里允许换行，避免横向溢出
    flex-wrap: wrap;
    justify-content: center;
  }

  &__link {
    .screen-link-action();

    &.is-danger {
      color: var(--screen-danger);

      // 令牌里没有「更深的危险色」，悬停只加下划线强化可点性，不另造颜色
      &:hover {
        text-decoration: underline;
      }
    }
  }
}
</style>
