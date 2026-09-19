package org.jeecg.modules.land.archive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.land.archive.entity.ArchiveCategory;

import java.util.List;
import java.util.Map;

/**
 * @Description: 档案类别树 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-09-18
 * @Version: V1.0
 */
public interface ArchiveCategoryMapper extends BaseMapper<ArchiveCategory> {

    /**
     * 批量重算子树的 path 与 level（移动类别时使用）。
     *
     * @param oldPrefix 原子树根的 path，如 /A/B
     * @param newPrefix 新子树根的 path，如 /C/B
     * @param levelDelta level 增量（父级层级变化量）
     * @return 受影响行数
     */
    int refreshSubtreePath(@Param("oldPrefix") String oldPrefix,
                           @Param("newPrefix") String newPrefix,
                           @Param("levelDelta") int levelDelta);

    /**
     * 按 path 前缀批量启停（停用父类别时级联停用整棵子树）。
     *
     * @param pathPrefix 形如 /A/B
     * @param status     1启用 0停用
     * @param updateBy   操作人
     * @return 受影响行数
     */
    int updateStatusByPathPrefix(@Param("pathPrefix") String pathPrefix,
                                 @Param("status") int status,
                                 @Param("updateBy") String updateBy);

    /**
     * 探测某张表在当前库中是否存在。
     *
     * <p>档案文件表（t_archive_file）尚未落地时，类别移除不做
     * 「该类别下是否存在档案」的前置校验，此处用于探测。
     *
     * <p>注意：SQL 里用的是 MySQL 的 {@code SCHEMA()}（{@code DATABASE()} 的同义词）——
     * jeecg 的 MyBatis 拦截器会用 JSqlParser 解析 SQL，而 JSqlParser 4.3 无法解析
     * {@code DATABASE()}，会抛 ParseException。
     *
     * @param tableName 表名
     * @return 1 存在，0 不存在
     */
    int countTable(@Param("tableName") String tableName);

    /**
     * 按类别统计档案（文件）数量。
     *
     * <p>★ 档案类别挂在 {@code t_archive_file.category_id} 上（每个文件一个类别），
     * 而不是档案主表 t_archive 上，因此这里的统计口径是「卷内文件数」。
     *
     * @return [{categoryId, cnt}]
     */
    List<Map<String, Object>> countArchiveGroupByCategory();

    /**
     * 按类别 path 前缀统计档案（文件）数量，<b>含全部子类别</b>。
     *
     * <p>用于「移除类别」的前置校验：只有该类别及其整棵子树下都没有任何档案文件时，
     * 才允许移除。
     *
     * @param categoryPath 类别 path（主键串），如 /A/B
     * @return 文件数
     */
    int countArchiveByCategoryPath(@Param("categoryPath") String categoryPath);
}
