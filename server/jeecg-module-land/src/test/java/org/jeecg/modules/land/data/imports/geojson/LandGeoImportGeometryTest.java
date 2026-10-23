package org.jeecg.modules.land.data.imports.geojson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeecg.modules.land.data.imports.geojson.impl.LandGeoImportServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Description: 地块图形（GeoJSON）导入 - 几何归一纯逻辑测试
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p><b>为什么只测 {@code normalizeGeometry} 而不写整套集成测试</b>：
 * 本功能真正容易错的只有「geometry 归一」这一段纯函数 ——
 * 剥第三维、6 位小数、首尾闭合、拒绝非 Polygon；
 * 剩下的「匹配 / 写库 / 履历」走的是与 Excel 批量导入同款且已被验证过的路径
 * （QueryWrapper.in 批量查、updateById、ChangeLogSupport）。
 * 这里的用例不起 Spring 上下文，毫秒级跑完，CI 里不会被远程库的抖动拖累。
 *
 * <p><b>样例取自真实文件</b>：{@code 数据/geojson/地块0922.geojson}（shp 转出），
 * 坐标带第三维 {@code 0.0}、未显式闭合的环也真实存在。
 */
public class LandGeoImportGeometryTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode geometry(String json) throws Exception {
        return JSON.readTree(json);
    }

    // ==================================================================
    // 一、正常归一
    // ==================================================================

    @Test
    public void normalizesRealShpPolygon() throws Exception {
        // 真实 shp 转出格式：坐标三元组 [lon, lat, 0.0]，首尾闭合
        String json = "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[117.366435507602858, 40.020097070230356, 0.0],"
                + "[117.368429108768552, 40.020093775270603, 0.0],"
                + "[117.368456521784879, 40.020091652727821, 0.0],"
                + "[117.366435507602858, 40.020097070230356, 0.0]]]}";

        String geojson = LandGeoImportServiceImpl.normalizeGeometry(geometry(json));

        assertNotNull(geojson);
        assertTrue(geojson.startsWith("{\"type\":\"Polygon\",\"coordinates\":[["),
                "必须是裸 Polygon geometry，不能带 Feature 外壳：" + geojson);
        assertFalse(geojson.contains("0.0,"), "第三维必须剥掉：" + geojson);
        // 6 位小数（COORD_DECIMALS）
        assertTrue(geojson.contains("117.366436"), "应保留 6 位小数：" + geojson);
        assertTrue(geojson.contains("40.020097"), "应保留 6 位小数：" + geojson);
        // 首尾闭合：第一个点 == 最后一个点（外环结束是点的 ] + 环的 ] + coordinates 的 ] + }）
        assertTrue(geojson.endsWith("117.366436,40.020097]]]}"), "首尾应闭合：" + geojson);
    }

    @Test
    public void closesOpenRingAutomatically() throws Exception {
        // 未闭合的三角环（3 个独立点）：自动补闭合点，共 4 个
        String json = "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[117.0, 40.0], [117.1, 40.0], [117.1, 40.1]]]}";

        String geojson = LandGeoImportServiceImpl.normalizeGeometry(geometry(json));

        assertNotNull(geojson, "未闭合的环应自动补闭合点而不是拒绝");
        assertEquals(4, countPoints(geojson), "3 点环闭合后应有 4 个点：" + geojson);
    }

    @Test
    public void keepsClosedRingPointCount() throws Exception {
        // 已闭合的环不重复补点
        String json = "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[117.0, 40.0], [117.1, 40.0], [117.1, 40.1], [117.0, 40.0]]]}";

        String geojson = LandGeoImportServiceImpl.normalizeGeometry(geometry(json));

        assertNotNull(geojson);
        assertEquals(4, countPoints(geojson), "已闭合的环不应补点：" + geojson);
    }

    @Test
    public void sixDecimalsMakeComparisonStable() throws Exception {
        // 小数位多于 6 位：四舍五入到 6 位 —— 同一图形两次导入应得到完全一致的字符串
        String json = "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[117.1234564, 40.1234567], [117.2, 40.1], [117.3, 40.2], [117.1234564, 40.1234567]]]}";

        String geojson = LandGeoImportServiceImpl.normalizeGeometry(geometry(json));

        assertNotNull(geojson);
        assertTrue(geojson.contains("117.123456"), "应舍入到 6 位：" + geojson);
        assertTrue(geojson.contains("40.123457"), "应舍入到 6 位：" + geojson);
    }

    // ==================================================================
    // 二、多块合并（同编号多 Feature → MultiPolygon）
    // ==================================================================

    @Test
    public void mergeSinglePolygonKeepsPolygonShape() {
        String polygon = "{\"type\":\"Polygon\",\"coordinates\":[[[117.0,40.0],[117.1,40.0],[117.1,40.1],[117.0,40.0]]]}";

        String merged = LandGeoImportServiceImpl.mergePolygons(java.util.Collections.singletonList(polygon));

        assertEquals(polygon, merged, "单块应原样返回（保持与绘制引擎产物一致，不多包一层）");
    }

    @Test
    public void mergeMultiplePolygonsIntoMultiPolygon() {
        String p1 = "{\"type\":\"Polygon\",\"coordinates\":[[[117.0,40.0],[117.1,40.0],[117.1,40.1],[117.0,40.0]]]}";
        String p2 = "{\"type\":\"Polygon\",\"coordinates\":[[[118.0,41.0],[118.2,41.0],[118.2,41.2],[118.0,41.0]]]}";

        String merged = LandGeoImportServiceImpl.mergePolygons(java.util.Arrays.asList(p1, p2));

        assertNotNull(merged);
        assertTrue(merged.startsWith("{\"type\":\"MultiPolygon\",\"coordinates\":[[["), 
                "多块应合并为裸 MultiPolygon：" + merged);
        // 每块的 coordinates 段原位拼接：两块都在、顺序保持
        assertTrue(merged.contains("117.0,40"), "第一块应原样保留：" + merged);
        assertTrue(merged.contains("118.0,41"), "第二块应原样保留：" + merged);
        assertTrue(merged.indexOf("117.0,40") < merged.indexOf("118.0,41"), "合并顺序应与文件顺序一致");
        // 结构可被 JSON 解析回读（不是拼坏的字符串）
        try {
            com.fasterxml.jackson.databind.JsonNode node = 
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(merged);
            assertEquals("MultiPolygon", node.path("type").asText());
            assertEquals(2, node.path("coordinates").size(), "应包含 2 个多边形");
        } catch (Exception e) {
            throw new AssertionError("合并结果应是合法 JSON：" + merged, e);
        }
    }

    @Test
    public void mergeAcceptsMultiPolygonMembers() {
        // 实测场景（津滨开（挂）2023-2）：同一编号的两个 Feature 本身各是 2 块的
        // MultiPolygon，合并后 4 块 —— 成员不能只认 Polygon
        String multi = "{\"type\":\"MultiPolygon\",\"coordinates\":["
                + "[[[117.0,40.0],[117.1,40.0],[117.1,40.1],[117.0,40.0]]],"
                + "[[[117.2,40.0],[117.3,40.0],[117.3,40.1],[117.2,40.0]]]]}";
        String single = "{\"type\":\"Polygon\",\"coordinates\":[[[118.0,41.0],[118.1,41.0],[118.1,41.1],[118.0,41.0]]]}";

        String merged = LandGeoImportServiceImpl.mergePolygons(java.util.Arrays.asList(multi, single));

        assertNotNull(merged, "MultiPolygon 成员应可参与合并");
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(merged);
            assertEquals("MultiPolygon", node.path("type").asText());
            assertEquals(3, node.path("coordinates").size(), "MultiPolygon 成员应展开为 2 块 + Polygon 1 块");
        } catch (Exception e) {
            throw new AssertionError("合并结果应是合法 JSON：" + merged, e);
        }
    }

    @Test
    public void mergeEmptyOrNullReturnsNull() {
        assertNull(LandGeoImportServiceImpl.mergePolygons(null));
        assertNull(LandGeoImportServiceImpl.mergePolygons(java.util.Collections.emptyList()));
        assertNull(LandGeoImportServiceImpl.mergePolygons(
                java.util.Arrays.asList((String) null)), "含非法成员应返回 null（调用方报错）");
    }

    // ==================================================================
    // 三、拒绝非法图形
    // ==================================================================

    @Test
    public void rejectsNonPolygonGeometry() throws Exception {
        assertNull(LandGeoImportServiceImpl.normalizeGeometry(
                geometry("{\"type\":\"MultiPolygon\",\"coordinates\":[]}")));
        assertNull(LandGeoImportServiceImpl.normalizeGeometry(
                geometry("{\"type\":\"Point\",\"coordinates\":[117.0, 40.0]}")));
        assertNull(LandGeoImportServiceImpl.normalizeGeometry(null));
        assertNull(LandGeoImportServiceImpl.normalizeGeometry(
                geometry("{\"type\":\"Polygon\"}")));
    }

    @Test
    public void acceptsMultiPolygonFeatureFromShp() throws Exception {
        // 实测 shp 成果（地块0922.geojson）：一块地多片不相连图形时本来就导出为
        // MultiPolygon（1140 个 Feature 里 20 个），必须整体接受而不是拒绝
        String json = "{\"type\":\"MultiPolygon\",\"coordinates\":["
                + "[[[117.0,40.0],[117.1,40.0],[117.1,40.1],[117.0,40.0]]],"
                + "[[[118.0,41.0],[118.2,41.0],[118.2,41.2],[118.0,41.0]]]]}";

        String geojson = LandGeoImportServiceImpl.normalizeGeometry(geometry(json));

        assertNotNull(geojson, "MultiPolygon Feature 应整体接受");
        assertTrue(geojson.startsWith("{\"type\":\"MultiPolygon\",\"coordinates\":[[["), 
                "应保持 MultiPolygon 形态：" + geojson);
        // 两块都在、坐标已归一（定长 6 位小数、闭合）
        assertTrue(geojson.contains("117.000000,40.000000") && geojson.contains("118.000000,41.000000"),
                "两块都应保留：" + geojson);
        com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(geojson);
        assertEquals(2, node.path("coordinates").size(), "不应丢块");
    }

    @Test
    public void multiPolygonWithOneBadBlockIsRejected() throws Exception {
        // 第二块坏（只有 2 个独立点）：整体拒绝而不是静默丢块 —— 半块图形比没有更误导
        String json = "{\"type\":\"MultiPolygon\",\"coordinates\":["
                + "[[[117.0,40.0],[117.1,40.0],[117.1,40.1],[117.0,40.0]]],"
                + "[[[118.0,41.0],[118.1,41.1],[118.0,41.0]]]]}";

        assertNull(LandGeoImportServiceImpl.normalizeGeometry(geometry(json)),
                "任一坏块应整体拒绝");
    }

    @Test
    public void rejectsRingWithTooFewPoints() throws Exception {
        // 2 个独立点：即使补上闭合点也只有 3 个点，不满足最小 4 点
        String json = "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[117.0, 40.0], [117.1, 40.1], [117.0, 40.0]]]}";

        assertNull(LandGeoImportServiceImpl.normalizeGeometry(geometry(json)),
                "少于 4 点的环应拒绝");
    }

    @Test
    public void rejectsOutOfBoundCoordinates() throws Exception {
        // 经度 > 180：八成是平面坐标（CGCS2000 投影米制）直接转出，没做坐标系转换
        String json = "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[456000.5, 4430000.2], [457000.5, 4430000.2],"
                + "[457000.5, 4431000.2], [456000.5, 4430000.2]]]}";

        assertNull(LandGeoImportServiceImpl.normalizeGeometry(geometry(json)),
                "经纬度越界的图形应拒绝（提示用户转 WGS84）");
    }

    @Test
    public void rejectsMalformedPoint() throws Exception {
        // 单个坐标不足 2 位
        String json = "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[117.0], [117.1, 40.0], [117.1, 40.1], [117.0, 40.0]]]}";

        assertNull(LandGeoImportServiceImpl.normalizeGeometry(geometry(json)));
    }

    // ==================================================================
    // 三、编号归一口径（匹配用的关键规则，防「一条都匹配不上」）
    // ==================================================================

    @Test
    public void crzdbhNormalizationMatchesDatabaseConvention() {
        // 库内 t_land.crzdbh 是 normalizeCode 的产物：补「号」结尾。
        // GeoJSON 文件里的 CRZDBH 一般不带「号」—— 不归一就按原文匹配，一条都对不上。
        assertEquals("津蓟（挂）2017-025号", org.jeecg.modules.land.data.support.DataSupport.normalizeCode("津蓟（挂）2017-025").getCode());
        assertEquals("津南（挂）2021-06号", org.jeecg.modules.land.data.support.DataSupport.normalizeCode("津南（挂）2021-06").getCode());
        // 已经带「号」的不重复补
        assertEquals("津蓟（挂）2017-025号", org.jeecg.modules.land.data.support.DataSupport.normalizeCode("津蓟（挂）2017-025号").getCode());
        // 半角括号归一成中文括号
        assertEquals("津西青（挂）2024-01号", org.jeecg.modules.land.data.support.DataSupport.normalizeCode("津西青(挂)2024-01").getCode());
        // 空白归 null
        assertNull(org.jeecg.modules.land.data.support.DataSupport.normalizeCode("   ").getCode());
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** 数输出 JSON 里的坐标点个数（数 ",[" 在 coordinates 段的出现次数 + 1） */
    private static int countPoints(String geojson) {
        int count = 1;
        int idx = geojson.indexOf("\"coordinates\":[[");
        for (int i = idx + "\"coordinates\":[[".length(); i < geojson.length() - 3; i++) {
            if (geojson.startsWith("],[", i)) {
                count++;
            }
        }
        return count;
    }
}
