import Vue from 'vue'
import axios from 'axios'
import store from '@/store'
import {
  VueAxios
} from './axios'
import router from '@/router/index'
import {
  ACCESS_TOKEN,
  TENANT_ID
} from "@/store/mutation-types"

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

const err = (error) => {
  if (error.response) {
    let data = error.response.data
    const token = Vue.ls.get(ACCESS_TOKEN)
    ////console.log("------异常响应------", token)
    ////console.log("------异常响应------", error.response.status)
    switch (error.response.status) {
      case 403:
        Vue.prototype.$Jnotification.error({
          message: '系统提示',
          description: '拒绝访问',
          duration: 4
        })
        break
      case 500:
        ////console.log("------error.response------", error.response)
        // update-begin- --- author:liusq ------ date:20200910 ---- for:处理Blob情况----
        let type = error.response.request.responseType;
        if (type === 'blob') {
          blobToJson(data);
          break;
        }
        // update-end- --- author:liusq ------ date:20200910 ---- for:处理Blob情况----
        if (token && data.message.includes("Token失效")) {
          // update-begin- --- author:scott ------ date:20190225 ---- for:Token失效采用弹框模式，不直接跳转----
          if (/wxwork|dingtalk/i.test(navigator.userAgent)) {
            Vue.prototype.$Jmessage.loading('登录已过期，正在重新登陆', 0)
          } else {
            Vue.prototype.$Jmodal.error({
              title: '登录已过期',
              content: '很抱歉，登录已过期，请重新登录',
              okText: '重新登录',
              mask: false,
              onOk: () => {
                store.dispatch('Logout').then(() => {
                  Vue.ls.remove(ACCESS_TOKEN)
                  try {
                    let path = window.document.location.pathname
                    ////console.log('location pathname -> ' + path)
                    if (path != '/' && path.indexOf('/user/login') == -1) {
                      window.location.reload()
                    }
                  } catch (e) {
                    window.location.reload()
                  }
                })
              }
            })
          }
          // update-end- --- author:scott ------ date:20190225 ---- for:Token失效采用弹框模式，不直接跳转----
        }
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
        Vue.prototype.$Jnotification.error({
          message: '系统提示',
          description: '很抱歉，登录已过期，请重新登录',
          duration: 4
        })
        if (token) {
          store.dispatch('Logout').then(() => {
            setTimeout(() => {
              window.location.reload()
            }, 1500)
          })
        }
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

// response interceptor
async function checkToken() {
  // const token = Vue.ls.get(ACCESS_TOKEN)
  // if (token) {
  //   ////console.log(Vue.ls.get(ACCESS_TOKEN), Vue.ls.get('REFRESH_TOKEN'));
  // }
  let res = await axios.get(window._CONFIG.VUE_APP_API_BASE_URL + '/app/sceneCreateApi/dataList', {
    params: {
      access_token: Vue.ls.get(ACCESS_TOKEN),
      page: 1,
      rows: 10

    }
  })
  ////console.log(res.data);
  window.beginRefresh = false
  if (res.data.code != 200) {
    refreshToken()
  } else {

  }
}
async function refreshToken() {
  let res2 = await axios.get(window._CONFIG.VUE_APP_API_BASE_URL + '/app/oauth/refreshToken', {
    params: {
      refreshToken: Vue.ls.get('REFRESH_TOKEN'),

    }
  })
  ////console.log(res2.data);
  if (res2.data.code == 200) {
    Vue.ls.set(ACCESS_TOKEN, res2.data.result)
  } else {
    ////console.log('刷新失败');
    try {
      Vue.ls.remove(ACCESS_TOKEN)
    } catch (error) {

    }
    window.location.reload();
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
                // checkToken()
                let res = await axios.get(window._CONFIG.VUE_APP_API_BASE_URL + '/app/sceneCreateApi/dataList', {
                  params: {
                    access_token: Vue.ls.get(ACCESS_TOKEN),
                    page: 1,
                    rows: 10

                  }
                })
                // ////console.log(res.data);
                window.beginRefresh = false
                if (res.data.code != 200) {
                  let res2 = await axios.get(window._CONFIG.VUE_APP_API_BASE_URL + '/app/oauth/refreshToken', {
                    params: {
                      refreshToken: Vue.ls.get('REFRESH_TOKEN'),

                    }
                  })
                  ////console.log(res2.data);
                  if (res2.data.code == 200) {
                    Vue.ls.set(ACCESS_TOKEN, res2.data.result)
                  } else {
                    ////console.log('刷新失败');
                    try {
                      Vue.ls.remove(ACCESS_TOKEN)
                    } catch (error) {

                    }
                    window.location.reload();
                  }
                } else {

                }
                config.data.set(key, Vue.ls.get(ACCESS_TOKEN));

              }
            }
          } else {
            // ////console.log('不是表单数据');
            if (config.data.access_token) {
              // checkToken()
              let res = await axios.get(window._CONFIG.VUE_APP_API_BASE_URL + '/app/sceneCreateApi/dataList', {
                params: {
                  access_token: Vue.ls.get(ACCESS_TOKEN),
                  page: 1,
                  rows: 10

                }
              })
              // ////console.log(res.data);
              window.beginRefresh = false
              if (res.data.code != 200) {
                let res2 = await axios.get(window._CONFIG.VUE_APP_API_BASE_URL + '/app/oauth/refreshToken', {
                  params: {
                    refreshToken: Vue.ls.get('REFRESH_TOKEN'),

                  }
                })
                ////console.log(res2.data);
                if (res2.data.code == 200) {
                  Vue.ls.set(ACCESS_TOKEN, res2.data.result)
                } else {
                  ////console.log('刷新失败');
                  try {
                    Vue.ls.remove(ACCESS_TOKEN)
                  } catch (error) {

                  }
                  window.location.reload();
                }
              } else {

              }
              config.data.access_token = Vue.ls.get(ACCESS_TOKEN)

            }
          }

        }
        // ////console.log(config.params);
        if (config.params && config.params.access_token) {
          // checkToken()
          let res = await axios.get(window._CONFIG.VUE_APP_API_BASE_URL + '/app/sceneCreateApi/dataList', {
            params: {
              access_token: Vue.ls.get(ACCESS_TOKEN),
              page: 1,
              rows: 10

            }
          })
          ////console.log(res.data);
          window.beginRefresh = false
          if (res.data.code != 200) {
            let res2 = await axios.get(window._CONFIG.VUE_APP_API_BASE_URL + '/app/oauth/refreshToken', {
              params: {
                refreshToken: Vue.ls.get('REFRESH_TOKEN'),

              }
            })
            ////console.log(res2.data);
            if (res2.data.code == 200) {
              Vue.ls.set(ACCESS_TOKEN, res2.data.result)
            } else {
              ////console.log('刷新失败');
              try {
                Vue.ls.remove(ACCESS_TOKEN)
              } catch (error) {

              }
              window.location.reload();
            }
          } else {

          }
          config.params.access_token = Vue.ls.get(ACCESS_TOKEN)

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
service.interceptors.response.use((response) => {
  if (response.data instanceof Object) {
    let res = response.data
    if (res.code && res.code.toString() === '-1' && res.message === '未知异常null') {
      Vue.prototype.$Jnotification.error({
        message: '系统提示',
        description: '很抱歉，遇到未知异常，请重新登录'
      })
      store.dispatch('Logout').then(() => {
        Vue.ls.remove(ACCESS_TOKEN)
        window.location.reload();
      })
    }
  } else {
    Vue.prototype.$Jnotification.error({
      message: '系统提示',
      description: '很抱歉，登录已过期，请重新登录'
    })
    store.dispatch('Logout').then(() => {
      Vue.ls.remove(ACCESS_TOKEN)
      window.location.reload();
    })
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
 * @param data
 */
function blobToJson(data) {
  let fileReader = new FileReader();
  let token = Vue.ls.get(ACCESS_TOKEN);
  fileReader.onload = function () {
    try {
      let jsonData = JSON.parse(this.result); // 说明是普通对象数据，后台转换失败
      ////console.log("jsonData", jsonData)
      if (jsonData.status === 500) {
        ////console.log("token----------》", token)
        if (token && jsonData.message.includes("Token失效")) {
          Modal.error({
            title: '登录已过期',
            content: '很抱歉，登录已过期，请重新登录',
            okText: '重新登录',
            mask: false,
            onOk: () => {
              store.dispatch('Logout').then(() => {
                Vue.ls.remove(ACCESS_TOKEN)
                window.location.reload()
              })
            }
          })
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