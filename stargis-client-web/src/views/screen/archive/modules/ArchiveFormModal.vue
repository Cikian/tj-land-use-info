<template>
  <!--
    ArchiveFormModal 档案新增 / 编辑弹窗
    --------------------------------
    三段式表单，顺序与用户的思考顺序一致：
      1. 关联项目 —— 这条档案属于哪个出让宗地下的哪个配套项目（必填，且决定后面的归档年度）
      2. 档案基本信息 —— 档案号、名称、类型、密级、期限、责任部门/人、年度、日期、状态、备注
      3. 档案文件 —— 上传卷内文件，每个文件都要指定一个末级档案类别

    公开方法（父组件通过 $refs 调用）：
      showAdd()        打开新增
      showEdit(record) 打开编辑（会按 id 拉详情，包含卷内文件）
    事件：
      ok               保存成功后抛出，父组件据此刷新列表

    为什么要自己写校验而不是用表单组件库：
      大屏组件库不依赖 antd，而档案的校验规则里有一条跨字段的规则
      （「关联项目必填」+「文件必须有类别」），用统一的 errors 对象集中表达
      比分散在各控件上更清晰，也方便把错误文案同时挂到控件和错误汇总里。
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
    <div class="archive-form">
      <!-- ================= 1. 关联项目 ================= -->
      <section class="archive-form__section">
        <h4 class="archive-form__section-title">
          <screen-icon name="link" :size="14" />
          关联项目
          <span class="archive-form__section-required" aria-hidden="true">*</span>
        </h4>
        <p class="archive-form__section-hint">
          档案必须挂在已录入的配套项目下。先选出让宗地，再选该宗地下的配套项目；
          配套项目下拉里没有想要的项时，请先到「数据管理」录入。
        </p>

        <project-picker
          v-model="association"
          :required="true"
          :show-invalid="submitted && !association.facilityId"
          @change="handleAssociationChange"
        />
        <p v-if="submitted && !association.facilityId" class="archive-form__error" role="alert">
          请选择配套项目后再保存
        </p>
      </section>

      <!-- ================= 2. 档案基本信息 ================= -->
      <section class="archive-form__section">
        <h4 class="archive-form__section-title">
          <screen-icon name="file-text" :size="14" />
          档案基本信息
        </h4>

        <div class="archive-form__grid">
          <screen-field label="档案号" :error="errors.archiveNo" html-for="af-archiveNo" tip="留空时由后端按年度自动生成">
            <div class="archive-form__inline">
              <screen-input
                id="af-archiveNo"
                v-model="model.archiveNo"
                :maxlength="64"
                :invalid="!!errors.archiveNo"
                placeholder="例如 DA-2026-0001"
              />
              <screen-button :loading="noLoading" @click="handleGenerateNo">自动生成</screen-button>
            </div>
          </screen-field>

          <screen-field label="档案名称" required :error="errors.archiveName" html-for="af-archiveName">
            <screen-input
              id="af-archiveName"
              v-model="model.archiveName"
              :maxlength="255"
              :invalid="!!errors.archiveName"
              placeholder="案卷题名，例如：XX 项目建议书批复"
            />
          </screen-field>

          <screen-field label="档案类型" required>
            <screen-radio-group
              v-model="model.archiveType"
              :options="ARCHIVE_TYPES"
              aria-label="档案类型"
            />
          </screen-field>

          <screen-field label="密级" required :error="errors.secretLevel">
            <screen-select
              v-model="model.secretLevel"
              :options="secretOptions"
              :invalid="!!errors.secretLevel"
              placeholder="请选择密级"
              aria-label="密级"
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

          <screen-field label="档案年度" :error="errors.archiveYear" html-for="af-archiveYear">
            <screen-input
              id="af-archiveYear"
              v-model="model.archiveYear"
              type="number"
              :min="1949"
              :max="2999"
              :invalid="!!errors.archiveYear"
              placeholder="例如 2026"
            />
          </screen-field>

          <screen-field label="责任部门">
            <screen-select
              v-model="model.responsibleDept"
              :options="deptOptions"
              placeholder="请选择责任部门"
              aria-label="责任部门"
            />
          </screen-field>

          <screen-field label="配套负责人" html-for="af-responsibleUser">
            <screen-input
              id="af-responsibleUser"
              v-model="model.responsibleUser"
              :maxlength="64"
              placeholder="姓名"
            />
          </screen-field>

          <screen-field label="归档日期" :error="errors.archiveDate">
            <screen-date-input
              id="af-archiveDate"
              v-model="model.archiveDate"
              :invalid="!!errors.archiveDate"
            />
          </screen-field>

          <screen-field label="档案状态" required>
            <screen-select
              v-model="model.status"
              :options="statusOptions"
              placeholder="请选择状态"
              aria-label="档案状态"
            />
          </screen-field>

          <screen-field class="archive-form__span-2" label="备注" html-for="af-remark">
            <screen-input
              id="af-remark"
              v-model="model.remark"
              type="textarea"
              :rows="2"
              :maxlength="1000"
              placeholder="补充说明，最多 1000 字"
            />
          </screen-field>
        </div>
      </section>

      <!-- ================= 3. 档案文件 ================= -->
      <section class="archive-form__section">
        <h4 class="archive-form__section-title">
          <screen-icon name="paperclip" :size="14" />
          档案文件
          <span class="archive-form__section-required" aria-hidden="true">*</span>
        </h4>
        <p class="archive-form__section-hint">
          上传的文件会成为这条档案的卷内文件。每个文件都必须选择一个<strong>末级</strong>档案类别，
          未选类别的文件无法保存。
        </p>

        <archive-file-table ref="fileTable" v-model="files" :biz-path="bizPath" />
        <p v-if="fileError" class="archive-form__error" role="alert">{{ fileError }}</p>
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
  ScreenIcon,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import ProjectPicker from './ProjectPicker.vue'
import ArchiveFileTable from './ArchiveFileTable.vue'
import { queryArchiveById, addArchive, editArchive, generateArchiveNo } from '@/api/land/archive'
import {
  ARCHIVE_TYPES,
  RETENTIONS,
  DEPTS,
  ARCHIVE_STATUSES,
  SECRET_LEVELS,
  toOptions,
  buildBizPath,
  today,
  yearOf,
  formatDate,
} from '../constants'

export default {
  name: 'ArchiveFormModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenRadioGroup,
    ScreenDateInput,
    ScreenButton,
    ScreenIcon,
    ProjectPicker,
    ArchiveFileTable,
  },
  data () {
    return {
      visible: false,
      title: '新增档案',
      saving: false,
      noLoading: false,
      loading: false,
      /** 是否已经点过一次保存：只有点过之后才把校验错误显示出来，避免一打开就满屏飘红 */
      submitted: false,
      model: this.buildEmptyModel(),
      association: {},
      files: [],
      errors: {},
      fileError: '',
      ARCHIVE_TYPES,
    }
  },
  computed: {
    bizPath () {
      return buildBizPath()
    },
    secretOptions () {
      return toOptions(SECRET_LEVELS)
    },
    retentionOptions () {
      return toOptions(RETENTIONS)
    },
    deptOptions () {
      return toOptions(DEPTS)
    },
    statusOptions () {
      return toOptions(ARCHIVE_STATUSES)
    },
  },
  methods: {
    /** 新档案的初始值：与后端 fillDefaults 保持一致，避免保存后字段被后端改掉 */
    buildEmptyModel () {
      return {
        id: null,
        archiveNo: '',
        archiveName: '',
        archiveType: 'electronic',
        secretLevel: '一般',
        retention: '',
        archiveYear: new Date().getFullYear(),
        responsibleDept: '',
        responsibleUser: '',
        archiveDate: today(),
        status: '未归档',
        remark: '',
      }
    },

    /* ---------------- 对外入口 ---------------- */

    showAdd () {
      this.title = '新增档案'
      this.model = this.buildEmptyModel()
      this.model.responsibleUser = this.currentRealname()
      this.association = {}
      this.files = []
      this.errors = {}
      this.fileError = ''
      this.submitted = false
      this.loading = false
      this.visible = true
      this.resetUploader()
    },

    showEdit (record) {
      if (!record || !record.id) {
        toast.warning('缺少档案 ID，无法编辑')
        return
      }
      this.title = '编辑档案'
      this.model = this.buildEmptyModel()
      this.association = {}
      this.files = []
      this.errors = {}
      this.fileError = ''
      this.submitted = false
      this.visible = true
      this.loading = true
      this.resetUploader()
      this.loadDetail(record.id)
    },

    /**
     * 清掉上一次打开时残留的上传队列与进度。
     *
     * 为什么要在几个 tick 里反复尝试：
     * 弹窗内容插槽由 ScreenModal 在 visible 为真时才真正挂载，而 uploader 又藏在
     * ArchiveFileTable 的 $refs 里 —— 依赖「第几个 tick 一定能拿到」很脆弱
     * （父更新 → 子更新 → 子子更新）。这里改成拿到就清、拿不到就再等一帧，最多重试几次，
     * 避免出现「第二次打开弹窗还显示上一次上传进度」。
     */
    resetUploader () {
      const tryClear = (attempt) => {
        this.$nextTick(() => {
          const table = this.$refs.fileTable
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

    /**
     * 拉详情并回显。
     * 关联项目字段后端只存 id + 冗余名称，这里原样带进 ProjectPicker，
     * 由它负责把当前值补进下拉选项（否则下拉会显示空白）。
     */
    loadDetail (id) {
      queryArchiveById(id)
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
            archiveName: data.archiveName || '',
            archiveType: data.archiveType || 'electronic',
            secretLevel: data.secretLevel || '一般',
            retention: data.retention || '',
            archiveYear: data.archiveYear || yearOf(data.archiveDate),
            responsibleDept: data.responsibleDept || '',
            responsibleUser: data.responsibleUser || '',
            archiveDate: data.archiveDate || today(),
            status: data.status || '未归档',
            remark: data.remark || '',
          }
          this.association = {
            landId: data.landId || null,
            crzdbh: data.crzdbh || null,
            facilityId: data.facilityId || null,
            ptxmmc: data.ptxmmc || null,
            dkmc: data.dkmc || null,
            xzqh: data.xzqh || null,
            ptsslb: data.ptsslb || null,
          }
          this.files = (data.files || []).map((item) => Object.assign({}, item))
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 交互 ---------------- */

    /** 关联项目变化：顺手把归档年度对齐到归档日期所在年份 */
    handleAssociationChange (value) {
      this.association = value || {}
      if (this.model.archiveDate) {
        this.model.archiveYear = yearOf(this.model.archiveDate)
      }
    },

    /** 预览档案号（后端只生成不落库，保存时才真正占用） */
    handleGenerateNo () {
      const year = this.model.archiveDate ? yearOf(this.model.archiveDate) : new Date().getFullYear()
      this.noLoading = true
      generateArchiveNo(year)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '档案号生成失败')
            return
          }
          // Result.OK(msg, data) 会把数据放在 result；这里兼容只放 message 的情况
          this.model.archiveNo = res.result || res.message || ''
        })
        .finally(() => {
          this.noLoading = false
        })
    },

    /* ---------------- 校验与提交 ---------------- */

    /** @returns {boolean} 是否通过 */
    validateForm () {
      const errors = {}
      if (!this.model.archiveName || !String(this.model.archiveName).trim()) {
        errors.archiveName = '请输入档案名称'
      }
      if (!this.model.secretLevel) errors.secretLevel = '请选择密级'
      if (!this.model.archiveType) errors.archiveType = '请选择档案类型'
      if (!this.model.status) errors.status = '请选择档案状态'

      const year = Number(this.model.archiveYear)
      if (this.model.archiveYear !== '' && this.model.archiveYear !== null && Number.isFinite(year)) {
        if (year < 1949 || year > 2999) errors.archiveYear = '档案年度需在 1949 - 2999 之间'
      } else if (this.model.archiveYear !== '' && this.model.archiveYear !== null) {
        errors.archiveYear = '档案年度必须是数字'
      }

      this.errors = errors

      const fileError = this.$refs.fileTable ? this.$refs.fileTable.validate() : ''
      this.fileError = fileError

      return Object.keys(errors).length === 0 && !fileError && !!this.association.facilityId
    },

    handleOk () {
      this.submitted = true
      if (!this.validateForm()) {
        toast.warning('还有必填项未完成，请检查标红的字段')
        return
      }
      this.submit()
    },

    /** 组装提交体：档案主体 + 关联项目冗余字段 + 卷内文件数组 */
    buildPayload () {
      const association = this.association || {}
      const year = Number(this.model.archiveYear)
      return Object.assign({}, this.model, {
        landId: association.landId || null,
        crzdbh: association.crzdbh || null,
        facilityId: association.facilityId || null,
        ptxmmc: association.ptxmmc || null,
        dkmc: association.dkmc || null,
        xzqh: association.xzqh || null,
        ptsslb: association.ptsslb || null,
        archiveNo: this.model.archiveNo || null,
        archiveYear: Number.isFinite(year) ? year : null,
        archiveDate: this.model.archiveDate || null,
        // 空字符串会让后端的必填校验失败，统一转成 null
        retention: this.model.retention || null,
        responsibleDept: this.model.responsibleDept || null,
        files: (this.files || []).map((item, index) => ({
          id: item.id || null,
          categoryId: item.categoryId || null,
          categoryName: item.categoryName || null,
          fileName: item.fileName,
          fileTitle: item.fileTitle || item.fileName,
          fileExt: item.fileExt || null,
          fileSize: item.fileSize || 0,
          fileMd5: item.fileMd5 || null,
          storeType: item.storeType || 'local',
          storePath: item.storePath,
          status: item.status || '已归档',
          sortNo: item.sortNo || index + 1,
        })),
      })
    },

    submit () {
      const payload = this.buildPayload()
      this.saving = true
      const request = payload.id ? editArchive(payload) : addArchive(payload)

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
    },

    /* ---------------- 工具 ---------------- */

    /** 预填负责人：取当前登录用户的真实姓名，取不到就用用户名，再取不到留空 */
    currentRealname () {
      const info = this.$store && this.$store.getters ? this.$store.getters.userInfo : null
      if (!info) return ''
      return info.realname || info.username || ''
    },

    formatDate,
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.archive-form {
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

  &__section-required {
    line-height: 1;
    color: var(--screen-danger);
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

  // 标签列 96px + 控件列，三列排布；窄屏逐级降列
  &__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  // 备注占满整行
  &__span-2 {
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
  .archive-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1000px) {
  .archive-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
