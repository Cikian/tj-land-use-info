package org.jeecg.modules.land.spatial.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * PG 连接小工具（不走 jeecg 的 MySQL 数据源——本功能是连任意 PG 做管理操作）。
 */
public class PgSupport {

    private PgSupport() {}

    public static String url(String host, int port, String dbName) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
    }

    public static Connection connect(String host, int port, String dbName,
                                     String user, String password) throws SQLException {
        return DriverManager.getConnection(url(host, port, dbName), user, password);
    }

    /** 数据库不存在则创建（在 postgres 库上执行）。库名必须先经白名单校验。 */
    public static void createDatabaseIfAbsent(String host, int port, String dbName,
                                              String user, String password) throws SQLException {
        try (Connection conn = connect(host, port, "postgres", user, password);
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'")) {
                if (rs.next()) return;
            }
            st.execute("CREATE DATABASE \"" + dbName + "\"");
        }
    }

    /** 目标库启用 PostGIS（幂等）。 */
    public static void enablePostgis(String host, int port, String dbName,
                                     String user, String password) throws SQLException {
        try (Connection conn = connect(host, port, dbName, user, password);
             Statement st = conn.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS postgis");
        }
    }

    /**
     * 导入前直接 DROP 旧表（重复导入幂等）。
     * ★ 为什么不用 shp2pgsql 的 -d 参数：-d 会生成 SELECT DropGeometryColumn(...)，
     * 它要求列已在 geometry_columns 注册——桌面端等工具导入的残留表若注册缺失，
     * 就会报 "column not found in geometry_columns table"。服务端直接 DROP TABLE
     * 能绕开这个 PostGIS 函数对历史残留表的注册依赖。
     */
    public static void dropTableIfExists(String host, int port, String dbName, String tableName,
                                         String user, String password) throws SQLException {
        try (Connection conn = connect(host, port, dbName, user, password);
             Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS \"" + tableName + "\" CASCADE");
        }
    }

    /** 库名/表名白名单：字母、数字、下划线、中文，且不以数字开头（防注入，禁引号分号空格） */
    public static void checkIdentifier(String name, String what) {
        if (name == null || !name.matches("[a-zA-Z_\\u4e00-\\u9fa5][\\w\\u4e00-\\u9fa5]*")) {
            throw new IllegalArgumentException(what + "不合法（只允许字母/数字/下划线/中文，且不能以数字开头）：" + name);
        }
    }
}
