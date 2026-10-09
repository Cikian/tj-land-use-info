package org.jeecg.modules.land.data.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * @Description: 数据更新与移除 - 回收站（软删记录）查询与恢复
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 为什么必须单独写这个 Mapper，不能复用 LandMapper / FacilityMapper</b>：
 * 两个实体的 {@code delFlag} 上都有 MyBatis-Plus 的 {@code @TableLogic}，
 * 于是 {@code BaseMapper} 的每一次查询都会自动追加 {@code del_flag = 0} ——
 * 「回收站」恰恰要查 {@code del_flag = 1} 的行，用实体查询永远查不到。
 * 所以这里用<b>手写注解 SQL 直接查表</b>，绕开逻辑删除过滤。
 *
 * <p><b>★ 两张表的软删列名不一样（真实结构差异，不是笔误）</b>：
 * <pre>
 *   t_land                           del_flag  (下划线, tinyint)
 *   xj_kjkfb_supporting_facilities   delFlag   (驼峰, varchar '0'/'1')
 * </pre>
 * 这是复制旧表时就留下的差异（Facility 实体上专门有 {@code @TableField("delFlag")}
 * 的注释在讲这件事）。写 SQL 时必须分辨，写错就是「回收站永远为空」。
 */
@Mapper
public interface DataRecycleMapper {

    // ==================================================================
    // 一、回收站列表
    // ==================================================================

    /**
     * 已移除的宗地。
     *
     * <p>返回列与 {@code t_land} 对齐，外加 {@code bizType} 常量列，
     * 让两类数据能合并到同一个列表里渲染。
     */
    @Select("<script>"
            + "SELECT 'land' AS bizType, l.`id` AS id, l.`crzdbh` AS bizKey, "
            + "       l.`dkmc` AS name, l.`xzqh` AS xzqh, l.`xmfl` AS xmfl, "
            + "       l.`lrdw` AS lrdw, l.`lrr` AS lrr, "
            + "       NULL AS projectName, "
            + "       NULL AS updateBy, l.`update_time` AS updateTime, "
            + "       l.`del_flag` AS delFlag "
            + "FROM `t_land` l WHERE l.`del_flag` = 1 "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "  AND (l.`crzdbh` LIKE CONCAT('%', #{keyword}, '%')"
            + "       OR l.`dkmc` LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + " ORDER BY l.`update_time` DESC, l.`crzdbh` ASC"
            + "</script>")
    List<Map<String, Object>> selectDeletedLands(@Param("keyword") String keyword);

    /**
     * 已移除的配套项目。
     *
     * <p>★ 注意 {@code delFlag} 是 varchar：用 {@code = '1'} 而不是 {@code = 1}
     * 虽然 MySQL 会隐式转换，但显式写字符串更贴合列定义，也避免
     * 未来换库（达梦 / PgSQL）时类型不匹配。
     */
    @Select("<script>"
            + "SELECT 'facility' AS bizType, f.`id` AS id, f.`crzdbh` AS bizKey, "
            + "       f.`ptxmmc` AS name, f.`xzqh` AS xzqh, f.`xmfl` AS xmfl, "
            + "       f.`lrdw` AS lrdw, f.`lrr` AS lrr, "
            + "       f.`ptxmmc` AS projectName, "
            + "       NULL AS updateBy, f.`createTime` AS updateTime, "
            + "       f.`delFlag` AS delFlag "
            + "FROM `xj_kjkfb_supporting_facilities` f WHERE f.`delFlag` = '1' "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "  AND (f.`crzdbh` LIKE CONCAT('%', #{keyword}, '%')"
            + "       OR f.`ptxmmc` LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + " ORDER BY f.`createTime` DESC, f.`crzdbh` ASC"
            + "</script>")
    List<Map<String, Object>> selectDeletedFacilities(@Param("keyword") String keyword);

    /** 回收站计数（按业务类型分开，页面顶部卡片用） */
    @Select("SELECT "
            + " (SELECT COUNT(*) FROM `t_land` WHERE `del_flag` = 1) AS landDeleted, "
            + " (SELECT COUNT(*) FROM `xj_kjkfb_supporting_facilities` WHERE `delFlag` = '1') AS facilityDeleted")
    Map<String, Object> selectRecycleSummary();

    // ==================================================================
    // 二、恢复
    // ==================================================================

    /** 恢复一条宗地 */
    @Update("UPDATE `t_land` SET `del_flag` = 0, `update_by` = #{updateBy}, `update_time` = NOW() "
            + "WHERE `id` = #{id} AND `del_flag` = 1")
    int restoreLand(@Param("id") String id, @Param("updateBy") String updateBy);

    /** 恢复一条配套项目 */
    @Update("UPDATE `xj_kjkfb_supporting_facilities` SET `delFlag` = '0' "
            + "WHERE `id` = #{id} AND `delFlag` = '1'")
    int restoreFacility(@Param("id") String id);

    // ==================================================================
    // 三、冲突检测（恢复前必须查，否则会撞唯一键）
    // ==================================================================

    /**
     * 原宗地编号在「有效记录」里是否已被占用。
     *
     * <p>★ 为什么恢复前必须查这个：
     * {@code t_land} 的唯一键是 {@code (crzdbh, del_flag)}，同一编号
     * <b>最多只能有一条 del_flag=0 和一条 del_flag=1</b>。
     * 用户「移除 A → 重新录入同编号 B → 再想恢复 A」时，
     * 恢复会直接撞唯一键并抛数据库异常 —— 必须在业务层先拦下来，
     * 给一句人话（「该编号已被另一条记录占用，请先处理那条记录」），
     * 而不是把 {@code Duplicate entry} 原样甩给用户。
     *
     * @return 已存在的有效记录 id，没有则 null
     */
    @Select("SELECT `id` FROM `t_land` WHERE `crzdbh` = #{crzdbh} AND `del_flag` = 0 LIMIT 1")
    String selectActiveLandIdByCrzdbh(@Param("crzdbh") String crzdbh);

    /**
     * 原配套项目名称在「有效记录」里是否已被占用。
     *
     * <p>配套表没有唯一键（旧表本来就没有），所以这里不阻止恢复，
     * 只用来给用户一句提示（「已存在同名配套项目，恢复后会出现两条同名记录」）。
     */
    @Select("SELECT COUNT(*) FROM `xj_kjkfb_supporting_facilities` "
            + "WHERE `ptxmmc` = #{ptxmmc} AND COALESCE(`delFlag`, '0') = '0'")
    int countActiveFacilityByPtxmmc(@Param("ptxmmc") String ptxmmc);

    /** 取一条被移除的宗地（恢复前的完整性校验 + 履历留痕要用它的 crzdbh） */
    @Select("SELECT `id`, `crzdbh`, `dkmc` FROM `t_land` WHERE `id` = #{id} AND `del_flag` = 1")
    Map<String, Object> selectDeletedLand(@Param("id") String id);

    /** 取一条被移除的配套项目 */
    @Select("SELECT `id`, `crzdbh`, `ptxmmc` FROM `xj_kjkfb_supporting_facilities` "
            + "WHERE `id` = #{id} AND `delFlag` = '1'")
    Map<String, Object> selectDeletedFacility(@Param("id") String id);
}
