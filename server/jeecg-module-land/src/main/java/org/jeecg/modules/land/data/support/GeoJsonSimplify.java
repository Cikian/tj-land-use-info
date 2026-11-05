package org.jeecg.modules.land.data.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * @Description: 地块图形概览抽稀（铺图层专用）—— 道格拉斯-普克（Douglas-Peucker）形状保真简化
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.1
 *
 * <p><b>★ 为什么必须是 DP 而不是等距抽稀</b>：
 * 等距抽稀（每隔 N 个点取一个）对「顶点分布不均匀」的边界是<b>破坏性</b>的——
 * 实测一块规则四边形（边界 3979 点，密密麻麻挤在圆角处），等距抽 40 点后
 * 四个拐角全被抽丢，形状直接缺一块（用户看到的「残缺不全」）。
 * DP 按「偏离原边界的最大距离」保点：拐角必保、直线段只留端点，
 * 40 个点也能保持形状走样最小 —— 这是 GIS 领域的标准做法。
 *
 * <p><b>★ 为什么需要抽稀</b>：
 * GeoJSON 导入后 {@code t_land} 里会有几百宗、单宗数万~几十万字符的图形
 * （实测 833 宗合计 30MB+，单宗最大 47 万字符）。铺图层（geoList）只读
 * 预计算的概览列（{@code dk_geojson_overview}，本算法生成），不碰大列；
 * 精确边界走 /geoDetail 按 id 单宗拉取（定位档再抽稀到 {@link #LOCATE_MAX_POINTS}，
 * 避免几千顶点的贴地面现场创建造成定位卡顿）。
 *
 * <p><b>容错</b>：入参不是合法 GeoJSON / 解析出现任何异常时<b>原样返回</b>——
 * 抽稀失败不能把宗地图层弄丢，大不了当次多传点数据。
 */
@Slf4j
public final class GeoJsonSimplify {

    /** 概览层每个环保留的最大点数（含闭合点）——铺图档，预计算落库 {@code dk_geojson_overview} */
    public static final int OVERVIEW_ENTRY_POINTS = 40;

    /**
     * 定位档每环点数上限。
     * 双击定位换上的「精确」图形在这里再抽稀一档：800 点对铺满屏幕的一个地块
     * 与原边界视觉无差别，但贴地面创建开销降为原来的几分之一 —— 消除定位卡顿。
     * 真要全精度数据（导出/分析）应走 Excel/原始 shp，不走渲染层。
     */
    public static final int LOCATE_MAX_POINTS = 800;

    /** 最小合法环：首尾闭合的多边形环至少 4 个点 */
    private static final int MIN_RING_POINTS = 4;

    /** DP 容差二分起点（度）：~0.01m，先按「基本无损」试，超预算再逐步放宽 */
    private static final double DP_START_TOLERANCE = 1e-7;

    private static final ObjectMapper JSON = new ObjectMapper();

    private GeoJsonSimplify() {
    }

    /**
     * 对裸 Polygon / MultiPolygon geometry 做概览抽稀（DP 简化到每环 ≤ maxPoints）。
     *
     * <p>每环点数 ≤ maxPoints 时<b>原样返回</b>（不重序列化，省 CPU）；
     * 超出时 DP 保点（容差倍增直到点数达标），拐角必保、闭合保持。
     *
     * @param geojson          裸 geometry 字符串（Polygon / MultiPolygon）
     * @param maxPointsPerRing 单环点数上限（含闭合点）
     * @return 简化后的裸 geometry 字符串；入参为空或不是可解析图形时原样返回
     */
    public static String simplify(String geojson, int maxPointsPerRing) {
        if (geojson == null || geojson.isEmpty() || maxPointsPerRing < MIN_RING_POINTS) {
            return geojson;
        }
        try {
            JsonNode geometry = JSON.readTree(geojson);
            String type = geometry.path("type").asText("");
            if ("Polygon".equals(type)) {
                JsonNode ringNode = geometry.path("coordinates").get(0);
                if (ringOverBudget(ringNode, maxPointsPerRing)) {
                    String ring = simplifyRing(ringNode, maxPointsPerRing);
                    return ring == null ? geojson
                            : "{\"type\":\"Polygon\",\"coordinates\":[" + ring + "]}";
                }
                // 所有环都在预算内：原样返回（不重序列化，省 CPU）
                return geojson;
            }
            if ("MultiPolygon".equals(type)) {
                JsonNode polygons = geometry.path("coordinates");
                if (!polygons.isArray() || polygons.size() == 0) {
                    return geojson;
                }
                boolean overBudget = false;
                for (JsonNode polygon : polygons) {
                    if (ringOverBudget(polygon.path(0), maxPointsPerRing)) {
                        overBudget = true;
                        break;
                    }
                }
                if (!overBudget) {
                    return geojson;
                }
                StringBuilder sb = new StringBuilder("{\"type\":\"MultiPolygon\",\"coordinates\":[");
                boolean first = true;
                for (JsonNode polygon : polygons) {
                    String ring = simplifyRing(polygon.path(0), maxPointsPerRing);
                    if (ring == null) {
                        return geojson;
                    }
                    if (!first) {
                        sb.append(',');
                    }
                    sb.append('[').append(ring).append(']');
                    first = false;
                }
                return sb.append("]}").toString();
            }
            return geojson;
        } catch (Exception e) {
            log.warn("地块图形概览抽稀失败，按原样返回（不影响功能，只影响传输量）：{}", e.getMessage());
            return geojson;
        }
    }

    /** 该环是否超过点数预算（超了才需要重建，否则整串原样返回） */
    private static boolean ringOverBudget(JsonNode ringNode, int maxPoints) {
        return ringNode != null && ringNode.isArray() && ringNode.size() > maxPoints;
    }

    // ==================================================================
    // 一、单环处理
    // ==================================================================

    /**
     * 简化一个环：解析 → 闭合 → 超预算时 DP 保点。
     *
     * @param coordinates 该环的坐标数组节点（{@code [[lon,lat(,z)],…]}）
     * @return 形如 {@code [[lon,lat],…]} 的环字符串（不含 Polygon 外层中括号）；
     *         环非法时返回 null
     */
    private static String simplifyRing(JsonNode coordinates, int maxPoints) {
        List<double[]> points = parseRingPoints(coordinates);
        if (points == null || points.size() < MIN_RING_POINTS) {
            return null;
        }
        ensureClosed(points);
        if (points.size() <= maxPoints) {
            return serializeRing(points);
        }

        // DP 保点：容差从极小值倍增，直到点数进预算（拐角/形状走样最小化）
        int n = points.size();
        double[] lon = new double[n];
        double[] lat = new double[n];
        for (int i = 0; i < n; i++) {
            lon[i] = points.get(i)[0];
            lat[i] = points.get(i)[1];
        }
        double tolerance = DP_START_TOLERANCE;
        boolean[] keep = null;
        boolean[] prevKeep = null;
        for (int iter = 0; iter < 40; iter++) {
            keep = douglasPeucker(lon, lat, tolerance);
            if (countTrue(keep) <= maxPoints) {
                break;
            }
            prevKeep = keep;
            tolerance *= 2;
        }
        // DP 极端情况下可能只剩首尾 2~3 点（细长/锯齿环）：
        // 先退回上一档容差（点数略超预算但形状更细、必 ≥ 最小环）；
        // 连上一档都不够时才用等距抽稀保底
        if (countTrue(keep) < MIN_RING_POINTS) {
            if (prevKeep != null && countTrue(prevKeep) >= MIN_RING_POINTS) {
                keep = prevKeep;
            } else {
                return decimateRing(points, maxPoints);
            }
        }

        List<double[]> kept = new ArrayList<>(maxPoints + 1);
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                kept.add(points.get(i));
            }
        }
        return serializeRing(kept);
    }

    /**
     * 道格拉斯-普克（迭代实现，避免深递归）：返回「保留标记」，
     * 首尾点恒保留（闭合环里二者相同，闭合性天然保持）。
     */
    private static boolean[] douglasPeucker(double[] lon, double[] lat, double tolerance) {
        int n = lon.length;
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        double tol2 = tolerance * tolerance;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, n - 1});
        while (!stack.isEmpty()) {
            int[] seg = stack.pop();
            int s = seg[0];
            int e = seg[1];
            if (e <= s + 1) {
                continue;
            }
            double x1 = lon[s];
            double y1 = lat[s];
            double dx = lon[e] - x1;
            double dy = lat[e] - y1;
            double len2 = dx * dx + dy * dy;
            double maxDist2 = -1;
            int idx = -1;
            for (int i = s + 1; i < e; i++) {
                double d2;
                if (len2 == 0) {
                    // 首尾重合（闭合环切到只剩首尾段）：距离取到端点的直线距离
                    double ddx = lon[i] - x1;
                    double ddy = lat[i] - y1;
                    d2 = ddx * ddx + ddy * ddy;
                } else {
                    double t = ((lon[i] - x1) * dx + (lat[i] - y1) * dy) / len2;
                    t = Math.max(0, Math.min(1, t));
                    double px = x1 + t * dx;
                    double py = y1 + t * dy;
                    double ddx = lon[i] - px;
                    double ddy = lat[i] - py;
                    d2 = ddx * ddx + ddy * ddy;
                }
                if (d2 > maxDist2) {
                    maxDist2 = d2;
                    idx = i;
                }
            }
            if (idx > 0 && maxDist2 > tol2) {
                keep[idx] = true;
                stack.push(new int[]{s, idx});
                stack.push(new int[]{idx, e});
            }
        }
        return keep;
    }

    /** 等距抽稀保底（仅在 DP 结果不足最小环的极端情况下使用）；自行保证首尾闭合 */
    private static String decimateRing(List<double[]> points, int maxPoints) {
        int size = points.size();
        int step = (int) Math.ceil((double) size / maxPoints);
        List<double[]> kept = new ArrayList<>(maxPoints + 1);
        for (int i = 0; i < size; i += step) {
            kept.add(points.get(i));
        }
        if (!samePoint(kept.get(0), kept.get(kept.size() - 1))) {
            kept.add(kept.get(0));
        }
        return serializeRing(kept);
    }

    private static int countTrue(boolean[] flags) {
        int count = 0;
        for (boolean flag : flags) {
            if (flag) {
                count++;
            }
        }
        return count;
    }

    // ==================================================================
    // 二、解析 / 序列化（与 normalizeGeometry 共用口径：剥 Z、6 位小数、闭合）
    // ==================================================================

    /** 解析一个环的坐标：剥第三维、经纬度越界校验；非法返回 null */
    private static List<double[]> parseRingPoints(JsonNode coordinates) {
        if (coordinates == null || !coordinates.isArray()
                || coordinates.size() == 0 || !coordinates.get(0).isArray()) {
            return null;
        }
        List<double[]> points = new ArrayList<>(coordinates.size());
        for (JsonNode point : coordinates) {
            if (!point.isArray() || point.size() < 2) {
                return null;
            }
            double lon = point.get(0).asDouble();
            double lat = point.get(1).asDouble();
            if (Double.isNaN(lon) || Double.isNaN(lat)
                    || Math.abs(lon) > 180 || Math.abs(lat) > 90) {
                // 经纬度越界基本是坐标系没转成 WGS84（如 CGCS2000 平面坐标直接转出）
                return null;
            }
            points.add(new double[]{lon, lat});
        }
        return points;
    }

    /** 首尾闭合（shp 转出的文件一般已闭合，这里兜底；闭合判据与序列化口径一致） */
    private static void ensureClosed(List<double[]> points) {
        double[] first = points.get(0);
        double[] last = points.get(points.size() - 1);
        if (!samePoint(first, last)) {
            points.add(new double[]{first[0], first[1]});
        }
    }

    private static boolean samePoint(double[] a, double[] b) {
        // ★ 用 6 位小数的字符串比（round 输出定长小数，字符串相等 = 数值相等）：
        //   BigDecimal == 比的是引用、double 直接比会有精度毛刺
        return round(a[0]).equals(round(b[0])) && round(a[1]).equals(round(b[1]));
    }

    /** 按原顺序序列化环（坐标 6 位小数定长输出，可直接拼 JSON） */
    private static String serializeRing(List<double[]> points) {
        StringBuilder sb = new StringBuilder(points.size() * 32).append('[');
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[').append(round(points.get(i)[0])).append(',')
                    .append(round(points.get(i)[1])).append(']');
        }
        return sb.append(']').toString();
    }

    /** 坐标保留 6 位小数（返回值直接可拼 JSON；定长小数使 equals 可作数值比较） */
    private static String round(double value) {
        // BigDecimal.ROUND_HALF_UP：与绘制端 toFixed(6) 的四舍五入口径一致
        return java.math.BigDecimal.valueOf(value)
                .setScale(6, java.math.BigDecimal.ROUND_HALF_UP)
                .toPlainString();
    }
}
