<template>
  <!--
    CompletionFormModal 竣工验收历史档案 · 新增 / 编辑
    ---------------------------------------------------------------
    三段式表单，顺序与用户整理一份历史档案的思路一致：
      1. 项目信息 —— 这是哪个历史项目（档案编号 + 名称 + 原编号 + 区划 + 类型 + 关联宗地）
      2. 建设与竣工信息 —— 参建单位、三个关键日期、投资额、保管期限
      3. 数字化信息 —— 数字化状态（必填）、加工单位与完成日期、DPI、页数与文件数、备注

    公开方法（父组件通过 $refs 调用）：
      showAdd()        打开新增
      showEdit(record) 打开编辑（按 id 拉详情回显）
    事件：
      ok               保存成功后抛出，父组件据此刷新列表与统计

    ★ 关联配套项目（landId / facilityId / ptxmmc）在本表单里**只读回显、不改动**：
      历史档案多数压根没有对应宗地或配套项目（存量项目先于本系统），因此这里不做
      项目选择器；但后端的编辑走「显式 SQL 整行覆盖」，如果提交时不带这三列，
      已建立的关联会被清成 NULL。所以 loadDetail 与 buildPayload 都把原值原样带回去。
      需要改关联时请到 admin-client 的竣工验收历史档案页（那边有配套项目选择器）。
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="1160"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    :body-max-height="'calc(100vh - 200px)'"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="completion-form">
      <!-- ================= 1. 项目信息 ================= -->
      <section class="completion-form__section">
        <h4 class="completion-form__section-title">
          <screen-icon name="folder-open" :size="14" />
          项目信息
        </h4>

        <div class="completion-form__grid">
          <screen-field
            label="档案编号"
            :error="errors.archiveNo"
            html-for="cf-archiveNo"
            tip="留空时由后端按年度自动生成"
          >
            <div class="completion-form__inline">
              <screen-input
                id="cf-archiveNo"
                v-model="model.archiveNo"
                :maxlength="64"
                :invalid="!!errors.archiveNo"
                placeholder="例如 JG-2026-0001"
                @blur="handleNoBlur"
              />
              <screen-button :loading="noLoading" @click="handleGenerateNo">自动生成</screen-button>
            </div>
          </screen-field>

          <screen-field label="历史项目名称" required :error="errors.projectName" html-for="cf-projectName">
            <screen-input
              id="cf-projectName"
              v-model="model.projectName"
              :maxlength="255"
              :invalid="!!errors.projectName"
              placeholder="例如：XX 路道路工程"
            />
          </screen-field>

          <screen-field label="项目原编号" html-for="cf-projectCode" tip="历史项目自带的编号，可空">
            <screen-input
              id="cf-projectCode"
              v-model="model.projectCode"
              :maxlength="128"
              placeholder="例如 2013-045"
            />
          </screen-field>

          <screen-field label="行政区划">
            <screen-select
              v-model="model.xzqh"
              :options="xzqhOptions"
              placeholder="请选择区划"
              aria-label="行政区划"
            />
          </screen-field>

          <screen-field label="项目类型">
            <screen-select
              v-model="model.projectType"
              :options="projectTypeOptions"
              placeholder="请选择类型"
              aria-label="项目类型"
            />
          </screen-field>

          <screen-field label="配套设施类别">
            <screen-select
              v-model="model.ptsslb"
              :options="ptsslbOptions"
              placeholder="可不填"
              aria-label="配套设施类别"
            />
          </screen-field>

          <screen-field label="出让宗地编号" html-for="cf-crzdbh">
            <screen-input
              id="cf-crzdbh"
              v-model="model.crzdbh"
              :maxlength="128"
              placeholder="用于兜底匹配扫描件"
            />
          </screen-field>

          <screen-field label="地块名称" html-for="cf-dkmc">
            <screen-input id="cf-dkmc" v-model="model.dkmc" :maxlength="255" placeholder="可不填" />
          </screen-field>

          <screen-field label="关联配套项目" tip="由档案维护/历史档案页建立，本页只读">
            <span class="completion-form__readonly">{{ model.ptxmmc || '未关联（历史项目常见）' }}</span>
          </screen-field>
        </div>
      </section>

      <!-- ================= 2. 建设与竣工信息 ================= -->
      <section class="completion-form__section">
        <h4 class="completion-form__section-title">
          <screen-icon name="calendar" :size="14" />
          建设与竣工信息
        </h4>

        <div class="completion-form__grid">
          <screen-field label="建设单位" html-for="cf-buildUnit">
            <screen-input id="cf-buildUnit" v-model="model.buildUnit" :maxlength="255" placeholder="可不填" />
          </screen-field>

          <screen-field label="施工单位" html-for="cf-constructUnit">
            <screen-input id="cf-constructUnit" v-model="model.constructUnit" :maxlength="255" placeholder="可不填" />
          </screen-field>

          <screen-field label="设计单位" html-for="cf-designUnit">
            <screen-input id="cf-designUnit" v-model="model.designUnit" :maxlength="255" placeholder="可不填" />
          </screen-field>

          <screen-field label="监理单位" html-for="cf-superviseUnit">
            <screen-input id="cf-superviseUnit" v-model="model.superviseUnit" :maxlength="255" placeholder="可不填" />
          </screen-field>

          <screen-field label="开工日期" :error="errors.startDate">
            <screen-date-input id="cf-startDate" v-model="model.startDate" :invalid="!!errors.startDate" />
          </screen-field>

          <screen-field label="竣工日期" :error="errors.completeDate">
            <screen-date-input id="cf-completeDate" v-model="model.completeDate" :invalid="!!errors.completeDate" />
          </screen-field>

          <screen-field label="验收日期" :error="errors.acceptanceDate">
            <screen-date-input id="cf-acceptanceDate" v-model="model.acceptanceDate" :invalid="!!errors.acceptanceDate" />
          </screen-field>

          <screen-field label="投资额(万元)" :error="errors.investAmount" html-for="cf-investAmount">
            <screen-input
              id="cf-investAmount"
              v-model="model.investAmount"
              type="number"
              :min="0"
              :invalid="!!errors.investAmount"
              placeholder="例如 1250.5"
            />
          </screen-field>

          <screen-field label="保管期限">
            <screen-select
              v-model="model.retention"
              :options="retentionOptions"
              placeholder="可不填"
              aria-label="保管期限"
            />
          </screen-field>
        </div>
      </section>

      <!-- ================= 3. 数字化信息 ================= -->
      <section class="completion-form__section">
        <h4 class="completion-form__section-title">
          <screen-icon name="layers" :size="14" />
          数字化信息
          <span class="completion-form__section-required" aria-hidden="true">*</span>
        </h4>
        <p class="completion-form__section-hint">
          扫描件本体不在本页上传：请先到「档案维护」上传，再到详情页点「关联档案」建立指针。
        </p>

        <div class="completion-form__grid">
          <screen-field label="数字化状态" required :error="errors.digitizeStatus">
            <screen-radio-group
              v-model="model.digitizeStatus"
              :options="statusOptions"
              aria-label="数字化状态"
            />
          </screen-field>

          <screen-field label="加工单位" html-for="cf-digitizeOrg">
            <screen-input
              id="cf-digitizeOrg"
              v-model="model.digitizeOrg"
              :maxlength="255"
              placeholder="数字化加工单位"
            />
          </screen-field>

          <screen-field label="完成日期" :error="errors.digitizeDate">
            <screen-date-input id="cf-digitizeDate" v-model="model.digitizeDate" :invalid="!!errors.digitizeDate" />
          </screen-field>

          <screen-field label="扫描分辨率" :error="errors.scanDpi" html-for="cf-scanDpi" :tip="dpiTip">
            <screen-input
              id="cf-scanDpi"
              v-model="model.scanDpi"
              type="number"
              :min="0"
              :max="2400"
              :invalid="!!errors.scanDpi"
              placeholder="DPI"
            />
          </screen-field>

          <screen-field label="总页数" :error="errors.pageCount" html-for="cf-pageCount">
            <screen-input
              id="cf-pageCount"
              v-model="model.pageCount"
              type="number"
              :min="0"
              :invalid="!!errors.pageCount"
              placeholder="扫描总页数"
            />
          </screen-field>

          <screen-field label="文件数" :error="errors.fileCount" html-for="cf-fileCount">
            <screen-input
              id="cf-fileCount"
              v-model="model.fileCount"
              type="number"
              :min="0"
              :invalid="!!errors.fileCount"
              placeholder="扫描文件个数"
            />
          </screen-field>

          <screen-field class="completion-form__span-all" label="备注" html-for="cf-remark">
            <screen-input
              id="cf-remark"
              v-model="model.remark"
              type="textarea"
              :rows="2"
              :maxlength="1000"
              placeholder="补充说明，最多 1000 字"
            />
          </screen-field>
        </div>

        <!-- 非阻断提示：状态已到「已数字化」却没有页数，多半是漏填 -->
        <p v-if="digitizedWithoutPages" class="completion-form__notice">
          <screen-icon name="info" :size="13" />
          状态已是「已数字化」，但总页数为空 —— 建议补上，否则统计里的总页数会少算这份档案。
        </p>
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
import { queryXzqhOptions } from '@/api/land/landData'
import { addCompletion, editCompletion, generateArchiveNo, checkArchiveNo, queryCompletionById } from '@/api/land/completion'
import {
  DIGITIZE_DEFAULT,
  DIGITIZE_STATUSES,
  PROJECT_TYPES,
  PTSSLB_OPTIONS,
  RETENTIONS,
  SCAN_DPI_OPTIONS
} from './constants'

/** 字符串数组 → ScreenSelect 需要的 [{ value, label }] */
function toOptions (list) {
  return (list || []).map((item) => ({ value: item, label: item }))
}

/** 空字符串 → null（后端把 '' 当已填写，会与"清空"语义冲突） */
function emptyToNull (value) {
  return value === '' || value === undefined ? null : value
}

/** 数值字段：空 → null，非数字 → 原样（交给校验拦下），其余转 Number */
function toNumberOrNull (value) {
  if (value === '' || value === null || value === undefined) return null
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

/** 取 yyyy-MM-dd 的年份；解析不出来时返回 null */
function yearOf (dateText) {
  if (!dateText) return null
  const year = Number(String(dateText).slice(0, 4))
  return Number.isFinite(year) && year > 1900 && year < 3000 ? year : null
}

export default {
  name: 'CompletionFormModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenRadioGroup,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon
  },
  data () {
    return {
      visible: false,
      title: '新增历史档案',
      saving: false,
      noLoading: false,
      /** 是否已经点过一次保存：只有点过之后才显示校验错误，避免一打开就满屏飘红 */
      submitted: false,
      model: this.buildEmptyModel(),
      errors: {},
      xzqhOptions: [],
      projectTypeOptions: toOptions(PROJECT_TYPES),
      retentionOptions: toOptions(RETENTIONS),
      ptsslbOptions: toOptions(PTSSLB_OPTIONS),
      statusOptions: toOptions(DIGITIZE_STATUSES)
    }
  },
  computed: {
    /** DPI 提示跟随候选档位常量，避免两处各写一遍后不一致 */
    dpiTip () {
      return `标准档位 ${SCAN_DPI_OPTIONS.join(' / ')}，也可填其它整数`
    },
    digitizedWithoutPages () {
      return this.model.digitizeStatus === '已数字化' &&
        (this.model.pageCount === '' || this.model.pageCount === null || this.model.pageCount === undefined)
    }
  },
  created () {
    this.loadXzqh()
  },
  methods: {
    /** 新档案初始值：与后端 fillDefaults / 建表默认值保持一致 */
    buildEmptyModel () {
      return {
        id: null,
        archiveNo: '',
        projectName: '',
        projectCode: '',
        xzqh: '',
        projectType: '',
        ptsslb: '',
        crzdbh: '',
        dkmc: '',
        // 关联项目三列：不由本表单修改，但必须原样回传（见文件头注释）
        landId: null,
        facilityId: null,
        ptxmmc: '',
        buildUnit: '',
        constructUnit: '',
        designUnit: '',
        superviseUnit: '',
        startDate: '',
        completeDate: '',
        acceptanceDate: '',
        investAmount: '',
        retention: '',
        digitizeStatus: DIGITIZE_DEFAULT,
        digitizeDate: '',
        digitizeOrg: '',
        pageCount: '',
        fileCount: '',
        scanDpi: '',
        remark: ''
      }
    },

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

    /* ---------------- 对外入口 ---------------- */

    showAdd () {
      this.title = '新增历史档案'
      this.model = this.buildEmptyModel()
      this.errors = {}
      this.submitted = false
      this.visible = true
      this.$nextTick(() => {
        this.handleGenerateNo()
      })
    },

    showEdit (record) {
      if (!record || !record.id) {
        toast.warning('缺少档案 ID，无法编辑')
        return
      }
      this.title = '编辑历史档案'
      this.model = this.buildEmptyModel()
      this.errors = {}
      this.submitted = false
      this.visible = true
      this.loadDetail(record.id)
    },

    /** 拉详情回显；空值统一转成 '' 以便控件正常显示 */
    loadDetail (id) {
      queryCompletionById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案详情加载失败')
            this.visible = false
            return
          }
          const data = res.result || {}
          this.model = {
            id: data.id,
            archiveNo: data.archiveNo || '',
            projectName: data.projectName || '',
            projectCode: data.projectCode || '',
            xzqh: data.xzqh || '',
            projectType: data.projectType || '',
            ptsslb: data.ptsslb || '',
            crzdbh: data.crzdbh || '',
            dkmc: data.dkmc || '',
            landId: data.landId || null,
            facilityId: data.facilityId || null,
            ptxmmc: data.ptxmmc || '',
            buildUnit: data.buildUnit || '',
            constructUnit: data.constructUnit || '',
            designUnit: data.designUnit || '',
            superviseUnit: data.superviseUnit || '',
            startDate: data.startDate || '',
            completeDate: data.completeDate || '',
            acceptanceDate: data.acceptanceDate || '',
            investAmount: data.investAmount === null || data.investAmount === undefined ? '' : data.investAmount,
            retention: data.retention || '',
            digitizeStatus: data.digitizeStatus || DIGITIZE_DEFAULT,
            digitizeDate: data.digitizeDate || '',
            digitizeOrg: data.digitizeOrg || '',
            pageCount: data.pageCount === null || data.pageCount === undefined ? '' : data.pageCount,
            fileCount: data.fileCount === null || data.fileCount === undefined ? '' : data.fileCount,
            scanDpi: data.scanDpi === null || data.scanDpi === undefined ? '' : data.scanDpi,
            remark: data.remark || ''
          }
        })
        .catch(() => {
          // 请求层已弹过错误提示，这里只把弹窗收起来，避免停在空白表单上
          this.visible = false
        })
    },

    /* ---------------- 交互 ---------------- */

    /** 预览档案编号（后端只生成不落库，保存时才真正占用） */
    handleGenerateNo () {
      // 年度优先按竣工日期取（历史档案的"档案年度"通常就是竣工年）
      const year = yearOf(this.model.completeDate) || new Date().getFullYear()
      this.noLoading = true
      generateArchiveNo(year)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案编号生成失败')
            return
          }
          // Result.OK(msg, data) 把数据放在 result；兼容只放 message 的情况
          this.model.archiveNo = res.result || res.message || ''
        })
        .finally(() => {
          this.noLoading = false
        })
    },

    /** 失焦即校验编号唯一性，避免保存时才被后端拒绝 */
    handleNoBlur () {
      if (!this.model.archiveNo) return
      checkArchiveNo({ archiveNo: this.model.archiveNo, id: this.model.id || undefined })
        .then((res) => {
          if (res && !res.success) {
            this.errors = Object.assign({}, this.errors, { archiveNo: res.message || '档案编号已存在' })
          } else {
            const next = Object.assign({}, this.errors)
            delete next.archiveNo
            this.errors = next
          }
        })
        .catch(() => {
          // 校验接口不可用时不阻断录入，最终仍由后端唯一键把关
        })
    },

    /* ---------------- 校验与提交 ---------------- */

    /** @returns {boolean} 是否通过 */
    validateForm () {
      const errors = {}
      if (!this.model.projectName || !String(this.model.projectName).trim()) {
        errors.projectName = '请输入历史项目名称'
      }
      if (!this.model.digitizeStatus) errors.digitizeStatus = '请选择数字化状态'

      // 数值字段：填了就必须是数字且不小于 0（空值合法 —— 历史档案常缺项）
      const numeric = [
        ['investAmount', '投资额'],
        ['pageCount', '总页数'],
        ['fileCount', '文件数'],
        ['scanDpi', '扫描分辨率']
      ]
      numeric.forEach(([key, label]) => {
        const value = this.model[key]
        if (value === '' || value === null || value === undefined) return
        const num = Number(value)
        if (!Number.isFinite(num)) {
          errors[key] = `${label}必须是数字`
        } else if (num < 0) {
          errors[key] = `${label}不能为负数`
        }
      })

      // 日期先后只做「明显颠倒」的提示，不做硬拦截：历史档案的日期本身可能不规整，
      // 拦住会让一份录入不进去的数据无法保存（与台账模块同一取舍）。
      const start = this.model.startDate
      const complete = this.model.completeDate
      if (start && complete && start > complete) {
        errors.completeDate = '竣工日期早于开工日期，请核对'
      }

      this.errors = errors
      return Object.keys(errors).length === 0
    },

    buildPayload () {
      return Object.assign({}, this.model, {
        archiveNo: emptyToNull(this.model.archiveNo),
        investAmount: toNumberOrNull(this.model.investAmount),
        pageCount: toNumberOrNull(this.model.pageCount),
        fileCount: toNumberOrNull(this.model.fileCount),
        scanDpi: toNumberOrNull(this.model.scanDpi),
        startDate: emptyToNull(this.model.startDate),
        completeDate: emptyToNull(this.model.completeDate),
        acceptanceDate: emptyToNull(this.model.acceptanceDate),
        digitizeDate: emptyToNull(this.model.digitizeDate),
        // ★ 关联项目三列原样回传：不这样会被后端的整行覆盖 SQL 清成 NULL
        landId: this.model.landId || null,
        facilityId: this.model.facilityId || null,
        ptxmmc: emptyToNull(this.model.ptxmmc)
      })
    },

    handleOk () {
      this.submitted = true
      if (!this.validateForm()) {
        toast.warning('还有必填项未完成，请检查标红的字段')
        return
      }
      const payload = this.buildPayload()
      this.saving = true
      const request = payload.id ? editCompletion(payload) : addCompletion(payload)

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
          // 请求层已经弹过错误提示，这里只负责不把弹窗留在 loading 状态
        })
        .finally(() => {
          this.saving = false
        })
    },

    handleCancel () {
      this.visible = false
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.completion-form {
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

  &__section-required {
    line-height: 1;
    color: var(--screen-danger);
  }

  &__section-hint {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-text-mute);
  }

  // 标签列 + 控件列，三列排布；窄屏逐级降列
  &__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  // 备注占满整行（★ 跨列写成两个长属性：Less 会把 `1 / -1` 当成除法，见 README 第 6 节第 1 条）
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

  // 只读回显（关联配套项目）
  &__readonly {
    display: block;
    font-size: var(--screen-font-sm);
    line-height: 28px;
    color: var(--screen-text-sub);
    .screen-ellipsis();
  }

  &__notice {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    color: var(--screen-warning);
  }
}

@media (max-width: 1500px) {
  .completion-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1000px) {
  .completion-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
