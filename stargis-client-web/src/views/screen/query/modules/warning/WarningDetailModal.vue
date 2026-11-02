<template>
  <!--
    WarningDetailModal 预警详情（结果行「查看」打开）
    ===============================================================
    ★ 详情**不请求接口**：契约 §6.1 明确「详情用行数据即可」。
      `/list` 返回的 WarningVO 已经带齐等级、预警值、逾期天数、实际/标准天数、
      问题类型、具体情况、决策意见、责任部门、计算时间 —— 再开一个 detail 接口
      只会多一次往返，还多一个「列表和详情对不上」的可能（重算之后尤其明显）。

    ★ 等级值按 levelCode 着色，与结果表、四色卡用同一套令牌（见 level-tones.less）。

    ★ 空值一律显示「—」：详情里「字段是空的」和「这一行没有这个字段」是两回事，
      留白会让人以为是页面没渲染出来（与配套详情弹窗同一处理）。
  -->
  <screen-modal
    :visible="visible"
    :title="modalTitle"
    :width="900"
    :show-footer="false"
    @cancel="$emit('close')"
  >
    <div v-if="row" class="warning-detail">
      <section class="warning-detail__group">
        <h4 class="warning-detail__title">预警对象</h4>
        <screen-descriptions
          variant="flat"
          :items="targetItems"
          :columns="2"
          label-width="120px"
        />
      </section>

      <section class="warning-detail__group">
        <h4 class="warning-detail__title">预警判定</h4>
        <screen-descriptions
          variant="flat"
          :items="judgeItems"
          :columns="2"
          label-width="120px"
        >
          <template #levelName="{ value }">
            <span class="warning-detail__level" :class="levelToneClass(row.levelCode)">
              {{ value }}
            </span>
          </template>
        </screen-descriptions>
      </section>

      <p class="warning-detail__foot">
        <screen-icon name="info" :size="13" />
        预警值是「实际历时 ÷ 标准历时」，逾期天数按工作日计算；记录会在「重新计算」或保存环节进度后刷新
      </p>
    </div>

    <screen-empty v-else text="未选择预警记录" />
  </screen-modal>
</template>

<script>
import {
  ScreenDescriptions,
  ScreenEmpty,
  ScreenIcon,
  ScreenModal
} from '@/components/screen'
import { levelToneClass, orDash, percentText } from './constants'

export default {
  name: 'WarningDetailModal',
  components: { ScreenDescriptions, ScreenEmpty, ScreenIcon, ScreenModal },
  props: {
    visible: { type: Boolean, default: false },
    /** 结果行的 WarningVO（详情完全由它渲染，不请求接口） */
    row: { type: Object, default: null }
  },
  computed: {
    modalTitle () {
      const name = this.row && this.row.yjName
      return name ? `预警详情 · ${name}` : '预警详情'
    },
    /** 这一条预警「挂在谁身上」 */
    targetItems () {
      const row = this.row || {}
      return [
        { key: 'yjType', label: '预警分类', value: orDash(row.yjType) },
        { key: 'ptxmmc', label: '配套项目', value: orDash(row.ptxmmc || row.ptName) },
        { key: 'crzdbh', label: '宗地编号', value: orDash(row.crzdbh) },
        { key: 'xzqh', label: '行政区划', value: orDash(row.xzqh) },
        { key: 'dkmc', label: '地块名称', value: orDash(row.dkmc) },
        { key: 'lcName', label: '流程环节', value: orDash(row.lcName) }
      ]
    },
    /** 判定相关的字段：等级、预警值、天数、问题与意见 */
    judgeItems () {
      const row = this.row || {}
      return [
        { key: 'levelName', label: '预警等级', value: orDash(row.levelName) },
        { key: 'yjPercent', label: '预警值', value: percentText(row.yjPercent) },
        { key: 'overdueDays', label: '逾期天数', value: orDash(row.overdueDays) },
        {
          key: 'days',
          label: '实际 vs 标准',
          value: `${orDash(row.actualDays)} / ${orDash(row.stdDays)} 个工作日`
        },
        { key: 'problemType', label: '问题类型', value: orDash(row.problemType) },
        { key: 'zrbm', label: '责任部门', value: orDash(row.zrbm) },
        { key: 'calcTime', label: '计算时间', value: orDash(row.calcTime) },
        { key: 'problemDetail', label: '具体情况', value: orDash(row.problemDetail), stack: true },
        { key: 'decisionOpinion', label: '决策意见', value: orDash(row.decisionOpinion), stack: true }
      ]
    }
  },
  methods: {
    levelToneClass
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';
@import './level-tones.less';

.warning-detail {
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  .screen-scrollbar();

  &__group {
    margin-bottom: 14px;
  }

  &__title {
    margin: 0 0 6px;
    padding-left: 8px;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    color: var(--screen-text);
    line-height: 1.4;
    border-left: 3px solid var(--screen-accent);
  }

  &__level {
    font-weight: 600;
    .warning-level-tones();
  }

  &__foot {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 4px 0 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }
}
</style>
