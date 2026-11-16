package org.jeecg.modules.land.archive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.dto.ArchiveQueryDTO;
import org.jeecg.modules.land.archive.entity.Archive;
import org.jeecg.modules.land.archive.vo.ArchiveProjectVO;
import org.jeecg.modules.land.archive.vo.ArchiveStatVO;

import java.util.List;
import java.util.Map;

/**
 * @Description: 档案主表 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 *
 * <p>列表 / 详情 / 统计 / 导出都要带「档案类别」——而类别挂在
 * {@code t_archive_file} 上，所以相关查询统一用一条
 * {@code GROUP_CONCAT} 子查询把类别名与类别ID聚合回来，
 * 前端列表的「档案类别」列直接取 {@code categoryNames} 即可。
 */
public interface ArchiveMapper extends BaseMapper<Archive> {

    /**
     * 档案分页查询（含类别聚合）。
     *
     * @param page MyBatis-Plus 分页对象
     * @param q    查询条件
     */
    IPage<Archive> selectArchivePage(Page<Archive> page, @Param("q") ArchiveQueryDTO q);

    /** 档案详情（含类别聚合，不含文件列表） */
    Archive selectDetailById(@Param("id") String id);

    /** 导出用：不分页地取出全部命中档案 */
    List<Archive> selectForExport(@Param("q") ArchiveQueryDTO q);

    /** 统计总览：总数 / 已归档 / 未归档 / 项目数 */
    Map<String, Object> selectStatOverview(@Param("q") ArchiveQueryDTO q);

    /** 卷内文件总数与总字节（跟随同一套过滤条件） */
    Map<String, Object> selectStatFileOverview(@Param("q") ArchiveQueryDTO q);

    /** 按状态统计 */
    List<ArchiveStatVO.NameCount> selectStatByStatus(@Param("q") ArchiveQueryDTO q);

    /** 按档案年度统计 */
    List<ArchiveStatVO.NameCount> selectStatByYear(@Param("q") ArchiveQueryDTO q);

    /** 按密级统计 */
    List<ArchiveStatVO.NameCount> selectStatBySecretLevel(@Param("q") ArchiveQueryDTO q);

    /** 按行政区统计 */
    List<ArchiveStatVO.NameCount> selectStatByXzqh(@Param("q") ArchiveQueryDTO q);

    /** 卷内文件按类别统计（返回 categoryId / categoryPath / categoryName / cnt） */
    List<Map<String, Object>> selectStatFileByCategory(@Param("q") ArchiveQueryDTO q);

    /** 按项目统计（方案原文重点） */
    List<ArchiveStatVO.ProjectStat> selectStatByProject(@Param("q") ArchiveQueryDTO q,
                                                        @Param("limit") int limit);

    // ==================================================================
    // 按项目聚合（P2-07）
    // ==================================================================

    /**
     * 项目分组计数（分页用）。
     *
     * <p>单独一条 COUNT 而不是让分页插件去包一层，原因是分组查询的
     * 「总条数」= 项目数而不是案卷数，插件自动生成的 count SQL 会把 GROUP BY 也数进去。
     */
    long countProjectGroups(@Param("q") ArchiveQueryDTO q);

    /**
     * 按项目分组取一页（树形第一层）。
     *
     * <p>分页用 {@code LIMIT offset, size} 手写：分组查询走 MyBatis-Plus 分页插件时
     * 拿不到「项目数」口径的总数，这里统一由 Service 拼 {@link com.baomidou.mybatisplus.extension.plugins.pagination.Page}。
     *
     * @param q      筛选条件（与列表同一套，保证两种视图看到同一批数据）
     * @param offset 偏移量
     * @param limit  每页项目数
     */
    List<ArchiveProjectVO> selectProjectGroups(@Param("q") ArchiveQueryDTO q,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    /**
     * 取某个项目分组下的全部案卷（树形第二层，含类别聚合）。
     *
     * @param q          筛选条件
     * @param projectKey 分组键（由 {@code selectProjectGroups} 生成，原样回传）
     */
    List<Archive> selectCaseFilesByProject(@Param("q") ArchiveQueryDTO q,
                                           @Param("projectKey") String projectKey);

    /**
     * 取某年度已用掉的最大流水号（档案号自动生成用）。
     *
     * @param prefix 形如 {@code DA-2026-}
     * @return 最大流水号；没有记录时返回 null
     */
    Integer selectMaxSeqOfYear(@Param("prefix") String prefix);

    /** 档案号重复校验（编辑时用 excludeId 排除自身） */
    int countByArchiveNo(@Param("archiveNo") String archiveNo,
                         @Param("excludeId") String excludeId);

    /**
     * 统计某个档案下<b>真实落库</b>的卷内文件行数（{@code t_archive_file.del_flag = 0}）。
     *
     * <p><b>为什么单独加这个方法（P1-11）</b>：收发文归档的「三段计数」里，第三段
     * （落库行数）以前直接用入参 {@code files.size()}，那是「我打算写几个」，
     * 不是「库里真有几个」。写入部分失败（某条 store_path 为空被跳过、
     * 批量插入被截断、并发删除）时，计数照样显示成功，等于把缺陷藏起来。
     *
     * <p>为什么手写 SQL 而不是 Wrapper：{@code ArchiveFile} 上有 {@code @TableLogic}，
     * 用实体 Wrapper 会依赖逻辑删除插件的拼装细节；这里的口径就是「库里现在有几行」，
     * 写成一条显式带 {@code del_flag = 0} 的 COUNT 最不容易被后来的改动带偏。
     */
    long countFilesByArchiveId(@Param("archiveId") String archiveId);
}
