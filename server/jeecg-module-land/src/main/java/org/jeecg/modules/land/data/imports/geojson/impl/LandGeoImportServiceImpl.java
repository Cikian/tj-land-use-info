package org.jeecg.modules.land.data.imports.geojson.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.imports.geojson.ILandGeoImportService;
import org.jeecg.modules.land.data.imports.geojson.LandGeoImportResultVO;
import org.jeecg.modules.land.data.imports.support.LandImportSupport;
import org.jeecg.modules.land.data.mapper.LandMapper;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;
import org.jeecg.modules.land.data.oplog.support.ChangeLogSupport;
import org.jeecg.modules.land.data.service.ILandService;
import org.jeecg.modules.land.data.support.DataSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 地块图形（GeoJSON）导入 - 解析 / 校验 / 入库实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p>实现要点（与 Excel 批量导入同一套踩点逻辑，外加本功能特有的几条）：
 *
 * <p><b>① 预览与入库共用同一条解析路径</b>（{@code parse(file, apply)}），
 * 保证「预览看到什么，导入就写什么」。
 *
 * <p><b>② 编号必须先归一再匹配。</b>
 * 库内 {@code t_land.crzdbh} 统一是 {@link DataSupport#normalizeCode} 归一后的写法
 * （半角括号→中文括号、去空白、摘【…】标记、保证以「号」结尾），
 * 而 GeoJSON 文件（一般从 shp 转出）里的 CRZDBH 是原始写法，如
 * {@code 津蓟（挂）2017-025} 不带「号」。不归一就按原文匹配，一条都对不上。
 *
 * <p><b>③ 存库的是「裸 geometry」字符串：单块 Polygon 或多块合并的 MultiPolygon。</b>
 * 与大屏「添加地块」绘制、{@code landGeoLayer.js} 渲染、{@code dk_geojson} 列的约定一致：
 * {@code {"type":"Polygon","coordinates":[[[lon,lat],…]]}}（单块）或
 * {@code {"type":"MultiPolygon","coordinates":[[[[…]]],[[…]]]}}（多块合并），
 * 不带 Feature/FeatureCollection 外壳。上传文件若是标准 FeatureCollection，
 * 需要拆出每个 Feature 的 geometry。
 *
 * <p><b>④ 坐标归一：剥离第三维、保留 6 位小数、首尾闭合。</b>
 * shp 转出的坐标常带第三位（如 {@code [117.36, 40.02, 0.0]}），渲染层
 * {@code Cesium.Cartesian3.fromDegrees(c[0], c[1], 0)} 虽然会忽略它，
 * 但留着既占存储也让「图形是否变化」的比对不稳定；6 位小数与绘制引擎
 * {@code drawPolygon.js} 的 {@code COORD_DECIMALS = 6} 同一口径。
 * 首尾不闭合的环自动补上闭合点（渲染层要求 {@code ring.length >= 4} 且闭合）。
 *
 * <p><b>⑤ 未匹配记警告；同编号多 Feature 自动合并。</b>
 * 编号没匹配上（宗地还没录入、或写法对不上）只跳过并警告，其余宗地照常导入 ——
 * 中心经常分批拿图形，不能因为个别编号对不上就让整批白跑；
 * 同一编号出现多个 Feature（一块地由多片不相连图形组成，或 shp 按地块拆了多行）
 * 属于真实数据形态，聚合为一块 MultiPolygon 整体挂到该宗地——
 * <b>导入即替换</b>：宗地图形始终 = 本次文件中该编号的全部多边形。
 *
 * <p><b>⑥ 有错误默认整批不入库。</b>与 Excel 导入同一安全阀：
 * 批量写库最怕「一半进了一半没进」。
 */
@Slf4j
@Service
public class LandGeoImportServiceImpl implements ILandGeoImportService {

    /**
     * dk_geojson 列的存储上限。
     *
     * <p>★ 列已升级为 mediumtext（见 sql/data/13_t_land_dk_geojson_mediumtext.sql）：
     * 实测地块矢量成果里单个多边形可到 3979 个顶点、归一后 9.2 万字符，
     * 原先按 text 列定的 60000 上限会把这样的地块整批卡死，故放宽到 200 万字符
     * （mediumtext 16MB 的实际余量，防御性用途——正常文件到不了这个量级）。
     */
    private static final int GEO_MAX_LENGTH = 2000000;

    /** 坐标保留的小数位数（与 stargis-client-web drawPolygon.js 的 COORD_DECIMALS 一致） */
    private static final int COORD_DECIMALS = 6;

    /** 最小合法环：首尾闭合的多边形环至少 4 个点（起点 + 至少 2 个中间点 + 闭合点） */
    private static final int MIN_RING_POINTS = 4;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ILandService landService;

    @Autowired
    private LandMapper landMapper;

    @Autowired
    private ChangeLogSupport changeLogSupport;

    @Autowired
    private LandImportSupport support;

    // ==================================================================
    // 一、预览 / 入库
    // ==================================================================

    @Override
    public LandGeoImportResultVO preview(MultipartFile file) {
        return parse(file, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LandGeoImportResultVO confirm(MultipartFile file) {
        LandGeoImportResultVO result = parse(file, true);
        log.info("地块图形（GeoJSON）导入：解析 {} 个地块 / 更新 {} 条 / 图形未变化 {} 条 / "
                        + "未匹配 {} 条 / 错误 {} 条 / 操作人 {}",
                result.getTotalFeatures(), result.getUpdatedRows(), result.getUnchangedRows(),
                result.getUnmatchedCount(), result.getErrorCount(),
                LandImportSupport.nameOrPlaceholder(support.currentRealname()));
        return result;
    }

    // ==================================================================
    // 二、解析与校验（预览与入库共用）
    // ==================================================================

    /**
     * 解析 + 校验 +（可选）入库的唯一路径。
     *
     * @param apply true = 真正写库；false = 只预览
     */
    private LandGeoImportResultVO parse(MultipartFile file, boolean apply) {
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("请选择要导入的 GeoJSON 文件");
        }

        JsonNode root;
        try {
            root = JSON.readTree(file.getInputStream());
        } catch (IOException e) {
            log.error("读取 GeoJSON 文件失败：{}", file.getOriginalFilename(), e);
            throw new JeecgBootException("读取文件失败：" + e.getMessage()
                    + "（请确认上传的是 .geojson / .json 文本文件）");
        } catch (RuntimeException e) {
            log.error("解析 GeoJSON 文件失败：{}", file.getOriginalFilename(), e);
            throw new JeecgBootException("文件不是合法的 JSON：" + StringUtils.defaultString(e.getMessage()));
        }
        if (root == null || root.isNull() || !root.isObject()) {
            throw new JeecgBootException("文件内容不是合法的 GeoJSON 对象"
                    + "（应为 FeatureCollection 或单个 Feature）");
        }

        LandGeoImportResultVO result = new LandGeoImportResultVO();
        String fileName = file.getOriginalFilename();

        // ---------- 取 features 数组（FeatureCollection 或单个 Feature） ----------
        JsonNode features = root.path("features");
        List<JsonNode> featureList = new ArrayList<>();
        if (features.isArray()) {
            for (JsonNode feature : features) {
                featureList.add(feature);
            }
        } else if (root.path("type").asText("").equals("Feature")) {
            // 单个 Feature 的文件也认（虽然少见，但 shp 转换工具导单地块时常给这种）
            featureList.add(root);
        } else {
            throw new JeecgBootException("文件里没有 features 数组（确认导出的是 FeatureCollection 格式）");
        }
        if (featureList.isEmpty()) {
            throw new JeecgBootException("文件里没有解析到任何地块（features 为空）");
        }
        result.setTotalFeatures(featureList.size());

        // ---------- 逐 Feature 解析：编号 + 图形（同编号多 Feature 自动合并） ----------
        // featureIndex 用 1 基序号（与前端错误表格的「序号」一致，方便对着文件数）
        //
        // ★ 同一编号出现多个 Feature 不再报错：一块地由多片不相连图形组成
        //   （或 shp 里按地块拆了多行）是真实数据形态，聚合为一个 MultiPolygon
        //   整体挂到该宗地。LinkedHashMap 保持文件顺序，图形顺序 = 文件里出现的顺序。
        List<Feature> matched = new ArrayList<>();
        Map<String, Feature> byCode = new LinkedHashMap<>();
        for (int i = 0; i < featureList.size(); i++) {
            int featureIndex = i + 1;
            JsonNode node = featureList.get(i);

            // ---- 编号：properties 里大小写不敏感取 crzdbh，按库内口径归一 ----
            String rawCrzdbh = readCrzdbh(node);
            if (rawCrzdbh == null) {
                result.addError(featureIndex, null,
                        "properties 里没有 CRZDBH（出让宗地编号）属性，无法匹配宗地");
                continue;
            }
            String crzdbh = DataSupport.normalizeCode(rawCrzdbh).getCode();
            if (crzdbh == null) {
                result.addError(featureIndex, rawCrzdbh, "CRZDBH 属性为空白，无法匹配宗地");
                continue;
            }

            // ---- 图形：每个 Feature 必须是合法 Polygon，坐标归一为 [lon,lat] 二维、闭合、6 位小数 ----
            String geojson = normalizeGeometry(node.path("geometry"));
            if (geojson == null) {
                result.addError(featureIndex, crzdbh,
                        "geometry 不是可用的图形（认 Polygon / MultiPolygon，"
                                + "且每个环至少 " + MIN_RING_POINTS + " 个坐标点、"
                                + "经纬度在 WGS84 范围内；若来自 shp 请确认已转 WGS84 地理坐标）");
                continue;
            }

            Feature feature = byCode.get(crzdbh);
            if (feature == null) {
                feature = new Feature(featureIndex, crzdbh, new ArrayList<>());
                byCode.put(crzdbh, feature);
                matched.add(feature);
            }
            feature.polygons.add(geojson);
            // 记录该编号在文件中出现的所有位置（错误/提示定位用）
            feature.indexes.add(featureIndex);
        }

        // ---- 按宗地合并：1 块存 Polygon、多块合并为 MultiPolygon；合并后查总量 ----
        for (Feature feature : matched) {
            String merged = mergePolygons(feature.polygons);
            if (merged == null || merged.length() > GEO_MAX_LENGTH) {
                result.addError(feature.indexes.get(0).intValue(), feature.crzdbh,
                        "地块图形数据过大（合并 " + feature.polygons.size() + " 块后 "
                                + (merged == null ? 0 : merged.length()) + " 字符，上限 " + GEO_MAX_LENGTH
                                + "），请先简化多边形后重试");
                continue;
            }
            feature.geojson = merged;
        }

        // ---------- 与库内比对：一次批量查出命中的宗地 ----------
        if (!matched.isEmpty()) {
            List<String> crzdbhList = new ArrayList<>(matched.size());
            for (Feature feature : matched) {
                crzdbhList.add(feature.crzdbh);
            }
            Map<String, Land> existing = loadExisting(crzdbhList);

            List<Feature> toUpdate = new ArrayList<>();
            for (Feature feature : matched) {
                if (feature.geojson == null) {
                    // 合并后超限：上面已按编号报过错误，这里只跳过
                    continue;
                }
                Land hit = existing.get(feature.crzdbh);
                if (hit == null) {
                    result.setUnmatchedCount(result.getUnmatchedCount() + 1);
                    result.addWarning("出让宗地编号「" + feature.crzdbh
                            + "」（第 " + joinIndexes(feature.indexes) + " 个地块，共 "
                            + feature.polygons.size() + " 块图形）未匹配到系统中的宗地，已跳过；"
                            + "请先在「经营性用地信息录入」里录入该宗地，或核对编号写法是否一致");
                    continue;
                }
                result.setValidFeatures(result.getValidFeatures() + 1);
                if (feature.geojson.equals(StringUtils.trimToEmpty(hit.getDkGeojson()))) {
                    // 图形一字不差：跳过，避免无意义写库 + 变更履历被刷屏
                    result.setUnchangedRows(result.getUnchangedRows() + 1);
                    continue;
                }
                feature.landId = hit.getId();
                feature.dkmc = hit.getDkmc();
                toUpdate.add(feature);
            }
            result.setWillUpdateRows(toUpdate.size());

            // ---------- 是否允许写入 ----------
            if (result.getErrorCount() > 0) {
                result.setAborted(true);
                result.addNotice("存在 " + result.getErrorCount() + " 条错误："
                        + (apply ? "本次未更新任何地块图形。" : "预览不写库。")
                        + "请修正文件后重传。");
                return result;
            }
            if (result.getUnmatchedCount() > 0) {
                result.addNotice("有 " + result.getUnmatchedCount()
                        + " 宗宗地因编号未匹配被跳过（不影响其它宗地导入）");
            }
            if (result.getUnchangedRows() > 0) {
                result.addNotice("有 " + result.getUnchangedRows()
                        + " 宗宗地的图形与系统现值一致，无需更新");
            }
            if (apply) {
                write(toUpdate, fileName, result);
            }
        } else {
            // 一个可写的都没有：要么全错，要么全没匹配上
            if (result.getErrorCount() > 0) {
                result.setAborted(true);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 2.1 编号
    // ------------------------------------------------------------------

    /**
     * 从 properties 里取出让宗地编号：大小写不敏感认 crzdbh
     * （shp 字段名转 GeoJSON 后可能保留为大写 CRZDBH，也可能被工具转成小写）。
     *
     * <p>取原始文本（不清洗空白）：归一交给 {@link DataSupport#normalizeCode}，
     * 那里会把半角括号、连字符、空白一次性处理掉。
     */
    private String readCrzdbh(JsonNode feature) {
        JsonNode properties = feature.path("properties");
        if (!properties.isObject()) {
            return null;
        }
        JsonNode value = properties.path("CRZDBH");
        if (value.isMissingNode()) {
            value = properties.path("crzdbh");
        }
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.trim().isEmpty() ? null : text;
    }

    // ------------------------------------------------------------------
    // 2.2 图形归一
    // ------------------------------------------------------------------

    /**
     * 把单个 Feature 的 geometry 归一为「裸 Polygon / MultiPolygon geometry」字符串。
     *
     * <ul>
     *   <li>认 {@code type === "Polygon"} 与 {@code type === "MultiPolygon"}：
     *       实测 shp 转出的成果里，一块地由多片不相连图形组成时本来就导出为
     *       MultiPolygon（地块0922.geojson 1140 个 Feature 里 20 个是 MultiPolygon，
     *       最多一块地含 3 片），必须整体接受，否则这 20 个宗地永远导不进去；</li>
     *   <li>Polygon：取外环 coordinates[0] 归一后返回单块；</li>
     *   <li>MultiPolygon：逐块取外环归一，再按 {@link #mergePolygons} 同样规则
     *       拼回 MultiPolygon（不丢块）；任何一块坏 → 整体返回 null（报错，
     *       不静默丢块——半块图形比没有更误导）；</li>
     *   <li>逐点剥离第三维（Z）、保留 {@value #COORD_DECIMALS} 位小数；
     *       首尾不闭合自动补闭合点；点数不足 {@value #MIN_RING_POINTS} 的环视为坏块；</li>
     *   <li>输出不带 Feature/FeatureCollection 外壳，与绘制引擎
     *       {@code drawPolygon.js#getGeoJSON} 的产物同族。</li>
     * </ul>
     *
     * <p><b>public static 以便单元测试直接构造 JsonNode 调用</b>（不必起 Spring 上下文）。
     *
     * @return 归一后的裸 Polygon / MultiPolygon geometry 字符串；不可用时返回 null
     */
    public static String normalizeGeometry(JsonNode geometry) {
        if (geometry == null || !geometry.isObject()) {
            return null;
        }
        String type = geometry.path("type").asText("");
        if ("Polygon".equals(type)) {
            JsonNode coordinates = geometry.path("coordinates");
            if (!coordinates.isArray() || coordinates.size() == 0) {
                return null;
            }
            String ring = normalizeRing(coordinates.get(0));
            return ring == null ? null
                    : "{\"type\":\"Polygon\",\"coordinates\":[" + ring + "]}";
        }
        if ("MultiPolygon".equals(type)) {
            JsonNode polygons = geometry.path("coordinates");
            if (!polygons.isArray() || polygons.size() == 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder("{\"type\":\"MultiPolygon\",\"coordinates\":[");
            boolean first = true;
            for (JsonNode polygon : polygons) {
                // polygon = [外环, 内环…]；本功能只存外环（与渲染层/绘制引擎口径一致）
                if (!polygon.isArray() || polygon.size() == 0) {
                    return null;
                }
                String ring = normalizeRing(polygon.get(0));
                if (ring == null) {
                    return null;
                }
                if (!first) {
                    sb.append(',');
                }
                sb.append('[').append(ring).append(']');
                first = false;
            }
            return sb.append("]}").toString();
        }
        return null;
    }

    /**
     * 归一一个多边形环（入参是该环的坐标数组）：剥 Z、6 位小数、闭合、最少点校验。
     *
     * @return 形如 {@code [[lon,lat],…]} 的环字符串（**不含** Polygon 的外层中括号）；
     *         不可用时返回 null
     */
    private static String normalizeRing(JsonNode coordinates) {
        if (!coordinates.isArray() || coordinates.size() == 0 || !coordinates.get(0).isArray()) {
            return null;
        }
        List<double[]> points = new ArrayList<>();
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
        // 首尾闭合（shp 转出的文件一般已闭合，这里兜底）。
        // ★ 必须在「点数校验」之前做：3 个独立点的三角环闭合后是 4 点，是合法的；
        //   先校验会把三角形误拒掉。
        double[] first = points.get(0);
        double[] last = points.get(points.size() - 1);
        if (!samePoint(first, last)) {
            points.add(new double[]{first[0], first[1]});
        }
        if (points.size() < MIN_RING_POINTS) {
            return null;
        }
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

    private static boolean samePoint(double[] a, double[] b) {
        // ★ 用 6 位小数的字符串比（round 输出定长小数，字符串相等 = 数值相等）：
        //   BigDecimal == 比的是引用、double 直接比会有精度毛刺，
        //   都会让「已闭合的环」被误判为未闭合而多补一个点
        return round(a[0]).equals(round(b[0])) && round(a[1]).equals(round(b[1]));
    }

    /** 坐标保留 6 位小数（返回值直接可拼 JSON；定长小数使 equals 可作数值比较） */
    private static String round(double value) {
        // BigDecimal.ROUND_HALF_UP：与绘制端 toFixed(6) 的四舍五入口径一致
        return java.math.BigDecimal.valueOf(value)
                .setScale(COORD_DECIMALS, java.math.BigDecimal.ROUND_HALF_UP)
                .toPlainString();
    }

    // ------------------------------------------------------------------
    // 2.3 查库与写库
    // ------------------------------------------------------------------

    /** 按出让宗地编号批量取库内<b>有效</b>记录（一次查询；@TableLogic 自动过滤 del_flag） */
    private Map<String, Land> loadExisting(List<String> crzdbhList) {
        Map<String, Land> map = new LinkedHashMap<>();
        if (crzdbhList == null || crzdbhList.isEmpty()) {
            return map;
        }
        QueryWrapper<Land> wrapper = new QueryWrapper<>();
        wrapper.in("crzdbh", crzdbhList);
        List<Land> list = landService.list(wrapper);
        if (list != null) {
            for (Land item : list) {
                map.put(item.getCrzdbh(), item);
            }
        }
        return map;
    }

    /**
     * 写库：逐条只更新 {@code dk_geojson + update_by/update_time}，并记一条动作级变更履历。
     *
     * <p><b>为什么履历不带字段明细</b>：dkGeojson 本就被 {@code ChangeLogSupport.SKIP_FIELDS}
     * 排除在字段级 diff 之外（整段 GeoJSON 是数 KB 长文本，参与 diff 会把履历撑爆）。
     * 这里手动 {@code add} 一条简短的「地块图形」变更项，让 {@code save(ACTION_IMPORT)}
     * 满足「有变更才落库」的条件 —— 动作、来源文件都能在「变更履历」里查到。
     */
    private void write(List<Feature> toUpdate, String fileName, LandGeoImportResultVO result) {
        String operator = support.currentUsername();
        for (Feature feature : toUpdate) {
            Land land = new Land();
            land.setId(feature.landId);
            land.setDkGeojson(feature.geojson);
            land.setUpdateBy(operator);
            land.setUpdateTime(new Date());
            landMapper.updateById(land);

            changeLogSupport.collector(DataSupport.BIZ_LAND, feature.landId, feature.crzdbh)
                    .add("dkGeojson", "地块图形（GeoJSON）",
                            "原图形", "导入图形（来源：" + StringUtils.defaultString(fileName, "手动上传") + "）")
                    .save(DataChangeLog.ACTION_IMPORT,
                            "导入地块图形（GeoJSON），来源文件：" + StringUtils.defaultString(fileName, "手动上传")
                                    + (StringUtils.isNotBlank(feature.dkmc)
                                    ? "（" + feature.dkmc + "）" : ""),
                            null);
        }
        result.setUpdatedRows(toUpdate.size());
    }

    // ------------------------------------------------------------------
    // 2.4 内部结构
    // ------------------------------------------------------------------

    /**
     * 合并同一宗地的多块图形（存库口径）。
     *
     * <ul>
     *   <li>1 块 → 原样返回（保持 Polygon / MultiPolygon 形态，与绘制引擎产物同族）；</li>
     *   <li>多块 → 合并为裸 MultiPolygon：{@code {"type":"MultiPolygon",
     *       "coordinates":[[[...点...]], [[...点...]]]}}，逐块摘出 coordinates 原位拼接；
     *       成员既可以是 Polygon（1 段），也可以是 MultiPolygon（多段，全部展开）——
     *       实测 shp 成果里同一编号的多个 Feature 本身就可能是 MultiPolygon
     *       （津滨开（挂）2023-2 两个 Feature 各含 2 块，合并后 4 块）；</li>
     *   <li>空列表 / 某块不是合法的归一产物 → 返回 null（调用方报错）。</li>
     * </ul>
     *
     * <p><b>public static 以便单元测试直接调用</b>（不必起 Spring 上下文）。
     */
    public static String mergePolygons(List<String> polygons) {
        if (polygons == null || polygons.isEmpty()) {
            return null;
        }
        if (polygons.size() == 1) {
            return polygons.get(0);
        }
        StringBuilder sb = new StringBuilder(polygons.size() * 256)
                .append("{\"type\":\"MultiPolygon\",\"coordinates\":[");
        boolean first = true;
        for (String polygon : polygons) {
            String coords = extractPolygonCoordinates(polygon);
            if (coords == null) {
                return null;
            }
            if (!first) {
                sb.append(',');
            }
            sb.append(coords);
            first = false;
        }
        return sb.append("]}").toString();
    }

    /**
     * 从归一后的裸 geometry 字符串里摘出「多边形段列表」（含各自的外层中括号）。
     * 只处理 {@link #normalizeGeometry} 的产物（形状固定），不做通用 JSON 解析：
     * <ul>
     *   <li>Polygon：coordinates 段 {@code [[[点],[点]]]} 即 1 个多边形段，原样返回；</li>
     *   <li>MultiPolygon：coordinates 段 {@code [[[poly]],[[poly]]]} 外面还包一层
     *       「多边形列表」的中括号，剥掉最外层后剩下的 {@code [[poly]],[[poly]]}
     *       才是逐个多边形段 —— 直接拼会多一层嵌套（JSON 合法但每块坐标都被
     *       当成一个巨型多边形的环，渲染全错）。</li>
     * </ul>
     */
    private static String extractPolygonCoordinates(String polygon) {
        if (polygon == null) {
            return null;
        }
        String polyPrefix = "{\"type\":\"Polygon\",\"coordinates\":";
        if (polygon.startsWith(polyPrefix) && polygon.endsWith("}")) {
            return polygon.substring(polyPrefix.length(), polygon.length() - 1);
        }
        String multiPrefix = "{\"type\":\"MultiPolygon\",\"coordinates\":";
        if (polygon.startsWith(multiPrefix) && polygon.endsWith("}")) {
            String inner = polygon.substring(multiPrefix.length(), polygon.length() - 1).trim();
            // 剥掉最外层「多边形列表」中括号：[a,b] → a,b
            if (inner.startsWith("[") && inner.endsWith("]")) {
                return inner.substring(1, inner.length() - 1);
            }
            return null;
        }
        return null;
    }

    /** 把 Feature 在文件中出现的位置序号拼成「3、5、8」样式（提示定位用） */
    private static String joinIndexes(List<Integer> indexes) {
        StringBuilder sb = new StringBuilder();
        for (Integer index : indexes) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(index);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 2.4 内部结构
    // ------------------------------------------------------------------

    /**
     * 一个编号（宗地）的聚合结果：同编号的多个 Feature 在此合并。
     * {@code polygons} 保持文件顺序；{@code geojson} 是合并后的存库串
     * （1 块 = Polygon，多块 = MultiPolygon；合并超限时为 null 并已报错）。
     */
    private static class Feature {
        final int featureIndex;
        final String crzdbh;
        final List<String> polygons;
        final List<Integer> indexes = new ArrayList<>();
        String geojson;
        String landId;
        String dkmc;

        Feature(int featureIndex, String crzdbh, List<String> polygons) {
            this.featureIndex = featureIndex;
            this.crzdbh = crzdbh;
            this.polygons = polygons;
            this.indexes.add(featureIndex);
        }
    }
}
