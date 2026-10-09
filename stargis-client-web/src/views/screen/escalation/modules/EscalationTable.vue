<template>
  <!--
    EscalationTable 提级论证项目表格
    --------------------------------
    「项目录入 / 查询统计 / 资料及台账管理」三处共用同一个表格，差别只有列集与操作：

      mode = 'default'  录入页与查询统计页的 10 列（设计文档 5.5「列表列」）
      mode = 'ledger'   台账页的 14 列（设计文档 5.6：序号/项目编号/项目名称 + … + 操作）
      readonly = true   查询统计页：隐藏编辑与删除
      selectable         录入页：批量删除用

    这是一个**纯展示**组件：不发任何请求，全部交互通过事件抛给父组件，
    这样三个页面才能零成本复用。

    事件：
      detail        (row)         查看详情
      edit          (row)         编辑
      delete        (row)         删除（父组件负责发请求）
      materials     (row)         台账的「材料下载」：直达详情的材料页签
      print         (row)         台账的「打印」：打印单项
      select-change (keys, rows)  行选择变化

    两处「不靠颜色」的坚持（README §5）：
      · 「已办结但论证结果为空」既有 --screen-warning 的图标，也有**文字**「未登记论证结果」，
        并且标题里给出原因，色盲用户与读屏用户都能拿到同样的信息；
      · 论证结果为空统一显示文字「未登记」，而不是留一个空单元格。
  -->
  <screen-data-table
    :columns="columns"
    :data="dataSource"
    :loading="loading"
    row-key="id"
    :min-width="mode === 'ledger' ? 2180 : 1560"
    :selectable="selectable"
    :selected-keys="selectedRowKeys"
    :empty-text="emptyText"
    @select-change="handleSelectChange"
  >
    <!--
      项目编号：点它等价于点「详情」。
      ★ 刻意用 type='slot' + 自有按钮而不是组件内置的 type='link'：
        README §3 与硬性规则 5 明确列出的列 type 只有
        text / number / percent / action / index / tag / slot，
        link / actions 虽然在组件源码里存在，但不在约定清单内，这里不越界使用。
    -->
    <template #projectNo="{ row }">
      <button type="button" class="escalation-table__no" :title="row.projectNo" @click.stop="$emit('detail', row)">
        {{ row.projectNo || '—' }}
      </button>
    </template>

    <!-- 项目名称：主标题 + 台账模式下的建设地点副行 -->
    <template #projectName="{ row }">
      <span class="escalation-table__name" :title="row.projectName">{{ row.projectName || '—' }}</span>
      <span v-if="mode === 'ledger' && row.buildLocation" class="escalation-table__sub">
        建设地点：{{ row.buildLocation }}
      </span>
    </template>

    <!-- 行政区划：后端台账里 xzqh 可能为空，退回功能区 -->
    <template #xzqh="{ row }">
      <span class="escalation-table__ellipsis" :title="row.xzqh || row.gnq || ''">
        {{ row.xzqh || row.gnq || '—' }}
      </span>
    </template>

    <!-- 项目类型：后端 @Dict 给的 _dictText 优先 -->
    <template #projectType="{ row }">
      {{ projectTypeText(row) || '—' }}
    </template>

    <template #totalInvestment="{ row }">
      <span v-if="row.totalInvestment === null || row.totalInvestment === undefined" class="escalation-table__muted">—</span>
      <span v-else class="escalation-table__number">{{ formatInvestment(row.totalInvestment) }}</span>
    </template>

    <!-- 办理状态：标签 + 「已办结但未登记论证结果」的文字提示 -->
    <template #status="{ row }">
      <span class="escalation-table__status">
        <screen-tag :tone="statusTone(row.status)" size="sm">{{ row.status || '未办理' }}</screen-tag>
        <span
          v-if="isClosedWithoutResult(row)"
          class="escalation-table__warn"
          title="办理状态为已办结，但论证结果还没有登记"
        >
          <screen-icon name="alert-triangle" :size="12" />
          未登记论证结果
        </span>
      </span>
    </template>

    <!-- 论证结果：与办理状态是两个独立维度，各自一个标签 -->
    <template #argResult="{ row }">
      <screen-tag v-if="row.argResult" :tone="argResultTone(row.argResult)" size="sm">
        {{ argResultText(row) }}
      </screen-tag>
      <span v-else class="escalation-table__muted">未登记</span>
    </template>

    <!-- 最新审核意见摘要：正文 + 登记人 / 时间副行 -->
    <template #latestOpinion="{ row }">
      <span v-if="row.latestOpinion" class="escalation-table__ellipsis" :title="row.latestOpinion">
        {{ opinionSummary(row.latestOpinion, opinionLimit) }}
      </span>
      <span v-else class="escalation-table__muted">暂无</span>
      <span
        v-if="row.latestOpinionBy || row.latestOpinionTime"
        class="escalation-table__sub"
      >
        {{ joinInfo(row.latestOpinionBy, row.latestOpinionTime, ' · ', '') }}
      </span>
    </template>

    <template #updateTime="{ row }">
      {{ row.updateTime || row.createTime || '—' }}
    </template>

    <!-- 操作列：用插槽而不是内置 actions，因为删除要挂二次确认气泡 -->
    <template #action="{ row }">
      <span class="escalation-table__actions">
        <template v-if="mode === 'ledger'">
          <button type="button" class="escalation-table__link" @click.stop="$emit('detail', row)">查看</button>
          <button type="button" class="escalation-table__link" @click.stop="$emit('materials', row)">材料下载</button>
          <button type="button" class="escalation-table__link" @click.stop="$emit('print', row)">打印</button>
        </template>

        <template v-else>
          <button type="button" class="escalation-table__link" @click.stop="$emit('detail', row)">详情</button>

          <template v-if="!readonly">
            <button type="button" class="escalation-table__link" @click.stop="$emit('edit', row)">编辑</button>

            <screen-popconfirm
              title="删除后不可恢复，确定删除该项目吗？"
              :description="row.projectNo ? `项目编号：${row.projectNo}` : ''"
              width="264"
              @confirm="$emit('delete', row)"
            >
              <button type="button" class="escalation-table__link is-danger">删除</button>
            </screen-popconfirm>
          </template>
        </template>
      </span>
    </template>
  </screen-data-table>
</template>

<script>
import { ScreenDataTable, ScreenTag, ScreenIcon, ScreenPopconfirm } from '@/components/screen'
import {
  dictText,
  DICT,
  statusTone,
  argResultTone,
  formatInvestment,
  opinionSummary,
  joinInfo,
  isClosedWithoutResult,
} from '../constants'

export default {
  name: 'EscalationTable',
  components: { ScreenDataTable, ScreenTag, ScreenIcon, ScreenPopconfirm },
  props: {
    /** 行数据 */
    dataSource: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    /** 是否显示行选择列（录入页批量删除） */
    selectable: { type: Boolean, default: false },
    /** 已选行的 id 集合 */
    selectedRowKeys: { type: Array, default: () => [] },
    /** 只读模式（查询统计页）：隐藏编辑与删除 */
    readonly: { type: Boolean, default: false },
    /** 'default' 10 列列表 | 'ledger' 14 列台账 */
    mode: { type: String, default: 'default' },
    /** 台账「最新审核意见」列的截断长度 */
    opinionLimit: { type: Number, default: 40 },
    emptyText: { type: String, default: '暂无提级论证项目' },
  },
  data () {
    return {
      columns: [],
    }
  },
  watch: {
    mode: {
      immediate: true,
      handler () {
        this.columns = this.mode === 'ledger' ? this.buildLedgerColumns() : this.buildDefaultColumns()
      },
    },
  },
  methods: {
    statusTone,
    argResultTone,
    formatInvestment,
    opinionSummary,
    joinInfo,
    isClosedWithoutResult,

    /** 项目类型 / 论证结果文本：后端 _dictText 优先，其次本模块固化的字典表 */
    projectTypeText (row) {
      return dictText(DICT.projectType, row.projectType, row.projectType_dictText)
    },
    argResultText (row) {
      return dictText(DICT.argResult, row.argResult, row.argResult_dictText)
    },

    /** 台账 14 列（设计文档 5.6，前 3 列是识别列，窄屏时横向滚动仍能定位） */
    buildLedgerColumns () {
      return [
        { key: 'index', title: '序号', width: 56, type: 'index', align: 'center' },
        { key: 'projectNo', title: '项目编号', width: 150, type: 'slot' },
        { key: 'projectName', title: '项目名称', width: 240, type: 'slot' },
        { key: 'declareDept', title: '申报单位', width: 200, ellipsis: true },
        { key: 'xzqh', title: '行政区划', width: 100, type: 'slot' },
        { key: 'projectType', title: '项目类型', width: 120, type: 'slot' },
        { key: 'totalInvestment', title: '总投资（亿元）', width: 120, type: 'slot', align: 'right' },
        { key: 'declareDate', title: '申报时间', width: 110 },
        { key: 'materialCount', title: '材料数', width: 80, type: 'number', align: 'right' },
        { key: 'status', title: '办理状态', width: 140, type: 'slot' },
        { key: 'argResult', title: '论证结果', width: 130, type: 'slot' },
        { key: 'latestOpinion', title: '最新审核意见', width: 260, type: 'slot' },
        { key: 'updateTime', title: '更新时间', width: 160, type: 'slot' },
        { key: 'action', title: '操作', width: 190, type: 'slot', align: 'center' },
      ]
    },

    /** 录入 / 查询的 10 列 */
    buildDefaultColumns () {
      return [
        { key: 'projectNo', title: '项目编号', width: 150, type: 'slot' },
        { key: 'projectName', title: '项目名称', width: 240, type: 'slot' },
        { key: 'declareDept', title: '申报单位', width: 190, ellipsis: true },
        { key: 'projectType', title: '项目类型', width: 120, type: 'slot' },
        { key: 'totalInvestment', title: '总投资（亿元）', width: 120, type: 'slot', align: 'right' },
        { key: 'declareDate', title: '申报时间', width: 110 },
        { key: 'status', title: '办理状态', width: 150, type: 'slot' },
        { key: 'argResult', title: '论证结果', width: 130, type: 'slot' },
        { key: 'materialCount', title: '材料数', width: 80, type: 'number', align: 'right' },
        { key: 'action', title: '操作', width: 170, type: 'slot', align: 'center' },
      ]
    },

    handleSelectChange (keys, rows) {
      this.$emit('select-change', keys, rows)
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.escalation-table {
  /* 项目编号：等宽数字 + 文字链观感，与表格操作列的链接同一套语言 */
  &__no {
    .screen-link-action();
    font-size: var(--screen-font-sm);
    font-variant-numeric: tabular-nums;
    letter-spacing: 0.02em;
  }

  &__name {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__sub {
    display: block;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  &__ellipsis {
    display: block;
    .screen-ellipsis();
  }

  &__number {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);
  }

  &__muted {
    color: var(--screen-text-mute);
  }

  &__status {
    display: inline-flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-2);
  }

  /* 「已办结但未登记论证结果」：图标 + 文字，颜色只是辅助线索 */
  &__warn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: var(--screen-font-xs);
    color: var(--screen-warning);
    white-space: nowrap;
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
