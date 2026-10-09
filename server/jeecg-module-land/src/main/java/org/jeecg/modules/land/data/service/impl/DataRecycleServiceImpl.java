package org.jeecg.modules.land.data.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.mapper.DataRecycleMapper;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;
import org.jeecg.modules.land.data.oplog.mapper.DataChangeLogMapper;
import org.jeecg.modules.land.data.oplog.support.ChangeLogSupport;
import org.jeecg.modules.land.data.service.IDataRecycleService;
import org.jeecg.modules.land.data.support.DataSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 数据管理 · 数据更新与移除（软删 / 恢复 / 变更留痕）Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★★ 本实现要绕过的三个真实结构陷阱</b>（写错任意一个都会「功能看起来正常、数据是错的」）：
 * <ol>
 *   <li><b>两张表的软删列名不同</b>：{@code t_land.del_flag}（下划线 tinyint）
 *       vs {@code xj_kjkfb_supporting_facilities.delFlag}（驼峰 varchar）。
 *       这是复制旧表时留下的差异，写 SQL 时必须分辨。</li>
 *   <li><b>不能用实体查询查回收站</b>：{@code Land.delFlag} 上有 {@code @TableLogic}，
 *       所有实体查询都会自动追加 {@code del_flag = 0}，永远查不到已删记录。
 *       所以回收站走手写 SQL（{@link DataRecycleMapper}）。</li>
 *   <li><b>恢复前必须查冲突</b>：{@code t_land} 唯一键 {@code (crzdbh, del_flag)}
 *       意味着「同编号一条有效 + 一条已删」是上限。不检测就把
 *       {@code Duplicate entry} 原样抛给用户。</li>
 * </ol>
 *
 * <p><b>★ 为什么恢复要区分「阻断」与「仅提示」</b>：
 * 宗地有唯一键，冲突必须<b>阻断</b>（否则数据库报错）；
 * 配套项目<b>没有唯一键</b>（旧表本来就没有），恢复不会失败，
 * 但会出现两条同名配套。这种情况下<b>只提示不阻断</b> ——
 * 同名配套在业务上可能是真实的（分标段建设），系统不该替用户做这个决定。
 */
@Slf4j
@Service
public class DataRecycleServiceImpl implements IDataRecycleService {

    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_PAGE_SIZE = 200;

    @Autowired
    private DataRecycleMapper recycleMapper;

    @Autowired
    private DataChangeLogMapper changeLogMapper;

    @Autowired
    private ChangeLogSupport changeLogSupport;

    @Autowired
    private DataSupport dataSupport;

    // ==================================================================
    // 一、回收站
    // ==================================================================

    @Override
    public List<Map<String, Object>> queryRecycleList(String bizType, String keyword) {
        String type = DataSupport.clean(bizType);
        String key = DataSupport.clean(keyword);
        List<Map<String, Object>> result = new ArrayList<>();
        if (type == null || DataSupport.BIZ_LAND.equals(type)) {
            result.addAll(nullSafe(recycleMapper.selectDeletedLands(key)));
        }
        if (type == null || DataSupport.BIZ_FACILITY.equals(type)) {
            result.addAll(nullSafe(recycleMapper.selectDeletedFacilities(key)));
        }
        return result;
    }

    @Override
    public Map<String, Object> queryRecycleSummary() {
        Map<String, Object> row = recycleMapper.selectRecycleSummary();
        Map<String, Object> result = new LinkedHashMap<>();
        long landDeleted = toLong(row, "landDeleted");
        long facilityDeleted = toLong(row, "facilityDeleted");
        result.put("landDeleted", landDeleted);
        result.put("facilityDeleted", facilityDeleted);
        result.put("total", landDeleted + facilityDeleted);
        return result;
    }

    // ==================================================================
    // 二、恢复
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> restore(String bizType, String id, HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        String type = DataSupport.clean(bizType);
        if (type == null) {
            throw new JeecgBootException("缺少业务类型（land / facility）");
        }
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少主键，无法恢复");
        }
        if (DataSupport.BIZ_LAND.equals(type)) {
            return restoreLand(id, request, result);
        }
        if (DataSupport.BIZ_FACILITY.equals(type)) {
            return restoreFacility(id, request, result);
        }
        throw new JeecgBootException("不支持的业务类型：" + bizType + "（只支持 land / facility）");
    }

    private Map<String, Object> restoreLand(String id, HttpServletRequest request,
                                            Map<String, Object> result) {
        Map<String, Object> deleted = recycleMapper.selectDeletedLand(id);
        if (deleted == null) {
            throw new JeecgBootException("未找到该已移除的宗地（可能已被恢复或彻底删除）");
        }
        String crzdbh = asString(deleted.get("crzdbh"));
        // ★ 唯一键冲突检测：同编号已有一条有效记录时，恢复必然撞
        //   t_land 的 uk_land_crzdbh (crzdbh, del_flag)
        String occupiedId = recycleMapper.selectActiveLandIdByCrzdbh(crzdbh);
        if (occupiedId != null) {
            throw new JeecgBootException("无法恢复：出让宗地编号「" + crzdbh
                    + "」已被另一条有效记录占用。请先处理那条记录（例如先移除它），或放弃恢复这一条。");
        }
        int updated = recycleMapper.restoreLand(id, dataSupport.currentUsername());
        if (updated <= 0) {
            throw new JeecgBootException("恢复失败：记录状态已变化，请刷新后重试");
        }
        String summary = "恢复经营性用地「" + crzdbh + "」";
        changeLogSupport.collector(DataSupport.BIZ_LAND, id, crzdbh)
                .save(DataChangeLog.ACTION_RESTORE, summary, request);

        result.put("success", true);
        result.put("bizType", DataSupport.BIZ_LAND);
        result.put("id", id);
        result.put("bizKey", crzdbh);
        result.put("message", "已恢复");
        log.info("恢复经营性用地成功：id={}, crzdbh={}, 操作人={}",
                id, crzdbh, DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
        return result;
    }

    private Map<String, Object> restoreFacility(String id, HttpServletRequest request,
                                                Map<String, Object> result) {
        Map<String, Object> deleted = recycleMapper.selectDeletedFacility(id);
        if (deleted == null) {
            throw new JeecgBootException("未找到该已移除的配套项目（可能已被恢复或彻底删除）");
        }
        String ptxmmc = asString(deleted.get("ptxmmc"));
        String crzdbh = asString(deleted.get("crzdbh"));
        // 配套表没有唯一键：同名只提示、不阻断（业务上分标段同名可能是真实的）
        int sameName = recycleMapper.countActiveFacilityByPtxmmc(ptxmmc);
        int updated = recycleMapper.restoreFacility(id);
        if (updated <= 0) {
            throw new JeecgBootException("恢复失败：记录状态已变化，请刷新后重试");
        }
        String summary = "恢复配套项目「" + ptxmmc + "」";
        changeLogSupport.collector(DataSupport.BIZ_FACILITY, id, ptxmmc)
                .save(DataChangeLog.ACTION_RESTORE, summary, request);

        result.put("success", true);
        result.put("bizType", DataSupport.BIZ_FACILITY);
        result.put("id", id);
        result.put("bizKey", ptxmmc);
        result.put("message", "已恢复");
        if (sameName > 0) {
            result.put("warning", "该宗地（" + crzdbh + "）下已存在 " + sameName
                    + " 条同名有效配套项目，恢复后会出现同名记录，请确认是否符合预期");
        }
        log.info("恢复配套项目成功：id={}, ptxmmc={}, 同名有效数={}, 操作人={}",
                id, ptxmmc, sameName, DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> restoreBatch(String bizType, List<String> ids, HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        int success = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                try {
                    Map<String, Object> one = restore(bizType, id, request);
                    success++;
                    Object warning = one.get("warning");
                    if (warning != null) {
                        warnings.add(String.valueOf(warning));
                    }
                } catch (Exception e) {
                    Map<String, Object> failure = new LinkedHashMap<>();
                    failure.put("id", id);
                    failure.put("reason", e.getMessage());
                    failures.add(failure);
                }
            }
        }
        result.put("successCount", success);
        result.put("failCount", failures.size());
        result.put("failures", failures);
        result.put("warnings", warnings);
        return result;
    }

    // ==================================================================
    // 三、变更留痕
    // ==================================================================

    @Override
    public Map<String, Object> queryChangeLogPage(String bizType, String action, String operator,
                                                  String keyword, Integer pageNo, Integer pageSize) {
        int page = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        String type = DataSupport.clean(bizType);
        String act = DataSupport.clean(action);
        String op = DataSupport.clean(operator);
        String key = DataSupport.clean(keyword);

        long total = changeLogMapper.countPageList(type, act, op, key);
        List<DataChangeLog> records = total == 0
                ? Collections.<DataChangeLog>emptyList()
                : changeLogMapper.selectPageList(type, act, op, key, (page - 1) * size, size);
        changeLogSupport.enrichAll(records);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNo", page);
        result.put("pageSize", size);
        result.put("pages", (int) ((total + size - 1) / size));
        return result;
    }

    @Override
    public List<DataChangeLog> queryHistory(String bizType, String bizId, Integer limit) {
        String type = DataSupport.clean(bizType);
        String id = DataSupport.clean(bizId);
        if (type == null || id == null) {
            return Collections.emptyList();
        }
        int size = limit == null || limit <= 0 ? DEFAULT_HISTORY_LIMIT : Math.min(limit, 200);
        return changeLogSupport.enrichAll(changeLogMapper.selectByBiz(type, id, size));
    }

    @Override
    public List<Map<String, Object>> queryActionDistribution(String bizType) {
        return nullSafe(changeLogMapper.selectActionDistribution(DataSupport.clean(bizType)));
    }

    @Override
    public List<Map<String, String>> queryActionOptions() {
        return ChangeLogSupport.actionOptions();
    }

    // ==================================================================
    // 四、内部工具
    // ==================================================================

    private static List<Map<String, Object>> nullSafe(List<Map<String, Object>> rows) {
        return rows == null ? Collections.<Map<String, Object>>emptyList() : rows;
    }

    /** 从查库返回的 Map 里取 long（键名大小写与驱动实现有关，统一按忽略大小写找） */
    private static long toLong(Map<String, Object> row, String key) {
        Object value = findValue(row, key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 忽略大小写取 Map 键（MyBatis 返回的 Map 键大小写随驱动与别名写法而变） */
    private static Object findValue(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
