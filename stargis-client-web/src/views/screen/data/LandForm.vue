<template>
  <!--
    经营性用地添加 / 编辑
    字段与旧系统 addXjCommercialLand.html 一致，样式使用大屏表单组件。
  -->
  <form class="land-form" @submit.prevent="submit">
    <section v-for="group in groups" :key="group.title" class="land-form__section">
      <h3 class="land-form__title">{{ group.title }}</h3>
      <div class="land-form__grid">
        <screen-field
          v-for="field in group.fields"
          :key="field.key"
          :label="field.label"
          :required="field.required"
          :error="errors[field.key]"
          :html-for="inputId(field.key)"
          label-width="168px"
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
            :invalid="!!errors[field.key]"
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

    <div class="land-form__actions">
      <screen-button icon="reload" :disabled="saving" @click="reset">清空</screen-button>
      <screen-button type="primary" icon="check" :loading="saving" @click="submit">
        {{ model.id ? '保存修改' : '保存' }}
      </screen-button>
    </div>
  </form>
</template>

<script>
import { ScreenButton, ScreenDateInput, ScreenField, ScreenInput, ScreenSelect } from '@/components/screen'
import { addLand, editLand } from '@/api/land/landData'

const YES_NO = ['否', '是']
const DISTRICTS = ['和平区', '红桥区', '河东区', '南开区', '河西区', '河北区', '东丽区', '津南区', '西青区', '北辰区']

function emptyModel () {
  return {
    id: '',
    tdzljhxdwjh: '',
    tdzldw: '',
    xmfl: '市级项目',
    xzqh: '',
    crzdbh: '',
    dkmc: '',
    dz: '',
    xz: '',
    nz: '',
    bz: '',
    ghydxz: '',
    crj: '',
    crsj: '',
    kjsydmj: '',
    zydmj: '',
    nrcbdptf: '',
    ptsfqq: '否',
    ptjsnr: '',
    srr: '',
    htydjfsj: '',
    tdzljh: '否',
    ptqkh: '否',
    ptcbh: '否',
    crzdtxsj: '否',
    lrdw: '',
    lrr: '',
    lxdh: '',
    zlqsnrsm: '',
    beizhu: '',
  }
}

export default {
  name: 'LandForm',
  components: { ScreenButton, ScreenDateInput, ScreenField, ScreenInput, ScreenSelect },
  data () {
    return {
      model: emptyModel(),
      errors: {},
      saving: false,
      groups: [
        {
          title: '宗地基本信息',
          fields: [
            { key: 'tdzljhxdwjh', label: '土地整理计划下达文件号' },
            { key: 'tdzldw', label: '土地整理单位' },
            { key: 'xmfl', label: '项目分类', type: 'select', required: true, options: ['市级项目', '区级项目'] },
            { key: 'xzqh', label: '行政区划', type: 'select', required: true, options: DISTRICTS },
            { key: 'crzdbh', label: '出让宗地编号', required: true },
            { key: 'dkmc', label: '地块名称', required: true },
            { key: 'dz', label: '东至' },
            { key: 'xz', label: '西至' },
            { key: 'nz', label: '南至' },
            { key: 'bz', label: '北至' },
          ],
        },
        {
          title: '出让与配套',
          fields: [
            { key: 'ghydxz', label: '规划用地性质', type: 'select', options: ['商业用地', '综合用地', '住宅用地', '工业用地', '其他用地'] },
            { key: 'crj', label: '出让金（亿元）' },
            { key: 'crsj', label: '出让时间', type: 'date' },
            { key: 'kjsydmj', label: '可建设用地面积（平方米）' },
            { key: 'zydmj', label: '总用地面积（平方米）' },
            { key: 'nrcbdptf', label: '纳入成本的配套费（万元）' },
            { key: 'ptsfqq', label: '配套是否齐全', type: 'select', options: YES_NO },
            { key: 'ptjsnr', label: '配套建设内容' },
            { key: 'srr', label: '受让人' },
            { key: 'htydjfsj', label: '合同约定交付时间', type: 'date' },
          ],
        },
        {
          title: '资料与录入',
          fields: [
            { key: 'tdzljh', label: '土地整理计划', type: 'select', options: YES_NO },
            { key: 'ptqkh', label: '配套情况函', type: 'select', options: YES_NO },
            { key: 'ptcbh', label: '配套筹备函', type: 'select', options: YES_NO },
            { key: 'crzdtxsj', label: '出让宗地图形数据（shp）', type: 'select', options: YES_NO },
            { key: 'lrdw', label: '录入单位' },
            { key: 'lrr', label: '录入人' },
            { key: 'lxdh', label: '联系电话' },
            { key: 'zlqsnrsm', label: '资料缺失内容及说明' },
            { key: 'beizhu', label: '备注' },
          ],
        },
      ],
    }
  },
  methods: {
    inputId (key) {
      return `land-${key}`
    },
    fill (record) {
      this.model = Object.assign(emptyModel(), record || {})
      this.errors = {}
    },
    reset () {
      this.fill()
    },
    validate () {
      const errors = {}
      if (!String(this.model.crzdbh || '').trim()) errors.crzdbh = '出让宗地编号不能为空'
      if (!String(this.model.dkmc || '').trim()) errors.dkmc = '地块名称不能为空'
      if (!this.model.xmfl) errors.xmfl = '请选择项目分类'
      if (!this.model.xzqh) errors.xzqh = '请选择行政区划'
      ;['crj', 'kjsydmj', 'zydmj', 'nrcbdptf'].forEach((key) => {
        const value = String(this.model[key] || '').trim()
        if (value && Number.isNaN(Number(value))) errors[key] = '请输入数字'
      })
      this.errors = errors
      return Object.keys(errors).length === 0
    },
    payload () {
      const data = Object.assign({}, this.model)
      ;['crj', 'kjsydmj', 'zydmj', 'nrcbdptf'].forEach((key) => {
        const value = String(data[key] || '').trim()
        data[key] = value === '' ? null : Number(value)
      })
      ;['crsj', 'htydjfsj'].forEach((key) => {
        if (!data[key]) data[key] = null
      })
      if (!data.id) delete data.id
      return data
    },
    submit () {
      if (this.saving || !this.validate()) return
      this.saving = true
      const request = this.model.id ? editLand(this.payload()) : addLand(this.payload())
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
.land-form {
  height: 100%;
  overflow: auto;
  padding: 8px 8px 20px;

  &__section {
    margin-bottom: 18px;
  }

  &__title {
    margin: 0 0 12px;
    font-size: 16px;
    font-weight: 500;
    color: #ffffff;
  }

  &__grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px 24px;
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    padding-top: 8px;
  }
}
</style>
