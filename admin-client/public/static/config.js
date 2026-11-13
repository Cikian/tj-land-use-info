/**
 * 存放配置常量(当值不为空时会覆盖env配置)
 */
window._CONFIG = {
  //接口父路径
  VUE_APP_API_BASE_URL: '',
  //MVT 瓦片对外服务基座：MVT 空间表服务页"复制地址"给地图客户端/中台用的前缀。
  //留空 = 跟随 VUE_APP_API_BASE_URL；部署后填外机可达地址（如 http://192.168.1.20:80/api），
  //保证无论操作者用什么地址打开管理端，复制的链接其它机器都能直接访问。
  //★ 当前开发机用 9802 直连形态：nginx 80 反代未启用，/api 形态（80 端口）外机连不上，
  //  之前复制的地址全部 Failed to fetch 就是这个原因。以后启用 nginx 反代时改成 http://服务器IP/api
  MVT_PUBLIC_BASE_URL: 'http://192.168.20.118:9802/api',
  //单点登录地址
  VUE_APP_CAS_BASE_URL: '',
  //文件预览路径（kkFileView）：运行时改这里不用重新打包。
  //例：http://192.168.1.20:8012/onlinePreview 或 /fileview/onlinePreview（nginx 反代）
  //留空则使用构建时 .env 的 VUE_APP_ONLINE_BASE_URL。
  VUE_APP_ONLINE_BASE_URL: ''
}

