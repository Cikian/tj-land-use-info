<template>
  <!--
    ScreenEmpty 大屏空状态占位
    --------------------------------
    面板、表格、树在没有数据时统一使用它，替代 antd 的 a-empty
    （浅色插画 + 浅色文字在青绿暗色大屏上非常突兀）。

    视觉：ScreenIcon 的 inbox 线性图标 + 主文案 + 可选「下一步提示」，
    颜色不是唯一线索 —— 图标与文字同时表达「这里没有数据」。

    用法：
      <screen-empty text="暂无预警信息" description="可切换行政区划后重试" />
      <screen-empty size="sm" bordered />

    布局：父级是 flex 纵向容器时，本组件用 flex: 1 1 auto 撑满剩余高度并整体居中；
          父级不是 flex 时退化为普通块级元素（保留上下留白）。
  -->
  <div class="screen-empty" :class="[`is-${sizeClass}`, { 'is-bordered': bordered }]" role="status">
    <screen-icon class="screen-empty__icon" name="inbox" :size="iconSize" />
    <p class="screen-empty__text">{{ text }}</p>
    <p v-if="hasDescription" class="screen-empty__desc">
      <slot name="description">{{ description }}</slot>
    </p>
  </div>
</template>

<script>
import ScreenIcon from './ScreenIcon'

export default {
  name: 'ScreenEmpty',
  components: { ScreenIcon },
  props: {
    /** 主文案，说明「没有数据」这件事本身 */
    text: { type: String, default: '暂无数据' },
    /** 次级文案，给用户一个下一步动作（例如「调整筛选条件后重试」） */
    description: { type: String, default: '' },
    /** 尺寸：sm 用于表格内 / md 用于整块面板 */
    size: { type: String, default: 'md' },
    /** 是否绘制虚线容器（用于没有面板外框的裸场景） */
    bordered: { type: Boolean, default: false },
  },
  computed: {
    /** 校验后的尺寸类名 */
    sizeClass () {
      return this.size === 'sm' ? 'sm' : 'md'
    },
    /** 图标尺寸随尺寸档位变化，保证图标与文字比例一致 */
    iconSize () {
      return this.size === 'sm' ? 20 : 32
    },
    /** 具名插槽 description 优先于 description 属性 */
    hasDescription () {
      return !!this.$slots.description || !!this.description
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--screen-space-2);
  // 父级为 flex 纵向容器时撑满剩余高度，否则按内容高度
  flex: 1 1 auto;
  min-height: 0;
  padding: var(--screen-space-5) var(--screen-space-4);
  text-align: center;

  &.is-bordered {
    min-height: 96px;
    border: 1px dashed var(--screen-border-soft);
    border-radius: var(--screen-radius);
  }

  // 图标承担「空」的第一层语义，用弱化文本色而不是极低透明度，保证暗色底上仍能看清
  &__icon {
    color: var(--screen-text-mute);
  }

  &__text {
    margin: 0;
    font-size: var(--screen-font-sm);
    line-height: 1.5;
    color: var(--screen-text-sub);
    word-break: break-word;
  }

  // 次级提示更弱一级（直接复用占位符混入，避免各组件各写一遍弱化文本色），
  // 并限宽，避免大屏上被拉成一整行长句
  &__desc {
    max-width: 320px;
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.6;
    word-break: break-word;
    .screen-placeholder();
  }

  // ---------- 紧凑档 ----------
  &.is-sm {
    gap: var(--screen-space-1);
    padding: var(--screen-space-3) var(--screen-space-2);

    &.is-bordered {
      min-height: 64px;
    }

    .screen-empty__text {
      font-size: var(--screen-font-xs);
    }
  }
}

// 本组件不含过渡与动画，静态渲染，因此无需 prefers-reduced-motion 处理
</style>
