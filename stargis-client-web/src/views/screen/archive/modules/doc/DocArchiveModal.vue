<template>
  <!--
    DocArchiveModal 收发文归档弹窗
    --------------------------------
    需求约定：「给出提示，让用户选择是否归档，如果归档同时要选择档案类别（必选）」。
    因此调用方在办结/归档前先弹确认气泡，用户确认后再打开本弹窗；
    本弹窗里 **档案类别是必填项**，未选不允许提交。

    收文与发文共用这一个弹窗，靠 docType 区分（receive / send）。

    归档后：公文的所有附件会成为该档案的卷内文件，统一挂在所选类别下。

    公开方法：
      open(doc, docType)  打开归档弹窗
    事件：
      ok(archiveId)  归档成功，父组件据此刷新列表与统计
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="620"
    :confirm-loading="confirmLoading"
    ok-text="确认归档"
    cancel-text="取消"
    @ok="handleOk"
    @cancel="close"
  >
    <div class="doc-archive">
      <!-- 归档范围提示：让用户明确「归档的是哪些东西」 -->
      <p v-if="tip" class="doc-archive__tip" role="status">
        <screen-icon name="info" :size="14" />
        <span>{{ tip }}</span>
      </p>

      <screen-field
        label="归档类别"
        required
        :label-width="'96px'"
        :error="errors.categoryId"
        tip="必须选择末级档案类别；公文附件会成为该档案的卷内文件。"
      >
        <category-picker
          ref="category"
          v-model="model.categoryId"
          :leaf-only="true"
          :clearable="false"
          :invalid="!!errors.categoryId"
          placeholder="必须选择末级档案类别"
        />
      </screen-field>

      <screen-field label="档案名称" :label-width="'96px'" html-for="doc-archive-name">
        <screen-input
          id="doc-archive-name"
          v-model="model.archiveName"
          :maxlength="255"
          placeholder="默认取公文标题，可修改"
        />
      </screen-field>

      <screen-field label="归档日期" :label-width="'96px'">
        <screen-date-input v-model="model.archiveDate" />
      </screen-field>

      <screen-field label="密级" :label-width="'96px'">
        <screen-select
          v-model="model.secretLevel"
          :options="secretOptions"
          placeholder="默认取公文密级"
          aria-label="密级"
        />
      </screen-field>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenDateInput,
  ScreenIcon,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import CategoryPicker from '../CategoryPicker.vue'
import { archiveDocReceive, archiveDocSend } from '@/api/land/document'
import { SECRET_LEVELS, toOptions, today } from '../../constants'

export default {
  name: 'DocArchiveModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenIcon,
    CategoryPicker,
  },
  data () {
    return {
      title: '归档到档案管理',
      tip: '',
      visible: false,
      confirmLoading: false,
      /** 收文 receive / 发文 send */
      docType: 'receive',
      doc: null,
      model: {
        categoryId: '',
        archiveName: '',
        archiveDate: today(),
        secretLevel: '',
      },
      errors: {},
    }
  },
  computed: {
    secretOptions () {
      return toOptions(SECRET_LEVELS)
    },
  },
  methods: {
    /**
     * @param {object} doc 收文 / 发文记录
     * @param {string} docType receive / send
     */
    open (doc, docType) {
      if (!doc || !doc.id) {
        toast.warning('缺少待归档的公文')
        return
      }
      this.doc = doc
      this.docType = docType === 'send' ? 'send' : 'receive'
      this.title = this.docType === 'send' ? '发文归档' : '收文归档'
      this.tip = `将把「${doc.docNo || ''} ${doc.docTitle || ''}」及其全部附件归档为一个档案`
      this.model = {
        categoryId: '',
        archiveName: doc.docTitle || '',
        archiveDate: today(),
        // 默认沿用公文密级，减少一次选择
        secretLevel: doc.secretLevel || '',
      }
      this.errors = {}
      this.visible = true

      // 类别树按需刷新，避免用户刚在类别管理里加了类别却选不到
      this.$nextTick(() => {
        const picker = this.$refs.category
        if (picker && typeof picker.refresh === 'function') picker.refresh()
      })
    },

    handleOk () {
      if (!this.model.categoryId) {
        this.errors = { categoryId: '归档必须选择档案类别' }
        toast.warning('归档必须选择档案类别')
        return
      }
      this.submit()
    },

    submit () {
      const payload = {
        docId: this.doc.id,
        categoryId: this.model.categoryId,
        archiveName: this.model.archiveName || undefined,
        archiveDate: this.model.archiveDate || undefined,
        secretLevel: this.model.secretLevel || undefined,
      }
      this.confirmLoading = true
      const task = this.docType === 'send' ? archiveDocSend(payload) : archiveDocReceive(payload)

      task
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '归档失败')
            return
          }
          toast.success('归档成功，已生成档案')
          this.visible = false
          this.$emit('ok', res.result)
        })
        .catch(() => {
          // 错误提示已由请求层处理
        })
        .finally(() => {
          this.confirmLoading = false
        })
    },

    close () {
      this.visible = false
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.doc-archive {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  &__tip {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    padding: var(--screen-space-2) var(--screen-space-3);
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-sub);
    background: rgba(31, 163, 232, 0.1);
    border: 1px solid rgba(31, 163, 232, 0.3);
    border-radius: var(--screen-radius-sm);

    /deep/ .screen-icon {
      margin-top: 2px;
      color: var(--screen-info);
    }
  }
}
</style>
