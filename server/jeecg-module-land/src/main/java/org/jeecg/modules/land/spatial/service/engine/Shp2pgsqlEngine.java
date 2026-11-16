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

/**
 * shp2pgsql 引擎（默认）。
 *
 * <p>三个关键实现决策：
 * <ol>
 *   <li><b>-d（drop+recreate）</b>：重复导入同名表时幂等覆盖，而不是报"表已存在"。</li>
 *   <li><b>默认输出 INSERT 语句</b>（不加 -D；-D 是 COPY FROM stdin，psql 专用）。</li>
 *   <li><b>stdout 按配置字符集读取</b>（land.spatial.sql-charset，默认 GBK）：
 *       shp2pgsql 输出 SQL 的编码跟随运行环境 locale，Windows 中文环境是 GBK，
 *       按 UTF-8 读会报 "malformed input off : 0"。</li>
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
        // 编码：shp 同目录有 .cpg 声明时交给 shp2pgsql 自动读取（声明什么用什么）；
        // 没有 .cpg 才传 -W 按配置解码。强制 GBK 解码 UTF-8 的 dbf 会报
        // "Illegal byte sequence"（iconv 严格校验）。
        String baseName = ctx.shpFile.getName().substring(0, ctx.shpFile.getName().lastIndexOf('.'));
        File cpg = new File(ctx.shpFile.getParentFile(), baseName + ".cpg");
        if (!cpg.isFile()) {
            cmd.add("-W");
            cmd.add(ctx.props.getDbfCharset());
        }
        cmd.add(ctx.shpFile.getAbsolutePath());
        cmd.add(ctx.tableName);

        log.info("[shp2pgsql] 执行：{}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process proc = pb.start();

        Charset sqlCharset = Charset.forName(ctx.props.getSqlCharset());
        int rows = 0;
        try (Connection conn = PgSupport.connect(ctx.host, ctx.port, ctx.dbName, ctx.user, ctx.password);
             Statement st = conn.createStatement();
             BufferedReader out = new BufferedReader(new InputStreamReader(proc.getInputStream(), sqlCharset))) {
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
