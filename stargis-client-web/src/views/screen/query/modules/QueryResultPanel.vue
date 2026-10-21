<template>
  <!--
    QueryResultPanel 查询结果表（查询统计三个页签共用）
    --------------------------------
    由三块组成：
      工具条  「共 N 条」+ 自定义列开关 + 导出 + 刷新
      列选择  展开后在工具条下方铺一排复选框（不弹浮层，见下面说明）
      表格    列由后端返回的 QueryColumnVO[] 生成，行是 { key: value } 的 Map

    ★ 为什么列选择是「就地展开」而不是浮层下拉：
      大屏是一整块定尺画布，浮层会挂到 body 上、脱离画布的缩放换算；
      而这一页的列有 30+ 个，浮层里要能滚动、要能点多选、还不能吃掉画布事件。
      就地展开一行复选框，既没有这些坑，键盘用户也能直接 Tab 过去。

    ★ 为什么列与行来自同一个响应：
      后端把「这 5 列是什么」和「这 5 列的值」一起返回（见 QueryPageVO 注释），
      列与数据天然对齐；旧系统是两次请求，改了自定义列之后表格会错位。
  -->
  <screen-panel class="query-result" :title="title" scrollable>
    <template #extra>
      <span class="query-result__total">
        共 <b>{{ total }}</b> 条
        <template v-if="selectedKeys.length">
          · 显示 <b class="is-accent">{{ selectedKeys.length }}</b> / {{ columns.length }} 列
        </template>
      </span>

      <screen-button size="sm" icon="grid" @click="togglePicker">
        {{ pickerOpen ? '收起列设置' : '自定义列' }}
      </screen-button>

      <screen-button size="sm" icon="download" :disabled="!total" @click="$emit('export')">
        导出 Excel
      </screen-button>

      <screen-button size="sm" icon="reload" :loading="loading" @click="$emit('refresh')">
        刷新
      </screen-button>
    </template>

    <!-- 自定义列：就地展开 -->
    <div v-if="pickerOpen" class="query-result__picker">
      <div class="query-result__picker-head">
        <span>勾选要显示的列（顺序与后端字段定义一致）</span>
        <button type="button" class="query-result__link" @click="$emit('reset-columns')">
          恢复默认列
        </button>
      </div>
      <div class="query-result__picker-grid">
        <label
          v-for="column in columns"
          :key="column.key"
          class="query-result__check"
          :class="{ 'is-checked': selectedKeys.indexOf(column.key) > -1 }"
        >
          <input
            type="checkbox"
            :checked="selectedKeys.indexOf(column.key) > -1"
            @change="$emit('toggle-column', column.key)"
          >
          <span>{{ column.label }}<em v-if="column.unit">（{{ column.unit }}）</em></span>
        </label>
      </div>
    </div>

    <screen-data-table
      :columns="tableColumns"
      :data="rows"
      :row-key="rowKey"
      :loading="loading"
      :min-width="minWidth"
      :empty-text="emptyText"
    >
      <!--
        编号列做成可点链接（经营性用地点开是「地块配套详情」）。
        ★ 只给 crzdbh / ptxmmc 写两个静态插槽，而不是用 `v-slot:[column]` 动态插槽：
          Vue 2.6 里「动态插槽名 + v-for」组合的编译行为不稳定，
          而这里的可点列本来就是固定的（宗地点编号、配套点名称）。
      -->
      <template #crzdbh="{ row }">
        <button
          type="button"
          class="query-result__link"
          :title="row.crzdbh || ''"
          @click.stop="$emit('open-detail', row)"
        >
          {{ row.crzdbh || '—' }}
        </button>
      </template>

      <!-- 配套数据集：点配套项目名称看该配套的详情（环节进展 / 时间节点 / 责任单位） -->
      <template #ptxmmc="{ row }">
        <button
          type="button"
          class="query-result__link"
          :title="row.ptxmmc || ''"
          @click.stop="$emit('open-detail', row)"
        >
          {{ row.ptxmmc || '（未填名称）' }}
        </button>
      </template>
    </screen-data-table>

    <template #footer>
      <screen-pagination
        :current="pageNo"
        :page-size="pageSize"
        :total="total"
        :page-size-options="pageSizeOptions"
        @change="$emit('page-change', $event)"
      />
    </template>
  </screen-panel>
</template>

<script>
import {
  ScreenButton,
  ScreenDataTable,
  ScreenPanel,
  ScreenPagination
} from '@/components/screen'

export default {
  name: 'QueryResultPanel',
  components: { ScreenButton, ScreenDataTable, ScreenPanel, ScreenPagination },
  props: {
    title: { type: String, default: '查询结果' },
    /** 后端 QueryColumnVO[]（全部可选列） */
    columns: { type: Array, default: () => [] },
    /** 当前选中显示的列 key */
    selectedKeys: { type: Array, default: () => [] },
    rows: { type: Array, default: () => [] },
    total: { type: Number, default: 0 },
    loading: { type: Boolean, default: false },
    pageNo: { type: Number, default: 1 },
    pageSize: { type: Number, default: 20 },
    pageSizeOptions: { type: Array, default: () => [20, 50, 100] },
    /** 哪些列做成可点链接（触发 open-detail） */
    linkKeys: { type: Array, default: () => [] },
    emptyText: { type: String, default: '没有符合条件的记录，试试放宽查询条件' }
  },
  data () {
    return { pickerOpen: false }
  },
  computed: {
    /** 本次真正展示的列（顺序按 selectedKeys，保证与勾选顺序一致） */
    visibleColumns () {
      const map = {}
      this.columns.forEach((column) => { map[column.key] = column })
      return this.selectedKeys
        .map((key) => map[key])
        .filter(Boolean)
    },
    tableColumns () {
      return this.visibleColumns.map((column) => {
        const isLink = this.linkKeys.indexOf(column.key) > -1
        return {
          key: column.key,
          title: column.unit ? `${column.label}（${column.unit}）` : column.label,
          width: this.columnWidth(column),
          // slot 只给可点列用（插槽见模板里的 #crzdbh）；数字列交给表格自己格式化
          type: isLink ? 'slot' : (column.type === 'NUMBER' ? 'number' : 'text'),
          precision: column.key === 'nrcbdptf' ? 3 : 2,
          align: column.type === 'NUMBER' ? 'right' : 'left',
          ellipsis: column.type === 'TEXT'
        }
      })
    },
    minWidth () {
      return this.tableColumns.reduce((sum, column) => sum + column.width, 0)
    },
    /** 行的唯一 key：宗地/配套都有 id；没有 id 时退回编号 + 行号（由后端保证 id 存在） */
    rowKey () {
      return 'id'
    }
  },
  methods: {
    togglePicker () {
      this.pickerOpen = !this.pickerOpen
    },
    /** 列宽：编号/名称/日期/数字各一档，够看又不至于把表格撑到需要横向滚两屏 */
    columnWidth (column) {
      if (column.key === 'crzdbh') return 170
      if (column.key === 'dkmc' || column.key === 'ptxmmc' || column.key === 'srr' || column.key === 'lpmc') return 200
      if (column.type === 'DATE') return 120
      if (column.type === 'NUMBER') return 130
      return 150
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.query-result {
  &__total {
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);

    b {
      color: var(--screen-text);
      font-weight: 600;
    }

    .is-accent {
      color: var(--screen-accent);
    }
  }

  &__link {
    padding: 0;
    font: inherit;
    color: var(--screen-accent);
    background: none;
    border: 0;
    cursor: pointer;
    text-decoration: underline dotted;
    .screen-focus-ring();

    &:hover {
      color: var(--screen-text);
    }
  }

  &__cell {
    display: block;
    .screen-ellipsis();

    &.is-number {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
  }

  /* ---------- 自定义列（就地展开） ---------- */
  &__picker {
    margin-bottom: 10px;
    padding: 10px 12px;
    background: var(--screen-elevate);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);
  }

  &__picker-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__picker-grid {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 6px 12px;
    max-height: 132px;
    overflow-y: auto;

    @media (max-width: 1500px) {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  &__check {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
    cursor: pointer;

    input {
      flex: 0 0 auto;
      accent-color: var(--screen-accent);
    }

    span {
      .screen-ellipsis();
    }

    em {
      font-style: normal;
      font-size: var(--screen-font-xs);
      color: var(--screen-text-mute);
    }

    &.is-checked {
      color: var(--screen-text);
    }
  }
}
</style>
