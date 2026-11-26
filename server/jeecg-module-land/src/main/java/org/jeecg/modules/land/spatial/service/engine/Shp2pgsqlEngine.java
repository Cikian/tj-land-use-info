package org.jeecg.modules.land.spatial.service.engine;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.land.spatial.support.PgSupport;
import org.jeecg.modules.land.spatial.support.Shp2pgsqlLocator;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * shp2pgsql 引擎（默认）。
 *
 * <p>三个关键实现决策：
 * <ol>
 *   <li><b>-d（drop+recreate）</b>：重复导入同名表时幂等覆盖，而不是报"表已存在"。</li>
 *   <li><b>默认输出 INSERT 语句</b>（不加 -D；-D 是 COPY FROM stdin，psql 专用）。</li>
 *   <li><b>stdout 编码显式钉死 UTF-8</b>：shp2pgsql 是 libpq 系工具，生成 SQL 的
 *       字符串编码跟随 client encoding —— 本引擎通过进程环境强制
 *       PGCLIENTENCODING=UTF8，stdout 因此恒为 UTF-8，读取也按 UTF-8。
 *       ★ 2026-11-15 实测踩坑：此前按 sql-charset（默认 GBK）读 stdout，而
 *         shp2pgsql 实际输出 UTF-8 —— UTF-8 字节被按 GBK 解读再存 UTF-8，
 *         产出「津西青（挂）」→「娲ヨタ闈掞紙鎸傦級」这类**双重编码乱码**
 *         （地块0922 表 1140 行里 1137 行中招，切片源表唯一值列全坏 →
 *         大屏按编号定位 100% 失败）。stderr 是 shp2pgsql 自身的报错文本，
 *         跟随 Windows locale，仍按 sql-charset 读。</li>
 * </ol>
 * INSERT 语句按行累积到分号再执行（shp2pgsql 每条语句以分号结尾；
 * CREATE TABLE 是多行，不能按行直接执行）。
 */
@Slf4j
public class Shp2pgsqlEngine implements IShpImportEngine {

    @Override
    public String name() {
        return "shp2pgsql";
    }

    @Override
    public int doImport(ShpImportContext ctx) throws Exception {
        String exe = Shp2pgsqlLocator.resolve(ctx.props.getShp2pgsqlPath(),
                PgSupport.url(ctx.host, ctx.port, "postgres"), ctx.user, ctx.password);

        /*
         * ★ 命令行一律 ASCII（2026-11-15 实测踩坑）：
         *   Windows 上 ProcessBuilder 传中文参数给 shp2pgsql（MinGW 程序，argv 走
         *   ANSI/GBK）后，shp2pgsql 把收到的 GBK 字节按 UTF-8 解释 → U+FFFD ——
         *   实测「地块0922」表名变成「???0922」直接建出乱码表，整次导入报废。
         *   对策：
         *     ① shp/shx/dbf/prj/cpg 复制成 ASCII 临时名（uuid），命令行路径无中文；
         *     ② 表名用 ASCII 临时名（imp_<uuid>）导入；
         *     ③ 导入提交成功后用 JDBC 执行 ALTER TABLE RENAME 成真实表名
         *        （走 JDBC 的 UTF-8 通道，无损）。
         */
        String asciiBase = "imp_" + UUID.randomUUID().toString().replace("-", "");
        File asciiDir = ctx.shpFile.getParentFile();
        String srcBase = ctx.shpFile.getName().substring(0, ctx.shpFile.getName().lastIndexOf('.'));
        String[] sidecars = {".shp", ".shx", ".dbf", ".prj", ".cpg"};
        List<File> asciiCopies = new ArrayList<>();
        for (String ext : sidecars) {
            File src = new File(asciiDir, srcBase + ext);
            if (src.isFile()) {
                File dst = new File(asciiDir, asciiBase + ext);
                java.nio.file.Files.copy(src.toPath(), dst.toPath());
                asciiCopies.add(dst);
            }
        }
        File asciiShp = new File(asciiDir, asciiBase + ".shp");
        String asciiTable = asciiBase;

        List<String> cmd = new ArrayList<>();
        cmd.add(exe);
        // 不用 -d：drop 已由服务层直接 DROP TABLE 处理（避开 DropGeometryColumn 的注册依赖）；
        // 不加 -D（COPY 格式）；-c 新建表
        cmd.add("-s");
        cmd.add(String.valueOf(ctx.srid));
        // -I 让 shp2pgsql 在生成的 SQL 里带上 CREATE INDEX ... gist：导入的表是 MVT
        // 瓦片出图的直接来源，无空间索引时大表低层级瓦片全表扫描会撞 10 秒查询超时。
        // 生成的建索引语句和建表/插数一起走本引擎的逐句执行，同事务提交，失败整体回退
        cmd.add("-I");
        // 编码（2026-11-17 加固）：不再信任 .cpg 声明与手填编码——GBK 的 DBF 被按
        // UTF-8 解码是「经营性用地信息」678 行编号损坏（不可逆）的根因。改为
        // **自动探测**：DBF 头部语言 ID + 严格 UTF-8 试解码（失败=不是 UTF-8）
        // + GBK 试解码出中文佐证，探测结果显式传 -W（可覆盖 .cpg 的错误声明）。
        // 探测失败（文件读不了）才回退旧逻辑：有 .cpg 交给它，没有用配置编码。
        File asciiDbf = new File(asciiDir, asciiBase + ".dbf");
        File cpg = new File(asciiDir, asciiBase + ".cpg");
        String detectedCharset = null;
        try {
            detectedCharset = DbfEncodingDetector.detect(asciiDbf, ctx.props.getDbfCharset());
        } catch (Exception e) {
            log.warn("[shp2pgsql] DBF 编码探测失败，回退 .cpg/配置编码：{}", e.getMessage());
        }
        if (detectedCharset != null) {
            log.info("[shp2pgsql] DBF 编码探测结果：{}（{}）", detectedCharset,
                    cpg.isFile() ? "存在 .cpg 声明，与探测不一致时以探测为准" : "无 .cpg 声明");
            cmd.add("-W");
            cmd.add(detectedCharset);
        } else if (!cpg.isFile()) {
            cmd.add("-W");
            cmd.add(ctx.props.getDbfCharset());
        }
        cmd.add(asciiShp.getAbsolutePath());
        cmd.add(asciiTable);

        log.info("[shp2pgsql] 执行：{}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        // ★ 钉死输出编码：shp2pgsql 生成 SQL 的字符串编码跟随 client encoding，
        //   不显式指定时跟随 Windows locale（中文环境 GBK）——与 DBF 的 -W 输入
        //   编码是两回事。强制 UTF8 后 stdout 恒为 UTF-8，与下方读取编码一致，
        //   杜绝「UTF-8 字节按 GBK 误读再存 UTF-8」的双重编码乱码（2026-11-15 实测）
        pb.environment().put("PGCLIENTENCODING", "UTF8");
        pb.redirectErrorStream(false);
        Process proc = pb.start();

        // stdout 恒为 UTF-8（PGCLIENTENCODING=UTF8 已钉死，sql-charset 不再参与）；
        // stderr 是 shp2pgsql 自身报错文本，跟随 Windows locale，按 sql-charset 读
        Charset sqlCharset = Charset.forName(ctx.props.getSqlCharset());
        int rows = 0;
        try (Connection conn = PgSupport.connect(ctx.host, ctx.port, ctx.dbName, ctx.user, ctx.password);
             Statement st = conn.createStatement();
             BufferedReader out = new BufferedReader(new InputStreamReader(proc.getInputStream(),
                     Charset.forName("UTF-8")))) {
            conn.setAutoCommit(false);
            String line;
            StringBuilder stmt = new StringBuilder();
            List<String> batch = new ArrayList<>(500);
            while ((line = out.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
                if (stmt.length() == 0) {
                    // 事务控制自己管理，跳过
                    if (trimmed.equalsIgnoreCase("BEGIN;") || trimmed.equalsIgnoreCase("COMMIT;")
                            || trimmed.equalsIgnoreCase("END;")) continue;
                    if (trimmed.startsWith("INSERT INTO")) rows++;
                }
                stmt.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    batch.add(stmt.toString());
                    stmt.setLength(0);
                    if (batch.size() >= 500) {
                        for (String s : batch) st.addBatch(s);
                        st.executeBatch();
                        batch.clear();
                    }
                }
            }
            if (stmt.length() > 0) batch.add(stmt.toString());
            if (!batch.isEmpty()) {
                for (String s : batch) st.addBatch(s);
                st.executeBatch();
            }
            // ★ 改名必须在 commit **之前**、与数据同一事务：连接是 autocommit=false，
            //   commit 后再执行的 ALTER 会开启一个没人提交的新事务，连接关闭即回滚
            //   （2026-11-15 实测踩坑：表卡在 imp_xxx 名上，RENAME 静默蒸发）
            if (ctx.appendMode) {
                // 追加模式：临时表（imp_ 前缀）保留原名交给服务层做列对齐追加，
                // stagedTable 回填供服务层定位；目标表不动
                ctx.stagedTable = asciiTable;
            } else if (!asciiTable.equals(ctx.tableName)) {
                st.execute("ALTER TABLE " + asciiTable
                        + " RENAME TO \"" + ctx.tableName.replace("\"", "\"\"") + "\"");
                ctx.stagedTable = ctx.tableName;
            }
            conn.commit();
        } catch (Exception e) {
            proc.destroyForcibly();
            throw e;
        }

        int exit = proc.waitFor();
        if (exit != 0) {
            String err = readAll(proc.getErrorStream(), sqlCharset);
            throw new IllegalStateException("shp2pgsql 执行失败（exit=" + exit + "）：" + err);
        }
        log.info("[shp2pgsql] 导入完成，表 {}.{} 共 {} 行", ctx.dbName, ctx.tableName, rows);
        return rows;
    }

    /** Java 8 没有 InputStream.readAllBytes()（Java 9+），手写缓冲读取 */
    private static String readAll(java.io.InputStream in, Charset charset) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), charset);
    }
}
