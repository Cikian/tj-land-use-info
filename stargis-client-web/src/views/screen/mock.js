/**
 * 首页兜底演示数据（高保真设计稿数字）
 * ------------------------------------------------------------------
 * 取值来源：UI 同事交付的高保真模型
 *          docs/高保真/市政-天津市经营性用地市政配套设施动态监管工作站/首页.html
 *
 * ⚠ 定位：**只在业务接口不可用或返回空数据时使用**。
 *   正常情况下首页数据由 index.vue 的 loadDashboard() 填充
 *   （LandDashboardVO / SupportingFacilitiesDashboardVO），接口成功后会整段覆盖这里的值。
 *   保留这份数据的意义是：没有部署业务后端时，界面仍然能按设计稿完整呈现，
 *   方便 UI 评审与像素比对。
 *
 * 顶栏菜单 / 系统标题 / 页签等静态常量在 ./config.js。
 */

/** 左面板：出让情况 / 落实配套情况 两个页签的兜底数据 */
export const demoLeftPanel = {
  tabs: [
    { key: 'transfer', label: '出让情况' },
    { key: 'support', label: '落实配套情况' },
  ],
  transfer: {
    cube: 'a',
    stat: { label: '已出让土地总数', value: 909, unit: '宗' },
    split: [
      { key: 'city', label: '市级', value: 245, unit: '宗', percent: 28, tone: 'green' },
      { key: 'district', label: '区级', value: 664, unit: '宗', percent: 72, tone: 'cyan' },
    ],
    columns: [
      { key: 'index', title: '序号' },
      { key: 'name', title: '行政区划' },
      { key: 'plots', title: '出让地块' },
      { key: 'roads', title: '涉及道路' },
    ],
    tables: {
      city: {
        rows: [
          { name: '河东区', plots: 26, roads: 49 },
          { name: '西青区', plots: 21, roads: 36 },
          { name: '河北区', plots: 17, roads: 29 },
          { name: '南开区', plots: 24, roads: 56 },
          { name: '东丽区', plots: 23, roads: 66 },
          { name: '河西区', plots: 38, roads: 79 },
          { name: '北辰区', plots: 51, roads: 81 },
          { name: '和平区', plots: 4, roads: 4 },
          { name: '津南区', plots: 20, roads: 13 },
          { name: '红桥区', plots: 21, roads: 20 },
        ],
        total: { name: '合计', plots: 245, roads: 433 },
      },
      district: {
        rows: [
          { name: '咸水沽镇', plots: 16, roads: 18 },
          { name: '陆路港物流装备产业园', plots: 7, roads: 15 },
          { name: '广达路', plots: 1, roads: 1 },
          { name: '蓟州区', plots: 1, roads: 1 },
          { name: '黄庄街', plots: 38, roads: 36 },
          { name: '军粮城街', plots: 1, roads: 0 },
          { name: '津南区', plots: 1, roads: 1 },
          { name: '宁河区', plots: 66, roads: 109 },
          { name: '中新天津生态城', plots: 18, roads: 16 },
          { name: '天津港', plots: 77, roads: 38 },
          { name: '滨海新区', plots: 3, roads: 1 },
        ],
        total: { name: '合计', plots: 229, roads: 236 },
      },
    },
  },
  support: {
    cube: 'b',
    stat: { label: '需要落实配套', value: 473, unit: '宗' },
    split: [
      { key: 'city', label: '市级', value: 160, unit: '宗', percent: 28, tone: 'green' },
      { key: 'district', label: '区级', value: 313, unit: '宗', percent: 72, tone: 'cyan' },
    ],
    columns: [
      { key: 'index', title: '序号' },
      { key: 'name', title: '行政区划' },
      { key: 'plots', title: '出让地块' },
      { key: 'roads', title: '涉及道路' },
    ],
    tables: {
      city: {
        rows: [
          { name: '河东区', plots: 48, roads: 15 },
          { name: '西青区', plots: 14, roads: 3 },
          { name: '河北区', plots: 28, roads: 0 },
          { name: '南开区', plots: 50, roads: 13 },
          { name: '东丽区', plots: 61, roads: 15 },
          { name: '河西区', plots: 72, roads: 23 },
          { name: '北辰区', plots: 64, roads: 18 },
          { name: '和平区', plots: 3, roads: 0 },
          { name: '津南区', plots: 13, roads: 1 },
          { name: '红桥区', plots: 20, roads: 7 },
        ],
        total: { name: '合计', plots: 373, roads: 95 },
      },
      district: {
        rows: [
          { name: '咸水沽镇', plots: 26, roads: 49 },
          { name: '陆路港物流装备产业园', plots: 21, roads: 36 },
          { name: '广达路', plots: 17, roads: 29 },
          { name: '蓟州区', plots: 24, roads: 56 },
          { name: '黄庄街', plots: 23, roads: 66 },
          { name: '军粮城街', plots: 38, roads: 79 },
          { name: '津南区', plots: 51, roads: 81 },
          { name: '宁河区', plots: 4, roads: 4 },
          { name: '中新天津生态城', plots: 20, roads: 13 },
          { name: '天津港', plots: 21, roads: 20 },
          { name: '滨海新区', plots: 12, roads: 12 },
        ],
        total: { name: '合计', plots: 245, roads: 433 },
      },
    },
  },
}

/** 左面板「排行」视图的兜底数据（高保真未包含该视图，沿用旧系统的口径） */
export const demoTransferRank = [
  { name: '北辰区', value: 51 },
  { name: '河西区', value: 38 },
  { name: '河东区', value: 26 },
  { name: '南开区', value: 24 },
  { name: '东丽区', value: 23 },
  { name: '西青区', value: 21 },
  { name: '红桥区', value: 21 },
  { name: '津南区', value: 20 },
  { name: '河北区', value: 17 },
  { name: '和平区', value: 4 },
]

export const demoSupportingRank = [
  { name: '河西区', value: 72 },
  { name: '北辰区', value: 64 },
  { name: '东丽区', value: 61 },
  { name: '南开区', value: 50 },
  { name: '河东区', value: 48 },
  { name: '河北区', value: 28 },
  { name: '红桥区', value: 20 },
  { name: '津南区', value: 13 },
]

/** 右面板：地图管理图层树（纯界面演示数据，接口尚未提供图层服务清单） */
export const demoLayerTree = [
  { id: 'terrain', label: '地形影像', level: 0, visible: false },
  { id: 'l1-1', label: '一级子级', level: 1, visible: true },
  { id: 'l2-1', label: '二级子级', level: 2, visible: false },
  { id: 'l2-2', label: '二级子级', level: 2, visible: true },
  { id: 'l2-3', label: '二级子级', level: 2, visible: true },
  { id: 'l2-4', label: '二级子级', level: 2, visible: false },
  { id: 'l1-2', label: '一级子级', level: 1, visible: true },
  { id: 'l2-5', label: '二级子级', level: 2, visible: false },
  { id: 'l2-6', label: '二级子级', level: 2, visible: true },
  { id: 'l2-7', label: '二级子级', level: 2, visible: true },
  { id: 'l2-8', label: '二级子级', level: 2, visible: false },
]

/** 底部属性表的兜底预警行（字段与后端 WarningItem 一致） */
export const demoWarningData = {
  city: [
    { id: 'c1', district: '河东区', landAcquisition: 19.72, feasibility: 19.72, preliminaryDesign: 19.72, fund: 19.72, notStarted: 19.72, notCompleted: 19.72, notHandedOver: 19.72 },
    { id: 'c2', district: '西青区', landAcquisition: 5.71, feasibility: 5.71, preliminaryDesign: 5.71, fund: 5.71, notStarted: 5.71, notCompleted: 5.71, notHandedOver: 5.71 },
    { id: 'c3', district: '河北区', landAcquisition: 12.5, feasibility: 12.5, preliminaryDesign: 12.5, fund: 12.5, notStarted: 12.5, notCompleted: 12.5, notHandedOver: 12.5 },
    { id: 'c4', district: '南开区', landAcquisition: 0, feasibility: 0, preliminaryDesign: 0, fund: 0, notStarted: 0, notCompleted: 0, notHandedOver: 0 },
  ],
  district: [
    { id: 'd1', district: '八里台镇', landAcquisition: 19.72, feasibility: 19.72, preliminaryDesign: 19.72, fund: 19.72, notStarted: 19.72, notCompleted: 19.72, notHandedOver: 19.72 },
    { id: 'd2', district: '军粮城街', landAcquisition: 5.71, feasibility: 5.71, preliminaryDesign: 5.71, fund: 5.71, notStarted: 5.71, notCompleted: 5.71, notHandedOver: 5.71 },
    { id: 'd3', district: '北辰区', landAcquisition: 12.5, feasibility: 12.5, preliminaryDesign: 12.5, fund: 12.5, notStarted: 12.5, notCompleted: 12.5, notHandedOver: 12.5 },
    { id: 'd4', district: '咸水沽镇', landAcquisition: 0, feasibility: 0, preliminaryDesign: 0, fund: 0, notStarted: 0, notCompleted: 0, notHandedOver: 0 },
  ],
  plot: [],
}

/** 地块预警信息弹窗（高保真为独立浮层，接口尚未提供该粒度的详情） */
export const demoPlotWarning = {
  title: '地块预警信息',
  total: 128,
  rows: [
    { landNo: '津东丽龙（挂）2025-19', plotName: '东丽区龙廷路西侧地块住宅项目', projectApproval: '100%', feasibility: '0%', preliminaryDesign: '0%', fund: '0%', notStarted: '0%', notCompleted: '0%', notHandedOver: '0%' },
    { landNo: '津东丽沙（挂）2025－10', plotName: '东丽区万新街沙柳北路A地块', projectApproval: '50%', feasibility: '0%', preliminaryDesign: '0%', fund: '0%', notStarted: '0%', notCompleted: '0%', notHandedOver: '0%' },
    { landNo: '津东丽丹（挂）2021-014', plotName: '东丽区李明庄公租房南侧（西侧）地块', projectApproval: '0%', feasibility: '0%', preliminaryDesign: '0%', fund: '0%', notStarted: '0%', notCompleted: '0%', notHandedOver: '0%' },
    { landNo: '津东丽龙（挂）2025-19', plotName: '东丽区龙廷路西侧地块住宅项目', projectApproval: '100%', feasibility: '0%', preliminaryDesign: '0%', fund: '0%', notStarted: '0%', notCompleted: '0%', notHandedOver: '0%' },
    { landNo: '津东丽沙（挂）2025－10', plotName: '东丽区万新街沙柳北路A地块', projectApproval: '50%', feasibility: '0%', preliminaryDesign: '0%', fund: '0%', notStarted: '0%', notCompleted: '0%', notHandedOver: '0%' },
    { landNo: '津东丽丹（挂）2021-014', plotName: '东丽区李明庄公租房南侧（西侧）地块', projectApproval: '0%', feasibility: '0%', preliminaryDesign: '0%', fund: '0%', notStarted: '0%', notCompleted: '0%', notHandedOver: '0%' },
    { landNo: '津东丽龙（挂）2025-19', plotName: '东丽区龙廷路西侧地块住宅项目', projectApproval: '100%', feasibility: '0%', preliminaryDesign: '0%', fund: '0%', notStarted: '0%', notCompleted: '0%', notHandedOver: '0%' },
    { landNo: '津东丽沙（挂）2025－10', plotName: '东丽区万新街沙柳北路A地块', projectApproval: '50%', feasibility: '0%', preliminaryDesign: '0%', fund: '0%', notStarted: '0%', notCompleted: '0%', notHandedOver: '0%' },
    { landNo: '津东丽丹（挂）2021-014', plotName: '东丽区李明庄公租房南侧（西侧）地块', projectApproval: '100%', feasibility: '0%', preliminaryDesign: '0%', fund: '0%', notStarted: '0%', notCompleted: '0%', notHandedOver: '0%' },
    { landNo: '津东丽龙（挂）2025-19', plotName: '东丽区龙廷路西侧地块住宅项目', projectApproval: '50%', feasibility: '0%', preliminaryDesign: '0%', fund: '0%', notStarted: '0%', notCompleted: '0%', notHandedOver: '0%' },
  ],
}
