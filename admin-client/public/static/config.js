/**
 * 存放配置常量(当值不为空时会覆盖env配置)
 */
window._CONFIG = {
  //接口父路径
  VUE_APP_API_BASE_URL: '',
  //单点登录地址
  VUE_APP_CAS_BASE_URL: '',
  //文件预览路径（kkFileView）：运行时改这里不用重新打包。
  //例：http://192.168.1.20:8012/onlinePreview 或 /fileview/onlinePreview（nginx 反代）
  //留空则使用构建时 .env 的 VUE_APP_ONLINE_BASE_URL。
  VUE_APP_ONLINE_BASE_URL: ''
}

