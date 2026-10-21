<template>
  <!--
    ScreenPickStage 「添加地块」绘制模式
    ==================================================================
    需求（2026-10 用户实测后改口径，同月由「选取」升级为「绘制」）：
    在**经营性用地 / 配套项目**的新增·编辑表单里点「添加地块」后，
      ① **不隐藏任何页面**（表单可拖动 + 背景不压暗，地图随时可操作）；
      ② 纯 GIS 操作（2026-10-21 用户明确要求去掉顶部按钮）：左键加点，
         **双击或右键结束**，结束即把 GeoJSON 抛回表单（表单**不关**，填到一半的内容都在）；
      ③ Esc = 取消（window 捕获监听，见 mounted）；
      ④ 本层只剩「一句提示 + 地图未就绪的兜底」，不再有任何可点按钮。

    ★ 历史口径：2026-10 上旬本层叫「选取地块」，当时语义是「去地图上选一个已有地块」，
      绘制引擎是空占位（getGeometry 恒 null）。后需求定为「在地球上画一个面、存 GeoJSON」，
      遂整层更名为「添加地块」，占位实现替换为 views/screen/drawPolygon.js 的真实绘制。

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
       本模式是「操作地图 + 画面」，拖动 / 缩放 / 绘制都必须落到**下面的地图**上。
       所以根节点是 `pointer-events: none`（原来顶部按钮所在处才显式开 auto，
       2026-10-21 按钮按需求移除后，整层没有任何吃事件的元素了）。
       ⚠ 这里**不能**写成根节点 `pointer-events: auto`：透明元素照样参与命中测试，
       整屏铺一个 auto 的 div 会把地图的鼠标事件全部吃掉，表现是
       「地图拖不动、滚轮不缩放」，而提示文案却还写着「地图可以自由拖动与缩放」。
       （这是本项目 README 第 3 条踩坑的同一个道理，只是方向相反：
       那边是「浮层要吃事件必须显式开」，这边是「整屏浮层必须显式关」。）

    4. **绘制引擎在 views/screen/drawPolygon.js，本层只管会话与回传**
       start() 里调 startDraw(window.viewer, ...)，引擎自己处理加点 / 橡皮筋 /
       双击 / 右键。引擎报 finish 事件时本层**立即** stop 并 $emit('done', geometry)
       —— 没有按钮，「双击或右键」就是回传的唯一出口（Esc 是取消）。
       ★ 双击的事件类型在本定制构建里叫 LEFT_DOUBLE_CLICK（老版名字），
         引擎里已做两个名字的兜底，见 drawPolygon.js 的 FINISH_DOUBLE_CLICK。

    用法（只在 views/screen/index.vue 里渲染一次，慎增第二处）：
      <screen-pick-stage
        v-if="pickActive"
        :map-ready="mapEnabled"
        @cancel="handlePickCancel"
        @done="handlePickDone"
      />
  -->
  <div class="pick-stage" role="region" aria-label="添加地块">
    <!-- 一句话说明：用户从表单跳进来，需要知道「现在在做什么、怎么结束、怎么回去」 -->
    <p class="pick-stage__hint">
      <screen-icon name="info" :size="14" />
      {{ hintText }}
    </p>

    <!-- 地图没起来时的兜底提示（?map=0 预览、或 Cesium 初始化失败） -->
    <p v-if="!mapReady" class="pick-stage__notice" role="status">
      <screen-icon name="alert-triangle" :size="14" />
      地图尚未加载，无法绘制地块。地图服务不可用时请用 ?map=0 预览界面，
      接入真实地图后这里会显示下方的三维地图。
    </p>
  </div>
</template>

<script>
import ScreenIcon from './ScreenIcon.vue'
import { toast } from './toast'
import { startDraw } from '@/views/screen/drawPolygon'
import { show as showDkPreview } from '@/views/screen/previewPolygon'

/** 各发起方的兜底提示文案（title 为空时用） */
const HINT_BY_SOURCE = {
  land: '正在添加经营性用地地块：左键加点，双击或右键结束，Esc 取消',
  facility: '正在添加配套项目地块：左键加点，双击或右键结束，Esc 取消'
}

/**
 * 地图绘制工具 —— 真实实现（引擎在 views/screen/drawPolygon.js）。
 *
 * 本对象只做三件事：
 *   · start()：拿全局 window.viewer 启动绘制（viewer 不存在 = Cesium 没起来，toast 兜底）；
 *   · stop()：停引擎、清工作实体（结束 / 取消 / 覆盖层卸载都要调，必须幂等）；
 *   · getGeometry()：取当前图形 —— 顶点 ≥3 就当场收口返回 { geojson, vertexCount }
 *     （不要求引擎已定格过），不足 3 个点返回 null。
 *
 * ★ 回传表单的唯一出口：引擎报 **finish（双击/右键结束）** 时，把成品面转交给
 *   previewPolygon.js 常驻预览层（画完的面**留在地图上**，跟着表单会话走，
 *   提交成功 / 取消关闭 / 重画 / 清除时才由表单清掉），然后停引擎、
 *   vm.$emit('done', geometry)。Esc 走 cancel（见 handleCancel / mounted 捕获监听）。
 *   ★ 绘制中不弹任何提示（2026-10-21 用户要求），引擎只在 finish 时回调。
 *   ★ 双击的事件类型在本定制构建里叫 LEFT_DOUBLE_CLICK（老版名字），
 *     引擎里已做两个名字的兜底，见 drawPolygon.js 的 FINISH_DOUBLE_CLICK。
 */
function createDrawTools (vm) {
  /** 先赋给局部变量再返回，onFinish 回调里才能拿到这个对象自身 */
  const tools = {
    /** 绘制是否已激活（start 成功后为 true） */
    active: false,

    /** 绘制句柄（startDraw 的返回值）；未启动时为 null */
    handle: null,

    /**
     * 激活绘制模式。
     * @returns {boolean} 是否真的开始绘制（地图没起来时为 false）
     */
    start () {
      if (this.active) return true
      const viewer = window.viewer
      if (!viewer || !window.Cesium) {
        vm.notifyDrawUnavailable()
        return false
      }
      this.handle = startDraw(viewer, {
        onFinish: () => {
          // 双击 / 右键结束 = 完成。① 成品面转交常驻预览层（不随引擎销毁）；
          // ② 停引擎回收工作实体；③ 图形抛回表单。引擎只在 ≥3 点时报 finish，
          // geometry 必非空。
          const geometry = tools.getGeometry()
          if (geometry && geometry.geojson) showDkPreview(geometry.geojson)
          tools.stop()
          vm.$emit('done', geometry)
        }
      })
      this.active = true
      return true
    },

    /** 结束绘制（完成 / 取消 / 组件卸载都会调用，必须幂等） */
    stop () {
      if (!this.active) return
      this.active = false
      if (this.handle) {
        this.handle.stop()
        this.handle = null
      }
    },

    /**
     * 取当前图形。
     * @returns {({geojson: string, vertexCount: number}|null)}
     *   null = 没有画出一个有效多边形（表单收到 null 会保留字段原值）
     */
    getGeometry () {
      return this.handle ? this.handle.getGeoJSON() : null
    }
  }
  return tools
}

export default {
  name: 'ScreenPickStage',
  components: { ScreenIcon },
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
      /** 绘制工具（真实实现，见 createDrawTools 的说明） */
      drawTools: null,
      /** Esc 的 keydown 处理器引用，便于销毁时精确移除 */
      escHandler: null
    }
  },
  computed: {
    hintText () {
      if (this.title) return this.title
      return HINT_BY_SOURCE[this.source] || '正在添加地块：左键加点；双击 / 右键或点「完成绘制」结束'
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
     * Esc = 取消绘制（不带走任何图形）。
     * 为什么用 window 监听而不是给本层加 @keydown：本层现在没有任何可聚焦元素
     * （按钮已按需求移除），焦点本来就常在地图或表单弹窗里，
     * 键盘用户按 Esc 会「没反应」。
     * ★★ 必须用**捕获阶段**（第三个参数 true）：下面那张表单弹窗（ScreenModal）的
     *    Escape 处理挂在它自己的 dialog 元素上。进入绘制模式后弹窗**仍然可见**
     *    （2026-10 起不再隐藏），焦点很可能就在弹窗里某个输入框上，
     *    事件从弹窗冒泡到 window，**弹窗的 @keydown 会先跑** ——
     *    结果就是「按 Esc 想取消绘制，却把整张填了一半的表单一起关掉」。
     *    在捕获阶段先拦下来，可以保证 Esc 只作用于绘制模式。
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
     * 绘制引擎立即激活（进本层就是为了画面，不用等用户再点什么）。
     * 放在 mounted 而不是 created：要等 window.viewer 可用性判定，
     * 且引擎要往 Cesium canvas 上挂监听，DOM 必须就绪。
     * 地图没起来时 start() 只会 toast 兜底，本层 UI 照常显示（见 !mapReady 提示）。
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
    /** Esc 取消：不带走任何图形，只把控制权还给表单（window 捕获监听触发） */
    handleCancel () {
      if (this.drawTools) this.drawTools.stop()
      this.$emit('cancel')
    },

    /**
     * 地图不可用时的统一提示（window.viewer 不存在 = Cesium 没起来）。
     */
    notifyDrawUnavailable () {
      toast.warning('地图尚未加载，无法添加地块；请确认地图服务可用后重试')
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
  // ★★ 整屏 pointer-events: none —— 鼠标事件必须穿到下面的地图上（拖动 / 缩放 / 绘制）。
  //    透明元素同样参与命中测试，这里写成 auto 会把地图彻底挡死。
  //    （原来只有顶部按钮显式开 auto，按钮移除后整层没有任何吃事件的元素。）
  pointer-events: none;
  // 显式声明可见：本层挂在 body 上，不依赖任何父级；将来若有人给 body 加
  // 「隐藏浮层」的全局规则（2026-10 之前确实有过 body.is-picking-map），这一行能保住本层。
  visibility: visible;
  overflow: hidden;

  /*
   * 本层不再有按钮（2026-10-21 需求：纯 GIS 操作，双击/右键结束），
   * 因此没有 __scrim / __actions —— 按钮那套样式连同「按钮柔光」一起回收。
   * 根节点保持 pointer-events: none + 无背景，下面就是真实地图（见头注释 2、3）。
   * ★ 本层挂在 body 上、不在缩放画布内（见 mounted），这里的 px 是真实像素。
   */

  /* ---------------- 一句话说明（怎么加点、怎么结束、怎么取消） ---------------- */
  &__hint {
    position: absolute;
    left: 50%;
    // 顶栏在设计画布里高 128px（本层是未缩放的真实像素层，顶栏真实下沿 ≤128px），
    // 取 150px 与原按钮区同一条安全线，不压导航又足够显眼
    top: 150px;
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

// 窄屏（约 <900px）提示条太长，允许换行
@media (max-width: 900px) {
  .pick-stage__hint {
    max-width: 92vw;
    white-space: normal;
  }
}
</style>
