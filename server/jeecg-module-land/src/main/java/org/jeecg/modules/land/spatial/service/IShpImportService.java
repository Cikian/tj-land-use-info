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

    /**
     * 按唯一值查切片源表要素范围（大屏列表定位的数据源，替代 dk_geojson）。
     *
     * 流程：land_mvt_source 按 code 取连接 → 连对应 PG 库 → geometry_columns
     * 取几何列 → ST_Extent(ST_Transform(geom,4326)) WHERE 唯一值字段 = 值
     * （精确 + 双向去「号」尾匹配，见 matchGroupWhere；多要素取并集范围）→ [minLon,minLat,maxLon,maxLat]。
     * ★ 值匹配用 PreparedStatement 参数化（值是业务数据，可能含引号等字符）。
     *
     * @param source 连接编码（land_mvt_source.code）
     * @param table  切片源表名（先过标识符白名单）
     * @param field  唯一值字段名（先过标识符白名单）
     * @param value  唯一值（业务数据，如宗地编号/项目名称）
     */
    java.util.List<Double> sliceFeatureBounds(String source, String table, String field, String value) throws Exception;

    /**
     * 回显某唯一值名下的全部要素几何（大屏图形管理弹窗打开时用）。
     *
     * 与 sliceFeatureBounds 同源（land_mvt_source 按 code 取连接 → geometry_columns
     * 取几何列），但**只走精确匹配**：回显是写路径的读，绝不能把 LIKE 近似命中
     * 的别家图形拉进编辑弹窗。
     *
     * @return 各要素的 GeoJSON（WGS84，ST_Transform(geom,4326)）字符串列表；
     *         该唯一值没有图形时返回空列表（不算错误）
     */
    java.util.List<String> sliceFeatureGeometries(String source, String table, String field, String value) throws Exception;

    /**
     * 整组替换某唯一值名下的要素几何（大屏图形管理弹窗「保存」用）。
     *
     * 语义（与前端「弹窗内暂存、点保存才落库」的交互严格对齐）：
     *   先 DELETE 该唯一值的全部旧行，再逐条 INSERT 本次提交的几何；
     *   几何列表为空 = 显式清空整组（弹窗里把所有块都删光的合法结果）。
     * ★ 坐标系：前端给的是 WGS84 GeoJSON，落库前 ST_Transform 到源表 SRID
     *   （geometry_columns 实测值，不写死）；属性列只写唯一值字段，其余留空 ——
     *   业务属性以业务表为准，切片源表只承担「几何 + 唯一值」两个职责。
     * ★ 整组替换在一个事务里完成，中途失败整体回滚，不会留下半组图形。
     *
     * @return 落库的要素行数（= geometries.size()）
     */
    int saveSliceFeatureGroup(String source, String table, String field, String value,
                              java.util.List<String> geometries) throws Exception;

    /**
     * 整组删除某唯一值名下的全部要素几何。
     * 两个用途：① 移除业务记录时联动清图形；② 编辑时改了唯一值（编号/名称），
     * 先删旧值的组再写新值的组，避免旧值名下留下孤儿图形继续在图层上显示。
     *
     * @return 删除的行数
     */
    int deleteSliceFeatureGroup(String source, String table, String field, String value) throws Exception;

    /**
     * 切片源表要素搜索（地名定位 / 空间查询共用的数据底座）。
     *
     * @param source  连接编码（land_mvt_source.code）
     * @param table   切片源表名
     * @param keyword 关键字（可空）：对该表全部字符类型列做 LIKE 模糊匹配
     * @param bbox    可选空间过滤 "xmin,ymin,xmax,ymax"（EPSG:4326），只返回与该范围相交的要素
     * @param limit   返回条数上限（1~200）
     * @return 每条：{ properties: 全部属性列(字符串化), geojson: WGS84 GeoJSON, bbox: [4326 包围盒] }
     */
    java.util.List<java.util.Map<String, Object>> featureSearch(String source, String table, String keyword,
                                                                String bbox, int limit) throws Exception;
}
