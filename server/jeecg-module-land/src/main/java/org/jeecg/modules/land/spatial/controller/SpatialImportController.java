package org.jeecg.modules.land.spatial.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.spatial.service.IShpImportService;
import org.jeecg.modules.land.spatial.vo.SpatialFeatureGroupVO;
import org.jeecg.modules.land.spatial.vo.SpatialPrecheckVO;
import org.jeecg.modules.land.spatial.vo.SpatialSourceStatsVO;
import org.jeecg.modules.land.spatial.vo.ShpImportResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: MVT 空间表服务 · SHP 上传入库
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-28
 * @Version V1.2
 *
 * <p>功能：上传 SHP 的 zip 压缩包（一个包只能有一个 shp），提交后自动：
 * 建库（如不存在）→ 启用 PostGIS → 导入几何与属性 → 替换式登记进 GIS 库管理清单
 * （land_mvt_source，MVT 管理页只显示本表——只有通过本系统添加的库才会被管理，
 * 添加的库默认就是一个 GIS 数据源）。导入引擎可插拔：
 * shp2pgsql（默认）/ GeoTools（信创，纯 Java）。
 *
 * <p>目标库已存在时不再是静默连接：先 /precheck 预检，
 * 前端让用户在 连接导入（保留库，覆盖同名表）/ 覆盖重建（整库删除后重建，
 * 仅限本系统导入的库）之间显式选择。
 *
 * <p>接口一览（统一前缀 /land/spatial）：
 * <pre>
 *   POST   /land/spatial/precheck             ★ 导入前预检（不上传文件）
 *   POST   /land/spatial/import               ★ 上传 SHP zip 并导入（existingPolicy=connect|overwrite）
 *   GET    /land/spatial/source/{id}/stats    ★ 库现状统计（确认弹窗用，口令不出服务端）
 *   GET    /land/spatial/sliceFeatureBounds   ★ 按唯一值查要素范围（大屏列表定位）
 *   GET    /land/spatial/featureGroup         ★ 按唯一值回显全部要素几何（图形管理弹窗）
 *   POST   /land/spatial/featureGroup         ★ 整组替换要素几何（事务内先删后插）
 *   DELETE /land/spatial/featureGroup         ★ 整组删除要素几何（移除联动/改编号迁移）
 *   DELETE /land/spatial/source/{id}          ★ 移除（dropDatabase=false 仅取消登记；true 连 PG 库一起删）
 * </pre>
 */
@Slf4j
@Api(tags = "MVT空间表服务-SHP上传入库")
@RestController
@RequestMapping("/land/spatial")
public class SpatialImportController {

    @Autowired
    private IShpImportService shpImportService;

    @AutoLog("SHP导入预检")
    @ApiOperation("导入前预检：目标库是否存在/凭据是否可用/是否本系统导入/库内现有表")
    @PostMapping("/precheck")
    public Result<SpatialPrecheckVO> precheck(
            @RequestParam("pgHost") String host,
            @RequestParam(value = "pgPort", defaultValue = "5432") int port,
            @RequestParam("pgUser") String user,
            @RequestParam("pgPassword") String password,
            @RequestParam("dbName") String dbName) {
        try {
            return Result.OK(shpImportService.precheck(host, port, user, password, dbName));
        } catch (Exception e) {
            log.error("SHP 导入预检失败", e);
            return Result.error("预检失败：" + e.getMessage());
        }
    }

    @AutoLog("SHP上传入库")
    @ApiOperation("上传 SHP zip 并导入 PostGIS（自动建库/启用扩展/替换式登记）")
    @PostMapping("/import")
    public Result<ShpImportResultVO> importShp(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pgHost") String pgHost,
            @RequestParam(value = "pgPort", defaultValue = "5432") int pgPort,
            @RequestParam("pgUser") String pgUser,
            @RequestParam("pgPassword") String pgPassword,
            @RequestParam("dbName") String dbName,
            @RequestParam(value = "tableName", required = false) String tableName,
            @RequestParam(value = "srid", defaultValue = "0") int srid,
            @RequestParam(value = "importEngine", required = false) String importEngine,
            @RequestParam(value = "existingPolicy", required = false) String existingPolicy) {
        try {
            ShpImportResultVO vo = shpImportService.importShp(file, pgHost, pgPort,
                    pgUser, pgPassword, dbName, tableName, srid, importEngine, existingPolicy);
            return Result.OK(vo);
        } catch (Exception e) {
            log.error("SHP 导入失败", e);
            return Result.error("SHP 导入失败：" + e.getMessage());
        }
    }

    @AutoLog("GIS库现状统计")
    @ApiOperation("查询清单中某库的现状（能否连上/库内表数量），供移除确认弹窗展示")
    @GetMapping("/source/{id}/stats")
    public Result<SpatialSourceStatsVO> sourceStats(@PathVariable String id) {
        try {
            return Result.OK(shpImportService.sourceStats(id));
        } catch (Exception e) {
            log.error("GIS 库现状统计失败", e);
            return Result.error("统计失败：" + e.getMessage());
        }
    }

    @AutoLog("切片要素范围查询")
    @ApiOperation("按唯一值查切片源表要素范围（大屏列表定位用，替代 dk_geojson 定位）")
    @GetMapping("/sliceFeatureBounds")
    public Result<java.util.List<Double>> sliceFeatureBounds(
            @RequestParam("source") String source,
            @RequestParam("table") String table,
            @RequestParam("field") String field,
            @RequestParam("value") String value) {
        try {
            return Result.OK(shpImportService.sliceFeatureBounds(source, table, field, value));
        } catch (Exception e) {
            log.error("切片要素范围查询失败", e);
            return Result.error("定位查询失败：" + e.getMessage());
        }
    }

    @AutoLog("切片要素组回显")
    @ApiOperation("按唯一值回显切片源表全部要素几何（图形管理弹窗打开时用，只走精确匹配）")
    @GetMapping("/featureGroup")
    public Result<java.util.List<String>> featureGroup(
            @RequestParam("source") String source,
            @RequestParam("table") String table,
            @RequestParam("field") String field,
            @RequestParam("value") String value) {
        try {
            return Result.OK(shpImportService.sliceFeatureGeometries(source, table, field, value));
        } catch (Exception e) {
            log.error("切片要素组回显失败", e);
            return Result.error("回显失败：" + e.getMessage());
        }
    }

    @AutoLog("切片要素组整组替换")
    @ApiOperation("整组替换某唯一值名下的要素几何（事务内先删后插；geometries 为空 = 清空整组）")
    @PostMapping("/featureGroup")
    public Result<Integer> saveFeatureGroup(@RequestBody SpatialFeatureGroupVO body) {
        try {
            if (body == null) {
                return Result.error("请求体不能为空");
            }
            return Result.OK(shpImportService.saveSliceFeatureGroup(
                    body.getSource(), body.getTable(), body.getField(), body.getValue(), body.getGeometries()));
        } catch (Exception e) {
            log.error("切片要素组保存失败", e);
            return Result.error("保存失败：" + e.getMessage());
        }
    }

    @AutoLog("切片要素组整组删除")
    @ApiOperation("整组删除某唯一值名下的要素几何（移除记录联动清图形 / 改编号先删旧组）")
    @DeleteMapping("/featureGroup")
    public Result<Integer> deleteFeatureGroup(
            @RequestParam("source") String source,
            @RequestParam("table") String table,
            @RequestParam("field") String field,
            @RequestParam("value") String value) {
        try {
            return Result.OK(shpImportService.deleteSliceFeatureGroup(source, table, field, value));
        } catch (Exception e) {
            log.error("切片要素组删除失败", e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    @AutoLog("切片要素搜索")
    @ApiOperation("切片源表要素搜索（地名定位/空间查询共用）：关键字模糊匹配全部字符列，可选 bbox 空间过滤")
    @GetMapping("/featureSearch")
    public Result<java.util.List<java.util.Map<String, Object>>> featureSearch(
            @RequestParam("source") String source,
            @RequestParam("table") String table,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "bbox", required = false) String bbox,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        try {
            return Result.OK(shpImportService.featureSearch(source, table, keyword, bbox, limit));
        } catch (Exception e) {
            log.error("切片要素搜索失败", e);
            return Result.error("要素搜索失败：" + e.getMessage());
        }
    }

    @AutoLog("移除GIS库管理")
    @ApiOperation("从 MVT 管理清单移除 GIS 库（dropDatabase=true 时连 PG 库一起删除，仅限本系统导入的库）")
    @DeleteMapping("/source/{id}")
    public Result<String> removeSource(@PathVariable String id,
                                       @RequestParam(value = "dropDatabase", defaultValue = "false") boolean dropDatabase) {
        try {
            shpImportService.removeGisSource(id, dropDatabase);
            return Result.OK("已移除");
        } catch (Exception e) {
            log.error("移除 GIS 库管理失败", e);
            return Result.error("移除失败：" + e.getMessage());
        }
    }
}
