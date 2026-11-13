package org.jeecg.modules.land.spatial;

import org.jeecg.modules.land.spatial.support.PgSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 PG 验证 PgSupport 新增的建库/删库/表清单能力（移除删库、覆盖重建的底层）。
 *
 * <p>环境变量 SPATIAL_TEST_PG_USER / SPATIAL_TEST_PG_PASSWORD 未设置时整类跳过
 * （与 MvtPostgisTest 的 MVT_TEST_* 同一约定）；地址/端口默认 127.0.0.1:5432，
 * 可用 SPATIAL_TEST_PG_HOST / SPATIAL_TEST_PG_PORT 覆盖。
 * 测试库用 tj_spatial_test_ 前缀，测试结束无论成败都兜底删一次。
 */
class PgSupportDatabaseTest {

    private String host;
    private int port;
    private String user;
    private String password;
    private String dbName;

    @BeforeEach
    void open() {
        host = envOrDefault("SPATIAL_TEST_PG_HOST", "127.0.0.1");
        port = Integer.parseInt(envOrDefault("SPATIAL_TEST_PG_PORT", "5432"));
        user = System.getenv("SPATIAL_TEST_PG_USER");
        password = System.getenv("SPATIAL_TEST_PG_PASSWORD");
        Assumptions.assumeTrue(user != null && !user.isEmpty() && password != null,
                "设置 SPATIAL_TEST_PG_USER/SPATIAL_TEST_PG_PASSWORD 后执行真实 PG 建删库测试");
        dbName = "tj_spatial_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @AfterEach
    void cleanup() {
        try {
            PgSupport.dropDatabase(host, port, dbName, user, password);
        } catch (Exception ignored) {
            // 兜底清理失败不掩盖测试本身的断言结果
        }
    }

    @Test
    void createDetectDropLifecycleIsIdempotent() throws Exception {
        assertFalse(PgSupport.databaseExists(host, port, dbName, user, password));
        PgSupport.createDatabaseIfAbsent(host, port, dbName, user, password);
        assertTrue(PgSupport.databaseExists(host, port, dbName, user, password));
        // 重复建库 = 幂等（createDatabaseIfAbsent 的既有语义，覆盖重建路径依赖它）
        PgSupport.createDatabaseIfAbsent(host, port, dbName, user, password);
        List<String> tables = PgSupport.listTables(host, port, dbName, user, password);
        assertNotNull(tables);
        assertTrue(tables.isEmpty(), "新建的库不应有用户表");
        PgSupport.dropDatabase(host, port, dbName, user, password);
        assertFalse(PgSupport.databaseExists(host, port, dbName, user, password));
        // 重复删库 = 幂等不抛（移除流程删库后再清登记，重试路径依赖它）
        PgSupport.dropDatabase(host, port, dbName, user, password);
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? def : v;
    }
}
