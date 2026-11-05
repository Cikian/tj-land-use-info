# MVT 接入差异说明

2026-10-28。本次增加标准 MVT 的显示链路，不涉及经营性用地和配套业务计算口径。

| 项目 | 接入前 | 本次实现与原因 |
|---|---|---|
| 资源注册 | 服务类型存于中台 `stargis_servicetype`，存在类型不代表大屏可渲染 | 注册编码为 `MVT` 的资源，审批后加入现有场景；资源与树节点均由中台正规接口保存 |
| 图层树 | 闭源 `LayerTreePop` 能返回资源节点，但不加载标准 MVT | 宿主局部指令监听原组件 `treeData` / `checkedKeys`，不改变组件父子关系；按资源 ID 去重，处理显隐、定位、异步取消和跨页恢复 |
| 地图绘制 | 厂商旧矢量瓦片路径依赖未加载的 `ol`，无法直接显示本次 Martin 服务 | 使用 `@mapbox/vector-tile` / `pbf` 解码真实瓦片并绘制透明 Canvas，由当前 Cesium 影像层负责显示和视野瓦片调度 |
| 坐标解释 | 原表 `jyxyd_dk_geom.geom` 标注 4547，直接转换偏西约 3° | 仅新增 `jyxyd_dk_mvt` 只读发布视图，将原坐标按 4548 解释；原表不更新，1120 个地块落在天津范围 |

实现入口：

- `stargis-client-web/src/utils/mvt/imageryProvider.js`：TileJSON / XYZ、空瓦片、解码、网络错误、请求取消。
- `stargis-client-web/src/utils/mvt/layerTreeDirective.js`：原图层树状态桥接和 viewer 生命周期管理。
- `stargis-client-web/src/views/screen/home/HomeLayerTree.vue`、`src/views/screen/map/index.vue`、`src/views/maps/components/Basic/HomeBasicBody.vue`：宿主接入。
- `stargis-client-web/src/views/maps/S3dmViewer.vue`：销毁时释放 MVT。
- `deploy/mvt/01_land_mvt_view.sql`：幂等视图与范围核对查询。

本版内置 12 组图层配色，按资源 ID 排序为整棵树分配，超过 12 层继续生成色相；同一资源多处挂树共用颜色。同一 viewer 生命周期内取消重勾、切换页面保持配色，整页刷新后按首次取得的树重新分配。点和面使用半透明填充，线和面边框使用对应亮色；已有资源显式样式仍优先。支持点、线、面和面洞；暂不提供样式设置界面、按属性分类着色、MVT 属性拾取、Mapbox 样式文件或文字标注。勾选仅显示、取消后释放图层，均不移动地图；双击名称才按资源范围定位，未勾选时定位也不改变显隐。刷新后保留中台节点，勾选即可重新加载。

资源无 `{z}/{x}/{y}` 后缀的 URL 是 TileJSON 元数据入口，加载器读取其 `tiles` 模板后请求二进制 MVT；当前模板为 `http://127.0.0.1:3001/jyxyd_dk_mvt/{z}/{x}/{y}`。Cesium 根据视野和缩放调度并缓存瓦片，可能同时请求不同层级的父子瓦片。

该桥接依赖插件当前公开在组件实例上的字段，升级 `stargis-function` 后应重新验收。配置、启动及本机验收记录见 [MVT 服务接入](../deploy/mvt/README.md)。

第二轮性能优化：有 TileJSON 范围且未显式设置 `minimumLevel` 时，按范围选择最多覆盖四张根瓦片的起始层级，自动选择最高到第 8 级。天津当前范围从第 8 级起步，避免为第 1～7 级大范围父瓦片反复生成整批地块。全球/跨日期线/零面积范围保留服务声明，XYZ 模板无 bounds 时沿用原起始层级。地图缩远时仍由 Cesium 使用起始瓦片显示，不改变勾选显隐和双击定位规则。
