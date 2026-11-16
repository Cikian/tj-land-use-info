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
import * as turf from '@turf/turf'
import { flashAt } from '../data/locateHighlight'
import { getModuleSliceContext } from '../data/sliceInfo'
import { featureSearchMvt } from '@/api/land/landAdmin'

/** 实体分组 id（老工作台用的是 placeLocate123，这里换一个避免互相清理） */
const ENTITY_GROUP_ID = 'placeLocateMap123'

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
      /** 切片源是否就绪（大屏图层树就绪后即可搜索） */
      sliceReady: false,
      /** 搜索来源标签（地块数据 / 配套数据），随切片源动态解析 */
      sliceSources: [],
      /** 搜索结果：{id, name, rows, geometry:{GeoJson}} */
      results: [],
      selectedIndex: -1,
      /** 动态气泡标签（DynamicDivLabel 实例），离开页面 / 重新搜索时统一关闭 */
      labels: [],
      locateSymbol: 0
    }
  },
  computed: {
    hint () {
      if (this.searching) return '搜索中…'
      if (!this.sliceReady) return 'MVT 切片源尚未就绪，请稍后再试'
      if (!this.results.length) return this.keyword ? '未找到匹配结果' : '输入名称后回车搜索'
      return ''
    },
    selectedRows () {
      const selected = this.results[this.selectedIndex]
      return selected ? selected.rows : []
    }
  },
  created () {
    this.initSliceSource()
  },
  beforeDestroy () {
    this.clearMapObjects()
  },
  methods: {
    /**
     * 切片源就绪（地名定位的数据源 = MVT 切片源表）：
     * 数据管理页联动渲染完成后 getModuleSliceContext 即可用；
     * 组件 mounted 早于壳层 sceneId 设置，所以延后轮询判定。
     */
    initSliceSource () {
      const check = () => {
        const land = getModuleSliceContext('land')
        const facility = getModuleSliceContext('facility')
        this.sliceSources = [land, facility].filter(Boolean).map((ctx, i) => ({
          ctx: ctx,
          label: i === 0 ? '地块数据' : '配套数据'
        }))
        this.sliceReady = this.sliceSources.length > 0
        if (!this.sliceReady) setTimeout(check, 1500)
      }
      setTimeout(check, 1000)
    },

    /* ---------------- 搜索（MVT 切片源表 featureSearch） ---------------- */
    onSearch () {
      const keyword = (this.keyword || '').trim()
      if (!keyword || !this.sliceReady) return
      this.clearMapObjects()
      this.results = []
      this.selectedIndex = -1
      this.searching = true
      let pending = this.sliceSources.length
      this.sliceSources.forEach(({ ctx, label }) => {
        featureSearchMvt({
          source: ctx.info.source,
          table: ctx.info.table,
          keyword: keyword,
          limit: 50
        })
          .then((res) => {
            if (!res || !res.success || !Array.isArray(res.result)) return
            res.result.forEach((item, index) => {
              const props = item.properties || {}
              const geojson = item.geojson ? JSON.parse(item.geojson) : null
              if (!geojson) return
              this.results.push({
                id: label + '-' + index,
                name: String(props.crzdbh || props.ptxmmc || props.dkm || props.dkmc || ('要素 ' + (index + 1))),
                rows: toRows(props),
                geometry: { GeoJson: geojson }
              })
            })
          })
          .catch(() => {})
          .finally(() => { if (--pending === 0) this.searching = false })
      })
    },

    /* ---------------- 定位（对应 drawSelectedOne / drawSelected） ---------------- */
    selectResult (index) {
      this.selectedIndex = index
      const item = this.results[index]
      if (!item) return
      this.clearMapObjects()
      this.drawSelected(item, item.id)
      // 定位闪烁（与数据管理定位同款视觉反馈）：拾中地块高亮 3 秒
      if (item.geometry && item.geometry.GeoJson) {
        flashAt({ geometries: [JSON.stringify(item.geometry.GeoJson)] })
      }
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
                perPositionHeight: false
              },
              depthTestEnabled: false,
              clampToS3M: true
            })
          } else if (entity.polyline) {
            viewer.entities.add({
              parent: group,
              polyline: {
                positions: entity.polyline.positions,
                material: Cesium.Color.AQUA,
                clampToGround: true,
                width: 5
              },
              depthTestEnabled: false,
              clampToS3M: true
            })
          } else if (entity.point || entity.label) {
            // 点要素：定位标签（Cesium 原生 label，随要素画在 group 里）
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
              disableDepthTestDistance: 700000.0
            }
          })
        }
        if (Aid !== undefined && bbox) {
          // 选中定位：相机落到要素包围盒（与原版一致外扩 0.005°）
          viewer.camera.setView({
            destination: Cesium.Rectangle.fromDegrees(bbox[0] - 0.005, bbox[1] - 0.005, bbox[2] + 0.005, bbox[3] + 0.005)
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
    }
  }
}
</script>

<style scoped lang="less">
@import '~@/components/screen/styles/screen-mixins.less';

.map-place {
  /*
   * 表头 / 单元格的横向偏移与列宽改用**百分比**（以设计稿内容宽 360 换算）：
   *   30 → 8.3333%、161 → 44.7222%、120 → 33.3333%、190 → 52.7778%。
   * 右面板可拖宽（400 → 340~720）后固定 px 会让字段值列贴在左边。
   */

  &__title-deco {
    position: absolute;
    right: 9px;
    top: 95px;
    // 标题装饰比面板宽 20（设计稿 400 → 420）；面板可拖宽，跟着走
    width: calc(var(--panel-right-w, 400px) + 20px);
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
    // 宽度由页面根的 --panel-right-w 下发（可拖动 340~720），不再写死 400
    width: var(--panel-right-w, 400px);
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
    // 左锚 20 + 右锚 50（给搜索图标留 36px）：输入区随面板变宽 / 变窄
    right: 50px;
    top: 0;
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
    // 图标贴搜索框右缘（设计稿 360 框里 x=326，即距右 20）
    right: 20px;
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
  // 属性区 320 高（表头 36 + 表体 284）；宽度随外层 __attr 的左右锚定走
  &__attr {
    position: absolute;
    left: 20px;
    right: 20px;
    bottom: 30px;
    height: 320px;
  }

  // 表头 / 表体左右双向锚定（原来是 left:0 + width:360）
  &__head {
    position: absolute;
    left: 0;
    right: 0;
    top: 0;
    height: 36px;
  }

  &__head-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
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

    // 30/360、161/360（设计稿 px → 内容宽百分比）
    &.is-name { left: 8.3333%; }
    &.is-value { left: 44.7222%; }
  }

  &__body {
    position: absolute;
    left: 0;
    right: 0;
    top: 36px;
    bottom: 0;
    overflow-y: auto;
    overflow-x: hidden;
  }

  &__body-bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
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

    // 与表头同一套百分比（列位置 + 列宽都随面板宽度变化）
    &.is-name {
      left: 8.3333%;
      width: 33.3333%;
    }

    &.is-value {
      left: 44.7222%;
      width: 52.7778%;
    }
  }
}
</style>
