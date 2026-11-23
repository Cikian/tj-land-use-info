<template>
  <!--
    ProcessTreePanel 29 环节进度树（六大阶段 × 各事项）
    --------------------------------
    这是「配套地块数据录入」的**旗舰视图**，也是后端 `FacilityProcessTreeVO`
    唯一的专用界面。整屏弹窗打开，因为 6 个阶段 × 各事项的表格需要横向空间。

    三段式版面：
      ① 顶部指标条 —— 环节总数 / 已完成 / 进行中 / 不涉及 / 未开启 / 已填报 / 逾期
                      + 整体完成度。这些数字**全部来自后端**（顶层汇总字段），
                      前端不重算：阶段汇总口径（尤其「不涉及」怎么剔除）
                      在后端 ProcessStatus 里是唯一实现。
      ② 两种视图 —— 「按阶段」是默认（6 个可折叠阶段分组，每组一张事项表）；
                    「平铺表格」给喜欢一张大表一次性扫完的人（走 /processList）。
                    两个视图的**表格内编辑能力完全一致**（状态 / 备注就地改，
                    共用同一份 pending 与同一次「整屏保存」）——只给其中一个留内联编辑，
                    用户会以为另一张表是只读的。
      ③ 底部整屏保存 —— 收集本地脏项，一次 POST /process/saveBatch，
                        并逐条展示失败明细。

    ★★ 为什么「整屏保存」是必要的而不是锦上添花：
      环节进度是**现场按阶段推进**的，一次会议可能连填好几个阶段的事项。
      如果每个事项都要开弹窗 → 保存 → 关弹窗，填 8 个事项就是 24 次交互。
      这里的做法是：弹窗里的保存只更新**本地树**（父组件就地改那一行），
      攒够了一次性提交；这样「改 10 个事项」是 10 次弹窗保存 + 1 次整屏提交。

    ★ 为什么本地保存不直接落库：
      后端 `/process/save` 一次只落一条，逐条落库需要用户为每个事项都确认一次，
      且部分失败时前面的已经写进去了 —— 反而更难解释。
      整屏提交返回 `{ successCount, failCount, failures }`，失败明细可以一次列清。

    ★ 撤回（单条）仍然直接落库：撤回是**单个事项**的动作，
      攒批撤回没有业务意义（用户点撤回就是「这条填错了，立刻清掉」）。

    ★★ P2-03 表格内直接编辑（2026-11）
      现场是**按阶段推进**的，一次会议可能连填好几个阶段的事项。原来「逐条打开弹窗 →
      保存 → 关掉 → 再开下一个」在 29 个事项上要几十次交互，测试反馈「配套流程填报复杂」。
      现在：状态（仍是那四个环节情况）与备注直接在表格里改，改完一次「整屏保存」；
      开始 / 预计 / 实际三个日期保持可填但**选填**（在弹窗里填，允许全空）。
      ★ 只改交互，不改业务模型：29 环节配置（阶段 / 事项 / 主管部门 / 标准时长）、
        附件计数、逾期预警、字段级留痕仍全部由后端产出，前端不补算、也不删环节。
      ★ 备注落的是 `jtwt`（具体问题）：后端 `t_facility_process` 没有独立的「备注」列，
        它是这条进度唯一的自由文本，详情页与留痕也按「具体问题」展示。为加一列备注去
        动表结构要连带 Mapper / 导入 / 留痕三处，收益不抵风险。

    ★★ 为什么顶栏要区分「未保存预览」与服务端数字
      待保存的改动只在内存里，后端算不出百分比；可用户改完立刻想知道「这么改会变成多少」。
      于是本地按**与后端同一公式**推演（summarizeStatuses，注释里写明了口径与同步要求），
      并明确标注「未保存预览」；保存成功后 pending 清空，顶栏自动切回后端重算的权威结果。
      保存失败时相反 —— 保留编辑内容，但进度恢复成服务端值，否则用户会以为失败的项
      也已经生效了。

    公开方法：
      open(facility)  facility = { id, ptxmmc, crzdbh }（列表行或详情对象）
    事件：
      changed  进度被改动且已落库（整屏保存成功 / 单条撤回成功），父组件据此刷新列表
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    fullscreen
    :show-footer="false"
    :body-max-height="null"
    @cancel="handleClose"
  >
    <template #head-extra>
      <div class="process-tree__tags">
        <screen-tag v-if="tree && tree.crzdbh" tone="muted" size="sm">{{ tree.crzdbh }}</screen-tag>
        <screen-tag v-if="dirtyCount" tone="warning" size="sm">待保存 {{ dirtyCount }} 个环节</screen-tag>
        <screen-tag v-else tone="success" size="sm">没有未保存的修改</screen-tag>
      </div>

      <screen-button
        size="sm"
        type="primary"
        icon="save"
        :loading="saving"
        :disabled="!dirtyCount || loading"
        @click="handleSaveBatch"
      >
        整屏保存{{ dirtyCount ? `（${dirtyCount}）` : '' }}
      </screen-button>

      <screen-button size="sm" icon="reload" :loading="loading" @click="loadTree">刷新</screen-button>
    </template>

    <div class="process-tree">
      <!-- ================= ① 顶部指标条 ================= -->
      <div class="process-tree__summary">
        <div class="process-tree__progress">
          <div class="process-tree__progress-head">
            <span class="process-tree__progress-value">{{ overallPercent }}%</span>
            <!-- 「未保存预览」是清单 P2-03 的硬要求：本地推演与服务端权威值必须一眼可分 -->
            <screen-tag v-if="previewing" tone="warning" size="sm">未保存预览</screen-tag>
            <screen-tag v-else-if="dirtyCount" tone="danger" size="sm">已恢复服务端进度</screen-tag>
          </div>
          <span class="process-tree__progress-label">整体完成度（已剔除「不涉及」）</span>
          <span class="process-tree__progress-track" aria-hidden="true">
            <i class="process-tree__progress-fill" :style="{ width: overallPercent + '%' }" />
          </span>
        </div>

        <ul class="process-tree__stats">
          <li v-for="item in summaries" :key="item.key" class="process-tree__stat">
            <span class="process-tree__stat-label">{{ item.label }}</span>
            <b class="process-tree__stat-value" :class="item.tone ? `is-${item.tone}` : ''">{{ item.value }}</b>
            <span class="process-tree__stat-unit">{{ item.unit }}</span>
          </li>
        </ul>
      </div>

      <screen-tabs v-model="view" :tabs="viewTabs" class="process-tree__views" />

      <!-- ================= ② 按阶段（树） ================= -->
      <div v-show="view === 'tree'" class="process-tree__pane">
        <screen-loading
          class="process-tree__content"
          :loading="loading"
          text="正在加载六大阶段进度…"
          :overlay="false"
        >
          <screen-panel
            v-for="stageView in stageViews"
            :key="stageView.stage.stageId"
            class="process-tree__stage"
            collapsible
            :title="stageView.stage.stageName"
            :sub-title="stageView.subTitle"
          >
            <template #extra>
              <screen-tag :tone="stageStatusTone(stageView.status)" size="sm">{{ stageView.status }}</screen-tag>
              <span class="process-tree__stage-percent">{{ stageView.percent }}%</span>
              <!-- 只有本阶段有本地未保存改动时才标「未保存预览」：没改过的阶段用后端值 -->
              <screen-tag v-if="stageView.preview" tone="warning" size="sm">未保存预览</screen-tag>
              <span
                v-if="stageView.overdueCount"
                class="process-tree__stage-overdue"
                :title="`本阶段有 ${stageView.overdueCount} 个环节逾期`"
              >
                <screen-icon name="alert-triangle" :size="12" />
                逾期 {{ stageView.overdueCount }}
              </span>
            </template>

            <screen-data-table
              :columns="itemColumns"
              :data="stageView.rows"
              row-key="lcId"
              :min-width="1400"
              :animated="false"
              empty-text="该阶段下没有配置事项"
            >
              <template #lcName="{ row }">
                <span class="process-tree__lc-name" :title="row.lcName || ''">
                  {{ row.lcName }}
                </span>
                <span class="process-tree__lc-path">{{ row.path || '' }}</span>
              </template>

              <template #zgbm="{ row }">
                <span class="process-tree__ellipsis" :title="row.zgbm || ''">{{ row.zgbm || '—' }}</span>
              </template>

              <!-- 状态：表格内直接改（选项仍是字典 land_process_status 的四个值） -->
              <template #lcqk="{ row }">
                <screen-select
                  :value="row.lcqk || '未开启'"
                  :options="statusOptions"
                  size="sm"
                  :clearable="false"
                  :aria-label="`${row.lcName} 的环节情况`"
                  @change="handleInlineStatus(row, $event)"
                />
              </template>

              <!--
                备注：写的是 `jtwt`（具体问题）—— 后端这一行没有独立备注列，
                它是唯一的自由文本，理由见组件头注释。
                「未填报」原先挂在状态格里，现在由占位符承担：少一行文字，信息不丢。
              -->
              <template #jtwt="{ row }">
                <screen-input
                  :value="row.jtwt || ''"
                  size="sm"
                  :maxlength="1000"
                  :placeholder="row.filled ? '可留空' : '未填报'"
                  :aria-label="`${row.lcName} 的备注`"
                  @change="handleInlineRemark(row, $event)"
                />
              </template>

              <template #dates="{ row }">
                <span class="process-tree__dates">
                  <span class="process-tree__date" :title="'开始时间'">{{ row.lckssj || '—' }}</span>
                  <span class="process-tree__date is-sub" :title="'预计结束时间'">
                    预计 {{ row.yjjssj || '—' }}
                  </span>
                  <span class="process-tree__date is-sub" :title="'实际结束时间'">
                    实际 {{ row.lcjssj || '—' }}
                  </span>
                </span>
              </template>

              <template #overdue="{ row }">
                <span v-if="row.overdue" class="process-tree__overdue" :title="`逾期 ${row.overdueDays || 0} 天`">
                  <screen-icon name="alert-triangle" :size="12" />
                  逾期 {{ row.overdueDays || 0 }} 天
                </span>
                <span v-else class="process-tree__mute">—</span>
              </template>

              <template #attachmentCount="{ row }">
                <span class="process-tree__num">{{ row.attachmentCount || 0 }}</span>
              </template>

              <template #action="{ row }">
                <span class="process-tree__actions">
                  <!-- 日期、问题类型、录入人等字段仍走弹窗：表格里放不下，也不该挤在 29 行里 -->
                  <button
                    type="button"
                    class="process-tree__link"
                    @click.stop="handleEditItem(row, stageView.stage)"
                  >
                    {{ row.filled ? '编辑' : '录入' }}
                  </button>
                  <span v-if="isDirty(row.lcId)" class="process-tree__dirty">已改</span>
                </span>
              </template>
            </screen-data-table>
          </screen-panel>
        </screen-loading>
      </div>

      <!-- ================= ②b 平铺表格 ================= -->
      <div v-show="view === 'flat'" class="process-tree__pane">
        <section class="process-tree__block">
          <h4 class="process-tree__block-title">
            全部环节（平铺）
            <span class="process-tree__block-sub">
              共 {{ flatRows.length }} 条 · 按阶段顺序排列 · 数据来自 /processList
            </span>
          </h4>

          <screen-data-table
            :columns="flatColumns"
            :data="flatViewRows"
            :loading="flatLoading"
            row-key="lcId"
            :min-width="1600"
            :animated="false"
            empty-text="暂无环节配置"
          >
            <template #stageName="{ row }">
              <span class="process-tree__ellipsis" :title="row.stageName || ''">{{ row.stageName || '—' }}</span>
              <span class="process-tree__lc-path">{{ row.stagePercent || 0 }}%</span>
            </template>

            <template #lcName="{ row }">
              <span class="process-tree__lc-name" :title="row.lcName || ''">{{ row.lcName }}</span>
            </template>

            <template #zgbm="{ row }">
              <span class="process-tree__ellipsis" :title="row.zgbm || ''">{{ row.zgbm || '—' }}</span>
            </template>

            <!--
              平铺视图与「按阶段」树视图**同一套内联编辑**（P2-03）：
              状态 / 备注都能直接在格子里改，改动进同一份 pending，
              由同一个「整屏保存（N）」提交。两个视图行为不一致时，
              用户会以为平铺那张表是只读的。
            -->
            <template #lcqk="{ row }">
              <screen-select
                :value="row.lcqk || '未开启'"
                :options="statusOptions"
                size="sm"
                :clearable="false"
                :aria-label="`${row.lcName} 的环节情况`"
                @change="handleInlineStatus(row, $event)"
              />
            </template>

            <!-- 备注写的是 `jtwt`（具体问题），与树视图、环节弹窗同一个字段 -->
            <template #jtwt="{ row }">
              <screen-input
                :value="row.jtwt || ''"
                size="sm"
                :maxlength="1000"
                :placeholder="row.filled ? '可留空' : '未填报'"
                :aria-label="`${row.lcName} 的备注`"
                @change="handleInlineRemark(row, $event)"
              />
            </template>

            <template #dates="{ row }">
              <span class="process-tree__dates">
                <span class="process-tree__date">{{ row.lckssj || '—' }}</span>
                <span class="process-tree__date is-sub">预计 {{ row.yjjssj || '—' }}</span>
                <span class="process-tree__date is-sub">实际 {{ row.lcjssj || '—' }}</span>
              </span>
            </template>

            <template #overdue="{ row }">
              <span v-if="row.overdue" class="process-tree__overdue">
                <screen-icon name="alert-triangle" :size="12" />
                逾期 {{ row.overdueDays || 0 }} 天
              </span>
              <span v-else class="process-tree__mute">—</span>
            </template>

            <template #action="{ row }">
              <span class="process-tree__actions">
                <!-- 日期 / 问题类型 / 录入人等表格里放不下的字段仍走弹窗 -->
                <button type="button" class="process-tree__link" @click.stop="handleEditFlat(row)">
                  {{ row.filled ? '编辑' : '录入' }}
                </button>
                <span v-if="isDirty(row.lcId)" class="process-tree__dirty">已改</span>
              </span>
            </template>
          </screen-data-table>
        </section>
      </div>

      <process-item-modal
        v-if="itemVisible"
        ref="itemModal"
        @saved="handleItemSaved"
        @withdrawn="handleItemWithdrawn"
      />
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenPanel,
  ScreenTabs,
  ScreenTag,
  ScreenButton,
  ScreenSelect,
  ScreenInput,
  ScreenDataTable,
  ScreenLoading,
  ScreenIcon
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import ProcessItemModal from './ProcessItemModal.vue'
import {
  queryProcessTree,
  queryProcessList,
  saveProcessBatch
} from '@/api/land/facilityAdmin'
import { queryLandDictItems } from '@/api/land/landAdmin'
import { PROCESS_STATUSES, dictDefinitions, stageStatusTone } from '../constants'

/**
 * 四个环节情况（与后端 ProcessStatus 枚举逐字一致）。
 * ★ 放在模块作用域：下面的汇总纯函数与表格内下拉共用同一批字面量，
 *   这四个字一旦有人手写成别的样子，「剔除不涉及」的分母就会悄悄算错。
 */
const STATUS = {
  notStarted: '未开启',
  running: '进行中',
  finished: '已完成',
  notInvolved: '不涉及'
}

/**
 * 一组环节情况 → 完成度 + 阶段状态。
 *
 * ★★ 与后端 `ProcessStatus` 同口径，改一处必须同步另一处：
 *   后端 jeecg-module-land/…/data/process/enums/ProcessStatus.java
 *     · stagePercent()：第 164-185 行
 *     · rollUpStageStatus()：第 111-156 行
 *   口径（两边必须一字不差，否则用户会看到「保存前后百分比跳一下」）：
 *     1. 完成度 = 已完成事项数 ÷ **剔除「不涉及」后**的事项数，四舍五入取整；
 *     2. 一个事项都没有 → 0%；全部「不涉及」→ 100%
 *        （★ 现行规则，属**待业务确认项**：语义是「无需办理 = 已了结」。
 *         若业务最终认为该阶段应显示「不涉及」而不是满进度，
 *         这里与后端 stagePercent 第 180-183 行必须一起改，不能只改一边）；
 *     3. 阶段状态回滚：空 → 未开启；全「不涉及」→ 不涉及；
 *        剔除后全「已完成」→ 已完成；剔除后全「未开启」→ 未开启；其余 → 进行中。
 *     4. 取值缺失（null / 空串）按「未开启」计 —— 后端 of(null) 为 null 时也走这个分支。
 *
 * ★ 为什么前端要再实现一遍：本地待保存的改动还没落库，后端算不出来，
 *   而用户改完立刻要看到「这么改会变成多少」。只在**未保存预览**时用它，
 *   没有本地改动时一律显示后端字段。
 *
 * @param {Array<string|null>} statuses 环节情况列表
 * @returns {{percent: number, status: string, total: number, finished: number,
 *            running: number, notStarted: number, notInvolved: number}}
 */
function summarizeStatuses (statuses) {
  const list = statuses || []
  let effective = 0
  let finished = 0
  let running = 0
  let notStarted = 0
  let notInvolved = 0

  list.forEach((raw) => {
    const status = raw || STATUS.notStarted
    if (status === STATUS.notInvolved) {
      notInvolved += 1
      return
    }
    effective += 1
    if (status === STATUS.finished) {
      finished += 1
    } else if (status === STATUS.running) {
      running += 1
    } else {
      notStarted += 1
    }
  })

  let percent = 0
  if (list.length) {
    // 全「不涉及」= 100%，见上面第 2 条（待业务确认项）
    percent = effective <= 0 ? 100 : Math.round(finished * 100 / effective)
  }

  let status = STATUS.running
  if (!list.length) {
    status = STATUS.notStarted
  } else if (effective <= 0) {
    status = STATUS.notInvolved
  } else if (finished === effective) {
    status = STATUS.finished
  } else if (notStarted === effective) {
    status = STATUS.notStarted
  }

  return {
    percent,
    status,
    total: list.length,
    finished,
    running,
    notStarted,
    notInvolved
  }
}

export default {
  name: 'ProcessTreePanel',
  components: {
    ScreenModal,
    ScreenPanel,
    ScreenTabs,
    ScreenTag,
    ScreenButton,
    ScreenSelect,
    ScreenInput,
    ScreenDataTable,
    ScreenLoading,
    ScreenIcon,
    ProcessItemModal
  },
  data () {
    return {
      visible: false,
      loading: false,
      saving: false,
      flatLoading: false,
      // 1118 P2-01：单位是按表格整批填报的，不逐环节上报时间 —— 默认落在平铺表格直填。
      // 树视图仍保留（看阶段进度、追溯预警用），切换不改任何业务口径。
      view: 'flat',
      facility: {},
      tree: null,
      flatRows: [],
      /** 本地待保存的变更：{ [lcId]: payload } —— 只在内存里，切页签不丢，关弹窗清空 */
      pending: {},
      /**
       * 上一轮整屏保存是否失败过。
       * ★ 失败后仍保留 pending（编辑内容不能丢），但进度必须切回服务端权威值 ——
       *   否则用户会把本地推演的百分比当成「已经保存成功」。下一次本地编辑会清掉它。
       */
      saveFailed: false,
      itemVisible: false,
      viewTabs: [
        { key: 'tree', label: '按阶段' },
        { key: 'flat', label: '平铺表格' }
      ],
      /** 状态下拉：先用字典兜底值，created 里取不到字典就保持它 */
      statusOptions: PROCESS_STATUSES.map((item) => ({ value: item, label: item })),
      itemColumns: [
        { key: 'lcName', title: '环节事项', width: 260, type: 'slot' },
        { key: 'zgbm', title: '主管部门', width: 130, type: 'slot' },
        { key: 'lcqk', title: '环节情况', width: 150, type: 'slot', align: 'center' },
        { key: 'jtwt', title: '备注', width: 240, type: 'slot' },
        { key: 'dates', title: '开始 / 预计结束 / 实际结束', width: 230, type: 'slot' },
        { key: 'overdue', title: '逾期', width: 110, type: 'slot', align: 'center' },
        { key: 'attachmentCount', title: '附件', width: 70, type: 'slot', align: 'center' },
        { key: 'action', title: '操作', width: 100, type: 'slot', align: 'center' }
      ],
      flatColumns: [
        { key: 'index', title: '#', width: 60, type: 'index', align: 'center' },
        { key: 'stageName', title: '所属阶段', width: 200, type: 'slot' },
        { key: 'lcName', title: '环节事项', width: 280, type: 'slot' },
        { key: 'zgbm', title: '主管部门', width: 150, type: 'slot' },
        { key: 'lcqk', title: '环节情况', width: 150, type: 'slot', align: 'center' },
        { key: 'jtwt', title: '备注', width: 240, type: 'slot' },
        { key: 'dates', title: '开始 / 预计 / 实际', width: 260, type: 'slot' },
        { key: 'overdue', title: '逾期', width: 120, type: 'slot', align: 'center' },
        { key: 'lrr', title: '录入人', width: 100, formatter: (value) => value || '—' },
        { key: 'action', title: '操作', width: 110, type: 'slot', align: 'center' }
      ]
    }
  },
  computed: {
    title () {
      const name = this.facility.ptxmmc || (this.tree && this.tree.facilityName) || ''
      return name ? `${name} · 29 环节进度` : '29 环节进度'
    },
    stages () {
      return (this.tree && this.tree.stages) || []
    },
    dirtyCount () {
      return Object.keys(this.pending).length
    },
    /**
     * 是否处于「未保存预览」。
     * ★ 两个条件缺一不可：
     *   · 有本地待保存改动（dirtyCount > 0）—— 没有改动就没有可预览的东西；
     *   · 上一轮整屏保存没有失败（!saveFailed）—— 失败后后端已经把成功的部分落库并
     *     重算了整棵树，这时继续显示本地推演的百分比，会让用户以为失败的项也生效了。
     *     清单 P2-03 对失败态的要求是「保留编辑内容并恢复权威进度」。
     */
    previewing () {
      return this.dirtyCount > 0 && !this.saveFailed
    },
    /**
     * 每个阶段一张表：行 = 后端事项 + 本地待保存改动（合并后渲染）；
     * 汇总（状态 / 完成度 / 已填数）在有本地改动的阶段按同一公式预览，没改动的阶段用后端值。
     * ★ 为什么要「按阶段」区分预览：只有脏阶段才可能出现「显示与后端不一致」，
     *   给干净的阶段也标「未保存预览」只会让用户以为整页都不可信。
     */
    stageViews () {
      const previewing = this.previewing
      return this.stages.map((stage) => {
        const rows = this.rowsOf(stage)
        const dirty = rows.some((row) => this.isDirty(row.lcId))
        const preview = previewing && dirty
        const filledCount = preview
          ? rows.filter((row) => !!row.filled).length
          : (stage.filledCount || 0)
        const summary = preview ? summarizeStatuses(rows.map((row) => row.lcqk)) : null
        return {
          stage,
          rows,
          preview,
          status: summary ? summary.status : (stage.status || STATUS.notStarted),
          percent: summary ? summary.percent : (stage.percent || 0),
          overdueCount: stage.overdueCount || 0,
          subTitle: this.stageSubTitle(stage, filledCount)
        }
      })
    },
    /** 全树事项（本地改动已合并）：整体预览与数量统计都按它算，口径与阶段表一致 */
    allRows () {
      return this.stageViews.reduce((rows, stageView) => rows.concat(stageView.rows), [])
    },
    /**
     * 平铺表格的渲染行：**与树视图同一套「本地改动已合并」的规则**（P2-03）。
     * ★ 不合并的话，在平铺里改完状态下拉，表格里显示的还是后端原值
     *   （只有 pending 变了），用户会以为没改上、于是重复改同一格。
     */
    flatViewRows () {
      return (this.flatRows || []).map((row) => {
        const patch = this.pending[row.lcId]
        return patch ? Object.assign({}, row, patch, { filled: true }) : row
      })
    },
    /**
     * 整体完成度。
     * ★ 未预览时直接用后端 `tree.percent`：后端是唯一权威。同一公式在前端再算一遍
     *   即使今天结果一样，也会在将来后端改口径时静默分叉 —— 没必要冒这个险。
     */
    overallPercent () {
      if (!this.previewing) return (this.tree && this.tree.percent) || 0
      return summarizeStatuses(this.allRows.map((row) => row.lcqk)).percent
    },
    /**
     * 顶部指标条。
     * ★ 七个数顺序按「用户最关心的排前面」：
     *   总事项 → 已完成 → 进行中 → 未开启 → 不涉及 → 已填报 → 逾期。
     *   逾期单独用 danger 色：它是唯一需要立刻行动的指标。
     * ★ 预览态的**数量也按合并后的行重算**，否则会出现「已完成 8 个 / 完成度 60%」
     *   这种自相矛盾的顶栏（后端字段还是保存前的）。
     */
    summaries () {
      const data = this.tree || {}
      let counts = {
        total: data.totalItems || 0,
        finished: data.finishedItems || 0,
        running: data.runningItems || 0,
        notStarted: data.notStartedItems || 0,
        notInvolved: data.notInvolvedItems || 0,
        filled: data.filledItems || 0
      }
      if (this.previewing) {
        const summary = summarizeStatuses(this.allRows.map((row) => row.lcqk))
        counts = {
          total: summary.total,
          finished: summary.finished,
          running: summary.running,
          notStarted: summary.notStarted,
          notInvolved: summary.notInvolved,
          filled: this.allRows.filter((row) => !!row.filled).length
        }
      }
      return [
        { key: 'total', label: '环节总数', value: counts.total, unit: '个' },
        { key: 'finished', label: '已完成', value: counts.finished, unit: '个', tone: 'ok' },
        { key: 'running', label: '进行中', value: counts.running, unit: '个', tone: 'primary' },
        { key: 'notStarted', label: '未开启', value: counts.notStarted, unit: '个' },
        { key: 'notInvolved', label: '不涉及', value: counts.notInvolved, unit: '个' },
        { key: 'filled', label: '已填报', value: counts.filled, unit: '个' },
        // 逾期依赖服务端的节假日日历与「今天」，本地不预告 —— 保存成功后 loadTree 会刷新
        { key: 'overdue', label: '逾期', value: data.overdueItems || 0, unit: '个', tone: 'error' }
      ]
    }
  },
  created () {
    // 表格内状态下拉与环节弹窗共用同一个字典（land_process_status）：
    // 各写一份必然漂移，出现「表格能选、弹窗不能选」这种最难解释的问题。
    queryLandDictItems(dictDefinitions()).then((dicts) => {
      if (dicts && dicts.processStatus) this.statusOptions = dicts.processStatus
    })
  },
  methods: {
    stageStatusTone,

    /* ---------------- 对外入口 ---------------- */

    open (facility) {
      const record = facility || {}
      this.facility = record
      this.visible = true
      this.view = 'tree'
      this.tree = null
      this.flatRows = []
      // ★ 关掉再打开必须清空待保存队列：上一批未提交的改动不该跟着新配套走，
      //   否则会把 A 配套的进度写到 B 配套上（后端只认 ptId + lcId）。
      this.pending = {}
      this.saveFailed = false
      this.loadTree()
    },

    handleClose () {
      if (this.dirtyCount) {
        // 有待保存的修改时给一次确认机会，避免手滑关掉白填
        const confirmed = window.confirm(`还有 ${this.dirtyCount} 个环节的修改没有保存，确定关闭吗？`)
        if (!confirmed) return
      }
      this.visible = false
      this.pending = {}
      this.saveFailed = false
    },

    /* ---------------- 数据 ---------------- */

    loadTree () {
      const ptId = this.facility.id
      if (!ptId) {
        toast.error('缺少配套项目标识，无法加载环节进度')
        return
      }
      this.loading = true
      queryProcessTree(ptId)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '环节进度加载失败')
            return
          }
          this.tree = res.result || {}
        })
        .catch(() => {
          this.tree = null
        })
        .finally(() => {
          this.loading = false
        })
    },

    loadFlat () {
      const ptId = this.facility.id
      if (!ptId || this.flatRows.length) return
      this.flatLoading = true
      queryProcessList(ptId)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '环节平铺列表加载失败')
            return
          }
          this.flatRows = res.result || []
        })
        .catch(() => {
          this.flatRows = []
        })
        .finally(() => {
          this.flatLoading = false
        })
    },

    /* ---------------- 展示辅助 ---------------- */

    /**
     * 阶段标题右侧的补充说明（已填 / 总数 + 标准时长）。
     * @param {object} stage 后端阶段节点
     * @param {number} [filledCount] 已填数（预览态传合并后的数字；不传则用后端值）
     */
    stageSubTitle (stage, filledCount) {
      const filled = typeof filledCount === 'number' ? filledCount : (stage.filledCount || 0)
      const parts = [`已填 ${filled} / ${stage.itemCount || 0} 个事项`]
      if (stage.processTime) {
        parts.push(`标准 ${stage.processTime} 个工作日`)
      }
      return parts.join(' · ')
    },

    /**
     * 事项行：把本地未保存的改动**合并进来**渲染。
     * ★ 这是「本地保存」能成立的关键：弹窗里保存后立刻看到表格变了，
     *   但库里还没写 —— 用户看到的就是他将要提交的内容。
     */
    rowsOf (stage) {
      return (stage.items || []).map((item) => {
        const patch = this.pending[item.lcId]
        return patch ? Object.assign({}, item, patch, { filled: true }) : item
      })
    },

    isDirty (lcId) {
      return !!this.pending[lcId]
    },

    /* ---------------- 表格内直接编辑（状态 / 备注） ---------------- */

    /**
     * 记一条本地待保存改动。
     * ★ 顺手清掉 saveFailed：用户又改了东西，就该重新进入「未保存预览」——
     *   上一轮失败后恢复的服务端进度已经不能代表他此刻看到的内容。
     */
    markPending (lcId, patch) {
      this.$set(this.pending, lcId, patch)
      this.saveFailed = false
    },

    /**
     * 组装一条**整行快照**，而不是只放「刚改的那个字段」。
     *
     * ★ 为什么必须整行：单元格里只能改状态与备注，若只提交这两列，
     *   日期、问题类型、录入人 / 电话这些字段就没法一起带上；
     *   而 `saveProcess` 是按 payload 逐字段 `updateById`（后端默认 NOT_NULL 策略：
     *   没传的字段保持库内原值，**不会**落成 null；只有显式传空串才是「清空」）。
     *   所以整行快照的意义是「让一次保存尽量带上用户的完整意图」，
     *   而不是「不传就会被清空」——后一种说法是错的（原注释就写错了，已纠正）。
     * @param {object} row 已合并过待保存改动的行
     * @param {object} changes 本次改动的字段（只认 lcqk / jtwt）
     */
    inlinePatch (row, changes) {
      const next = changes || {}
      const pick = (key, fallback) => {
        if (next[key] !== undefined) return next[key]
        const current = row[key]
        return current === undefined || current === '' ? fallback : current
      }
      return {
        lcName: row.lcName || null,
        // 没录过状态的行，下拉显示的就是「未开启」，保存也按「未开启」写：
        // 不传状态时后端会按「有实际结束时间→已完成，否则→进行中」推断，
        // 那会让表格里明明写着「未开启」的一行落库成「进行中」。
        lcqk: pick('lcqk', STATUS.notStarted),
        lckssj: row.lckssj || null,
        yjjssj: row.yjjssj || null,
        lcjssj: row.lcjssj || null,
        czwtlx: row.czwtlx || null,
        jtwt: pick('jtwt', null) || null,
        gzjy: row.gzjy || null,
        lrdw: row.lrdw || null,
        lrr: row.lrr || null,
        lxdh: row.lxdh || null
      }
    },

    /**
     * 拼「整行快照」前的取数收口。
     *
     * ★ 平铺接口（/processList）只返回展示字段，没有 gzjy / lrdw / lxdh / czwtlx 这些
     *   「弹窗里才录」的列；直接拿平铺行去拼快照，会把它们当空值一起提交。
     *   这里用树里同一个 lcId 的行补齐（树的行来自 /processTree，字段齐全），
     *   再用平铺行覆盖 —— 平铺行带着用户刚改的值，优先级更高。
     */
    patchSource (row) {
      const lcId = row && row.lcId
      const base = lcId ? this.allRows.find((item) => item.lcId === lcId) : null
      if (!base) return row
      const merged = Object.assign({}, base)
      Object.keys(row || {}).forEach((key) => {
        if (row[key] !== undefined && row[key] !== null) merged[key] = row[key]
      })
      return merged
    },

    /** 表格内改状态（选项来自 land_process_status，与弹窗同一套；树 / 平铺两个视图共用一个入口） */
    handleInlineStatus (row, status) {
      const value = status || STATUS.notStarted
      if (value === (row.lcqk || STATUS.notStarted)) return
      const source = this.patchSource(row)
      this.markPending(row.lcId, this.inlinePatch(source, { lcqk: value }))
      // ★ 业务联动提前说一声（不阻断、不改写用户的选择）：后端要求「已完成」必须有
      //   实际结束时间，而表格里没有日期输入 —— 让用户知道去哪补，别等整屏保存才吃
      //   一条失败明细。日期三个字段仍是选填，只是「已完成」这一条联动必须守住。
      if (value === STATUS.finished && !source.lcjssj) {
        toast.warning(`「${row.lcName}」标为已完成还需填实际结束时间，请点该行「编辑」补填后再整屏保存`)
      }
    },

    /** 表格内改备注（写 jtwt：这一行唯一的自由文本，见组件头注释） */
    handleInlineRemark (row, value) {
      const text = String(value === undefined || value === null ? '' : value).trim()
      if (text === String(row.jtwt || '')) return
      this.markPending(row.lcId, this.inlinePatch(this.patchSource(row), { jtwt: text }))
    },

    /* ---------------- 编辑一个事项 ---------------- */

    handleEditItem (row, stage) {
      this.itemVisible = true
      this.$nextTick(() => {
        const modal = this.$refs.itemModal
        if (!modal) return
        modal.open({
          ptId: this.facility.id,
          facilityName: this.facility.ptxmmc,
          stageName: stage.stageName,
          lcId: row.lcId,
          lcName: row.lcName,
          zgbm: row.zgbm,
          processTime: row.processTime || stage.processTime,
          lcqk: row.lcqk,
          lckssj: row.lckssj,
          yjjssj: row.yjjssj,
          lcjssj: row.lcjssj,
          czwtlx: row.czwtlx,
          jtwt: row.jtwt,
          gzjy: row.gzjy,
          lrdw: row.lrdw,
          lrr: row.lrr,
          lxdh: row.lxdh,
          filled: row.filled,
          progressId: row.progressId
        })
      })
    },

    /** 平铺视图的行没有 zgbm 之外的上下文字段，这里从阶段配置里补齐 */
    handleEditFlat (row) {
      const stage = this.stages.find((item) => item.stageId === row.stageId) ||
        this.stages.find((item) => item.stageName === row.stageName)
      this.handleEditItem(
        Object.assign({}, row, { processTime: stage ? stage.processTime : row.processTime }),
        { stageName: row.stageName, processTime: stage ? stage.processTime : row.processTime }
      )
    },

    /**
     * 弹窗保存成功：只更新**本地**，不重新拉树。
     * ★ 不重拉的原因：重拉会把用户已经改好但没提交的其它行覆盖掉。
     */
    handleItemSaved (item) {
      const patch = {
        lcqk: item.lcqk,
        lckssj: item.lckssj,
        yjjssj: item.yjjssj,
        lcjssj: item.lcjssj,
        czwtlx: item.czwtlx,
        jtwt: item.jtwt,
        gzjy: item.gzjy,
        lrdw: item.lrdw,
        lrr: item.lrr,
        lxdh: item.lxdh,
        lcName: item.lcName
      }
      this.markPending(item.lcId, patch)
      // 待保存的环节在阶段汇总里按本地推演预览（打「未保存预览」标），
      // 这里只提示数量，不假装已经落库。
      toast.info(`已暂存，还有 ${this.dirtyCount} 个环节待整屏保存`)
    },

    /**
     * 单条撤回是**直接落库**的动作（见组件头注释），所以：
     *   · 先把该环节的未保存改动从队列里摘掉（撤回后本地那份改动已经没有意义，
     *     留着它整屏保存会把刚撤回的进度又写回去）；
     *   · 再重拉一次树，让阶段汇总与逾期都按服务端口径重算。
     */
    handleItemWithdrawn (lcId) {
      if (lcId) this.$delete(this.pending, lcId)
      this.loadTree()
      this.flatRows = []
      this.$emit('changed')
    },

    /* ---------------- 整屏保存 ---------------- */

    /**
     * 整屏保存：把待保存的项逐条变成后端要的 payload。
     * ★ 必须带 lcId 与 lcName：后端按 lcId 定位环节，lcName 只用于失败明细的可读文案；
     *   没有 lcId 的项会被**静默跳过**（后端 continue），所以这里先过滤一遍并计数。
     * ★ 失败语义（清单 P2-03）：**保留编辑内容 + 恢复权威进度**。
     *   所以三条失败路径（接口层失败 / 部分失败 / 请求异常）都要 saveFailed = true 并重拉树：
     *   本地推演的百分比此时必须让位给服务端值，否则用户会以为失败的项也生效了。
     */
    handleSaveBatch () {
      const ptId = this.facility.id
      const keys = Object.keys(this.pending)
      if (!ptId || !keys.length) return

      const items = keys.map((lcId) => Object.assign({ lcId }, this.pending[lcId]))
      this.saving = true
      saveProcessBatch(ptId, items)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '整屏保存失败')
            // 一条都没落库：改动全留着，进度按服务端恢复
            this.saveFailed = true
            this.loadTree()
            return
          }
          const result = res.result || {}
          const failures = result.failures || []
          if (failures.length) {
            // ★ 部分失败必须逐条说清楚：否则用户会以为「保存成功」，
            //   而失败的几个环节其实一个字都没写进去。
            const lines = failures
              .slice(0, 3)
              .map((item) => `${item.lcName || item.lcId}：${item.reason || '保存失败'}`)
            const more = failures.length > 3 ? `；…共 ${failures.length} 条失败` : ''
            toast.warning(`${res.message || '部分环节保存失败'} —— ${lines.join('；')}${more}`)
          } else {
            toast.success(`整屏保存成功：${result.successCount || items.length} 个环节已落库`)
          }
          // 成功的部分从待保存队列里摘掉，失败的原样留着让用户改
          const failedIds = failures.map((item) => item.lcId)
          keys.forEach((lcId) => {
            if (failedIds.indexOf(lcId) === -1) {
              this.$delete(this.pending, lcId)
            }
          })
          // ★ 有失败项时退出「未保存预览」：树按后端重算后，剩下的待保存项
          //   不能再用本地推演去覆盖顶栏数字（清单要求「失败时恢复权威进度」）。
          this.saveFailed = failures.length > 0
          // 落库后阶段汇总与逾期都该重算，重拉一次树与平铺表
          this.loadTree()
          this.flatRows = []
          if (this.view === 'flat') this.loadFlat()
          if ((result.successCount || 0) > 0) {
            this.$emit('changed')
          }
        })
        .catch(() => {
          // 请求层已提示；改动留着，进度恢复成服务端值
          this.saveFailed = true
          this.loadTree()
        })
        .finally(() => {
          this.saving = false
        })
    }
  },
  watch: {
    /** 切到平铺视图时懒加载（用户可能只看树，没必要一开弹窗就打两次接口） */
    view (value) {
      if (value === 'flat') this.loadFlat()
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.process-tree {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-2);
  height: 100%;
  min-height: 0;

  &__tags {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  /* ---------------- ① 顶部指标条 ---------------- */
  &__summary {
    display: flex;
    align-items: center;
    gap: var(--screen-space-5);
    flex-wrap: wrap;
    padding: var(--screen-space-2) var(--screen-space-3);
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
    box-shadow: var(--screen-shadow-inset);
  }

  &__progress {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 0 0 200px;
    min-width: 0;
  }

  /** 完成度数字 + 「未保存预览」标签同排：标签必须紧贴数字，才不会被当成页面级提示 */
  &__progress-head {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  &__progress-value {
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-number-sm);
    font-weight: 700;
    line-height: 1;
    color: var(--screen-accent);
  }

  &__progress-label {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__progress-track {
    display: block;
    height: 4px;
    margin-top: 4px;
    background: var(--screen-bar-track);
    border-radius: var(--screen-radius-pill);
    overflow: hidden;
  }

  &__progress-fill {
    display: block;
    height: 100%;
    min-width: 2px;
    background-image: linear-gradient(90deg, var(--screen-bar-from) 0%, var(--screen-bar-to) 100%);
    border-radius: var(--screen-radius-pill);
    transition: width var(--screen-duration) var(--screen-ease);
  }

  &__stats {
    display: flex;
    align-items: center;
    gap: var(--screen-space-5);
    flex: 1 1 auto;
    flex-wrap: wrap;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__stat {
    display: flex;
    align-items: baseline;
    gap: 4px;
    min-width: 0;
  }

  &__stat-label {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    white-space: nowrap;
  }

  &__stat-value {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-md);
    color: var(--screen-text);

    &.is-ok {
      color: var(--screen-success);
    }

    &.is-primary {
      color: var(--screen-accent);
    }

    &.is-error {
      color: var(--screen-danger);
    }
  }

  &__stat-unit {
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__views {
    flex: 0 0 auto;
  }

  /* ---------------- ② 视图 ---------------- */
  &__pane {
    display: flex;
    flex-direction: column;
    flex: 1 1 auto;
    min-height: 0;
  }

  /** ScreenLoading 的根节点不是弹性子项，必须显式撑满（README 踩坑 9） */
  &__content {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    padding-right: var(--screen-space-1);
    .screen-scrollbar();
  }

  /**
   * 每个阶段一张可折叠面板。
   * ★ flex: 0 0 auto —— 阶段面板不能平分剩余高度：
   *   事项数少的阶段（3 个）按内容高度，事项多的（8 个）自己撑开，
   *   整块区域由 __content 统一滚动，这样一屏能看到最多内容。
   */
  &__stage {
    flex: 0 0 auto;
  }

  &__stage-percent {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
  }

  &__stage-overdue {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    font-size: var(--screen-font-xs);
    color: var(--screen-danger);
  }

  &__block {
    display: flex;
    flex-direction: column;
    flex: 1 1 0;
    min-height: 0;
    padding: var(--screen-space-2) var(--screen-space-3) var(--screen-space-1);
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
    box-shadow: var(--screen-shadow-inset);
  }

  &__block-title {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    margin: 0 0 var(--screen-space-1);
    font-size: var(--screen-font-sm);
    font-weight: 600;
    color: var(--screen-text);

    &::before {
      content: '';
      flex: none;
      width: 3px;
      height: 12px;
      border-radius: var(--screen-radius-pill);
      background: linear-gradient(180deg, var(--screen-accent) 0%, var(--screen-accent-deep) 100%);
    }
  }

  &__block-sub {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  /* ---------------- 单元格 ---------------- */
  &__lc-name {
    display: block;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__lc-path {
    display: block;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__ellipsis {
    display: block;
    .screen-ellipsis();
  }

  &__dates {
    display: flex;
    flex-direction: column;
    gap: 1px;
  }

  &__date {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text);

    &.is-sub {
      color: var(--screen-text-mute);
    }
  }

  &__overdue {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    font-size: var(--screen-font-xs);
    color: var(--screen-danger);
  }

  &__mute {
    color: var(--screen-text-mute);
  }

  &__num {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
  }

  &__link {
    .screen-link-action();
  }

  &__dirty {
    font-size: var(--screen-font-xs);
    color: var(--screen-warning);
  }
}

@media (prefers-reduced-motion: reduce) {
  .process-tree__progress-fill {
    transition: none;
  }
}
</style>
