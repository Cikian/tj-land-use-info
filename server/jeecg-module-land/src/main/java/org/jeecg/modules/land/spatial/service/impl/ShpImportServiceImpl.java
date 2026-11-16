package org.jeecg.modules.land.spatial.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.land.spatial.config.SpatialProperties;
import org.jeecg.modules.land.spatial.service.IShpImportService;
import org.jeecg.modules.land.spatial.service.engine.GeotoolsEngine;
import org.jeecg.modules.land.spatial.service.engine.IShpImportEngine;
import org.jeecg.modules.land.spatial.service.engine.Shp2pgsqlEngine;
import org.jeecg.modules.land.spatial.support.PgSupport;
import org.jeecg.modules.land.spatial.vo.SpatialPrecheckVO;
import org.jeecg.modules.land.spatial.vo.SpatialSourceStatsVO;
import org.jeecg.modules.land.spatial.vo.ShpImportResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * SHP 上传入库流程编排。
 *
 * <p>步骤：参数/白名单校验 → zip 解压并校验（一个 zip 只能有一个 shp，
 * .dbf/.shx 必须配套）→ 目标库处理（按预检策略：连接导入 / 覆盖重建）→
 * 启用 PostGIS → 选引擎导入 → 替换式登记进 GIS 库清单与出图数据源 →
 * 清理临时文件。任何一步失败都会把真实错误抛给前端（不静默），
 * 方便定位桌面端导入时看不到的失败原因。
 *
 * <p>★ 登记用「先删后插」而不是 INSERT IGNORE：land_mvt_source 的 DDL 一直
 * 在仓库外手工维护，code 上有没有唯一键无法保证，INSERT IGNORE 防不防重
 * 全凭运气；先删后插不依赖唯一键，还能顺带清掉历史遗留的重复登记行。
 */
@Slf4j
@Service
public class ShpImportServiceImpl implements IShpImportService {

    /** 本系统 SHP 导入创建的库在清单里的来源标记；只有这类库允许删除 PG 库/覆盖重建 */
    static final String OWN_IMPORT_REMARK = "SHP 上传入库";

    /** 解析登记里存的 JDBC 串，取出 host/port（删库要用，表里没有独立的端口列） */
    private static final Pattern DB_URL_PATTERN = Pattern.compile("^jdbc:postgresql://([^:/]+):(\\d+)/(.+)$");

    /** 预检/探活的连接超时（秒）。管理操作要快失败，不能像导入那样容忍慢库 */
    private static final int PROBE_TIMEOUT_SECS = 8;

    @Autowired
    private SpatialProperties props;

    /** jeecg 主库（MySQL）数据源：land_mvt_source 管理清单表存这里 */
    @Autowired
    private DataSource dataSource;

    @Override
    public SpatialPrecheckVO precheck(String host, int port, String user,
                                      String password, String dbName) throws Exception {
        PgSupport.checkIdentifier(dbName, "数据库名");
        SpatialPrecheckVO vo = new SpatialPrecheckVO();
        List<String> tables = new ArrayList<>();
        try (Connection conn = PgSupport.connect(host, port, dbName, user, password, PROBE_TIMEOUT_SECS);
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(
                     "SELECT schemaname || '.' || tablename FROM pg_tables "
                     + "WHERE schemaname NOT IN ('pg_catalog', 'information_schema') ORDER BY 1")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            vo.setPgExists(true);
            vo.setCredOk(true);
        } catch (SQLException e) {
            if ("3D000".equals(e.getSQLState())) {
                // 库不存在：再用同一凭据连 postgres 维护库，验证凭据本身可用（建库路径靠它）
                try (Connection ignored2 = PgSupport.connect(host, port, "postgres", user, password, PROBE_TIMEOUT_SECS)) {
                    vo.setPgExists(false);
                    vo.setCredOk(true);
                } catch (SQLException e2) {
                    throw friendlyConnectError(host, port, e2);
                }
            } else if ("28P01".equals(e.getSQLState())) {
                throw new SQLException("账号或密码错误，无法连接目标库 " + dbName, e);
            } else {
                throw friendlyConnectError(host, port, e);
            }
        }
        vo.setTables(tables);
        // 清单状态：code 是否已登记、是否本系统导入的库（决定能否删库/覆盖重建）
        List<Map<String, Object>> rows = jdbc().queryForList(
                "SELECT remark FROM land_mvt_source WHERE code = ?", dbName);
        vo.setRegistered(!rows.isEmpty());
        vo.setOwnImported(!rows.isEmpty() && OWN_IMPORT_REMARK.equals(rows.get(0).get("remark")));
        return vo;
    }

    @Override
    public ShpImportResultVO importShp(MultipartFile file, String host, int port,
                                       String user, String password, String dbName,
                                       String tableName, int srid, String importEngine,
                                       String existingPolicy) throws Exception {
        long t0 = System.currentTimeMillis();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传 SHP 的 zip 压缩包");
        }
        PgSupport.checkIdentifier(dbName, "数据库名");

        File workDir = new File(props.getTempDir(), UUID.randomUUID().toString());
        File shpFile = null;
        try {
            // 1) 解压并校验 zip 内容
            shpFile = unzipAndValidate(file, workDir);

            // 2) 表名：未填取 shp 文件名（去扩展名、空白/横线转下划线）
            if (tableName == null || tableName.trim().isEmpty()) {
                String fn = shpFile.getName();
                tableName = fn.substring(0, fn.lastIndexOf('.'))
                        .replaceAll("[\\s-]+", "_");
            }
            PgSupport.checkIdentifier(tableName, "表名");

            // 3) SRID：表单优先，其次 .prj 识别，最后默认 4326
            if (srid <= 0) {
                srid = detectSrid(shpFile);
            }

            // 4) 目标库处理：
            //    overwrite=覆盖重建（整库删除后重建，服务端再验一次"本系统导入"才允许删，
            //              防止绕过前端直调接口删别人的库）；
            //    connect/缺省=连接导入（库不存在才建；存在则原样复用，仅覆盖同名表）。
            if ("overwrite".equalsIgnoreCase(existingPolicy)) {
                requireOwnImported(dbName);
                PgSupport.dropDatabase(host, port, dbName, user, password);
            }
            PgSupport.createDatabaseIfAbsent(host, port, dbName, user, password);
            PgSupport.enablePostgis(host, port, dbName, user, password);
            PgSupport.dropTableIfExists(host, port, dbName, tableName, user, password);

            // 5) 选引擎导入
            String engineName = (importEngine == null || importEngine.trim().isEmpty())
                    ? props.getImportEngine() : importEngine.trim();
            IShpImportEngine engine = "geotools".equalsIgnoreCase(engineName)
                    ? new GeotoolsEngine() : new Shp2pgsqlEngine();

            IShpImportEngine.ShpImportContext ctx = new IShpImportEngine.ShpImportContext();
            ctx.host = host;
            ctx.port = port;
            ctx.user = user;
            ctx.password = password;
            ctx.dbName = dbName;
            ctx.tableName = tableName;
            ctx.srid = srid;
            ctx.shpFile = shpFile;
            ctx.props = props;
            int rows = engine.doImport(ctx);

            // 6) 替换式登记（清单 + 出图数据源都在服务端完成，前端不再重复注册）
            replaceRegistration(dbName, host, port, user, password);

            return ShpImportResultVO.ok(dbName, tableName, rows, engine.name(), srid,
                    System.currentTimeMillis() - t0);
        } finally {
            cleanup(workDir);
        }
    }

    @Override
    public void removeGisSource(String id, boolean dropDatabase) throws Exception {
        JdbcTemplate jdbc = jdbc();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT code, db_url, db_name, db_username, db_password, remark FROM land_mvt_source WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("管理清单中不存在该记录：" + id);
        }
        Map<String, Object> row = rows.get(0);
        String code = (String) row.get("code");
        if (dropDatabase) {
            String remark = (String) row.get("remark");
            if (!OWN_IMPORT_REMARK.equals(remark)) {
                throw new IllegalArgumentException("该库非本系统导入（来源：" + remark + "），不允许删除 PG 库，只能取消登记");
            }
            Matcher m = DB_URL_PATTERN.matcher(String.valueOf(row.get("db_url")));
            if (!m.matches()) {
                throw new IllegalArgumentException("登记的连接串无法解析，无法删除 PG 库：" + row.get("db_url"));
            }
            // 先删库成功，再清登记；失败则整体中止、登记保留以便重试
            // （反过来会出现"库还在、登记没了"的孤儿库，谁也管不到它）
            PgSupport.dropDatabase(m.group(1), Integer.parseInt(m.group(2)),
                    (String) row.get("db_name"),
                    (String) row.get("db_username"), (String) row.get("db_password"));
            log.info("[spatial] 已删除 PG 库：{}（code={}）", row.get("db_name"), code);
        }
        jdbc.update("DELETE FROM land_mvt_source WHERE id = ?", id);
        // 同步清理 sys_data_source 注册（出图连接随注册消失）
        jdbc.update("DELETE FROM sys_data_source WHERE code = ?", code);
        log.info("[spatial] 已从管理清单移除：{}（code={}，删库={}）", id, code, dropDatabase);
    }

    @Override
    public SpatialSourceStatsVO sourceStats(String id) {
        List<Map<String, Object>> rows = jdbc().queryForList(
                "SELECT code, db_url, db_name, db_username, db_password, remark FROM land_mvt_source WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("管理清单中不存在该记录：" + id);
        }
        Map<String, Object> row = rows.get(0);
        SpatialSourceStatsVO vo = new SpatialSourceStatsVO();
        vo.setCode((String) row.get("code"));
        vo.setDbName((String) row.get("db_name"));
        vo.setOwnImported(OWN_IMPORT_REMARK.equals(row.get("remark")));
        vo.setTableCount(-1);
        Matcher m = DB_URL_PATTERN.matcher(String.valueOf(row.get("db_url")));
        if (!m.matches()) {
            vo.setReachable(false);
            return vo;
        }
        try (Connection conn = PgSupport.connect(m.group(1), Integer.parseInt(m.group(2)),
                (String) row.get("db_name"),
                (String) row.get("db_username"), (String) row.get("db_password"), PROBE_TIMEOUT_SECS);
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(
                     "SELECT count(*) FROM pg_tables "
                     + "WHERE schemaname NOT IN ('pg_catalog', 'information_schema')")) {
            vo.setReachable(true);
            if (rs.next()) {
                vo.setTableCount(rs.getInt(1));
            }
        } catch (SQLException e) {
            // 库不可达（已被人手工删除 / 网络不通）不算错误：弹窗里如实显示，删库时幂等放行
            log.warn("[spatial] 探活失败：code={} {}", row.get("code"), e.getMessage());
            vo.setReachable(false);
        }
        return vo;
    }

    /** 校验目标库确实是本系统 SHP 导入创建的，才允许整库删除/覆盖重建 */
    private void requireOwnImported(String dbName) {
        List<Map<String, Object>> rows = jdbc().queryForList(
                "SELECT remark FROM land_mvt_source WHERE code = ?", dbName);
        if (rows.isEmpty() || !OWN_IMPORT_REMARK.equals(rows.get(0).get("remark"))) {
            throw new IllegalArgumentException("该库非本系统导入（"
                    + (rows.isEmpty() ? "未在管理清单中" : rows.get(0).get("remark"))
                    + "），不允许覆盖重建；请更换目标库名，或先在 PG 中手工处理该库");
        }
    }

    private SQLException friendlyConnectError(String host, int port, SQLException e) {
        if ("28P01".equals(e.getSQLState())) {
            return new SQLException("账号或密码错误，无法连接 " + host + ":" + port, e);
        }
        return new SQLException("无法连接 " + host + ":" + port + "：" + e.getMessage(), e);
    }

    /** protected 只为单测替换 MySQL 访问（真实库集成测试不走事务，别在测试里提交 land_mvt_source 数据） */
    protected JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 解压 zip 并校验：只允许一个 .shp，且同名 .dbf/.shx 必须存在。
     * 文件名一律落到工作目录根（zip 内可能带目录层级）。
     */
    private File unzipAndValidate(MultipartFile file, File workDir) throws IOException {
        if (!workDir.mkdirs() && !workDir.isDirectory()) {
            throw new IOException("无法创建临时目录：" + workDir);
        }
        File shp = null;
        String baseName = null;
        // ★ 必须指定 GBK 构造 ZipInputStream：Windows 打的 zip 里中文文件名是 GBK 编码
        // 且无 UTF-8 标志位，JDK 默认按 UTF-8 解码会直接抛 "malformed input off : 0"。
        // GBK 能兼容解码（UTF-8 文件名可能乱码，但 .shp/.dbf 等 ASCII 后缀不受影响）。
        try (InputStream in = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(in, java.nio.charset.Charset.forName("GBK"))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = new File(entry.getName()).getName();
                String lower = name.toLowerCase();
                File out = new File(workDir, name);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    // Java 8 没有 ZipInputStream.transferTo()（Java 9+），手动缓冲复制
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                    }
                }
                if (lower.endsWith(".shp")) {
                    if (shp != null) {
                        throw new IllegalArgumentException("一个压缩包里只能有一个 .shp 文件");
                    }
                    shp = out;
                    baseName = name.substring(0, name.length() - 4);
                }
            }
        }
        if (shp == null) {
            throw new IllegalArgumentException("压缩包里没有找到 .shp 文件");
        }
        // .dbf/.shx 必需（属性与索引），同名检查
        for (String ext : new String[]{".dbf", ".shx"}) {
            if (!new File(workDir, baseName + ext).isFile()) {
                throw new IllegalArgumentException("缺少配套文件：" + baseName + ext
                        + "（shp 必须与 .dbf/.shx 一起打包上传）");
            }
        }
        return shp;
    }

    /**
     * 替换式登记：清单（land_mvt_source）+ 出图数据源（sys_data_source）。
     * 先按 code 删旧再插新——以本次导入的连接信息为准（凭据在预检已验证），
     * 不依赖唯一键防重，还能顺带清掉历史重复行。
     * ★ 为什么登记收口到服务端：原来前端在导入成功后另调
     * /land/spatial/source/register 注册 sys_data_source，但该端点在后端
     * 并不存在，调用被 .catch 静默吞掉——sys_data_source 从未真正登记过。
     * 现在导入成功即登记两表，前端不再参与登记。
     */
    private void replaceRegistration(String dbName, String host, int port, String user, String password) {
        JdbcTemplate jdbc = jdbc();
        jdbc.update("DELETE FROM land_mvt_source WHERE code = ?", dbName);
        jdbc.update("DELETE FROM sys_data_source WHERE code = ?", dbName);
        jdbc.update("INSERT INTO land_mvt_source "
                        + "(id, code, name, db_url, db_name, db_username, db_password, remark, create_time) "
                        + "VALUES (REPLACE(UUID(),'-',''), ?, ?, ?, ?, ?, ?, ?, NOW())",
                dbName, dbName, PgSupport.url(host, port, dbName),
                dbName, user, password, OWN_IMPORT_REMARK);
        jdbc.update("INSERT INTO sys_data_source "
                        + "(id, code, name, db_type, db_driver, db_url, db_name, db_username, db_password) "
                        + "VALUES (REPLACE(UUID(),'-',''), ?, ?, '6', 'org.postgresql.Driver', ?, ?, ?, ?)",
                dbName, dbName + "（SHP 导入）", PgSupport.url(host, port, dbName),
                dbName, user, password);
        log.info("[spatial] 已替换登记 GIS 库：{} -> {}", dbName, PgSupport.url(host, port, dbName));
    }

    /** 从同名 .prj 粗略识别 SRID；识别不出返回 4326 */
    private int detectSrid(File shpFile) {
        String name = shpFile.getName();
        File prj = new File(shpFile.getParentFile(), name.substring(0, name.lastIndexOf('.')) + ".prj");
        if (prj.isFile()) {
            try {
                // Java 11+ 的 Files.readString 不可用，用 readAllBytes 兼容 Java 8
                String wkt = new String(Files.readAllBytes(prj.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                String upper = wkt.toUpperCase();
                if (upper.contains("117") && (upper.contains("CGCS2000") || upper.contains("CHINA_GEODETIC"))) {
                    return 4547; // CGCS2000 3度带 CM 117E（天津地块数据）
                }
                if (upper.contains("WGS_1984") || upper.contains("WGS 84")) return 4326;
                if (upper.contains("XIAN_1980") || upper.contains("XIAN 80")) return 4610;
            } catch (IOException e) {
                log.warn("读取 .prj 失败（忽略，使用默认 SRID）：{}", e.getMessage());
            }
        }
        return 4326;
    }


    private void cleanup(File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            Files.walk(dir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }
}
