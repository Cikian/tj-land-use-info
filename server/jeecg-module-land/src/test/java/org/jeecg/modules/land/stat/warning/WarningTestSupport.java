package org.jeecg.modules.land.stat.warning;

import org.jeecg.modules.land.LandIntegrationTestBase;
import org.jeecg.modules.land.data.dto.FacilitySaveDTO;
import org.jeecg.modules.land.data.dto.LandSaveDTO;
import org.jeecg.modules.land.data.process.support.WorkdayCalculator;
import org.jeecg.modules.land.data.service.IFacilityAdminService;
import org.jeecg.modules.land.data.service.ILandAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 分级预警测试基类：造数 / 取预警行 / 查等级与意见 的公共工具
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-11-02
 * @Version V1.0
 *
 * <p>为什么单独抽一层而不是在两个测试类里各写一份：
 * <ul>
 *   <li><b>业务日期必须由「工作日日历」反推</b>。预警的核心变量是「逾期了几个工作日」，
 *       而「今天」是不可控的（测试任何一天都可能跑）。所以造数一律用
 *       {@link #dateBeforeWorkdays(int)}：从今天往回数 N 个工作日得到日期，
 *       保证 {@code workdaysBetween(该日期, 今天) == N}。两边共用同一个
 *       {@link WorkdayCalculator}，跨周末 / 节假日 / 未维护年份都自动一致 ——
 *       手写 {@code today - N} 会在跨周末时多算 2 天，断言立刻不稳。</li>
 *   <li><b>环节 ID 一律从库里按 {@code path} 取</b>（{@link #config(String)}），
 *       不把 40 位主键写死在测试里：换环境重建种子时主键会变，
 *       而 {@code path}（0001 / 00010001）是业务标识，稳定。</li>
 *   <li><b>预警表的断言要用 {@code Map} 而不是实体</b>：被测的是
 *       {@code ProcessWarningMapper.xml} 写进去的<b>列值</b>，
 *       走 {@code BaseMapper.selectList} 会引入 @TableLogic / 驼峰映射等额外变量，
 *       不如直接 {@code SELECT} 到 {@code Map} 看库里的真实值。</li>
 * </ul>
 *
 * <p>★ 造数一律带 {@code TJ-TEST-DATA-} 前缀，并且整个用例跑在事务里、结束回滚
 * （见 {@link LandIntegrationTestBase}）。{@code t_process_warning} 是本次新增的表，
 * 基类的兜底清理不认识它，所以本类补一段 {@link #cleanWarningRows()}。
 */
public abstract class WarningTestSupport extends LandIntegrationTestBase {

    /** 测试数据前缀（与基类的兜底清理前缀一致） */
    protected static final String TEST_PREFIX = TEST_DATA_CRZDBH_PREFIX;

    // ------------------------------------------------------------------
    // 环节配置的 path（业务标识，从库里按它取 id / 标准时长 / is_parallel）
    // ------------------------------------------------------------------

    /** 0001 立项用地规划许可阶段：标准 20 工作日，4 个事项**全部 is_parallel=1**（并行） */
    protected static final String STAGE_LIXIANG = "0001";
    protected static final String ITEM_LIXIANG_1 = "00010001";
    protected static final String ITEM_LIXIANG_2 = "00010002";

    /** 0004 项目施工阶段：标准 60 工作日，3 个事项**全部 is_parallel=0**（串行） */
    protected static final String STAGE_PROJECT = "0004";
    protected static final String ITEM_PROJECT_1 = "00040001";
    protected static final String ITEM_PROJECT_2 = "00040002";

    @Autowired
    protected IFacilityAdminService facilityAdminService;

    @Autowired
    protected ILandAdminService landAdminService;

    @Autowired
    protected WorkdayCalculator workdayCalculator;

    /** 直接看库用的模板（走测试事务里的同一个连接，语义与 Mapper 一致） */
    private JdbcTemplate jdbc;

    /** 环节配置快照：key = path（0001 / 00010001） */
    private final Map<String, Config> configs = new LinkedHashMap<>();

    @BeforeEach
    public void loadProcessConfig() {
        configs.clear();
        List<Map<String, Object>> rows = jdbc().queryForList(
                "SELECT `id`, `path`, `name`, `parent_id` AS parentId, `process_time` AS processTime, "
                        + "`is_parallel` AS isParallel, `zgbm` FROM `t_process_configuration` "
                        + "WHERE `del_flag` = 0 ORDER BY `location`");
        for (Map<String, Object> row : rows) {
            Config config = new Config();
            config.id = asString(row.get("id"));
            config.path = asString(row.get("path"));
            config.name = asString(row.get("name"));
            config.parentId = asString(row.get("parentId"));
            config.processTime = asInt(row.get("processTime"), 0);
            config.isParallel = asInt(row.get("isParallel"), 0);
            config.zgbm = asString(row.get("zgbm"));
            configs.put(config.path, config);
        }
    }

    /**
     * 事务回滚之后再物理擦一遍本次新增表的测试行。
     *
     * <p>★ 为什么单独写：{@code t_process_warning} 没有 {@code del_flag}（算出来的快照），
     * 基类的 {@code cleanTestRows()} 只覆盖了 data / archive 域的表。
     * 事务正常回滚时本来就干净，这里只是「万一回滚没生效 / 有人手工提交过」的兜底 ——
     * 与基类同一个原则：兜底失败不判测试失败（{@link #safeCleanup} 已内建重试与容忍）。
     */
    @AfterTransaction
    public void cleanWarningRows() {
        safeCleanup(dataSource, "分级预警测试行清理",
                // 预警行按冗余列 crzdbh 识别（t_process_warning 没有 del_flag，只能物理删）
                new Object[]{"DELETE FROM `t_process_warning` WHERE `crzdbh` LIKE ?", TEST_PREFIX + "%"},
                // 本测试可能临时插的决策意见（正文带测试前缀，不会误删种子）
                new Object[]{"DELETE FROM `t_decision_opinion` WHERE `opinion` LIKE ?", TEST_PREFIX + "%"},
                // 本测试可能临时插的预警等级（名称带测试前缀）
                new Object[]{"DELETE FROM `t_process_warning_level` WHERE `level_name` LIKE ?", TEST_PREFIX + "%"});
    }

    // ==================================================================
    // 一、造数
    // ==================================================================

    /**
     * 造一个「一宗地 + 一条配套」的夹具（不写任何环节进度）。
     *
     * <p>宗地与配套都带 {@code TJ-TEST-DATA-} 前缀，基类的兜底清理能认出来。
     * 里面对 {@code crzdbh} / {@code ptxmmc} 做**回读**：写入侧会做名称归一
     * （AGENTS.md §2.4：补末尾「号」、去空白），断言必须用库里真实的值，
     * 否则「编号差一个号」会让按编号筛选的用例假失败。
     */
    protected Fixture newFixture() {
        String tag = uniqueTag();
        String rawCode = TEST_PREFIX + "warn-" + tag;
        // 宗地与配套给**不同**的行政区划 / 地块名称：预警行的冗余列取「配套优先、回落宗地」，
        // 两个值不同才能验出到底取了哪一个（相同的话断言永远成立、也永远验不出 bug）
        String landXzqh = TEST_PREFIX + "预警区-宗地-" + tag;
        String landDkmc = TEST_PREFIX + "预警地块-宗地-" + tag;
        landAdminService.createLand(buildLand(rawCode, landDkmc, "市级项目", landXzqh, null), null);

        String ptxmmc = TEST_PREFIX + "预警配套-" + tag;
        String facilityXzqh = TEST_PREFIX + "预警区-配套-" + tag;
        String facilityDkmc = TEST_PREFIX + "预警地块-配套-" + tag;
        Fixture fixture = new Fixture();
        FacilitySaveDTO dto = buildFacility(storedCode(rawCode), ptxmmc, "新建", "否", "否");
        dto.setXzqh(facilityXzqh);
        dto.setDkmc(facilityDkmc);
        fixture.ptId = facilityAdminService.createFacility(dto, null);

        Map<String, Object> row = jdbc().queryForMap(
                "SELECT `crzdbh`, `ptxmmc`, `xzqh`, `dkmc` FROM `t_supporting_facilities` WHERE `id` = ?",
                fixture.ptId);
        fixture.crzdbh = asString(row.get("crzdbh"));
        fixture.ptxmmc = asString(row.get("ptxmmc"));
        fixture.xzqh = asString(row.get("xzqh"));
        fixture.dkmc = asString(row.get("dkmc"));
        fixture.landXzqh = landXzqh;
        fixture.landDkmc = landDkmc;
        return fixture;
    }

    /**
     * 直接往 {@code t_facility_process} 插一条环节进度。
     *
     * <p><b>★ 为什么不走 {@code facilityAdminService.saveProcess}</b>：
     * <ol>
     *   <li>那个方法保存成功后会**立刻重算一次**（契约 §3.9 的钩子），
     *       于是「造数 → 断言 created 条数」就变成了「造数时已经算过一次、再算一次」——
     *       想验的幂等/新增语义会被钩子提前消费掉；</li>
     *   <li>它还会按「开始日 + 标准时长」自己推算 {@code yjjssj}，
     *       而本测试恰恰需要**精确指定** {@code yjjssj} 来控制「逾期几个工作日」。</li>
     * </ol>
     * 钩子本身另有专门用例（{@code WarningRecalcTest#saveProcessHookRecalculates}）覆盖，
     * 这里用直接 INSERT 把「算法」与「触发器」两件事解耦。
     */
    protected void insertProcess(Fixture fixture, String itemPath, String lcqk,
                                 Date lckssj, Date yjjssj, Date lcjssj,
                                 String czwtlx, String jtwt) {
        Config item = config(itemPath);
        jdbc().update("INSERT INTO `t_facility_process` "
                        + "(`id`, `pt_id`, `crzdbh`, `lc_id`, `lc_path`, `lc_name`, `stage_id`, `lcqk`, "
                        + "`lckssj`, `yjjssj`, `lcjssj`, `czwtlx`, `jtwt`, `lrdw`, `lrr`, `lxdh`, "
                        + "`create_account`, `create_time`) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                TEST_PREFIX + "PROC-" + uniqueTag() + "-" + itemPath,
                fixture.ptId, fixture.crzdbh, item.id, item.path, item.name, item.parentId, lcqk,
                toSqlDate(lckssj), toSqlDate(yjjssj), toSqlDate(lcjssj), czwtlx, jtwt,
                "测试录入单位", "测试录入人", "13800000000", "test");
    }

    /** 更新某条环节进度的「实际结束时间 + 环节情况」（模拟「补录完成」） */
    protected void finishProcess(Fixture fixture, String itemPath, String lcqk, Date lcjssj) {
        jdbc().update("UPDATE `t_facility_process` SET `lcqk` = ?, `lcjssj` = ? "
                        + "WHERE `pt_id` = ? AND `lc_id` = ?",
                lcqk, toSqlDate(lcjssj), fixture.ptId, config(itemPath).id);
    }

    // ==================================================================
    // 二、取预警行
    // ==================================================================

    /** 某个配套当前的全部预警行（按库里真实列值取，供断言） */
    protected List<Map<String, Object>> warningRows(String ptId) {
        return jdbc().queryForList("SELECT `id`, `yj_name` AS yjName, `yj_type` AS yjType, `pt_id` AS ptId, "
                + "`pt_name` AS ptName, `ptxmmc`, `crzdbh`, `xzqh`, `xzqh2`, `dkmc`, "
                + "`lc_id` AS lcId, `lc_name` AS lcName, `stage_id` AS stageId, "
                + "`level_id` AS levelId, `level_code` AS levelCode, `level_name` AS levelName, "
                + "`yj_value` AS yjValue, `overdue_days` AS overdueDays, `std_days` AS stdDays, "
                + "`actual_days` AS actualDays, `problem_type` AS problemType, "
                + "`problem_detail` AS problemDetail, `decision_opinion` AS decisionOpinion, "
                + "`zrbm`, `calc_time` AS calcTime "
                + "FROM `t_process_warning` WHERE `pt_id` = ? ORDER BY `yj_type`, `lc_id`", ptId);
    }

    /** 按「分类 + 环节ID」找一行预警；没有返回 null */
    protected Map<String, Object> findWarning(List<Map<String, Object>> rows, String yjType, String lcId) {
        for (Map<String, Object> row : rows) {
            if (equalsText(yjType, row.get("yjType")) && equalsText(lcId, row.get("lcId"))) {
                return row;
            }
        }
        return null;
    }

    /** 某个配套某分类的预警行数 */
    protected long countWarnings(String ptId, String yjType) {
        List<Map<String, Object>> rows = warningRows(ptId);
        long count = 0;
        for (Map<String, Object> row : rows) {
            if (equalsText(yjType, row.get("yjType"))) {
                count++;
            }
        }
        return count;
    }

    // ==================================================================
    // 三、等级与决策意见（从库里读期望值，不硬编码话术）
    // ==================================================================

    /** 按 levelCode 取启用中的等级 id（blue / yellow / orange / red） */
    protected String levelIdByCode(String levelCode) {
        List<String> ids = jdbc().queryForList(
                "SELECT `id` FROM `t_process_warning_level` WHERE `del_flag` = 0 AND `level_code` = ? "
                        + "ORDER BY `sort_no` LIMIT 1", String.class, levelCode);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 精确匹配的决策意见正文（没有则 null） */
    protected String exactOpinion(String levelId, String problemType) {
        return firstOpinion(levelId, problemType);
    }

    /** 通用兜底意见正文（problem_type = ''，没有则 null） */
    protected String genericOpinion(String levelId) {
        return firstOpinion(levelId, "");
    }

    private String firstOpinion(String levelId, String problemType) {
        List<String> opinions = jdbc().queryForList(
                "SELECT `opinion` FROM `t_decision_opinion` WHERE `del_flag` = 0 "
                        + "AND `level_id` = ? AND `problem_type` = ? ORDER BY `sort_no`, `id` LIMIT 1",
                String.class, levelId, problemType);
        return opinions.isEmpty() ? null : opinions.get(0);
    }

    // ==================================================================
    // 四、工作日日期推算
    // ==================================================================

    /** 今天零点 */
    protected Date today() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 从今天往回数 {@code workdays} 个工作日，得到日期 D，满足
     * {@code workdaysBetween(D, 今天) == workdays}（含头不含尾口径 {@code (D, 今天]}）。
     *
     * <p>★ 用测试所依赖的**同一个** {@link WorkdayCalculator} 判断工作日：
     * 跨周末、跨法定节假日、跨「未维护年份」三种情形都会自动与被测算法一致，
     * 不需要在测试里再维护一份节假日表（那正是最容易过期的东西）。
     */
    protected Date dateBeforeWorkdays(int workdays) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today());
        int counted = 0;
        int guard = 0;
        while (counted < workdays && guard++ < 36500) {
            calendar.add(Calendar.DATE, -1);
            if (workdayCalculator.isWorkday(calendar.getTime())) {
                counted++;
            }
        }
        return calendar.getTime();
    }

    /** 字符串日期 → {@link Date}（只给固定日期的用例用，如跨国庆长假） */
    protected Date date(String yyyyMMdd) {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(yyyyMMdd);
        } catch (Exception e) {
            throw new IllegalArgumentException("测试日期写错了：" + yyyyMMdd, e);
        }
    }

    // ==================================================================
    // 五、环节配置查询
    // ==================================================================

    /** 按 path 取环节配置；取不到直接失败（说明环境的种子数据不对，而不是算法有问题） */
    protected Config config(String path) {
        Config config = configs.get(path);
        if (config == null) {
            throw new IllegalStateException("t_process_configuration 里没有 path=" + path
                    + " 的启用记录，请先执行 sql/data/02_t_process_configuration.sql（或 02b 定值脚本）");
        }
        return config;
    }

    /** 全部阶段的标准时长之和（总体预警的分母） */
    protected int stageStdDaysSum() {
        int sum = 0;
        for (Config config : configs.values()) {
            if (config.parentId == null) {
                sum += Math.max(config.processTime, 0);
            }
        }
        return sum;
    }

    /** 某阶段下的事项配置（用于断言串行/并行的前置条件） */
    protected List<Config> itemsOfStage(String stagePath) {
        String stageId = config(stagePath).id;
        List<Config> items = new ArrayList<>();
        for (Config config : configs.values()) {
            if (stageId.equals(config.parentId)) {
                items.add(config);
            }
        }
        return items;
    }

    // ==================================================================
    // 六、辅助
    // ==================================================================

    protected JdbcTemplate jdbc() {
        if (jdbc == null) {
            jdbc = new JdbcTemplate(dataSource);
        }
        return jdbc;
    }

    /** 测试数据唯一后缀（nanoTime 保证同一轮内不重号） */
    protected static String uniqueTag() {
        return String.valueOf(System.nanoTime());
    }

    protected static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    protected static int asInt(Object value, int defaultValue) {
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    protected static boolean equalsText(String expected, Object actual) {
        return expected == null ? actual == null : expected.equals(asString(actual));
    }

    protected static java.sql.Date toSqlDate(Date date) {
        return date == null ? null : new java.sql.Date(date.getTime());
    }

    /** 环节配置的一行（只留测试用得到的字段） */
    protected static final class Config {
        String id;
        String path;
        String name;
        String parentId;
        int processTime;
        int isParallel;
        String zgbm;
    }

    /** 「一宗地 + 一条配套」夹具 */
    protected static final class Fixture {
        String ptId;
        /** 落库后的规范编号（末尾带「号」，由写入侧归一） */
        String crzdbh;
        /** 落库后的配套项目名称（由写入侧归一） */
        String ptxmmc;
        /** 配套表上的行政区划（预警行的 xzqh 应取它，而不是宗地表的） */
        String xzqh;
        /** 配套表上的地块名称（预警行的 dkmc 应取它） */
        String dkmc;
        /** 宗地表上的行政区划（只在配套侧为空时才会被用到） */
        String landXzqh;
        /** 宗地表上的地块名称 */
        String landDkmc;
    }

    /** 造一个合法的宗地提交体（与 HomeStatTest / StatQueryTest 同一套字段） */
    private LandSaveDTO buildLand(String crzdbh, String dkmc, String xmfl, String xzqh, String xzqh2) {
        LandSaveDTO dto = new LandSaveDTO();
        dto.setCrzdbh(crzdbh);
        dto.setDkmc(dkmc);
        dto.setXmfl(xmfl);
        dto.setXzqh(xzqh);
        dto.setXzqh2(xzqh2);
        dto.setLrdw("测试录入单位");
        dto.setLrr("测试录入人");
        dto.setLxdh("13800000000");
        dto.setCrj(new BigDecimal("1.00"));
        return dto;
    }

    /** 造一个合法的配套提交体 */
    private FacilitySaveDTO buildFacility(String crzdbh, String ptxmmc,
                                          String jsxx, String sjfg, String sfkg) {
        FacilitySaveDTO dto = new FacilitySaveDTO();
        dto.setCrzdbh(crzdbh);
        dto.setPtxmmc(ptxmmc);
        dto.setPtsslb("道路");
        dto.setJsxx(jsxx);
        dto.setSfjg(sjfg);
        dto.setSfkg(sfkg);
        dto.setDldj("城市次干路");
        dto.setZjly("土地整理成本");
        dto.setLrdw("测试录入单位");
        dto.setLrr("测试录入人");
        dto.setLxdh("13800000000");
        dto.setGhhxkd(new BigDecimal("30.00"));
        dto.setCd(new BigDecimal("520.00"));
        dto.setTzgs(new BigDecimal("1800.00"));
        return dto;
    }
}
