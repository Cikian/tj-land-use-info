<template>
  <!--
    WarningResultPanel 预警信息查询 · 结果表
    ===============================================================
    列与契约 §5.1 / §5.5 的表头一一对应：
      预警分类 / 预警等级 / 预警名称 / 配套项目 / 行政区划 / 地块名称 /
      流程环节 / 预警值 / 逾期天数 / 责任部门 / 计算时间 / 操作

    ★ 等级列走 `type: 'slot'` 自己渲染，而不是 `type: 'tag'`：
      ScreenTag 的语气只有 accent/success/warning/danger/info/muted，
      套到「蓝/黄/橙/红」四档上会把黄色和橙色都变成同一个 warning 色，
      等级就分不出来了。这里按 levelCode 上 `--screen-level-*` 令牌（见 level-tones.less）。

    ★ 预警值只拼百分号：后端 `yjPercent` **已经乘过 100**（契约 §3.5），
      前端再乘一次就会把 110% 显示成 11000%。

    ★ 列定义写在 computed 里而不是 data：formatter 是函数，放 data 会被 Vue 变成响应式，
      表格每行每次渲染都要多一层依赖收集，行数多了会拖慢翻页。
  -->
  <screen-panel class="warning-result" title="预警信息" scrollable>
    <template #extra>
      <span class="warning-result__total">共 <b>{{ total }}</b> 条</span>
      <screen-button size="sm" icon="reload" :loading="loading" @click="$emit('refresh')">
        刷新
      </screen-button>
    </template>

    <screen-data-table
      :columns="columns"
      :data="rows"
      row-key="id"
      :loading="loading"
      :min-width="minWidth"
      empty-text="没有符合条件的预警记录，试试放宽查询条件（或先点「重新计算」）"
      @action="handleAction"
    >
      <!-- 预警等级：按 levelCode 着色（四档令牌） -->
      <template #levelName="{ row }">
        <span class="warning-result__level" :class="levelToneClass(row.levelCode)">
          {{ row.levelName || '—' }}
        </span>
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
import { levelToneClass, percentText } from './constants'

export default {
  name: 'WarningResultPanel',
  components: { ScreenButton, ScreenDataTable, ScreenPanel, ScreenPagination },
  props: {
    /** 当前页预警行（WarningVO[]） */
    rows: { type: Array, default: () => [] },
    total: { type: Number, default: 0 },
    loading: { type: Boolean, default: false },
    pageNo: { type: Number, default: 1 },
    pageSize: { type: Number, default: 10 },
    pageSizeOptions: { type: Array, default: () => [10, 20, 50, 100] }
  },
  computed: {
    columns () {
      return [
        { key: 'yjType', title: '预警分类', width: 110 },
        { key: 'levelName', title: '预警等级', width: 110, type: 'slot' },
        { key: 'yjName', title: '预警名称', width: 230 },
        { key: 'ptxmmc', title: '配套项目', width: 200 },
        { key: 'xzqh', title: '行政区划', width: 110 },
        { key: 'dkmc', title: '地块名称', width: 180 },
        { key: 'lcName', title: '流程环节', width: 150 },
        {
          key: 'yjPercent',
          title: '预警值',
          width: 100,
          align: 'right',
          formatter: percentText
        },
        { key: 'overdueDays', title: '逾期天数', width: 100, type: 'number', align: 'right' },
        { key: 'zrbm', title: '责任部门', width: 170 },
        { key: 'calcTime', title: '计算时间', width: 175 },
        { key: 'action', title: '操作', width: 80, type: 'action', actionText: '查看', align: 'center' }
      ]
    },
    minWidth () {
      return this.columns.reduce((sum, column) => sum + column.width, 0)
    }
  },
  methods: {
    levelToneClass,
    /** 操作列点击：把整行交给详情弹窗（详情**不请求接口**，见契约 §6.1） */
    handleAction (row) {
      this.$emit('open-detail', row)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';
@import './level-tones.less';

.warning-result {
  &__total {
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);

    b {
      color: var(--screen-text);
      font-weight: 600;
    }
  }

  &__level {
    font-weight: 600;
    .warning-level-tones();
  }
}
</style>
