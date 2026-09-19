<template>
  <div class="mylables2 pop2" v-show="isShow">
    <div v-show="isShow" style="/* border: 2px solid rgba(20, 204, 235, 1); */ border-radius: 15px; overflow: visible">
      <div class="smallpop" :style="{ position: 'relative', top: mode == 3 ? '-25px' : '' }">
        <div class="pop-body2" style="min-height: 10px; text-align: center; font-size: 14px; overflow: visible">
          <div
            class="neon"
            :style="{
              marginTop: mode == 3 ? '-30px' : '',
              color: selected ? '#0ff8e6' : '#ce723b',
            }"
          >
            <!-- border: selected ? '2px #33666f solid' : '2px #b88048 solid', -->
            <span style="">{{ width }}</span>
          </div>
          <img :src="imgSrc" alt="" :class="imghClass" />
          <!-- <div>
            <span style="width: 3px; height: 30px; background: red"></span>
          </div> -->
          <br />
          <span
            style="display: inline-block; height: 50px; border-left: 3px solid #fff; width: 0px; margin-top: -100px"
            v-if="mode == 3"
          ></span>
          <br />
          <!-- <div>
            <span
              style="display: inline-block; height: 1px; border-top: 3px solid #fff; width: 3px; margin--top: -150px"
              v-if="mode == 3"
            ></span>
          </div> -->

          <!-- <div><img :src="imgSrc" alt="" style="width: 102px; height: 108px" /></div> -->
        </div>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  props: ['labelCoo', 'width'],
  data() {
    return {
      isShow: true,
      imgSrc: require(`@/assets/newdesign/header/3d.png`),
      imghClass: 'class3d',
      mode: 3,
      selected: false,
    }
  },
  created() {
    if (this.labelCoo.mode == 3) {
      this.imgSrc = require(`@/assets/newdesign/header/3d.png`)
      this.imghClass = 'class3d'
      this.mode = 3
    } else {
      this.imgSrc = require(`@/assets/newdesign/header/2d.png`)
      this.imghClass = 'class2d'
      this.mode = 2
    }
    this.$bus.$on(this.labelCoo.id, () => {
      if (this.labelCoo.mode == 3) {
        this.imgSrc = require(`@/assets/newdesign/header/3d2.png`)
        this.selected = true
      } else {
        this.imgSrc = require(`@/assets/newdesign/header/2d2.png`)
        this.selected = true
      }
    })
    this.$bus.$on('refreshPlacePop', () => {
      this.selected = false
      if (this.labelCoo.mode == 3) {
        this.imgSrc = require(`@/assets/newdesign/header/3d.png`)
      } else {
        this.imgSrc = require(`@/assets/newdesign/header/2d.png`)
      }
    })
  },
  methods: {
    closePop() {
      this.isShow = false
    },
  },
}
</script>

<style scoped lang="less">
.class3d {
  animation: class2d 5s linear infinite;
  overflow: hidden;
  width: 51px;
  height: 51px;
}
@keyframes class2d {
  100% {
    transform: rotate(360deg);
  }
}
.class2d {
  width: 51px;
  height: 65px;
}
// .class3d {
//   animation: class3d 2s linear infinite;
//   overflow: hidden;
// }
// @keyframes class3d {
//   0% {
//     transform: translate(0px, 0px);
//   }
//   50% {
//     transform: translate(0px, 5px); /* 可配置跳动方向 */
//   }
//   100% {
//     transform: translate(0px, 0px);
//   }
// }
.neon {
  // border: 2px #b88048 solid;
  // border: 2px #15e590 solid;
  // padding: 0px 5px;
  text-shadow: -1px 1px 0 #000, 1px 1px 0 #000, 1px -1px 0 #000, -1px -1px 0 #000;
  // text-shadow: -1px 1px 0 #eee, 1px 1px 0 #eee, 1px -1px 0 #eee, -1px -1px 0 #eee;
  font-weight: 700;
  // color: #cce7f8;
  // color: #000 !important;
  // color: rgb(255, 162, 59);
  // color: rgb(226, 231, 233);
  font-size: 20px;
  // -webkit-animation: shining 0.5s alternate infinite;
  // animation: shining 0.5s alternate infinite;
  border-radius: 5px;
}

@keyframes shining {
  from {
    // text-shadow: 0 0 10px lightblue, 0 0 20px lightblue, 0 0 30px lightblue, 0 0 40px skyblue, 0 0 50px skyblue,
    //   0 0 60px skyblue;
    text-shadow: 0 0 10px lightblue;
    // text-shadow: 0 0 5px #000;
  }

  to {
    // text-shadow: 0 0 5px lightblue, 0 0 10px lightblue, 0 0 15px lightblue, 0 0 20px skyblue, 0 0 25px skyblue,
    //   0 0 30px skyblue;
    text-shadow: 0 0 10px lightblue, 0 0 15px lightblue, 0 0 20px lightblue, 0 0 25px skyblue;
    // text-shadow: 0 0 5px #000, 0 0 10px #000, 0 0 15px #000, 0 0 20px #000;
  }
}

.mylables2 {
  // background: red;
  // .smallpop {
  //   z-index: 1000000;
  // }
  // z-index: 10;
  min-height: 0;
  min-width: 0;
  border: 0px;
  width: fit-content;
  height: fit-content;
  position: absolute;
  left: 100px;
  bottom: 0px;
  pointer-events: none;
  // cursor: pointer;
  white-space: pre;

  .pop-body2 {
    padding: 10px;
    color: rgb(229, 253, 255);
  }
}
.pop2 {
  pointer-events: none;
  border: 0px;
  background-clip: padding-box, border-box;
  background-origin: padding-box, border-box;
  opacity: 1;
  .pop-header {
    height: 46px;
    display: inline-flex;
    justify-content: center;
    align-items: center;
    .pop-title {
      display: inline-flex;
      justify-content: center;
      align-items: center;
      color: #ffffff;
      font-family: ysbth;
      // font-family: 'sy', 'Source Han Sans CN', 'Source Han Sans CN-Regular';
      font-size: 23px;
      -webkit-background-clip: text;

      .pop-title-logo {
        width: 30px;
        height: 20px;
        background-image: url('~@/assets/imgs/logo.png');
        background-repeat: no-repeat;
        background-position: center;
        background-size: 80%;
        margin-right: 0px;
      }
    }
    .pop-close {
      width: 26px;
      height: 26px;
      background-image: url('~@/assets/imgs/close.png');
      position: absolute;
      right: 10px;
      cursor: pointer;
    }
  }
  .pop-body {
    min-height: 354px;
    overflow-y: auto;
    color: #ffffff;
  }
  .pop-close {
    // pointer-events: auto;
    width: 18px;
    height: 18px;
    background-image: url('~@/assets/imgs/close.png');
    background-size: cover;
    position: absolute;
    right: 25px;
    top: 15px;
    margin-left: 10px;
    cursor: pointer;
  }
}
</style>