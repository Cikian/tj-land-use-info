package org.jeecg.modules.land.spatial.service;

import org.jeecg.modules.land.spatial.vo.SpatialPrecheckVO;
import org.jeecg.modules.land.spatial.vo.SpatialSourceStatsVO;
import org.jeecg.modules.land.spatial.vo.ShpImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * SHP 上传入库服务（MVT 空间表服务的数据入口）。
 */
public interface IShpImportService {

    /**
     * 导入前预检：目标库在不在、本次凭据能否连上、是否本系统导入的库、库内现有哪些表。
     * 供前端决定"库已存在"时让用户在 连接导入 / 覆盖重建 之间显式选择，
     * 而不是静默替用户决定。预检失败（凭据错 / 主机不可达）直接抛出，不进导入。
     */
    SpatialPrecheckVO precheck(String host, int port, String user,
                               String password, String dbName) throws Exception;

    /**
     * 上传 zip（内含且仅含一个 .shp 及配套文件）→ 建库（如不存在）→
     * 启用 PostGIS → 双引擎导入 → 替换式登记（清单 + 出图数据源）。
     *
     * @param file           zip 压缩包
     * @param host           PG 地址
     * @param port           PG 端口
     * @param user           PG 账号
     * @param password       PG 密码
     * @param dbName         目标库名（不存在则自动创建）
     * @param tableName      目标表名（为空时取 shp 文件名）
     * @param srid           坐标系（<=0 时尝试从 .prj 识别，识别不到默认 4326）
     * @param importEngine   导入引擎（为空取配置 land.spatial.import-engine）
     * @param existingPolicy 目标库已存在时的处理策略：connect=连接导入（保留库，
     *                       仅覆盖同名表）；overwrite=覆盖重建（整库删除后重建）。
     *                       库不存在时忽略。为空按 connect 处理（兼容旧调用方）。
     */
    ShpImportResultVO importShp(MultipartFile file, String host, int port,
                                String user, String password, String dbName,
                                String tableName, int srid, String importEngine,
                                String existingPolicy) throws Exception;

    /**
     * 从 GIS 库管理清单（land_mvt_source）移除，并清理 sys_data_source 注册。
     *
     * @param dropDatabase false=仅取消登记（PG 库及其数据保留）；
     *                     true=先删除 PG 库（先断开所有连接再 DROP），删库成功后才清
     *                     登记——删库失败则整体中止、登记保留以便重试，
     *                     避免出现"库还在、登记没了"的孤儿库。
     *                     ★ 仅限 remark='SHP 上传入库' 的库；其它来源一律拒绝。
     */
    void removeGisSource(String id, boolean dropDatabase) throws Exception;

    /**
     * 查询清单中某个库的现状（能否连上、库内多少张表），
     * 供移除确认弹窗展示"将丢失什么"。用登记里存的连接信息在服务端探活，
     * 口令不下发浏览器。
     */
    SpatialSourceStatsVO sourceStats(String id);
}
