package org.jeecg.modules.land.spatial.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * 带连接/套接字超时的连接变体（预检等交互场景用）。
     * pgjdbc 的 connectTimeout/socketTimeout 单位都是秒；不带超时的预检
     * 遇到不可达主机会把前端请求挂到网关超时，表现成"无响应"。
     */
    public static Connection connect(String host, int port, String dbName,
                                     String user, String password, int timeoutSecs) throws SQLException {
        return DriverManager.getConnection(
                url(host, port, dbName) + "?connectTimeout=" + timeoutSecs + "&socketTimeout=" + timeoutSecs,
                user, password);
    }

    /** 目标库是否已存在（连 postgres 维护库查 pg_database）。库名必须先经白名单校验。 */
    public static boolean databaseExists(String host, int port, String dbName,
                                         String user, String password) throws SQLException {
        try (Connection conn = connect(host, port, "postgres", user, password);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'")) {
            return rs.next();
        }
    }

    /**
     * 删除 PG 数据库（幂等：库不存在视为已删）。
     * ★ 必须先 terminate 该库上的所有其它连接：DROP DATABASE 要求没有活跃连接，
     * 而 MVT 出图的 Druid 连接池与地图客户端长连接很容易占住库，不先断开
     * 就报 "database is being accessed by other users"。terminate 是异步生效的，
     * 紧接着 DROP 仍可能撞上正在退出的会话，所以带退避重试。
     */
    public static void dropDatabase(String host, int port, String dbName,
                                    String user, String password) throws SQLException {
        try (Connection conn = connect(host, port, "postgres", user, password);
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'")) {
                if (!rs.next()) return;
            }
            st.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                    + "WHERE datname = '" + dbName + "' AND pid <> pg_backend_pid()");
            SQLException last = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    st.execute("DROP DATABASE \"" + dbName + "\"");
                    return;
                } catch (SQLException e) {
                    last = e;
                    try {
                        Thread.sleep(300L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("删除数据库被中断：" + dbName, ie);
                    }
                }
            }
            throw new SQLException("删除数据库失败（可能有连接尚未释放，请稍后重试）：" + last.getMessage(), last);
        }
    }

    /**
     * 列出目标库的用户表（排除系统 schema），格式 schema.表名。
     * 用于删除/覆盖重建前的知情确认——让用户看到将丢失什么。
     */
    public static List<String> listTables(String host, int port, String dbName,
                                          String user, String password) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = connect(host, port, dbName, user, password);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT schemaname || '.' || tablename FROM pg_tables "
                     + "WHERE schemaname NOT IN ('pg_catalog', 'information_schema') ORDER BY 1")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
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
