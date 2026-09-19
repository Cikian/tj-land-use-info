package org.jeecg.modules.land.archive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.dto.ArchiveQueryDTO;
import org.jeecg.modules.land.archive.entity.Archive;
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
}
