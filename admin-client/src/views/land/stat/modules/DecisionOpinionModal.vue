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
        <a-form-model-item label="预警等级" prop="levelId">
          <a-select v-model="model.levelId" style="width: 100%" placeholder="请选择预警等级">
            <a-select-option v-for="level in levels" :key="level.id" :value="level.id">
              {{ levelLabel(level) }}
            </a-select-option>
          </a-select>
          <div class="form-item-tip">等级的天数区间在「预警等级维护」页调整，这里只做引用。</div>
        </a-form-model-item>

        <a-form-model-item label="问题类型" prop="problemType">
          <a-select v-model="model.problemType" style="width: 100%" placeholder="请选择问题类型">
            <a-select-option :value="generalOption">通用（所有问题类型）</a-select-option>
            <a-select-option v-for="type in problemTypes" :key="type" :value="type">{{ type }}</a-select-option>
          </a-select>
          <div class="form-item-tip">
            选「通用」= 该等级下所有问题类型的兜底意见（problem_type 存空串）；
            匹配时精确组合优先，取不到才回落到通用。
          </div>
        </a-form-model-item>

        <a-form-model-item label="决策意见" prop="opinion">
          <a-textarea
            v-model="model.opinion"
            :rows="6"
            :maxLength="1000"
            placeholder="例如：已发生逾期，请责任部门核实该环节进展，督促承办单位尽快办结并补录实际完成时间。" />
          <div class="form-item-tip form-item-tip--count">{{ (model.opinion || '').length }} / 1000 字</div>
        </a-form-model-item>
      </a-form-model>
    </a-spin>
  </a-modal>
</template>

<script>
  import { saveDecisionOpinion } from '@/api/land/statWarning'

  /**
   * 决策意见 新增 / 编辑 弹窗（契约 §5.7）
   *
   * 两个坑：
   *   ① problem_type 空串是「通用兜底意见」，而 a-select 把 '' 当成「未选择」，
   *      所以界面模型里用哨兵 __general__ 表示通用，提交前再换回空串；
   *   ② 「同一 (问题类型, 等级) 只允许一条」只能由后端判（前端拿不到别人刚存的记录），
   *      后端 Result.error 的 message（如「已存在，请直接编辑」）原样提示。
   */
  const GENERAL_OPTION = '__general__'

  export default {
    name: 'DecisionOpinionModal',
    props: {
      /** 等级下拉数据（来自 /land/stat/warning/options 的 levels） */
      levels: {
        type: Array,
        default: () => []
      },
      /** 问题类型下拉数据（来自 /options 的 problemTypes，源头是字典 land_process_issue） */
      problemTypes: {
        type: Array,
        default: () => []
      }
    },
    data () {
      return {
        title: '新增决策意见',
        visible: false,
        confirmLoading: false,
        /** 「通用（所有问题类型）」在界面模型里的哨兵值 */
        generalOption: GENERAL_OPTION,
        model: this.buildEmptyModel(),
        labelCol: { xs: { span: 24 }, sm: { span: 5 } },
        wrapperCol: { xs: { span: 24 }, sm: { span: 18 } }
      }
    },
    computed: {
      validatorRules () {
        const that = this
        return {
          levelId: [
            { required: true, message: '请选择预警等级', trigger: 'change' }
          ],
          opinion: [
            { required: true, message: '请填写决策意见', trigger: 'blur' },
            { validator: that.validateOpinionLength, trigger: 'blur' }
          ]
        }
      }
    },
    methods: {
      buildEmptyModel () {
        return {
          id: undefined,
          levelId: undefined,
          problemType: GENERAL_OPTION,
          opinion: ''
        }
      },
      /**
       * 打开弹窗：record 为空表示新增；preset 用于把列表页当前筛选条件带进来（少点两次）。
       * 只认「带 id 的记录」—— 调用方写成 @click="handleEdit" 时传进来的是 MouseEvent，
       * 直接 Object.assign 会得到一张空表单（本项目踩过）。
       */
      show (record, preset) {
        const target = (record && record.id) ? record : null
        this.model = this.buildEmptyModel()
        if (target) {
          this.title = '编辑决策意见'
          this.model.id = target.id
          this.model.levelId = target.levelId
          // 后端空串表示通用；空串在 a-select 里等于「未选择」，所以换成哨兵
          this.model.problemType = target.problemType ? target.problemType : GENERAL_OPTION
          this.model.opinion = target.opinion || ''
        } else {
          this.title = '新增决策意见'
          const pre = preset || {}
          this.model.levelId = pre.levelId || undefined
          this.model.problemType = pre.problemType || GENERAL_OPTION
        }
        this.visible = true
        this.$nextTick(() => {
          this.$refs.form && this.$refs.form.clearValidate()
        })
      },
      /** 选项文案带上天数区间，避免 4 个等级里选错档 */
      levelLabel (level) {
        const min = level && level.minDays
        const max = level && level.maxDays
        const filled = min !== null && min !== undefined && max !== null && max !== undefined
        return filled ? `${level.levelName}（逾期 ${min} - ${max} 天）` : (level ? level.levelName : '')
      },
      validateOpinionLength (rule, value, callback) {
        const text = (value || '').trim()
        if (!text) {
          // 空内容交给 required 规则报错，避免同一个问题提示两遍
          callback()
          return
        }
        if (text.length > 1000) {
          callback(new Error(`决策意见最多 1000 字，当前 ${text.length} 字`))
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
            levelId: this.model.levelId,
            // 哨兵 → 空串（通用兜底意见）
            problemType: this.model.problemType === GENERAL_OPTION ? '' : this.model.problemType,
            // levelName 不传：后端 DecisionOpinionVO 的注释写明「冗余列由 Service 按 levelId 回填」，
            // 前端拼一份只会在下拉数据过期时存进旧名称
            opinion: (this.model.opinion || '').trim()
          }
          saveDecisionOpinion(payload).then(res => {
            if (res.success) {
              this.$message.success(res.message || '保存成功')
              this.$emit('ok', payload)
              this.close()
            } else {
              // 「已存在，请直接编辑」「该等级不存在」等：原样提示后端原因
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
</style>
