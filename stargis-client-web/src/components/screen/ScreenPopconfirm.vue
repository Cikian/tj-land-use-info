<template>
  <!--
    ScreenPopconfirm 危险操作二次确认
    --------------------------------
    档案删除、批量删除、类别移除等破坏性操作使用。admin-client 里对应 a-popconfirm。

    为什么不用 a-popconfirm：同样是为了暗色主题统一；而且这里需要复用
    ScreenPopover 的「自动翻转到上方」逻辑——底部预警区/表格最后一行的删除按钮
    如果再往下弹气泡会被视口裁掉。

    用法：
      <screen-popconfirm title="删除后不可恢复，确定删除该档案吗？" @confirm="handleDelete(row)">
        <screen-button type="text" icon="trash" tone="danger">删除</screen-button>
      </screen-popconfirm>

    交互约定（重要）：
      触发元素自己的 click 会被本组件在**捕获阶段拦截**，
      因此默认插槽里的按钮不要再挂 @click——确认框负责开关，确认逻辑走 @confirm。
  -->
  <span ref="trigger" class="screen-popconfirm__trigger" @click.capture="handleTriggerClick">
    <slot />

    <screen-popover
      :open="innerOpen"
      :anchor="$refs.trigger"
      :placement="placement"
      :width="width"
      :match-width="false"
      :max-height="null"
      @close="handlePopoverClose"
    >
      <div class="screen-popconfirm" role="dialog" :aria-label="title">
        <div class="screen-popconfirm__head">
          <screen-icon
            class="screen-popconfirm__icon"
            :class="`is-${tone}`"
            :name="tone === 'danger' ? 'alert-triangle' : 'info'"
            :size="15"
          />
          <span class="screen-popconfirm__title">{{ title }}</span>
        </div>

        <p v-if="description" class="screen-popconfirm__desc">{{ description }}</p>

        <div class="screen-popconfirm__foot">
          <screen-button size="sm" @click="handleCancel">{{ cancelText }}</screen-button>
          <screen-button
            size="sm"
            :type="tone === 'danger' ? 'danger' : 'primary'"
            :loading="confirmLoading"
            @click="handleConfirm"
          >
            {{ okText }}
          </screen-button>
        </div>
      </div>
    </screen-popover>
  </span>
</template>

<script>
import ScreenIcon from './ScreenIcon'
import ScreenButton from './ScreenButton'
import ScreenPopover from './ScreenPopover'

export default {
  name: 'ScreenPopconfirm',
  components: { ScreenIcon, ScreenButton, ScreenPopover },
  props: {
    /** 主问句，必须能独立说明「会做什么、后果是什么」 */
    title: { type: String, default: '' },
    /** 补充说明，例如影响范围 */
    description: { type: String, default: '' },
    okText: { type: String, default: '确定' },
    cancelText: { type: String, default: '取消' },
    /** 语气：danger 危险操作（默认） / default 普通确认 */
    tone: { type: String, default: 'danger' },
    /** 确认按钮 loading */
    confirmLoading: { type: Boolean, default: false },
    /** 受控开关（建议 .sync）；不传时组件内部自管 */
    open: { type: Boolean, default: null },
    /** 气泡相对触发元素的位置 */
    placement: { type: String, default: 'top-end' },
    /** 气泡宽度 */
    width: { type: Number, default: 248 },
    disabled: { type: Boolean, default: false },
  },
  data () {
    return {
      innerOpen: false,
    }
  },
  watch: {
    open (val) {
      if (val === null || val === undefined) return
      this.innerOpen = val
    },
  },
  methods: {
    handleTriggerClick (event) {
      if (this.disabled) return
      // 捕获阶段拦住，避免默认插槽里的按钮自己再处理一次点击
      event.stopPropagation()
      event.preventDefault()
      this.toggle()
    },
    toggle () {
      this.setOpen(!this.innerOpen)
    },
    setOpen (value) {
      this.innerOpen = value
      this.$emit('update:open', value)
      this.$emit('open-change', value)
    },
    handlePopoverClose () {
      this.setOpen(false)
    },
    handleConfirm () {
      this.$emit('confirm')
      // 确认后默认收起；父组件若需要保持打开（例如确认失败要重试）可自行再 setOpen(true)
      this.setOpen(false)
    },
    handleCancel () {
      this.setOpen(false)
      this.$emit('cancel')
    },
    /** 供父组件在操作失败后把气泡重新打开 */
    openConfirm () {
      this.setOpen(true)
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

// 触发元素容器：inline-flex 保证按钮与文字基线正常，且不占据多余宽度
.screen-popconfirm__trigger {
  display: inline-flex;
  align-items: center;
}

.screen-popconfirm {
  padding: var(--screen-space-3);

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 6px;
  }

  &__icon {
    margin-top: 2px;

    &.is-danger {
      color: var(--screen-danger);
    }

    &.is-default {
      color: var(--screen-info);
    }
  }

  &__title {
    flex: 1 1 auto;
    min-width: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.5;
    color: var(--screen-text);
  }

  &__desc {
    margin: 6px 0 0 21px;
    font-size: var(--screen-font-xs);
    line-height: 1.5;
    color: var(--screen-text-mute);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 6px;
    margin-top: var(--screen-space-3);
  }
}
</style>
