<template>
  <a-modal
    :title="title"
    :width="1000"
    :visible="visible"
    :confirmLoading="submitting"
    :maskClosable="false"
    okText="保存"
    cancelText="取消"
    @ok="handleSubmit"
    @cancel="handleClose">
    <a-form-model ref="form" :model="model" :rules="rules" :label-col="labelCol" :wrapper-col="wrapperCol">
      <!-- ============ 一、编号与状态 ============ -->
      <div class="handover-form__section">
        <div class="handover-form__section-title">移交事项标识</div>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-model-item label="移交编号" prop="handoverNo">
              <a-input v-model="model.handoverNo" placeholder="留空自动生成 YJ-{年}-{4位}" @blur="handleNoBlur">
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

      <!-- ============ 二、道路信息 ============ -->
      <div class="handover-form__section">
        <div class="handover-form__section-title">
          道路信息
          <span class="handover-form__hint">选「关联配套项目」可自动带出宗地编号、地块名称与行政区</span>
        </div>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-model-item label="道路名称" prop="roadName">
              <a-input v-model="model.roadName" placeholder="必填" />
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
                  {{ item.ptxmmc }}
                  <span v-if="item.crzdbh" class="handover-form__option-extra">（{{ item.crzdbh }}）</span>
                </a-select-option>
              </a-select>
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="道路编号" prop="roadCode">
              <a-input v-model="model.roadCode" allowClear />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="道路等级" prop="dldj">
              <a-select v-model="model.dldj" allowClear placeholder="请选择" show-search>
                <a-select-option v-for="item in dldjOptions" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="配套设施类别" prop="ptsslb">
              <a-input v-model="model.ptsslb" allowClear />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="起点" prop="startPoint">
              <a-input v-model="model.startPoint" allowClear />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="终点" prop="endPoint">
              <a-input v-model="model.endPoint" allowClear />
            </a-form-model-item>
          </a-col>
          <a-col :span="4">
            <a-form-model-item label="长度(米)" prop="lengthM">
              <a-input-number v-model="model.lengthM" :min="0" style="width: 100%" />
            </a-form-model-item>
          </a-col>
          <a-col :span="4">
            <a-form-model-item label="红线宽(米)" prop="redLineWidth">
              <a-input-number v-model="model.redLineWidth" :min="0" style="width: 100%" />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="出让宗地编号" prop="crzdbh">
              <a-input v-model="model.crzdbh" allowClear />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="地块名称" prop="dkmc">
              <a-input v-model="model.dkmc" allowClear />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="行政区划" prop="xzqh">
              <a-select v-model="model.xzqh" allowClear placeholder="全部" show-search>
                <a-select-option v-for="item in xzqhOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </a-select-option>
              </a-select>
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="功能区" prop="gnq">
              <a-select v-model="model.gnq" allowClear placeholder="非功能区可留空">
                <a-select-option v-for="item in gnqOptions" :key="item" :value="item">{{ item }}</a-select-option>
              </a-select>
            </a-form-model-item>
          </a-col>
        </a-row>
      </div>

      <!-- ============ 三、协议与移交 ============ -->
      <div class="handover-form__section">
        <div class="handover-form__section-title">
          协议与移交
          <span class="handover-form__hint">迁移生成的记录这些字段是空的（旧库没有），请按实际协议补录</span>
        </div>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-model-item label="移交类型" prop="handoverType">
              <a-select v-model="model.handoverType" allowClear placeholder="请选择">
                <a-select-option v-for="item in handoverTypes" :key="item.value" :value="item.value">
                  {{ item.text }}
                </a-select-option>
              </a-select>
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="协议编号" prop="agreementNo">
              <a-input v-model="model.agreementNo" allowClear />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="协议签订日期" prop="agreementDate">
              <a-date-picker v-model="model.agreementDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
            </a-form-model-item>
          </a-col>
          <a-col :span="16">
            <a-form-model-item label="协议名称" prop="agreementName">
              <a-input v-model="model.agreementName" allowClear />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="实际移交日期" prop="handoverDate">
              <a-date-picker v-model="model.handoverDate" style="width: 100%" valueFormat="YYYY-MM-DD" />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="建设单位" prop="buildUnit">
              <a-input v-model="model.buildUnit" allowClear />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="接收管养单位" prop="receiveUnit">
              <a-input v-model="model.receiveUnit" allowClear />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="养护起始日期" prop="maintenanceStart">
              <a-date-picker v-model="model.maintenanceStart" style="width: 100%" valueFormat="YYYY-MM-DD" />
            </a-form-model-item>
          </a-col>
          <a-col :span="8">
            <a-form-model-item label="养护截止日期" prop="maintenanceEnd">
              <a-date-picker v-model="model.maintenanceEnd" style="width: 100%" valueFormat="YYYY-MM-DD" />
            </a-form-model-item>
          </a-col>
        </a-row>
        <div class="handover-form__tip">
          <a-icon type="info-circle" />
          协议扫描件与移交单不在本页上传：请到「档案维护」上传后，回到本事项点「关联档案」建立关联。
        </div>
      </div>

      <a-form-model-item label="备注" prop="remark">
        <a-textarea v-model="model.remark" :rows="2" :maxLength="1000" placeholder="选填" />
      </a-form-model-item>
    </a-form-model>
  </a-modal>
</template>

<script>
  import { queryXzqhOptions, searchFacilityOptions } from '@/api/land/landData'
  import {
    DICT,
    DLDJ_OPTIONS,
    GNQ_OPTIONS,
    HANDOVER_STATUS,
    addHandover,
    checkHandoverNo,
    editHandover,
    generateHandoverNo,
    loadDictItems
  } from '@/api/land/handover'

  /**
   * 道路交付及养护协议移交事项 - 新增/编辑弹窗
   *
   * 三段式表单：移交事项标识 → 道路信息 → 协议与移交。
   * 与台账模块同一套交互约定：编号可自动生成并失焦校验；配套项目远程搜索回填空字段（不覆盖已改过的值）；
   * 状态用 3 值 radio 按钮组（不入字典，与后端 HandoverStatus 一致）。
   */
  export default {
    name: 'HandoverModal',
    data () {
      return {
        title: '新增移交事项',
        visible: false,
        submitting: false,
        isEdit: false,
        labelCol: { span: 8 },
        wrapperCol: { span: 16 },
        statuses: HANDOVER_STATUS,
        gnqOptions: GNQ_OPTIONS,
        dldjOptions: DLDJ_OPTIONS,
        xzqhOptions: [],
        handoverTypes: [],
        facilityOptions: [],
        facilityLoading: false,
        model: this.buildEmptyModel(),
        rules: {
          roadName: [
            { required: true, message: '请输入道路名称', trigger: 'blur' },
            { max: 200, message: '道路名称不能超过 200 个字符', trigger: 'blur' }
          ],
          handoverNo: [
            { max: 64, message: '移交编号不能超过 64 个字符', trigger: 'blur' }
          ]
        }
      }
    },
    created () {
      queryXzqhOptions().then(res => {
        if (res.success) {
          this.xzqhOptions = res.result || []
        }
      }).catch(() => { this.xzqhOptions = [] })
      loadDictItems(DICT.handoverType).then(items => { this.handoverTypes = items || [] })
    },
    methods: {
      buildEmptyModel () {
        return {
          id: undefined,
          handoverNo: '',
          roadName: '',
          roadCode: '',
          dldj: undefined,
          startPoint: '',
          endPoint: '',
          lengthM: undefined,
          redLineWidth: undefined,
          xzqh: undefined,
          gnq: undefined,
          ptsslb: undefined,
          crzdbh: '',
          landId: undefined,
          dkmc: '',
          facilityId: undefined,
          ptxmmc: undefined,
          handoverType: undefined,
          agreementNo: '',
          agreementName: '',
          agreementDate: undefined,
          buildUnit: '',
          receiveUnit: '',
          handoverDate: undefined,
          maintenanceStart: undefined,
          maintenanceEnd: undefined,
          status: '待移交',
          remark: ''
        }
      },
      add () {
        this.isEdit = false
        this.title = '新增移交事项'
        this.model = this.buildEmptyModel()
        this.facilityOptions = []
        this.visible = true
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate()
          }
        })
        generateHandoverNo().then(res => {
          if (res && res.success && !this.model.handoverNo) {
            this.model.handoverNo = res.result
          }
        }).catch(() => { /* 生成失败不阻塞手工编号 */ })
      },
      edit (record) {
        if (!record) {
          return
        }
        this.isEdit = true
        this.title = `编辑移交事项 ${record.handoverNo || ''}`
        this.visible = true
        this.facilityOptions = []
        this.model = Object.assign(this.buildEmptyModel(), record)
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate()
          }
        })
      },
      handleClose () {
        this.visible = false
        this.submitting = false
      },
      handleGenerateNo () {
        generateHandoverNo().then(res => {
          if (res && res.success) {
            this.model.handoverNo = res.result
            this.$message.success(`已生成移交编号：${res.result}`)
          } else {
            this.$message.warning((res && res.message) || '生成移交编号失败')
          }
        })
      },
      handleNoBlur () {
        if (!this.model.handoverNo) {
          return
        }
        checkHandoverNo({ handoverNo: this.model.handoverNo, id: this.model.id }).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
          }
        })
      },
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
        // ★ 只在字段为空时回填，不覆盖用户已手工改过的值
        if (!this.model.roadName) {
          this.model.roadName = hit.ptxmmc
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
      handleSubmit () {
        this.$refs.form.validate(valid => {
          if (!valid) {
            return
          }
          this.submitting = true
          const payload = Object.assign({}, this.model)
          Object.keys(payload).forEach(key => {
            if (payload[key] === '') {
              payload[key] = undefined
            }
          })
          const action = this.isEdit ? editHandover(payload) : addHandover(payload)
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

  .handover-form {
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

    &__tip {
      margin: 0 0 16px;
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
