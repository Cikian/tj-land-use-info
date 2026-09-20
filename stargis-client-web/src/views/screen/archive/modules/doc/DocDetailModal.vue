<template>
  <!--
    DocDetailModal 收发文详情（只读）
    --------------------------------
    收文与发文共用一个组件，靠 docType 区分字段：
      收文：来文单位 / 来文字号 / 收文日期 / 页数·份数 / 办理期限 / 当前处理人 / 办结时间 / 办结说明
      发文：主送单位 / 抄送单位 / 发文日期 / 签发人 / 拟稿人
    公共：出让宗地编号 / 配套项目 / 登记人·时间 / 最后更新 / 备注。

    收文额外提供「查看流转痕迹」入口（转到 DocFlowModal，那里有时间轴与流转操作）。

    附件列表直接复用 DocAttachmentTable 的只读模式，
    这样「附件列定义」只有一处，不会出现详情页和编辑页显示不一致。

    公开方法：
      open(doc, docType)
    事件：
      open-flow(doc)  查看流转痕迹
      edit(doc)       编辑
      archive(doc)    归档
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="960"
    :show-footer="false"
    :body-max-height="'calc(100vh - 200px)'"
    @cancel="close"
  >
    <template #head-extra>
      <div v-if="doc" class="doc-detail__tags">
        <screen-tag v-if="docType === 'receive'" :tone="docStatusTone(doc.status)" size="sm">
          {{ doc.status || '—' }}
        </screen-tag>
        <screen-tag :tone="doc.archiveId ? 'success' : 'muted'" size="sm">
          {{ doc.archiveId ? '已归档' : '未归档' }}
        </screen-tag>
        <screen-tag v-if="doc.secretLevel" :tone="secretTone(doc.secretLevel)" size="sm">
          密级：{{ doc.secretLevel }}
        </screen-tag>
        <screen-tag v-if="doc.urgency && doc.urgency !== '普通'" :tone="docUrgencyTone(doc.urgency)" size="sm">
          {{ doc.urgency }}
        </screen-tag>
      </div>

      <screen-button
        v-if="docType === 'receive'"
        size="sm"
        icon="clock"
        :disabled="!doc"
        @click="handleOpenFlow"
      >
        查看流转痕迹
      </screen-button>

      <screen-button size="sm" icon="edit" :disabled="!doc" @click="handleEdit">编辑</screen-button>

      <screen-button
        v-if="doc && !doc.archiveId"
        size="sm"
        icon="archive"
        @click="handleArchive"
      >
        归档
      </screen-button>
    </template>

    <div v-if="doc" class="doc-detail">
      <!-- 标题行 -->
      <header class="doc-detail__head">
        <span class="doc-detail__no">{{ doc.docNo || '—' }}</span>
        <h3 class="doc-detail__title">{{ doc.docTitle || '—' }}</h3>
      </header>

      <section class="doc-detail__block">
        <h4 class="doc-detail__block-title">{{ docType === 'send' ? '发文信息' : '收文信息' }}</h4>
        <screen-descriptions :items="mainItems" :columns="2" label-width="104px" />
      </section>

      <section class="doc-detail__block">
        <h4 class="doc-detail__block-title">关联与登记</h4>
        <screen-descriptions :items="metaItems" :columns="2" label-width="104px" />
      </section>

      <section class="doc-detail__block doc-detail__block--grow">
        <h4 class="doc-detail__block-title">
          附件
          <span class="doc-detail__block-sub">共 {{ attachments.length }} 个</span>
        </h4>
        <doc-attachment-table :value="attachments" :biz-path="bizPath" disabled />
      </section>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenDescriptions,
  ScreenTag,
  ScreenButton,
} from '@/components/screen'
import DocAttachmentTable from './DocAttachmentTable.vue'
import {
  docStatusTone,
  docUrgencyTone,
  secretTone,
  joinInfo,
  buildBizPath,
} from '../../constants'

export default {
  name: 'DocDetailModal',
  components: { ScreenModal, ScreenDescriptions, ScreenTag, ScreenButton, DocAttachmentTable },
  data () {
    return {
      visible: false,
      docType: 'receive',
      doc: null,
    }
  },
  computed: {
    title () {
      return this.docType === 'send' ? '发文详情' : '收文详情'
    },
    attachments () {
      return (this.doc && this.doc.attachments) || []
    },
    /**
     * 只读模式下 attachments 里已有 storePath，bizPath 只是形参（不会触发上传），
     * 但保持与实际上传目录一致，避免使用者误以为有别的目录。
     */
    bizPath () {
      return buildBizPath(new Date(), this.docType === 'send' ? 'send' : 'receive')
    },
    mainItems () {
      const d = this.doc || {}
      if (this.docType === 'send') {
        return [
          { key: 'toDept', label: '主送单位', value: d.toDept },
          { key: 'ccDept', label: '抄送单位', value: d.ccDept },
          { key: 'issueDate', label: '发文日期', value: d.issueDate },
          { key: 'docType', label: '文件类型', value: d.docType },
          { key: 'signer', label: '签发人', value: d.signer },
          { key: 'drafter', label: '拟稿人', value: d.drafter },
          { key: 'copies', label: '份数', value: d.copies },
        ]
      }
      return [
        { key: 'fromDept', label: '来文单位', value: d.fromDept },
        { key: 'fromDocNo', label: '来文字号', value: d.fromDocNo },
        { key: 'receiveDate', label: '收文日期', value: d.receiveDate },
        { key: 'docType', label: '文件类型', value: d.docType },
        {
          key: 'pageCopies',
          label: '页数 / 份数',
          // 不能用 `|| '—'`：0 页 / 0 份是合法值，会被误显示成「—」
          value: `${this.displayOrDash(d.pageCount)} / ${this.displayOrDash(d.copies)}`,
        },
        { key: 'handleDeadline', label: '办理期限', value: d.handleDeadline },
        { key: 'currentHandlerName', label: '当前处理人', value: d.currentHandlerName },
        { key: 'finishTime', label: '办结时间', value: d.finishTime },
        { key: 'finishOpinion', label: '办结说明', value: d.finishOpinion, span: 2 },
      ]
    },
    metaItems () {
      const d = this.doc || {}
      return [
        { key: 'crzdbh', label: '出让宗地编号', value: d.crzdbh },
        { key: 'ptxmmc', label: '配套项目', value: d.ptxmmc },
        { key: 'createInfo', label: '登记人 / 时间', value: joinInfo(d.createBy, d.createTime) },
        { key: 'updateInfo', label: '最后更新', value: joinInfo(d.updateBy, d.updateTime) },
        { key: 'remark', label: '备注', value: d.remark, span: 2 },
      ]
    },
  },
  methods: {
    docStatusTone,
    docUrgencyTone,
    secretTone,

    /** 0 是合法值，只有 null/undefined/'' 才回退到占位符 */
    displayOrDash (value) {
      return value === null || value === undefined || value === '' ? '—' : value
    },

    /* ---------------- 对外入口 ---------------- */

    open (doc, docType) {
      if (!doc) {
        return
      }
      this.doc = doc
      this.docType = docType === 'send' ? 'send' : 'receive'
      this.visible = true
    },

    close () {
      this.visible = false
    },

    /* ---------------- 操作 ---------------- */

    handleOpenFlow () {
      this.$emit('open-flow', this.doc)
      this.visible = false
    },
    handleEdit () {
      this.$emit('edit', this.doc)
    },
    handleArchive () {
      this.$emit('archive', this.doc)
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.doc-detail {
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

  &__title {
    margin: 0;
    min-width: 0;
    font-size: var(--screen-font-lg);
    font-weight: 600;
    color: var(--screen-text);
  }

  &__block {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-2);
    min-height: 0;

    &--grow {
      flex: 1 1 auto;
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
}
</style>
