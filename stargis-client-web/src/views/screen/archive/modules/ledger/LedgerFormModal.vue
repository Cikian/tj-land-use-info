<template>
  <!--
    LedgerFormModal 台账新增 / 编辑弹窗
    --------------------------------
    分段顺序与用户的思考顺序一致：
      1. 台账标识   —— 编号（可自动生成）+ 状态
      2. 关联项目   —— 选填；选中后自动带出宗地与行政区信息
      3. 道路与宗地 —— 道路名称（必填）等台账字段
      4. 验收信息   —— 验收类型 / 结果 / 日期 / 单号 / 组织单位 / 竣工日期
      5. 13 类资料  —— 逐项勾选（资料矩阵的录入入口）
      6. 移交信息   —— 移交单位 / 接收管养单位 / 移交日期
      7. 备注

    ★ 为什么资料用原生 checkbox 而不是 Screen 组件：
      组件库没有 checkbox 原子（ScreenRadioGroup 是互斥单选，语义不对），
      13 类资料需要的是「可多选的布尔集合」；原生 input + label 既能拿到
      无障碍语义（label 即名称），又能用令牌自定义样式，不引入 antd。

    公开方法：
      showAdd()        打开新增（会自动预生成编号）
      showEdit(record) 打开编辑（按 id 拉详情回显，含 13 类资料勾选）
    事件：
      ok  保存成功后抛出，父组件据此刷新列表与统计
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="1180"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    :body-max-height="'calc(100vh - 200px)'"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="ledger-form">
      <!-- ================= 1. 台账标识 ================= -->
      <section class="ledger-form__section">
        <h4 class="ledger-form__section-title">
          <screen-icon name="archive" :size="14" />
          台账标识
        </h4>

        <div class="ledger-form__grid">
          <screen-field
            label="台账编号"
            :error="errors.ledgerNo"
            html-for="lf-ledgerNo"
            tip="留空时由后端按验收年度自动生成（YS-年-4位）"
          >
            <div class="ledger-form__inline">
              <screen-input
                id="lf-ledgerNo"
                v-model="model.ledgerNo"
                :maxlength="64"
                :invalid="!!errors.ledgerNo"
                placeholder="例如 YS-2026-0001"
                @blur="handleNoBlur"
              />
              <screen-button :loading="noLoading" @click="handleGenerateNo">自动生成</screen-button>
            </div>
          </screen-field>

          <screen-field label="状态" required>
            <screen-radio-group
              v-model="model.status"
              :options="statusOptions"
              aria-label="台账状态"
            />
          </screen-field>
        </div>
      </section>

      <!-- ================= 2. 关联项目（选填） ================= -->
      <section class="ledger-form__section">
        <h4 class="ledger-form__section-title">
          <screen-icon name="folder-open" :size="14" />
          关联配套项目
          <span class="ledger-form__section-kicker">选填</span>
        </h4>
        <p class="ledger-form__section-hint">
          选中后会带出宗地编号、地块名称、行政区划与设施类别。
          <strong>存量道路不一定有对应的配套项目</strong>
          （迁移的 1340 条里有 53 条的宗地在系统里找不到），
          这种情况直接在下面的「道路与宗地信息」里手工填写即可。
        </p>

        <project-picker v-model="association" :required="false" @change="handleAssociationChange" />
      </section>

      <!-- ================= 3. 道路与宗地信息 ================= -->
      <section class="ledger-form__section">
        <h4 class="ledger-form__section-title">
          <screen-icon name="layers" :size="14" />
          道路与宗地信息
        </h4>

        <div class="ledger-form__grid">
          <screen-field label="道路名称" required :error="errors.roadName" html-for="lf-roadName">
            <screen-input
              id="lf-roadName"
              v-model="model.roadName"
              :maxlength="200"
              :invalid="!!errors.roadName"
              placeholder="必填，例如 XX 路（XX 道 - XX 路）"
            />
          </screen-field>

          <screen-field label="行政区划">
            <screen-select
              v-model="model.xzqh"
              :options="xzqhOptions"
              placeholder="请选择行政区划"
              aria-label="行政区划"
            />
          </screen-field>

          <screen-field label="功能区">
            <screen-select
              v-model="model.gnq"
              :options="gnqOptions"
              placeholder="非功能区可留空"
              aria-label="功能区"
            />
          </screen-field>

          <screen-field label="设施类别">
            <screen-select
              v-model="model.ptsslb"
              :options="ptsslbOptions"
              placeholder="请选择设施类别"
              aria-label="配套设施类别"
            />
          </screen-field>

          <screen-field label="道路等级" html-for="lf-dldj">
            <screen-input
              id="lf-dldj"
              v-model="model.dldj"
              :maxlength="64"
              placeholder="例如 城市主干道"
            />
          </screen-field>

          <screen-field label="出让宗地编号" html-for="lf-crzdbh">
            <screen-input
              id="lf-crzdbh"
              v-model="model.crzdbh"
              :maxlength="128"
              placeholder="宗地编号"
            />
          </screen-field>

          <screen-field label="地块名称" html-for="lf-dkmc">
            <screen-input
              id="lf-dkmc"
              v-model="model.dkmc"
              :maxlength="200"
              placeholder="地块名称"
            />
          </screen-field>

          <screen-field label="配套项目名称" html-for="lf-ptxmmc" tip="留空则与道路名称一致">
            <screen-input
              id="lf-ptxmmc"
              v-model="model.ptxmmc"
              :maxlength="200"
              placeholder="配套项目名称"
            />
          </screen-field>
        </div>
      </section>

      <!-- ================= 4. 验收信息 ================= -->
      <section class="ledger-form__section">
        <h4 class="ledger-form__section-title">
          <screen-icon name="check-circle" :size="14" />
          验收信息
        </h4>

        <div class="ledger-form__grid">
          <screen-field label="验收类型">
            <screen-select
              v-model="model.acceptanceType"
              :options="typeOptions"
              placeholder="请选择验收类型"
              aria-label="验收类型"
            />
          </screen-field>

          <screen-field label="验收结果">
            <screen-select
              v-model="model.acceptanceResult"
              :options="resultOptions"
              placeholder="请选择验收结果"
              aria-label="验收结果"
            />
          </screen-field>

          <screen-field label="验收日期">
            <screen-date-input id="lf-acceptanceDate" v-model="model.acceptanceDate" />
          </screen-field>

          <screen-field label="验收单编号" html-for="lf-acceptanceNo">
            <screen-input
              id="lf-acceptanceNo"
              v-model="model.acceptanceNo"
              :maxlength="128"
              placeholder="验收单编号"
            />
          </screen-field>

          <screen-field label="验收组织单位" html-for="lf-acceptanceOrg">
            <screen-input
              id="lf-acceptanceOrg"
              v-model="model.acceptanceOrg"
              :maxlength="200"
              placeholder="组织验收的单位"
            />
          </screen-field>

          <screen-field label="实际竣工日期">
            <screen-date-input id="lf-completeDate" v-model="model.completeDate" />
          </screen-field>
        </div>
      </section>

      <!-- ================= 5. 13 类资料 ================= -->
      <section class="ledger-form__section">
        <h4 class="ledger-form__section-title">
          <screen-icon name="file-text" :size="14" />
          资料归集情况
          <span class="ledger-form__section-kicker">
            已归集 {{ checkedCount }}/{{ materialTotal }} 类
          </span>
        </h4>
        <p class="ledger-form__section-hint">
          勾选表示该道路的这类资料<strong>已归集</strong>（原件统一存放在档案管理模块，
          台账只记录「有没有」）。悬停可看每一类资料的来源说明；
          批量补录请用列表上方的「批量补录」。
        </p>

        <div class="ledger-form__materials">
          <label
            v-for="item in materialList"
            :key="item.key"
            class="ledger-form__material"
            :class="{ 'is-on': isChecked(item.key) }"
            :title="item.source || item.label"
          >
            <input
              class="ledger-form__check"
              type="checkbox"
              :checked="isChecked(item.key)"
              @change="toggleMaterial(item.key, $event)"
            />
            <span class="ledger-form__material-seq">{{ padSeq(item.seq) }}</span>
            <span class="ledger-form__material-label">{{ item.label }}</span>
          </label>
        </div>

        <div class="ledger-form__material-actions">
          <screen-button size="sm" @click="checkAllMaterials(true)">全部已归集</screen-button>
          <screen-button size="sm" @click="checkAllMaterials(false)">全部未归集</screen-button>
        </div>
      </section>

      <!-- ================= 6. 移交信息 ================= -->
      <section class="ledger-form__section">
        <h4 class="ledger-form__section-title">
          <screen-icon name="send" :size="14" />
          移交信息
        </h4>

        <div class="ledger-form__grid">
          <screen-field label="移交单位" html-for="lf-handoverUnit">
            <screen-input
              id="lf-handoverUnit"
              v-model="model.handoverUnit"
              :maxlength="200"
              placeholder="移交单位"
            />
          </screen-field>

          <screen-field label="接收管养单位" html-for="lf-receiveUnit">
            <screen-input
              id="lf-receiveUnit"
              v-model="model.receiveUnit"
              :maxlength="200"
              placeholder="接收管养单位"
            />
          </screen-field>

          <screen-field label="移交日期">
            <screen-date-input id="lf-handoverDate" v-model="model.handoverDate" />
          </screen-field>
        </div>
      </section>

      <!-- ================= 7. 备注 ================= -->
      <section class="ledger-form__section">
        <screen-field label="备注" html-for="lf-remark">
          <screen-input
            id="lf-remark"
            v-model="model.remark"
            type="textarea"
            :rows="2"
            :maxlength="1000"
            placeholder="补充说明，最多 1000 字"
          />
        </screen-field>
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
  ScreenRadioGroup,
  ScreenDateInput,
  ScreenButton,
  ScreenIcon
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import ProjectPicker from '../ProjectPicker.vue'
import { queryXzqhOptions } from '@/api/land/landData'
import {
  addLedger,
  editLedger,
  generateLedgerNo,
  checkLedgerNo,
  queryLedgerById,
  queryLedgerMaterials
} from '@/api/land/ledger'
import {
  ACCEPTANCE_RESULTS,
  ACCEPTANCE_TYPES,
  GNQ_OPTIONS,
  LEDGER_STATUS,
  LEDGER_MATERIALS_FALLBACK,
  MATERIAL_TOTAL,
  PTSSLB_OPTIONS,
  normalizeMaterials,
  padSeq,
  toOptions,
  today,
  yearOf
} from './constants'

export default {
  name: 'LedgerFormModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenRadioGroup,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon,
    ProjectPicker
  },
  props: {
    /** 13 类资料定义（面板已拉过就传进来，避免重复请求） */
    materials: { type: Array, default: () => [] }
  },
  data () {
    return {
      visible: false,
      title: '新增台账',
      saving: false,
      noLoading: false,
      loading: false,
      /** 是否点过一次保存：只有点过之后才显示校验错误，避免一打开就满屏飘红 */
      submitted: false,
      model: this.buildEmptyModel(),
      association: {},
      errors: {},
      xzqhOptions: [],
      localMaterials: LEDGER_MATERIALS_FALLBACK.slice(),
      statusOptions: toOptions(LEDGER_STATUS),
      typeOptions: toOptions(ACCEPTANCE_TYPES),
      resultOptions: toOptions(ACCEPTANCE_RESULTS),
      gnqOptions: toOptions(GNQ_OPTIONS),
      ptsslbOptions: toOptions(PTSSLB_OPTIONS)
    }
  },
  computed: {
    materialList () {
      return this.materials && this.materials.length ? this.materials : this.localMaterials
    },
    materialTotal () {
      return this.materialList.length || MATERIAL_TOTAL
    },
    checkedCount () {
      return this.materialList.reduce((sum, item) => sum + (this.isChecked(item.key) ? 1 : 0), 0)
    }
  },
  created () {
    this.loadXzqh()
    if (!this.materials || !this.materials.length) {
      this.loadMaterials()
    }
  },
  methods: {
    /**
     * 新台账的初始值：与后端 fillDefaults 保持一致
     * （状态默认「未验收」、资料全 0、日期留空），避免保存后字段被后端改掉。
     */
    buildEmptyModel () {
      const model = {
        id: null,
        ledgerNo: '',
        roadName: '',
        xzqh: '',
        gnq: '',
        crzdbh: '',
        landId: null,
        dkmc: '',
        ptsslb: '',
        facilityId: null,
        ptxmmc: '',
        dldj: '',
        jsdw: '',
        sgdw: '',
        jldw: '',
        acceptanceType: '',
        acceptanceNo: '',
        acceptanceDate: '',
        acceptanceOrg: '',
        acceptanceResult: '',
        completeDate: '',
        handoverUnit: '',
        receiveUnit: '',
        handoverDate: '',
        status: '未验收',
        remark: ''
      }
      LEDGER_MATERIALS_FALLBACK.forEach((item) => {
        model[item.key] = 0
      })
      return model
    },

    /* ---------------- 数据加载 ---------------- */

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

    loadMaterials () {
      return queryLedgerMaterials()
        .then((res) => {
          if (res && res.success) {
            this.localMaterials = normalizeMaterials(res.result)
            // 兜底资料列表补齐后，保证 model 上 13 个勾选字段都存在
            this.localMaterials.forEach((item) => {
              if (this.model[item.key] === undefined) this.$set(this.model, item.key, 0)
            })
          }
        })
        .catch(() => {
          this.localMaterials = LEDGER_MATERIALS_FALLBACK.slice()
        })
    },

    /* ---------------- 对外入口 ---------------- */

    showAdd () {
      this.title = '新增台账'
      this.model = this.buildEmptyModel()
      this.association = {}
      this.errors = {}
      this.submitted = false
      this.loading = false
      this.visible = true
      // 预生成编号：后端只预览不落库，用户仍可手工改写
      this.handleGenerateNo()
    },

    showEdit (record) {
      if (!record || !record.id) {
        toast.warning('缺少台账 ID，无法编辑')
        return
      }
      this.title = '编辑台账'
      this.model = this.buildEmptyModel()
      this.association = {
        landId: record.landId || null,
        crzdbh: record.crzdbh || null,
        facilityId: record.facilityId || null,
        ptxmmc: record.ptxmmc || null,
        dkmc: record.dkmc || null,
        xzqh: record.xzqh || null,
        ptsslb: record.ptsslb || null
      }
      this.errors = {}
      this.submitted = false
      this.visible = true
      this.loading = true
      this.loadDetail(record.id)
    },

    /** 拉详情回显：列表行虽然也有 13 个勾选字段，但详情是权威来源（且含后端的 materialCount） */
    loadDetail (id) {
      return queryLedgerById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '台账详情加载失败')
            this.visible = false
            return
          }
          const data = res.result || {}
          const model = this.buildEmptyModel()
          Object.keys(model).forEach((key) => {
            if (data[key] !== undefined && data[key] !== null) {
              model[key] = data[key]
            }
          })
          // 13 个勾选列统一成 0/1（后端是 tinyint，可能回 0/1 或 true/false）
          this.materialList.forEach((item) => {
            model[item.key] = Number(data[item.key]) === 1 ? 1 : 0
          })
          model.id = data.id
          this.model = model
          this.association = {
            landId: data.landId || null,
            crzdbh: data.crzdbh || null,
            facilityId: data.facilityId || null,
            ptxmmc: data.ptxmmc || null,
            dkmc: data.dkmc || null,
            xzqh: data.xzqh || null,
            ptsslb: data.ptsslb || null
          }
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 交互 ---------------- */

    /**
     * 选中配套项目后带出宗地与行政区信息。
     * 只覆盖「非空」的值，这样用户在宗地下拉里清空选择时不会把手工填写的宗地信息一起抹掉。
     */
    handleAssociationChange (value) {
      const next = value || {}
      this.association = next
      if (next.landId) this.model.landId = next.landId
      if (next.crzdbh) this.model.crzdbh = next.crzdbh
      if (next.dkmc) this.model.dkmc = next.dkmc
      if (next.facilityId) this.model.facilityId = next.facilityId
      if (next.ptxmmc) this.model.ptxmmc = next.ptxmmc
      if (next.ptsslb) this.model.ptsslb = next.ptsslb
      if (next.xzqh) this.model.xzqh = next.xzqh
    },

    /** 预览台账编号（后端只生成不落库，保存时才真正占用） */
    handleGenerateNo () {
      const year = yearOf(this.model.acceptanceDate || this.model.completeDate || today())
      this.noLoading = true
      return generateLedgerNo(year)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '台账编号生成失败')
            return
          }
          // Result.OK(msg, data) 把数据放在 result；兼容只放 message 的情况
          this.model.ledgerNo = res.result || res.message || ''
        })
        .catch(() => {
          // 编号生成失败不阻断：用户可以手工填写，或留空由后端保存时生成
        })
        .finally(() => {
          this.noLoading = false
        })
    },

    /** 失焦时做一次编号唯一校验，尽早发现「编号已被占用」 */
    handleNoBlur () {
      if (!this.model.ledgerNo) return
      checkLedgerNo({ ledgerNo: this.model.ledgerNo, id: this.model.id || undefined })
        .then((res) => {
          if (res && !res.success) {
            this.$set(this.errors, 'ledgerNo', res.message || '台账编号已存在')
          } else {
            this.$set(this.errors, 'ledgerNo', '')
          }
        })
        .catch(() => {})
    },

    isChecked (key) {
      return Number(this.model[key]) === 1
    },

    toggleMaterial (key, event) {
      const checked = !!(event && event.target && event.target.checked)
      this.$set(this.model, key, checked ? 1 : 0)
    },

    checkAllMaterials (checked) {
      this.materialList.forEach((item) => {
        this.$set(this.model, item.key, checked ? 1 : 0)
      })
    },

    /* ---------------- 校验与提交 ---------------- */

    /** @returns {boolean} 是否通过 */
    validateForm () {
      const errors = {}
      if (!this.model.roadName || !String(this.model.roadName).trim()) {
        errors.roadName = '请输入道路名称'
      }
      if (!this.model.status) {
        errors.status = '请选择台账状态'
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

    /** 组装提交体：空字符串统一转 null（避免后端的必填 / 唯一校验被空串带偏） */
    buildPayload () {
      const materials = {}
      this.materialList.forEach((item) => {
        materials[item.key] = this.isChecked(item.key) ? 1 : 0
      })
      return Object.assign({}, this.model, materials, {
        id: this.model.id || null,
        ledgerNo: this.model.ledgerNo || null,
        roadName: String(this.model.roadName).trim(),
        xzqh: this.model.xzqh || null,
        gnq: this.model.gnq || null,
        crzdbh: this.model.crzdbh || null,
        dkmc: this.model.dkmc || null,
        ptsslb: this.model.ptsslb || null,
        dldj: this.model.dldj || null,
        ptxmmc: this.model.ptxmmc || this.model.roadName || null,
        acceptanceType: this.model.acceptanceType || null,
        acceptanceNo: this.model.acceptanceNo || null,
        acceptanceDate: this.model.acceptanceDate || null,
        acceptanceOrg: this.model.acceptanceOrg || null,
        acceptanceResult: this.model.acceptanceResult || null,
        completeDate: this.model.completeDate || null,
        handoverUnit: this.model.handoverUnit || null,
        receiveUnit: this.model.receiveUnit || null,
        handoverDate: this.model.handoverDate || null,
        status: this.model.status || '未验收',
        remark: this.model.remark || null
      })
    },

    submit () {
      const payload = this.buildPayload()
      this.saving = true
      const request = payload.id ? editLedger(payload) : addLedger(payload)

      return request
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
          // 请求层已弹过错误提示，这里只负责不让弹窗停在 loading
        })
        .finally(() => {
          this.saving = false
        })
    },

    handleCancel () {
      this.visible = false
    },

    padSeq
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.ledger-form {
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

  &__section-kicker {
    margin-left: 4px;
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
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

  // 三列表单；窄屏逐级降列
  &__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  // 编号输入 + 自动生成按钮同一行
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

  /* ---------------- 13 类资料勾选 ---------------- */
  &__materials {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--screen-space-2) var(--screen-space-3);
  }

  &__material {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 8px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
    background: var(--screen-elevate);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      border-color: var(--screen-accent);
      color: var(--screen-text);
    }

    // 已归集：强调色描边 + 文本提亮，同时 checkbox 本身是勾上的（不靠颜色单独表意）
    &.is-on {
      color: var(--screen-text);
      border-color: var(--screen-accent);
      background: rgba(130, 198, 255, 0.12);
    }
  }

  &__check {
    flex: 0 0 auto;
    width: 14px;
    height: 14px;
    margin: 0;
    accent-color: var(--screen-accent);
    cursor: pointer;
  }

  &__material-seq {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  &__material-label {
    min-width: 0;
    .screen-ellipsis();
  }

  &__material-actions {
    display: flex;
    gap: var(--screen-space-2);
  }
}

@media (max-width: 1500px) {
  .ledger-form__grid,
  .ledger-form__materials {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1000px) {
  .ledger-form__grid,
  .ledger-form__materials {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
