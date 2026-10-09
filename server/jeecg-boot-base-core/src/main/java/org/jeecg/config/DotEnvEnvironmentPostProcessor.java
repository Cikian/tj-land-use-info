package org.jeecg.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 启动时加载 {@code .env} 文件，把它作为一个最低优先级的 PropertySource。
 *
 * <h3>为什么必须要有这个类</h3>
 * 本工程把 yml 里的口令/密钥都换成了 {@code ${TJ_DB_PASSWORD}} 这类占位符，
 * 真实值放在 {@code server/.env}（不进仓库）。但
 * <b>Spring Boot 本身并不认识 .env 文件</b> —— 它只解析系统属性、
 * 环境变量、以及 {@code application-*.properties/yml}。
 * 于是直接用 IDE 或 {@code java -jar} 启动时，占位符解析不到，报：
 * <pre>
 *   Could not resolve placeholder 'TJ_DB_HOST' in value "${TJ_DB_HOST}"
 * </pre>
 *
 * <p>原先只提供了 {@code scripts/run-with-env.ps1} 来注入环境变量，但那样：
 * <ul>
 *   <li>IDE 里点「Run」还是会失败；</li>
 *   <li>{@code java -jar} 也要记得先 source 一遍；</li>
 *   <li>排查时容易误以为是配置写错了，而其实只是「少了一个启动壳」。</li>
 * </ul>
 * 这个后置处理器把「读 .env」变成应用自身的能力，三条路径（IDE / jar / 脚本）
 * 都能直接用，脚本则保留给「想用真环境变量」的场景。
 *
 * <h3>优先级：最低</h3>
 * 用 {@link MapPropertySource} 追加到**最后**，因此：
 * <pre>
 *   系统属性 -Dkey=value   ＞   真实环境变量   ＞   .env 文件   ＞   yml 里的默认值
 * </pre>
 * 这样 CI/生产用真环境变量注入时，不会被仓库外的 .env 意外覆盖；
 * 而本地开发没有环境变量时，.env 正好补上。
 *
 * <h3>为什么不用 spring.config.import</h3>
 * {@code spring.config.import} 支持 {@code optional:file:.env[.properties]}，
 * 但 .env 的语法（{@code KEY=VALUE}、{@code #} 注释、值里可含 {@code @ : #}）
 * 并不是 properties 语法 —— properties 会把 {@code :} 当分隔符，
 * 口令里一旦出现 {@code :} 就会被**静默截断**，然后报一个「Access denied」
 * 而肉眼看口令完全正确。这里自己解析，可以严格按 .env 的习惯来。
 *
 * <p>★ 文件不存在时**静默跳过**（不抛异常）：CI/生产本来就不该有 .env，
 * 它们靠真环境变量；只有该有而没有时，占位符解析失败会给出更准确的报错。
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 自定义 .env 位置的系统属性名（绝对路径或相对当前工作目录） */
    private static final String LOCATION_PROPERTY = "jeecg.dotenv.location";

    /** 向上查找 .env 的层数：兼容 IDE（模块目录）、mvn（模块目录）、jar（server 目录） */
    private static final int MAX_PARENT_LEVELS = 4;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        File dotEnv = resolveDotEnvFile();
        if (dotEnv == null) {
            return;
        }
        Map<String, Object> values = parse(dotEnv);
        if (values.isEmpty()) {
            return;
        }
        // addLast：优先级最低，真实环境变量与系统属性都优先于它
        environment.getPropertySources()
                .addLast(new MapPropertySource("dotEnvFile[" + dotEnv.getPath() + "]", values));
    }

    /**
     * 定位 .env。
     *
     * <p>查找顺序：
     * <ol>
     *   <li>{@code -Djeecg.dotenv.location=...} 显式指定；</li>
     *   <li>当前工作目录开始，向上逐级查找（最多 {@value #MAX_PARENT_LEVELS} 级）。
     *       向上找是必要的：IDE 里工作目录常常是模块目录，
     *       而 .env 放在 {@code server/} 下。</li>
     * </ol>
     */
    private File resolveDotEnvFile() {
        String explicit = System.getProperty(LOCATION_PROPERTY);
        if (explicit != null && !explicit.trim().isEmpty()) {
            File f = new File(explicit.trim());
            return f.isFile() ? f : null;
        }

        File dir = new File(System.getProperty("user.dir", "."));
        for (int level = 0; level <= MAX_PARENT_LEVELS && dir != null; level++) {
            File candidate = new File(dir, ".env");
            if (candidate.isFile()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    /**
     * 解析 .env。
     *
     * <p>规则刻意保持简单（与 .env 的通行习惯一致）：
     * <ul>
     *   <li>忽略空行与 {@code #} 开头的注释行；</li>
     *   <li>按**第一个** {@code =} 切分，值里可以含 {@code =}；</li>
     *   <li>值不做引号剥离、不做转义处理 —— 我们规定 .env 里不加引号、
     *       不写行尾注释，所以也不需要这些；</li>
     *   <li>去掉行尾的 {@code \r}（Windows 编辑过的文件常见），
     *       否则口令会多一个不可见字符，报出极具误导性的「Access denied」；</li>
     *   <li>去掉可能存在的 UTF-8 BOM，否则第一个键名会带上 BOM 而永远匹配不上。</li>
     * </ul>
     */
    private Map<String, Object> parse(File file) {
        Map<String, Object> values = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    // 去掉 UTF-8 BOM
                    if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                        line = line.substring(1);
                    }
                    first = false;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1);
                while (value.endsWith("\r")) {
                    value = value.substring(0, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (Exception e) {
            // 读 .env 失败不该让应用起不来：它只是「本地兜底」，
            // 真正的环境变量仍然有效；这里只提示，让占位符解析失败时的报错去说明问题
            System.err.println("[jeecg] 读取 .env 失败（已忽略）：" + file.getPath() + " → " + e.getMessage());
            return new LinkedHashMap<>();
        }
        System.out.println("[jeecg] 已加载环境变量文件：" + file.getPath() + "（" + values.size() + " 项）");
        return values;
    }
}
