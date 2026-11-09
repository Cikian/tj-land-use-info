/**
 * =============================================================================
 *  三维大屏端（stargis-client-web）运行时配置
 *  部署位置：D:/phpstudy_pro/WWW/gis.web/static/config.js
 *           （覆盖 dist 里那份同名文件；改完刷新浏览器即生效，不用重新打包）
 * -----------------------------------------------------------------------------
 *  大屏有「四个后端」，原来写死的都是 http://127.0.0.1:xxxx —— 浏览器在别的电脑上
 *  打开时 127.0.0.1 指的是客户端自己，所以必须改成同源的 nginx 路径，
 *  由 nginx 转发到本机对应端口（见 nginx-gis.web.conf）：
 *
 *      中台 ZK-SERVER   4548   →  /zk/
 *      Java 业务后端    9802   →  /api/        （context-path 就是 /api）
 *      JNI 分析服务     38190  →  /analysis/
 *      地图/数据服务     38083/38084 → /mapsrv/、/datasrv/
 *
 *  ★ 用 window.location.origin 而不是写死 http://gis.web：
 *    换域名、换 IP、加端口都不用再改这个文件。
 * =============================================================================
 */
window._CONFIG = {
  PROJECT_NAME: '天津市经营性用地市政配套设施动态监管工作站', // 系统名称
  UE4_URL: 'http://127.0.0.1', // 当前版本不可用

  // 中台（网关）：登录、场景/图层、功能权限都走它 → nginx /zk/ → 127.0.0.1:4548/
  VUE_APP_API_BASE_URL: window.location.origin + '/zk',

  TOKENTIME: 1000, //token时长(分钟)
  RETIME: 2000, //refresh时长(分钟)
  MOUSETIME: 3000, //鼠标超时时长(分钟)
  HEIGHT: 0,
  HIGOFFSET: 0,
  LEAFLET_OPEN: false,

  // 单点登录地址（未启用）
  VUE_APP_CAS_BASE_URL: '',

  // 文件预览（kkFileView）：nginx /fileview/ → 127.0.0.1:8012/
  VUE_APP_ONLINE_BASE_URL: '/fileview/onlinePreview',

  // 地图服务（38083）→ nginx /mapsrv/；该服务不在本机时改回 http://<IP>:38083
  VUE_MAP_URL: window.location.origin + '/mapsrv',

  // 数据服务（38084）→ nginx /datasrv/；该服务不在本机时改回 http://<IP>:38084
  VUE_DATA_SERVER_URL: window.location.origin + '/datasrv',

  // Java 业务后端（jeecg，9802 + /api）：/land/**、/sys/**、档案/附件等业务接口
  VUE_BUSINESS_JAVA_URL: window.location.origin + '/api',

  // JNI 分析服务（38190）：断面/爆管/覆土等插件与出图 → nginx /analysis/
  VUE_DATA_JAVA_URL: window.location.origin + '/analysis',

  lisenceUrl: 'http://106.0.5.113:8083/stargis/rest/services/System/UtileServer/CheckLocker' //cesium权限检验
}
