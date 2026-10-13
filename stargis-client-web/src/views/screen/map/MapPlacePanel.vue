<template>
  <!--
    MapPlacePanel 地图管理页右面板「地名定位」
    ==================================================================
    逻辑提取自老三维工作台顶栏搜索框 HomeHeader.vue（本仓库可读源码，
    与 stargis-function 插件 QueryPlaceName/QueryPlaceNamePop 同一套实现）：
      配置  登录后中台功能配置 sysConfig.ShortCut 里 componentname =
            'QueryPlaceName' 的项 → dataurl（{dbData1: 数据源id, fields1: 字段}）
            → jeecg 后端 /app/basicinfo/metaJson 取数据源元信息 → allParams
      查询  数据库型(DatasourceID)：组装 fuzzyValues 模糊查询 → POST 中台
            数据服务 VUE_DATA_SERVER_URL + serverUrl（/stargis/rest/tasks/test/query）
            要素服务型(Featurcclassid)：星图 FeatureServer 走 Cesium.SpatialFilter，
            ArcGIS 服务走 Cesium.QueryTask（where 字段 like '%关键词%'）
      定位  GeoJsonDataSource 画青色图形（贴地）+ DynamicDivLabel 气泡标签，
            相机 setView 到要素包围盒（外扩 0.005°）

    与原实现的两处刻意差异（本组件内注释标明）：
      1. 搜索结果只列在面板里，点击某条才画图定位（原版搜索完成即全量画图，
         点结果时清掉重画选中项；这里保持「点结果才画」的一致体验）；
      2. 原版把名称写在搜索下拉里，这里直接用结果列表呈现，选中要素的
         全部属性落到下方「字段名/字段值」表（复用拾取查询面板的表格切图）。

    没有配置（sysConfig 里没有 QueryPlaceName 项）时给出提示，不报错；
    ?map=0 预览（无 Cesium）时查询和列表照常，画图与飞行自动跳过。
  -->
  <div class="map-place">
    <img class="map-place__title-deco" :src="hf.panelTitleRight" alt="" aria-hidden="true" />
    <h2 class="map-place__title">地名定位</h2>

    <section class="map-place__panel stage-hit">
      <!-- 搜索框（与首页图层面板检索框同款切图） -->
      <div class="map-place__search">
        <img class="map-place__search-bg" :src="hf.fieldSearch" alt="" aria-hidden="true" />
        <input
          v-model="keyword"
          class="map-place__search-input"
          type="text"
          placeholder="请输入地点名称"
          aria-label="地名搜索"
          @keyup.enter="onSearch"
        />
        <img
          class="map-place__search-icon"
          :src="hf.fieldSearchIcon"
          alt="搜索"
          role="button"
          tabindex="0"
          @click="onSearch"
          @keydown.enter.prevent="onSearch"
        />
      </div>

      <!-- 结果列表 -->
      <div class="map-place__list" :class="{ 'has-attr': selectedRows.length }">
        <p v-if="hint" class="map-place__hint">{{ hint }}</p>
        <div
          v-for="(item, index) in results"
          :key="item.id"
          class="map-place__row"
          :class="{ 'is-selected': index === selectedIndex }"
          @click="selectResult(index)"
        >
          <span class="map-place__row-index">{{ index + 1 }}</span>
          <span class="map-place__row-name" :title="item.name">{{ item.name }}</span>
        </div>
      </div>

      <!-- 选中要素的属性表（复用拾取查询面板的表格切图） -->
      <div v-if="selectedRows.length" class="map-place__attr">
        <div class="map-place__head">
          <img class="map-place__head-bg" :src="hf.pickTableHead" alt="" aria-hidden="true" />
          <span class="map-place__th is-name">字段名</span>
          <span class="map-place__th is-value">字段值</span>
        </div>
        <div class="map-place__body">
          <img class="map-place__body-bg" :src="hf.pickTableBody" alt="" aria-hidden="true" />
          <div
            v-for="(row, index) in selectedRows"
            :key="`${index}-${row.name}`"
            class="map-place__trow"
          >
            <img
              v-if="index % 2 === 0"
              class="map-place__trow-bg"
              :src="hf.tableRow"
              alt=""
              aria-hidden="true"
            />
            <span class="map-place__td is-name" :title="row.name">{{ row.name }}</span>
            <span class="map-place__td is-value" :title="row.value">{{ row.value }}</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { hf } from '@/assets/screen-blue'
import { getAction, postJson } from '@/api/manage'
import * as turf from '@turf/turf'
import DynamicDivLabel from '@/utils/wyw/index'

/** 实体分组 id（老工作台用的是 placeLocate123，这里换一个避免互相清理） */
const ENTITY_GROUP_ID = 'placeLocateMap123'
/** 属性区高度：表头 36 + 表体 284（面板内坐标），结果列表给属性区让位 */
const ATTR_ZONE_HEIGHT = 320
/** 气泡标签上限：原实现不限量，这里封顶防止一次搜索画几百个 DOM 标签 */
const LABEL_LIMIT = 50

function toRows (properties) {
  // 要素属性 → {name, value} 行数组（数值统一转字符串，模板直接渲染）
  const rows = []
  for (const key in properties) {
    if (key === 'name') continue
    const value = properties[key]
    rows.push({ name: key, value: value === null || value === undefined ? '' : String(value) })
  }
  return rows
}

export default {
  name: 'MapPlacePanel',
  data () {
    return {
      hf,
      keyword: '',
      searching: false,
      /** 配置是否就绪（sysConfig.ShortCut 里有没有 QueryPlaceName 数据源） */
      configReady: false,
      serverUrl: '/stargis/rest/tasks/test/query',
      /** 查询参数清单：DatasourceID（数据库型）/ Featurcclassid（要素服务型） */
      allParams: [],
      /** 配置解析的中间量：数据源 id 串与返回字段（与 HomeHeader 同名同义） */
      allDBData: [],
      allFields: [],
      /** 搜索结果：{id, name, rows, geometry:{GeoJson}} */
      results: [],
      selectedIndex: -1,
      /** 动态气泡标签（DynamicDivLabel 实例），离开页面 / 重新搜索时统一关闭 */
      labels: [],
      locateSymbol: 0,
    }
  },
  computed: {
    hint () {
      if (this.searching) return '搜索中…'
      if (!this.configReady) return '地名定位未配置数据源，请在 GIS 功能配置「快捷方式 → 地名定位」里配置后使用'
      if (!this.results.length) return this.keyword ? '未找到匹配结果' : '输入名称后回车搜索'
      return ''
    },
    selectedRows () {
      const selected = this.results[this.selectedIndex]
      return selected ? selected.rows : []
    },
  },
  created () {
    this.loadConfig()
  },
  beforeDestroy () {
    this.clearMapObjects()
  },
  methods: {
    /* ---------------- 配置加载（对应 HomeHeader.prepareQueryPlaceName） ---------------- */
    loadConfig () {
      const config = this.$store.getters.sysConfig || {}
      const shortcut = config.ShortCut
      const item = shortcut && shortcut.children
        ? shortcut.children.find((child) => child.componentname === 'QueryPlaceName')
        : null
      if (!item || !item.dataurl) {
        this.configReady = false
        return
      }
      try {
        // 与原实现一致：GIS 配置端输出的 dataurl 是对象字面量串（非严格 JSON）
        const dbDataParam = eval('(' + item.dataurl + ')') // eslint-disable-line no-eval
        Object.keys(dbDataParam).forEach((key) => {
          if (key.indexOf('dbData') < 0) return
          const sub = key.replace('dbData', '')
          if (dbDataParam['fields' + sub]) {
            this.allDBData = this.allDBData || []
            this.allDBData.push(dbDataParam[key])
            this.allFields = this.allFields || []
            this.allFields.push(dbDataParam['fields' + sub])
          }
        })
      } catch (error) {
        this.configReady = false
        return
      }
      if (item.serverurl) {
        try {
          const server = eval('(' + item.serverurl + ')') // eslint-disable-line no-eval
          if (server && server.dataUrl) this.serverUrl = server.dataUrl
        } catch (error) { /* serverurl 解析失败则用默认地址 */ }
      }
      this.loadDataSource()
    },

    /** 数据源元信息 → 查询参数（对应 HomeHeader.getDataSource） */
    loadDataSource () {
      const pairs = this.allDBData || []
      if (!pairs.length) {
        this.configReady = false
        return
      }
      let pending = pairs.length
      pairs.forEach((item, index) => {
        const parts = String(item).split('_')
        let dbName = ''
        let url = ''
        if (parts.length > 2) {
          dbName = parts[parts.length - 2]
          url = '/app/basicinfo/metaJson'
        } else {
          dbName = item
          url = '/app/basicinfo/metaJsonByClassId'
        }
        getAction(url, { id: dbName, flag: true }).then((response) => {
          const result = response.result
          if (!result) return
          if (result.Featurcclassid) {
            // 要素服务型
            this.allParams.push({
              f: 'json',
              spatialReference: { wkid: '4326' },
              returnGeometry: true,
              layerOption: 'all',
              Featurcclassid: result.Featurcclassid,
              fieldName: this.allFields[index],
            })
          } else if (result.DBConectionString) {
            // 数据库型
            const datasource = {}
            datasource.DBConectionString = result.DBConectionString
            datasource.DBConectionString.DatasourceType = parseInt(result.DBConectionString.DatasourceType)
            datasource.DBConectionString.Port = parseInt(result.DBConectionString.Port)
            datasource.DatasetName = result.Datasets[0] ? result.Datasets[0].DatasetName : ''
            datasource.TableName =
              result.Datasets[0] && result.Datasets[0].FeatureClasses[0]
                ? result.Datasets[0].FeatureClasses[0].TableName
                : ''
            if (this.allFields[index]) {
              datasource.VagueField = [this.allFields[index]]
              datasource.ReturnField = [{ Field: this.allFields[index] }]
            }
            datasource.BeginRow = 0
            datasource.RowsLimit = 500
            this.allParams.push({ TrakerName: 'QueryVagueValue', DatasourceID: datasource })
          }
        }).finally(() => {
          if (--pending === 0) this.configReady = true
        })
      })
    },

    /* ---------------- 搜索（对应 HomeHeader.onSearch） ---------------- */
    onSearch () {
      const keyword = (this.keyword || '').trim()
      if (!keyword) return
      if (!this.allParams.length) return
      this.clearMapObjects()
      this.results = []
      this.selectedIndex = -1
      this.searching = true
      let pending = this.allParams.length
      const settle = () => {
        if (--pending === 0) this.searching = false
      }
      this.allParams.forEach((item) => {
        if (item.DatasourceID) {
          const task = this.getTaskWYW(item)
          task.fuzzyValues = [{ field: item.DatasourceID.ReturnField[0].Field, value: keyword }]
          postJson(window._CONFIG.VUE_DATA_SERVER_URL + this.serverUrl, this.getTaskParamsWYW(task))
            .then((response) => {
              this.collectStargis(response.result, item.DatasourceID.ReturnField[0].Field)
            })
            .catch(() => {})
            .finally(settle)
        } else if (item.Featurcclassid) {
          this.queryFeatureServer(item, keyword).finally(settle)
        } else {
          settle()
        }
      })
    },

    /** 要素服务型查询：星图 FeatureServer / ArcGIS QueryTask（对应原实现的分支） */
    queryFeatureServer (item, keyword) {
      const Cesium = window.Cesium
      if (!Cesium || (!Cesium.SpatialFilter && !Cesium.QueryTask)) return Promise.resolve()
      let promise = null
      const field = item.fieldName
      if (item.Featurcclassid.includes('stargis/rest') && item.Featurcclassid.includes('FeatureServer')) {
        item.where = `"${field}" like '%${keyword}%'`
        item.useFieldAlias = false
        item.maxReturnCount = 200
        let url = item.Featurcclassid
        if (window.secretkey) url = url + '?secretkey=' + window.secretkey
        promise = new Cesium.StargisQuery(url).execute(new Cesium.SpatialFilter(item)).then((data) => {
          this.collectStargis(data, field)
        })
      } else {
        item.where = encodeURI(`"${field}" like '%${keyword}%'`)
        let url = item.Featurcclassid + '/0/query'
        if (window.secretkey) url = url + '?secretkey=' + window.secretkey
        promise = new Cesium.QueryTask(url).execute(new Cesium.Query(item)).then((data) => {
          this.collectArcgis(data, field)
        })
      }
      return promise ? promise.catch(() => {}) : Promise.resolve()
    },

    /** 中台 / GeoJSON 结果 → 结果列表（对应 successStargisServer） */
    collectStargis (data, field) {
      if (!data || !data.features || !data.features.length) return
      data.features.forEach((feature) => {
        const name = feature.properties ? feature.properties[field] : ''
        if (name === undefined || name === null || name === '') return
        this.results.push({
          id: 'placeLocate' + this.locateSymbol++,
          name: String(name),
          rows: toRows(feature.properties || {}),
          geometry: { GeoJson: feature.geometry || null },
        })
      })
    },

    /** ArcGIS 结果 → 结果列表（对应 successArcgisServer，含 esri 几何转 GeoJSON） */
    collectArcgis (data, field) {
      const { features, geometryType } = data
      if (!features || !features.length) return
      features.forEach((item) => {
        const value = item.attributes ? item.attributes[field] : ''
        if (value === undefined || value === null || value === '') return
        let geometry = null
        if (geometryType === 'esriGeometryPoint') {
          geometry = turf.point([item.geometry.x, item.geometry.y]).geometry
        } else if (geometryType === 'esriGeometryPolyline') {
          const dims = this.dimensionArray(item.geometry.paths)
          geometry = (dims === 3 ? turf.multiLineString(item.geometry.paths) : turf.lineString(item.geometry.paths)).geometry
        } else if (geometryType === 'esriGeometryPolygon') {
          const dims = this.dimensionArray(item.geometry.rings)
          geometry = (dims === 4 ? turf.multiPolygon(item.geometry.rings) : turf.polygon(item.geometry.rings)).geometry
        }
        this.results.push({
          id: 'placeLocate' + this.locateSymbol++,
          name: String(value),
          rows: toRows(item.attributes || {}),
          geometry: { GeoJson: geometry },
        })
      })
    },

    /** 数组维数（原 dimension_array_es6） */
    dimensionArray (arr) {
      if (arr instanceof Array) {
        return Math.max(...arr.map((item) => 1 + this.dimensionArray(item)))
      }
      return 0
    },

    /* ---------------- 数据库查询任务组装（对应 getTaskWYW / getTaskParamsWYW） ---------------- */
    getTaskWYW (item) {
      const result = item.DatasourceID
      const conn = result.DBConectionString
      const typeOf = this.getDataBaseType || (() => 'mysql')
      return {
        subtask: 'query',
        datasource: {
          type: typeOf(conn.DatasourceType),
          database: conn.Database,
          instance: conn.Instance,
          host: conn.Server,
          port: parseInt(conn.Port),
          user: conn.User,
          pwd: conn.Pwd,
        },
        dataset: result.DatasetName || '',
        featureclass: result.TableName || '',
        resultOffset: 0,
        maxReturnCount: 2000,
        resultRecordCount: 20,
        returnGeometry: true,
        inSR: { EPSG: 4326 },
        outSR: { EPSG: 4326 },
      }
    },
    getTaskParamsWYW (params) {
      return JSON.stringify({ task: 'test', synchronize: true, inprocess: false, param: params })
    },

    /* ---------------- 定位（对应 drawSelectedOne / drawSelected） ---------------- */
    selectResult (index) {
      this.selectedIndex = index
      const item = this.results[index]
      if (!item) return
      this.clearMapObjects()
      this.drawSelected(item, item.id)
    },

    /** 画图形 + 气泡标签 + 相机飞到包围盒；无 Cesium（?map=0）时只跳过画图 */
    drawSelected (item, Aid) {
      const Cesium = window.Cesium
      const viewer = window.viewer
      if (!Cesium || !viewer || !item.geometry.GeoJson) return
      const geoJson = item.geometry.GeoJson
      const bbox = turf.bbox(geoJson)
      let lonLat = null
      if (geoJson.type === 'MultiPolygon' || geoJson.type === 'Polygon') {
        const center = turf.center(geoJson)
        if (center && center.geometry) lonLat = center.geometry.coordinates
      } else if (geoJson.type === 'MultiLineString' || geoJson.type === 'LineString') {
        const coords = geoJson.coordinates.flat()
        lonLat = coords.length === 2 ? coords : coords[Math.floor(coords.length / 2 - 1)]
      }

      Cesium.GeoJsonDataSource.load(geoJson).then((dataSource) => {
        let group = viewer.entities.getById(ENTITY_GROUP_ID)
        if (!group) {
          group = viewer.entities.add(new Cesium.Entity({ id: ENTITY_GROUP_ID }))
        }
        dataSource.entities.values.forEach((entity) => {
          if (entity.polygon) {
            viewer.entities.add({
              parent: group,
              polygon: {
                hierarchy: entity.polygon.hierarchy,
                material: Cesium.Color.AQUA,
                clampToGround: true,
                perPositionHeight: false,
              },
              depthTestEnabled: false,
              clampToS3M: true,
            })
          } else if (entity.polyline) {
            viewer.entities.add({
              parent: group,
              polyline: {
                positions: entity.polyline.positions,
                material: Cesium.Color.AQUA,
                clampToGround: true,
                width: 5,
              },
              depthTestEnabled: false,
              clampToS3M: true,
            })
          } else if (this.labels.length < LABEL_LIMIT) {
            // 点要素：DynamicDivLabel 气泡（原版每点一个，这里封顶防刷屏）
            this.labels.push({
              obj: new DynamicDivLabel(viewer, entity.position.getValue(), { id: item.id, mode: 3 }, item.name),
            })
          }
        })
        if (lonLat) {
          viewer.entities.add({
            parent: group,
            position: Cesium.Cartesian3.fromDegrees(lonLat[0], lonLat[1], 1),
            label: {
              text: item.name,
              font: '20px sans-serif',
              showBackground: true,
              horizontalOrigin: Cesium.HorizontalOrigin.CENTER,
              verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
              disableDepthTestDistance: 700000.0,
            },
          })
        }
        if (Aid !== undefined && bbox) {
          // 选中定位：相机落到要素包围盒（与原版一致外扩 0.005°）
          viewer.camera.setView({
            destination: Cesium.Rectangle.fromDegrees(bbox[0] - 0.005, bbox[1] - 0.005, bbox[2] + 0.005, bbox[3] + 0.005),
          })
        }
      })
    },

    /** 清掉本组件画的全部图形与气泡（换工具 / 离开页面 / 重新搜索） */
    clearMapObjects () {
      this.labels.forEach((label) => {
        try { label.obj.windowClose() } catch (error) { /* 已关闭则忽略 */ }
      })
      this.labels = []
      const viewer = window.viewer
      if (viewer && viewer.entities) {
        const group = viewer.entities.getById(ENTITY_GROUP_ID)
        if (group) {
          group._children.slice().forEach((child) => viewer.entities.remove(child))
          viewer.entities.remove(group)
        }
      }
    },
  },
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.map-place {
  &__title-deco {
    position: absolute;
    right: 9px;
    top: 95px;
    width: 420px;
    height: 46px;
    pointer-events: none;
  }

  &__title {
    position: absolute;
    // 与「拾取查询」标题同一位置：文字右缘距右边界 83px
    right: 83px;
    top: 102px;
    margin: 0;
    font-size: var(--screen-font-lg);
    font-weight: 500;
    line-height: 24px;
    color: #ffffff;
    text-align: right;
    white-space: nowrap;
  }

  &__panel {
    position: absolute;
    right: 30px;
    top: 149px;
    bottom: 50px;
    width: 400px;
    background-image: url('~@/assets/screen-blue/panel-right-bg.png');
    background-repeat: no-repeat;
    background-size: 100% 100%;
  }

  /* ---------------- 搜索框（同首页图层树检索框） ---------------- */
  &__search {
    position: absolute;
    left: 20px;
    right: 20px;
    top: 20px;
    height: 28px;
  }

  &__search-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 28px;
    display: block;
    pointer-events: none;
  }

  &__search-input {
    position: absolute;
    left: 20px;
    top: 0;
    width: 290px;
    height: 28px;
    padding: 0;
    font-family: inherit;
    font-size: var(--screen-font-sm);
    line-height: 28px;
    color: #ffffff;
    background: transparent;
    border: 0;
    outline: none;

    &::placeholder {
      color: #4a7396;
    }
  }

  &__search-icon {
    position: absolute;
    left: 326px;
    top: 7px;
    width: 14px;
    height: 14px;
    display: block;
    cursor: pointer;
    .screen-focus-ring();
  }

  /* ---------------- 结果列表 ---------------- */
  &__list {
    position: absolute;
    left: 20px;
    right: 20px;
    top: 60px;
    bottom: 30px;
    overflow-y: auto;
    overflow-x: hidden;

    // 属性表显示时给底部让位（属性区 320 高 + 10 间距）
    &.has-attr {
      bottom: 350px;
    }
  }

  &__hint {
    margin: 100px 0 0;
    font-size: var(--screen-font-sm);
    line-height: 22px;
    color: #4a7396;
    text-align: center;
  }

  &__row {
    position: relative;
    display: flex;
    align-items: center;
    height: 36px;
    padding: 0 10px;
    cursor: pointer;

    &:hover,
    &.is-selected {
      background: rgba(24, 68, 112, 0.55);
    }
  }

  &__row-index {
    width: 26px;
    flex: none;
    font-size: var(--screen-font-sm);
    color: #66afd4;
    font-variant-numeric: tabular-nums;
  }

  &__row-name {
    flex: 1;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: #82c6ff;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  /* ---------------- 选中要素属性表（同拾取查询面板） ---------------- */
  &__attr {
    position: absolute;
    left: 20px;
    right: 20px;
    bottom: 30px;
    height: 320px;
  }

  &__head {
    position: absolute;
    left: 0;
    top: 0;
    width: 360px;
    height: 36px;
  }

  &__head-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 360px;
    height: 36px;
    display: block;
    pointer-events: none;
  }

  &__th {
    position: absolute;
    top: 8px;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: #ffffff;
    white-space: nowrap;

    &.is-name { left: 30px; }
    &.is-value { left: 161px; }
  }

  &__body {
    position: absolute;
    left: 0;
    top: 36px;
    width: 360px;
    bottom: 0;
    overflow-y: auto;
    overflow-x: hidden;
  }

  &__body-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 360px;
    height: 100%;
    display: block;
    pointer-events: none;
  }

  &__trow {
    position: relative;
    width: 100%;
    height: 46px;
  }

  &__trow-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 46px;
    display: block;
    pointer-events: none;
  }

  &__td {
    position: absolute;
    top: 12px;
    font-size: var(--screen-font-sm);
    line-height: 20px;
    color: #82c6ff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    font-variant-numeric: tabular-nums;

    &.is-name {
      left: 30px;
      width: 120px;
    }

    &.is-value {
      left: 161px;
      width: 190px;
    }
  }
}
</style>
