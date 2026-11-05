package org.jeecg.modules.land.spatial.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.land.spatial.service.IShpImportService;
import org.jeecg.modules.land.spatial.vo.ShpImportResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: MVT 空间表服务 · SHP 上传入库
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-28
 * @Version V1.1
 *
 * <p>功能：上传 SHP 的 zip 压缩包（一个包只能有一个 shp），提交后自动：
 * 建库（如不存在）→ 启用 PostGIS → 导入几何与属性 → 登记进 GIS 库管理清单
 * （land_mvt_source，MVT 管理页只显示本表——只有通过本系统添加的库才会被管理，
 * 添加的库默认就是一个 GIS 数据库）。导入引擎可插拔：
 * shp2pgsql（默认）/ GeoTools（信创，纯 Java）。
 *
 * <p>接口一览（统一前缀 /land/spatial）：
 * <pre>
 *   POST   /land/spatial/import      ★ 上传 SHP zip 并导入（自动登记清单）
 *   DELETE /land/spatial/source/{id}  ★ 从管理清单移除（不删除 PG 库本身）
 * </pre>
 */
@Slf4j
@Api(tags = "MVT空间表服务-SHP上传入库")
@RestController
@RequestMapping("/land/spatial")
public class SpatialImportController {

    @Autowired
    private IShpImportService shpImportService;

    @AutoLog("SHP上传入库")
    @ApiOperation("上传 SHP zip 并导入 PostGIS（自动建库/启用扩展/登记清单）")
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
            @RequestParam(value = "importEngine", required = false) String importEngine) {
        try {
            ShpImportResultVO vo = shpImportService.importShp(file, pgHost, pgPort,
                    pgUser, pgPassword, dbName, tableName, srid, importEngine);
            return Result.OK(vo);
        } catch (Exception e) {
            log.error("SHP 导入失败", e);
            return Result.error("SHP 导入失败：" + e.getMessage());
        }
    }

    @AutoLog("移除GIS库管理")
    @ApiOperation("从 MVT 管理清单移除 GIS 库（不删除 PG 库本身）")
    @DeleteMapping("/source/{id}")
    public Result<String> removeSource(@PathVariable String id) {
        try {
            shpImportService.removeGisSource(id);
            return Result.OK("已移除");
        } catch (Exception e) {
            log.error("移除 GIS 库管理失败", e);
            return Result.error("移除失败：" + e.getMessage());
        }
    }
}
