<template>
  <a-modal
    :title="title"
    :width="1200"
    :visible="visible"
    :confirmLoading="confirmLoading"
    :maskClosable="false"
    :destroyOnClose="true"
    okText="保存"
    cancelText="取消"
    @ok="handleOk"
    @cancel="handleCancel">
    <a-spin :spinning="loading">
      <a-form-model ref="form" :model="model" :rules="rules" :label-col="labelCol" :wrapper-col="wrapperCol">
        <!-- ============ 关联项目（第一步：先选宗地 → 再选配套项目） ============ -->
        <div class="form-section">
          <div class="form-section__title">
            <a-icon type="link" />
            <span>关联项目</span>
            <span class="form-section__hint">档案主体是配套项目；先选出让宗地，再联动选择该宗地下的配套项目</span>
          </div>
          <project-picker
            v-model="association"
            :required="true"
            :disabled="false"
            @change="handleAssociationChange" />
        </div>

        <!-- ============ 档案基本信息 ============ -->
        <div class="form-section">
          <div class="form-section__title">
            <a-icon type="file-text" />
            <span>档案基本信息</span>
          </div>

          <a-row :gutter="16">
            <a-col :xs="24" :md="12">
              <a-form-model-item label="档案号" prop="archiveNo">
                <a-input-group compact>
                  <a-input
                    v-model="model.archiveNo"
                    style="width: calc(100% - 92px)"
                    :maxLength="64"
                    placeholder="可手工改写，留空自动生成" />
                  <a-button style="width: 92px" :loading="noLoading" @click="handleGenerateNo">自动生成</a-button>
                </a-input-group>
                <div class="form-item-tip">默认规则 DA-{年份}-{4位流水}；允许按历史档案编号手工改写，保存时会做唯一性校验。</div>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-model-item label="档案名称" prop="archiveName">
                <a-input v-model="model.archiveName" :maxLength="255" placeholder="案卷题名，如「XX路道路工程规划许可证」" />
              </a-form-model-item>
            </a-col>
          </a-row>

          <a-row :gutter="16">
            <a-col :xs="24" :md="8">
              <a-form-model-item label="档案类型" prop="archiveType">
                <a-radio-group v-model="model.archiveType">
                  <a-radio value="electronic">电子档案</a-radio>
                  <a-radio value="paper">纸质档案</a-radio>
                </a-radio-group>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="密级" prop="secretLevel">
                <a-select v-model="model.secretLevel" placeholder="请选择密级">
                  <a-select-option v-for="item in secretLevels" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="8">
              <a-form-model-item label="保管期限" prop="retention">
                <a-select v-model="model.retention" allowClear placeholder="请选择保管期限">
                  <a-select-option v-for="item in retentions" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
          </a-row>

          <a-row :gutter="16">
            <a-col :xs="24" :md="6">
              <a-form-model-item label="责任部门" prop="responsibleDept">
                <a-select v-model="model.responsibleDept" allowClear placeholder="请选择责任部门">
                  <a-select-option v-for="item in depts" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="6">
              <a-form-model-item label="负责人" prop="responsibleUser">
                <a-input v-model="model.responsibleUser" :maxLength="64" placeholder="默认取当前登录人" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="6">
              <a-form-model-item label="档案年度" prop="archiveYear">
                <a-input-number v-model="model.archiveYear" :min="1949" :max="2999" :precision="0" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="6">
              <a-form-model-item label="归档日期" prop="archiveDate">
                <a-date-picker
                  v-model="model.archiveDate"
                  style="width: 100%"
                  valueFormat="YYYY-MM-DD"
                  placeholder="选择日期" />
              </a-form-model-item>
            </a-col>
          </a-row>

          <a-row :gutter="16">
            <a-col :xs="24" :md="8">
              <a-form-model-item label="状态" prop="status">
                <a-select v-model="model.status">
                  <a-select-option v-for="item in statuses" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
                <div class="form-item-tip">置为「已归档」时该档案必须至少有一个卷内文件。</div>
              </a-form-model-item>
            </a-col>
            <a-col :xs="24" :md="16">
              <a-form-model-item label="备注" prop="remark">
                <a-textarea v-model="model.remark" :rows="2" :maxLength="1000" placeholder="补充说明，可留空" />
              </a-form-model-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 档案文件 ============ -->
        <div class="form-section">
          <div class="form-section__title">
            <a-icon type="paper-clip" />
            <span>档案文件</span>
            <span class="form-section__hint">每个文件必须关联一个档案类别（只能是末级类别）</span>
          </div>
          <archive-file-table ref="fileTable" v-model="files" :bizPath="bizPath" />
        </div>
      </a-form-model>
    </a-spin>
  </a-modal>
</template>

<script>
  import { addArchive, editArchive, queryArchiveById, generateArchiveNo } from '@/api/land/archive'
  import ProjectPicker from './ProjectPicker'
  import ArchiveFileTable from './ArchiveFileTable'

  /**
   * 档案 新增 / 编辑 弹窗（方案 2.3.2 第 2 项：档案维护）
   *
   * 需求要点（已与需求方确认）：
   *  1. 档案主体是配套项目，录入时「先选出让宗地 → 再联动选该宗地下的配套项目」；
   *  2. 档案类别挂在「文件」上，每个上传的文件各选一个末级类别，档案本身不设类别；
   *  3. 档案号自动生成（DA-{yyyy}-{4位}），但允许手工改写，保存时做唯一性校验。
   */
  export default {
    name: 'ArchiveModal',
    components: { ProjectPicker, ArchiveFileTable },
    data () {
      return {
        title: '新增档案',
        visible: false,
        loading: false,
        confirmLoading: false,
        noLoading: false,
        labelCol: { xs: { span: 24 }, sm: { span: 6 } },
        wrapperCol: { xs: { span: 24 }, sm: { span: 18 } },
        model: this.buildEmptyModel(),
        files: [],
        association: {},
        secretLevels: ['一般', '内部', '秘密', '机密'],
        retentions: ['永久', '长期', '定期'],
        depts: ['发改（行政审批）', '规划', '财政', '住建', '其他'],
        statuses: ['未归档', '归档中', '审核中', '已归档']
      }
    },
    computed: {
      rules () {
        return {
          archiveName: [
            { required: true, message: '请输入档案名称', trigger: 'blur' },
            { max: 255, message: '档案名称不能超过 255 个字符', trigger: 'blur' }
          ],
          archiveNo: [
            { max: 64, message: '档案号不能超过 64 个字符', trigger: 'blur' }
          ],
          secretLevel: [{ required: true, message: '请选择密级', trigger: 'change' }],
          archiveType: [{ required: true, message: '请选择档案类型', trigger: 'change' }],
          status: [{ required: true, message: '请选择状态', trigger: 'change' }]
        }
      },
      /** 上传子目录：/archive/{yyyy}/{MM} */
      bizPath () {
        const now = new Date()
        const month = String(now.getMonth() + 1).padStart(2, '0')
        return `/archive/${now.getFullYear()}/${month}`
      }
    },
    methods: {
      buildEmptyModel () {
        const now = new Date()
        return {
          id: null,
          archiveNo: '',
          archiveName: '',
          archiveType: 'electronic',
          secretLevel: '一般',
          retention: undefined,
          archiveYear: now.getFullYear(),
          responsibleDept: undefined,
          responsibleUser: '',
          archiveDate: this.formatDate(now),
          status: '未归档',
          remark: ''
        }
      },
      // ------------------------------------------------------------------
      // 打开
      // ------------------------------------------------------------------
      /** 新增 */
      showAdd () {
        this.title = '新增档案'
        this.model = this.buildEmptyModel()
        this.model.responsibleUser = this.currentRealname()
        this.files = []
        this.association = {}
        this.visible = true
        this.resetForm()
      },
      /** 编辑 */
      showEdit (record) {
        if (!record || !record.id) {
          this.$message.warning('请选择要编辑的档案')
          return
        }
        this.title = '编辑档案'
        this.visible = true
        this.resetForm()
        this.loading = true
        queryArchiveById(record.id).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            this.visible = false
            return
          }
          const data = res.result || {}
          this.model = {
            id: data.id,
            archiveNo: data.archiveNo,
            archiveName: data.archiveName,
            archiveType: data.archiveType || 'electronic',
            secretLevel: data.secretLevel || '一般',
            retention: data.retention || undefined,
            archiveYear: data.archiveYear || new Date().getFullYear(),
            responsibleDept: data.responsibleDept || undefined,
            responsibleUser: data.responsibleUser || '',
            archiveDate: data.archiveDate || this.formatDate(new Date()),
            status: data.status || '未归档',
            remark: data.remark || ''
          }
          this.association = {
            landId: data.landId,
            crzdbh: data.crzdbh,
            facilityId: data.facilityId,
            ptxmmc: data.ptxmmc,
            dkmc: data.dkmc,
            xzqh: data.xzqh,
            ptsslb: data.ptsslb
          }
          this.files = (data.files || []).map(item => Object.assign({}, item))
        }).finally(() => {
          this.loading = false
        })
      },
      resetForm () {
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate()
          }
        })
      },
      currentRealname () {
        const info = this.$store.getters.userInfo || {}
        return info.realname || info.username || ''
      },
      // ------------------------------------------------------------------
      // 交互
      // ------------------------------------------------------------------
      handleAssociationChange (value) {
        this.association = value || {}
        // 归档年度跟随归档日期；行政区跟随项目
        if (this.model.archiveDate) {
          this.model.archiveYear = Number(String(this.model.archiveDate).substring(0, 4))
        }
      },
      handleGenerateNo () {
        this.noLoading = true
        const year = Number(String(this.model.archiveDate || '').substring(0, 4)) || new Date().getFullYear()
        generateArchiveNo(year).then(res => {
          if (res.success) {
            this.model.archiveNo = res.result
            this.$message.success(`已生成档案号 ${res.result}`)
          } else {
            this.$message.warning(res.message)
          }
        }).finally(() => {
          this.noLoading = false
        })
      },
      // ------------------------------------------------------------------
      // 提交
      // ------------------------------------------------------------------
      handleOk () {
        this.$refs.form.validate(valid => {
          if (!valid) {
            return
          }
          if (!this.association.facilityId) {
            this.$message.warning('请选择关联的配套项目')
            return
          }
          const fileError = this.$refs.fileTable ? this.$refs.fileTable.validate() : ''
          if (fileError) {
            this.$message.warning(fileError)
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
          dkmc: this.association.dkmc,
          xzqh: this.association.xzqh,
          ptsslb: this.association.ptsslb,
          archiveNo: this.model.archiveNo || null,
          files: this.files.map((item, index) => {
            return {
              id: item.id || null,
              categoryId: item.categoryId,
              categoryName: item.categoryName || null,
              fileName: item.fileName,
              fileTitle: item.fileTitle || item.fileName,
              fileExt: item.fileExt,
              fileSize: item.fileSize,
              fileMd5: item.fileMd5,
              storeType: item.storeType || 'local',
              storePath: item.storePath,
              status: item.status || '已归档',
              sortNo: item.sortNo || index + 1
            }
          })
        })

        this.confirmLoading = true
        const task = payload.id ? editArchive(payload) : addArchive(payload)
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
      handleCancel () {
        this.visible = false
      },
      // ------------------------------------------------------------------
      // 工具
      // ------------------------------------------------------------------
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
