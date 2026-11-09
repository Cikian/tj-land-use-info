/**
 * =============================================================================
 *  管理端（admin-client）运行时配置
 *  部署位置：D:/phpstudy_pro/WWW/admin.web/static/config.js
 *           （覆盖 dist 里那份同名文件；改完刷新浏览器即生效，不用重新打包）
 * -----------------------------------------------------------------------------
 *  为什么要改这个文件：它是 dist 里唯一「改地址不用重打包」的入口。
 *  构建时 .env.production 写的是 http://localhost:8080/jeecg-boot，
 *  照原样部署的话，浏览器会绕过 nginx 直接去敲 8080，换个电脑就全挂。
 * =============================================================================
 */
window._CONFIG = {
  /**
   * 接口父路径。
   * 走 nginx 反代：页面上的请求变成 http://admin.web/api/xxx → 127.0.0.1:9802/api/xxx
   * ★ 必须带 origin（不能只写 '/api'）：右上角「消息通知」会用这个值拼 WebSocket 地址，
   *   而 new WebSocket('/api/websocket/1') 是非法 URL，会直接抛异常。
   */
  VUE_APP_API_BASE_URL: window.location.origin + '/api',

  /** 单点登录地址（本项目未启用，留空） */
  VUE_APP_CAS_BASE_URL: '',

  /**
   * kkFileView 在线预览。
   * 相对路径即可：由 nginx 把 /fileview/ 转给 127.0.0.1:8012。
   * 也可以在地址后直接写全：http://<服务器IP>:8012/onlinePreview
   */
  VUE_APP_ONLINE_BASE_URL: '/fileview/onlinePreview'
}
