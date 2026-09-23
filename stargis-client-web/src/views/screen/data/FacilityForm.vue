<template>
  <!--
    配套项目添加 / 编辑
    字段与旧系统 addxjPublicFacilities.html 一致。
  -->
  <form class="facility-form" @submit.prevent="submit">
    <section v-for="group in groups" :key="group.title" class="facility-form__section">
      <h3 class="facility-form__title">{{ group.title }}</h3>
      <div class="facility-form__grid">
        <screen-field
          v-for="field in group.fields"
          :key="field.key"
          :label="field.label"
          :required="field.required"
          :error="errors[field.key]"
          :html-for="inputId(field.key)"
          label-width="210px"
        >
          <screen-select
            v-if="field.type === 'select'"
            :id="inputId(field.key)"
            v-model="model[field.key]"
            :options="field.options"
            :placeholder="`请选择${field.label}`"
            :invalid="!!errors[field.key]"
            :required="field.required"
            clearable
          />
          <screen-date-input
            v-else-if="field.type === 'date'"
            :id="inputId(field.key)"
            v-model="model[field.key]"
            :placeholder="`请选择${field.label}`"
          />
          <screen-input
            v-else
            :id="inputId(field.key)"
            v-model="model[field.key]"
            :placeholder="`请输入${field.label}`"
            :invalid="!!errors[field.key]"
            :required="field.required"
            :aria-required="field.required ? 'true' : undefined"
          />
        </screen-field>
      </div>
    </section>
    <div class="facility-form__actions">
      <screen-button icon="reload" :disabled="saving" @click="reset">清空</screen-button>
      <screen-button type="primary" icon="check" :loading="saving" @click="submit">
        {{ model.id ? '保存修改' : '保存' }}
      </screen-button>
    </div>
  </form>
</template>

<script>
import { ScreenButton, ScreenDateInput, ScreenField, ScreenInput, ScreenSelect } from '@/components/screen'
import { addFacility, editFacility } from '@/api/land/landData'

const YES_NO = ['否', '是']

function emptyModel () {
  return {
    id: '', crzdbh: '', ptxmmc: '', ptsslb: '', jsxx: '', dldj: '', ghhxkd: '', cd: '', tzgs: '', zjly: '',
    dkcrscndptjgsj: '', jsdw: '', sjdw: '', kcdw: '', jldw: '', sgdw: '', jsgydw: '', xjpfsfwc: '', xjpfzt: '',
    kypfsfwc: '', kypfzt: '', csjgspfsfwc: '', csjgspfzt: '', gspfje: '', zjlsqk: '', sfkg: '', kgzt: '',
    yjkgsj: '', sjkgsj: '', sfjg: '', yjjgsj: '', sjjgsj: '', sfyj: '', ptxmhdydydjdc: '', xjpfwj: '', kypfwj: '',
    csjgspfwj: '', dlgh: '', ghgcxk: '', gxzhslsj: '', zyptfa: '', zyglyj: '', ghydxkyhbsxbl: '', sgxk: '',
    bdcdj: '', jtwt: '', gzjy: '', zlqsnrjsm: '', lrdw: '', lrr: '', lxdh: '', bz: '',
  }
}

export default {
  name: 'FacilityForm',
  components: { ScreenButton, ScreenDateInput, ScreenField, ScreenInput, ScreenSelect },
  data () {
    return {
      model: emptyModel(),
      errors: {},
      saving: false,
      groups: [
        {
          title: '项目基本信息',
          fields: [
            { key: 'crzdbh', label: '出让宗地编号', required: true },
            { key: 'ptxmmc', label: '配套项目名称', required: true },
            { key: 'ptsslb', label: '配套设施类别' },
            { key: 'jsxx', label: '建设性质' },
            { key: 'dldj', label: '道路等级' },
            { key: 'ghhxkd', label: '规划红线宽度（米）' },
            { key: 'cd', label: '长度（米）' },
            { key: 'tzgs', label: '投资估算（万元）' },
            { key: 'zjly', label: '资金来源' },
            { key: 'dkcrscndptjgsj', label: '地块出让时承诺的配套竣工时间', type: 'date' },
          ],
        },
        {
          title: '参建单位',
          fields: [
            { key: 'jsdw', label: '建设单位' },
            { key: 'sjdw', label: '设计单位' },
            { key: 'kcdw', label: '勘察单位' },
            { key: 'jldw', label: '监理单位' },
            { key: 'sgdw', label: '施工单位' },
            { key: 'jsgydw', label: '接收管养单位' },
          ],
        },
        {
          title: '进展情况',
          fields: [
            { key: 'xjpfsfwc', label: '项建批复是否完成', type: 'select', options: YES_NO },
            { key: 'xjpfzt', label: '项建批复状态' },
            { key: 'kypfsfwc', label: '可研批复是否完成', type: 'select', options: YES_NO },
            { key: 'kypfzt', label: '可研批复状态' },
            { key: 'csjgspfsfwc', label: '初设及概算批复是否完成', type: 'select', options: YES_NO },
            { key: 'csjgspfzt', label: '初设及概算批复状态' },
            { key: 'gspfje', label: '概算批复金额（万元）' },
            { key: 'zjlsqk', label: '资金落实情况' },
            { key: 'sfkg', label: '是否开工', type: 'select', options: YES_NO },
            { key: 'kgzt', label: '开工状态' },
            { key: 'yjkgsj', label: '预计开工时间', type: 'date' },
            { key: 'sjkgsj', label: '实际开工时间', type: 'date' },
            { key: 'sfjg', label: '是否竣工', type: 'select', options: YES_NO },
            { key: 'yjjgsj', label: '预计竣工时间', type: 'date' },
            { key: 'sjjgsj', label: '实际竣工时间', type: 'date' },
            { key: 'sfyj', label: '是否移交', type: 'select', options: YES_NO },
          ],
        },
        {
          title: '资料与录入',
          fields: [
            { key: 'ptxmhdydydjdc', label: '配套项目核定用地与地籍调查' },
            { key: 'xjpfwj', label: '项建批复文件' },
            { key: 'kypfwj', label: '可研批复文件' },
            { key: 'csjgspfwj', label: '初设及概算批复文件' },
            { key: 'dlgh', label: '道路规划' },
            { key: 'ghgcxk', label: '规划工程许可' },
            { key: 'gxzhslsj', label: '管线综合矢量数据（shp）' },
            { key: 'zyptfa', label: '专业配套方案' },
            { key: 'zyglyj', label: '专业管理意见' },
            { key: 'ghydxkyhbsxbl', label: '规划用地许可与划拨手续办理' },
            { key: 'sgxk', label: '施工许可' },
            { key: 'bdcdj', label: '不动产登记' },
            { key: 'jtwt', label: '具体问题' },
            { key: 'gzjy', label: '工作建议' },
            { key: 'zlqsnrjsm', label: '资料缺失内容及说明' },
            { key: 'lrdw', label: '录入单位' },
            { key: 'lrr', label: '录入人' },
            { key: 'lxdh', label: '联系电话' },
            { key: 'bz', label: '备注' },
          ],
        },
      ],
    }
  },
  methods: {
    inputId (key) {
      return `facility-${key}`
    },
    fill (record) {
      const model = emptyModel()
      Object.keys(model).forEach((key) => {
        if (record && record[key] != null) model[key] = record[key]
      })
      this.model = model
      this.errors = {}
    },
    reset () {
      this.fill()
    },
    validate () {
      const errors = {}
      if (!String(this.model.crzdbh || '').trim()) errors.crzdbh = '出让宗地编号不能为空'
      if (!String(this.model.ptxmmc || '').trim()) errors.ptxmmc = '配套项目名称不能为空'
      ;['ghhxkd', 'cd', 'tzgs', 'gspfje'].forEach((key) => {
        const value = String(this.model[key] || '').trim()
        if (value && Number.isNaN(Number(value))) errors[key] = '请输入数字'
      })
      this.errors = errors
      return Object.keys(errors).length === 0
    },
    payload () {
      const data = Object.assign({}, this.model)
      ;['ghhxkd', 'cd', 'tzgs', 'gspfje'].forEach((key) => {
        const value = String(data[key] || '').trim()
        data[key] = value === '' ? null : Number(value)
      })
      ;['dkcrscndptjgsj', 'yjkgsj', 'sjkgsj', 'yjjgsj', 'sjjgsj'].forEach((key) => {
        if (!data[key]) data[key] = null
      })
      if (!data.id) delete data.id
      return data
    },
    submit () {
      if (this.saving || !this.validate()) return
      this.saving = true
      const request = this.model.id ? editFacility(this.payload()) : addFacility(this.payload())
      request
        .then((response) => {
          if (!response || !response.success) {
            this.$screenToast.error((response && response.message) || '保存失败')
            return
          }
          this.$screenToast.success(response.message || '保存成功')
          this.reset()
          this.$emit('saved')
        })
        .catch((error) => {
          this.$screenToast.error((error && error.message) || '保存失败')
        })
        .finally(() => {
          this.saving = false
        })
    },
  },
}
</script>

<style scoped lang="less">
.facility-form {
  height: 70vh;
  overflow: auto;
  padding: 8px 8px 20px;

  &__section { margin-bottom: 18px; }
  &__title { margin: 0 0 12px; font-size: 16px; color: #fff; }
  &__grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 24px; }
  &__actions { display: flex; justify-content: flex-end; gap: 12px; }
}
</style>
