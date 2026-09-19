<template>
  <a-modal
    :title="title"
    :width="860"
    :visible="visible"
    :footer="null"
    :maskClosable="false"
    :destroyOnClose="true"
    @cancel="close">
    <a-spin :spinning="loading">
      <template v-if="doc">
        <!-- 公文概要 -->
        <div class="flow-head">
          <div class="flow-head__main">
            <div class="flow-head__no">{{ doc.docNo }}</div>
            <div class="flow-head__title" :title="doc.docTitle">{{ doc.docTitle }}</div>
          </div>
          <div class="flow-head__tags">
            <a-tag :color="statusColor(doc.status)">{{ doc.status }}</a-tag>
            <a-tag v-if="doc.archiveId" color="green">已归档</a-tag>
            <a-tag v-else>未归档</a-tag>
          </div>
        </div>

        <a-descriptions size="small" bordered :column="{ xxl: 3, xl: 3, lg: 3, md: 2, sm: 1, xs: 1 }" class="flow-desc">
          <a-descriptions-item label="来文单位">{{ doc.fromDept || '—' }}</a-descriptions-item>
          <a-descriptions-item label="来文日期">{{ doc.receiveDate || '—' }}</a-descriptions-item>
          <a-descriptions-item label="关联项目">{{ doc.ptxmmc || '—' }}</a-descriptions-item>
          <a-descriptions-item label="承办人（当前）">{{ doc.currentHandlerName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="办理期限">{{ doc.handleDeadline || '—' }}</a-descriptions-item>
          <a-descriptions-item label="办结时间">{{ doc.finishTime || '—' }}</a-descriptions-item>
        </a-descriptions>

        <!-- 流转时间轴 -->
        <div class="flow-section">
          <div class="flow-section__title">
            <a-icon type="history" />
            <span>流转痕迹（{{ flows.length }} 步）</span>
          </div>
          <a-timeline v-if="flows.length" class="flow-timeline">
            <a-timeline-item
              v-for="(item, index) in flows"
              :key="item.id"
              :color="timelineColor(item, index)">
              <div class="flow-item">
                <div class="flow-item__head">
                  <span class="flow-item__action">{{ item.action }}</span>
                  <span class="flow-item__handler">{{ item.handlerName || item.handler || '—' }}</span>
                  <!-- ★ color 必须是 antd 的预设色（blue/red/green/orange/cyan/gold/...）：
                       写 'processing' 这类状态色会被当成自定义色 → 白字 + 非法背景色被丢弃 → 看不见 -->
                  <a-tag v-if="!item.handleTime" color="blue" class="flow-item__badge">待办</a-tag>
                </div>
                <div class="flow-item__opinion">{{ item.opinion || '（未填写意见）' }}</div>
                <div class="flow-item__time">
                  送达 {{ item.receiveTime || '—' }}
                  <template v-if="item.handleTime"> · 处理 {{ item.handleTime }}</template>
                </div>
              </div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="暂无流转记录" />
        </div>

        <!-- 操作区 -->
        <div v-if="canOperate" class="flow-section flow-section--action">
          <div class="flow-section__title">
            <a-icon type="solution" />
            <span>流转操作</span>
            <span class="flow-section__hint">
              {{ isMyTurn ? '当前待办在你手上' : '当前没有待办人（待分办），你可以直接分办给他人' }}
            </span>
          </div>

          <a-radio-group v-model="action" buttonStyle="solid" class="flow-actions">
            <a-radio-button v-has="'land:docReceive:transfer'" value="transfer">转办 / 分办</a-radio-button>
            <a-radio-button v-has="'land:docReceive:reject'" value="reject">退回</a-radio-button>
            <a-radio-button v-has="'land:docReceive:finish'" value="finish">办结</a-radio-button>
          </a-radio-group>

          <a-form-model
            ref="form"
            :model="form"
            :rules="rules"
            :label-col="{ span: 4 }"
            :wrapper-col="{ span: 20 }"
            class="flow-form">
            <a-form-model-item v-if="action === 'transfer'" label="转办给" prop="toUsername">
              <user-select v-model="form.toUsername" :excludeUsername="currentUsername" placeholder="选择接收人" />
            </a-form-model-item>

            <a-form-model-item :label="action === 'reject' ? '退回原因' : '处理意见'" prop="opinion">
              <a-textarea
                v-model="form.opinion"
                :rows="3"
                :maxLength="2000"
                :placeholder="action === 'reject' ? '退回必须填写原因' : '填写办理说明 / 批注（可留空）'" />
            </a-form-model-item>

            <a-form-model-item :wrapper-col="{ span: 20, offset: 4 }">
              <a-button type="primary" :loading="submitting" @click="handleSubmit">
                {{ submitText }}
              </a-button>
              <a-button class="flow-form__cancel" @click="close">关闭</a-button>
            </a-form-model-item>
          </a-form-model>
        </div>

        <div v-else class="flow-section flow-section--done">
          <a-icon type="check-circle" theme="filled" class="flow-section__done-icon" />
          <span>
            {{ doc.archiveId ? '该收文已办结并归档，流程结束。' : '该收文已办结，可在列表点「归档」把它归入档案。' }}
          </span>
        </div>
      </template>
    </a-spin>
  </a-modal>
</template>

<script>
  import UserSelect from './UserSelect'
  import { queryDocReceiveById, transferDocReceive, rejectDocReceive, finishDocReceive } from '@/api/land/document'
  import { USER_AUTH } from '@/store/mutation-types'

  /**
   * 收文流转弹窗（「收文中心内的流转」）
   *
   * 精简三级流转 + 退回：
   *   登记 ─► 待承办 ─转办/分办─► 承办中 ─办结─► 已办结 ─归档─► 已归档
   *                     ▲              │
   *                     └─── 退回 ─────┘
   *
   * 三个动作合成一个界面，但背后是三个语义化接口（transfer / reject / finish），
   * 校验都挂在各自动作上：转办必须选人且不能转给自己；退回必须写原因；
   * 办结后由父组件弹「是否归档」并强制选择档案类别。
   */
  export default {
    name: 'DocFlowModal',
    components: { UserSelect },
    data () {
      return {
        title: '收文流转',
        visible: false,
        loading: false,
        submitting: false,
        doc: null,
        flows: [],
        action: 'transfer',
        form: {
          toUsername: undefined,
          opinion: ''
        }
      }
    },
    computed: {
      currentUsername () {
        const info = this.$store.getters.userInfo || {}
        return info.username || ''
      },
      /** 是否已办结/归档（办结后不再提供流转操作） */
      isClosed () {
        if (!this.doc) {
          return true
        }
        return this.doc.status === '已办结' || this.doc.status === '已归档'
      },
      canOperate () {
        // 已办结/归档不再允许流转；同时要求至少拥有一个流转动作的权限
        if (!this.doc || this.isClosed) {
          return false
        }
        return this.hasPerm('land:docReceive:transfer') ||
          this.hasPerm('land:docReceive:reject') ||
          this.hasPerm('land:docReceive:finish')
      },
      /** 当前是否有待办，且待办人是自己 */
      isMyTurn () {
        if (!this.doc) {
          return false
        }
        const openFlow = this.flows.find(item => !item.handleTime)
        if (!openFlow) {
          return false
        }
        return openFlow.handler === this.currentUsername
      },
      rules () {
        if (this.action === 'transfer') {
          return {
            toUsername: [{ required: true, message: '请选择转办接收人', trigger: 'change' }]
          }
        }
        if (this.action === 'reject') {
          return {
            opinion: [{ required: true, message: '退回必须填写原因', trigger: 'blur' }]
          }
        }
        return {}
      },
      submitText () {
        if (this.action === 'transfer') {
          return '确认转办'
        }
        if (this.action === 'reject') {
          return '确认退回'
        }
        return '确认办结'
      }
    },
    watch: {
      action () {
        this.form.opinion = ''
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate()
          }
        })
      }
    },
    methods: {
      /**
       * JS 层权限判断（v-has 指令只在 DOM 插入时生效，选默认动作需要代码里判断）。
       * 权限清单来自登录接口返回的 auth 数组，存在 sessionStorage 里。
       */
      hasPerm (code) {
        try {
          const list = JSON.parse(sessionStorage.getItem(USER_AUTH) || '[]')
          return list.some(item => item.action === code)
        } catch (e) {
          return false
        }
      },
      /** 按权限挑一个默认可用的流转动作 */
      resolveDefaultAction () {
        if (this.hasPerm('land:docReceive:transfer')) {
          return 'transfer'
        }
        if (this.hasPerm('land:docReceive:reject')) {
          return 'reject'
        }
        return 'finish'
      },
      open (record) {
        if (!record || !record.id) {
          this.$message.warning('请选择要流转的收文')
          return
        }
        this.visible = true
        this.action = this.resolveDefaultAction()
        this.form = { toUsername: undefined, opinion: '' }
        this.load(record.id)
      },
      load (id) {
        this.loading = true
        return queryDocReceiveById(id).then(res => {
          if (!res.success) {
            this.$message.warning(res.message)
            this.visible = false
            return
          }
          this.doc = res.result || null
          this.flows = (this.doc && this.doc.flows) || []
        }).finally(() => {
          this.loading = false
        })
      },
      handleSubmit () {
        const doSubmit = () => {
          this.submitting = true
          const payload = { docId: this.doc.id, opinion: this.form.opinion || undefined }
          let task
          if (this.action === 'transfer') {
            task = transferDocReceive(Object.assign(payload, { toUsername: this.form.toUsername }))
          } else if (this.action === 'reject') {
            task = rejectDocReceive(payload)
          } else {
            task = finishDocReceive(payload)
          }
          task.then(res => {
            if (res.success) {
              this.$message.success(res.message || '操作成功')
              if (this.action === 'finish') {
                this.visible = false
                this.$emit('finished', this.doc)
              } else {
                this.load(this.doc.id)
              }
            } else {
              this.$message.warning(res.message)
            }
          }).finally(() => {
            this.submitting = false
          })
        }

        const rules = this.rules
        if (Object.keys(rules).length && this.$refs.form) {
          this.$refs.form.validate(valid => {
            if (valid) {
              doSubmit()
            }
          })
        } else {
          doSubmit()
        }
      },
      close () {
        this.visible = false
      },
      timelineColor (item, index) {
        if (!item.handleTime) {
          return 'blue'
        }
        if (item.action === '退回') {
          return 'red'
        }
        if (item.action === '办结') {
          return 'green'
        }
        return index === 0 ? 'green' : 'gray'
      },
      statusColor (status) {
        if (status === '已办结' || status === '已归档') {
          return 'green'
        }
        if (status === '已退回') {
          return 'red'
        }
        if (status === '承办中') {
          return 'cyan'
        }
        return 'orange'
      }
    }
  }
</script>

<style lang="less" scoped>
  @border-color: #e2e8f0;
  @text-muted: #475569;
  @text-weak: #94a3b8;

  .flow-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 10px;
    padding-bottom: 12px;

    &__main {
      min-width: 0;
    }

    &__no {
      font-family: 'DIN Alternate', 'Bebas Neue', monospace;
      font-size: 13px;
      letter-spacing: 1px;
      color: #2e7cf6;
    }

    &__title {
      font-size: 16px;
      font-weight: 600;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__tags {
      display: flex;
      gap: 6px;

      /deep/ .ant-tag {
        margin: 0;
      }
    }
  }

  .flow-desc {
    margin-bottom: 16px;
  }

  .flow-section {
    padding: 16px;
    margin-bottom: 16px;
    border: 1px solid @border-color;
    border-radius: 8px;

    &__title {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 14px;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
    }

    &__hint {
      margin-left: 4px;
      font-size: 12px;
      font-weight: 400;
      color: @text-weak;
    }

    &--action {
      background: #f8fafc;
      margin-bottom: 0;
    }

    &--done {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: @text-muted;
      background: #f6ffed;
      border-color: #b7eb8f;
      margin-bottom: 0;
    }

    &__done-icon {
      color: #52c41a;
      font-size: 16px;
    }
  }

  .flow-timeline {
    /deep/ .ant-timeline-item {
      padding-bottom: 16px;
    }
  }

  .flow-item {
    &__head {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
    }

    &__action {
      font-weight: 600;
      color: #0f172a;
    }

    &__handler {
      color: @text-muted;
    }

    &__badge {
      margin: 0;
      font-size: 11px;
      line-height: 16px;
    }

    &__opinion {
      margin: 4px 0;
      font-size: 13px;
      line-height: 20px;
      color: #334155;
      word-break: break-word;
    }

    &__time {
      font-size: 12px;
      color: @text-weak;
    }
  }

  .flow-actions {
    margin-bottom: 16px;
  }

  .flow-form {
    &__cancel {
      margin-left: 8px;
    }
  }
</style>
