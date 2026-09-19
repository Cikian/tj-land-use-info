<template>
  <a-modal
    :title="title"
    :width="980"
    :visible="visible"
    :confirmLoading="confirmLoading"
    :maskClosable="false"
    :destroyOnClose="true"
    okText="保存"
    cancelText="取消"
    @ok="handleOk"
    @cancel="close">
    <a-spin :spinning="loading">
      <a-form-model ref="form" :model="model" :rules="rules" :label-col="labelCol" :wrapper-col="wrapperCol">
        <!-- ============ 关联项目 ============ -->
        <div class="form-section">
          <div class="form-section__title">
            <a-icon type="link" />
            <span>关联项目</span>
            <span class="form-section__hint">先选出让宗地，再联动选择该宗地下的配套项目</span>
          </div>
          <project-picker v-model="association" :required="true" />
        </div>

        <!-- ============ 发文信息 ============ -->
        <div class="form-section">
          <div class="form-section__title">
            <a-icon type="export" />
            <span>发文信息</span>
            <span class="form-section__hint">发文是台账记录，没有流转；办结后可直接归档</span>
          </div>

          <a-row :gutter="16">
            <a-col :xs="24" :md="12">
              <a-form-model-item label="发文登记号" prop="docNo">
                <a-input-group compact>
                  <a-input
                    v-model="model.docNo"
                    style="width: calc(100% - 92px)"
                    :maxLength="64"
                    placeholder="留空自动生成" />
                  <a-button style="width: 92px" :loading="noLoading" @click="handleGenerateNo">自动生成</a-button>
                </a-input-group>
                <div class="form-item-tip">默认规则 FW-{年份}-{4位流水}，可手工改写（会做唯一性校验）。</div>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-model-item label="文件标题" prop="docTitle">
                <a-input v-model="model.docTitle" :maxLength="500" placeholder="公文标题" />
              </a-form-model-item>
            </a-col>
          </a-row>

          <a-row :gutter="16">
            <a-col :xs="24" :md="8">
              <a-form-model-item label="主送单位" prop="toDept">
                <a-input v-model="model.toDept" :maxLength="500" placeholder="多个单位用「、」分隔" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="抄送单位" prop="ccDept">
                <a-input v-model="model.ccDept" :maxLength="500" placeholder="可留空" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="发文日期" prop="issueDate">
                <a-date-picker v-model="model.issueDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
              </a-form-model-item>
            </a-col>
          </a-row>

          <a-row :gutter="16">
            <a-col :xs="24" :md="6">
              <a-form-model-item label="文件类型" prop="docType">
                <a-select v-model="model.docType" allowClear placeholder="请选择">
                  <a-select-option v-for="item in docTypes" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="6">
              <a-form-model-item label="签发人" prop="signer">
                <a-input v-model="model.signer" :maxLength="64" placeholder="可留空" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="6">
              <a-form-model-item label="拟稿人" prop="drafter">
                <a-input v-model="model.drafter" :maxLength="64" placeholder="可留空" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="6">
              <a-form-model-item label="份数" prop="copies">
                <a-input-number v-model="model.copies" :min="0" :precision="0" style="width: 100%" />
              </a-form-model-item>
            </a-col>
          </a-row>

          <a-row :gutter="16">
            <a-col :xs="24" :md="6">
              <a-form-model-item label="密级" prop="secretLevel">
                <a-select v-model="model.secretLevel" allowClear placeholder="请选择">
                  <a-select-option v-for="item in secretLevels" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="6">
              <a-form-model-item label="紧急程度" prop="urgency">
                <a-select v-model="model.urgency" allowClear placeholder="请选择">
                  <a-select-option v-for="item in urgencies" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-model-item label="备注" prop="remark">
                <a-input v-model="model.remark" :maxLength="1000" placeholder="可留空" />
              </a-form-model-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 附件 ============ -->
        <div class="form-section">
          <div class="form-section__title">
            <a-icon type="paper-clip" />
            <span>公文附件</span>
            <span class="form-section__hint">归档时会成为档案的卷内文件</span>
          </div>
          <doc-attachment-table v-model="attachments" :bizPath="bizPath" />
        </div>
      </a-form-model>
    </a-spin>
  </a-modal>
</template>

<script>
  import ProjectPicker from '@/views/land/archive/modules/ProjectPicker'
  import DocAttachmentTable from './DocAttachmentTable'
  import { addDocSend, editDocSend, queryDocSendById, generateDocSendNo } from '@/api/land/document'

  /**
   * 发文 登记 / 编辑 弹窗
   *
   * 方案原文对发文的要求只有「发文的信息记录」，所以这里是纯台账表单：
   * 登记 / 编辑 / 附件 / 归档，没有流转步骤。
   * 注意修正了旧系统的字段语义反置问题（旧 fileReceiveNum 实际存的是发文文号），
   * 新表用语义正确的 docNo（发文登记号）。
   */
  export default {
    name: 'DocSendModal',
    components: { ProjectPicker, DocAttachmentTable },
    data () {
      return {
        title: '发文登记',
        visible: false,
        loading: false,
        confirmLoading: false,
        noLoading: false,
        labelCol: { xs: { span: 24 }, sm: { span: 6 } },
        wrapperCol: { xs: { span: 24 }, sm: { span: 18 } },
        model: this.buildEmptyModel(),
        association: {},
        attachments: [],
        docTypes: ['通知', '函', '批复', '报告', '其他'],
        urgencies: ['普通', '急件', '特急'],
        secretLevels: ['一般', '内部', '秘密', '机密']
      }
    },
    computed: {
      rules () {
        return {
          docTitle: [
            { required: true, message: '请输入文件标题', trigger: 'blur' },
            { max: 500, message: '文件标题不能超过 500 个字符', trigger: 'blur' }
          ],
          docNo: [{ max: 64, message: '登记号不能超过 64 个字符', trigger: 'blur' }]
        }
      },
      bizPath () {
        const now = new Date()
        const month = String(now.getMonth() + 1).padStart(2, '0')
        return `/send/${now.getFullYear()}/${month}`
      }
    },
    methods: {
      buildEmptyModel () {
        return {
          id: null,
          docNo: '',
          docTitle: '',
          docType: undefined,
          toDept: '',
          ccDept: '',
          issueDate: this.formatDate(new Date()),
          signer: '',
          drafter: '',
          secretLevel: '一般',
          urgency: '普通',
          copies: undefined,
          remark: ''
        }
      },
      showAdd () {
        this.title = '发文登记'
        this.model = this.buildEmptyModel()
        this.association = {}
        this.attachments = []
        this.visible = true
        this.clearValidate()
      },
      showEdit (record) {
        if (!record || !record.id) {
          this.$message.warning('请选择要编辑的发文')
          return
        }
        this.title = '编辑发文'
        this.visible = true
        this.loading = true
        queryDocSendById(record.id).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            this.visible = false
            return
          }
          const doc = res.result || {}
          this.model = {
            id: doc.id,
            docNo: doc.docNo,
            docTitle: doc.docTitle,
            docType: doc.docType || undefined,
            toDept: doc.toDept || '',
            ccDept: doc.ccDept || '',
            issueDate: doc.issueDate || this.formatDate(new Date()),
            signer: doc.signer || '',
            drafter: doc.drafter || '',
            secretLevel: doc.secretLevel || '一般',
            urgency: doc.urgency || '普通',
            copies: doc.copies,
            remark: doc.remark || ''
          }
          this.association = {
            landId: doc.landId,
            crzdbh: doc.crzdbh,
            facilityId: doc.facilityId,
            ptxmmc: doc.ptxmmc
          }
          this.attachments = (doc.attachments || []).map(item => Object.assign({}, item))
        }).finally(() => {
          this.loading = false
        })
      },
      clearValidate () {
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate()
          }
        })
      },
      handleGenerateNo () {
        this.noLoading = true
        const year = Number(String(this.model.issueDate || '').substring(0, 4)) || new Date().getFullYear()
        generateDocSendNo(year).then(res => {
          if (res.success) {
            this.model.docNo = res.result
            this.$message.success(`已生成发文登记号 ${res.result}`)
          } else {
            this.$message.warning(res.message)
          }
        }).finally(() => {
          this.noLoading = false
        })
      },
      handleOk () {
        this.$refs.form.validate(valid => {
          if (!valid) {
            return
          }
          if (!this.association.facilityId) {
            this.$message.warning('请选择关联的配套项目')
            return
          }
          this.submit()
        })
      },
      submit () {
        const payload = Object.assign({}, this.model, {
          landId: this.association.landId,
          crzdbh: this.association.crzdbh,
          facilityId: this.association.facilityId,
          ptxmmc: this.association.ptxmmc,
          docNo: this.model.docNo || null,
          attachments: this.attachments.map((item, index) => ({
            id: item.id || null,
            fileName: item.fileName,
            fileExt: item.fileExt,
            fileSize: item.fileSize,
            fileMd5: item.fileMd5,
            storeType: item.storeType || 'local',
            storePath: item.storePath,
            sortNo: index + 1
          }))
        })

        this.confirmLoading = true
        const task = payload.id ? editDocSend(payload) : addDocSend(payload)
        task.then(res => {
          if (res.success) {
            this.$message.success(res.message || '保存成功')
            this.visible = false
            this.$emit('ok')
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
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-weak: #94a3b8;

  .form-section {
    padding: 16px 16px 4px;
    margin-bottom: 16px;
    border: 1px solid @border-color;
    border-radius: 8px;

    &__title {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 14px;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
    }

    &__hint {
      margin-left: 4px;
      font-size: 12px;
      font-weight: 400;
      color: @text-weak;
    }
  }

  .form-item-tip {
    margin-top: 4px;
    font-size: 12px;
    line-height: 18px;
    color: @text-weak;
  }
</style>
