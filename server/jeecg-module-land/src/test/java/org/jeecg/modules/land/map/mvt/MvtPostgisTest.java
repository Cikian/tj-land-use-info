package org.jeecg.modules.land.map.mvt;

import org.jeecg.modules.land.map.mvt.support.*;
import org.jeecg.modules.land.map.mvt.vo.MvtTable;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 PostGIS 验证；所有测试表在独立事务中创建，结束一律回滚。 */
class MvtPostgisTest {
    private Connection connection;
    private SingleConnectionDataSource source;
    private JdbcTemplate jdbc;
    private final MvtPostgis pg = new MvtPostgis();
    private String schema;

    @BeforeEach
    void open() throws Exception {
        String url = System.getenv("MVT_TEST_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isEmpty(), "设置 MVT_TEST_JDBC_URL/USER/PASSWORD 后执行真实空间库测试");
        connection = DriverManager.getConnection(url, System.getenv("MVT_TEST_USER"), System.getenv("MVT_TEST_PASSWORD"));
        connection.setAutoCommit(false);
        source = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(source);
        schema = "mvt_test_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + MvtSql.quote(schema));
        jdbc.execute("CREATE TABLE " + MvtSql.quote(schema) + ".\"地块\"\"测试\" (gid int, name text, geom geometry(Point,4548), other_geom geometry(Point,4326))");
        jdbc.execute("INSERT INTO " + MvtSql.quote(schema) + ".\"地块\"\"测试\" VALUES (1,'天津',ST_Transform(ST_SetSRID(ST_Point(117.43,38.80),4326),4548),ST_SetSRID(ST_Point(117.43,38.80),4326))");
        jdbc.execute("CREATE VIEW " + MvtSql.quote(schema) + ".map_view AS SELECT gid,geom FROM " + MvtSql.quote(schema) + ".\"地块\"\"测试\"");
        jdbc.execute("CREATE TABLE " + MvtSql.quote(schema) + ".unknown_srid (geom geometry)");
        jdbc.execute("CREATE TABLE " + MvtSql.quote(schema) + ".geo (gid int, geom geography(Point,4326))");
        jdbc.execute("INSERT INTO " + MvtSql.quote(schema) + ".geo VALUES (2,ST_SetSRID(ST_Point(117.43,38.80),4326)::geography)");
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) {
            try { connection.rollback(); } finally { connection.close(); }
        }
    }

    @Test
    void discoversViewsAndEveryGeometryColumnWithoutChangingSourceData() {
        List<MvtTable> tables = pg.discover(source);
        assertEquals(5, tables.stream().filter(t -> schema.equals(t.getSchema())).count());
        assertFalse(find(tables, "unknown_srid", "geom").isAvailable());
        assertEquals("v", find(tables,"map_view","geom").getKind());
        MvtTable table = find(tables,"地块\"测试","geom");
        assertTrue(table.isAvailable());
        assertEquals(4548, table.getSrid());
        assertEquals(new HashSet<>(Arrays.asList("gid", "name")), table.getFields().keySet());
        List<Double> bounds = pg.bounds(source,table);
        assertEquals(117.43,bounds.get(0),0.00001);
        assertEquals(38.80,bounds.get(1),0.00001);
    }

    @Test
    void generatesBinaryTilesAtGlobalAndLocalZoomsAndReturnsEmptyOutsideView() {
        List<MvtTable> tables = pg.discover(source);
        for (String name : Arrays.asList("地块\"测试", "map_view", "geo")) {
            MvtTable table = find(tables,name,"geom");
            for (int zoom : new int[] {0,1,4,5,8,14,22}) {
                int x = (int) Math.floor((117.43+180)/360 * (1 << zoom));
                double latitude = Math.toRadians(38.80);
                int y = (int) Math.floor((1-Math.log(Math.tan(latitude)+1/Math.cos(latitude))/Math.PI)/2*(1 << zoom));
                byte[] tile = pg.tile(source,table,zoom,x,y);
                assertNotNull(tile);
                assertTrue(tile.length > 0, name + " 在层级 " + zoom + " 应有瓦片");
            }
            byte[] empty = pg.tile(source,table,8,10,10);
            assertTrue(empty == null || empty.length == 0);
        }
    }

    @Test
    void queryOptimizationPreservesBinaryOutputAndClipping() {
        String relation = MvtSql.quote(schema) + ".clip_cases";
        jdbc.execute("CREATE TABLE " + relation + " (gid int, name text, geom geometry(Geometry,3857))");
        // 同时覆盖正常要素、包围框相交但几何不相交、裁剪后消失的小面、跨边界线及空几何。
        jdbc.execute("WITH b AS (SELECT ST_TileEnvelope(8,211,97) g), "
                + "v AS (SELECT ST_XMin(g) x,ST_YMin(g) y,ST_XMax(g)-ST_XMin(g) w FROM b) "
                + "INSERT INTO " + relation + " SELECT gid,'case-'||gid,ST_SetSRID(geom,3857) FROM v "
                + "CROSS JOIN LATERAL (VALUES "
                + "(1,ST_Point(x+w/2,y+w/2)),"
                + "(2,ST_MakeLine(ST_Point(x-2*w,y+w/4),ST_Point(x+w/4,y-2*w))),"
                + "(3,ST_MakeEnvelope(x+w/2,y+w/2,x+w/2+w/100000,y+w/2+w/100000)),"
                + "(4,ST_Buffer(ST_Point(x+w/2,y+w/2),w/8)),"
                + "(5,ST_Point(x+2*w,y+2*w)),"
                + "(6,NULL::geometry),"
                + "(7,ST_MakeLine(ST_Point(x-w/8,y+w/2),ST_Point(x+w+w/8,y+w/2)))"
                + ") cases(gid,geom)");
        MvtTable table = find(pg.discover(source), "clip_cases", "geom");
        for (boolean nativeFilter : new boolean[] {true,false}) {
            for (int[] tile : new int[][] {{8,211,97},{8,10,10},{0,0,0}}) {
                String optimized = MvtSql.tile(table,nativeFilter,tile[0]);
                String previous = optimized.replace(" OFFSET 0", "");
                byte[] before = jdbc.queryForObject(previous,byte[].class,tile[0],tile[1],tile[2],table.getTable());
                byte[] after = jdbc.queryForObject(optimized,byte[].class,tile[0],tile[1],tile[2],table.getTable());
                assertArrayEquals(before,after,"查询优化必须保留裁剪、空瓦片和属性编码结果");
                if (tile[1] == 211) { assertTrue(after.length > 0); }
            }
        }
    }

    @Test
    void thinningPreservesHolesPartsBoundaryLinesAndSmallPolygons() {
        // 在 MVT 整数量化网格上构造已知图形，再转入地方坐标系；密集共线点可安全去除。
        for (String type : Arrays.asList("Polygon", "MultiPolygon", "LineString", "MultiLineString")) {
            String name = "dense_" + type.toLowerCase(Locale.ROOT);
            String relation = MvtSql.quote(schema) + "." + MvtSql.quote(name);
            jdbc.execute("CREATE TABLE " + relation + " (gid int, geom geometry(" + type + ",4548))");
            String shape = type.contains("Polygon")
                    ? "ST_Difference(ST_MakeEnvelope(x+u*512,y+u*512,x+u*3584,y+u*3584,3857),"
                        + "ST_MakeEnvelope(x+u*1536,y+u*1536,x+u*2560,y+u*2560,3857))"
                    : "ST_SetSRID(ST_MakeLine(ST_Point(x-u*32,y+u*1024),ST_Point(x+u*4128,y+u*3072)),3857)";
            if (type.startsWith("Multi")) {
                String part = "ST_Translate(ST_Scale(ST_Translate(" + shape + ",-x,-y),0.4,0.4),x,y)";
                shape = "ST_Collect(" + part + ",ST_Translate(" + part + ",u*2048,u*2048))";
            }
            jdbc.execute("WITH b AS (SELECT ST_TileEnvelope(8,211,97) g), "
                    + "v AS (SELECT ST_XMin(g) x,ST_YMin(g) y,(ST_XMax(g)-ST_XMin(g))/4096 u FROM b) "
                    + "INSERT INTO " + relation + " SELECT 1,ST_Transform(ST_Segmentize(" + shape + ",u/64),4548) FROM v");
            if (type.contains("Polygon")) {
                String small = "ST_MakeEnvelope(x+u*128,y+u*128,x+u*132,y+u*132,3857)";
                if (type.startsWith("Multi")) small = "ST_Multi(" + small + ")";
                jdbc.execute("WITH b AS (SELECT ST_TileEnvelope(8,211,97) g), "
                        + "v AS (SELECT ST_XMin(g) x,ST_YMin(g) y,(ST_XMax(g)-ST_XMin(g))/4096 u FROM b) "
                        + "INSERT INTO " + relation + " SELECT 2,ST_Transform(" + small + ",4548) FROM v");
            }
            MvtTable table = find(pg.discover(source), name, "geom");
            String optimized = MvtSql.tile(table,true,8);
            String coarse = MvtSql.tile(table,true,5);
            // 未明确类型的列继续走原始转换路径，作为没有顶点抽稀的对照组。
            table.setGeometryType("Geometry");
            String reference = MvtSql.tile(table,true,8);
            assertArrayEquals(jdbc.queryForObject(MvtSql.tile(table,true,5),byte[].class,5,26,12,"fixtures"),
                    jdbc.queryForObject(coarse,byte[].class,5,26,12,"fixtures"),"粗层级保留原始顶点及小面的量化取舍");
            byte[] before = jdbc.queryForObject(reference,byte[].class,8,211,97,"fixtures");
            byte[] after = jdbc.queryForObject(optimized,byte[].class,8,211,97,"fixtures");
            assertTrue(before.length > 0);
            assertTrue(after.length > 0);
            String select = "SELECT gid,\"__mvt_geom\" AS geom FROM features WHERE \"__mvt_geom\" IS NOT NULL";
            String beforeGeometry = reference.substring(0,reference.indexOf("SELECT ST_AsMVT(features")) + select;
            String afterGeometry = optimized.substring(0,optimized.indexOf("SELECT ST_AsMVT(features")) + select;
            Map<String,Object> result = jdbc.queryForMap("SELECT "
                    + "count(*) FILTER (WHERE a.gid IS NULL OR b.gid IS NULL) AS missing, "
                    + "max(ST_HausdorffDistance(a.geom,b.geom)) AS error, "
                    + "bool_and(ST_IsValid(b.geom)) AS valid, "
                    + "bool_and(ST_NumGeometries(a.geom)=ST_NumGeometries(b.geom)) AS parts, "
                    + "bool_and(COALESCE(ST_NRings(a.geom)=ST_NRings(b.geom),true)) AS rings "
                    + "FROM (" + beforeGeometry + ") a FULL JOIN (" + afterGeometry + ") b USING(gid)",
                    8,211,97,8,211,97);
            assertEquals(0,((Number)result.get("missing")).intValue(),type + " 不能丢要素");
            assertTrue(((Number)result.get("error")).doubleValue() <= 1.5,type + " 量化偏差不得超过 0.1 画布像素");
            assertEquals(true,result.get("valid"),type + " 必须保持几何有效");
            assertEquals(true,result.get("parts"),type + " 必须保留部件");
            assertEquals(true,result.get("rings"),type + " 必须保留面洞");
        }
    }

    private MvtTable find(List<MvtTable> tables, String name, String geometry) {
        return tables.stream().filter(t -> schema.equals(t.getSchema()) && name.equals(t.getTable())
                && geometry.equals(t.getGeometry())).findFirst().orElseThrow(AssertionError::new);
    }
}
