package org.jeecg.modules.land.spatial.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.land.spatial.config.SpatialProperties;
import org.jeecg.modules.land.spatial.service.IShpImportService;
import org.jeecg.modules.land.spatial.service.engine.GeotoolsEngine;
import org.jeecg.modules.land.spatial.service.engine.IShpImportEngine;
import org.jeecg.modules.land.spatial.service.engine.Shp2pgsqlEngine;
import org.jeecg.modules.land.spatial.support.PgSupport;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * SHP 上传入库流程编排。
 *
 * <p>步骤：参数/白名单校验 → zip 解压并校验（一个 zip 只能有一个 shp，
 * .dbf/.shx 必须配套）→ 建库（如不存在）→ 启用 PostGIS → 选引擎导入 →
 * 登记进 GIS 库清单（land_mvt_source，MVT 管理页只显示本表）→
 * 清理临时文件。任何一步失败都会把真实错误抛给前端（不静默），
 * 方便定位桌面端导入时看不到的失败原因。
 */
@Slf4j
@Service
public class ShpImportServiceImpl implements IShpImportService {

    @Autowired
    private SpatialProperties props;

    /** jeecg 主库（MySQL）数据源：land_mvt_source 管理清单表存这里 */
    @Autowired
    private DataSource dataSource;

    @Override
    public ShpImportResultVO importShp(MultipartFile file, String host, int port,
                                       String user, String password, String dbName,
                                       String tableName, int srid, String importEngine) throws Exception {
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

            // 4) 建库（如不存在）+ 启用 PostGIS + 服务端先 DROP 旧表（幂等重导）
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

            // 6) 登记进 GIS 库管理清单（MVT 管理页只显示本表；sys_data_source 注册由前端完成）
            registerGisSource(dbName, host, port, user, password);

            return ShpImportResultVO.ok(dbName, tableName, rows, engine.name(), srid,
                    System.currentTimeMillis() - t0);
        } finally {
            cleanup(workDir);
        }
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

    @Override
    public void removeGisSource(String id) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> codes = jdbc.queryForList(
                "SELECT code FROM land_mvt_source WHERE id = ?", String.class, id);
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("管理清单中不存在该记录：" + id);
        }
        jdbc.update("DELETE FROM land_mvt_source WHERE id = ?", id);
        // 同步清理 sys_data_source 注册（出图连接随注册消失）
        jdbc.update("DELETE FROM sys_data_source WHERE code = ?", codes.get(0));
        log.info("[spatial] 已从管理清单移除：{}（code={}）", id, codes.get(0));
    }

    /**
     * 登记进 GIS 库管理清单（land_mvt_source）。MVT 管理页的数据源列表
     * 只读本表——只有通过本系统添加的库才会被管理。重复登记忽略。
     */
    /** 注册 sys_data_source（MVT 出图的 DynamicDBUtil 按此表取连接，密码存明文） */
    @Override
    public void registerSysDataSource(String code, String host, int port,
                                      String user, String password, String dbName) {
        try {
            new JdbcTemplate(dataSource).update(
                    "INSERT IGNORE INTO sys_data_source "
                    + "(id, code, name, db_type, db_driver, db_url, db_name, db_username, db_password) "
                    + "VALUES (REPLACE(UUID(),'-',''), ?, ?, '6', 'org.postgresql.Driver', ?, ?, ?, ?)",
                    code, code + "（SHP 导入）",
                    "jdbc:postgresql://" + host + ":" + port + "/" + dbName,
                    dbName, user, password);
            log.info("[spatial] 已注册数据源：{} -> {}", code, dbName);
        } catch (Exception e) {
            log.warn("[spatial] 数据源注册跳过：{}", e.getMessage());
        }
    }

    private void registerGisSource(String dbName, String host, int port, String user, String password) {
        try {
            new JdbcTemplate(dataSource).update(
                    "INSERT IGNORE INTO land_mvt_source "
                            + "(id, code, name, db_url, db_name, db_username, db_password, remark, create_time) "
                            + "VALUES (REPLACE(UUID(),'-',''), ?, ?, ?, ?, ?, ?, 'SHP 上传入库', NOW())",
                    dbName, dbName, "jdbc:postgresql://" + host + ":" + port + "/" + dbName,
                    dbName, user, password);
            log.info("[spatial] 已登记 GIS 库清单：{}", dbName);
        } catch (Exception e) {
            log.warn("[spatial] GIS 库清单登记跳过：{}", e.getMessage());
        }
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
