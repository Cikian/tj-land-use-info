package org.jeecg.modules.land.data.process.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Description: 非工作日（节假日）日历 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p>只服务 {@link org.jeecg.modules.land.data.process.support.WorkdayCalculator}。
 * 建表与迁移脚本 {@code sql/data/06_t_non_working_day.sql}（116 行，实测只有 2023 年）。
 *
 * <p><b>★ 为什么单独一个 Mapper 而不是塞进 WorkdayCalculator 做内部类</b>：
 * MyBatis 的 Mapper 扫描（{@code @MapperScan("org.jeecg.modules.**.mapper*")}）
 * 与单元测试的 {@code MapperScannerConfigurer} 都按<b>包</b>扫描；
 * 内部类虽然也能注册，但两处扫描配置的匹配写法不一致（一个按包一个按接口），
 * 很容易出现「生产能跑、测试报 NoSuchBean」这种只在一边坏的情况。
 */
@Mapper
public interface NonWorkingDayMapper {

    /**
     * 全部非工作日日期（yyyy-MM-dd）。
     *
     * <p>★ 用 {@code DATE_FORMAT} 而不是直接取 date 列：
     * 返回 String 让调用方不必关心 JDBC 把 DATE 映射成
     * {@code java.sql.Date} / {@code LocalDate} / {@code String} 的差异
     * （这个差异随驱动版本变，而 yyyy-MM-dd 字符串是稳定的）。
     */
    @Select("SELECT DATE_FORMAT(`non_working_time`, '%Y-%m-%d') FROM `t_non_working_day` "
            + "WHERE `non_working_time` IS NOT NULL")
    List<String> selectAllDates();

    /** 某年已维护的非工作日天数（0 = 该年未维护，工作日推算会退化） */
    @Select("SELECT COUNT(*) FROM `t_non_working_day` WHERE YEAR(`non_working_time`) = #{year}")
    int countByYear(@Param("year") int year);
}
