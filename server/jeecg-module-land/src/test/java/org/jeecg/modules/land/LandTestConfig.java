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

    /**
     * 默认连 application-dev.yml 指向的库。
     *
     * <p>★ 地址/口令都不再写死在代码里，读取顺序：
     * <b>系统属性 → 环境变量 → .env 文件 → 下面的默认值</b>。
     *
     * <p><b>为什么由测试自己读 .env，而不是让 Maven 注入</b>：
     * surefire 的 {@code <environmentVariables>} 没有「文件不存在就跳过」的写法，
     * 一旦写成 {@code <TJ_DB_PASSWORD>${env.TJ_DB_PASSWORD}</TJ_DB_PASSWORD>}，
     * 没有 .env 的环境（CI）会因为解析不到 {@code env.TJ_DB_PASSWORD} 而**直接把构建搞挂**；
     * 而引入 dotenv 插件又要下载新依赖 —— 本工程的依赖是离线锁定的。
     * 自己读一个 properties 文件最省事：零新依赖、IDE 单测与 Maven 两条路都能生效、
     * 口令也不会出现在命令行或构建日志里。
     */
    private static final String DEFAULT_HOST = "49.232.252.56";
    private static final String DEFAULT_PORT = "3306";
    private static final String DEFAULT_DB = "tj-jyxyd";
    private static final String DEFAULT_URL = "jdbc:mysql://" + DEFAULT_HOST + ":" + DEFAULT_PORT + "/" + DEFAULT_DB
            + "?characterEncoding=UTF-8&useUnicode=true&useSSL=false&tinyInt1isBit=false"
            + "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String DEFAULT_USER = "root";

    /** .env 里读出来的键值（只读一次，避免重复 IO） */
    private static final java.util.Properties DOT_ENV = loadDotEnv();

    /**
     * 从 {@code server/.env} 读取环境变量。
     *
     * <p>手写而不是用 Properties.load：.env 里的值可能含 {@code :}、{@code =}、{@code @}
     * 等字符，而这些正是 Properties 的分隔符与转义符 —— 用 Properties.load 读
     * {@code TJ_ZK_PASSWORD=Chen0809@} 没问题，但读到含 {@code :} 的值就会截断。
     * 这里只按**第一个 {@code =}** 切分，且不做任何转义处理，语义与 .env 习惯一致。
     *
     * <p>文件不存在时返回空 Properties（CI 环境用真正的环境变量注入即可）。
     */
    private static java.util.Properties loadDotEnv() {
        java.util.Properties props = new java.util.Properties();
        // 从测试类向上找 server/.env：
        //   <repo>/server/jeecg-module-land/src/test/java/... → 相对 module 目录是 ../../.env
        // 直接按 user.dir 猜不如多找几个候选位置
        String[] candidates = {
                "../../.env",      // mvn -pl jeecg-module-land 时 user.dir = <repo>/server/jeecg-module-land
                "../.env",         // 万一从 module 的更深处执行
                ".env"             // 万一就在 module 目录下直接跑
        };
        for (String candidate : candidates) {
            java.io.File f = new java.io.File(candidate);
            if (!f.isFile()) {
                continue;
            }
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(f),
                            java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, eq).trim();
                    // ★ 值只做「去行尾 \r」，不做其它 trim：
                    //   口令首尾空格本身就非法，而 .env 我们规定「不加引号、不写行尾注释」，
                    //   所以这里不替用户猜（不 trim 空格），只处理跨平台换行差异。
                    //   ★★ 必须显式去掉末尾 \r：Windows 上本文件是 CRLF，
                    //      BufferedReader.readLine() 只按 \n 切分，会把 \r 留在值末尾，
                    //      于是口令变成 "Chen0809@mysql\r" →
                    //      报出极具误导性的 "Access denied ... (using password: YES)"，
                    //      因为肉眼看口令完全正确。
                    String value = trimmed.substring(eq + 1);
                    while (value.endsWith("\r")) {
                        value = value.substring(0, value.length() - 1);
                    }
                    props.setProperty(key, value);
                }
                System.out.println("[LandTestConfig] 已从 " + f.getPath() + " 读取 "
                        + props.size() + " 个环境变量");
                return props;
            } catch (Exception e) {
                System.out.println("[LandTestConfig] 读取 " + f.getPath() + " 失败：" + e.getMessage());
            }
        }
        System.out.println("[LandTestConfig] 未找到 .env，将只使用系统属性与环境变量");
        return props;
    }

    /** 系统属性优先，其次环境变量，再其次 .env，最后默认值 */
    private static String prop(String key, String envKey, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(envKey);
        }
        if (value == null || value.trim().isEmpty()) {
            value = DOT_ENV.getProperty(envKey);
        }
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    /**
     * 取数据库口令。
     *
     * <p>★ 不设「兜底默认口令」是刻意的：口令缺失时**立刻报错**，
     * 而不是拿一个写在仓库里的口令去连库。测试连的是真实远端库，
     * 静默用错口令的后果比一条报错严重得多 —— 错口令连不上还能看出原因，
     * 连上了就会朝真实数据写测试数据。
     *
     * <p>★ 启动时打一行**指纹**（长度 + 末 6 位哈希），不打印口令本身：
     * 排查「口令读进来了但连不上」时，指纹能立刻区分「读错了」与「库那边拒绝了」
     * 两种情况，而不会把口令写进构建日志。
     */
    private static String requirePassword() {
        String password = prop("ledger.test.password", "TJ_DB_PASSWORD", null);
        if (password == null) {
            throw new IllegalStateException(
                    "集成测试缺少数据库口令。请二选一：\n"
                    + "  1) 在 server/.env 里设置 TJ_DB_PASSWORD=...（推荐，模板见 server/.env.example）；\n"
                    + "  2) 或加 JVM 参数 -Dledger.test.password=...\n"
                    + "（.env 不会被提交到仓库）");
        }
        String fingerprint;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            fingerprint = sb.toString();
        } catch (Exception e) {
            fingerprint = "n/a";
        }
        System.out.println("[LandTestConfig] 数据库口令已就绪：长度=" + password.length()
                + " 指纹=" + fingerprint + "（不打印口令本体）");
        return password;
    }

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(prop("ledger.test.jdbcUrl", "TJ_DB_URL", DEFAULT_URL));
        dataSource.setUsername(prop("ledger.test.username", "TJ_DB_USER", DEFAULT_USER));
        dataSource.setPassword(requirePassword());
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
