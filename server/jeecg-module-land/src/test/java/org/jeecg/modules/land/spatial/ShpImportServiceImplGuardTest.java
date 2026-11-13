package org.jeecg.modules.land.spatial;

import org.jeecg.modules.land.spatial.config.SpatialProperties;
import org.jeecg.modules.land.spatial.service.impl.ShpImportServiceImpl;
import org.jeecg.modules.land.spatial.vo.SpatialSourceStatsVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理动作的服务端防线（纯单测，不依赖真实库）：
 * 标识符白名单、"本系统导入"才允许删库/覆盖重建、连接串解析、库不可达降级。
 *
 * <p>★ 为什么防线要在服务端再验一遍：前端的选择弹窗只是 UX，
 * 拿 curl 直接打 /land/spatial/source/{id}?dropDatabase=true 或
 * existingPolicy=overwrite 时，靠的是这里的 requireOwnImported / remark 校验，
 * 否则手工配置的生产空间库（如 land_postgis）会被一条请求整库删掉。
 */
class ShpImportServiceImplGuardTest {

    /** 用桩 JdbcTemplate 替掉 MySQL 访问——只测守卫逻辑，不测真实登记 */
    private ShpImportServiceImpl serviceWithRows(List<Map<String, Object>> rows) {
        return new ShpImportServiceImpl() {
            {
                // 单测没有 Spring 注入，props 补上默认值（importShp 在解压阶段就要用 tempDir）
                ReflectionTestUtils.setField(this, "props", new SpatialProperties());
            }

            @Override
            protected JdbcTemplate jdbc() {
                return new JdbcTemplate() {
                    @Override
                    public List<Map<String, Object>> queryForList(String sql, Object... args) {
                        return rows;
                    }

                    @Override
                    public int update(String sql, Object... args) {
                        return 1;
                    }
                };
            }
        };
    }

    private static Map<String, Object> row(String remark, String dbUrl) {
        Map<String, Object> row = new HashMap<>();
        row.put("code", "tj_spatial_test_x");
        row.put("db_url", dbUrl);
        row.put("db_name", "tj_spatial_test_x");
        row.put("db_username", "postgres");
        row.put("db_password", "x");
        row.put("remark", remark);
        return row;
    }

    @Test
    void precheckRejectsIllegalDbNameBeforeAnyConnection() {
        ShpImportServiceImpl service = serviceWithRows(Collections.emptyList());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.precheck("127.0.0.1", 5432, "postgres", "x", "1bad;drop"));
        assertTrue(e.getMessage().contains("数据库名不合法"));
    }

    @Test
    void dropDatabaseRefusedForNonImportedSource() {
        ShpImportServiceImpl service = serviceWithRows(Collections.singletonList(
                row("手工登记", "jdbc:postgresql://127.0.0.1:5432/tj_spatial_test_x")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.removeGisSource("any-id", true));
        assertTrue(e.getMessage().contains("不允许删除 PG 库"));
    }

    @Test
    void dropDatabaseRefusedWhenDbUrlUnparseable() {
        ShpImportServiceImpl service = serviceWithRows(Collections.singletonList(
                row("SHP 上传入库", "jdbc:postgresql://127.0.0.1/tj_spatial_test_x")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.removeGisSource("any-id", true));
        assertTrue(e.getMessage().contains("连接串无法解析"));
    }

    @Test
    void removeUnknownIdRejected() {
        ShpImportServiceImpl service = serviceWithRows(Collections.emptyList());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.removeGisSource("nope", false));
        assertTrue(e.getMessage().contains("管理清单中不存在该记录"));
    }

    @Test
    void statsDegradeWhenDbUrlUnparseable() throws Exception {
        ShpImportServiceImpl service = serviceWithRows(Collections.singletonList(
                row("SHP 上传入库", "not-a-jdbc-url")));
        // 库不可达不算错误：如实返回不可达 + 表数 -1，删库流程靠 dropDatabase 的幂等兜底
        SpatialSourceStatsVO stats = service.sourceStats("any-id");
        assertTrue(!stats.isReachable());
        assertEquals(-1, stats.getTableCount());
        assertTrue(stats.isOwnImported());
    }

    @Test
    void overwriteImportRefusedForUnregisteredDb() throws Exception {
        ShpImportServiceImpl service = serviceWithRows(Collections.emptyList());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.importShp(fakeShpZip(), "127.0.0.1", 5432, "postgres", "x",
                        "tj_spatial_test_x", "t", 4548, "geotools", "overwrite"));
        assertTrue(e.getMessage().contains("不允许覆盖重建"));
        assertTrue(e.getMessage().contains("未在管理清单中"));
    }

    @Test
    void overwriteImportRefusedForManuallyRegisteredDb() throws Exception {
        ShpImportServiceImpl service = serviceWithRows(Collections.singletonList(
                row("手工登记", "jdbc:postgresql://127.0.0.1:5432/tj_spatial_test_x")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.importShp(fakeShpZip(), "127.0.0.1", 5432, "postgres", "x",
                        "tj_spatial_test_x", "t", 4548, "geotools", "overwrite"));
        assertTrue(e.getMessage().contains("手工登记"));
    }

    /** zip 只需结构合法（.shp + 同名 .dbf/.shx），内容无所谓——守卫在连 PG 之前就拦截 */
    private MockMultipartFile fakeShpZip() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (String name : new String[]{"t.shp", "t.dbf", "t.shx"}) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write(new byte[]{1});
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("file", "t.zip", "application/zip", out.toByteArray());
    }
}
