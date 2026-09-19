import Vue from 'vue'
import router from './router'
import store from './store'
import NProgress from 'nprogress' // progress bar
import 'nprogress/nprogress.css' // progress bar style
import notification from 'ant-design-vue/es/notification'
import {
  ACCESS_TOKEN,
  INDEX_MAIN_PAGE_PATH,
  OAUTH2_LOGIN_PAGE_PATH
} from '@/store/mutation-types'
import {
  generateIndexRouter,
  isOAuth2AppEnv
} from '@/utils/util'

NProgress.configure({
  showSpinner: false
}) // NProgress Configuration

const whiteList = ['/user/login', '/user/register', '/user/register-result', '/user/alteration'] // no redirect whitelist , '/stargis/index'
whiteList.push(OAUTH2_LOGIN_PAGE_PATH)

import {
  USER_NAME,
  USER_INFO,
  USER_AUTH,
  SYS_BUTTON_AUTH,
  UI_CACHE_DB_DICT_DATA,
  TENANT_ID,
  CACHE_INCLUDED_ROUTES
} from "@/store/mutation-types"
router.beforeEach((to, from, next) => {
  // console.log(to, from, next);
  // to.path.indexOf('webgl') > -1
  if (to.query.access_token && to.query.sceneid && to.query.username && to.query.refresh_token) {
    window.sceneId = to.query.sceneid
    // console.log(encodeURI(to.query.username))
    // to.query.username = encodeURI(to.query.username)
    // commit('SET_TOKEN', '')
    // commit('SET_SYS_CONFIG', [])
    try {
      Vue.ls.remove(ACCESS_TOKEN)

    } catch (error) {

    }
    Vue.ls.set(ACCESS_TOKEN, to.query.access_token)
    Vue.ls.set('REFRESH_TOKEN', to.query.refresh_token)
    try {
      Vue.ls.remove(USER_INFO)

    } catch (error) {

    }
    Vue.ls.set(USER_INFO, {
      // username: encodeURI(to.query.username)
    }, 7 * 24 * 60 * 60 * 1000)
    try {
      Vue.ls.remove(USER_NAME)

    } catch (error) {

    }
    Vue.ls.set(USER_NAME, to.query.username, 7 * 24 * 60 * 60 * 1000)
    try {
      Vue.ls.remove(UI_CACHE_DB_DICT_DATA)
      Vue.ls.remove(CACHE_INCLUDED_ROUTES)
      Vue.ls.remove(TENANT_ID)
    } catch (error) {

    }

    to = {
      "name": "home",
      "meta": {},
      "path": "/",
      "hash": "",
      "query": {},
      "params": {},
      "fullPath": "/",
      "matched": []
    }
  }
  NProgress.start() // start progress bar
  setTimeout(() => {
    if (Vue.ls.get(ACCESS_TOKEN)) {
      // alert(Vue.ls.get(ACCESS_TOKEN))
      /* has token */
      if (to.path === '/user/login' || to.path === OAUTH2_LOGIN_PAGE_PATH) {
        next({
          path: INDEX_MAIN_PAGE_PATH
        })
        NProgress.done()
      } else {
        if (store.getters.sysConfig.length === 0) {
          store.dispatch('GetPermissionList').then(res => {
              const menuData = res.result;
              //console.log(res.message)
              if (menuData === null || menuData === "" || menuData === undefined) {
                return;
              }
              let constRoutes = [];
              constRoutes = generateIndexRouter(menuData);
              // 添加主界面路由
              store.dispatch('UpdateAppRouter', {
                constRoutes
              }).then(() => {
                // 根据roles权限生成可访问的路由表
                // 动态添加可访问路由表
                router.addRoutes(store.getters.addRouters)
                const redirect = decodeURIComponent(from.query.redirect || to.path)
                if (to.path === redirect) {
                  // hack方法 确保addRoutes已完成 ,set the replace: true so the navigation will not leave a history record
                  next({
                    ...to,
                    replace: true
                  })
                } else {
                  // 跳转到目的路由
                  next({
                    path: redirect
                  })
                }
              })
            })
            .catch(() => {
              /* notification.error({
                 message: '系统提示',
                 description: '请求用户信息失败，请重试！'
               })*/
              store.dispatch('Logout').then(() => {
                next({
                  path: 'user/login',
                  query: {
                    redirect: to.fullPath
                  }
                }) ///user/login
              })
            })
        } else {
          next()
        }
      }
    } else {
      if (whiteList.indexOf(to.path) !== -1) {
        // 在免登录白名单，如果进入的页面是login页面并且当前是OAuth2app环境，就进入OAuth2登录页面
        if (to.path === 'user/login' && isOAuth2AppEnv()) { ///user/login
          next({
            path: OAUTH2_LOGIN_PAGE_PATH
          })
        } else {
          // 在免登录白名单，直接进入
          next()
        }
        NProgress.done()
      } else {
        // 如果当前是在OAuth2APP环境，就跳转到OAuth2登录页面
        let path = isOAuth2AppEnv() ? OAUTH2_LOGIN_PAGE_PATH : 'user/login' ///user/login
        next({
          path: path,
          query: {
            redirect: to.fullPath
          }
        })
        // window.location.href="http://www.baidu.com"
        NProgress.done() // if current page is login will not trigger afterEach hook, so manually handle it
      }
    }
  }, 100);
  // if (Vue.ls.get(ACCESS_TOKEN)) {
  //   alert(Vue.ls.get(ACCESS_TOKEN))
  //   /* has token */
  //   if (to.path === '/user/login' || to.path === OAUTH2_LOGIN_PAGE_PATH) {
  //     next({
  //       path: INDEX_MAIN_PAGE_PATH
  //     })
  //     NProgress.done()
  //   } else {
  //     if (store.getters.sysConfig.length === 0) {
  //       store.dispatch('GetPermissionList').then(res => {
  //           const menuData = res.result;
  //           //console.log(res.message)
  //           if (menuData === null || menuData === "" || menuData === undefined) {
  //             return;
  //           }
  //           let constRoutes = [];
  //           constRoutes = generateIndexRouter(menuData);
  //           // 添加主界面路由
  //           store.dispatch('UpdateAppRouter', {
  //             constRoutes
  //           }).then(() => {
  //             // 根据roles权限生成可访问的路由表
  //             // 动态添加可访问路由表
  //             router.addRoutes(store.getters.addRouters)
  //             const redirect = decodeURIComponent(from.query.redirect || to.path)
  //             if (to.path === redirect) {
  //               // hack方法 确保addRoutes已完成 ,set the replace: true so the navigation will not leave a history record
  //               next({
  //                 ...to,
  //                 replace: true
  //               })
  //             } else {
  //               // 跳转到目的路由
  //               next({
  //                 path: redirect
  //               })
  //             }
  //           })
  //         })
  //         .catch(() => {
  //           /* notification.error({
  //              message: '系统提示',
  //              description: '请求用户信息失败，请重试！'
  //            })*/
  //           store.dispatch('Logout').then(() => {
  //             next({
  //               path: 'user/login',
  //               query: {
  //                 redirect: to.fullPath
  //               }
  //             }) ///user/login
  //           })
  //         })
  //     } else {
  //       next()
  //     }
  //   }
  // } else {
  //   if (whiteList.indexOf(to.path) !== -1) {
  //     // 在免登录白名单，如果进入的页面是login页面并且当前是OAuth2app环境，就进入OAuth2登录页面
  //     if (to.path === 'user/login' && isOAuth2AppEnv()) { ///user/login
  //       next({
  //         path: OAUTH2_LOGIN_PAGE_PATH
  //       })
  //     } else {
  //       // 在免登录白名单，直接进入
  //       next()
  //     }
  //     NProgress.done()
  //   } else {
  //     // 如果当前是在OAuth2APP环境，就跳转到OAuth2登录页面
  //     let path = isOAuth2AppEnv() ? OAUTH2_LOGIN_PAGE_PATH : 'user/login' ///user/login
  //     next({
  //       path: path,
  //       query: {
  //         redirect: to.fullPath
  //       }
  //     })
  //     // window.location.href="http://www.baidu.com"
  //     NProgress.done() // if current page is login will not trigger afterEach hook, so manually handle it
  //   }
  // }
})

router.afterEach(() => {
  NProgress.done() // finish progress bar
})