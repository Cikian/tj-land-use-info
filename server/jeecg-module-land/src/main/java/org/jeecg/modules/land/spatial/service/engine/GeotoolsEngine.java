package org.jeecg.modules.land.spatial.service.engine;

import lombok.extern.slf4j.Slf4j;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.jeecg.modules.land.spatial.support.PgSupport;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * GeoTools 引擎（信创推荐）。
 *
 * <p>纯 Java 实现：GeoTools 读 SHP → 自己拼 DDL/INSERT → JDBC 执行，
 * 不依赖任何本机二进制（shp2pgsql/psql 都不需要），麒麟/UOS/鲲鹏等
 * 国产环境零适配。
 *
 * <p>几个务实的简化：
 * <ul>
 *   <li>几何列统一声明为 geometry(GEOMETRY, srid)——不细究 Polygon/MultiPolygon，
 *       避免源数据混类型导致建表失败；属性列按 Java 类型做基础映射。</li>
 *   <li>几何用 ST_GeomFromText 写入，属性走 PreparedStatement 参数绑定
 *       （避免文本转义/注入问题）。</li>
 *   <li>dbf 字符集默认 GBK（land.spatial.dbf-charset 可配）。</li>
 * </ul>
 */
@Slf4j
public class GeotoolsEngine implements IShpImportEngine {

    @Override
    public String name() {
        return "geotools";
    }

    @Override
    public int doImport(ShpImportContext ctx) throws Exception {
        ShapefileDataStore store = new ShapefileDataStore(ctx.shpFile.toURI().toURL());
        store.setCharset(Charset.forName(ctx.props.getDbfCharset()));

        SimpleFeatureType schema = store.getSchema();
        List<AttributeDescriptor> attrs = schema.getAttributeDescriptors();

        try (Connection conn = PgSupport.connect(ctx.host, ctx.port, ctx.dbName, ctx.user, ctx.password)) {
            conn.setAutoCommit(false);

            // 1) 建表（几何列不细分类型，属性按 Java 类型映射）
            StringBuilder ddl = new StringBuilder("CREATE TABLE \"")
                    .append(ctx.tableName).append("\" (");
            List<String> colNames = new ArrayList<>();
            for (AttributeDescriptor a : attrs) {
                String cn = a.getLocalName();
                if (cn.equalsIgnoreCase("the_geom") || cn.equalsIgnoreCase("SHAPE")
                        || cn.equalsIgnoreCase("geom") || cn.equalsIgnoreCase("geometry")) {
                    continue; // 几何列单独处理
                }
                colNames.add(cn);
                ddl.append("\"").append(cn).append("\" ").append(sqlType(a.getType().getBinding())).append(", ");
            }
            ddl.append("\"SHAPE\" geometry(GEOMETRY, ").append(ctx.srid).append("))");
            try (Statement st = conn.createStatement()) {
                st.execute(ddl.toString());
            }

            // 2) 批量插入
            StringBuilder ins = new StringBuilder("INSERT INTO \"").append(ctx.tableName).append("\" (");
            for (String cn : colNames) ins.append("\"").append(cn).append("\", ");
            ins.append("\"SHAPE\") VALUES (");
            for (int i = 0; i < colNames.size(); i++) ins.append("?, ");
            ins.append("ST_GeomFromText(?, ").append(ctx.srid).append("))");

            int rows = 0;
            try (PreparedStatement ps = conn.prepareStatement(ins.toString());
                 SimpleFeatureIterator it = store.getFeatureSource().getFeatures().features()) {
                int batch = 0;
                while (it.hasNext()) {
                    SimpleFeature f = it.next();
                    int idx = 1;
                    for (String cn : colNames) {
                        Object v = f.getAttribute(cn);
                        ps.setObject(idx++, v);
                    }
                    Object geom = f.getDefaultGeometry();
                    ps.setString(idx, geom == null ? null : geom.toString());
                    ps.addBatch();
                    rows++;
                    if (++batch >= 500) {
                        ps.executeBatch();
                        batch = 0;
                    }
                }
                if (batch > 0) ps.executeBatch();
            }
            // 3) 空间索引随导入事务一起建（PG 的 DDL 可回滚，失败整体回退）：本表是 MVT
            //    瓦片出图的直接来源，无空间索引时大表低层级瓦片全表扫描会撞 10 秒查询
            //    超时。几何列固定是 SHAPE（建表时命名），索引名按表名派生。
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE INDEX IF NOT EXISTS \"" + ctx.tableName
                        + "_geom_idx\" ON \"" + ctx.tableName + "\" USING GIST (\"SHAPE\")");
                st.execute("ANALYZE \"" + ctx.tableName + "\"");
            }
            conn.commit();
            log.info("[geotools] 导入完成，表 {}.{} 共 {} 行", ctx.dbName, ctx.tableName, rows);
            return rows;
        } finally {
            store.dispose();
        }
    }

    private static String sqlType(Class<?> binding) {
        if (Integer.class.isAssignableFrom(binding) || Short.class.isAssignableFrom(binding)) return "int4";
        if (Long.class.isAssignableFrom(binding)) return "int8";
        if (Double.class.isAssignableFrom(binding) || Float.class.isAssignableFrom(binding)
                || java.math.BigDecimal.class.isAssignableFrom(binding)) return "float8";
        if (Boolean.class.isAssignableFrom(binding)) return "bool";
        if (java.util.Date.class.isAssignableFrom(binding) || java.sql.Timestamp.class.isAssignableFrom(binding)) {
            return "timestamp";
        }
        return "text";
    }
}
