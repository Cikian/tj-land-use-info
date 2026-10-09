<template>
  <!--
    HandoverTable 道路交付及养护协议移交事项 · 列表表格
    --------------------------------
    纯展示组件：不发任何请求，全部交互通过事件抛给父组件（面板）。

    列（清单 §6.3.6 的结果列）：序号 / 移交编号 / 道路名称 / 道路等级 / 行政区划 /
    出让宗地编号 / 移交类型 / 协议编号与日期 / 接收管养单位 / 养护期 / 状态 / 操作。

    ★ 两处**非阻断**的业务提示（黄色小字，只提示不拦操作）：
      1.「协议信息待补录」= 协议编号与协议签订日期都为空。
         迁移来的 84 条全是这样（旧库只有「是否移交=是」一个标志位），
         不提示的话这批数据会一直「看起来已移交、实际没有任何协议凭证」；
      2.「养护期已过期 / 90 天内到期」= 按养护截止日期与今天比较得出。
    为什么不做成硬校验：验收与移交都是**线下办完才回系统登记**，
    扫描件与协议往往滞后于状态（现实里常见「先移交、后补资料」），
    拦住不让保存只会让人绕过系统去用 Excel。

    事件：
      detail        (row)          查看详情
      edit          (row)          编辑
      delete        (row)          删除（父组件负责发请求）
      status-change (row, status)  变更状态（状态列里直接改）
  -->
  <screen-data-table
    :columns="columns"
    :data="dataSource"
    :loading="loading"
    row-key="id"
    :min-width="1792"
    :empty-text="emptyText"
  >
    <!-- 移交编号：链接进详情 -->
    <template #handoverNo="{ row }">
      <button type="button" class="handover-table__link is-strong" @click.stop="$emit('detail', row)">
        {{ row.handoverNo || '—' }}
      </button>
    </template>

    <!-- 道路名称：名称 + 两条业务提示（最多显示一条，避免行高翻倍） -->
    <template #roadName="{ row }">
      <span class="handover-table__name" :title="row.roadName || ''">{{ row.roadName || '—' }}</span>
      <span v-if="missingAgreement(row)" class="handover-table__warn">
        <screen-icon name="alert-triangle" :size="11" />
        {{ MISSING_AGREEMENT_TEXT }}
      </span>
      <span v-else-if="orphanLand(row)" class="handover-table__warn">
        <screen-icon name="alert-triangle" :size="11" />
        {{ ORPHAN_LAND_TEXT }}
      </span>
    </template>

    <!-- 行政区划：功能区数据（原旧库混在区划列里）降级显示为灰色 -->
    <template #xzqh="{ row }">
      <span v-if="row.xzqh">{{ row.xzqh }}</span>
      <span v-else-if="row.gnq" class="handover-table__mute">{{ row.gnq }}</span>
      <span v-else class="handover-table__mute">—</span>
    </template>

    <!-- 移交类型 -->
    <template #handoverType="{ row }">
      <screen-tag v-if="row.handoverType" tone="muted" size="sm">{{ row.handoverType }}</screen-tag>
      <span v-else class="handover-table__mute">—</span>
    </template>

    <!-- 协议编号 / 签订日期 -->
    <template #agreement="{ row }">
      <span v-if="row.agreementNo" class="handover-table__strong">{{ row.agreementNo }}</span>
      <span v-else class="handover-table__warn-text">待补录</span>
      <span v-if="row.agreementDate" class="handover-table__mute">{{ row.agreementDate }}</span>
    </template>

    <!-- 养护期：起止 + 到期提示 -->
    <template #maintenance="{ row }">
      <span v-if="maintenanceRangeText(row)">{{ maintenanceRangeText(row) }}</span>
      <span v-else class="handover-table__mute">—</span>
      <span v-if="maintenanceWarning(row)" class="handover-table__warn">
        <screen-icon name="clock" :size="11" />
        {{ maintenanceWarning(row) }}
      </span>
    </template>

    <!-- 状态：圆点（辅助线索）+ 3 值下拉（直接改，改完由父组件调 /status） -->
    <template #status="{ row }">
      <span class="handover-table__status">
        <i class="handover-table__dot" :class="statusDotClass(row.status)" aria-hidden="true" />
        <screen-select
          :value="row.status || '待移交'"
          :options="statusOptions"
          :clearable="false"
          size="sm"
          :aria-label="`变更「${row.roadName || ''}」的移交状态`"
          @change="(value) => handleStatusChange(row, value)"
        />
      </span>
    </template>

    <!-- 操作 -->
    <template #action="{ row }">
      <span class="handover-table__actions">
        <button type="button" class="handover-table__link" @click.stop="$emit('detail', row)">详情</button>
        <button type="button" class="handover-table__link" @click.stop="$emit('edit', row)">编辑</button>
        <screen-popconfirm
          title="删除后不可恢复，确定删除该移交事项吗？"
          :description="row.handoverNo ? `移交编号：${row.handoverNo}` : ''"
          width="264"
          @confirm="$emit('delete', row)"
        >
          <button type="button" class="handover-table__link is-danger">删除</button>
        </screen-popconfirm>
      </span>
    </template>
  </screen-data-table>
</template>

<script>
import { ScreenDataTable, ScreenSelect, ScreenTag, ScreenIcon, ScreenPopconfirm } from '@/components/screen'
import {
  MISSING_AGREEMENT_TEXT,
  ORPHAN_LAND_TEXT,
  handoverStatusOptions,
  maintenanceRangeText,
  maintenanceWarning,
  missingAgreement,
  orphanLand,
  statusDotClass
} from './constants'

export default {
  name: 'HandoverTable',
  components: { ScreenDataTable, ScreenSelect, ScreenTag, ScreenIcon, ScreenPopconfirm },
  props: {
    /** 行数据 */
    dataSource: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    emptyText: { type: String, default: '没有符合条件的移交事项' }
  },
  data () {
    return {
      MISSING_AGREEMENT_TEXT,
      ORPHAN_LAND_TEXT,
      statusOptions: handoverStatusOptions(),
      columns: [
        { key: 'index', title: '序号', width: 56, type: 'index', align: 'center' },
        { key: 'handoverNo', title: '移交编号', width: 148, type: 'slot' },
        { key: 'roadName', title: '道路名称', width: 300, type: 'slot' },
        { key: 'dldj', title: '道路等级', width: 108 },
        { key: 'xzqh', title: '行政区划', width: 92, type: 'slot' },
        { key: 'crzdbh', title: '出让宗地编号', width: 170, ellipsis: true },
        { key: 'handoverType', title: '移交类型', width: 100, type: 'slot' },
        { key: 'agreement', title: '协议编号 / 日期', width: 160, type: 'slot' },
        { key: 'receiveUnit', title: '接收管养单位', width: 168, ellipsis: true },
        { key: 'maintenance', title: '养护期', width: 190, type: 'slot' },
        { key: 'status', title: '状态', width: 150, type: 'slot' },
        { key: 'action', title: '操作', width: 150, type: 'slot', align: 'center' }
      ]
    }
  },
  methods: {
    maintenanceWarning,
    maintenanceRangeText,
    missingAgreement,
    orphanLand,
    statusDotClass,
    handleStatusChange (row, status) {
      if (!row || !status || status === row.status) return
      this.$emit('status-change', row, status)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.handover-table {
  &__name {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__strong {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__mute {
    display: block;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  &__warn {
    display: flex;
    align-items: center;
    gap: 3px;
    margin-top: 2px;
    font-size: var(--screen-font-xs);
    line-height: 1.4;
    // 预警橙（语义令牌，与首页「未移交」同一套语义）
    color: var(--screen-warning);

    /deep/ .screen-icon {
      flex: 0 0 auto;
    }
  }

  &__warn-text {
    display: block;
    font-size: var(--screen-font-xs);
    color: var(--screen-warning);
  }

  &__status {
    display: flex;
    align-items: center;
    gap: 6px;

    /deep/ .screen-select {
      flex: 1 1 auto;
      min-width: 0;
    }
  }

  // 状态圆点：颜色只是辅助线索，下拉里的文字才是主线索
  &__dot {
    flex: 0 0 auto;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--screen-text-mute);

    &.is-done {
      background: var(--screen-success);
    }

    &.is-doing {
      background: var(--screen-warning);
    }

    &.is-waiting {
      background: var(--screen-text-sub);
    }
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: var(--screen-space-3);
    // 三个操作在窄列里换行也比横向溢出好
    flex-wrap: wrap;
  }

  &__link {
    .screen-link-action();

    &.is-strong {
      font-family: var(--screen-font-number-family);
      letter-spacing: 0.4px;
      color: var(--screen-accent);
    }

    &.is-danger {
      color: var(--screen-danger);

      &:hover {
        opacity: 0.85;
      }
    }
  }
}
</style>
