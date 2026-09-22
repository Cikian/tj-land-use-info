<template>
  <div>
    <a-form-model ref="form" :model="model" :rules="validatorRules">
      <!-- <a-form-model-item required prop="username">
        <a-input v-model="model.username" size="large" placeholder="请输入帐户名">
          <a-icon slot="prefix" type="user" :style="{ color: 'rgba(255,255,255,.35)' }" />
        </a-input>
      </a-form-model-item>
      <a-form-model-item required prop="password">
        <a-input v-model="model.password" size="large" type="password" autocomplete="false" placeholder="请输入密码">
          <a-icon slot="prefix" type="lock" :style="{ color: 'rgba(255,255,255,.35)' }" />
        </a-input>
      </a-form-model-item> -->
      <a-form-model-item required prop="username">
        <div class="input-bg">
          <img class="input-icon" src="~@/assets/user.png" alt="user" />
          <a-input v-model="model.username" size="large" placeholder="请输入用户名" class="input-text" />
        </div>
      </a-form-model-item>
      <a-form-model-item required prop="password">
        <div class="input-bg">
          <img class="input-icon" src="~@/assets/psw.png" alt="psw" />
          <a-input v-model="model.password" size="large" type="password" autocomplete="false" placeholder="请输入密码"
            class="input-text" />
        </div>
      </a-form-model-item>

      <!--        <a-row :gutter="0">-->
      <!--          <a-col :span="16">-->
      <!--            <a-form-model-item required prop="inputCode">-->
      <!--              <a-input v-model="model.inputCode" size="large" type="text" placeholder="请输入验证码">-->
      <!--                <a-icon slot="prefix" type="smile" :style="{ color: 'rgba(0,0,0,.25)' }"/>-->
      <!--              </a-input>-->
      <!--            </a-form-model-item>-->
      <!--          </a-col>-->
      <!--          <a-col :span="8" style="text-align: right">-->
      <!--            <img v-if="requestCodeSuccess" style="margin-top: 2px;" :src="randCodeImage" @click="handleChangeCheckCode"/>-->
      <!--            <img v-else style="margin-top: 2px;" src="../../assets/checkcode.png" @click="handleChangeCheckCode"/>-->
      <!--          </a-col>-->
      <!--        </a-row>-->
    </a-form-model>
  </div>
</template>

<script>
import { getAction } from '@/api/manage'
import Vue from 'vue'
import { mapActions } from 'vuex'
import { login1 } from '../../api/login'
import * as base from './accountEncode/base'
import {
  notification
} from 'ant-design-vue'

export default {
  name: 'LoginAccount',
  data() {
    return {
      requestCodeSuccess: false,
      randCodeImage: '',
      currdatetime: '',
      loginType: 0,
      model: {
        username: '',
        password: '', //0505演示
        inputCode: '',
      },
      validatorRules: {
        username: [{ required: true, message: '请输入用户名!' }, { validator: this.handleUsernameOrEmail }],
        password: [
          {
            required: true,
            message: '请输入密码!',
            validator: 'click',
          },
        ],
        inputCode: [
          {
            required: true,
            message: '请输入验证码!',
          },
        ],
      },
    }
  },
  created() {
    // this.handleChangeCheckCode(); // 获取验证码
    this.handleLoginToken()
  },
  methods: {
    ...mapActions(['Login']),
    /**刷新验证码*/
    handleChangeCheckCode() {
      this.currdatetime = new Date().getTime()
      this.model.inputCode = ''
      getAction(`/sys/randomImage/${this.currdatetime}`)
        .then((res) => {
          if (res.success) {
            this.randCodeImage = res.result
            this.requestCodeSuccess = true
          } else {
            this.$message.error(res.message)
            this.requestCodeSuccess = false
          }
        })
        .catch(() => {
          this.requestCodeSuccess = false
        })
    },
    // 判断登录类型
    handleUsernameOrEmail(rule, value, callback) {
      const regex = /^([a-zA-Z0-9_-])+@([a-zA-Z0-9_-])+((\.[a-zA-Z0-9_-]{2,3}){1,2})$/
      if (regex.test(value)) {
        this.loginType = 0
      } else {
        this.loginType = 1
      }
      callback()
    },
    /**
     * 验证字段
     * @param arr
     * @param callback
     */
    validateFields(arr, callback) {
      let promiseArray = []
      for (let item of arr) {
        let p = new Promise((resolve, reject) => {
          this.$refs['form'].validateField(item, (err) => {
            if (!err) {
              resolve()
            } else {
              reject(err)
            }
          })
        })
        promiseArray.push(p)
      }
      Promise.all(promiseArray)
        .then(() => {
          callback()
        })
        .catch((err) => {
          callback(err)
        })
    },
    acceptUsername(username) {
      this.model['username'] = username
    },
    //账号密码登录
    handleLogin(rememberMe) {
      // this.validateFields([ 'username', 'password', 'inputCode' ], (err)=>{
      this.validateFields(['username', 'password'], (err) => {
        if (!err) {
          let loginName = JSON.parse(JSON.stringify(this.model.username))
          let password = JSON.parse(JSON.stringify(this.model.password))
          for (var i = 0; i < 3; i++) {
            loginName = encode(loginName)
            password = encode(password)
          }
          // console.log(loginName, password)

          // let loginParams = {
          //   // userName: this.model.username,
          //   // userName: 'admin',
          //   userName: loginName,
          //   // passWord: this.model.password,
          //   // passWord: 'admin',
          //   passWord: password,
          //   // captcha: this.model.inputCode,
          //   // checkKey: this.currdatetime,
          //   // remember_me: rememberMe,
          //   // //新一代中台
          //   hours: window._CONFIG.VUE_APP_API_TIME || 10,
          //   //新验证
          //   accessTokenValidityMinute: window._CONFIG.TOKENTIME || 30,
          //   refreshTokenValidityMinute: window._CONFIG.RETIME || 24 * 60,
          // }
          // 新验证
          const loginParams = new FormData()
          loginParams.append('userName', loginName)
          loginParams.append('passWord', password)
          loginParams.append('accessTokenValidityMinute', window._CONFIG.TOKENTIME || 30)
          loginParams.append('refreshTokenValidityMinute', window._CONFIG.RETIME || 24 * 60)
          // console.log('登录参数', loginParams)

          // 【stargis 改造】除了发给中台的表单参数（三重 Base64 后的账号口令），
          // 还要把**明文**账号口令一起交给 store：中台登录成功后 store 会用这份明文
          // 去登录 Java 业务后端（jeecg）的 /sys/login（那边后端自己加盐校验，不能传密文）。
          // 明文只在内存里传递，不写入 localStorage/sessionStorage。
          this.Login({
            form: loginParams,
            username: this.model.username,
            password: this.model.password
          })
            .then((res) => {
              console.log(res);
              // if (res.code == 20004) {
              //   notification['error']({
              //     message: '许可校验',
              //     description: res.msg,
              //   });
              //   return
              // } else if (res.code == 200 && res.result && res.result.hasOwnProperty('days')) {
              //   if (res.result.days < 7) {
              //     notification['success']({
              //       message: '许可校验',
              //       description: "加密锁权限剩余" + res.result.days + "天即将到期，请联系平台运维人员及时更新",
              //     });
              //   } else {
              //     notification['success']({
              //       message: '许可校验',
              //       description: "加密锁权限距离过期还有" + res.result.days + "天",
              //     });
              //   }
              // }
              // console.log(res.result)
              this.$emit('success', res.result)
            })
            .catch((err) => {
              this.$emit('fail', err)
            })
        } else {
          this.$emit('validateFail')
        }
      })
    },
    handleLoginToken() {
      let addstr = document.URL
      addstr = decodeURI(addstr)
      let num = addstr.indexOf('?token=')
      addstr = addstr.substr(num + 7)
      if (num < 0) {
        return
      }
      let loginParams = {
        access_token: addstr, //"96331968ad5f3d59e63cea06ad42cb793363aa4b8c3cd7c04d530596d946a640"
      }
      console.log('登录参数', loginParams)
      // 【stargis 改造】单点登录路径只有中台令牌、没有明文口令，
      // 因此**不会**登录 Java 业务后端（jeecg）——store 只在收到 { form, username, password }
      // 时才做第二步 jeecg 登录。后续 jeecg 侧接口（/land/**、/sys/**）会返回 401，
      // 由 utils/request.js 按“未登录业务后端”提示，不会影响中台会话。
      this.Login(loginParams)
        .then((res) => {
          this.$emit('success', res.result)
        })
        .catch((err) => {
          this.$emit('fail', err)
        })
    },
    // base64加密
    Base64() {
      // private property
      _keyStr = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/='
      // public method for encoding
      this.encode = function (input) {
        var output = ''
        var chr1, chr2, chr3, enc1, enc2, enc3, enc4
        var i = 0
        input = _utf8_encode(input)
        while (i < input.length) {
          chr1 = input.charCodeAt(i++)
          chr2 = input.charCodeAt(i++)
          chr3 = input.charCodeAt(i++)
          enc1 = chr1 >> 2
          enc2 = ((chr1 & 3) << 4) | (chr2 >> 4)
          enc3 = ((chr2 & 15) << 2) | (chr3 >> 6)
          enc4 = chr3 & 63
          if (isNaN(chr2)) {
            enc3 = enc4 = 64
          } else if (isNaN(chr3)) {
            enc4 = 64
          }
          output = output + _keyStr.charAt(enc1) + _keyStr.charAt(enc2) + _keyStr.charAt(enc3) + _keyStr.charAt(enc4)
        }
        return output
      }

      // public method for decoding
      this.decode = function (input) {
        var output = ''
        var chr1, chr2, chr3
        var enc1, enc2, enc3, enc4
        var i = 0
        input = input.replace(/[^A-Za-z0-9\+\/\=]/g, '')
        while (i < input.length) {
          enc1 = _keyStr.indexOf(input.charAt(i++))
          enc2 = _keyStr.indexOf(input.charAt(i++))
          enc3 = _keyStr.indexOf(input.charAt(i++))
          enc4 = _keyStr.indexOf(input.charAt(i++))
          chr1 = (enc1 << 2) | (enc2 >> 4)
          chr2 = ((enc2 & 15) << 4) | (enc3 >> 2)
          chr3 = ((enc3 & 3) << 6) | enc4
          output = output + String.fromCharCode(chr1)
          if (enc3 != 64) {
            output = output + String.fromCharCode(chr2)
          }
          if (enc4 != 64) {
            output = output + String.fromCharCode(chr3)
          }
        }
        output = _utf8_decode(output)
        return output
      }

      // private method for UTF-8 encoding
      _utf8_encode = function (string) {
        string = string.toString().replace(/\r\n/g, '\n')
        var utftext = ''
        for (var n = 0; n < string.length; n++) {
          var c = string.charCodeAt(n)
          if (c < 128) {
            utftext += String.fromCharCode(c)
          } else if (c > 127 && c < 2048) {
            utftext += String.fromCharCode((c >> 6) | 192)
            utftext += String.fromCharCode((c & 63) | 128)
          } else {
            utftext += String.fromCharCode((c >> 12) | 224)
            utftext += String.fromCharCode(((c >> 6) & 63) | 128)
            utftext += String.fromCharCode((c & 63) | 128)
          }
        }
        return utftext
      }

      // private method for UTF-8 decoding
      _utf8_decode = function (utftext) {
        var string = ''
        var i = 0
        var c = (c1 = c2 = 0)
        while (i < utftext.length) {
          c = utftext.charCodeAt(i)
          if (c < 128) {
            string += String.fromCharCode(c)
            i++
          } else if (c > 191 && c < 224) {
            c2 = utftext.charCodeAt(i + 1)
            string += String.fromCharCode(((c & 31) << 6) | (c2 & 63))
            i += 2
          } else {
            c2 = utftext.charCodeAt(i + 1)
            c3 = utftext.charCodeAt(i + 2)
            string += String.fromCharCode(((c & 15) << 12) | ((c2 & 63) << 6) | (c3 & 63))
            i += 3
          }
        }
        return string
      }
    },
  },
}
</script>

<style scoped>
.input-bg {
  position: relative;
  background: url('~@/assets/loginput.png') no-repeat center center;
  background-size: cover;
  display: flex;
  align-items: center;
  height: 48px;
  border: #3a71bb 1px solid;
  /* border-radius: 6px; */
  margin-bottom: 8px;
  /* 可选：加点内边距让输入框和图标不贴边 */
  padding-left: 8px;
  padding-right: 8px;
  
}

.input-icon {
  width: 20px;
  height: 20px;
  margin-right: 12px;
  margin-left: 5px;
  z-index: 2;
}

.input-text {
  flex: 1;
  background: transparent;
  color: #fff;
  border: none;
  box-shadow: none;
  font-size: 16px;
  /* 让输入框高度和父容器一致 */
  height: 48px;
  line-height: 48px;
  /* 去掉 outline */
  outline: none;
}

/* 强制覆盖 a-input 内部样式 */
.input-text ::v-deep .ant-input {
  background: transparent !important;
  color: #fff !important;
  border: none !important;
  box-shadow: none !important;
  height: 48px !important;
  line-height: 48px !important;
  outline: none !important;
  padding-left: 0 !important;
  padding-right: 0 !important;
}
.input-text ::v-deep .ant-input::placeholder {
  color: #f83909 !important;         /* 设置为白色 */
  /* color: #799dc8 !important;       */
  font-size: 20px !important;     /* 设置字体大小 */
  opacity: 0.8;                     /* 保证颜色不透明 */
  font-family: 'sy', 'Source Han Sans CN', 'Source Han Sans CN-Regular' !important;
}
</style>