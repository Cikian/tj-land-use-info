package org.jeecg.modules.land;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * @Description: land 模块集成测试基类
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p>三件事：
 * <ol>
 *   <li>把 {@link LandTestConfig} 装起来（DataSource + MyBatis-Plus + 本模块 Bean）；</li>
 *   <li><b>整个测试方法跑在一个事务里，结束自动回滚</b> —— 所以哪怕测试里做了
 *       增删改，库里也不会留下数据；</li>
 *   <li><b>回滚之后再物理清理一次「测试行」</b>（{@link #cleanTestRows()}，走
 *       {@code @AfterTransaction} 而不是 {@code @AfterEach}）——
 *       万一某次回滚没生效、或有人手工把数据提交了，这条兜底能保证
 *       {@code ledger_no LIKE 'YS-TEST-%'} 的记录不会残留在库里。
 *       ★ 写在 {@code @AfterEach} 里是不行的：那时事务还没结束，DELETE 会被一起回滚。</li>
 * </ol>
 *
 * <p>因此这套测试<b>可以安全地跑在线上库上</b>（默认就是 application-dev.yml 指向的库），
 * 验证的是真实数据与真实 SQL。
 *
 * <p><b>★ 为什么用 JUnit 5 而不是 JUnit 4</b>：本模块的 test 作用域引入
 * {@code spring-boot-starter-test}（2.6.6），它带来的是 JUnit 5（jupiter）。
 * maven-surefire-plugin 2.22.2 一旦发现 JUnit 5 就改用「JUnit Platform 提供器」，
 * 而该提供器<b>只认 JUnit 5 测试</b>（要跑 JUnit 4 得额外引 junit-vintage-engine）。
 * 实测用 JUnit 4 写会得到「Tests run: 0」这种最费时间的假绿，所以统一用 JUnit 5。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LandTestConfig.class)
@Transactional
public abstract class LandIntegrationTestBase {

    /** 测试行的台账编号前缀（兜底清理靠它识别，业务上不会产生这个前缀） */
    public static final String TEST_LEDGER_NO_PREFIX = "YS-TEST-";

    /** 测试行的移交事项编号前缀 */
    public static final String TEST_HANDOVER_NO_PREFIX = "YJ-TEST-";

    /**
     * 测试行的出让宗地编号前缀（经营性用地批量导入测试用）。
     *
     * <p>批量导入测试会真的往 {@code t_land} 写记录，所以兜底清理必须覆盖它 ——
     * 否则万一某次回滚没生效，测试数据会残留在真实宗地表里，
     * 而且因为它 crzdbh 唯一，还会把「同一个编号将来正式录入」的路堵死。
     */
    public static final String TEST_LAND_CRZDBH_PREFIX = "TJ-TEST-IMP-";

    /**
     * 数据管理测试行的业务前缀（方案 2.3.1（三）第 1/2/6 项用）。
     *
     * <p>与导入测试的前缀分开，是为了让「哪一批测试造的数据」在清理时能分辨 ——
     * 两者都往 {@code t_land} 写，但导入测试还会在 {@code t_land_import_log} 留痕。
     */
    public static final String TEST_DATA_KEY_PREFIX = "TJ-TEST-DATA-";

    /** 数据管理测试的宗地编号前缀（t_land / 配套 / 环节进度 共用同一前缀） */
    public static final String TEST_DATA_CRZDBH_PREFIX = "TJ-TEST-DATA-";

    @Autowired
    protected DataSource dataSource;

    /**
     * 每个测试方法结束（事务已回滚）后，把测试行物理删掉。
     *
     * <p>★ 这是「兜底」，不是断言 —— 所以它失败时**不能把测试判成失败**。
     *
     * <p>为什么必须这样处理：本测试连的是**远程库**（49.232.252.56），
     * 而 {@link LandTestConfig} 用的是 {@code DriverManagerDataSource} ——
     * 它没有连接池，**每条语句都会新开一次 TCP 连接**。下面有 7 条 DELETE，
     * 也就是 7 次建连；全量 147 个用例跑下来是上千次建连，
     * 偶尔就会撞上远程库的瞬时拒绝，抛
     * {@code CannotGetJdbcConnectionException}。
     *
     * <p>实测过两次这种偶发失败（一次在 {@code t_facility_process}，一次在
     * {@code t_supporting_facilities}，都是同一段清理代码的不同行）。
     * 它报出来的是**假红**：测试方法本身早就通过了，事务也回滚了，
     * 库里并没有数据残留 —— 只是「顺手再擦一遍」这个动作没连上库。
     * 这种失败会让人去怀疑一个根本没问题的测试，所以这里显式容忍。
     *
     * <p>处理方式：
     * <ol>
     *   <li>整段清理只建**一个**连接（{@code ConnectionCallback}），把 7 条 DELETE
     *       放在同一次连接里 —— 建连次数从 7 次降到 1 次，本身就大幅降低撞上的概率；</li>
     *   <li>失败时重试 3 次（间隔 500ms / 1000ms），覆盖瞬时抖动；</li>
     *   <li>仍然失败就**只打印一行警告**，不抛出 —— 因为事务已经回滚，
     *       真正的数据安全由事务保证，这里清不掉最多是留下几行 {@code *-TEST-*} 前缀的
     *       垃圾行，下次跑测试时同一段代码会顺手把它们一起清掉。</li>
     * </ol>
     */
    @AfterTransaction
    public void cleanTestRows() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // ★ 用 ConnectionCallback 把 9 条 DELETE 收进同一个连接：
        //   DriverManagerDataSource 每条语句一次建连，收成一次是这里最有效的优化
        ConnectionCallback<Void> clean = connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM `t_data_change_log` WHERE `biz_key` LIKE ?")) {
                ps.setString(1, TEST_DATA_KEY_PREFIX + "%");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM `t_land_attachment` WHERE `biz_key` LIKE ?")) {
                ps.setString(1, TEST_DATA_KEY_PREFIX + "%");
                ps.executeUpdate();
            }
            // ★ 附件目录表：靠 biz_id 关联测试宗地/配套（biz_key 可能为空，不能只按它清）。
            //   必须在删 t_land / t_supporting_facilities 之前执行，否则子查询已查不到 id。
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM `t_land_attachment_dir` WHERE `biz_id` IN "
                            + "(SELECT `id` FROM `t_land` WHERE `crzdbh` LIKE ? UNION "
                            + " SELECT `id` FROM `t_supporting_facilities` WHERE `crzdbh` LIKE ?)")) {
                ps.setString(1, TEST_DATA_CRZDBH_PREFIX + "%");
                ps.setString(2, TEST_DATA_CRZDBH_PREFIX + "%");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM `t_facility_process` WHERE `crzdbh` LIKE ?")) {
                ps.setString(1, TEST_DATA_CRZDBH_PREFIX + "%");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM `t_supporting_facilities` WHERE `crzdbh` LIKE ?")) {
                ps.setString(1, TEST_DATA_CRZDBH_PREFIX + "%");
                ps.executeUpdate();
            }
            // ★ 注意：t_land 上唯一键是 (crzdbh, del_flag)，所以要连 del_flag=1 的软删残留一起清。
            //   两个测试批次用了不同前缀，所以这里要清两遍。
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM `t_land` WHERE `crzdbh` LIKE ?")) {
                ps.setString(1, TEST_DATA_CRZDBH_PREFIX + "%");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM `t_land` WHERE `crzdbh` LIKE ?")) {
                ps.setString(1, TEST_LAND_CRZDBH_PREFIX + "%");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM `t_road_acceptance_ledger` WHERE `ledger_no` LIKE ?")) {
                ps.setString(1, TEST_LEDGER_NO_PREFIX + "%");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM `t_road_handover` WHERE `handover_no` LIKE ?")) {
                ps.setString(1, TEST_HANDOVER_NO_PREFIX + "%");
                ps.executeUpdate();
            }
            return null;
        };

        DataAccessException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                jdbcTemplate.execute(clean);
                return;
            } catch (DataAccessException e) {
                last = e;
                if (attempt < 3) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        // 兜底清理失败不判失败：事务已回滚，数据安全由事务保证
        System.out.println("[LandIntegrationTestBase] 测试行兜底清理未成功（已重试 3 次），"
                + "不影响测试结论；残留的 *-TEST-* 行会在下次运行时被同一段代码清掉。原因："
                + (last == null ? "未知" : last.getMessage()));
    }

    /**
     * 安全执行「兜底清理」SQL：一个连接、失败重试、最终失败只告警不判失败。
     *
     * <p>★ 为什么做成可复用的静态方法，而不是只在基类里写一遍：
     * 档案（竣工档案/台账/移交）等测试模块有**自己的** {@code @AfterTransaction} 清理
     * （它们要清各自的表），各自都手写了 {@code new JdbcTemplate(...).update(...)}。
     * 全量跑 147 个用例时，这些清理合计要开上千次连接（测试用的是
     * {@code DriverManagerDataSource}，**没有连接池**，每条语句一次 TCP 建连），
     * 于是会偶发撞上远程库拒绝，抛 {@code CannotGetJdbcConnectionException} /
     * {@code Communications link failure}。
     *
     * <p>这类失败报的是**假红**：测试方法本身早已通过、事务也已回滚、库里没有残留，
     * 只是「顺手再擦一遍」这个动作没连上库。它会让人去怀疑一个根本没问题的测试
     * （实测已在 {@code t_facility_process}、{@code t_supporting_facilities}、
     * {@code t_completion_archive} 三处出现过）。
     *
     * <p>处理方式与基类 {@link #cleanTestRows()} 完全一致：
     * <ol>
     *   <li>所有语句收进**同一个连接**（建连次数从 N 降到 1）；</li>
     *   <li>失败重试 3 次（间隔 500ms / 1000ms），覆盖瞬时抖动；</li>
     *   <li>仍失败只打印一行警告 —— 数据安全由事务保证，清不掉最多留下几行
     *       测试前缀的垃圾行，下次运行时会被同一段代码一起清掉。</li>
     * </ol>
     *
     * @param dataSource 数据源
     * @param taskName   任务名（只用于日志，便于定位是哪段清理没成功）
     * @param statements {@code [SQL, 参数1, 参数2, ...]} 形式的数组，参数可省略
     */
    protected static void safeCleanup(DataSource dataSource, String taskName, Object[]... statements) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        ConnectionCallback<Void> clean = connection -> {
            for (Object[] statement : statements) {
                try (PreparedStatement ps = connection.prepareStatement((String) statement[0])) {
                    for (int i = 1; i < statement.length; i++) {
                        ps.setObject(i, statement[i]);
                    }
                    ps.executeUpdate();
                }
            }
            return null;
        };

        DataAccessException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                jdbcTemplate.execute(clean);
                return;
            } catch (DataAccessException e) {
                last = e;
                if (attempt < 3) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        System.out.println("[LandIntegrationTestBase] " + taskName
                + " 兜底清理未成功（已重试 3 次），不影响测试结论；残留的测试前缀行会在下次运行时被清掉。原因："
                + (last == null ? "未知" : last.getMessage()));
    }

    /** 取集合第一个元素，空集合返回 null（避免测试里到处写 get(0) 的空判断） */
    protected static <T> T first(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }
}
