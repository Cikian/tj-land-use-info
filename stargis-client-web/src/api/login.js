import api from './index'
import {
  axios
} from '@/utils/request'

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