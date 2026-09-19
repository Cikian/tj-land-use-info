<template>
  <a-modal
    :title="title"
    :width="620"
    :visible="visible"
    :confirmLoading="confirmLoading"
    :maskClosable="false"
    :destroyOnClose="true"
    :okText="okText"
    cancelText="取消"
    @ok="handleOk"
    @cancel="close">
    <a-alert
      v-if="tip"
      class="doc-archive__tip"
      type="info"
      show-icon
      :message="tip" />

    <a-form-model ref="form" :model="model" :rules="rules" :label-col="labelCol" :wrapper-col="wrapperCol">
      <a-form-model-item label="归档到档案类别" prop="categoryId">
        <category-picker
          ref="category"
          v-model="model.categoryId"
          :allowClear="false"
          placeholder="必须选择末级档案类别" />
        <div class="doc-archive__hint">
          公文的所有附件会成为该档案的卷内文件，统一挂在这个类别下；归档后可到「档案管理 → 档案维护」继续补充材料。
        </div>
      </a-form-model-item>

      <a-form-model-item label="档案名称" prop="archiveName">
        <a-input v-model="model.archiveName" :maxLength="255" placeholder="默认取公文标题，可修改" />
      </a-form-model-item>

      <a-form-model-item label="归档日期" prop="archiveDate">
        <a-date-picker v-model="model.archiveDate" style="width: 100%" valueFormat="YYYY-MM-DD" placeholder="选择归档日期" />
      </a-form-model-item>

      <a-form-model-item label="密级" prop="secretLevel">
        <a-select v-model="model.secretLevel" allowClear placeholder="默认取公文密级">
          <a-select-option v-for="item in secretLevels" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>
      </a-form-model-item>
    </a-form-model>
  </a-modal>
</template>

<script>
  import CategoryPicker from '@/views/land/archive/modules/CategoryPicker'
  import { archiveDocReceive, archiveDocSend } from '@/api/land/document'
  import { queryArchiveCategoryTree } from '@/api/land/archiveCategory'

  /**
   * 收发文归档弹窗
   *
   * 需求约定：「给出提示，让用户选择是否归档，如果归档同时要选择档案类别（必选）」。
   * 因此调用方在办结/归档前先弹确认框，用户确认后再打开本弹窗，
   * 本弹窗里档案类别是<b>必填</b>项，未选不允许提交。
   *
   * 收文与发文共用这一个弹窗，靠 docType 区分（receive / send）。
   */
  export default {
    name: 'DocArchiveModal',
    components: { CategoryPicker },
    data () {
      return {
        title: '归档到档案管理',
        tip: '',
        okText: '确认归档',
        visible: false,
        confirmLoading: false,
        docType: 'receive',
        doc: null,
        model: {
          categoryId: undefined,
          archiveName: '',
          archiveDate: this.formatDate(new Date()),
          secretLevel: undefined
        },
        secretLevels: ['一般', '内部', '秘密', '机密'],
        labelCol: { xs: { span: 24 }, sm: { span: 7 } },
        wrapperCol: { xs: { span: 24 }, sm: { span: 17 } }
      }
    },
    computed: {
      rules () {
        return {
          categoryId: [{ required: true, message: '归档必须选择档案类别', trigger: 'change' }]
        }
      }
    },
    methods: {
      /**
       * @param {object} doc 收文/发文记录
       * @param {string} docType receive / send
       */
      open (doc, docType) {
        if (!doc || !doc.id) {
          this.$message.warning('缺少待归档的公文')
          return
        }
        this.doc = doc
        this.docType = docType || 'receive'
        this.title = this.docType === 'send' ? '发文归档' : '收文归档'
        this.tip = `将把「${doc.docNo || ''} ${doc.docTitle || ''}」及其全部附件归档为一个档案`
        this.model = {
          categoryId: undefined,
          archiveName: doc.docTitle || '',
          archiveDate: this.formatDate(new Date()),
          secretLevel: doc.secretLevel || undefined
        }
        this.visible = true
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate()
          }
          // 类别树按需刷新，避免用户刚在类别管理里加了类别却选不到
          if (this.$refs.category) {
            this.$refs.category.refresh()
          }
        })
      },
      handleOk () {
        this.$refs.form.validate(valid => {
          if (!valid) {
            return
          }
          if (!this.model.categoryId) {
            this.$message.warning('归档必须选择档案类别')
            return
          }
          this.submit()
        })
      },
      submit () {
        const payload = {
          docId: this.doc.id,
          categoryId: this.model.categoryId,
          archiveName: this.model.archiveName || undefined,
          archiveDate: this.model.archiveDate || undefined,
          secretLevel: this.model.secretLevel || undefined
        }
        this.confirmLoading = true
        const task = this.docType === 'send' ? archiveDocSend(payload) : archiveDocReceive(payload)
        task.then(res => {
          if (res.success) {
            this.$message.success('归档成功，已生成档案' + (res.result ? `（ID：${res.result}）` : ''))
            this.visible = false
            this.$emit('ok', res.result)
          } else {
            this.$message.warning(res.message)
          }
        }).finally(() => {
          this.confirmLoading = false
        })
      },
      close () {
        this.visible = false
      },
      formatDate (date) {
        const d = date || new Date()
        const month = String(d.getMonth() + 1).padStart(2, '0')
        const day = String(d.getDate()).padStart(2, '0')
        return `${d.getFullYear()}-${month}-${day}`
      },
      /** 供调用方预热类别树（可选） */
      warmUpCategories () {
        return queryArchiveCategoryTree({ status: 1 })
      }
    }
  }
</script>

<style lang="less" scoped>
  .doc-archive {
    &__tip {
      margin-bottom: 16px;
    }

    &__hint {
      margin-top: 6px;
      font-size: 12px;
      line-height: 18px;
      color: #94a3b8;
    }
  }
</style>
