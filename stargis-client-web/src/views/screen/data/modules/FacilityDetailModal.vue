<template>
  <!--
    FacilityDetailModal 配套项目详情
    --------------------------------
    全屏弹窗 + 四个页签：
      基本信息 —— 53 个字段按 6 组（标识与属性 / 参建单位 / 前期审批与进度 /
                  档案要件（历史，只读）/ 说明 / 录入信息）分组展示
      六阶段进度 —— 直接挂 ProcessTreePanel（与列表里的入口是同一个组件）
      附件 —— 该配套的附件列表（按 bizType=facility 查）
      变更履历 —— 字段级变更（走 `/land/data/recycle/history`，
                  这是「按业务对象取履历」的唯一入口，与宗地用的
                  `/landAdmin/history` 同一份数据、不同通道）

    ★ 「档案要件」为什么单独一组并且是只读：
      那 11 列在旧表里是「有没有这份文件」的是/否标志位，不是文件本身。
      详情页如果把标志位渲染成可点的链接，用户会以为点开能看到文件 ——
      实际文件在新系统的统一附件模块里，标志位只是历史口径。
      所以这一组老实标明「历史标志位」，并在旁边给出「看实际文件请翻附件页签」。

    公开方法：
      open(record, tab)  按列表行打开；tab 可指定初始页签
    事件：
      edit(detail)      点「编辑」时抛出
      remove(detail)    点「移除」时抛出
  -->
  <screen-modal
    :visible.sync="visible"
    title="配套项目详情"
    fullscreen
    :show-footer="false"
    :body-max-height="null"
    @cancel="handleClose"
  >
    <template #head-extra>
      <div class="facility-detail__tags">
        <screen-tag v-if="detail && detail.xmfl" :tone="detail.xmfl === '市级项目' ? 'success' : 'info'" size="sm">
          {{ detail.xmfl }}
        </screen-tag>
        <screen-tag v-if="detail && detail.ptsslb" tone="info" size="sm">{{ detail.ptsslb }}</screen-tag>
        <screen-tag v-if="detail && detail.xzqh" tone="muted" size="sm">{{ detail.xzqh }}</screen-tag>
        <screen-tag v-if="detail" :tone="detail.sfkg === '是' ? 'success' : 'muted'" size="sm">
          {{ detail.sfkg === '是' ? '已开工' : '未开工' }}
        </screen-tag>
        <screen-tag v-if="detail" :tone="detail.sfjg === '是' ? 'success' : 'muted'" size="sm">
          {{ detail.sfjg === '是' ? '已竣工' : '未竣工' }}
        </screen-tag>
        <screen-tag v-if="detail" :tone="detail.sfyj === '是' ? 'success' : 'warning'" size="sm">
          {{ detail.sfyj === '是' ? '已移交' : '未移交' }}
        </screen-tag>
      </div>

      <screen-button size="sm" icon="layers" :disabled="!detail" @click="handleOpenProcess">
        六阶段进度
      </screen-button>
      <screen-button size="sm" icon="edit" :disabled="!detail" @click="handleEdit">编辑</screen-button>
      <screen-popconfirm
        title="移除后可在「数据更新与移除」里恢复，确定移除该配套项目吗？"
        :description="(detail && detail.ptxmmc) || ''"
        width="300"
        @confirm="handleRemove"
      >
        <screen-button size="sm" type="danger" icon="trash" :disabled="!detail">移除</screen-button>
      </screen-popconfirm>
    </template>

    <div class="facility-detail">
      <header class="facility-detail__head">
        <span class="facility-detail__code">{{ (detail && detail.crzdbh) || '—' }}</span>
        <h3 class="facility-detail__name">{{ (detail && detail.ptxmmc) || '加载中…' }}</h3>
      </header>

      <screen-tabs v-model="activeTab" :tabs="tabs" class="facility-detail__tabs" />

      <!-- ================= 基本信息 ================= -->
      <div v-show="activeTab === 'base'" class="facility-detail__pane">
        <screen-loading
          class="facility-detail__content"
          :loading="loading"
          text="正在加载配套项目详情…"
          :overlay="false"
        >
          <div class="facility-detail__two-col">
            <div class="facility-detail__main">
              <section
                v-for="group in mainGroups"
                :key="group.key"
                class="facility-detail__section"
              >
                <h4 class="facility-detail__section-title">
                  {{ group.title }}
                  <span class="facility-detail__section-kicker">共 {{ group.items.length }} 项</span>
                </h4>
                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="group.items"
                  :columns="group.columns || 2"
                  label-width="150px"
                />
              </section>
            </div>

            <div class="facility-detail__sidebar">
              <section class="facility-detail__section">
                <h4 class="facility-detail__section-title">
                  档案要件（历史标志位）
                  <span class="facility-detail__section-kicker">只读 · 实际文件见「附件」页签</span>
                </h4>
                <div class="facility-detail__flags">
                  <span v-for="item in archiveFlagItems" :key="item.key" class="facility-detail__flag">
                    <span class="facility-detail__flag-label">{{ item.label }}</span>
                    <screen-tag :tone="item.value === '是' ? 'success' : 'muted'" size="sm">
                      {{ item.value || '未填' }}
                    </screen-tag>
                  </span>
                </div>
              </section>

              <section class="facility-detail__section">
                <h4 class="facility-detail__section-title">
                  录入与说明
                  <span class="facility-detail__section-kicker">长文本独占整行</span>
                </h4>
                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="entryItems"
                  :columns="1"
                  label-width="104px"
                />
              </section>
            </div>
          </div>
        </screen-loading>
      </div>

      <!-- ================= 六阶段进度 ================= -->
      <div v-show="activeTab === 'process'" class="facility-detail__pane">
        <!--
          这里直接用列表页那一个组件实例：`processVisible` 为真时才挂载，
          避免「详情里嵌一个不显示的整屏弹窗」把点击吃掉。
        -->
        <div class="facility-detail__placeholder">
          <screen-empty
            v-if="!processVisible"
            text="六阶段进度在独立视图中录入"
            description="进度录入需要整屏宽度（6 个阶段 × 每个阶段多个事项），点下方按钮或标题栏的「六阶段进度」打开"
          />
          <screen-button v-if="!processVisible" type="primary" icon="layers" @click="handleOpenProcess">
            打开六阶段进度
          </screen-button>
        </div>
      </div>

      <!-- ================= 附件（按材料类型分组的目录树） ================= -->
      <div v-show="activeTab === 'files'" class="facility-detail__pane">
        <section class="facility-detail__block facility-detail__block--grow">
          <h4 class="facility-detail__block-title">
            配套附件
            <span class="facility-detail__block-sub">
              共 {{ attachmentTree.totalFiles || 0 }} 个 · {{ attachmentSizeText }}
              · 按材料类型分组；上传/删除请到「配套附件管理」页签
            </span>
          </h4>

          <!--
            ★ 这里用目录树而不是平铺表格：详情页回答的是「这个项目的材料
              按类型齐不齐」，分组后一眼能看出缺哪类材料；平铺表格给不出这个结构。
            ★ editable=false：详情页保持只读（上传/删除统一在附件管理页做），
              避免详情弹窗与附件管理两处都能写、留痕口径不一致。
          -->
          <attachment-tree-view
            :tree="attachmentTree"
            :loading="attachmentLoading"
            :editable="false"
            empty-text="该配套项目还没有附件"
            @preview="previewAttachment"
            @download="downloadAttachment"
          />
        </section>
      </div>

      <!-- ================= 变更履历 ================= -->
      <div v-show="activeTab === 'history'" class="facility-detail__pane">
        <section class="facility-detail__block facility-detail__block--grow">
          <h4 class="facility-detail__block-title">
            字段级变更履历
            <span class="facility-detail__block-sub">共 {{ history.length }} 条 · 含环节进度与附件的操作</span>
          </h4>

          <screen-data-table
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
            <template #createTime="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
            <template #bizType="{ row }">
              <screen-tag tone="muted" size="sm">{{ bizTypeText(row.bizType) }}</screen-tag>
            </template>
            <template #action="{ row }">
              <screen-tag :tone="actionTone(row.action)" size="sm">{{ row.actionText || row.action }}</screen-tag>
            </template>
            <template #changeCount="{ row }">
              <span class="facility-detail__num">{{ row.changeCount || 0 }} 项</span>
            </template>
            <template #summary="{ row }">
              <span class="facility-detail__summary" :title="row.summary || ''">{{ row.summary || '—' }}</span>
            </template>
            <template #detailAction="{ row }">
              <button
                type="button"
                class="facility-detail__link"
                :disabled="!((row.details || []).length)"
                @click.stop="handleShowDetail(row)"
              >
                查看改了什么
              </button>
            </template>
          </screen-data-table>
        </section>
      </div>

      <!-- 六阶段进度：整屏弹窗，与列表页复用的同一个组件 -->
      <process-tree-panel
        v-if="processVisible"
        ref="processPanel"
        @changed="handleProcessChanged"
      />

      <!--
        字段级差异明细。
        ★ 用 v-if：这是「弹窗里再开弹窗」，内层隐藏时两个 fixed 对话框会互相盖住，
          表现是「关闭明细后详情页点不动」。
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
            <span class="facility-detail__field-key">{{ row.field }}</span>
          </template>
          <template #before="{ row }">
            <span class="facility-detail__before">{{ row.before || '（空）' }}</span>
          </template>
          <template #after="{ row }">
            <span class="facility-detail__after">{{ row.after || '（空）' }}</span>
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
  ScreenEmpty,
  ScreenPopconfirm
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import ProcessTreePanel from './ProcessTreePanel.vue'
import AttachmentTreeView from './AttachmentTreeView.vue'
import { queryFacilityAdminById } from '@/api/land/facilityAdmin'
import { queryAttachmentTree, buildAttachmentDownloadUrl } from '@/api/land/attachment'
import { queryChangeHistory } from '@/api/land/dataRecycle'
import {
  formatSize,
  formatTime,
  joinInfo,
  actionTone,
  bizTypeText
} from '../constants'

/** 「档案要件（历史标志位）」清单，顺序与旧表列顺序一致（与录入弹窗同一份口径） */
const ARCHIVE_FLAGS = [
  { key: 'xjpfwj', label: '项建批复文件' },
  { key: 'kypfwj', label: '可研批复文件' },
  { key: 'csjgspfwj', label: '初设及概算批复文件' },
  { key: 'dlgh', label: '道路规划' },
  { key: 'ghgcxk', label: '规划工程许可' },
  { key: 'gxzhslsj', label: '管线综合矢量数据(shp)' },
  { key: 'zyptfa', label: '专业配套方案' },
  { key: 'zyglyj', label: '专业管理意见' },
  { key: 'ghydxkyhbsxbl', label: '规划用地许可与划拨手续办理' },
  { key: 'sgxk', label: '施工许可' },
  { key: 'bdcdj', label: '不动产登记' }
]

export default {
  name: 'FacilityDetailModal',
  components: {
    ScreenModal,
    ScreenTabs,
    ScreenTag,
    ScreenDataTable,
    ScreenDescriptions,
    ScreenButton,
    ScreenLoading,
    ScreenEmpty,
    ScreenPopconfirm,
    ProcessTreePanel,
    AttachmentTreeView
  },
  data () {
    return {
      visible: false,
      loading: false,
      attachmentLoading: false,
      historyLoading: false,
      activeTab: 'base',
      detail: null,
      /** 附件树（按材料类型分组）：{ groups:[...], totalFiles, totalSize, typeCount } */
      attachmentTree: { groups: [] },
      history: [],
      processVisible: false,
      detailVisible: false,
      detailTitle: '字段变更明细',
      activeDetails: [],
      tabs: [
        { key: 'base', label: '基本信息' },
        { key: 'process', label: '六阶段进度' },
        { key: 'files', label: '附件' },
        { key: 'history', label: '变更履历' }
      ],
      historyColumns: [
        { key: 'createTime', title: '时间', width: 170, type: 'slot' },
        { key: 'bizType', title: '业务对象', width: 110, type: 'slot', align: 'center' },
        { key: 'action', title: '动作', width: 110, type: 'slot', align: 'center' },
        { key: 'changeCount', title: '变更字段', width: 100, type: 'slot', align: 'center' },
        { key: 'summary', title: '摘要', width: 300, type: 'slot' },
        { key: 'operatorName', title: '操作人', width: 120, formatter: (value, row) => value || row.operator || '—' },
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
    /** 主栏分组：字段最多的四组，顺序 = 用户读详情的顺序 */
    mainGroups () {
      const data = this.detail || {}
      return [
        {
          key: 'identity',
          title: '标识与属性',
          columns: 2,
          items: [
            { key: 'crzdbh', label: '出让宗地编号', value: data.crzdbh, tone: 'accent' },
            { key: 'ptxmmc', label: '配套项目名称', value: data.ptxmmc },
            { key: 'dkmc', label: '地块名称', value: data.dkmc },
            { key: 'ptsslb', label: '配套设施类别', value: data.ptsslb },
            { key: 'xzqh', label: '行政区划', value: data.xzqh },
            { key: 'xmfl', label: '项目分类', value: data.xmfl },
            { key: 'jsxx', label: '建设性质', value: data.jsxx },
            { key: 'dldj', label: '道路等级', value: data.dldj },
            { key: 'sfzsjtjlz', label: '是否涉及提级论证', value: data.sfzsjtjlz },
            { key: 'tjlzsftg', label: '提级论证是否通过', value: data.tjlzsftg },
            { key: 'ghhxkd', label: '规划红线宽度（米）', value: data.ghhxkd, tone: 'number' },
            { key: 'cd', label: '长度（米）', value: data.cd, tone: 'number' },
            { key: 'tzgs', label: '投资估算（万元）', value: data.tzgs, tone: 'number' },
            { key: 'zjly', label: '资金来源', value: data.zjly },
            { key: 'dkcrscndptjgsj', label: '承诺配套竣工时间', value: data.dkcrscndptjgsj, tone: 'number' }
          ]
        },
        {
          key: 'units',
          title: '参建单位',
          columns: 2,
          items: [
            { key: 'jsdw', label: '建设单位', value: data.jsdw },
            { key: 'sjdw', label: '设计单位', value: data.sjdw },
            { key: 'kcdw', label: '勘察单位', value: data.kcdw },
            { key: 'jldw', label: '监理单位', value: data.jldw },
            { key: 'sgdw', label: '施工单位', value: data.sgdw },
            { key: 'jsgydw', label: '接收管养单位', value: data.jsgydw }
          ]
        },
        {
          key: 'progress',
          title: '前期审批与进度',
          columns: 3,
          items: [
            { key: 'xjpfsfwc', label: '项建批复是否完成', value: data.xjpfsfwc },
            { key: 'xjpfzt', label: '项建批复状态', value: data.xjpfzt },
            { key: 'kypfsfwc', label: '可研批复是否完成', value: data.kypfsfwc },
            { key: 'kypfzt', label: '可研批复状态', value: data.kypfzt },
            { key: 'csjgspfsfwc', label: '初设及概算批复是否完成', value: data.csjgspfsfwc },
            { key: 'csjgspfzt', label: '初设及概算批复状态', value: data.csjgspfzt },
            { key: 'gspfje', label: '概算批复金额（万元）', value: data.gspfje, tone: 'number' },
            { key: 'zjlsqk', label: '资金落实情况', value: data.zjlsqk },
            { key: 'sfkg', label: '是否开工', value: data.sfkg },
            { key: 'kgzt', label: '开工状态', value: data.kgzt },
            { key: 'yjkgsj', label: '预计开工时间', value: data.yjkgsj, tone: 'number' },
            { key: 'sjkgsj', label: '实际开工时间', value: data.sjkgsj, tone: 'number' },
            { key: 'sfjg', label: '是否竣工', value: data.sfjg },
            { key: 'yjjgsj', label: '预计竣工时间', value: data.yjjgsj, tone: 'number' },
            { key: 'sjjgsj', label: '实际竣工时间', value: data.sjjgsj, tone: 'number' },
            { key: 'sfyj', label: '是否移交', value: data.sfyj }
          ]
        }
      ]
    },
    archiveFlagItems () {
      const data = this.detail || {}
      return ARCHIVE_FLAGS.map((item) => ({
        key: item.key,
        label: item.label,
        value: data[item.key]
      }))
    },
    entryItems () {
      const data = this.detail || {}
      return [
        { key: 'ptxmhdydydjdc', label: '核定用地与地籍调查', value: data.ptxmhdydydjdc },
        { key: 'jtwt', label: '具体问题', value: data.jtwt, stack: true },
        { key: 'gzjy', label: '工作建议', value: data.gzjy, stack: true },
        { key: 'zlqsnrjsm', label: '资料缺失说明', value: data.zlqsnrjsm, stack: true },
        { key: 'bz', label: '备注', value: data.bz, stack: true },
        { key: 'lrdw', label: '录入单位', value: data.lrdw },
        { key: 'lrr', label: '录入人', value: data.lrr },
        { key: 'lxdh', label: '联系电话', value: data.lxdh, tone: 'number' },
        { key: 'createTime', label: '创建信息', value: joinInfo(data.createTime, data.createAccount) }
      ]
    },
    historyRows () {
      return (this.history || []).map((item, index) => Object.assign({ key: item.id || `h-${index}` }, item))
    },
    attachmentSizeText () {
      const total = Number(this.attachmentTree.totalSize || 0)
      return formatSize(total)
    }
  },
  methods: {
    formatSize,
    formatTime,
    actionTone,
    bizTypeText,

    /* ---------------- 对外入口 ---------------- */

    open (record, tab) {
      if (!record || !record.id) {
        toast.warning('缺少配套项目主键，无法查看详情')
        return
      }
      this.visible = true
      this.activeTab = tab || 'base'
      this.detail = null
      this.attachmentTree = { groups: [] }
      this.history = []
      this.detailVisible = false
      this.processVisible = false
      this.load(record.id)
    },

    load (id) {
      this.loading = true
      queryFacilityAdminById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '配套项目详情加载失败')
            this.visible = false
            return
          }
          this.detail = res.result || {}
          // 附件与履历不阻塞主信息展示，并行拉取
          this.loadAttachments(this.detail.id)
          this.loadHistory(this.detail.id)
        })
        .finally(() => {
          this.loading = false
        })
    },

    /**
     * 附件树（按材料类型分组）。
     * ★ 用 /attachment/tree 而不是按业务查列表：详情页要的是「这个项目的附件
     *   按材料类型怎么分布」，列表给不出这个结构；而且树里已经带了
     *   每个类型的小计与总数，不必再前端累加。
     */
    loadAttachments (bizId) {
      this.attachmentLoading = true
      queryAttachmentTree('facility', bizId)
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

    /**
     * 履历。
     * ★ 走 `/land/data/recycle/history?bizType=facility&bizId=`：
     *   这是「按业务对象取履历」的**唯一**通道（宗地之所以另有
     *   `/landAdmin/history`，只是后端顺手开了一个便捷入口，数据是同一张表）。
     * ★ `bizType=facility` 只返回**配套本体**的变更，不含该配套下各环节（bizType=process）
     *   与附件（bizType=attachment）的记录 —— 后端按 bizType 精确匹配。
     *   所以想看某条环节的改动，要打开那一条环节的详情（ProcessItemModal 的履历入口）；
     *   本页要的是「这条配套改过哪些字段」。
     */
    loadHistory (bizId) {
      this.historyLoading = true
      queryChangeHistory('facility', bizId, 100)
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

    /* ---------------- 操作 ---------------- */

    handleOpenProcess () {
      if (!this.detail) return
      this.activeTab = 'process'
      this.processVisible = true
      this.$nextTick(() => {
        const panel = this.$refs.processPanel
        if (panel && typeof panel.open === 'function') {
          panel.open(this.detail)
        }
      })
    },

    /** 环节进度改过之后，主体信息里的「是否开工 / 竣工」等列可能也变了，重拉一次 */
    handleProcessChanged () {
      if (this.detail && this.detail.id) {
        this.load(this.detail.id)
      }
      this.$emit('changed')
    },

    downloadAttachment (row) {
      if (!row || !row.id) {
        toast.warning('该附件还没有落库，无法下载')
        return
      }
      window.open(buildAttachmentDownloadUrl(row.id), '_blank')
    },

    /**
     * 预览附件。
     * ★ 详情页没有内嵌预览弹窗（那是附件管理页的职责），
     *   所以这里复用「下载」打开同一个地址：浏览器能直接渲染的
     *   （图片 / PDF）会当场显示，不能渲染的（dwg / zip）会走下载 ——
     *   对用户来说「点一下就看到内容」这个预期是满足的。
     *   不新引一个预览弹窗，是为了避免详情页与附件管理两套预览实现。
     */
    previewAttachment (row) {
      this.downloadAttachment(row)
    },

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
      this.detailTitle = `字段变更明细 · ${row.actionText || row.action || ''} ${formatTime(row.createTime)}`
      this.detailVisible = true
    },

    handleEdit () {
      if (this.detail) this.$emit('edit', this.detail)
    },

    handleRemove () {
      if (this.detail) this.$emit('remove', this.detail)
    },

    handleClose () {
      this.visible = false
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.facility-detail {
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

  &__code {
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

  /** ScreenLoading 的根节点不是弹性子项，必须显式撑满（README 踩坑 9） */
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

  &__placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--screen-space-4);
    flex: 1 1 auto;
    min-height: 320px;
    padding: var(--screen-space-6);
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
  }

  &__two-col {
    display: grid;
    grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
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

  /* 历史标志位：两列小方块，标签 + 是/否 */
  &__flags {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-2);
  }

  &__flag {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
    padding: 4px 8px;
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
    min-width: 0;
  }

  &__flag-label {
    min-width: 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

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
  描述列表的字号 / 行距统一在这里收口（只作用于详情分组，不影响表格页签）。
  标签与值同为 14px，靠颜色分层；字号与绝对行高一致是对齐的前提。
*/
.facility-detail__section {
  --sd-row-pad: 7px;
  --sd-pair-gap: var(--screen-space-3);
  --sd-value-size: 14px;
  --sd-wrap-line-h: 21px;

  .screen-descriptions__label-text {
    font-size: 14px;
    color: var(--screen-text-mute);
  }
}

/* 窄窗兜底：极窄时三列并排的「前期审批与进度」也收成一列 */
@media (max-width: 900px) {
  .facility-detail__section {
    --sd-cols: 1;
  }

  .facility-detail__flags {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
