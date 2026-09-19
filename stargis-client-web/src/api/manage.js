import Vue from 'vue'
import {
  axios
} from '@/utils/request'
import signMd5Utils from '@/utils/encryption/signMd5Utils'
import {
  ACCESS_TOKEN
} from "@/store/mutation-types"

const api = {
  user: '/mock/api/user',
  role: '/mock/api/role',
  service: '/mock/api/service',
  permission: '/mock/api/permission',
  permissionNoPager: '/mock/api/permission/no-pager'
}


async function checkToken() {
  const token = Vue.ls.get(ACCESS_TOKEN)
  if (token) {
    console.log(Vue.ls.get(ACCESS_TOKEN), Vue.ls.get('REFRESH_TOKEN'));
  }
}
export default api

//post
export function postAction(url, parameter) {
  // checkToken()
  // let sign = signMd5Utils.getSign(url, parameter);
  const token = Vue.ls.get(ACCESS_TOKEN)
  //将签名和时间戳，添加在请求接口 Header
  // let signHeader = {"access_token": token, "X-Sign": sign,"X-TIMESTAMP": signMd5Utils.getDateTimeToString()};
  parameter = parameter || {}
  parameter["access_token"] = token;
  if (url.indexOf('bookMark') > -1 || url.indexOf('animate') > -1 || url.indexOf('dxxh') > -1) {
    let sceneId = window.sceneId
    parameter.set("sceneid", sceneId)
  }
  return axios({
    url: url,
    method: 'post',
    data: parameter,
    // headers: signHeader
  })
}
export function postActionNoAccess(url, parameter) {
  // let sign = signMd5Utils.getSign(url, parameter);
  // const token = Vue.ls.get(ACCESS_TOKEN)
  //将签名和时间戳，添加在请求接口 Header
  // let signHeader = {"access_token": token, "X-Sign": sign,"X-TIMESTAMP": signMd5Utils.getDateTimeToString()};
  parameter = parameter || {}
  // parameter["access_token"] = token;
  if (url.indexOf('bookMark') > -1 || url.indexOf('animate') > -1 || url.indexOf('dxxh') > -1) {
    let sceneId = window.sceneId
    parameter["sceneid"] = sceneId
  }
  return axios({
    url: url,
    method: 'post',
    data: parameter,
    // headers: signHeader
  })
}

//putStringAction
export function postStringAction(url, parameter) {
  return axios({
    url: url,
    method: 'post',
    data: parameter
  })
}

//post method= {post | put}
export function httpAction(url, parameter, method) {
  parameter = parameter || {}
  const token = Vue.ls.get(ACCESS_TOKEN)
  parameter["access_token"] = token;
  return axios({
    url: url,
    method: method,
    data: parameter
  })
}

//put
export function putAction(url, parameter) {
  return axios({
    url: url,
    method: 'put',
    data: parameter
  })
}

/**
 * 文件上传 用于富文本上传图片
 * @param url
 * @param parameter
 * @returns {*}
 */
export function postJson(url, parameter) {
  return axios.post(url, parameter, {
    headers: {
      'Content-Type': 'application/json'
    }
  })
}

//get
export function getAction(url, parameter) {
  parameter = parameter || {}
  let sign = signMd5Utils.getSign(url, parameter);
  //将签名和时间戳，添加在请求接口 Header
  const token = Vue.ls.get(ACCESS_TOKEN)
  parameter["access_token"] = token;
  //bookmark
  if (url.indexOf('bookMark') > -1 || url.indexOf('animate') > -1 || url.indexOf('dxxh') > -1) {
    const sceneId = window.sceneId
    parameter["sceneid"] = sceneId;
  }

  return axios({
    url: url,
    method: 'get',
    params: parameter
  })
}

//deleteAction
export function deleteAction(url, parameter) {
  parameter = parameter || {}
  const token = Vue.ls.get(ACCESS_TOKEN)
  parameter["access_token"] = token;
  return axios({
    url: url,
    method: 'delete',
    params: parameter
  })
}

export function getUserList(parameter) {
  return axios({
    url: api.user,
    method: 'get',
    params: parameter
  })
}

export function getRoleList(parameter) {
  return axios({
    url: api.role,
    method: 'get',
    params: parameter
  })
}

export function getServiceList(parameter) {
  return axios({
    url: api.service,
    method: 'get',
    params: parameter
  })
}

export function getPermissions(parameter) {
  return axios({
    url: api.permissionNoPager,
    method: 'get',
    params: parameter
  })
}

// id == 0 add     post
// id != 0 update  put
export function saveService(parameter) {
  return axios({
    url: api.service,
    method: parameter.id == 0 ? 'post' : 'put',
    data: parameter
  })
}

/**
 * 下载文件 用于excel导出
 * @param url
 * @param parameter
 * @returns {*}
 */
export function downFile(url, parameter) {
  return axios({
    url: url,
    params: parameter,
    method: 'get',
    responseType: 'blob'
  })
}

/**
 * 下载文件
 * @param url 文件路径
 * @param fileName 文件名
 * @param parameter
 * @returns {*}
 */
export function downloadFile(url, fileName, parameter) {
  return downFile(url, parameter).then((data) => {
    if (!data || data.size === 0) {
      Vue.prototype['$message'].warning('文件下载失败')
      return
    }
    if (typeof window.navigator.msSaveBlob !== 'undefined') {
      window.navigator.msSaveBlob(new Blob([data]), fileName)
    } else {
      let url = window.URL.createObjectURL(new Blob([data]))
      let link = document.createElement('a')
      link.style.display = 'none'
      link.href = url
      link.setAttribute('download', fileName)
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link) //下载完成移除元素
      window.URL.revokeObjectURL(url) //释放掉blob对象
    }
  })
}

/**
 * 文件上传 用于富文本上传图片
 * @param url
 * @param parameter
 * @returns {*}
 */
export function jsonAction(url, parameter) {
  return axios({
    url: url,
    data: parameter,
    method: 'post',
    dataType: 'JSON',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8', // 上传json数据
    },
  })
}

/**
 * 文件上传 用于富文本上传图片
 * @param url
 * @param parameter
 * @returns {*}
 */
export function uploadAction(url, parameter) {
  return axios({
    url: url,
    data: parameter,
    method: 'post',
    headers: {
      'Content-Type': 'multipart/form-data', // 文件上传
    },
  })
}

/**
 * 获取文件服务访问路径
 * @param avatar
 * @param subStr
 * @returns {*}
 */
export function getFileAccessHttpUrl(avatar, subStr) {
  if (!subStr) subStr = 'http'
  try {
    if (avatar && avatar.startsWith(subStr)) {
      return avatar;
    } else {
      if (avatar && avatar.length > 0 && avatar.indexOf('[') == -1) {
        return window._CONFIG['staticDomainURL'] + "/" + avatar;
      }
    }
  } catch (err) {
    return;
  }
}