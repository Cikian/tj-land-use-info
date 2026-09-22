<template>
  <!--
    DocFlowModal 收文流转弹窗（「收文中心内的流转」）
    --------------------------------
    精简三级流转 + 退回：
      登记 ─► 待承办 ─转办/分办─► 承办中 ─办结─► 已办结 ─归档─► 已归档
                       ▲              │
                       └─── 退回 ─────┘

    三个动作合成一个界面，但背后是三个语义化接口（transfer / reject / finish），
    校验分别挂在各自动作上：
      · 转办：必须选接收人，且不能转给自己（选择器已排除当前账号）
      · 退回：必须填写原因
      · 办结：意见可留空；办结后由父组件弹「是否立即归档」

    公开方法：
      open(record)  按列表行打开（内部再按 id 拉详情与流转记录）
    事件：
      finished(doc) 办结成功时抛出，父组件据此提示是否归档
  -->
  <screen-modal
    :visible.sync="visible"
    title="收文流转"
    :width="880"
    :show-footer="false"
    :body-max-height="'calc(100vh - 200px)'"
    @cancel="close"
  >
    <screen-loading :loading="loading" text="正在加载流转信息…" :overlay="false">
      <div v-if="doc" class="doc-flow">
        <!-- ================= 公文概要 ================= -->
        <div class="doc-flow__head">
          <div class="doc-flow__head-main">
            <span class="doc-flow__no">{{ doc.docNo }}</span>
            <span class="doc-flow__title" :title="doc.docTitle">{{ doc.docTitle }}</span>
          </div>
          <div class="doc-flow__head-tags">
            <screen-tag :tone="docStatusTone(doc.status)" size="sm">{{ doc.status || '—' }}</screen-tag>
            <screen-tag :tone="doc.archiveId ? 'success' : 'muted'" size="sm">
              {{ doc.archiveId ? '已归档' : '未归档' }}
            </screen-tag>
            <screen-tag v-if="doc.urgency" :tone="docUrgencyTone(doc.urgency)" size="sm">
              {{ doc.urgency }}
            </screen-tag>
          </div>
        </div>

        <screen-descriptions :items="summaryItems" :columns="3" label-width="96px" />

        <!-- ================= 流转时间轴 ================= -->
        <section class="doc-flow__section">
          <h4 class="doc-flow__section-title">
            <screen-icon name="clock" :size="14" />
            流转痕迹
            <span class="doc-flow__section-sub">共 {{ flows.length }} 步</span>
          </h4>

          <ol v-if="flows.length" class="doc-flow__timeline">
            <li
              v-for="(item, index) in flows"
              :key="item.id || index"
              class="doc-flow__step"
              :class="`is-${docFlowTone(item.action, !item.handleTime)}`"
            >
              <span class="doc-flow__dot" aria-hidden="true" />
              <div class="doc-flow__step-body">
                <div class="doc-flow__step-head">
                  <span class="doc-flow__step-action">{{ item.action || '—' }}</span>
                  <span class="doc-flow__step-handler">{{ item.handlerName || item.handler || '—' }}</span>
                  <screen-tag v-if="!item.handleTime" tone="info" size="sm">待办</screen-tag>
                </div>
                <p class="doc-flow__step-opinion">{{ item.opinion || '（未填写意见）' }}</p>
                <p class="doc-flow__step-time">
                  送达 {{ item.receiveTime || '—' }}
                  <template v-if="item.handleTime"> · 处理 {{ item.handleTime }}</template>
                </p>
              </div>
            </li>
          </ol>
          <screen-empty v-else text="暂无流转记录" size="sm" />
        </section>

        <!-- ================= 流转操作 ================= -->
        <section v-if="!isClosed" class="doc-flow__section doc-flow__section--action">
          <h4 class="doc-flow__section-title">
            <screen-icon name="sliders" :size="14" />
            流转操作
            <span class="doc-flow__section-sub">
              {{ isMyTurn ? '当前待办在你手上' : '当前没有待办人（待分办），你可以直接分办给他人' }}
            </span>
          </h4>

          <screen-radio-group
            v-model="action"
            class="doc-flow__actions"
            :options="DOC_FLOW_ACTIONS"
            aria-label="选择流转动作"
          />

          <div class="doc-flow__form">
            <screen-field
              v-if="action === 'transfer'"
              label="转办给"
              required
              :label-width="'84px'"
              :error="errors.toUsername"
            >
              <doc-user-select
                v-model="form.toUsername"
                :exclude-username="currentUsername"
                placeholder="按姓名 / 账号搜索接收人"
              />
            </screen-field>

            <screen-field
              :label="action === 'reject' ? '退回原因' : '处理意见'"
              :required="action === 'reject'"
              :label-width="'84px'"
              :error="errors.opinion"
              :tip="action === 'reject' ? '退回必须说明原因' : '填写办理说明 / 批注，可留空'"
            >
              <screen-input
                v-model="form.opinion"
                type="textarea"
                :rows="3"
                :maxlength="2000"
                :invalid="!!errors.opinion"
                :placeholder="action === 'reject' ? '请写明退回理由，便于对方修改后重报' : '可留空'"
              />
            </screen-field>
          </div>

          <div class="doc-flow__footer">
            <screen-button @click="close">关闭</screen-button>
            <screen-button type="primary" :loading="submitting" @click="handleSubmit">
              {{ submitText }}
            </screen-button>
          </div>
        </section>

        <!-- ================= 已结束 ================= -->
        <section v-else class="doc-flow__section doc-flow__section--done">
          <screen-icon name="check-circle" :size="16" />
          <span>
            {{ doc.archiveId ? '该收文已办结并归档，流程结束。' : '该收文已办结，可在列表点「归档」把它归入档案。' }}
          </span>
        </section>
      </div>
    </screen-loading>
  </screen-modal>
</template>

<script>
import {
  ScreenModal,
  ScreenLoading,
  ScreenDescriptions,
  ScreenTag,
  ScreenEmpty,
  ScreenRadioGroup,
  ScreenField,
  ScreenInput,
  ScreenButton,
  ScreenIcon,
} from '@/components/screen'
import { toast } from '@/components/screen/toast'
import DocUserSelect from './DocUserSelect.vue'
import {
  queryDocReceiveById,
  transferDocReceive,
  rejectDocReceive,
  finishDocReceive,
} from '@/api/land/document'
import { DOC_FLOW_ACTIONS, docStatusTone, docFlowTone, docUrgencyTone, joinInfo } from '../../constants'

export default {
  name: 'DocFlowModal',
  components: {
    ScreenModal,
    ScreenLoading,
    ScreenDescriptions,
    ScreenTag,
    ScreenEmpty,
    ScreenRadioGroup,
    ScreenField,
    ScreenInput,
    ScreenButton,
    ScreenIcon,
    DocUserSelect,
  },
  data () {
    return {
      DOC_FLOW_ACTIONS,
      visible: false,
      loading: false,
      submitting: false,
      doc: null,
      flows: [],
      action: 'transfer',
      form: {
        toUsername: '',
        opinion: '',
      },
      errors: {},
    }
  },
  computed: {
    /** 当前登录账号：流转记录按 username 存储，所以要用 username 比较 */
    currentUsername () {
      const info = (this.$store && this.$store.getters && this.$store.getters.userInfo) || {}
      return info.username || ''
    },
    /** 已办结 / 已归档不再提供流转操作 */
    isClosed () {
      if (!this.doc) return true
      return this.doc.status === '已办结' || this.doc.status === '已归档'
    },
    /** 当前待办是否在自己手上（用于给出更准确的提示文案） */
    isMyTurn () {
      if (!this.doc) return false
      const openFlow = this.flows.find((item) => !item.handleTime)
      if (!openFlow) return false
      return openFlow.handler === this.currentUsername
    },
    submitText () {
      if (this.action === 'transfer') return '确认转办'
      if (this.action === 'reject') return '确认退回'
      return '确认办结'
    },
    summaryItems () {
      const data = this.doc || {}
      return [
        { key: 'fromDept', label: '来文单位', value: data.fromDept },
        { key: 'receiveDate', label: '来文日期', value: data.receiveDate },
        { key: 'ptxmmc', label: '关联项目', value: data.ptxmmc },
        { key: 'handler', label: '承办人（当前）', value: data.currentHandlerName },
        { key: 'deadline', label: '办理期限', value: data.handleDeadline },
        { key: 'finishTime', label: '办结时间', value: data.finishTime },
        { key: 'fromDocNo', label: '来文字号', value: data.fromDocNo },
        { key: 'secretLevel', label: '密级', value: data.secretLevel },
        { key: 'ptsslb', label: '项目地址', value: joinInfo(data.crzdbh, data.dkmc) },
      ]
    },
  },
  watch: {
    /** 切换动作时清空意见与上一动作的校验错误，避免把退回原因带到办结上 */
    action () {
      this.form.opinion = ''
      this.errors = {}
    },
  },
  methods: {
    docStatusTone,
    docFlowTone,
    docUrgencyTone,

    /* ---------------- 对外入口 ---------------- */

    open (record) {
      if (!record || !record.id) {
        toast.warning('请选择要流转的收文')
        return
      }
      this.visible = true
      this.action = 'transfer'
      this.form = { toUsername: '', opinion: '' }
      this.errors = {}
      this.load(record.id)
    },

    load (id) {
      this.loading = true
      return queryDocReceiveById(id)
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '流转信息加载失败')
            this.visible = false
            return
          }
          this.doc = res.result || null
          this.flows = (this.doc && this.doc.flows) || []
        })
        .finally(() => {
          this.loading = false
        })
    },

    /* ---------------- 校验与提交 ---------------- */

    validate () {
      const errors = {}
      if (this.action === 'transfer' && !this.form.toUsername) {
        errors.toUsername = '请选择转办接收人'
      }
      if (this.action === 'reject' && !String(this.form.opinion || '').trim()) {
        errors.opinion = '退回必须填写原因'
      }
      if (this.action === 'transfer' && this.form.toUsername === this.currentUsername) {
        errors.toUsername = '不能把待办转办给自己'
      }
      this.errors = errors
      return Object.keys(errors).length === 0
    },

    handleSubmit () {
      if (!this.validate()) {
        toast.warning('还有必填项未完成')
        return
      }

      const payload = { docId: this.doc.id, opinion: this.form.opinion || undefined }
      let task
      if (this.action === 'transfer') {
        task = transferDocReceive(Object.assign(payload, { toUsername: this.form.toUsername }))
      } else if (this.action === 'reject') {
        task = rejectDocReceive(payload)
      } else {
        task = finishDocReceive(payload)
      }

      this.submitting = true
      task
        .then((res) => {
          if (!res || !res.success) {
            toast.error((res && res.message) || '操作失败')
            return
          }
          toast.success(res.message || '操作成功')
          if (this.action === 'finish') {
            // 办结后由父组件负责「是否立即归档」的确认
            this.visible = false
            this.$emit('finished', this.doc)
          } else {
            // 转办 / 退回后停留在弹窗内刷新，方便连续操作
            this.load(this.doc.id)
            this.form = { toUsername: '', opinion: '' }
          }
        })
        .catch(() => {
          // 错误提示已由请求层处理
        })
        .finally(() => {
          this.submitting = false
        })
    },

    close () {
      this.visible = false
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.doc-flow {
  display: flex;
  flex-direction: column;
  gap: var(--screen-space-4);

  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: var(--screen-space-3);
    padding-bottom: var(--screen-space-2);
    border-bottom: 1px solid var(--screen-border-soft);
  }

  &__head-main {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  &__no {
    font-family: var(--screen-font-number-family);
    font-variant-numeric: tabular-nums;
    font-size: var(--screen-font-sm);
    letter-spacing: 0.04em;
    color: var(--screen-accent);
  }

  &__title {
    font-size: var(--screen-font-lg);
    font-weight: 600;
    color: var(--screen-text);
    .screen-ellipsis();
  }

  &__head-tags {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
  }

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
    padding: var(--screen-space-3);
    background: rgba(6, 20, 40, 0.5);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius);

    &--action {
      background: rgba(20, 48, 90, 0.45);
    }

    &--done {
      flex-direction: row;
      align-items: center;
      gap: var(--screen-space-2);
      font-size: var(--screen-font-sm);
      color: var(--screen-text-sub);
      border-color: rgba(67, 233, 114, 0.3);

      /deep/ .screen-icon {
        color: var(--screen-success);
      }
    }
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

  &__section-sub {
    margin-left: 4px;
    font-size: var(--screen-font-xs);
    font-weight: 400;
    color: var(--screen-text-mute);
  }

  /* ---------------- 时间轴 ---------------- */
  &__timeline {
    position: relative;
    margin: 0;
    padding: 0 0 0 18px;
    list-style: none;

    /* 竖线 */
    &::before {
      content: '';
      position: absolute;
      top: 6px;
      bottom: 6px;
      left: 5px;
      width: 1px;
      background: var(--screen-border);
    }
  }

  &__step {
    position: relative;
    padding: 0 0 var(--screen-space-3) 0;

    &:last-child {
      padding-bottom: 0;
    }
  }

  &__dot {
    position: absolute;
    top: 5px;
    left: -17px;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: var(--screen-bar-muted-to);
    box-shadow: 0 0 0 3px var(--screen-panel-bg-solid);
  }

  /* 节点语气：待办 / 退回 / 办结 / 其他 */
  &__step.is-info .doc-flow__dot {
    background: var(--screen-info);
    box-shadow: 0 0 0 3px var(--screen-panel-bg-solid), 0 0 8px var(--screen-info);
  }

  &__step.is-danger .doc-flow__dot {
    background: var(--screen-danger);
    box-shadow: 0 0 0 3px var(--screen-panel-bg-solid), 0 0 8px var(--screen-danger);
  }

  &__step.is-success .doc-flow__dot {
    background: var(--screen-success);
    box-shadow: 0 0 0 3px var(--screen-panel-bg-solid), 0 0 8px var(--screen-success);
  }

  &__step-body {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  &__step-head {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    flex-wrap: wrap;
    font-size: var(--screen-font-sm);
  }

  &__step-action {
    font-weight: 600;
    color: var(--screen-text);
  }

  &__step-handler {
    color: var(--screen-text-sub);
  }

  &__step-opinion {
    margin: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.6;
    color: var(--screen-text-sub);
    word-break: break-word;
  }

  &__step-time {
    margin: 0;
    font-size: var(--screen-font-xs);
    color: var(--screen-text-mute);
  }

  /* ---------------- 操作区 ---------------- */
  &__actions {
    align-self: flex-start;
  }

  &__form {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-3);
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: var(--screen-space-2);
    padding-top: var(--screen-space-2);
    border-top: 1px solid var(--screen-border-soft);
  }
}
</style>
