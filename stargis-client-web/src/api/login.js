import api from './index'
import {
  axios
} from '@/utils/request'
import { javaBaseUrl } from '@/api/manageJava'
import signMd5Utils from '@/utils/encryption/signMd5Utils'

/**
 * 【stargis 改造】Java 业务后端（jeecg）登录用的固定验证码。
 *
 * jeecg 的 /sys/login 默认要求图形验证码（走 Redis 校验），
 * 但后端 LoginController 预留了固定值旁路：
 *
 *   LoginController.java:99
 *   if (!"cikian".equals(lowerCaseCaptcha)) { ...走 Redis 校验... }
 *
 * 即 captcha 传 "cikian"（大小写不敏感，后端会 toLowerCase）时跳过验证码校验；
 * captcha 仍然必须非空，否则后端直接返回“验证码无效”。
 * 这样本系统就不需要在登录页再画一个 jeecg 的验证码。
 */
export const JEECG_LOGIN_CAPTCHA = 'cikian'

/**
 * login func
 * parameter: {
 *     username: '',
 *     password: '',
 *     remember_me: true,
 *     captcha: '12345'
 * }
 * @param parameter
 * @returns {*}
 */
export function loginUser(parameter) {
  return axios({
    url: '/app/oauth/token',
    method: 'get',
    params: parameter
  })
}

export function login(parameter) {
  // // let url = '/app/oauth/token';
  // let url = '/app/encrypt/tokenapply';
  // if (parameter.access_token) {
  //   url = '/app/oauth/tokensm4'
  // }

  // //新一代中台
  // let url = '/app/oauth/tokenapply';
  // if (parameter.access_token) {
  //   url = '/app/oauth/tokensm4'
  // }
  // // parameter.balalala = 'balalala' //测试
  // // console.log(parameter);
  // return axios({
  //   url: url,
  //   method: 'get',
  //   params: parameter
  // })
  //新验证
  let url = '/app/oauth/tokenapply';
  return axios({
    url: url,
    method: 'post',
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    data: parameter
  })
}

/**
 * 【stargis 改造】登录 Java 业务后端（jeecg boot）：POST {VUE_DATA_JAVA_URL}/sys/login
 *
 * 为什么要单独一个函数而不是复用上面的 login()：
 *   上面的 login() 打的是**中台**（/app/oauth/tokenapply），
 *   本函数打的是 **Java 业务后端**（jeecg），两者是两套独立的认证体系。
 *
 * 关键点（与 jeecg 前端 admin-client 的 api/login.js 对齐）：
 *   1. 口令必须是**明文**：后端自己做 PasswordUtil.encrypt(username, password, salt)
 *      校验，前端不能做任何加密/编码（中台那套三重 Base64 在 jeecg 侧会直接登录失败）。
 *   2. captcha 固定 "cikian"，checkKey 随便给（仅参与 Redis key 拼装，
 *      固定验证码旁路下不会被真正校验），见 JEECG_LOGIN_CAPTCHA 注释。
 *   3. remember_me: true 与 jeecg 原生前端一致（后端目前不强制校验该字段）。
 *   4. X-Sign / X-TIMESTAMP：jeecg 后端签名拦截器只对 jeecg.signUrls 里配置的
 *      字典类接口生效（application-dev.yml），/sys/login 并不在其中；
 *      这里仍然按 jeecg 原生前端的习惯带上，保持行为一致、便于以后放开签名校验。
 *
 * 响应结构（Result<JSONObject>）：
 *   { success: true, code: 200, message: "登录成功",
 *     result: { token, userInfo, departs, multi_depart, sysAllDictItems, tenantList? } }
 * 失败时 success=false，message 为后端提示（如“用户名或密码错误”“验证码无效”）。
 *
 * @param {{username: string, password: string}} parameter 明文账号口令
 * @returns {Promise}
 */
export function jeecgLogin(parameter) {
  const url = '/sys/login'
  const data = {
    username: parameter.username,
    password: parameter.password,
    captcha: JEECG_LOGIN_CAPTCHA,
    checkKey: String(Date.now()),
    remember_me: true
  }
  return axios({
    url: url,
    method: 'post',
    baseURL: javaBaseUrl(),
    data: data,
    headers: {
      'X-Sign': signMd5Utils.getSign(url, data),
      'X-TIMESTAMP': String(Date.now())
    }
  })
}

/**
 * 【stargis 改造】退出 Java 业务后端（jeecg）：POST {VUE_DATA_JAVA_URL}/sys/logout
 *
 * jeecg 后端按请求头 X-Access-Token 取令牌，然后把 Redis 里的
 *   PREFIX_USER_TOKEN+token、用户 shiro 权限缓存、用户信息缓存
 * 全部删掉（LoginController.logout）。
 *
 * 注意：jeecg **没有** /sys/refreshToken 之类的刷新接口。
 * 它的“无感刷新”是后端滑动续期：登录时把 token 写入 Redis 并设 2 小时有效期，
 * 之后每次请求只要 Redis 里还有这个 key 就放行；JWT 自身（1 小时）过期后
 * 后端会用同一个 key 重新签发并重置 2 小时有效期（ShiroRealm.jwtTokenRefresh）。
 * 所以前端要做的只有一件事：每个请求都带上同一个 X-Access-Token。
 *
 * @param {string} token jeecg 令牌
 * @returns {Promise}
 */
export function jeecgLogout(token) {
  return axios({
    url: '/sys/logout',
    method: 'post',
    baseURL: javaBaseUrl(),
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
      'X-Access-Token': token
    }
  })
}

export function phoneLogin(parameter) {
  return axios({
    url: '/sys/phoneLogin',
    method: 'post',
    data: parameter
  })
}

export function getSmsCaptcha(parameter) {
  return axios({
    url: api.SendSms,
    method: 'post',
    data: parameter
  })
}

// export function getInfo() {
//   return axios({
//     url: '/api/user/info',
//     method: 'get',
//     headers: {
//       'Content-Type': 'application/json;charset=UTF-8'
//     }
//   })
// }

export function logout(logoutToken) {
  return axios({
    url: '/app/outApi',
    method: 'get',
    params: {
      'access_token': logoutToken
    }
    // headers: {
    //   'Content-Type': 'application/json;charset=UTF-8',
    //   'X-Access-Token':  logoutToken
    // }
  })
}

/**
 * 第三方登录
 * @param token
 * @param thirdType
 * @returns {*}
 */
export function thirdLogin(token, thirdType) {
  return axios({
    url: `/sys/thirdLogin/getLoginUser/${token}/${thirdType}`,
    method: 'get',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8'
    }
  })
}

/**
 * 强退其他账号
 * @param token
 * @returns {*}
 */
export function forceLogout(parameter) {
  return axios({
    url: '/sys/online/forceLogout',
    method: 'post',
    data: parameter
  })
}