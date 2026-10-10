package org.jeecg.modules.land.data.attachment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.land.data.attachment.entity.LandAttachment;

import java.util.List;
import java.util.Map;

/**
 * @Description: 数据管理统一附件 Mapper
 * @Author: 星际空间（天津）科技发展有限公司
 * @Date: 2026-10-08
 * @Version V1.0
 *
 * <p><b>★ 全部走注解 SQL，不建 XML</b>：这几句都是简单查询/自增，
 * 建 XML 只会多一个要维护的文件，还会被 mapper-locations 的通配卷进别的模块。
 *
 * <p><b>★ 一律带 {@code del_flag = 0}</b>：本 Mapper 用注解 SQL 手写，
 * <b>MyBatis-Plus 的 {@code @TableLogic} 只对 BaseMapper 的方法生效</b>，
 * 手写 SQL 不会自动加逻辑删除条件。漏了这个条件，已删除的附件会出现在列表里。
 */
@Mapper
public interface LandAttachmentMapper extends BaseMapper<LandAttachment> {

    /** 附件全部列（表别名 a） */
    String COLUMNS = " a.id, a.biz_type AS bizType, a.biz_id AS bizId, a.biz_key AS bizKey, "
            + " a.file_type AS fileType, a.file_name AS fileName, a.file_ext AS fileExt, "
            + " a.file_size AS fileSize, a.file_md5 AS fileMd5, a.content_type AS contentType, "
            + " a.store_type AS storeType, a.store_path AS storePath, a.remark, a.sort_no AS sortNo, "
            + " a.upload_by AS uploadBy, a.upload_name AS uploadName, a.upload_time AS uploadTime, "
            + " a.download_count AS downloadCount, a.del_flag AS delFlag ";

    /** 某业务对象的附件列表，按排序号 + 上传时间 */
    @Select("SELECT " + COLUMNS + " FROM t_land_attachment a "
            + "WHERE a.del_flag = 0 AND a.biz_type = #{bizType} AND a.biz_id = #{bizId} "
            + "ORDER BY IFNULL(a.sort_no, 9999) ASC, a.upload_time ASC")
    List<LandAttachment> selectByBiz(@Param("bizType") String bizType, @Param("bizId") String bizId);

    /** 一批业务对象的附件（列表页批量取，避免 N+1） */
    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM t_land_attachment a "
            + "WHERE a.del_flag = 0 AND a.biz_type = #{bizType} AND a.biz_id IN "
            + "<foreach collection='bizIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " ORDER BY a.biz_id ASC, IFNULL(a.sort_no, 9999) ASC"
            + "</script>")
    List<LandAttachment> selectByBizIds(@Param("bizType") String bizType,
                                        @Param("bizIds") List<String> bizIds);

    /**
     * 按业务主键统计附件数与总大小。
     *
     * <p>返回列：{@code bizId} / {@code num} / {@code totalSize}
     */
    @Select("<script>"
            + "SELECT a.biz_id AS bizId, COUNT(*) AS num, IFNULL(SUM(a.file_size), 0) AS totalSize "
            + "FROM t_land_attachment a "
            + "WHERE a.del_flag = 0 AND a.biz_type = #{bizType} AND a.biz_id IN "
            + "<foreach collection='bizIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " GROUP BY a.biz_id"
            + "</script>")
    List<Map<String, Object>> selectCountGroupByBiz(@Param("bizType") String bizType,
                                                    @Param("bizIds") List<String> bizIds);

    /** 附件类型分布（数据管理页顶部的「附件概览」用） */
    @Select("SELECT a.file_type AS fileType, COUNT(*) AS num, IFNULL(SUM(a.file_size), 0) AS totalSize "
            + "FROM t_land_attachment a "
            + "WHERE a.del_flag = 0 AND (#{bizType} IS NULL OR a.biz_type = #{bizType}) "
            + "GROUP BY a.file_type ORDER BY num DESC")
    List<Map<String, Object>> selectTypeDistribution(@Param("bizType") String bizType);

    /** 全局附件总数与总大小（数据管理页概览卡） */
    @Select("SELECT COUNT(*) AS num, IFNULL(SUM(file_size), 0) AS totalSize "
            + "FROM t_land_attachment WHERE del_flag = 0 "
            + "AND (#{bizType} IS NULL OR biz_type = #{bizType})")
    Map<String, Object> selectSummary(@Param("bizType") String bizType);

    /**
     * 下载次数 +1。
     *
     * <p>★ 用 SQL 自增而不是「读出来 +1 再写回」：后者在并发下载时会丢计数
     * （两个请求都读到 5，各写回 6，实际应为 7）。
     */
    @Update("UPDATE t_land_attachment SET download_count = download_count + 1 WHERE id = #{id}")
    int increaseDownloadCount(@Param("id") String id);

    /**
     * 按 MD5 找同库内已存在的附件（秒传/去重提示用）。
     *
     * <p>只提示不阻止：同一个文件挂在两个配套项目下是正常业务
     * （比如同一份规划条件函同时支撑多个地块），不能当成重复上传拒绝。
     */
    @Select("SELECT " + COLUMNS + " FROM t_land_attachment a "
            + "WHERE a.del_flag = 0 AND a.file_md5 = #{md5} LIMIT 1")
    LandAttachment selectByMd5(@Param("md5") String md5);
}
