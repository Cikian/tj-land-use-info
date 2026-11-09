package org.jeecg.modules.land.spatial.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 空间数据服务配置（land.spatial.*）
 *
 * <p>为什么 shp2pgsql 路径必须可配 + 自动探测：shp2pgsql 是 PG 安装目录下的
 * 可执行文件，不同机器安装位置不同（C/D 盘、版本号、自定义目录），
 * 写死路径换台机器就废。留空时按「配置 → SHOW data_directory 反推 →
 * 常见安装目录 → PATH」顺序自动寻找（见 Shp2pgsqlLocator）。
 *
 * <p>import-engine 双实现是为了信创：shp2pgsql 是 C 二进制（平台相关），
 * GeoTools 是纯 Java（麒麟/鲲鹏等国产环境零适配），可按部署环境切换。
 *
 * <p>pg-* 系列是目标 PG 数据库的默认连接信息——SHP 上传入库和 MVT 出图共用。
 */
@Data
@Component
@ConfigurationProperties(prefix = "land.spatial")
public class SpatialProperties {

    // ── 目标 PG 数据库默认连接信息 ──
    private String pgHost = "127.0.0.1";
    private int pgPort = 5432;
    private String pgUser = "postgres";
    private String pgPassword = "123456";

    // ── shp2pgsql 工具 ──

    /**
     * shp2pgsql 可执行文件路径（可为完整文件路径或所在目录）。
     * 为空时自动探测。例：D:/Program Files/PostgreSQL/16/bin/shp2pgsql.exe
     */
    private String shp2pgsqlPath = "";

    // ── 导入引擎 ──

    /**
     * 默认导入引擎：shp2pgsql（默认，最快）/ geotools（信创推荐，纯 Java）。
     * 请求参数 importEngine 可覆盖此默认值。
     */
    private String importEngine = "shp2pgsql";

    // ── 临时目录与字符集 ──

    /** SHP zip 解压临时目录（默认 系统临时目录/land-spatial） */
    private String tempDir = System.getProperty("java.io.tmpdir") + "/land-spatial";

    /** SHP 属性文件（.dbf）读取字符集，默认 GBK（国内数据多为此编码） */
    private String dbfCharset = "GBK";

    /**
     * shp2pgsql 输出 SQL 的读取字符集。
     * 输出编码跟随运行环境 locale（Windows 中文 = GBK；Linux UTF-8 环境需改为 UTF-8），
     * 配错会报 "malformed input off : 0"。
     */
    private String sqlCharset = "GBK";
}
