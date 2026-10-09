package org.jeecg.modules.land;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * @Description: land 模块集成测试用的最小 Spring 配置
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-30
 * @Version: V1.0
 *
 * <p><b>★ 为什么需要它</b>：整个应用（`jeecg-system-start`）在本机构建不了 ——
 * `jeecg-system-biz` 依赖中台私有件 {@code cn.cikian:stargis-zk-sdk}，该件不在公共仓库，
 * 于是「把应用起起来做 HTTP 验证」这条路走不通。
 * 这里只装配**本模块真正需要的那几件东西**：
 * <pre>
 *   DataSource（直连 application-dev.yml 同一个库）
 *   ↓
 *   MybatisSqlSessionFactoryBean（★ 必须用 MP 的这一个，用原生 MyBatis 的
 *     SqlSessionFactoryBean 则 BaseMapper 的 CRUD 不可用）+ 分页插件
 *   ↓
 *   MapperScannerConfigurer（扫本模块的 Mapper 接口）
 *   ↓
 *   @ComponentScan（本模块的 Controller / Service / Support）
 *   ↓
 *   事务管理器（让 Service 上的 @Transactional 生效，测试类再加一层回滚）
 * </pre>
 *
 * <p>连接参数优先取系统属性，默认与 {@code application-dev.yml} 一致：
 * <pre>
 *   -Dledger.test.jdbcUrl=... -Dledger.test.username=... -Dledger.test.password=...
 * </pre>
 *
 * <p><b>★ tinyInt1isBit=false 不能省</b>：13 个资料勾选列与 del_flag 都是 {@code tinyint(1)}，
 * 实体里是 {@code Integer}；不开这个参数，驱动会把它们映射成 Boolean，
 * 与实体的 Integer 不匹配（这正是 dev 配置里也带着它的原因）。
 */
@Configuration
@EnableTransactionManagement
@ComponentScan(basePackages = {
        "org.jeecg.modules.land.archive.ledger.controller",
        "org.jeecg.modules.land.archive.ledger.service.impl",
        "org.jeecg.modules.land.archive.ledger.support",
        // 道路交付及养护协议移交事项（方案 2.3.2 第 6 项）
        "org.jeecg.modules.land.archive.handover.controller",
        "org.jeecg.modules.land.archive.handover.service.impl",
        "org.jeecg.modules.land.archive.handover.support",
        // 竣工验收项目历史工程资料数字化档案（方案 2.3.2 第 8 项）
        "org.jeecg.modules.land.archive.completion.controller",
        "org.jeecg.modules.land.archive.completion.service.impl",
        "org.jeecg.modules.land.archive.completion.support",
        // ★ 数据管理 · 经营性用地批量导入（方案 2.3.1（三）第 3 项）
        //   这里同时扫 imports 下的 service.impl（导入服务）与 support（当前用户/模板支撑），
        //   并复用 data.service.impl（ILandService）与 data.mapper（LandMapper）——
        //   批量导入最终落的是 t_land，走的就是宗地那一套 Service/Mapper。
        "org.jeecg.modules.land.data.imports.service.impl",
        "org.jeecg.modules.land.data.imports.support",
        // ★ 数据管理 · 其余 5 项（方案 2.3.1（三）第 1/2/4/5/6 项）
        //   服务层：宗地录入 / 配套录入 / 回收站 / 附件 / 配套批量导入
        //   支撑层：当前用户与文本清洗 / 变更留痕 / 工作日日历
        "org.jeecg.modules.land.data.service.impl",
        "org.jeecg.modules.land.data.support",
        "org.jeecg.modules.land.data.oplog.support",
        "org.jeecg.modules.land.data.process.support",
        "org.jeecg.modules.land.data.attachment.service.impl",
        "org.jeecg.modules.land.data.imports.facility.service.impl",
        "org.jeecg.modules.land.data.imports.facility.support"
})
public class LandTestConfig {

    /** 默认连 application-dev.yml 指向的库（可用系统属性覆盖） */
    private static final String DEFAULT_URL = "jdbc:mysql://49.232.252.56:3306/tj-jyxyd"
            + "?characterEncoding=UTF-8&useUnicode=true&useSSL=false&tinyInt1isBit=false"
            + "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "Chen0809@mysql";

    private static String prop(String key, String defaultValue) {
        String value = System.getProperty(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(prop("ledger.test.jdbcUrl", DEFAULT_URL));
        dataSource.setUsername(prop("ledger.test.username", DEFAULT_USER));
        dataSource.setPassword(prop("ledger.test.password", DEFAULT_PASSWORD));
        return dataSource;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        // 本模块 + 收发文/档案等既有模块的 XML 都不在这里加载：只加载本模块的，保持测试聚焦
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(
                "classpath*:org/jeecg/modules/land/**/mapper/xml/*Mapper.xml"));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(interceptor);
        return factory.getObject();
    }

    @Bean
    public static MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        // ★ 逐个列出 Mapper 包，不用 `org.jeecg.modules.land` 通配：
        //   MapperScannerConfigurer 会把「包下所有接口」都注册成 Mapper，
        //   而本模块的 service/ 包下也有接口（IXxxService），一起扫进来会产生
        //   与真实 Bean 重名的 MapperFactoryBean，属于无谓的噪音。
        configurer.setBasePackage(String.join(",",
                "org.jeecg.modules.land.archive.ledger.mapper",
                "org.jeecg.modules.land.archive.handover.mapper",
                "org.jeecg.modules.land.archive.completion.mapper",
                "org.jeecg.modules.land.archive.mapper",
                "org.jeecg.modules.land.archive.document.mapper",
                "org.jeecg.modules.land.escalation.mapper",
                "org.jeecg.modules.land.data.mapper",
                // 数据管理 · 经营性用地批量导入的日志 Mapper（注解 SQL，无 XML）
                "org.jeecg.modules.land.data.imports.mapper",
                // 数据管理 · 其余 5 项（方案 2.3.1（三）第 1/2/4/5/6 项）
                "org.jeecg.modules.land.data.process.mapper",
                "org.jeecg.modules.land.data.attachment.mapper",
                "org.jeecg.modules.land.data.oplog.mapper",
                "org.jeecg.modules.land.data.imports.facility.mapper"));
        configurer.setSqlSessionFactoryBeanName("sqlSessionFactory");
        return configurer;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
