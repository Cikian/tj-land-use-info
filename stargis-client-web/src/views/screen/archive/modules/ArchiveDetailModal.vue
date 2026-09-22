<template>
  <!--
    ArchiveDetailModal 档案详情（全屏弹窗 + 四页签）
    --------------------------------
    基本信息 / 档案文件 / 收发文情况 / 操作记录。

    为什么用全屏弹窗而不是抽屉：
      档案详情的信息量很大（两组描述 + 三类表格），抽屉宽度不够会到处折行；
      全屏弹窗既保留「浮层」的上下文，又能给表格足够的横向空间。

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
        <!--
          版式：两列「卡片列」，左右各自撑满高度，外框高度对齐 → 页面中心是平衡的。
            左列：关联项目（按内容） + 其他信息（吃掉剩余高度）
            右列：档案属性（吃掉剩余高度）
          原来是把两组描述各占一半宽度并排，结果左边 6 项、右边 15 项，
          右列拖得很长、左列下方一大片空，视觉重心全偏在右上角。
          拆出「其他信息」后左右都是 5~6 行，且字段列宽足够（标签 108 + 值 250 以上），
          长值不再折行。
        -->
        <div v-show="activeTab === 'base'" class="archive-detail__pane archive-detail__pane--base">
          <div class="archive-detail__col">
            <section class="archive-detail__block">
              <h4 class="archive-detail__block-title">关联项目</h4>
              <screen-descriptions :items="projectItems" :columns="2" />
            </section>

            <section class="archive-detail__block archive-detail__block--grow">
              <h4 class="archive-detail__block-title">其他信息</h4>
              <screen-descriptions :items="otherItems" :columns="2" />
            </section>
          </div>

          <section class="archive-detail__block archive-detail__block--grow">
            <h4 class="archive-detail__block-title">档案属性</h4>
            <screen-descriptions :items="attributeItems" :columns="3" />
          </section>
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
    projectItems () {
      const data = this.detail || {}
      return [
        { key: 'ptxmmc', label: '配套项目', value: data.ptxmmc, span: 2 },
        { key: 'crzdbh', label: '出让宗地编号', value: data.crzdbh },
        { key: 'dkmc', label: '地块名称', value: data.dkmc },
        { key: 'ptsslb', label: '配套设施类别', value: data.ptsslb },
        { key: 'xzqh', label: '所属行政区', value: data.xzqh },
        { key: 'sourceType', label: '项目来源', value: sourceTypeText(data.sourceType) },
      ]
    },
    attributeItems () {
      const data = this.detail || {}
      const sizeText = formatSize(data.totalSize)
      // 档案的「本体属性」：档案号/名称/类型/密级/期限/年度/责任/日期/状态
      // 其余（创建信息、最后更新、备注）属于审计信息，放到左列「其他信息」里，
      // 让左右两列的行数接近，版面才平衡。
      return [
        { key: 'archiveNo', label: '档案号', value: data.archiveNo },
        { key: 'archiveName', label: '档案名称', value: data.archiveName, span: 2 },
        { key: 'archiveType', label: '档案类型', value: archiveTypeText(data.archiveType) },
        { key: 'secretLevel', label: '密级', value: data.secretLevel },
        { key: 'retention', label: '保管期限', value: data.retention },
        { key: 'archiveYear', label: '档案年度', value: data.archiveYear },
        { key: 'responsibleDept', label: '责任部门', value: data.responsibleDept },
        { key: 'responsibleUser', label: '配套负责人', value: data.responsibleUser },
        { key: 'archiveDate', label: '归档日期', value: data.archiveDate },
        { key: 'status', label: '档案状态', value: data.status },
        {
          key: 'fileSummary',
          label: '卷内文件',
          value: `${this.files.length} 个${sizeText !== '—' ? ` · ${sizeText}` : ''}`,
        },
        { key: 'categoryNames', label: '档案类别', value: this.categoryNames, span: 3 },
      ]
    },
    /** 左列下半部分：档案的审计/补充信息 */
    otherItems () {
      const data = this.detail || {}
      return [
        { key: 'createTime', label: '创建信息', value: this.joinInfo(data.createTime, data.createBy) },
        { key: 'updateTime', label: '最后更新', value: this.joinInfo(data.updateTime, data.updateBy) },
        { key: 'remark', label: '备注', value: data.remark, span: 2 },
      ]
    },
    /** 多个卷内文件可能分属不同类别，去重后用「、」连接 */
    categoryNames () {
      const names = this.files.map((item) => item.categoryName).filter(Boolean)
      const unique = Array.from(new Set(names))
      return unique.length ? unique.join('、') : '未分类'
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
  gap: var(--screen-space-3);
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
    gap: var(--screen-space-4);
    flex: 1 1 auto;
    min-height: 0;
  }

  /**
   * 基本信息：两列卡片列。
   *   align-items: stretch  → 左右两列的外框高度始终相等；
   *   align-content: safe center → 整块内容在正文里垂直居中，上下留白对称，
   *     而不是贴顶堆着、下面空一大片（safe 保证内容超高时退回顶对齐，不裁切）。
   */
  &__pane--base {
    display: grid;
    grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
    align-items: stretch;
    align-content: safe center;
    gap: var(--screen-space-4);
  }

  &__col {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-4);
    min-width: 0;
    min-height: 0;
  }

  /* 卡片化：与首页面板同一套玻璃质感，让分区边界一眼可见 */
  &__block {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    min-height: 0;
    padding: var(--screen-space-4);
    background: var(--screen-panel-bg);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius);
    box-shadow: var(--screen-shadow), var(--screen-shadow-inset);
    -webkit-backdrop-filter: blur(var(--screen-blur));
    backdrop-filter: blur(var(--screen-blur));

    // 只让需要的块吃掉剩余高度，避免所有块都被拉高
    &--grow {
      flex: 1 1 0;
      min-height: 200px;
    }
  }

  &__block-title {
    display: flex;
    align-items: baseline;
    gap: var(--screen-space-2);
    margin: 0;
    font-size: var(--screen-font-sm);
    font-weight: 600;
    color: var(--screen-text);

    &::before {
      content: '';
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

/* 窄屏（例如把窗口拉窄调试）：基本信息的两列改为一列，避免字段被压得过窄 */
@media (max-width: 1200px) {
  .archive-detail__pane--base {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
