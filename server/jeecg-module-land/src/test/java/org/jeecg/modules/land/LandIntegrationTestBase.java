package org.jeecg.modules.land;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
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

    /** 每个测试方法结束（事务已回滚）后，把测试行物理删掉 */
    @AfterTransaction
    public void cleanTestRows() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM `t_road_acceptance_ledger` WHERE `ledger_no` LIKE ?",
                TEST_LEDGER_NO_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM `t_road_handover` WHERE `handover_no` LIKE ?",
                TEST_HANDOVER_NO_PREFIX + "%");
        // ★ 经营性用地批量导入测试造的数据（t_land 上的唯一键是 (crzdbh, del_flag)，
        //   所以这里要连 del_flag=1 的软删残留一起清掉）
        jdbcTemplate.update("DELETE FROM `t_land` WHERE `crzdbh` LIKE ?",
                TEST_LAND_CRZDBH_PREFIX + "%");
        // ★ 数据管理其余 5 项的测试数据（方案 2.3.1（三）第 1/2/4/5/6 项），
        //   它们用的是另一个前缀，必须单独清一遍。
        //   顺序很要紧：先删子表再删父表 —— 虽然本库业务表之间没有外键约束
        //   （实测确认 5 个外键全在 sys_qrtz_*），但 t_facility_process.pt_id
        //   指向配套项目、t_land_attachment.biz_id 指向三者，先删子表更符合直觉，
        //   也避免将来真加了外键时这段清理突然开始报错。
        jdbcTemplate.update("DELETE FROM `t_data_change_log` WHERE `biz_key` LIKE ?",
                TEST_DATA_KEY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM `t_land_attachment` WHERE `biz_key` LIKE ?",
                TEST_DATA_KEY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM `t_facility_process` WHERE `crzdbh` LIKE ?",
                TEST_DATA_CRZDBH_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM `xj_kjkfb_supporting_facilities` WHERE `crzdbh` LIKE ?",
                TEST_DATA_CRZDBH_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM `t_land` WHERE `crzdbh` LIKE ?",
                TEST_DATA_CRZDBH_PREFIX + "%");
    }

    /** 取集合第一个元素，空集合返回 null（避免测试里到处写 get(0) 的空判断） */
    protected static <T> T first(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }
}
