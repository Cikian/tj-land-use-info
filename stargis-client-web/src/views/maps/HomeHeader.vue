<template>
  <div class="home-header">
    <div class="title">
      <div style="display: flex">
        <div class="c2">
          <div class="c2ex">
            <span id="titleFont" style="pointer-events: auto; user-select: none">{{
              sysName
            }}</span>
            <!-- <span id="titleFont" @click="changeTheme" style="pointer-events: auto; user-select: none">{{
              sysName
            }}</span> -->
          </div>
        </div>
      </div>
    </div>
    <div class="right-btn">
      <div class="fun-select">
        <!-- <img :src="require('@/assets/newdesign/header/u733.png')" alt=""                 :dropdownClassName="'home-dropdown'"    
             -->
        <!-- dropdownClassName 无法实现实时渲染，但可以通过全局获取的颜色类型设置不同样式 zbs-->
        <img :src="this.GlobalImgFolderSelect('newdesign/header/u733.png')" alt=""
          style="position: absolute;margin-left: 10px;" />
        <a-select ref="select" v-model:value="funValue" allowClear
          style="width: 160px; height: 36px; border-radius: 16px; box-sizing: border-box" @change="handleFunChange"
          :dropdownClassName="changeHomedropdown()" :dropdownStyle="dropdownStyle" :dropdownMenuStyle="menuStyle"
          placeholder="请选择功能" @dropdownVisibleChange="dropdownVisibleChange" id="headerFuncSelect"
          class="headerFuncSelect">
          <a-icon slot="suffixIcon" type="down" style="color: #fff" />
          <a-select-option class="headerSelectOption" :value="item.componentname" v-for="(item, index) in allFuncData"
            :key="index" v-if="!item.functionType || item.functionType == 23 || mode == item.functionType">{{
              item['name']
            }}</a-select-option>
        </a-select>
      </div>
      <div class="changeMode">

        <!-- 改为动态使用css内容更改样式，CSS移动到common_btn.less  -->
        <div class="changeMode3D" @click="changeMode(3)"
          :class="{ 'dimension-highlight': mode == 3, 'dimension-no-highlight': mode != 3 }">
          <img :src="mode == 3 ? this.GlobalImgFolderSelect('newdesign/header/u735.png') : this.GlobalImgFolderSelect('newdesign/header/u142.png')
            " alt="" />
          3D
        </div>
        <div class="changeMode2D" @click="changeMode(2)"
          :class="{ 'dimension-highlight': mode == 2, 'dimension-no-highlight': mode != 2 }">
          <img :src="mode == 2 ? this.GlobalImgFolderSelect('newdesign/header/u734.png') : this.GlobalImgFolderSelect('newdesign/header/u143.png')
            " alt="" />
          2D
        </div>
      </div>

      <img :src="this.GlobalImgFolderSelect('newdesign/header/u135.png')" alt=""
        style="float: left; margin-right: 8px; margin-left: 3px" />

      <span class="headerShortcut" style="width: 350px">
        <a-tooltip placement="topLeft" title="绕点飞行" arrow-point-at-center
          v-show="showFlyWithPoint == 23 || mode == showFlyWithPoint">
          <div class="circle-btn" :class="[isFly ? 'circle-btn-checked' : '']" @click="changeFly">
            <!-- <img :src="isFly ? require('@/assets/imgs/fly1.png') : require('@/assets/imgs/fly0.png')" /> -->
            <img
              :src="isFly ? this.GlobalImgFolderSelect('imgs/fly1.png') : this.GlobalImgFolderSelect('imgs/fly0.png')" />
          </div>
        </a-tooltip>
        <a-tooltip placement="topLeft" title="深度检测" arrow-point-at-center>
          <div class="circle-btn" :class="[isDeep ? 'circle-btn-checked' : '']" @click="changeDeep">
            <!-- <img :src="isDeep ? require('@/assets/imgs/deep1.png') : require('@/assets/imgs/deep0.png')" /> -->
            <img
              :src="isDeep ? this.GlobalImgFolderSelect('imgs/deep1.png') : this.GlobalImgFolderSelect('imgs/deep0.png')" />
          </div>
        </a-tooltip>
        <a-tooltip placement="topLeft" title="场景出图" arrow-point-at-center>
          <div class="circle-btn" :class="[isCamera ? 'circle-btn-checked' : '']" @click="changeCamera">
            <!-- <img :src="isCamera ? require('@/assets/imgs/camera1.png') : require('@/assets/imgs/camera0.png')" /> -->
            <img
              :src="isCamera ? this.GlobalImgFolderSelect('imgs/camera1.png') : this.GlobalImgFolderSelect('imgs/camera0.png')" />
          </div>
        </a-tooltip>
        <a-tooltip placement="topLeft" title="图上标绘" arrow-point-at-center>
          <div class="circle-btn" :class="[showMapMark ? 'circle-btn-checked' : '']" @click="changeShowMapMark"><img
              :src="showMapMark ? imgs.earthUrlChecked : imgs.earthUrl"></div>
        </a-tooltip>
        <a-tooltip placement="topLeft" title="拾取查询" arrow-point-at-center>
          <query-pick ref="queryPickClick"></query-pick>
        </a-tooltip>
        <a-tooltip placement="topLeft" title="视图书签" arrow-point-at-center>
          <bookmarks></bookmarks>
        </a-tooltip>
        <a-tooltip placement="topLeft" title="清除" arrow-point-at-center>
          <div class="circle-btn" @click="clearAllObject"><img :src="imgs.clearUrl" /></div>
        </a-tooltip>
      </span>

      <span class="headerShortcutPlace">
        <a-tooltip placement="topLeft" title="图层树" arrow-point-at-center>
          <div class="circle-btn layer-btn" @click="showLayerTree" :class="[showLayer ? 'circle-btn-checked' : '']">
            <img :src="showLayer ? imgs.layerUrlChecked : imgs.layerUrl" />
          </div>
        </a-tooltip>
        <span style="min-width: 51px">
          <a-tooltip placement="topLeft" title="方案树" arrow-point-at-center
            v-show="showSchemeTree == 23 || mode == showSchemeTree">
            <div class="circle-btn" @click="showApprovalTree" :class="[showApproval ? 'circle-btn-checked' : '']">
              <img :src="showApproval ? imgs.approvalUrlChecked : imgs.approvalUrl" />
            </div>
          </a-tooltip>
        </span>
        <span style="min-width: 51px">
          <a-tooltip placement="topLeft" title="图例" arrow-point-at-center
            v-show="showLegendShortCut && (showSchemeTree == 23 || mode == showSchemeTree)">
            <div class="circle-btn" @click="showLegendPop" :class="[showLegend ? 'circle-btn-checked' : '']">
              <img :src="showLegend ? imgs.legendUrlChecked : imgs.legendUrl" />
            </div>
          </a-tooltip>
        </span>
        
      </span>
      <!-- position: absolute; top: 7px -->
    </div>
    <div class="searchdiv">
      <a-select show-search label-in-value mode="combobox" :value="searchValue" placeholder="请输入地点搜索"
        style=" border-radius: 16px; max-width: 380px; min-width: 80px;box-sizing: border-box; flex: 1 auto"
        :filter-option="false" @search="fetchPlace" @change="handlePlaceChange" :options="crossPlaceOptions"
        @dropdownVisibleChange="dropdownVisibleChangePlace" id="headerPlaceSelect" :dropdownStyle="dropdownStylePlace"
        :dropdownMenuStyle="menuStylePlace" :dropdownClassName="'home-dropdownPlace'" allowClear
        v-show="showQueryPlaceName == 23 || mode == showQueryPlaceName">
      </a-select>
      <!-- <img src="@/assets/newdesign/header/search.png" alt="" -->
      <img :src="this.GlobalImgFolderSelect('newdesign/header/search.png')" alt=""
        style="z-index: 10000; width: 25px; height: 25px; float: right; margin-left: -38px" @click="onSearch()"
        v-show="showQueryPlaceName == 23 || mode == showQueryPlaceName" />
      <div class="search-list" v-show="searchList && searchList.length > 0">
        <div class="search-list-title">
          <img
            src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABEAAAAQCAYAAADwMZRfAAAABHNCSVQICAgIfAhkiAAAAF1JREFUOI1jYBgF6IARmSPw/bHDPwYmBwYGBgYmhn8HPnDKHmBgYGDg+/60npGRsYGBgYHh////DZ84pRtxGkKuQRiGkGMQVkNINYgJlyGkANp4h+KAJTeKRwEmAAD+82kK0fJTpwAAAABJRU5ErkJggg=="
            alt="" v-show="showLocateResult" />
          搜索结果
          <span class="openLocateResult" style="font-size: 12px; color: #5c9d86; margin-left: 100px"
            v-show="!showLocateResult" @click="openLocateResult">点击展开</span>
          <span class="search-list-result">共有 <span style="color: orange">{{ searchList.length }}</span> 个结果
          </span>
        </div>
        <div class="search-list-body" v-show="showLocateResult">
          <a-list size="small" :data-source="searchList">
            <a-list-item slot="renderItem" slot-scope="item" @click="drawSelectedOne(item, item.id)" :style="{
              cursor: 'pointer',
              background: selectLocate == item ? 'rgba(34, 129, 105, 0.7)' : '',
              paddingLeft: '10px',
              borderRadius: '8px',
            }">
              <img :src="this.GlobalImgFolderSelect(`newdesign/header/u84.png`)" alt="" class="locate" />
              {{ item.feature[0].Value }}
            </a-list-item>
          </a-list>
        </div>
        <div style="border-top: 1px rgb(33, 74, 69) solid; text-align: center; color: #5c9d86; margin-top: 10px"
          v-show="showLocateResult">
          <span class="openLocateResult" style="margin: 8px auto; font-size: 12px" @click="openLocateResult">点击收起</span>
        </div>
      </div>
    </div>

    <div class="user-info">
      <a-dropdown>
        <div class="circle-btn" style="margin-right: 8px"><img :src="this.GlobalImgFolderSelect('imgs/user.png')" />
        </div>
        <a-menu slot="overlay" style="background: rgb(0, 0, 0, 0.6)">
          <a-menu-item>
            <a target="_blank" rel="" @click="logout" style="color: #fff">退出登录</a>
          </a-menu-item>
        </a-menu>
      </a-dropdown>
      <div class="user-name" style="z-index: 1000; pointer-events: initial; cursor: pointer" @click="changeUE">
        {{ userName }}
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions } from 'vuex'
import Vue from 'vue'
import { USER_NAME, USER_INFO } from '@/store/mutation-types'
import * as turf from '@turf/turf'
import debounce from 'lodash/debounce'
import DynamicDivLabel from '@/utils/wyw/index'
// window.currentMapMode = 3
// import u735 from (StyleImgFolder+'newdesign/header/u735.png')
// import u142 from (StyleImgFolder+'newdesign/header/u142.png')


export default {
  name: 'HomeHeader',
  props: {},
  components: {

  },
  data() {
    // this.singleFetchId = 0
    // this.fetchPlace = debounce(this.fetchPlace, 1000) //这两行为地名定位所需
    return {
      showLegendShortCut: false,
      showMapMark: false,
      showLegend: false,
      funValue: undefined,
      imgs: {
        earthUrl: this.GlobalImgFolderSelect('imgs/earthType0.png'),
        earthUrlChecked: this.GlobalImgFolderSelect('imgs/earthType1.png'),
        northUrl: this.GlobalImgFolderSelect('imgs/north0.png'),
        pickUrl: this.GlobalImgFolderSelect('imgs/pick0.png'),
        clearUrl: this.GlobalImgFolderSelect('imgs/clear0.png'),
        layerUrl: this.GlobalImgFolderSelect('imgs/layer0.png'),
        layerUrlChecked: this.GlobalImgFolderSelect('imgs/layer1.png'),
        legendUrl: this.GlobalImgFolderSelect('imgs/legend0.png'),
        legendUrlChecked: this.GlobalImgFolderSelect('imgs/legend1.png'),
        approvalUrlChecked: this.GlobalImgFolderSelect('imgs/project1.png'),
        approvalUrl: this.GlobalImgFolderSelect('imgs/project0.png'),
        ueUrlChecked: this.GlobalImgFolderSelect('imgs/ue1.png'),
        ueUrl: this.GlobalImgFolderSelect('imgs/ue0.png'),

      },
      dropdownStyle: {
        // background: 'rgba(4, 79, 88, 0.8)',
        background: 'rgba(0,0,0,0)',
        borderRadius: '10px', // 圆角
        // marginTop: '1px',
        // overflow: 'visible',
        // backgroundClip: 'content-box',
        // padding: '15px',
      },
      menuStyle: {
        // color: 'rgba(0, 0, 0, 1)',
        // overflow: 'visible',
        maxHeight: 'fit-content',
        background: 'rgb(0,0,0,.6)',
        borderRadius: '10px', // 圆角
        padding: '5px 5px',
      },
      showLayer: false,
      showApproval: false,
      is2D: false,
      isFly: false,
      isDeep: true,
      fontLength: '',
      sysName: '',
      allFuncData: [],
      userName: '',
      baseConf: [
        {
          apporder: '0.0',
          children: [
            {
              apporder: '0.0',
              children: [],
              componentname: 'MeasureArea',
              dataurl: '',
              hasapp: null,
              icon: 'map-btns/MeasureArea',
              id: '4028eea181a47ce70181a4978816000f',
              name: '面积量测',
              nodetype: 'node',
              oldid: '4028eea181a47ce70181a48f27c30004',
              parentid: '4028eea181a47ce70181a4978814000d',
              serverurl: '',
            },
            {
              apporder: '1.0',
              children: [],
              componentname: 'MeasureLine',
              dataurl: '',
              hasapp: null,
              icon: 'map-btns/MeasureLine',
              id: '4028eea181a47ce70181a4978815000e',
              name: '直线测量',
              nodetype: 'node',
              oldid: '4028eea181a47ce70181a48e453c0003',
              parentid: '4028eea181a47ce70181a4978814000d',
              serverurl: '',
            },
            {
              apporder: '2.0',
              children: [],
              componentname: 'MeasureLine',
              dataurl: '',
              hasapp: null,
              icon: 'map-btns/SplitScreen',
              id: '4028eea181a47ce70181a4978815000g',
              name: '多屏比选',
              nodetype: 'node',
              oldid: '4028eea181a47ce70181a48e453c0005',
              parentid: '4028eea181a47ce70181a4978814000d',
              serverurl: '',
            },
          ],
          componentname: 'Common',
          dataurl: '',
          hasapp: '1',
          icon: '',
          id: '4028eea181a47ce70181a4978814000d',
          name: '常用功能',
          nodetype: 'group',
          oldid: '4028eea181a47ce70181a488b44b0000',
          parentid: '-1',
          serverurl: '',
        },
        {
          apporder: '1.0',
          children: [
            {
              apporder: '0.0',
              children: [],
              componentname: 'QueryStatistic',
              dataurl: '',
              hasapp: null,
              icon: 'map-btns/QueryStatistic',
              id: '4028eea181a47ce70181a497912a0011',
              name: '综合统计',
              nodetype: 'node',
              oldid: '4028eea181a47ce70181a49456b20006',
              parentid: '4028eea181a47ce70181a497912a0010',
              serverurl: '',
            },
            {
              apporder: '0.0',
              children: [],
              componentname: 'QuerySpatial',
              dataurl: '',
              hasapp: null,
              icon: 'map-btns/QuerySpatial',
              id: '4028eea181a47ce70181a497912a0012',
              name: '空间查询',
              nodetype: 'node',
              oldid: '4028eea181a47ce70181a494af5d0007',
              parentid: '4028eea181a47ce70181a497912a0010',
              serverurl: '',
            },
            {
              apporder: '0.0',
              children: [],
              componentname: 'QueryAttribute',
              dataurl: '',
              hasapp: null,
              icon: 'map-btns/QueryAttribute',
              id: '4028eea181a47ce70181a497912b0013',
              name: '属性查询',
              nodetype: 'node',
              oldid: '4028eea181a47ce70181a49506440008',
              parentid: '4028eea181a47ce70181a497912a0010',
              serverurl: '',
            },
          ],
          componentname: 'QueryStatistics',
          dataurl: '',
          hasapp: '1',
          icon: '',
          id: '4028eea181a47ce70181a497912a0010',
          name: '空间查询统计',
          nodetype: 'group',
          oldid: '4028eea181a47ce70181a48a0b310001',
          parentid: '-1',
          serverurl: '',
        },
        {
          apporder: '2.0',
          children: [
            {
              apporder: '0.0',
              children: [],
              componentname: 'PipeQuery',
              dataurl: '',
              hasapp: null,
              icon: 'map-btns/PipeQuery',
              id: '4028eea181a47ce70181a49799b50015',
              name: '管线查询',
              nodetype: 'node',
              oldid: '4028eea181a47ce70181a4959c6b0009',
              parentid: '4028eea181a47ce70181a49799b40014',
              serverurl: '',
            },
            {
              apporder: '0.0',
              children: [],
              componentname: 'PipeContionQuery',
              dataurl: '',
              hasapp: null,
              icon: 'map-btns/PipeContionQuery',
              id: '4028eea181a47ce70181a49799b50016',
              name: '条件查询',
              nodetype: 'node',
              oldid: '4028eea181a47ce70181a497308a000c',
              parentid: '4028eea181a47ce70181a49799b40014',
              serverurl: '',
            },
          ],
          componentname: 'PipeManager',
          dataurl: '',
          hasapp: '1',
          icon: '',
          id: '4028eea181a47ce70181a49799b40014',
          name: '地下空间',
          nodetype: 'group',
          oldid: '4028eea181a47ce70181a48a52a70002',
          parentid: '-1',
          serverurl: '',
        },
      ],
      mode: 3,
      ShortCut: [], //快捷方式栏
      showQueryPlaceName: 23,
      showFlyWithPoint: 23,
      showCompass: 23,
      showSchemeTree: 23,
      //以下为地名定位所需变量

      serverIp: window._CONFIG.VUE_DATA_SERVER_URL,
      // serverUrl: '/stargis/rest/tasks/submit',
      serverUrl: '/stargis/rest/tasks/test/query',
      placefetching: false,
      crossPlaceOptions: [],
      dbDataParam: null,
      searchList: [],
      searchValue: '',
      allParams: [],
      allDBData: [],
      allFields: [],
      dataCount: 200,
      dropdownStylePlace: {
        background: 'rgba(0,0,0,0)',
        borderRadius: '10px', // 圆角
      },
      menuStylePlace: {
        maxHeight: '300px',
        background: 'rgb(0,0,0,.6)',
        borderRadius: '10px', // 圆角
        padding: '5px 5px',
      },
      showLocateResult: true,
      selectLocate: undefined,
      locateSymbol: 0,
      //场景出图
      isCamera: false,
      theme: 1,
      //UE
      showUE: false,
    }
  },
  created() {

    this.userName = Vue.ls.get(USER_NAME)
    let info = Vue.ls.get(USER_INFO)
    this.sysName = info.sysName || window._CONFIG.PROJECT_NAME
    document.title = this.sysName
    setTimeout(() => {
      this.getFunConfig()
    }, 500)

    this.$bus.$on('cancelHeaderChecked', (val) => {
      this.showLayer = val
    })

    this.$bus.$on('cancelApprovalHeaderChecked', (val) => {
      this.showApproval = val
    })
    this.$bus.$on('bookChangeFun', (val) => {
      this.funValue = val
      this.$bus.$emit('changeFun', val)
    })
    window.headPlacelabel = []
    this.$bus.$on('cancelScenePlottingHeadChecked', (val) => {
      this.isCamera = false
    })
  },
  mounted() {
    setTimeout(() => {
      this.changeTitleLength()
    }, 500)
  },
  methods: {
    changeShowMapMark() {
      this.showMapMark = !this.showMapMark
      this.$bus.$emit('showMapPlottingPop', this.showMapMark)
    },
    handleFunChange(value, e) {
      this.$bus.$emit('changeFun', value)
    },
    showLayerTree() {
      this.showLayer = !this.showLayer
      this.$bus.$emit('showLayerTreePop', this.showLayer)
    },
    showApprovalTree() {
      this.showApproval = !this.showApproval
      this.$bus.$emit('showManagerApprovalPop', this.showApproval)
    },
    showLegendPop() {
      this.showLegend = !this.showLegend;
      this.$bus.$emit('showLegendPop', this.showLegend)
    },
    // changeMode() {
    //   this.is2D = !this.is2D
    //   viewer.scene.mode = this.is2D ? Cesium.SceneMode.COLUMBUS_VIEW : Cesium.SceneMode.SCENE3D
    // },

    changeFly() {
      this.isFly = !this.isFly
      let camera = viewer.camera
      if (this.isFly) {
        this.$message.info('请在地图上绘制飞行的中心点')
        camera.flyCircleLoop = true // 相机绕点旋转开启循环模式
        let center = new Cesium.Cartesian3(0, 0, 0)
        if (!this.handlerPoint) {
          this.handlerPoint = new Cesium.DrawHandler(viewer, Cesium.DrawMode.Point)
        }
        this.handlerPoint.drawEvt.addEventListener(function (result) {
          center = result.object.position
          camera.flyCircle(center) // 相机绕中心点旋转
        })
        this.handlerPoint.activate()
      } else {
        camera.flyCircleLoop = false // 相机绕点旋转开启循环模式
        camera.stopFlyCircle() // 停止相机绕中心点旋转
        this.handlerPoint.clear()
        this.handlerPoint.deactivate()
      }
    },
    clear2dHighlight() {
      // 清除之前的高亮
      if (window.leafletHighlightLayer) {
        window.leafletmap.removeLayer(window.leafletHighlightLayer);
        window.leafletHighlightLayer = null;
      }
    },
    clearAllObject() {
      if (window.currentMapMode == 2) {
        this.clear2dHighlight();
        window.currentClickLayer = {}
      }
      else {
        this.$bus.$emit('headClearAll')
        let selLayer = window.viewer.scene._layers.getSelectedLayer()
        selLayer && selLayer.releaseSelection()
        if (Cesium.defined(window.screenSpaceEventHandler)) {
          window.screenSpaceEventHandler.removeInputAction(Cesium.ScreenSpaceEventType.LEFT_CLICK)
          window.screenSpaceEventHandler = null
        }
        if (Cesium.defined(window.viewer.entities.getById(588009))) {
          window.viewer.entities.removeById(588009) // 移除上个圆。
        }
        if (window.handlerDraw) {
          window.handlerDraw.clear()
          window.handlerDraw = null
        }
        try {
          if (Cesium.defined(window.viewer.entities.getById(123412345))) {
            window.viewer.entities.removeById(123412345)
          }
        } catch (e) { }
        try {
          let dataSource1 = window.localDataManager.getLayer(21156)
          if (Cesium.defined(dataSource1)) {
            dataSource1.show = false
          }

          let layer = window.localDataManager.getLayer('pickLayer123');
          if (layer !== undefined && layer !== null) {
            viewer.dataSources.remove(layer);
          }
        } catch (e) { }
        try {
          let dataSource = window.localDataManager.getLayer(212)
          if (Cesium.defined(dataSource)) {
            dataSource.show = false
          }
        } catch (e) { }
        try {
          for (let key in window.hiddenObjId) {
            let layer = viewer.scene.layers.find(key)
            layer.setOnlyObjsVisible(window.hiddenObjId[key], true)
          }
          this.setLayerSelected(false)
          window.hiddenObjId = {}
        } catch (e) { }
      }

    },
    setLayerSelected(checked) {
      let layers = viewer.scene.layers.layerQueue
      layers.map((item) => {
        item.selectEnabled = checked
      })
    },
    toNorth() {
      viewer.scene.sun.show = !viewer.scene.sun.show
      // viewer.camera.setView({
      //   destination: viewer.camera.position,
      //   orientation: {
      //     heading: Cesium.Math.toRadians(0),
      //     pitch: viewer.camera.pitch,
      //   },
      // })
    },
    changeDeep() {
      this.isDeep = !this.isDeep
      viewer.scene.globe.depthTestAgainstTerrain = this.isDeep
    },
    ...mapActions(['Logout']),
    logout() {
      // this.sysName = '星际高渲染驾驶舱大屏系统';
      // document.title = this.sysName
      // window.viewer.destroy();

      const that = this

      this.$confirm({
        title: '提示',
        content: '真的要注销登录吗 ?',
        onOk() {
          return that
            .Logout({})
            .then(() => {
              // update-begin author:scott date:20211223 for:【JTC-198】退出登录体验不好
              //that.$router.push({ path: '/user/login' });
              window.location.reload()
              // update-end author:scott date:20211223 for:【JTC-198】退出登录体验不好
            })
            .catch((err) => {
              that.$message.error({
                title: '错误',
                description: err.message,
              })
            })
        },
        onCancel() { },
      })
    },
    changeUE() {
      this.showUE = true
      document.getElementById('home').innerHTML =
        '<object type="text/html" data=' + window._CONFIG.UE4_URL + ' width="100%" height="100%"></object>'
    },
    getFunConfig() {
      let obj = this.$store.getters.sysConfig
      for (let key in obj) {
        if (key != 'ShortCut') {
          this.allFuncData.push(obj[key])
        } else {
          this.ShortCut = obj[key].children
          for (let i = 0; i < this.ShortCut.length; i++) {
            if (this.ShortCut[i].componentname == 'QueryPlaceName') {
              if (this.ShortCut[i].functionType) {
                this.showQueryPlaceName = this.ShortCut[i].functionType * 1
              }
              // this.showQueryPlaceName = this.ShortCut[i].functionType * 1
              this.singleFetchId = 0
              this.fetchPlace = debounce(this.fetchPlace, 1000)
              this.prepareQueryPlaceName(this.ShortCut[i])
            } else if (this.ShortCut[i].componentname == 'FlyWithPoint') {
              this.showFlyWithPoint = this.ShortCut[i].functionType * 1
            } else if (this.ShortCut[i].componentname == 'Compass') {
              this.showCompass = this.ShortCut[i].functionType * 1
            } else if (this.ShortCut[i].componentname == 'SchemeTree') {
              this.showSchemeTree = this.ShortCut[i].functionType * 1
            }
          }
        }
        // this.allFuncData.push(obj[key])
      }
      // console.log(obj)
    },
    changeTitleLength() {
      let temp1 = document.getElementById('titleFont')
      this.fontLength =
        (temp1.style.width || temp1.clientWidth || temp1.offsetWidth || temp1.scrollWidth || 350) + 40 + 'px'
    },

    /////常用功能下拉框边框颜色
    dropdownVisibleChange(val) {
      switch (val) {
        case true:
          if (window.SelectColorType == "blue") {///蓝色
            document.getElementById('headerFuncSelect').style.border = '1px solid #08CCFF'
            document.getElementById('headerFuncSelect').style.boxShadow = '0px 0px 6px rgb(13 111 213 / 100%)'
            break;
          }
          else {///绿色
            document.getElementById('headerFuncSelect').style.border = '1px solid #0ff8e6'
            document.getElementById('headerFuncSelect').style.boxShadow = '0px 0px 6px rgb(15 248 230 / 100%)'
            // document.getElementById('headerFuncSelect').style.height = '36px'
            break;
          }

        case false:
          document.getElementById('headerFuncSelect').style.border = '1px solid rgba(0,0,0,0)'
          document.getElementById('headerFuncSelect').style.boxShadow = ''
          // document.getElementById('headerFuncSelect').style.height = '32px'
          break;

        default:
          break;
      }
      // console.log(this.crossPlaceOptions)
    },
    changeMode(val) {
      // console.log(this.allFuncData, this.funValue)
      //切换3D,2D
      this.$bus.$emit('changeFun', undefined)
      this.mode = val
      for (let i = 0; i < this.allFuncData.length; i++) {
        if (this.allFuncData[i].componentname == this.funValue) {
          if (
            !this.allFuncData[i].functionType ||
            this.allFuncData[i].functionType == '23' ||
            this.allFuncData[i].functionType == this.mode
          ) {
          } else {
            this.funValue = undefined
            // this.$bus.$emit('changeFun', undefined)
          }
        }
      }

      let that = this
      setTimeout(function () {
        that.$bus.$emit('changeFun', that.funValue)
        if (window._CONFIG.LEAFLET_OPEN) {
          that.$bus.$emit('change3D2Dmodes', val)
          window.currentMapMode = val  //设置全局变量 当前地图模式为三维地图/二维
          if (val == 3) { that.viewTo3d() } else { that.viewTo2d() }
          that.contrlMapEvent(val) //控制二三维地图联动事件
        }
      }, 500)

      if (!(this.showSchemeTree == 23 || this.mode == this.showSchemeTree)) {
        this.$bus.$emit('showManagerApprovalPop', false)
      }
      this.searchList = []
      this.selectLocate = undefined
      this.removeEntitiesById('placeLocate123')
      for (let i = 0; i < window.headPlacelabel.length; i++) {
        window.headPlacelabel[i].obj.windowClose()
      }
      window.headPlacelabel.length = 0
      this.locateSymbol = 0
    },
    viewTo3d() {
      const to3dDom = document.getElementById("home")
      const to2dDom = document.getElementById("home2d")
      // console.log("切换到3D",to3dDom,to2dDom);
      to2dDom.style.display = "none"
      to3dDom.style.display = "block"
      to3dDom.style.left = "0"
      to3dDom.style.width = "100%"
    },
    viewTo2d() {
      // //打印当前Cesium的中心点和高度
      var scene = window.viewer.scene;
      var camera = scene.camera;
      var cameraPosition = camera.position;
      var cartographic = Cesium.Cartographic.fromCartesian(cameraPosition);
      var longitude = Cesium.Math.toDegrees(cartographic.longitude);
      var latitude = Cesium.Math.toDegrees(cartographic.latitude);
      var height = cartographic.height;
      console.log('当前Cesium中心点坐标：', longitude, latitude,height);

      const to3dDom = document.getElementById("home")
      const to2dDom = document.getElementById("home2d")
      // console.log("切换到2D",to3dDom,to2dDom);
      to3dDom.style.display = "none"
      to2dDom.style.display = "block"
      to2dDom.style.width = "100%"
      if (window.leafletmap) {
        window.leafletmap.invalidateSize(false)
        if (this.Jump2d <= 0) { //只在初次切换时跳转，防止多次切换时反复跳转影响体验
          setTimeout(() => {
            this.doJumpTo2d(longitude, latitude, height)
            this.Jump2d ++
          }, 500);
        }

      }
    },
    doJumpTo2d(lon, lat, height) {
      // 优先使用视野矩形计算 zoom（更准确）
      let zoom = null
      try {
        const rect = window.viewer.camera.computeViewRectangle()
        if (rect) {
          const west = Cesium.Math.toDegrees(rect.west)
          const east = Cesium.Math.toDegrees(rect.east)
          const north = Cesium.Math.toDegrees(rect.north)
          const south = Cesium.Math.toDegrees(rect.south)
          const lonSpan = Math.abs(east - west) || 0.000001
          const latCenter = (north + south) / 2
          // 赤道周长（米）
          const earthCircumference = 40075016.686
          // 经度跨度对应米数（按中心纬度缩放）
          const spanMeters = (lonSpan / 360) * earthCircumference * Math.cos((latCenter * Math.PI) / 180)
          const mapWidth = (to2dDom && to2dDom.clientWidth) || window.innerWidth || 1024
          const metersPerPixel = spanMeters / mapWidth
          const initialResolution = earthCircumference / 256 // meters per pixel at zoom 0
          zoom = Math.log2(initialResolution / metersPerPixel)
          zoom = Math.round(Math.max(0, Math.min(20, zoom)))
        }
      } catch (e) {
        zoom = null
      }
      // 回退：用高度估算（改进版）
      if (zoom === null) {
        console.warn('计算 Leaflet 视图 zoom 失败，改用高度估算')
        zoom = this.calculateLeafletZoomFromCesiumHeight(height)
      }
      // 最终设置 Leaflet 视图
      if (typeof zoom === 'number' && !isNaN(zoom)) {
        try {
          // console.log('跳转到 Leaflet 视图：', lat, lon, zoom)
          window.leafletmap.setView([lat, lon], zoom)
        } catch (e) {
          // 有些自定义 map wrapper 可能使用不同方法
          console.warn('设置 Leaflet 视图失败', e)
        }
      }
    },
    //二三维地图联动，切换后启动相关事件，实现地图的联动
    contrlMapEvent(val) {
      if (val == 3) {
        //切换到三维
        this.unregMapMoveEvent();
        this.regSceneMoveEvent();
      } else if (val == 2) {
        this.unregSceneMoveEvent();
        this.regMapMoveEvent();
      }
    },
    regSceneMoveEvent() {
      let that = this;
      window.SceneMoveEventHandler = new Cesium.ScreenSpaceEventHandler(window.viewer.scene.canvas)
      window.SceneMoveEventHandler.setInputAction(function (move) {
        that.SceneToMap(move);
      }, Cesium.ScreenSpaceEventType.MOUSE_MOVE);
      window.SceneMoveEventHandler.setInputAction(function (wheel) {
        that.SceneToMap(wheel);
      }, Cesium.ScreenSpaceEventType.WHEEL);
    },
    unregSceneMoveEvent() {
      if (!window.SceneMoveEventHandler) {
        return;
      }
      // console.log('Cesium mousemove and wheel event 关闭');
      window.SceneMoveEventHandler.removeInputAction(Cesium.ScreenSpaceEventType.MOUSE_MOVE)
      window.SceneMoveEventHandler.removeInputAction(Cesium.ScreenSpaceEventType.WHEEL)
    },
    regMapMoveEvent() {
      if (window.leafletmap) {
        window.leafletmap.on('mousemove', this.MaptoScene);
        // console.log('Leaflet mousemove event 开启');
      } else {
        // console.error('Leaflet map instance not found');
      }
    },
    unregMapMoveEvent() {
      if (window.leafletmap) {
        window.leafletmap.off('mousemove', this.MaptoScene);
        // console.log('Leaflet mousemove event 关闭');
      } else {
        // console.error('Leaflet map instance not found');
      }
    },
    SceneToMap(e) {
      var scene = window.viewer.scene;
      var camera = scene.camera;
      var cameraPosition = camera.position;
      var cartographic = Cesium.Cartographic.fromCartesian(cameraPosition);
      var longitude = Cesium.Math.toDegrees(cartographic.longitude);
      var latitude = Cesium.Math.toDegrees(cartographic.latitude);


      var rectangle = window.viewer.camera.computeViewRectangle();
      // 弧度转为经纬度，west为左（西）侧边界的经度，以下类推
      var west = (rectangle.west / Math.PI) * 180;
      var north = (rectangle.north / Math.PI) * 180;
      var east = (rectangle.east / Math.PI) * 180;
      var south = (rectangle.south / Math.PI) * 180;
      if (window.leafletmap) {
        // console.log('## north,south,east,west', north, south, east, west);
        window.leafletmap.fitBounds([
          [north, east],
          [south, west]
        ], false);
        // window.leafletmap.invalidateSize(false)
      }

    },
    MaptoScene() {
      //获取当前地图范围
      var bounds = window.leafletmap.getBounds();
      //根据给定的地图范围计算场景的高度
      var altitude = this.calculateAltitudeFromBounds(bounds);
      //获取地图中心点
      var center = window.leafletmap.getCenter();

      //设置场景相机
      window.viewer.scene.camera.setView({
        destination: new Cesium.Cartesian3.fromDegrees(
          center.lng,
          center.lat,
          altitude
        ),
      });
    },
    /// 根据给定的地图范围计算场景的高度
    calculateAltitudeFromBounds(bounds) {
      var _PI = 3.1415926;
      var _earthRadius = 6378137;
      var altitude = _earthRadius;
      var boundsWidth = bounds._northEast.lng - bounds._southWest.lng;
      //bounds._northEast.lng - bounds._southWest.lat
      if (boundsWidth >= 120) {
        altitude = (_earthRadius * boundsWidth) / 60 - _earthRadius;
      } else if (boundsWidth != 0) {
        var angle1 = (boundsWidth / 360) * _PI;
        var height = Math.sin(angle1) * _earthRadius;
        var a = height / Math.tan(angle1);
        var b = height / Math.tan(_PI / 6);
        altitude = a + b - _earthRadius;
      }
      return altitude;
    },
    //根据 Cesium 高度计算 Leaflet 缩放等级
    calculateLeafletZoomFromCesiumHeight(height) {
      try {
        if (!height || height <= 0 || !isFinite(height)) return 3
        const earthCircumference = 40075016.686
        const tileSize = 256
        const initialResolution = earthCircumference / tileSize // meters/pixel at zoom 0
        // 估算可视宽度对应地面米数：用相机高度乘以一个经验因子，避免直接用高度导致极端值
        // 经验因子：假设视野可见宽度约为 2 * height * tan(fov/2)，取 fov ~ 45deg => factor ≈ 2*tan(22.5°) ≈ 0.83
        const fovFactor = 0.83
        const approxSpanMeters = height * fovFactor
        const mapWidth = window.innerWidth || 1024
        const metersPerPixel = approxSpanMeters / mapWidth
        let zoom = Math.log2(initialResolution / metersPerPixel)
        zoom = Math.round(Math.max(0, Math.min(20, zoom)))
        return zoom
      } catch (e) {
        return 3
      }
    },
    ////标题栏点击修改样式  暂用
    changeTheme() {
      // this.theme == 3 && (this.theme = 0)
      this.theme == 2 && (this.theme = 0)
      this.theme++

      document.getElementById('app').className = 'system-theme' + this.theme
      if (this.theme == 2) { window.SelectColorType = "blue" }
      else { window.SelectColorType = "green" }

    },
    ////可选功能栏下拉框内颜色
    changeHomedropdown() {
      if (window.SelectColorType == "blue") {
        return 'home-dropdown2'
      }
      else {
        return 'home-dropdown'
      }
    },

    //地名定位
    prepareQueryPlaceName(QueryPlaceNameParam) {

      // console.log(QueryPlaceNameParam.dataurl);
      // alert(1)
      if (QueryPlaceNameParam.dataurl) {
        this.dbDataParam = eval('(' + QueryPlaceNameParam.dataurl + ')')
        if (this.dbDataParam) {
          let allKeys = Object.keys(this.dbDataParam)
          allKeys.forEach((item) => {
            if (item.indexOf('dbData') >= 0) {
              let sub = item.replace('dbData', '')
              if (this.dbDataParam['fields' + sub]) {
                this.allDBData.push(this.dbDataParam[item])
                this.allFields.push(this.dbDataParam['fields' + sub])
              }
            }
          })
        }
      }
      if (QueryPlaceNameParam.serverurl) {
        let p = eval('(' + QueryPlaceNameParam.serverurl + ')')
        p && (this.serverUrl = p.dataUrl)
      }
      this.getDataSource()
    },
    handlePlaceChange(value, option) {
      if (!value) {
        this.searchList = []
        this.showLocateResult = true
        this.searchValue = ''
        this.selectLocate = undefined
        this.removeEntitiesById('placeLocate123')
        for (let i = 0; i < window.headPlacelabel.length; i++) {
          window.headPlacelabel[i].obj.windowClose()
        }
        window.headPlacelabel.length = 0
        return
      }
      if (value.label.includes('$%&!')) {
        value.label = value.label.substring(0, value.label.lastIndexOf('$%&!'))
        value.key = value.key.substring(0, value.key.lastIndexOf('$%&!'))
        Object.assign(this, {
          searchValue: value,
          fetching: false,
        })
        this.onSearch()
      } else {
        Object.assign(this, {
          searchValue: value,
          fetching: false,
        })
      }
      // alert(value.label)
      // Object.assign(this, {
      //   searchValue: value,
      //   fetching: false,
      // });
      // this.searchValue=value
      // this.fetching=false
      // alert(this.searchValue)
      // this.onSearch()
    },
    fetchPlace(value) {
      this.searchList = []
      this.singleFetchId += 1
      const fetchId = this.singleFetchId
      this.data = []
      this.placefetching = true
      this.crossPlaceOptions = []
      let indexs = 0
      this.allParams.forEach(async (item, index) => {
        let params = JSON.parse(JSON.stringify(item))
        if (params.DatasourceID) {
          let obj = this.getTaskWYW(item)
          obj.fuzzyValues = [{ "field": item.DatasourceID.ReturnField[0].Field, "value": this.searchValue.label }]
          let json = this.getTaskParamsWYW(obj)
          // console.log(this.serverIp + this.serverUrl);
          // return
          let searchData = await postJson(this.serverIp + this.serverUrl, json)
          if (searchData.result.features) {
            let data = []
            searchData.result.features.map(item => {
              item.properties.name = item.properties[this.allFields[index]]
              if (item && item.properties && item.properties.name != '') {
                data.push({
                  label: item.properties.name,
                  title: item.properties.name,
                  value: item.properties.name + '$%&!' + Math.random() * 10000,
                  geometry: item.geometry ? item.geometry : null,
                  key: indexs,
                  paramsIndex: index
                })
                indexs++
              }
            })
            data && data.length > 0 && this.crossPlaceOptions.push(...data)
          }
          this.placefetching = false
          // params.DatasourceID.RowsLimit = 20
          // params.TrakerName = 'QueryVagueValue'
          // params.DatasourceID.VagueValue = value
          // let json = this.getSubmitParams(params)
          // fetch(this.serverIp + this.serverUrl, {
          //   method: 'POST',
          //   headers: {
          //     'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
          //   },
          //   body: json,
          // })
          //   .then((response) => response.json())
          //   .then((body) => {
          //     if (fetchId !== this.singleFetchId) {
          //       // for fetch callback order
          //       return
          //     }
          //     if (body.ReturnField) {
          //       let data = []
          //       body.ReturnField.map((item) => {
          //         if (item.feature[0] && item.feature[0].Value != '') {
          //           data.push({
          //             label: item.feature[0].Value,
          //             title: item.feature[0].Value,
          //             value: item.feature[0].Value + '$%&!' + Math.random() * 10000,
          //             geometry: item.geometry ? item.geometry : null,
          //             key: indexs,
          //             paramsIndex: index,
          //           })
          //           indexs++
          //         }
          //       })
          //       data && data.length > 0 && this.crossPlaceOptions.push(...data)
          //     }
          //     this.placefetching = false
          //   })
        } else if (item.Featurcclassid) {
          item.where = encodeURI(`${item.fieldName} like '%${value}%'`)
          let para = new Cesium.Query(item)



          // let identify = new Cesium.QueryTask(item.Featurcclassid + '/0')
          let BMurl = item.Featurcclassid + '/0' + "/query"
          if (window.secretkey) {
            BMurl = BMurl + "?secretkey=" + window.secretkey
          }
          let identify = new Cesium.QueryTask(BMurl)




          let promise = identify.execute(para)
          if (promise) {
            promise.then((datas) => {
              let data = []
              if (datas && datas.features) {
                let geometryType = datas.geometryType
                datas.features.map((item3, index3) => {
                  if (index3 < 100) {
                    let feature = this.getArcgisGeomtry(item3, geometryType)
                    data.push({
                      label: item3.attributes[item.fieldName],
                      value: item3.attributes[item.fieldName] + '$%&!' + 'arcgis' + indexs,
                      geometry: { GeoJson: feature ? feature.geometry : null, originGeom: item3.geometry },
                      key: indexs,
                      paramsIndex: index,
                      title: item3.attributes[item.fieldName],
                    })
                    indexs++
                  }
                })
                data && data.length > 0 && this.crossPlaceOptions.push(...data)
              }
            })
          }
        }
      })
    },
    getArcgisGeomtry(item, geometryType) {
      let features = null
      if (geometryType === 'esriGeometryPoint') {
        features = turf.point([item.geometry.x, item.geometry.y])
      } else if (geometryType === 'esriGeometryPolyline') {
        let nums = this.dimension_array_es6(item.geometry.paths)
        features = nums === 3 ? turf.multiLineString(item.geometry.paths) : turf.lineString(item.geometry.paths)
      } else if (geometryType === 'esriGeometryPolygon') {
        let nums = this.dimension_array_es6(item.geometry.rings)
        features = nums === 4 ? turf.multiPolygon(item.geometry.rings) : turf.polygon(item.geometry.rings)
      }
      return features
    },
    async onSearch() {
      this.removeEntitiesById('placeLocate123')
      for (let i = 0; i < window.headPlacelabel.length; i++) {
        window.headPlacelabel[i].obj.windowClose()
      }
      window.headPlacelabel.length = 0
      this.selectLocate = undefined
      this.locateSymbol = 0

      if (!this.searchValue.label) {
        this.$message.warn('请输入搜索信息')
        return
      }
      if (!this.allParams || this.allParams.length === 0) {
        this.$message.warn('获取配置参数失败，请检查功能配置')
        return
      }
      this.searchList = []
      this.allParams.forEach(async (item) => {
        if (item.DatasourceID) {
          let obj = this.getTaskWYW(item)
          obj.fuzzyValues = [{ "field": item.DatasourceID.ReturnField[0].Field, "value": this.searchValue.label }]
          // console.log(obj);
          let json = this.getTaskParamsWYW(obj)
          // console.log(json);
          let searchData = await postJson(this.serverIp + this.serverUrl, json)
          // console.log(searchData);
          this.successStargisServer(searchData.result, item.DatasourceID.ReturnField[0].Field)
          if (item == this.allParams[this.allParams.length - 1]) {
            setTimeout(() => {
              if (this.searchList.length == 1) {
                this.drawSelectedOne(this.searchList[0], this.searchList[0].id)
                this.searchList = []
              }
            }, 500);
          }
          // // item.DatasourceID.VagueValue = this.searchValue.label
          // // let json = this.getSubmitParams(item)
          // let results = await axios({ url: this.serverIp + this.serverUrl, method: 'post', data: json })
          // if (results.data) {
          //   for (let i = 0; i < results.data.ReturnField.length; i++) {
          //     //添加id以便选中
          //     if (!results.data.ReturnField[i].id) {
          //       results.data.ReturnField[i].id = 'headLocate' + this.locateSymbol
          //       this.locateSymbol += 1
          //       this.drawSelected(results.data.ReturnField[i])
          //     }
          //   }
          //   this.searchList.push(...results.data.ReturnField)
          //   // for (let i = 0; i < this.searchList.length; i++) {
          //   //   if (!this.searchList[i].id) {
          //   //     this.searchList[i].id = 'headLocate' + this.locateSymbol
          //   //     this.locateSymbol += 1
          //   //   }
          //   // }
          //   if (item == this.allParams[this.allParams.length - 1]) {
          //     // console.log(this.searchList, this.searchList.length)
          //     if (this.searchList.length == 1) {
          //       this.drawSelectedOne(this.searchList[0], this.searchList[0].id)
          //       this.searchList = []
          //     }
          //   }
          // }
        } else if (item.Featurcclassid) {
          if (item.Featurcclassid.includes('stargis/rest') && item.Featurcclassid.includes('FeatureServer')) {
            item.where = `"${item.fieldName}" like '%${this.searchValue.label}%'`
            //优化20230423wei{
            item.useFieldAlias = false
            //}
            item.maxReturnCount = this.dataCount
            let para = new Cesium.SpatialFilter(item)


            // let identify = new Cesium.StargisQuery(item.Featurcclassid)
            let BMurl = item.Featurcclassid
            if (window.secretkey) {
              BMurl = BMurl + "?secretkey=" + window.secretkey
            }
            let identify = new Cesium.StargisQuery(BMurl)




            let promise = identify.execute(para)
            if (promise) {
              promise.then((data) => {
                this.successStargisServer(data, item.fieldName)
                // for (let i = 0; i < this.searchList.length; i++) {
                //   if (!this.searchList[i].id) {
                //     this.searchList[i].id = 'headLocate' + this.locateSymbol
                //     this.locateSymbol += 1
                //   }
                // }
                if (item == this.allParams[this.allParams.length - 1]) {
                  setTimeout(() => {
                    if (this.searchList.length == 1) {
                      this.drawSelectedOne(this.searchList[0], this.searchList[0].id)
                      this.searchList = []
                    }
                  }, 500);

                }
              })
            }
          } else {
            item.where = encodeURI(`"${item.fieldName}" like '%${this.searchValue.label}%'`)
            let para = new Cesium.Query(item)

            // let identify = new Cesium.QueryTask(item.Featurcclassid + '/0')
            let BMurl = item.Featurcclassid + '/0' + "/query"
            if (window.secretkey) {
              BMurl = BMurl + "?secretkey=" + window.secretkey
            }
            let identify = new Cesium.QueryTask(BMurl)


            let promise = identify.execute(para)
            if (promise) {
              promise.then((data) => {
                this.successArcgisServer(data, item.fieldName)
                // for (let i = 0; i < this.searchList.length; i++) {
                //   if (!this.searchList[i].id) {
                //     this.searchList[i].id = 'headLocate' + this.locateSymbol
                //     this.locateSymbol += 1
                //   }
                // }
                if (item == this.allParams[this.allParams.length - 1]) {
                  setTimeout(() => {
                    if (this.searchList.length == 1) {
                      this.drawSelectedOne(this.searchList[0], this.searchList[0].id)
                      this.searchList = []
                    }
                  }, 500);
                }
              })
            }
          }
          // if (this.searchList.length == 1) {
          //   this.drawSelectedOne(this.searchList[0], this.searchList[0].id)
          //   this.searchList = []
          // }
        }
      })
    },
    getTaskWYW(item) {
      console.log(item);
      let result = item.DatasourceID
      let obj = {}
      obj.subtask = 'query'
      let DatasourceType = 'mysql'
      DatasourceType = this.getDataBaseType(result.DBConectionString.DatasourceType)
      obj.datasource = {
        type: DatasourceType,
        database: result.DBConectionString.Database,
        instance: result.DBConectionString.Instance,
        host: result.DBConectionString.Server,
        port: parseInt(result.DBConectionString.Port),
        user: result.DBConectionString.User,
        pwd: result.DBConectionString.Pwd,
      }
      obj.dataset = result.DatasetName ? result.DatasetName : '';
      obj.featureclass = result.TableName ? result.TableName : '';
      obj.resultOffset = 0
      obj.maxReturnCount = 2000
      obj.resultRecordCount = 20
      obj.returnGeometry = true;
      obj.inSR = { "EPSG": 4326 };
      obj.outSR = { "EPSG": 4326 };
      return obj
    },
    getTaskParamsWYW(params) {
      let conn = { task: 'test', synchronize: true, inprocess: false, param: params }
      return JSON.stringify(conn)
    },
    successStargisServer(data, field) {
      if (!data || data.features.length === 0) {
        return
      }
      data.features.map((item, index) => {
        let obj = { feature: [{ Value: item.properties[field] }], geometry: { GeoJson: item ? item.geometry : null } }
        //添加id，以便选中
        obj.id = 'headLocate' + this.locateSymbol
        this.locateSymbol += 1
        this.drawSelected(obj)
        this.searchList.push(obj)
      })
    },
    successArcgisServer(data, field) {
      let { features, geometryType } = data
      if (!features || features.length === 0) {
        return
      }
      features.forEach((item, index) => {
        let obj = {}
        if (index < this.dataCount) {
          let features = null
          if (geometryType === 'esriGeometryPoint') {
            features = turf.point([item.geometry.x, item.geometry.y])
          } else if (geometryType === 'esriGeometryPolyline') {
            let nums = this.dimension_array_es6(item.geometry.paths)
            features = nums === 3 ? turf.multiLineString(item.geometry.paths) : turf.lineString(item.geometry.paths)
          } else if (geometryType === 'esriGeometryPolygon') {
            let nums = this.dimension_array_es6(item.geometry.rings)
            features = nums === 4 ? turf.multiPolygon(item.geometry.rings) : turf.polygon(item.geometry.rings)
          }
          obj = {
            feature: [{ Value: item.attributes[field] }],
            geometry: { GeoJson: features ? features.geometry : null },
          }
          //添加id，以便选中
          obj.id = 'headLocate' + this.locateSymbol
          this.locateSymbol += 1
          this.drawSelected(obj)
          this.searchList.push(obj)
        }
      })
    },
    drawSelectedOne(data, id) {
      this.removeEntitiesById('placeLocate123')
      this.$bus.$emit('refreshPlacePop', true)
      // for (let i = 0; i < window.headPlacelabel.length; i++) {
      //   window.headPlacelabel[i].obj.windowClose()
      // }
      // window.headPlacelabel.length = 0
      this.selectLocate = data //控制表格变色
      // for (let i = 0; i < this.searchList.length; i++) {
      //   if (this.searchList[i].id != id) {
      //     this.drawSelected(this.searchList[i])
      //   } else {
      //     this.drawSelected(this.searchList[i], id)
      //   }
      // }
      this.drawSelected(data, id)
    },
    drawSelected(data, Aid) {
      let { GeoJson } = data.geometry
      let sourcePromise = Cesium.GeoJsonDataSource.load(GeoJson)
      let bbox = turf.bbox(GeoJson)
      let type = null
      let center = null
      let lonLat = null
      if (GeoJson.type === 'MultiPolygon' || GeoJson.type === 'Polygon') {
        center = turf.center(GeoJson)
        center && center.geometry && (lonLat = center.geometry.coordinates)
      } else if (GeoJson.type === 'MultiLineString') {
        center = GeoJson.coordinates.flat()
        if (center.length === 2) {
          center = turf.center(GeoJson)
          center && center.geometry && (lonLat = center.geometry.coordinates)
        } else {
          lonLat = center[Math.floor(center.length / 2 - 1)]
        }
      } else if (GeoJson.type === 'LineString') {
        center = GeoJson.coordinates
        if (center.length === 2) {
          center = turf.center(GeoJson)
          center && center.geometry && (lonLat = center.geometry.coordinates)
        } else {
          center && (lonLat = center[Math.floor(center.length / 2 - 1)])
        }
      }

      sourcePromise.then((dataSource) => {
        let layer = viewer.entities.getById('placeLocate123')
        if (!layer) {
          layer = viewer.entities.add(
            new Cesium.Entity({
              id: 'placeLocate123',
            })
          )
        } else {
          // this.removeEntitiesById('placeLocate123')
        }
        dataSource.entities.values.map((item) => {
          if (item.polygon) {
            window.viewer.entities.add({
              parent: layer,
              polygon: {
                hierarchy: item.polygon.hierarchy,
                material: Cesium.Color.AQUA,
                clampToGround: true,
                perPositionHeight: false,
              },
              depthTestEnabled: false,
              clampToS3M: true,
            })
          } else if (item.polyline) {
            window.viewer.entities.add({
              parent: layer,
              polyline: {
                positions: item.polyline.positions,
                material: Cesium.Color.AQUA,
                clampToGround: true,
                width: 5,
              },
              depthTestEnabled: false,
              clampToS3M: true,
            })
          } else {
            type = 'point'
            // let imgPath
            // if (this.mode == 2) {
            //   imgPath = require(`@/assets/newdesign/header/2Dlocate.png`)
            //   if (Aid != undefined && data.id == Aid) {
            //     imgPath = require(`@/assets/newdesign/header/2Dlocate2.png`)
            //   }
            // } else {
            //   imgPath = require(`@/assets/newdesign/header/3Dlocate.png`)
            //   if (Aid != undefined && data.id == Aid) {
            //     imgPath = require(`@/assets/newdesign/header/3Dlocate2.png`)
            //   }
            // }

            // window.viewer.entities.add({
            //   parent: layer,
            //   position: item.position,
            //   id: data.id,
            //   label: {
            //     text: data.feature[0].Value,
            //     font: '20px sans-serif',
            //     showBackground: true,
            //     horizontalOrigin: Cesium.HorizontalOrigin.CENTER,
            //     verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
            //     // pixelOffset: new Cesium.Cartesian2(0.0, -32 - 2),
            //     // pixelOffset: new Cesium.Cartesian2(0.0, -121 - 2),
            //     pixelOffset: new Cesium.Cartesian2(0.0, -51 - 2),
            //     disableDepthTestDistance: 700000.0,
            //     distanceDisplayCondition: new Cesium.DistanceDisplayCondition(10.0, 2000.0),
            //   },
            //   billboard: {
            //     // image: require(`@/assets/newdesign/header/3Dlocate.png`),
            //     image: imgPath,
            //     show: true,
            //     // verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
            //     // width: 27,
            //     // height: 32,
            //     verticalOrigin: Cesium.VerticalOrigin.CENTER,
            //     disableDepthTestDistance: 700000.0,
            //   },
            // })
            if (Aid == undefined) {
              let conn = {}
              if (!window.headPlacelabel[0]) {
                conn.obj = new DynamicDivLabel(
                  window.viewer,
                  item.position._value,
                  { id: data.id, mode: this.mode },
                  data.feature[0].Value
                )
                window.headPlacelabel.push(conn)
              } else {
                conn.obj = new DynamicDivLabel(
                  window.viewer,
                  item.position._value,
                  { id: data.id, mode: this.mode },
                  data.feature[0].Value
                )
                window.headPlacelabel.push(conn)
              }
            } else {
              this.$bus.$emit(Aid, true)
            }
          }
        })
        if (lonLat) {
          window.viewer.entities.add({
            parent: layer,
            position: Cesium.Cartesian3.fromDegrees(lonLat[0], lonLat[1], 1),
            label: {
              text: data.feature[0].Value,
              font: '20px sans-serif',
              showBackground: true,
              horizontalOrigin: Cesium.HorizontalOrigin.CENTER,
              verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
              disableDepthTestDistance: 700000.0,
            },
          })
        }
        if (Aid != undefined) {
          // let locateEntity = viewer.entities.getById(data.id)
          // console.log(locateEntity)
          bbox &&
            viewer.camera.setView({
              destination: Cesium.Rectangle.fromDegrees(
                bbox[0] - 0.005,
                bbox[1] - 0.005,
                bbox[2] + 0.005,
                bbox[3] + 0.005
              ),
            })
        }
        // bbox &&
        //   viewer.camera.setView({
        //     destination: Cesium.Rectangle.fromDegrees(bbox[0] - 0.02, bbox[1] - 0.02, bbox[2] + 0.02, bbox[3] + 0.02),
        //   })
        // bbox && viewer.camera.setView({
        //   destination : Cesium.Rectangle.fromDegrees(type === 'point' ? bbox[0] - 0.001 : bbox[0], type === 'point' ? bbox[1] - 0.001 : bbox[1], type === 'point' ? bbox[2] + 0.001 : bbox[2], type === 'point' ? bbox[3] + 0.001 : bbox[3])
        // });
      })
    },
    removeEntitiesById(id) {
      let parent = viewer.entities.getById(id)
      if (!parent) {
        return
      }
      parent._children.map((item) => {
        viewer.entities.remove(item)
      })
      parent._children = []
      this.locateSymbol = 0
    },
    async getDataSource() {
      if (!this.dbDataParam || this.allDBData.length === 0 || this.allDBData.length !== this.allFields.length) {
        // this.$message.warn('获取地名定位配置参数失败，请检查功能配置')
        console.log('获取地名定位配置参数失败，请检查功能配置')
        return
      }
      this.allDBData.forEach((item, index) => {
        let str = item.split('_')
        let dbName = ''
        let url = ''
        if (str.length > 2) {
          dbName = str[str.length - 2]
          url = '/app/basicinfo/metaJson'
        } else {
          dbName = item
          url = '/app/basicinfo/metaJsonByClassId'
        }
        getAction(url, { id: dbName, flag: true }).then((res) => {
          let result = res.result
          if (result.Featurcclassid) {
            let conn = {}
            conn.f = 'json'
            conn.spatialReference = { wkid: '4326' }
            conn.returnGeometry = true
            conn.layerOption = 'all'
            conn.Featurcclassid = result.Featurcclassid
            conn.fieldName = this.allFields[index]
            this.allParams.push(conn)
          } else {
            let obj = {}
            obj.TrakerName = 'QueryVagueValue'
            let DatasourceIDObj = {}
            DatasourceIDObj.DBConectionString = result.DBConectionString
            DatasourceIDObj.DBConectionString.DatasourceType = parseInt(result.DBConectionString.DatasourceType)
            DatasourceIDObj.DBConectionString.Port = parseInt(result.DBConectionString.Port)
            DatasourceIDObj.DatasetName = result.Datasets[0] ? result.Datasets[0].DatasetName : ''
            DatasourceIDObj.TableName =
              result.Datasets[0] && result.Datasets[0].FeatureClasses[0]
                ? result.Datasets[0].FeatureClasses[0].TableName
                : ''
            if (this.allFields[index]) {
              DatasourceIDObj.VagueField = [this.allFields[index]]
              DatasourceIDObj.ReturnField = [{ Field: this.allFields[index] }]
            }
            DatasourceIDObj.BeginRow = 0
            DatasourceIDObj.RowsLimit = 500
            obj.DatasourceID = DatasourceIDObj
            this.allParams.push(obj)
          }
        })
      })
    },
    getSubmitParams(params) {
      let conn = { task: 'test', synchronize: true, inprocess: false, param: JSON.stringify(params) }
      let json = ''
      for (let i in conn) {
        if (json !== '') {
          json = json + '&'
        }
        json = json + i + '=' + conn[i]
      }
      return json
    },
    dimension_array_es6(arr) {
      // 判断数组的维数
      let _this = this
      if (arr instanceof Array) {
        return Math.max(
          ...arr.map((e) => {
            return 1 + parseInt(_this.dimension_array_es6(e))
          })
        )
      } else {
        return 0
      }
    },
    dropdownVisibleChangePlace(val) {
      // console.log(val)
      switch (val) {
        case true:
          if (window.SelectColorType == "blue") {///蓝色
            this.searchList = []
            this.showLocateResult = true
            document.getElementById('headerPlaceSelect').style.border = '1px solid #08CCFF'
            document.getElementById('headerPlaceSelect').style.boxShadow = '0px 0px 6px rgb(13 111 213 / 100%)'
            // document.getElementById('headerFuncSelect').style.height = '36px'
            break
          }
          else {///绿色
            this.searchList = []
            this.showLocateResult = true
            document.getElementById('headerPlaceSelect').style.border = '1px solid #0ff8e6'
            document.getElementById('headerPlaceSelect').style.boxShadow = '0px 0px 6px rgb(15 248 230 / 100%)'
            // document.getElementById('headerFuncSelect').style.height = '36px'
            break
          }


        case false:
          document.getElementById('headerPlaceSelect').style.border = '1px solid rgba(0,0,0,0)'
          document.getElementById('headerPlaceSelect').style.boxShadow = ''
          // document.getElementById('headerFuncSelect').style.height = '32px'
          break

        default:
          break
      }
    },
    openLocateResult() {
      this.showLocateResult = !this.showLocateResult
    },
    //场景出图
    changeCamera() {
      this.isCamera = !this.isCamera
      this.$bus.$emit('showScenePlottingPop', this.isCamera)
    },
  },
}
</script>

<style lang="less">
.ant-message {
  color: rgba(0, 0, 0, 0.65);
  top: 80px !important;
}

.home-header {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 70px;
  display: flex;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(3, 25, 42, 1), rgba(3, 25, 42, 0));
  z-index: 1000;
  pointer-events: none;
  justify-content: space-between;

  .title {
    display: inline-flex;
    // width: 30%;
    width: fit-content;
    margin-left: 14px;
    font-size: 36px;
    color: #f0f0f0;
    flex-shrink: 0;

    span {
      line-height: 60px;
      font-family: pmzdbtt;
      // font-family: 'sy', 'Source Han Sans CN', 'Source Han Sans CN-Regular';
      // font-weight: bold;
    }
  }

  .right-btn {
    padding-left: 150px;
    height: 100%;
    // width: 740px;
    width: fit-content;
    float: right;
    display: inline-flex;
    align-items: center;
    justify-content: center;

    .ant-select-selection {
      border-radius: 16px;
      color: #ffffff;
      background-color: rgba(250, 250, 250, 0.3);
      // border: 1px solid rgba(250, 250, 250, 0);
      border: 1px solid rgba(167, 175, 172, 0.3);
    }

    .ant-select-arrow-icon {
      color: rgba(250, 250, 250, 0.3);
    }

    .ant-select-arrow .ant-select-arrow-icon svg path {
      color: rgb(250, 250, 250, 0.3);
    }

    .changeMode {
      pointer-events: initial;
      float: left;
      width: 120px;
      height: 36px;
      background-color: rgba(250, 250, 250, 0.3);
      border-radius: 18px;
      cursor: pointer;

      .changeMode3D {
        width: 50%;
        float: left;
        height: 36px;
        line-height: 36px;
        text-align: center;
        color: #ffffff;
        border-radius: 18px;

        img {
          margin-top: -2px;
          margin-right: 2px;
        }

        border: 1px solid rgba(0, 0, 0, 0);
      }


      .changeMode2D {
        width: 50%;
        float: left;
        height: 36px;
        line-height: 36px;
        text-align: center;
        color: #ffffff;
        border-radius: 18px;

        img {
          margin-top: -2px;
          margin-right: 2px;
        }

        border: 1px solid rgba(0, 0, 0, 0);
      }
    }



    .headerShortcut {
      float: left;
      display: inline-flex;

      pointer-events: initial;

      .ant-select-selection--single {
        height: 36px;
        padding-left: 30px;
        padding-top: 1px;
      }

      .ant-select-selection__placeholder,
      .ant-select-search__field__placeholder {
        line-height: 18px;
      }

      .headerFuncSelect {
        border: 1px solid rgba(0, 0, 0, 0);
      }

      .ant-select-selection__rendered {
        line-height: 33px;
        height: 36px;
      }
    }

    .headerShortcutPlace {
      float: left;
      display: inline-flex;

      pointer-events: initial;

      .ant-select-clear-icon {
        // margin-right: 130px !important;
        position: relative;
        right: 40px;
        width: 30px;
        height: 30px;

        img {
          width: 30px;
          height: 30px;
        }
      }

      .ant-select-selection--single {
        height: 36px;
        padding-left: 2px;
        padding-top: 1px;
      }

      .ant-select-selection__placeholder,
      .ant-select-search__field__placeholder {
        line-height: 18px;
      }

      .headerFuncSelect {
        border: 1px solid rgba(0, 0, 0, 0);
      }

      .ant-select-selection__rendered {
        line-height: 33px;
        height: 36px;
      }

      .search-list {
        width: 430px;
        pointer-events: initial;
        position: absolute;
        // margin-left: 147px;
        margin-left: 200px;
        background: rgba(0, 0, 0, 0.6);
        border-radius: 10px;
        margin-top: 40px;

        .search-list-title {
          width: calc(100% - 10px);
          height: 30px;
          line-height: 30px;
          color: rgb(255, 255, 255);
          // background: rgb(15, 79, 76);
          margin: 6px 5px 6px 5px;

          img {
            margin-bottom: 4px;
          }

          .search-list-result {
            float: right;
            padding-right: 8px;
          }
        }

        .search-list-body {
          padding: 4px 4px;
          max-height: 300px;
          overflow-y: auto;

          .locate {
            margin-right: 6px;
            margin-bottom: 2px;
          }
        }

        .openLocateResult:hover {
          color: #ffffff !important;
          cursor: pointer;
        }
      }


      .ant-list {
        color: #ffffff;
      }

      .ant-list-split .ant-list-item {
        border-bottom: 1px solid rgba(0, 0, 0, 0.2);
      }
    }
  }

  .searchdiv {
    display: inline-flex;
    pointer-events: initial;
    height: 100%;
    // width: 740px;
    width: fit-content;
    float: right;
    align-items: center;
    justify-content: center;
    flex: 1 auto;

    .ant-select-selection {
      border-radius: 16px;
      color: #ffffff;
      background-color: rgba(250, 250, 250, 0.3);
      // border: 1px solid rgba(250, 250, 250, 0);
      border: 1px solid rgba(167, 175, 172, 0.3);
    }

    .ant-select-arrow-icon {
      color: rgba(250, 250, 250, 0.3);
    }

    .ant-select-clear-icon {
      // margin-right: 130px !important;
      position: relative;
      right: 40px;
      width: 30px;
      height: 30px;

      img {
        width: 30px;
        height: 30px;
      }
    }

    .ant-select-selection--single {
      height: 36px;
      padding-left: 10px;
      padding-top: 1px;
    }

    .ant-select-selection__placeholder,
    .ant-select-search__field__placeholder {
      line-height: 18px;
    }

    .headerFuncSelect {
      border: 1px solid rgba(0, 0, 0, 0);
    }

    .ant-select-selection__rendered {
      line-height: 33px;
      height: 36px;
    }



    .ant-list {
      color: #ffffff;
    }

    .ant-list-split .ant-list-item {
      border-bottom: 1px solid rgba(0, 0, 0, 0.2);
    }
  }

  .search-list {
    pointer-events: initial;
    position: fixed;
    background: rgba(0, 0, 0, 0.6);
    border-radius: 10px;
    // margin-top: 420px;
    top: 57px;
    width: fit-content;
    min-width: 375px;
    margin-left: 20px;

    .search-list-title {
      width: calc(100% - 10px);
      height: 30px;
      line-height: 30px;
      color: rgb(255, 255, 255);
      // background: rgb(15, 79, 76);
      margin: 6px 5px 6px 5px;

      img {
        margin-bottom: 4px;
      }

      .search-list-result {
        float: right;
        padding-right: 8px;
      }
    }

    .search-list-body {
      padding: 4px 4px;
      max-height: 300px;
      overflow-y: auto;

      .locate {
        margin-right: 6px;
        margin-bottom: 2px;
      }
    }

    .openLocateResult:hover {
      color: #ffffff !important;
      cursor: pointer;
    }

    .ant-list {
      color: #ffffff;
    }

    .ant-list-split .ant-list-item {
      border-bottom: 1px solid rgba(0, 0, 0, 0.2);
    }
  }

  .circle-btn {
    cursor: pointer;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    border: 1px solid rgba(250, 250, 250, 0.5);
    background-color: rgba(250, 250, 250, 0.3);
    display: flex;
    justify-content: center;
    align-items: center;
    margin-right: 15px;
    pointer-events: initial;

    img {
      width: 60%;
      height: 60%;
    }
  }

  ////移动至common_btn.less 分类进行替换
  // .circle-btn-checked {
  //   box-shadow: 0px 0px 10px rgba(10, 255, 218, 0.7);
  //   border: 1px solid rgb(10, 255, 218);
  //   // box-shadow: 0px 0px 10px rgba(10, 255, 218, 0.7);
  //   // border:  1px solid rgb(13, 188, 255);

  // }

  .layer-btn {
    margin-left: 25px;
    margin-right: 16px;
  }



  .user-info {
    display: inline-flex;
    width: 120px;
    justify-content: center;
    align-items: center;
    margin-left: 10px;

    .user-name {
      color: #ffffff;
      font-size: 14px;
      line-height: 36px;
    }
  }

  .fun-select {
    float: left;
    margin-left: 20px;
    margin-right: 30px;
    display: flex;
    pointer-events: initial;
    justify-content: flex-start;
    align-items: center;

    .ant-select-selection--single {
      height: 36px;
      padding-left: 20px;
      padding-top: 1px;
    }

    .ant-select-selection__placeholder,
    .ant-select-search__field__placeholder {
      line-height: 18px;
    }

    .headerFuncSelect {
      border: 1px solid rgba(0, 0, 0, 0);
    }

    .ant-select-selection__rendered {
      line-height: 33px;
      height: 36px;
    }
  }
}

// .ant-dropdown-menu-item-active {
//   background-color: rgba(34, 129, 105, 0.7) !important;
//   border-radius: 10px;
// }

.home-dropdown,
.home-dropdownPlace {
  .ant-select-dropdown-menu-item {
    color: #ffffff;
  }

  .ant-select-dropdown-menu-item:hover:not(.ant-select-dropdown-menu-item-disabled),
  .ant-select-dropdown-menu-item-selected,
  .ant-select-dropdown-menu-item-active:not(.ant-select-dropdown-menu-item-disabled) {
    background-color: rgba(34, 129, 105, 0.7);
    border-radius: 8px; // 圆角
  }

  .headerSelectOption {
    margin-top: 2px !important;
  }
}

//设置蓝色样式，移到common_pop中会有部分样式丢失  暂置
.home-dropdown2,
.home-dropdownPlace2 {
  .ant-select-dropdown-menu-item {
    color: #ffffff;
  }

  .ant-select-dropdown-menu-item:hover:not(.ant-select-dropdown-menu-item-disabled),
  .ant-select-dropdown-menu-item-selected,
  .ant-select-dropdown-menu-item-active:not(.ant-select-dropdown-menu-item-disabled) {
    // background-color: rgba(34, 129, 105, 0.7);
    background-color: rgba(16, 137, 236, 0.7);
    border-radius: 8px; // 圆角
  }

  .headerSelectOption {
    margin-top: 2px !important;
  }
}

//下拉框选项前图标
.home-dropdownPlace {
  .ant-select-dropdown-menu-item::before {
    content: url('../../assets/style_green/newdesign/header/u124.png');
    margin-right: 16px;
    vertical-align: middle;
  }
}


.c2 {
  display: flex;
  /*width: 368px;*/
  height: 90px;
}

////移至common_pop统一管理
// .c2::before {
//   display: block;
//   background: url('~@/assets/imgs/title-background.png') no-repeat left center;
//   width: 13px;
//   height: 93px;
//   background-size: 376px;
//   content: ' ';
// }

// .c2::after {
//   display: block;
//   background: url('~@/assets/imgs/title-background.png') no-repeat right center;
//   width: 13px;
//   height: 92px;
//   background-size: 396px;
//   content: ' ';
// }


// .c2 .c2ex {
//   flex: 1;
//   background: url('~@/assets/imgs/title-background.png') repeat-x center center;
//   background-size: 5000px 19px;
//   color: white;
//   line-height: 60px;
//   font-family: ysbth;
//   text-align: center;
// }</style>