<template>
  <!--
    ScreenField 表单字段容器
    --------------------------------
    统一「标签 + 控件 + 提示/错误」三段式的排版与间距，让档案模块里所有表单项
    的标签列宽、行距、错误位置完全一致（admin-client 用 a-form-model 自动生成
    这些，大屏没有 antd 表单，所以自己收口成一个容器）。

    用法：
      <screen-field label="档案名称" required :error="errors.archiveName" html-for="archiveName">
        <screen-input id="archiveName" v-model="model.archiveName" :invalid="!!errors.archiveName" />
      </screen-field>

    无障碍说明（重要）：
      这里渲染的 `*` 只是视觉标记，且已 aria-hidden。**必填语义必须由控件自己承担**
      （控件上加 aria-required="true"），否则读屏用户听不到「必填」。

    实现说明：
      标签列宽通过内联 style 注入 grid-template-columns。
      不能用 CSS 里的 v-bind()——那是 Vue 3 SFC 的特性，本项目是 Vue 2.6。
  -->
  <div
    class="screen-field"
    :class="[`is-${size}`, { 'has-error': !!error, 'is-stacked': stacked }]"
    :style="rootStyle"
  >
    <label v-if="label" class="screen-field__label" :for="htmlFor || undefined">
      <span class="screen-field__label-text">{{ label }}</span>
      <span v-if="required" class="screen-field__required" aria-hidden="true">*</span>
    </label>

    <div class="screen-field__main">
      <div class="screen-field__control">
        <slot />
      </div>

      <!-- 错误优先于提示；错误用 role="alert" 让读屏立即播报 -->
      <p v-if="error" class="screen-field__error" role="alert">{{ error }}</p>
      <p v-else-if="tip" class="screen-field__tip">{{ tip }}</p>
      <slot name="message" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'ScreenField',
  props: {
    /** 标签文案；不传则不渲染标签列 */
    label: { type: String, default: '' },
    /** 是否必填（仅视觉标记，必填语义由控件承担） */
    required: { type: Boolean, default: false },
    /** 常驻提示文案，帮助用户填写 */
    tip: { type: String, default: '' },
    /** 校验错误文案，传入后覆盖提示并标红 */
    error: { type: String, default: '' },
    /** 标签列宽，同一行内的多个字段请传同一个值以保证对齐 */
    labelWidth: { type: String, default: '96px' },
    /** 关联控件的 id（对应控件的 id 属性），让点击标签能聚焦控件 */
    htmlFor: { type: String, default: '' },
    /** 尺寸：sm 28 / md 32 */
    size: { type: String, default: 'md' },
    /** 标签换到控件上方（窄容器 / 长标签场景） */
    stacked: { type: Boolean, default: false },
  },
  computed: {
    rootStyle () {
      if (this.stacked) return null
      return { gridTemplateColumns: `${this.labelWidth} 1fr` }
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-field {
  display: grid;
  grid-template-columns: 96px 1fr;
  align-items: start;
  gap: 0 var(--screen-space-3);
  min-width: 0;

  &.is-stacked {
    grid-template-columns: 1fr;
    gap: 4px 0;
  }

  &__label {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 2px;
    // 与控件首行文字垂直居中：控件高度 32/28
    min-height: 32px;
    font-size: var(--screen-font-sm);
    color: var(--screen-text-sub);
    text-align: right;
    cursor: default;
  }

  &.is-sm &__label {
    min-height: 28px;
    font-size: var(--screen-font-xs);
  }

  &.is-stacked &__label {
    justify-content: flex-start;
    min-height: 0;
    text-align: left;
  }

  &__label-text {
    min-width: 0;
    .screen-ellipsis();
  }

  &__required {
    flex: 0 0 auto;
    line-height: 1;
    color: var(--screen-danger);
  }

  &__main {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;
  }

  &__control {
    display: flex;
    align-items: center;
    gap: var(--screen-space-2);
    min-width: 0;
  }

  &__tip {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.5;
    color: var(--screen-text-mute);
  }

  &__error {
    margin: 0;
    font-size: var(--screen-font-xs);
    line-height: 1.5;
    color: var(--screen-danger);
  }
}
</style>
