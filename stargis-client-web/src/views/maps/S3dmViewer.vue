<template>
  <div id="home" ref="stargisMap"></div>
</template>

<script>
import html2canvas from 'html2canvas'
import { axios } from '../../utils/request'

export default {
  name: 'S3dmViewer',
  components: {},
  data() {
    return {}
  },
  mounted() {
    this.initMap()
    this.$bus.$on('downloadMapImage', (val) => {
      this.toImage(val)
    })
  },
  methods: {
    initMap() {
      //
      // document.getElementById("home").innerHTML = '<object type="text/html" data="http://192.168.20.3/" width="100%" height="100%"></object>';
      //
      // return;

      Cesium.LisenceMaker.lisenceUrl = window._CONFIG.lisenceUrl;
      // Cesium.Math.Radious=6356752.3142451793;
      window.viewer = new Cesium.Viewer('home', {
        navigation: true,
        // imageryProvider: new Cesium.SingleTileImageryProvider({
        //   url: require(`@/assets/imgs/world.png`)
        // }),
        // navigation: false,
        contextOptions: {
          //cesium状态下允许canvas转图片convertToImage
          webgl: {
            preserveDrawingBuffer: true,
          },
          allowTextureFilterAnisotropic: true,
        },
      })

      // viewer.imageryLayers.addImageryProvider(
      //   new Cesium.WebMapTileServiceImageryProvider({
      //     // url: window._CONFIG.BD_MAP,
      //     url: 'http://t0.tianditu.com/img_w/wmts?service=wmts&request=GetTile&version=1.0.0&LAYER=img&tileMatrixSet=w&TileMatrix={TileMatrix}&TileRow={TileRow}&TileCol={TileCol}&style=default&tk=1d109683f4d84198e37a38c442d68311',
      //     layer: "tdtVecBasicLayer",
      //     style: "default",
      //     format: "image/jpeg",
      //     // subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
      //     // subdomains: ['0', '7'],
      //     tileMatrixSetID: "GoogleMapsCompatible",
      //     show: true,
      //   })
      // )

      // viewer.cesiumWidget.creditContainer.style.display = "none";
      let scene = window.viewer.scene
      let that = this;

      // if (Cesium.FeatureDetection.supportsImageRenderingPixelated()) {//判断是否支持图像渲染像素化处理
      //   window.viewer.resolutionScale = 4 / 3;
      // }
      scene.fxaa = true;
      scene.postProcessStages.fxaa.enabled = true;
      // scene.logarithmicDepthBuffer = false;
      scene.logarithmicDepthBuffer = false;
      //
      scene.undergroundMode = false //设置开启地下场景
      // scene.screenSpaceCameraController.minimumZoomDistance = window._CONFIG.HEIGHT-30 //设置相机最小缩放距离,距离地表-1000米
      // let height = window._CONFIG.HEIGHT<=0?1:window._CONFIG.HEIGHT/20
      // scene.screenSpaceCameraController._zoomFactor = scene.screenSpaceCameraController._zoomFactor/height
      
      if (!window._CONFIG.HEIGHT) {
        window._CONFIG.HEIGHT = 0
      }
      if (!window._CONFIG.HIGOFFSET) {
        window._CONFIG.HIGOFFSET = 0
      }
      // 创建高程为0的参考平面
      if(window._CONFIG.HEIGHT !== 0){
        viewer.SetElevationPlaneHeight(window._CONFIG.HEIGHT,window._CONFIG.HIGOFFSET);
      }


      scene.hdrEnabled = false
      // scene.shadowMap.darkness = 1.275; // 设置第二重烘焙纹理的效果（明暗程度）
      // scene.skyAtmosphere.brightnessShift = 0.4;  //修改大气的亮度
      // scene.debugShowFramesPerSecond = false;
      // scene.hdrEnabled = false;
      scene.sun.show = true;
      // if (Cesium.FeatureDetection.supportsImageRenderingPixelated()) {//判断是否支持图像渲染像素化处理
      //   viewer.resolutionScale = window.devicePixelRatio;
      // }
      // scene.fxaa = true;
      // scene.postProcessStages.fxaa.enabled = true;

      scene.skyAtmosphere.show = false //隐藏大气圈
      // viewer.scene.terrainProvider.isCreateSkirt = false // 关闭裙边
      // viewer.scene.globe.globeAlpha = 0
      // viewer.scene.underGlobe._baseColor=new Cesium.Color(0.8,0.8,0.8,0.8)
      // viewer.scene.underGlobe._show=false
      // scene.screenSpaceCameraController.enableTilt = false;
      // viewer.scene.screenSpaceCameraController.minimumZoomDistance = 600;
      // viewer.scene.screenSpaceCameraController.maximumZoomDistance = 3817894.110660365;


      viewer.scene.skyBox = new Cesium.GroundSkyBox({
        sources: {
          positiveX: require(`@/assets/imgs/03/px.jpg`),
          negativeX: require(`@/assets/imgs/03/nx.jpg`),
          positiveY: require(`@/assets/imgs/03/py.jpg`),
          negativeY: require(`@/assets/imgs/03/ny.jpg`),
          positiveZ: require(`@/assets/imgs/03/pz.jpg`),
          negativeZ: require(`@/assets/imgs/03/nz.jpg`),
        },
      })



      // // 添加光源
      // let position1 = new Cesium.Cartesian3.fromDegrees(117.20701528650977, 39.08619779992728, 480)
      // //光源方向点
      // let targetPosition1 = new Cesium.Cartesian3.fromDegrees(117.20701528650977, 39.08619779992728, 430)
      // let dirLightOptions = {
      //   targetPosition: targetPosition1,
      //   color: new Cesium.Color(1, 1, 1, 0.1),
      //   intensity: 0.55,
      // }
      // let directionalLight_1 = new Cesium.DirectionalLight(position1, dirLightOptions)
      // scene.addLightSource(directionalLight_1)

      // scene.addStarTilesLayerByScp("http://127.0.0.1:18080/tree%E5%90%88%40%E5%9B%AD%E5%8C%BA%E4%B8%89%E7%BB%B4%E6%95%B0%E6%8D%AE-%E7%9C%9F%E5%AE%9E%E8%84%B1%E5%AF%86%E4%BD%86%E6%9C%AA%E5%8A%A0%E5%AF%86_ds1/tileset.json", {name: 'yqqita',rangeScale:10});
      // scene.addStarTilesLayerByScp("http://127.0.0.1:18080/water%40%E5%9B%AD%E5%8C%BA%E4%B8%89%E7%BB%B4%E6%95%B0%E6%8D%AE-%E7%9C%9F%E5%AE%9E%E8%84%B1%E5%AF%86%E4%BD%86%E6%9C%AA%E5%8A%A0%E5%AF%86_ds1/tileset.json", {name: 'yqshui',rangeScale:10});
      // scene.addStarTilesLayerByScp("http://127.0.0.1:18080/building%40%E5%9B%AD%E5%8C%BA%E4%B8%89%E7%BB%B4%E6%95%B0%E6%8D%AE-%E7%9C%9F%E5%AE%9E%E8%84%B1%E5%AF%86%E4%BD%86%E6%9C%AA%E5%8A%A0%E5%AF%86_ds1/tileset.json", {name: 'yqjz',rangeScale:10});
      //
      // scene.addStarTilesLayerByScp('http://localhost:8083/stargis/rest/services/tjsw/3DTileServer/patch@环内脱密未加密/tileset.json', {
      //   name: '水面',
      //   rangeScale:10,
      //   maxVisibleDistance:100000,
      //   maxVisibleAltitude:100000,
      // });
      //
      // scene.addStarTilesLayerByScp('http://localhost:8083/stargis/rest/services/tjsw/3DTileServer/building@环内脱密未加密/tileset.json', {
      //   name: '建筑',
      //   maxVisibleDistance:2000,
      //   maxVisibleAltitude:2000,
      //   blending:false,
      //   rangeScale:10
      // });
      //
      // scene.addStarTilesLayerByScp('http://localhost:8083/stargis/rest/services/tjsw/3DTileServer/guidepost合@环内脱密未加密/tileset.json', {
      //   name: '其他',
      //   maxVisibleDistance:800,
      //   maxVisibleAltitude:800,
      //   rangeScale:10
      // });

      // scene.screenSpaceCameraController.enableTilt = false;
      //
      // let drawRect = new Cesium.DrawRect(document.body,viewer);
      //
      // document.addEventListener('keydown', function (e) {
      //   if (e.key === 'Shift') {
      //     drawRect.drawRect.activate();
      //   }
      // });
      // document.addEventListener('keyup', function (e) {
      //   if (e.key === 'Shift') {
      //     drawRect.drawRect.deactivate();
      //   }
      // });

      // viewer.camera.flyTo({
      //   // destination: Cesium.Cartesian3.fromDegrees(117.485467360123, 39.91058249616607, 131659.4527393031), //Cesium.Cartesian3.fromDegrees(117.23450846595091, 39.042188221280924, 210000.246589106785004),
      //   // destination: Cesium.Cartesian3.fromDegrees(117.47774827446963, 39.10315760680579, 359315.02697537205),
      //   destination: Cesium.Cartesian3.fromDegrees(116.14249699999998, 36.44045850000002, 7797.614675408148),
      //   orientation: {
      //     // heading: 0.0034659788848951933, //6.2831789,
      //     // pitch: -1.470228745913574, //-1.5282651
      //     heading: 6.283185307179586, //6.2831789,
      //     pitch: -1.5707963267948966, //-1.5282651
      //   },
      // })
      // return
      if (window.initView) {
        viewer.camera.flyTo({
          // destination: Cesium.Cartesian3.fromDegrees(117.485467360123, 39.91058249616607, 131659.4527393031), //Cesium.Cartesian3.fromDegrees(117.23450846595091, 39.042188221280924, 210000.246589106785004),
          // destination: Cesium.Cartesian3.fromDegrees(117.47774827446963, 39.10315760680579, 359315.02697537205),
          destination: Cesium.Cartesian3.fromDegrees(parseFloat(window.initView[0]), parseFloat(window.initView[1]), parseFloat(window.initView[2])),
          orientation: {
            heading: Cesium.Math.toRadians(0.0),
            pitch: Cesium.Math.toRadians(-90)
          },
        })
      } else {
        viewer.camera.flyTo({
          // destination: Cesium.Cartesian3.fromDegrees(117.485467360123, 39.91058249616607, 131659.4527393031), //Cesium.Cartesian3.fromDegrees(117.23450846595091, 39.042188221280924, 210000.246589106785004),
          // destination: Cesium.Cartesian3.fromDegrees(117.47774827446963, 39.10315760680579, 359315.02697537205),
          destination: Cesium.Cartesian3.fromDegrees(117.62659970682701, 39.335073544515225, 337523.32515122986),
          orientation: {
            heading: Cesium.Math.toRadians(0.0),
            pitch: Cesium.Math.toRadians(-90)
          },
        })
      }
      console.log('Cesium Viewer 初始化完成');
      // this.$bus.$emit('s3dmviewerLoaded', viewer) //地图加载完成事件，用于触发其他
      return


    },
    createVideo() {
      let videocanvas = document.createElement('video')
      videocanvas.setAttribute('id', 'videocanvas')
      videocanvas.setAttribute('visibility', 'hidden')
      videocanvas.setAttribute('muted', 'true')
      videocanvas.muted = true
      // videocanvas.setAttribute('autoplay', 'autoplay')
      videocanvas.setAttribute('loop', 'loop')
      // videocanvas.setAttribute('src', require(`@/assets/imgs/MP4/1.MOV`))
      videocanvas.setAttribute('controls', 'controls')
      videocanvas.style['z-index'] = 10000
      videocanvas.style['width'] = '0'
      videocanvas.style['height'] = '0'
      videocanvas.style.position = 'absolute'
      videocanvas.style['top'] = 0
      videocanvas.style['left'] = '300px'
      document.getElementsByTagName('body')[0].appendChild(videocanvas)


      let videocanvas2 = document.createElement('video')
      videocanvas2.setAttribute('id', 'videocanvas2')
      videocanvas2.setAttribute('visibility', 'hidden')
      videocanvas2.setAttribute('muted', 'true')
      videocanvas2.muted = true
      // videocanvas.setAttribute('autoplay', 'autoplay')
      videocanvas2.setAttribute('loop', 'loop')
      // videocanvas2.setAttribute('src', require(`@/assets/imgs/MP4/2.MP4`))
      videocanvas2.setAttribute('controls', 'controls')
      videocanvas2.style['z-index'] = 10000
      videocanvas2.style['width'] = '0'
      videocanvas2.style['height'] = '0'
      videocanvas2.style.position = 'absolute'
      videocanvas2.style['top'] = 0
      videocanvas2.style['left'] = '300px'
      document.getElementsByTagName('body')[0].appendChild(videocanvas2)

      let videocanvas3 = document.createElement('video')
      videocanvas3.setAttribute('id', 'videocanvas3')
      videocanvas3.setAttribute('visibility', 'hidden')
      videocanvas3.setAttribute('muted', 'true')
      videocanvas3.muted = true
      // videocanvas.setAttribute('autoplay', 'autoplay')
      videocanvas3.setAttribute('loop', 'loop')
      // videocanvas3.setAttribute('src', require(`@/assets/imgs/MP4/NJL.mp4`))
      videocanvas3.setAttribute('controls', 'controls')
      videocanvas3.style['z-index'] = 10000
      videocanvas3.style['width'] = '0'
      videocanvas3.style['height'] = '0'
      videocanvas3.style.position = 'absolute'
      videocanvas3.style['top'] = 0
      videocanvas3.style['left'] = '300px'
      document.getElementsByTagName('body')[0].appendChild(videocanvas3)

      let videocanvas4 = document.createElement('video')
      videocanvas4.setAttribute('id', 'videocanvas4')
      videocanvas4.setAttribute('visibility', 'hidden')
      videocanvas4.setAttribute('muted', 'true')
      videocanvas4.muted = true
      // videocanvas.setAttribute('autoplay', 'autoplay')
      videocanvas4.setAttribute('loop', 'loop')
      // videocanvas4.setAttribute('src', require(`@/assets/imgs/MP4/DLDXHL.mp4`))
      videocanvas4.setAttribute('controls', 'controls')
      videocanvas4.style['z-index'] = 10000
      videocanvas4.style['width'] = '0'
      videocanvas4.style['height'] = '0'
      videocanvas4.style.position = 'absolute'
      videocanvas4.style['top'] = 0
      videocanvas4.style['left'] = '300px'
      document.getElementsByTagName('body')[0].appendChild(videocanvas4)

    },

    toImage(val) {
      if (!val) {
        return
      }
      let { height, width } = val
      // 手动创建一个 canvas 标签
      const canvas = document.createElement('canvas')
      // 获取父标签，意思是这个标签内的 DOM 元素生成图片
      // stargisMap是给截图范围内的父级元素自定义的ref名称
      let canvasBox = this.$refs.stargisMap
      // 获取父级的宽高
      const domWidth = parseInt(window.getComputedStyle(canvasBox).width)
      const domHeight = parseInt(window.getComputedStyle(canvasBox).height)
      canvas.width = domWidth
      canvas.height = domHeight
      canvas.style.width = domWidth + 'px'
      canvas.style.height = domHeight + 'px'
      const options = {
        canvas: canvas,
        useCORS: true,
      }
      html2canvas(canvasBox, options).then((canvas) => {
        // toDataURL 图片格式转成 base64
        let dataURL = canvas.toDataURL('image/png')
        this.downloadImage(dataURL, height, width)
      })
    },
    convertImageToCanva(image, height, width) {
      let canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height)
      return canvas
    },
    downloadImage(url, height, width) {
      let image = new Image()
      image.src = url
      image.onload = () => {
        let canvas = this.convertImageToCanva(image, height, width)
        let url = canvas.toDataURL('image/png')
        let a = document.createElement('a')
        let event = new MouseEvent('click')
        a.download = new Date().getTime() + '.png' // 指定下载图片的名称
        a.href = url
        a.dispatchEvent(event) // 触发超链接的点击事件
      }
    },
    download() {
      let url = `http://221.7.13.58:9004/open-api/device/taosdata/datachange/report-exprot`
      // let url = `http://192.168.1.10:7000/device/taosdata/datachange/report-exprot`
      const config = {
        method: 'post',
        url: url,
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Bearer 15c263b5-0855-4d7d-a76c-95cf905ad496',
          // Authorization: 'Bearer 9cc4553a-2dcd-442b-b75d-31c771891494'
        },
        responseType: 'blob',
        data: { monitorId: 1, startTime: '2022-09-29 21:23:22', endTime: '2022-09-30 21:23:22' },
      }
      axios(config).then((response) => {
        console.log(response)
        const url = window.URL.createObjectURL(new Blob([response]))
        const link = document.createElement('a')
        link.href = url
        link.setAttribute('download', 'a.xlsx')
        document.body.appendChild(link)
        link.click()
      })
    },
  },
}
</script>

<style>
#home {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
}

#player {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
}

.cesium-viewer-navigationContainer {
  top: 66px;
}

.cesium-viewer-bottom {
  pointer-events: none;
}
</style>