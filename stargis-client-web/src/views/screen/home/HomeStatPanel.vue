<template>
  <!--
    HomeStatPanel 首页统计面板（高保真还原，左右两块各一份）
    ==================================================================
    定尺取自高保真模型 首页.html：
      标题装饰 大标题-左_u21.png   (9, 95)    420×46      ← 左面板
               大标题-右_u24.png   (1491, 95) 420×46      ← 右面板
      标题文字                     (83, 104)  18px #FFFFFF
      面板底     bg-模块-左_u23.png (30, 149)  400×881
      内容区                       (50, 209)  360×785
        ├─ 统计卡  stat-card.png    360×120  + 3D 立方体切图 131×118
        ├─ 双卡    市级 / 区级       175×80，同时作为下方明细表的切换按钮
        └─ 明细表  table-head/body/row.png，表头 36 + 11 行 × 46

    ★ 2026-10-27 拆分：原先一块面板用页签装「出让情况 / 落实配套情况」两个统计，
      用户要求拆开 —— 左侧只放「出让地块情况统计」，右侧只放「落实配套情况统计」。
      于是本组件改成**一个面板一个统计**，由 side 决定镜像位置（左右底图 /
      标题装饰 / 标题对齐方式不同），页签逻辑整体删掉。
      同一套渲染逻辑服务两块面板：数字、双卡、明细/排行全部由 section 驱动。

    保留的两处「按高保真 + 保留现有内容」扩展（已与需求方确认）：
      1. 明细区右上角「明细 / 排行」切换：默认「明细」与设计稿完全一致，
         「排行」展示原有「各区出让排行 / 各区配套落实」，不丢失既有内容。
         ★ 2026-11-12：排行改为按项目分类**分开展示** —— 复用上方「市级 / 区级」
           双卡作为切换器，同一时刻只看一份榜单（两组量级不同，混排没有可比性）。
      2. 数字全部来自接口（不再是设计稿里的 909 / 245 / 664 占位值）。
         圆环的弧长也改成按数据现画（内联 SVG），不再用设计稿里 28% / 72% 的定值切图。
  -->
  <div class="home-stat" :class="`is-${side}`" :style="rootStyle">
    <!-- 面板标题 -->
    <img class="home-stat__title-deco" :src="side === 'left' ? hf.panelTitleLeft : hf.panelTitleRight" alt="" aria-hidden="true" />
    <h2 class="home-stat__title">{{ title }}</h2>

    <!--
      面板宽度调节条（2026-11）：贴在面板**内侧竖边**。
      左面板贴右边缘、往右拖变宽；右面板贴左边缘、往左拖变宽（reverse）。
      折叠时藏起来 —— 面板已经滑出屏幕，留在原地的拖动条没有意义，
      而且会和屏边把手抢点击区域。
    -->
    <screen-resize-bar
      v-show="!collapsed"
      class="home-stat__resizer stage-hit"
      orientation="vertical"
      :reverse="side === 'right'"
      :value="width"
      :min="widthMin"
      :max="widthMax"
      :aria-label="side === 'left' ? '拖动调整左侧统计面板宽度' : '拖动调整右侧面板宽度'"
      @input="handleResize"
    />

    <!-- 面板主体 -->
    <section class="home-stat__panel stage-hit">
      <!--
        「明细 / 排行」切换（高保真没有这个控件）。
        需求方要求保留原有的「各区出让排行 / 各区配套落实」，所以做成面板
        右上角的小按钮：默认「明细」与设计稿完全一致，点一下切到排行视图。
        ★ 2026-10-27：面板页签删掉后，这里正好是设计稿页签条右端的留白位置
          （原页签止于面板内 x=335、内宽 400），所以按钮回到这个角上，
          而不是挤进 36px 高的表头行里压住表头切图。
      -->
      <button
        type="button"
        class="home-stat__view-switch stage-hit"
        :aria-pressed="String(viewMode === 'rank')"
        :title="viewMode === 'table' ? '切换到排行视图' : '切换到明细视图'"
        @click="viewMode = viewMode === 'table' ? 'rank' : 'table'"
      >
        {{ viewMode === 'table' ? '排行' : '明细' }}
      </button>

      <!-- 内容区 -->
      <div class="home-stat__body">
        <!-- 统计卡 -->
        <div class="home-stat__stat">
          <img class="home-stat__stat-bg" :src="hf.statCard" alt="" aria-hidden="true" />
          <img
            class="home-stat__stat-cube"
            :src="section.cube === 'b' ? hf.statCubeB : hf.statCubeA"
            alt=""
            aria-hidden="true"
          />
          <span class="home-stat__stat-label">{{ stat.label }}</span>
          <span class="home-stat__stat-figure">
            <span class="home-stat__stat-value">{{ stat.value }}</span>
            <span class="home-stat__stat-unit">{{ stat.unit }}</span>
          </span>
        </div>

        <!-- 市级 / 区级 双卡（兼作明细切换） -->
        <div class="home-stat__split">
          <button
            v-for="(item, index) in split"
            :key="item.key"
            type="button"
            class="home-stat__split-card"
            :class="{ 'is-active': item.key === activeSplit }"
            :style="{ left: splitLeft(index), width: SPLIT_WIDTH }"
            @click="activeSplit = item.key"
          >
            <img
              class="home-stat__split-bg"
              :src="item.key === activeSplit ? hf.splitCardActive : hf.splitCard"
              alt=""
              aria-hidden="true"
            />
            <img
              class="home-stat__ring-track"
              :src="hf.ringTrack"
              alt=""
              aria-hidden="true"
            />
            <!--
              进度弧用内联 SVG 现画，不再用设计稿那 4 张「定值弧」切图。
              ★ 切图的问题（就是圆环看起来「乱」的原因）：
                1) 弧长写死（市级 28%、区级 72%），数据一变弧长就对不上；
                2) 四张图的画布尺寸各不相同（76×76 / 50×76），
                   塞进 56×56 / 30×56 的盒子里会被裁切、横向压扁，
                   区级那张还会变成一条竖着的椭圆弧。
              这里统一按 56×56 画布、r=26.25、线宽 3.1 重画，
              几何与 ring-track.svg 的圆环完全重合；弧长由 percent 决定，
              起止角从 12 点开始顺时针（与旧系统 canvas 的 1.5π 起笔一致）。
            -->
            <svg class="home-stat__ring-arc" viewBox="0 0 56 56" aria-hidden="true">
              <!-- percent=0 时不画：圆头线帽会把零长度弧画成一个点 -->
              <circle
                v-if="ringDash(item) > 0"
                class="home-stat__ring-arc-line"
                cx="28"
                cy="28"
                r="26.25"
                :stroke="ringColor(item)"
                :style="{ filter: `drop-shadow(0 0 3px ${ringColor(item)})` }"
                :stroke-dasharray="`${ringDash(item)} 999`"
                transform="rotate(-90 28 28)"
              />
            </svg>
            <!--
              高保真里圆环文字是 14px、只显示整数百分比（28%），
              精确到 2 位的占比放进 title，避免 6 个字符把 56px 的圆环撑破。
            -->
            <span class="home-stat__ring-text" :title="ringTitle(item)">{{ ringPercent(item) }}%</span>
            <span class="home-stat__split-label">{{ item.label }}</span>
            <span class="home-stat__split-figure">
              <span class="home-stat__split-value">{{ item.value }}</span>
              <span class="home-stat__split-unit">{{ item.unit }}</span>
            </span>
          </button>
        </div>

        <!--
          「明细 / 排行」切换。
          高保真没有这个控件；但需求方要求保留原有的「各区出让排行 / 各区配套落实」，
          做成明细区表头右侧的小按钮：默认「明细」与设计稿完全一致，点一下切到排行视图。
        -->
        <div class="home-stat__detail">
          <div class="home-stat__detail-head">
            <img class="home-stat__detail-head-bg" :src="hf.tableHead" alt="" aria-hidden="true" />
            <template v-if="viewMode === 'table'">
              <span
                v-for="col in columns"
                :key="col.key"
                class="home-stat__th"
                :style="{ left: TH_OFFSET[col.key] }"
              >{{ col.title }}</span>
            </template>
            <span v-else class="home-stat__th home-stat__th--rank">
              {{ rankTitleText }}
            </span>
          </div>

          <!-- 明细表 -->
          <div v-if="viewMode === 'table'" class="home-stat__table">
            <img class="home-stat__table-bg" :src="hf.tableBody" alt="" aria-hidden="true" />
            <div
              v-for="(row, index) in tableRows"
              :key="index"
              class="home-stat__tr"
              :class="{ 'is-alt': index % 2 === 0, 'is-total': row.isTotal }"
            >
              <img
                v-if="index % 2 === 0"
                class="home-stat__tr-bg"
                :src="hf.tableRow"
                alt=""
                aria-hidden="true"
              />
              <!--
                单元格按 columns 通用渲染：列与取值字段由页面（views/screen/index.vue）
                按面板下发，这里不再写死 plots / roads —— 否则「落实配套」面板会把
                已完成配套数显示到「涉及道路」列下。
              -->
              <span
                v-for="col in columns"
                :key="col.key"
                class="home-stat__td"
                :style="{ left: TH_OFFSET[col.key] }"
              >{{ row[col.key] }}</span>
            </div>

            <!-- 后端暂无该维度明细时明确留白，不用其它口径的数据顶替 -->
            <p v-if="!table.rows.length" class="home-stat__empty">
              {{ loading ? '加载中…' : '暂无数据' }}
            </p>
          </div>

          <!--
            排行（保留原有内容）。
            ★ 2026-11-12 起按「市级 / 区级」分开统计：榜单跟着上方双卡的选中项走，
              序号与条形都按该组内部归一。后端返回的 ranks 是把两级混在一张表里
              按 value 全局倒序的，直接渲染会出现「河西区（市级）36」排在
              「津南区（区级）66」下面 —— 两个量级不同的集合互相比较，榜单没有意义。
          -->
          <div v-else class="home-stat__rank">
            <img class="home-stat__table-bg" :src="hf.tableBody" alt="" aria-hidden="true" />
            <div
              v-for="(item, index) in rankRows"
              :key="item.name"
              class="home-stat__rank-row"
            >
              <img
                v-if="index % 2 === 0"
                class="home-stat__tr-bg"
                :src="hf.tableRow"
                alt=""
                aria-hidden="true"
              />
              <span class="home-stat__rank-no">{{ pad(index + 1) }}</span>
              <span class="home-stat__rank-name">{{ item.name }}</span>
              <span class="home-stat__rank-bar">
                <i :style="{ width: `${barWidth(item.value)}%` }" />
              </span>
              <span class="home-stat__rank-value">{{ item.value }}</span>
            </div>

            <!--
              拆成两级之后，某一级没有分组数据（例如库里的区级项目全都落在
              已删除 / 没有 xzqh2 的宗地上）是可能的，明确留白，不复用另一级的榜单。
            -->
            <p v-if="!rankRows.length" class="home-stat__empty">
              {{ loading ? '加载中…' : '暂无数据' }}
            </p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'
import ScreenResizeBar from '@/components/screen/ScreenResizeBar.vue'

/**
 * 表头 / 单元格的横向偏移。
 *
 * ★ 2026-11 起改成**百分比**（以设计稿内容宽 360 换算），不再写死 px：
 *   左右面板宽度现在可以拖动（400 → 340~560），写死 186/283 会让列永远贴在
 *   左边、右边空一大片。设计稿比例下换算结果与原来的 px 完全一致
 *   （14/360 = 3.8889% → 400 宽时仍是 14px）。
 *
 * facilities / completed 沿用 plots / roads 的偏移：两块面板只是换列名与取值字段，
 * 设计稿的横向定尺不变，所以不新增一套坐标，避免两块面板的列宽视觉漂移。
 */
const CONTENT_WIDTH = 360
const pct = (value) => `${((value / CONTENT_WIDTH) * 100).toFixed(4)}%`

const TH_OFFSET = {
  index: pct(14),
  name: pct(52),
  plots: pct(186),
  roads: pct(283),
  facilities: pct(186),
  completed: pct(283)
}

/** 双卡横向位置 / 宽度：设计稿两张 175 宽，左卡 x=0、右卡 x=185（间距 10） */
const SPLIT_LEFT = [pct(0), pct(185)]
const SPLIT_WIDTH = pct(175)

/** 面板滑出距离的屏边留白：折叠位移 = 面板宽 + 30（与 rail / 面板 right/left 对齐） */
const PANEL_EDGE = 30

const padIndex = (n) => (n < 10 ? `0${n}` : `${n}`)

/** 空 section 兜底：接口没回来时面板保持结构与空白，不用设计稿占位值 */
const EMPTY_SECTION = {
  cube: 'a',
  stat: { label: '', value: 0, unit: '宗' },
  split: [],
  columns: [],
  tables: { city: { rows: [], total: null }, district: { rows: [], total: null } }
}

/** 空排行：市级 / 区级各一份，缺一份就只渲染另一份（不互相顶替） */
const EMPTY_RANK = { city: [], district: [] }

/** 双卡 key → 排行表头里的分组后缀 */
const SPLIT_LABEL = { city: '市级', district: '区级' }

export default {
  name: 'HomeStatPanel',
  components: { ScreenResizeBar },
  props: {
    /** 面板朝向：left = 出让地块情况统计 / right = 落实配套情况统计（决定镜像位置与底图） */
    side: {
      type: String,
      default: 'left',
      validator: (value) => value === 'left' || value === 'right'
    },
    /** 面板标题（高保真里左是固定文案，右侧同理） */
    title: { type: String, default: '' },
    /** 本面板要展示的那一份统计（出让情况 / 落实配套情况），结构见 index.vue 的 emptyPanelSection */
    section: { type: Object, default: () => EMPTY_SECTION },
    /**
     * 各区排行（「排行」视图用），★ 按项目分类分成两份：{ city: [], district: [] }。
     * 左 = 各区出让排行、右 = 各区配套落实，两级各排各的（不混排）。
     * 结构由 views/screen/index.vue 的 rankGroups 生成。
     */
    rank: { type: Object, default: () => EMPTY_RANK },
    /** 排行视图的表头文案；不传时按 side 兜底。实际渲染时会在后面补「（市级）/（区级）」 */
    rankTitle: { type: String, default: '' },
    /** 接口加载态（只影响留白文案） */
    loading: { type: Boolean, default: false },
    /** 面板是否已折叠（整体滑出屏幕；把手由屏边 ScreenSideRail 提供） */
    collapsed: { type: Boolean, default: false },
    /**
     * 面板宽度（设计画布像素）。由 views/screen/index.vue 的 panelLayout 下发，
     * 拖动内侧的 ScreenResizeBar 时通过 width-change 事件回传。
     */
    width: { type: Number, default: 400 },
    /** 宽度下限 / 上限（同样来自 panelLayout，避免组件里再写一套口径） */
    widthMin: { type: Number, default: 340 },
    widthMax: { type: Number, default: 560 }
  },
  data () {
    return {
      hf,
      TH_OFFSET,
      SPLIT_WIDTH,
      /** 与 split 双卡一一对应：city = 市级，district = 区级 */
      activeSplit: 'city',
      viewMode: 'table'
    }
  },
  computed: {
    stat () {
      return this.section.stat || EMPTY_SECTION.stat
    },
    split () {
      return this.section.split || []
    },
    columns () {
      return this.section.columns || []
    },
    table () {
      const tables = this.section.tables || {}
      return tables[this.activeSplit] || tables.city || { rows: [], total: null }
    },
    /** 明细表行（补上序号，并把合计行单独标记） */
    tableRows () {
      const rows = this.table.rows.map((row, index) => ({ ...row, index: index + 1 }))
      if (this.table.total) rows.push({ ...this.table.total, index: '', isTotal: true })
      return rows
    },
    /**
     * 当前选中（市级 / 区级）那一份排行。
     * ★ 双卡本来就是「市级 / 区级」切明细表的控件，排行跟着同一个选中项走：
     *   同一时刻只展示一份榜单，序号与条形都按该组内部归一。
     *   某一份为空时**不**退回另一份 —— 拿区级榜单冒充市级会让人误读。
     */
    rankRows () {
      const groups = this.rank || EMPTY_RANK
      return groups[this.activeSplit] || []
    },
    rankTitleText () {
      const base = this.rankTitle || (this.side === 'left' ? '各区出让排行' : '各区配套落实')
      // 分组后缀优先取双卡自身的文案（保证与卡片标签一字不差），没配双卡时按 key 兜底
      const card = this.split.find((item) => item.key === this.activeSplit)
      const label = (card && card.label) || SPLIT_LABEL[this.activeSplit]
      return label ? `${base}（${label}）` : base
    },
    /** 条形长度按当前这一组的最大值归一（两组量级不同，不能共用一把尺子） */
    rankMax () {
      return this.rankRows.reduce((max, item) => Math.max(max, Number(item.value) || 0), 1)
    },
    /**
     * 根节点内联样式：宽度变量 + 折叠位移。
     * ★ 宽度用 CSS 变量（--home-panel-w）下发而不是逐个改子元素：
     *   面板底图、标题装饰、拖动条位置都靠 var() 联动，宽度只维护一处。
     * ★ 折叠位移随宽度变（面板宽 + 30），写死 430 在拖宽后就滑不干净。
     */
    rootStyle () {
      const style = { '--home-panel-w': `${this.width}px` }
      if (!this.collapsed) return style
      const distance = this.width + PANEL_EDGE
      style.transform = `translateX(${this.side === 'right' ? distance : -distance}px)`
      return style
    }
  },
  watch: {
    /** 双卡选中项跟着数据走：section 换了（接口回来 / 换成另一份统计）就回到第一张卡 */
    split (list) {
      if (!list.some((item) => item.key === this.activeSplit)) {
        this.activeSplit = (list[0] && list[0].key) || 'city'
      }
    }
  },
  methods: {
    pad: padIndex,
    /** 双卡横向位置（百分比）：0 / 51.3889%，随面板变宽按比例摊开 */
    splitLeft (index) {
      return SPLIT_LEFT[index] || SPLIT_LEFT[0]
    },
    /** 拖动面板内侧的调节条：把新宽度抛给页面（页面写进 panelLayout 并持久化） */
    handleResize (value) {
      this.$emit('width-change', value)
    },
    /**
     * 环形图进度色：出让情况绿/青，落实配套红/金。
     * 四个色值都取自高保真切图（#43e972 / #54c9c3 / #e95b43 / #c99e54）——
     * 切图弃用后颜色直接写在这里，保证观感与设计稿一致。
     */
    ringColor (item) {
      const palette = this.section.cube === 'b'
        ? { city: '#e95b43', district: '#c99e54' }
        : { city: '#43e972', district: '#54c9c3' }
      return palette[item.key] || palette.city
    },
    /**
     * 进度弧长（px）。圆周长按 r=26.25 计算，percent 是精确百分比（如 25.38）。
     * 0% → "0 999" 不画；100% → 正好一整圈。
     */
    ringDash (item) {
      const circumference = 2 * Math.PI * 26.25
      const ratio = Math.min(Math.max(Number(item.percent || 0) / 100, 0), 1)
      return Number((circumference * ratio).toFixed(2))
    },
    /** 圆环文字：高保真只显示整数百分比（28%），精确值放 title */
    ringPercent (item) {
      return Math.round(Number(item.percent || 0))
    },
    ringTitle (item) {
      return `占比 ${Number(item.percent || 0).toFixed(2)}%`
    },
    barWidth (value) {
      return Math.max(4, Math.round((value / this.rankMax) * 100))
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.home-stat {
  /*
    铺满设计画布后再整体做位移（折叠 = 整体滑出屏幕）。
    ⚠ 根节点必须是「定尺画布大小」的定位容器：面板 / 标题都是 absolute、
      坐标相对画布写死（left:30 / right:9 …），根节点一旦不是 inset:0，
      它们就会相对一个 0 尺寸的盒子定位，位置全飞。
    根节点不开 pointer-events（画布整体穿透），只有面板与控件带 .stage-hit。
    ★ 折叠位移用 rootStyle（内联 transform，左右方向不同；距离 = 面板宽 + 30）
      下发，这里只负责给出过渡时长 —— 位移写在 CSS 类里会让左右两套规则
      各自维护一份距离。
  */
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  transition: transform var(--screen-duration-slow) var(--screen-ease);

  &__title-deco {
    position: absolute;
    top: 95px;
    // 设计稿 420 = 面板 400 + 21（面板 left 30、装饰 left 9）；
    // 面板宽度可拖动，装饰跟着一起伸缩，否则变宽后内侧会空一条。
    width: calc(var(--home-panel-w, 400px) + 21px);
    height: 46px;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    white-space: nowrap;
  }

  &__panel {
    position: absolute;
    // 上贴 149、下贴 50：正文区随设计画布高度伸展。
    // 设计比例（1080 高）下高度正好是 881，与设计稿一致；
    // 比 16:9 窄的窗口画布会更高，面板跟着长高，底部不会空出一片。
    top: 149px;
    bottom: 50px;
    // 宽度由 panelLayout 下发（拖动内侧调节条改变），底图随宽度拉伸
    width: var(--home-panel-w, 400px);
    background-repeat: no-repeat;
    background-size: 100% 100%;
  }

  /* 宽度调节条：贴在面板内侧竖边（左面板贴右缘、右面板贴左缘），上下与面板对齐 */
  &__resizer {
    position: absolute;
    top: 149px;
    bottom: 50px;
    z-index: 5;
  }

  &.is-left &__resizer {
    left: calc(30px + var(--home-panel-w, 400px) - 6px);
  }

  &.is-right &__resizer {
    right: calc(30px + var(--home-panel-w, 400px) - 6px);
  }

  /* ---------------- 左侧（出让地块情况统计）：设计稿原样 ---------------- */
  &.is-left &__title-deco {
    left: 9px;
  }

  &.is-left &__title {
    left: 83px;
  }

  &.is-left &__panel {
    left: 30px;
    background-image: url('~@/assets/screen-blue/panel-left-bg.png');
  }

  /* ---------------- 右侧（落实配套情况统计）：标题装饰 / 底图镜像 ---------------- */
  /* 右面板贴 right:30、宽 400；标题装饰右缘距画布 9px（设计稿 x=1491..1911），
     标题文字右对齐到距画布 85px —— 与右面板「图层树」标题同一条基线。 */
  &.is-right &__title-deco {
    right: 9px;
  }

  &.is-right &__title {
    right: 85px;
    text-align: right;
  }

  &.is-right &__panel {
    right: 30px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
  }

  /* ---------------- 内容区 ---------------- */
  &__body {
    position: absolute;
    left: 20px;
    top: 60px;
    right: 20px;
    // 设计稿 881 - 60 - 785 = 36
    bottom: 36px;
  }

  &__stat {
    position: absolute;
    left: 0;
    top: 3px;
    width: 100%;
    height: 120px;
  }

  &__stat-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 120px;
    display: block;
  }

  &__stat-cube {
    position: absolute;
    left: 12px;
    top: 0;
    width: 131px;
    height: 118px;
    display: block;
  }

  &__stat-label {
    position: absolute;
    // 设计稿 156 / 360 = 43.3333%：面板变宽时文字与数值区一起内缩比例
    left: 43.3333%;
    top: 29px;
    font-size: 16px;
    font-weight: 500;
    line-height: 16px;
    color: #ffffff;
    white-space: nowrap;
  }

  /*
   * 数值 + 单位排成一行。
   * ⚠ 不能用「各自绝对定位」：数值位数一变（2 位 / 3 位 / 4 位）单位就会压上去。
   *   用 flex 让单位始终跟在数值右侧，数值再长也只是往右推。
   *   字号/行高取设计稿原值（u37 = 28px / 21px、u38 = 16px），
   *   之前放大到 32px 是「看起来更醒目」，但会把统计卡的文字压到卡片下沿。
   */
  &__stat-figure {
    position: absolute;
    left: 43.3333%;
    right: 16px;
    top: 59px;
    display: flex;
    align-items: baseline;
    gap: 4px;
    min-width: 0;
  }

  &__stat-value {
    font-family: var(--screen-font-number-family);
    font-size: 28px;
    font-weight: 500;
    line-height: 21px;
    color: #ffe597;
    font-variant-numeric: tabular-nums;
  }

  &__stat-unit {
    font-size: 16px;
    line-height: 1;
    color: #66afd4;
  }

  /* ---------------- 市级 / 区级 ---------------- */
  &__split {
    position: absolute;
    left: 0;
    top: 133px;
    width: 100%;
    height: 80px;
  }

  &__split-card {
    position: absolute;
    top: 0;
    // 设计稿 175 / 360 = 48.6111%（另一半留给 10px 间距），宽度随面板伸缩
    width: 48.6111%;
    height: 80px;
    padding: 0;
    background: transparent;
    border: 0;
    cursor: pointer;
    .screen-focus-ring();
  }

  &__split-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 80px;
    display: block;
  }

  &__ring-track,
  &__ring-arc {
    position: absolute;
    left: 20px;
    top: 10px;
    width: 56px;
    height: 56px;
    display: block;
    pointer-events: none;
  }

  /* 进度弧：几何与 ring-track.svg 重合（r=26.25 / 线宽 3.1），弧长由数据决定 */
  &__ring-arc-line {
    fill: none;
    stroke-width: 3.1;
    stroke-linecap: round;
  }

  /*
   * 圆环文字：设计稿 u46 是 left 36 / top 32 / 27×11 / 14px。
   * 这里用「left 20 + width 56 + 居中」代替：圆心正好 48，
   * 位数变化（9% / 100%）时也不会偏离圆心；top 31 + 行高 14 让文字在圆心垂直居中。
   */
  &__ring-text {
    position: absolute;
    left: 20px;
    top: 31px;
    width: 56px;
    height: 14px;
    font-family: var(--screen-font-number-family);
    font-size: 14px;
    font-weight: 500;
    line-height: 14px;
    color: #ffffff;
    text-align: center;
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
  }

  &__split-label {
    position: absolute;
    left: 95px;
    top: 19px;
    font-size: 14px;
    line-height: 14px;
    color: #66afd4;
    white-space: nowrap;
  }

  /* 数值 + 单位同样排成一行，避免位数变化时单位被压住 */
  &__split-figure {
    position: absolute;
    left: 95px;
    right: 8px;
    top: 41px;
    display: flex;
    align-items: baseline;
    gap: 4px;
    min-width: 0;
  }

  &__split-value {
    font-family: var(--screen-font-number-family);
    font-size: 20px;
    font-weight: 500;
    line-height: 15px;
    color: #ffffff;
    font-variant-numeric: tabular-nums;
  }

  &__split-unit {
    font-size: 12px;
    line-height: 1;
    color: #66afd4;
  }

  /* ---------------- 明细 / 排行 ---------------- */
  &__detail {
    position: absolute;
    left: 0;
    top: 233px;
    right: 0;
    // 设计稿 785 - 233 = 552，高度随面板一起伸展
    bottom: 0;
  }

  &__detail-head {
    position: absolute;
    left: 1px;
    right: 0;
    top: 0;
    height: 36px;
  }

  &__detail-head-bg {
    position: absolute;
    left: 0;
    top: 0;
    // 设计稿里表头切图 357 宽，占 359 的表头区、右边留 2px。
    // ⚠ <img> 必须显式给宽度，只写 left/right 不会拉伸
    width: calc(100% - 2px);
    height: 36px;
    display: block;
  }

  &__th {
    position: absolute;
    top: 11px;
    /* 设计稿 u69~u72：14px / 行高 14 / top 11 */
    font-size: 14px;
    line-height: 14px;
    color: #ffffff;
    white-space: nowrap;

    &--rank {
      left: 14px;
    }
  }

  /*
    「明细 / 排行」切换：高保真无此控件，放在面板页签条右端的留白处。
    ★ 2026-10-27 页签删除后回到原位置：设计稿页签止于面板内 x=335，
      按钮右缘贴面板内右侧 5px（= 旧实现的 left:345 / width:50）。
  */
  &__view-switch {
    position: absolute;
    right: 5px;
    top: 14px;
    width: 50px;
    height: 30px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-xs);
    line-height: 28px;
    color: #82c6ff;
    background: rgba(85, 185, 255, 0.12);
    border: 1px solid var(--screen-border);
    border-radius: var(--screen-radius-sm);
    cursor: pointer;
    transition: color var(--screen-duration) var(--screen-ease),
      background-color var(--screen-duration) var(--screen-ease);
    .screen-focus-ring();

    &:hover {
      color: #ffffff;
      background: rgba(85, 185, 255, 0.24);
    }

    &[aria-pressed='true'] {
      color: #ffffff;
      background: rgba(85, 185, 255, 0.28);
      border-color: var(--screen-border-strong);
    }
  }

  &__table,
  &__rank {
    position: absolute;
    left: 0;
    right: 0;
    top: 46px;
    bottom: 0;
    overflow-x: hidden;
    overflow-y: auto;
  }

  &__table {
    .home-stat__tr:last-child {
      position: sticky;
      bottom: 0;
      z-index: 1;
      background: rgba(5, 24, 48, 0.96);
    }
  }

  &__table-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    display: block;
    pointer-events: none;
  }

  /* 后端暂无该维度明细时的留白提示 */
  &__empty {
    position: absolute;
    left: 0;
    top: 120px;
    width: 100%;
    margin: 0;
    font-size: var(--screen-font-sm);
    color: #4a7396;
    text-align: center;
  }

  /*
   * 行高必须回到设计稿的 46px（u58/u59/u60 切图就是 359×46）。
   * 之前写 54px 是为了容纳放大的 17px 字号，结果把 46px 的行底切图
   * 纵向拉长了 17%，行与行之间的渐变和分隔线全部变形 —— 这是表格看起来「乱」的直接原因。
   */
  &__tr,
  &__rank-row {
    position: relative;
    width: 100%;
    height: 46px;
  }

  &__tr-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 46px;
    display: block;
    pointer-events: none;
  }

  &__td {
    position: absolute;
    /* 14px 文字在 46px 行内垂直居中：(46 - 14) / 2 = 16 */
    top: 16px;
    font-size: 14px;
    line-height: 14px;
    color: #66afd4;
    white-space: nowrap;
  }

  &__tr.is-total &__td {
    color: #ffffff;
    font-weight: 500;
  }

  /* 排行条 */
  &__rank-no {
    position: absolute;
    left: 14px;
    top: 16px;
    font-size: 14px;
    color: #4a7396;
    font-variant-numeric: tabular-nums;
  }

  &__rank-name {
    position: absolute;
    left: 52px;
    top: 16px;
    font-size: 14px;
    color: #66afd4;
    white-space: nowrap;
  }

  &__rank-bar {
    position: absolute;
    // 设计稿 148 / 360、130 / 360：面板变宽时条形按比例拉长，不挤在左边
    left: 41.1111%;
    top: 18px;
    width: 36.1111%;
    height: 9px;
    border-radius: var(--screen-radius-pill);
    background: var(--screen-bar-track);
    overflow: hidden;

    i {
      display: block;
      height: 100%;
      border-radius: var(--screen-radius-pill);
      background-image: linear-gradient(90deg, var(--screen-bar-from) 0%, var(--screen-bar-to) 100%);
    }
  }

  &__rank-value {
    position: absolute;
    right: 14px;
    top: 16px;
    font-family: var(--screen-font-number-family);
    font-size: 14px;
    color: #ffffff;
    font-variant-numeric: tabular-nums;
  }
}
</style>
