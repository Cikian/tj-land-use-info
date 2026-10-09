<template>
  <!--
    ScreenDescriptions 大屏描述列表（标签 / 值网格）
    --------------------------------
    档案详情面板用它替代 antd 的 a-descriptions：a-descriptions 自带浅灰表头底色与
    深灰标签字，在青绿暗色大屏上几乎不可读。

    两种外观（variant）：
      bordered（默认）  标签带淡底、单元格有描边，适合表单回显与并列分区；
      flat              无标签底色、无竖线，只用行分隔线，
                        适合「详情页」这种需要快速通读的长字段清单。

    版面契约（改这个文件前必读，细节见 <style> 顶部）：
      1. 单元格宽度 = 容器宽 / columns，**列数是 JS 给的固定值**，
         所以「标签列 + 值列」必须在单元格内自己收口：
         标签列 = min(内容宽, --sd-label-max)，值列 = 剩余全部。
      2. 值默认单行 + 省略号（title 给全文），行高因此是**常量**；
         需要通读的长文本用 item.stack 独占整行、可折行。

    条目字段：
      { key, label, value, width, stack, tone }
      width 占几列，默认 1；stack 为 true 时独占整行且标签在上、值在下
      tone  'number' 等宽数位 | 'accent' 主色强调 | 其余不处理
      （历史写法 span 表示「占几对标签/值」，等价于占几列，仍然兼容）

    无障碍：
      dt / dd 保持原生术语-定义结构，读屏能直接播报「标签 → 值」，
      无需 aria-label 人为关联；也不要给分组元素加 role="group"（会让标签被读两遍）。
      值被省略号截断时，完整内容通过 title 提供（溢出的才补，不溢出不加）。

    用法：
      <screen-descriptions :items="detailItems" :columns="3" />

      flat 变体（详情页：扁平紧凑）
      <screen-descriptions variant="flat" :items="items" :columns="4" />

      具名插槽按 item.key 覆盖某个值的渲染：
      <screen-descriptions :items="detailItems">
        <template #status="{ value }">
          <screen-tag :tone="toneOf(value)">{{ value }}</screen-tag>
        </template>
      </screen-descriptions>

    响应式：
      ≤1200px 缩为 2 列，≤900px 缩为 1 列（媒体查询覆盖 --sd-cols）。
      flat 变体是容器自适应：用 auto-fit + minmax(--sd-col-min, 1fr) 让列数跟着
      **容器宽度**走，窄容器自动减列，不会把值列压成一条竖排文字。
  -->
  <dl
    class="screen-descriptions"
    :class="[`is-${sizeClass}`, `is-${variantClass}`, { 'is-wrap': allowWrap }]"
    :style="rootStyle"
  >
    <!--
      ★ 每个条目 = 一个「自包含」的网格单元：单元格自己用 grid 排「标签 | 值」，
        不依赖任何隐式轨道尺寸。
        （两条不要回退的老路，见 <style> 顶部「版面契约」的踩坑记录。）
    -->
    <div
      v-for="(item, index) in normalized"
      :key="itemKey(item, index)"
      class="screen-descriptions__item"
      :class="{ 'is-stack': isStack(item), 'is-row-end': item.isRowEnd }"
    >
      <dt
        class="screen-descriptions__label"
        :class="{ 'is-stack': isStack(item) }"
      >
        <span class="screen-descriptions__label-text">{{ item.label }}</span>
      </dt>
      <dd
        ref="value"
        class="screen-descriptions__value"
        :class="[`is-${toneClass(item)}`, { 'is-empty': isEmpty(item), 'is-stack': isStack(item) }]"
        :title="valueTitle(item)"
      >
        <slot :name="slotName(item, index)" :item="item" :value="item.value">
          {{ displayValue(item) }}
        </slot>
      </dd>
    </div>
  </dl>
</template>

<script>
/** 允许的条目语气，未知取值一律不落类名 */
const TONES = ['number', 'accent']

export default {
  name: 'ScreenDescriptions',
  props: {
    /** 条目数组：[{ key, label, value, width, stack, tone }] */
    items: { type: Array, default: () => [] },
    /** 每行的「标签 + 值」对数 */
    columns: { type: Number, default: 3 },
    /**
     * 外观：bordered 有边框格子（默认，表单回显）/ flat 扁平紧凑（详情页通读）
     */
    variant: { type: String, default: 'bordered' },
    /** 是否绘制单元格描边与标签底色；false 时标签在上、值在下（等价 variant="plain"） */
    bordered: { type: Boolean, default: true },
    /** 尺寸：sm 紧凑（表格/侧栏） / md 常规 */
    size: { type: String, default: 'md' },
    /**
     * 标签列的**上限**宽度（不是固定宽度）。
     * 标签列实际宽 = min(该组最长标签的内容宽, 本值)：
     *   - 短标签组不会被撑出一大片空白（旧实现写死 108px，3 字标签后面全是空的）；
     *   - 长标签组不会把值列挤到折行（旧实现用 max-content，长标签能把值吃到 0）。
     */
    labelWidth: { type: String, default: '160px' },
    /**
     * 允许值折行显示完整内容（默认 false，保持「单行 + 省略号」的紧凑契约）。
     *
     * 默认关闭的原因：常量行高是 bordered / flat 变体的版面契约（见 <style> 顶部），
     * 值一旦折行，行高就随内容变化——密集表格 / 表单回显不需要这个。
     * 详情页这类「必须看全」的场景按需打开：
     *   - 值取消 nowrap + 省略号，改为折行（overflow-wrap: anywhere 处理超长无空格串）；
     *   - 长文本条目（stack）本来就是折行的，不受影响；
     *   - 打开后行高不再恒定，但仍保持逐行分隔线，视觉上更接近留白宽松的清单。
     */
    allowWrap: { type: Boolean, default: false },
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
     * 校验后的外观类名。
     * bordered=false 是历史写法，等价于 plain（标签在上、值在下、无边框）。
     */
    variantClass () {
      if (this.variant === 'flat') return 'flat'
      if (this.bordered === false) return 'plain'
      return this.variant === 'plain' ? 'plain' : 'bordered'
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
        '--sd-label-max': this.labelWidth || '160px',
      }
    },
    /**
     * 归一化条目，只用来算「每行最后一个条目」（isRowEnd）：
     * 行尾单元的底线不画，避免页面右侧出现一段段悬空的横线。
     * 由于外层是等宽轨道自动流式排布，这里只需按 width（占几列）累加游标。
     */
    normalized () {
      const columns = this.safeColumns
      let cursor = 0

      const rows = this.items.map((item) => {
        let width = parseInt(item && item.width, 10)
        // 兼容历史写法：span 表示「占几对（标签+值）」，等价于占几列
        if (!Number.isFinite(width) || width < 1) width = parseInt(item && item.span, 10)
        if (!Number.isFinite(width) || width < 1) width = 1
        if (width > columns) width = columns
        if (cursor + width > columns) cursor = 0

        cursor = (cursor + width) % columns

        return Object.assign({}, item, { width, isRowEnd: cursor === 0 })
      })

      // 最后一行即使没排满，视觉上也是「本行末尾」：把该行最后一个条目补标为行尾
      for (let i = rows.length - 1; i >= 0; i -= 1) {
        if (!rows[i].isRowEnd) {
          rows[i].isRowEnd = true
          break
        }
      }

      return rows
    },
  },
  methods: {
    /** 列表 key：优先用业务 key，缺失时退回下标 */
    itemKey (item, index) {
      return item.key === undefined || item.key === null ? `sd-${index}` : item.key
    },
    /** 作用域插槽名：优先用业务 key，缺失时退回下标，保证调用方仍能按位覆盖 */
    slotName (item, index) {
      return item.key === undefined || item.key === null ? `item-${index}` : item.key
    },
    isStack (item) {
      return !!item.stack
    },
    toneClass (item) {
      return TONES.indexOf(item.tone) > -1 ? item.tone : ''
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
    /**
     * 值被省略号截断时补 title 显示全文。
     * ★ 只有真的溢出才补：不溢出也挂 title 会多出一个和正文重复的系统提示。
     * ★ 判定必须用「DOM 实测」（scrollWidth > clientWidth），字数估算在等宽数字 /
     *   全角标点 / 不同字体下很容易判错。
     * ★ 溢出结果由 syncTitles() 在挂载 / 尺寸变化后算好缓存在 _titleIndex，
     *   这里只读缓存；否则 resize 之后 title 会一直停留在旧视口的判断上。
     */
    valueTitle (item, index) {
      if (!this._titleIndex) return undefined
      if (this.isSlotOverridden(item, index)) return undefined
      return this._titleIndex[index] ? String(item.value) : undefined
    },
    /**
     * 实测每个值元素是否溢出，把结论缓存进 _titleIndex。
     * 返回 true 表示缓存发生了变化（调用方据此决定要不要重渲染）。
     */
    syncTitles () {
      const els = this.$refs.value
      if (!els) return false
      const list = Array.isArray(els) ? els : [els]
      const next = {}
      let changed = false

      list.forEach((el, index) => {
        if (!el) return
        const overflow = el.scrollWidth > el.clientWidth + 1
        if (overflow) next[index] = true
        if (!!this._titleIndex[index] !== overflow) changed = true
      })

      // 缓存里多出来的旧下标也算变化
      Object.keys(this._titleIndex).forEach((key) => {
        if (!next[key]) changed = true
      })

      this._titleIndex = next
      return changed
    },
    /**
     * 尺寸变化后重算溢出。
     * ★ 必须放在 $nextTick 之后：resize 回调里 DOM 布局未必已经更新。
     * ★ 只有结论真的变了才 $forceUpdate，避免 ResizeObserver 与渲染互相触发成死循环。
     */
    scheduleSyncTitles () {
      this.$nextTick(() => {
        if (this.syncTitles()) this.$forceUpdate()
      })
    },
    /**
     * 该条目是否被具名插槽覆盖。
     * 插槽内容通常不是纯文本（标签 / 链接），补 title 会和插槽内容说的不是一回事。
     * $scopedSlots 在 Vue 2.6+ 才有；2.5 走 $slots，两者都查一遍。
     */
    isSlotOverridden (item, index) {
      const name = this.slotName(item, index)
      const scoped = this.$scopedSlots || {}
      if (typeof scoped[name] === 'function') return true
      const plain = this.$slots || {}
      return !!plain[name]
    },
  },
  mounted () {
    // 首帧先算一遍：值是否被省略号截断，只有实测才知道
    this._titleIndex = {}
    this.scheduleSyncTitles()

    // 尺寸变化后重算（弹窗拉宽 / 分栏变化 / 系统缩放都会触发）
    if (typeof ResizeObserver !== 'undefined' && this.$el) {
      this._resizeObserver = new ResizeObserver(this.scheduleSyncTitles)
      this._resizeObserver.observe(this.$el)
    } else if (typeof window !== 'undefined') {
      // 兜底：不支持 ResizeObserver 的内核只监听窗口 resize
      this._onWindowResize = this.scheduleSyncTitles
      window.addEventListener('resize', this._onWindowResize)
    }
  },
  beforeDestroy () {
    if (this._resizeObserver) {
      this._resizeObserver.disconnect()
      this._resizeObserver = null
    }
    if (this._onWindowResize && typeof window !== 'undefined') {
      window.removeEventListener('resize', this._onWindowResize)
      this._onWindowResize = null
    }
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

/**
 * 版面契约（改这个文件前先读）
 * ------------------------------------------------------------------
 * 外层 Grid：把宽度均分成 --sd-cols 条**等宽**轨道（minmax(0, 1fr)）。
 * 单元格    ：每条轨道一个 .screen-descriptions__item，内部再用 grid 排
 *             「标签（内容宽，封顶 --sd-label-max） | 值（吃掉剩余全部）」。
 *
 * 为什么标签列必须是「内容宽 + 封顶」，而不是定宽 / max-content：
 *   定宽（旧实现 108px）：3 字标签后面拖一大段空白 → 看着「文字太分散」，
 *                        而且标签一长就被 nowrap 截断；
 *   max-content（中途试过）：长标签（如「材料数 / 意见记录数」）能把整个
 *                        单元格吃满，值列只剩十几像素 → 值逐个字换行、
 *                        行高翻倍。这正是用户截图里的样子。
 *   min(内容宽, 封顶)：短标签组不留空白、长标签组不挤值，行高才不会失控。
 *
 * 行高为什么是常量：
 *   值默认 `white-space: nowrap` + 省略号（全文走 title），标签也 nowrap，
 *   所以每个单元永远一行 = --sd-row-h。
 *   需要通读的长文本用 item.stack 独占整行、允许折行（这是唯一会变高的条目）。
 *
 * ★ 三条不要回退的老路（都实际出过问题）：
 *   1) 外层切「标签轨 + 值轨」，单元格内再嵌套 Grid：内层 1fr 会被压到
 *      min-content，值列只剩几十像素、长文本逐字竖排；
 *   2) 单元格 display: contents，dt/dd 直接落外层格子：依赖 contents 生效，
 *      一旦不生效（构建 / 内核 / 缓存差异）dt/dd 退化成两个块级子元素，
 *      整页变成「标签一行、值一行」的超高版面；
 *   3) 标签列用 max-content 不封顶：见上，长标签会把值列吃光。
 */
.screen-descriptions {
  // 列数转发层：行内只写 --sd-input-cols，媒体查询覆盖本变量即可改列数
  --sd-cols: var(--sd-input-cols, 3);
  // 标签列封顶宽度（行内由 labelWidth 属性写入）
  --sd-label-max: 160px;
  // ★ 下面两个变量**不要在这里直接声明**（--sd-row-pad / --sd-pair-gap）。
  //   调用方是在自己的容器元素上设它们、靠继承传进来的；而「元素自身的直接声明」
  //   永远优先于「继承来的值」——特异性管不了继承。一旦在这里声明，
  //   调用方的覆盖就会静默失效。
  //   所以默认值全部下沉到使用处的 var(--x, 默认值)，见下方各规则。
  //   历史上这两行的写法：
  //     --sd-row-pad: var(--screen-space-2);
  //     --sd-pair-gap: var(--screen-space-2);
  //   —— 已移除，行为由 var() 的第二参数保证，默认视觉效果不变。

  // 值右侧留白，同时也是相邻单元格之间的间距（这个不做覆盖入口，保持直接声明）
  --sd-gutter: var(--screen-space-3);

  display: grid;
  grid-template-columns: repeat(var(--sd-cols), minmax(0, 1fr));
  align-content: start;
  min-width: 0;
  margin: 0;

  // ---------- 单元格 ----------
  &__item {
    display: grid;
    // ★ 标签轨用 auto（= max-content，但可以被压小），值轨 minmax(0, 1fr)：
    //   两轨都允许收缩，配合值的省略号，任何单元格宽度下都不会出现逐字竖排。
    grid-template-columns: auto minmax(0, 1fr);
    // 基线对齐：标签与值的第一行文字落在同一条基线上
    align-items: baseline;
    min-width: 0;
    // 值右侧的间距放在单元格上：底线画在单元格上，
    // 分隔线始终落在整行的真正底部，不会出现「标签下面半截横线」。
    padding: 0 var(--sd-gutter) 0 0;
    border-bottom: 1px solid var(--screen-border-soft);

    // 行尾（每行最后一个单元格）不画底线，避免右侧出现悬空的横线
    &.is-row-end {
      border-bottom-color: transparent;
    }
  }

  // ---------- 标签 ----------
  &__label {
    min-width: 0;
    // 封顶：超过 --sd-label-max 的部分由内层 span 省略号收掉
    max-width: var(--sd-label-max);
    margin: 0;
    padding: var(--sd-row-pad, var(--screen-space-2)) 0;
    font-size: var(--screen-font-xs);
    font-weight: 400;
    line-height: var(--sd-line-h, 18px);
    color: var(--screen-text-mute);
    background: transparent;
    border: 0;
  }

  // 标签文字本体：只有它做省略，dt 本身保持可伸缩
  &__label-text {
    display: block;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  // ---------- 值 ----------
  &__value {
    min-width: 0;
    margin: 0;
    padding: var(--sd-row-pad, var(--screen-space-2)) 0 var(--sd-row-pad, var(--screen-space-2)) var(--sd-pair-gap, var(--screen-space-2));
    font-size: var(--screen-font-sm);
    line-height: var(--sd-line-h, 18px);
    color: var(--screen-text);
    border: 0;
    // ★ 单行 + 省略号：行高因此是常量；溢出时组件会补 title 显示全文
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    // 空值（占位破折号）弱化，避免和真实数据同等醒目
    &.is-empty {
      .screen-placeholder();
    }

    // 数值类：等宽数位保证纵向对齐
    &.is-number {
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
    }

    // 主色强调：编号、单号这类需要一眼定位的值
    &.is-accent {
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      color: var(--screen-accent);
    }
  }

  // ---------- 无边框：标签在上、值在下（flex column，天然按 DOM 顺序排） ----------
  &.is-plain {
    .screen-descriptions__item {
      grid-template-columns: minmax(0, 1fr);
      align-items: stretch;
      row-gap: var(--screen-space-1);
    }

    .screen-descriptions__label {
      max-width: none;
      padding: 0;
      background: transparent;
      border: 0;
    }

    .screen-descriptions__value {
      padding: 0;
      border: 0;
    }
  }

  // ---------- 扁平紧凑：详情页通读用 ----------
  // 去掉标签底色与竖线，只留行分隔线。
  // 行高 = 18(文字) + 5*2(内边距) + 1(底线) = 29px，且**不随内容变化**。
  // ★ --sd-row-pad / --sd-line-h 都不在这里直接声明：它们是「调用方优先」的变量，
  //   「元素自身的直接声明」会压过继承值，导致调用方的覆盖静默失效。
  //   默认值改由下面两条规则的 var() 第二参数提供，默认视觉完全不变。
  &.is-flat {
    .screen-descriptions__label {
      line-height: var(--sd-line-h, 18px);
      font-size: var(--screen-font-xs);
    }

    .screen-descriptions__value {
      font-size: var(--screen-font-xs);
      line-height: var(--sd-line-h, 18px);
    }
  }

  // ---------- 跨整行的长文本条目：标签在上、值在下 ----------
  // 同样用单列 grid；让单元格独占整行。
  // ★ grid-column 的值必须用 ~"..." 转义：
  //   Less 会把 `1 / -1`、`-1 / -1` 里的 `N /` 当除法吃掉，
  //   编译成 `grid-column: 1` / `grid-column: -1`（只占 1 条轨道），
  //   还会多生成一条隐式轨道，把整行的等宽分布彻底打乱（整页压成一列）。
  &__item.is-stack {
    grid-column: ~'1 / -1';
    grid-template-columns: minmax(0, 1fr);
    align-items: stretch;
    row-gap: 2px;
  }

  &__item.is-stack > &__label {
    max-width: none;
    padding: 0;
    line-height: 20px;
    background: transparent;
    border: 0;
  }

  &__item.is-stack > &__label > &__label-text {
    white-space: normal;
    overflow: visible;
    text-overflow: clip;
  }

  &__item.is-stack > &__value {
    padding: 0 0 var(--screen-space-2);
    line-height: 19px;
    // 长文本要能通读，这里必须允许折行（唯一会撑高行的条目）
    white-space: pre-wrap;
    overflow: visible;
    text-overflow: clip;
    word-break: break-word;
  }

  // ---------- 紧凑档 ----------
  &.is-sm {
    --sd-row-pad: 3px;

    .screen-descriptions__label {
      font-size: var(--screen-font-xs);
    }

    .screen-descriptions__value {
      font-size: var(--screen-font-xs);
    }
  }

  // ---------- 值折行（详情页「必须看全」的场景） ----------
  // 由 allowWrap 打开，默认关闭：关着的时候行高恒为 --sd-row-h，是版面契约的一部分。
  // 打开后值不再截断，行高随内容变化，但逐行分隔线把每条记录切开，仍是一份清晰的清单。
  &.is-wrap {
    // ★ 这里**不要**再直接声明 --sd-row-pad / --sd-pair-gap / --sd-value-size /
    //   --sd-wrap-line-h。
    //   原理：调用方（详情弹窗）是在自己的容器元素上设这些变量，靠**继承**传进来；
    //   而「元素自身的直接声明」永远优先于「继承来的值」——特异性管不了继承。
    //   一旦组件在这里直接声明，调用方的覆盖就会静默失效（本轮踩过的坑）。
    //   所以全部改成在使用处 var(--x, 默认值)：既不写死，又保留可覆盖性。
    --sd-row-h: 26px;

    .screen-descriptions__item {
      // ★ start 而不是 baseline：标签与值字号可能不同，baseline 对齐会按各自的
      //   行盒算出不同位置；统一从上边缘起排，再让两侧用同高的内边距 + 同一个
      //   绝对行高，文字就会落在同一条基线上。
      align-items: start;
      padding-top: 2px;
      padding-bottom: 2px;
    }

    .screen-descriptions__label {
      padding: var(--sd-row-pad, 7px) 0;
      // ★ 行内（非 stack）的标签与值必须用**同一个绝对行高**（px）。
      //   用单位数字行高（如 1.5）时，两侧字号不同 → 行盒高度不同 →
      //   各自的半行距（leading）不同 → 文字基线错开（实测 3.7px，
      //   正是「标签和值没对齐」的来源）。绝对值与字号无关，行盒严格相等。
      //   变量 --sd-wrap-line-h 由调用方给绝对 px（见各详情弹窗 .xxx-detail__section）。
      line-height: var(--sd-wrap-line-h, 22px) !important;
    }

    // 左侧内边距必须写在值元素**自己**身上，才能吃到调用方传来的 --sd-pair-gap
    .screen-descriptions__value {
      padding: var(--sd-row-pad, 7px) 0 var(--sd-row-pad, 7px) var(--sd-pair-gap, var(--screen-space-3));
      // ★ 覆盖 flat 变体的 15px：调用方通过 --sd-value-size 指定值的字号。
      //   !important 是因为 flat 变体的同类规则特异性更高，靠叠选择器去压会让
      //   「谁生效」难以推理；调用方仍然只通过变量调节，入口不变。
      font-size: var(--sd-value-size, var(--screen-font-sm)) !important;
      // ★ 取消 nowrap + 省略号：详情页的值必须能看全，hover 看全文不算可读
      white-space: normal;
      overflow: visible;
      text-overflow: clip;
      // 超长无空格串（编号 / URL / 哈希）也要能断开，否则会顶破单元格
      overflow-wrap: anywhere;
      word-break: break-word;
      // 与标签用同一个绝对行高，保证两侧文字基线重合（见 .screen-descriptions__label）
      line-height: var(--sd-wrap-line-h, 22px) !important;
    }

    // ---------- stack 条目：标签与值必须在同一条基线上 ----------
    // ★ 基础规则里 `&__item.is-stack > &__label { padding: 0 }` 是给
    //   「标签在上、值在下」的两行版面用的，那时两侧不需要左右对齐。
    //   但详情页的 stack 条目是「左标签 | 右值」的一行版面（值跨满整行、
    //   仅为了不截断而允许折行），两侧内边距必须一致，否则标签会比值高出
    //   一整个内边距（实测 5px），看起来就是「没对齐」。
    //   这里用 .is-wrap 前缀把特异性提到 (0,3,0)，压过基础的 (0,2,0)。
    .screen-descriptions__item.is-stack > .screen-descriptions__label {
      padding: var(--sd-row-pad, 7px) 0;
      line-height: var(--sd-wrap-line-h, 22px) !important;
    }

    // stack 条目的值仍允许折行，行高略松以便长文通读
    .screen-descriptions__item.is-stack > .screen-descriptions__value {
      line-height: 1.65 !important;
    }
  }
}

// 本组件不主动添加过渡/动画；TransitionGroup 之类由调用方负责，故无需 reduced-motion 分支

// ---------- 响应式：窄屏减少列数（单元格自动流到更少的轨道上） ----------
// ★ 选择器必须带上 :not(.is-wrap)：allowWrap 的调用方要在**窄容器**里靠
//   容器查询自己决定列数（详情页两栏布局的左右栏宽度和视口宽度并不同步），
//   这里若按视口宽度强改 --sd-cols，会把调用方设的列数覆盖掉。
@media (max-width: 1200px) {
  .screen-descriptions:not(.is-wrap) {
    --sd-cols: 2;
  }
}

@media (max-width: 900px) {
  .screen-descriptions:not(.is-wrap) {
    --sd-cols: 1;
  }
}
</style>