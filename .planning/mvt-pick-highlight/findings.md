# 排查发现

- 用户截图：右面板已显示地块0922 的 gid=151，地块内部仅有一个青色小框。
- imageryProvider.pickFeatures 重新请求高层级 MVT 后，将裁剪后几何转成 GeoJSON 用作高亮；MvtSql 使用 ST_AsMVTGeom(...,true)，所以无法保证完整要素。
- imageryProvider 的 toLL 将纬度方向翻转，并用纬度线性插值代替 WebMercator 反投影；这也会影响点内判断。
- map/index.vue 只在 scene.pick 为空时执行 MVT 拾取，自己的高亮 entity 会挡住连续点击。
- 事件处理器未在入口验证 pickActive，可能在其它工具模式触发拾取。
- 高亮只认 Polygon，并将首环外的所有环当洞，不能正确绘制 MultiPolygon。
- 当前层选择优先图层名含“地块”，不利于拾取重叠的配套设施；应优先可见图层的堆叠顺序。
- 项目已有 MvtService 的数据源/空间表白名单、MvtPostgis 查询与 10 秒超时，可复用增加按点返回完整几何的只读查询。
