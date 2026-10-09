package org.jeecg.modules.land;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code DotEnvEnvironmentPostProcessor} 的回归测试。
 *
 * <p><b>它防的是什么</b>：本工程 4 个 profile 的 yml 里全是 {@code ${TJ_XXX}} 占位符，
 * 真实值在 {@code server/.env}。Spring Boot <b>不认识 .env</b>，全靠这个后置处理器
 * 把它读成一个 PropertySource。它一旦失效（改错注册文件、解析规则写错、
 * 查找目录层数不够），启动就会直接失败：
 * <pre>
 *   Could not resolve placeholder 'TJ_DB_HOST' in value "${TJ_DB_HOST}"
 * </pre>
 * 而这条报错发生在启动最早期，表现是「起不来」而不是「某功能不对」，
 * 排查成本高。所以这里用测试把它钉住。
 *
 * <p><b>为什么能测到「真实生效」</b>：测试直接实例化后置处理器并调用
 * {@code postProcessEnvironment}，然后用 {@link StandardEnvironment#resolvePlaceholders}
 * 走一遍与 Spring 启动时**完全相同**的占位符解析路径 ——
 * 不是只测「文件能读出来」，而是测「占位符真的能被解开」。
 */
public class DotEnvEnvironmentPostProcessorTest {

    /** 后置处理器全限定名（与 META-INF/spring.factories 里登记的必须一致） */
    private static final String PROCESSOR_CLASS =
            "org.jeecg.config.DotEnvEnvironmentPostProcessor";

    /**
     * 注册是否生效：能通过 spring.factories 被 Spring Boot 发现。
     *
     * <p>★ 这条断言的意义：{@code spring.factories} 是 Java Properties 格式
     * （注释用 {@code !}/{@code #}，行尾反斜杠是**续行**而不是换行）。
     * 格式写错时文件「存在但解析不出条目」—— 没人会注意到，
     * 直到启动报占位符解析不了。这里把它变成一条会红的测试。
     *
     * <p>★ 用 {@code loadFactoryNames} 而不是 {@code loadFactories}：
     * 后者会把 classpath 上**所有** EnvironmentPostProcessor 都实例化一遍，
     * 其中 Spring Boot 自带的 {@code CloudFoundryVcapEnvironmentPostProcessor}
     * 没有无参构造，于是抛
     * {@code IllegalArgumentException: Unable to instantiate factory class [...]}。
     * 那是框架自身的事，与本模块无关，却会让这条断言红掉 ——
     * 所以只查「名字在不在清单里」，实例化单独测（见 {@link #instantiableByClassName}）。
     */
    @Test
    public void registeredInSpringFactories() {
        List<String> names = SpringFactoriesLoader.loadFactoryNames(
                EnvironmentPostProcessor.class, getClass().getClassLoader());
        assertTrue(names.contains(PROCESSOR_CLASS),
                "META-INF/spring.factories 未登记 " + PROCESSOR_CLASS
                        + " —— 启动时 .env 不会被加载，占位符会解析失败。当前清单：" + names);
    }

    /** 通过类名能实例化（与 spring.factories 的反射实例化同一路径） */
    @Test
    public void instantiableByClassName() throws Exception {
        Class<?> clazz = Class.forName(PROCESSOR_CLASS);
        assertNotNull(clazz.getDeclaredConstructor().newInstance());
        assertTrue(EnvironmentPostProcessor.class.isAssignableFrom(clazz),
                PROCESSOR_CLASS + " 必须实现 EnvironmentPostProcessor");
    }

    /**
     * 核心：加载 .env 后，{@code ${TJ_XXX}} 能被解析成真实值。
     *
     * <p>测试从 {@code jeecg-module-land} 模块目录运行，处理器会向上找到
     * {@code server/.env}（这正是 IDE/bootRun 场景下工作目录不是 server 时的真实情况）。
     */
    @Test
    public void resolvesPlaceholdersFromDotEnv() throws Exception {
        ConfigurableEnvironment environment = new StandardEnvironment();
        EnvironmentPostProcessor processor = (EnvironmentPostProcessor)
                Class.forName(PROCESSOR_CLASS).getDeclaredConstructor().newInstance();
        processor.postProcessEnvironment(environment, null);

        String host = environment.resolvePlaceholders("${TJ_DB_HOST}");
        String user = environment.resolvePlaceholders("${TJ_DB_USER}");
        String name = environment.resolvePlaceholders("${TJ_DB_NAME}");

        // 断言「解析成功」本身：解析不出来时 Spring 会抛
        // IllegalArgumentException（正是线上那条报错），测试会直接红。
        assertFalse(host.contains("${"), "TJ_DB_HOST 未被解析：" + host);
        assertNotNull(host);
        assertNotNull(user);
        assertNotNull(name);

        // 拼出 JDBC URL，顺带验证 yml 里那种「多个占位符嵌套在字符串中」的写法
        String url = environment.resolvePlaceholders(
                "jdbc:mysql://${TJ_DB_HOST}:${TJ_DB_PORT}/${TJ_DB_NAME}");
        assertFalse(url.contains("${"), "JDBC URL 里仍有未解析的占位符：" + url);
        assertTrue(url.startsWith("jdbc:mysql://"), "JDBC URL 格式不对：" + url);

        System.out.println("[DotEnvEnvironmentPostProcessorTest] 解析结果："
                + "host=" + host + " user=" + user + " db=" + name);
    }

    /**
     * 优先级：系统属性必须压过 .env。
     *
     * <p>CI/生产用真环境变量或 {@code -D} 注入时，不能被仓库外的 .env 意外覆盖 ——
     * 这是「加了一个本地兜底文件」最需要防的副作用。
     */
    @Test
    public void systemPropertyWinsOverDotEnv() throws Exception {
        String key = "TJ_DB_HOST";
        String overridden = "system-property-should-win";
        System.setProperty(key, overridden);
        try {
            ConfigurableEnvironment environment = new StandardEnvironment();
            EnvironmentPostProcessor processor = (EnvironmentPostProcessor)
                    Class.forName(PROCESSOR_CLASS).getDeclaredConstructor().newInstance();
            processor.postProcessEnvironment(environment, null);

            assertEquals(overridden, environment.resolvePlaceholders("${" + key + "}"),
                    "系统属性应优先于 .env（PropertySource 追加在最后）");
        } finally {
            System.clearProperty(key);
        }
    }

    /**
     * .env 里没有的键，不应被凭空造出来。
     *
     * <p>否则「配置写错了」会变成「读到了一个来路不明的值」，更难查。
     */
    @Test
    public void missingKeyIsNotInvented() throws Exception {
        ConfigurableEnvironment environment = new StandardEnvironment();
        EnvironmentPostProcessor processor = (EnvironmentPostProcessor)
                Class.forName(PROCESSOR_CLASS).getDeclaredConstructor().newInstance();
        processor.postProcessEnvironment(environment, null);

        assertNull(environment.getProperty("TJ_THIS_KEY_DOES_NOT_EXIST_IN_DOT_ENV"),
                "不存在的键不应出现在环境中");
    }
}
