import Vue from 'vue'
import { axios } from '@/utils/request'
import signMd5Utils from '@/utils/encryption/signMd5Utils'
import { ACCESS_TOKEN } from '@/store/mutation-types'

/**
 * Java 业务后端请求层
 * ===============================================================
 * 【为什么需要这个文件】
 * 本系统有两个后端，配置见 public/static/config.js：
 *
 *   VUE_APP_API_BASE_URL = http://127.0.0.1:4548          中台（网关）
 *   VUE_DATA_JAVA_URL    = http://127.0.0.1:9802/api      Java 业务后端
 *
 * 而 `src/config/index.js` 里 `domianURL = VUE_APP_API_BASE_URL`，
 * 并且 window._CONFIG 的值会覆盖 .env，所以 `@/api/manage`（走 domianURL）
 * 里发出去的请求全部落在**中台**上。
 *
 * 档案管理这类业务接口（/land/**、/sys/common/**）是 Java 后端的，
 * 必须显式切到 VUE_DATA_JAVA_URL，否则请求会打到中台上 404。
 * 这与 admin-client 的行为一致：那边的 public/static/config.js 把
 * VUE_APP_API_BASE_URL 留空，domianURL 退化成 .env 的 9802/api，正好就是 Java 后端。
 *
 * 【为什么不直接改 domianURL】
 * 登录、单点登录、场景/图层（/app/**、/mock/**）等请求仍然必须走中台，
 * 全局改基址会把这些一起打挂。所以这里只给业务请求单独开一条通道。
 *
 * 【为什么不用 @/api/manage 的 getAction 等 + 覆盖 baseURL】
 * 因为 `@/api/manage` 会给每个请求塞 `parameter.access_token`，
 * 而 `src/utils/request.js` 的请求拦截器一旦看到 `params.access_token`，
 * 就会**额外**往中台发一次 `/app/sceneCreateApi/dataList` 做刷新判断，
 * 失败时还会清 token + 整页 reload。业务请求不需要这套中台机制，
 * 这里改为只带 `X-Access-Token` 请求头（Java 端 JwtFilter 的首选来源，
 * 也是 admin-client 一直采用的方式），从而跳过那次多余的中台往返。
 */

/** Java 业务后端基址；缺配置时退回中台地址，再缺就退回 jeecg 默认前缀 */
export function javaBaseUrl () {
  const config = window._CONFIG || {}
  return config.VUE_DATA_JAVA_URL || config.domianURL || '/jeecg-boot'
}

/**
 * Java 端静态资源（/sys/common/static）基址。
 * `@/api/manage` 的 getFileAccessHttpUrl 用的是中台的 staticDomainURL，
 * 取业务文件时会指向错误的后端，所以这里单独给一份。
 */
export function javaStaticBaseUrl () {
  return `${javaBaseUrl()}/sys/common/static`
}

/**
 * 文件访问地址（用于未落库的临时文件预览）
 * @param {string} path 相对存储路径，例如 archive/2026/09/x.pdf
 * @returns {string|undefined}
 */
export function getJavaFileAccessHttpUrl (path) {
  if (!path) return undefined
  if (path.indexOf('http') === 0) return path
  const clean = String(path).replace(/^\/+/, '')
  if (!clean) return undefined
  return `${javaStaticBaseUrl()}/${clean}`
}

/**
 * 业务接口公共请求头。
 *
 * 三件套缺一不可：
 *   X-Access-Token  Java 端 JwtFilter 鉴权的首选来源
 *                   （本项目的 request.js 请求拦截器是注释掉的，
 *                    所以不像 admin-client 那样能自动带上，必须显式设置）
 *   X-Sign          jeecg 的签名校验头，与 admin-client 保持一致
 *   X-TIMESTAMP     签名时间戳，毫秒；后端兼容 yyyyMMddHHmmss 与毫秒两种格式，
 *                   这里统一用毫秒（与 admin-client 的 getTimestamp() 等价）。
 *                   注意 stargis 的 signMd5Utils 只有 getDateTimeToString()，
 *                   没有 getTimestamp()，所以直接用 Date.now()。
 */
function buildHeaders (url, parameter, extraHeaders) {
  const token = Vue.ls.get(ACCESS_TOKEN)
  const headers = {
    'X-Sign': signMd5Utils.getSign(url, parameter),
    'X-TIMESTAMP': String(Date.now()),
  }
  if (token) {
    headers['X-Access-Token'] = token
  }
  return Object.assign(headers, extraHeaders || {})
}

/** GET：参数拼在 query string 上 */
export function javaGetAction (url, parameter) {
  const params = parameter || {}
  return axios({
    url,
    method: 'get',
    baseURL: javaBaseUrl(),
    params,
    headers: buildHeaders(url, params),
  })
}

/** POST：JSON body */
export function javaPostAction (url, parameter) {
  const data = parameter || {}
  return axios({
    url,
    method: 'post',
    baseURL: javaBaseUrl(),
    data,
    headers: buildHeaders(url, data),
  })
}

/** PUT：JSON body（后端 /edit 同时接受 PUT 与 POST） */
export function javaPutAction (url, parameter) {
  const data = parameter || {}
  return axios({
    url,
    method: 'put',
    baseURL: javaBaseUrl(),
    data,
    headers: buildHeaders(url, data),
  })
}

/** DELETE：参数拼在 query string 上 */
export function javaDeleteAction (url, parameter) {
  const params = parameter || {}
  return axios({
    url,
    method: 'delete',
    baseURL: javaBaseUrl(),
    params,
    headers: buildHeaders(url, params),
  })
}

/**
 * 指定 method 的写操作（等价于 @/api/manage 的 httpAction）
 * @param {string} url
 * @param {object} parameter JSON body
 * @param {'post'|'put'} method
 */
export function javaHttpAction (url, parameter, method) {
  const data = parameter || {}
  return axios({
    url,
    method,
    baseURL: javaBaseUrl(),
    data,
    headers: buildHeaders(url, data),
  })
}

/**
 * 浏览器原生下载用的完整地址。
 *
 * 原生下载（window.open / a[href]）无法带自定义请求头，而 Java 端的 JwtFilter
 * 支持从 query string 的 token 参数取令牌，所以这里把 token 拼在 URL 上 ——
 * 与 admin-client 的 buildDownloadUrl 行为一致。
 *
 * @param {string} path 业务路径，例如 /land/archive/export
 * @param {object} params 查询参数，空值会被忽略
 */
export function buildJavaDownloadUrl (path, params) {
  const query = []
  Object.keys(params || {}).forEach((key) => {
    const value = params[key]
    if (value !== undefined && value !== null && value !== '') {
      query.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    }
  })
  const token = Vue.ls.get(ACCESS_TOKEN)
  if (token) {
    query.push(`token=${encodeURIComponent(token)}`)
  }
  const base = javaBaseUrl()
  return `${base}${path}${query.length ? '?' + query.join('&') : ''}`
}

/** Java 通用上传接口地址（jeecg 的文件上传入口） */
export function javaUploadUrl () {
  return `${javaBaseUrl()}/sys/common/upload`
}

export default {
  javaBaseUrl,
  javaStaticBaseUrl,
  getJavaFileAccessHttpUrl,
  javaGetAction,
  javaPostAction,
  javaPutAction,
  javaDeleteAction,
  javaHttpAction,
  buildJavaDownloadUrl,
  javaUploadUrl,
}
