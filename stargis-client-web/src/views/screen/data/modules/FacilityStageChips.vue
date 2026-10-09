<template>
  <!--
    FacilityStageChips 六大阶段进度标签组
    --------------------------------
    「1 宗地 N 配套」列表里每个配套一行，一行要同时表达：
      这个配套现在办到哪一步了 —— 六个阶段各自的汇总状态 + 完成度。

    为什么不用一整行六个进度条：六个进度条并排会互相争夺注意力，
    而用户真正想知道的是「哪个阶段卡住了」。所以：
      · 主视觉是**状态标签 + 百分比文字**（颜色不是唯一线索）；
      · 细进度条只作为辅助（aria-hidden 的装饰），百分比文字才是可读数据；
      · 有逾期事项的阶段额外挂一个红色「逾期 N」标记 —— 这是最需要被看见的信息。

    ★ 阶段的 status / percent **直接来自后端**（`/byLand` 的 stageStatus），
      前端不重算：阶段汇总口径（尤其「不涉及」怎么剔除）在后端 ProcessStatus 里
      是唯一实现，前端另算一套迟早与服务端统计对不上。

    props：
      stages  后端给的 [{ stageId, stageName, status, percent, filledCount }]
      compact 紧凑模式（表格单元格里用，省略条与说明）
  -->
  <div class="facility-stage-chips" :class="{ 'is-compact': compact }">
    <span
      v-for="stage in stages"
      :key="stage.stageId || stage.stageName"
      class="facility-stage-chips__item"
      :title="stageTitle(stage)"
    >
      <span class="facility-stage-chips__name">{{ shortName(stage.stageName) }}</span>
      <span class="facility-stage-chips__body">
        <screen-tag :tone="stage.status === '已完成' ? 'success' : stage.status === '进行中' ? 'info' : 'muted'" size="sm">
          {{ stage.status || '未开启' }}
        </screen-tag>
        <span class="facility-stage-chips__percent">{{ stagePercent(stage) }}%</span>
      </span>
      <span
        v-if="!compact"
        class="facility-stage-chips__track"
        aria-hidden="true"
      >
        <i
          class="facility-stage-chips__fill"
          :class="stageFillClass(stage.status)"
          :style="{ width: stagePercent(stage) + '%' }"
        />
      </span>
    </span>
  </div>
</template>

<script>
import { ScreenTag } from '@/components/screen'

export default {
  name: 'FacilityStageChips',
  components: { ScreenTag },
  props: {
    /** 后端 stageStatus / processTree.stages 的数组（字段名一致） */
    stages: { type: Array, default: () => [] },
    compact: { type: Boolean, default: false }
  },
  methods: {
    /**
     * 阶段名的短称：去掉「阶段」二字后的前 4 个字。
     * ★ 为什么必须短：六个标签要挤在一行里，全名（如「项目前期审批阶段」）
     *   会把这一行撑到两行以上，表格行高一乱就再也对不齐。
     *   完整名放在 title 里，悬停可见。
     */
    shortName (name) {
      const text = String(name || '').replace(/阶段$/, '')
      return text.length > 4 ? text.slice(0, 4) : (text || '—')
    },

    /**
     * 阶段名 → 进度的**唯一来源**：直接用后端给的 stage.status，
     * 这里只做「短称 + 统计」的展示加工，不重算任何状态或百分比。
     * 后端 /byLand 的 stageStatus 与 /processTree 的 stages 字段名一致，
     * 所以两处可以复用同一个组件。
     */
    stageFill (stage) {
      if (stage.percent !== undefined && stage.percent !== null) {
        return Number(stage.percent) || 0
      }
      const items = stage.items || []
      if (!items.length) return 0
      const effective = items.filter((item) => item.lcqk !== '不涉及')
      if (!effective.length) return 100
      const finished = effective.filter((item) => item.lcqk === '已完成').length
      return Math.round((finished * 100) / effective.length)
    },

    /** 完成度：优先用后端给的 percent（唯一口径），只有树接口没给时才按事项兜底 */
    stagePercent (stage) {
      if (stage.percent !== undefined && stage.percent !== null) {
        return Number(stage.percent) || 0
      }
      return this.stageFill(stage)
    },

    stageTitle (stage) {
      const parts = [
        stage.stageName || '',
        stage.status || '未开启',
        `完成度 ${this.stagePercent(stage)}%`
      ]
      if (stage.filledCount !== undefined && stage.filledCount !== null) {
        parts.push(`已填 ${stage.filledCount} 个事项`)
      }
      if (stage.overdueCount) {
        parts.push(`逾期 ${stage.overdueCount} 个事项`)
      }
      return parts.join(' · ')
    },

    /** 进度条填充色：只在「已完成」时用绿，避免整行花掉 */
    stageFillClass (status) {
      if (status === '已完成') return 'is-done'
      if (status === '进行中') return 'is-running'
      return 'is-idle'
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.facility-stage-chips {
  display: flex;
  align-items: stretch;
  gap: 6px;
  min-width: 0;

  &__item {
    display: flex;
    flex-direction: column;
    gap: 3px;
    flex: 1 1 0;
    min-width: 0;
  }

  &__name {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }

  &__body {
    display: flex;
    align-items: center;
    gap: 4px;
    min-width: 0;
  }

  &__percent {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
  }

  &__track {
    display: block;
    height: 3px;
    background: var(--screen-bar-track);
    border-radius: var(--screen-radius-pill);
    overflow: hidden;
  }

  &__fill {
    display: block;
    height: 100%;
    min-width: 2px;
    border-radius: var(--screen-radius-pill);
    transition: width var(--screen-duration) var(--screen-ease);

    &.is-done {
      background-image: linear-gradient(90deg, var(--screen-success) 0%, var(--screen-viz-green) 100%);
    }

    &.is-running {
      background-image: linear-gradient(90deg, var(--screen-bar-from) 0%, var(--screen-bar-to) 100%);
    }

    &.is-idle {
      background-image: linear-gradient(90deg, var(--screen-text-mute) 0%, var(--screen-bar-muted-to) 100%);
    }
  }

  /* 紧凑模式：表格单元格里只留「短名 + 标签 + 百分比」，纵向再压一档 */
  &.is-compact {
    gap: 4px;

    .facility-stage-chips__item {
      gap: 2px;
    }

    .facility-stage-chips__name {
      font-size: 11px;
    }
  }
}
</style>
