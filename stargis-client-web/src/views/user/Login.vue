<template>
  <div class="main">
    <!-- 系统标题 -->
    <div class="system-title">
      {{sysName}}
    </div>
      <!-- 星际GIS统一开发协同平台
    </div> -->
    <div class="login_bg_bg">
      <img src="~@/assets/dlkbg.png" alt="" style="width: 589px" />
      <div style="width: 400px; position: relative; top: -400px; left: 95px">
        <!-- <img src="~@/assets/logocim.png" alt="" style="margin-top: -170px; margin-left: 90px" /> -->
        <div style="width: 224px;position: relative; top: -55px; left: 120px">
          <span style="color: white;font-family: sy;
          font-weight: bold;font-size: 34px; ">欢迎登录</span>
        </div>
        <a-form-model class="user-layout-login" @keyup.enter.native="handleSubmit">
          <!--      <a-tabs :activeKey="customActiveKey" :tabBarStyle="{ textAlign: 'center', borderBottom: 'unset' }"  @change="handleTabClick">-->
          <!--        <a-tab-pane key="tab1" tab="账号密码登录">-->
          <login-account ref="alogin" @validateFail="validateFail" @success="requestSuccess"
            @fail="requestFailed"></login-account>
          <!--        </a-tab-pane>-->

          <!--        <a-tab-pane key="tab2" tab="手机号登录">-->
          <!--          <login-phone ref="plogin" @validateFail="validateFail" @success="requestSuccess" @fail="requestFailed"></login-phone>-->
          <!--        </a-tab-pane>-->
          <!--      </a-tabs>-->

          <!-- <a-form-model-item> -->
          <!--        <a-checkbox @change="handleRememberMeChange" default-checked>自动登录</a-checkbox>-->
          <!--        <router-link :to="{ name: 'alteration'}" class="forge-password" style="float: right;">-->
          <!--          忘记密码-->
          <!--        </router-link>-->
          <!--        <router-link :to="{ name: 'register'}" class="forge-password" style="float: right;margin-right: 10px" >-->
          <!--          注册账户-->
          <!--        </router-link>-->
          <!-- </a-form-model-item> -->

          <a-form-item style="margin-top: 45px">
            <a-button size="large" type="primary" htmlType="submit" class="login-button" :loading="loginBtn"
              @click.stop.prevent="handleSubmit" :disabled="loginBtn">登录
            </a-button>
          </a-form-item>
        </a-form-model>
      </div>
    </div>

    <login-select-tenant ref="loginSelect" @success="loginSelectOk"></login-select-tenant>
  </div>
</template>

<script>
import Vue from 'vue'
import { ACCESS_TOKEN, ENCRYPTED_STRING } from '@/store/mutation-types'
// import ThirdLogin from './third/ThirdLogin'
import LoginSelectTenant from './LoginSelectTenant'
import TwoStepCaptcha from '@/components/tools/TwoStepCaptcha'
import { getEncryptedString } from '@/utils/encryption/aesEncrypt'
import { timeFix } from '@/utils/util'

import LoginAccount from './LoginAccount'
// import LoginPhone from './LoginPhone'


export default {
  components: {
    LoginSelectTenant,
    TwoStepCaptcha,
    // ThirdLogin,
    LoginAccount,
    // LoginPhone
  },
  data() {
    return {
      customActiveKey: 'tab1',
      rememberMe: true,
      loginBtn: false,
      requiredTwoStepCaptcha: false,
      stepCaptchaVisible: false,
      encryptedString: {
        key: '',
        iv: '',
      },
      sysName: window._CONFIG.PROJECT_NAME,
    }
  },
  created() {
    Vue.ls.remove(ACCESS_TOKEN)
    this.getRouterData()
    this.rememberMe = true
    this.$bus.$on('beginLogin', () => {
      this.handleSubmit()
    })

  },
  methods: {
    handleTabClick(key) {
      this.customActiveKey = key
    },
    handleRememberMeChange(e) {
      this.rememberMe = e.target.checked
    },
    /**跳转到登录页面的参数-账号获取*/
    getRouterData() {
      this.$nextTick(() => {
        let temp = this.$route.params.username || this.$route.query.username || ''
        if (temp) {
          this.$refs.alogin.acceptUsername(temp)
        }
      })
    },

    //登录
    handleSubmit() {
      this.loginBtn = true
      if (this.customActiveKey === 'tab1') {
        // 使用账户密码登录
        this.$refs.alogin.handleLogin(this.rememberMe)
      } else {
        //手机号码登录
        this.$refs.plogin.handleLogin(this.rememberMe)
      }
    },
    // 校验失败
    validateFail() {
      this.loginBtn = false
    },
    // 登录后台成功
    requestSuccess(loginResult) {
      this.$refs.loginSelect.show(loginResult)
    },
    //登录后台失败
    requestFailed(err) {
      let description =
        ((err.response || {}).data || {}).message || err.message || err.msg || '请求出现错误，请稍后再试'
      this.$notification['error']({
        message: '登录失败',
        description: description,
        duration: 4,
      })
      //账户密码登录错误后更新验证码
      if (this.customActiveKey === 'tab1' && description.indexOf('密码错误') > 0) {
        this.$refs.alogin.handleChangeCheckCode()
      }
      this.loginBtn = false
    },
    loginSelectOk() {
      this.loginSuccess()
    },
    //登录成功
    loginSuccess() {
      this.$router.push({ path: '/stargis' }).catch(() => {
        // console.log('登录跳转首页出错,这个错误从哪里来的')
      })
      this.$notification.success({
        message: '欢迎',
        description: `${timeFix()}，欢迎回来`,
      })
    },

    stepCaptchaSuccess() {
      this.loginSuccess()
    },
    stepCaptchaCancel() {
      this.Logout().then(() => {
        this.loginBtn = false
        this.stepCaptchaVisible = false
      })
    },
    //获取密码加密规则
    getEncrypte() {
      var encryptedString = Vue.ls.get(ENCRYPTED_STRING)
      if (encryptedString == null) {
        getEncryptedString().then((data) => {
          this.encryptedString = data
        })
      } else {
        this.encryptedString = encryptedString
      }
    },
  },
}
</script>
<style lang="less" scoped>
.system-title {
  position: absolute;
  top: 40px;
  left: 60px;
  font-size: 38px;
  font-family: 'sy', 'Source Han Sans CN', 'Source Han Sans CN-Regular';
  font-weight: bold;
  background: linear-gradient(90deg, #009df4 0%, #00e9d0 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  /* 兼容部分浏览器 */
  background-clip: text;
  text-fill-color: transparent;
  letter-spacing: 2px;
  z-index: 100;
}

.login_bg_bg {
  position: absolute;
  right: 265px;
  top: 245px;
  height: 480px;
}

.user-layout-login {
  /deep/ .ant-input {
    background: transparent;
  }

  /deep/ .has-error .ant-input-affix-wrapper .ant-input,
  .has-error .ant-input-affix-wrapper .ant-input:hover {
    background-color: transparent;
    color: #ffffff;
  }

  /deep/ input:-internal-autofill-selected {
    background-color: transparent !important;
    background-image: none !important;
    color: rgb(255, 255, 255) !important;
    box-shadow: inset 0 0 0 1000px white !important;
  }

  /deep/ .ant-input-affix-wrapper .ant-input:not(:first-child) {
    padding-left: 40px;
    color: #ffffff;
  }

  /deep/ .ant-form-item-required::before {
    display: inline-block;
    margin-right: 4px;
    color: white;
    font-size: 14px;
    // font-family: SimSun, sans-serif;
    font-family: 'sy', 'Source Han Sans CN', 'Source Han Sans CN-Regular';
    line-height: 1;
    content: '';
  }

  .loginTab {
    width: 100%;
    height: 60.5px;
    display: flex;
    align-items: center;
    border-bottom: 1px solid #0071fd;
    margin-top: 5px;

    span {
      font-weight: bold;
      color: #1072ff;
      font-size: 22px;
      // font-family: SimHei;
      font-family: 'sy', 'Source Han Sans CN', 'Source Han Sans CN-Regular';
    }
  }

  label {
    font-size: 14px;
  }

  .getCaptcha {
    display: block;
    width: 100%;
    height: 40px;
  }

  .forge-passWord {
    font-size: 14px;
  }

  button.login-button {
    padding: 0 15px;
    font-size: 16px;
    border: none;
    border-radius: 0px;
    height: 48px;
    width: 100%;
    font-size: 24px !important;
    font-weight: bold !important;
    // font-family: SimSun !important;
    font-family: 'sy', 'Source Han Sans CN', 'Source Han Sans CN-Regular' !important;
    // background-color: #126ae2; 
    background-image: url(~@/assets/loginbtn.png);
  }

  .user-login-other {
    text-align: left;
    margin-top: 24px;
    line-height: 22px;

    .item-icon {
      font-size: 24px;
      color: rgba(0, 0, 0, 0.2);
      margin-left: 16px;
      vertical-align: middle;
      cursor: pointer;
      transition: color 0.3s;

      &:hover {
        color: #1890ff;
      }
    }

    .register {
      float: right;
    }
  }
}

.getCaptcha {
  display: block;
  width: 100%;
  height: 40px;
}

.forge-password {
  font-size: 14px;
}

button.login-button {
  padding: 0 15px;
  font-size: 16px;
  height: 40px;
  width: 100%;
}

.user-login-other {
  text-align: left;
  margin-top: 24px;
  line-height: 22px;

  .item-icon {
    font-size: 24px;
    color: rgba(0, 0, 0, 0.2);
    margin-left: 16px;
    vertical-align: middle;
    cursor: pointer;
    transition: color 0.3s;

    &:hover {
      color: #1890ff;
    }
  }

  .register {
    float: right;
  }
}
</style>
<style>
.valid-error .ant-select-selection__placeholder {
  color: #f5222d;
}
</style>