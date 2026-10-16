<template>
  <!--
    ScreenPickStage 「选取地块」模式
    ==================================================================
    需求（2026-10 用户实测后改口径）：在**经营性用地 / 配套项目**的新增·编辑表单里
    点「选取地块」后，
      ① ~~所有页面全部隐藏，只显示下方地图~~ → **不再隐藏任何页面**；
      ② 页面上方中间有两个明显的按钮：取消选择 / 选择完成（下移到顶栏之下，见 &__actions）；
      ③ 点这两个按钮后回到表单（表单**不关**，填到一半的内容都在）。

    ★ 为什么「不再隐藏页面」：这一版把表单弹窗改成了「可拖动 + 背景不压暗」
      （ScreenModal 的 draggable / :dimmed="false"），用户把窗口往旁边一拖，
      地图本来就随时可点可拖。再整屏藏一遍页面反而把「填到一半的表单」一起藏了，
      用户想核对一下刚填的字段都得先退出选取模式。
      所以本组件的职责从「盖住一切的地图模式」收缩成「两个按钮 + 一句提示」。

    实现要点（每一条都是踩过坑才这么写的）：

    1. **本层为什么要挂到 document.body**
       发起方是 ScreenModal 里的表单弹窗，而 ScreenModal 本身就在 body 上、z-index 1200；
       本层如果留在设计画布（ScreenStage 的缩放层）里，既会被弹窗盖住，
       又会被 transform: scale 缩放，地图坐标全错。
       挂 body + z-index **1250**（高于 ScreenModal 1200、低于 ScreenPopover 1260；
       ScreenToast 1300 仍在最上面，这是有意的 —— 提示必须看得见）。

    2. **地图不重新创建，只是「露出来」**
       本层刻意**不做背景**（transparent）也不加 backdrop-filter：
       下面那层就是 views/screen/index.vue 里那个常驻的真实 Cesium 地图
       （<s3dm-viewer>，pointer-events 由它自己管），这里只要不挡就没问题。
       Cesium 容器 #home 的 canvas 是**布局尺寸**（不受父级 opacity / visibility 影响），
       所以任何时候都不要用 display: none 去「藏」它 —— 会让 canvas 尺寸归零、
       恢复时要重新 resize，镜头还会跳一下。

    3. **本层自己不吃鼠标**
       本模式是「操作地图 + 选地块」，拖动 / 缩放 / 拾取都必须落到**下面的地图**上。
       所以根节点是 `pointer-events: none`，只有两个按钮显式 `pointer-events: auto`。
       ⚠ 这里**不能**写成根节点 `pointer-events: auto`：透明元素照样参与命中测试，
       整屏铺一个 auto 的 div 会把地图的鼠标事件全部吃掉，表现是
       「地图拖不动、滚轮不缩放」，而提示文案却还写着「地图可以自由拖动与缩放」。
       （这是本项目 README 第 3 条踩坑的同一个道理，只是方向相反：
       那边是「浮层要吃事件必须显式开」，这边是「整屏浮层必须显式关」。）

    4. **绘制功能尚未接（需求明确「先不做，把方法预留」）**
       绘制入口在 drawTools 里：现在是一个**空实现**的占位对象，
       每个方法只写日志并抛一条 toast，不碰 Cesium。
       接入时的落点也写在那里（draw-geometric / draw-graph 等插件组件）。

    用法（只在 views/screen/index.vue 里渲染一次，慎增第二处）：
      <screen-pick-stage
        v-if="pickActive"
        :map-ready="mapEnabled"
        @cancel="handlePickCancel"
        @done="handlePickDone"
      />
  -->
  <div class="pick-stage" role="region" aria-label="选取地块">
    <!--
      按钮背后的一小块柔光：让按钮在任何底图上都读得清。
      ★ 2026-10 起它**只盖住按钮那一小块**（原来是从视口最上沿拉满整宽的渐变）——
        页面不再隐藏后，整宽渐变会把露出来的顶栏也一起压暗，看起来像界面出了问题。
      仍然不拦截鼠标（地图要能拖）。
    -->
    <div class="pick-stage__scrim" aria-hidden="true" />
    <!-- ===== 页面上方中间的两个按钮（下移到顶栏之下，不压导航） ===== -->
    <div class="pick-stage__actions">
      <screen-button
        class="pick-stage__action is-cancel"
        type="default"
        size="lg"
        icon="close"
        aria-label="取消选择"
        @click="handleCancel"
      >
        取消选择
      </screen-button>

      <screen-button
        class="pick-stage__action is-done"
        type="primary"
        size="lg"
        icon="check"
        aria-label="选择完成"
        @click="handleDone"
      >
        选择完成
      </screen-button>
    </div>

    <!-- 一句话说明：用户从表单跳进来，需要知道「现在在选什么、怎么回去」 -->
    <p class="pick-stage__hint">
      <screen-icon name="info" :size="14" />
      {{ hintText }}
    </p>

    <!-- 地图没起来时的兜底提示（?map=0 预览、或 Cesium 初始化失败） -->
    <p v-if="!mapReady" class="pick-stage__notice" role="status">
      <screen-icon name="alert-triangle" :size="14" />
      地图尚未加载，当前只显示选取界面。地图服务不可用时请用 ?map=0 预览界面，
      接入真实地图后这里会显示下方的三维地图。
    </p>
  </div>
</template>

<script>
import ScreenButton from './ScreenButton.vue'
import ScreenIcon from './ScreenIcon.vue'
import { toast } from './toast'

/** 各发起方的兜底提示文案（title 为空时用） */
const HINT_BY_SOURCE = {
  land: '正在选取经营性用地地块：可先把表单窗口拖到一边，再在地图上操作',
  facility: '正在选取配套项目地块：可先把表单窗口拖到一边，再在地图上操作'
}

/**
 * 地图绘制工具 —— **占位实现**（需求：绘制功能先不做，只把方法预留）。
 *
 * 现在每个方法只提示「尚未开放」，不调用任何 Cesium / stargis-function 接口，
 * 因此点按钮不会把界面搞坏。后续接入时按下面三步做，**不用改组件其余部分**：
 *
 *   ① 把绘制引擎挂进覆盖层（与 views/screen/map/index.vue 借用
 *      <query-pick ref="pickEngine" v-show="false" /> 的写法一致）：
 *        <draw-geometric v-show="false" ref="drawGeometric" />
 *      这些组件由 stargis-function 全局注册（见 src/main.js 的 Vue.use(StargisFunction)），
 *      直接写标签即可，不必 import。
 *   ② 在 start() 里调用它的激活方法（btnClick() / controlChecked()，
 *      不同插件方法名不同，参考 views/maps/components/Common/HomeCommonFooter.vue 的 cancelAll）。
 *      同时**必须**把 this.active 置为 true（见 start() 里的 TODO）。
 *   ③ 绘制结束时把图形交回去。★ 这里有个容易写错的地方：
 *      drawTools 是**普通对象**（createDrawTools 返回的字面量），
 *      `this` 指向它自己，**没有 `$emit`** —— 在 stop() 里写
 *      `this.$emit('done', geometry)` 会直接抛 "this.$emit is not a function"。
 *      正确做法是用闭包里的组件实例 vm：
 *        · 由按钮触发完成（handleDone）→ getGeometry() 返回图形即可，无需 emit；
 *        · 由插件异步画完（没有按钮点击）→ 在插件回调里调 vm.$emit('done', geometry)，
 *          并保证 handleDone 不会重复 emit（两者要有一处幂等）。
 *      geometry 建议是 GeoJSON 字符串 + 面积（与 t_land / t_supporting_facilities
 *      将来要存的字段对齐；**字段落库属于后端改动，本次不涉及**）。
 */
function createDrawTools (vm) {
  return {
    /** 绘制是否已激活（接入后由插件的 checked 同步） */
    active: false,

    /**
     * 激活绘制模式。
     * @returns {boolean} 是否真的开始绘制（占位实现恒为 false）
     */
    start () {
      vm.notifyDrawPending('开始绘制')
      // ⚠ TODO（接入绘制时必改）：这里要置 this.active = true，
      //   否则 stop() 第一行就 return，插件的监听永远停不掉。
      //   占位实现刻意保持 false —— 「没有开始过」比「假装开始过」诚实。
      return false
    },

    /** 结束绘制（取消选择 / 选择完成都会调用，必须幂等） */
    stop () {
      if (!this.active) return
      this.active = false
      // 接入后在这里调用插件的 cancelChecked()，并清掉未完成的临时图形
    },

    /** 取当前画好的图形；没有画时返回 null（表单收到 null 会保留字段原值） */
    getGeometry () {
      return null
    }
  }
}

export default {
  name: 'ScreenPickStage',
  components: { ScreenButton, ScreenIcon },
  props: {
    /**
     * 下方真实地图此刻是否可用。
     * 由 views/screen/index.vue 传入（?map=0 预览时是 false）。
     * ★ 这里**不负责**加载地图：地图是那一层的 <s3dm-viewer>，它必须保持挂载，
     *   本覆盖层只把界面藏起来把地图露出来。
     */
    mapReady: { type: Boolean, default: true },
    /**
     * 发起方标识：'land'（经营性用地）/ 'facility'（配套项目）。
     * 只影响兜底文案，不参与逻辑判断。
     */
    source: { type: String, default: '' },
    /** 发起方给的说明文案；为空时按 source 取兜底文案 */
    title: { type: String, default: '' }
  },
  data () {
    return {
      /** 绘制工具（占位实现，见 createDrawTools 的说明） */
      drawTools: null,
      /** Esc 的 keydown 处理器引用，便于销毁时精确移除 */
      escHandler: null
    }
  },
  computed: {
    hintText () {
      if (this.title) return this.title
      return HINT_BY_SOURCE[this.source] || '正在选取地块：可先把表单窗口拖到一边，再在地图上操作'
    }
  },
  created () {
    this.drawTools = createDrawTools(this)
  },
  mounted () {
    /*
     * ★★ 把本组件挂到 document.body 上。
     * ===============================================================
     * 本组件由 views/screen/index.vue 渲染在 <screen-stage> 的**默认插槽**里，
     * 于是真实父节点是 .screen-stage__canvas —— 它带 `position: absolute;
     * z-index: 1`（见 ScreenStage.vue），**自己就是一个层叠上下文**。
     * 在这个上下文里写 z-index: 1250 是没有跨出去的效果的：
     *   · body 层级上的 .screen-modal（ScreenModal，z-index 1200）实际画在**本层之上**；
     *   · 本层还会被画布的 `transform: scale(k)` 一起缩放（1920 宽视口下 k≈0.91），
     *     两个按钮的定尺尺寸随之缩水，与「挂满视口、定尺 54px 高」的自述不符。
     * 实测（1920×1080）：不挂 body 时按钮只有 154×49，且 .screen-modal 仍在其上方。
     *
     * ScreenModal / ScreenPopover 都是这么做的（document.body.appendChild(this.$el)），
     * 这里沿用同一套做法，保证 z-index 语义与注释一致。
     * ⚠ 卸载时必须把节点摘掉：本组件的 $el 已经不在 Vue 管的父节点下了，
     *   Vue 自身不会替我们移除，漏掉就会在每次开关选取模式后留下一个空 div。
     */
    document.body.appendChild(this.$el)

    /*
     * Esc = 取消选择。
     * 为什么用 window 监听而不是给本层加 @keydown：本层里两个按钮才是可聚焦元素，
     * 用户点完按钮焦点未必还在本层内（选取时要操作地图，焦点本来就常在地图上），
     * 键盘用户按 Esc 会「没反应」。
     * ★★ 必须用**捕获阶段**（第三个参数 true）：下面那张表单弹窗（ScreenModal）的
     *    Escape 处理挂在它自己的 dialog 元素上。进入选取模式后弹窗**仍然可见**
     *    （2026-10 起不再隐藏），焦点很可能就在弹窗里某个输入框上，
     *    事件从弹窗冒泡到 window，**弹窗的 @keydown 会先跑** ——
     *    结果就是「按 Esc 想取消选择，却把整张填了一半的表单一起关掉」。
     *    在捕获阶段先拦下来，可以保证 Esc 只作用于选取模式。
     *    同时用 preventDefault 阻断浏览器默认行为，stopPropagation 阻断后续所有监听。
     */
    this.escHandler = (event) => {
      if (event.key !== 'Escape' && event.key !== 'Esc') return
      event.preventDefault()
      event.stopPropagation()
      this.handleCancel()
    }
    window.addEventListener('keydown', this.escHandler, true)

    /*
     * 绘制引擎按需激活（占位实现里只弹提示）。
     * 放在 mounted 而不是 created：接入真实插件后要等 DOM 就绪才能拿 ref。
     */
    this.drawTools.start()
  },
  beforeDestroy () {
    /*
     * 摘掉挂到 body 上的节点（见 mounted 里的说明）。
     * ⚠ 顺序无关紧要，但**必须**做：$el 已不在 Vue 的父节点下，Vue 不会替我们移除。
     *   用 parentNode 判空再删，保证重复调用 / 已被移除时也不报错。
     */
    if (this.$el && this.$el.parentNode) {
      this.$el.parentNode.removeChild(this.$el)
    }
    if (this.escHandler) {
      window.removeEventListener('keydown', this.escHandler, true)
      this.escHandler = null
    }
    // 必须停绘制：否则退出选取模式后插件的拾取/绘制监听还挂在 Cesium 上
    if (this.drawTools) this.drawTools.stop()
  },
  methods: {
    /** 取消选择：不带走任何图形，只把控制权还给表单 */
    handleCancel () {
      if (this.drawTools) this.drawTools.stop()
      this.$emit('cancel')
    },

    /**
     * 选择完成：把画好的图形抛回表单。
     * ★ 本期绘制尚未接入，getGeometry() 恒为 null —— 表单收到 null 时**保留字段原值**，
     *   所以现在点「选择完成」等价于「关掉这张地图并回到表单」，
     *   不会有半个坐标被写进模型里。接入绘制后这里改成抛真实图形即可。
     */
    handleDone () {
      const geometry = this.drawTools ? this.drawTools.getGeometry() : null
      if (this.drawTools) this.drawTools.stop()
      if (!geometry) {
        // 明确告诉用户「没画东西 = 没写回字段」，避免以为是静默失败了
        toast.info('尚未绘制地块，本次没有写回任何字段')
      }
      this.$emit('done', geometry)
    },

    /**
     * 绘制功能未接入时的统一提示（占位实现专用）。
     * @param {string} action 用户刚做的动作，用于把提示说清楚
     */
    notifyDrawPending (action) {
      toast.info(`地图绘制选取功能尚未开放（${action}），当前可拖动、缩放地图查看地块`)
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.pick-stage {
  position: fixed;
  // 铺满视口，且高于 ScreenModal 的 1200（发起选取的表单弹窗要留在下面可见）。
  // ScreenToast 在 1300，提示会比本层更靠上，这是我们想要的。
  inset: 0;
  z-index: 1250;
  // ★ 刻意不设 background：下面就是真实地图，本层的任务就是「让地图露出来」。
  //   也刻意不加 backdrop-filter（整屏模糊在没有硬件加速时会掉到 100ms+，见 screen-mixins.less）。
  // ★★ 整屏 pointer-events: none —— 鼠标事件必须穿到下面的地图上（拖动 / 缩放 / 后续绘制）。
  //    透明元素同样参与命中测试，这里写成 auto 会把地图彻底挡死。
  //    两个按钮自己声明 auto（见 &__action），所以依旧可点。
  pointer-events: none;
  // 显式声明可见：本层挂在 body 上，不依赖任何父级；将来若有人给 body 加
  // 「隐藏浮层」的全局规则（2026-10 之前确实有过 body.is-picking-map），这一行能保住本层。
  visibility: visible;
  overflow: hidden;

  /*
   * 按钮背后的柔光：保证按钮在亮底图上也有对比度；不挡鼠标，地图仍可拖动。
   * ★ 只覆盖按钮那一小块（约 640×160 的椭圆），**不是**整宽渐变 ——
   *   页面不再隐藏后，整宽渐变会把露出来的顶栏也压暗，像界面坏了。
   * ★ 本层挂在 body 上、不在缩放画布内（见 mounted），所以这里的 px 是真实像素。
   */
  &__scrim {
    position: absolute;
    left: 50%;
    top: 126px;
    transform: translateX(-50%);
    width: 640px;
    height: 168px;
    pointer-events: none;
    background: radial-gradient(
      closest-side,
      rgba(1, 6, 18, 0.7) 0%,
      rgba(1, 6, 18, 0.42) 58%,
      rgba(1, 6, 18, 0) 100%
    );
  }

  /* ---------------- 上方中间的两个按钮 ---------------- */
  &__actions {
    position: absolute;
    left: 50%;
    /*
     * ★ 2026-10：本层不再隐藏页面，按钮**不能**再贴视口最上沿（原来 top: 24px），
     *   那会正好压在顶栏的导航菜单上。
     *   顶栏在设计画布里高 128px；本层是未缩放的真实像素层，而画布缩放 k ≤ 1，
     *   所以顶栏的真实下沿 ≤ 128px，取 150px 在任何常见视口下都留有余量。
     *   （实测 1894×987：k=0.914，顶栏下沿 117px。）
     */
    top: 150px;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: var(--screen-space-4);
    // 容器本身不占事件，避免两按钮之间的空隙吃掉地图拖动
    pointer-events: none;
  }

  &__action {
    // 需求要求「明显」：比常规按钮更高更宽、字更大更粗、带辉光。
    // ★ 只加尺寸与阴影，不改 ScreenButton 的类型色（primary / default 语义不变）
    min-width: 168px;
    height: 54px;
    padding: 0 28px;
    font-size: var(--screen-font-lg);
    font-weight: 700;
    letter-spacing: 0.06em;
    border-radius: var(--screen-radius-lg);
    box-shadow: var(--screen-shadow);
    // 两个按钮必须自己收事件（父级的 pointer-events: none 会传下来）
    pointer-events: auto;

    &.is-done {
      // 主按钮的渐变来自 ScreenButton，这里只补一层辉光把它顶起来
      box-shadow: var(--screen-shadow), 0 0 18px var(--screen-accent-glow);
    }
  }

  /* ---------------- 说明了什么 / 没起来的地图 ---------------- */
  &__hint {
    position: absolute;
    left: 50%;
    // 跟着按钮一起下移：按钮 150 + 高 54，这里留 12px 间距
    top: 216px;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0;
    padding: 6px 14px;
    font-size: var(--screen-font-sm);
    line-height: 1.6;
    white-space: nowrap;
    color: var(--screen-text);
    background: rgba(8, 22, 42, 0.72);
    border: 1px solid var(--screen-border-soft);
    border-radius: var(--screen-radius-pill);
    pointer-events: none;

    /deep/ .screen-icon {
      color: var(--screen-accent);
    }
  }

  &__notice {
    position: absolute;
    left: 50%;
    bottom: var(--screen-space-6);
    transform: translateX(-50%);
    display: flex;
    align-items: flex-start;
    gap: 6px;
    max-width: 720px;
    margin: 0;
    padding: 10px 14px;
    font-size: var(--screen-font-sm);
    line-height: 1.7;
    color: var(--screen-warning);
    background: rgba(8, 22, 42, 0.86);
    border: 1px solid var(--screen-warning);
    border-radius: var(--screen-radius);
    pointer-events: none;
  }
}

// 窄屏（约 <900px）两个按钮会挤到一起，允许换行并收窄
@media (max-width: 900px) {
  .pick-stage__action {
    min-width: 132px;
    padding: 0 18px;
    font-size: var(--screen-font-md);
  }

  .pick-stage__hint {
    max-width: 92vw;
    white-space: normal;
  }
}
</style>
