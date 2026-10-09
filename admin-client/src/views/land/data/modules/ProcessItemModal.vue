<template>
  <a-modal
    :title="title"
    :width="860"
    :visible="visible"
    :confirm-loading="saving"
    ok-text="保存"
    cancel-text="取消"
    @ok="handleSave"
    @cancel="handleClose">
    <!-- ============ 环节基本信息（只读） ============ -->
    <div class="process-item__head">
      <div class="process-item__stage">
        <a-tag color="blue">{{ stageName || '—' }}</a-tag>
        <span class="process-item__lcname">{{ lcName || '—' }}</span>
      </div>
      <div class="process-item__meta">
        <span v-if="processTime">标准办理时长 <b>{{ processTime }}</b> 个工作日</span>
        <span v-if="zgbm">主管部门：{{ zgbm }}</span>
        <span v-if="isParallel === 1">该环节可并行办理</span>
      </div>
    </div>

    <a-form :form="form" layout="vertical" class="process-item__form">
      <!-- ============ 环节情况 ============ -->
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="环节情况" required>
            <a-radio-group v-model="model.lcqk" button-style="solid">
              <a-radio-button
                v-for="option in statusOptions"
                :key="option"
                :value="option">
                {{ option }}
              </a-radio-button>
            </a-radio-group>
            <div class="form-item__hint">
              「已完成」必须填实际结束时间；「不涉及」表示本项目不涉及该环节，阶段汇总时会从分母里剔除
            </div>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="存在问题类型">
            <a-select
              v-model="model.czwtlx"
              placeholder="无问题可不填"
              allow-clear
              :options="issueOptions" />
          </a-form-item>
        </a-col>
      </a-row>

      <!-- ============ 时间 ============ -->
      <a-row :gutter="12">
        <a-col :span="8">
          <a-form-item label="环节开始时间">
            <a-date-picker
              v-model="model.lckssj"
              style="width: 100%"
              value-format="YYYY-MM-DD"
              placeholder="选择开始日期"
              @change="handleStartChange" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item>
            <template slot="label">
              预计结束时间
              <a-tooltip title="留空时后端会按「开始日 + 该环节标准办理时长（工作日）」自动推算；工作日会剔除周末与节假日日历中维护的节假日。手工填写则以你填的为准。">
                <a-icon type="question-circle" class="form-item__help" />
              </a-tooltip>
            </template>
            <a-date-picker
              v-model="model.yjjssj"
              style="width: 100%"
              value-format="YYYY-MM-DD"
              placeholder="留空则自动推算"
              @change="expectEndManual = true" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="环节实际结束时间">
            <a-date-picker
              v-model="model.lcjssj"
              style="width: 100%"
              value-format="YYYY-MM-DD"
              placeholder="已完成时必填" />
          </a-form-item>
        </a-col>
      </a-row>

      <!-- 推算结果提示 -->
      <a-alert
        v-if="calcHint"
        class="process-item__calc"
        :type="calcHintType"
        show-icon
        :message="calcHint" />

      <!-- ============ 问题与建议 ============ -->
      <a-form-item label="具体问题">
        <a-textarea
          v-model="model.jtwt"
          :rows="2"
          :max-length="1000"
          placeholder="例如：管线迁改协调中，涉及燃气与排水两趟管线" />
      </a-form-item>
      <a-form-item label="工作建议">
        <a-textarea
          v-model="model.gzjy"
          :rows="2"
          :max-length="1000"
          placeholder="例如：建议先行办理管线综合选址意见书" />
      </a-form-item>

      <!-- ============ 录入信息 ============ -->
      <a-row :gutter="12">
        <a-col :span="8">
          <a-form-item label="录入单位">
            <a-input v-model="model.lrdw" placeholder="录入单位" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="录入人">
            <a-input v-model="model.lrr" placeholder="录入人" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="联系电话" :validate-status="phoneError ? 'error' : ''" :help="phoneError">
            <a-input v-model="model.lxdh" placeholder="11 位手机号" />
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>
  </a-modal>
</template>

<script>
  import { saveProcess, calcExpectEnd } from '@/api/land/facilityAdmin'

  /**
   * 环节进度填报弹窗（29 环节录入的核心交互）
   *
   * ★ 三条业务规则在这里落地，每一条都对应旧系统的一个缺陷：
   *
   *  1. **「已完成」必须填实际结束时间**。旧系统完全不校验，库里于是存在
   *     「环节情况=已完成 但结束时间为空」的脏数据 —— 这类数据在逾期预警里
   *     会被当成「还在办」，永远算不出准确的逾期天数。
   *
   *  2. **结束时间不能早于开始时间**。同样是旧系统没校验的。
   *
   *  3. **预计结束时间可自动推算、也可手工覆盖**。自动推算走工作日
   *     （剔除周末与节假日日历）；一旦用户手工改过，`expectEndManual`
   *     置位，之后改开始时间**不再覆盖**用户的值 ——
   *     否则用户精心填的日期会在改一下开始时间后被悄悄冲掉。
   *
   * ★ 为什么推算结果里要提示「日历是否已维护」：
   *   `t_non_working_day` 需要中心按年维护。未维护的年份会退化为
   *   「周末 + 内置法定节假日估算」，与真实放假安排有偏差。
   *   不提示，中心会以为系统算错了。
   */
  export default {
    name: 'ProcessItemModal',
    props: {
      /** 配套项目ID（父组件传入，避免每次提交都从 record 里取） */
      ptId: { type: String, default: null },
      /** 配套项目名称（只用于提示文案） */
      ptName: { type: String, default: '' }
    },
    data () {
      return {
        visible: false,
        saving: false,
        form: this.$form.createForm(this),
        // ---- 当前环节（只读展示） ----
        stageId: null,
        stageName: '',
        lcId: null,
        lcName: '',
        processTime: null,
        zgbm: '',
        isParallel: null,
        // ---- 表单模型 ----
        model: this.emptyModel(),
        /** 用户是否手工改过预计结束时间（改过就不再自动覆盖） */
        expectEndManual: false,
        /** 推算结果提示 */
        calcHint: '',
        calcHintType: 'info',
        phoneError: '',
        statusOptions: ['未开启', '进行中', '已完成', '不涉及'],
        issueOptions: [
          { value: '审批问题', label: '审批问题' },
          { value: '资金问题', label: '资金问题' },
          { value: '权属问题', label: '权属问题' },
          { value: '管线问题', label: '管线问题' },
          { value: '地质问题', label: '地质问题' }
        ]
      }
    },
    computed: {
      title () {
        return this.lcName ? `环节进度 · ${this.lcName}` : '环节进度填报'
      }
    },
    methods: {
      emptyModel () {
        return {
          lcqk: '未开启',
          lckssj: null,
          yjjssj: null,
          lcjssj: null,
          czwtlx: undefined,
          jtwt: '',
          gzjy: '',
          lrdw: '',
          lrr: '',
          lxdh: ''
        }
      },

      /**
       * 打开弹窗。
       *
       * @param {object} stage 阶段节点（含 stageName）
       * @param {object} item  环节节点（含 lcId/lcName/processTime/zgbm/isParallel 与已有进度）
       */
      open (stage, item) {
        this.stageId = stage ? stage.stageId : null
        this.stageName = stage ? stage.stageName : ''
        this.lcId = item.lcId
        this.lcName = item.lcName
        this.processTime = item.processTime
        this.zgbm = item.zgbm
        this.isParallel = item.isParallel
        // ★ 已填报的环节回填原值；未填报的给一个合理默认（进行中）
        this.model = {
          lcqk: item.lcqk && item.lcqk !== '未开启' ? item.lcqk : (item.filled ? item.lcqk : '进行中'),
          lckssj: item.lckssj || null,
          yjjssj: item.yjjssj || null,
          lcjssj: item.lcjssj || null,
          czwtlx: item.czwtlx || undefined,
          jtwt: item.jtwt || '',
          gzjy: item.gzjy || '',
          lrdw: item.lrdw || '',
          lrr: item.lrr || '',
          lxdh: item.lxdh || ''
        }
        // 已经有预计结束时间（后端算的或上次手工填的）视为「手工」，
        // 避免用户改开始时间时把它冲掉 —— 这是最容易让人白填一次的坑
        this.expectEndManual = !!item.yjjssj
        this.calcHint = ''
        this.calcHintType = 'info'
        this.phoneError = ''
        this.visible = true
      },

      handleClose () {
        this.visible = false
      },

      /**
       * 开始时间变化：自动推算预计结束时间。
       *
       * ★ 只在「用户没手工改过」且「开始时间有值」时才推算并回填。
       */
      handleStartChange () {
        if (!this.model.lckssj) {
          this.calcHint = ''
          return
        }
        if (this.expectEndManual && this.model.yjjssj) {
          this.calcHint = '预计结束时间已手工填写，不会随开始时间自动变化；如需重算请先清空它。'
          this.calcHintType = 'info'
          return
        }
        const days = this.processTime || 0
        calcExpectEnd(this.model.lckssj, days).then(res => {
          if (!res.success || !res.result) {
            return
          }
          const result = res.result
          this.model.yjjssj = result.endDate
          const based = result.calendarBased
          this.calcHintType = based ? 'success' : 'warning'
          this.calcHint = `按「${result.startDate} + ${result.workdays} 个工作日」推算，预计 ${result.endDate} 结束。` +
            (based
              ? '已使用中心维护的非工作日日历。'
              : '★ 该年份尚未维护非工作日日历，本次按「周末 + 内置法定节假日」估算，与实际放假安排可能有偏差。')
        }).catch(() => {
          // 推算失败不阻断填报：用户仍可手工选日期
          this.calcHint = '预计结束时间自动推算失败，请手工选择日期。'
          this.calcHintType = 'warning'
        })
      },

      /** 本地校验（与后端同一套规则，目的是让用户立刻看到问题而不是提交后才报错） */
      validate () {
        this.phoneError = ''
        if (!this.model.lcqk) {
          this.$message.warning('请选择环节情况')
          return false
        }
        if (this.model.lcqk === '已完成' && !this.model.lcjssj) {
          this.$message.warning('环节情况为「已完成」时必须填写环节实际结束时间')
          return false
        }
        if (this.model.lckssj && this.model.lcjssj && this.model.lcjssj < this.model.lckssj) {
          this.$message.warning('环节实际结束时间不能早于开始时间')
          return false
        }
        const phone = (this.model.lxdh || '').trim()
        if (phone && !/^1[3456789]\d{9}$/.test(phone)) {
          this.phoneError = '联系电话格式不正确，应为 1 开头的 11 位手机号'
          this.$message.warning(this.phoneError)
          return false
        }
        return true
      },

      handleSave () {
        if (!this.ptId) {
          this.$message.error('缺少配套项目ID，无法保存')
          return
        }
        if (!this.validate()) {
          return
        }
        this.saving = true
        const payload = Object.assign({}, this.model, { lcName: this.lcName })
        saveProcess(this.ptId, this.lcId, payload).then(res => {
          if (!res.success) {
            this.$message.error(res.message || '保存失败')
            return
          }
          this.$message.success('已保存')
          this.visible = false
          this.$emit('ok')
        }).catch(e => {
          this.$message.error((e && e.message) || '保存失败')
        }).finally(() => {
          this.saving = false
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .process-item {
    &__head {
      padding: 10px 12px;
      margin-bottom: 14px;
      background: #f8fafc;
      border: 1px solid @border-color;
      border-radius: 8px;
    }

    &__stage {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }

    &__lcname {
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
    }

    &__meta {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin-top: 6px;
      font-size: 12px;
      color: @text-weak;

      b {
        color: #0f172a;
      }
    }

    &__calc {
      margin-bottom: 14px;
    }

    &__form {
      /deep/ .ant-form-item {
        margin-bottom: 12px;
      }
    }

    .form-item__hint {
      margin-top: 4px;
      font-size: 12px;
      line-height: 18px;
      color: @text-weak;
    }

    .form-item__help {
      margin-left: 4px;
      color: @text-weak;
    }
  }
</style>
