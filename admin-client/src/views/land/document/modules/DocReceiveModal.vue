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

        <!-- ============ 收文信息 ============ -->
        <div class="form-section">
          <div class="form-section__title">
            <a-icon type="import" />
            <span>收文信息</span>
          </div>

          <a-row :gutter="16">
            <a-col :xs="24" :md="12">
              <a-form-model-item label="收文登记号" prop="docNo">
                <a-input-group compact>
                  <a-input
                    v-model="model.docNo"
                    style="width: calc(100% - 92px)"
                    :maxLength="64"
                    placeholder="留空自动生成" />
                  <a-button style="width: 92px" :loading="noLoading" @click="handleGenerateNo">自动生成</a-button>
                </a-input-group>
                <div class="form-item-tip">默认规则 SW-{年份}-{4位流水}，可手工改写（会做唯一性校验）。</div>
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
              <a-form-model-item label="来文单位" prop="fromDept">
                <a-input v-model="model.fromDept" :maxLength="200" placeholder="如来文单位名称" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="来文字号" prop="fromDocNo">
                <a-input v-model="model.fromDocNo" :maxLength="100" placeholder="如来文字号" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="收文日期" prop="receiveDate">
                <a-date-picker v-model="model.receiveDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
              </a-form-model-item>
            </a-col>
          </a-row>

          <a-row :gutter="16">
            <a-col :xs="24" :md="8">
              <a-form-model-item label="文件类型" prop="docType">
                <a-select v-model="model.docType" allowClear placeholder="请选择">
                  <a-select-option v-for="item in docTypes" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="紧急程度" prop="urgency">
                <a-select v-model="model.urgency" allowClear placeholder="请选择">
                  <a-select-option v-for="item in urgencies" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="密级" prop="secretLevel">
                <a-select v-model="model.secretLevel" allowClear placeholder="请选择">
                  <a-select-option v-for="item in secretLevels" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
          </a-row>

          <a-row :gutter="16">
            <a-col :xs="24" :md="8">
              <a-form-model-item label="页数" prop="pageCount">
                <a-input-number v-model="model.pageCount" :min="0" :precision="0" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="份数" prop="copies">
                <a-input-number v-model="model.copies" :min="0" :precision="0" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="办理期限" prop="handleDeadline">
                <a-date-picker v-model="model.handleDeadline" style="width: 100%" valueFormat="YYYY-MM-DD" />
              </a-form-model-item>
            </a-col>
          </a-row>

          <a-row :gutter="16">
            <a-col :xs="24" :md="12">
              <a-form-model-item label="承办人" prop="currentHandler">
                <user-select v-model="model.currentHandler" :disabled="isEdit" placeholder="选择承办人（可留空，稍后分办）" />
                <div class="form-item-tip">
                  {{ isEdit ? '流转（转办/退回/办结）请在列表点「流转」操作，编辑不会推进流程。' : '选定后系统会立即为该承办人生成一条待办流转记录。' }}
                </div>
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
            <span class="form-section__hint">扫描件 / 正文 / 附件可一并上传，归档时会成为档案的卷内文件</span>
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
  import UserSelect from './UserSelect'
  import {
    addDocReceive, editDocReceive, queryDocReceiveById, generateDocReceiveNo
  } from '@/api/land/document'

  /**
   * 收文 登记 / 编辑 弹窗
   *
   * 注意「编辑不推进流转」：
   * 旧 tj-sfw 把流转逻辑塞在 updateById 里然后整段注释掉，既没生效也不好排查；
   * 本实现的编辑只负责改信息与附件，转办 / 退回 / 办结走三个独立的语义化接口。
   */
  export default {
    name: 'DocReceiveModal',
    components: { ProjectPicker, DocAttachmentTable, UserSelect },
    data () {
      return {
        title: '收文登记',
        visible: false,
        loading: false,
        confirmLoading: false,
        noLoading: false,
        isEdit: false,
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
        return `/receive/${now.getFullYear()}/${month}`
      }
    },
    methods: {
      buildEmptyModel () {
        const today = this.formatDate(new Date())
        return {
          id: null,
          docNo: '',
          docTitle: '',
          docType: undefined,
          fromDept: '',
          fromDocNo: '',
          receiveDate: today,
          urgency: '普通',
          secretLevel: '一般',
          pageCount: undefined,
          copies: undefined,
          handleDeadline: undefined,
          currentHandler: undefined,
          remark: ''
        }
      },
      showAdd () {
        this.title = '收文登记'
        this.isEdit = false
        this.model = this.buildEmptyModel()
        this.association = {}
        this.attachments = []
        this.visible = true
        this.clearValidate()
      },
      showEdit (record) {
        if (!record || !record.id) {
          this.$message.warning('请选择要编辑的收文')
          return
        }
        this.title = '编辑收文'
        this.isEdit = true
        this.visible = true
        this.loading = true
        queryDocReceiveById(record.id).then(res => {
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
            fromDept: doc.fromDept || '',
            fromDocNo: doc.fromDocNo || '',
            receiveDate: doc.receiveDate || this.formatDate(new Date()),
            urgency: doc.urgency || '普通',
            secretLevel: doc.secretLevel || '一般',
            pageCount: doc.pageCount,
            copies: doc.copies,
            handleDeadline: doc.handleDeadline || undefined,
            currentHandler: doc.currentHandler || undefined,
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
        const year = Number(String(this.model.receiveDate || '').substring(0, 4)) || new Date().getFullYear()
        generateDocReceiveNo(year).then(res => {
          if (res.success) {
            this.model.docNo = res.result
            this.$message.success(`已生成收文登记号 ${res.result}`)
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
        // 编辑时不要把 currentHandler 交上去（流转不走这里）
        if (this.isEdit) {
          delete payload.currentHandler
        }

        this.confirmLoading = true
        const task = payload.id ? editDocReceive(payload) : addDocReceive(payload)
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
