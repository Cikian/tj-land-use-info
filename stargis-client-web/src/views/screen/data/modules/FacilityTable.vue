<template>
  <!--
    FacilityTable 配套项目列表表格
    --------------------------------
    纯展示组件：不发任何请求，全部交互通过事件抛给父组件。

    ★ 「六阶段」列有两种数据来源，组件都能吃：
      · 从 `/byLand` 来的行带 `stageStatus`（数组，每个阶段一条汇总）——
        这是「按宗地看配套」时的权威口径，直接渲染；
      · 从 `/facilityAdmin/list` 来的行**没有** stageStatus（后端分页返回的是
        Facility 实体本身）—— 此时该列显示「未加载」，而不是假装成「未开启」。
        假装的后患是：用户会以为这六个阶段真的没开始，而实际只是没查。
      需要阶段汇总时点「六阶段」进入 ProcessTreePanel，那里是权威视图。

    事件：
      byLand    (row)  只看该宗地下的全部配套
      detail    (row)  详情
      process   (row)  打开六阶段进度树
      history   (row)  变更履历
      edit      (row)  编辑
      remove    (row)  移除
      attachments (row) 该配套的附件（跳到附件面板）
  -->
  <screen-data-table
    :columns="columns"
    :data="dataSource"
    :loading="loading"
    row-key="id"
    :min-width="1780"
    :selectable="selectable"
    :selected-keys="selectedRowKeys"
    :empty-text="emptyText"
    @select-change="handleSelectChange"
  >
    <template #crzdbh="{ row }">
      <!-- 点编号 = 「只看这一宗地的配套」，这是 1 宗地 N 配套最自然的入口 -->
      <button type="button" class="facility-table__link" @click.stop="$emit('byLand', row)">
        {{ row.crzdbh || '—' }}
      </button>
    </template>
    <!-- 配套项目名称：主标题 + 配套设施类别副行（一行里同时给出「是哪条配套」和「什么类别」） -->
    <template #ptxmmc="{ row }">
      <span class="facility-table__name" :title="row.ptxmmc || ''">{{ row.ptxmmc || '—' }}</span>
      <span v-if="row.ptsslb" class="facility-table__sub">{{ row.ptsslb }}</span>
    </template>

    <template #xzqh="{ row }">
      <span>{{ row.xzqh || '—' }}</span>
    </template>

    <template #xmfl="{ row }">
      <screen-tag :tone="row.xmfl === '市级项目' ? 'success' : 'info'" size="sm">
        {{ row.xmfl || '—' }}
      </screen-tag>
    </template>

    <template #jsdw="{ row }">
      <span class="facility-table__ellipsis" :title="row.jsdw || ''">{{ row.jsdw || '—' }}</span>
    </template>

    <template #tzgs="{ row }">
      <span class="facility-table__num">
        {{ row.tzgs === null || row.tzgs === undefined ? '—' : row.tzgs }}
      </span>
    </template>

    <!-- 整体进度：overdueCount 用红色标记，这是最需要被看见的信息 -->
    <template #percent="{ row }">
      <span class="facility-table__percent">
        <b>{{ progressOf(row) }}%</b>
        <span class="facility-table__track" aria-hidden="true">
          <i class="facility-table__fill" :style="{ width: progressOf(row) + '%' }" />
        </span>
        <span
          v-if="overdueOf(row)"
          class="facility-table__overdue"
          :title="`${overdueOf(row)} 个环节已逾期`"
        >
          <screen-icon name="alert-triangle" :size="12" />
          逾期 {{ overdueOf(row) }}
        </span>
      </span>
    </template>

    <template #stageStatus="{ row }">
      <facility-stage-chips
        v-if="(row.stageStatus || []).length"
        :stages="row.stageStatus"
        compact
      />
      <button
        v-else
        type="button"
        class="facility-table__link is-muted"
        title="该视图按分页返回，不含阶段汇总；点这里打开权威的六阶段视图"
        @click.stop="$emit('process', row)"
      >
        点开六阶段
      </button>
    </template>

    <template #action="{ row }">
      <span class="facility-table__actions">
        <button type="button" class="facility-table__link" @click.stop="$emit('process', row)">六阶段</button>
        <button type="button" class="facility-table__link" @click.stop="$emit('detail', row)">详情</button>
        <button type="button" class="facility-table__link" @click.stop="$emit('history', row)">履历</button>
        <button type="button" class="facility-table__link" @click.stop="$emit('attachments', row)">附件</button>

        <template v-if="!readonly">
          <button type="button" class="facility-table__link" @click.stop="$emit('edit', row)">编辑</button>

          <screen-popconfirm
            title="移除后可在「数据更新与移除」里恢复，确定移除该配套项目吗？"
            :description="row.ptxmmc ? `配套项目：${row.ptxmmc}` : ''"
            width="300"
            @confirm="$emit('remove', row)"
          >
            <button type="button" class="facility-table__link is-danger">移除</button>
          </screen-popconfirm>
        </template>
      </span>
    </template>
  </screen-data-table>
</template>

<script>
import { ScreenDataTable, ScreenTag, ScreenIcon, ScreenPopconfirm } from '@/components/screen'
import FacilityStageChips from './FacilityStageChips.vue'

export default {
  name: 'FacilityTable',
  components: {
    ScreenDataTable,
    ScreenTag,
    ScreenIcon,
    ScreenPopconfirm,
    FacilityStageChips
  },
  props: {
    dataSource: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    selectable: { type: Boolean, default: false },
    selectedRowKeys: { type: Array, default: () => [] },
    /** 只读模式：隐藏编辑/移除 */
    readonly: { type: Boolean, default: false },
    emptyText: { type: String, default: '暂无配套项目' }
  },
  data () {
    return {
      columns: [
        { key: 'crzdbh', title: '出让宗地编号', width: 190, type: 'slot' },
        { key: 'ptxmmc', title: '配套项目 / 类别', width: 250, type: 'slot' },
        { key: 'xzqh', title: '行政区划', width: 100, type: 'slot' },
        { key: 'xmfl', title: '项目分类', width: 110, type: 'slot', align: 'center' },
        { key: 'jsdw', title: '建设单位', width: 190, type: 'slot' },
        { key: 'tzgs', title: '投资估算(万元)', width: 130, type: 'slot', align: 'right' },
        { key: 'percent', title: '整体进度', width: 200, type: 'slot' },
        { key: 'stageStatus', title: '六大阶段', width: 380, type: 'slot' },
        { key: 'action', title: '操作', width: 260, type: 'slot', align: 'center' }
      ]
    }
  },
  methods: {
    /**
     * 整体完成度。
     * ★ 优先级：processStat.percent（后端唯一口径）→ 阶段 percent 的均值（近似，仅供排序参考）。
     *   两者都拿不到时返回 0 并让调用方看到「0%」，因为这里**没有任何可以推算的原料**；
     *   不写「—」是因为进度列的轨宽需要数字，写文字会把轨道挤没。
     */
    progressOf (row) {
      if (row && row.processStat && row.processStat.percent !== undefined && row.processStat.percent !== null) {
        return Number(row.processStat.percent) || 0
      }
      const stages = (row && row.stageStatus) || []
      if (!stages.length) return 0
      const sum = stages.reduce((total, item) => total + (Number(item.percent) || 0), 0)
      return Math.round(sum / stages.length)
    },

    overdueOf (row) {
      if (row && row.processStat && row.processStat.overdue) {
        return Number(row.processStat.overdue) || 0
      }
      const stages = (row && row.stageStatus) || []
      return stages.reduce((total, item) => total + (Number(item.overdueCount) || 0), 0)
    },

    handleSelectChange (keys, rows) {
      this.$emit('select-change', keys, rows)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.facility-table {
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

  &__ellipsis {
    display: block;
    .screen-ellipsis();
  }

  &__num {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);
  }

  &__percent {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;

    b {
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-sm);
      color: var(--screen-accent);
    }
  }

  &__track {
    display: block;
    height: 4px;
    background: var(--screen-bar-track);
    border-radius: var(--screen-radius-pill);
    overflow: hidden;
  }

  &__fill {
    display: block;
    height: 100%;
    min-width: 2px;
    // 进度条是纯装饰，真实数值由上面的 b 承担
    background-image: linear-gradient(90deg, var(--screen-bar-from) 0%, var(--screen-bar-to) 100%);
    border-radius: var(--screen-radius-pill);
    transition: width var(--screen-duration) var(--screen-ease);
  }

  &__overdue {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    font-size: var(--screen-font-xs);
    color: var(--screen-danger);
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
    // 六个操作在窄列里一定会换行，允许换行避免横向溢出
    flex-wrap: wrap;
    justify-content: center;
  }

  &__link {
    .screen-link-action();
  }
}

@media (prefers-reduced-motion: reduce) {
  .facility-table__fill {
    transition: none;
  }
}
</style>
