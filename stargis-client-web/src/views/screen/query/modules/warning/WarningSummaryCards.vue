<template>
  <!--
    WarningSummaryCards 预警信息查询 · 四色统计卡
    ===============================================================
    蓝 / 黄 / 橙 / 红 四档 + 合计，数据来自 `GET /land/stat/warning/summary`。

    ★ 为什么以「固定四档」为骨架去后端结果里取数（而不是直接遍历 byLevel）：
      契约 §5.2 要求 byLevel 四档齐全，但那是后端承诺；前端一旦直接渲染后端数组，
      某个等级被停用或接口只返回有数据的档位时，卡片会从五格变四格 → **跳色、版面抖动**，
      用户会以为「这一档这次不存在」。固定骨架 + 缺项补 0，版面与颜色语义都恒定。

    ★ 颜色取 `--screen-level-*`（与首页看板同一套令牌），见 ./level-tones.less。

    ★ 无障碍：卡片本身是「标签 + 数值」两个真实文本节点，颜色不是唯一线索；
      数值异步变化时用一个视觉隐藏的 role="status" 播报完整语义（不是只报一个裸数字）。
  -->
  <div class="warning-cards">
    <div class="warning-cards__row" role="group" aria-label="预警等级统计">
      <div
        v-for="card in cards"
        :key="card.key"
        class="warning-cards__card"
        :class="card.toneClass"
      >
        <span class="warning-cards__label">{{ card.label }}</span>
        <span class="warning-cards__value">
          {{ card.count }}<em>条</em>
        </span>
      </div>
    </div>

    <p class="warning-cards__foot">
      <template v-if="loading">正在统计…</template>
      <template v-else-if="calcTime">统计时间：{{ calcTime }}</template>
      <template v-else>暂无统计结果</template>
    </p>

    <p class="warning-cards__sr-only" role="status" aria-live="polite" aria-atomic="true">
      {{ announceText }}
    </p>
  </div>
</template>

<script>
import { WARNING_LEVEL_SLOTS, levelToneClass } from './constants'

export default {
  name: 'WarningSummaryCards',
  props: {
    /** `/summary` 的 byLevel：[{ levelId, levelCode, levelName, count }] */
    byLevel: { type: Array, default: () => [] },
    /** `/summary` 的 total（允许字符串：后端若把总数序列化成字符串也不该报警告） */
    total: { type: [Number, String], default: 0 },
    /** `/summary` 的 calcTime（本轮统计对应的重算时刻） */
    calcTime: { type: String, default: '' },
    loading: { type: Boolean, default: false }
  },
  computed: {
    cards () {
      const byLevel = this.byLevel || []
      const list = WARNING_LEVEL_SLOTS.map((slot) => {
        // 按 levelCode 匹配（不用 levelId：等级被重建后 id 会变，code 是稳定的着色语义）
        const hit = byLevel.filter((item) => String(item && item.levelCode).toLowerCase() === slot.code)[0]
        return {
          key: slot.code,
          label: (hit && hit.levelName) || slot.label,
          count: Number((hit && hit.count) || 0),
          toneClass: levelToneClass(slot.code)
        }
      })
      list.push({ key: 'total', label: '合计', count: Number(this.total || 0), toneClass: 'is-total' })
      return list
    },
    announceText () {
      const parts = this.cards
        .filter((card) => card.key !== 'total')
        .map((card) => `${card.label}${card.count}条`)
        .join('、')
      return `预警统计已更新：共 ${this.total || 0} 条${parts ? '，' + parts : ''}`
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';
@import './level-tones.less';

.warning-cards {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &__row {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: var(--screen-space-2);

    @media (max-width: 1500px) {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  &__card {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: var(--screen-space-2) var(--screen-space-3);
    .screen-glass();
    // 左侧语气条：与数值颜色一起构成非颜色单一线索（色弱用户也能分档）
    border-left: 3px solid currentColor;
    .warning-level-tones();
  }

  &__label {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__value {
    .screen-number-font(var(--screen-font-number-sm), 700);
    color: inherit;

    em {
      margin-left: 3px;
      font-family: var(--screen-font-family);
      font-size: var(--screen-font-xs);
      font-style: normal;
      font-weight: 400;
      color: var(--screen-text-mute);
    }
  }

  /* 合计卡不着等级色：它是「总量」，用主文本色与四档区分开 */
  &__card.is-total {
    color: var(--screen-text);
  }

  &__foot {
    margin: 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  /* 视觉隐藏但可被读屏读到（数值播报用） */
  &__sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
  }
}
</style>
