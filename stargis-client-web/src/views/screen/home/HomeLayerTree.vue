<template>
  <!--
    HomeLayerTree 首页右面板「地图管理」图层树
    ==================================================================
    与地图管理页（../map/index.vue）的「图层管理」同款：stargis-function
    的 LayerTreePop 弹层 + LayerTree 隐藏引擎，图层树数据 / 服务懒加载 /
    显隐全部由插件内部完成（请求 /app/sceneCreateApi/getSceneAndTree），
    本组件只负责宿主定位与拉起时序。
    定尺取自高保真模型 首页.html：
      标题装饰 大标题-右_u24.png   (1491, 95)   420×46
      标题文字 地图管理（右对齐）    (1763, 104)  18px #FFFFFF
      面板底   panel-right-bg.png  (1490, 149)  400×881
  -->
  <div class="home-tree">
    <img class="home-tree__title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
    <h2 class="home-tree__title">地图管理</h2>

    <!-- 插件弹层宿主：铺满右面板框（同 map 页 __plugin-host 定位与底图） -->
    <div class="home-tree__panel stage-hit">
      <layer-tree-pop ref="layerTreePop" :config-min="pluginConfig" />
    </div>

    <!--
      隐藏引擎（同 map 页 engineTools 挂法）：按钮本体不用，只借用
      btnClick() 的图层树激活逻辑。顺序契约同 map 页 togglePluginTool：
      弹层先挂载（created 里注册 $once 总线监听），引擎再 btnClick()。
    -->
    <layer-tree
      ref="layerTreeEngine"
      v-show="false"
      :config-min="pluginConfig"
      aria-hidden="true"
    />
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'

export default {
  name: 'HomeLayerTree',
  data () {
    return {
      hf,
      // 同 map 页注册表 layers 工具传给插件的 config-min
      pluginConfig: { icon: 'map-btns/QueryPick' },
    }
  },
  mounted () {
    this.openWhenMapReady()
  },
  beforeDestroy () {
    this.stopReadyPolling()
  },
  methods: {
    /**
     * 插件引擎激活契约（同 map/index.vue）：btnClick() 激活前会调
     * $parent.cancelAll() 复位兄弟工具。首页只有图层树一个插件工具，
     * 无需实际复位，补齐契约避免引擎报 cancelAll is not a function。
     */
    cancelAll () {},

    /**
     * S3dmViewer 异步建 viewer，进首页时地图可能尚未就绪：轮询
     * window.viewer（仿 map 页 startLayerProbeWhenReady，500ms × 20 次）
     * 后再拉起图层树。?map=0 或超时则不等地图，仍展示面板——树数据来自
     * 中台，只是开关图层无效。
     */
    openWhenMapReady () {
      let tries = 0
      this._readyTimer = setInterval(() => {
        if (window.viewer) {
          this.stopReadyPolling()
          this.activateEngine()
        } else if (++tries > 20) {
          this.stopReadyPolling()
          this.ensurePopVisible()
        }
      }, 500)
    },

    stopReadyPolling () {
      if (this._readyTimer) {
        clearInterval(this._readyTimer)
        this._readyTimer = null
      }
    },

    /** 仿 map 页 togglePluginTool 弹窗分支：引擎激活，弹层保证可见 */
    activateEngine () {
      this.$nextTick(() => {
        const engine = this.$refs.layerTreeEngine
        try {
          if (engine) engine.btnClick()
        } catch (error) {
          this.$screenToast.info('地图未就绪，图层管理暂不可用')
        }
        this.ensurePopVisible()
      })
    },

    /**
     * LayerTree 引擎不发总线开事件（注册表 event: null），弹层可见性
     * 由页面保证（同 map 页 ensurePopVisible）。
     */
    ensurePopVisible () {
      const popVm = this.$refs.layerTreePop
      if (popVm && popVm.isShow === false && typeof popVm.showPop === 'function') {
        popVm.showPop()
      }
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.home-tree {
  &__title-deco {
    position: absolute;
    // 设计稿 x=1491..1911（420 宽），即**距右边界 9px**；
    // 之前误写成 right:429px（那是左边缘到右边界的距离），导致整条装饰条跑到中间
    right: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    // 设计稿标题文字右缘止于 x=1835，即距右边界 85px
    right: 85px;
    top: 102px;
    width: 86px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  &__panel {
    position: absolute;
    right: 30px;
    // 上贴 149、下贴 50：正文区随设计画布高度伸展（同左面板 / map 页插件宿主）
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;

    // 各插件弹窗根类名不一（*.pop 结尾或自定义），统一按「根铺满面板 +
    // 内层 .pop 卡片铺满 + 旧标题栏零高 + 关闭钮右上角」处理。
    // ★ 与 map/index.vue __plugin-host 的覆盖段逐条一致：插件原始样式是
    //   300px 小浮窗，不覆盖就撑不满右面板（首页首版踩过的坑）。
    /deep/ [class*='-pop'],
    /deep/ .placename {
      position: absolute !important;
      top: 0 !important;
      left: 0 !important;
      right: 0 !important;
      bottom: 0 !important;
      width: 100% !important;
      height: 100% !important;
      max-height: none !important;
      overflow-y: auto !important;
      overflow-x: hidden !important;
      background: transparent !important;
      box-shadow: none !important;
      border: none !important;

      .pop {
        position: relative !important;
        left: 0 !important;
        top: 0 !important;
        width: 100% !important;
        min-height: 100% !important;
        background: transparent !important;
        box-shadow: none !important;
        border: none !important;
      }

      .pop-header {
        height: 0 !important;
        overflow: visible;
        background: transparent !important;
      }

      .pop-title {
        display: none !important;
      }

      .pop-close {
        position: absolute !important;
        top: 6px !important;
        right: 6px !important;
        z-index: 5;
      }

      .pop-body {
        width: 100% !important;
        padding: 6px 10px !important;
      }

      // 插件自带的浅灰条件区压成暗色
      .all-condition {
        background: rgba(0, 0, 0, 0.2) !important;
        border: 1px solid #2c6b8a !important;
        color: #ffffff;
      }

      // 表单控件压成暗色主题
      .ant-input,
      textarea.ant-input,
      .ant-select-selection {
        background-color: rgba(0, 0, 0, 0.15) !important;
        color: #ffffff !important;
        border-color: #2c6b8a !important;
      }

      .ant-select-arrow,
      .ant-input::placeholder {
        color: #4a7396 !important;
      }

      .layer-info-title,
      .query-type-title {
        white-space: nowrap;
      }

      // 插件弹窗内文字默认亮色（老主题文字色不定，深底上会不可见）
      color: #cfe8ff;

      .ant-tree,
      .ant-tree .ant-tree-node-content-wrapper {
        color: #82c6ff;
      }

      .ant-list {
        color: #cfe8ff;
      }
    }
  }
}
</style>
