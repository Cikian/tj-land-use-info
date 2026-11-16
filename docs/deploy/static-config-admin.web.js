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

  /**
   * MVT 瓦片对外服务基座（MVT 空间表服务页"复制地址"的前缀）。
   * ★ 必须填外机可达地址，不能跟 VUE_APP_API_BASE_URL 一样用 origin：
   *   复制出去的 TileJSON/XYZ 地址要贴进中台/地图客户端，
   *   得保证那台机器访问得到——用 origin 形态时，操作者若以
   *   http://admin.web（hosts 别名）打开页面，复制出的链接别的机器解析不了。
   * 例：http://192.168.1.20/api（走 nginx 同源反代）或 http://192.168.1.20:9802/api（直连）
   * 留空 = 跟随 VUE_APP_API_BASE_URL。
   */
  MVT_PUBLIC_BASE_URL: 'http://192.168.1.20/api',

  /** 单点登录地址（本项目未启用，留空） */
  VUE_APP_CAS_BASE_URL: '',

  /**
   * kkFileView 在线预览。
   * 相对路径即可：由 nginx 把 /fileview/ 转给 127.0.0.1:8012。
   * 也可以在地址后直接写全：http://<服务器IP>:8012/onlinePreview
   */
  VUE_APP_ONLINE_BASE_URL: '/fileview/onlinePreview'
}
