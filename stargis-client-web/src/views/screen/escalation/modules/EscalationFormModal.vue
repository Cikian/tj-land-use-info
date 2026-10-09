<template>
  <!--
    EscalationFormModal 提级论证项目 新增 / 编辑（三步向导）
    --------------------------------
    对应设计文档 5.4 的 28 个字段，刻意分成三步 + 一个独立分组：

      第一步 项目基本信息（第 1–8 项）
              + **独立的「关联地块信息（选填）」分组**（第 9–16 项）
      第二步 提级论证信息与结果（第 17–28 项）
      第三步 提级论证材料（材料类型必选 + 至少 1 个文件）

    ★ 为什么第 9–16 项单独成组、不与基本信息混排：
      设计文档 1.3 考证过——论证对象是「项目」，地块信息只是可选的上下文，
      全部可以留空。混排会让用户以为地块信息也必须填。

    ★ 项目编号：新增时调 genProjectNo 预生成 TJ-{yyyy}-{4位}，**允许手工改写**，
      失焦与保存前都做一次 checkNo 唯一性校验（后端在 add/edit 里也会再校验一次）。

    ★ 不用 antd 的 a-steps / a-form-model：
      步骤条自绘（数字圆点 + 标题 + 说明），校验用本组件的 errors 对象集中表达，
      比把规则散落到 28 个控件上更好维护，也符合「大屏只用 Screen* 组件」的硬性规则。

    事件：
      ok  保存成功后抛出（父组件据此刷新列表）
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="1180"
    :confirm-loading="saving || checkingNo"
    ok-text="保存"
    cancel-text="取消"
    :body-max-height="'calc(100vh - 200px)'"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <!-- 编辑回显时拉详情：内联加载态（overlay=false，不遮挡已渲染的框架） -->
    <screen-loading :loading="loading" :overlay="false" text="项目详情加载中…" />

    <!-- 步骤条 -->
    <ol class="escalation-form__steps">
      <li
        v-for="(item, index) in steps"
        :key="item.title"
        class="escalation-form__step"
        :class="{ 'is-active': index === step, 'is-done': index < step }"
      >
        <span class="escalation-form__step-no" aria-hidden="true">{{ index + 1 }}</span>
        <span class="escalation-form__step-text">
          <b>{{ item.title }}</b>
          <em>{{ item.desc }}</em>
        </span>
      </li>
    </ol>

    <div class="escalation-form">
      <!-- ==================== 第一步 ==================== -->
      <template v-if="step === 0">
        <section class="escalation-form__section">
          <h4 class="escalation-form__section-title">
            <screen-icon name="file-text" :size="14" />
            项目基本信息
            <span class="escalation-form__section-hint">带 * 的为必填项</span>
          </h4>

          <div class="escalation-form__grid">
            <screen-field
              class="escalation-form__field is-full"
              label="项目编号"
              required
              label-width="104px"
              html-for="esc-projectNo"
              :error="errors.projectNo"
              tip="默认规则 TJ-{年份}-{4位流水}；允许手工改写，保存时做唯一性校验"
            >
              <div class="escalation-form__with-action">
                <screen-input
                  id="esc-projectNo"
                  v-model="model.projectNo"
                  clearable
                  :maxlength="64"
                  :invalid="!!errors.projectNo"
                  placeholder="可手工改写，留空由后端自动生成"
                  @blur="handleCheckNo"
                />
                <screen-button :loading="noLoading" @click="handleGenerateNo">自动生成</screen-button>
              </div>
            </screen-field>

            <screen-field
              class="escalation-form__field"
              label="项目名称"
              required
              label-width="104px"
              html-for="esc-projectName"
              :error="errors.projectName"
            >
              <screen-input
                id="esc-projectName"
                v-model="model.projectName"
                clearable
                :maxlength="255"
                :invalid="!!errors.projectName"
                placeholder="提级论证项目名称"
              />
            </screen-field>

            <screen-field
              class="escalation-form__field"
              label="申报单位"
              required
              label-width="104px"
              html-for="esc-declareDept"
              :error="errors.declareDept"
            >
              <screen-input
                id="esc-declareDept"
                v-model="model.declareDept"
                clearable
                :maxlength="200"
                :invalid="!!errors.declareDept"
                placeholder="如 滨海新区发展和改革局"
              />
            </screen-field>

            <screen-field
              class="escalation-form__field"
              label="申报时间"
              required
              label-width="104px"
              :error="errors.declareDate"
            >
              <screen-date-input
                v-model="model.declareDate"
                placeholder="选择申报日期"
                :invalid="!!errors.declareDate"
              />
            </screen-field>

            <screen-field class="escalation-form__field" label="项目类型" label-width="104px">
              <screen-select
                v-model="model.projectType"
                :options="projectTypeOptions"
                placeholder="请选择项目类型"
                aria-label="项目类型"
              />
            </screen-field>

            <screen-field class="escalation-form__field" label="项目规模" label-width="104px" html-for="esc-projectScale">
              <screen-input
                id="esc-projectScale"
                v-model="model.projectScale"
                clearable
                :maxlength="100"
                placeholder="如 总建筑面积 12.6 万㎡"
              />
            </screen-field>

            <screen-field
              class="escalation-form__field"
              label="总投资（亿元）"
              required
              label-width="104px"
              html-for="esc-totalInvestment"
              :error="errors.totalInvestment"
            >
              <screen-input
                id="esc-totalInvestment"
                v-model="model.totalInvestment"
                type="number"
                :min="0"
                step="0.0001"
                :invalid="!!errors.totalInvestment"
                placeholder="如 12.8"
              />
            </screen-field>

            <screen-field
              class="escalation-form__field"
              label="建设地点"
              required
              label-width="104px"
              html-for="esc-buildLocation"
              :error="errors.buildLocation"
            >
              <screen-input
                id="esc-buildLocation"
                v-model="model.buildLocation"
                clearable
                :maxlength="255"
                :invalid="!!errors.buildLocation"
                placeholder="如 高新区创新大道 88 号"
              />
            </screen-field>

            <screen-field
              class="escalation-form__field is-full"
              label="项目概述"
              required
              label-width="104px"
              html-for="esc-projectSummary"
              :error="errors.projectSummary"
              tip="项目建设内容概述，不超过 2000 字"
            >
              <screen-input
                id="esc-projectSummary"
                v-model="model.projectSummary"
                type="textarea"
                :rows="3"
                :maxlength="2000"
                :invalid="!!errors.projectSummary"
                placeholder="项目建设内容概述"
              />
            </screen-field>
          </div>
        </section>

        <!-- ★ 第 9–16 项：独立分组，不与「项目基本信息」混排 -->
        <section class="escalation-form__section is-optional">
          <h4 class="escalation-form__section-title">
            <screen-icon name="layers" :size="14" />
            关联地块信息（选填）
            <span class="escalation-form__section-hint">
              论证对象是「项目」，地块信息只是可选的上下文，均可留空
            </span>
          </h4>

          <div class="escalation-form__grid">
            <screen-field class="escalation-form__field" label="行政区划" label-width="104px">
              <screen-select
                v-model="model.xzqh"
                :options="xzqhOptions"
                placeholder="全部区划"
                aria-label="行政区划"
              />
            </screen-field>

            <screen-field class="escalation-form__field" label="功能区" label-width="104px">
              <screen-select
                v-model="model.gnq"
                :options="gnqOptions"
                placeholder="请选择功能区"
                aria-label="功能区"
              />
            </screen-field>

            <screen-field class="escalation-form__field" label="土地整理" label-width="104px">
              <screen-radio-group
                v-model="model.tdzlProject"
                :options="ynOptions"
                size="sm"
                aria-label="是否土地整理项目"
              />
            </screen-field>

            <screen-field class="escalation-form__field" label="地块名称" label-width="104px" html-for="esc-dkmc">
              <screen-input id="esc-dkmc" v-model="model.dkmc" clearable :maxlength="200" placeholder="选填" />
            </screen-field>

            <screen-field class="escalation-form__field" label="地块面积（㎡）" label-width="104px" html-for="esc-dkArea">
              <screen-input
                id="esc-dkArea"
                v-model="model.dkArea"
                type="number"
                :min="0"
                step="0.01"
                placeholder="选填"
              />
            </screen-field>

            <screen-field class="escalation-form__field" label="规划用地性质" label-width="104px" html-for="esc-ghydxz">
              <screen-input id="esc-ghydxz" v-model="model.ghydxz" clearable :maxlength="200" placeholder="选填" />
            </screen-field>

            <screen-field
              class="escalation-form__field"
              label="关联出让宗地"
              label-width="104px"
              tip="按宗地编号 / 地块名称搜索，选中后自动带出地块信息"
            >
              <screen-select
                v-model="landId"
                :options="landOptions"
                searchable
                :filter-local="false"
                :empty-text="landLoading ? '加载中…' : '无匹配宗地'"
                placeholder="搜索宗地编号或地块名称"
                aria-label="关联出让宗地"
                @search="loadLandOptions"
                @change="handleLandChange"
              />
            </screen-field>

            <screen-field
              class="escalation-form__field"
              label="关联配套项目"
              label-width="104px"
              tip="关联后由后端回写配套表的「是否做提级论证」标记"
            >
              <screen-select
                v-model="model.facilityId"
                :options="facilityOptions"
                :disabled="!model.crzdbh"
                searchable
                :filter-local="false"
                :empty-text="facilityLoading ? '加载中…' : '该宗地下没有配套项目'"
                :placeholder="model.crzdbh ? '选择该宗地下的配套项目' : '请先选择关联出让宗地'"
                aria-label="关联配套项目"
                @search="loadFacilityOptions"
                @change="handleFacilityChange"
              />
            </screen-field>
          </div>
        </section>
      </template>

      <!-- ==================== 第二步 ==================== -->
      <template v-else-if="step === 1">
        <section class="escalation-form__section">
          <h4 class="escalation-form__section-title">
            <screen-icon name="folder-open" :size="14" />
            提级论证信息
          </h4>

          <div class="escalation-form__grid">
            <screen-field
              v-for="field in argTextFields"
              :key="field.key"
              class="escalation-form__field is-full"
              :label="field.label"
              label-width="104px"
              :html-for="`esc-${field.key}`"
            >
              <screen-input
                :id="`esc-${field.key}`"
                v-model="model[field.key]"
                type="textarea"
                :rows="2"
                :maxlength="field.maxlength"
                :placeholder="field.placeholder"
              />
            </screen-field>
          </div>
        </section>

        <section class="escalation-form__section">
          <h4 class="escalation-form__section-title">
            <screen-icon name="check-circle" :size="14" />
            论证结果（人工登记）
            <span class="escalation-form__section-hint">
              流程在线下办理，这里只登记结论，系统不驱动流转
            </span>
          </h4>

          <div class="escalation-form__grid">
            <screen-field class="escalation-form__field" label="论证结果" label-width="104px" tip="选填，可在审批页登记意见时再补">
              <screen-select
                v-model="model.argResult"
                :options="argResultOptions"
                placeholder="请选择论证结果"
                aria-label="论证结果"
              />
            </screen-field>

            <screen-field class="escalation-form__field" label="论证组织单位" label-width="104px" html-for="esc-argOrg">
              <screen-input id="esc-argOrg" v-model="model.argOrg" clearable :maxlength="200" placeholder="选填" />
            </screen-field>

            <screen-field class="escalation-form__field" label="论证会日期" label-width="104px">
              <screen-date-input v-model="model.argMeetingDate" placeholder="选填" />
            </screen-field>

            <screen-field class="escalation-form__field" label="论证完成日期" label-width="104px">
              <screen-date-input v-model="model.argDate" placeholder="选填" />
            </screen-field>

            <screen-field
              class="escalation-form__field"
              label="办理状态"
              required
              label-width="104px"
              :error="errors.status"
            >
              <screen-select
                v-model="model.status"
                :options="statusOptions"
                :invalid="!!errors.status"
                placeholder="请选择办理状态"
                aria-label="办理状态"
              />
            </screen-field>

            <screen-field class="escalation-form__field is-full" label="论证专家名单" label-width="104px" html-for="esc-argExpertList">
              <screen-input
                id="esc-argExpertList"
                v-model="model.argExpertList"
                type="textarea"
                :rows="2"
                :maxlength="1000"
                placeholder="姓名 / 单位，不超过 1000 字"
              />
            </screen-field>

            <screen-field class="escalation-form__field is-full" label="论证结论" label-width="104px" html-for="esc-argConclusion">
              <screen-input
                id="esc-argConclusion"
                v-model="model.argConclusion"
                type="textarea"
                :rows="2"
                :maxlength="2000"
                placeholder="不超过 2000 字"
              />
            </screen-field>

            <screen-field class="escalation-form__field is-full" label="备注" label-width="104px" html-for="esc-remark">
              <screen-input
                id="esc-remark"
                v-model="model.remark"
                type="textarea"
                :rows="2"
                :maxlength="1000"
                placeholder="补充说明，可留空"
              />
            </screen-field>
          </div>
        </section>
      </template>

      <!-- ==================== 第三步 ==================== -->
      <template v-else>
        <section class="escalation-form__section">
          <h4 class="escalation-form__section-title">
            <screen-icon name="paperclip" :size="14" />
            提级论证材料
            <span class="escalation-form__section-hint">
              材料类型必选，至少上传 1 个文件（{{ MATERIAL_ALLOWED_EXT_TEXT }}，单个不超过 {{ MATERIAL_MAX_SIZE_MB }}MB）
            </span>
          </h4>

          <escalation-material-table ref="materialTable" v-model="materials" />
          <p v-if="errors.materials" class="escalation-form__error" role="alert">{{ errors.materials }}</p>
        </section>
      </template>
    </div>

    <!-- 底部按钮自己控制（上一步 / 下一步 / 保存），所以不用弹窗默认底栏 -->
    <template #footer>
      <screen-button @click="handleCancel">取消</screen-button>
      <screen-button v-if="step > 0" icon="arrow-left" @click="prevStep">上一步</screen-button>
      <screen-button v-if="step < 2" type="primary" @click="nextStep">下一步</screen-button>
      <screen-button
        v-else
        type="primary"
        icon="save"
        :loading="saving || checkingNo"
        :disabled="loading"
        @click="handleSubmit"
      >
        保存
      </screen-button>
    </template>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenDateInput,
  ScreenRadioGroup,
  ScreenButton,
  ScreenIcon,
  ScreenLoading,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { queryLandOptions, queryLandById, queryXzqhOptions, queryFacilityOptions } from '@/api/land/landData'
import { genProjectNo, checkProjectNo, queryProjectById, queryMaterialList, addProject, editProject } from '@/api/land/escalation'
import EscalationMaterialTable from './EscalationMaterialTable.vue'
import {
  STATUS_OPTIONS,
  ARG_RESULT_OPTIONS,
  PROJECT_TYPE_OPTIONS,
  GNQ_OPTIONS,
  YN_OPTIONS,
  MATERIAL_ALLOWED_EXT_TEXT,
  MATERIAL_MAX_SIZE_MB,
  toOptions,
  today,
  yearOf,
  ynNumber,
} from '../constants'

/** 第二步的 5 个长文本字段：结构相同，用一份配置渲染，避免 5 段重复模板 */
const ARG_TEXT_FIELDS = [
  { key: 'argReason', label: '提级论证事由', maxlength: 2000, placeholder: '不超过 2000 字' },
  { key: 'argBasis', label: '提级论证依据', maxlength: 2000, placeholder: '政策 / 规划依据，不超过 2000 字' },
  { key: 'argNecessity', label: '必要性说明', maxlength: 2000, placeholder: '不超过 2000 字' },
  { key: 'argFeasibility', label: '可行性说明', maxlength: 2000, placeholder: '不超过 2000 字' },
  { key: 'argContent', label: '论证事项内容', maxlength: 2000, placeholder: '不超过 2000 字' },
]

export default {
  name: 'EscalationFormModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenDateInput,
    ScreenRadioGroup,
    ScreenButton,
    ScreenIcon,
    ScreenLoading,
    EscalationMaterialTable,
  },
  data () {
    return {
      MATERIAL_ALLOWED_EXT_TEXT,
      MATERIAL_MAX_SIZE_MB,
      title: '新增提级论证项目',
      visible: false,
      step: 0,
      saving: false,
      checkingNo: false,
      /** 编辑回显时的详情加载态 */
      loading: false,
      noLoading: false,
      steps: [
        { title: '项目基本信息', desc: '项目 / 关联地块（选填）' },
        { title: '论证信息与结果', desc: '论证事由 / 结果登记' },
        { title: '提级论证材料', desc: '类型必选，至少 1 个' },
      ],
      argTextFields: ARG_TEXT_FIELDS,
      /** 校验错误集中表达：key -> 文案，空串表示通过 */
      errors: {},
      model: this.buildEmptyModel(),
      materials: [],
      /** 出让宗地下拉的选中值（宗地表主键；主表里存的是 crzdbh 文本） */
      landId: '',
      landOptions: [],
      /**
       * 宗地原始行（id → { crzdbh, dkmc, xzqh }）。
       * ScreenSelect 的 options 只有 { value, label }，选中后要带出地块信息，
       * 所以原始字段必须另存一份，不能只依赖 label 反解。
       */
      landRows: [],
      facilityOptions: [],
      xzqhOptions: [],
      landLoading: false,
      facilityLoading: false,
      projectTypeOptions: toOptions(PROJECT_TYPE_OPTIONS),
      argResultOptions: toOptions(ARG_RESULT_OPTIONS),
      statusOptions: toOptions(STATUS_OPTIONS),
      gnqOptions: toOptions(GNQ_OPTIONS),
      ynOptions: YN_OPTIONS,
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
        declareDate: today(),
        projectType: '',
        projectScale: '',
        totalInvestment: '',
        buildLocation: '',
        projectSummary: '',
        // 关联地块（选填）
        xzqh: '',
        gnq: '',
        dkmc: '',
        dkArea: '',
        ghydxz: '',
        tdzlProject: '',
        crzdbh: '',
        facilityId: '',
        ptxmmc: '',
        // 论证信息与结果
        argReason: '',
        argBasis: '',
        argNecessity: '',
        argFeasibility: '',
        argContent: '',
        argResult: '',
        argConclusion: '',
        argOrg: '',
        argMeetingDate: '',
        argExpertList: '',
        argDate: '',
        status: '未办理',
        remark: '',
      }
    },

    /* ---------------- 基础下拉 ---------------- */

    loadXzqh () {
      queryXzqhOptions()
        .then((res) => {
          if (!res || !res.success) return
          this.xzqhOptions = (res.result || []).map((item) => {
            if (item && item.value !== undefined) return { value: item.value, label: item.label }
            const value = item && (item.xzqh || item.name)
            return { value, label: value }
          })
        })
        .catch(() => {
          this.xzqhOptions = []
        })
    },

    /* ---------------- 打开：新增 / 编辑 ---------------- */

    showAdd () {
      this.title = '新增提级论证项目'
      this.step = 0
      this.errors = {}
      this.model = this.buildEmptyModel()
      this.materials = []
      this.landId = ''
      this.landOptions = []
      this.facilityOptions = []
      this.visible = true
      // 预生成项目编号（允许改写）
      this.handleGenerateNo()
    },

    showEdit (record) {
      if (!record || !record.id) {
        toast.warning('请选择要编辑的项目')
        return
      }
      this.title = '编辑提级论证项目'
      this.step = 0
      this.errors = {}
      this.visible = true
      this.loading = true
      queryProjectById(record.id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '项目详情加载失败')
            this.visible = false
            return
          }
          const data = res.result || {}
          this.model = Object.assign(this.buildEmptyModel(), {
            id: data.id,
            projectNo: data.projectNo || '',
            projectName: data.projectName || '',
            declareDept: data.declareDept || '',
            declareDate: data.declareDate || today(),
            projectType: data.projectType || '',
            projectScale: data.projectScale || '',
            totalInvestment:
              data.totalInvestment === null || data.totalInvestment === undefined
                ? ''
                : String(data.totalInvestment),
            buildLocation: data.buildLocation || '',
            projectSummary: data.projectSummary || '',
            xzqh: data.xzqh || '',
            gnq: data.gnq || '',
            dkmc: data.dkmc || '',
            dkArea: data.dkArea === null || data.dkArea === undefined ? '' : String(data.dkArea),
            ghydxz: data.ghydxz || '',
            // 下拉值必须是字符串（ScreenSelect 只接受 String/Number）
            tdzlProject:
              data.tdzlProject === null || data.tdzlProject === undefined
                ? ''
                : String(data.tdzlProject),
            crzdbh: data.crzdbh || '',
            facilityId: data.facilityId || '',
            ptxmmc: data.ptxmmc || '',
            argReason: data.argReason || '',
            argBasis: data.argBasis || '',
            argNecessity: data.argNecessity || '',
            argFeasibility: data.argFeasibility || '',
            argContent: data.argContent || '',
            argResult: data.argResult || '',
            argConclusion: data.argConclusion || '',
            argOrg: data.argOrg || '',
            argMeetingDate: data.argMeetingDate || '',
            argExpertList: data.argExpertList || '',
            argDate: data.argDate || '',
            status: data.status || '未办理',
            remark: data.remark || '',
          })
          // 回显宗地下拉：需要把已有选项灌进去，否则选中项显示不出名称
          this.landId = data.landId || ''
          if (data.landId) {
            this.loadLandOptions('')
          }
          if (data.crzdbh) {
            this.loadFacilityOptions('')
          }
          this.materials = data.materials || []
          if (!data.materials) {
            queryMaterialList(data.id).then((materialRes) => {
              if (materialRes && materialRes.success) {
                this.materials = materialRes.result || []
              }
            })
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 项目编号 ---------------- */

    handleGenerateNo () {
      this.noLoading = true
      genProjectNo(yearOf(this.model.declareDate))
        .then((res) => {
          if (!res || !res.success) {
            toast.warning((res && res.message) || '项目编号生成失败，可手工填写')
            return
          }
          this.model.projectNo = res.result || ''
        })
        .finally(() => {
          this.noLoading = false
        })
    },

    /**
     * 编号唯一性校验：只在失焦时提示，不阻断输入。
     * 真正的拦截交给后端 add/edit（保存前还会再校验一次）。
     */
    handleCheckNo () {
      const projectNo = (this.model.projectNo || '').trim()
      if (!projectNo) return
      checkProjectNo({ projectNo, id: this.model.id || undefined }).then((res) => {
        if (res && res.success && res.result === false) {
          toast.warning(`项目编号「${projectNo}」已存在，请改写或点「自动生成」`)
        }
      })
    },

    /* ---------------- 关联地块 ---------------- */

    loadLandOptions (keyword) {
      this.landLoading = true
      return queryLandOptions({ keyword: keyword || undefined, limit: 50 })
        .then((res) => {
          if (!res || !res.success) return
          this.landRows = res.result || []
          this.landOptions = this.landRows.map((item) => ({
            value: item.id,
            label: item.dkmc ? `${item.crzdbh} · ${item.dkmc}` : String(item.crzdbh || item.id),
          }))
        })
        .finally(() => {
          this.landLoading = false
        })
    },

    findLandRow (landId) {
      return this.landRows.filter((item) => item.id === landId)[0] || null
    },

    /** 选中出让宗地 → 自动带出地块名称 / 行政区划 / 规划用地性质 / 面积 */
    handleLandChange (landId) {
      if (!landId) {
        this.landId = ''
        this.model.crzdbh = ''
        this.model.facilityId = ''
        this.model.ptxmmc = ''
        this.facilityOptions = []
        return
      }
      const hit = this.findLandRow(landId)
      this.landId = landId
      if (hit && hit.crzdbh) this.model.crzdbh = hit.crzdbh
      if (hit && hit.dkmc) this.model.dkmc = this.model.dkmc || hit.dkmc
      if (hit && hit.xzqh) this.model.xzqh = this.model.xzqh || hit.xzqh
      this.facilityOptions = []
      this.model.facilityId = ''
      this.model.ptxmmc = ''

      // 列表接口不一定带规划用地性质，按 id 再查一次详情补齐（查不到仅忽略，不阻断填写）
      queryLandById({ id: landId }).then((res) => {
        if (!res || !res.success || !res.result) return
        const detail = res.result
        if (detail.crzdbh) this.model.crzdbh = detail.crzdbh
        if (detail.dkmc && !this.model.dkmc) this.model.dkmc = detail.dkmc
        if (detail.xzqh && !this.model.xzqh) this.model.xzqh = detail.xzqh
        if (detail.ghydxz && !this.model.ghydxz) this.model.ghydxz = detail.ghydxz
        if ((this.model.dkArea === '' || this.model.dkArea === null) && detail.dkArea !== null && detail.dkArea !== undefined) {
          this.model.dkArea = String(detail.dkArea)
        }
        // 宗地编号齐了才能查配套项目，所以这一步放在详情回来之后
        this.loadFacilityOptions('')
      })
    },

    loadFacilityOptions (keyword) {
      if (!this.model.crzdbh) {
        this.facilityOptions = []
        return Promise.resolve()
      }
      this.facilityLoading = true
      return queryFacilityOptions({ crzdbh: this.model.crzdbh, keyword: keyword || undefined, limit: 50 })
        .then((res) => {
          if (!res || !res.success) return
          this.facilityOptions = (res.result || []).map((item) => ({
            value: item.id,
            label: item.ptsslb ? `${item.ptxmmc} · ${item.ptsslb}` : String(item.ptxmmc || item.id),
          }))
        })
        .finally(() => {
          this.facilityLoading = false
        })
    },

    handleFacilityChange (facilityId) {
      if (!facilityId) {
        this.model.facilityId = ''
        this.model.ptxmmc = ''
        return
      }
      const hit = this.facilityOptions.filter((item) => item.value === facilityId)[0]
      this.model.facilityId = facilityId
      this.model.ptxmmc = hit && hit.ptxmmc ? hit.ptxmmc : this.model.ptxmmc
    },

    /* ---------------- 步骤控制与校验 ---------------- */

    /** 每一步只校验本步的字段，避免「后面的必填项」在第 1 步就报红 */
    validateStep (target) {
      const errors = Object.assign({}, this.errors)
      let keys = []
      if (target === 0) {
        keys = ['projectName', 'declareDept', 'declareDate', 'totalInvestment', 'buildLocation', 'projectSummary']
      } else if (target === 1) {
        keys = ['status']
      } else {
        keys = ['materials']
      }
      // 先清掉本步的旧错误，再按当前值重算，避免「改对了还红着」
      keys.forEach((key) => {
        errors[key] = ''
      })

      if (target === 0) {
        const model = this.model
        if (!String(model.projectName || '').trim()) errors.projectName = '请输入项目名称'
        if (!String(model.declareDept || '').trim()) errors.declareDept = '请输入申报单位'
        if (!model.declareDate) errors.declareDate = '请选择申报时间'
        const investment = model.totalInvestment
        if (investment === '' || investment === null || investment === undefined) {
          errors.totalInvestment = '请输入总投资'
        } else if (!Number.isFinite(Number(investment)) || Number(investment) < 0) {
          errors.totalInvestment = '总投资必须是不小于 0 的数字'
        }
        if (!String(model.buildLocation || '').trim()) errors.buildLocation = '请输入建设地点'
        if (!String(model.projectSummary || '').trim()) errors.projectSummary = '请输入项目概述'
      }

      if (target === 1 && !this.model.status) {
        errors.status = '请选择办理状态'
      }

      if (target === 2) {
        const table = this.$refs.materialTable
        errors.materials = table ? table.validate() : '材料组件未就绪，请重开弹窗'
      }

      this.errors = errors
      return keys.every((key) => !errors[key])
    },

    nextStep () {
      if (!this.validateStep(this.step)) return
      if (this.step < 2) this.step += 1
    },

    prevStep () {
      if (this.step > 0) this.step -= 1
    },

    /** 保存：三步全量校验，失败时跳到第一个出错的步骤（否则用户看不到错在哪） */
    handleSubmit () {
      const step0Ok = this.validateStep(0)
      const step1Ok = this.validateStep(1)
      const step2Ok = this.validateStep(2)
      if (!step0Ok) {
        this.step = 0
        toast.warning('第一步还有必填项没有填写')
        return
      }
      if (!step1Ok) {
        this.step = 1
        toast.warning('请选择办理状态')
        return
      }
      if (!step2Ok) {
        this.step = 2
        toast.warning(this.errors.materials || '材料不完整')
        return
      }

      /*
        保存前的编号唯一性校验（与失焦提示同一套接口，但这里是**硬拦截**）。
        校验接口本身失败时不阻断保存：后端 add/edit 内部还会再校验一次，
        前端把「网络抖动」当成「编号重复」会让人无法保存。
      */
      const projectNo = String(this.model.projectNo || '').trim()
      if (!projectNo) {
        this.submit()
        return
      }
      this.checkingNo = true
      checkProjectNo({ projectNo, id: this.model.id || undefined })
        .then((res) => {
          if (res && res.success && res.result === false) {
            toast.error(`项目编号「${projectNo}」已存在，请改写或点「自动生成」`)
            return
          }
          this.submit()
        })
        .catch(() => {
          this.submit()
        })
        .finally(() => {
          this.checkingNo = false
        })
    },

    submit () {
      const model = this.model
      const table = this.$refs.materialTable
      const payload = {
        id: model.id,
        projectNo: String(model.projectNo || '').trim() || null,
        projectName: String(model.projectName || '').trim(),
        declareDept: String(model.declareDept || '').trim(),
        declareDate: model.declareDate || null,
        projectType: model.projectType || null,
        projectScale: model.projectScale || null,
        totalInvestment: model.totalInvestment === '' ? null : Number(model.totalInvestment),
        buildLocation: model.buildLocation || null,
        projectSummary: model.projectSummary || null,
        xzqh: model.xzqh || null,
        gnq: model.gnq || null,
        dkmc: model.dkmc || null,
        dkArea: model.dkArea === '' ? null : Number(model.dkArea),
        ghydxz: model.ghydxz || null,
        tdzlProject: ynNumber(model.tdzlProject),
        crzdbh: model.crzdbh || null,
        facilityId: model.facilityId || null,
        ptxmmc: model.ptxmmc || null,
        argReason: model.argReason || null,
        argBasis: model.argBasis || null,
        argNecessity: model.argNecessity || null,
        argFeasibility: model.argFeasibility || null,
        argContent: model.argContent || null,
        argResult: model.argResult || null,
        argConclusion: model.argConclusion || null,
        argOrg: model.argOrg || null,
        argMeetingDate: model.argMeetingDate || null,
        argExpertList: model.argExpertList || null,
        argDate: model.argDate || null,
        status: model.status || null,
        remark: model.remark || null,
        materials: table ? table.collect() : [],
      }

      this.saving = true
      const task = payload.id ? editProject(payload) : addProject(payload)
      task
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '保存失败')
            return
          }
          toast.success(res.message || '保存成功')
          this.visible = false
          this.$emit('ok')
        })
        .finally(() => {
          this.saving = false
        })
    },

    handleCancel () {
      this.visible = false
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

/* ---------------- 步骤条 ---------------- */
.escalation-form__steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--screen-space-3);
  margin: 0 0 var(--screen-space-4);
  padding: 0 0 var(--screen-space-3);
  list-style: none;
  border-bottom: 1px solid var(--screen-border-soft);
}

.escalation-form__step {
  display: flex;
  align-items: center;
  gap: var(--screen-space-2);
  min-width: 0;
  color: var(--screen-text-mute);

  &.is-active,
  &.is-done {
    color: var(--screen-text-sub);
  }
}

.escalation-form__step-no {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-family: var(--screen-font-number-family);
  font-size: var(--screen-font-xs);
  color: var(--screen-text-sub);
  background: var(--screen-row-alt);
  border: 1px solid var(--screen-border);
  border-radius: 50%;
}

.escalation-form__step.is-active .escalation-form__step-no {
  color: var(--screen-text-on-accent);
  background: var(--screen-accent);
  border-color: var(--screen-accent);
}

.escalation-form__step.is-done .escalation-form__step-no {
  color: var(--screen-success);
  border-color: var(--screen-success);
}

.escalation-form__step-text {
  display: flex;
  flex-direction: column;
  min-width: 0;

  b {
    font-size: var(--screen-font-sm);
    font-weight: 500;
    .screen-ellipsis();
  }

  em {
    font-size: var(--screen-font-xs);
    font-style: normal;
    color: var(--screen-text-mute);
    .screen-ellipsis();
  }
}

/* ---------------- 表单分区 ---------------- */
.escalation-form {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-4);
}

.escalation-form__section {
  padding: var(--screen-space-4);
  background: var(--screen-panel-bg-solid);
  border: 1px solid var(--screen-border-soft);
  border-radius: var(--screen-radius);

  /* 「关联地块信息（选填）」用更弱的底色，一眼看出它是可选上下文 */
  &.is-optional {
    background: var(--screen-row-alt);
  }
}

.escalation-form__section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 var(--screen-space-4);
  font-size: var(--screen-font-md);
  font-weight: 500;
  color: var(--screen-text);
}

.escalation-form__section-hint {
  margin-left: 4px;
  font-size: var(--screen-font-xs);
  font-weight: 400;
  color: var(--screen-text-mute);
}

.escalation-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--screen-space-4);
}

.escalation-form__field {
  min-width: 0;

  /* 整行字段（项目编号 / 项目概述 / 长文本）。
     ★ 这里必须写两个长属性（grid-column-start / grid-column-end）：
       Less 会把跨列简写里的「一到负一」当成除法算成 -1，网格项会跑位（README 踩坑 1）。 */
  &.is-full {
    grid-column-start: 1;
    grid-column-end: -1;
  }
}

.escalation-form__with-action {
  display: flex;
  align-items: center;
  gap: var(--screen-space-2);

  /deep/ .screen-input {
    flex: 1 1 auto;
    min-width: 0;
  }
}

.escalation-form__error {
  margin: var(--screen-space-2) 0 0;
  font-size: var(--screen-font-xs);
  color: var(--screen-danger);
}

// 大屏窄屏降级：一列到底，保证控件可用宽度
@media (max-width: 1200px) {
  .escalation-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .escalation-form__steps {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
