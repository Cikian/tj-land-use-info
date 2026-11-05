package org.jeecg.modules.land.data.imports.facilitygeo.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.entity.Facility;
import org.jeecg.modules.land.data.imports.facilitygeo.IFacilityGeoImportService;
import org.jeecg.modules.land.data.imports.facilitygeo.FacilityGeoImportResultVO;
import org.jeecg.modules.land.data.imports.geojson.impl.LandGeoImportServiceImpl;
import org.jeecg.modules.land.data.mapper.FacilityMapper;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;
import org.jeecg.modules.land.data.oplog.support.ChangeLogSupport;
import org.jeecg.modules.land.data.support.DataSupport;
import org.jeecg.modules.land.data.support.GeoJsonSimplify;
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
 * @Description: 配套地块图形（GeoJSON）导入 - 解析 / 校验 / 入库实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-23
 * @Version V1.0
 *
 * <p>与经营性用地 GeoJSON 导入（{@code LandGeoImportServiceImpl}）同一套骨架：
 * ① 预览与入库共用 {@code parse(file, apply)} 一条路径；② 坐标归一（剥 Z、
 * 6 位小数、闭合）；③ 同名多 Feature 合并 MultiPolygon、导入即替换；
 * ④ 未匹配记警告、有错整批不写。<b>解析与合并算法直接复用宗地实现的
 * public static 方法</b>（{@code normalizeGeometry / mergePolygons}）——
 * 两类图形的几何口径完全一致，不复制一份免得日后改一处漏一处。
 *
 * <p>配套特有的差异：
 *
 * <p><b>① 匹配键是 PTXMMC（配套项目名称），不是 CRZDBH。</b>
 * 配套 shp 成果的属性里没有 CRZDBH（实测 配套数据0922.geojson：1664 个 Feature
 * 只有 PTXMMC）。名称按库内口径 {@link DataSupport#normalizeName} 归一
 * （半角括号→中文括号、连字符、去空白），与写入侧一致。
 *
 * <p><b>② 同名跨宗地多条记录是业务常态，全部更新。</b>
 * 一条道路（如「中滨大道」）在 11 个宗地各记一条配套；文件按名称给图形，
 * 无法区分到某一条——同名记录全部更新同一份图形，并在结果中注明
 * 「该名称更新了 N 条记录」。配套图形本来就基本空白（1377 条里仅 3 条手绘），
 * 这是从无到有的补数据。
 */
@Slf4j
@Service
public class FacilityGeoImportServiceImpl implements IFacilityGeoImportService {

    /** dk_geojson 列的存储上限（列已随 15 号脚本升级 mediumtext） */
    private static final int GEO_MAX_LENGTH = 2000000;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private FacilityMapper facilityMapper;

    @Autowired
    private ChangeLogSupport changeLogSupport;

    @Autowired
    private org.jeecg.modules.land.data.oplog.mapper.DataChangeLogMapper changeLogMapper;

    @Autowired
    private DataSupport dataSupport;

    // ==================================================================
    // 一、预览 / 入库
    // ==================================================================

    @Override
    public FacilityGeoImportResultVO preview(MultipartFile file) {
        return parse(file, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FacilityGeoImportResultVO confirm(MultipartFile file) {
        FacilityGeoImportResultVO result = parse(file, true);
        log.info("配套地块图形（GeoJSON）导入：解析 {} 个地块 / 更新 {} 条记录 / 图形未变化 {} 条 / "
                        + "未匹配名称 {} 个 / 错误 {} 条 / 操作人 {}",
                result.getTotalFeatures(), result.getUpdatedRows(), result.getUnchangedRows(),
                result.getUnmatchedCount(), result.getErrorCount(),
                DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
        return result;
    }

    // ==================================================================
    // 二、解析与校验（预览与入库共用）
    // ==================================================================

    private FacilityGeoImportResultVO parse(MultipartFile file, boolean apply) {
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("请选择要导入的 GeoJSON 文件");
        }

        JsonNode root;
        try {
            root = JSON.readTree(file.getInputStream());
        } catch (IOException e) {
            log.error("读取配套 GeoJSON 文件失败：{}", file.getOriginalFilename(), e);
            throw new JeecgBootException("读取文件失败：" + e.getMessage()
                    + "（请确认上传的是 .geojson / .json 文本文件）");
        } catch (RuntimeException e) {
            log.error("解析配套 GeoJSON 文件失败：{}", file.getOriginalFilename(), e);
            throw new JeecgBootException("文件不是合法的 JSON：" + StringUtils.defaultString(e.getMessage()));
        }
        if (root == null || root.isNull() || !root.isObject()) {
            throw new JeecgBootException("文件内容不是合法的 GeoJSON 对象"
                    + "（应为 FeatureCollection 或单个 Feature）");
        }

        FacilityGeoImportResultVO result = new FacilityGeoImportResultVO();
        String fileName = file.getOriginalFilename();

        // ---------- 取 features 数组（FeatureCollection 或单个 Feature） ----------
        JsonNode features = root.path("features");
        List<JsonNode> featureList = new ArrayList<>();
        if (features.isArray()) {
            for (JsonNode feature : features) {
                featureList.add(feature);
            }
        } else if (root.path("type").asText("").equals("Feature")) {
            featureList.add(root);
        } else {
            throw new JeecgBootException("文件里没有 features 数组（确认导出的是 FeatureCollection 格式）");
        }
        if (featureList.isEmpty()) {
            throw new JeecgBootException("文件里没有解析到任何地块（features 为空）");
        }
        result.setTotalFeatures(featureList.size());

        // ---------- 逐 Feature：名称 + 图形（同名称多 Feature 合并） ----------
        // 名称按库内口径归一（normalizeName）：配套写入侧用的同一方法。
        // ★ 缺名称 / 图形不合法 → 记为警告并跳过（不作为错误卡整批）：
        //   实测成果文件里混着少量废行（缺名称 24 个、退化图形 4 个），
        //   为它们拦下 1600+ 条好数据不划算——预览里逐条列明，能导的全导。
        List<NameFeature> matched = new ArrayList<>();
        Map<String, NameFeature> byName = new LinkedHashMap<>();
        for (int i = 0; i < featureList.size(); i++) {
            int featureIndex = i + 1;
            JsonNode node = featureList.get(i);

            String rawName = readPtxmmc(node);
            if (rawName == null) {
                result.addWarning("第 " + featureIndex + " 个地块：properties 里没有 PTXMMC（配套项目名称）属性，已跳过");
                continue;
            }
            String name = DataSupport.normalizeName(rawName, 100);
            if (name == null) {
                result.addWarning("第 " + featureIndex + " 个地块：PTXMMC 属性为空白，已跳过");
                continue;
            }

            String geojson = LandGeoImportServiceImpl.normalizeGeometry(node.path("geometry"));
            if (geojson == null) {
                result.addWarning("第 " + featureIndex + " 个地块（" + name + "）：geometry 不是可用的图形"
                        + "（认 Polygon / MultiPolygon，且每个环至少 4 个坐标点、经纬度在 WGS84 范围内），已跳过");
                continue;
            }

            NameFeature feature = byName.get(name);
            if (feature == null) {
                feature = new NameFeature(featureIndex, name, new ArrayList<>());
                byName.put(name, feature);
                matched.add(feature);
            }
            feature.polygons.add(geojson);
            feature.indexes.add(featureIndex);
        }

        // ---------- 按名称合并：1 段存 Polygon、多段合并 MultiPolygon；合并后查总量 ----------
        for (NameFeature feature : matched) {
            String merged = LandGeoImportServiceImpl.mergePolygons(feature.polygons);
            if (merged == null || merged.length() > GEO_MAX_LENGTH) {
                result.addWarning("第 " + feature.indexes.get(0) + " 个地块（" + feature.name
                        + "）：地块图形数据过大（合并 " + feature.polygons.size() + " 段后 "
                        + (merged == null ? 0 : merged.length()) + " 字符，上限 " + GEO_MAX_LENGTH
                        + "），已跳过");
                continue;
            }
            feature.geojson = merged;
        }

        // ---------- 与库内比对：按名称一次批量查出命中的配套（含同名多条） ----------
        if (!matched.isEmpty()) {
            List<String> nameList = new ArrayList<>(matched.size());
            for (NameFeature feature : matched) {
                nameList.add(feature.name);
            }
            Map<String, List<Facility>> existing = loadExisting(nameList);

            List<NameFeature> toUpdate = new ArrayList<>();
            for (NameFeature feature : matched) {
                if (feature.geojson == null) {
                    continue;   // 合并超限：上面已报过错误
                }
                List<Facility> hits = existing.get(feature.name);
                if (hits == null || hits.isEmpty()) {
                    result.setUnmatchedCount(result.getUnmatchedCount() + 1);
                    result.addWarning("配套项目名称「" + feature.name
                            + "」（第 " + joinIndexes(feature.indexes) + " 个地块，共 "
                            + feature.polygons.size() + " 段图形）未匹配到系统中的配套记录，已跳过；"
                            + "请先在「配套地块数据录入」里录入该配套，或核对名称写法是否一致");
                    continue;
                }
                if (hits.size() > 1) {
                    result.setMultiRecordNames(result.getMultiRecordNames() + 1);
                }
                boolean anyChanged = false;
                int unchanged = 0;
                for (Facility hit : hits) {
                    result.setValidFeatures(result.getValidFeatures() + 1);
                    if (feature.geojson.equals(StringUtils.trimToEmpty(hit.getDkGeojson()))) {
                        // 该记录图形一字不差：跳过
                        unchanged++;
                        continue;
                    }
                    anyChanged = true;
                }
                if (!anyChanged) {
                    // 同名全部一致：整组跳过，避免无意义写库 + 履历刷屏
                    result.setUnchangedRows(result.getUnchangedRows() + hits.size());
                    continue;
                }
                result.setUnchangedRows(result.getUnchangedRows() + unchanged);
                toUpdate.add(feature);
            }
            result.setWillUpdateRows(toUpdate.size());

            // ---------- 是否允许写入 ----------
            if (result.getErrorCount() > 0) {
                result.setAborted(true);
                result.addNotice("存在 " + result.getErrorCount() + " 条错误："
                        + (apply ? "本次未更新任何配套图形。" : "预览不写库。")
                        + "请修正文件后重传。");
                return result;
            }
            if (result.getUnmatchedCount() > 0) {
                result.addNotice("有 " + result.getUnmatchedCount()
                        + " 个名称未匹配到配套记录被跳过（不影响其它名称导入）");
            }
            if (result.getMultiRecordNames() > 0) {
                result.addNotice("有 " + result.getMultiRecordNames()
                        + " 个名称在系统中对应多条配套记录（同名跨宗地），已全部更新为同一份图形");
            }
            if (result.getUnchangedRows() > 0) {
                result.addNotice("有 " + result.getUnchangedRows()
                        + " 条记录的图形与系统现值一致，无需更新");
            }
            if (apply) {
                write(toUpdate, fileName, result);
            }
        } else {
            if (result.getErrorCount() > 0) {
                result.setAborted(true);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 2.1 名称
    // ------------------------------------------------------------------

    /**
     * 从 properties 里取配套项目名称：大小写不敏感认 ptxmmc
     * （shp 字段名转 GeoJSON 后可能保持大写 PTXMMC，也可能被工具转成小写）。
     * 取原始文本，归一交给 {@link DataSupport#normalizeName}。
     */
    private String readPtxmmc(JsonNode feature) {
        JsonNode properties = feature.path("properties");
        if (!properties.isObject()) {
            return null;
        }
        JsonNode value = properties.path("PTXMMC");
        if (value.isMissingNode()) {
            value = properties.path("ptxmmc");
        }
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.trim().isEmpty() ? null : text;
    }

    // ------------------------------------------------------------------
    // 2.2 查库与写库
    // ------------------------------------------------------------------

    /** 按名称批量取库内<b>有效</b>记录（一次查询；同名跨宗地返回多条） */
    private Map<String, List<Facility>> loadExisting(List<String> names) {
        Map<String, List<Facility>> map = new LinkedHashMap<>();
        if (names == null || names.isEmpty()) {
            return map;
        }
        QueryWrapper<Facility> wrapper = new QueryWrapper<>();
        wrapper.select("id", "crzdbh", "ptxmmc", "dk_geojson")
                .eq("delFlag", "0")
                .in("ptxmmc", names);
        List<Facility> list = facilityMapper.selectList(wrapper);
        if (list != null) {
            for (Facility item : list) {
                map.computeIfAbsent(item.getPtxmmc(), k -> new ArrayList<>()).add(item);
            }
        }
        return map;
    }

    /**
     * 写库：同名命中的每条记录只更新 {@code dk_geojson / dk_geojson_overview /
     * updateTime}，并各记一条动作级变更履历（BIZ_FACILITY / ACTION_IMPORT）。
     * ★ 不写 t_facility_import_log——那是配套 Excel 导入页面的「导入记录」表，
     * 两个功能互不相关（宗地侧踩过的坑，不重复）。
     */
    private void write(List<NameFeature> toUpdate, String fileName, FacilityGeoImportResultVO result) {
        Date now = new Date();
        List<Facility> patches = new ArrayList<>();
        List<DataChangeLog> logs = new ArrayList<>();
        for (NameFeature feature : toUpdate) {
            for (Facility hit : loadExistingByName(feature.name)) {
                if (feature.geojson.equals(StringUtils.trimToEmpty(hit.getDkGeojson()))) {
                    continue;   // 写库前再核一次（预览到确认之间图形可能被改过）
                }
                Facility patch = new Facility();
                patch.setId(hit.getId());
                patch.setDkGeojson(feature.geojson);
                patch.setDkGeojsonOverview(GeoJsonSimplify.simplify(
                        feature.geojson, GeoJsonSimplify.OVERVIEW_ENTRY_POINTS));
                patch.setUpdateTime(now);
                patches.add(patch);

                logs.add(changeLogSupport.buildGeoImportRecord(
                        DataSupport.BIZ_FACILITY, hit.getId(), hit.getPtxmmc(), fileName));
            }
        }
        // ★ 分批批量写：逐条 updateById + 逐条履历在远程库上是几千次往返（必超时）
        int GEO_CHUNK = 50;
        for (int i = 0; i < patches.size(); i += GEO_CHUNK) {
            facilityMapper.batchUpdateGeo(patches.subList(i, Math.min(i + GEO_CHUNK, patches.size())));
        }
        int LOG_CHUNK = 200;
        for (int i = 0; i < logs.size(); i += LOG_CHUNK) {
            changeLogMapper.batchInsert(logs.subList(i, Math.min(i + LOG_CHUNK, logs.size())));
        }
        result.setUpdatedRows(patches.size());
    }

    /** 写库时按名称重查一次有效记录（预览与确认之间数据可能已变化） */
    private List<Facility> loadExistingByName(String name) {
        QueryWrapper<Facility> wrapper = new QueryWrapper<>();
        wrapper.eq("delFlag", "0").eq("ptxmmc", name);
        return facilityMapper.selectList(wrapper);
    }

    /** 把名称在文件中出现的位置序号拼成「3、5、8」样式（提示定位用） */
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
    // 2.3 内部结构
    // ------------------------------------------------------------------

    /**
     * 一个名称（配套项目）的聚合结果：同名称的多个 Feature 在此合并。
     * {@code geojson} 是合并后的存库串（1 段 = Polygon，多段 = MultiPolygon；
     * 合并超限时为 null 并已报错）。
     */
    private static class NameFeature {
        final int featureIndex;
        final String name;
        final List<String> polygons;
        final List<Integer> indexes = new ArrayList<>();
        String geojson;

        NameFeature(int featureIndex, String name, List<String> polygons) {
            this.featureIndex = featureIndex;
            this.name = name;
            this.polygons = polygons;
            this.indexes.add(featureIndex);
        }
    }
}
