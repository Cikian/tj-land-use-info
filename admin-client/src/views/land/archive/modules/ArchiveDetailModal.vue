<template>
  <a-modal
    :visible="visible"
    :width="'100%'"
    :footer="null"
    :closable="true"
    :destroyOnClose="true"
    wrapClassName="archive-detail-modal"
    :bodyStyle="{ padding: '0' }"
    @cancel="close">
    <a-spin :spinning="loading" class="archive-detail">
      <template v-if="detail">
        <!-- ============ 页头 ============ -->
        <header class="archive-detail__head">
          <div class="archive-detail__head-main">
            <div class="archive-detail__no">{{ detail.archiveNo }}</div>
            <div class="archive-detail__name" :title="detail.archiveName">{{ detail.archiveName }}</div>
          </div>
          <!--
            标签与按钮共用一行：左端标签、右端按钮。
            与右上角的 × 不冲突 —— × 的 56×56 点击区占 y 0~56，而这一行在档案号 + 档案名称
            两行之后，实测从 y=77 开始（净空 21px），天然错开。
          -->
          <div class="archive-detail__head-bar">
            <div class="archive-detail__tags">
              <a-tag :color="statusColor(detail.status)">{{ detail.status }}</a-tag>
              <a-tag>{{ detail.archiveType === 'paper' ? '纸质档案' : '电子档案' }}</a-tag>
              <a-tag :color="secretColor(detail.secretLevel)">密级：{{ detail.secretLevel || '一般' }}</a-tag>
              <a-tag v-if="detail.retention">保管期限：{{ detail.retention }}</a-tag>
              <a-tag color="blue">{{ fileCount }} 个文件</a-tag>
            </div>
            <div class="archive-detail__head-actions">
              <a-button v-has="'land:archive:edit'" icon="edit" @click="$emit('edit', detail)">编辑</a-button>
              <a-button v-has="'land:archive:export'" icon="download" @click="$emit('export', detail)">导出该项目档案</a-button>
              <a-button icon="reload" :loading="relatedLoading" @click="loadRelated">刷新收发文</a-button>
            </div>
          </div>
        </header>

        <!-- ============ 页签 ============ -->
        <a-tabs v-model="activeTab" class="archive-detail__tabs">
          <!-- ---------- 基本信息 ---------- -->
          <a-tab-pane key="base" tab="基本信息">
            <div class="archive-detail__panel">
              <div class="archive-detail__section-title">关联项目</div>
              <a-descriptions class="detail-desc" size="small" bordered :column="{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }">
                <a-descriptions-item label="配套项目">{{ detail.ptxmmc || '—' }}</a-descriptions-item>
                <a-descriptions-item label="出让宗地编号">{{ detail.crzdbh || '—' }}</a-descriptions-item>
                <a-descriptions-item label="地块名称">{{ detail.dkmc || '—' }}</a-descriptions-item>
                <a-descriptions-item label="配套设施类别">{{ detail.ptsslb || '—' }}</a-descriptions-item>
                <a-descriptions-item label="所属行政区">{{ detail.xzqh || '—' }}</a-descriptions-item>
                <a-descriptions-item label="项目来源">
                  <span>{{ sourceTypeText(detail.sourceType) }}</span>
                </a-descriptions-item>
              </a-descriptions>

              <div class="archive-detail__section-title">档案属性</div>
              <a-descriptions class="detail-desc" size="small" bordered :column="{ xxl: 3, xl: 3, lg: 2, md: 2, sm: 1, xs: 1 }">
                <a-descriptions-item label="档案号">{{ detail.archiveNo }}</a-descriptions-item>
                <a-descriptions-item label="档案名称">{{ detail.archiveName }}</a-descriptions-item>
                <a-descriptions-item label="档案年度">{{ detail.archiveYear || '—' }}</a-descriptions-item>
                <a-descriptions-item label="责任部门">{{ detail.responsibleDept || '—' }}</a-descriptions-item>
                <a-descriptions-item label="配套负责人">{{ detail.responsibleUser || '—' }}</a-descriptions-item>
                <a-descriptions-item label="归档日期">{{ detail.archiveDate || '—' }}</a-descriptions-item>
                <a-descriptions-item label="档案类别">
                  <span v-if="categoryNames">{{ categoryNames }}</span>
                  <span v-else class="archive-detail__muted">—</span>
                </a-descriptions-item>
                <a-descriptions-item label="文件数 / 总大小">
                  {{ fileCount }} 个 / {{ totalSizeText }}
                </a-descriptions-item>
                <a-descriptions-item label="创建信息">{{ joinInfo(detail.createBy, detail.createTime) }}</a-descriptions-item>
                <a-descriptions-item label="备注" :span="3">{{ detail.remark || '—' }}</a-descriptions-item>
              </a-descriptions>
            </div>
          </a-tab-pane>

          <!-- ---------- 档案文件 ---------- -->
          <a-tab-pane key="files" :tab="`档案文件（${fileCount}）`">
            <div class="archive-detail__panel">
              <a-table
                size="small"
                rowKey="id"
                :columns="fileColumns"
                :dataSource="files"
                :pagination="false"
                :locale="{ emptyText: '该档案还没有卷内文件' }">
                <template slot="index" slot-scope="text, record, index">{{ index + 1 }}</template>
                <template slot="fileName" slot-scope="text, record">
                  <a @click="downloadFile(record)">{{ text }}</a>
                  <div v-if="record.fileTitle && record.fileTitle !== text" class="archive-detail__muted">
                    题名：{{ record.fileTitle }}
                  </div>
                </template>
                <template slot="fileSize" slot-scope="text, record">
                  {{ record.readableSize || formatSize(text) }}
                </template>
                <template slot="status" slot-scope="text">
                  <a-tag :color="text === '已归档' ? 'green' : 'orange'">{{ text || '已归档' }}</a-tag>
                </template>
                <template slot="action" slot-scope="text, record">
                  <a v-has="'land:archive:download'" @click="downloadFile(record)">下载</a>
                </template>
              </a-table>
            </div>
          </a-tab-pane>
          <!-- ---------- 收发文情况 ---------- -->
          <a-tab-pane key="docs" :tab="`收发文情况（${related.receives.length + related.sends.length}）`">
            <div class="archive-detail__panel">
              <div class="archive-detail__section-title">
                收文（{{ related.receives.length }}）
                <span class="archive-detail__section-hint">按配套项目「{{ detail.ptxmmc || '—' }}」关联</span>
              </div>
              <a-table
                size="small"
                rowKey="id"
                :columns="receiveColumns"
                :dataSource="related.receives"
                :pagination="false"
                :locale="{ emptyText: '该项目暂无收文记录' }">
                <template slot="status" slot-scope="text">
                  <a-tag :color="docStatusColor(text)">{{ text }}</a-tag>
                </template>
                <template slot="archiveFlag" slot-scope="text">
                  <a-tag v-if="text" color="green">已归档</a-tag>
                  <span v-else class="archive-detail__muted">未归档</span>
                </template>
              </a-table>

              <div class="archive-detail__section-title">发文（{{ related.sends.length }}）</div>
              <a-table
                size="small"
                rowKey="id"
                :columns="sendColumns"
                :dataSource="related.sends"
                :pagination="false"
                :locale="{ emptyText: '该项目暂无发文记录' }">
                <template slot="archiveFlag" slot-scope="text">
                  <a-tag v-if="text" color="green">已归档</a-tag>
                  <span v-else class="archive-detail__muted">未归档</span>
                </template>
              </a-table>
            </div>
          </a-tab-pane>

          <!-- ---------- 操作记录 ---------- -->
          <a-tab-pane key="logs" :tab="`操作记录（${logs.length}）`">
            <div class="archive-detail__panel">
              <a-table
                size="small"
                rowKey="id"
                :columns="logColumns"
                :dataSource="logs"
                :pagination="{ pageSize: 20, size: 'small' }"
                :locale="{ emptyText: '暂无操作记录' }">
                <template slot="action" slot-scope="text">
                  <a-tag>{{ text }}</a-tag>
                </template>
              </a-table>
            </div>
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-spin>
  </a-modal>
</template>

<script>
  import { queryArchiveById, queryArchiveLogs, queryRelatedDocuments, buildDownloadUrl, archiveUrl } from '@/api/land/archive'

  /**
   * 档案详情（全屏弹窗）
   *
   * 需求原文：「在档案详情中，有一块需要关联展示该项目的收发文情况」——
   * 因此第三个页签专门展示该配套项目下的收文与发文（按 facilityId 关联）。
   *
   * 其余页签：基本信息 / 档案文件 / 操作记录（方案要求「记录相关操作」）。
   */
  export default {
    name: 'ArchiveDetailModal',
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
        fileColumns: [
          { title: '#', width: 48, align: 'center', scopedSlots: { customRender: 'index' } },
          { title: '文件名', dataIndex: 'fileName', ellipsis: true, scopedSlots: { customRender: 'fileName' } },
          { title: '档案类别', dataIndex: 'categoryName', width: 260, customRender: text => text || '—' },
          { title: '大小', dataIndex: 'fileSize', width: 100, scopedSlots: { customRender: 'fileSize' } },
          { title: '状态', dataIndex: 'status', width: 90, scopedSlots: { customRender: 'status' } },
          { title: '上传时间', dataIndex: 'createTime', width: 160, customRender: text => text || '—' },
          { title: '操作', width: 70, scopedSlots: { customRender: 'action' } }
        ],
        receiveColumns: [
          { title: '收文登记号', dataIndex: 'docNo', width: 150 },
          { title: '文件标题', dataIndex: 'docTitle', ellipsis: true },
          { title: '来文单位', dataIndex: 'fromDept', width: 160, customRender: text => text || '—' },
          { title: '来文日期', dataIndex: 'receiveDate', width: 110, customRender: text => text || '—' },
          { title: '当前处理人', dataIndex: 'currentHandlerName', width: 110, customRender: text => text || '—' },
          { title: '状态', dataIndex: 'status', width: 90, scopedSlots: { customRender: 'status' } },
          { title: '归档', dataIndex: 'archiveId', width: 80, scopedSlots: { customRender: 'archiveFlag' } }
        ],
        sendColumns: [
          { title: '发文登记号', dataIndex: 'docNo', width: 150 },
          { title: '文件标题', dataIndex: 'docTitle', ellipsis: true },
          { title: '主送单位', dataIndex: 'toDept', width: 180, customRender: text => text || '—' },
          { title: '发文日期', dataIndex: 'issueDate', width: 110, customRender: text => text || '—' },
          { title: '签发人', dataIndex: 'signer', width: 100, customRender: text => text || '—' },
          { title: '归档', dataIndex: 'archiveId', width: 80, scopedSlots: { customRender: 'archiveFlag' } }
        ],
        logColumns: [
          { title: '时间', dataIndex: 'operateTime', width: 170 },
          { title: '动作', dataIndex: 'action', width: 90, scopedSlots: { customRender: 'action' } },
          { title: '操作人', dataIndex: 'operateName', width: 120, customRender: (text, record) => text || record.operateBy || '—' },
          { title: '明细', dataIndex: 'detail', ellipsis: true },
          { title: 'IP', dataIndex: 'ip', width: 130, customRender: text => text || '—' }
        ]
      }
    },
    computed: {
      fileCount () {
        return this.files.length
      },
      totalSizeText () {
        const total = this.files.reduce((sum, item) => sum + (Number(item.fileSize) || 0), 0)
        return this.formatSize(total)
      },
      categoryNames () {
        const names = []
        this.files.forEach(item => {
          if (item.categoryName && names.indexOf(item.categoryName) === -1) {
            names.push(item.categoryName)
          }
        })
        return names.join('、')
      }
    },
    methods: {
      open (record) {
        if (!record || !record.id) {
          this.$message.warning('请选择要查看的档案')
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
        queryArchiveById(id).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            this.visible = false
            return
          }
          this.detail = res.result || null
          this.files = (this.detail && this.detail.files) || []
          // 收发文与操作记录并行拉取，互不阻塞详情渲染
          this.loadRelated()
          this.loadLogs()
        }).finally(() => {
          this.loading = false
        })
      },
      loadRelated () {
        if (!this.detail) {
          return Promise.resolve()
        }
        this.relatedLoading = true
        return queryRelatedDocuments({
          facilityId: this.detail.facilityId || undefined,
          landId: this.detail.landId || undefined,
          crzdbh: this.detail.crzdbh || undefined,
          limit: 100
        }).then(res => {
          if (res.success && res.result) {
            this.related = {
              receives: res.result.receives || [],
              sends: res.result.sends || []
            }
          }
        }).finally(() => {
          this.relatedLoading = false
        })
      },
      loadLogs () {
        if (!this.detail) {
          return Promise.resolve()
        }
        return queryArchiveLogs(this.detail.id).then(res => {
          if (res.success) {
            this.logs = res.result || []
          }
        })
      },
      downloadFile (record) {
        if (!record.id) {
          return
        }
        window.open(buildDownloadUrl(archiveUrl.fileDownload, { id: record.id }), '_blank')
      },
      close () {
        this.visible = false
      },
      // ------------------------------------------------------------------
      // 展示工具
      // ------------------------------------------------------------------
      joinInfo (a, b) {
        const parts = [a, b].filter(v => v !== null && v !== undefined && v !== '')
        return parts.length ? parts.join(' · ') : '—'
      },
      sourceTypeText (type) {
        if (type === 'doc_receive') {
          return '收文归档'
        }
        if (type === 'doc_send') {
          return '发文归档'
        }
        return '手工录入'
      },
      statusColor (status) {
        if (status === '已归档') {
          return 'green'
        }
        if (status === '审核中') {
          return 'orange'
        }
        if (status === '归档中') {
          return 'blue'
        }
        // ★ 兜底必须返回 undefined，不能写 'default'：
        //   'default' 不在 antd 的预设色列表里，会被当成「自定义颜色」处理 ——
        //   于是加上 .ant-tag-has-color（文字变白），而 backgroundColor:'default' 是非法值被浏览器丢弃，
        //   结果就是白底白字，标签整个看不见。（详见 docs 7.5）
        return undefined
      },
      secretColor (level) {
        if (level === '机密') {
          return 'red'
        }
        if (level === '秘密') {
          return 'volcano'
        }
        if (level === '内部') {
          return 'orange'
        }
        // 「一般」密级没有强调色，返回 undefined 走 .ant-tag 的默认灰（不能写 'default'，原因同上）
        return undefined
      },
      docStatusColor (status) {
        if (status === '已办结' || status === '已归档') {
          return 'green'
        }
        if (status === '已退回') {
          return 'red'
        }
        if (status === '承办中') {
          return 'cyan'
        }
        return 'orange'
      },
      formatSize (bytes) {
        const value = Number(bytes)
        if (!value) {
          return '—'
        }
        const units = ['B', 'KB', 'MB', 'GB', 'TB']
        let size = value
        let unit = 0
        while (size >= 1024 && unit < units.length - 1) {
          size /= 1024
          unit++
        }
        return unit === 0 ? `${size} ${units[unit]}` : `${size.toFixed(2)} ${units[unit]}`
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .archive-detail {
    display: block;
    height: 100%;

    &__head {
      /*
        页头分两段：
          第 1 段：档案号 / 档案名称（右上角留出 antd 关闭按钮 × 的 56×56 点击区）
          第 2 段：标签组靠左 + 操作按钮靠右，**共用一行**
        早先按钮和标题挤在同一行，正好落在 × 的点击区上；后来把按钮单独放一行虽然不遮挡了，
        但标签和按钮分成两行显得松散。现在合并到一行，且这一行位于 × 点击区（y 0~56）下方。
      */
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 16px 24px 14px;
      background: #f8fafc;
      border-bottom: 1px solid @border-color;
      /* 全屏弹窗的 .ant-modal-content 是直角（border-radius:0），
         页头不要再做圆角，否则贴边处会露出两个白色小缺口 */
    }

    &__head-main {
      min-width: 0;
      /* 标题最多排到 × 左侧（页头 24px + 这里 48px = 72px > 关闭区 56px） */
      padding-right: 48px;
    }

    /*
      标签 + 按钮的共用行。
      用 margin-left:auto 而不是 justify-content:space-between 把按钮顶到右边：
      space-between 在 flex-wrap 换行后，落在自己那一行的单个元素会被推到**最左**，
      而 margin-left:auto 在换行后依然是右对齐。
    */
    &__head-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px 16px;
      min-width: 0;
    }

    &__no {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 13px;
      letter-spacing: 1px;
      color: #2e7cf6;
    }

    &__name {
      margin: 2px 0 0;
      font-size: 18px;
      font-weight: 600;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__tags {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
      min-width: 0;

      /deep/ .ant-tag {
        margin: 0;
      }
    }

    &__head-actions {
      display: flex;
      flex-wrap: wrap;
      /* 靠右；即使换行到下一行也保持右对齐 */
      justify-content: flex-end;
      margin-left: auto;
      gap: 8px;

      /*
        按钮默认 flex-shrink:1，在窄屏或按钮文案变长时会被压扁到文字贴边。
        这里禁止收缩并禁止换行，宁可整组换行。
      */
      /deep/ .ant-btn {
        flex: none;
        white-space: nowrap;
      }
    }

    &__tabs {
      padding: 0 24px 24px;

      /deep/ .ant-tabs-bar {
        margin-bottom: 16px;
      }
    }

    &__panel {
      min-height: 320px;
    }

    &__section-title {
      display: flex;
      align-items: baseline;
      gap: 8px;
      margin: 4px 0 10px;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;

      &:not(:first-child) {
        margin-top: 22px;
      }
    }

    &__section-hint {
      font-size: 12px;
      font-weight: 400;
      color: @text-weak;
    }

    &__muted {
      color: @text-weak;
    }
  }

  /*
    信息表（a-descriptions，bordered）：
    ★ 必须把 table-layout 固定住。
      antd 在 bordered 模式下**显式**写了
        .ant-descriptions-bordered .ant-descriptions-view > table { table-layout: auto; }
      于是浏览器会优先满足「内容列」——而「档案类别」的值是
      「工程建设手续 / 施工图审查 / … 、工程建设手续 / 竣工验收备案 / …」这种超长串，
      它会把标签列挤到只剩两三个字宽，结果「责任部门」被拆成「责任部 / 门」、
      「归档日期」被拆成「归档日 / 期」，看着就像样式坏了。
      这里用更高优先级（root class + /deep/，4 个类选择器 > antd 的 2 个）改回 fixed，
      并给标签列一个够用的固定宽度，长内容改为在自己单元格里换行。
      （同样的写法在 ArchiveCategoryList.vue 里已验证过。）

    标签列宽度取 132px：antd 全局 box-sizing: border-box，减去左右各 12px 内边距后
    还有 108px，够放最长的那个标签「文件数 / 总大小」（约 97px）；
    刻意不写 white-space:nowrap —— 万一以后加了更长的标签，让它在本列内换行，
    而不是溢出到内容列上。
  */
  .archive-detail /deep/ .detail-desc {
    .ant-descriptions-view > table {
      table-layout: fixed;
    }

    .ant-descriptions-item-label,
    .ant-descriptions-item-content {
      padding: 10px 12px;
      vertical-align: middle;
      word-break: break-word;
    }

    .ant-descriptions-item-label {
      width: 132px;
      color: @text-muted;
      font-weight: 400;
    }
  }
</style>

<!--
  全屏弹窗的样式必须放在非 scoped 的 style 里：
  a-modal 会被渲染到 body 下，wrapClassName 所在的节点不在本组件的 DOM 树内，
  scoped 属性选择器匹配不到。
-->
<style lang="less">
  .archive-detail-modal {
    top: 0;
    padding-bottom: 0;

    .ant-modal {
      top: 0;
      max-width: 100%;
      padding-bottom: 0;
      margin: 0;
    }

    .ant-modal-content {
      display: flex;
      flex-direction: column;
      height: 100vh;
      border-radius: 0;
    }

    .ant-modal-body {
      flex: 1 1 auto;
      min-height: 0;
      /* 页签内容区自己滚动，页头与页签栏固定 */
      overflow: auto;
      padding: 0;
    }

    /*
      关闭按钮保持 antd 默认定位（top:0 / right:0，56×56 点击区）：
      页头已经预留了 64px 右内边距，两者不会再叠在一起。
      之前这里写过 top:14px，把 × 从角落推进了页头按钮所在的那一行，
      反而加重了遮挡，已去掉。
    */
    .ant-modal-close {
      color: #64789a;
      transition: color 0.2s ease;

      &:hover,
      &:focus {
        color: #2e7cf6;
      }
    }
  }
</style>
