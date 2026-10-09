import Vue from 'vue'
import axios from 'axios'
import {
  VueAxios
} from './axios'
import router from '@/router/index'
import {
  ACCESS_TOKEN,
  TENANT_ID
} from "@/store/mutation-types"
import {
  logoutAndGoToLogin,
  isHandlingSessionExpiry
} from '@/utils/session'

/**
 * 【指定 axios的 baseURL】
 * 如果手工指定 baseURL: '/jeecg-boot'
 * 则映射后端域名，通过 vue.config.js
 * @type {*|string}
 */
let apiBaseUrl = window._CONFIG['domianURL'] || "/jeecg-boot";
//////console.log("apiBaseUrl= ",apiBaseUrl)
// 创建 axios 实例
const service = axios.create({
  //baseURL: '/jeecg-boot',
  baseURL: apiBaseUrl, // api base_url
  timeout: 60000 // 请求超时时间
})

/**
 * 【stargis 改造】这次失败的请求是不是打到 Java 业务后端（jeecg）的？
 *
 * 业务请求统一走 src/api/manageJava.js，它显式传 baseURL = window._CONFIG.VUE_DATA_JAVA_URL；
 * 而本 axios 实例的默认 baseURL 是中台的 domianURL（VUE_APP_API_BASE_URL），
 * 两个后端地址不同，据此区分即可。
 *
 * @param {object} error axios 错误对象
 * @returns {boolean}
 */
function isJeecgRequest (error) {
  const jeecgBase = (window._CONFIG || {}).VUE_DATA_JAVA_URL
  if (!jeecgBase) {
    return false
  }
  const config = (error && error.config) || {}
  const base = config.baseURL || ''
  if (base && base.indexOf(jeecgBase) === 0) {
    return true
  }
  return (config.url || '').indexOf(jeecgBase) === 0
}

/**
 * 【stargis 改造】中台的令牌类业务码。
 * 来自平台框架 com.estar.platform.appserver.jwt.exception.message.CodeEnum：
 *   110000 非法token！ / 110001 token已经过期！ / 110002 Token 不能为空 /
 *   100001 需要重新登录！
 * 中台的 LoginInterceptor 抛 JKException，由 ExceptionHandle 统一写成
 * `{ code, message, date }`（注意字段名是 message，不是其它接口的 msg），
 * 且**不会改 HTTP 状态码**，所以业务码是判断中台登录态的主要依据。
 */
const TOKEN_ERROR_CODES = ['110000', '110001', '110002', '100001']

/** 文案特征（两个后端的措辞都覆盖上，兜住后端码值调整的情况） */
const TOKEN_ERROR_TEXT = /token失效|token已经过期|token已过期|token过期|非法token|token\s*不能为空|令牌失效|令牌无效|令牌已过期|登录已过期|登录失效|登录状态已失效|需要重新登录|请重新登录|重新登陆/i

/**
 * 【stargis 改造】响应体里是否携带「令牌不可用」特征。
 *
 * 注意 success 分支也要判：中台的鉴权失败是 HTTP 200 + 业务码，
 * 只在 err 里判断会漏掉最常见的那种过期。
 *
 * @param {object} data 响应体
 * @returns {boolean}
 */
function isTokenInvalidPayload (data) {
  if (!data || typeof data !== 'object') {
    return false
  }
  const code = data.code
  const codeText = code === undefined || code === null ? '' : String(code)
  if (TOKEN_ERROR_CODES.indexOf(codeText) > -1) {
    return true
  }
  // jeecg 的 Result：{ success:false, code:401, message:'Token失效，请重新登录' }
  if (codeText === '401') {
    return true
  }
  const message = String(data.message || data.msg || '')
  // ExceptionHandle 的兜底分支（未知异常，code=-1）；历史版本前端一直按「请重新登录」处理
  if (codeText === '-1' && message.indexOf('未知异常') > -1) {
    return true
  }
  return TOKEN_ERROR_TEXT.test(message)
}

/**
 * 【stargis 改造】这次失败的请求算不算鉴权失败。
 *
 * 中台：HTTP 401，或 200 + 令牌类业务码（见 isTokenInvalidPayload）。
 * jeecg：JwtFilter 任何校验不通过都是 HTTP 401 +
 *        { success:false, code:401, message:'Token失效，请重新登录' }。
 *
 * @param {object} error axios 错误对象
 * @param {boolean} isJeecg 是否打到 Java 业务后端
 * @returns {boolean}
 */
function isAuthFailure (error, isJeecg) {
  const response = (error && error.response) || {}
  if (response.status === 401) {
    return true
  }
  if (isTokenInvalidPayload(response.data)) {
    return true
  }
  // jeecg 偶尔会把鉴权失败包在业务码里返回 200
  const data = response.data || {}
  return isJeecg && typeof data.message === 'string' && data.message.indexOf('Token失效') > -1
}

/** 失效提示文案：按失效的是哪一套登录态区分，方便排查 */
function loginExpiredText (isJeecg) {
  return isJeecg ? '业务后端登录已过期，请重新登录' : '登录已过期，请重新登录'
}

/**
 * 主动注销类请求（中台 /app/outApi、jeecg /sys/logout）：
 * 令牌本来就可能已经失效，既不要提示，更不能递归触发一次退出。
 */
function isLogoutRequest (config) {
  const url = ((config || {}).url) || ''
  return url.indexOf('/sys/logout') > -1 || url.indexOf('/app/outApi') > -1
}

/**
 * 登录 / 换取令牌类请求：失败原因由登录页自己提示（密码错误、验证码无效…），
 * 绝不能被当成「登录态失效」而触发退出跳转，否则会打断正在登录的用户。
 */
function isLoginRequest (config) {
  const url = ((config || {}).url) || ''
  return /\/sys\/login|\/sys\/phoneLogin|\/sys\/thirdLogin|\/app\/oauth\/token|\/sso\/oauth\/check/.test(url)
}

const err = (error) => {
  // 已经进入「退出到登录页」流程：这段时间里并发的请求还会陆续失败，
  // 全部静默丢掉，避免退出瞬间刷出一屏红字
  if (isHandlingSessionExpiry()) {
    return Promise.reject(error)
  }

  const config = (error && error.config) || {}
  const isJeecg = isJeecgRequest(error)
  const isLogout = isLogoutRequest(config)
  const isLogin = isLoginRequest(config)

  // ==========================================================================
  // 【stargis 改造】鉴权失败 = 登录态不可用 → 清掉两套登录态并跳回登录页。
  //
  // 背景：中台令牌与 jeecg 令牌是两套独立会话，但**只要任意一套不可用**，
  // 用户在本系统里就已经什么都做不了：
  //   中台令牌过期 → 场景 / 图层 / 统计等 /app/** 接口全挂；
  //   jeecg 令牌过期 → 档案 / 收发文 / 提级论证等业务接口全挂。
  // 所以不再只弹一句「登录已过期」了事（用户既退不出去也干不了活），
  // 统一走 utils/session.js 的 logoutAndGoToLogin：清两套本地登录态 +
  // 尽力通知两个后端注销 + 跳登录页。
  // ==========================================================================
  if (!isLogout && !isLogin && isAuthFailure(error, isJeecg)) {
    logoutAndGoToLogin(loginExpiredText(isJeecg))
    return Promise.reject(error)
  }

  if (error.response) {
    let data = error.response.data
    switch (error.response.status) {
      case 403:
        Vue.prototype.$Jnotification.error({
          message: '系统提示',
          description: '拒绝访问',
          duration: 4
        })
        break
      case 500:
        // update-begin- --- author:liusq ------ date:20200910 ---- for:处理Blob情况----
        // 导出/下载类接口返回的是 blob，鉴权失败时错误体藏在文件流里，单独解析
        if (error.response.request && error.response.request.responseType === 'blob') {
          blobToJson(data, isJeecg)
        }
        // update-end- --- author:liusq ------ date:20200910 ---- for:处理Blob情况----
        // 其余 500 的「Token失效」已在上面统一按登录态失效处理
        break
      case 404:
        Vue.prototype.$Jnotification.error({
          message: '系统提示',
          description: '很抱歉，资源未找到!',
          duration: 4
        })
        break
      case 504:
        Vue.prototype.$Jnotification.error({
          message: '系统提示',
          description: '网络超时'
        })
        break
      case 401:
        // 非注销请求的 401 已在上面统一处理；走到这里的是主动注销（/app/outApi、
        // /sys/logout）本身返回 401——令牌早就没了，静默即可
        break
      default:
        Vue.prototype.$Jnotification.error({
          message: '系统提示',
          description: data.message,
          duration: 4
        })
        break
    }
  } else if (error.message) {
    if (error.message.includes('timeout')) {
      Vue.prototype.$Jnotification.error({
        message: '系统提示',
        description: '网络超时'
      })
    } else {
      Vue.prototype.$Jnotification.error({
        message: '系统提示',
        description: error.message
      })
    }
  }
  return Promise.reject(error)
};

// request interceptor
// service.interceptors.request.use(config => {
//   const token = Vue.ls.get(ACCESS_TOKEN)
//   ////console.log(token)
//   if (token) {
//     config.headers[ 'X-Access-Token' ] = token // 让每个请求携带自定义 token 请根据实际情况自行修改
//   }
//
// // update-begin--author:sunjianlei---date:20200723---for 如果当前在low-app环境，并且携带了appId，就向Header里传递appId
// const $route = router.currentRoute
// if ($route && $route.name && $route.name.startsWith('low-app') && $route.params.appId) {
//   config.headers['X-Low-App-ID'] = $route.params.appId
// }
// // update-end--author:sunjianlei---date:20200723---for 如果当前在low-app环境，并且携带了appId，就向Header里传递appId
//
// //update-begin-author:taoyan date:2020707 for:多租户
// let tenantid = Vue.ls.get(TENANT_ID)
// if (!tenantid) {
//   tenantid = 0;
// }
// // config.headers[ 'tenant-id' ] = tenantid
// //update-end-author:taoyan date:2020707 for:多租户
// if(config.method=='get'){
//   if(config.url.indexOf("sys/dict/getDictItems")<0){
//     config.params = {
//       _t: Date.parse(new Date())/1000,
//       ...config.params
//     }
//   }
// }
//   return config
// },(error) => {
//   return Promise.reject(error)
// })

/**
 * 【中台】探测当前 access_token 是否还有效，失效则用 refresh_token 换一个新的。
 *
 * 中台的令牌校验不走 HTTP 状态码，业务码非 200 就说明这次带上去的令牌不能用：
 *   1. 先拿 /app/sceneCreateApi/dataList 当探针（中台最轻量的登录态接口）；
 *   2. code != 200 就用 /app/oauth/refreshToken 刷新；
 *   3. 刷新也失败 → 中台登录态已不可用，退出到登录页。
 *
 * 【stargis 改造】这段逻辑原先在请求拦截器里按「FormData / 普通 body / params」
 * 复制了 4 份，这里合并成一处，避免改一处漏三处。
 * 探针/刷新请求本身失败时也不再闷着：HTTP 401 之类按登录态失效处理，
 * 其它错误（网络不通）照旧抛给调用方。
 */
async function ensureMediumTokenValid () {
  let res
  try {
    res = await axios.get(window._CONFIG.VUE_APP_API_BASE_URL + '/app/sceneCreateApi/dataList', {
      params: {
        access_token: Vue.ls.get(ACCESS_TOKEN),
        page: 1,
        rows: 10
      }
    })
  } catch (error) {
    if (isAuthFailure(error, false)) {
      logoutAndGoToLogin(loginExpiredText(false))
    }
    throw error
  }
  window.beginRefresh = false
  if (res.data.code == 200) {
    return
  }
  let res2
  try {
    res2 = await axios.get(window._CONFIG.VUE_APP_API_BASE_URL + '/app/oauth/refreshToken', {
      params: {
        refreshToken: Vue.ls.get('REFRESH_TOKEN')
      }
    })
  } catch (error) {
    // 刷新接口都打不通，中台侧已经没有可用的登录态了
    logoutAndGoToLogin(loginExpiredText(false))
    return
  }
  if (res2.data.code == 200) {
    Vue.ls.set(ACCESS_TOKEN, res2.data.result)
  } else {
    // 刷新失败（refresh_token 也过期 / 被踢），中台登录态彻底不可用
    logoutAndGoToLogin(loginExpiredText(false))
  }
}

service.interceptors.request.use(
  async (config) => {
      // if (window.secretkey) {
      //   config.headers['authorization'] = 'Bearer ' + window.secretkey;
      // }
      // 在发送请求之前做些什么，比如添加token等
      // ////console.log('请求拦截器', config);
      // ////console.log(config.url.indexOf(config.baseURL));
      // var base = config.url.substring(0, window._CONFIG.VUE_APP_API_BASE_URL.length - 1)
      // ////console.log(base);
      if ((config.url.indexOf(config.baseURL) > -1 || config.url.indexOf('http') < 0) && config.url.indexOf('/app/oauth/tokenapply') < 0 && config.url.indexOf('/app/oauth/refreshToken') < 0) {
        // ////console.log('请求拦截器', config);
        // ////console.log(config.data);
        if (config.data) {
          if (config.data instanceof FormData) {
            // ////console.log('是表单数据');
            for (let [key, value] of config.data.entries()) {
              ////console.log(key, value);
              if (key == 'access_token') {
                await ensureMediumTokenValid()
                // localStorage 里已经拿不到令牌（例如登出流程）时保持原值，
                // 不能覆盖成 undefined —— FormData.set(key, undefined) 会提交字符串 "undefined"
                if (Vue.ls.get(ACCESS_TOKEN)) {
                  config.data.set(key, Vue.ls.get(ACCESS_TOKEN));
                }
              }
            }
          } else {
            // ////console.log('不是表单数据');
            if (config.data.access_token) {
              await ensureMediumTokenValid()
              // 同上：登出时 config 上带的是调用前捕获的旧令牌，必须保留，
              // 否则 /app/outApi 会因为收到 undefined 而无法真正注销中台会话
              if (Vue.ls.get(ACCESS_TOKEN)) {
                config.data.access_token = Vue.ls.get(ACCESS_TOKEN)
              }
            }
          }
        }
        // ////console.log(config.params);
        if (config.params && config.params.access_token) {
          await ensureMediumTokenValid()
          if (Vue.ls.get(ACCESS_TOKEN)) {
            config.params.access_token = Vue.ls.get(ACCESS_TOKEN)
          }
        }
      } else {

      }
      // console.log(config);
      return config;
    },
    (error) => {
      // 处理请求错误
      return Promise.reject(error);
    }
)
/**
 * 【stargis 改造】响应的 Object 判断不能直接用来识别「登录过期」。
 *
 * 中台的鉴权失败是 LoginInterceptor 直接写出去的，Content-Type 被设成
 * text/html;charset=UTF-8，axios 不会把它当 JSON 解析，此时 response.data 是
 * 一段 JSON 文本（例如 '{"code":110001,"message":"token已经过期！","date":null}'）。
 * 这里先尝试解析回对象，解析不出来就原样返回。
 */
function parseBodyIfJsonString (data) {
  if (typeof data !== 'string') {
    return data
  }
  const text = data.trim()
  if (text.charAt(0) !== '{' && text.charAt(0) !== '[') {
    return data
  }
  try {
    return JSON.parse(text)
  } catch (e) {
    return data
  }
}

service.interceptors.response.use((response) => {
  const body = parseBodyIfJsonString(response.data)
  const config = response.config || {}
  const isJeecg = isJeecgRequest({ config })

  // ==========================================================================
  // 【stargis 改造】中台 / jeecg 任一登录态不可用 → 退出到登录页。
  //
  // 这一段是必须的：中台的令牌失效是 HTTP 200 + 业务码
  // （110000 非法token / 110001 token已经过期 / 110002 Token 不能为空 / 100001 需要重新登录），
  // 只判断 HTTP 状态码会整个漏掉。
  //
  // 旧实现是「response.data 不是对象就当作登录过期 → 注销 + 整页 reload」，
  // 而中台的失败体恰好是未解析的 JSON 字符串，于是**碰巧**能触发；
  // 但任何正常返回纯文本 / 空响应的接口也会被误判成登录过期。
  // 现在改成按响应体特征判断，只有真的鉴权失败才退出。
  // ==========================================================================
  if (!isHandlingSessionExpiry() && !isLogoutRequest(config) && !isLoginRequest(config) && isTokenInvalidPayload(body)) {
    logoutAndGoToLogin(loginExpiredText(isJeecg))
  }

  // //console.log(response);
  if (response && response.config && response.config.url.indexOf('metaJson') > -1) {
    if (response.data && response.data.result && response.data.result.Featurcclassid) {
      // //console.log(response.data.result.Featurcclassid);
      if (response.data.result.Featurcclassid && response.data.result.Featurcclassid.indexOf('stargis') > -1 && response.data.result.Featurcclassid.indexOf('Query') < 0) {
        if (response.data.result.Featurcclassid.endsWith('/')) {
          response.data.result.Featurcclassid = response.data.result.Featurcclassid + 'Query'
        } else {
          response.data.result.Featurcclassid = response.data.result.Featurcclassid + '/Query'
        }
      }

    }
  }
  if (response && response.config && response.config.url.indexOf('getSceneAndTree') > -1) {
    if (response.data && response.data.result && response.data.result.proxyUrlEnable && response.data.result.secretkey) {
      window.secretkey = response.data.result.secretkey
    }
    if (response.data && response.data.result && response.data.result.layer) {
      // //console.log(response.data.result.layer);
      changeFeaId(response.data.result.layer)
    }
  }
  // //console.log(response.data);
  return response.data
}, err)

function changeFeaId(layer) {
  for (let i = 0; i < layer.length; i++) {
    if (layer[i].featurcclassid && layer[i].featurcclassid.indexOf('stargis') > -1 && layer[i].featurcclassid.indexOf('Query') < 0) {
      if (layer[i].featurcclassid.endsWith('/')) {
        layer[i].featurcclassid = layer[i].featurcclassid + 'Query'
      } else {
        layer[i].featurcclassid = layer[i].featurcclassid + '/Query'
      }
    } else if (layer[i].featurcclassid && layer[i].featurcclassid.indexOf('stargis') > -1 && layer[i].featurcclassid.indexOf('Query') > 0) {
      layer[i].featurcclassid = layer[i].featurcclassid
    }
    if (layer[i].children) {
      changeFeaId(layer[i].children)
    }

  }
}
const installer = {
  vm: {},
  // install(Vue, router = {}) {
  //   Vue.use(VueAxios, router, service)
  // }
  install(Vue) {
    Vue.use(VueAxios, service)
  }
}
/**
 * Blob解析fadfda
 *
 * 导出 / 下载类接口带 responseType: 'blob'，鉴权失败时后端把 JSON 错误体
 * 当成文件流返回，必须读出来才能判断。命中「Token失效」同样按登录态失效
 * 退出到登录页（旧实现用的是这里根本没 import 的 Modal，一旦触发就是
 * ReferenceError：弹框弹不出来，人也退不出去）。
 *
 * @param {Blob} data 响应体
 * @param {boolean} isJeecg 是否打到 Java 业务后端
 */
function blobToJson(data, isJeecg) {
  let fileReader = new FileReader();
  fileReader.onload = function () {
    try {
      let jsonData = JSON.parse(this.result); // 说明是普通对象数据，后台转换失败
      ////console.log("jsonData", jsonData)
      if (jsonData.status === 500 && !isHandlingSessionExpiry()) {
        if (isTokenInvalidPayload(jsonData)) {
          logoutAndGoToLogin(loginExpiredText(isJeecg))
        }
      }
    } catch (err) {
      // 解析成对象失败，说明是正常的文件流
      ////console.log("blob解析fileReader返回err", err)
    }
  };
  fileReader.readAsText(data)
}

export {
  installer as VueAxios,
  service as axios
}