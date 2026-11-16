<template>
  <!--
    HandoverFormModal 道路交付及养护协议移交事项 · 新增 / 编辑弹窗
    --------------------------------
    三段式表单，顺序与用户的思考顺序一致：
      1. 移交事项标识 —— 移交编号（可自动生成 YJ-{年}-{4位}）+ 状态
      2. 道路信息     —— 道路本体 + 关联配套项目（选中后回填宗地/地块/区划）
      3. 协议与移交   —— 协议签订情况、接收管养单位、移交日期、养护起止
    最后是备注。

    公开方法（父组件通过 $refs 调用）：
      showAdd()        打开新增（自动预生成移交编号）
      showEdit(record) 打开编辑（按 id 拉详情，保证拿到最新值）
    事件：
      ok               保存成功后抛出，父组件据此刷新列表与统计

    为什么自己写校验而不是用表单库：
      大屏组件库不依赖 antd，且这里有一条跨字段规则（养护截止不得早于起始），
      用统一的 errors 对象集中表达比分散在各控件上更清晰，
      也方便把错误文案同时挂到控件与 ScreenField 上。

    ★ 配套项目回填规则：**只在字段为空时写，不覆盖用户已经改过的值**。
      现实里同一条道路可能被人工纠正过名称/宗地，回填把人工修正冲掉是最难查的那种数据问题。
      唯一的例外是「道路名称」：它是配套项目的只读联动字段（用户重新选项目时才回填），
      用户没有第二个入口可以改它，所以不存在「冲掉用户输入」的问题。
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="1120"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    :body-max-height="'calc(100vh - 200px)'"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="handover-form">
      <!--
        加载态包一层 ScreenLoading（overlay=false 只做内容态）。
        ★ 它的根节点不是弹性子项，必须显式 flex: 1 1 auto; min-height: 0，
          否则放在 flex 列里不会拉伸（README 第 6 节第 9 条）。
      -->
      <screen-loading
        class="handover-form__loading"
        :loading="loading"
        text="正在加载移交事项…"
        :overlay="false"
      >
        <!-- ================= 1. 移交事项标识 ================= -->
        <section class="handover-form__section">
          <h4 class="handover-form__section-title">
            <screen-icon name="file-text" :size="14" />
            移交事项标识
          </h4>

          <div class="handover-form__grid">
            <screen-field
              label="移交编号"
              :error="errors.handoverNo"
              html-for="hf-handoverNo"
              tip="留空由后端按年度自动生成"
            >
              <div class="handover-form__inline">
                <screen-input
                  id="hf-handoverNo"
                  v-model="model.handoverNo"
                  :maxlength="64"
                  :invalid="!!errors.handoverNo"
                  placeholder="例如 YJ-2026-0001"
                  @blur="handleNoBlur"
                />
                <screen-button :loading="noLoading" @click="handleGenerateNo">自动生成</screen-button>
              </div>
            </screen-field>

            <screen-field label="移交状态" required>
              <screen-radio-group
                v-model="model.status"
                :options="statusOptions"
                aria-label="移交状态"
              />
            </screen-field>
          </div>
        </section>

        <!-- ================= 2. 道路信息 ================= -->
        <section class="handover-form__section">
          <h4 class="handover-form__section-title">
            <screen-icon name="layers" :size="14" />
            道路信息
          </h4>
          <p class="handover-form__section-hint">
            先选「关联配套项目」：道路名称随项目带出，出让宗地编号、地块名称与行政区
            <strong>只填空字段</strong>（不会覆盖你已经改过的值）。移交事项登记的是真正发生 / 在办的移交，
            没移交的道路不需要在这里建记录。
          </p>

          <div class="handover-form__grid">
            <!--
              道路名称与配套项目名称只保留**一个**用户输入入口（清单 P1-10 第 4 条）：
              先选配套项目，道路名称作为只读联动字段自动带出（后端仍冗余存 roadName）。
              只读而不是隐藏的原因：历史数据里两者可能不同值（人工改过），
              必须原样可见、原样保存，不能被联动静默覆盖。
            -->
            <screen-field
              label="关联配套项目"
              html-for="hf-facility"
              tip="选项目后自动带出道路名称、宗地编号与地块名称"
            >
              <screen-select
                id="hf-facility"
                v-model="model.facilityId"
                :options="facilityOptions"
                searchable
                :filter-local="false"
                :empty-text="facilityLoading ? '搜索中…' : '没有匹配的配套项目'"
                placeholder="按项目名称 / 宗地编号搜索"
                aria-label="关联配套项目"
                @search="handleFacilitySearch"
                @open="handleFacilityOpen"
                @change="handleFacilityChange"
              />
            </screen-field>

            <screen-field
              label="道路名称"
              required
              :error="errors.roadName"
              html-for="hf-roadName"
              tip="随「关联配套项目」自动带出，修改请改配套项目名称"
            >
              <screen-input
                id="hf-roadName"
                :value="model.roadName"
                readonly
                :invalid="!!errors.roadName"
                placeholder="选择「关联配套项目」后自动带出"
              />
            </screen-field>

            <screen-field label="道路编号" html-for="hf-roadCode">
              <screen-input id="hf-roadCode" v-model="model.roadCode" :maxlength="64" clearable />
            </screen-field>

            <screen-field label="道路等级">
              <screen-select
                v-model="model.dldj"
                :options="dldjOptions"
                placeholder="请选择"
                aria-label="道路等级"
              />
            </screen-field>

            <screen-field label="配套设施类别" html-for="hf-ptsslb">
              <screen-input id="hf-ptsslb" v-model="model.ptsslb" :maxlength="64" clearable placeholder="例如 道路及管线" />
            </screen-field>

            <screen-field label="起点" html-for="hf-startPoint">
              <screen-input id="hf-startPoint" v-model="model.startPoint" :maxlength="128" clearable />
            </screen-field>

            <screen-field label="终点" html-for="hf-endPoint">
              <screen-input id="hf-endPoint" v-model="model.endPoint" :maxlength="128" clearable />
            </screen-field>

            <screen-field label="长度(米)" :error="errors.lengthM" html-for="hf-lengthM">
              <screen-input
                id="hf-lengthM"
                v-model="model.lengthM"
                type="number"
                :min="0"
                :step="0.01"
                :invalid="!!errors.lengthM"
                placeholder="例如 1250.5"
              />
            </screen-field>

            <screen-field label="红线宽度(米)" :error="errors.redLineWidth" html-for="hf-redLineWidth">
              <screen-input
                id="hf-redLineWidth"
                v-model="model.redLineWidth"
                type="number"
                :min="0"
                :step="0.01"
                :invalid="!!errors.redLineWidth"
                placeholder="例如 40"
              />
            </screen-field>

            <screen-field label="出让宗地编号" html-for="hf-crzdbh">
              <screen-input id="hf-crzdbh" v-model="model.crzdbh" :maxlength="64" clearable />
            </screen-field>

            <screen-field label="地块名称" html-for="hf-dkmc">
              <screen-input id="hf-dkmc" v-model="model.dkmc" :maxlength="200" clearable />
            </screen-field>

            <screen-field label="行政区划">
              <screen-select
                v-model="model.xzqh"
                :options="xzqhOptions"
                placeholder="全部区划"
                aria-label="行政区划"
              />
            </screen-field>

            <screen-field label="功能区">
              <screen-select
                v-model="model.gnq"
                :options="gnqOptions"
                placeholder="非功能区留空"
                aria-label="功能区"
              />
            </screen-field>
          </div>
        </section>

        <!-- ================= 3. 协议与移交 ================= -->
        <section class="handover-form__section">
          <h4 class="handover-form__section-title">
            <screen-icon name="archive" :size="14" />
            协议与移交
          </h4>
          <p class="handover-form__section-hint">
            迁移生成的 84 条这些字段是空的（旧库只有「是否移交=是」一个标志位），
            请按实际协议补录。协议扫描件与移交单不在本页上传：先到「档案维护」上传，
            再在详情里点「关联档案」建立关联。
          </p>

          <div class="handover-form__grid">
            <screen-field label="移交类型">
              <screen-select
                v-model="model.handoverType"
                :options="typeOptions"
                placeholder="请选择"
                aria-label="移交类型"
              />
            </screen-field>

            <screen-field label="协议编号" html-for="hf-agreementNo">
              <screen-input id="hf-agreementNo" v-model="model.agreementNo" :maxlength="128" clearable />
            </screen-field>

            <screen-field label="协议签订日期">
              <screen-date-input id="hf-agreementDate" v-model="model.agreementDate" />
            </screen-field>

            <screen-field class="handover-form__span-all" label="协议名称" html-for="hf-agreementName">
              <screen-input
                id="hf-agreementName"
                v-model="model.agreementName"
                :maxlength="255"
                clearable
                placeholder="例如 XX 道路交付及养护协议"
              />
            </screen-field>

            <screen-field label="实际移交日期">
              <screen-date-input id="hf-handoverDate" v-model="model.handoverDate" />
            </screen-field>

            <screen-field label="建设单位" html-for="hf-buildUnit">
              <screen-input id="hf-buildUnit" v-model="model.buildUnit" :maxlength="200" clearable />
            </screen-field>

            <screen-field label="接收管养单位" html-for="hf-receiveUnit">
              <screen-input
                id="hf-receiveUnit"
                v-model="model.receiveUnit"
                :maxlength="200"
                clearable
                placeholder="例如 区城管委"
              />
            </screen-field>

            <screen-field label="养护起始日期" :error="errors.maintenanceStart">
              <screen-date-input
                id="hf-maintenanceStart"
                v-model="model.maintenanceStart"
                :invalid="!!errors.maintenanceStart"
              />
            </screen-field>

            <screen-field label="养护截止日期" :error="errors.maintenanceEnd">
              <screen-date-input
                id="hf-maintenanceEnd"
                v-model="model.maintenanceEnd"
                :invalid="!!errors.maintenanceEnd"
              />
            </screen-field>
          </div>
        </section>

        <screen-field label="备注" html-for="hf-remark" label-width="96px">
          <screen-input
            id="hf-remark"
            v-model="model.remark"
            type="textarea"
            :rows="2"
            :maxlength="1000"
            placeholder="补充说明，最多 1000 字（迁移记录的「宗地无匹配记录」说明也在这里）"
          />
        </screen-field>
      </screen-loading>
    </div>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenRadioGroup,
  ScreenDateInput,
  ScreenButton,
  ScreenIcon,
  ScreenLoading
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { queryXzqhOptions, searchFacilityOptions } from '@/api/land/landData'
import {
  addHandover,
  checkHandoverNo,
  editHandover,
  generateHandoverNo,
  queryHandoverById
} from '@/api/land/handover'
import {
  DLDJ_OPTIONS,
  GNQ_OPTIONS,
  handoverStatusOptions,
  handoverTypeOptions
} from './constants'

/** 下拉一次拉取的最大条数（后端 limit 参数） */
const OPTION_LIMIT = 50

/** 可编辑字段的初始值（空表单） */
function buildEmptyModel () {
  return {
    id: null,
    handoverNo: '',
    roadName: '',
    roadCode: '',
    dldj: '',
    startPoint: '',
    endPoint: '',
    lengthM: '',
    redLineWidth: '',
    xzqh: '',
    gnq: '',
    ptsslb: '',
    crzdbh: '',
    landId: '',
    dkmc: '',
    facilityId: '',
    /**
     * 后端的配套项目名称（只读回显用）。
     * 为什么要它：编辑时的下拉 label 必须用后端 `ptxmmc`，不能拿可能与后端不同的
     * `roadName` 顶替（清单 P1-10 第 3 条）；保存时也一并回传，避免后端用道路名称兜底。
     */
    ptxmmc: '',
    handoverType: '',
    agreementNo: '',
    agreementName: '',
    agreementDate: '',
    buildUnit: '',
    receiveUnit: '',
    handoverDate: '',
    maintenanceStart: '',
    maintenanceEnd: '',
    status: '待移交',
    remark: ''
  }
}

export default {
  name: 'HandoverFormModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenRadioGroup,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon,
    ScreenLoading
  },
  data () {
    return {
      visible: false,
      title: '新增移交事项',
      saving: false,
      noLoading: false,
      loading: false,
      /** 只有点过一次保存才把校验错误显示出来，避免一打开就满屏飘红 */
      submitted: false,
      model: buildEmptyModel(),
      errors: {},
      xzqhOptions: [],
      facilityOptions: [],
      facilityRawMap: {},
      facilityLoading: false,
      statusOptions: handoverStatusOptions(),
      typeOptions: handoverTypeOptions(),
      dldjOptions: DLDJ_OPTIONS.map((item) => ({ value: item, label: item })),
      gnqOptions: GNQ_OPTIONS.map((item) => ({ value: item, label: item }))
    }
  },
  created () {
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
  methods: {
    /* ---------------- 对外入口 ---------------- */

    showAdd () {
      this.title = '新增移交事项'
      this.model = buildEmptyModel()
      this.errors = {}
      this.submitted = false
      this.loading = false
      this.facilityOptions = []
      this.facilityRawMap = {}
      this.visible = true
      // 预生成编号（失败不阻塞：用户可以手填，保存时后端也会兜底生成）
      this.handleGenerateNo(true)
      // 新增时就要有候选：原来必须输入关键词才请求，用户会以为没有可选的配套项目
      this.loadFacilityOptions('')
    },

    showEdit (record) {
      if (!record || !record.id) {
        toast.warning('缺少移交事项 ID，无法编辑')
        return
      }
      this.title = '编辑移交事项'
      this.model = buildEmptyModel()
      this.errors = {}
      this.submitted = false
      this.facilityOptions = []
      this.facilityRawMap = {}
      this.visible = true
      this.loading = true
      this.loadDetail(record.id)
    },

    /**
     * 拉详情并回显（详情接口是权威数据源，不用列表行的快照，
     * 因为列表可能已经被别人改过、或本行的字段不完整）。
     */
    loadDetail (id) {
      queryHandoverById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '移交事项详情加载失败')
            this.visible = false
            return
          }
          const data = res.result || {}
          const model = buildEmptyModel()
          Object.keys(model).forEach((key) => {
            if (key === 'id') return
            const value = data[key]
            if (value !== undefined && value !== null) model[key] = value
          })
          model.id = data.id
          // 数值型字段用字符串承接，避免 ScreenInput 在 number 类型下把 0 显示成空
          model.lengthM = data.lengthM === undefined || data.lengthM === null ? '' : data.lengthM
          model.redLineWidth = data.redLineWidth === undefined || data.redLineWidth === null ? '' : data.redLineWidth
          this.model = model

          // 候选要跟着回显的宗地走：首发一批，再把当前项目补进候选
          // （当前项可能不在前 50 条里，不补下拉会显示空白）
          this.loadFacilityOptions('')
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 交互 ---------------- */

    handleGenerateNo (silent) {
      const year = this.yearOf(this.model.agreementDate || this.model.handoverDate)
      this.noLoading = true
      generateHandoverNo(year)
        .then((res) => {
          if (!res || !res.success) {
            if (!silent) toast.error((res && res.message) || '移交编号生成失败')
            return
          }
          // Result.OK(msg, data) 把数据放在 result；兼容只放 message 的情况
          this.model.handoverNo = res.result || res.message || ''
        })
        .catch(() => {
          if (!silent) toast.error('移交编号生成失败，可手工填写')
        })
        .finally(() => {
          this.noLoading = false
        })
    },

    /** 失焦时校验编号唯一性（非阻断：真冲突时后端也会拒绝并给出同样的提示） */
    handleNoBlur () {
      const handoverNo = String(this.model.handoverNo || '').trim()
      if (!handoverNo) return
      checkHandoverNo({ handoverNo, id: this.model.id || undefined })
        .then((res) => {
          if (res && !res.success) {
            this.errors = Object.assign({}, this.errors, { handoverNo: res.message || '移交编号已存在' })
          } else if (this.errors.handoverNo) {
            const next = Object.assign({}, this.errors)
            delete next.handoverNo
            this.errors = next
          }
        })
        .catch(() => {
          // 校验接口失败不阻塞保存流程（后端保存时还会再校验一次）
        })
    },

    /* ---------------- 配套项目候选 ---------------- */

    /** 展开下拉时刷新首批（只在还没加载过时发请求，避免每次点开都打接口） */
    handleFacilityOpen () {
      if (!this.facilityOptions.length) this.loadFacilityOptions('')
    },

    /**
     * 配套项目远程搜索。
     * 关键词为空时加载**首批**而不是清空候选：清空会让当前已选项一起消失，
     * 下拉显示空白，看起来像「项目丢了」。
     * 后端 `/land/data/facility/search` 的关键词同时匹配项目名与宗地编号，
     * 所以「按项目名 / 宗地编号搜索」是同一个入参。
     */
    handleFacilitySearch (keyword) {
      this.loadFacilityOptions(keyword)
    },

    /**
     * 拉取配套项目候选（整体替换，失败/为空时清空，不留上一轮结果）。
     * 道路类项目的排前面：这页登记的都是道路移交，先看到道路更顺手；
     * 但不丢弃其它类别 —— 这里只是**排序偏好**，不是过滤。
     *
     * ★ `roadFirst: true`（P1-10，后端可选参数）把「道路排前面」交给后端做：
     *   前端只能对已返回的 50 条排序，第 51 条以后的道路根本进不了候选，
     *   表现为「明明有这条路，搜不到 / 要输全名才出现」。
     *   后端未上线时该参数会被忽略（GET 多一个查询参数不影响老接口），
     *   此时下面的 roadRank 本地排序仍然生效，属于双保险。
     */
    loadFacilityOptions (keyword) {
      const text = String(keyword || '').trim()
      this.facilityLoading = true
      searchFacilityOptions({ keyword: text || undefined, limit: OPTION_LIMIT, roadFirst: true })
        .then((res) => {
          const list = res && res.success ? res.result || [] : []
          this.applyFacilityList(list)
        })
        .catch(() => {
          this.facilityOptions = []
          this.facilityRawMap = {}
          this.ensureCurrentFacilityOption()
        })
        .finally(() => {
          this.facilityLoading = false
        })
    },

    /** 后端列表 → 下拉选项 + 原始对象映射（handleFacilityChange 要看原始字段） */
    applyFacilityList (list) {
      const sorted = (list || []).slice().sort((a, b) => this.roadRank(a) - this.roadRank(b))
      const rawMap = {}
      this.facilityOptions = sorted.map((item) => {
        rawMap[item.id] = item
        return {
          value: item.id,
          // label 用可读名称：项目名（+宗地编号便于区分同名道路），名称缺失才退到宗地编号
          label: item.ptxmmc
            ? (item.crzdbh ? `${item.ptxmmc}（${item.crzdbh}）` : item.ptxmmc)
            : (item.crzdbh || '未命名配套项目')
        }
      })
      this.facilityRawMap = rawMap
      this.ensureCurrentFacilityOption()
    },

    /** 道路类（ptsslb 含「道路」）排前面，其余保持原序 */
    roadRank (item) {
      const category = String((item && item.ptsslb) || '')
      return category.indexOf('道路') > -1 ? 0 : 1
    },

    /**
     * 把当前已选的配套项目补进候选。
     * ★ label 用后端 `ptxmmc`（其次宗地编号，最后占位文案），
     *   **不用** `roadName`：两者可能不同值，用 roadName 会掩盖真实项目名（清单 P1-10 第 3 条）。
     */
    ensureCurrentFacilityOption () {
      const model = this.model || {}
      if (!model.facilityId) return
      if (this.facilityOptions.some((item) => item.value === model.facilityId)) return
      const name = model.ptxmmc || this.readableModelName()
      this.facilityOptions = [{ value: model.facilityId, label: name }].concat(this.facilityOptions)
      // 原始对象也补上，否则用户不动下拉、直接保存时回填字段会拿不到
      if (!this.facilityRawMap[model.facilityId]) {
        const raw = {
          id: model.facilityId,
          ptxmmc: model.ptxmmc,
          crzdbh: model.crzdbh,
          dkmc: model.dkmc,
          ptsslb: model.ptsslb,
          landId: model.landId,
          dldj: model.dldj,
          xzqh: model.xzqh
        }
        this.facilityRawMap = Object.assign({}, this.facilityRawMap, { [model.facilityId]: raw })
      }
    },

    /** 回显用的可读项目名：ptxmmc → 宗地编号 → 占位文案（绝不拿 id 当名称） */
    readableModelName () {
      const model = this.model || {}
      return model.ptxmmc || model.crzdbh || '当前已选配套项目（名称待补）'
    },

    /**
     * 选中配套项目后回填道路信息。
     *
     * ★ 道路名称**每次选中都跟随项目名称**：它是只读联动字段，用户唯一的输入入口是
     *   配套项目。但只在「用户重新选择项目」时回填 —— 编辑打开一条历史记录时不动
     *   roadName，历史数据里两者不同值（人工改过）的记录因此不会被静默覆盖。
     * ★ 其余字段只填空字段：同一条道路的人工修正不能被回填冲掉。
     * ★ 区划归位：旧数据里行政区划列混着功能区取值（生态城 / 经开区…），
     *   直接把功能区写进 xzqh 会造出「行政区划=生态城」这种脏数据，
     *   所以这里按取值判断该落到 xzqh 还是 gnq。
     */
    handleFacilityChange (facilityId) {
      if (!facilityId) {
        this.model.facilityId = ''
        this.model.ptxmmc = ''
        return
      }
      this.model.facilityId = facilityId
      const hit = this.facilityRawMap[facilityId]
      if (!hit) return

      // 项目名称（后端字段）与道路名称同源：都取配套项目名称
      const projectName = hit.ptxmmc || ''
      this.model.ptxmmc = projectName
      if (projectName) {
        this.model.roadName = projectName
      }

      if (!this.model.crzdbh) this.model.crzdbh = hit.crzdbh || ''
      if (!this.model.dkmc) this.model.dkmc = hit.dkmc || ''
      if (!this.model.ptsslb) this.model.ptsslb = hit.ptsslb || ''
      if (!this.model.landId) this.model.landId = hit.landId || ''
      if (!this.model.dldj) this.model.dldj = hit.dldj || ''

      const area = hit.xzqh || ''
      if (area) {
        if (GNQ_OPTIONS.indexOf(area) > -1) {
          if (!this.model.gnq) this.model.gnq = area
        } else if (!this.model.xzqh) {
          this.model.xzqh = area
        }
      }
    },

    /* ---------------- 校验与提交 ---------------- */

    /** @returns {boolean} 是否通过 */
    validateForm () {
      const errors = {}
      // 道路名称是只读联动字段，为空只可能是「还没选配套项目」
      if (!String(this.model.roadName || '').trim()) {
        errors.roadName = this.model.facilityId
          ? '配套项目名称为空，请重新选择关联配套项目'
          : '请先选择「关联配套项目」'
      }
      if (!this.model.status) {
        errors.status = '请选择移交状态'
      }

      const lengthM = this.model.lengthM
      if (lengthM !== '' && lengthM !== null && lengthM !== undefined) {
        if (!Number.isFinite(Number(lengthM)) || Number(lengthM) < 0) {
          errors.lengthM = '长度必须是不小于 0 的数字'
        }
      }
      const redLineWidth = this.model.redLineWidth
      if (redLineWidth !== '' && redLineWidth !== null && redLineWidth !== undefined) {
        if (!Number.isFinite(Number(redLineWidth)) || Number(redLineWidth) < 0) {
          errors.redLineWidth = '红线宽度必须是不小于 0 的数字'
        }
      }

      // 跨字段：养护截止不得早于养护起始
      if (this.model.maintenanceStart && this.model.maintenanceEnd &&
        this.model.maintenanceEnd < this.model.maintenanceStart) {
        errors.maintenanceEnd = '养护截止日期不能早于养护起始日期'
      }

      this.errors = errors
      return Object.keys(errors).length === 0
    },

    handleOk () {
      this.submitted = true
      if (!this.validateForm()) {
        toast.warning('还有必填项未完成，请检查标红的字段')
        return
      }
      this.submit()
    },

    /**
     * 组装提交体。
     * ★ 从**固定字段清单**组装，而不是把整条记录展开传回去：
     *   记录上还带着 sourceFacilityId（迁移幂等键）、archiveId / archiveCount（档案指针）、
     *   seq / relatedArchives（非表字段），它们都不该由表单改写。
     */
    buildPayload () {
      const model = this.model
      const text = (value) => {
        const str = value === undefined || value === null ? '' : String(value).trim()
        return str === '' ? null : str
      }
      const number = (value) => {
        if (value === '' || value === null || value === undefined) return null
        const num = Number(value)
        return Number.isFinite(num) ? num : null
      }
      return {
        id: model.id || undefined,
        handoverNo: text(model.handoverNo),
        roadName: text(model.roadName),
        roadCode: text(model.roadCode),
        dldj: text(model.dldj),
        startPoint: text(model.startPoint),
        endPoint: text(model.endPoint),
        lengthM: number(model.lengthM),
        redLineWidth: number(model.redLineWidth),
        xzqh: text(model.xzqh),
        gnq: text(model.gnq),
        ptsslb: text(model.ptsslb),
        crzdbh: text(model.crzdbh),
        landId: text(model.landId),
        dkmc: text(model.dkmc),
        facilityId: text(model.facilityId),
        // 后端 RoadHandover.ptxmmc 与 roadName 是两列（历史数据可能不同值）：
        // 只读联动后两者同源，但这里仍显式回传，避免后端用 roadName 兜底时把
        // 历史记录的 ptxmmc 覆盖成道路名称
        ptxmmc: text(model.ptxmmc),
        handoverType: text(model.handoverType),
        agreementNo: text(model.agreementNo),
        agreementName: text(model.agreementName),
        agreementDate: text(model.agreementDate),
        buildUnit: text(model.buildUnit),
        receiveUnit: text(model.receiveUnit),
        handoverDate: text(model.handoverDate),
        maintenanceStart: text(model.maintenanceStart),
        maintenanceEnd: text(model.maintenanceEnd),
        status: text(model.status),
        remark: text(model.remark)
      }
    },

    submit () {
      const payload = this.buildPayload()
      this.saving = true
      const request = payload.id ? editHandover(payload) : addHandover(payload)

      request
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
          // 请求层已弹过错误提示，这里只负责不把弹窗留在 loading 状态
        })
        .finally(() => {
          this.saving = false
        })
    },

    handleCancel () {
      this.visible = false
    },

    /* ---------------- 工具 ---------------- */

    /** 从日期串里取年份（'yyyy-MM-dd' 或 Date 都能吃） */
    yearOf (value) {
      if (!value) return new Date().getFullYear()
      const date = value instanceof Date ? value : new Date(String(value).replace(/-/g, '/'))
      if (isNaN(date.getTime())) return new Date().getFullYear()
      return date.getFullYear()
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.handover-form {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-4);

  // ScreenLoading 的根节点不是弹性子项，必须显式拉伸（README 第 6 节第 9 条）
  &__loading {
    flex: 1 1 auto;
    min-height: 0;
  }

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    padding: var(--screen-space-3);
    background: var(--screen-row-alt);
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
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);

    strong {
      color: var(--screen-accent-soft);
      font-weight: 600;
    }
  }

  // 三列排布；窄屏逐级降列
  &__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  // 占满整行的字段。
  // ⚠ 不能写 `grid-column: 1 / -1`：Less 会把 `1 / -1` 当成除法算成 -1（README 第 6 节第 1 条）。
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
}

@media (max-width: 1500px) {
  .handover-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1000px) {
  .handover-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
