<template>
  <!--
    ScreenPagination 分页器
    --------------------------------
    档案列表（每页 10/20/50/100）与按项目统计表使用。

    为什么不用 a-pagination：antd 分页是浅色描边 + 蓝色当前页，暗色面板上观感割裂，
    而且档案列表需要「共 N 条」和「跳至 N 页」同时出现在紧凑的一行里。

    用法：
      <screen-pagination
        :current="pagination.current"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        @change="handlePageChange" />     ← change 抛 { current, pageSize, total }

    无障碍：外层 nav + aria-label；当前页 aria-current="page"；
    页码按钮用 aria-label 说明「第 N 页」，省略号不可聚焦且 aria-hidden。
  -->
  <nav v-if="showPager" class="screen-pagination" aria-label="分页导航">
    <span class="screen-pagination__total">共 <b>{{ total }}</b> 条</span>

    <label v-if="showSizeChanger" class="screen-pagination__sizer">
      <span class="screen-pagination__sizer-label">每页</span>
      <screen-select
        :value="innerPageSize"
        :options="sizeOptions"
        size="sm"
        :clearable="false"
        :searchable="false"
        placement="top-start"
        aria-label="每页条数"
        @change="handleSizeChange"
      />
      <span class="screen-pagination__sizer-label">条</span>
    </label>

    <ul class="screen-pagination__pages">
      <li>
        <button
          type="button"
          class="screen-pagination__btn"
          :disabled="innerCurrent <= 1 || disabled"
          aria-label="上一页"
          @click="go(innerCurrent - 1)"
        >
          <screen-icon name="chevron-left" :size="13" />
        </button>
      </li>

      <li v-for="(item, index) in pages" :key="`${item}-${index}`">
        <span v-if="item === 'prev-more' || item === 'next-more'" class="screen-pagination__more" aria-hidden="true">
          …
        </span>
        <button
          v-else
          type="button"
          class="screen-pagination__btn is-number"
          :class="{ 'is-active': item === innerCurrent }"
          :aria-label="`第 ${item} 页`"
          :aria-current="item === innerCurrent ? 'page' : undefined"
          :disabled="disabled"
          @click="go(item)"
        >
          {{ item }}
        </button>
      </li>

      <li>
        <button
          type="button"
          class="screen-pagination__btn"
          :disabled="innerCurrent >= totalPages || disabled"
          aria-label="下一页"
          @click="go(innerCurrent + 1)"
        >
          <screen-icon name="chevron-right" :size="13" />
        </button>
      </li>
    </ul>

    <label v-if="showQuickJumper" class="screen-pagination__jumper">
      <span class="screen-pagination__sizer-label">跳至</span>
      <input
        ref="jumper"
        class="screen-pagination__jumper-input"
        type="text"
        inputmode="numeric"
        :value="jumpText"
        :disabled="disabled"
        aria-label="跳转页码"
        @input="jumpText = $event.target.value"
        @keydown.enter.prevent="handleJump"
        @blur="handleJump"
      />
      <span class="screen-pagination__sizer-label">页</span>
    </label>
  </nav>
</template>

<script>
import ScreenIcon from './ScreenIcon'
import ScreenSelect from './ScreenSelect'

/** 页码两侧各展开几页，超出用省略号折叠 */
const SIBLING_COUNT = 2

export default {
  name: 'ScreenPagination',
  components: { ScreenIcon, ScreenSelect },
  props: {
    /** 当前页码，从 1 开始 */
    current: { type: Number, default: 1 },
    /** 每页条数 */
    pageSize: { type: Number, default: 10 },
    /** 总条数 */
    total: { type: Number, default: 0 },
    /** 可选每页条数 */
    pageSizeOptions: { type: Array, default: () => [10, 20, 50, 100] },
    /** 是否显示每页条数切换 */
    showSizeChanger: { type: Boolean, default: true },
    /** 是否显示快速跳页 */
    showQuickJumper: { type: Boolean, default: true },
    disabled: { type: Boolean, default: false },
  },
  data () {
    return {
      /** 内部维护页码，父组件没回传时也能即时反馈 */
      innerCurrent: this.current,
      innerPageSize: this.pageSize,
      jumpText: '',
    }
  },
  computed: {
    totalPages () {
      if (!this.total || this.total <= 0) return 1
      return Math.max(1, Math.ceil(this.total / this.innerPageSize))
    },
    /** 一条数据都没有时不渲染分页，避免列表为空还显示一大块控件 */
    showPager () {
      return this.total > 0
    },
    sizeOptions () {
      return this.pageSizeOptions.map((size) => ({ value: size, label: `${size} 条/页` }))
    },
    /** 页码窗口：首页、末页、当前页 ±SIBLING_COUNT，中间用省略号占位 */
    pages () {
      const last = this.totalPages
      const current = this.innerCurrent
      if (last <= SIBLING_COUNT * 2 + 5) {
        return Array.from({ length: last }, (_, index) => index + 1)
      }

      const result = [1]
      const start = Math.max(2, current - SIBLING_COUNT)
      const end = Math.min(last - 1, current + SIBLING_COUNT)

      if (start > 2) result.push('prev-more')
      for (let page = start; page <= end; page += 1) result.push(page)
      if (end < last - 1) result.push('next-more')
      result.push(last)
      return result
    },
  },
  watch: {
    current (val) {
      this.innerCurrent = val
    },
    pageSize (val) {
      this.innerPageSize = val
    },
  },
  methods: {
    go (page) {
      if (this.disabled) return
      const next = Math.min(Math.max(1, page), this.totalPages)
      if (next === this.innerCurrent) return
      this.innerCurrent = next
      this.emitChange()
    },
    handleSizeChange (size) {
      if (this.disabled) return
      const nextSize = Number(size) || this.innerPageSize
      if (nextSize === this.innerPageSize) return
      this.innerPageSize = nextSize
      // 改每页条数后回到第 1 页，否则可能停在一个已不存在的页码上
      this.innerCurrent = 1
      this.emitChange()
      this.$emit('size-change', nextSize)
    },
    handleJump () {
      if (this.disabled) return
      const raw = String(this.jumpText).trim()
      this.jumpText = ''
      if (!raw) return
      const page = parseInt(raw, 10)
      if (!Number.isFinite(page)) return
      this.go(page)
    },
    emitChange () {
      this.$emit('change', {
        current: this.innerCurrent,
        pageSize: this.innerPageSize,
        total: this.total,
      })
    },
  },
}
</script>

<style scoped lang="less">
@import './styles/screen-mixins.less';

.screen-pagination {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--screen-space-3);
  font-size: var(--screen-font-xs);
  color: var(--screen-text-sub);

  &__total {
    flex: 0 0 auto;

    b {
      margin: 0 2px;
      font-family: var(--screen-font-number-family);
      font-variant-numeric: tabular-nums;
      font-size: var(--screen-font-sm);
      color: var(--screen-accent-soft);
    }
  }

  &__sizer,
  &__jumper {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex: 0 0 auto;
  }

  // 每页条数下拉固定宽度，避免切换 50 → 100 时整行重排
  &__sizer {
    /deep/ .screen-select {
      width: 104px;
    }
  }

  &__sizer-label {
    color: var(--screen-text-mute);
    white-space: nowrap;
  }

  &__pages {
    display: flex;
    align-items: center;
    gap: 4px;
    flex: 0 0 auto;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 26px;
    height: 26px;
    padding: 0 6px;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    font-variant-numeric: tabular-nums;
    color: var(--screen-text-sub);
    background: rgba(6, 20, 40, 0.72);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease),
      border-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover:not(:disabled):not(.is-active) {
      color: var(--screen-accent);
      border-color: var(--screen-border-strong);
      background: rgba(130, 198, 255, 0.1);
    }

    // 当前页：底色 + 描边 + 字重，三重线索
    &.is-active {
      color: var(--screen-accent);
      font-weight: 600;
      background: rgba(130, 198, 255, 0.16);
      border-color: var(--screen-border-strong);
    }

    &:disabled {
      color: var(--screen-text-mute);
      background: rgba(6, 20, 40, 0.38);
      border-color: var(--screen-border-soft);
      cursor: not-allowed;
    }
  }

  &__more {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 26px;
    color: var(--screen-text-mute);
  }

  &__jumper-input {
    width: 48px;
    height: 26px;
    padding: 0 6px;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    font-variant-numeric: tabular-nums;
    text-align: center;
    color: var(--screen-text);
    background: rgba(6, 20, 40, 0.72);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);
    outline: none;
    transition: border-color var(--screen-duration) var(--screen-ease);

    &:focus {
      .screen-control-focus();
    }

    &:disabled {
      .screen-control-disabled();
    }
  }

  // 数据量大时（每页 100 条）让分页贴到右侧，与左侧统计信息分开
  &__pages {
    margin-left: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .screen-pagination__btn,
  .screen-pagination__jumper-input {
    transition: none;
  }
}
</style>
