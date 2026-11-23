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
            // 可写探测：大屏图形编辑要往切片源库写几何，登记了只读账号的库
            // 会在录入前就暴露（以「能否建表」近似判断写权限，CREATE 覆盖面最宽）
            try (java.sql.ResultSet ws = st.executeQuery(
                    "SELECT has_database_privilege(current_user, current_database(), 'CREATE')")) {
                vo.setWritable(ws.next() && ws.getBoolean(1));
            } catch (SQLException ignore) {
                // 探测本身失败不拦预检：写权限以 unknown（false）呈现，让前端如实提示
            }
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
        boolean appendMode = "append".equalsIgnoreCase(existingPolicy);
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

            // 3.5) 追加模式 .prj 优先：追加文件的真实基准以 .prj 声明为准，
            //      与表单默认值不一致时必须改标（2026-11-15 实测踩坑：追加 SHP 是
            //      CM 120E 基准，被表单默认 4548 错标，坐标整体错位一个带宽——
            //      同一份数据在图层上分裂成两团）。临时表按真实 SRID 标注后，
            //      appendStagedToTarget 的 ST_Transform 会自动转到目标表基准。
            if (appendMode) {
                int prjSrid = detectSrid(shpFile);
                if (prjSrid > 0 && prjSrid != srid) {
                    log.info("[spatial] 追加导入：.prj 声明 SRID={} 与表单 SRID={} 不一致，以 .prj 为准", prjSrid, srid);
                    srid = prjSrid;
                }
            }

            // 4) 目标库处理：
            //    overwrite=覆盖重建（整库删除后重建，服务端再验一次"本系统导入"才允许删，
            //              防止绕过前端直调接口删别人的库）；
            //    connect/缺省=连接导入（库不存在才建；存在则原样复用，仅覆盖同名表）；
            //    append=追加到现有表（目标表必须已存在，不覆盖、不建表——shp 数据先进
            //            imp_ 临时表，导入成功后按列交集 INSERT SELECT 进目标表再删临时表）。
            if ("overwrite".equalsIgnoreCase(existingPolicy)) {
                requireOwnImported(dbName);
                PgSupport.dropDatabase(host, port, dbName, user, password);
            }
            PgSupport.createDatabaseIfAbsent(host, port, dbName, user, password);
            PgSupport.enablePostgis(host, port, dbName, user, password);
            if (appendMode) {
                requireTargetTable(host, port, dbName, tableName, user, password);
            } else {
                PgSupport.dropTableIfExists(host, port, dbName, tableName, user, password);
            }

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
            ctx.appendMode = appendMode;
            int rows = engine.doImport(ctx);

            if (appendMode) {
                // 追加：按列交集把临时表数据并入目标表（几何按目标列 SRID/维度转换），
                // 返回追加行数；临时表用完即删（失败时本步异常，临时表随 DROP 清理由
                // 下方 catch 外的 cleanup 兜底——临时表名以 imp_ 前缀，不影响目标表）
                rows = appendStagedToTarget(host, port, dbName, tableName,
                        ctx.stagedTable, user, password);
            }

            // 6) 替换式登记（清单 + 出图数据源都在服务端完成，前端不再重复注册）
            replaceRegistration(dbName, host, port, user, password);

            return ShpImportResultVO.ok(dbName, tableName, rows, engine.name(), srid,
                    System.currentTimeMillis() - t0);
        } finally {
            cleanup(workDir);
        }
    }

    /** 追加模式前置校验：目标表必须已存在（append 不建表、不覆盖，否则明确报错指路） */
    private void requireTargetTable(String host, int port, String dbName,
                                    String tableName, String user, String password) throws Exception {
        try (Connection conn = PgSupport.connect(host, port, dbName, user, password);
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                     + " WHERE n.nspname = 'public' AND c.relname = '" + tableName.replace("'", "''") + "'"
                     + " AND c.relkind = 'r'")) {
            if (rs.next() && rs.getInt(1) == 0) {
                throw new IllegalArgumentException("目标表不存在：" + tableName
                        + "。请先完成该表的全量导入（选「连接导入」），或改选新建库导入");
            }
        } catch (SQLException e) {
            throw new Exception("检查目标表失败：" + e.getMessage(), e);
        }
    }

    /**
     * 落位自检：临时表按声明 SRID 试转 4326 后，与**目标表现有数据的 bbox** 相交
     * （放宽 0.25°）才算落位；不落位时依次尝试 3 度带邻带与常用基准，取第一个
     * 落位命中项 —— **数据驱动自动纠偏**。
     * ★ 判据演进：初版用"落在中国范围"（73~136E）——太宽，CM 差一个带宽错位 3°
     *   后数据落在 113°E 依然"在中国"，防线形同虚设（2026-11-15 实测）；
     *   追加的正确落位就是**目标表附近**，锚定它才检测得出带宽错位。
     *
     * @param anchor 目标表现有数据的 4326 bbox（[xmin,ymin,xmax,ymax]；目标表为空时为 null，退回中国范围判定）
     * @return 实测能把临时表数据转到目标表附近的源 SRID（全部失败时原样返回声明值）
     */
    private int detectTrueSrid(java.sql.Statement st, String stagedTable, int declaredSrid,
                               double[] anchor) throws SQLException {
        if (inPlace(st, stagedTable, declaredSrid, anchor)) {
            return declaredSrid;
        }
        int[] candidates = {4549, 4548, 4547, 4550, 4546, 4551, 4545, 4552, 4553, 4544, 4554, 4490, 4526, 4326};
        for (int cand : candidates) {
            if (cand == declaredSrid) continue;
            if (inPlace(st, stagedTable, cand, anchor)) {
                log.warn("[spatial] 追加落位自检：声明 SRID={} 转换后与目标表位置不符，按实测改用 SRID={}",
                        declaredSrid, cand);
                return cand;
            }
        }
        return declaredSrid;
    }

    /**
     * 临时表按指定 SRID 转 4326 后的 bbox 是否与锚（目标表 bbox 放宽 0.25°）相交；
     * 无锚（目标表为空）时退回"落在中国范围"（73~136E, 3~55N）。
     */
    private boolean inPlace(java.sql.Statement st, String stagedTable, int srid, double[] anchor) throws SQLException {
        double[] b = stagedBbox4326(st, stagedTable, srid);
        if (b == null) return false;
        if (anchor != null && anchor.length == 4) {
            double pad = 0.25;
            return b[0] <= anchor[2] + pad && b[2] >= anchor[0] - pad
                    && b[1] <= anchor[3] + pad && b[3] >= anchor[1] - pad;
        }
        return b[0] >= 73 && b[2] <= 136 && b[1] >= 3 && b[3] <= 55;
    }

    /** 临时表按指定 SRID 转 4326 后的 bbox（[xmin,ymin,xmax,ymax]；空表返回 null） */
    private double[] stagedBbox4326(java.sql.Statement st, String stagedTable, int srid) throws SQLException {
        try (java.sql.ResultSet rs = st.executeQuery(
                "SELECT ST_XMin(e)::float8, ST_YMin(e)::float8, ST_XMax(e)::float8, ST_YMax(e)::float8 FROM ("
                + "SELECT ST_Extent(ST_Transform(ST_SetSRID(geom, " + srid + "), 4326)) AS e FROM "
                + quoteIdent(stagedTable) + " WHERE geom IS NOT NULL) t")) {
            if (rs.next() && rs.getObject(1) != null) {
                return new double[]{rs.getDouble(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)};
            }
        }
        return null;
    }

    /**
     * 追加：把临时表（shp2pgsql 全量导入的 imp_ 表）按**列交集**并入目标表。
     *   · 几何列单独转换：临时表 SRID（geometry_columns 实测）→ 目标 SRID（ST_Transform）
     *     → 按目标列真实 typmod 的 Z/M 标志 Force（复用 findGeomColumnAndSrid 口径）；
     *   · 属性列取两表交集（排除 gid/__oid/oid_1 引擎自加列与几何列），目标表独有列留空，
     *     gid 不写入（走目标表自增）；
     *   · 追加 + 删临时表同一事务，失败整体回滚——目标表原有数据分毫不动。
     * @return 追加的行数
     */
    private int appendStagedToTarget(String host, int port, String dbName, String targetTable,
                                     String stagedTable, String user, String password) throws Exception {
        if (stagedTable == null || stagedTable.trim().isEmpty()) {
            throw new IllegalStateException("引擎未回填临时表名，无法追加");
        }
        try (Connection conn = PgSupport.connect(host, port, dbName, user, password);
             java.sql.Statement st = conn.createStatement()) {
            // 1) 目标表几何元数据
            String[] tgtGeom = findGeomColumnAndSrid(st, targetTable);
            // 2) 临时表几何 SRID 与**落位自检**：
            //    SRID 声明链（.prj 识别 / 表单 / 临时表注册）多次实测不可靠——CM 差一个
            //    带宽，数据整体错位 3°（2026-11-15 五轮踩坑）。落位判据是**目标表现有
            //    数据的 bbox**（追加数据必须落在它附近；"落在中国范围"的初版判据太宽，
            //    错 3° 后落在 113°E 依然"在中国"，防线形同虚设）——数据驱动自动纠偏。
            int stagedSrid = 4326;
            try (java.sql.ResultSet rs = st.executeQuery(
                    "SELECT COALESCE(srid::text, '4326') FROM geometry_columns"
                    + " WHERE f_table_schema = 'public' AND f_table_name = '" + stagedTable.replace("'", "''") + "' LIMIT 1")) {
                if (rs.next()) {
                    stagedSrid = rs.getInt(1);
                }
            }
            // 落位锚：目标表现有数据的 4326 bbox（空表时 anchor=null，退回中国范围判定）
            double[] anchor = null;
            try (java.sql.ResultSet rs = st.executeQuery(
                    "SELECT ST_XMin(e)::float8, ST_YMin(e)::float8, ST_XMax(e)::float8, ST_YMax(e)::float8 FROM ("
                    + "SELECT ST_Extent(ST_Transform(geom, 4326)) AS e FROM "
                    + quoteIdent(targetTable) + " WHERE geom IS NOT NULL) t")) {
                if (rs.next() && rs.getObject(1) != null) {
                    anchor = new double[]{rs.getDouble(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)};
                }
            }
            stagedSrid = detectTrueSrid(st, stagedTable, stagedSrid, anchor);
            // 3) 属性列交集（排除引擎自加列与几何列；目标表独有列追加时留空）
            List<String> commonCols = new ArrayList<>();
            try (java.sql.ResultSet rs = st.executeQuery(
                    "SELECT a.column_name FROM information_schema.columns a"
                    + " WHERE a.table_schema = 'public' AND a.table_name = '" + targetTable.replace("'", "''") + "'"
                    + " AND a.column_name IN (SELECT column_name FROM information_schema.columns"
                    + "   WHERE table_schema = 'public' AND table_name = '" + stagedTable.replace("'", "''") + "')"
                    + " AND a.column_name NOT IN ('gid', '__oid', 'oid_1', '" + tgtGeom[0].replace("'", "''") + "')"
                    + " ORDER BY a.ordinal_position")) {
                while (rs.next()) {
                    commonCols.add(rs.getString(1));
                }
            }
            // 4) 追加 SQL：INSERT SELECT（几何维度对齐链 = 临时SRID → 目标SRID → Z/M Force）
            String geomExpr = "ST_Transform(ST_SetSRID(geom, " + stagedSrid + "), " + tgtGeom[1] + ")";
            boolean hasZ = "1".equals(tgtGeom[2]);
            boolean hasM = "1".equals(tgtGeom[3]);
            if (hasZ && hasM) {
                geomExpr = "ST_Force4D(" + geomExpr + ")";
            } else if (hasZ) {
                geomExpr = "ST_Force3D(" + geomExpr + ")";
            } else if (hasM) {
                geomExpr = "ST_Force3DM(" + geomExpr + ", 0)";
            } else {
                geomExpr = "ST_Force2D(" + geomExpr + ")";
            }
            StringBuilder cols = new StringBuilder();
            StringBuilder sel = new StringBuilder();
            for (String col : commonCols) {
                String q = quoteIdent(col);
                if (cols.length() > 0) {
                    cols.append(", ");
                    sel.append(", ");
                }
                cols.append(q);
                sel.append(q);
            }
            String insert = "INSERT INTO " + quoteIdent(targetTable)
                    + " (" + (cols.length() > 0 ? cols + ", " : "") + quoteIdent(tgtGeom[0]) + ")"
                    + " SELECT " + (sel.length() > 0 ? sel + ", " : "") + geomExpr
                    + " FROM " + quoteIdent(stagedTable);
            conn.setAutoCommit(false);
            try {
                int n = st.executeUpdate(insert);
                st.execute("DROP TABLE IF EXISTS " + quoteIdent(stagedTable));
                conn.commit();
                return n;
            } catch (Exception ex) {
                try {
                    conn.rollback();
                } catch (SQLException ignore) {
                    // 回滚失败时真实异常照原样上抛
                }
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignore) {
                    // 归还连接前的状态恢复，失败无害
                }
            }
        } catch (SQLException e) {
            throw new Exception("追加切片数据失败：" + e.getMessage(), e);
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
                    resolveDbName(row, m),
                    resolveRegField(row, "db_username"), resolveRegField(row, "db_password"));
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
                resolveDbName(row, m),
                resolveRegField(row, "db_username"), resolveRegField(row, "db_password"), PROBE_TIMEOUT_SECS);
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

    /**
     * 按唯一值查切片源表要素范围（大屏列表定位的数据源，替代 dk_geojson）。
     *
     * 流程：land_mvt_source 按 code 取连接 → 连对应 PG 库 → geometry_columns
     * 取几何列 → ST_Extent(ST_Transform(geom,4326)) WHERE 唯一值字段 = 值
     * （精确 + 双向去「号」尾匹配，见 matchGroupWhere；多要素取并集范围）→ [minLon,minLat,maxLon,maxLat]。
     * ★ 值匹配用 PreparedStatement 参数化（值是业务数据，可能含引号等字符）。
     */
    @Override
    public java.util.List<Double> sliceFeatureBounds(String source, String table, String field, String value) throws Exception {
        PgSupport.checkIdentifier(table, "表名");
        PgSupport.checkIdentifier(field, "唯一值字段");
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("唯一值不能为空");
        }
        // 源注册：连接串与口令在 land_mvt_source（发布时登记）
        // ★ db_name 必须查出来：历史版本漏查这一列，reg.get("db_name") 恒为 null，
        //   String.valueOf 把字面量 "null" 传给 PG → 「数据库 "null" 不存在」
        //   （2026-11-15 定位首次真实执行时踩到；即便列缺失，resolveDbName
        //   也会回退连接串里的库名兜底）
        JdbcTemplate jdbc = jdbc();
        java.util.List<Map<String, Object>> regs = jdbc.queryForList(
                "SELECT db_url, db_name, db_username, db_password FROM land_mvt_source WHERE code = ?", source);
        if (regs.isEmpty()) {
            throw new IllegalArgumentException("切片源不存在：" + source);
        }
        Map<String, Object> reg = regs.get(0);
        Matcher m = DB_URL_PATTERN.matcher(String.valueOf(reg.get("db_url")));
        if (!m.matches()) {
            throw new IllegalArgumentException("登记的连接串无法解析：" + reg.get("db_url"));
        }
        String quotedTable = "\"" + table.replace("\"", "\"\"") + "\"";
        try (Connection pg = PgSupport.connect(m.group(1), Integer.parseInt(m.group(2)),
                resolveDbName(reg, m),
                resolveRegField(reg, "db_username"), resolveRegField(reg, "db_password"), PROBE_TIMEOUT_SECS);
             java.sql.Statement st = pg.createStatement()) {
            String quotedField = quoteIdent(resolveGroupField(st, table, field));
            // 几何列动态取：shp2pgsql 导入的表是 geom、geotools 引擎是 SHAPE，两套引擎可差异
            String geomCol = null;
            try (java.sql.ResultSet rs = st.executeQuery(
                    "SELECT f_geometry_column FROM geometry_columns WHERE f_table_schema = 'public'"
                    + " AND f_table_name = '" + table.replace("'", "''") + "' LIMIT 1")) {
                if (rs.next()) geomCol = rs.getString(1);
            }
            if (geomCol == null) {
                throw new IllegalArgumentException("切片源表没有几何列：" + table);
            }
            String quotedGeom = "\"" + geomCol.replace("\"", "\"\"") + "\"";
            // 值匹配（读路径三处与写路径两处统一口径，见 matchGroupWhere）：
            // 精确相等 + 双向去「号」尾相等 —— 实测业务表编号带「号」尾（津东万（挂）2018-202号）、
            // 切片源表不带（…202），单向 LIKE 在业务值比源表值长时永远命中不了；
            // 通配 LIKE 会造成「1号」这类短值的大面积误伤，故用 RTRIM 精确方案
            String sql = "SELECT ST_XMin(b),ST_YMin(b),ST_XMax(b),ST_YMax(b) FROM ("
                    + "SELECT ST_Extent(ST_Transform(" + quotedGeom + ",4326)) AS b FROM " + quotedTable
                    + " WHERE " + matchGroupWhere(quotedField) + ") e";
            try (java.sql.PreparedStatement ps = pg.prepareStatement(sql)) {
                ps.setString(1, value);
                ps.setString(2, value);
                ps.setString(3, value);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        long damaged = countEncodingDamagedRows(st, quotedTable, quotedField);
                        if (damaged > 0) {
                            throw new IllegalArgumentException(encodingDamagedMessage(quotedTable, damaged, value));
                        }
                        throw new IllegalArgumentException("切片图层中未找到该记录的图形（唯一值：" + value + "）");
                    }
                    java.util.List<Double> bounds = new java.util.ArrayList<>();
                    for (int i = 1; i <= 4; i++) {
                        bounds.add(rs.getDouble(i));
                    }
                    return bounds;
                }
            }
        } catch (SQLException e) {
            throw new Exception("查询切片要素范围失败：" + e.getMessage(), e);
        }
    }

    /* ------------------------------------------------------------------
     * 要素组编辑（大屏图形管理：回显 / 整组替换 / 整组删除）。
     * 与 sliceFeatureBounds 同源：land_mvt_source 按 code 取连接，
     * geometry_columns 动态取几何列 —— 抽出 openSliceSource / findGeomColumn
     * 两个私有步骤，三个写方法不再各抄一遍连接样板。
     * ------------------------------------------------------------------ */

    /** 双引号包裹 PG 标识符（中文表名/列名安全；内嵌双引号翻倍转义） */
    private static String quoteIdent(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    /**
     * 取登记行的字符串字段：列缺失 / NULL / 空串统一规整为空串。
     * ★ 不能用 String.valueOf 兜底 —— 列为 NULL 时它会产出字面量 "null"，
     *   传给 PG 连接就成了「数据库 "null" 不存在」这种真实踩过的报错。
     */
    private static String resolveRegField(Map<String, Object> reg, String key) {
        Object value = reg.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        return "null".equals(text) ? "" : text;
    }

    /**
     * 登记行的库名：db_name 列为准；列为空回退连接串里的库名（m.group(3)）。
     * ★ 历史登记行可能缺 db_name（2026-11-15 定位实测踩到「数据库 "null" 不存在」）——
     *   该库发布能出图说明连接串里的库名有效，回退它即可救活全部旧登记，不要求用户补数据。
     */
    private static String resolveDbName(Map<String, Object> reg, Matcher urlMatcher) {
        String name = resolveRegField(reg, "db_name");
        if (name.isEmpty()) {
            name = urlMatcher.group(3);
            int q = name.indexOf('?');
            if (q > -1) {
                name = name.substring(0, q);
            }
        }
        return name;
    }

    /** 按 code 打开切片源 PG 连接（连接串解析与口令不出服务端，口径同 sliceFeatureBounds） */
    private Connection openSliceSource(String source) throws Exception {
        List<Map<String, Object>> regs = jdbc().queryForList(
                "SELECT db_url, db_name, db_username, db_password FROM land_mvt_source WHERE code = ?", source);
        if (regs.isEmpty()) {
            throw new IllegalArgumentException("切片源不存在：" + source);
        }
        Map<String, Object> reg = regs.get(0);
        Matcher m = DB_URL_PATTERN.matcher(String.valueOf(reg.get("db_url")));
        if (!m.matches()) {
            throw new IllegalArgumentException("登记的连接串无法解析：" + reg.get("db_url"));
        }
        return PgSupport.connect(m.group(1), Integer.parseInt(m.group(2)),
                resolveDbName(reg, m),
                resolveRegField(reg, "db_username"), resolveRegField(reg, "db_password"), PROBE_TIMEOUT_SECS);
    }

    /**
     * geometry_columns 动态取几何列与 SRID；Z/M 维度改从 **pg_attribute.atttypmod**
     * 位标志解析（PostGIS 编码：0x20000000=Z、0x10000000=M，低 29 位为 SRID）。
     * ★ 为什么不再用 geometry_columns.type/coord_dimension：实测 shp2pgsql 建出的表
     *   两者与真实 typmod **不一致**——写入检查只认 typmod，跟着声明选维度函数
     *   出现了「缺 Z → 加 Z → 报缺 M → 加 M → 又报缺 Z」的连环打脸（2026-11-15
     *   三轮实测）。atttypmod 是插入检查的唯一依据，解析它不存在再猜错的空间。
     * @return [几何列名, srid 文本, hasZ("1"/"0"), hasM("1"/"0")]
     */
    private String[] findGeomColumnAndSrid(java.sql.Statement st, String table) throws SQLException {
        String geomCol = null;
        String srid = "4326";
        try (java.sql.ResultSet rs = st.executeQuery(
                "SELECT f_geometry_column, COALESCE(srid::text, '4326') FROM geometry_columns"
                + " WHERE f_table_schema = 'public' AND f_table_name = '" + table.replace("'", "''") + "' LIMIT 1")) {
            if (rs.next()) {
                geomCol = rs.getString(1);
                srid = rs.getString(2);
            }
        }
        if (geomCol == null) {
            throw new IllegalArgumentException("切片源表没有几何列：" + table);
        }
        // 从列的真实 typmod 解析 Z/M（format_type 是 PG 官方的 typmod 可读形式，
        // 如 "geometry(MultiPolygonZM,4548)" —— 与插入检查完全同源；
        // ★ 不手工解 atttypmod 位标志：PostGIS 3.x 的位编码与 2.x 不同，实测踩坑）
        String hasZ = "0";
        String hasM = "0";
        try (java.sql.ResultSet rs = st.executeQuery(
                "SELECT COALESCE(format_type(a.atttypid, a.atttypmod), 'geometry') FROM pg_attribute a"
                + " WHERE a.attrelid = ('public." + table.replace("'", "''") + "')::regclass"
                + " AND a.attname = '" + geomCol.replace("'", "''") + "' AND a.attnum > 0 LIMIT 1")) {
            if (rs.next()) {
                String typmodText = rs.getString(1);
                hasZ = typmodText.contains("Z") ? "1" : "0";
                hasM = typmodText.contains("M") ? "1" : "0";
            }
        }
        return new String[]{geomCol, srid, hasZ, hasM};
    }

    /** 三要素组方法共用的参数校验 */
    private void checkGroupArgs(String table, String field, String value) {
        PgSupport.checkIdentifier(table, "表名");
        PgSupport.checkIdentifier(field, "唯一值字段");
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("唯一值不能为空");
        }
    }

    /**
     * 把接口的逻辑字段名解析成切片表里的真实列名（大小写不敏感）。
     *
     * <p>SHP 导入会保留 DBF 列名的大小写；现有地块表就是 {@code CRZDBH}，而前端契约
     * 统一传小写 {@code crzdbh}。PostgreSQL 双引号标识符区分大小写，直接 quote 入参会让
     * 图形回显、整组保存和定位一起报「column crzdbh does not exist」。以 pg_attribute
     * 的真实列名为准，接口字段契约不变，也不允许模糊匹配到其它列。
     */
    static String resolveGroupField(java.sql.Statement st, String table, String field) throws SQLException {
        String sql = "SELECT a.attname FROM pg_attribute a"
                + " WHERE a.attrelid = ('public." + table.replace("'", "''") + "')::regclass"
                + " AND a.attnum > 0 AND NOT a.attisdropped"
                + " AND LOWER(a.attname) = LOWER('" + field.replace("'", "''") + "') LIMIT 1";
        try (java.sql.ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        throw new IllegalArgumentException("切片源表没有唯一值字段：" + field);
    }

    /**
     * 唯一值匹配的统一 WHERE（读写路径共用，占位符恰好 3 个，按 value/value/value 传参）：
     *   f = v
     *   OR RTRIM(v,'号') = f   —— 业务值多「号」尾时命中源表值（实测主口径差）
     *   OR RTRIM(f,'号') = v   —— 源表值多「号」尾时命中业务值（反向兼容）
     * 用 RTRIM 精确相等而不是双向 LIKE：LIKE 会在「1号」这类短值上大面积误伤
     * （读会串图形、写会误删别人的块）。
     */
    private static String matchGroupWhere(String quotedField) {
        return quotedField + " = ? OR RTRIM(?, '号') = " + quotedField
                + " OR RTRIM(" + quotedField + ", '号') = ?";
    }

    /**
     * 编码损坏时的统一报错文案：说清「是什么问题 + 怎么修」，不让人以为是地图/定位功能坏了。
     */
    private static String encodingDamagedMessage(String quotedTable, long damaged, String value) {
        return "切片库「" + quotedTable + "」有 " + damaged + " 行编号中文已损坏（显示为「\uFFFD」），"
                + "与业务编号「" + value + "」无法匹配，因此定位/图形同步会找不到要素。"
                + "这是 SHP 导入时的字符编码丢失（不可逆），请按原始 DBF 编码（一般为 GBK）"
                + "重新导入该 SHP 后再操作。";
    }

    /**
     * 匹配 0 行时判定「是不是切片库存量中文编码损坏」。
     *
     * <p><b>为什么需要这一步</b>：1118 报告的「双击宗地定位报错」「移除后地图图形不消失」
     * 都是同一个现象——切片源表的编号列里存的是 U+FFFD（'�'，编码转换失败的替换字符），
     * 例如 {@code 娲ヨ摕锛堟寕锛�2017-025}，与业务库的「津东丽方（挂）2024-40号」
     * 永远不可能相等。此时读（定位、图形回显）命中 0 行，写（整组替换/删除）也命中 0 行，
     * 表面看像"功能坏了"，实际是**存量数据在导入时丢了编码**，且 U+FFFD 不可逆。
     *
     * <p>所以这里只在"没匹配到"的分支多查一次，把结论直接写进报错里：
     * 让用户知道要重新导入 SHP，而不是反复重试定位或怀疑地图组件。
     *
     * @return 损坏行数；0 表示不是编码问题（正常"该记录没有图形"）
     */
    private long countEncodingDamagedRows(java.sql.Statement st, String quotedTable, String quotedField) {
        try (java.sql.ResultSet rs = st.executeQuery(
                "SELECT count(*) FROM " + quotedTable + " WHERE " + quotedField + " LIKE '%\uFFFD%'")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            // 探测失败不能盖掉主流程的真实报错
            log.debug("探测切片源编码损坏失败：{}", e.getMessage());
        }
        return 0L;
    }

    /**
     * 回显某唯一值名下的全部要素几何（图形管理弹窗打开时用）。
     * ★ 匹配口径同 matchGroupWhere（精确 + 双向去「号」尾）：同地块的「号」尾
     *   口径差要认（业务表编号普遍带「号」、源表不带）；但不会出现通配 LIKE
     *   把别家图形拉进编辑弹窗的问题。
     */
    @Override
    public java.util.List<String> sliceFeatureGeometries(String source, String table, String field, String value) throws Exception {
        checkGroupArgs(table, field, value);
        try (Connection pg = openSliceSource(source);
             java.sql.Statement st = pg.createStatement()) {
            String[] geom = findGeomColumnAndSrid(st, table);
            String actualField = resolveGroupField(st, table, field);
            String sql = "SELECT ST_AsGeoJSON(ST_Transform(" + quoteIdent(geom[0]) + ",4326)) FROM " + quoteIdent(table)
                    + " WHERE " + matchGroupWhere(quoteIdent(actualField));
            List<String> result = new ArrayList<>();
            try (java.sql.PreparedStatement ps = pg.prepareStatement(sql)) {
                ps.setString(1, value);
                ps.setString(2, value);
                ps.setString(3, value);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString(1));
                    }
                }
            }
            if (result.isEmpty()) {
                long damaged = countEncodingDamagedRows(st, quoteIdent(table), quoteIdent(actualField));
                if (damaged > 0) {
                    throw new IllegalArgumentException(encodingDamagedMessage(quoteIdent(table), damaged, value));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new Exception("回显切片要素失败：" + e.getMessage(), e);
        }
    }

    /**
     * 整组替换某唯一值名下的要素几何：事务内先删全部旧行、再逐条插入本次几何
     * （列表为空 = 显式清空整组）。中途任何一步失败整体回滚，不留半组图形。
     * 坐标系：前端提交 WGS84 GeoJSON，落库前 ST_Transform 到源表 SRID。
     */
    @Override
    public int saveSliceFeatureGroup(String source, String table, String field, String value,
                                     java.util.List<String> geometries) throws Exception {
        checkGroupArgs(table, field, value);
        List<String> geoms = geometries == null ? new ArrayList<String>() : geometries;
        try (Connection pg = openSliceSource(source);
             java.sql.Statement st = pg.createStatement()) {
            String[] geom = findGeomColumnAndSrid(st, table);
            String actualField = resolveGroupField(st, table, field);
            pg.setAutoCommit(false);
            try {
                try (java.sql.PreparedStatement del = pg.prepareStatement(
                        "DELETE FROM " + quoteIdent(table) + " WHERE " + matchGroupWhere(quoteIdent(actualField)))) {
                    del.setString(1, value);
                    del.setString(2, value);
                    del.setString(3, value);
                    int removed = del.executeUpdate();
                    if (removed == 0) {
                        // 同 deleteSliceFeatureGroup：0 行可能是"本来没有"，也可能是编号乱码删不掉。
                        // 后者会让「编辑后旧图形仍在」看起来像保存失败，日志里必须留证据。
                        long damaged = countEncodingDamagedRows(st, quoteIdent(table), quoteIdent(actualField));
                        if (damaged > 0) {
                            log.warn("整组替换前删除旧图形命中 0 行：{}",
                                    encodingDamagedMessage(quoteIdent(table), damaged, value));
                        }
                    }
                }
                if (!geoms.isEmpty()) {
                    // ★ 按列真实 typmod 的 Z/M 标志对齐写入几何维度（见
                    //   findGeomColumnAndSrid 的注释——atttypmod 是插入检查的唯一依据，
                    //   geometry_columns 的声明实测与它不一致）
                    String geomExpr = "ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326), " + geom[1] + ")";
                    boolean hasZ = "1".equals(geom[2]);
                    boolean hasM = "1".equals(geom[3]);
                    if (hasZ && hasM) {
                        geomExpr = "ST_Force4D(" + geomExpr + ")";
                    } else if (hasZ) {
                        geomExpr = "ST_Force3D(" + geomExpr + ")";
                    } else if (hasM) {
                        geomExpr = "ST_Force3DM(" + geomExpr + ", 0)";
                    } else {
                        geomExpr = "ST_Force2D(" + geomExpr + ")";
                    }
                    String insert = "INSERT INTO " + quoteIdent(table) + " (" + quoteIdent(actualField) + ", "
                            + quoteIdent(geom[0]) + ") VALUES (?, " + geomExpr + ")";
                    try (java.sql.PreparedStatement ins = pg.prepareStatement(insert)) {
                        for (String g : geoms) {
                            if (g == null || g.trim().isEmpty()) {
                                throw new IllegalArgumentException("提交的几何列表中存在空图形");
                            }
                            ins.setString(1, value);
                            ins.setString(2, g);
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                pg.commit();
            } catch (Exception ex) {
                try {
                    pg.rollback();
                } catch (SQLException ignore) {
                    // 连接已坏时回滚本就无意义，真实异常照原样上抛
                }
                throw ex;
            } finally {
                try {
                    pg.setAutoCommit(true);
                } catch (SQLException ignore) {
                    // 归还连接前的状态恢复，失败无害（连接随即关闭）
                }
            }
            return geoms.size();
        } catch (SQLException e) {
            throw new Exception("保存切片要素失败：" + e.getMessage(), e);
        }
    }

    /**
     * 整组删除某唯一值名下的全部要素几何（移除记录联动清图形 / 改编号时先删旧组）。
     * 顺带做一次几何列校验：防止调用方误传无几何列的普通业务表。
     */
    @Override
    public int deleteSliceFeatureGroup(String source, String table, String field, String value) throws Exception {
        checkGroupArgs(table, field, value);
        try (Connection pg = openSliceSource(source);
             java.sql.Statement st = pg.createStatement()) {
            findGeomColumnAndSrid(st, table);
            String actualField = resolveGroupField(st, table, field);
            try (java.sql.PreparedStatement ps = pg.prepareStatement(
                    "DELETE FROM " + quoteIdent(table) + " WHERE " + matchGroupWhere(quoteIdent(actualField)))) {
                ps.setString(1, value);
                ps.setString(2, value);
                ps.setString(3, value);
                int deleted = ps.executeUpdate();
                if (deleted == 0) {
                    // 删除 0 行有两种可能：本来就没有图形（正常），或切片库编号已乱码删不掉。
                    // 后者正是 1118「移除后地图地块仍在」的现象来源 —— 至少要在日志里说清楚。
                    long damaged = countEncodingDamagedRows(st, quoteIdent(table), quoteIdent(actualField));
                    if (damaged > 0) {
                        log.warn("删除切片要素命中 0 行：{}", encodingDamagedMessage(quoteIdent(table), damaged, value));
                    }
                }
                return deleted;
            }
        } catch (SQLException e) {
            throw new Exception("删除切片要素失败：" + e.getMessage(), e);
        }
    }

    /**
     * 切片源表要素搜索（地名定位 / 空间查询共用的数据底座）。
     *   · keyword：对该表全部字符类型列 LIKE 模糊匹配（任一列命中即返回）；
     *   · bbox：可选空间过滤（EPSG:4326 包围盒与要素相交）；
     *   · 返回每条的全部属性列（字符串化）+ WGS84 GeoJSON + 包围盒，
     *     前端据此列表展示、点击定位与高亮。
     * ★ 列清单动态取 information_schema（字符类型列），SHP 导入的截断列名/中文列名都兼容。
     */
    @Override
    public java.util.List<java.util.Map<String, Object>> featureSearch(String source, String table, String keyword,
                                                                       String bbox, int limit) throws Exception {
        PgSupport.checkIdentifier(table, "表名");
        int safeLimit = Math.max(1, Math.min(200, limit <= 0 ? 50 : limit));
        try (Connection pg = openSliceSource(source);
             java.sql.Statement st = pg.createStatement()) {
            String[] geom = findGeomColumnAndSrid(st, table);
            String quotedGeom = quoteIdent(geom[0]);
            // 1) 字符类型列清单（keyword 模糊匹配的目标）
            java.util.List<String> textCols = new ArrayList<>();
            try (java.sql.ResultSet rs = st.executeQuery(
                    "SELECT a.attname FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid"
                    + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                    + " JOIN pg_type t ON t.oid = a.atttypid"
                    + " WHERE n.nspname = 'public' AND c.relname = '" + table.replace("'", "''") + "'"
                    + " AND a.attnum > 0 AND NOT a.attisdropped"
                    + " AND t.typname IN ('varchar','text','char','bpchar')")) {
                while (rs.next()) textCols.add(rs.getString(1));
            }
            // 2) 组装 WHERE：keyword 模糊（任一字符列）+ bbox 空间相交
            StringBuilder where = new StringBuilder(" WHERE ").append(quotedGeom).append(" IS NOT NULL");
            String kw = keyword == null ? "" : keyword.trim();
            java.util.List<String> whereParams = new ArrayList<>();
            if (!kw.isEmpty()) {
                StringBuilder like = new StringBuilder();
                for (String col : textCols) {
                    if (like.length() > 0) like.append(" OR ");
                    like.append(quoteIdent(col)).append(" LIKE ?");
                    whereParams.add("%" + kw + "%");
                }
                if (like.length() > 0) where.append(" AND (").append(like).append(")");
            }
            if (bbox != null && !bbox.trim().isEmpty()) {
                String[] parts = bbox.split(",");
                if (parts.length == 4) {
                    try {
                        double x1 = Double.parseDouble(parts[0].trim());
                        double y1 = Double.parseDouble(parts[1].trim());
                        double x2 = Double.parseDouble(parts[2].trim());
                        double y2 = Double.parseDouble(parts[3].trim());
                        where.append(" AND ST_Intersects(").append(quotedGeom)
                             .append(", ST_Transform(ST_SetSRID(ST_MakeEnvelope(?, ?, ?, ?, 4326), 4326), ")
                             .append(geom[1]).append("))");
                        for (double v : new double[]{x1, y1, x2, y2}) whereParams.add(String.valueOf(v));
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("bbox 格式应为 xmin,ymin,xmax,ymax（数值）");
                    }
                }
            }
            // 3) 查询：属性列（字符列，字符串化）+ GeoJSON + 每行 bbox
            //    ★ 属性列从 information_schema 动态拼出（全部来自 pg_catalog 校验过的标识符），
            //      值统一 rs.getString 取出为 properties —— 前端列表直接展示
            StringBuilder cols = new StringBuilder();
            for (String col : textCols) {
                if (cols.length() > 0) cols.append(", ");
                cols.append(quoteIdent(col));
            }
            String sql = "SELECT "
                    + (cols.length() > 0 ? cols + ", " : "")
                    + "ST_AsGeoJSON(ST_Transform(" + quotedGeom + ", 4326)) AS __geo, "
                    + "ST_XMin(e) AS __x1, ST_YMin(e) AS __y1, ST_XMax(e) AS __x2, ST_YMax(e) AS __y2 FROM ("
                    + "SELECT " + (cols.length() > 0 ? cols + ", " : "") + quotedGeom + ", "
                    + "ST_Extent(ST_Transform(" + quotedGeom + ", 4326)) OVER () AS e FROM " + quoteIdent(table)
                    + where.toString() + " LIMIT " + safeLimit + ") t";
            java.util.List<java.util.Map<String, Object>> result = new ArrayList<>();
            try (java.sql.PreparedStatement ps = pg.prepareStatement(sql)) {
                for (int i = 0; i < whereParams.size(); i++) {
                    ps.setString(i + 1, whereParams.get(i));
                }
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                        // properties：全部字符列（与 cols 顺序一致）
                        java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();
                        for (String col : textCols) {
                            Object v = rs.getObject(col);
                            properties.put(col, v == null ? "" : String.valueOf(v));
                        }
                        item.put("properties", properties);
                        item.put("geojson", rs.getString("__geo"));
                        java.util.List<Double> bb = new ArrayList<>();
                        bb.add(rs.getDouble("__x1"));
                        bb.add(rs.getDouble("__y1"));
                        bb.add(rs.getDouble("__x2"));
                        bb.add(rs.getDouble("__y2"));
                        item.put("bbox", bb);
                        result.add(item);
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw new Exception("要素搜索失败：" + e.getMessage(), e);
        }
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
