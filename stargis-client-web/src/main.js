/** init domain config */
import './config'

import Vue from 'vue'
import App from './App.vue'
import Storage from 'vue-ls'
import router from './router'
import store from './store/'
import { VueAxios } from '@/utils/request'
import '../src/assets/less/font.css'
require('@jeecg/antd-online-mini')
require('@jeecg/antd-online-mini/dist/OnlineForm.css')

import Antd, { version } from 'ant-design-vue'

console.log('ant-design-vue version:', version)

import Viser from 'viser-vue'
import 'ant-design-vue/dist/antd.less' // or 'ant-design-vue/dist/antd.less'

import '@/permission' // permission control
import '@/utils/filter' // base filter
import Print from 'vue-print-nb-jeecg'
/*import '@babel/polyfill'*/
import preview from 'vue-photo-preview'
import 'vue-photo-preview/dist/skin.css'
import SSO from '@/cas/sso.js'
import {
  ACCESS_TOKEN,
  JEECG_ACCESS_TOKEN,
  JEECG_USER_INFO,
  DEFAULT_COLOR,
  DEFAULT_THEME,
  DEFAULT_LAYOUT_MODE,
  DEFAULT_COLOR_WEAK,
  SIDEBAR_TYPE,
  DEFAULT_FIXED_HEADER,
  DEFAULT_FIXED_HEADER_HIDDEN,
  DEFAULT_FIXED_SIDEMENU,
  DEFAULT_CONTENT_WIDTH_TYPE,
  DEFAULT_MULTI_PAGE
} from '@/store/mutation-types'
import config from '@/defaultSettings'

import JDictSelectTag from './components/dict/index.js'
import hasPermission from '@/utils/hasPermission'
import vueBus from '@/utils/vueBus'
import JeecgComponents from '@/components/jeecg/index'
import '@/assets/less/JAreaLinkage.less'
// 【性能】地图/大屏公共样式由「每个组件各自 @import」改为「全局只引入一次」。
// 原先 common_btn.less(76KB) + common_pop.less(170KB) 被 12 个组件的 <style scoped> 各引一遍，
// 经 less-loader 编译后被 vue-style-loader 重复注入到 <head>，实测样式表 90 个、
// CSS 规则 1.78 万条，每次重排/重绘的样式匹配成本被成倍放大。
// 注意：这两个文件内的选择器都是全局类名（.spatial-pop、.query-dropdown 等），
// 本身不依赖 scoped 作用域，提升为全局引入后渲染效果一致。
import '@/assets/less/common_btn.less'
import '@/assets/less/common_pop.less'
// 列表页通用样式（ant 表格/弹窗/按钮微调）原先被 23 个列表组件各自 @import，
// 这里同样是全局选择器，改为全局引入一次即可，避免 23 份重复规则。
import '@/assets/less/common.less'
// 大屏基础组件设计令牌（青绿暗色主题，CSS 变量，全局引入一次）
import '@/components/screen/styles/screen-tokens.less'
// 大屏消息提示（命令式，替代浅色的 antd $message）：注册 this.$screenToast
import ScreenToast from '@/components/screen/toast'
import VueAreaLinkage from 'vue-area-linkage'
import '@/components/jeecg/JVxeTable/install'
import '@/components/JVxeCells/install'
//表单验证
import { rules } from '@/utils/rules'

Vue.prototype.rules = rules
Vue.config.productionTip = false
Vue.use(Storage, config.storageOptions)
Vue.use(Antd)
Vue.use(VueAxios, router)
Vue.use(Viser)
Vue.use(hasPermission)
Vue.use(JDictSelectTag)
Vue.use(Print)
Vue.use(preview)
Vue.use(vueBus)
Vue.use(JeecgComponents)
Vue.use(VueAreaLinkage)
Vue.use(ScreenToast)

//依据样式返回不同样式下的图片路径 zbs
Vue.prototype.GlobalImgFolderSelect = function(imgpath) {
  let ImgStylePath = 'style_' + window.SelectColorType + '/'

  return require('@/assets/' + ImgStylePath + imgpath)
}
//根据数据源类型值返回对应的数据库类型 zbs 20251016
Vue.prototype.getDataBaseType = function(DatasourceType) {
  let type = 'mysql'
  console.log('根据数据源类型值返回对应的数据库类型 DatasourceType', DatasourceType)
  switch (DatasourceType) {
    case '1':
      type = 'mysql'
      break;
    case '2':
      type = 'firebird'
      break;
    case '3':
      type = 'oracle'
      break;
    case '4':
      type = 'sqlserver'
      break;
    case '5':
      type = 'sqlite'
      break;
    case '15':
      type = 'shentong'
      break;
    case '16':
      type = 'kingbasees'
      break;
    case '17':
      type = 'dameng'
      break;
    case '18':
      type = 'postgres'
      break;
    default:
      type = 'mysql'
      break;
  }

  return type
}
//依据颜色样式选择 返回下拉框的颜色 具体样式在common_pop.less中定义
Vue.prototype.changedropdown = function() {
  if (window.SelectColorType == 'blue') {
    return 'query-dropdown2'
  } else {
    return 'query-dropdown'
  }
}

import VueContextMenu from 'vue-contextmenu'
Vue.use(VueContextMenu)
SSO.init(() => {
  main()
})

 import StargisFunction from "stargis-function";
 import "../node_modules/stargis-function/stargis-function.css";
// 必须紧跟在上面的 stargis-function.css 之后：用同名 @font-face 覆盖掉
// 包内 15.65MB 的 'sy' 字体声明，改为按需（永不）加载，详见文件内注释
import '@/assets/less/font-override.less'
 Vue.use(StargisFunction);

import localDataManager from '@/utils/localDataManager.js'
import setMapUrl from '@/utils/setMapUrl.js'
window.setMapUrl = setMapUrl
window.localDataManager = localDataManager
import {
  getAction,
  postAction,
  postActionNoAccess,
  jsonAction,
  downloadFile,
  postStringAction,
  postJson
} from './api/manage.js'
window.getAction = getAction
window.postAction = postAction
window.postActionNoAccess = postActionNoAccess
window.jsonAction = jsonAction
window.postJson = postJson
window.downloadFile = downloadFile
window.postStringAction = postStringAction
// import {
//   ACCESS_TOKEN
// } from "./store/mutation-types.js"
window.ACCESS_TOKEN = ACCESS_TOKEN
import { dealImage, base64ImgtoFile } from '@/utils/wyw/dealImage.js'
window.dealImage = dealImage
window.base64ImgtoFile = base64ImgtoFile
import { preventReClick } from '@/utils/preventReClick'
window.preventReClick = preventReClick

function main() {
  new Vue({
    router,
    store,
    mounted() {
      store.commit('SET_SIDEBAR_TYPE', Vue.ls.get(SIDEBAR_TYPE, true))
      store.commit('TOGGLE_THEME', Vue.ls.get(DEFAULT_THEME, config.navTheme))
      store.commit('TOGGLE_LAYOUT_MODE', Vue.ls.get(DEFAULT_LAYOUT_MODE, config.layout))
      store.commit('TOGGLE_FIXED_HEADER', Vue.ls.get(DEFAULT_FIXED_HEADER, config.fixedHeader))
      store.commit('TOGGLE_FIXED_SIDERBAR', Vue.ls.get(DEFAULT_FIXED_SIDEMENU, config.fixSiderbar))
      store.commit('TOGGLE_CONTENT_WIDTH', Vue.ls.get(DEFAULT_CONTENT_WIDTH_TYPE, config.contentWidth))
      store.commit('TOGGLE_FIXED_HEADER_HIDDEN', Vue.ls.get(DEFAULT_FIXED_HEADER_HIDDEN, config.autoHideHeader))
      store.commit('TOGGLE_WEAK', Vue.ls.get(DEFAULT_COLOR_WEAK, config.colorWeak))
      store.commit('TOGGLE_COLOR', Vue.ls.get(DEFAULT_COLOR, config.primaryColor))
      store.commit('SET_TOKEN', Vue.ls.get(ACCESS_TOKEN))
      // 【stargis 改造】刷新页面后把 Java 业务后端（jeecg）的登录态从 localStorage 恢复回 store，
      // 与上面的中台 SET_TOKEN 对应；令牌本身的读取以 localStorage 为准（见 manageJava.js）
      store.commit('SET_JEECG_TOKEN', Vue.ls.get(JEECG_ACCESS_TOKEN) || '')
      store.commit('SET_JEECG_INFO', Vue.ls.get(JEECG_USER_INFO) || {})
      store.commit('SET_MULTI_PAGE', Vue.ls.get(DEFAULT_MULTI_PAGE, config.multipage))
    },
    render: h => h(App)
  }).$mount('#app')
}
