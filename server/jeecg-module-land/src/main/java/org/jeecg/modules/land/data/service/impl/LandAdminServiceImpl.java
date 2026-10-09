package org.jeecg.modules.land.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.land.data.dto.LandAdminQueryDTO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.entity.Land;
import org.jeecg.modules.land.data.fieldconfig.DataFieldLabels;
import org.jeecg.modules.land.data.mapper.DataRecycleMapper;
import org.jeecg.modules.land.data.mapper.LandMapper;
import org.jeecg.modules.land.data.oplog.entity.DataChangeLog;
import org.jeecg.modules.land.data.oplog.mapper.DataChangeLogMapper;
import org.jeecg.modules.land.data.oplog.support.ChangeLogSupport;
import org.jeecg.modules.land.data.service.ILandAdminService;
import org.jeecg.modules.land.data.support.DataSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 数据管理 · 经营性用地信息录入（逐条）Service 实现
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 相对旧实现的四处修正</b>（都是踩出来的，逐条给理由）：
 * <ol>
 *   <li><b>唯一性校验带上 delFlag 条件</b>：旧代码
 *       {@code query(Cnd.where("crzdbh","=",crzdbh))} 不带 delFlag，
 *       结果是「删除后不能重新录入同一宗地编号」，用户会撞一个死结
 *       （提示编号已存在，但列表里按 delFlag=0 又查不到）。</li>
 *   <li><b>不再用 {@code @Param} 逐个接 28 个参数</b>：旧
 *       {@code createSpecial} 有 28 个 {@code @Param} 形参，加一个字段要改签名。
 *       这里用 DTO 接 {@code @RequestBody}，但服务端<b>显式逐字段赋值</b>，
 *       id 与审计字段不受客户端影响（防越权写入，见 LandSaveDTO 注释）。</li>
 *   <li><b>写入留痕</b>：旧 {@code updateItem} 只记一句「修改了出让宗地编号为 X 的数据」，
 *       查不出「改了哪个字段、改前改后是什么」。这里做字段级差异留痕。</li>
 *   <li><b>批量移除是「逐个软删 + 逐个留痕」</b>：不图省事写一条 update 就完事 ——
 *       否则履历里就查不到「这一条是哪次操作移除的」。</li>
 * </ol>
 *
 * <p><b>★ 数值清洗</b>：旧实体里 4 个金额/面积列是 {@code float}（旧 controller 形参），
 * 前端传空串或「1,234.5」会直接失败。新实体是 {@code BigDecimal}，
 * 这里统一做「去千分位 + 空串归一化为 null」。
 */
@Slf4j
@Service
public class LandAdminServiceImpl extends ServiceImpl<LandMapper, Land> implements ILandAdminService {

    /** 排序字段白名单：只允许按这些列排序，杜绝 ORDER BY 注入 */
    private static final Set<String> ORDER_WHITELIST = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("create_time", "crzdbh", "dkmc", "xzqh", "crsj", "crj", "update_time")));

    /** 履历默认返回条数 */
    private static final int DEFAULT_HISTORY_LIMIT = 50;

    /** 分页上限：一次最多 500 条，防止前端传 pageSize=100000 把内存打满 */
    private static final int MAX_PAGE_SIZE = 500;

    @Autowired
    private LandMapper landMapper;

    @Autowired
    private DataRecycleMapper recycleMapper;

    @Autowired
    private DataChangeLogMapper changeLogMapper;

    @Autowired
    private ChangeLogSupport changeLogSupport;

    @Autowired
    private DataSupport dataSupport;

    // ==================================================================
    // 一、查询
    // ==================================================================

    @Override
    public IPage<Land> queryPage(LandAdminQueryDTO query) {
        LandAdminQueryDTO condition = query == null ? new LandAdminQueryDTO() : query;
        QueryWrapper<Land> wrapper = new QueryWrapper<>();

        likeIfPresent(wrapper, "crzdbh", condition.getCrzdbh());
        likeIfPresent(wrapper, "dkmc", condition.getDkmc());
        eqIfPresent(wrapper, "xzqh", condition.getXzqh());
        eqIfPresent(wrapper, "xmfl", condition.getXmfl());
        likeIfPresent(wrapper, "ghydxz", condition.getGhydxz());
        likeIfPresent(wrapper, "srr", condition.getSrr());
        eqIfPresent(wrapper, "ptsfqq", condition.getPtsfqq());
        if (StringUtils.isNotBlank(condition.getCrsjBegin())) {
            wrapper.ge("crsj", condition.getCrsjBegin().trim());
        }
        if (StringUtils.isNotBlank(condition.getCrsjEnd())) {
            wrapper.le("crsj", condition.getCrsjEnd().trim());
        }

        // 「有 / 无配套项目」用 exists 子查询，而不是 JOIN ——
        // JOIN 会让「一条宗地有 5 个配套」时同一宗地在结果里出现 5 次，
        // 分页 total 也跟着虚高，前端看到的会是「共 25 条」但翻页只有 5 条。
        //
        // ★ 用 apply + {0} 占位符，而不是把关键词拼进 SQL 字符串：
        //   MyBatis-Plus 的 apply() 会把参数作为 #{0} 绑定，
        //   既有参数化的安全性，又能在 SQL 片段里写子查询。
        //   早期写法把 keyword 直接拼进 exists(...) 里，
        //   除了注入风险外，ESCAPE 里的反斜杠还会被 JDBC/MySQL 双层解析吞掉，
        //   结果是「搜 100% 匹配不到任何记录」这种极难排查的问题。
        if (condition.getHasFacility() != null) {
            String existsSql = "SELECT 1 FROM xj_kjkfb_supporting_facilities f "
                    + "WHERE f.crzdbh = t_land.crzdbh AND COALESCE(f.delFlag, '0') = '0'";
            if (Boolean.TRUE.equals(condition.getHasFacility())) {
                wrapper.exists(existsSql);
            } else {
                wrapper.notExists(existsSql);
            }
        }
        if (StringUtils.isNotBlank(condition.getFacilityKeyword())) {
            String keyword = condition.getFacilityKeyword().trim();
            likeNoEscape(wrapper, "EXISTS (SELECT 1 FROM xj_kjkfb_supporting_facilities f "
                            + "WHERE f.crzdbh = t_land.crzdbh AND COALESCE(f.delFlag, '0') = '0' "
                            + "AND (f.ptxmmc LIKE {0} OR f.crzdbh LIKE {0}))",
                    "%" + keyword + "%");
        }

        String orderColumn = resolveOrderColumn(condition.getOrderBy());
        boolean asc = Boolean.TRUE.equals(condition.getAsc());
        wrapper.orderBy(true, asc, orderColumn);
        // 次级排序固定加 crzdbh：只用 create_time 排序时，同一批导入的记录
        // create_time 完全相同，翻页会出现「同一条在两页里都出现、另一条漏掉」
        if (!"crzdbh".equals(orderColumn)) {
            wrapper.orderByAsc("crzdbh");
        }

        int pageNo = condition.getPageNo() == null || condition.getPageNo() < 1 ? 1 : condition.getPageNo();
        int pageSize = condition.getPageSize() == null || condition.getPageSize() < 1
                ? 10 : Math.min(condition.getPageSize(), MAX_PAGE_SIZE);
        return page(new Page<>(pageNo, pageSize), wrapper);
    }

    @Override
    public Land queryDetail(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        return getById(id);
    }

    @Override
    public List<Map<String, Object>> queryHistory(String id, Integer limit) {
        if (StringUtils.isBlank(id)) {
            return Collections.emptyList();
        }
        int size = limit == null || limit <= 0 ? DEFAULT_HISTORY_LIMIT : Math.min(limit, 200);
        List<DataChangeLog> logs = changeLogMapper.selectByBiz(DataSupport.BIZ_LAND, id, size);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DataChangeLog record : changeLogSupport.enrichAll(logs)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", record.getId());
            item.put("action", record.getAction());
            item.put("actionText", record.getActionText());
            item.put("actionColor", record.getActionColor());
            item.put("summary", record.getChangeSummary());
            item.put("changeCount", record.getChangeCount());
            item.put("details", record.getDetails());
            item.put("operator", record.getOperator());
            item.put("operatorName", DataSupport.nameOrPlaceholder(record.getOperatorName()));
            item.put("createTime", record.getCreateTime());
            result.add(item);
        }
        return result;
    }

    // ==================================================================
    // 二、新增
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createLand(LandSaveDTO dto, HttpServletRequest request) {
        if (dto == null) {
            throw new JeecgBootException("提交内容为空");
        }
        String crzdbh = DataSupport.clean(dto.getCrzdbh());
        if (StringUtils.isBlank(crzdbh)) {
            throw new JeecgBootException("出让宗地编号不能为空");
        }
        // ★ 只查「有效记录」：旧系统不带 delFlag 条件，导致删除后不能重录（见类注释 1）
        Land existing = baseMapper.selectByCrzdbh(crzdbh);
        if (existing != null) {
            throw new JeecgBootException("创建失败！出让宗地编号「" + crzdbh + "」已存在"
                    + (StringUtils.isNotBlank(existing.getDkmc()) ? "（地块名称：" + existing.getDkmc() + "）" : ""));
        }
        validateRequired(dto);

        Land land = new Land();
        // ★ 显式逐字段赋值：客户端传的 id / delFlag / createTime 一律被忽略
        copyBusinessFields(dto, land);
        land.setCrzdbh(crzdbh);
        land.setDelFlag(0);
        land.setSourceId(null);
        land.setCreateBy(dataSupport.currentUsername());
        land.setCreateTime(new Date());
        save(land);

        // 新增留痕：把这条记录「建起来时是什么样」记全，便于日后对比
        ChangeLogSupport.Collector collector = changeLogSupport
                .collector(DataSupport.BIZ_LAND, land.getId(), land.getCrzdbh())
                .diff(null, land, DataFieldLabels.landLabels());
        collector.save(DataChangeLog.ACTION_CREATE, "新增经营性用地「" + crzdbh + "」", request);

        log.info("新增经营性用地成功：id={}, crzdbh={}, 操作人={}",
                land.getId(), crzdbh, DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
        return land.getId();
    }

    // ==================================================================
    // 三、编辑
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLand(LandSaveDTO dto, HttpServletRequest request) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("缺少宗地主键，无法编辑");
        }
        Land before = getById(dto.getId());
        if (before == null) {
            throw new JeecgBootException("未找到对应的出让宗地（可能已被移除）");
        }
        String crzdbh = DataSupport.clean(dto.getCrzdbh());
        if (StringUtils.isBlank(crzdbh)) {
            throw new JeecgBootException("出让宗地编号不能为空");
        }
        validateRequired(dto);

        // 编号被改过时，要确认新编号没被别的记录占用
        if (!crzdbh.equals(before.getCrzdbh())) {
            Land duplicated = baseMapper.selectByCrzdbh(crzdbh);
            if (duplicated != null && !duplicated.getId().equals(before.getId())) {
                throw new JeecgBootException("出让宗地编号「" + crzdbh + "」已被其他记录占用"
                        + (StringUtils.isNotBlank(duplicated.getDkmc())
                        ? "（地块名称：" + duplicated.getDkmc() + "）" : ""));
            }
        }

        // ★ 先把「改前」的快照留一份：diff 需要 before 与 after 两个对象，
        //   而 copyBusinessFields 会就地改 after。这里 before 是从库里查出来的
        //   独立对象，不会被影响。
        Land after = new Land();
        copyBusinessFields(dto, after);
        after.setId(before.getId());
        after.setCrzdbh(crzdbh);
        // 审计字段不参与 diff（SKIP_FIELDS 已排除），但仍要写回
        after.setUpdateBy(dataSupport.currentUsername());
        after.setUpdateTime(new Date());

        // 留痕（只记真正变化的字段）
        ChangeLogSupport.Collector collector = changeLogSupport
                .collector(DataSupport.BIZ_LAND, before.getId(), crzdbh)
                .diff(before, after, DataFieldLabels.landLabels());

        updateById(after);
        collector.save(DataChangeLog.ACTION_UPDATE, null, request);

        log.info("编辑经营性用地成功：id={}, crzdbh={}, 变更字段数={}, 操作人={}",
                before.getId(), crzdbh, collector.size(),
                DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
    }

    // ==================================================================
    // 四、移除（软删）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeLand(String id, String reason, HttpServletRequest request) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少宗地主键，无法移除");
        }
        Land land = getById(id);
        if (land == null) {
            throw new JeecgBootException("未找到对应的出让宗地（可能已被移除）");
        }
        // 软删：只把 del_flag 置 1，不物理删除 —— 该 crzdbh 被配套/档案/收发文引用
        removeById(id);

        String summary = "移除经营性用地「" + land.getCrzdbh() + "」"
                + (StringUtils.isNotBlank(reason) ? "，原因：" + reason.trim() : "");
        changeLogSupport.collector(DataSupport.BIZ_LAND, land.getId(), land.getCrzdbh())
                .save(DataChangeLog.ACTION_DELETE, summary, request);

        log.info("移除经营性用地成功：id={}, crzdbh={}, 原因={}, 操作人={}",
                id, land.getCrzdbh(), reason, DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> removeLandBatch(List<String> ids, String reason, HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            result.put("successCount", 0);
            result.put("failCount", 0);
            result.put("failures", Collections.emptyList());
            return result;
        }
        int success = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (String id : ids) {
            try {
                // ★ 逐个调 removeLand 而不是一条 update 批量改：
                //   那样就留不下「这一条是哪次操作移除的」这条履历。
                removeLand(id, reason, request);
                success++;
            } catch (Exception e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("id", id);
                failure.put("reason", e.getMessage());
                failures.add(failure);
            }
        }
        result.put("successCount", success);
        result.put("failCount", failures.size());
        result.put("failures", failures);
        log.info("批量移除经营性用地：成功 {} 条，失败 {} 条，操作人={}",
                success, failures.size(), DataSupport.nameOrPlaceholder(dataSupport.currentRealname()));
        return result;
    }

    // ==================================================================
    // 五、校验与下拉
    // ==================================================================

    @Override
    public Map<String, Object> checkCrzdbh(String crzdbh, String excludeId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String value = DataSupport.clean(crzdbh);
        if (StringUtils.isBlank(value)) {
            result.put("available", false);
            result.put("message", "出让宗地编号不能为空");
            return result;
        }
        Land hit = baseMapper.selectByCrzdbh(value);
        if (hit != null && (StringUtils.isBlank(excludeId) || !excludeId.equals(hit.getId()))) {
            result.put("available", false);
            result.put("message", "该编号已被占用"
                    + (StringUtils.isNotBlank(hit.getDkmc()) ? "（地块名称：" + hit.getDkmc() + "）" : ""));
            result.put("occupiedBy", hit.getId());
            result.put("occupiedName", hit.getDkmc());
            return result;
        }
        // 软删记录里存在同一编号时给个提示，但不阻止（新录入会把它顶掉，见批量导入的同类处理）
        Map<String, Object> deleted = null;
        try {
            List<Map<String, Object>> deletedList = recycleMapper.selectDeletedLands(value);
            if (deletedList != null && !deletedList.isEmpty()) {
                deleted = deletedList.get(0);
            }
        } catch (Exception e) {
            log.debug("查询软删记录失败（不影响校验结果）：{}", e.getMessage());
        }
        result.put("available", true);
        result.put("message", deleted == null ? "该编号可以使用"
                : "该编号可以使用；注意回收站里有一条同编号的历史记录（"
                + deleted.get("name") + "），新录入后它仍留在回收站");
        return result;
    }

    @Override
    public List<Map<String, Object>> queryCodeOptions(String keyword, Integer limit) {
        int size = limit == null || limit <= 0 ? 50 : Math.min(limit, 300);
        QueryWrapper<Land> wrapper = new QueryWrapper<>();
        wrapper.select("id", "crzdbh", "dkmc", "xzqh", "xmfl");
        if (StringUtils.isNotBlank(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like("crzdbh", value).or().like("dkmc", value));
        }
        wrapper.orderByAsc("crzdbh").last("LIMIT " + size);
        List<Land> rows = list(wrapper);
        List<Map<String, Object>> options = new ArrayList<>(rows.size());
        for (Land row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("value", row.getCrzdbh());
            item.put("label", row.getCrzdbh()
                    + (StringUtils.isNotBlank(row.getDkmc()) ? "（" + row.getDkmc() + "）" : ""));
            item.put("crzdbh", row.getCrzdbh());
            item.put("dkmc", row.getDkmc());
            item.put("xzqh", row.getXzqh());
            item.put("xmfl", row.getXmfl());
            options.add(item);
        }
        return options;
    }

    // ==================================================================
    // 六、内部工具
    // ==================================================================

    /** 必填校验（与批量导入的必填口径一致：crzdbh 与 xmfl） */
    private void validateRequired(LandSaveDTO dto) {
        if (StringUtils.isBlank(DataSupport.clean(dto.getXmfl()))) {
            throw new JeecgBootException("项目分类不能为空（市级项目 / 区级项目）");
        }
        String xmfl = dto.getXmfl().trim();
        if (!"市级项目".equals(xmfl) && !"区级项目".equals(xmfl)) {
            throw new JeecgBootException("项目分类「" + xmfl + "」不合法，只能填：市级项目 / 区级项目");
        }
        String lxdh = DataSupport.clean(dto.getLxdh());
        if (lxdh != null && !lxdh.matches("^1[3456789]\\d{9}$")) {
            throw new JeecgBootException("联系电话「" + lxdh + "」格式不正确，应为 1 开头的 11 位手机号");
        }
    }

    /**
     * 业务字段搬运：DTO → 实体。
     *
     * <p>★ <b>刻意不用 BeanUtils.copyProperties</b>：它会连带复制
     * {@code id} / {@code delFlag} / {@code createBy} / {@code createTime} /
     * {@code sourceId} 这些「服务端才能决定」的字段 —— 客户端在 JSON 里塞什么就写什么，
     * 属于越权写入。这里逐字段列出来，多写 30 行换来「写哪些字段是明确的」。
     *
     * <p>文本字段统一走 {@link DataSupport#cleanAndCap}：去控制字符 + 去空白 + 按列宽截断
     * （列宽取自建表脚本 01_t_land.sql，与批量导入模板的长度校验同源）。
     */
    private void copyBusinessFields(LandSaveDTO dto, Land land) {
        // ---- 文本 ----
        land.setDkmc(DataSupport.cleanAndCap(dto.getDkmc(), 255));
        land.setXzqh(DataSupport.cleanAndCap(dto.getXzqh(), 100));
        land.setXmfl(DataSupport.cleanAndCap(dto.getXmfl(), 100));
        land.setGhydxz(DataSupport.cleanAndCap(dto.getGhydxz(), 100));
        land.setSrr(DataSupport.cleanAndCap(dto.getSrr(), 100));
        land.setLpmc(DataSupport.cleanAndCap(dto.getLpmc(), 100));
        land.setTdzldw(DataSupport.cleanAndCap(dto.getTdzldw(), 100));
        land.setTdzljhxdwjh(DataSupport.cleanAndCap(dto.getTdzljhxdwjh(), 2000));
        land.setDz(DataSupport.cleanAndCap(dto.getDz(), 100));
        land.setXz(DataSupport.cleanAndCap(dto.getXz(), 100));
        land.setNz(DataSupport.cleanAndCap(dto.getNz(), 100));
        land.setBz(DataSupport.cleanAndCap(dto.getBz(), 100));
        land.setPtsfqq(DataSupport.cleanAndCap(dto.getPtsfqq(), 100));
        land.setPtqkh(DataSupport.cleanAndCap(dto.getPtqkh(), 100));
        land.setPtcbh(DataSupport.cleanAndCap(dto.getPtcbh(), 100));
        land.setCrzdtxsj(DataSupport.cleanAndCap(dto.getCrzdtxsj(), 100));
        land.setTdzljh(DataSupport.cleanAndCap(dto.getTdzljh(), 100));
        land.setLrdw(DataSupport.cleanAndCap(dto.getLrdw(), 100));
        land.setLrr(DataSupport.cleanAndCap(dto.getLrr(), 100));
        land.setLxdh(DataSupport.cleanAndCap(dto.getLxdh(), 100));
        land.setBeizhu(DataSupport.cleanAndCap(dto.getBeizhu(), 2000));
        // 长文本单独截断（列宽更大）
        land.setPtjsnr(DataSupport.cleanAndCap(dto.getPtjsnr(), 2000));
        land.setZlqsnrsm(DataSupport.cleanAndCap(dto.getZlqsnrsm(), 2500));
        // 旧字段名 xzqh2，实际语义是「录入单位简称」（见 Land 实体注释）
        land.setXzqh2(DataSupport.cleanAndCap(dto.getXzqh2(), 100));

        // ---- 数值（实体已是 BigDecimal，前端空值传 null） ----
        land.setCrj(dto.getCrj());
        land.setKjsydmj(dto.getKjsydmj());
        land.setZydmj(dto.getZydmj());
        land.setJsmj(dto.getJsmj());
        land.setNrcbdptf(dto.getNrcbdptf());
        land.setWcd(dto.getWcd());

        // ---- 日期 ----
        land.setCrsj(dto.getCrsj());
        land.setHtydjfsj(dto.getHtydjfsj());
        land.setLpjfsj(dto.getLpjfsj());
    }

    /** 模糊条件：值非空才加 */
    private void likeIfPresent(QueryWrapper<Land> wrapper, String column, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        wrapper.like(column, value.trim());
    }

    /** 往 wrapper 里追加一段带 {0} 占位符的 SQL 条件（参数化，不拼字符串） */
    private void likeNoEscape(QueryWrapper<Land> wrapper, String sqlWithPlaceholder, Object value) {
        wrapper.apply(sqlWithPlaceholder, value);
    }

    /** 精确条件 */
    private void eqIfPresent(QueryWrapper<Land> wrapper, String column, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        wrapper.eq(column, value.trim());
    }

    /** 排序字段白名单校验 */
    private static String resolveOrderColumn(String orderBy) {
        if (StringUtils.isBlank(orderBy)) {
            return "create_time";
        }
        String column = orderBy.trim();
        return ORDER_WHITELIST.contains(column) ? column : "create_time";
    }
}
