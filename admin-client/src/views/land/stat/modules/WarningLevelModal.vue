<template>
  <a-modal
    :title="title"
    :width="640"
    :visible="visible"
    :confirmLoading="confirmLoading"
    :destroyOnClose="true"
    okText="保存"
    cancelText="取消"
    @ok="handleOk()"
    @cancel="handleCancel()">
    <a-spin :spinning="confirmLoading">
      <a-form-model ref="form" :model="model" :rules="validatorRules" :label-col="labelCol" :wrapper-col="wrapperCol">
        <a-form-model-item label="等级名称" prop="levelName" hasFeedback>
          <a-input v-model="model.levelName" :maxLength="50" placeholder="例如：蓝色预警" allowClear />
        </a-form-model-item>

        <a-form-model-item label="等级别名" prop="levelCode">
          <a-input
            v-model="model.levelCode"
            :maxLength="20"
            placeholder="blue / yellow / orange / red"
            allowClear
            @blur="normalizeCode()" />
          <div class="form-item-tip">
            前端着色令牌：蓝 / 黄 / 橙 / 红分别用 blue / yellow / orange / red，
            只能小写字母、数字、下划线或连字符（2~20 位），且不能与其它等级重复。
          </div>
        </a-form-model-item>

        <a-form-model-item label="区间下界" prop="minDays">
          <a-input-number
            v-model="model.minDays"
            :min="0"
            :max="9999"
            :precision="0"
            style="width: 160px"
            placeholder="逾期天数" />
          <span class="form-item-unit">天</span>
          <div class="form-item-tip">
            闭区间的起点（含本数）。业务上从 1 天起：逾期 0 天＝今天正好到期，不产生预警。
          </div>
        </a-form-model-item>

        <a-form-model-item label="区间上界" prop="maxDays">
          <a-input-number
            v-model="model.maxDays"
            :min="0"
            :max="9999"
            :precision="0"
            style="width: 160px"
            placeholder="逾期天数" />
          <span class="form-item-unit">天</span>
          <div class="form-item-tip">
            闭区间的终点（含本数），不能小于下界；与其它等级的区间不得重叠。
          </div>
        </a-form-model-item>

        <a-form-model-item label="区间预览">
          <div class="range-preview">
            <a-icon type="swap-right" class="range-preview__icon" />
            {{ rangePreview }}
          </div>
        </a-form-model-item>

        <a-form-model-item label="排序号" prop="sortNo">
          <a-input-number v-model="model.sortNo" :min="0" :precision="0" style="width: 160px" placeholder="升序即由轻到重" />
          <div class="form-item-tip">数值越小越靠前；列表、下拉与等级匹配都按它升序，所以它应与区间由小到大一致。</div>
        </a-form-model-item>

        <a-form-model-item label="备注" prop="remark">
          <a-textarea v-model="model.remark" :rows="3" :maxLength="255" placeholder="该等级的处置口径，例如「严重逾期：提级督办并报告分管领导」" />
          <div class="form-item-tip form-item-tip--count">{{ (model.remark || '').length }} / 255 字</div>
        </a-form-model-item>
      </a-form-model>
    </a-spin>
  </a-modal>
</template>

<script>
  import { saveWarningLevel } from '@/api/land/statWarning'

  /**
   * 预警等级 新增 / 编辑 弹窗（契约 §5.6）
   *
   * 前端只做「形状」校验（必填、整数、下界 <= 上界、别名格式），
   * 真正的业务规则（别名唯一、区间不重叠）必须由后端判：前端拿不到并发窗口内的其它修改。
   * 后端 Result.error 的 message 是给人看的中文原因，这里**原样**提示，不加工、不吞掉。
   */
  export default {
    name: 'WarningLevelModal',
    props: {
      /** 新增时建议的排序号（= 现有最大排序号 + 1），由列表页算好传进来 */
      defaultSortNo: {
        type: Number,
        default: 1
      }
    },
    data () {
      return {
        title: '新增预警等级',
        visible: false,
        confirmLoading: false,
        model: this.buildEmptyModel(),
        labelCol: { xs: { span: 24 }, sm: { span: 6 } },
        wrapperCol: { xs: { span: 24 }, sm: { span: 16 } }
      }
    },
    computed: {
      validatorRules () {
        const that = this
        return {
          levelName: [
            { required: true, message: '请输入等级名称', trigger: 'blur' }
          ],
          levelCode: [
            { required: true, message: '请输入等级别名（blue / yellow / orange / red）', trigger: 'blur' },
            {
              pattern: /^[a-z0-9_-]{2,20}$/,
              message: '只能用小写字母、数字、下划线或连字符，长度 2~20 位',
              trigger: 'blur'
            }
          ],
          minDays: [
            { required: true, validator: that.validateDays, trigger: ['blur', 'change'] },
            { validator: that.validateRange, trigger: ['blur', 'change'] }
          ],
          maxDays: [
            { required: true, validator: that.validateDays, trigger: ['blur', 'change'] },
            { validator: that.validateRange, trigger: ['blur', 'change'] }
          ]
        }
      },
      /** 把「逾期 X ~ Y 天」写清楚，避免用户把闭区间误解成开区间 */
      rangePreview () {
        const min = this.model.minDays
        const max = this.model.maxDays
        if (!this.isFilled(min) || !this.isFilled(max)) {
          return '填完上下界后，这里预览本次配置的闭区间'
        }
        if (Number(min) > Number(max)) {
          return '区间下界大于上界，请对调后再保存'
        }
        const name = (this.model.levelName || '').trim() || '本等级'
        if (Number(min) === Number(max)) {
          return `闭区间：仅逾期 ${min} 个工作日 → ${name}`
        }
        return `闭区间：逾期 ${min} ~ ${max} 个工作日（含 ${min} 天与 ${max} 天）→ ${name}`
      }
    },
    methods: {
      buildEmptyModel () {
        return {
          id: undefined,
          levelName: '',
          levelCode: '',
          minDays: undefined,
          maxDays: undefined,
          sortNo: undefined,
          remark: ''
        }
      },
      /**
       * 打开弹窗：record 为空表示新增。
       * 只认「带 id 的记录」—— 调用方若写成 @click="handleEdit"（不带括号），
       * Vue 会把 MouseEvent 传进来，直接 Object.assign 就得到一张空表单（本项目踩过）。
       */
      show (record) {
        const target = (record && record.id) ? record : null
        this.model = Object.assign(this.buildEmptyModel(), target || {})
        if (target) {
          this.title = '编辑预警等级'
        } else {
          this.title = '新增预警等级'
          this.model.sortNo = this.defaultSortNo
        }
        this.visible = true
        this.$nextTick(() => {
          this.$refs.form && this.$refs.form.clearValidate()
        })
      },
      /** 别名统一变小写后再上屏，用户输 RED 不会被格式校验卡住 */
      normalizeCode () {
        if (this.model.levelCode) {
          this.model.levelCode = String(this.model.levelCode).trim().toLowerCase()
        }
      },
      validateDays (rule, value, callback) {
        if (!this.isFilled(value)) {
          callback(new Error('请填写逾期工作日天数'))
          return
        }
        const num = Number(value)
        if (isNaN(num) || Math.floor(num) !== num) {
          callback(new Error('请填写整数天数'))
          return
        }
        if (num < 0) {
          callback(new Error('天数不能小于 0'))
          return
        }
        callback()
      },
      validateRange (rule, value, callback) {
        const min = this.model.minDays
        const max = this.model.maxDays
        // 上界还没填时交给各自的必填校验去报，这里只比较「两个都填了」的情况
        if (!this.isFilled(min) || !this.isFilled(max)) {
          callback()
          return
        }
        if (Number(min) > Number(max)) {
          callback(new Error('区间下界不能大于上界'))
          return
        }
        callback()
      },
      handleOk () {
        this.$refs.form.validate(valid => {
          if (!valid) {
            return
          }
          this.confirmLoading = true
          const payload = {
            id: this.model.id,
            levelName: (this.model.levelName || '').trim(),
            levelCode: (this.model.levelCode || '').trim().toLowerCase(),
            minDays: Number(this.model.minDays),
            maxDays: Number(this.model.maxDays),
            // 排序号留空就按 0 存（DB 列 NOT NULL DEFAULT 0），不要发 null 过去
            sortNo: this.isFilled(this.model.sortNo) ? Number(this.model.sortNo) : 0,
            remark: (this.model.remark || '').trim()
          }
          saveWarningLevel(payload).then(res => {
            if (res.success) {
              this.$message.success(res.message || '保存成功')
              this.$emit('ok', payload)
              this.close()
            } else {
              // 别名重复 / 区间重叠等：原样提示后端给的原因
              this.$message.warning(res.message || '保存失败')
            }
          }).catch(() => {
            // 具体错误提示由请求拦截器统一给出
          }).finally(() => {
            this.confirmLoading = false
          })
        })
      },
      handleCancel () {
        this.close()
      },
      close () {
        this.visible = false
        this.model = this.buildEmptyModel()
      },
      /** 0 是合法下界，不能用真值判断 */
      isFilled (value) {
        return value !== null && value !== undefined && value !== ''
      }
    }
  }
</script>

<style lang="less" scoped>
  .form-item-tip {
    margin-top: 4px;
    font-size: 12px;
    line-height: 18px;
    color: #8c9aae;
  }

  .form-item-tip--count {
    text-align: right;
  }

  .form-item-unit {
    margin-left: 8px;
    color: #475569;
  }

  .range-preview {
    padding: 6px 10px;
    font-size: 13px;
    line-height: 20px;
    color: #1d4ed8;
    background: #eff6ff;
    border: 1px dashed #bfdbfe;
    border-radius: 6px;
  }

  .range-preview__icon {
    margin-right: 4px;
  }
</style>
