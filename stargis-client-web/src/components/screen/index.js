/**
 * 大屏基础组件库（Screen UI）
 * ---------------------------------------------------------------
 * 设计来源：UI 设计稿《天津市经营性用地市政基础设施配套动态监管工作站》
 * 主题：青绿暗色（Token 见 ./styles/screen-tokens.less，已在 main.js 全局引入）
 *
 * 使用方式一（局部注册，推荐，按需引入）：
 *   import { ScreenPanel, ScreenDonut } from '@/components/screen'
 *   export default { components: { ScreenPanel, ScreenDonut } }
 *
 * 使用方式二（全局注册）：
 *   import ScreenUI from '@/components/screen'
 *   Vue.use(ScreenUI)
 *
 * 消息提示（命令式，样式与大屏一致，替代 antd 浅色 $message）：
 *   import ScreenToast from '@/components/screen/toast'
 *   Vue.use(ScreenToast)                 // 注册 this.$screenToast
 *   this.$screenToast.success('保存成功')
 *
 * 组件清单
 * ------------------------------------------------------------------
 * 容器 / 布局
 *   ScreenPanel      面板容器（标题栏 / 内容插槽 / 底栏 / 折叠 / 四角装饰）
 *   ScreenHeader     大屏顶栏（标题 / 导航 / 实时时钟 / 用户 / 在线状态）
 *   ScreenMapStage   地图舞台（地图插槽 + 兜底底图 + 取景框 + 暗角）
 *   ScreenModal      弹窗（挂 body + 焦点陷阱 + 滚动锁）
 *   ScreenPopover    浮层容器（挂 body，供下拉/气泡复用，解决面板裁剪问题）
 *   ScreenPopconfirm 危险操作二次确认
 *
 * 展示 / 数据可视化
 *   ScreenStatValue  大号统计数值（含数字滚动）
 *   ScreenCountUp    数字滚动原子组件
 *   ScreenSplitStat  分级占比统计条（市级 / 区级）
 *   ScreenDonut      环形占比图
 *   ScreenGauge      仪表盘进度
 *   ScreenRankList   排行条形列表（单行紧凑式）
 *   ScreenBarList    标签条形列表（两行堆叠式，适合分类统计）
 *   ScreenLineChart  趋势折线 / 面积图
 *   ScreenDataTable  数据表格（文本 / 百分比条 / 标签 / 链接 / 多操作 / 行选择）
 *   ScreenDescriptions 标签-值描述栅格
 *   ScreenTabs       标签页（胶囊 / 下划线）
 *   ScreenTag        状态标签
 *   ScreenEmpty      空状态
 *   ScreenLoading    加载态（含遮罩）
 *
 * 表单控件
 *   ScreenField      表单字段容器（标签 + 控件 + 提示/错误）
 *   ScreenInput      输入框（单行 / 多行 / 数字）
 *   ScreenSelect     下拉选择（可搜索 / 可远程搜索 / 可清除）
 *   ScreenTreeSelect 树形下拉（分类选择）
 *   ScreenDateInput  日期选择（单日 / 区间，原生选择器 + 暗色适配）
 *   ScreenRadioGroup 分段单选
 *   ScreenPagination 分页器
 *   ScreenTree       树（展开 / 选中 / 拖拽排序与改挂）
 *   ScreenUpload     文件上传（XHR + 进度 + 扩展名/大小校验）
 *
 * 原子
 *   ScreenIcon       线性图标集（禁止用 emoji 当结构图标）
 *   ScreenToast      消息提示容器（一般通过 toast.js 命令式调用）
 */

import ScreenPanel from './ScreenPanel.vue'
import ScreenHeader from './ScreenHeader.vue'
import ScreenMapStage from './ScreenMapStage.vue'
import ScreenModal from './ScreenModal.vue'
import ScreenPopover from './ScreenPopover.vue'
import ScreenPopconfirm from './ScreenPopconfirm.vue'

import ScreenStatValue from './ScreenStatValue.vue'
import ScreenCountUp from './ScreenCountUp.vue'
import ScreenSplitStat from './ScreenSplitStat.vue'
import ScreenDonut from './ScreenDonut.vue'
import ScreenGauge from './ScreenGauge.vue'
import ScreenRankList from './ScreenRankList.vue'
import ScreenBarList from './ScreenBarList.vue'
import ScreenLineChart from './ScreenLineChart.vue'
import ScreenDataTable from './ScreenDataTable.vue'
import ScreenDescriptions from './ScreenDescriptions.vue'
import ScreenTabs from './ScreenTabs.vue'
import ScreenTag from './ScreenTag.vue'
import ScreenEmpty from './ScreenEmpty.vue'
import ScreenLoading from './ScreenLoading.vue'

import ScreenField from './ScreenField.vue'
import ScreenInput from './ScreenInput.vue'
import ScreenSelect from './ScreenSelect.vue'
import ScreenTreeSelect from './ScreenTreeSelect.vue'
import ScreenDateInput from './ScreenDateInput.vue'
import ScreenRadioGroup from './ScreenRadioGroup.vue'
import ScreenButton from './ScreenButton.vue'
import ScreenPagination from './ScreenPagination.vue'
import ScreenTree from './ScreenTree.vue'
import ScreenUpload from './ScreenUpload.vue'

import ScreenIcon from './ScreenIcon.vue'
import ScreenToast from './ScreenToast.vue'

const components = {
  // 容器 / 布局
  ScreenPanel,
  ScreenHeader,
  ScreenMapStage,
  ScreenModal,
  ScreenPopover,
  ScreenPopconfirm,

  // 展示 / 数据可视化
  ScreenStatValue,
  ScreenCountUp,
  ScreenSplitStat,
  ScreenDonut,
  ScreenGauge,
  ScreenRankList,
  ScreenBarList,
  ScreenLineChart,
  ScreenDataTable,
  ScreenDescriptions,
  ScreenTabs,
  ScreenTag,
  ScreenEmpty,
  ScreenLoading,

  // 表单控件
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenTreeSelect,
  ScreenDateInput,
  ScreenRadioGroup,
  ScreenButton,
  ScreenPagination,
  ScreenTree,
  ScreenUpload,

  // 原子
  ScreenIcon,
  ScreenToast,
}

/** Vue 插件：全局注册全部大屏组件 */
const ScreenUI = {
  install (Vue) {
    Object.keys(components).forEach((name) => {
      Vue.component(name, components[name])
    })
  },
}

export {
  ScreenPanel,
  ScreenHeader,
  ScreenMapStage,
  ScreenModal,
  ScreenPopover,
  ScreenPopconfirm,
  ScreenStatValue,
  ScreenCountUp,
  ScreenSplitStat,
  ScreenDonut,
  ScreenGauge,
  ScreenRankList,
  ScreenBarList,
  ScreenLineChart,
  ScreenDataTable,
  ScreenDescriptions,
  ScreenTabs,
  ScreenTag,
  ScreenEmpty,
  ScreenLoading,
  ScreenField,
  ScreenInput,
  ScreenSelect,
  ScreenTreeSelect,
  ScreenDateInput,
  ScreenRadioGroup,
  ScreenButton,
  ScreenPagination,
  ScreenTree,
  ScreenUpload,
  ScreenIcon,
  ScreenToast,
}

export default ScreenUI
