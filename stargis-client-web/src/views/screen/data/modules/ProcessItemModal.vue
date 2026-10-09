<template>
  <!--
    ProcessItemModal 环节进度录入 / 编辑（一个事项）
    --------------------------------
    本弹窗只做一件事：把**一个环节**的进度填清楚。字段与后端
    `FacilityAdminServiceImpl.saveProcess` 接受的 payload 一一对应：
      lcqk（环节情况，必填）、lckssj（开始）、yjjssj（预计结束）、
      lcjssj（实际结束）、czwtlx（问题类型）、jtwt（具体问题）、
      gzjy（工作建议）、lrdw / lrr / lxdh（录入单位 / 人 / 电话）

    ★★ 「预计结束时间」的自动带出规则（这是本弹窗最需要解释的一段）
      · 用户改了「环节开始时间」且**没有手工改过预计结束时间**时才自动带出；
        一旦用户手工改过（`expectOverridden`），此后无论开始时间怎么变都不再覆盖 ——
        否则用户精心调整过的预计完成日会被下一次改动悄悄冲掉。
      · 天数取该环节的**标准办理时长**（processTime，工作日），
        由调用方从环节配置里带进来；没有配置时不自动带出（宁可留空让用户填，
        也不要拿一个编出来的天数算出一个看起来很像真的日期）。
      · 自动带出走后端 `/expectEnd` 而不是前端自己加天数：
        工作日推算要剔除周末与节假日日历，这套算法在后端 `WorkdayCalculator` 里
        是唯一实现（旧递归算法在长假跨区间时会滚雪球，后端已经重写）。
      · ★ `calendarBased === false` 必须**明确警告**：它表示该年度还没有人工维护
        节假日日历，后端只剔除了周末，结果会偏乐观。不提示的话，用户会把这个日期
        当成「含节假日」的正式口径，一路报上去。

    ★ 服务端的状态—时间自洽校验（本弹窗提前镜像，少一次往返）：
      「已完成」必须填实际结束时间；实际结束时间不得早于开始时间。

    公开方法：
      open(payload)  打开并编辑一个事项
        payload = { ptId, facilityName, lcId, lcName, stageName, processTime,
                    lcqk, lckssj, yjjssj, lcjssj, czwtlx, jtwt, gzjy,
                    lrdw, lrr, lxdh, filled, progressId }
    事件：
      saved (item, id)  保存成功，抛出最新的进度字段（父组件据此更新本地树，避免整树重拉）
      withdrawn(lcId)   撤回成功
  -->
  <screen-modal
    :visible.sync="visible"
    :title="title"
    :width="880"
    :show-footer="false"
    :body-max-height="'calc(100vh - 200px)'"
    @cancel="handleClose"
  >
    <div class="process-item">
      <!-- 环节身份：让用户在填写时始终知道「这是哪个阶段的哪个事项」 -->
      <header class="process-item__head">
        <span class="process-item__stage">{{ context.stageName || '—' }}</span>
        <h3 class="process-item__name">{{ context.lcName || '—' }}</h3>
        <span class="process-item__meta">
          <screen-tag v-if="context.zgbm" tone="muted" size="sm">主管部门：{{ context.zgbm }}</screen-tag>
          <screen-tag v-if="context.processTime" tone="info" size="sm">
            标准时长 {{ context.processTime }} 个工作日
          </screen-tag>
          <screen-tag v-if="!context.filled" tone="warning" size="sm">未填报</screen-tag>
        </span>
      </header>

      <p v-if="!context.processTime" class="process-item__alert is-info">
        <screen-icon name="info" :size="14" />
        该环节没有配置标准办理时长，因此改开始时间时不会自动带出预计结束时间，请手工填写。
      </p>

      <!--
        节假日日历缺失的警告：只在真的用过一次 /expectEnd 且后端回了
        calendarBased=false 时出现。挂在最显眼的位置（状态字段上方），
        因为它直接影响用户对「预计结束时间」这个数字的信任度。
      -->
      <p v-if="calendarWarning" class="process-item__alert is-warning" role="alert">
        <screen-icon name="alert-triangle" :size="14" />
        {{ calendarWarning }}
      </p>

      <div class="process-item__grid">
        <screen-field label="环节情况" required :error="errors.lcqk" label-width="112px">
          <screen-radio-group
            v-model="model.lcqk"
            :options="statusOptions"
            aria-label="环节情况"
          />
        </screen-field>

        <screen-field label="存在问题类型" label-width="112px">
          <screen-select
            v-model="model.czwtlx"
            :options="issueOptions"
            placeholder="无问题可留空"
            aria-label="存在问题类型"
          />
        </screen-field>

        <screen-field label="环节开始时间" :error="errors.lckssj" label-width="112px">
          <screen-date-input
            v-model="model.lckssj"
            :invalid="!!errors.lckssj"
            placeholder="请选择开始时间"
            @change="handleStartChange"
          />
        </screen-field>

        <screen-field
          label="预计结束时间"
          label-width="112px"
          :tip="expectTip"
        >
          <div class="process-item__inline">
            <screen-date-input
              v-model="model.yjjssj"
              :placeholder="expectLoading ? '正在按工作日推算…' : '可手工修改'"
              @change="handleExpectManualChange"
            />
            <screen-button
              size="sm"
              icon="rotate-ccw"
              :loading="expectLoading"
              :disabled="!model.lckssj || !context.processTime"
              @click="fetchExpectEnd(true)"
            >
              重新推算
            </screen-button>
          </div>
        </screen-field>

        <screen-field label="实际结束时间" :error="errors.lcjssj" label-width="112px">
          <screen-date-input
            v-model="model.lcjssj"
            :invalid="!!errors.lcjssj"
            placeholder="办结后填写"
          />
        </screen-field>

        <screen-field label="录入单位" html-for="pi-lrdw" label-width="112px">
          <screen-input id="pi-lrdw" v-model="model.lrdw" :maxlength="100" placeholder="录入单位" />
        </screen-field>

        <screen-field label="录入人" html-for="pi-lrr" label-width="112px">
          <screen-input id="pi-lrr" v-model="model.lrr" :maxlength="100" placeholder="姓名" />
        </screen-field>

        <screen-field label="联系电话" :error="errors.lxdh" html-for="pi-lxdh" label-width="112px">
          <screen-input
            id="pi-lxdh"
            v-model="model.lxdh"
            :maxlength="100"
            :invalid="!!errors.lxdh"
            placeholder="11 位手机号"
          />
        </screen-field>

        <screen-field
          class="process-item__span"
          label="具体问题"
          html-for="pi-jtwt"
          label-width="112px"
          tip="状态选「有问题」时这里必须写清楚是什么问题"
        >
          <screen-input
            id="pi-jtwt"
            v-model="model.jtwt"
            type="textarea"
            :rows="3"
            :maxlength="1000"
            placeholder="例如：北侧燃气管道未迁改，影响路基施工"
          />
        </screen-field>

        <screen-field
          class="process-item__span"
          label="工作建议"
          html-for="pi-gzjy"
          label-width="112px"
        >
          <screen-input
            id="pi-gzjy"
            v-model="model.gzjy"
            type="textarea"
            :rows="2"
            :maxlength="1000"
            placeholder="例如：建议协调燃气公司提前迁改"
          />
        </screen-field>
      </div>

      <footer class="process-item__foot">
        <span class="process-item__hint">
          <template v-if="context.progressId">
            该环节已录入过进度，保存会**更新**原记录并留下字段级履历。
          </template>
          <template v-else>该环节还没录入过进度，保存会**新增**一条记录。</template>
        </span>

        <div class="process-item__foot-actions">
          <screen-popconfirm
            v-if="context.progressId"
            title="撤回后该环节回到「未填报」，已录入的进度会被清空（会留一条撤回履历），确定吗？"
            :description="context.lcName || ''"
            width="320"
            :confirm-loading="withdrawing"
            @confirm="handleWithdraw"
          >
            <screen-button type="danger" icon="rotate-ccw" :disabled="saving">撤回</screen-button>
          </screen-popconfirm>

          <screen-button icon="close" :disabled="saving" @click="handleClose">取消</screen-button>
          <screen-button type="primary" icon="check" :loading="saving" @click="handleSave">
            保存本环节
          </screen-button>
        </div>
      </footer>
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
  ScreenTag,
  ScreenIcon,
  ScreenPopconfirm
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import { saveProcess, deleteProcess, queryExpectEnd } from '@/api/land/facilityAdmin'
import { queryLandDictItems } from '@/api/land/landAdmin'
import { PROCESS_STATUSES, PROCESS_ISSUES, dictDefinitions } from '../constants'

/** 后端强校验的手机号规则 */
const PHONE_PATTERN = /^1[3456789]\d{9}$/

/** 一个事项的进度字段空模型（与 saveProcess 接受的 payload 一致） */
function emptyModel () {
  return {
    lcqk: '未开启',
    lckssj: '',
    yjjssj: '',
    lcjssj: '',
    czwtlx: '',
    jtwt: '',
    gzjy: '',
    lrdw: '',
    lrr: '',
    lxdh: ''
  }
}

export default {
  name: 'ProcessItemModal',
  components: {
    ScreenModal,
    ScreenField,
    ScreenInput,
    ScreenSelect,
    ScreenRadioGroup,
    ScreenDateInput,
    ScreenButton,
    ScreenTag,
    ScreenIcon,
    ScreenPopconfirm
  },
  data () {
    return {
      visible: false,
      saving: false,
      withdrawing: false,
      expectLoading: false,
      /** 当前编辑的事项上下文（含 ptId / lcId / 标准时长 / 主管部门…） */
      context: {},
      model: emptyModel(),
      errors: {},
      /**
       * 用户是否**手工改过**预计结束时间。
       * ★ 这是自动带出的开关：一旦置位，改开始时间就不再覆盖预计结束时间。
       *   放在 data 里而不是计算属性，因为它是「本次编辑过程中发生过什么」的状态，
       *   不是从数据能推出来的。
       */
      expectOverridden: false,
      /** calendarBased=false 时的警告文案（空串表示无警告） */
      calendarWarning: '',
      statusOptions: PROCESS_STATUSES.map((item) => ({ value: item, label: item })),
      issueOptions: PROCESS_ISSUES.map((item) => ({ value: item, label: item }))
    }
  },
  computed: {
    title () {
      return this.context.filled ? '编辑环节进度' : '录入环节进度'
    },
    expectTip () {
      if (!this.context.processTime) {
        return '该环节未配置标准时长，需手工填写'
      }
      if (this.expectOverridden) {
        return '已按你的手工输入保留，改开始时间不会覆盖它（点「重新推算」可恢复自动）'
      }
      return `改「环节开始时间」时自动按 ${this.context.processTime} 个工作日推算，可手工修改`
    }
  },
  created () {
    queryLandDictItems(dictDefinitions()).then((dicts) => {
      if (dicts.processStatus) this.statusOptions = dicts.processStatus
      if (dicts.processIssue) this.issueOptions = dicts.processIssue
    })
  },
  methods: {
    /* ---------------- 对外入口 ---------------- */

    /**
     * 打开并编辑一个事项。
     * @param {object} payload 见组件头注释
     */
    open (payload) {
      const context = payload || {}
      this.context = context
      this.errors = {}
      this.calendarWarning = ''
      this.expectLoading = false
      this.model = Object.assign(emptyModel(), {
        lcqk: context.lcqk || '未开启',
        lckssj: context.lckssj || '',
        yjjssj: context.yjjssj || '',
        lcjssj: context.lcjssj || '',
        czwtlx: context.czwtlx || '',
        jtwt: context.jtwt || '',
        gzjy: context.gzjy || '',
        lrdw: context.lrdw || '',
        lrr: context.lrr || '',
        lxdh: context.lxdh || ''
      })
      // 已录入过的记录里预计结束时间是「后端算过或用户填过的确定值」，
      // 打开时先视为已被覆盖，避免用户一进来改个开始时间就把它冲掉。
      this.expectOverridden = !!(context.yjjssj && context.filled)
      this.visible = true
    },

    handleClose () {
      this.visible = false
    },

    /* ---------------- 预计结束时间的自动带出 ---------------- */

    /** 用户改开始时间：只在没被手工覆盖过时自动推算 */
    handleStartChange () {
      if (this.expectOverridden) return
      this.fetchExpectEnd(false)
    },

    /**
     * 用户手工改预计结束时间 → 置位「已被覆盖」。
     * ★ 但「清空」不该算覆盖：用户清空往往就是想让它重新自动算，
     *   这时把开关放开，下一次改开始时间会重新带出。
     */
    handleExpectManualChange (value) {
      if (!value) {
        this.expectOverridden = false
        return
      }
      this.expectOverridden = true
    },

    /**
     * 调后端按工作日推算。
     * @param {boolean} manual 是否是用户点「重新推算」（是则强制覆盖 + 解除覆盖标记）
     */
    fetchExpectEnd (manual) {
      const startDate = this.model.lckssj
      const days = this.context.processTime
      if (!startDate || !days) {
        if (manual) {
          toast.warning('需要先选择开始时间，且该环节要有标准办理时长')
        }
        return
      }
      this.expectLoading = true
      queryExpectEnd(startDate, days)
        .then((res) => {
          if (!res || !res.success) {
            // 推算失败不阻断填写：用户可以手工填，保存时后端还会再算一次
            if (manual) toast.warning((res && res.message) || '预计结束时间推算失败，请手工填写')
            return
          }
          const data = res.result || {}
          if (data.endDate) {
            this.model.yjjssj = data.endDate
            if (manual) this.expectOverridden = false
          }
          if (data.calendarBased === false) {
            this.calendarWarning = `该年度还没有维护节假日日历，本次预计结束时间只剔除了周末，未剔除法定节假日（结果偏乐观）；请核对后使用，或直接手工填写。${data.maintainedYears ? `已维护的年度：${data.maintainedYears}。` : ''}`
          } else {
            this.calendarWarning = ''
          }
        })
        .catch(() => {
          if (manual) toast.warning('预计结束时间推算失败，请手工填写')
        })
        .finally(() => {
          this.expectLoading = false
        })
    },

    /* ---------------- 校验与保存 ---------------- */

    validateForm () {
      const errors = {}
      if (!this.model.lcqk) {
        errors.lcqk = '请选择环节情况'
      }
      // 镜像后端：已完成必须有实际结束时间；结束不得早于开始
      if (this.model.lcqk === '已完成' && !this.model.lcjssj) {
        errors.lcjssj = '环节情况为「已完成」时必须填写实际结束时间'
      }
      if (this.model.lckssj && this.model.lcjssj && this.model.lcjssj < this.model.lckssj) {
        errors.lcjssj = '实际结束时间不能早于开始时间'
      }
      const lxdh = String(this.model.lxdh || '').trim()
      if (lxdh && !PHONE_PATTERN.test(lxdh)) {
        errors.lxdh = '联系电话格式不正确，应为 1 开头的 11 位手机号'
      }
      this.errors = errors
      return Object.keys(errors).length === 0
    },

    /**
     * 组装 payload。
     * ★ 日期统一传 yyyy-MM-dd（后端 parseDate 接受 - / . 与 yyyyMMdd，
     *   但统一格式能让留痕里的 before/after 保持一致，便于比对）。
     * ★ 空串转 null：后端 asString 会把空串当 null，但显式传 null 更清楚，
     *   也避免「清空某个日期」这类意图被误读。
     */
    buildPayload () {
      return {
        lcqk: this.model.lcqk || null,
        lckssj: this.model.lckssj || null,
        yjjssj: this.model.yjjssj || null,
        lcjssj: this.model.lcjssj || null,
        czwtlx: this.model.czwtlx || null,
        jtwt: this.model.jtwt || null,
        gzjy: this.model.gzjy || null,
        lrdw: this.model.lrdw || null,
        lrr: this.model.lrr || null,
        lxdh: this.model.lxdh || null
      }
    },

    handleSave () {
      if (!this.validateForm()) {
        toast.warning('还有必填项未完成，请检查标红的字段')
        return
      }
      if (!this.context.ptId || !this.context.lcId) {
        toast.error('缺少配套项目或环节标识，无法保存')
        return
      }
      this.saving = true
      const payload = this.buildPayload()
      saveProcess(this.context.ptId, this.context.lcId, payload)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '环节进度保存失败')
            return
          }
          toast.success(res.message || '环节进度已保存')
          this.visible = false
          // 把最新的进度字段抛给父组件：父组件据此就地更新那一行，
          // 不必为了一个字段重拉整棵进度树
          this.$emit('saved', Object.assign({}, this.context, payload, {
            filled: true,
            progressId: res.result || this.context.progressId
          }), res.result)
        })
        .catch(() => {
          // 请求层已提示
        })
        .finally(() => {
          this.saving = false
        })
    },

    handleWithdraw () {
      if (!this.context.ptId || !this.context.lcId) return
      this.withdrawing = true
      deleteProcess(this.context.ptId, this.context.lcId)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '撤回失败')
            return
          }
          toast.success(res.message || '已撤回，该环节回到未填报')
          this.visible = false
          this.$emit('withdrawn', this.context.lcId)
        })
        .catch(() => {})
        .finally(() => {
          this.withdrawing = false
        })
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.process-item {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-3);

  &__head {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    flex-wrap: wrap;
    padding-bottom: var(--screen-space-2);
    border-bottom: 1px solid var(--screen-border-soft);
  }

  &__stage {
    padding: 2px 8px;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-on-accent);
    background: var(--screen-accent-deep);
    border-radius: var(--screen-radius-pill);
  }

  &__name {
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 600;
    color: var(--screen-text);
  }

  &__meta {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
    margin-left: auto;
  }

  &__alert {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    padding: 8px 10px;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);

    &.is-warning {
      color: var(--screen-warning);
      background: rgba(245, 165, 36, 0.08);
      border-color: var(--screen-warning);
    }

    &.is-info {
      color: var(--screen-accent-soft);
      background: rgba(130, 198, 255, 0.08);
    }
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--screen-space-3) var(--screen-space-4);
  }

  /* 长文本字段跨两列 */
  &__span {
    grid-column-start: 1;
    grid-column-end: -1;
  }

  &__inline {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    min-width: 0;

    /deep/ .screen-date-input {
      flex: 1 1 auto;
      min-width: 0;
    }

    /deep/ .screen-btn {
      flex: 0 0 auto;
    }
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--screen-space-3);
    flex-wrap: wrap;
    padding-top: var(--screen-space-3);
    border-top: 1px solid var(--screen-border-soft);
  }

  &__hint {
    flex: 1 1 260px;
    min-width: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.7;
    color: var(--screen-text-mute);

    b {
      color: var(--screen-accent-soft);
    }
  }

  &__foot-actions {
    display: inline-flex;
    align-items: center;
    gap: var(--screen-space-2);
  }
}

@media (max-width: 900px) {
  .process-item__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
