package org.jeecg.modules.land.data.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Description: 地块图形概览抽稀（道格拉斯-普克）- 单元测试
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.1
 *
 * <p><b>★ keepsCorners 是本类的核心回归测试</b>：
 * 旧实现是等距抽稀（每隔 N 点取 1 个），对「顶点密集挤在某一段」的边界是
 * 破坏性的——实测一块规则四边形（边界 3979 点挤在圆角处）抽完四角全丢、
 * 形状缺一块（用户看到的「残缺不全」）。DP 按「偏离原边界的最大距离」保点，
 * 拐角必保 —— 这个用例就是把那个真实场景固化下来。
 */
public class GeoJsonSimplifyTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode geometry(String json) throws Exception {
        return JSON.readTree(json);
    }

    // ==================================================================
    // 一、短环原样返回（不付重序列化的 CPU）
    // ==================================================================

    @Test
    public void shortPolygonReturnsIdenticalString() {
        String geojson = "{\"type\":\"Polygon\",\"coordinates\":[[[117.1,40.1],[117.2,40.1],[117.2,40.2],[117.1,40.1]]]}";

        assertEquals(geojson, GeoJsonSimplify.simplify(geojson, 200),
                "未超上限的图形应原样返回");
    }

    @Test
    public void garbageReturnsIdenticalString() {
        assertEquals("not-json", GeoJsonSimplify.simplify("not-json", 200), "坏数据原样返回");
        assertEquals("", GeoJsonSimplify.simplify("", 200));
        assertEquals(null, GeoJsonSimplify.simplify(null, 200));
    }

    // ==================================================================
    // 二、形状保真（本算法存在的理由）
    // ==================================================================

    @Test
    public void douglasPeuckerKeepsCorners() throws Exception {
        // 真实场景还原：规则四边形，1000 个点密密麻麻挤在 A→B 这条边上（圆角），
        // 其余三边各只有角点。等距抽 40 点会把 B/C/D 三个角抽丢（形状缺一块）；
        // DP 必须把四个角全保住。
        StringBuilder dense = new StringBuilder();
        for (int i = 1; i <= 1000; i++) {
            if (i > 1) {
                dense.append(',');
            }
            dense.append('[').append(117.0 + i * 0.001).append(",40.0]");
        }
        String geojson = "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[117.0,40.0]," + dense + ","
                + "[118.0,40.0],[118.0,41.0],[117.0,41.0],[117.0,40.0]]]}";

        String simplified = GeoJsonSimplify.simplify(geojson, 40);

        JsonNode ring = geometry(simplified).path("coordinates").get(0);
        assertTrue(ring.size() <= 41, "点数应进预算，实际 " + ring.size());
        // 四个角全部保留（6 位小数定长输出）
        assertTrue(containsPoint(ring, 117.0, 40.0), "角 A 应保留");
        assertTrue(containsPoint(ring, 118.0, 40.0), "角 B 应保留（等距抽稀会把它抽丢）");
        assertTrue(containsPoint(ring, 118.0, 41.0), "角 C 应保留");
        assertTrue(containsPoint(ring, 117.0, 41.0), "角 D 应保留");
        // 闭合
        assertEquals(ring.get(0).get(0).asDouble(), ring.get(ring.size() - 1).get(0).asDouble(), 1e-9);
    }

    @Test
    public void smoothWaveRingSimplifiedWithinLimit() throws Exception {
        // 1000 点的平滑波状边界：简化后点数进预算、闭合保持、体积显著下降
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            if (i > 0) {
                points.append(',');
            }
            double lon = 117.0 + i * 0.0001;
            double lat = 40.0 + 0.001 * Math.sin(i * Math.PI / 40);
            points.append('[').append(lon).append(',').append(lat).append(']');
        }
        String geojson = "{\"type\":\"Polygon\",\"coordinates\":[[" + points + "]]}";

        String simplified = GeoJsonSimplify.simplify(geojson, 200);

        JsonNode ring = geometry(simplified).path("coordinates").get(0);
        assertTrue(ring.size() <= 201, "抽稀后环点数应 ≤ 上限+闭合点，实际 " + ring.size());
        assertTrue(ring.size() >= 4, "简化结果必须仍是合法环");
        assertEquals(ring.get(0).get(0).asDouble(), ring.get(ring.size() - 1).get(0).asDouble(), 1e-9);
        assertEquals(ring.get(0).get(1).asDouble(), ring.get(ring.size() - 1).get(1).asDouble(), 1e-9);
        assertTrue(simplified.length() < geojson.length() / 3,
                "1000 点简化到 200 应减容 2/3 以上：" + geojson.length() + " -> " + simplified.length());
    }

    @Test
    public void multiPolygonEveryBlockSimplified() throws Exception {
        StringBuilder block = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            if (i > 0) {
                block.append(',');
            }
            block.append('[').append(117.0 + i * 0.0001).append(",40.0]");
        }
        String geojson = "{\"type\":\"MultiPolygon\",\"coordinates\":["
                + "[[" + block + "]]"
                + ",[[" + block + "]]]}";

        String simplified = GeoJsonSimplify.simplify(geojson, 200);

        JsonNode node = geometry(simplified);
        assertEquals("MultiPolygon", node.path("type").asText());
        assertEquals(2, node.path("coordinates").size(), "两块都应保留");
        assertTrue(node.path("coordinates").get(0).get(0).size() <= 201, "第一块应简化");
        assertTrue(node.path("coordinates").get(1).get(0).size() <= 201, "第二块应简化");
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private static boolean containsPoint(JsonNode ring, double lon, double lat) {
        String target = GeoJsonSimplifyRoundHelper.format(lon) + "," + GeoJsonSimplifyRoundHelper.format(lat);
        for (JsonNode point : ring) {
            if ((GeoJsonSimplifyRoundHelper.format(point.get(0).asDouble())
                    + "," + GeoJsonSimplifyRoundHelper.format(point.get(1).asDouble())).equals(target)) {
                return true;
            }
        }
        return false;
    }

    /** 与实现同口径的 6 位小数定长格式（用于点位相等断言） */
    private static final class GeoJsonSimplifyRoundHelper {
        static String format(double value) {
            return java.math.BigDecimal.valueOf(value)
                    .setScale(6, java.math.BigDecimal.ROUND_HALF_UP)
                    .toPlainString();
        }
    }
}
