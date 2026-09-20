/**
 * 存放配置常量
 */
window._CONFIG = {
  PROJECT_NAME: '星际GIS统一开发协同平台', // 系统名称
  UE4_URL: 'http://127.0.0.1',  // 当前版本不可用
  //接口父路径
  VUE_APP_API_BASE_URL: 'http://127.0.0.1:4548',

  TOKENTIME: 1000, //token时长(分钟)
  RETIME: 2000, //refresh时长(分钟)
  MOUSETIME: 3000, //鼠标超时时长(分钟)
  HEIGHT: 0,
  HIGOFFSET : 0,
  LEAFLET_OPEN: false,
  //单点登录地址
  VUE_APP_CAS_BASE_URL: '', // 文件预览路径
  VUE_APP_ONLINE_BASE_URL: '',

  VUE_MAP_URL: 'http://127.0.0.1:8083', // 配置了服务地址则读取该地址，没有配置则使用本身的地址
  VUE_DATA_SERVER_URL: 'http://127.0.0.1:8084', // 配置后台服务，和server是一起的，只是不是一个端口， 查询统计功能  http://192.168.110.110:9084  http://192.168.110.223:8084
  VUE_DATA_JAVA_URL: 'http://127.0.0.1:9802/api', // java后端

  // VUE_SENSORS_URL:'http://127.0.0.1:8844',    //局域网物联接口
  // VUE_SENSORS_URL:'http://127.0.0.1:8843/api',  //外网物联接口

  lisenceUrl: 'http://106.0.5.113:8083/stargis/rest/services/System/UtileServer/CheckLocker', //cesium权限检验

}