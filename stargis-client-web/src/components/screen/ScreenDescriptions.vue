<template>
  <!--
    ScreenDescriptions 大屏描述列表（标签 / 值网格）
    --------------------------------
    档案详情面板用它替代 antd 的 a-descriptions：a-descriptions 自带浅灰表头底色与
    深灰标签字，在青绿暗色大屏上几乎不可读。

    布局：CSS Grid，列模板为 repeat(columns, labelWidth 1fr)，
          即「每一对标签/值占两列」。items[].span = N 表示该条目占 N 对（2N 列），
          由 grid-column-start / grid-column-end 计算得出（跨列起始列按行内游标累加）。

    用法：
      <screen-descriptions :items="detailItems" :columns="3" />

      <screen-descriptions :items="detailItems">
        <template #status="{ value }">
          <screen-tag :tone="toneOf(value)">{{ value }}</screen-tag>
        </template>
        <template #fileUrl="{ value }">
          <a class="link" :href="value">查看附件</a>
        </template>
      </screen-descriptions>

    无障碍（选择原生 dl 的理由）：
      HTML 规范允许 <dl> 的子元素是「包裹 dt / dd 的 <div>」，术语与定义的结构关系
      由 DOM 直接给出，读屏可以正确播报「标签 → 值」，无需 aria-label 人为关联；
      因此不再给分组 div 加 role="group"：那样会覆盖分组 div 的通用语义，
      并让标签被读两遍（group 名 + dt 各一次），反而更差。

    响应式：
      宽屏（> 1200px）才应用 items[].span 的跨列（由 matchMedia 驱动的 wide 标记控制，
      避免用 !important 去压行内样式）；≤1200px 缩为每行 2 对，≤900px 缩为每行 1 对，
      列数由媒体查询覆盖 --sd-cols 变量实现。
  -->
  <!-- bordered=false 时由条目的 is-plain 修饰类去掉描边与标签底色（默认态即「有边框」，无需额外修饰类） -->
  <dl class="screen-descriptions" :class="`is-${sizeClass}`" :style="rootStyle">
    <div
      v-for="(item, index) in normalized"
      :key="itemKey(item, index)"
      class="screen-descriptions__item"
      :class="{ 'is-plain': !bordered }"
      :style="itemStyle(item)"
    >
      <dt class="screen-descriptions__label" :title="item.label">{{ item.label }}</dt>
      <dd class="screen-descriptions__value" :class="{ 'is-empty': isEmpty(item) }">
        <slot :name="slotName(item, index)" :item="item" :value="item.value">
          {{ displayValue(item) }}
        </slot>
      </dd>
    </div>
  </dl>
</template>

<script>
export default {
  name: 'ScreenDescriptions',
  props: {
    /** 条目数组：[{ key, label, value, span }]；span 为可选整数，表示占几对（2 列） */
    items: { type: Array, default: () => [] },
    /** 每行的「标签 + 值」对数 */
    columns: { type: Number, default: 3 },
    /** 是否绘制单元格描边与标签底色；false 时标签在上、值在下 */
    bordered: { type: Boolean, default: true },
    /** 尺寸：sm 紧凑（表格/侧栏） / md 常规 */
    size: { type: String, default: 'md' },
    /** 标签列宽，任意 CSS 长度值 */
    labelWidth: { type: String, default: '108px' },
  },
  data () {
    return {
      /** 是否处于宽屏：只有宽屏才按 span 跨列，窄屏交给自动排布 */
      wide: true,
      /** matchMedia 查询对象，需在 beforeDestroy 中解绑 */
      wideQuery: null,
    }
  },
  computed: {
    /** 校验后的列数，防止 0 / 负数 / NaN 造成非法 grid 模板 */
    safeColumns () {
      const count = parseInt(this.columns, 10)
      return Number.isFinite(count) && count > 0 ? count : 3
    },
    /** 校验后的尺寸类名 */
    sizeClass () {
      return this.size === 'sm' ? 'sm' : 'md'
    },
    /**
     * 根节点内联样式：只写入「实例变量」。
     * 列数文档里最直觉的写法是把列数直接写成行内 grid-template-columns，
     * 但行内样式优先级高于媒体查询，窄屏就没法覆盖了；
     * 因此这里写入 --sd-input-cols，样式表里再用 --sd-cols 转发一层，
     * 媒体查询只需要改 --sd-cols 就能真正生效。
     */
    rootStyle () {
      return {
        '--sd-input-cols': String(this.safeColumns),
        '--sd-label-w': this.labelWidth || '108px',
      }
    },
    /**
     * 预计算每个条目的跨列信息：
     * 用游标按行累加，span 超出本行剩余列数时换行（回到第 1 列）。
     */
    normalized () {
      const columns = this.safeColumns
      let cursor = 0

      return this.items.map((item) => {
        let span = parseInt(item.span, 10)
        if (!Number.isFinite(span) || span < 1) span = 1
        if (span > columns) span = columns
        if (cursor + span > columns) cursor = 0

        // 每一「对」占 2 列，所以第 cursor 对的起始网格线是 cursor * 2 + 1（1 起）
        const startColumn = cursor * 2 + 1
        cursor = (cursor + span) % columns

        return Object.assign({}, item, { span, startColumn })
      })
    },
  },
  mounted () {
    if (typeof window === 'undefined' || !window.matchMedia) return
    this.wideQuery = window.matchMedia('(min-width: 1201px)')
    this.wide = this.wideQuery.matches

    // Safari < 14 只有已废弃的 addListener
    if (this.wideQuery.addEventListener) {
      this.wideQuery.addEventListener('change', this.handleWideChange)
    } else if (this.wideQuery.addListener) {
      this.wideQuery.addListener(this.handleWideChange)
    }
  },
  beforeDestroy () {
    if (!this.wideQuery) return
    if (this.wideQuery.removeEventListener) {
      this.wideQuery.removeEventListener('change', this.handleWideChange)
    } else if (this.wideQuery.removeListener) {
      this.wideQuery.removeListener(this.handleWideChange)
    }
    this.wideQuery = null
  },
  methods: {
    /** 媒体查询回调：同步宽屏标记 */
    handleWideChange (event) {
      this.wide = !!event.matches
    },
    /** 列表 key：优先用业务 key，缺失时退回下标 */
    itemKey (item, index) {
      return item.key === undefined || item.key === null ? `sd-${index}` : item.key
    },
    /** 作用域插槽名：优先用业务 key，缺失时退回下标，保证调用方仍能按位覆盖 */
    slotName (item, index) {
      return item.key === undefined || item.key === null ? `item-${index}` : item.key
    },
    /**
     * 跨列样式：宽屏才生效（窄屏列数变少，固定列号会把单元格挤到错误位置）。
     * 在 JS 里拼字符串，也顺带绕开 Less 把 `N / N` 当除法的坑。
     */
    itemStyle (item) {
      if (!this.wide) return {}
      return {
        gridColumnStart: String(item.startColumn),
        gridColumnEnd: `span ${item.span * 2}`,
      }
    },
    /** 空值判定：null / undefined / 空串都算空 */
    isEmpty (item) {
      const value = item.value
      return value === null || value === undefined || value === ''
    },
    /** 空值统一显示为破折号，避免出现「标签后面什么都没有」的歧义 */
    displayValue (item) {
      return this.isEmpty(item) ? '—' : item.value
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-descriptions {
  // 列数转发层：行内只写 --sd-input-cols，媒体查询覆盖本变量即可改列数
  --sd-cols: var(--sd-input-cols, 3);

  display: grid;
  grid-template-columns: repeat(var(--sd-cols), var(--sd-label-w, 108px) 1fr);
  align-content: start;
  min-width: 0;
  margin: 0;

  // 每个条目本身是一个跨 2N 列的网格单元，内部再排「标签 | 值」两格
  &__item {
    display: grid;
    grid-template-columns: var(--sd-label-w, 108px) 1fr;
    min-width: 0;
  }

  &__label {
    display: flex;
    align-items: center;
    min-width: 0;
    margin: 0;
    padding: var(--screen-space-2) var(--screen-space-3);
    font-size: var(--screen-font-xs);
    font-weight: 400;
    line-height: 1.5;
    color: var(--screen-text-mute);
    background: var(--screen-row-alt);
    border-right: 1px solid var(--screen-border-soft);
    border-bottom: 1px solid var(--screen-border-soft);
    word-break: break-word;
  }

  &__value {
    min-width: 0;
    margin: 0;
    padding: var(--screen-space-2) var(--screen-space-3);
    font-size: var(--screen-font-sm);
    line-height: 1.5;
    color: var(--screen-text);
    border-bottom: 1px solid var(--screen-border-soft);
    word-break: break-word;
    white-space: pre-wrap;

    // 空值（占位破折号）弱化，避免和真实数据同等醒目
    &.is-empty {
      .screen-placeholder();
    }
  }

  // ---------- 无边框：标签在上、值在下 ----------
  &__item.is-plain {
    display: flex;
    flex-direction: column;
    gap: var(--screen-space-1);
    padding: var(--screen-space-2) 0;

    .screen-descriptions__label {
      padding: 0;
      background: transparent;
      border: 0;
    }

    .screen-descriptions__value {
      padding: 0;
      border: 0;
    }
  }

  // ---------- 紧凑档 ----------
  &.is-sm {
    .screen-descriptions__label {
      padding: var(--screen-space-1) var(--screen-space-2);
    }

    .screen-descriptions__value {
      padding: var(--screen-space-1) var(--screen-space-2);
      font-size: var(--screen-font-xs);
    }

    .screen-descriptions__item.is-plain {
      padding: var(--screen-space-1) 0;
    }
  }
}

// 本组件不主动添加过渡/动画；TransitionGroup 之类由调用方负责，故无需 reduced-motion 分支

// ---------- 响应式：窄屏减少每行的对数 ----------
@media (max-width: 1200px) {
  .screen-descriptions {
    --sd-cols: 2;
  }
}

@media (max-width: 900px) {
  .screen-descriptions {
    --sd-cols: 1;
  }
}
</style>
