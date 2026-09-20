<template>
  <!--
    DocSendFormModal 发文 登记 / 编辑 弹窗
    --------------------------------
    方案对发文的要求只有「发文的信息记录」，所以这里是**纯台账表单**：
    登记 / 编辑 / 附件 / 归档，没有流转步骤。

    与收文的差别（表单字段）：
      收文有 来文单位 / 来文字号 / 收文日期 / 页数 / 办理期限 / 承办人；
      发文换成 主送单位 / 抄送单位 / 发文日期 / 签发人 / 拟稿人。

    公开方法：
      showAdd()        发文登记
      showEdit(record) 编辑发文（按 id 拉详情，含附件）
    事件：
      ok  保存成功后抛出
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="1040"
    :confirm-loading="confirmLoading"
    ok-text="保存"
    cancel-text="取消"
    :body-max-height="'calc(100vh - 200px)'"
    @ok="handleOk"
    @cancel="close"
  >
    <div class="doc-form">
      <!-- ================= 关联项目 ================= -->
      <section class="doc-form__section">
        <h4 class="doc-form__section-title">
          <screen-icon name="link" :size="14" />
          关联项目
          <span class="doc-form__section-hint">先选出让宗地，再联动选择该宗地下的配套项目</span>
        </h4>
        <project-picker v-model="association" :required="true" :show-invalid="submitted && !association.facilityId" />
        <p v-if="submitted && !association.facilityId" class="doc-form__error" role="alert">
          请选择关联的配套项目
        </p>
      </section>

      <!-- ================= 发文信息 ================= -->
      <section class="doc-form__section">
        <h4 class="doc-form__section-title">
          <screen-icon name="send" :size="14" />
          发文信息
          <span class="doc-form__section-hint">发文是台账记录，没有流转；登记后可直接归档</span>
        </h4>

        <div class="doc-form__grid">
          <screen-field label="发文登记号" :error="errors.docNo" html-for="dsf-no" tip="默认规则 FW-{年份}-{4位流水}，可手工改写">
            <div class="doc-form__inline">
              <screen-input
                id="dsf-no"
                v-model="model.docNo"
                :maxlength="64"
                :invalid="!!errors.docNo"
                placeholder="留空则保存时由后端生成"
              />
              <screen-button :loading="noLoading" @click="handleGenerateNo">自动生成</screen-button>
            </div>
          </screen-field>

          <screen-field label="文件标题" required :error="errors.docTitle" html-for="dsf-title">
            <screen-input
              id="dsf-title"
              v-model="model.docTitle"
              :maxlength="500"
              :invalid="!!errors.docTitle"
              placeholder="公文标题"
            />
          </screen-field>

          <screen-field label="发文日期">
            <screen-date-input v-model="model.issueDate" />
          </screen-field>

          <screen-field label="主送单位" html-for="dsf-to" tip="多个单位用「、」分隔">
            <screen-input id="dsf-to" v-model="model.toDept" :maxlength="500" placeholder="如：市住建委、市规划资源局" />
          </screen-field>

          <screen-field label="抄送单位" html-for="dsf-cc">
            <screen-input id="dsf-cc" v-model="model.ccDept" :maxlength="500" placeholder="可留空" />
          </screen-field>

          <screen-field label="文件类型">
            <screen-select v-model="model.docType" :options="docTypeOptions" placeholder="请选择" aria-label="文件类型" />
          </screen-field>

          <screen-field label="签发人" html-for="dsf-signer">
            <screen-input id="dsf-signer" v-model="model.signer" :maxlength="64" placeholder="可留空" />
          </screen-field>

          <screen-field label="拟稿人" html-for="dsf-drafter">
            <screen-input id="dsf-drafter" v-model="model.drafter" :maxlength="64" placeholder="可留空" />
          </screen-field>

          <screen-field label="份数" html-for="dsf-copies">
            <screen-input id="dsf-copies" v-model="model.copies" type="number" :min="0" placeholder="可留空" />
          </screen-field>

          <screen-field label="密级">
            <screen-select v-model="model.secretLevel" :options="secretOptions" placeholder="请选择" aria-label="密级" />
          </screen-field>

          <screen-field label="紧急程度">
            <screen-select v-model="model.urgency" :options="urgencyOptions" placeholder="请选择" aria-label="紧急程度" />
          </screen-field>

          <screen-field class="doc-form__span-all" label="备注" html-for="dsf-remark">
            <screen-input id="dsf-remark" v-model="model.remark" :maxlength="1000" placeholder="可留空" />
          </screen-field>
        </div>
      </section>

      <!-- ================= 附件 ================= -->
      <section class="doc-form__section">
        <h4 class="doc-form__section-title">
          <screen-icon name="paperclip" :size="14" />
          公文附件
          <span class="doc-form__section-hint">归档时会成为档案的卷内文件</span>
        </h4>
        <doc-attachment-table ref="attachmentTable" v-model="attachments" :biz-path="bizPath" />
      </section>
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
  ScreenButton,
  ScreenIcon,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import ProjectPicker from '../ProjectPicker.vue'
import DocAttachmentTable from './DocAttachmentTable.vue'
import { addDocSend, editDocSend, queryDocSendById, generateDocSendNo } from '@/api/land/document'
import {
  DOC_TYPES,
  DOC_URGENCIES,
  SECRET_LEVELS,
  toOptions,
  today,
  yearOf,
  buildBizPath,
} from '../../constants'

export default {
  name: 'DocSendFormModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon,
    ProjectPicker,
    DocAttachmentTable,
  },
  data () {
    return {
      title: '发文登记',
      visible: false,
      loading: false,
      confirmLoading: false,
      noLoading: false,
      submitted: false,
      model: this.buildEmptyModel(),
      association: {},
      attachments: [],
      errors: {},
    }
  },
  computed: {
    docTypeOptions () {
      return toOptions(DOC_TYPES)
    },
    urgencyOptions () {
      return toOptions(DOC_URGENCIES)
    },
    secretOptions () {
      return toOptions(SECRET_LEVELS)
    },
    /** 上传子目录：/send/{yyyy}/{MM} */
    bizPath () {
      return buildBizPath(new Date(), 'send')
    },
  },
  methods: {
    buildEmptyModel () {
      return {
        id: null,
        docNo: '',
        docTitle: '',
        docType: '',
        toDept: '',
        ccDept: '',
        issueDate: today(),
        signer: '',
        drafter: '',
        secretLevel: '一般',
        urgency: '普通',
        copies: '',
        remark: '',
      }
    },

    /* ---------------- 对外入口 ---------------- */

    showAdd () {
      this.title = '发文登记'
      this.model = this.buildEmptyModel()
      this.association = {}
      this.attachments = []
      this.errors = {}
      this.submitted = false
      this.visible = true
      this.resetUploader()
    },

    showEdit (record) {
      if (!record || !record.id) {
        toast.warning('请选择要编辑的发文')
        return
      }
      this.title = '编辑发文'
      this.model = this.buildEmptyModel()
      this.association = {}
      this.attachments = []
      this.errors = {}
      this.submitted = false
      this.visible = true
      this.loading = true
      this.resetUploader()

      queryDocSendById(record.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '发文详情加载失败')
            this.visible = false
            return
          }
          const doc = res.result || {}
          this.model = {
            id: doc.id,
            docNo: doc.docNo || '',
            docTitle: doc.docTitle || '',
            docType: doc.docType || '',
            toDept: doc.toDept || '',
            ccDept: doc.ccDept || '',
            issueDate: doc.issueDate || today(),
            signer: doc.signer || '',
            drafter: doc.drafter || '',
            secretLevel: doc.secretLevel || '一般',
            urgency: doc.urgency || '普通',
            copies: doc.copies === null || doc.copies === undefined ? '' : doc.copies,
            remark: doc.remark || '',
          }
          this.association = {
            landId: doc.landId || null,
            crzdbh: doc.crzdbh || null,
            facilityId: doc.facilityId || null,
            ptxmmc: doc.ptxmmc || null,
          }
          this.attachments = (doc.attachments || []).map((item) => Object.assign({}, item))
        })
        .finally(() => {
          this.loading = false
        })
    },

    /** 清掉上一次打开时残留的上传队列（多 tick 重试，避免插槽尚未挂载） */
    resetUploader () {
      const tryClear = (attempt) => {
        this.$nextTick(() => {
          const table = this.$refs.attachmentTable
          const uploader = table && table.$refs && table.$refs.uploader
          if (uploader && typeof uploader.clear === 'function') {
            uploader.clear()
            return
          }
          if (attempt < 4) tryClear(attempt + 1)
        })
      }
      tryClear(0)
    },

    /* ---------------- 交互 ---------------- */

    handleGenerateNo () {
      this.noLoading = true
      const year = yearOf(this.model.issueDate)
      generateDocSendNo(year)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '登记号生成失败')
            return
          }
          this.model.docNo = res.result || res.message || ''
          toast.success(`已生成发文登记号 ${this.model.docNo}`)
        })
        .finally(() => {
          this.noLoading = false
        })
    },

    /* ---------------- 校验与提交 ---------------- */

    validate () {
      const errors = {}
      const title = String(this.model.docTitle || '').trim()
      if (!title) errors.docTitle = '请输入文件标题'
      else if (title.length > 500) errors.docTitle = '文件标题不能超过 500 个字符'
      if (String(this.model.docNo || '').length > 64) errors.docNo = '登记号不能超过 64 个字符'
      this.errors = errors
      return Object.keys(errors).length === 0 && !!this.association.facilityId
    },

    handleOk () {
      this.submitted = true
      if (!this.validate()) {
        toast.warning('还有必填项未完成，请检查标红的字段')
        return
      }
      this.submit()
    },

    buildPayload () {
      const association = this.association || {}
      return Object.assign({}, this.model, {
        landId: association.landId || null,
        crzdbh: association.crzdbh || null,
        facilityId: association.facilityId || null,
        ptxmmc: association.ptxmmc || null,
        docNo: this.model.docNo || null,
        docType: this.model.docType || null,
        copies: this.model.copies === '' ? null : Number(this.model.copies),
        attachments: (this.attachments || []).map((item, index) => ({
          id: item.id || null,
          fileName: item.fileName,
          fileExt: item.fileExt || null,
          fileSize: item.fileSize || 0,
          fileMd5: item.fileMd5 || null,
          storeType: item.storeType || 'local',
          storePath: item.storePath,
          sortNo: item.sortNo || index + 1,
        })),
      })
    },

    submit () {
      const payload = this.buildPayload()
      this.confirmLoading = true
      const task = payload.id ? editDocSend(payload) : addDocSend(payload)

      task
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '保存失败')
            return
          }
          toast.success(res.message || '保存成功')
          this.visible = false
          this.$emit('ok', payload.id)
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

.doc-form {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-4);

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    padding: var(--screen-space-3);
    background: rgba(2, 24, 28, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);
  }

  &__section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: var(--screen-font-md);
    font-weight: 600;
    color: var(--screen-text);

    /deep/ .screen-icon {
      color: var(--screen-accent);
    }
  }

  &__section-hint {
    margin-left: 4px;
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  &__span-all {
    grid-column-start: 1;
    grid-column-end: -1;
  }

  &__inline {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    min-width: 0;

    /deep/ .screen-input {
      flex: 1 1 auto;
      min-width: 0;
    }

    /deep/ .screen-btn {
      flex: 0 0 auto;
    }
  }

  &__error {
    margin: 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-danger);
  }
}

@media (max-width: 1500px) {
  .doc-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1000px) {
  .doc-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
