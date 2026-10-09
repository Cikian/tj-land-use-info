<template>
  <!--
    EscalationDetailModal 提级论证项目详情（只读全屏弹窗）
    --------------------------------
    对应设计文档 5.2：从列表 / 台账 / 审批页都能打开同一个详情。

    三个页签：
      base       基本信息（含**独立的「关联地块信息（选填）」分组**）
      materials  提级论证材料（只读，可下载 / 预览）
      records    审核意见记录（append-only，时间倒序）

    ★ 只读：登记意见放在「提级论证审批」页做（那是一套三栏工作台，登记时能看到项目全貌），
      详情弹窗只提供「查看 + 打印 + 去编辑」。详情与审批页共用 EscalationMaterialTable
      的只读模式，避免两处材料表各写一遍。

    版式（本次重做 · 方案 A「左右分栏 + 分组标题」）：
      - 基本信息改成左右两栏：
          左栏（1.4fr）项目基本信息（2 列）+ 提级论证信息（长文本通栏）；
          右栏（1fr）  关联地块信息（选填）+ 论证结果与办理状态，各 1 列；
        两栏之间一条极细竖线，≤1280px 自动降为单栏（竖线转横线）。
      - 每个分组 = 「青色竖条 + 分组标题 + 可选说明」，不再用卡片套卡片：
        基本信息的边界靠分组与留白表达，卡片只留给材料 / 意见记录两个页签。
      - 描述列表开 allowWrap：**值不再截断**（旧实现 nowrap + 省略号，
        长部门名必须悬停看全文）。字号层级在弹窗自己的 `.escalation-detail__section`
        里收口：标签 14px 弱色 / 值 16px 白色，行距 7px。见文件末尾的样式块。
      - 长文本字段（项目概述 / 论证事由 / 依据 / 必要性 / 可行性 / 论证事项内容 /
        论证结论 / 专家名单 / 备注）用 stack 条目独占整行、标签在上值在下，
        既不会把某一列撑高，也不再和短字段混在同一个视觉节奏里；
      - 空的分组 / 通道直接不渲染（例如没有论证结论时不再占一整行空位）。

    事件：
      edit (detail)  点「编辑」时抛出，由调用页接手打开三步向导
  -->
  <screen-modal
    :visible.sync="visible"
    title="项目详情"
    fullscreen
    :show-footer="false"
    :body-max-height="null"
    @cancel="handleClose"
  >
    <template #head-extra>
      <div class="escalation-detail__tags">
        <screen-tag v-if="detail" :tone="statusTone(detail.status)" size="sm">
          {{ detail.status || '未办理' }}
        </screen-tag>
        <screen-tag v-if="detail && detail.argResult" :tone="argResultTone(detail.argResult)" size="sm">
          论证结果：{{ detail.argResult }}
        </screen-tag>
        <screen-tag v-else-if="detail" tone="muted" size="sm">论证结果：未登记</screen-tag>
        <screen-tag v-if="detail" tone="info" size="sm">{{ detail.materialCount || 0 }} 个材料</screen-tag>
        <screen-tag v-if="detail" tone="info" size="sm">{{ records.length }} 条意见记录</screen-tag>
      </div>

      <screen-button size="sm" icon="edit" :disabled="!detail" @click="handleEdit">编辑</screen-button>
      <screen-button size="sm" icon="download" :disabled="!detail" @click="handlePrint">打印</screen-button>
      <screen-button size="sm" icon="reload" :loading="loading" @click="reload">刷新</screen-button>
    </template>

    <div class="escalation-detail">
      <header class="escalation-detail__head">
        <span class="escalation-detail__no">{{ (detail && detail.projectNo) || '—' }}</span>
        <h3 class="escalation-detail__name">{{ (detail && detail.projectName) || '加载中…' }}</h3>
      </header>

      <screen-tabs v-model="activeTab" :tabs="tabs" class="escalation-detail__tabs" />

      <screen-loading
        class="escalation-detail__content"
        :loading="loading"
        text="正在加载项目详情…"
        :overlay="false"
      >
        <!-- ================= 基本信息 ================= -->
        <div v-show="activeTab === 'base'" class="escalation-detail__pane">
          <!--
            方案 A 版式：左右两栏 + 分组标题。
            左栏（主） 项目基本信息 + 提级论证信息 —— 字段数相当时左右重量才平衡；
            右栏（侧） 关联地块信息（选填）+ 论证结果与办理状态 —— 纵向叠放，各 1 列；
            两栏之间一条极细竖线，≤1280px 自动降为单栏（竖线转横线）。
          -->
          <div class="escalation-detail__two-col">
            <div class="escalation-detail__main">
              <section class="escalation-detail__section">
                <h4 class="escalation-detail__section-title">
                  项目基本信息
                  <span class="escalation-detail__section-kicker">
                    项目维度，共 {{ baseItems.length }} 项
                  </span>
                </h4>
                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="baseItems"
                  :columns="2"
                  label-width="112px"
                />
              </section>

              <section class="escalation-detail__section">
                <h4 class="escalation-detail__section-title">
                  提级论证信息
                  <span class="escalation-detail__section-kicker">长文本逐条展示</span>
                </h4>
                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="argItems"
                  :columns="1"
                />
              </section>
            </div>

            <div class="escalation-detail__sidebar">
              <!-- ★ 独立的「关联地块信息（选填）」分组，不与项目基本信息混排 -->
              <section class="escalation-detail__section">
                <h4 class="escalation-detail__section-title">
                  关联地块信息（选填）
                  <span class="escalation-detail__section-kicker">地块信息仅作上下文</span>
                </h4>
                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="landItems"
                  :columns="1"
                  label-width="140px"
                />
              </section>

              <section class="escalation-detail__section">
                <h4 class="escalation-detail__section-title">
                  论证结果与办理状态
                  <span class="escalation-detail__section-kicker">流程线下办理，人工登记</span>
                </h4>

                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="resultItems"
                  :columns="1"
                  label-width="140px"
                >
                  <template #status="{ value }">
                    <screen-tag :tone="statusTone(value)" size="sm">{{ value || '未办理' }}</screen-tag>
                  </template>
                  <template #argResult="{ value }">
                    <screen-tag v-if="value" :tone="argResultTone(value)" size="sm">{{ value }}</screen-tag>
                    <span v-else class="escalation-detail__muted">未登记</span>
                  </template>
                </screen-descriptions>

                <!--
                  「已办结但论证结果为空」：详情页同样给出**文字**提示（README §5），
                  不让用户只看一眼标签颜色就以为一切都登记齐了。
                -->
                <p v-if="closedWithoutResult" class="escalation-detail__warn" role="status">
                  <screen-icon name="alert-triangle" :size="13" />
                  办理状态已是「已办结」，但论证结果还没有登记，请到审批页登记论证结论
                </p>
              </section>
            </div>
          </div>
        </div>

        <!-- ================= 材料 ================= -->
        <div v-show="activeTab === 'materials'" class="escalation-detail__pane">
          <section class="escalation-detail__block escalation-detail__block--grow">
            <h4 class="escalation-detail__block-title">
              提级论证材料
              <span class="escalation-detail__block-hint">
                共 {{ (detail && detail.materialCount) || 0 }} 个，可下载 / 预览
              </span>
            </h4>
            <escalation-material-table
              v-if="detail"
              ref="materialTable"
              :project-id="detail.id"
              disabled
            />
          </section>
        </div>

        <!-- ================= 意见记录 ================= -->
        <div v-show="activeTab === 'records'" class="escalation-detail__pane">
          <section class="escalation-detail__block escalation-detail__block--grow">
            <h4 class="escalation-detail__block-title">
              审核意见记录
              <span class="escalation-detail__block-hint">
                时间倒序 · 只增不改不删，共 {{ records.length }} 条
              </span>
            </h4>

            <screen-empty
              v-if="!records.length"
              text="该项目还没有登记过审核意见"
              description="登记入口在「提级论证审批」页的右栏"
              bordered
            />

            <ul v-else class="escalation-detail__records">
              <li v-for="item in records" :key="item.id" class="escalation-detail__record">
                <div class="escalation-detail__record-head">
                  <span class="escalation-detail__record-time">
                    {{ item.recordTime || item.createTime || '—' }}
                  </span>
                  <span class="escalation-detail__record-who">
                    {{ item.recorderName || item.recorderId || '—' }}
                  </span>
                  <screen-tag tone="muted" size="sm">{{ item.recordType || '审核意见' }}</screen-tag>
                  <screen-tag v-if="item.action" :tone="actionTone(item.action)" size="sm">
                    {{ item.action }}
                  </screen-tag>
                </div>

                <p class="escalation-detail__record-opinion">{{ item.opinion }}</p>

                <div v-if="parseAttachments(item.attachmentIds).length" class="escalation-detail__record-files">
                  <button
                    v-for="file in parseAttachments(item.attachmentIds)"
                    :key="file.path"
                    type="button"
                    class="escalation-detail__record-file"
                    @click="openAttachment(file.path)"
                  >
                    <screen-icon name="paperclip" :size="12" />{{ file.name }}
                  </button>
                </div>
              </li>
            </ul>
          </section>
        </div>
      </screen-loading>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenTabs,
  ScreenTag,
  ScreenDescriptions,
  ScreenLoading,
  ScreenEmpty,
  ScreenButton,
  ScreenIcon,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { getJavaFileAccessHttpUrl } from '@/api/manageJava'
import { queryProjectById, queryRecordList } from '@/api/land/escalation'
import EscalationMaterialTable from './EscalationMaterialTable.vue'
import {
  statusTone,
  argResultTone,
  actionTone,
  formatInvestment,
  joinInfo,
  ynText,
  parseAttachments,
  isClosedWithoutResult,
} from '../constants'

/** 详情里统一用「—」占位；注意 0 是合法值，不能用 `value || '—'` */
const DASH = '—'

/** 空值判定：null / undefined / 空串都算空（0 不算空） */
function isBlank (value) {
  return value === null || value === undefined || value === ''
}

export default {
  name: 'EscalationDetailModal',
  components: {
    ScreenModal,
    ScreenTabs,
    ScreenTag,
    ScreenDescriptions,
    ScreenLoading,
    ScreenEmpty,
    ScreenButton,
    ScreenIcon,
    EscalationMaterialTable,
  },
  data () {
    return {
      visible: false,
      loading: false,
      activeTab: 'base',
      detail: null,
      records: [],
      tabs: [
        { key: 'base', label: '基本信息' },
        { key: 'materials', label: '提级论证材料' },
        { key: 'records', label: '审核意见记录' },
      ],
    }
  },
  computed: {
    /**
     * 项目基本信息：8 个短字段 + 1 个长文本。
     * 项目名称 / 建设地点 / 项目概述按「长字段」处理（stack 独占整行），
     * 否则它们会把所在列撑高，同一行其它列下方留出一大片空白。
     */
    baseItems () {
      const d = this.detail || {}
      return [
        { key: 'projectNo', label: '项目编号', value: d.projectNo, tone: 'accent' },
        { key: 'projectType', label: '项目类型', value: d.projectType },
        { key: 'declareDept', label: '申报单位', value: d.declareDept },
        { key: 'declareDate', label: '申报时间', value: d.declareDate, tone: 'number' },
        { key: 'projectScale', label: '项目规模', value: d.projectScale },
        { key: 'totalInvestment', label: '总投资（亿元）', value: this.investmentText(d.totalInvestment), tone: 'number' },
        { key: 'projectName', label: '项目名称', value: d.projectName, stack: true },
        { key: 'buildLocation', label: '建设地点', value: d.buildLocation, stack: true },
        { key: 'projectSummary', label: '项目概述', value: d.projectSummary, stack: true },
      ]
    },
    landItems () {
      const d = this.detail || {}
      return [
        { key: 'xzqh', label: '行政区划', value: d.xzqh },
        { key: 'gnq', label: '功能区', value: d.gnq },
        { key: 'ghydxz', label: '规划用地性质', value: d.ghydxz },
        { key: 'tdzlProject', label: '是否土地整理项目', value: ynText(d.tdzlProject) },
        { key: 'dkArea', label: '地块面积（㎡）', value: d.dkArea, tone: 'number' },
        { key: 'crzdbh', label: '关联出让宗地编号', value: d.crzdbh, tone: 'number' },
        { key: 'dkmc', label: '地块名称', value: d.dkmc, stack: true },
        { key: 'ptxmmc', label: '关联配套项目', value: d.ptxmmc, stack: true },
      ]
    },
    /** 提级论证信息：全部是长文本，统一 stack 整行展示 */
    argItems () {
      const d = this.detail || {}
      return [
        { key: 'argReason', label: '提级论证事由', value: d.argReason, stack: true },
        { key: 'argBasis', label: '提级论证依据', value: d.argBasis, stack: true },
        { key: 'argNecessity', label: '必要性说明', value: d.argNecessity, stack: true },
        { key: 'argFeasibility', label: '可行性说明', value: d.argFeasibility, stack: true },
        { key: 'argContent', label: '论证事项内容', value: d.argContent, stack: true },
      ]
    },
    /**
     * 论证结果与办理状态。
     * 结论 / 专家名单 / 备注在没登记时直接不渲染：空占一整行既浪费高度，
     * 也会让人误以为「这里本来就有内容」。
     */
    resultItems () {
      const d = this.detail || {}
      const items = [
        { key: 'status', label: '办理状态', value: d.status || '未办理' },
        { key: 'argResult', label: '论证结果', value: d.argResult },
        { key: 'argOrg', label: '论证组织单位', value: d.argOrg },
        { key: 'argMeetingDate', label: '论证会日期', value: d.argMeetingDate, tone: 'number' },
        { key: 'argDate', label: '论证完成日期', value: d.argDate, tone: 'number' },
        {
          key: 'counts',
          label: '材料数 / 意见记录数',
          value: `${(d.materialCount || 0)} 个 / ${this.records.length} 条`,
          tone: 'number',
        },
        { key: 'createInfo', label: '创建信息', value: joinInfo(d.createBy, d.createTime) },
      ]

      if (!isBlank(d.argConclusion)) {
        items.push({ key: 'argConclusion', label: '论证结论', value: d.argConclusion, stack: true })
      }
      if (!isBlank(d.argExpertList)) {
        items.push({ key: 'argExpertList', label: '论证专家名单', value: d.argExpertList, stack: true })
      }
      if (!isBlank(d.remark)) {
        items.push({ key: 'remark', label: '备注', value: d.remark, stack: true })
      }

      return items
    },
    closedWithoutResult () {
      return isClosedWithoutResult(this.detail)
    },
  },
  methods: {
    statusTone,
    argResultTone,
    actionTone,
    parseAttachments,

    /**
     * 打开详情。
     * @param {object} record 列表行（至少有 id）
     * @param {string} [tab] 指定落在哪个页签（台账的「材料下载」直达 materials）
     */
    open (record, tab) {
      if (!record || !record.id) {
        toast.warning('请选择要查看的项目')
        return
      }
      this.visible = true
      this.activeTab = tab && this.tabs.some((item) => item.key === tab) ? tab : 'base'
      this.detail = null
      this.records = []
      this.load(record.id)
    },

    load (id) {
      this.loading = true
      return queryProjectById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '项目详情加载失败')
            this.visible = false
            return
          }
          this.detail = res.result || null
          // 详情里若已带意见记录就直接用，否则再拉一次记录接口
          const inline = this.detail && this.detail.records
          if (inline) {
            this.records = inline
          } else {
            this.loadRecords()
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    loadRecords () {
      if (!this.detail) return Promise.resolve()
      return queryRecordList(this.detail.id).then((res) => {
        if (res && res.success) {
          this.records = res.result || []
        }
      })
    },

    reload () {
      if (this.detail) this.load(this.detail.id)
    },

    handleEdit () {
      if (!this.detail) return
      this.$emit('edit', this.detail)
    },

    handlePrint () {
      window.print()
    },

    handleClose () {
      this.visible = false
    },

    /** 供调用页主动关闭（例如点「编辑」时先关详情再开向导） */
    close () {
      this.visible = false
    },

    /* ---------------- 展示工具 ---------------- */

    /** 总投资：空值给占位符，0 是合法值（不能写 `value || '—'`） */
    investmentText (value) {
      if (isBlank(value)) return DASH
      const text = formatInvestment(value)
      return text === '' ? DASH : text
    },

    openAttachment (path) {
      const url = getJavaFileAccessHttpUrl(path)
      if (url) {
        window.open(url, '_blank')
      } else {
        toast.warning('该附件还没有可访问的地址')
      }
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.escalation-detail {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-2);
  min-width: 0;

  &__tags {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-2);
    min-width: 0;
  }

  &__head {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
    min-width: 0;
  }

  &__no {
    flex: none;
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-sm);
    letter-spacing: 0.02em;
    color: var(--screen-accent);
  }

  &__name {
    margin: 0;
    min-width: 0;
    font-size: var(--screen-font-xl);
    font-weight: 600;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__tabs {
    flex: none;
  }

  /*
    ★ ScreenLoading 的根节点不是弹性子项（README 踩坑 9）：
    放在 flex 列里必须显式 flex: 1 1 auto + min-height: 0，
    否则里面的面板 / 表格拿不到确定高度而塌陷。
  */
  &__content {
    flex: 1 1 auto;
    min-height: 0;
  }

  &__pane {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    min-width: 0;
  }

  /*
    ★ 基本信息专用版式（方案 A：左右分栏 + 分组标题）
    ------------------------------------------------------------------
    为什么这里不用 &__block 卡片：基本信息是「一段一段的清单」，
    卡片套卡片会让边界过多、留白被吃掉，读起来像表格而不是详情。
    改成「分组标题 + 分隔线」后，重心回到数据本身：
      two-col  左右两栏，主栏给字段最多的两组，侧栏给短分组
      section  一个分组 = 标题（带青色竖条）+ 内容
    ------------------------------------------------------------------ */
  &__two-col {
    display: grid;
    grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
    // ★ stretch（而不是 start）：右栏要拉到与左栏等高，
    //   否则两栏底部会空出一大片、竖分隔线也只有半截长。
    align-items: stretch;
    gap: var(--screen-space-5);
    min-width: 0;

    // 窗口变窄时降为单栏：两栏各留 ~560px，值列才不至于被省略号吃掉
    @media (max-width: 1280px) {
      grid-template-columns: minmax(0, 1fr);
      gap: var(--screen-space-3);
    }
  }

  &__main,
  &__sidebar {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-5);
    min-width: 0;
  }

  // 右栏：与左栏之间一条极细竖线，用于区分两栏而不增加卡片重量
  &__sidebar {
    padding-left: var(--screen-space-5);
    border-left: 1px solid var(--screen-border-soft);

    @media (max-width: 1280px) {
      padding-left: 0;
      padding-top: var(--screen-space-3);
      border-left: 0;
      border-top: 1px solid var(--screen-border-soft);
    }
  }

  &__section {
    min-width: 0;

    // 同栏内第二个分组起：加一条细线切开，避免相邻分组的字段连成一片
    & + & {
      padding-top: var(--screen-space-4);
      border-top: 1px solid var(--screen-border-soft);
    }
  }

  &__section-title {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-2);
    margin: 0 0 var(--screen-space-1);
    font-size: var(--screen-font-md);
    font-weight: 600;
    color: var(--screen-text);

    // 标题前的青色竖条，与首页面板标题同一视觉语言
    &::before {
      content: '';
      flex: none;
      width: 3px;
      height: 15px;
      border-radius: var(--screen-radius-pill);
      background: linear-gradient(180deg, var(--screen-accent) 0%, var(--screen-accent-deep) 100%);
      box-shadow: 0 0 8px var(--screen-accent-glow);
    }
  }

  // 标题右侧的说明文字：说明数量 / 口径，不抢标题
  &__section-kicker {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  /*
    卡片化：边更轻、内边距更紧，让「数据」而不是「容器」占据视觉重心。
    只给「材料 / 意见记录」这两个页签用 —— 它们的内容本身是表格 / 列表，
    需要卡片把内容和页面背景切开。
  */
  &__block {
    min-width: 0;
    padding: var(--screen-space-2) var(--screen-space-3) var(--screen-space-1);
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
    box-shadow: var(--screen-shadow-inset);
    // ⚠ 不加 backdrop-filter：没有硬件加速时，弹窗里每一块卡片做一次背景模糊
    //   会让打开弹窗/切页签掉到 200ms 以上（见 screen-mixins.less 的实测）

    // 材料表 / 意见记录这类列表要吃掉剩余高度
    &--grow {
      display: flex;
      flex-direction: column;
      flex: 1 1 auto;
      min-height: 0;
    }
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

  &__block-hint {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  &__muted {
    color: var(--screen-text-mute);
  }

  &__warn {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: var(--screen-space-2) 0 0;
    padding: var(--screen-space-2) var(--screen-space-3);
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-warning);
    background: var(--screen-row-alt);
    border-left: 3px solid var(--screen-warning);
    border-radius: var(--screen-radius-sm);
  }

  /* ---------------- 意见记录 ---------------- */
  &__records {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__record {
    padding: var(--screen-space-3) var(--screen-space-4);
    margin-bottom: var(--screen-space-2);
    background: var(--screen-row-alt);
    border: 1px solid var(--screen-border-soft);
    border-left: 3px solid var(--screen-accent);
    border-radius: var(--screen-radius);
  }

  &__record-head {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
    margin-bottom: 6px;
    font-size: var(--screen-font-xs);
  }

  &__record-time {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-sub);
  }

  &__record-who {
    color: var(--screen-text);
  }

  &__record-opinion {
    margin: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-text-sub);
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__record-files {
    display: flex;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
    margin-top: var(--screen-space-2);
  }

  &__record-file {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    background: none;
    border: 0;
    cursor: pointer;
    .screen-link-action();
  }
}

/*
  基本信息的描述列表：字号 / 行距 / 标签与值的间距统一在这里收口，
  只作用于本弹窗的基本信息两栏（.escalation-detail__section 内部），
  不影响材料 / 意见记录两个页签。

  层级关系（详情页要「一眼能扫，不喧哗」）：
    标签与值同为 14px，靠**颜色**分层而不是字号：
      标签 = 弱色（--screen-text-mute），定位用
      值   = 白色，主要数据
    字号相同 + 同一个绝对行高（--sd-wrap-line-h）是**对齐的前提**：
    两侧字号或行高只要有一个不同，文字基线就会错开（见该组件 <style> 的说明）。
  这些变量都由 ScreenDescriptions 的 is-wrap 分支消费。
*/
.escalation-detail__section {
  // 这些变量会被 ScreenDescriptions 的 is-wrap 分支读取（见该组件 <style>）。
  // 能生效的前提是组件那边**没有**在 .screen-descriptions 上直接声明同名变量——
  // 「元素自身的直接声明」永远优先于「继承来的值」，特异性管不了继承。
  --sd-row-pad: 7px;
  --sd-pair-gap: var(--screen-space-3);
  --sd-value-size: 14px;
  --sd-wrap-line-h: 21px;

  .screen-descriptions__label-text {
    font-size: 14px;
    color: var(--screen-text-mute);
  }
}

/* 窄窗兜底：两栏本身在 1280px 降为单栏（见 &__two-col），
   这里再兜一层——极窄时「项目基本信息」的两列并排也收成一列。 */
@media (max-width: 900px) {
  .escalation-detail__section {
    --sd-cols: 1;
  }
}
</style>
