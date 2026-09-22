import Vue from 'vue'
import {
  login,
  logout,
  phoneLogin,
  thirdLogin,
  jeecgLogin,
  jeecgLogout
} from "@/api/login"
import {
  notification
} from 'ant-design-vue'
import {
  ACCESS_TOKEN,
  USER_NAME,
  USER_INFO,
  USER_AUTH,
  SYS_BUTTON_AUTH,
  UI_CACHE_DB_DICT_DATA,
  TENANT_ID,
  CACHE_INCLUDED_ROUTES,
  JEECG_ACCESS_TOKEN,
  JEECG_USER_INFO,
  JEECG_DICT_ITEMS
} from "@/store/mutation-types"
import {
  welcome
} from "@/utils/util"
import {
  queryPermissionsByUser
} from '@/api/api'
import {
  getAction
} from '@/api/manage'

function getSystemName({
  commit
}) {
  return new Promise(function (resolve, reject) {
    getAction('/app/systemname/queryData').then((result) => { // 获取配置的系统名称
      if (result.result && result.result[0]) {
        resolve(result.result[0])
      } else {
        reject()
      }
    })
  })
}

/**
 * 【stargis 改造】清空 Java 业务后端（jeecg）的本地登录态。
 * 只动本地（Vuex + localStorage），不发任何请求，重复调用安全。
 * @param {Function} commit vuex commit
 */
function resetJeecgState(commit) {
  commit('SET_JEECG_TOKEN', '')
  commit('SET_JEECG_INFO', {})
  commit('SET_JEECG_DICT_ITEMS', {})
  Vue.ls.remove(JEECG_ACCESS_TOKEN)
  Vue.ls.remove(JEECG_USER_INFO)
  Vue.ls.remove(JEECG_DICT_ITEMS)
}

const user = {
  state: {
    token: '',
    username: '',
    realname: '',
    tenantid: '',
    welcome: '',
    avatar: '',
    permissionList: [],
    info: {},
    // 系统安全模式
    sysSafeMode: null,
    layer: [],
    sysConfig: [],
    // 【stargis 改造】Java 业务后端（jeecg）登录态，与上面的中台登录态并存
    jeecgToken: '',
    jeecgInfo: {},
    jeecgDictItems: {}
  },

  mutations: {
    SET_TOKEN: (state, token) => {
      state.token = token
    },
    SET_NAME: (state, {
      username,
      realname,
      welcome
    }) => {
      state.username = username
      state.realname = realname
      state.welcome = welcome
    },
    SET_AVATAR: (state, avatar) => {
      state.avatar = avatar
    },
    SET_PERMISSIONLIST: (state, permissionList) => {
      state.permissionList = permissionList
    },
    SET_INFO: (state, info) => {
      state.info = info
    },
    SET_TENANT: (state, id) => {
      state.tenantid = id
    },
    SET_SYS_SAFE_MODE: (state, sysSafeMode) => {
      if (typeof sysSafeMode === 'boolean') {
        state.sysSafeMode = sysSafeMode
      } else {
        state.sysSafeMode = false
      }
    },
    SET_LAYER: (state, info) => {
      state.layer = info
    },
    SET_SYS_CONFIG: (state, info) => {
      state.sysConfig = info
    },
    SET_SYS_NAME: (state, info) => {
      state.sysName = info
    },
    // 【stargis 改造】Java 业务后端（jeecg）登录态
    SET_JEECG_TOKEN: (state, token) => {
      state.jeecgToken = token
    },
    SET_JEECG_INFO: (state, info) => {
      state.jeecgInfo = info
    },
    SET_JEECG_DICT_ITEMS: (state, items) => {
      state.jeecgDictItems = items
    }
  },

  actions: {
    // CAS验证登录
    ValidateLogin({
      commit
    }, userInfo) {
      return new Promise((resolve, reject) => {
        getAction("/sys/cas/client/validateLogin", userInfo).then(response => {
          // console.log("----cas 登录--------", response);
          if (response.success) {
            const result = response.result
            const userInfo = result.userInfo;
            Vue.ls.set(ACCESS_TOKEN, result.token, 7 * 24 * 60 * 60 * 1000)
            Vue.ls.set(USER_NAME, userInfo.username, 7 * 24 * 60 * 60 * 1000)
            Vue.ls.set(USER_INFO, userInfo, 7 * 24 * 60 * 60 * 1000)
            commit('SET_TOKEN', result.token)
            commit('SET_INFO', userInfo)
            commit('SET_NAME', {
              username: userInfo.username,
              realname: userInfo.realname,
              welcome: welcome()
            })
            commit('SET_AVATAR', userInfo.avatar)
            resolve(response)
          } else {
            resolve(response)
          }
        }).catch(error => {
          reject(error)
        })
      })
    },
    /**
     * 登录（【stargis 改造】中台 + Java 业务后端 jeecg 双登录）
     *
     * payload 两种形态：
     *   1. 账号口令登录（登录页）：{ form: FormData, username: '明文账号', password: '明文口令' }
     *      - form    发给中台 /app/oauth/tokenapply（三重 Base64 后的账号口令 + 有效期）
     *      - username/password 是**明文**，只用于紧接着登录 jeecg，不落任何持久化存储
     *   2. 单点登录（?token=xxx）：{ access_token: '中台令牌' }
     *      这条路径手上没有明文口令，按约定**跳过** jeecg 登录
     *      （jeecg 侧接口 401 时由 utils/request.js 按“未登录”提示，不影响中台会话）
     *
     * 执行顺序：先中台，中台返回成功后马上登录 jeecg；jeecg 失败则整体判定为登录失败，
     * 并把已经建立的中台会话回滚掉，避免出现“中台登录了、jeecg 没登录”的半登录状态。
     */
    Login({
      commit,
      dispatch
    }, payload) {
      return new Promise((resolve, reject) => {
        // 兼容单点登录/历史调用：整个 payload 就是中台 login() 的入参
        const isAccountLogin = !!(payload && payload.form)
        const loginParams = isAccountLogin ? payload.form : payload
        const plainAccount = isAccountLogin ? {
          username: payload.username,
          password: payload.password
        } : null
        login(loginParams).then(async response => {
          let res = response

          if (res.code == 20004) {
            notification['error']({
              message: '许可校验',
              description: res.msg,
            });
            return
          } else if (res.code == 200 && res.result && res.result.hasOwnProperty('days')) {
            if (res.result.days < 7) {
              notification['success']({
                message: '许可校验',
                description: "加密锁权限剩余" + res.result.days + "天即将到期，请联系平台运维人员及时更新",
              });
            } else {
              notification['success']({
                message: '许可校验',
                description: "加密锁权限距离过期还有" + res.result.days + "天",
              });
            }
          }
          if (response.msg === '登录成功') {
            // console.log(response);
            const {
              result
            } = response
            const {
              userInfo
            } = result
            // Vue.ls.set(ACCESS_TOKEN, result.token, 7 * 24 * 60 * 60 * 1000)
            Vue.ls.set(ACCESS_TOKEN, result.token)
            Vue.ls.set('REFRESH_TOKEN', result.refreshToken)
            // Vue.ls.set(USER_NAME, userInfo.name, 7 * 24 * 60 * 60 * 1000)
            Vue.ls.set(USER_NAME, userInfo.username, 7 * 24 * 60 * 60 * 1000)
            Vue.ls.set(USER_INFO, userInfo, 7 * 24 * 60 * 60 * 1000)
            // Vue.ls.set(UI_CACHE_DB_DICT_DATA, result.sysAllDictItems, 7 * 24 * 60 * 60 * 1000)
            commit('SET_TOKEN', result.token)
            commit('SET_INFO', userInfo)
            commit('SET_NAME', {
              username: userInfo.username,
              realname: userInfo.username,
              welcome: welcome()
            })
            // commit('SET_AVATAR', userInfo.avatar)

            // 【stargis 改造】第 2 步：中台登录成功后登录 Java 业务后端（jeecg）
            if (plainAccount) {
              try {
                await dispatch('JeecgLogin', plainAccount)
              } catch (err) {
                // jeecg 登录失败 → 回滚中台登录态，整体按登录失败处理
                // （此时用户还没进系统，但中台已经发了令牌，不清理会留下半登录态）
                await dispatch('Logout')
                reject(err)
                return
              }
            }

            getSystemName({
              commit
            }).then((res) => {
              userInfo.sysName = res.name
              Vue.ls.set(USER_INFO, userInfo, 7 * 24 * 60 * 60 * 1000)
              commit('SET_SYS_NAME', res.name)
            })
            resolve(response)
          } else {
            reject(response)
          }
        }).catch(error => {
          reject(error)
        })
      })
    },
    /**
     * 【stargis 改造】登录 Java 业务后端（jeecg）：POST {VUE_DATA_JAVA_URL}/sys/login
     *
     * 要点：
     *   - 明文账号口令（中台与 jeecg 是同一套账号口令：用户同步时同一份明文同时写入两端）；
     *   - 中台与 jeecg 的令牌分开存（JEECG_ACCESS_TOKEN / JEECG_USER_INFO），
     *     绝不覆盖中台的 ACCESS_TOKEN，否则 /app/** 接口会立刻 401；
     *   - jeecg 的“无感刷新”在后端（ShiroRealm.jwtTokenRefresh 滑动续期，闲置约 2 小时失效），
     *     前端只需保证每个请求都带同一个 X-Access-Token，这里不发任何刷新请求；
     *   - 失败时 reject 一个带 message 的对象，登录页会直接把 message 弹给用户。
     */
    JeecgLogin({
      commit
    }, payload) {
      return new Promise((resolve, reject) => {
        const username = payload && payload.username
        const password = payload && payload.password
        if (!username || !password) {
          reject({
            code: 500,
            jeecgLogin: true,
            message: '缺少用户名或密码，无法登录业务后端'
          })
          return
        }
        jeecgLogin({
          username,
          password
        }).then(response => {
          if (response && response.success && response.result && response.result.token) {
            const result = response.result
            const info = result.userInfo || {}
            Vue.ls.set(JEECG_ACCESS_TOKEN, result.token)
            Vue.ls.set(JEECG_USER_INFO, info, 7 * 24 * 60 * 60 * 1000)
            if (result.sysAllDictItems) {
              Vue.ls.set(JEECG_DICT_ITEMS, result.sysAllDictItems, 7 * 24 * 60 * 60 * 1000)
            }
            commit('SET_JEECG_TOKEN', result.token)
            commit('SET_JEECG_INFO', info)
            commit('SET_JEECG_DICT_ITEMS', result.sysAllDictItems || {})
            // multi_depart: 0=无部门 1=一个部门 2=多个部门
            // 同步过来的用户必然只属于一个部门，出现 2 说明数据不符合约定，仅告警不阻断
            if (result.multi_depart === 2) {
              console.warn('[stargis] 业务后端返回 multi_depart=2（用户属于多个部门），' +
                '本系统约定一个用户只对应一个部门，请检查同步数据')
            }
            resolve(result)
          } else {
            reject({
              code: (response && response.code) || 500,
              jeecgLogin: true,
              message: '业务后端登录失败：' + ((response && (response.message || response.msg)) || '未知错误')
            })
          }
        }).catch(error => {
          // 网络异常 / 4xx / 5xx：utils/request.js 的 err 已经弹过提示，这里只把信息往上抛
          const body = (error && error.response && error.response.data) || {}
          reject({
            code: (error && error.response && error.response.status) || 500,
            jeecgLogin: true,
            message: '业务后端登录失败：' + (body.message || (error && error.message) || '请求异常')
          })
        })
      })
    },
    /**
     * 【stargis 改造】退出 Java 业务后端（jeecg）并清空本地 jeecg 登录态。
     * 无论后端调用成功与否，本地都会清理干净。
     */
    JeecgLogout({
      commit
    }) {
      return new Promise((resolve) => {
        const token = Vue.ls.get(JEECG_ACCESS_TOKEN)
        if (!token) {
          resetJeecgState(commit)
          resolve()
          return
        }
        jeecgLogout(token).then(() => {
          resetJeecgState(commit)
          resolve()
        }).catch(() => {
          resetJeecgState(commit)
          resolve()
        })
      })
    },
    //手机号登录
    PhoneLogin({
      commit
    }, userInfo) {
      return new Promise((resolve, reject) => {
        phoneLogin(userInfo).then(response => {
          if (response.code == '200') {
            const result = response.result
            const userInfo = result.userInfo
            Vue.ls.set(ACCESS_TOKEN, result.token, 7 * 24 * 60 * 60 * 1000)
            Vue.ls.set(USER_NAME, userInfo.username, 7 * 24 * 60 * 60 * 1000)
            Vue.ls.set(USER_INFO, userInfo, 7 * 24 * 60 * 60 * 1000)
            Vue.ls.set(UI_CACHE_DB_DICT_DATA, result.sysAllDictItems, 7 * 24 * 60 * 60 * 1000)
            commit('SET_TOKEN', result.token)
            commit('SET_INFO', userInfo)
            commit('SET_NAME', {
              username: userInfo.username,
              realname: userInfo.realname,
              welcome: welcome()
            })
            commit('SET_AVATAR', userInfo.avatar)
            resolve(response)
          } else {
            reject(response)
          }
        }).catch(error => {
          reject(error)
        })
      })
    },
    // 获取用户信息
    GetPermissionList({
      commit
    }) {
      return new Promise((resolve, reject) => {
        // queryPermissionsByUser().then(response => {
        //   let obj = {}
        //   response.result.map((item) => {
        //     obj[item.componentname] = item
        //   })
        //   commit('SET_SYS_CONFIG', obj)
        //   resolve(response)
        // }).catch(error => {
        //   reject(error)
        // })
        let para = {
          page: 1,
          rows: 10000,
        }
        getAction('/app/sceneCreateApi/dataList', para).then(res => {
          window.mousetimedelay = false
          // console.log(res);
          //设置超时

          // 监听鼠标移动事件
          document.addEventListener('mousemove', function () {
            // 如果已经设置了定时器，则清除它
            if (window.mouseMoveTimeout) {
              clearTimeout(window.mouseMoveTimeout);
            }
            if (window.mousetimedelay) {
              // alert('退出系统')
              try {
                Vue.ls.remove(ACCESS_TOKEN)
              } catch (error) {

              }
              window.location.reload();
              window.mousetimedelay = false
            }

            // 设置新的定时器，如果在30分钟后鼠标没有移动，则调用exitSystem函数
            window.mouseMoveTimeout = setTimeout(function () {
              // 鼠标长时间未移动，执行退出系统的逻辑
              console.log('鼠标长时间未移动，退出系统。');
              window.mousetimedelay = true
              // 这里可以添加退出系统的代码
            }, window._CONFIG.MOUSETIME * 60 * 1000);
          });

          if (res && res.msg) {
            let sceneId
            if (window.sceneId) {
              // sceneId = res.result.data[0].id
              // // window.sceneId = sceneId
              sceneId = window.sceneId
            } else {

              sceneId = res.result.data[0].id
              for (let i = 0; i < res.result.data.length; i++) {
                if (res.result.data[i].viewFlag && res.result.data[i].viewFlag == "1") {
                  sceneId = res.result.data[i].id

                }

              }
              window.sceneId = sceneId
              // console.log('to.query-----window.sceneId',res.result)
            }
            for (let i = 0; i < res.result.data.length; i++) {
              if (res.result.data[i].id == sceneId) {
                window._CONFIG.PROJECT_NAME = res.result.data[i].sceneName

                if (res.result.data[i].fs3) {
                  if (res.result.data[i].fs3.split(',').length > 1) {
                    window.initView = res.result.data[i].fs3.split(',')

                  }
                }

                if (!window.SelectColorType) {
                  // res.result.data[0].theme
                  let ColoeTheme = 0
                  // console.log('res.result.data[0].theme', res.result.data[i].theme);
                  if (res.result.data[i].theme == '0') { //绿色样式
                    window.SelectColorType = 'green';
                    ColoeTheme = 1
                  } else if (res.result.data[i].theme == '1') { //蓝色样式
                    window.SelectColorType = 'blue';
                    ColoeTheme = 2
                  } else {
                    window.SelectColorType = 'green';
                    ColoeTheme = 1
                  }
                  // console.log('设置场景颜色样式 SelectColorType', window.SelectColorType);
                  document.getElementById('app').className = 'system-theme' + ColoeTheme
                }


              }

            }
            //增加主题颜色的样式替换 20250206zbs
            if (!window.SelectColorType) {
              // res.result.data[0].theme
              let ColoeTheme = 0
              // console.log('res.result.data[0].theme', res.result.data[0].theme);
              if (res.result.data[0].theme == '0') { //绿色样式
                window.SelectColorType = 'green';
                ColoeTheme = 1
              } else if (res.result.data[0].theme == '1') { //蓝色样式
                window.SelectColorType = 'blue';
                ColoeTheme = 2
              } else {
                window.SelectColorType = 'green';
                ColoeTheme = 1
              }
              // console.log('设置场景颜色样式 SelectColorType', window.SelectColorType);
              document.getElementById('app').className = 'system-theme' + ColoeTheme
            }
            // commit('SET_SYS_CONFIG', res.result.data[0])
            // resolve(response)
            getAction('/app/sceneCreateApi/funDataBySceneid', {
              sceneid: sceneId
            }).then((response) => {
              // console.log(response);
              //手动组二三维
              for (let i = 0; i < response.result.length; i++) {
                response.result[i].functionType = '23'
                let functionTypeStr = ''
                if (response.result[i].children) {
                  for (let j = 0; j < response.result[i].children.length; j++) {
                    if (response.result[i].children[j].nodetype == 'node') {
                      functionTypeStr = response.result[i].children[j].functionType ? functionTypeStr + response.result[i].children[j].functionType : functionTypeStr
                    } else {
                      response.result[i].children[j].functionType = '23'
                      let smallModuleFunctionTypeStr = ''
                      if (response.result[i].children[j].children) {
                        for (let k = 0; k < response.result[i].children[j].children.length; k++) {
                          smallModuleFunctionTypeStr = response.result[i].children[j].children[k].functionType ? smallModuleFunctionTypeStr + response.result[i].children[j].children[k].functionType : smallModuleFunctionTypeStr
                        }
                      }
                      if (smallModuleFunctionTypeStr) {
                        if (smallModuleFunctionTypeStr.indexOf('2') > -1 && smallModuleFunctionTypeStr.indexOf('3') < 0) {
                          response.result[i].children[j].functionType = '2'
                        } else if (smallModuleFunctionTypeStr.indexOf('3') > -1 && smallModuleFunctionTypeStr.indexOf('2') < 0) {
                          response.result[i].children[j].functionType = '3'
                        } else if (smallModuleFunctionTypeStr.indexOf('3') > -1 && smallModuleFunctionTypeStr.indexOf('2') > -1) {
                          response.result[i].children[j].functionType = '23'
                        }
                      }
                      functionTypeStr = functionTypeStr + smallModuleFunctionTypeStr
                    }

                  }
                }
                if (functionTypeStr) {
                  if (functionTypeStr.indexOf('2') > -1 && functionTypeStr.indexOf('3') < 0) {
                    response.result[i].functionType = '2'
                  } else if (functionTypeStr.indexOf('3') > -1 && functionTypeStr.indexOf('2') < 0) {
                    response.result[i].functionType = '3'
                  } else if (functionTypeStr.indexOf('3') > -1 && functionTypeStr.indexOf('2') > -1) {
                    response.result[i].functionType = '23'
                  }
                }
              }
              // console.log(response);
              if (response.result) {
                solveDataurl(response.result)
              }

              function solveDataurl(children) {
                for (let i = 0; i < children.length; i++) {
                  if (children[i].nodetype == 'node') {
                    if (children[i].dataURL) {
                      if (children[i].dataURL && children[i].dataURL == '{}') {
                        children[i].dataURL = ''
                        children[i].dataurl = ''
                      } else {
                        let jsonstr = eval("(" + children[i].dataURL + ")")
                        let json = {}
                        for (const key in jsonstr) {
                          if (jsonstr[key].type == 'layer') {
                            // json[key] = jsonstr[key].value.replace(/,/g, '@$&#')
                            json[key] = jsonstr[key].value.replace(/,/g, '@@&#').replace(/@@/g, '@$')
                            // while (jsonstr[key].value.indexOf(',') > -1) {
                            //   jsonstr[key].value = jsonstr[key].value.replace(',', '@$&#')
                            // }
                            // json[key] = jsonstr[key].value
                            // console.log(json[key]);
                          } else {
                            json[key] = jsonstr[key].value
                          }

                        }
                        // children[i].dataURL = JSON.stringify(json)
                        children[i].dataurl = JSON.stringify(json)
                      }

                    }
                    if (children[i].serverurl && children[i].serverurl == '{}') {
                      children[i].serverurl = ''
                    }
                  } else if (children[i].nodetype == 'group') {
                    if (children[i].children) {
                      solveDataurl(children[i].children)
                    }
                  }

                }
              } //

              let obj = {}
              response.result.map((item) => {
                obj[item.componentname] = item
              })
              commit('SET_SYS_CONFIG', obj)
              resolve(response)
            })
          } else {
            reject(error)
          }
        }).catch((error) => {
          // notification.error({
          //   message: '系统提示',
          //   description: '你密码错了'
          // })
          reject(error)
        })
      })
    },
    // 登出（【stargis 改造】中台 + Java 业务后端 jeecg 一起登出）
    Logout({
      commit,
      state,
      dispatch
    }) {
      return new Promise((resolve) => {
        let logoutToken = state.token;
        commit('SET_TOKEN', '')
        commit('SET_SYS_CONFIG', [])
        Vue.ls.remove(ACCESS_TOKEN)
        Vue.ls.remove('REFRESH_TOKEN')
        Vue.ls.remove(USER_INFO)
        Vue.ls.remove(USER_NAME)
        Vue.ls.remove(UI_CACHE_DB_DICT_DATA)
        Vue.ls.remove(CACHE_INCLUDED_ROUTES)
        Vue.ls.remove(TENANT_ID)
        // 【stargis 改造】同步注销 jeecg 会话（清 Redis 里的 token/权限缓存），
        // 并清掉本地 jeecg 登录态；失败也不阻断中台登出流程
        dispatch('JeecgLogout')
        //console.log('logoutToken: '+ logoutToken)                                                                                                                   
        logout(logoutToken).then(() => {
          if (process.env.VUE_APP_SSO == 'true') {
            let sevice = 'http://' + window.location.host + '/'
            let serviceUrl = encodeURIComponent(sevice)
            window.location.href = process.env.VUE_APP_CAS_BASE_URL + '/logout?service=' + serviceUrl
          }
          resolve()
        }).catch(() => {
          resolve()
        })
      })
    },
    // 第三方登录
    ThirdLogin({
      commit
    }, param) {
      return new Promise((resolve, reject) => {
        thirdLogin(param.token, param.thirdType).then(response => {
          if (response.code == '200') {
            const result = response.result
            const userInfo = result.userInfo
            Vue.ls.set(ACCESS_TOKEN, result.token, 7 * 24 * 60 * 60 * 1000)
            Vue.ls.set(USER_NAME, userInfo.username, 7 * 24 * 60 * 60 * 1000)
            Vue.ls.set(USER_INFO, userInfo, 7 * 24 * 60 * 60 * 1000)
            commit('SET_TOKEN', result.token)
            commit('SET_INFO', userInfo)
            commit('SET_NAME', {
              username: userInfo.username,
              realname: userInfo.realname,
              welcome: welcome()
            })
            commit('SET_AVATAR', userInfo.avatar)
            resolve(response)
          } else {
            reject(response)
          }
        }).catch(error => {
          reject(error)
        })
      })
    },
    saveTenant({
      commit
    }, id) {
      Vue.ls.set(TENANT_ID, id, 7 * 24 * 60 * 60 * 1000)
      commit('SET_TENANT', id)
    }

  }
}

export default user