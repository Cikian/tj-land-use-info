<template>
  <a-modal
    :title="title"
    :width="900"
    :visible="visible"
    :footer="null"
    :destroyOnClose="true"
    @cancel="close">
    <!--
      这里**不要**再包 a-spin：详情数据是父组件（DocReceiveList / DocSendList）
      在点击「详情」时就已经查好了才调 open() 的，本组件内部没有任何异步等待，
      原先的 :spinning="loading" 只是引用了一个 data 里并不存在的 loading，
      每次渲染都会报 “Property or method "loading" is not defined”。
    -->
    <template v-if="doc">
      <div class="doc-detail__head">
        <div class="doc-detail__main">
          <div class="doc-detail__no">{{ doc.docNo }}</div>
          <div class="doc-detail__title" :title="doc.docTitle">{{ doc.docTitle }}</div>
        </div>
        <div class="doc-detail__tags">
          <a-tag v-if="docType === 'receive'" :color="statusColor(doc.status)">{{ doc.status }}</a-tag>
          <a-tag v-if="doc.archiveId" color="green">已归档</a-tag>
          <a-tag v-else>未归档</a-tag>
          <a-tag v-if="doc.secretLevel" :color="secretColor(doc.secretLevel)">密级：{{ doc.secretLevel }}</a-tag>
          <a-tag v-if="doc.urgency && doc.urgency !== '普通'" color="orange">{{ doc.urgency }}</a-tag>
        </div>
      </div>

      <!--
        ★ column 必须写成**数字**，不能写成响应式对象 { xxl: 2, ..., xs: 1 }。
        因为下面「办结说明」这个**非末项**用了 :span="2"，而 antd 是按
        「本行 span 之和 == column」来切行的：column 传对象时，首次渲染 screens 还是空的，
        getColumn() 会**兜底返回 3**，于是「1 + 1 + span2」在 3 列下溢出（3-1-1-2 = -1），
        控制台报 “Sum of column `span` in a line exceeds `column` of Descriptions”。
        写成固定数字后 getColumn() 第一时间就返回 2，不会再出现这种偏差。
        另外「备注」是**末项**，不要写 :span —— antd 会把末项的 span 强制改写成本行剩余列数，
        写它对渲染没有任何影响，却会让上面那段记账溢出（详见 docs 7.6）。
      -->
      <a-descriptions size="small" bordered :column="2">
        <template v-if="docType === 'receive'">
          <a-descriptions-item label="来文单位">{{ doc.fromDept || '—' }}</a-descriptions-item>
          <a-descriptions-item label="来文字号">{{ doc.fromDocNo || '—' }}</a-descriptions-item>
          <a-descriptions-item label="收文日期">{{ doc.receiveDate || '—' }}</a-descriptions-item>
          <a-descriptions-item label="文件类型">{{ doc.docType || '—' }}</a-descriptions-item>
          <a-descriptions-item label="页数 / 份数">{{ (doc.pageCount || '—') + ' / ' + (doc.copies || '—') }}</a-descriptions-item>
          <a-descriptions-item label="办理期限">{{ doc.handleDeadline || '—' }}</a-descriptions-item>
          <a-descriptions-item label="当前处理人">{{ doc.currentHandlerName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="办结时间">{{ doc.finishTime || '—' }}</a-descriptions-item>
          <a-descriptions-item label="办结说明" :span="2">{{ doc.finishOpinion || '—' }}</a-descriptions-item>
        </template>
        <template v-else>
          <a-descriptions-item label="主送单位">{{ doc.toDept || '—' }}</a-descriptions-item>
          <a-descriptions-item label="抄送单位">{{ doc.ccDept || '—' }}</a-descriptions-item>
          <a-descriptions-item label="发文日期">{{ doc.issueDate || '—' }}</a-descriptions-item>
          <a-descriptions-item label="文件类型">{{ doc.docType || '—' }}</a-descriptions-item>
          <a-descriptions-item label="签发人">{{ doc.signer || '—' }}</a-descriptions-item>
          <a-descriptions-item label="拟稿人">{{ doc.drafter || '—' }}</a-descriptions-item>
        </template>
        <a-descriptions-item label="出让宗地编号">{{ doc.crzdbh || '—' }}</a-descriptions-item>
        <a-descriptions-item label="配套项目">{{ doc.ptxmmc || '—' }}</a-descriptions-item>
        <a-descriptions-item label="登记人 / 时间">{{ joinInfo(doc.createBy, doc.createTime) }}</a-descriptions-item>
        <a-descriptions-item label="最后更新">{{ joinInfo(doc.updateBy, doc.updateTime) }}</a-descriptions-item>
        <!-- 最后一项不写 :span：antd 会把它的 span 强制改写成「本行剩余列数」，写不写渲染都一样，
             但写了会让「span 之和」的记账溢出而误报告警（详见上面 备注/办结说明 的说明） -->
        <a-descriptions-item label="备注">{{ doc.remark || '—' }}</a-descriptions-item>
      </a-descriptions>

      <div class="doc-detail__section-title">
        <a-icon type="paper-clip" />
        <span>附件（{{ attachments.length }}）</span>
        <a v-if="docType === 'receive'" class="doc-detail__link" @click="handleOpenFlow">查看流转痕迹</a>
      </div>
      <a-table
        size="small"
        rowKey="id"
        :columns="attachmentColumns"
        :dataSource="attachments"
        :pagination="false"
        :locale="{ emptyText: '该公文没有附件' }">
        <template slot="index" slot-scope="text, record, index">{{ index + 1 }}</template>
        <template slot="fileName" slot-scope="text, record">
          <a @click="download(record)">{{ text }}</a>
        </template>
        <template slot="fileSize" slot-scope="text, record">{{ record.readableSize || formatSize(text) }}</template>
        <template slot="action" slot-scope="text, record">
          <a @click="download(record)">下载</a>
        </template>
      </a-table>
    </template>
  </a-modal>
</template>

<script>
  import { getFileAccessHttpUrl } from '@/api/manage'

  /**
   * 收发文详情（只读）
   *
   * 收文与发文共用一个组件，靠 docType 区分字段。
   * 收文额外提供「查看流转痕迹」入口，跳到流转弹窗（那里有时间轴与流转操作）。
   */
  export default {
    name: 'DocDetailModal',
    data () {
      return {
        visible: false,
        docType: 'receive',
        doc: null,
        attachmentColumns: [
          { title: '#', width: 48, align: 'center', scopedSlots: { customRender: 'index' } },
          { title: '文件名', dataIndex: 'fileName', ellipsis: true, scopedSlots: { customRender: 'fileName' } },
          { title: '大小', dataIndex: 'fileSize', width: 110, scopedSlots: { customRender: 'fileSize' } },
          { title: '上传人', dataIndex: 'uploadName', width: 120, customRender: (text, record) => text || record.uploadBy || '—' },
          { title: '上传时间', dataIndex: 'uploadTime', width: 165, customRender: text => text || '—' },
          { title: '操作', width: 80, scopedSlots: { customRender: 'action' } }
        ]
      }
    },
    computed: {
      title () {
        return this.docType === 'send' ? '发文详情' : '收文详情'
      },
      attachments () {
        return (this.doc && this.doc.attachments) || []
      }
    },
    methods: {
      open (doc, docType) {
        this.doc = doc
        this.docType = docType || 'receive'
        this.visible = true
      },
      close () {
        this.visible = false
      },
      handleOpenFlow () {
        this.$emit('open-flow', this.doc)
        this.visible = false
      },
      download (record) {
        const raw = record.url || record.storePath
        if (!raw) {
          return
        }
        window.open(getFileAccessHttpUrl(String(raw).replace(/^\/+/, '')), '_blank')
      },
      joinInfo (a, b) {
        const parts = [a, b].filter(v => v !== null && v !== undefined && v !== '')
        return parts.length ? parts.join(' · ') : '—'
      },
      statusColor (status) {
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
        // ★ 返回 undefined 走 .ant-tag 默认灰；不能写 'default'（非 antd 预设色 → 白底白字看不见，详见 docs 7.5）
        return undefined
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
  .doc-detail {
    &__head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 10px;
      padding-bottom: 12px;
    }

    &__main {
      min-width: 0;
    }

    &__no {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 13px;
      letter-spacing: 1px;
      color: #2e7cf6;
    }

    &__title {
      font-size: 16px;
      font-weight: 600;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__tags {
      display: flex;
      gap: 6px;

      /deep/ .ant-tag {
        margin: 0;
      }
    }

    &__section-title {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 20px 0 10px;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
    }

    &__link {
      margin-left: auto;
      font-size: 13px;
      font-weight: 400;
    }
  }
</style>
