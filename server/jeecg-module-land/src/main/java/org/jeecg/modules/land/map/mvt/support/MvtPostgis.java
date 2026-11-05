package org.jeecg.modules.land.map.mvt.support;

import org.jeecg.modules.land.map.mvt.vo.MvtTable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;

/** 仅执行目录及瓦片查询，不修改来源表，也不依赖 Martin。 */
@Component
public class MvtPostgis {
    private JdbcTemplate jdbc(DataSource source) {
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(10);
        return jdbc;
    }

    public List<MvtTable> discover(DataSource source) {
        JdbcTemplate jdbc = jdbc(source);
        jdbc.queryForObject("SELECT postgis_lib_version()", String.class);
        String sql = "SELECT n.nspname, c.relname, c.relkind, a.attname, t.typname, "
                + "postgis_typmod_srid(a.atttypmod) AS srid, postgis_typmod_type(a.atttypmod) AS geom_type, "
                + "obj_description(c.oid, 'pg_class') AS comment, "
                + "EXISTS(SELECT 1 FROM spatial_ref_sys s WHERE s.srid=postgis_typmod_srid(a.atttypmod)) AS known_srid "
                + "FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                + "JOIN pg_attribute a ON a.attrelid=c.oid JOIN pg_type t ON t.oid=a.atttypid "
                + "WHERE c.relkind IN ('r','v','m','p') AND a.attnum>0 AND NOT a.attisdropped "
                + "AND t.typname IN ('geometry','geography') AND n.nspname NOT LIKE 'pg\\_%' ESCAPE '\\' "
                + "AND n.nspname<>'information_schema' AND has_schema_privilege(n.oid,'USAGE') "
                + "AND has_table_privilege(c.oid,'SELECT') ORDER BY n.nspname,c.relname,a.attname";
        List<MvtTable> tables = jdbc.query(sql, (rs, index) -> {
            MvtTable table = new MvtTable();
            table.setSchema(rs.getString("nspname"));
            table.setTable(rs.getString("relname"));
            table.setGeometry(rs.getString("attname"));
            table.setGeometryType(rs.getString("geom_type"));
            table.setSrid(rs.getInt("srid"));
            table.setGeography("geography".equals(rs.getString("typname")));
            table.setKind(rs.getString("relkind"));
            table.setComment(rs.getString("comment"));
            table.setAvailable(table.getSrid() > 0 && rs.getBoolean("known_srid"));
            table.setReason(table.isAvailable() ? "" : "几何列没有明确且有效的 SRID，请先在数据库中声明坐标系或创建带正确 SRID 的视图");
            return table;
        });
        // 属性同样来自目录白名单，排除其它几何列、二进制及瓦片内部保留名。
        Map<String, Map<String, String>> fields = new HashMap<>();
        jdbc.query("SELECT n.nspname,c.relname,a.attname,t.typcategory,t.typname FROM pg_attribute a "
                + "JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace "
                + "JOIN pg_type t ON t.oid=a.atttypid WHERE c.relkind IN ('r','v','m','p') "
                + "AND a.attnum>0 AND NOT a.attisdropped AND t.typcategory IN ('N','S','B','D') "
                + "AND a.attname<>'__mvt_geom' AND has_schema_privilege(n.oid,'USAGE') "
                + "AND has_table_privilege(c.oid,'SELECT') ORDER BY a.attnum", rs -> {
            String key = rs.getString(1) + "\0" + rs.getString(2);
            String category = rs.getString(4);
            String type = "N".equals(category) ? "Number" : "B".equals(category) ? "Boolean" : "String";
            fields.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(rs.getString(3), type);
        });
        tables.forEach(table -> table.setFields(fields.getOrDefault(table.getSchema() + "\0" + table.getTable(), Collections.emptyMap())));
        return tables;
    }

    public byte[] tile(DataSource source, MvtTable table, int z, int x, int y) {
        MvtSql.validateTile(z, x, y);
        // 大范围瓦片反投影到地方投影时，即使不报错也可能折叠；正投影筛选可避免漏图。
        boolean nativeFilter = table.getSrid() == 3857 || (z >= 5 && x > 0 && x < (1 << z) - 1);
        try {
            return jdbc(source).queryForObject(MvtSql.tile(table, nativeFilter, z), byte[].class, z, x, y, table.getTable());
        } catch (org.springframework.dao.DataAccessException ex) {
            // 低层级瓦片覆盖全球；投影坐标系可能无法反投影极区，此时改用正投影过滤。
            if (projectionFailure(ex)) {
                return jdbc(source).queryForObject(MvtSql.tile(table, false, z), byte[].class, z, x, y, table.getTable());
            }
            throw ex;
        }
    }

    private boolean projectionFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException && "XX000".equals(((SQLException) cause).getSQLState())
                    && cause.getMessage() != null && cause.getMessage().contains("transform")) {
                return true;
            }
        }
        return false;
    }

    public List<Double> bounds(DataSource source, MvtTable table) {
        String sql = "SELECT ST_XMin(b),ST_YMin(b),ST_XMax(b),ST_YMax(b) FROM (SELECT "
                + "ST_Extent(ST_Transform(" + MvtSql.geometry(table) + ",4326)) AS b FROM "
                + MvtSql.relation(table) + " t) e";
        return jdbc(source).query(sql, rs -> {
            if (!rs.next() || rs.getObject(1) == null) { return Collections.emptyList(); }
            List<Double> result = Arrays.asList(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4));
            if (result.stream().anyMatch(v -> !Double.isFinite(v))
                    || result.get(0) < -180 || result.get(2) > 180 || result.get(1) < -90 || result.get(3) > 90) {
                return Collections.emptyList();
            }
            return result;
        });
    }
}
