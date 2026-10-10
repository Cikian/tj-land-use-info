<template>
  <!--
    LandDetailModal 经营性用地详情
    --------------------------------
    全屏弹窗 + 分组描述列表 + 「变更履历」页签。

    ★ 为什么用全屏弹窗而不是窄抽屉：
      详情要同时展示 6 组共 34 个字段与一份字段级履历表，抽屉宽度不够会到处折行；
      全屏弹窗保留「浮层」的上下文，又能给履历表足够的横向空间。

    ★ 为什么用「分组标题 + 分隔线」而不是卡片套卡片：
      描述列表本身是「一段一段的清单」，卡片会把留白吃掉、边界过多，
      读起来像表格而不是详情。分组边界靠竖条标题 + 留白表达即可。
      版式与 ArchiveDetailModal 的「方案 A 左右分栏」同一语言。

    ★ 履历为什么在详情里而不是单独的入口：
      用户看到一条宗地时最常问的两句是「这是什么」和「谁改过」。
      前者是描述列表、后者是履历，放在同一个弹窗的两个页签里，
      不必先关掉详情再去别处找履历。

    公开方法：
      open(record, tab)  按列表行打开；tab='history' 时直接落到履历页签
    事件：
      edit(detail)            点「编辑」时抛出，由列表页接手打开编辑弹窗
      facilities(detail)      点「查看配套」时抛出，由列表页跳到配套面板
  -->
  <screen-modal
    :visible.sync="visible"
    title="经营性用地详情"
    fullscreen
    :show-footer="false"
    :body-max-height="null"
    @cancel="handleClose"
  >
    <template #head-extra>
      <div class="land-detail__tags">
        <screen-tag v-if="detail && detail.xmfl" :tone="detail.xmfl === '市级项目' ? 'success' : 'info'" size="sm">
          {{ detail.xmfl }}
        </screen-tag>
        <screen-tag v-if="detail && detail.xzqh" tone="muted" size="sm">{{ detail.xzqh }}</screen-tag>
        <screen-tag v-if="detail && detail.ptsfqq" :tone="detail.ptsfqq === '是' ? 'success' : 'warning'" size="sm">
          配套{{ detail.ptsfqq === '是' ? '齐全' : '不齐全' }}
        </screen-tag>
        <screen-tag v-if="history.length" tone="info" size="sm">变更 {{ history.length }} 次</screen-tag>
      </div>

      <screen-button size="sm" icon="edit" :disabled="!detail" @click="handleEdit">编辑</screen-button>
      <screen-button size="sm" icon="layers" :disabled="!detail" @click="handleFacilities">
        查看该宗地配套
      </screen-button>
    </template>

    <div class="land-detail">
      <!-- 标题行：宗地编号 + 地块名称 -->
      <header class="land-detail__head">
        <span class="land-detail__no">{{ (detail && detail.crzdbh) || '—' }}</span>
        <h3 class="land-detail__name">{{ (detail && detail.dkmc) || '加载中…' }}</h3>
      </header>

      <screen-tabs v-model="activeTab" :tabs="tabs" class="land-detail__tabs" />

      <screen-loading
        class="land-detail__content"
        :loading="loading"
        text="正在加载宗地详情…"
        :overlay="false"
      >
        <!-- ================= 基本信息 ================= -->
        <div v-show="activeTab === 'base'" class="land-detail__pane">
          <div class="land-detail__two-col">
            <div class="land-detail__main">
              <section
                v-for="group in baseGroups"
                :key="group.key"
                class="land-detail__section"
              >
                <h4 class="land-detail__section-title">
                  {{ group.title }}
                  <span class="land-detail__section-kicker">共 {{ group.items.length }} 项</span>
                </h4>
                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="group.items"
                  :columns="group.columns || 2"
                  label-width="128px"
                />
              </section>
            </div>

            <div class="land-detail__sidebar">
              <section class="land-detail__section">
                <h4 class="land-detail__section-title">
                  录入信息
                  <span class="land-detail__section-kicker">谁在什么时候录的</span>
                </h4>
                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="entryItems"
                  :columns="1"
                  label-width="104px"
                />
              </section>

              <section class="land-detail__section">
                <h4 class="land-detail__section-title">
                  说明与备注
                  <span class="land-detail__section-kicker">长文本独占整行</span>
                </h4>
                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="remarkItems"
                  :columns="1"
                  label-width="104px"
                />
              </section>
            </div>
          </div>
        </div>

        <!-- ================= 附件（按材料类型分组的目录树） ================= -->
        <div v-show="activeTab === 'files'" class="land-detail__pane">
          <section class="land-detail__block land-detail__block--grow">
            <h4 class="land-detail__block-title">
              宗地附件
              <span class="land-detail__block-sub">
                共 {{ attachmentTree.totalFiles || 0 }} 个 · {{ attachmentSizeText }}
                · 按材料类型分组；上传/删除请到「配套附件管理」页签
              </span>
            </h4>

            <!--
              ★ 与配套详情同一口径：按材料类型分组展示，一眼看出缺哪类材料。
              ★ editable=false：详情页只读，写操作统一在附件管理页，
                这样留痕与权限口径只有一处。
            -->
            <attachment-tree-view
              :tree="attachmentTree"
              :loading="attachmentLoading"
              :editable="false"
              empty-text="该宗地还没有附件"
              @preview="previewAttachment"
              @download="downloadAttachment"
            />
          </section>
        </div>

        <!-- ================= 变更履历 ================= -->
        <div v-show="activeTab === 'history'" class="land-detail__pane">
          <section class="land-detail__block land-detail__block--grow">
            <h4 class="land-detail__block-title">
              字段级变更履历
              <span class="land-detail__block-sub">
                共 {{ history.length }} 条 · 新增 / 修改 / 移除 / 恢复都会留痕
              </span>
            </h4>

            <div v-if="!history.length && !historyLoading" class="land-detail__empty">
              <screen-empty text="该宗地还没有变更记录" description="首次录入后，任何一次保存都会在这里留下字段级差异" />
            </div>

            <screen-data-table
              v-else
              :columns="historyColumns"
              :data="historyRows"
              :loading="historyLoading"
              row-key="key"
              :min-width="1180"
              :animated="false"
              row-clickable
              empty-text="暂无变更记录"
              @row-click="handleHistoryRowClick"
            >
              <template #action="{ row }">
                <screen-tag :tone="actionTone(row.action)" size="sm">{{ row.actionText || row.action }}</screen-tag>
              </template>
              <template #changeCount="{ row }">
                <span class="land-detail__num">{{ row.changeCount || 0 }} 项</span>
              </template>
              <template #summary="{ row }">
                <span class="land-detail__summary" :title="row.summary || ''">{{ row.summary || '—' }}</span>
              </template>
              <template #operator="{ row }">
                <span>{{ row.operatorName || row.operator || '—' }}</span>
              </template>
              <template #detailAction="{ row }">
                <button
                  type="button"
                  class="land-detail__link"
                  :disabled="!((row.details || []).length)"
                  @click.stop="handleShowDetail(row)"
                >
                  查看改了什么
                </button>
              </template>
            </screen-data-table>
          </section>
        </div>
      </screen-loading>

      <!--
        字段级差异明细：与「变更留痕」页共用同一套三列展示。
        ★ 用 v-if 而不是 :visible —— 这是「弹窗里再开弹窗」，两个弹窗的对话框都是
          position: fixed 且 pointer-events: auto，内层隐藏时仍会占满全屏并吃掉点击，
          表现是「关闭明细后详情页点不动」。可见时才挂载可以彻底避免这个问题。
      -->
      <screen-modal
        v-if="detailVisible"
        :visible.sync="detailVisible"
        :title="detailTitle"
        :width="860"
        :show-footer="false"
        :body-max-height="'calc(100vh - 240px)'"
        @cancel="detailVisible = false"
      >
        <screen-data-table
          :columns="changeDetailColumns"
          :data="activeDetails"
          row-key="field"
          :min-width="640"
          :animated="false"
          empty-text="这条记录没有字段级明细"
        >
          <template #label="{ row }">
            <span>{{ row.label || row.field }}</span>
            <span class="land-detail__field-key">{{ row.field }}</span>
          </template>
          <template #before="{ row }">
            <span class="land-detail__before">{{ row.before || '（空）' }}</span>
          </template>
          <template #after="{ row }">
            <span class="land-detail__after">{{ row.after || '（空）' }}</span>
          </template>
        </screen-data-table>
      </screen-modal>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenTabs,
  ScreenTag,
  ScreenDataTable,
  ScreenDescriptions,
  ScreenButton,
  ScreenLoading,
  ScreenEmpty
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { queryLandAdminById, queryLandHistory } from '@/api/land/landAdmin'
import { queryAttachmentTree, buildAttachmentDownloadUrl } from '@/api/land/attachment'
import AttachmentTreeView from './AttachmentTreeView.vue'
import { actionTone, formatTime, formatSize, joinInfo } from '../constants'

/** 履历表格里展示的时间格式（后端给的是 yyyy-MM-dd HH:mm:ss） */
function historyTime (value) {
  return formatTime(value)
}

export default {
  name: 'LandDetailModal',
  components: {
    ScreenModal,
    ScreenTabs,
    ScreenTag,
    ScreenDataTable,
    ScreenDescriptions,
    ScreenButton,
    ScreenLoading,
    ScreenEmpty,
    AttachmentTreeView
  },
  data () {
    return {
      visible: false,
      loading: false,
      historyLoading: false,
      activeTab: 'base',
      /** 附件树（按材料类型分组）：{ groups:[...], totalFiles, totalSize, typeCount } */
      attachmentTree: { groups: [] },
      attachmentLoading: false,
      detail: null,
      history: [],
      detailVisible: false,
      detailTitle: '字段变更明细',
      activeDetails: [],
      tabs: [
        { key: 'base', label: '基本信息' },
        { key: 'files', label: '附件' },
        { key: 'history', label: '变更履历' }
      ],
      historyColumns: [
        { key: 'createTime', title: '时间', width: 170, formatter: (value) => historyTime(value) },
        { key: 'action', title: '动作', width: 100, type: 'slot', align: 'center' },
        { key: 'bizKey', title: '宗地编号', width: 190, ellipsis: true },
        { key: 'changeCount', title: '变更字段', width: 100, type: 'slot', align: 'center' },
        { key: 'summary', title: '摘要', width: 300, type: 'slot' },
        { key: 'operator', title: '操作人', width: 120, type: 'slot' },
        { key: 'detailAction', title: '明细', width: 130, type: 'slot', align: 'center' }
      ],
      changeDetailColumns: [
        { key: 'label', title: '字段', width: 220, type: 'slot' },
        { key: 'before', title: '改前', width: 200, type: 'slot' },
        { key: 'after', title: '改后', type: 'slot' }
      ]
    }
  },
  computed: {
    /**
     * 基本信息按「用户填表的顺序」分 4 组。
     * ★ 每组的 label 都用 **中文业务名**（与录入表单上的标签逐字一致），
     *   不要用实体字段名：履历里已经有 field 名，详情里再出现一遍只是噪音。
     * ★ tone: 'number' 让等宽数字字体生效（出让金 / 面积 / 日期），
     *   金额与日期纵向对齐后一眼能扫。
     */
    baseGroups () {
      const data = this.detail || {}
      return [
        {
          key: 'identity',
          title: '宗地标识',
          columns: 2,
          items: [
            { key: 'crzdbh', label: '出让宗地编号', value: data.crzdbh, tone: 'accent' },
            { key: 'dkmc', label: '地块名称', value: data.dkmc },
            { key: 'xzqh', label: '行政区划', value: data.xzqh },
            { key: 'xmfl', label: '项目分类', value: data.xmfl },
            { key: 'ghydxz', label: '规划用地性质', value: data.ghydxz },
            { key: 'xzqh2', label: '录入单位简称', value: data.xzqh2 }
          ]
        },
        {
          key: 'transfer',
          title: '出让与面积',
          columns: 2,
          items: [
            { key: 'crj', label: '出让金（亿元）', value: data.crj, tone: 'number' },
            { key: 'crsj', label: '出让时间', value: data.crsj, tone: 'number' },
            { key: 'kjsydmj', label: '可建设用地面积（㎡）', value: data.kjsydmj, tone: 'number' },
            { key: 'zydmj', label: '总用地面积（㎡）', value: data.zydmj, tone: 'number' },
            { key: 'jsmj', label: '建设面积（㎡）', value: data.jsmj, tone: 'number' },
            { key: 'nrcbdptf', label: '纳入成本的配套费（万元）', value: data.nrcbdptf, tone: 'number' },
            { key: 'wcd', label: '完成度', value: data.wcd, tone: 'number' },
            { key: 'srr', label: '受让人', value: data.srr },
            { key: 'htydjfsj', label: '合同约定交付时间', value: data.htydjfsj, tone: 'number' },
            { key: 'lpmc', label: '楼盘名称', value: data.lpmc },
            { key: 'lpjfsj', label: '楼盘交付时间', value: data.lpjfsj, tone: 'number' }
          ]
        },
        {
          key: 'boundary',
          title: '四至',
          columns: 4,
          items: [
            { key: 'dz', label: '东至', value: data.dz },
            { key: 'xz', label: '西至', value: data.xz },
            { key: 'nz', label: '南至', value: data.nz },
            { key: 'bz', label: '北至', value: data.bz }
          ]
        },
        {
          key: 'prepare',
          title: '土地整理与配套',
          columns: 2,
          items: [
            { key: 'tdzldw', label: '土地整理单位', value: data.tdzldw },
            { key: 'tdzljhxdwjh', label: '整理计划下达文件号', value: data.tdzljhxdwjh, stack: true },
            { key: 'tdzljh', label: '土地整理计划', value: data.tdzljh },
            { key: 'ptsfqq', label: '配套是否齐全', value: data.ptsfqq },
            { key: 'ptqkh', label: '配套情况函', value: data.ptqkh },
            { key: 'ptcbh', label: '配套筹备函', value: data.ptcbh },
            { key: 'crzdtxsj', label: '出让宗地图形数据（shp）', value: data.crzdtxsj },
            { key: 'ptjsnr', label: '配套建设内容', value: data.ptjsnr, stack: true }
          ]
        }
      ]
    },
    entryItems () {
      const data = this.detail || {}
      return [
        { key: 'lrdw', label: '录入单位', value: data.lrdw },
        { key: 'lrr', label: '录入人', value: data.lrr },
        { key: 'lxdh', label: '联系电话', value: data.lxdh, tone: 'number' },
        { key: 'createTime', label: '创建信息', value: joinInfo(data.createTime, data.createBy) },
        { key: 'updateTime', label: '最后更新', value: joinInfo(data.updateTime, data.updateBy) }
      ]
    },
    remarkItems () {
      const data = this.detail || {}
      return [
        { key: 'zlqsnrsm', label: '资料缺失说明', value: data.zlqsnrsm, stack: true },
        { key: 'beizhu', label: '备注', value: data.beizhu, stack: true }
      ]
    },
    /** 履历行加稳定 key（ScreenDataTable 的 row-key 需要唯一值） */
    historyRows () {
      return (this.history || []).map((item, index) => Object.assign({ key: item.id || `h-${index}` }, item))
    },
    /** 附件合计大小的可读文本（后端已算好 totalSize，前端只负责格式化） */
    attachmentSizeText () {
      return formatSize(Number(this.attachmentTree.totalSize || 0))
    }
  },
  methods: {
    actionTone,

    /* ---------------- 对外入口 ---------------- */

    open (record, tab) {
      if (!record || !record.id) {
        toast.warning('缺少宗地主键，无法查看详情')
        return
      }
      this.visible = true
      // ★ 三个页签都要能直接打开（列表上「附件」入口会传 'files'）
      this.activeTab = (tab === 'history' || tab === 'files') ? tab : 'base'
      this.detail = null
      this.history = []
      this.attachmentTree = { groups: [] }
      this.detailVisible = false
      this.load(record.id)
    },

    load (id) {
      this.loading = true
      queryLandAdminById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '宗地详情加载失败')
            this.visible = false
            return
          }
          this.detail = res.result || {}
          // 履历与附件都不阻塞主信息展示，并行拉取
          this.loadHistory(id)
          this.loadAttachments(id)
        })
        .finally(() => {
          this.loading = false
        })
    },

    /**
     * 附件树（按材料类型分组）。
     * ★ 走 /attachment/tree 而不是按业务查列表：详情页要的是
     *   「这个宗地的材料按类型怎么分布」，列表给不出这个结构；
     *   树里已带每个类型的小计与总数，前端不必再累加。
     */
    loadAttachments (bizId) {
      this.attachmentLoading = true
      queryAttachmentTree('land', bizId)
        .then((res) => {
          if (!res || !res.success) {
            this.attachmentTree = { groups: [] }
            return
          }
          this.attachmentTree = res.result || { groups: [] }
        })
        .catch(() => {
          this.attachmentTree = { groups: [] }
        })
        .finally(() => {
          this.attachmentLoading = false
        })
    },

    downloadAttachment (row) {
      if (!row || !row.id) {
        toast.warning('该附件还没有落库，无法下载')
        return
      }
      window.open(buildAttachmentDownloadUrl(row.id), '_blank')
    },

    /**
     * 预览附件：复用下载地址（浏览器能渲染的当场显示，不能渲染的走下载）。
     * 详情页不内嵌预览弹窗，避免与附件管理页出现两套预览实现。
     */
    previewAttachment (row) {
      this.downloadAttachment(row)
    },

    loadHistory (id) {
      this.historyLoading = true
      queryLandHistory(id, 100)
        .then((res) => {
          if (!res || !res.success) {
            this.history = []
            return
          }
          this.history = res.result || []
        })
        .catch(() => {
          this.history = []
        })
        .finally(() => {
          this.historyLoading = false
        })
    },

    /* ---------------- 交互 ---------------- */

    /** 点击整行等价于点「查看改了什么」（有明细时才有意义） */
    handleHistoryRowClick (row) {
      if (row && (row.details || []).length) {
        this.handleShowDetail(row)
      }
    },

    handleShowDetail (row) {
      const details = (row && row.details) || []
      if (!details.length) {
        toast.info('这条记录没有字段级明细（例如「移除 / 恢复」只记动作）')
        return
      }
      this.activeDetails = details.map((item, index) =>
        Object.assign({ key: item.field || `d-${index}` }, item)
      )
      this.detailTitle = `字段变更明细 · ${row.actionText || row.action || ''} ${historyTime(row.createTime)}`
      this.detailVisible = true
    },

    handleEdit () {
      if (this.detail) this.$emit('edit', this.detail)
    },

    handleFacilities () {
      if (this.detail) this.$emit('facilities', this.detail)
    },

    handleClose () {
      this.visible = false
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.land-detail {
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

  &__head {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
    padding-bottom: var(--screen-space-2);
    border-bottom: 1px solid var(--screen-border-soft);
  }

  &__no {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-lg);
    font-weight: 600;
    color: var(--screen-accent);
    text-shadow: 0 0 10px var(--screen-accent-glow);
  }

  &__name {
    margin: 0;
    min-width: 0;
    font-size: var(--screen-font-lg);
    font-weight: 600;
    color: var(--screen-text);
  }

  &__tabs {
    flex: 0 0 auto;
  }

  /**
   * ScreenLoading 的根节点是普通块级元素，作为 flex 子项默认不拉伸，
   * 会导致里面的面板/表格拿不到高度而塌陷（README 踩坑 9）。这里显式让它撑满。
   */
  &__content {
    display: flex;
    flex-direction: column;
    flex: 1 1 auto;
    min-height: 0;
  }

  &__pane {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    .screen-scrollbar();
  }

  /*
    左右分栏：主栏给字段最多的「宗地标识 / 出让与面积 / 四至 / 土地整理」，
    侧栏放短分组（录入信息 / 说明与备注）。
    align-items: stretch 让侧栏拉到与主栏等高，否则两栏底部会空一大片。
  */
  &__two-col {
    display: grid;
    grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
    align-items: stretch;
    gap: var(--screen-space-5);
    min-width: 0;
    padding-right: var(--screen-space-1);

    @media (max-width: 1280px) {
      grid-template-columns: minmax(0, 1fr);
      gap: var(--screen-space-3);
    }
  }

  &__main {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-4);
    min-width: 0;
  }

  &__sidebar {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-5);
    min-width: 0;
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

    // 同一个容器里第二个分组起：加一条细线切开，避免两组字段连成一片
    & + & {
      padding-top: var(--screen-space-3);
      border-top: 1px solid var(--screen-border-soft);
    }
  }

  &__section-title {
    display: flex;
    align-items: center;
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

  &__section-kicker {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  /* 履历区卡片：内容本身是表格，需要卡片把表格与页面背景切开 */
  &__block {
    display: flex;
    flex-direction: column;
    min-width: 0;
    min-height: 0;
    padding: var(--screen-space-2) var(--screen-space-3) var(--screen-space-1);
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
    box-shadow: var(--screen-shadow-inset);

    &--grow {
      flex: 1 1 0;
      min-height: 240px;
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

  &__block-sub {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  &__empty {
    padding: var(--screen-space-5) 0;
  }

  &__num {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text);
  }

  &__summary {
    display: block;
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__link {
    .screen-link-action();
  }

  /* ---------------- 字段级明细 ---------------- */
  &__field-key {
    display: block;
    font-family: var(--screen-font-number-family);
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__before {
    color: var(--screen-text-mute);
    text-decoration: line-through;
  }

  &__after {
    color: var(--screen-success);
    font-weight: 600;
  }
}

/*
  描述列表的字号 / 行距统一在这里收口，只作用于详情内的分组，
  不影响履历页签的表格。
  层级靠**颜色**而不是字号：标签弱色定位、值白色承载数据；
  字号与行高一致是对齐的前提（见 ScreenDescriptions 的 is-wrap 分支）。
*/
.land-detail__section {
  --sd-row-pad: 7px;
  --sd-pair-gap: var(--screen-space-3);
  --sd-value-size: 14px;
  --sd-wrap-line-h: 21px;

  .screen-descriptions__label-text {
    font-size: 14px;
    color: var(--screen-text-mute);
  }
}

/* 窄窗兜底：极窄时「出让与面积」的两列并排也收成一列 */
@media (max-width: 900px) {
  .land-detail__section {
    --sd-cols: 1;
  }
}
</style>
