package org.jeecg.modules.land.spatial.service;

import org.jeecg.modules.land.spatial.vo.ShpImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * SHP 上传入库服务（MVT 空间表服务的数据入口）。
 */
public interface IShpImportService {

    /**
     * 上传 zip（内含且仅含一个 .shp 及配套文件）→ 建库（如不存在）→
     * 启用 PostGIS → 双引擎导入。
     *
     * @param file         zip 压缩包
     * @param host         PG 地址
     * @param port         PG 端口
     * @param user         PG 账号
     * @param password     PG 密码
     * @param dbName       目标库名（不存在则自动创建）
     * @param tableName    目标表名（为空时取 shp 文件名）
     * @param srid         坐标系（<=0 时尝试从 .prj 识别，识别不到默认 4326）
     * @param importEngine 导入引擎（为空取配置 land.spatial.import-engine）
     */
    ShpImportResultVO importShp(MultipartFile file, String host, int port,
                                String user, String password, String dbName,
                                String tableName, int srid, String importEngine) throws Exception;

    /**
     * 从 GIS 库管理清单（land_mvt_source）移除，并清理 sys_data_source 注册。
     * ★ 只移除管理关系，不删除 PG 库本身。
     */
    void removeGisSource(String id);

    /**
     * 注册 sys_data_source（存明文密码）。
     * ★ 不走 /sys/dataSource/add——该接口会对密码做解密，明文会被搅成乱码密文。
     */
    void registerSysDataSource(String code, String host, int port,
                               String user, String password, String dbName);
}
