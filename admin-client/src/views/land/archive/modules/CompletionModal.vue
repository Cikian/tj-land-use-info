<template>
  <a-modal
    :title="title"
    :width="1080"
    :visible="visible"
    :confirmLoading="submitting"
    :maskClosable="false"
    okText="保存"
    cancelText="取消"
    @ok="handleSubmit"
    @cancel="handleClose">
    <a-spin :spinning="loading">
      <a-form-model ref="form" :model="model" :rules="rules" :label-col="labelCol" :wrapper-col="wrapperCol">
        <!-- ============ 一、档案标识 ============ -->
        <div class="completion-form__section">
          <div class="completion-form__section-title">档案标识</div>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-model-item label="档案编号" prop="archiveNo">
                <a-input v-model="model.archiveNo" placeholder="留空自动生成 JG-{年}-{4位}" @blur="handleArchiveNoBlur">
                  <a-button slot="addonAfter" type="link" size="small" @click="handleGenerateNo">自动生成</a-button>
                </a-input>
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="数字化状态" prop="digitizeStatus">
                <a-radio-group v-model="model.digitizeStatus" buttonStyle="solid">
                  <a-radio-button v-for="item in statuses" :key="item" :value="item">{{ item }}</a-radio-button>
                </a-radio-group>
              </a-form-model-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 二、历史项目信息 ============ -->
        <div class="completion-form__section">
          <div class="completion-form__section-title">
            历史项目信息
            <span class="completion-form__hint">
              存量历史项目不一定有对应的宗地/配套项目，关联信息全部可以留空
            </span>
          </div>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-model-item label="历史项目名称" prop="projectName">
                <a-input v-model="model.projectName" placeholder="必填" />
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="项目编号" prop="projectCode">
                <a-input v-model="model.projectCode" placeholder="历史项目自带的原编号（选填）" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="关联配套项目" prop="facilityId">
                <a-select
                  show-search
                  allowClear
                  placeholder="输入配套项目名称搜索（选填，便于关联扫描件）"
                  :filterOption="false"
                  :defaultActiveFirstOption="false"
                  :notFoundContent="facilityLoading ? '搜索中…' : '未找到配套项目'"
                  @search="handleFacilitySearch"
                  @change="handleFacilityChange">
                  <a-select-option v-for="item in facilityOptions" :key="item.id" :value="item.id">
                    {{ item.ptxmmc }}<span class="completion-form__option-extra">{{ item.crzdbh ? '（' + item.crzdbh + '）' : '' }}</span>
                  </a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="出让宗地编号" prop="crzdbh">
                <a-input v-model="model.crzdbh" placeholder="选填；关联扫描件的兜底查找键" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="地块名称" prop="dkmc">
                <a-input v-model="model.dkmc" placeholder="选填" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="配套设施类别" prop="ptsslb">
                <a-select v-model="model.ptsslb" allowClear placeholder="选填" style="width: 100%">
                  <a-select-option v-for="item in ptsslbOptions" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="行政区划" prop="xzqh">
                <a-select v-model="model.xzqh" allowClear show-search placeholder="16 区" style="width: 100%">
                  <a-select-option v-for="item in xzqhOptions" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="项目类型" prop="projectType">
                <a-select v-model="model.projectType" allowClear placeholder="请选择" style="width: 100%">
                  <a-select-option v-for="item in projectTypes" :key="item.value" :value="item.value">
                    {{ item.text }}
                  </a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="建设单位" prop="buildUnit">
                <a-input v-model="model.buildUnit" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="施工单位" prop="constructUnit">
                <a-input v-model="model.constructUnit" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="设计单位" prop="designUnit">
                <a-input v-model="model.designUnit" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="监理单位" prop="superviseUnit">
                <a-input v-model="model.superviseUnit" allowClear />
              </a-form-model-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 三、关键日期与投资保管 ============ -->
        <div class="completion-form__section">
          <div class="completion-form__section-title">关键日期与投资</div>
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-model-item label="开工日期" prop="startDate">
                <a-date-picker v-model="model.startDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="竣工日期" prop="completeDate">
                <a-date-picker v-model="model.completeDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="验收日期" prop="acceptanceDate">
                <a-date-picker v-model="model.acceptanceDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="投资额(万元)" prop="investAmount">
                <a-input-number v-model="model.investAmount" :min="0" :precision="2" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="保管期限" prop="retention">
                <a-select v-model="model.retention" allowClear placeholder="请选择" style="width: 100%">
                  <a-select-option v-for="item in retentions" :key="item.value" :value="item.value">
                    {{ item.text }}
                  </a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 四、★数字化信息 ============ -->
        <div class="completion-form__section">
          <div class="completion-form__section-title">
            数字化信息
            <span class="completion-form__counter">
              进度 <b>{{ percentPreview }}%</b>
            </span>
          </div>
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-model-item label="数字化单位" prop="digitizeOrg">
                <a-input v-model="model.digitizeOrg" placeholder="扫描加工单位" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="完成日期" prop="digitizeDate">
                <a-date-picker v-model="model.digitizeDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="扫描分辨率" prop="scanDpi">
                <a-select v-model="model.scanDpi" allowClear placeholder="DPI" style="width: 100%">
                  <a-select-option v-for="dpi in scanDpiOptions" :key="dpi" :value="dpi">{{ dpi }} DPI</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="总页数" prop="pageCount">
                <a-input-number v-model="model.pageCount" :min="0" style="width: 100%" />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="文件数" prop="fileCount">
                <a-input-number v-model="model.fileCount" :min="0" style="width: 100%" />
              </a-form-model-item>
            </a-col>
          </a-row>

          <!-- 状态与扫描件的一致性提示（历史上最常见的「假完成」） -->
          <a-alert
            v-if="showArchiveHint"
            class="completion-form__alert"
            type="warning"
            showIcon
            message="数字化状态为「已数字化」，但本档案还没有关联扫描件"
            description="扫描件请在「档案维护」中上传后，回到本页点「关联扫描件」挂上；否则状态与实际资料会对不上。" />

          <div class="completion-form__tip">
            <a-icon type="info-circle" />
            扫描件本体不上传到这里：请在「档案维护」上传后，在详情页点「关联扫描件」建立关联。
          </div>
        </div>

        <a-form-model-item label="备注" prop="remark">
          <a-textarea v-model="model.remark" :rows="2" :maxLength="1000" placeholder="选填" />
        </a-form-model-item>
      </a-form-model>
    </a-spin>
  </a-modal>
</template>

<script>
  import { queryXzqhOptions, searchFacilityOptions } from '@/api/land/landData'
  import {
    DICT,
    DIGITIZE_STATUS,
    PTSSLB_OPTIONS,
    SCAN_DPI_OPTIONS,
    addCompletion,
    checkArchiveNo,
    digitizePercent,
    editCompletion,
    generateArchiveNo,
    loadDictItems
  } from '@/api/land/completion'

  /**
   * 竣工验收项目历史工程资料数字化档案 - 新增/编辑弹窗
   *
   * 四段式表单：档案标识 → 历史项目信息 → 关键日期与投资 → ★数字化信息。
   *
   * 几个关键交互：
   *  1. 档案编号默认留空自动生成（JG-{yyyy}-{4位}），也可以点「自动生成」预览后手工改写；
   *     失焦时做一次唯一性校验（服务端也会再校验一次）。
   *  2. 「关联配套项目」是远程搜索下拉，选中后把配套项目名称、宗地编号、地块名称、
   *     配套设施类别一并回填（只在字段为空时回填，不覆盖用户已手工改过的值）；
   *     ★ 这一项是选填：存量历史项目常常没有对应的配套项目记录。
   *  3. 数字化状态用 radio 按钮组（3 值，不入字典，与后端 DigitizeStatus 一致），
   *     旁边实时显示折算后的进度百分比。
   *  4. ★ 当状态选「已数字化」但本档案还没关联扫描件时，给出黄色提示 ——
   *     这是历史档案数字化推进中最常见的「假完成」。
   */
  export default {
    name: 'CompletionModal',
    data () {
      return {
        title: '新增历史档案',
        visible: false,
        loading: false,
        submitting: false,
        isEdit: false,
        labelCol: { span: 7 },
        wrapperCol: { span: 17 },
        statuses: DIGITIZE_STATUS,
        ptsslbOptions: PTSSLB_OPTIONS,
        scanDpiOptions: SCAN_DPI_OPTIONS,
        xzqhOptions: [],
        projectTypes: [],
        retentions: [],
        facilityOptions: [],
        facilityLoading: false,
        model: this.buildEmptyModel(),
        rules: {
          projectName: [
            { required: true, message: '请输入历史项目名称', trigger: 'blur' },
            { max: 255, message: '历史项目名称不能超过 255 个字符', trigger: 'blur' }
          ],
          archiveNo: [
            { max: 64, message: '档案编号不能超过 64 个字符', trigger: 'blur' }
          ]
        }
      }
    },
    computed: {
      /** 当前状态折算的进度（与后端 CompletionSupport.digitizePercent 同口径） */
      percentPreview () {
        return digitizePercent(this.model.digitizeStatus)
      },
      /** 是否显示「已数字化但未挂扫描件」的提示 */
      showArchiveHint () {
        return this.model.digitizeStatus === '已数字化' && !this.model.archiveId && !!this.model.id
      }
    },
    created () {
      this.loadXzqh()
      this.loadDicts()
    },
    methods: {
      buildEmptyModel () {
        return {
          id: undefined,
          archiveNo: '',
          projectName: '',
          projectCode: '',
          xzqh: undefined,
          projectType: undefined,
          landId: undefined,
          facilityId: undefined,
          crzdbh: '',
          ptxmmc: '',
          dkmc: '',
          ptsslb: undefined,
          buildUnit: '',
          constructUnit: '',
          designUnit: '',
          superviseUnit: '',
          startDate: undefined,
          completeDate: undefined,
          acceptanceDate: undefined,
          investAmount: undefined,
          retention: undefined,
          digitizeStatus: '未数字化',
          digitizeDate: undefined,
          digitizeOrg: '',
          pageCount: undefined,
          fileCount: undefined,
          scanDpi: undefined,
          remark: ''
        }
      },
      /** 打开：新增 */
      add () {
        this.isEdit = false
        this.title = '新增历史档案'
        this.model = this.buildEmptyModel()
        this.facilityOptions = []
        this.visible = true
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate()
          }
        })
        // 预生成一个档案编号（按竣工/验收日期所在年度，都没有就按当前年）
        generateArchiveNo().then(res => {
          if (res && res.success && !this.model.archiveNo) {
            this.model.archiveNo = res.result
          }
        }).catch(() => { /* 生成失败不阻塞录入手工编号 */ })
      },
      /** 打开：编辑 */
      edit (record) {
        if (!record) {
          return
        }
        this.isEdit = true
        this.title = `编辑历史档案 ${record.archiveNo || ''}`
        this.visible = true
        this.facilityOptions = []
        // 列表行已带全部字段（后端列表直接返回实体），先按行回显
        this.model = Object.assign(this.buildEmptyModel(), this.normalizeRecord(record))
        // 编辑时把「已有的关联指针」也带上，仅用于一致性提示（不会提交给后端）
        this.model.archiveId = record.archiveId
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate()
          }
        })
      },
      normalizeRecord (record) {
        return Object.assign({}, record)
      },
      handleClose () {
        this.visible = false
        this.submitting = false
      },
      loadXzqh () {
        queryXzqhOptions().then(res => {
          if (res.success) {
            this.xzqhOptions = res.result || []
          }
        }).catch(() => { this.xzqhOptions = [] })
      },
      loadDicts () {
        loadDictItems(DICT.projectType).then(items => { this.projectTypes = items || [] })
        loadDictItems(DICT.retention).then(items => { this.retentions = items || [] })
      },
      // ------------------------------------------------------------------
      // 档案编号
      // ------------------------------------------------------------------
      handleGenerateNo () {
        generateArchiveNo().then(res => {
          if (res && res.success) {
            this.model.archiveNo = res.result
            this.$message.success(`已生成档案编号：${res.result}`)
          } else {
            this.$message.warning((res && res.message) || '生成档案编号失败')
          }
        })
      },
      handleArchiveNoBlur () {
        const archiveNo = this.model.archiveNo
        if (!archiveNo) {
          return
        }
        checkArchiveNo({ archiveNo, id: this.model.id }).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
          }
        })
      },
      // ------------------------------------------------------------------
      // 关联配套项目（选填）
      // ------------------------------------------------------------------
      handleFacilitySearch (keyword) {
        if (!keyword) {
          this.facilityOptions = []
          return
        }
        this.facilityLoading = true
        searchFacilityOptions({ keyword, limit: 50 }).then(res => {
          this.facilityOptions = res && res.success ? (res.result || []) : []
        }).catch(() => {
          this.facilityOptions = []
        }).finally(() => {
          this.facilityLoading = false
        })
      },
      handleFacilityChange (facilityId) {
        if (!facilityId) {
          this.model.facilityId = undefined
          return
        }
        const hit = this.facilityOptions.filter(item => item.id === facilityId)[0]
        if (!hit) {
          this.model.facilityId = facilityId
          return
        }
        this.model.facilityId = hit.id
        // ★ 只在字段为空时回填，不覆盖用户已经手工改过的值
        if (!this.model.ptxmmc) {
          this.model.ptxmmc = hit.ptxmmc
        }
        if (!this.model.crzdbh) {
          this.model.crzdbh = hit.crzdbh
        }
        if (!this.model.dkmc) {
          this.model.dkmc = hit.dkmc
        }
        if (!this.model.ptsslb && hit.ptsslb) {
          this.model.ptsslb = hit.ptsslb
        }
        if (!this.model.xzqh && hit.xzqh) {
          this.model.xzqh = this.xzqhOptions.some(item => item.value === hit.xzqh) ? hit.xzqh : undefined
        }
      },
      // ------------------------------------------------------------------
      // 提交
      // ------------------------------------------------------------------
      handleSubmit () {
        this.$refs.form.validate(valid => {
          if (!valid) {
            return
          }
          this.submitting = true
          const payload = Object.assign({}, this.model)
          // 展示型/服务端维护字段不属于入参契约，去掉再提交
          // （seq 序号、数字化进度、关联扫描件列表与指针、关联档案数、审计列）
          const dropKeys = ['seq', 'digitizePercent', 'relatedArchives',
            'archiveId', 'archiveCount', 'createBy', 'createTime', 'updateBy', 'updateTime', 'delFlag']
          dropKeys.forEach(key => { delete payload[key] })
          // 空字符串一律转 undefined，避免把空串写进日期/数值字段
          Object.keys(payload).forEach(key => {
            if (payload[key] === '') {
              payload[key] = undefined
            }
          })
          const action = this.isEdit ? editCompletion(payload) : addCompletion(payload)
          action.then(res => {
            if (res.success) {
              this.$message.success(this.isEdit ? '修改成功！' : '新增成功！')
              this.visible = false
              this.$emit('ok')
            } else {
              this.$message.warning(res.message)
            }
          }).catch(e => {
            this.$message.error((e && e.message) || '保存失败')
          }).finally(() => {
            this.submitting = false
          })
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;

  .completion-form {
    &__section {
      padding: 12px 16px 0;
      margin-bottom: 16px;
      border: 1px solid @border-color;
      border-radius: 8px;
      background: #fff;
    }

    &__section-title {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 10px;
      margin-bottom: 12px;
      padding-bottom: 8px;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
      border-bottom: 1px solid @border-color;
    }

    &__hint {
      font-size: 12px;
      font-weight: 400;
      color: #94a3b8;
    }

    &__counter {
      margin-left: auto;
      font-size: 13px;
      font-weight: 400;
      color: @text-muted;

      b {
        color: #2e7cf6;
      }
    }

    &__alert {
      margin-bottom: 12px;
    }

    &__tip {
      margin: 4px 0 16px;
      font-size: 12px;
      line-height: 18px;
      color: #94a3b8;
    }

    &__option-extra {
      color: #94a3b8;
      font-size: 12px;
    }
  }
</style>
