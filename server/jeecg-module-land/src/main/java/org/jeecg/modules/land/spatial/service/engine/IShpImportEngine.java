package org.jeecg.modules.land.spatial.service.engine;

import org.jeecg.modules.land.spatial.config.SpatialProperties;

import java.io.File;

/**
 * SHP 导入引擎接口（双实现：shp2pgsql / GeoTools）。
 *
 * <p>引擎只负责「把一个已解压校验过的 shp 写入指定库表」，建库/启用扩展由
 * 服务层统一完成。实现类必须无状态（每次导入 new 一个上下文传入）。
 */
public interface IShpImportEngine {

    /** 引擎名（用于结果回显与日志）：shp2pgsql / geotools */
    String name();

    /**
     * 执行导入。
     *
     * @param ctx 导入上下文（目标库连接信息、表名、SRID、shp 文件、配置）
     * @return 实际导入的要素行数
     */
    int doImport(ShpImportContext ctx) throws Exception;

    /** 导入上下文 */
    class ShpImportContext {
        public String host;
        public int port;
        public String user;
        public String password;
        public String dbName;
        public String tableName;
        public int srid;
        public File shpFile;
        public SpatialProperties props;
    }
}
