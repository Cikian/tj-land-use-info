<template>
  <!--
    ArchiveDetailModal 档案详情（全屏弹窗 + 四页签）
    --------------------------------
    基本信息 / 档案文件 / 收发文情况 / 操作记录。

    为什么用全屏弹窗而不是抽屉：
      档案详情的信息量很大（多组描述 + 三类表格），抽屉宽度不够会到处折行；
      全屏弹窗既保留「浮层」的上下文，又能给表格足够的横向空间。

    版式（本次重做 · 方案 A「左右分栏 + 分组标题」）：
      - 基本信息改成左右两栏：
          左栏（1.4fr）档案属性 —— 字段最多，占主要横向空间，内部 2 列排布；
          右栏（1fr）  关联项目 / 其他信息 —— 纵向叠放，各 1 列，宽度一致便于扫读；
        两栏之间一条极细竖线，≤1280px 自动降为单栏（竖线转横线）。
      - 每个分组 = 「青色竖条 + 分组标题 + 可选说明」，不再用卡片套卡片：
        基本信息的边界靠分组与留白表达，卡片只留给三个表格页签。
      - 描述列表开 allowWrap：**值不再截断**（旧实现 nowrap + 省略号，长部门名
        必须悬停看全文）。字号层级在弹窗自己的 `.archive-detail__section` 里收口：
        标签 14px 弱色 / 值 16px 白色，行距 7px。见文件末尾的样式块。
      - 档案名称 / 档案类别 不再在属性里重复一遍 —— 弹窗标题行已经展示了它们，
        重复出现在两处只会增加噪音。

    公开方法：
      open(record)  按列表行打开详情（内部再按 id 拉全量数据）
    事件：
      edit(detail)   点「编辑」时抛出，由列表页接手打开编辑弹窗
      export(detail) 点「导出该项目档案」时抛出，由列表页接手导出
  -->
  <screen-modal
    :visible.sync="visible"
    title="档案详情"
    fullscreen
    :show-footer="false"
    :body-max-height="null"
    @cancel="handleClose"
  >
    <template #head-extra>
      <div class="archive-detail__tags">
        <screen-tag v-if="detail" :tone="statusTone(detail.status)" size="sm">
          {{ detail.status || '—' }}
        </screen-tag>
        <screen-tag v-if="detail" tone="muted" size="sm">{{ archiveTypeText(detail.archiveType) }}</screen-tag>
        <screen-tag v-if="detail" :tone="secretTone(detail.secretLevel)" size="sm">密级：{{ detail.secretLevel || '—' }}</screen-tag>
        <screen-tag v-if="detail && detail.retention" tone="muted" size="sm">保管期限：{{ detail.retention }}</screen-tag>
        <screen-tag v-if="detail" tone="info" size="sm">{{ files.length }} 个卷内文件</screen-tag>
      </div>

      <screen-button
        size="sm"
        icon="edit"
        :disabled="!detail"
        @click="handleEdit"
      >
        编辑
      </screen-button>

      <screen-button
        size="sm"
        icon="download"
        :disabled="!detail"
        @click="handleExport"
      >
        导出该项目档案
      </screen-button>

      <!--
        反向闭环：从档案跳到「同一配套项目/宗地下」的方案 2.3.2 第 6/7/8 项页面。
        用路由跳转而不是事件穿透：这些页面是档案模块的兄弟页签，
        档案详情又被「档案维护」「档案查询」两个面板复用，逐层 $emit 要改 4 个文件；
        改走路由后，页签切换与条件下发由 archive/index.vue 的 `$route.query` watcher 统一处理。
        没有关联项目的档案（facilityId 与 crzdbh 都为空）不显示按钮，避免跳过去看到空列表。
      -->
      <screen-button
        v-for="link in relatedModuleLinks"
        :key="link.tab"
        size="sm"
        :icon="link.icon"
        @click="openRelatedTab(link.tab)"
      >
        {{ link.label }}
      </screen-button>
    </template>

    <div class="archive-detail">
      <!-- 标题行：档案号 + 档案名称 -->
      <header class="archive-detail__head">
        <span class="archive-detail__no">{{ (detail && detail.archiveNo) || '—' }}</span>
        <h3 class="archive-detail__name">{{ (detail && detail.archiveName) || '加载中…' }}</h3>
      </header>

      <screen-tabs v-model="activeTab" :tabs="tabs" class="archive-detail__tabs" />

      <screen-loading
        class="archive-detail__content"
        :loading="loading"
        text="正在加载档案详情…"
        :overlay="false"
      >
        <!-- ================= 基本信息 ================= -->
        <div v-show="activeTab === 'base'" class="archive-detail__pane">
          <!--
            方案 A 版式：左右两栏 + 分组标题。
            左栏（主） 档案属性 —— 档案本体信息，字段最多，占主要横向空间；
            右栏（侧） 关联项目 / 其他信息 —— 两个短分组纵向叠放，宽度一致便于扫读。
            两栏之间用极细竖线分隔，窄容器时自动降为单栏。
          -->
          <div class="archive-detail__two-col">
            <section class="archive-detail__section archive-detail__section--main">
              <h4 class="archive-detail__section-title">
                档案属性
                <span class="archive-detail__section-kicker">
                  档案本体信息，共 {{ attributeItems.length }} 项
                </span>
              </h4>
              <screen-descriptions
                variant="flat"
                allow-wrap
                :items="attributeItems"
                :columns="2"
                label-width="112px"
              />
            </section>

            <div class="archive-detail__sidebar">
              <section class="archive-detail__section">
                <h4 class="archive-detail__section-title">
                  关联项目
                  <span class="archive-detail__section-kicker">共 {{ projectItems.length }} 项</span>
                </h4>
                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="projectItems"
                  :columns="1"
                  label-width="104px"
                />
              </section>

              <section class="archive-detail__section">
                <h4 class="archive-detail__section-title">
                  其他信息
                  <span class="archive-detail__section-kicker">共 {{ otherItems.length }} 项</span>
                </h4>
                <screen-descriptions
                  variant="flat"
                  allow-wrap
                  :items="otherItems"
                  :columns="1"
                  label-width="104px"
                />
              </section>
            </div>
          </div>
        </div>

        <!-- ================= 档案文件 ================= -->
        <div v-show="activeTab === 'files'" class="archive-detail__pane">
          <section class="archive-detail__block archive-detail__block--grow">
            <h4 class="archive-detail__block-title">
              卷内文件
              <span class="archive-detail__block-sub">共 {{ files.length }} 个 · {{ totalSizeText }}</span>
            </h4>

            <screen-data-table
              :columns="fileColumns"
              :data="files"
              row-key="id"
              :min-width="900"
              empty-text="该档案还没有卷内文件"
            >
              <template #index="{ index }">
                <span class="archive-detail__index">{{ String(index + 1).padStart(2, '0') }}</span>
              </template>

              <template #fileName="{ row }">
                <button type="button" class="archive-detail__link" @click="downloadFile(row)">
                  {{ row.fileName }}
                </button>
                <span v-if="row.fileTitle && row.fileTitle !== row.fileName" class="archive-detail__sub">
                  {{ row.fileTitle }}
                </span>
              </template>

              <template #fileSize="{ row }">
                {{ row.readableSize || formatSize(row.fileSize) }}
              </template>

              <template #status="{ row }">
                <screen-tag :tone="fileStatusTone(row.status)" size="sm">{{ row.status || '—' }}</screen-tag>
              </template>

              <template #action="{ row }">
                <button
                  type="button"
                  class="archive-detail__link"
                  @click="downloadFile(row)"
                >
                  下载
                </button>
              </template>
            </screen-data-table>
          </section>
        </div>

        <!-- ================= 收发文情况 ================= -->
        <div v-show="activeTab === 'docs'" class="archive-detail__pane">
          <section class="archive-detail__block">
            <h4 class="archive-detail__block-title">
              收文
              <span class="archive-detail__block-sub">共 {{ (related.receives || []).length }} 条</span>
            </h4>
            <screen-data-table
              :columns="receiveColumns"
              :data="related.receives || []"
              row-key="id"
              :min-width="980"
              :max-height="240"
              :animated="false"
              empty-text="该项目没有关联的收文记录"
            >
              <template #docStatus="{ row }">
                <screen-tag :tone="docStatusTone(row.status)" size="sm">{{ row.status || '—' }}</screen-tag>
              </template>
              <template #archiveFlag="{ row }">
                <screen-tag :tone="row.archiveId ? 'success' : 'muted'" size="sm">
                  {{ row.archiveId ? '已归档' : '未归档' }}
                </screen-tag>
              </template>
            </screen-data-table>
          </section>

          <section class="archive-detail__block">
            <h4 class="archive-detail__block-title">
              发文
              <span class="archive-detail__block-sub">共 {{ (related.sends || []).length }} 条</span>
            </h4>
            <screen-data-table
              :columns="sendColumns"
              :data="related.sends || []"
              row-key="id"
              :min-width="860"
              :max-height="240"
              :animated="false"
              empty-text="该项目没有关联的发文记录"
            >
              <template #archiveFlag="{ row }">
                <screen-tag :tone="row.archiveId ? 'success' : 'muted'" size="sm">
                  {{ row.archiveId ? '已归档' : '未归档' }}
                </screen-tag>
              </template>
            </screen-data-table>
          </section>
        </div>

        <!-- ================= 操作记录 ================= -->
        <div v-show="activeTab === 'logs'" class="archive-detail__pane">
          <section class="archive-detail__block archive-detail__block--grow">
            <h4 class="archive-detail__block-title">
              操作记录
              <span class="archive-detail__block-sub">新增 / 修改 / 上传 / 下载 / 导出都会留痕</span>
            </h4>
            <screen-data-table
              :columns="logColumns"
              :data="logs"
              row-key="id"
              :min-width="920"
              :animated="false"
              empty-text="暂无操作记录"
            >
              <template #logAction="{ row }">
                <screen-tag :tone="logActionTone(row.action)" size="sm">{{ row.action || '—' }}</screen-tag>
              </template>
            </screen-data-table>
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
  ScreenDataTable,
  ScreenDescriptions,
  ScreenButton,
  ScreenLoading,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import {
  queryArchiveById,
  queryArchiveLogs,
  queryRelatedDocuments,
  buildJavaDownloadUrl,
  archiveUrl,
} from '@/api/land/archive'
import {
  formatSize,
  statusTone,
  secretTone,
  fileStatusTone,
  docStatusTone,
  archiveTypeText,
  sourceTypeText,
} from '../constants'

/** 操作记录里不同动作对应的标签语气 */
function logActionTone (action) {
  switch (action) {
    case '新增':
    case '归档':
      return 'success'
    case '删除':
      return 'danger'
    case '修改':
      return 'warning'
    case '导出':
    case '下载':
      return 'info'
    default:
      return 'muted'
  }
}

export default {
  name: 'ArchiveDetailModal',
  components: {
    ScreenModal,
    ScreenTabs,
    ScreenTag,
    ScreenDataTable,
    ScreenDescriptions,
    ScreenButton,
    ScreenLoading,
  },
  data () {
    return {
      visible: false,
      loading: false,
      relatedLoading: false,
      activeTab: 'base',
      detail: null,
      files: [],
      logs: [],
      related: { receives: [], sends: [] },

      tabs: [
        { key: 'base', label: '基本信息' },
        { key: 'files', label: '档案文件' },
        { key: 'docs', label: '收发文情况' },
        { key: 'logs', label: '操作记录' },
      ],

      fileColumns: [
        { key: 'index', title: '#', width: 48, type: 'slot', align: 'center' },
        { key: 'fileName', title: '文件名', width: 300, type: 'slot' },
        { key: 'categoryName', title: '档案类别', width: 240, ellipsis: true },
        { key: 'fileSize', title: '大小', width: 100, type: 'slot', align: 'right' },
        { key: 'status', title: '状态', width: 90, type: 'slot', align: 'center' },
        { key: 'createTime', title: '上传时间', width: 160 },
        { key: 'action', title: '操作', width: 80, type: 'slot', align: 'center' },
      ],

      receiveColumns: [
        { key: 'docNo', title: '收文登记号', width: 150 },
        { key: 'docTitle', title: '文件标题', width: 260, ellipsis: true },
        { key: 'fromDept', title: '来文单位', width: 170, ellipsis: true },
        { key: 'receiveDate', title: '来文日期', width: 110 },
        { key: 'currentHandlerName', title: '当前处理人', width: 110 },
        { key: 'docStatus', title: '状态', width: 90, type: 'slot', align: 'center' },
        { key: 'archiveFlag', title: '归档', width: 80, type: 'slot', align: 'center' },
      ],

      sendColumns: [
        { key: 'docNo', title: '发文登记号', width: 150 },
        { key: 'docTitle', title: '文件标题', width: 260, ellipsis: true },
        { key: 'toDept', title: '主送单位', width: 190, ellipsis: true },
        { key: 'issueDate', title: '发文日期', width: 110 },
        { key: 'signer', title: '签发人', width: 100 },
        { key: 'archiveFlag', title: '归档', width: 80, type: 'slot', align: 'center' },
      ],

      logColumns: [
        { key: 'operateTime', title: '时间', width: 170 },
        { key: 'logAction', title: '动作', width: 90, type: 'slot', align: 'center' },
        { key: 'operateName', title: '操作人', width: 120, formatter: (value, row) => value || row.operateBy || '—' },
        { key: 'detail', title: '明细', width: 340, ellipsis: true },
        { key: 'ip', title: 'IP', width: 140 },
      ],
    }
  },
  computed: {
    /**
     * 「查看同一项目的台账 / 移交事项 / 竣工档案」入口（方案 2.3.2 第 7 / 6 / 8 项）。
     * 没有关联项目（facilityId 与 crzdbh 都为空）时不显示，避免跳过去看到空列表。
     */
    relatedModuleLinks () {
      const data = this.detail || {}
      if (!data.facilityId && !data.crzdbh) {
        return []
      }
      return [
        { tab: 'ledger', label: '查看该项台账', icon: 'archive' },
        { tab: 'handover', label: '查看该项移交事项', icon: 'file-text' },
        { tab: 'completion', label: '查看该项目竣工档案', icon: 'layers' },
      ]
    },

    /**
     * 关联项目。
     * 只放 4 个短字段：地块名称 / 配套项目 这类长文本移到「档案属性」主栏，
     * 让左栏（主栏）的高度和右栏对齐，两栏底部不会一边空一大片。
     */
    projectItems () {
      const data = this.detail || {}
      return [
        { key: 'crzdbh', label: '出让宗地编号', value: data.crzdbh, tone: 'number' },
        { key: 'xzqh', label: '所属行政区', value: data.xzqh },
        { key: 'ptsslb', label: '配套设施类别', value: data.ptsslb },
        { key: 'sourceType', label: '项目来源', value: sourceTypeText(data.sourceType) },
      ]
    },
    attributeItems () {
      const data = this.detail || {}
      const sizeText = formatSize(data.totalSize)
      // 档案的「本体属性」：档案号/类型/密级/期限/年度/责任/日期/状态。
      // 档案名称与档案类别**不在这里**：弹窗标题行已经展示了档案名称，
      // 档案类别在「档案文件」页签的表格里有完整列表，重复只会增加噪音。
      // 末尾三项是长文本，用 stack 独占整行（跨两列），既填充主栏高度又不挤短字段。
      return [
        { key: 'archiveNo', label: '档案号', value: data.archiveNo, tone: 'accent' },
        { key: 'archiveType', label: '档案类型', value: archiveTypeText(data.archiveType) },
        { key: 'secretLevel', label: '密级', value: data.secretLevel },
        { key: 'retention', label: '保管期限', value: data.retention },
        { key: 'archiveYear', label: '档案年度', value: data.archiveYear, tone: 'number' },
        { key: 'archiveDate', label: '归档日期', value: data.archiveDate, tone: 'number' },
        { key: 'responsibleDept', label: '责任部门', value: data.responsibleDept },
        { key: 'responsibleUser', label: '配套负责人', value: data.responsibleUser },
        { key: 'status', label: '档案状态', value: data.status },
        {
          key: 'fileSummary',
          label: '卷内文件',
          value: `${this.files.length} 个${sizeText !== '—' ? ` · ${sizeText}` : ''}`,
        },
        { key: 'dkmc', label: '地块名称', value: data.dkmc, stack: true },
        { key: 'ptxmmc', label: '配套项目', value: data.ptxmmc, stack: true },
        { key: 'remark', label: '备注', value: data.remark, stack: true },
      ]
    },
    /** 「其他信息」：档案的审计/补充信息 */
    otherItems () {
      const data = this.detail || {}
      return [
        { key: 'createTime', label: '创建信息', value: this.joinInfo(data.createTime, data.createBy) },
        { key: 'updateTime', label: '最后更新', value: this.joinInfo(data.updateTime, data.updateBy) },
      ]
    },
    totalSizeText () {
      const total = this.files.reduce((sum, item) => sum + (Number(item.fileSize) || 0), 0)
      return formatSize(total)
    },
  },
  methods: {
    formatSize,
    statusTone,
    secretTone,
    fileStatusTone,
    docStatusTone,
    archiveTypeText,
    logActionTone,

    /* ---------------- 对外入口 ---------------- */

    open (record) {
      if (!record || !record.id) {
        toast.warning('缺少档案 ID，无法查看详情')
        return
      }
      this.visible = true
      this.activeTab = 'base'
      this.detail = null
      this.files = []
      this.logs = []
      this.related = { receives: [], sends: [] }
      this.load(record.id)
    },

    load (id) {
      this.loading = true
      queryArchiveById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案详情加载失败')
            this.visible = false
            return
          }
          this.detail = res.result || {}
          this.files = (this.detail && this.detail.files) || []
          // 收发文与操作记录不阻塞主信息展示，并行拉取
          this.loadRelated()
          this.loadLogs()
        })
        .finally(() => {
          this.loading = false
        })
    },

    /**
     * 收发文情况。
     * 优先用 facilityId，其次 landId，最后 crzdbh —— 与后端的 OR 匹配逻辑一致，
     * 只传一个最精确的标识，避免条件互相放大。
     */
    loadRelated () {
      const data = this.detail || {}
      this.relatedLoading = true
      queryRelatedDocuments({
        facilityId: data.facilityId || undefined,
        landId: data.facilityId ? undefined : data.landId || undefined,
        crzdbh: data.facilityId || data.landId ? undefined : data.crzdbh || undefined,
        limit: 100,
      })
        .then((res) => {
          if (!res || !res.success) return
          const result = res.result || {}
          this.related = {
            receives: result.receives || [],
            sends: result.sends || [],
          }
        })
        .catch(() => {
          // 收发文是辅助信息，拉取失败不影响档案主体查看
        })
        .finally(() => {
          this.relatedLoading = false
        })
    },

    loadLogs () {
      const data = this.detail || {}
      if (!data.id) return
      queryArchiveLogs(data.id)
        .then((res) => {
          if (!res || !res.success) return
          this.logs = res.result || []
        })
        .catch(() => {
          this.logs = []
        })
    },

    /* ---------------- 操作 ---------------- */

    /** 已落库的文件走下载接口，服务端会记录「下载」操作 */
    downloadFile (row) {
      if (!row || !row.id) {
        toast.warning('该文件还没保存，请先保存档案')
        return
      }
      window.open(buildJavaDownloadUrl(archiveUrl.fileDownload, { id: row.id }), '_blank')
    },

    handleEdit () {
      if (this.detail) this.$emit('edit', this.detail)
    },

    handleExport () {
      if (this.detail) this.$emit('export', this.detail)
    },

    /**
     * 跳到「同一配套项目/宗地」的第 6/7/8 项页面（方案 2.3.2）。
     * 条件优先用 facilityId（准确），没有时退化成 crzdbh（兜底）——
     * 与后端「关联档案」的匹配口径一致（facility_id 优先、crzdbh 兜底）。
     */
    openRelatedTab (tab) {
      const data = this.detail || {}
      const query = { tab }
      if (data.facilityId) {
        query.facilityId = data.facilityId
      } else if (data.crzdbh) {
        query.crzdbh = data.crzdbh
      }
      // 同路由换 query 时组件不会重建，页签切换与条件下发由
      // archive/index.vue 对 `$route.query` 的 watcher 统一处理
      this.$router.push({ path: '/screen/archive', query })
      this.handleClose()
    },

    handleClose () {
      this.visible = false
    },

    joinInfo (a, b) {
      const parts = [a, b].filter(Boolean)
      return parts.length ? parts.join(' · ') : '—'
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.archive-detail {
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
   * 会导致里面的面板/表格拿不到高度而塌陷。这里显式让它成为撑满的弹性列。
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
  }

  /*
    ★ 基本信息专用版式（方案 A：左右分栏 + 分组标题）
    ------------------------------------------------------------------
    为什么这里不用 &__block 卡片：基本信息是「一段一段的清单」，
    卡片套卡片会让边界过多、留白被吃掉，读起来像表格而不是详情。
    改成「分组标题 + 分隔线」后，重心回到数据本身：
      two-col  左右两栏，主栏给字段最多的「档案属性」
      sidebar  右栏，两个短分组纵向叠放，宽度一致便于扫读
      section  一个分组 = 标题（带青色竖条）+ 内容
    ------------------------------------------------------------------ */
  &__two-col {
    display: grid;
    grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
    // ★ stretch（而不是 start）：右栏要拉到与左栏等高，
    //   否则两栏底部会空出一大片、竖分隔线也只有半截长。
    //   配合 &__sidebar 的 space-between，右栏两个分组分布到上下两端，
    //   版面立刻从「左上角一坨」变成「一屏铺开的清单」。
    align-items: stretch;
    gap: var(--screen-space-5);
    min-width: 0;

    // 窗口变窄时降为单栏：两栏各留 ~560px，值列才不至于被省略号吃掉
    @media (max-width: 1280px) {
      grid-template-columns: minmax(0, 1fr);
      gap: var(--screen-space-3);
    }
  }

  // 右栏：与左栏之间一条极细竖线，用于区分两栏而不增加卡片重量
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

    // 右栏里第二个分组起：加一条细线切开，避免两个分组的字段连成一片
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

  // 标题右侧的说明文字：说明数量 / 口径，不抢标题
  &__section-kicker {
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  /*
    卡片化：与首页面板同一套玻璃质感，让分区边界一眼可见；边更轻、内边距更紧。
    只给「档案文件 / 收发文情况 / 操作记录」这三个表格页签用
    —— 它们的内容本身是表格，需要卡片把表格和页面背景切开。
  */
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
    // ⚠ 不加 backdrop-filter：没有硬件加速时，弹窗里每一块卡片做一次背景模糊
    //   会让打开弹窗/切页签掉到 200ms 以上（见 screen-mixins.less 的实测）

    // 只让需要的块吃掉剩余高度，避免所有块都被拉高
    &--grow {
      flex: 1 1 0;
      min-height: 200px;
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

  &__index {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-mute);
  }

  &__link {
    .screen-link-action();
  }

  &__sub {
    display: block;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }
}

// 收发文两个块并排，充分利用全屏宽度
.archive-detail__pane > .archive-detail__block {
  min-width: 0;
}

/*
  基本信息的描述列表：字号 / 行距 / 标签与值的间距统一在这里收口，
  只作用于本弹窗的基本信息两栏（.archive-detail__section 内部），
  不影响档案文件 / 收发文 / 操作记录三个表格页签。

  层级关系（详情页要「一眼能扫，不喧哗」）：
    标签与值同为 14px，靠**颜色**分层而不是字号：
      标签 = 弱色（--screen-text-mute），定位用
      值   = 白色，主要数据
    字号相同 + 同一个绝对行高（--sd-wrap-line-h）是**对齐的前提**：
    两侧字号或行高只要有一个不同，文字基线就会错开（见该组件 <style> 的说明）。
  这些变量都由 ScreenDescriptions 的 is-wrap 分支消费。
*/
.archive-detail__section {
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
   这里再兜一层——极窄时「档案属性」的两列并排也收成一列。 */
@media (max-width: 900px) {
  .archive-detail__section {
    --sd-cols: 1;
  }
}
</style>
