/**
 * 大屏演示数据（Mock）
 * ---------------------------------------------------------------
 * 取值与 UI 设计稿保持一致（817 宗 / 426 宗 / 16.5% 等），用于组件与页面联调。
 * 接入真实接口时：把本文件替换为接口返回，并保持字段结构不变即可，
 * 页面与组件均不感知数据来源。
 */

/** 顶栏导航 */
export const headerMenus = [
  { key: 'home', label: '首页' },
  { key: 'map', label: '地图管理' },
  { key: 'data', label: '数据管理' },
  { key: 'analysis', label: '配套分析管理' },
  { key: 'archive', label: '档案管理' },
  { key: 'review', label: '提级论证管理' },
]

/** 系统标题 */
export const systemTitle = '天津市经营性用地市政基础设施配套动态监管工作站'

/** 当前用户 */
export const currentUser = {
  name: '普通用户',
  online: true,
}

/** 出让地块情况统计 */
export const transferStat = {
  total: 817,
  unit: '宗',
  split: [
    { label: '市级', value: 215, percent: 26, tone: 'green' },
    { label: '区级', value: 602, percent: 74, tone: 'cyan' },
  ],
  donut: [
    { name: '市级', value: 215 },
    { name: '区级', value: 602 },
  ],
  donutCenterLabel: '市级',
  footer: { total: 215, road: 388 },
}

/** 各区出让排行（宗） */
export const transferRank = [
  { name: '北辰区', value: 45 },
  { name: '河西区', value: 36 },
  { name: '东丽区', value: 21 },
  { name: '南开区', value: 21 },
  { name: '河东区', value: 21 },
  { name: '西青区', value: 19 },
  { name: '红桥区', value: 18 },
  { name: '津南区', value: 15 },
  { name: '河北区', value: 13 },
  { name: '和平区', value: 4 },
]

/** 需要落实配套 */
export const supportingStat = {
  total: 426,
  unit: '宗',
  split: [
    { label: '市级', value: 159, percent: 37, tone: 'green' },
    { label: '区级', value: 267, percent: 63, tone: 'cyan' },
  ],
  gauge: {
    percent: 16.5,
    label: '全部配套完成率',
  },
}

/** 各区配套落实（项） */
export const supportingRank = [
  { name: '河西区', value: 66 },
  { name: '北辰区', value: 61 },
  { name: '东丽区', value: 59 },
  { name: '南开区', value: 50 },
  { name: '河东区', value: 37 },
  { name: '河北区', value: 28 },
  { name: '红桥区', value: 20 },
  { name: '津南区', value: 12 },
]

/** 预警页签 */
export const warningTabs = [
  { key: 'city', label: '市级项目预警' },
  { key: 'district', label: '区级项目预警' },
  { key: 'plot', label: '地块预警信息' },
]

/** 预警表格列定义：type 支持 text / percent / number / action / index / tag / slot */
export const warningColumns = [
  { key: 'action', title: '操作', width: 86, type: 'action', align: 'center', actionText: '查看' },
  { key: 'district', title: '行政区划', width: 120, type: 'text' },
  { key: 'landAcquisition', title: '征地组卷未完成', width: 150, type: 'percent', tone: 'accent' },
  { key: 'feasibility', title: '可研批复未完成', width: 150, type: 'percent', tone: 'accent' },
  { key: 'preliminaryDesign', title: '初设及概算批复未完成', width: 180, type: 'percent', tone: 'accent' },
  { key: 'fund', title: '资金未落实', width: 130, type: 'percent', tone: 'warning' },
  { key: 'notStarted', title: '未开工', width: 120, type: 'percent', tone: 'warning' },
  { key: 'notCompleted', title: '未竣工', width: 120, type: 'percent', tone: 'warning' },
  { key: 'notHandedOver', title: '未移交', width: 120, type: 'percent', tone: 'warning' },
]

/** 预警表格数据 */
export const warningData = {
  city: [
    {
      id: 'city-1',
      district: '东丽区',
      landAcquisition: 7.14,
      feasibility: 6.12,
      preliminaryDesign: 8.16,
      fund: 43.88,
      notStarted: 11.22,
      notCompleted: 52.88,
      notHandedOver: 63.27,
    },
    {
      id: 'city-2',
      district: '北辰区',
      landAcquisition: 5.36,
      feasibility: 4.48,
      preliminaryDesign: 9.32,
      fund: 38.46,
      notStarted: 15.38,
      notCompleted: 46.15,
      notHandedOver: 57.69,
    },
    {
      id: 'city-3',
      district: '河西区',
      landAcquisition: 3.85,
      feasibility: 7.69,
      preliminaryDesign: 11.54,
      fund: 30.77,
      notStarted: 19.23,
      notCompleted: 42.31,
      notHandedOver: 50.0,
    },
    {
      id: 'city-4',
      district: '南开区',
      landAcquisition: 9.09,
      feasibility: 3.03,
      preliminaryDesign: 6.06,
      fund: 51.52,
      notStarted: 21.21,
      notCompleted: 57.58,
      notHandedOver: 66.67,
    },
  ],
  district: [
    {
      id: 'district-1',
      district: '武清区',
      landAcquisition: 12.5,
      feasibility: 8.33,
      preliminaryDesign: 4.17,
      fund: 45.83,
      notStarted: 20.83,
      notCompleted: 54.17,
      notHandedOver: 62.5,
    },
    {
      id: 'district-2',
      district: '宝坻区',
      landAcquisition: 6.67,
      feasibility: 13.33,
      preliminaryDesign: 10.0,
      fund: 36.67,
      notStarted: 16.67,
      notCompleted: 46.67,
      notHandedOver: 56.67,
    },
    {
      id: 'district-3',
      district: '静海区',
      landAcquisition: 10.53,
      feasibility: 5.26,
      preliminaryDesign: 15.79,
      fund: 42.11,
      notStarted: 26.32,
      notCompleted: 63.16,
      notHandedOver: 68.42,
    },
  ],
  plot: [
    {
      id: 'plot-1',
      district: '津南区 03-07 地块',
      landAcquisition: 0,
      feasibility: 0,
      preliminaryDesign: 25.0,
      fund: 75.0,
      notStarted: 50.0,
      notCompleted: 100.0,
      notHandedOver: 100.0,
    },
    {
      id: 'plot-2',
      district: '西青区 11-02 地块',
      landAcquisition: 33.33,
      feasibility: 0,
      preliminaryDesign: 0,
      fund: 66.67,
      notStarted: 33.33,
      notCompleted: 66.67,
      notHandedOver: 100.0,
    },
    {
      id: 'plot-3',
      district: '红桥区 05-11 地块',
      landAcquisition: 0,
      feasibility: 20.0,
      preliminaryDesign: 20.0,
      fund: 40.0,
      notStarted: 60.0,
      notCompleted: 80.0,
      notHandedOver: 80.0,
    },
  ],
}
