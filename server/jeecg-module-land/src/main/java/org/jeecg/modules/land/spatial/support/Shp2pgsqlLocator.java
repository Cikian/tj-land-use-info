package org.jeecg.modules.land.spatial.support;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * shp2pgsql 可执行文件定位器。
 *
 * <p>为什么需要它：shp2pgsql 是 PostGIS 安装包里的 C 可执行文件，路径随
 * 安装环境变化（盘符、版本号、自定义目录）。定位顺序：
 * <ol>
 *   <li>配置 land.spatial.shp2pgsql-path 显式指定（文件或目录）</li>
 *   <li>连库执行 SHOW data_directory，反推同级 bin 目录（本机跑着 PG 时最准）</li>
 *   <li>常见安装目录扫描（C/D 盘 PostgreSQL 各版本、Linux 常见位置）</li>
 *   <li>退回 PATH（直接以 "shp2pgsql" 命令名执行，交给操作系统找）</li>
 * </ol>
 */
public class Shp2pgsqlLocator {

    private Shp2pgsqlLocator() {}

    /**
     * @param configuredPath 配置项 land.spatial.shp2pgsql-path（可为空）
     * @param pgUrl          用于反推安装目录的 PG 连接串（jdbc:postgresql://...）
     * @param user           PG 账号
     * @param password       PG 密码
     * @return 可执行的 shp2pgsql 路径；实在找不到返回 "shp2pgsql"（PATH 兜底）
     */
    public static String resolve(String configuredPath, String pgUrl, String user, String password) {
        // 1) 配置优先
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            File f = new File(configuredPath.trim());
            if (f.isFile()) return f.getAbsolutePath();
            if (f.isDirectory()) {
                File exe = new File(f, isWindows() ? "shp2pgsql.exe" : "shp2pgsql");
                if (exe.isFile()) return exe.getAbsolutePath();
            }
            // 配置了但不存在：明确报错，不静默降级，避免"以为在用 A 实际在用 B"
            throw new IllegalStateException("配置的 shp2pgsql 路径不存在：" + configuredPath);
        }

        // 2) SHOW data_directory 反推 bin 目录
        try (Connection conn = DriverManager.getConnection(pgUrl, user, password);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW data_directory")) {
            if (rs.next()) {
                File dataDir = new File(rs.getString(1));
                File bin = new File(dataDir.getParentFile(), "bin");
                File exe = new File(bin, isWindows() ? "shp2pgsql.exe" : "shp2pgsql");
                if (exe.isFile()) return exe.getAbsolutePath();
            }
        } catch (Exception ignored) {
            // 反推失败（比如 PG 在远程机器）继续走本地扫描
        }

        // 3) 常见安装目录
        List<String> candidates = new ArrayList<>();
        if (isWindows()) {
            for (String drive : new String[]{"C:", "D:", "E:"}) {
                for (int v = 10; v <= 18; v++) {
                    candidates.add(drive + "/Program Files/PostgreSQL/" + v + "/bin/shp2pgsql.exe");
                }
            }
        } else {
            candidates.add("/usr/bin/shp2pgsql");
            candidates.add("/usr/local/bin/shp2pgsql");
            for (int v = 10; v <= 18; v++) {
                candidates.add("/usr/pgsql-" + v + "/bin/shp2pgsql");
            }
        }
        for (String c : candidates) {
            if (new File(c).isFile()) return c;
        }

        // 4) PATH 兜底
        return "shp2pgsql";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }
}
