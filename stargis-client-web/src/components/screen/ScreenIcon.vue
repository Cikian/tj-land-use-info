<template>
  <!--
    ScreenIcon 大屏图标
    --------------------------------
    为什么自建图标集：
      1. 大屏不允许用 emoji 当结构图标（跨平台字形不一致、无法用 Token 控制颜色）；
      2. 项目里已装的 antd 图标是「实心多色系」，与设计稿的 1.6px 线性风格不搭；
      3. 统一一套 24×24 线性几何，保证同一层级内笔画粗细一致。

    用法：
      <screen-icon name="search" :size="14" />
      <screen-icon name="trash" :size="14" title="删除" />   ← 带 title 时对读屏可见

    无障碍约定：
      - 默认 aria-hidden="true"（图标旁始终有文字，或图标在带 aria-label 的控件内部）；
      - 只有「图标是唯一信息载体」时才传 title，此时组件输出 role="img" + <title>。
  -->
  <svg
    class="screen-icon"
    :class="{ 'is-spin': spin }"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    :role="title ? 'img' : undefined"
    :aria-hidden="title ? undefined : 'true'"
    focusable="false"
  >
    <title v-if="title">{{ title }}</title>
    <template v-for="(shape, index) in shapes">
      <circle
        v-if="shape.c"
        :key="`c-${index}`"
        :cx="shape.c[0]"
        :cy="shape.c[1]"
        :r="shape.c[2]"
      />
      <ellipse
        v-else-if="shape.e"
        :key="`e-${index}`"
        :cx="shape.e[0]"
        :cy="shape.e[1]"
        :rx="shape.e[2]"
        :ry="shape.e[3]"
      />
      <path v-else :key="`p-${index}`" :d="shape.d" />
    </template>
  </svg>
</template>

<script>
/**
 * 图标几何数据：全部按 24×24 viewBox 手工整理，
 * 与设计稿的线性风格一致（统一 1.6px 描边、圆头圆角连接）。
 * 需要新图标时：优先在这里补一条几何数据，不要引入第三方图标库。
 */
const ICONS = {
  plus: [{ d: 'M12 5v14M5 12h14' }],
  minus: [{ d: 'M5 12h14' }],
  search: [{ c: [11, 11, 7] }, { d: 'M20.5 20.5l-4.4-4.4' }],
  reload: [{ d: 'M21 4v6h-6' }, { d: 'M3 20v-6h6' }, { d: 'M3.5 9a9 9 0 0 1 15-3.2L21 8' }, { d: 'M20.5 15a9 9 0 0 1-15 3.2L3 16' }],
  download: [{ d: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4' }, { d: 'M7 10l5 5 5-5' }, { d: 'M12 15V3' }],
  upload: [{ d: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4' }, { d: 'M17 8l-5-5-5 5' }, { d: 'M12 3v12' }],
  trash: [{ d: 'M3 6h18' }, { d: 'M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6' }, { d: 'M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2' }, { d: 'M10 11v6M14 11v6' }],
  edit: [{ d: 'M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z' }],
  eye: [{ d: 'M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z' }, { c: [12, 12, 3] }],
  close: [{ d: 'M18 6L6 18M6 6l12 12' }],
  check: [{ d: 'M20 6L9 17l-5-5' }],
  'check-circle': [{ d: 'M22 11.08V12a10 10 0 1 1-5.93-9.14' }, { d: 'M22 4L12 14.01l-3-3' }],
  'x-circle': [{ c: [12, 12, 10] }, { d: 'M15 9l-6 6M9 9l6 6' }],
  'alert-triangle': [
    { d: 'M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z' },
    { d: 'M12 9v4M12 17h.01' },
  ],
  info: [{ c: [12, 12, 10] }, { d: 'M12 16v-4M12 8h.01' }],
  'chevron-down': [{ d: 'M6 9l6 6 6-6' }],
  'chevron-up': [{ d: 'M18 15l-6-6-6 6' }],
  'chevron-left': [{ d: 'M15 18l-6-6 6-6' }],
  'chevron-right': [{ d: 'M9 18l6-6-6-6' }],
  'chevrons-left': [{ d: 'M11 17l-5-5 5-5' }, { d: 'M18 17l-5-5 5-5' }],
  'chevrons-right': [{ d: 'M13 17l5-5-5-5' }, { d: 'M6 17l5-5-5-5' }],
  'arrow-up': [{ d: 'M12 19V5M5 12l7-7 7 7' }],
  'arrow-down': [{ d: 'M12 5v14M19 12l-7 7-7-7' }],
  'arrow-left': [{ d: 'M19 12H5M12 19l-7-7 7-7' }],
  'arrow-right': [{ d: 'M5 12h14M12 5l7 7-7 7' }],
  calendar: [
    { d: 'M19 4H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z' },
    { d: 'M16 2v4M8 2v4M3 10h18' },
  ],
  folder: [{ d: 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z' }],
  'folder-open': [
    { d: 'M4 20h15a2 2 0 0 0 1.9-1.4l1.4-4.6H7.5L4 20z' },
    { d: 'M4 20V5a2 2 0 0 1 2-2h4l2 3h7a2 2 0 0 1 2 2v5' },
  ],
  'file-text': [
    { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z' },
    { d: 'M14 2v6h6M16 13H8M16 17H8M10 9H8' },
  ],
  'file-plus': [
    { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z' },
    { d: 'M14 2v6h6M12 18v-6M9 15h6' },
  ],
  paperclip: [
    { d: 'M21.4 11.1l-9.2 9.2a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5' },
  ],
  'pie-chart': [{ d: 'M21.2 15.9A10 10 0 1 1 8 2.8' }, { d: 'M22 12A10 10 0 0 0 12 2v10z' }],
  'bar-chart': [{ d: 'M18 20V10M12 20V4M6 20v-6' }],
  'trending-up': [{ d: 'M23 6l-9.5 9.5-5-5L1 18' }, { d: 'M17 6h6v6' }],
  grid: [
    { d: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z' },
  ],
  layers: [{ d: 'M12 2L2 7l10 5 10-5-10-5z' }, { d: 'M2 17l10 5 10-5M2 12l10 5 10-5' }],
  filter: [{ d: 'M22 3H2l8 9.46V19l4 2v-8.54L22 3z' }],
  'external-link': [
    { d: 'M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6' },
    { d: 'M15 3h6v6M10 14L21 3' },
  ],
  clock: [{ c: [12, 12, 10] }, { d: 'M12 6v6l4 2' }],
  user: [{ d: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2' }, { c: [12, 7, 4] }],
  inbox: [
    { d: 'M22 12h-6l-2 3h-4l-2-3H2' },
    { d: 'M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z' },
  ],
  send: [{ d: 'M22 2L11 13' }, { d: 'M22 2l-7 20-4-9-9-4 20-7z' }],
  archive: [{ d: 'M21 8v13H3V8' }, { d: 'M1 3h22v5H1z' }, { d: 'M10 12h4' }],
  save: [
    { d: 'M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z' },
    { d: 'M17 21v-8H7v8M7 3v5h8' },
  ],
  database: [
    { e: [12, 5, 9, 3] },
    { d: 'M21 12c0 1.66-4 3-9 3s-9-1.34-9-3' },
    { d: 'M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5' },
  ],
  maximize: [
    { d: 'M8 3H5a2 2 0 0 0-2 2v3M21 8V5a2 2 0 0 0-2-2h-3' },
    { d: 'M3 16v3a2 2 0 0 0 2 2h3M16 21h3a2 2 0 0 0 2-2v-3' },
  ],
  'rotate-ccw': [{ d: 'M1 4v6h6' }, { d: 'M3.51 15a9 9 0 1 0 2.13-9.36L1 10' }],
  'sort': [{ d: 'M11 5h10M11 9h7M11 15h10M11 19h7' }, { d: 'M3 8l3-3 3 3M6 5v14' }],
  'sliders': [
    { d: 'M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3' },
    { d: 'M1 14h6M9 8h6M17 16h6' },
  ],
  'lock': [{ d: 'M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z' }, { d: 'M7 11V7a5 5 0 0 1 10 0v4' }],
  'more': [{ c: [12, 12, 1] }, { c: [19, 12, 1] }, { c: [5, 12, 1] }],
}

export default {
  name: 'ScreenIcon',
  props: {
    /** ICONS 中的图标名 */
    name: { type: String, required: true },
    /** 边长（px），同一层级请固定使用同一尺寸 */
    size: { type: [Number, String], default: 14 },
    /** 笔画粗细，默认 1.6（与设计稿一致） */
    strokeWidth: { type: [Number, String], default: 1.6 },
    /** 旋转动画（用于 loading 图标） */
    spin: { type: Boolean, default: false },
    /** 仅当图标是唯一信息载体时传入，输出 role="img" + <title> */
    title: { type: String, default: '' },
  },
  computed: {
    shapes () {
      return ICONS[this.name] || []
    },
  },
}
</script>

<style scoped lang="less">
.screen-icon {
  display: block;
  flex: 0 0 auto;
  overflow: visible;

  &.is-spin {
    animation: screen-icon-spin 900ms linear infinite;
  }
}

@keyframes screen-icon-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .screen-icon.is-spin {
    animation-duration: 2400ms;
  }
}
</style>
