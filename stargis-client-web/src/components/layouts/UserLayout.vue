<template>
  <div id="userLayout" :class="['user-layout-wrapper', device]">
    <!-- <div class="swiper mySwiper">
      <div class="swiper-wrapper">
        <div class="swiper-slide" style="width:100%,height:100%;" id="yanshi5">
          <img class="swiper-slide" src="@/assets/yanshi/1.png" style="width:100%,height:100%" id="yanshi1" />
          <video
            src="@/assets/yanshi/5.mp4"
            style="position: absolute; width: 64%; height: 80%; right: 2.5%; top: 16.7%"
            controls
          ></video>
        </div>
        <img class="swiper-slide" src="@/assets/yanshi/2.png" style="width:100%,height:100%" id="yanshi2" />
        <img class="swiper-slide" src="@/assets/yanshi/3.png" style="width:100%,height:100%" id="yanshi3" />
        <img class="swiper-slide" src="@/assets/yanshi/4.png" style="width:100%,height:100%" id="yanshi4" />
      </div>
      <div class="swiper-button-next">
        <div
          style="position: absolute; width: 100%; height: 100%; z-index: 100000; pointer-events: auto"
          @click="login"
        ></div>
      </div>
      <div class="swiper-button-prev"></div>
    </div> -->
    <div class="container">
      <div class="top">
        <div class="header">
          <a href="/"> </a>
        </div>
        <div class="desc">
          <!--          Jeecg Boot 是中国最具影响力的 企业级 低代码平台-->
        </div>
      </div>

      <route-view></route-view>

      <div class="footer">
        <!--        <div class="links">-->
        <!--          <a href="http://doc.jeecg.com" target="_blank">帮助</a>-->
        <!--          <a href="https://github.com/zhangdaiscott/jeecg-boot" target="_blank">隐私</a>-->
        <!--          <a href="https://github.com/zhangdaiscott/jeecg-boot/blob/master/LICENSE" target="_blank">条款</a>-->
        <!--        </div>-->
        <div class="copyright">
          Copyright &copy; {{ new Date().getFullYear() }}
          <a  href="https://stargis.tigis.com.cn" target="_blank">星际空间（天津）科技发展有限公司</a> 出品
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import RouteView from '@/components/layouts/RouteView'
import { mixinDevice } from '@/utils/mixin.js'

export default {
  name: 'UserLayout',
  components: { RouteView },
  mixins: [mixinDevice],
  data() {
    return {}
  },
  created() {
    // var swiper = new Swiper('.mySwiper', {
    //   navigation: {
    //     nextEl: '.swiper-button-next',
    //     prevEl: '.swiper-button-prev',
    //   },
    // })
  },
  mounted() {
    let that = this
    document.body.classList.add('userLayout')
    // var swiper = new Swiper('.mySwiper', {
    //   navigation: {
    //     nextEl: '.swiper-button-next',
    //     prevEl: '.swiper-button-prev',
    //   },
    //   on: {
    //     slideChangeTransitionEnd: function () {
    //       // console.log('1')
    //       // console.log(this.realIndex)
    //       // if (this.realIndex == 8) {
    //       //   that.$bus.$emit('beginLogin')
    //       // }
    //     },
    //   },
    // })
  },
  beforeDestroy() {
    document.body.classList.remove('userLayout')
  },
  methods: {
    login(e) {
      try {
        if (document.getElementsByClassName('swiper-slide-active')[0].id == 'yanshi4') {
          this.$bus.$emit('beginLogin')
        }
        setTimeout(() => {
          var txt = document.getElementsByClassName('swiper-slide-active')[0].id
          console.log(txt)
        }, 200)
      } catch (error) {}
    },
  },
}
</script>

<style lang="less" scoped>
.swiper {
  position: absolute;
  width: 100%;
  height: 100%;
}

.swiper-slide {
  text-align: center;
  font-size: 18px;
  background: #fff;

  /* Center slide text vertically */
  display: -webkit-box;
  display: -ms-flexbox;
  display: -webkit-flex;
  display: flex;
  -webkit-box-pack: center;
  -ms-flex-pack: center;
  -webkit-justify-content: center;
  justify-content: center;
  -webkit-box-align: center;
  -ms-flex-align: center;
  -webkit-align-items: center;
  align-items: center;
}

.swiper-slide img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
#userLayout.user-layout-wrapper {
  height: 100%;
  background-image: url(~@/assets/bg.jpg); //0505演示
  background-size: 100%;
  &.mobile {
    .container {
      .main {
        max-width: 368px;
        width: 98%;
      }
    }
  }

  .container {
    width: 100%;
    min-height: 100%;
    background-size: 100%;
    padding: 110px 0 144px;
    position: relative;

    a {
      text-decoration: none;
    }

    .top {
      text-align: center;

      .header {
        height: 44px;
        line-height: 44px;

        .badge {
          position: absolute;
          display: inline-block;
          line-height: 1;
          vertical-align: middle;
          margin-left: -12px;
          margin-top: -10px;
          opacity: 0.8;
        }

        .logo {
          height: 44px;
          vertical-align: top;
          margin-right: 16px;
          border-style: none;
        }

        .title {
          font-size: 33px;
          color: rgba(0, 0, 0, 0.85);
          // font-family: 'Chinese Quote', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB',
          //   'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji',
          //   'Segoe UI Symbol';
          font-family: 'sy', 'Source Han Sans CN', 'Source Han Sans CN-Regular';
          font-weight: 600;
          position: relative;
          top: 2px;
        }
      }
      .desc {
        font-size: 14px;
        color: rgba(0, 0, 0, 0.45);
        margin-top: 12px;
        margin-bottom: 40px;
      }
    }

    .main {
      /*min-width: 260px;*/
      /*width: 368px;*/
      margin: 0 auto;
    }

    .footer {
      position: absolute;
      width: 100%;
      bottom: 0;
      padding: 0 16px;
      margin: 48px 0 24px;
      text-align: center;

      .links {
        margin-bottom: 8px;
        font-size: 14px;
        a {
          color: rgba(0, 0, 0, 0.45);
          transition: all 0.3s;
          &:not(:last-child) {
            margin-right: 40px;
          }
        }
      }
      .copyright {
        color: rgba(255, 255, 255, 0.8);
        font-size: 14px;
      }
    }
  }
}
</style>