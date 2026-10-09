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
          <span class="process-tree__progress-value">{{ (tree && tree.percent) || 0 }}%</span>
          <span class="process-tree__progress-label">整体完成度（已剔除「不涉及」）</span>
          <span class="process-tree__progress-track" aria-hidden="true">
            <i class="process-tree__progress-fill" :style="{ width: ((tree && tree.percent) || 0) + '%' }" />
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
            v-for="stage in stages"
            :key="stage.stageId"
            class="process-tree__stage"
            collapsible
            :title="stage.stageName"
            :sub-title="stageSubTitle(stage)"
          >
            <template #extra>
              <screen-tag :tone="stageStatusTone(stage.status)" size="sm">{{ stage.status }}</screen-tag>
              <span class="process-tree__stage-percent">{{ stage.percent || 0 }}%</span>
              <span
                v-if="stage.overdueCount"
                class="process-tree__stage-overdue"
                :title="`本阶段有 ${stage.overdueCount} 个环节逾期`"
              >
                <screen-icon name="alert-triangle" :size="12" />
                逾期 {{ stage.overdueCount }}
              </span>
            </template>

            <screen-data-table
              :columns="itemColumns"
              :data="rowsOf(stage)"
              row-key="lcId"
              :min-width="1240"
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

              <template #lcqk="{ row }">
                <screen-tag :tone="processStatusTone(row.lcqk)" size="sm">{{ row.lcqk || '未开启' }}</screen-tag>
                <span v-if="!row.filled" class="process-tree__unfilled">未填报</span>
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
                  <button type="button" class="process-tree__link" @click.stop="handleEditItem(row, stage)">
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
            :data="flatRows"
            :loading="flatLoading"
            row-key="lcId"
            :min-width="1360"
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

            <template #lcqk="{ row }">
              <screen-tag :tone="processStatusTone(row.lcqk)" size="sm">{{ row.lcqk || '未开启' }}</screen-tag>
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
              <button type="button" class="process-tree__link" @click.stop="handleEditFlat(row)">编辑</button>
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
import { processStatusTone, stageStatusTone } from '../constants'

export default {
  name: 'ProcessTreePanel',
  components: {
    ScreenModal,
    ScreenPanel,
    ScreenTabs,
    ScreenTag,
    ScreenButton,
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
      view: 'tree',
      facility: {},
      tree: null,
      flatRows: [],
      /** 本地待保存的变更：{ [lcId]: payload } —— 只在内存里，切页签不丢，关弹窗清空 */
      pending: {},
      itemVisible: false,
      viewTabs: [
        { key: 'tree', label: '按阶段' },
        { key: 'flat', label: '平铺表格' }
      ],
      itemColumns: [
        { key: 'lcName', title: '环节事项', width: 300, type: 'slot' },
        { key: 'zgbm', title: '主管部门', width: 150, type: 'slot' },
        { key: 'lcqk', title: '环节情况', width: 150, type: 'slot', align: 'center' },
        { key: 'dates', title: '开始 / 预计结束 / 实际结束', width: 260, type: 'slot' },
        { key: 'overdue', title: '逾期', width: 120, type: 'slot', align: 'center' },
        { key: 'attachmentCount', title: '附件', width: 80, type: 'slot', align: 'center' },
        { key: 'action', title: '操作', width: 110, type: 'slot', align: 'center' }
      ],
      flatColumns: [
        { key: 'index', title: '#', width: 60, type: 'index', align: 'center' },
        { key: 'stageName', title: '所属阶段', width: 200, type: 'slot' },
        { key: 'lcName', title: '环节事项', width: 280, type: 'slot' },
        { key: 'zgbm', title: '主管部门', width: 150, type: 'slot' },
        { key: 'lcqk', title: '环节情况', width: 120, type: 'slot', align: 'center' },
        { key: 'dates', title: '开始 / 预计 / 实际', width: 260, type: 'slot' },
        { key: 'overdue', title: '逾期', width: 120, type: 'slot', align: 'center' },
        { key: 'lrr', title: '录入人', width: 100, formatter: (value) => value || '—' },
        { key: 'action', title: '操作', width: 100, type: 'slot', align: 'center' }
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
     * 顶部指标条。
     * ★ 七个数全部来自后端顶层汇总字段，顺序按「用户最关心的排前面」：
     *   总事项 → 已完成 → 进行中 → 未开启 → 不涉及 → 已填报 → 逾期。
     *   逾期单独用 danger 色：它是唯一需要立刻行动的指标。
     */
    summaries () {
      const data = this.tree || {}
      return [
        { key: 'total', label: '环节总数', value: data.totalItems || 0, unit: '个' },
        { key: 'finished', label: '已完成', value: data.finishedItems || 0, unit: '个', tone: 'ok' },
        { key: 'running', label: '进行中', value: data.runningItems || 0, unit: '个', tone: 'primary' },
        { key: 'notStarted', label: '未开启', value: data.notStartedItems || 0, unit: '个' },
        { key: 'notInvolved', label: '不涉及', value: data.notInvolvedItems || 0, unit: '个' },
        { key: 'filled', label: '已填报', value: data.filledItems || 0, unit: '个' },
        { key: 'overdue', label: '逾期', value: data.overdueItems || 0, unit: '个', tone: 'error' }
      ]
    }
  },
  methods: {
    processStatusTone,
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

    /** 阶段标题右侧的补充说明（已填 / 总数 + 标准时长） */
    stageSubTitle (stage) {
      const parts = [`已填 ${stage.filledCount || 0} / ${stage.itemCount || 0} 个事项`]
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
      this.$set(this.pending, item.lcId, patch)
      // 待保存的环项在阶段汇总里还算不出来（那要后端重算），
      // 所以这里只提示数量，不假装阶段状态变了。
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
          // 落库后阶段汇总与逾期都该重算，重拉一次树与平铺表
          this.loadTree()
          this.flatRows = []
          if (this.view === 'flat') this.loadFlat()
          if ((result.successCount || 0) > 0) {
            this.$emit('changed')
          }
        })
        .catch(() => {
          // 请求层已提示
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

  &__unfilled {
    display: block;
    margin-top: 2px;
    font-size: var(--screen-font-xs);
    color: var(--screen-warning);
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
