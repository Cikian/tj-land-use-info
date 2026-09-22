import Vue from 'vue'
import { USER_INFO, ENHANCE_PRE, JEECG_ACCESS_TOKEN, JEECG_USER_INFO } from "@/store/mutation-types"
const getters = {
  device: state => state.app.device,
  theme: state => state.app.theme,
  color: state => state.app.color,
  token: state => state.user.token,
  avatar: state => {state.user.avatar = Vue.ls.get(USER_INFO).avatar; return state.user.avatar},
  username: state => state.user.info.username,
  nickname: state => {state.user.info.realname = Vue.ls.get(USER_INFO).realname; return state.user.info.realname},
  welcome: state => state.user.welcome,
  permissionList: state => state.user.permissionList,
  userInfo: state => {state.user.info = Vue.ls.get(USER_INFO); return state.user.info},
  addRouters: state => state.permission.addRouters,
  onlAuthFields: state => {return state.online.authFields },
  enhanceJs:(state) => (code) => {
    state.enhance.enhanceJs[code] = Vue.ls.get(ENHANCE_PRE+code);
    return state.enhance.enhanceJs[code]
  },
  sysSafeMode: state => state.user.sysSafeMode,
  layer: state => state.user.layer,
  sysConfig: state => state.user.sysConfig,
  sysName: state => state.user.sysName,
  // 【stargis 改造】Java 业务后端（jeecg）登录态。
  // 页面刷新后 state 会丢，所以回落到 localStorage（登录时写入，登出清空）。
  jeecgToken: state => state.user.jeecgToken || Vue.ls.get(JEECG_ACCESS_TOKEN),
  jeecgUserInfo: state => state.user.jeecgInfo && Object.keys(state.user.jeecgInfo).length
    ? state.user.jeecgInfo : Vue.ls.get(JEECG_USER_INFO),
  jeecgDictItems: state => state.user.jeecgDictItems,
}

export default getters
