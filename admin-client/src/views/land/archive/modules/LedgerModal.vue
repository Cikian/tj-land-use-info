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
        <!-- ============ 一、台账标识与关联项目 ============ -->
        <div class="ledger-form__section">
          <div class="ledger-form__section-title">台账标识</div>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-model-item label="台账编号" prop="ledgerNo">
                <a-input v-model="model.ledgerNo" placeholder="留空自动生成 YS-{年}-{4位}" @blur="handleLedgerNoBlur">
                  <a-button slot="addonAfter" type="link" size="small" @click="handleGenerateNo">自动生成</a-button>
                </a-input>
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="状态" prop="status">
                <a-radio-group v-model="model.status" buttonStyle="solid">
                  <a-radio-button v-for="item in statuses" :key="item" :value="item">{{ item }}</a-radio-button>
                </a-radio-group>
              </a-form-model-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 二、道路与项目信息 ============ -->
        <div class="ledger-form__section">
          <div class="ledger-form__section-title">
            道路与项目信息
            <span class="ledger-form__hint">选「关联配套项目」可自动带出宗地编号、地块名称与行政区</span>
          </div>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-model-item label="道路名称" prop="roadName">
                <a-input v-model="model.roadName" placeholder="必填，通常与配套项目名称一致" />
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="关联配套项目" prop="facilityId">
                <a-select
                  show-search
                  allowClear
                  placeholder="输入道路/配套项目名称搜索"
                  :filterOption="false"
                  :defaultActiveFirstOption="false"
                  :notFoundContent="facilityLoading ? '搜索中…' : '未找到配套项目'"
                  @search="handleFacilitySearch"
                  @change="handleFacilityChange">
                  <a-select-option v-for="item in facilityOptions" :key="item.id" :value="item.id">
                    {{ item.ptxmmc }}<span class="ledger-form__option-extra">{{ item.crzdbh ? '（' + item.crzdbh + '）' : '' }}</span>
                  </a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="出让宗地编号" prop="crzdbh">
                <a-input v-model="model.crzdbh" placeholder="如 津西青西（挂）2022-004" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="地块名称" prop="dkmc">
                <a-input v-model="model.dkmc" placeholder="可由宗地编号反查带出" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="行政区划" prop="xzqh">
                <a-select v-model="model.xzqh" allowClear placeholder="全部" show-search>
                  <a-select-option v-for="item in xzqhOptions" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="功能区" prop="gnq">
                <a-select v-model="model.gnq" allowClear placeholder="非功能区道路可留空">
                  <a-select-option v-for="item in gnqOptions" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="配套设施类别" prop="ptsslb">
                <a-select v-model="model.ptsslb" allowClear placeholder="请选择">
                  <a-select-option v-for="item in ptsslbOptions" :key="item" :value="item">{{ item }}</a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="12">
              <a-form-model-item label="道路等级" prop="dldj">
                <a-input v-model="model.dldj" placeholder="如 城市主干路 / 次干路 / 支路" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="建设单位" prop="jsdw">
                <a-input v-model="model.jsdw" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="施工单位" prop="sgdw">
                <a-input v-model="model.sgdw" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="监理单位" prop="jldw">
                <a-input v-model="model.jldw" allowClear />
              </a-form-model-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 三、验收与移交 ============ -->
        <div class="ledger-form__section">
          <div class="ledger-form__section-title">验收与移交</div>
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-model-item label="验收类型" prop="acceptanceType">
                <a-select v-model="model.acceptanceType" allowClear placeholder="请选择">
                  <a-select-option v-for="item in acceptanceTypes" :key="item.value" :value="item.value">
                    {{ item.text }}
                  </a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="验收单编号" prop="acceptanceNo">
                <a-input v-model="model.acceptanceNo" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="验收日期" prop="acceptanceDate">
                <a-date-picker v-model="model.acceptanceDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="验收组织单位" prop="acceptanceOrg">
                <a-input v-model="model.acceptanceOrg" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="验收结果" prop="acceptanceResult">
                <a-select v-model="model.acceptanceResult" allowClear placeholder="请选择">
                  <a-select-option v-for="item in acceptanceResults" :key="item.value" :value="item.value">
                    {{ item.text }}
                  </a-select-option>
                </a-select>
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="实际竣工日期" prop="completeDate">
                <a-date-picker v-model="model.completeDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="移交单位" prop="handoverUnit">
                <a-input v-model="model.handoverUnit" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="接收管养单位" prop="receiveUnit">
                <a-input v-model="model.receiveUnit" allowClear />
              </a-form-model-item>
            </a-col>
            <a-col :span="8">
              <a-form-model-item label="移交日期" prop="handoverDate">
                <a-date-picker v-model="model.handoverDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
              </a-form-model-item>
            </a-col>
          </a-row>
        </div>

        <!-- ============ 四、★13 类资料勾选矩阵 ============ -->
        <div class="ledger-form__section">
          <div class="ledger-form__section-title">
            验收及移交资料（勾选已归集）
            <span class="ledger-form__counter">
              已归集 <b>{{ selectedCount }}</b> / {{ materials.length }}
            </span>
            <span class="ledger-form__section-actions">
              <a @click="handleSelectAllMaterials">全选</a>
              <a-divider type="vertical" />
              <a @click="handleClearMaterials">清空</a>
            </span>
          </div>
          <div class="ledger-form__materials">
            <div v-for="item in materials" :key="item.key" class="ledger-form__material-item">
              <a-checkbox
                :checked="Number(model[item.key]) === 1"
                @change="e => handleMaterialChange(item.key, e.target.checked)">
                <a-tooltip :title="item.source">
                  <span class="ledger-form__material-label">
                    <span class="ledger-form__material-seq">{{ padSeq(item.seq) }}</span>
                    {{ item.label }}
                  </span>
                </a-tooltip>
              </a-checkbox>
            </div>
          </div>
          <div class="ledger-form__tip">
            <a-icon type="info-circle" />
            勾选表示该类资料已归集；资料原件不上传到这里，请在「档案维护」中上传后回到台账点「关联档案」。
          </div>
        </div>

        <a-form-model-item label="备注" prop="remark">
          <a-textarea v-model="model.remark" :rows="2" :maxLength="1000" placeholder="选填（迁移生成的记录在这里标注「出让宗地未匹配」等提示）" />
        </a-form-model-item>
      </a-form-model>
    </a-spin>
  </a-modal>
</template>

<script>
  import { queryXzqhOptions, searchFacilityOptions } from '@/api/land/landData'
  import {
    DICT,
    GNQ_OPTIONS,
    LEDGER_MATERIALS_FALLBACK,
    LEDGER_STATUS,
    PTSSLB_OPTIONS,
    addLedger,
    checkLedgerNo,
    editLedger,
    generateLedgerNo,
    loadDictItems,
    queryLedgerMaterials
  } from '@/api/land/ledger'

  /**
   * 道路设施验收及移交资料台账 - 新增/编辑弹窗
   *
   * 四段式表单：台账标识 → 道路与项目信息 → 验收与移交 → ★13 类资料勾选矩阵。
   *
   * 几个关键交互：
   *  1. 台账编号默认留空自动生成（YS-{yyyy}-{4位}），也可以点「自动生成」预览后手工改写；
   *     失焦时做一次唯一性校验（服务端也会再校验一次）。
   *  2. 「关联配套项目」是远程搜索下拉，选中后把配套项目名称、宗地编号、地块名称、行政区划
   *     一并回填（只在字段为空时回填，不覆盖用户已经手工改过的值）。
   *  3. 13 类资料用 checkbox 网格展示，勾选值按 0/1 提交（与库里 tinyint(1) 对应）；
   *     上方实时显示「已归集 x/13」，并提供全选/清空。
   *  4. 状态 4 值用 radio 按钮组（不入字典，与后端 LedgerStatus 一致）。
   */
  export default {
    name: 'LedgerModal',
    props: {
      /**
       * 13 类资料定义（页面从后端 /materials 拉取后传入）。
       *
       * ★ 刻意不叫 `materials`：本组件内部已经用 `materials` 存「当前生效的资料定义」，
       *   同名会与 prop 冲突（Vue 会告警）。父页面传进来后赋值给内部 `materials`，
       *   从而「后端加一类资料」时父页面的矩阵列与本弹窗的勾选项同时跟上。
       */
      materialDefs: {
        type: Array,
        default: null
      }
    },
    data () {
      return {
        title: '新增台账',
        visible: false,
        loading: false,
        submitting: false,
        isEdit: false,
        labelCol: { span: 7 },
        wrapperCol: { span: 17 },
        statuses: LEDGER_STATUS,
        gnqOptions: GNQ_OPTIONS,
        ptsslbOptions: PTSSLB_OPTIONS,
        materials: LEDGER_MATERIALS_FALLBACK,
        xzqhOptions: [],
        acceptanceTypes: [],
        acceptanceResults: [],
        facilityOptions: [],
        facilityLoading: false,
        model: this.buildEmptyModel(),
        rules: {
          roadName: [
            { required: true, message: '请输入道路名称', trigger: 'blur' },
            { max: 200, message: '道路名称不能超过 200 个字符', trigger: 'blur' }
          ],
          ledgerNo: [
            { max: 64, message: '台账编号不能超过 64 个字符', trigger: 'blur' }
          ]
        }
      }
    },
    computed: {
      /** 已勾选的资料数（与后端 LedgerMaterial.countMaterials 同口径） */
      selectedCount () {
        return this.materials.reduce((sum, item) => {
          return sum + (Number(this.model[item.key]) === 1 ? 1 : 0)
        }, 0)
      }
    },
    created () {
      this.loadXzqh()
      this.loadDicts()
      // 资料定义：① 父页面已拉好就直接用，省一次请求；② 否则自己拉；③ 都失败用本地兜底
      if (this.materialDefs && this.materialDefs.length) {
        this.materials = this.materialDefs
        return
      }
      queryLedgerMaterials().then(res => {
        if (res && res.success && Array.isArray(res.result) && res.result.length) {
          this.materials = res.result
        }
      }).catch(() => { /* 静默：保留本地兜底列表 */ })
    },
    methods: {
      buildEmptyModel () {
        const model = {
          id: undefined,
          ledgerNo: '',
          roadName: '',
          xzqh: undefined,
          gnq: undefined,
          crzdbh: '',
          landId: undefined,
          dkmc: '',
          ptsslb: undefined,
          facilityId: undefined,
          ptxmmc: undefined,
          dldj: '',
          jsdw: '',
          sgdw: '',
          jldw: '',
          acceptanceType: undefined,
          acceptanceNo: '',
          acceptanceDate: undefined,
          acceptanceOrg: '',
          acceptanceResult: undefined,
          completeDate: undefined,
          handoverUnit: '',
          receiveUnit: '',
          handoverDate: undefined,
          status: '未验收',
          remark: ''
        }
        LEDGER_MATERIALS_FALLBACK.forEach(item => {
          model[item.key] = 0
        })
        return model
      },
      /** 打开：新增 */
      add () {
        this.isEdit = false
        this.title = '新增台账'
        this.model = this.buildEmptyModel()
        this.facilityOptions = []
        this.visible = true
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate()
          }
        })
        // 预生成一个台账编号（按验收/竣工日期所在年度，都没有就按当前年）
        generateLedgerNo().then(res => {
          if (res && res.success && !this.model.ledgerNo) {
            this.model.ledgerNo = res.result
          }
        }).catch(() => { /* 生成失败不阻塞录入手工编号 */ })
      },
      /** 打开：编辑 */
      edit (record) {
        if (!record) {
          return
        }
        this.isEdit = true
        this.title = `编辑台账 ${record.ledgerNo || ''}`
        this.loading = true
        this.visible = true
        this.facilityOptions = []
        // 列表行已带全部字段（后端列表直接返回实体），先按行回显，再拉详情补关联档案等信息
        this.model = Object.assign(this.buildEmptyModel(), this.normalizeRecord(record))
        this.loading = false
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate()
          }
        })
      },
      /** 把接口返回的记录转成表单模型（13 个勾选列统一成 0/1） */
      normalizeRecord (record) {
        const model = Object.assign({}, record)
        LEDGER_MATERIALS_FALLBACK.forEach(item => {
          model[item.key] = Number(record[item.key]) === 1 ? 1 : 0
        })
        return model
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
        loadDictItems(DICT.acceptanceType).then(items => { this.acceptanceTypes = items || [] })
        loadDictItems(DICT.acceptanceResult).then(items => { this.acceptanceResults = items || [] })
      },
      // ------------------------------------------------------------------
      // 台账编号
      // ------------------------------------------------------------------
      handleGenerateNo () {
        generateLedgerNo().then(res => {
          if (res && res.success) {
            this.model.ledgerNo = res.result
            this.$message.success(`已生成台账编号：${res.result}`)
          } else {
            this.$message.warning((res && res.message) || '生成台账编号失败')
          }
        })
      },
      handleLedgerNoBlur () {
        const ledgerNo = this.model.ledgerNo
        if (!ledgerNo) {
          return
        }
        checkLedgerNo({ ledgerNo, id: this.model.id }).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
          }
        })
      },
      // ------------------------------------------------------------------
      // 关联配套项目
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
        this.model.ptxmmc = hit.ptxmmc
        // ★ 只在字段为空时回填，不覆盖用户已经手工改过的值
        if (!this.model.roadName) {
          this.model.roadName = hit.ptxmmc
        }
        if (!this.model.crzdbh) {
          this.model.crzdbh = hit.crzdbh
        }
        if (!this.model.dkmc) {
          this.model.dkmc = hit.dkmc
        }
        if (!this.model.xzqh && hit.xzqh) {
          this.model.xzqh = this.xzqhOptions.some(item => item.value === hit.xzqh) ? hit.xzqh : undefined
        }
        if (!this.model.ptsslb && hit.ptsslb) {
          this.model.ptsslb = hit.ptsslb
        }
      },
      // ------------------------------------------------------------------
      // 13 类资料
      // ------------------------------------------------------------------
      handleMaterialChange (key, checked) {
        this.$set(this.model, key, checked ? 1 : 0)
      },
      handleSelectAllMaterials () {
        this.materials.forEach(item => this.$set(this.model, item.key, 1))
      },
      handleClearMaterials () {
        this.materials.forEach(item => this.$set(this.model, item.key, 0))
      },
      padSeq (seq) {
        return seq < 10 ? `0${seq}` : `${seq}`
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
          // 列表行里带回来的展示型/服务端维护字段不属于入参契约，去掉再提交
          // （seq 序号、资料统计、关联档案列表、档案指针、迁移幂等键、审计列）
          const dropKeys = ['seq', 'materialTotal', 'materialMissing', 'relatedArchives',
            'archiveId', 'archiveCount', 'sourceFacilityId', 'materialCount',
            'createBy', 'createTime', 'updateBy', 'updateTime', 'delFlag']
          dropKeys.forEach(key => { delete payload[key] })
          // 空字符串一律转 undefined，避免把空串写进日期/数值字段
          Object.keys(payload).forEach(key => {
            if (payload[key] === '') {
              payload[key] = undefined
            }
          })
          const action = this.isEdit ? editLedger(payload) : addLedger(payload)
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

  .ledger-form {
    &__section {
      padding: 12px 16px 0;
      margin-bottom: 16px;
      border: 1px solid @border-color;
      border-radius: 8px;
      background: #fff;
    }

    &__section-title {
      display: flex;
      align-items: center;
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
      font-size: 13px;
      font-weight: 400;
      color: @text-muted;

      b {
        color: #2e7cf6;
      }
    }

    &__section-actions {
      margin-left: auto;
      font-size: 13px;
      font-weight: 400;
    }

    /* 13 类资料：4 列网格 */
    &__materials {
      display: flex;
      flex-wrap: wrap;
    }

    &__material-item {
      width: 25%;
      padding: 4px 8px 4px 0;
      box-sizing: border-box;

      /deep/ .ant-checkbox-wrapper {
        display: flex;
        align-items: center;
        width: 100%;
      }

      /deep/ .ant-checkbox + span {
        flex: 1 1 auto;
        min-width: 0;
        padding-right: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    &__material-seq {
      display: inline-block;
      margin-right: 6px;
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 12px;
      color: #94a3b8;
    }

    &__material-label {
      display: inline-block;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      vertical-align: bottom;
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

  /* 窄屏时资料网格退化成 2 列，避免文字被压成省略号 */
  @media (max-width: 992px) {
    .ledger-form__material-item {
      width: 50%;
    }
  }
</style>
