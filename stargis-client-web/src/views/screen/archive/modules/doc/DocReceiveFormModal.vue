<template>
  <!--
    DocReceiveFormModal 收文 登记 / 编辑 弹窗
    --------------------------------
    三段式：关联项目 → 收文信息 → 公文附件。

    注意「编辑不推进流转」：
      旧 tj-sfw 把流转逻辑塞在 updateById 里然后整段注释掉，既没生效也不好排查；
      本实现的编辑只负责改信息与附件，转办 / 退回 / 办结走三个独立的语义化接口
      （DocFlowModal）。

    公开方法：
      showAdd()        收文登记
      showEdit(record) 编辑收文（按 id 拉详情，含附件）
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

      <!-- ================= 收文信息 ================= -->
      <section class="doc-form__section">
        <h4 class="doc-form__section-title">
          <screen-icon name="inbox" :size="14" />
          收文信息
        </h4>

        <div class="doc-form__grid">
          <screen-field label="收文登记号" :error="errors.docNo" html-for="drf-no" tip="默认规则 SW-{年份}-{4位流水}，可手工改写">
            <div class="doc-form__inline">
              <screen-input
                id="drf-no"
                v-model="model.docNo"
                :maxlength="64"
                :invalid="!!errors.docNo"
                placeholder="留空则保存时由后端生成"
              />
              <screen-button :loading="noLoading" @click="handleGenerateNo">自动生成</screen-button>
            </div>
          </screen-field>

          <screen-field label="文件标题" required :error="errors.docTitle" html-for="drf-title">
            <screen-input
              id="drf-title"
              v-model="model.docTitle"
              :maxlength="500"
              :invalid="!!errors.docTitle"
              placeholder="公文标题"
            />
          </screen-field>

          <screen-field label="来文单位" html-for="drf-from">
            <screen-input id="drf-from" v-model="model.fromDept" :maxlength="200" placeholder="如来文单位名称" />
          </screen-field>

          <screen-field label="来文字号" html-for="drf-fromno">
            <screen-input id="drf-fromno" v-model="model.fromDocNo" :maxlength="100" placeholder="如来文字号" />
          </screen-field>

          <screen-field label="收文日期">
            <screen-date-input v-model="model.receiveDate" />
          </screen-field>

          <screen-field label="文件类型">
            <screen-select v-model="model.docType" :options="docTypeOptions" placeholder="请选择" aria-label="文件类型" />
          </screen-field>

          <screen-field label="紧急程度">
            <screen-select v-model="model.urgency" :options="urgencyOptions" placeholder="请选择" aria-label="紧急程度" />
          </screen-field>

          <screen-field label="密级">
            <screen-select v-model="model.secretLevel" :options="secretOptions" placeholder="请选择" aria-label="密级" />
          </screen-field>

          <screen-field label="页数" html-for="drf-page">
            <screen-input id="drf-page" v-model="model.pageCount" type="number" :min="0" placeholder="可留空" />
          </screen-field>

          <screen-field label="份数" html-for="drf-copies">
            <screen-input id="drf-copies" v-model="model.copies" type="number" :min="0" placeholder="可留空" />
          </screen-field>

          <screen-field label="办理期限">
            <screen-date-input v-model="model.handleDeadline" />
          </screen-field>

          <screen-field
            label="承办人"
            :tip="isEdit
              ? '流转（转办 / 退回 / 办结）请在列表点「流转」操作，编辑不会推进流程。'
              : '选定后系统会立即为该承办人生成一条待办流转记录。'"
          >
            <doc-user-select
              v-model="model.currentHandler"
              :disabled="isEdit"
              placeholder="选择承办人（可留空，稍后分办）"
            />
          </screen-field>

          <screen-field class="doc-form__span-all" label="备注" html-for="drf-remark">
            <screen-input id="drf-remark" v-model="model.remark" :maxlength="1000" placeholder="可留空" />
          </screen-field>
        </div>
      </section>

      <!-- ================= 附件 ================= -->
      <section class="doc-form__section">
        <h4 class="doc-form__section-title">
          <screen-icon name="paperclip" :size="14" />
          公文附件
          <span class="doc-form__section-hint">扫描件 / 正文 / 附件可一并上传，归档时会成为档案的卷内文件</span>
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
import DocUserSelect from './DocUserSelect.vue'
import {
  addDocReceive,
  editDocReceive,
  queryDocReceiveById,
  generateDocReceiveNo,
} from '@/api/land/document'
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
  name: 'DocReceiveFormModal',
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
    DocUserSelect,
  },
  data () {
    return {
      title: '收文登记',
      visible: false,
      loading: false,
      confirmLoading: false,
      noLoading: false,
      isEdit: false,
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
    /** 上传子目录：/receive/{yyyy}/{MM} */
    bizPath () {
      return buildBizPath(new Date(), 'receive')
    },
  },
  methods: {
    buildEmptyModel () {
      return {
        id: null,
        docNo: '',
        docTitle: '',
        docType: '',
        fromDept: '',
        fromDocNo: '',
        receiveDate: today(),
        urgency: '普通',
        secretLevel: '一般',
        pageCount: '',
        copies: '',
        handleDeadline: '',
        currentHandler: '',
        remark: '',
      }
    },

    /* ---------------- 对外入口 ---------------- */

    showAdd () {
      this.title = '收文登记'
      this.isEdit = false
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
        toast.warning('请选择要编辑的收文')
        return
      }
      this.title = '编辑收文'
      this.isEdit = true
      this.model = this.buildEmptyModel()
      this.association = {}
      this.attachments = []
      this.errors = {}
      this.submitted = false
      this.visible = true
      this.loading = true
      this.resetUploader()

      queryDocReceiveById(record.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '收文详情加载失败')
            this.visible = false
            return
          }
          const doc = res.result || {}
          this.model = {
            id: doc.id,
            docNo: doc.docNo || '',
            docTitle: doc.docTitle || '',
            docType: doc.docType || '',
            fromDept: doc.fromDept || '',
            fromDocNo: doc.fromDocNo || '',
            receiveDate: doc.receiveDate || today(),
            urgency: doc.urgency || '普通',
            secretLevel: doc.secretLevel || '一般',
            pageCount: doc.pageCount === null || doc.pageCount === undefined ? '' : doc.pageCount,
            copies: doc.copies === null || doc.copies === undefined ? '' : doc.copies,
            handleDeadline: doc.handleDeadline || '',
            currentHandler: doc.currentHandler || '',
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
      const year = yearOf(this.model.receiveDate)
      generateDocReceiveNo(year)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '登记号生成失败')
            return
          }
          this.model.docNo = res.result || res.message || ''
          toast.success(`已生成收文登记号 ${this.model.docNo}`)
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
      const payload = Object.assign({}, this.model, {
        landId: association.landId || null,
        crzdbh: association.crzdbh || null,
        facilityId: association.facilityId || null,
        ptxmmc: association.ptxmmc || null,
        docNo: this.model.docNo || null,
        // 空串会让后端的数字/日期字段解析失败，统一转成 null
        pageCount: this.model.pageCount === '' ? null : Number(this.model.pageCount),
        copies: this.model.copies === '' ? null : Number(this.model.copies),
        handleDeadline: this.model.handleDeadline || null,
        docType: this.model.docType || null,
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
      // 编辑时不提交 currentHandler：流转不走这里，否则会覆盖当前待办人
      if (this.isEdit) delete payload.currentHandler
      return payload
    },

    submit () {
      const payload = this.buildPayload()
      this.confirmLoading = true
      const task = payload.id ? editDocReceive(payload) : addDocReceive(payload)

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
    background: rgba(6, 20, 40, 0.5);
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
