<template>
  <a-modal
    :title="title"
    :width="1100"
    :visible="visible"
    :maskClosable="false"
    :destroyOnClose="true"
    :bodyStyle="{ maxHeight: '68vh', overflow: 'auto' }"
    @cancel="handleCancel">
    <!-- 三步向导的底部按钮自己控制（上一步 / 下一步 / 保存） -->
    <template slot="footer">
      <a-button @click="handleCancel">取消</a-button>
      <a-button v-if="step > 0" icon="left" @click="prevStep">上一步</a-button>
      <a-button v-if="step < 2" type="primary" @click="nextStep">
        下一步 <a-icon type="right" />
      </a-button>
      <a-button v-else type="primary" icon="save" :loading="confirmLoading" @click="handleSubmit">保存</a-button>
    </template>

    <a-steps :current="step" size="small" class="escalation-form__steps">
      <a-step title="项目基本信息" description="项目 / 关联地块（选填）" />
      <a-step title="论证信息与结果" description="论证事由 / 结果登记" />
      <a-step title="提级论证材料" description="类型必选，至少 1 个" />
    </a-steps>

    <a-spin :spinning="loading">
      <a-form-model ref="form" :model="model" :rules="rules" :label-col="labelCol" :wrapper-col="wrapperCol">
        <!-- ==================== 第一步 ==================== -->
        <div v-show="step === 0">
          <div class="form-section">
            <div class="form-section__title">
              <a-icon type="file-text" />
              <span>项目基本信息</span>
              <span class="form-section__hint">带 * 的为必填项</span>
            </div>

            <a-row :gutter="16">
              <a-col :xs="24" :md="12">
                <a-form-model-item label="项目编号" prop="projectNo">
                  <a-input-group compact>
                    <a-input
                      v-model="model.projectNo"
                      style="width: calc(100% - 92px)"
                      :maxLength="64"
                      placeholder="可手工改写，留空自动生成"
                      @blur="handleCheckNo" />
                    <a-button style="width: 92px" :loading="noLoading" @click="handleGenerateNo">自动生成</a-button>
                  </a-input-group>
                  <div class="form-item-tip">默认规则 TJ-{年份}-{4位流水}；允许手工改写，保存时做唯一性校验。</div>
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-model-item label="项目名称" prop="projectName">
                  <a-input v-model="model.projectName" :maxLength="255" placeholder="提级论证项目名称" />
                </a-form-model-item>
              </a-col>
            </a-row>

            <a-row :gutter="16">
              <a-col :xs="24" :md="12">
                <a-form-model-item label="申报单位" prop="declareDept">
                  <a-input v-model="model.declareDept" :maxLength="200" placeholder="如 滨海新区发展和改革局" />
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-model-item label="申报时间" prop="declareDate">
                  <a-date-picker
                    v-model="model.declareDate"
                    style="width: 100%"
                    valueFormat="YYYY-MM-DD"
                    :disabledDate="disabledFuture"
                    placeholder="选择申报日期" />
                </a-form-model-item>
              </a-col>
            </a-row>

            <a-row :gutter="16">
              <a-col :xs="24" :md="12">
                <a-form-model-item label="项目类型" prop="projectType">
                  <j-dict-select-tag
                    v-model="model.projectType"
                    :dictCode="DICT.projectType"
                    placeholder="请选择项目类型" />
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-model-item label="项目规模" prop="projectScale">
                  <a-input v-model="model.projectScale" :maxLength="100" placeholder="如 总建筑面积 12.6 万㎡" />
                </a-form-model-item>
              </a-col>
            </a-row>

            <a-row :gutter="16">
              <a-col :xs="24" :md="12">
                <a-form-model-item label="总投资（亿元）" prop="totalInvestment">
                  <a-input-number
                    v-model="model.totalInvestment"
                    :min="0"
                    :precision="4"
                    style="width: 100%"
                    placeholder="如 12.8" />
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-model-item label="建设地点" prop="buildLocation">
                  <a-input v-model="model.buildLocation" :maxLength="255" placeholder="如 高新区创新大道 88 号" />
                </a-form-model-item>
              </a-col>
            </a-row>

            <a-form-model-item label="项目概述" prop="projectSummary">
              <a-textarea v-model="model.projectSummary" :rows="3" :maxLength="2000" placeholder="项目建设内容概述，不超过 2000 字" />
            </a-form-model-item>
          </div>

          <!-- ★ 第 9–16 项：独立分组，不与「项目基本信息」混排（设计文档 5.4） -->
          <div class="form-section form-section--optional">
            <div class="form-section__title">
              <a-icon type="environment" />
              <span>关联地块信息（选填）</span>
              <span class="form-section__hint">论证对象是「项目」，地块信息只是可选的上下文，均可留空</span>
            </div>

            <a-row :gutter="16">
              <a-col :xs="24" :md="8">
                <a-form-model-item label="行政区划" prop="xzqh">
                  <a-select v-model="model.xzqh" allowClear placeholder="16 区" style="width: 100%">
                    <a-select-option v-for="item in xzqhOptions" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </a-select-option>
                  </a-select>
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="8">
                <a-form-model-item label="功能区" prop="gnq">
                  <a-select v-model="model.gnq" allowClear placeholder="功能区" style="width: 100%">
                    <a-select-option v-for="item in gnqOptions" :key="item" :value="item">{{ item }}</a-select-option>
                  </a-select>
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="8">
                <a-form-model-item label="是否土地整理项目" prop="tdzlProject">
                  <j-dict-select-tag v-model="model.tdzlProject" :dictCode="DICT.yn" type="radio" />
                </a-form-model-item>
              </a-col>
            </a-row>

            <a-row :gutter="16">
              <a-col :xs="24" :md="8">
                <a-form-model-item label="地块名称" prop="dkmc">
                  <a-input v-model="model.dkmc" :maxLength="200" placeholder="选填" />
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="8">
                <a-form-model-item label="地块面积（㎡）" prop="dkArea">
                  <a-input-number v-model="model.dkArea" :min="0" :precision="2" style="width: 100%" placeholder="选填" />
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="8">
                <a-form-model-item label="规划用地性质" prop="ghydxz">
                  <a-input v-model="model.ghydxz" :maxLength="200" placeholder="选填" />
                </a-form-model-item>
              </a-col>
            </a-row>

            <a-row :gutter="16">
              <a-col :xs="24" :md="12">
                <a-form-model-item label="关联出让宗地编号" prop="crzdbh">
                  <a-select
                    :value="landId"
                    show-search
                    allowClear
                    placeholder="按宗地编号 / 地块名称搜索（选中自动带出地块信息）"
                    :filterOption="false"
                    :notFoundContent="landLoading ? '加载中…' : '无匹配宗地'"
                    @search="loadLandOptions"
                    @focus="handleLandFocus"
                    @change="handleLandChange">
                    <a-select-option v-for="item in landOptions" :key="item.id" :value="item.id">
                      {{ item.crzdbh }}<span class="form-item-muted"> · {{ item.dkmc || '—' }}</span>
                    </a-select-option>
                  </a-select>
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-model-item label="关联配套项目" prop="facilityId">
                  <a-select
                    :value="model.facilityId"
                    show-search
                    allowClear
                    :disabled="!model.crzdbh"
                    :placeholder="model.crzdbh ? '选择该宗地下的配套项目' : '请先选择关联出让宗地'"
                    :filterOption="false"
                    :notFoundContent="facilityLoading ? '加载中…' : '该宗地下没有配套项目'"
                    @search="loadFacilityOptions"
                    @change="handleFacilityChange">
                    <a-select-option v-for="item in facilityOptions" :key="item.id" :value="item.id">
                      {{ item.ptxmmc }}<span class="form-item-muted"> · {{ item.ptsslb || '—' }}</span>
                    </a-select-option>
                  </a-select>
                  <div class="form-item-tip">关联后会回写配套表的「是否做提级论证」标记（后端开关控制）。</div>
                </a-form-model-item>
              </a-col>
            </a-row>
          </div>
        </div>

        <!-- ==================== 第二步 ==================== -->
        <div v-show="step === 1">
          <div class="form-section">
            <div class="form-section__title">
              <a-icon type="solution" />
              <span>提级论证信息</span>
            </div>

            <a-form-model-item label="提级论证事由" prop="argReason">
              <a-textarea v-model="model.argReason" :rows="2" :maxLength="2000" placeholder="不超过 2000 字" />
            </a-form-model-item>
            <a-form-model-item label="提级论证依据" prop="argBasis">
              <a-textarea v-model="model.argBasis" :rows="2" :maxLength="2000" placeholder="政策 / 规划依据，不超过 2000 字" />
            </a-form-model-item>
            <a-form-model-item label="必要性说明" prop="argNecessity">
              <a-textarea v-model="model.argNecessity" :rows="2" :maxLength="2000" placeholder="不超过 2000 字" />
            </a-form-model-item>
            <a-form-model-item label="可行性说明" prop="argFeasibility">
              <a-textarea v-model="model.argFeasibility" :rows="2" :maxLength="2000" placeholder="不超过 2000 字" />
            </a-form-model-item>
            <a-form-model-item label="论证事项内容" prop="argContent">
              <a-textarea v-model="model.argContent" :rows="2" :maxLength="2000" placeholder="不超过 2000 字" />
            </a-form-model-item>
          </div>

          <div class="form-section">
            <div class="form-section__title">
              <a-icon type="audit" />
              <span>论证结果（人工登记）</span>
              <span class="form-section__hint">流程在线下办理，这里只登记结论，系统不驱动流转</span>
            </div>

            <a-row :gutter="16">
              <a-col :xs="24" :md="12">
                <a-form-model-item label="论证结果" prop="argResult">
                  <j-dict-select-tag
                    v-model="model.argResult"
                    :dictCode="DICT.argResult"
                    placeholder="请选择论证结果" />
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-model-item label="论证组织单位" prop="argOrg">
                  <a-input v-model="model.argOrg" :maxLength="200" placeholder="选填" />
                </a-form-model-item>
              </a-col>
            </a-row>

            <a-row :gutter="16">
              <a-col :xs="24" :md="8">
                <a-form-model-item label="论证会日期" prop="argMeetingDate">
                  <a-date-picker v-model="model.argMeetingDate" style="width: 100%" valueFormat="YYYY-MM-DD" placeholder="选填" />
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="8">
                <a-form-model-item label="论证完成日期" prop="argDate">
                  <a-date-picker v-model="model.argDate" style="width: 100%" valueFormat="YYYY-MM-DD" placeholder="选填" />
                </a-form-model-item>
              </a-col>
              <a-col :xs="24" :md="8">
                <a-form-model-item label="办理状态" prop="status">
                  <a-select v-model="model.status" style="width: 100%">
                    <a-select-option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</a-select-option>
                  </a-select>
                </a-form-model-item>
              </a-col>
            </a-row>

            <a-form-model-item label="论证专家名单" prop="argExpertList">
              <a-textarea v-model="model.argExpertList" :rows="2" :maxLength="1000" placeholder="姓名 / 单位，不超过 1000 字" />
            </a-form-model-item>
            <a-form-model-item label="论证结论" prop="argConclusion">
              <a-textarea v-model="model.argConclusion" :rows="2" :maxLength="2000" placeholder="不超过 2000 字" />
            </a-form-model-item>
            <a-form-model-item label="备注" prop="remark">
              <a-textarea v-model="model.remark" :rows="2" :maxLength="1000" placeholder="补充说明，可留空" />
            </a-form-model-item>
          </div>
        </div>

        <!-- ==================== 第三步 ==================== -->
        <div v-show="step === 2">
          <div class="form-section">
            <div class="form-section__title">
              <a-icon type="paper-clip" />
              <span>提级论证材料</span>
              <span class="form-section__hint">材料类型必选，至少上传 1 个文件</span>
            </div>
            <escalation-material-table ref="materialTable" v-model="materials" @change="handleMaterialChange" />
          </div>
        </div>
      </a-form-model>
    </a-spin>
  </a-modal>
</template>

<script>
  import JDictSelectTag from '@/components/dict/JDictSelectTag'
  import EscalationMaterialTable from './EscalationMaterialTable'
  import { queryFacilityOptions, queryLandById, queryLandOptions, queryXzqhOptions } from '@/api/land/landData'
  import {
    DICT,
    STATUS_OPTIONS,
    addProject,
    checkProjectNo,
    editProject,
    genProjectNo,
    queryMaterialList,
    queryProjectById
  } from '@/api/land/escalation'

  /**
   * 提级论证项目 新增 / 编辑（三步向导，设计文档 5.4 的 28 个字段）
   *
   * 第一步 项目基本信息：第 1–8 项（第 9–16 项是**独立的「关联地块信息（选填）」分组**，
   *       不与基本信息混排，依据设计文档 1.3 的考证：论证对象是项目，地块只是可选上下文）；
   * 第二步 提级论证信息与结果：第 17–28 项（含 status / arg_result 两个下拉）；
   * 第三步 提级论证材料：材料类型必选 + 多文件。
   *
   * 编号：新增时调 genProjectNo 预生成 TJ-{yyyy}-{4位}，允许手工改写，保存前做唯一性校验。
   */
  export default {
    name: 'EscalationFormModal',
    components: { JDictSelectTag, EscalationMaterialTable },
    data () {
      return {
        DICT,
        title: '新增提级论证项目',
        visible: false,
        step: 0,
        loading: false,
        confirmLoading: false,
        noLoading: false,
        labelCol: { xs: { span: 24 }, sm: { span: 7 } },
        wrapperCol: { xs: { span: 24 }, sm: { span: 17 } },
        statusOptions: STATUS_OPTIONS,
        gnqOptions: ['生态城', '经开区', '高新区', '保税区', '其他'],
        xzqhOptions: [],
        landOptions: [],
        /** 出让宗地下拉的选中值（宗地表主键；主表里存的是 crzdbh 文本） */
        landIdValue: undefined,
        facilityOptions: [],
        landLoading: false,
        facilityLoading: false,
        landLoadedOnce: false,
        materials: [],
        model: this.buildEmptyModel()
      }
    },
    computed: {
      /** 出让宗地下拉的选中值（宗地表主键；表单里存的是 crzdbh 文本） */
      landId () {
        return this.landIdValue
      },
      rules () {
        return {
          projectName: [
            { required: true, message: '请输入项目名称', trigger: 'blur' },
            { max: 255, message: '项目名称不能超过 255 个字符', trigger: 'blur' }
          ],
          declareDept: [
            { required: true, message: '请输入申报单位', trigger: 'blur' },
            { max: 200, message: '申报单位不能超过 200 个字符', trigger: 'blur' }
          ],
          declareDate: [{ required: true, message: '请选择申报时间', trigger: 'change' }],
          projectNo: [{ max: 64, message: '项目编号不能超过 64 个字符', trigger: 'blur' }],
          status: [{ required: true, message: '请选择办理状态', trigger: 'change' }],
          projectSummary: [{ max: 2000, message: '项目概述不能超过 2000 个字符', trigger: 'blur' }],
          argReason: [{ max: 2000, message: '提级论证事由不能超过 2000 个字符', trigger: 'blur' }],
          argBasis: [{ max: 2000, message: '提级论证依据不能超过 2000 个字符', trigger: 'blur' }],
          argNecessity: [{ max: 2000, message: '必要性说明不能超过 2000 个字符', trigger: 'blur' }],
          argFeasibility: [{ max: 2000, message: '可行性说明不能超过 2000 个字符', trigger: 'blur' }],
          argContent: [{ max: 2000, message: '论证事项内容不能超过 2000 个字符', trigger: 'blur' }],
          argConclusion: [{ max: 2000, message: '论证结论不能超过 2000 个字符', trigger: 'blur' }],
          argExpertList: [{ max: 1000, message: '论证专家名单不能超过 1000 个字符', trigger: 'blur' }]
        }
      }
    },
    created () {
      this.loadXzqh()
    },
    methods: {
      buildEmptyModel () {
        return {
          id: null,
          projectNo: '',
          projectName: '',
          declareDept: '',
          declareDate: this.formatDate(new Date()),
          projectType: undefined,
          projectScale: '',
          totalInvestment: undefined,
          buildLocation: '',
          projectSummary: '',
          xzqh: undefined,
          gnq: undefined,
          dkmc: '',
          dkArea: undefined,
          ghydxz: '',
          tdzlProject: undefined,
          crzdbh: undefined,
          facilityId: undefined,
          ptxmmc: '',
          argReason: '',
          argBasis: '',
          argNecessity: '',
          argFeasibility: '',
          argContent: '',
          argResult: undefined,
          argConclusion: '',
          argOrg: '',
          argMeetingDate: undefined,
          argExpertList: '',
          argDate: undefined,
          status: '未办理',
          remark: ''
        }
      },
      loadXzqh () {
        queryXzqhOptions().then(res => {
          if (res.success) {
            this.xzqhOptions = res.result || []
          }
        })
      },
      // ------------------------------------------------------------------
      // 打开
      // ------------------------------------------------------------------
      showAdd () {
        this.title = '新增提级论证项目'
        this.step = 0
        this.model = this.buildEmptyModel()
        this.materials = []
        this.landIdValue = undefined
        this.facilityOptions = []
        this.visible = true
        this.resetForm()
        // 预生成项目编号（允许改写）
        this.handleGenerateNo()
      },
      showEdit (record) {
        if (!record || !record.id) {
          this.$message.warning('请选择要编辑的项目')
          return
        }
        this.title = '编辑提级论证项目'
        this.step = 0
        this.visible = true
        this.resetForm()
        this.loading = true
        queryProjectById(record.id).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            this.visible = false
            return
          }
          const data = res.result || {}
          this.model = Object.assign(this.buildEmptyModel(), {
            id: data.id,
            projectNo: data.projectNo || '',
            projectName: data.projectName || '',
            declareDept: data.declareDept || '',
            declareDate: data.declareDate || this.formatDate(new Date()),
            projectType: data.projectType || undefined,
            projectScale: data.projectScale || '',
            totalInvestment: data.totalInvestment === null || data.totalInvestment === undefined ? undefined : Number(data.totalInvestment),
            buildLocation: data.buildLocation || '',
            projectSummary: data.projectSummary || '',
            xzqh: data.xzqh || undefined,
            gnq: data.gnq || undefined,
            dkmc: data.dkmc || '',
            dkArea: data.dkArea === null || data.dkArea === undefined ? undefined : Number(data.dkArea),
            ghydxz: data.ghydxz || '',
            tdzlProject: data.tdzlProject === null || data.tdzlProject === undefined ? undefined : String(data.tdzlProject),
            crzdbh: data.crzdbh || undefined,
            facilityId: data.facilityId || undefined,
            ptxmmc: data.ptxmmc || '',
            argReason: data.argReason || '',
            argBasis: data.argBasis || '',
            argNecessity: data.argNecessity || '',
            argFeasibility: data.argFeasibility || '',
            argContent: data.argContent || '',
            argResult: data.argResult || undefined,
            argConclusion: data.argConclusion || '',
            argOrg: data.argOrg || '',
            argMeetingDate: data.argMeetingDate || undefined,
            argExpertList: data.argExpertList || '',
            argDate: data.argDate || undefined,
            status: data.status || '未办理',
            remark: data.remark || ''
          })
          // 编辑回显：宗地下拉要能显示当前值对应的名称
          this.landIdValue = data.landId || undefined
          if (data.crzdbh) {
            this.loadFacilityOptions('')
          }
          if (data.landId) {
            this.loadLandOptions('')
          }
          const inline = data.materials
          if (inline) {
            this.materials = inline
          } else {
            queryMaterialList(data.id).then(materialRes => {
              if (materialRes.success) {
                this.materials = materialRes.result || []
              }
            })
          }
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
      // ------------------------------------------------------------------
      // 步骤控制
      // ------------------------------------------------------------------
      /** 每一步只校验本步的字段，避免「后面的必填项」在第 1 步就报红 */
      stepFields () {
        if (this.step === 0) {
          return ['projectNo', 'projectName', 'declareDept', 'declareDate']
        }
        if (this.step === 1) {
          return ['status', 'projectSummary', 'argReason', 'argBasis', 'argNecessity', 'argFeasibility', 'argContent', 'argConclusion', 'argExpertList']
        }
        return []
      },
      nextStep () {
        this.validateStep(() => {
          if (this.step === 0) {
            this.step = 1
            return
          }
          if (this.step === 1) {
            this.step = 2
          }
        })
      },
      prevStep () {
        if (this.step > 0) {
          this.step -= 1
        }
      },
      validateStep (done) {
        const fields = this.stepFields()
        if (!fields.length || !this.$refs.form) {
          done()
          return
        }
        this.$refs.form.validateField(fields, error => {
          if (!error) {
            done()
          }
        })
      },
      // ------------------------------------------------------------------
      // 显示工具
      // ------------------------------------------------------------------
      formatDate (date) {
        const d = date || new Date()
        const month = String(d.getMonth() + 1).padStart(2, '0')
        const day = String(d.getDate()).padStart(2, '0')
        return `${d.getFullYear()}-${month}-${day}`
      },
      disabledFuture (current) {
        return current && current.valueOf() > Date.now()
      },
      // ------------------------------------------------------------------
      // 项目编号
      // ------------------------------------------------------------------
      handleGenerateNo () {
        this.noLoading = true
        const year = Number(String(this.model.declareDate || '').substring(0, 4)) || new Date().getFullYear()
        genProjectNo(year).then(res => {
          if (res.success) {
            this.model.projectNo = res.result
          } else {
            this.$message.warning(res.message)
          }
        }).finally(() => {
          this.noLoading = false
        })
      },
      handleCheckNo () {
        const projectNo = (this.model.projectNo || '').trim()
        if (!projectNo) {
          return
        }
        checkProjectNo({ projectNo: projectNo, id: this.model.id || undefined }).then(res => {
          if (res.success && res.result === false) {
            this.$message.warning(`项目编号「${projectNo}」已存在，请改写`)
          }
        })
      },
      // ------------------------------------------------------------------
      // 关联地块（第 9–16 项）
      // ------------------------------------------------------------------
      handleLandFocus () {
        if (!this.landLoadedOnce) {
          this.loadLandOptions('')
        }
      },
      loadLandOptions (keyword) {
        this.landLoading = true
        return queryLandOptions({ keyword: keyword || undefined, limit: 50 }).then(res => {
          if (res.success) {
            this.landOptions = res.result || []
            this.landLoadedOnce = true
          }
        }).finally(() => {
          this.landLoading = false
        })
      },
      /** 选中出让宗地 → 自动带出 第 11/9/13 项（地块名称 / 行政区划 / 规划用地性质） */
      handleLandChange (landId) {
        if (!landId) {
          this.landIdValue = undefined
          this.model.crzdbh = undefined
          this.model.facilityId = undefined
          this.model.ptxmmc = ''
          return
        }
        const land = this.landOptions.filter(item => item.id === landId)[0] || {}
        this.landIdValue = landId
        this.model.crzdbh = land.crzdbh
        this.model.dkmc = land.dkmc || this.model.dkmc
        this.model.xzqh = land.xzqh || this.model.xzqh
        this.facilityOptions = []
        this.loadFacilityOptions('')
        // landOptions 可能不含规划用地性质，按 id 再查一次详情补齐（查不到仅忽略，不阻断）
        queryLandById({ id: landId }).then(res => {
          if (res.success && res.result) {
            const detail = res.result
            this.model.dkmc = detail.dkmc || this.model.dkmc
            this.model.xzqh = detail.xzqh || this.model.xzqh
            this.model.ghydxz = detail.ghydxz || this.model.ghydxz
            this.model.dkArea = detail.dkArea !== null && detail.dkArea !== undefined ? Number(detail.dkArea) : this.model.dkArea
            if (detail.crzdbh) {
              this.model.crzdbh = detail.crzdbh
            }
          }
        })
      },
      loadFacilityOptions (keyword) {
        if (!this.model.crzdbh) {
          this.facilityOptions = []
          return Promise.resolve()
        }
        this.facilityLoading = true
        return queryFacilityOptions({ crzdbh: this.model.crzdbh, keyword: keyword || undefined, limit: 50 })
          .then(res => {
            if (res.success) {
              this.facilityOptions = res.result || []
            }
          }).finally(() => {
            this.facilityLoading = false
          })
      },
      handleFacilityChange (facilityId) {
        if (!facilityId) {
          this.model.facilityId = undefined
          this.model.ptxmmc = ''
          return
        }
        const facility = this.facilityOptions.filter(item => item.id === facilityId)[0] || {}
        this.model.facilityId = facilityId
        this.model.ptxmmc = facility.ptxmmc || ''
      },
      // ------------------------------------------------------------------
      // 材料
      // ------------------------------------------------------------------
      handleMaterialChange () {
        // 材料增删后无需额外处理，保存时统一 collect
      },
      // ------------------------------------------------------------------
      // 提交
      // ------------------------------------------------------------------
      handleSubmit () {
        this.validateStep(() => {
          const materialError = this.$refs.materialTable ? this.$refs.materialTable.validate() : ''
          if (materialError) {
            this.step = 2
            this.$message.warning(materialError)
            return
          }
          this.submit()
        })
      },
      submit () {
        const payload = Object.assign({}, this.model, {
          projectNo: (this.model.projectNo || '').trim() || null,
          totalInvestment: this.model.totalInvestment === undefined ? null : this.model.totalInvestment,
          dkArea: this.model.dkArea === undefined ? null : this.model.dkArea,
          tdzlProject: this.model.tdzlProject === undefined || this.model.tdzlProject === '' ? 0 : Number(this.model.tdzlProject),
          materials: this.$refs.materialTable ? this.$refs.materialTable.collect() : []
        })
        this.confirmLoading = true
        const task = payload.id ? editProject(payload) : addProject(payload)
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
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-weak: #94a3b8;

  .escalation-form__steps {
    margin-bottom: 18px;
  }

  .form-section {
    padding: 16px 16px 4px;
    margin-bottom: 16px;
    border: 1px solid @border-color;
    border-radius: 8px;

    &--optional {
      background: #f8fafc;
    }

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

  .form-item-muted {
    color: @text-weak;
  }
</style>
