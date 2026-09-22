/** 首页静态展示配置；业务数据由 Java 后端实时加载。 */

export const headerMenus = [
  { key: 'home', label: '首页' },
  { key: 'map', label: '地图管理' },
  { key: 'data', label: '数据管理' },
  { key: 'analysis', label: '配套分析管理' },
  { key: 'archive', label: '档案管理' },
  { key: 'review', label: '提级论证管理' },
]

export const systemTitle = '天津市经营性用地市政基础设施配套动态监管工作站'

export const warningTabs = [
  { key: 'city', label: '市级项目预警' },
  { key: 'district', label: '区级项目预警' },
  { key: 'plot', label: '地块预警信息' },
]

export const warningColumns = [
  { key: 'action', title: '操作', width: 86, type: 'action', align: 'center', actionText: '查看' },
  { key: 'district', title: '行政区划', width: 160, type: 'text' },
  { key: 'landAcquisition', title: '项建批复未完成', width: 150, type: 'percent', tone: 'accent' },
  { key: 'feasibility', title: '可研批复未完成', width: 150, type: 'percent', tone: 'accent' },
  { key: 'preliminaryDesign', title: '初设及概算批复未完成', width: 180, type: 'percent', tone: 'accent' },
  { key: 'fund', title: '资金未落实', width: 130, type: 'percent', tone: 'warning' },
  { key: 'notStarted', title: '未开工', width: 120, type: 'percent', tone: 'warning' },
  { key: 'notCompleted', title: '未竣工', width: 120, type: 'percent', tone: 'warning' },
  { key: 'notHandedOver', title: '未移交', width: 120, type: 'percent', tone: 'warning' },
]
