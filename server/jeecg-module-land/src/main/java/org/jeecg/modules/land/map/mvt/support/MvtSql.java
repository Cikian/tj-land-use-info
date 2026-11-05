package org.jeecg.modules.land.map.mvt.support;

import org.jeecg.modules.land.map.mvt.vo.MvtTable;

/** URL 参数必须先通过目录白名单；此处仍逐个引用标识符，支持中文、空格及双引号表名。 */
public final class MvtSql {
    private MvtSql() { }

    public static String quote(String value) {
        if (value == null || value.isEmpty() || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("空间表标识不能为空或包含零字符");
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static void validateTile(int z, int x, int y) {
        if (z < 0 || z > 22 || x < 0 || y < 0 || x >= (1L << z) || y >= (1L << z)) {
            throw new IllegalArgumentException("瓦片坐标超出范围（层级为 0～22，x/y 必须位于该层级内）");
        }
    }

    public static String relation(MvtTable table) {
        return quote(table.getSchema()) + "." + quote(table.getTable());
    }

    public static String geometry(MvtTable table) {
        String column = "t." + quote(table.getGeometry());
        return table.isGeography() ? column + "::geometry" : column;
    }

    public static String tile(MvtTable table, boolean nativeFilter, int zoom) {
        String geom = geometry(table);
        StringBuilder attributes = new StringBuilder();
        table.getFields().keySet().forEach(name -> attributes.append("t.").append(quote(name)).append(", "));
        // buffer=64 与筛选窗口保持一致，跨瓦片边界的要素才不会出现接缝。
        String filter = nativeFilter
                ? geom + " && b.native_box"
                : "ST_Transform(" + geom + ", 3857) && b.expanded";
        String bounds = "b AS (SELECT box, expanded"
                + (nativeFilter ? ", ST_Envelope(ST_Transform(ST_Segmentize(expanded, (ST_XMax(box)-ST_XMin(box))/16.0), " + table.getSrid() + ")) AS native_box" : "")
                + " FROM expanded_bounds OFFSET 0), ";
        String renderGeometry = "ST_Force2D(" + geom + ")";
        // 粗层级的小面处于量化消失的临界值，保留原顶点，避免抽稀改变其取舍。
        if (zoom >= 8 && thinVertices(table, nativeFilter)) {
            // 先在源坐标系去掉极近的相邻顶点，再投影；容差仅为一个 MVT 量化单位的 1/64。
            // 点、多点、集合、曲线及全球投影兜底不抽稀，避免合并独立要素或改变其语义。
            String tolerance = "LEAST(ST_XMax(b.native_box)-ST_XMin(b.native_box),"
                    + "ST_YMax(b.native_box)-ST_YMin(b.native_box))/4096.0/64.0";
            renderGeometry = "ST_RemoveRepeatedPoints(" + renderGeometry + ", " + tolerance + ")";
        }
        return "WITH bounds AS (SELECT ST_TileEnvelope(?, ?, ?) AS box), "
                + "expanded_bounds AS (SELECT box, ST_Expand(box, (ST_XMax(box)-ST_XMin(box))*64.0/4096) AS expanded FROM bounds), "
                + bounds
                + "features AS (SELECT " + attributes
                + "ST_AsMVTGeom(ST_Transform(" + renderGeometry + ",3857),b.box,4096,64,true) AS \"__mvt_geom\" "
                // 防止外层非空条件被内联回扫描层，导致坐标转换与裁剪在过滤和编码时各执行一次。
                // OFFSET 0 保留查询结果，并兼容不支持 MATERIALIZED CTE 的 PostgreSQL 版本。
                + "FROM " + relation(table) + " t CROSS JOIN b WHERE " + geom + " IS NOT NULL AND " + filter + " OFFSET 0) "
                + "SELECT ST_AsMVT(features, ?, 4096, '__mvt_geom') FROM features WHERE \"__mvt_geom\" IS NOT NULL";
    }

    private static boolean thinVertices(MvtTable table, boolean nativeFilter) {
        if (!nativeFilter || table.isGeography() || table.getSrid() == 3857) return false;
        String type = table.getGeometryType();
        return "POLYGON".equalsIgnoreCase(type) || "MULTIPOLYGON".equalsIgnoreCase(type)
                || "LINESTRING".equalsIgnoreCase(type) || "MULTILINESTRING".equalsIgnoreCase(type);
    }
}
